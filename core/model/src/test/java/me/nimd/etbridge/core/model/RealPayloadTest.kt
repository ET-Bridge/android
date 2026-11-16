package me.nimd.etbridge.core.model

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Decodes real ETLab responses saved locally (never committed: they hold personal data).
 * Point ETLAB_FIXTURES at a folder of <endpoint>.json files to run; skipped otherwise.
 */
class RealPayloadTest {
    private val dir = System.getenv("ETLAB_FIXTURES")?.let(::File)

    private fun read(name: String): String? = dir?.resolve("$name.json")?.takeIf { it.exists() }?.readText()

    @Test fun decodesEveryRealPayload() {
        assumeTrue(dir?.isDirectory == true)
        read("profile")?.let { val p = EtlabJson.decodeFromString(StudentProfile.serializer(), it); assertTrue(p.name.isNotBlank()); assertTrue(!p.url.isNullOrBlank()) }
        read("attendancebysubject")?.let { val a = EtlabJson.decodeFromString(ListSerializer(SubjectAttendance.serializer()), it); assertTrue(a.isNotEmpty() && a.all { s -> s.totalClasses > 0 }) }
        read("timetablenew")?.let {
            val list = EtlabJson.parseToJsonElement(it).jsonObject["timetable"]!!
            val days = EtlabJson.decodeFromJsonElement(ListSerializer(DayTimetable.serializer()), list)
            assertTrue(days.isNotEmpty()); assertTrue(days.any { d -> d.holidayName != null })
        }
        read("evaluations")?.let {
            val e = EtlabJson.decodeFromString(EvaluationsData.serializer(), it)
            assertTrue(e.assignments.isNotEmpty()); assertTrue(e.seriesExams.isNotEmpty())
            assertTrue(e.assignments.any { a -> a.isMarked }); assertTrue(e.assignments.any { a -> a.isOpen })
            assertTrue(e.seriesExams.any { s -> s.isScheduled } || e.seriesExams.all { s -> s.isPublished })
        }
        read("teachers")?.let { EtlabJson.decodeFromString(ListSerializer(Teacher.serializer()), it) }
        read("studymaterials")?.let { EtlabJson.decodeFromString(ListSerializer(StudyMaterial.serializer()), it) }
        read("fees")?.let { EtlabJson.decodeFromString(FeesData.serializer(), it) }
        read("activitypoints_legacy")?.let { EtlabJson.decodeFromString(ActivityPointsData.serializer(), it) }
        read("surveys")?.let { EtlabJson.decodeFromString(ListSerializer(SurveyItem.serializer()), it) }
        read("subjects")?.let { val c = EtlabJson.decodeFromString(ListSerializer(Course.serializer()), it); assertTrue(c.all { x -> x.attenPer >= 0 }) }
        read("academicanalysis")?.let {
            val a = EtlabJson.decodeFromString(AcademicAnalysis.serializer(), it)
            assertTrue(a.semesters.flatMap { s -> s.subjects }.any { s -> s.evaluations.any { e -> e.classAvg > 0 } })
        }
        read("syllabus")?.let { EtlabJson.parseToJsonElement(it) as JsonObject }
    }
}
