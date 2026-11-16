package me.nimd.etbridge.core.model

import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LenientTest {
    @Test fun numbersAsStringsAndPercentages() {
        val s = EtlabJson.decodeFromString(
            SubjectAttendance.serializer(),
            """{"subject":"X","code":12,"class_attended":"40","total_classes":48.0,"percentage":"83.3%","attendance_target":null,"extra":{"a":1}}""",
        )
        assertEquals("12", s.code)
        assertEquals(40.0, s.classAttended)
        assertEquals(83.3, s.percentage)
        assertEquals(75.0, s.attendanceTarget)
    }

    @Test fun garbageNumbersFallBackToZero() {
        val s = EtlabJson.decodeFromString(SubjectAttendance.serializer(), """{"class_attended":"N/A","total_classes":{}}""")
        assertEquals(0.0, s.classAttended); assertEquals(0.0, s.totalClasses)
    }

    @Test fun booleansFromNumbers() {
        val t = EtlabJson.decodeFromString(Teacher.serializer(), """{"name":"A","is_hod":1,"is_mentor":"false","phone":9876543210}""")
        assertTrue(t.isHod); assertEquals("9876543210", t.phone)
    }

    @Test fun nullListsAndObjectsUseDefaults() {
        val p = EtlabJson.decodeFromString(StudentProfile.serializer(), """{"name":"A","timetable":null,"attendance":null,"photo":null}""")
        assertTrue(p.timetable.isEmpty()); assertNull(p.photo); assertEquals(0.0, p.attendance.percentage)
    }

    @Test fun parseFloatPrefixLikeJs() {
        assertEquals(41.5, parseLenientDouble("41.5 marks"))
    }

    @Test fun semesterFormatting() {
        assertEquals("Semester 1", formatSemester("Ist Semester"))
        assertEquals("Sem 5", formatSemester("S5", "Sem"))
        assertEquals("Semester 3", formatSemester("Third"))
    }

    @Test fun dates() {
        assertEquals("31/10/2026", formatIndianDate("2026-10-31 10:00:00"))
        assertEquals("31 Oct 2026", formatIndianDate("31/10/2026", DateStyle.Short))
    }

    @Test fun decodesDemoTimetableShape() {
        val list = EtlabJson.decodeFromString(
            ListSerializer(DayTimetable.serializer()),
            """[{"date":"2026-11-02","day":"Monday","sub":[{"hour":"1","subject":"A","subject_code":"C","teacher_name":"T","covered_topics":["x",2]}]}]""",
        )
        assertEquals(1, list[0].sub[0].hour); assertEquals(listOf("x", "2"), list[0].sub[0].coveredTopics)
    }
}
