package me.nimd.etbridge.core.domain

import me.nimd.etbridge.core.model.DayTimetable
import me.nimd.etbridge.core.model.SubjectAttendance
import me.nimd.etbridge.core.model.TimetablePeriod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubjectHistoryTest {
    private val net = SubjectAttendance(subject = "Computer Networks", code = "CST303")

    @Test fun distinctCodesDoNotMatch() {
        assertFalse(SubjectHistory.matches(net, TimetablePeriod(subject = "Computer Networks Lab", subjectCode = "CSL332")))
    }

    @Test fun codeMatchIgnoresPunctuation() {
        assertTrue(SubjectHistory.matches(net, TimetablePeriod(subject = "CN", subjectCode = "CST 303")))
    }

    @Test fun substitutionMatchesRegularSubject() {
        assertTrue(SubjectHistory.matches(net, TimetablePeriod(subject = "Maths", subjectCode = "MAT301", regularSubject = "Computer Networks")))
    }

    @Test fun historySplitsPastAndUpcoming() {
        val days = listOf(
            DayTimetable("2026-11-10", "Tuesday", listOf(TimetablePeriod(hour = 1, subjectCode = "CST303", attendanceStatus = "absent", coveredTopics = listOf("Subnetting")))),
            DayTimetable("2026-11-20", "Friday", listOf(TimetablePeriod(hour = 2, subjectCode = "CST303", attendanceStatus = "not_marked"))),
        )
        val h = SubjectHistory.forSubject(net, days, today = "2026-11-16")
        assertEquals(1, h.absent.size)
        assertEquals(1, h.upcoming.size)
        assertEquals("Subnetting", h.coveredTopics.single().second)
    }
}
