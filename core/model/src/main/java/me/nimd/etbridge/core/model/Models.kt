package me.nimd.etbridge.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/*
 * Ported from webapp/src/api/types.ts. These classes double as wire DTOs and domain models:
 * every field is lenient and defaulted so partial or oddly-typed ETLab payloads still decode.
 */

const val DEMO_TOKEN = "demo-token"

@Serializable
data class Institute(
    val id: LString = "",
    val name: LString = "",
    val shortName: LStringN = null,
    val apiBaseUrl: LStringN = null,
    val domains: List<LString> = emptyList(),
    val keywords: List<LString> = emptyList(),
)

@Serializable
data class AuthSession(
    val token: String,
    val instituteId: String,
    val instituteName: String,
    val username: String,
    val studentName: String? = null,
    val studentPhoto: String? = null,
) {
    val isDemo: Boolean get() = token == DEMO_TOKEN
}

@Serializable
data class TimetablePeriod(
    val hour: LInt = 0,
    val date: LStringN = null,
    val day: LStringN = null,
    val type: LStringN = null,
    @SerialName("subject_type") val subjectType: LStringN = null,
    val subject: LString = "",
    @SerialName("subject_code") val subjectCode: LString = "",
    @SerialName("teacher_name") val teacherName: LString = "",
    /** 'present' | 'absent' | 'duty_leave' | 'not_marked' | other */
    @SerialName("attendance_status") val attendanceStatus: LStringN = null,
    @SerialName("covered_topics") val coveredTopics: List<LString> = emptyList(),
    /** 'substitution' | 'special' | other */
    val change: LStringN = null,
    @SerialName("in_place_of") val inPlaceOf: LStringN = null,
    @SerialName("regular_subject") val regularSubject: LStringN = null,
    val suspended: LBool = false,
    @SerialName("suspension_reason") val suspensionReason: LStringN = null,
) {
    val isDutyLeave: Boolean get() = attendanceStatus == "duty_leave"
    val isSubstitution: Boolean get() = !change.isNullOrBlank()
}

@Serializable
data class DayTimetable(
    val date: LString = "",
    val day: LString = "",
    val sub: List<TimetablePeriod> = emptyList(),
    @SerialName("all_hours_suspended") val allHoursSuspended: LBool = false,
    @SerialName("holiday_name") val holidayName: LStringN = null,
)

@Serializable
data class AttendanceSummary(
    @SerialName("total_classes") val totalClasses: LDouble = 0.0,
    @SerialName("classes_attended") val classesAttended: LDouble = 0.0,
    val percentage: LDouble = 0.0,
)

@Serializable
data class StudentProfile(
    val login: LBool = true,
    val name: LString = "",
    @SerialName("student_id") val studentId: LString = "",
    @SerialName("roll_no") val rollNo: LString = "",
    @SerialName("admission_no") val admissionNo: LString = "",
    @SerialName("register_no") val registerNo: LString = "",
    @SerialName("curnt_sem") val currentSem: LString = "",
    @SerialName("sem_id") val semId: LInt = 0,
    @SerialName("batch_id") val batchId: LInt = 0,
    val course: LString = "",
    val email: LString = "",
    val phone: LString = "",
    val gender: LStringN = null,
    val photo: LStringN = null,
    /** Relative, authenticated photo path (e.g. /api/v1/profile/photos/file/image?v=…). */
    val url: LStringN = null,
    val attendance: AttendanceSummary = AttendanceSummary(),
    val timetable: List<TimetablePeriod> = emptyList(),
    val notices: List<NoticeItem> = emptyList(),
    val circulars: List<CircularItem> = emptyList(),
)

@Serializable
data class SubjectAttendance(
    val key: LString = "",
    val subject: LString = "",
    val code: LString = "",
    @SerialName("display_code") val displayCode: LStringN = null,
    @SerialName("subject_type") val subjectType: LString = "",
    @SerialName("teacher_name") val teacherName: LString = "",
    @SerialName("class_attended") val classAttended: LDouble = 0.0,
    @SerialName("total_classes") val totalClasses: LDouble = 0.0,
    val percentage: LDouble = 0.0,
    @SerialName("absent_hours") val absentHours: LDouble = 0.0,
    /** Missing in the payload means the KTU default of 75%. */
    @SerialName("attendance_target") val attendanceTarget: LDouble = 75.0,
    @SerialName("can_miss_hours") val canMissHours: LDouble = 0.0,
    @SerialName("must_attend_hours") val mustAttendHours: LDouble = 0.0,
) {
    val stableKey: String get() = key.ifBlank { code.ifBlank { subject } }
    val shownCode: String get() = displayCode?.takeIf { it.isNotBlank() } ?: code
}

@Serializable
data class SeriesExam(
    val id: LString = "",
    val subject: LString = "",
    @SerialName("subject_code") val subjectCode: LString = "",
    @SerialName("subject_name") val subjectName: LStringN = null,
    val title: LString = "",
    val date: LString = "",
    /** Hours the exam occupies, e.g. "4,5". */
    val period: LStringN = null,
    @SerialName("maximum_mark") val maximumMark: LDouble = 0.0,
    /** Numeric mark or a status string like "AB"; blank until published. */
    val mark: LString = "",
    val status: LString = "",
    @SerialName("exam_type") val examType: LStringN = null,
) {
    val isPublished: Boolean get() = mark.isNotBlank()
    val isScheduled: Boolean get() = !isPublished && status.equals("Scheduled", ignoreCase = true)
}

@Serializable
data class Assignment(
    val id: LString = "",
    val subject: LString = "",
    @SerialName("subject_code") val subjectCode: LString = "",
    @SerialName("subject_name") val subjectName: LStringN = null,
    @SerialName("subject_type") val subjectType: LStringN = null,
    val title: LString = "",
    val details: LStringN = null,
    @SerialName("issue_date") val issueDate: LStringN = null,
    @SerialName("last_date") val lastDate: LString = "",
    val status: LString = "",
    @SerialName("can_download") val canDownload: LBool = false,
    @SerialName("can_submit") val canSubmit: LBool = false,
    val upload: LBool = false,
    val url: LStringN = null,
    @SerialName("can_delete_submission") val canDeleteSubmission: LBool = false,
    @SerialName("submission_mode") val submissionMode: LStringN = null,
    @SerialName("max_mark") val maxMark: LStringN = null,
    val mark: LStringN = null,
    @SerialName("submission_kinds") val submissionKinds: List<LString> = emptyList(),
) {
    /** Same buckets as the webapp: a mark means Marked; status SUBMITTED means Submitted. */
    val isMarked: Boolean get() = !mark.isNullOrBlank()
    val isSubmitted: Boolean get() = status.trim().equals("SUBMITTED", ignoreCase = true)
    val isOpen: Boolean get() = !isSubmitted && !isMarked
    /** ETLab sends "0" when an assignment carries no marks. */
    val maxMarkOrNull: String? get() = maxMark?.takeIf { it.isNotBlank() && it.toDoubleOrNull() != 0.0 }
}

@Serializable
data class EvaluationsData(
    val login: LBool = true,
    @SerialName("eval_types") val evalTypes: List<LString> = emptyList(),
    @SerialName("series_exams") val seriesExams: List<SeriesExam> = emptyList(),
    @SerialName("module_tests") val moduleTests: List<SeriesExam> = emptyList(),
    val assignments: List<Assignment> = emptyList(),
    val homeworks: List<Assignment> = emptyList(),
    val tutorials: List<Assignment> = emptyList(),
    @SerialName("class_projects") val classProjects: List<Assignment> = emptyList(),
    val seminars: List<Assignment> = emptyList(),
    @SerialName("self_studies") val selfStudies: List<Assignment> = emptyList(),
) {
    /** Assignment-like work (homework, tutorials, projects…) labelled by kind. */
    val otherWork: List<Pair<String, Assignment>>
        get() = homeworks.map { "Homework" to it } + tutorials.map { "Tutorial" to it } +
            classProjects.map { "Project" to it } + seminars.map { "Seminar" to it } +
            selfStudies.map { "Self study" to it }
}

@Serializable
data class Teacher(
    val id: LString = "",
    @SerialName("user_id") val userId: LStringN = null,
    val name: LString = "",
    val email: LStringN = null,
    val phone: LStringN = null,
    val subject: LStringN = null,
    val photo: LStringN = null,
    val designation: LStringN = null,
    @SerialName("is_hod") val isHod: LBool = false,
    @SerialName("is_staff_advisor") val isStaffAdvisor: LBool = false,
    @SerialName("is_mentor") val isMentor: LBool = false,
    @SerialName("role_label") val roleLabel: LStringN = null,
)

@Serializable
data class StudyMaterial(
    val id: LString = "",
    val title: LString = "",
    val details: LStringN = null,
    val subject: LString = "",
    val teacher: LStringN = null,
    @SerialName("file_name") val fileName: LStringN = null,
    val link: LStringN = null,
    val module: LStringN = null,
    val files: List<LString> = emptyList(),
    @SerialName("added_on") val addedOn: LString = "",
)

@Serializable
data class FeeInstallment(
    val name: LString = "",
    val amount: LDouble = 0.0,
    val paid: LDouble = 0.0,
    val status: LStringN = null,
    @SerialName("receipt_no") val receiptNo: LStringN = null,
    @SerialName("due_date") val dueDate: LStringN = null,
)

@Serializable
data class FeeType(
    @SerialName("fee_type_id") val feeTypeId: LInt = 0,
    @SerialName("fee_type_name") val feeTypeName: LString = "",
    val installments: List<FeeInstallment> = emptyList(),
)

@Serializable
data class FeesData(
    val login: LBool = true,
    @SerialName("total_fee") val totalFee: LDouble = 0.0,
    @SerialName("total_paid") val totalPaid: LDouble = 0.0,
    @SerialName("total_balance") val totalBalance: LDouble = 0.0,
    @SerialName("has_due") val hasDue: LBool = false,
    @SerialName("can_pay") val canPay: LBool = false,
    val receipts: List<JsonObject> = emptyList(),
    @SerialName("fee_types") val feeTypes: List<FeeType> = emptyList(),
)

@Serializable
data class ActivityAward(
    val id: LString = "",
    @SerialName("activity_name") val activityName: LString = "",
    val category: LStringN = null,
    val points: LDouble = 0.0,
    val semester: LStringN = null,
    val status: LStringN = null,
    val date: LStringN = null,
)

@Serializable
data class ActivityPointsData(
    val flow: LStringN = null,
    @SerialName("total_points_awarded") val totalPointsAwarded: LDouble = 0.0,
    val awarded: List<ActivityAward> = emptyList(),
)

@Serializable
data class DutyLeave(
    val id: LString = "",
    val reason: LStringN = null,
    @SerialName("from_date") val fromDate: LStringN = null,
    @SerialName("to_date") val toDate: LStringN = null,
    val status: LStringN = null,
    val hours: LDouble = 0.0,
    @SerialName("approved_by") val approvedBy: LStringN = null,
    @SerialName("submitted_on") val submittedOn: LStringN = null,
)

/** A duty leave the student logged locally that ETLab has not recorded yet. */
@Serializable
data class PendingDutyLeave(
    val id: String,
    val reason: String,
    /** yyyy-MM-dd */
    val date: String,
    val endDate: String? = null,
    val hours: Int,
    val selectedHours: List<Int> = emptyList(),
    @SerialName("subject_code") val subjectCode: String? = null,
    @SerialName("subject_name") val subjectName: String? = null,
    val notes: String? = null,
    val createdAt: String,
    val reconciled: Boolean = false,
    val reconciledAt: String? = null,
)

@Serializable
data class SurveyItem(
    val id: LString = "",
    val name: LString = "",
    val subject: LStringN = null,
    @SerialName("teacher_names") val teacherNames: LStringN = null,
    val session: LStringN = null,
    val status: LStringN = null,
    @SerialName("start_date") val startDate: LStringN = null,
    val deadline: LStringN = null,
    @SerialName("is_open") val isOpen: LBool = false,
)

@Serializable
data class CourseGrade(
    @SerialName("course_code") val courseCode: LString = "",
    @SerialName("course_name") val courseName: LString = "",
    val grade: LString = "",
    val credits: LDouble = 0.0,
)

@Serializable
data class UniversityResult(
    @SerialName("exam_id") val examId: LStringN = null,
    @SerialName("exam_name") val examName: LString = "",
    val semester: LStringN = null,
    val sgpa: LStringN = null,
    val cgpa: LStringN = null,
    val results: List<CourseGrade> = emptyList(),
)

@Serializable
data class NoticeItem(
    val id: LString = "",
    val title: LString = "",
    val content: LStringN = null,
    val date: LString = "",
    val source: LStringN = null,
    @SerialName("posted_by") val postedBy: LStringN = null,
    @SerialName("created_at") val createdAt: LStringN = null,
)

@Serializable
data class CircularItem(
    val id: LString = "",
    val title: LString = "",
    val description: LStringN = null,
    val url: LStringN = null,
    val date: LString = "",
)

@Serializable
data class DashboardData(
    val notices: List<NoticeItem> = emptyList(),
    val circulars: List<CircularItem> = emptyList(),
)

@Serializable
data class Course(
    val id: LString = "",
    val name: LString = "",
    val code: LString = "",
    val credits: LDouble = 0.0,
    val type: LString = "",
    @SerialName("type_code") val typeCode: LInt = 0,
    val slot: LStringN = null,
    @SerialName("semester_id") val semesterId: LInt = 0,
    val hours: LInt = 0,
    @SerialName("teacher_name") val teacherName: LStringN = null,
    @SerialName("teacher_id") val teacherId: LStringN = null,
    @SerialName("atten_text") val attenText: LStringN = null,
    @SerialName("atten_per") val attenPer: LDouble = 0.0,
)

@Serializable
data class SyllabusTopic(
    val id: LString = "",
    val module: LInt = 0,
    val topic: LString = "",
    val covered: LBool = false,
    val hours: LDouble = 0.0,
    @SerialName("is_extra") val isExtra: LBool = false,
)

@Serializable
data class CourseOutcome(
    val id: LString = "",
    val code: LString = "",
    val description: LString = "",
    @SerialName("bloom_level") val bloomLevel: LStringN = null,
)

@Serializable
data class CourseExecution(
    val id: LString = "",
    val date: LString = "",
    val hour: LInt = 0,
    val topic: LString = "",
    val status: LString = "",
)

@Serializable
data class SourceBook(
    val id: LString = "",
    val title: LString = "",
    val author: LString = "",
    val publisher: LStringN = null,
    val edition: LStringN = null,
    @SerialName("is_reference") val isReference: LBool = false,
)

@Serializable
data class CourseDetails(
    val id: LString = "",
    val name: LString = "",
    val code: LString = "",
    val syllabus: List<SyllabusTopic> = emptyList(),
    val outcomes: List<CourseOutcome> = emptyList(),
    val execution: List<CourseExecution> = emptyList(),
    @SerialName("source_books") val sourceBooks: List<SourceBook> = emptyList(),
)

@Serializable
data class CourseRisk(
    val code: LString = "",
    val name: LString = "",
    val semester: LString = "",
    @SerialName("attendance_per") val attendancePer: LDouble = 0.0,
    @SerialName("evaluation_avg") val evaluationAvg: LDouble = 0.0,
    /** 1 (low) to 5 (critical) */
    @SerialName("risk_level") val riskLevel: LInt = 1,
    val reasons: List<LString> = emptyList(),
)

@Serializable
data class SemesterAttendance(
    val present: LDouble = 0.0,
    val total: LDouble = 0.0,
    val percent: LDouble = 0.0,
)

@Serializable
data class SemesterAnalysis(
    val position: LInt = 0,
    val semester: LString = "",
    /** Numeric SGPA or a status such as "Awaiting". */
    val sgpa: LString = "",
    val credit: LDouble = 0.0,
    @SerialName("is_passed") val isPassed: LBool = false,
    val result: LString = "",
    val attendance: SemesterAttendance? = null,
    @SerialName("cumulative_cgpa") val cumulativeCgpa: LDouble = 0.0,
    @SerialName("cumulative_credit") val cumulativeCredit: LDouble = 0.0,
    @SerialName("total_backlogs") val totalBacklogs: LInt = 0,
    @SerialName("total_courses") val totalCourses: LInt = 0,
    val subjects: List<AnalysisSubject> = emptyList(),
) {
    /** ETLab reports 0 (or text such as "Awaiting") until results are published. */
    val sgpaValue: Double? get() = sgpa.toDoubleOrNull()?.takeIf { it > 0 }
}

@Serializable
data class SubjectEvaluation(
    val category: LStringN = null,
    val name: LString = "",
    @SerialName("exam_id") val examId: LStringN = null,
    val mark: LDouble = 0.0,
    @SerialName("has_mark") val hasMark: LBool = false,
    @SerialName("maximum_mark") val maximumMark: LDouble = 0.0,
    @SerialName("class_avg") val classAvg: LDouble = 0.0,
    val rank: LInt = 0,
    val date: LStringN = null,
    val status: LStringN = null,
    @SerialName("is_absent") val isAbsent: LBool = false,
)

@Serializable
data class AnalysisSubject(
    @SerialName("subject_name") val subjectName: LString = "",
    @SerialName("subject_full_name") val subjectFullName: LStringN = null,
    val code: LString = "",
    @SerialName("subject_type") val subjectType: LStringN = null,
    val attendance: SemesterAttendance? = null,
    @SerialName("internal_mark") val internalMark: LDouble = 0.0,
    val result: LStringN = null,
    val credit: LDouble = 0.0,
    val evaluations: List<SubjectEvaluation> = emptyList(),
)

@Serializable
data class AnalysisStudent(
    val name: LString = "",
    val admissionNo: LString = "",
    val batch: LString = "",
    @SerialName("current_sem") val currentSem: LString = "",
    val cgpa: LDouble = 0.0,
    @SerialName("cumulative_credit") val cumulativeCredit: LDouble = 0.0,
    @SerialName("total_backlogs") val totalBacklogs: LInt = 0,
    @SerialName("current_backlogs") val currentBacklogs: LInt = 0,
)

@Serializable
data class AcademicAnalysis(
    val name: LString = "",
    val cgpa: LDouble = 0.0,
    @SerialName("earned_credit") val earnedCredit: LDouble = 0.0,
    @SerialName("total_credit") val totalCredit: LDouble = 0.0,
    @SerialName("total_backlogs") val totalBacklogs: LInt = 0,
    @SerialName("total_passed") val totalPassed: LInt = 0,
    @SerialName("total_exams") val totalExams: LInt = 0,
    val student: AnalysisStudent? = null,
    @SerialName("courses_at_risk") val coursesAtRisk: List<CourseRisk> = emptyList(),
    val semesters: List<SemesterAnalysis> = emptyList(),
)
