package me.nimd.etbridge.core.domain

import me.nimd.etbridge.core.model.DayTimetable
import me.nimd.etbridge.core.model.DutyLeave
import me.nimd.etbridge.core.model.PendingDutyLeave
import me.nimd.etbridge.core.model.SubjectAttendance
import me.nimd.etbridge.core.model.TimetablePeriod
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttendanceCalculatorTest {

    private fun subject(attended: Double, total: Double, code: String = "CST301", name: String = "Automata") =
        SubjectAttendance(subject = name, code = code, classAttended = attended, totalClasses = total)

    private fun pdl(date: String, hours: List<Int>, code: String? = null, reconciled: Boolean = false) =
        PendingDutyLeave(
            id = "p$date$hours", reason = "Fest", date = date, hours = hours.size,
            selectedHours = hours, subjectCode = code, createdAt = "2026-11-01T00:00:00Z", reconciled = reconciled,
        )

    @Test fun safeMarginMatchesWebFormula() {
        // 40/48 = 83.3%. floor(40/0.75 - 48) = floor(5.33) = 5
        val m = AttendanceCalculator.bunkMargin(40.0, 48.0)
        assertTrue(m.isSafe); assertEquals(5, m.canMiss); assertEquals(0, m.mustAttend)
    }

    @Test fun shortageMarginMatchesWebFormula() {
        // 30/45 = 66.7%. ceil((33.75 - 30)/0.25) = 15
        val m = AttendanceCalculator.bunkMargin(30.0, 45.0)
        assertFalse(m.isSafe); assertEquals(15, m.mustAttend); assertEquals(0, m.canMiss)
    }

    @Test fun exactlyAtTargetIsSafeWithZeroMargin() {
        val m = AttendanceCalculator.bunkMargin(30.0, 40.0)
        assertTrue(m.isSafe); assertEquals(0, m.canMiss)
    }

    @Test fun zeroTotalIsNeutral() {
        val p = AttendanceCalculator.project(subject(0.0, 0.0))
        assertEquals(0, p.currentCanMiss); assertEquals(0, p.currentMustAttend)
    }

    @Test fun customTargetIsHonoured() {
        val m = AttendanceCalculator.bunkMargin(40.0, 48.0, target = 85.0)
        assertFalse(m.isSafe)
        // ceil((0.85*48 - 40) / 0.15) = ceil(5.33) = 6
        assertEquals(6, m.mustAttend)
    }

    @Test fun pendingDutyLeaveMatchedViaTimetable() {
        val tt = listOf(
            DayTimetable(
                date = "2026-11-10", day = "Tuesday",
                sub = listOf(
                    TimetablePeriod(hour = 1, subject = "Automata", subjectCode = "CST 301"),
                    TimetablePeriod(hour = 2, subject = "Networks", subjectCode = "CST303"),
                    TimetablePeriod(hour = 3, subject = "Automata Theory", subjectCode = ""),
                ),
            ),
        )
        val p = AttendanceCalculator.project(
            subject(30.0, 45.0), listOf(pdl("2026-11-10", listOf(1, 2, 3))), tt,
        )
        // hour 1 by code, hour 3 by fuzzy name, hour 2 is another subject
        assertEquals(2, p.pendingDlHours)
        assertEquals(32.0, p.projectedAttended)
        assertEquals(7, p.projectedMustAttend) // ceil((33.75 - 32) / 0.25)
    }

    @Test fun pendingFallbackUsesSubjectCodeWhenNoTimetable() {
        val p = AttendanceCalculator.project(subject(30.0, 45.0), listOf(pdl("2026-11-10", emptyList(), "cst-301").copy(hours = 3)), null)
        assertEquals(3, p.pendingDlHours)
    }

    @Test fun reconciledLeavesAreIgnored() {
        val p = AttendanceCalculator.project(subject(30.0, 45.0), listOf(pdl("2026-11-10", emptyList(), "CST301", reconciled = true)), null)
        assertEquals(0, p.pendingDlHours)
    }

    @Test fun projectionNeverExceedsTotal() {
        val p = AttendanceCalculator.project(subject(44.0, 45.0), listOf(pdl("x", emptyList(), "CST301").copy(hours = 5)), null)
        assertEquals(45.0, p.projectedAttended)
    }

    @Test fun reconcilerMatchesOfficialRange() {
        val pending = listOf(pdl("2026-11-10", listOf(1)), pdl("2026-11-20", listOf(1)))
        val official = listOf(DutyLeave(id = "1", fromDate = "2026-11-09 00:00:00", toDate = "2026-11-11"))
        assertEquals(listOf(pending[0].id), DutyLeaveReconciler.findNewlyReconciled(pending, official, emptyList()))
    }

    @Test fun reconcilerMatchesTimetableDutyLeaveHour() {
        val pending = listOf(pdl("2026-11-10", listOf(4)))
        val tt = listOf(DayTimetable(date = "2026-11-10", sub = listOf(TimetablePeriod(hour = 4, attendanceStatus = "duty_leave"))))
        assertEquals(1, DutyLeaveReconciler.findNewlyReconciled(pending, emptyList(), tt).size)
    }

    @Test fun scheduleShowsTomorrowAfterDayEnd() {
        val tt = listOf(
            DayTimetable(date = "2026-11-17", day = "Tuesday", sub = listOf(TimetablePeriod(hour = 2), TimetablePeriod(hour = 1))),
        )
        val f = Schedule.focus(LocalDateTime.of(2026, 11, 16, 17, 0), tt, emptyList(), emptyList())
        assertTrue(f.isTomorrow); assertEquals(1, f.nextPeriod?.hour)
    }

    @Test fun scheduleFindsActiveAndNext() {
        val tt = listOf(DayTimetable(date = "2026-11-16", sub = (1..6).map { TimetablePeriod(hour = it) }))
        val f = Schedule.focus(LocalDateTime.of(2026, 11, 16, 10, 0), tt, emptyList(), emptyList())
        assertEquals(2, f.activePeriod?.hour); assertEquals(3, f.nextPeriod?.hour)
    }
}
