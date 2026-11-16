package me.nimd.etbridge.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import me.nimd.etbridge.core.data.prefs.UserPreferencesRepository
import me.nimd.etbridge.core.data.repo.DutyLeaveRepository
import me.nimd.etbridge.core.data.repo.EtlabData
import me.nimd.etbridge.core.data.repo.EtlabRepository
import me.nimd.etbridge.core.data.session.SessionStore
import me.nimd.etbridge.core.domain.AttendanceCalculator
import me.nimd.etbridge.core.model.formatPercent
import me.nimd.etbridge.widget.WidgetUpdater
import java.io.IOException

/**
 * Periodic background sync (every 4 hours on any network). After refreshing it diffs the new
 * data against what was cached and posts notifications for meaningful changes.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repo: EtlabRepository,
    private val sessionStore: SessionStore,
    private val prefsRepo: UserPreferencesRepository,
    private val dutyLeaves: DutyLeaveRepository,
    private val reminders: ClassReminderScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val session = sessionStore.load() ?: return Result.success()
        val prefs = prefsRepo.snapshot()

        val before = Before(
            attendance = repo.snapshot(EtlabData.Attendance).orEmpty(),
            noticeIds = repo.snapshot(EtlabData.Profile)?.notices?.map { it.id }?.toSet(),
            circularIds = repo.snapshot(EtlabData.Profile)?.circulars?.map { it.id }?.toSet(),
            markedExamIds = repo.snapshot(EtlabData.Evaluations)?.seriesExams
                ?.filter { it.mark.isNotBlank() }?.map { it.id }?.toSet(),
        )

        val result = repo.refreshAll(force = true)
        WidgetUpdater.updateAll(applicationContext)
        reminders.schedule()

        if (result.failures.isNotEmpty() && result.failures.values.all { it is IOException } &&
            result.changed.isEmpty()
        ) {
            return if (runAttemptCount < 3) Result.retry() else Result.success()
        }
        if (session.isDemo) return Result.success()

        if (prefs.notifyLowAttendance && EtlabData.Attendance.key in result.changed) {
            notifyAttendanceDrops(before, prefs.attendanceTarget.toDouble())
        }
        if (prefs.notifyNewContent) notifyNewContent(before)
        return Result.success()
    }

    private data class Before(
        val attendance: List<me.nimd.etbridge.core.model.SubjectAttendance>,
        val noticeIds: Set<String>?,
        val circularIds: Set<String>?,
        val markedExamIds: Set<String>?,
    )

    private suspend fun notifyAttendanceDrops(before: Before, userTarget: Double) {
        val override = userTarget.takeIf { it != AttendanceCalculator.DEFAULT_TARGET }
        val pending = dutyLeaves.pending.first()
        val days = repo.snapshot(EtlabData.SemesterTimetable).orEmpty()
        val wasSafe = before.attendance.associate { s ->
            s.stableKey to AttendanceCalculator.project(s, pending, days, override).isProjectedSafe
        }
        val now = repo.snapshot(EtlabData.Attendance).orEmpty()
        val dropped = now.mapNotNull { s ->
            val p = AttendanceCalculator.project(s, pending, days, override)
            if (!p.isProjectedSafe && wasSafe[s.stableKey] == true) s to p else null
        }
        if (dropped.isEmpty()) return
        val title = if (dropped.size == 1) "${dropped[0].first.subject} dropped below ${dropped[0].second.target.toInt()}%"
        else "${dropped.size} subjects dropped below target"
        val body = dropped.joinToString("\n") { (s, p) ->
            "${s.subject}: ${p.projectedPercentage.formatPercent()}% · attend ${p.projectedMustAttend} more"
        }
        Notifications.post(applicationContext, 1001, Notifications.CHANNEL_ATTENDANCE, title, body.lineSequence().first(), "attendance", body)
    }

    private suspend fun notifyNewContent(before: Before) {
        val profile = repo.snapshot(EtlabData.Profile)
        val newNotices = before.noticeIds?.let { old -> profile?.notices.orEmpty().filter { it.id !in old } }.orEmpty()
        val newCirculars = before.circularIds?.let { old -> profile?.circulars.orEmpty().filter { it.id !in old } }.orEmpty()
        if (newNotices.isNotEmpty() || newCirculars.isNotEmpty()) {
            val titles = (newNotices.map { it.title } + newCirculars.map { it.title })
            val count = titles.size
            Notifications.post(
                applicationContext, 1002, Notifications.CHANNEL_UPDATES,
                if (count == 1) "New notice" else "$count new notices & circulars",
                titles.first(), "notices", titles.joinToString("\n"),
            )
        }
        val exams = repo.snapshot(EtlabData.Evaluations)?.seriesExams.orEmpty()
        val newMarks = before.markedExamIds?.let { old -> exams.filter { it.mark.isNotBlank() && it.id !in old } }.orEmpty()
        if (newMarks.isNotEmpty()) {
            Notifications.post(
                applicationContext, 1003, Notifications.CHANNEL_UPDATES,
                if (newMarks.size == 1) "New marks: ${newMarks[0].subject}" else "${newMarks.size} new marks published",
                newMarks.joinToString(", ") { "${it.subjectCode} ${it.mark}/${it.maximumMark.toInt()}" },
                "academics",
            )
        }
    }
}
