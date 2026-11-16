package me.nimd.etbridge.core.domain

import me.nimd.etbridge.core.model.AttendanceSummary
import me.nimd.etbridge.core.model.DayTimetable
import me.nimd.etbridge.core.model.PendingDutyLeave
import me.nimd.etbridge.core.model.SubjectAttendance
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Port of SubjectProjection in webapp/src/utils/attendance.ts. */
data class SubjectProjection(
    val currentAttended: Double,
    val totalClasses: Double,
    val currentPercentage: Double,
    val pendingDlHours: Int,
    val projectedAttended: Double,
    val projectedPercentage: Double,
    val currentCanMiss: Int,
    val currentMustAttend: Int,
    val projectedCanMiss: Int,
    val projectedMustAttend: Int,
    val isCurrentlySafe: Boolean,
    val isProjectedSafe: Boolean,
    val target: Double,
) {
    val hasPending: Boolean get() = pendingDlHours > 0
}

data class BunkMargin(val canMiss: Int, val mustAttend: Int, val isSafe: Boolean)

object AttendanceCalculator {

    const val DEFAULT_TARGET = 75.0

    private fun clean(s: String?): String = s.orEmpty().filter { it.isLetterOrDigit() }.lowercase()

    /**
     * How many lecture hours `attended / total` allows you to skip (when at or above `target`)
     * or must attend consecutively (when below it).
     *
     * canMiss    = floor(P / t - T)
     * mustAttend = ceil((t·T - P) / (1 - t))
     */
    fun bunkMargin(attended: Double, total: Double, target: Double = DEFAULT_TARGET): BunkMargin {
        if (total <= 0) return BunkMargin(0, 0, isSafe = true)
        val t = target / 100.0
        val pct = attended / total * 100.0
        val safe = pct >= target
        return if (safe) {
            BunkMargin(max(0, floor(attended / t - total).toInt()), 0, isSafe = true)
        } else {
            BunkMargin(0, max(0, ceil((t * total - attended) / (1 - t)).toInt()), isSafe = false)
        }
    }

    /**
     * Pending (self-reported, un-reconciled) duty-leave hours that apply to [subject].
     * Matches each selected hour of a pending leave against that date's timetable period;
     * without timetable data, falls back to the leave's own subject code.
     */
    fun pendingDlHoursForSubject(
        subject: SubjectAttendance,
        pending: List<PendingDutyLeave>,
        timetable: List<DayTimetable>?,
    ): Int {
        if (pending.isEmpty()) return 0
        val targetCode = clean(subject.code.ifBlank { subject.displayCode })
        val targetName = clean(subject.subject)
        var matched = 0

        for (pdl in pending) {
            if (pdl.reconciled) continue
            val day = timetable?.firstOrNull { it.date == pdl.date }
            if (day != null && pdl.selectedHours.isNotEmpty()) {
                for (h in pdl.selectedHours) {
                    val period = day.sub.firstOrNull { it.hour == h } ?: continue
                    val pCode = clean(period.subjectCode)
                    val pName = clean(period.subject)
                    val codeMatch = targetCode.isNotEmpty() && pCode.isNotEmpty() && targetCode == pCode
                    val nameMatch = targetName.isNotEmpty() && pName.isNotEmpty() &&
                        (targetName == pName || targetName.contains(pName) || pName.contains(targetName))
                    if (codeMatch || nameMatch) matched++
                }
            } else if (!pdl.subjectCode.isNullOrBlank() && targetCode.isNotEmpty() && clean(pdl.subjectCode) == targetCode) {
                matched += if (pdl.hours > 0) pdl.hours else 1
            }
        }
        return matched
    }

    fun project(
        subject: SubjectAttendance,
        pending: List<PendingDutyLeave> = emptyList(),
        timetable: List<DayTimetable>? = null,
        overrideTarget: Double? = null,
    ): SubjectProjection {
        val attended = subject.classAttended
        val total = subject.totalClasses
        val target = overrideTarget ?: subject.attendanceTarget.takeIf { it > 0 } ?: DEFAULT_TARGET
        val currentPct = if (total > 0) attended / total * 100 else subject.percentage

        val dl = pendingDlHoursForSubject(subject, pending, timetable)
        val projectedAttended = if (total > 0) min(total, attended + dl) else attended + dl
        val projectedPct = if (total > 0) projectedAttended / total * 100 else currentPct

        val current = bunkMargin(attended, total, target)
        val projected = bunkMargin(projectedAttended, total, target)

        return SubjectProjection(
            currentAttended = attended,
            totalClasses = total,
            currentPercentage = currentPct,
            pendingDlHours = dl,
            projectedAttended = projectedAttended,
            projectedPercentage = projectedPct,
            currentCanMiss = current.canMiss,
            currentMustAttend = current.mustAttend,
            projectedCanMiss = projected.canMiss,
            projectedMustAttend = projected.mustAttend,
            isCurrentlySafe = currentPct >= target,
            isProjectedSafe = projectedPct >= target,
            target = target,
        )
    }
}

/** Overall attendance across subjects, as shown on the dashboard. */
data class OverallAttendance(
    val attended: Double,
    val total: Double,
    val percentage: Double,
    val projectedPercentage: Double,
    val pendingDlHours: Int,
    val margin: BunkMargin,
) {
    companion object {
        fun from(summary: AttendanceSummary?, pendingDlHours: Int): OverallAttendance {
            val attended = summary?.classesAttended ?: 0.0
            val total = summary?.totalClasses ?: 0.0
            val pct = summary?.percentage?.takeIf { it > 0 } ?: if (total > 0) attended / total * 100 else 0.0
            val projected = if (total > 0) min(100.0, (attended + pendingDlHours) / total * 100) else pct
            return OverallAttendance(
                attended = attended,
                total = total,
                percentage = pct,
                projectedPercentage = projected,
                pendingDlHours = pendingDlHours,
                margin = AttendanceCalculator.bunkMargin(attended, total),
            )
        }
    }
}
