package me.nimd.etbridge.core.data.demo

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import me.nimd.etbridge.core.data.network.EtlabApi
import me.nimd.etbridge.core.model.AcademicAnalysis
import me.nimd.etbridge.core.model.ActivityPointsData
import me.nimd.etbridge.core.model.Course
import me.nimd.etbridge.core.model.CourseDetails
import me.nimd.etbridge.core.model.DayTimetable
import me.nimd.etbridge.core.model.DutyLeave
import me.nimd.etbridge.core.model.EtlabJson
import me.nimd.etbridge.core.model.EvaluationsData
import me.nimd.etbridge.core.model.FeesData
import me.nimd.etbridge.core.model.Institute
import me.nimd.etbridge.core.model.StudentProfile
import me.nimd.etbridge.core.model.StudyMaterial
import me.nimd.etbridge.core.model.SubjectAttendance
import me.nimd.etbridge.core.model.SurveyItem
import me.nimd.etbridge.core.model.Teacher
import me.nimd.etbridge.core.model.UniversityResult
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Everything the repository needs from ETLab. Implemented by the live API and by demo mode. */
interface EtlabDataSource {
    suspend fun profile(): StudentProfile
    suspend fun attendance(): List<SubjectAttendance>
    suspend fun monthTimetable(month: YearMonth): List<DayTimetable>
    suspend fun evaluations(): EvaluationsData
    suspend fun teachers(): List<Teacher>
    suspend fun studyMaterials(): List<StudyMaterial>
    suspend fun fees(): FeesData
    suspend fun activityPoints(): ActivityPointsData
    suspend fun dutyLeaves(): List<DutyLeave>
    suspend fun surveys(): List<SurveyItem>
    suspend fun universityResults(): List<UniversityResult>
    suspend fun courses(): List<Course>
    suspend fun courseDetails(courseId: String): CourseDetails
    suspend fun academicAnalysis(): AcademicAnalysis
}

@Singleton
class RemoteDataSource @Inject constructor(private val api: EtlabApi) : EtlabDataSource {
    override suspend fun profile() = api.profile()
    override suspend fun attendance() = api.attendance()
    override suspend fun monthTimetable(month: YearMonth) = api.monthTimetable(month)

    /**
     * The webapp's getEvaluations() is a stub that never calls ETLab (the /evaluations payload
     * hasn't been mapped yet), so real accounts get no series marks or assignments there either.
     */
    override suspend fun evaluations() = EvaluationsData()

    override suspend fun teachers() = api.teachers()
    override suspend fun studyMaterials() = api.studyMaterials()
    override suspend fun fees() = api.fees()
    override suspend fun activityPoints() = api.activityPoints()
    override suspend fun dutyLeaves() = api.dutyLeaves()
    override suspend fun surveys() = api.surveys()
    override suspend fun universityResults() = api.universityResults()
    override suspend fun courses() = api.courses()
    override suspend fun courseDetails(courseId: String) = api.courseDetails(courseId)
    override suspend fun academicAnalysis() = api.academicAnalysis()
}

/**
 * Demo mode (username/password "demo"): serves the webapp's mockData.ts fixtures, exported to
 * assets/demo. All yyyy-MM-dd dates are shifted by whole weeks so the demo timetable always
 * lines up with the current week (weekdays stay correct).
 */
@Singleton
class DemoDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : EtlabDataSource {

    private val anchorMonday = LocalDate.of(2026, 11, 16)
    private val dateRegex = Regex("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b")

    private val shiftDays: Long
        get() = ChronoUnit.WEEKS.between(anchorMonday, LocalDate.now().with(DayOfWeek.MONDAY)) * 7

    private suspend fun <T> load(name: String, serializer: KSerializer<T>): T = withContext(Dispatchers.IO) {
        delay(120) // a hint of latency so loading states are exercised in demo mode
        val raw = context.assets.open("demo/$name.json").bufferedReader().use { it.readText() }
        val shift = shiftDays
        val shifted = if (shift == 0L) raw else dateRegex.replace(raw) { m ->
            runCatching { LocalDate.parse(m.value).plusDays(shift).toString() }.getOrDefault(m.value)
        }
        EtlabJson.decodeFromString(serializer, shifted)
    }

    suspend fun institutes(): List<Institute> = load("fallback_institutes", ListSerializer(Institute.serializer()))

    override suspend fun profile() = load("demo_profile", StudentProfile.serializer())
    override suspend fun attendance() = load("demo_attendance", ListSerializer(SubjectAttendance.serializer()))
    override suspend fun monthTimetable(month: YearMonth) =
        load("demo_month_timetable", ListSerializer(DayTimetable.serializer()))
            .filter { it.date.startsWith(month.toString()) }

    override suspend fun evaluations() = load("demo_evaluations", EvaluationsData.serializer())
    override suspend fun teachers() = load("demo_teachers", ListSerializer(Teacher.serializer()))
    override suspend fun studyMaterials() = load("demo_materials", ListSerializer(StudyMaterial.serializer()))
    override suspend fun fees() = load("demo_fees", FeesData.serializer())
    override suspend fun activityPoints() = load("demo_activity_points", ActivityPointsData.serializer())
    override suspend fun dutyLeaves() = load("demo_duty_leaves", ListSerializer(DutyLeave.serializer()))
    override suspend fun surveys() = load("demo_surveys", ListSerializer(SurveyItem.serializer()))
    override suspend fun universityResults() = load("demo_results", ListSerializer(UniversityResult.serializer()))
    override suspend fun courses() = load("demo_courses", ListSerializer(Course.serializer()))

    override suspend fun courseDetails(courseId: String): CourseDetails {
        val all = load("demo_course_details", MapSerializer(String.serializer(), CourseDetails.serializer()))
        return all[courseId] ?: CourseDetails(id = courseId, name = "Course Details")
    }

    override suspend fun academicAnalysis() = load("demo_academic_analysis", AcademicAnalysis.serializer())
}
