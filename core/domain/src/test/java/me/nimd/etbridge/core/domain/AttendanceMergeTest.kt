package me.nimd.etbridge.core.domain

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import me.nimd.etbridge.core.model.AttendanceRecord
import me.nimd.etbridge.core.model.DayTimetable
import me.nimd.etbridge.core.model.EtlabJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Shapes mirror real /timetablenew and /attendancebyday payloads (names are made up). */
class AttendanceMergeTest {

    private val timetable = EtlabJson.decodeFromString(
        ListSerializer(DayTimetable.serializer()),
        """
        [{"date":"2026-11-02","day":"Monday","sub":[
          {"hour":1,"type":"T","subject":"Maths","subject_code":"MAT101","teacher_name":"T Maths","attendance_status":"N/A","covered_topics":["Limits, continuity"]},
          {"hour":2,"type":"T","subject":"Physics","subject_code":"PHY121","teacher_name":"T Physics","attendance_status":"N/A"},
          {"hour":3,"type":"Free","subject":"Physics","subject_code":"PHY121","teacher_name":"T Physics","attendance_status":"N/A"},
          {"hour":5,"type":"T","subject":"Electrical","subject_code":"EST104","teacher_name":"T Elec","attendance_status":"N/A",
           "change":"substitution","in_place_of":"T Other","regular_subject":"Graphics"},
          {"hour":6,"type":"M","subject":"Minor","attendance_status":"present"}
        ]},
        {"date":"2026-11-03","day":"Tuesday","sub":[
          {"hour":1,"type":"T","subject":"Graphics","subject_code":"GRA103","teacher_name":"T Gr","attendance_status":"N/A"}
        ]}]
        """.trimIndent(),
    )

    private val days = EtlabJson.decodeFromString(
        MapSerializer(String.serializer(), ListSerializer(AttendanceRecord.serializer())),
        """
        {"2026-11-02":[
          {"subject_type":"Theory","subject_code":"MAT101","hour":1,"sub":"Maths","status":"Absent","counts_as_present":false,
           "attendance_type":"Regular","source":"attendance","covered_topics":"Limits, continuity"},
          {"subject_type":"Theory","subject_code":"MAT101","hour":2,"sub":"Maths","status":"Present","counts_as_present":true},
          {"subject_type":"Practical","subject_code":"PYT105","hour":3,"sub":"Python ","status":"Duty Leave","counts_as_present":true},
          {"subject_type":"Theory","subject_code":"EST104","hour":5,"sub":"Electrical","status":"Present"},
          {"subject_type":"Theory","subject_code":"LSC128","hour":6,"sub":"Life Skills","status":"Absent","covered_topics":"Resume, Interview"}
        ],
        "2026-11-07":[
          {"subject_type":"Theory","subject_code":"GRA103","hour":1,"sub":"Graphics","status":"Absent"}
        ]}
        """.trimIndent(),
    )

    private val merged = AttendanceMerge.merge(timetable, days)
    private fun period(date: String, hour: Int) = merged.single { it.date == date }.sub.single { it.hour == hour }

    @Test fun sameSubjectKeepsTeacherAndTopicArray() {
        val p = period("2026-11-02", 1)
        assertEquals("absent", p.attendanceStatus)
        assertEquals("T Maths", p.teacherName)
        assertEquals(listOf("Limits, continuity"), p.coveredTopics) // not split on the comma
    }

    @Test fun swappedHourShowsTaughtSubject() {
        val p = period("2026-11-02", 2)
        assertEquals("MAT101", p.subjectCode)
        assertEquals("Maths", p.subject)
        assertEquals("present", p.attendanceStatus)
        assertEquals("", p.teacherName) // the timetable teacher belonged to Physics
    }

    @Test fun labSlotUsedForAnotherSubjectAndDutyLeave() {
        val p = period("2026-11-02", 3)
        assertEquals("PYT105", p.subjectCode)
        assertEquals("Python", p.subject)
        assertEquals("duty_leave", p.attendanceStatus)
        assertTrue(p.isDutyLeave)
        assertEquals("P", p.type)
    }

    @Test fun substitutionInfoKeptWhenSubjectMatches() {
        val p = period("2026-11-02", 5)
        assertEquals("substitution", p.change)
        assertEquals("Graphics", p.regularSubject)
    }

    @Test fun minorSlotReplacedAndStaleSubstitutionCleared() {
        val p = period("2026-11-02", 6)
        assertEquals("Life Skills", p.subject)
        assertEquals("absent", p.attendanceStatus)
        assertEquals(listOf("Resume", "Interview"), p.coveredTopics)
        assertNull(p.regularSubject)
    }

    @Test fun workingSaturdayMissingFromTimetableIsAdded() {
        val day = merged.single { it.date == "2026-11-07" }
        assertEquals("Saturday", day.day)
        assertEquals("GRA103", day.sub.single().subjectCode)
    }

    @Test fun unmarkedDaysUntouched() {
        assertEquals("N/A", period("2026-11-03", 1).attendanceStatus)
    }

    @Test fun dayCountsMatchRecords() {
        val s = SubjectHistory.daySummaries(merged).getValue("2026-11-02")
        assertEquals(2, s.present)
        assertEquals(2, s.absent)
        assertEquals(1, s.dutyLeave)
    }

    @Test fun noRecordsReturnsTimetable() {
        assertEquals(timetable, AttendanceMerge.merge(timetable, emptyMap()))
    }
}
