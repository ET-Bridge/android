package me.nimd.etbridge.core.data.network

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import me.nimd.etbridge.core.model.AcademicAnalysis
import me.nimd.etbridge.core.model.ActivityPointsData
import me.nimd.etbridge.core.model.Course
import me.nimd.etbridge.core.model.CourseDetails
import me.nimd.etbridge.core.model.CourseRisk
import me.nimd.etbridge.core.model.DayTimetable
import me.nimd.etbridge.core.model.DutyLeave
import me.nimd.etbridge.core.model.EvaluationsData
import me.nimd.etbridge.core.model.EtlabJson
import me.nimd.etbridge.core.model.FeesData
import me.nimd.etbridge.core.model.Institute
import me.nimd.etbridge.core.model.StudentProfile
import me.nimd.etbridge.core.model.StudyMaterial
import me.nimd.etbridge.core.model.SubjectAttendance
import me.nimd.etbridge.core.model.SurveyItem
import me.nimd.etbridge.core.model.SyllabusTopic
import me.nimd.etbridge.core.model.Teacher
import me.nimd.etbridge.core.model.UniversityResult
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

class ApiException(val status: Int, message: String) : Exception(message) {
    val isUnauthorized: Boolean get() = status == 401 || status == 403
}

data class LoginResult(val token: String, val name: String?, val photo: String?)

/**
 * Port of EtlabApiClient's network calls (webapp/src/api/client.ts), talking to ETLab directly.
 * Responses are read as JsonElement first so non-array/odd payloads degrade to empty lists
 * instead of crashing, matching the webapp's `Array.isArray(...) ? ... : []` guards.
 */
@Singleton
class EtlabApi @Inject constructor(private val client: HttpClient) {

    private val base = EtlabEndpoints.API_BASE

    private suspend fun getJson(path: String, block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {}): JsonElement {
        val res = client.get("$base$path", block)
        return res.requireJson(path)
    }

    private suspend fun HttpResponse.requireJson(what: String): JsonElement {
        if (!status.isSuccess()) throw ApiException(status.value, "Failed to load $what (${status.value})")
        val text = bodyAsText()
        return runCatching { EtlabJson.parseToJsonElement(text) }
            .getOrElse { throw ApiException(status.value, "Unexpected response from ETLab for $what") }
    }

    private fun <T> JsonElement.listOf(serializer: KSerializer<T>): List<T> =
        if (this is JsonArray) mapNotNull { runCatching { EtlabJson.decodeFromJsonElement(serializer, it) }.getOrNull() }
        else emptyList()

    private fun <T> JsonElement.decode(serializer: KSerializer<T>): T = EtlabJson.decodeFromJsonElement(serializer, this)

    private fun JsonObject.str(vararg keys: String): String? = keys.firstNotNullOfOrNull { k ->
        (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
    }

    // --- Auth -------------------------------------------------------------------------------

    suspend fun institutes(): List<Institute> =
        getJson("/institutes") { header(EtlabHeadersInterceptor.SKIP_AUTH, "1") }.listOf(Institute.serializer())

    suspend fun login(username: String, password: String, instituteId: String): LoginResult {
        val res = client.post("$base/login") {
            header(EtlabHeadersInterceptor.SKIP_AUTH, "1")
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("username", username.trim())
                put("password", password)
                put("institute_id", instituteId)
            }.toString())
        }
        val text = res.bodyAsText()
        val json = runCatching { EtlabJson.parseToJsonElement(text).jsonObject }.getOrNull()
        if (!res.status.isSuccess()) {
            val msg = json?.str("error", "message") ?: "Server returned error HTTP ${res.status.value}"
            throw ApiException(res.status.value, msg)
        }
        val token = json?.str("token")
        val ok = (json?.get("login") as? JsonPrimitive)?.let { it.booleanOrNull ?: (it.contentOrNull == "1") } ?: false
        if (json == null || token == null || !ok) {
            throw ApiException(401, json?.str("error") ?: "Invalid username or password.")
        }
        val student = json["student"] as? JsonObject
        return LoginResult(
            token = token,
            // ETLab returns profile_name and a relative photo path in `url`.
            name = json.str("profile_name", "name")?.collapseSpaces() ?: student?.str("name"),
            photo = (json.str("photo", "image", "student_photo", "url") ?: student?.str("photo", "image"))
                ?.let(EtlabEndpoints::absolute),
        )
    }

    // --- Student data -----------------------------------------------------------------------

    suspend fun profile(): StudentProfile {
        val raw = getJson("/profile").jsonObject
        val student = raw["student"] as? JsonObject
        val profile = raw.decode(StudentProfile.serializer())
        // Same photo-field fallbacks and nested attendance as getProfile() in client.ts
        val photo = (
            raw.str("photo", "image", "student_photo", "profile_photo", "avatar", "url")
                ?: student?.str("photo", "image", "student_photo", "avatar", "url")
            )?.let(EtlabEndpoints::absolute)
        val attendance = if (raw["attendance"] is JsonObject) profile.attendance
        else (student?.get("attendance") as? JsonObject)?.decode(me.nimd.etbridge.core.model.AttendanceSummary.serializer())
            ?: profile.attendance
        return profile.copy(name = profile.name.collapseSpaces(), photo = photo, attendance = attendance)
    }

    suspend fun attendance(semId: Int? = null): List<SubjectAttendance> =
        getJson("/attendancebysubject") { semId?.let { parameter("sem_id", it) } }
            .listOf(SubjectAttendance.serializer())

    suspend fun monthTimetable(month: YearMonth): List<DayTimetable> {
        val raw = getJson("/timetablenew") { parameter("month", month.toString()) }
        val list = (raw as? JsonObject)?.get("timetable") ?: raw
        return list.listOf(DayTimetable.serializer())
    }

    suspend fun teachers(): List<Teacher> = getJson("/teachers").listOf(Teacher.serializer()).map { t ->
        t.copy(name = t.name.collapseSpaces(), photo = t.photo?.takeIf { it.isNotBlank() }?.let(EtlabEndpoints::absolute))
    }

    suspend fun evaluations(): EvaluationsData = getJson("/evaluations").decode(EvaluationsData.serializer())

    suspend fun studyMaterials(): List<StudyMaterial> =
        getJson("/studymaterials").listOf(StudyMaterial.serializer()).map { m ->
            m.copy(link = m.fileName?.takeIf { it.isNotBlank() }?.let(EtlabEndpoints::absolute))
        }

    suspend fun fees(): FeesData = getJson("/fees").decode(FeesData.serializer())

    suspend fun activityPoints(): ActivityPointsData =
        getJson("/activitypoints/legacy").decode(ActivityPointsData.serializer())

    suspend fun dutyLeaves(): List<DutyLeave> = getJson("/dutyleaves").listOf(DutyLeave.serializer())

    suspend fun surveys(): List<SurveyItem> = getJson("/surveys").listOf(SurveyItem.serializer())

    suspend fun universityResults(): List<UniversityResult> {
        val raw = getJson("/resultsuniversity")
        val list = (raw as? JsonObject)?.get("results") ?: JsonArray(emptyList())
        return list.listOf(UniversityResult.serializer())
    }

    suspend fun courses(): List<Course> = getJson("/subjects").listOf(Course.serializer())

    /**
     * /syllabus/{id} only returns topics. Unlike the webapp we don't pad the result with
     * made-up outcomes or books; missing sections are simply hidden in the UI.
     */
    suspend fun courseDetails(courseId: String): CourseDetails {
        val raw = getJson("/syllabus/$courseId").jsonObject
        return CourseDetails(
            id = courseId,
            name = raw.str("subject", "name") ?: "Course",
            code = raw.str("code").orEmpty(),
            syllabus = (raw["topics"] ?: JsonArray(emptyList())).listOf(SyllabusTopic.serializer()),
        )
    }

    suspend fun academicAnalysis(): AcademicAnalysis {
        val raw = getJson("/academicanalysis")
        val data = raw.decode(AcademicAnalysis.serializer())
        if (data.coursesAtRisk.isNotEmpty()) return data
        return data.copy(coursesAtRisk = deriveRisks(data))
    }

    /** Same heuristic as client.ts when the API doesn't compute courses_at_risk. */
    private fun deriveRisks(data: AcademicAnalysis): List<CourseRisk> = buildList {
        for (sem in data.semesters) for (sub in sem.subjects) {
            val att = sub.attendance?.takeIf { it.total > 0 }?.percent ?: 100.0
            val marked = sub.evaluations.filter { it.hasMark && !it.isAbsent }
            val max = marked.sumOf { if (it.maximumMark > 0) it.maximumMark else 40.0 }
            val evalAvg = if (max > 0) Math.round(marked.sumOf { it.mark } / max * 100).toDouble() else 0.0
            if (att < 75 || (evalAvg > 0 && evalAvg < 40)) {
                add(
                    CourseRisk(
                        code = sub.code.ifBlank { "COURSE" },
                        name = sub.subjectName.trim().ifBlank { sub.subjectFullName ?: sub.code },
                        semester = sem.semester,
                        attendancePer = att,
                        evaluationAvg = evalAvg,
                        riskLevel = if (att < 68) 4 else if (att < 75) 3 else 2,
                        reasons = buildList {
                            if (att < 75) add("Attendance is ${att.toInt()}% (below the 75% minimum)")
                            if (evalAvg > 0 && evalAvg < 40) add("Internal exam average is ${evalAvg.toInt()}%")
                        },
                    ),
                )
            }
        }
    }

    // --- Assignments ------------------------------------------------------------------------

    suspend fun uploadAssignment(assignmentId: String, fileName: String, mimeType: String, bytes: ByteArray) {
        val res = client.post("$base/uploadassignment/$assignmentId") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", bytes, Headers.build {
                            append(HttpHeaders.ContentType, mimeType)
                            append(HttpHeaders.ContentDisposition, "filename=\"${fileName.replace("\"", "")}\"")
                        })
                    },
                ),
            )
        }
        if (!res.status.isSuccess()) throw ApiException(res.status.value, "Upload failed with server status ${res.status.value}")
    }

    suspend fun deleteAssignment(assignmentId: String) {
        val res = client.post("$base/deleteassignment") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("assignment_id", assignmentId) }.toString())
        }
        if (!res.status.isSuccess()) throw ApiException(res.status.value, "Delete submission failed with status ${res.status.value}")
    }
}

private fun String.collapseSpaces(): String = trim().replace(Regex("\\s+"), " ")
