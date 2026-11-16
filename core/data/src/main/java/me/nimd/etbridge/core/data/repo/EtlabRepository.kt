package me.nimd.etbridge.core.data.repo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import me.nimd.etbridge.core.data.db.CacheDao
import me.nimd.etbridge.core.data.db.CacheEntry
import me.nimd.etbridge.core.data.demo.DemoDataSource
import me.nimd.etbridge.core.data.demo.EtlabDataSource
import me.nimd.etbridge.core.data.demo.RemoteDataSource
import me.nimd.etbridge.core.data.network.ApiException
import me.nimd.etbridge.core.data.prefs.UserPreferencesRepository
import me.nimd.etbridge.core.data.session.SessionStore
import me.nimd.etbridge.core.model.AcademicAnalysis
import me.nimd.etbridge.core.model.ActivityPointsData
import me.nimd.etbridge.core.model.Course
import me.nimd.etbridge.core.model.CourseDetails
import me.nimd.etbridge.core.model.DayTimetable
import me.nimd.etbridge.core.model.DutyLeave
import me.nimd.etbridge.core.model.EtlabJson
import me.nimd.etbridge.core.model.EvaluationsData
import me.nimd.etbridge.core.model.FeesData
import me.nimd.etbridge.core.model.StudentProfile
import me.nimd.etbridge.core.model.StudyMaterial
import me.nimd.etbridge.core.model.SubjectAttendance
import me.nimd.etbridge.core.model.SurveyItem
import me.nimd.etbridge.core.model.Teacher
import me.nimd.etbridge.core.model.UniversityResult
import java.io.IOException
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/** A cached ETLab payload: its Room key and how to (de)serialize it. */
class Cached<T> internal constructor(val key: String, val serializer: KSerializer<T>)

object EtlabData {
    val Profile = Cached("profile", StudentProfile.serializer())
    val Attendance = Cached("attendance", ListSerializer(SubjectAttendance.serializer()))
    /** Current month's timetable (has live substitutions and attendance marks). */
    val Timetable = Cached("timetable_month", ListSerializer(DayTimetable.serializer()))
    /** Current month plus the three before it, newest first (semester-wide history). */
    val SemesterTimetable = Cached("timetable_semester", ListSerializer(DayTimetable.serializer()))
    val Evaluations = Cached("evaluations", EvaluationsData.serializer())
    val Teachers = Cached("teachers", ListSerializer(Teacher.serializer()))
    val Materials = Cached("materials", ListSerializer(StudyMaterial.serializer()))
    val Fees = Cached("fees", FeesData.serializer())
    val ActivityPoints = Cached("activity_points", ActivityPointsData.serializer())
    val DutyLeaves = Cached("duty_leaves", ListSerializer(DutyLeave.serializer()))
    val Surveys = Cached("surveys", ListSerializer(SurveyItem.serializer()))
    val Results = Cached("results_university", ListSerializer(UniversityResult.serializer()))
    val Courses = Cached("courses", ListSerializer(Course.serializer()))
    val Analysis = Cached("analysis", AcademicAnalysis.serializer())

    fun courseDetails(id: String) = Cached("course_$id", CourseDetails.serializer())
}

data class SyncState(
    val isRefreshing: Boolean = false,
    /** User-facing message from the last sync when nothing could be refreshed. */
    val error: String? = null,
    val isOffline: Boolean = false,
    val sessionExpired: Boolean = false,
)

data class SyncResult(
    val failures: Map<String, Throwable>,
    /** Keys whose payload changed in this sync (used for notifications). */
    val changed: Set<String>,
    val skipped: Boolean = false,
) {
    val ok: Boolean get() = failures.isEmpty()
}

/**
 * Stale-while-revalidate store for every ETLab resource, the native counterpart of the
 * webapp's EtlabApiClient cache + AuthContext.refreshData(). Screens observe Room-backed
 * flows; refreshes write through to Room. Data older than [MAX_AGE_MS] is refetched.
 */
@Singleton
class EtlabRepository @Inject constructor(
    private val cacheDao: CacheDao,
    private val sessionStore: SessionStore,
    private val remote: RemoteDataSource,
    private val demo: DemoDataSource,
    private val prefs: UserPreferencesRepository,
    private val dutyLeaveRepository: DutyLeaveRepository,
) {
    companion object {
        const val MAX_AGE_MS: Long = 4 * 60 * 60 * 1000L
    }

    private val source: EtlabDataSource
        get() = if (sessionStore.current?.isDemo == true) demo else remote

    private val refreshMutex = Mutex()
    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    val lastSync: Flow<Long?> = prefs.preferences.map { it.lastSyncMillis }.distinctUntilChanged()

    fun <T> observe(cached: Cached<T>): Flow<T?> = cacheDao.observe(cached.key)
        .map { it?.json }
        .distinctUntilChanged()
        .map { json -> json?.let { runCatching { EtlabJson.decodeFromString(cached.serializer, it) }.getOrNull() } }
        .flowOn(Dispatchers.Default)

    suspend fun <T> snapshot(cached: Cached<T>): T? = cacheDao.get(cached.key)?.json?.let {
        runCatching { EtlabJson.decodeFromString(cached.serializer, it) }.getOrNull()
    }

    suspend fun isStale(): Boolean {
        val last = prefs.snapshot().lastSyncMillis ?: return true
        return System.currentTimeMillis() - last >= MAX_AGE_MS
    }

    /** Returns true when the payload actually changed. */
    private suspend fun <T> write(cached: Cached<T>, value: T): Boolean {
        val json = EtlabJson.encodeToString(cached.serializer, value)
        val previous = cacheDao.get(cached.key)?.json
        cacheDao.upsert(CacheEntry(cached.key, json, System.currentTimeMillis()))
        return previous != null && previous != json
    }

    private suspend fun isFresh(key: String): Boolean {
        val at = cacheDao.fetchedAt(key) ?: return false
        return System.currentTimeMillis() - at < MAX_AGE_MS
    }

    private class Outcome(val key: String, val error: Throwable?, val changed: Boolean)

    private suspend fun <T> refreshOne(cached: Cached<T>, force: Boolean, fetch: suspend () -> T): Outcome {
        if (!force && isFresh(cached.key)) return Outcome(cached.key, null, false)
        return try {
            Outcome(cached.key, null, write(cached, fetch()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Outcome(cached.key, e, false)
        }
    }

    /** Fetches the current month and the three before it in parallel and merges by date. */
    private suspend fun refreshTimetables(force: Boolean): List<Outcome> = coroutineScope {
        if (!force && isFresh(EtlabData.SemesterTimetable.key) && isFresh(EtlabData.Timetable.key)) {
            return@coroutineScope emptyList()
        }
        val current = YearMonth.now()
        val months = (0L until 4L).map { current.minusMonths(it) }
        val results = months.map { m -> async { runCatching { m to source.monthTimetable(m) } } }.awaitAll()
        val outcomes = mutableListOf<Outcome>()

        val currentMonth = results.firstOrNull()?.getOrNull()?.second
        val firstError = results.firstNotNullOfOrNull { it.exceptionOrNull() }
        outcomes += if (currentMonth != null) {
            Outcome(EtlabData.Timetable.key, null, write(EtlabData.Timetable, currentMonth))
        } else {
            Outcome(EtlabData.Timetable.key, firstError ?: IOException("Timetable unavailable"), false)
        }

        val merged = results.mapNotNull { it.getOrNull()?.second }.flatten()
            .filter { it.date.isNotBlank() }
            .associateBy { it.date }.values
            .sortedByDescending { it.date }
        if (merged.isNotEmpty()) {
            outcomes += Outcome(EtlabData.SemesterTimetable.key, null, write(EtlabData.SemesterTimetable, merged))
        }
        outcomes
    }

    /**
     * Refreshes every resource in parallel. With [force] = false, only resources older than four
     * hours are refetched (same as the webapp's refreshData(false)). Concurrent calls coalesce.
     */
    suspend fun refreshAll(force: Boolean = false): SyncResult {
        if (sessionStore.current == null) return SyncResult(emptyMap(), emptySet(), skipped = true)
        if (refreshMutex.isLocked) {
            refreshMutex.withLock { }
            return SyncResult(emptyMap(), emptySet(), skipped = true)
        }
        return refreshMutex.withLock {
            _syncState.update { it.copy(isRefreshing = true) }
            try {
                val src = source
                val outcomes = coroutineScope {
                    val jobs = listOf(
                        async { listOf(refreshOne(EtlabData.Profile, force) { src.profile() }) },
                        async { listOf(refreshOne(EtlabData.Attendance, force) { src.attendance() }) },
                        async { refreshTimetables(force) },
                        async { listOf(refreshOne(EtlabData.Evaluations, force) { src.evaluations() }) },
                        async { listOf(refreshOne(EtlabData.Teachers, force) { src.teachers() }) },
                        async { listOf(refreshOne(EtlabData.Materials, force) { src.studyMaterials() }) },
                        async { listOf(refreshOne(EtlabData.Fees, force) { src.fees() }) },
                        async { listOf(refreshOne(EtlabData.ActivityPoints, force) { src.activityPoints() }) },
                        async { listOf(refreshOne(EtlabData.DutyLeaves, force) { src.dutyLeaves() }) },
                        async { listOf(refreshOne(EtlabData.Surveys, force) { src.surveys() }) },
                        async { listOf(refreshOne(EtlabData.Results, force) { src.universityResults() }) },
                        async { listOf(refreshOne(EtlabData.Courses, force) { src.courses() }) },
                        async { listOf(refreshOne(EtlabData.Analysis, force) { src.academicAnalysis() }) },
                    )
                    jobs.awaitAll().flatten()
                }

                val failures = outcomes.filter { it.error != null }.associate { it.key to it.error!! }
                val changed = outcomes.filter { it.changed }.map { it.key }.toSet()
                val coreKeys = setOf(EtlabData.Profile.key, EtlabData.Attendance.key)
                val coreOk = outcomes.any { it.key in coreKeys && it.error == null }

                if (coreOk) prefs.setLastSync(System.currentTimeMillis())

                val unauthorized = failures.values.any { (it as? ApiException)?.isUnauthorized == true } && !coreOk
                val offline = failures.isNotEmpty() && failures.values.all { it is IOException }
                _syncState.value = SyncState(
                    isRefreshing = false,
                    error = when {
                        unauthorized -> "Your ETLab session has expired. Please sign in again."
                        offline && !coreOk -> "You're offline. Showing saved data."
                        !coreOk && failures.isNotEmpty() -> failures.values.first().message ?: "Couldn't reach ETLab."
                        else -> null
                    },
                    isOffline = offline,
                    sessionExpired = unauthorized,
                )

                reconcileDutyLeaves()
                SyncResult(failures, changed)
            } catch (e: CancellationException) {
                _syncState.update { it.copy(isRefreshing = false) }
                throw e
            }
        }
    }

    suspend fun reconcileDutyLeaves() {
        val official = snapshot(EtlabData.DutyLeaves).orEmpty()
        val timetable = (snapshot(EtlabData.SemesterTimetable).orEmpty() + snapshot(EtlabData.Timetable).orEmpty())
        dutyLeaveRepository.reconcile(official, timetable)
    }

    fun observeCourseDetails(courseId: String): Flow<CourseDetails?> = observe(EtlabData.courseDetails(courseId))

    /** Fetches a course's syllabus, falling back to the cached copy when offline. */
    suspend fun refreshCourseDetails(courseId: String, force: Boolean = false): Result<Unit> {
        val cached = EtlabData.courseDetails(courseId)
        val outcome = refreshOne(cached, force) { source.courseDetails(courseId) }
        return outcome.error?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    fun clearSyncError() = _syncState.update { it.copy(error = null) }

    suspend fun clear() {
        cacheDao.clear()
        _syncState.value = SyncState()
    }
}
