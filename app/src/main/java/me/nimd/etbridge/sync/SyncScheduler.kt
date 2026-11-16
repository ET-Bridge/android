package me.nimd.etbridge.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import me.nimd.etbridge.core.data.prefs.UserPreferencesRepository
import me.nimd.etbridge.core.data.repo.EtlabData
import me.nimd.etbridge.core.data.repo.EtlabRepository
import me.nimd.etbridge.core.data.session.SessionStore
import me.nimd.etbridge.core.domain.Schedule
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionStore: SessionStore,
    private val prefs: UserPreferencesRepository,
    private val reminders: ClassReminderScheduler,
) {
    private val wm get() = WorkManager.getInstance(context)

    /** Keeps the periodic sync registered exactly while signed in with sync enabled. */
    suspend fun observeAndSchedule() {
        sessionStore.load()
        combine(sessionStore.session, prefs.preferences) { s, p -> (s != null && p.backgroundSync) to (s != null && p.notifyNextClass) }
            .distinctUntilChanged()
            .collect { (sync, remind) ->
                if (sync) schedulePeriodic() else wm.cancelUniqueWork(PERIODIC)
                if (remind) reminders.schedule() else reminders.cancelAll()
            }
    }

    private fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(4, TimeUnit.HOURS, 30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Immediate one-off sync (used by tests / "sync now" in settings). */
    fun syncNow() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniqueWork(ONE_OFF, ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        const val PERIODIC = "etbridge-periodic-sync"
        const val ONE_OFF = "etbridge-sync-now"
    }
}

/** Schedules "class in 10 minutes" notifications for the rest of today. */
@Singleton
class ClassReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: EtlabRepository,
    private val prefs: UserPreferencesRepository,
) {
    private val wm get() = WorkManager.getInstance(context)

    suspend fun schedule() {
        if (!prefs.snapshot().notifyNextClass) return
        val now = LocalDateTime.now()
        val monthly = repo.snapshot(EtlabData.Timetable).orEmpty()
        val semester = repo.snapshot(EtlabData.SemesterTimetable).orEmpty()
        val profileToday = repo.snapshot(EtlabData.Profile)?.timetable.orEmpty()
        val day = Schedule.periodsFor(now.toLocalDate(), monthly, semester)
        val periods = Schedule.uniqueByHour(day?.sub?.takeIf { it.isNotEmpty() } ?: profileToday)
        wm.cancelAllWorkByTag(TAG)
        for (p in periods) {
            if (p.suspended) continue
            val slot = Schedule.slotFor(p.hour) ?: continue
            val fireAt = now.toLocalDate().atTime(slot.start).minusMinutes(10)
            val delay = Duration.between(now, fireAt)
            if (delay.isNegative) continue
            val request = OneTimeWorkRequestBuilder<ClassReminderWorker>()
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(
                        "subject" to p.subject.ifBlank { p.subjectCode },
                        "teacher" to p.teacherName,
                        "time" to slot.label,
                        "hour" to p.hour,
                        "substitution" to (p.change.orEmpty().isNotBlank()),
                    ),
                )
                .addTag(TAG)
                .build()
            wm.enqueueUniqueWork("class-${now.toLocalDate()}-${p.hour}", ExistingWorkPolicy.REPLACE, request)
        }
    }

    fun cancelAll() {
        wm.cancelAllWorkByTag(TAG)
    }

    private companion object {
        const val TAG = "class-reminder"
    }
}

@HiltWorker
class ClassReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val prefs: UserPreferencesRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!prefs.snapshot().notifyNextClass) return Result.success()
        val subject = inputData.getString("subject").orEmpty()
        val teacher = inputData.getString("teacher").orEmpty()
        val time = inputData.getString("time").orEmpty()
        val hour = inputData.getInt("hour", 0)
        val sub = inputData.getBoolean("substitution", false)
        Notifications.post(
            applicationContext,
            2000 + hour,
            Notifications.CHANNEL_CLASSES,
            "Hour $hour in 10 min: $subject",
            listOfNotNull(time, teacher.takeIf { it.isNotBlank() }, "Substitution".takeIf { sub }).joinToString(" · "),
            "timetable",
        )
        return Result.success()
    }
}
