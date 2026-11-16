package me.nimd.etbridge.ui

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.nimd.etbridge.core.data.prefs.UserPreferencesRepository
import me.nimd.etbridge.core.data.repo.DutyLeaveRepository
import me.nimd.etbridge.core.data.repo.EtlabData
import me.nimd.etbridge.core.data.repo.EtlabRepository
import me.nimd.etbridge.core.domain.AttendanceCalculator
import me.nimd.etbridge.core.domain.OverallAttendance
import me.nimd.etbridge.core.domain.SubjectProjection
import me.nimd.etbridge.core.model.AcademicAnalysis
import me.nimd.etbridge.core.model.ActivityPointsData
import me.nimd.etbridge.core.model.Course
import me.nimd.etbridge.core.model.DayTimetable
import me.nimd.etbridge.core.model.DutyLeave
import me.nimd.etbridge.core.model.EvaluationsData
import me.nimd.etbridge.core.model.FeesData
import me.nimd.etbridge.core.model.PendingDutyLeave
import me.nimd.etbridge.core.model.StudentProfile
import me.nimd.etbridge.core.model.StudyMaterial
import me.nimd.etbridge.core.model.SubjectAttendance
import me.nimd.etbridge.core.model.SurveyItem
import me.nimd.etbridge.core.model.Teacher
import me.nimd.etbridge.core.model.UniversityResult
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
data class SubjectRow(val subject: SubjectAttendance, val projection: SubjectProjection)

/** Everything a screen may need, decoded once and shared app-wide. */
@Immutable
data class StudentSnapshot(
    val loaded: Boolean = false,
    val profile: StudentProfile? = null,
    val attendance: List<SubjectAttendance>? = null,
    val timetable: List<DayTimetable> = emptyList(),
    val semesterTimetable: List<DayTimetable> = emptyList(),
    val evaluations: EvaluationsData? = null,
    val teachers: List<Teacher> = emptyList(),
    val materials: List<StudyMaterial> = emptyList(),
    val fees: FeesData? = null,
    val activityPoints: ActivityPointsData? = null,
    val dutyLeaves: List<DutyLeave> = emptyList(),
    val surveys: List<SurveyItem> = emptyList(),
    val results: List<UniversityResult> = emptyList(),
    val courses: List<Course> = emptyList(),
    val analysis: AcademicAnalysis? = null,
    val pendingDutyLeaves: List<PendingDutyLeave> = emptyList(),
    val subjects: List<SubjectRow> = emptyList(),
    val overall: OverallAttendance = OverallAttendance.from(null, 0),
    val target: Double = AttendanceCalculator.DEFAULT_TARGET,
) {
    /** Timetable days from both sources, newest first, current month taking precedence. */
    val allDays: List<DayTimetable> by lazy {
        (timetable + semesterTimetable).distinctBy { it.date }.sortedByDescending { it.date }
    }
    val unreconciledPending: List<PendingDutyLeave> get() = pendingDutyLeaves.filter { !it.reconciled }
}

@Singleton
class StudentDataHub @Inject constructor(
    repo: EtlabRepository,
    dutyLeaves: DutyLeaveRepository,
    prefs: UserPreferencesRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Suppress("UNCHECKED_CAST")
    private val raw: Flow<StudentSnapshot> = combine(
        listOf(
            repo.observe(EtlabData.Profile),
            repo.observe(EtlabData.Attendance),
            repo.observe(EtlabData.Timetable),
            repo.observe(EtlabData.SemesterTimetable),
            repo.observe(EtlabData.Evaluations),
            repo.observe(EtlabData.Teachers),
            repo.observe(EtlabData.Materials),
            repo.observe(EtlabData.Fees),
            repo.observe(EtlabData.ActivityPoints),
            repo.observe(EtlabData.DutyLeaves),
            repo.observe(EtlabData.Surveys),
            repo.observe(EtlabData.Results),
            repo.observe(EtlabData.Courses),
            repo.observe(EtlabData.Analysis),
            dutyLeaves.pending,
            prefs.preferences.map { it.attendanceTarget }.distinctUntilChanged(),
        ),
    ) { a ->
        val attendance = a[1] as List<SubjectAttendance>?
        val timetable = (a[2] as List<DayTimetable>?).orEmpty()
        val semester = (a[3] as List<DayTimetable>?).orEmpty()
        val pending = a[14] as List<PendingDutyLeave>
        val userTarget = (a[15] as Int).toDouble()
        val profile = a[0] as StudentProfile?
        val days = (timetable + semester).distinctBy { it.date }
        val override = userTarget.takeIf { it != AttendanceCalculator.DEFAULT_TARGET }
        val rows = attendance.orEmpty().map { s ->
            SubjectRow(s, AttendanceCalculator.project(s, pending, days, override))
        }
        val pendingHours = pending.filter { !it.reconciled }.sumOf { it.hours }
        StudentSnapshot(
            loaded = true,
            profile = profile,
            attendance = attendance,
            timetable = timetable,
            semesterTimetable = semester,
            evaluations = a[4] as EvaluationsData?,
            teachers = (a[5] as List<Teacher>?).orEmpty(),
            materials = (a[6] as List<StudyMaterial>?).orEmpty(),
            fees = a[7] as FeesData?,
            activityPoints = a[8] as ActivityPointsData?,
            dutyLeaves = (a[9] as List<DutyLeave>?).orEmpty(),
            surveys = (a[10] as List<SurveyItem>?).orEmpty(),
            results = (a[11] as List<UniversityResult>?).orEmpty(),
            courses = (a[12] as List<Course>?).orEmpty(),
            analysis = a[13] as AcademicAnalysis?,
            pendingDutyLeaves = pending,
            subjects = rows,
            overall = OverallAttendance.from(profile?.attendance, pendingHours),
            target = override ?: AttendanceCalculator.DEFAULT_TARGET,
        )
    }.flowOn(Dispatchers.Default)

    val snapshot: StateFlow<StudentSnapshot> =
        raw.stateIn(scope, SharingStarted.WhileSubscribed(10_000), StudentSnapshot())

    /** Wall clock that ticks every 30 s, for "now/next class" UI. */
    val clock: Flow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(30_000)
        }
    }
}
