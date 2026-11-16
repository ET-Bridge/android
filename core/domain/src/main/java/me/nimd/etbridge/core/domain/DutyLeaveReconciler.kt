package me.nimd.etbridge.core.domain

import me.nimd.etbridge.core.model.DayTimetable
import me.nimd.etbridge.core.model.DutyLeave
import me.nimd.etbridge.core.model.PendingDutyLeave

/**
 * Port of the auto-reconciliation effect in webapp/src/context/AuthContext.tsx: a pending
 * duty leave is considered officially recorded once ETLab lists a duty leave whose date
 * range covers it, or the timetable marks a matching period as `duty_leave`.
 */
object DutyLeaveReconciler {

    private fun clean(s: String?) = s.orEmpty().filter { it.isLetterOrDigit() }.uppercase()

    fun isOfficiallyRecorded(
        pending: PendingDutyLeave,
        official: List<DutyLeave>,
        timetable: List<DayTimetable>,
    ): Boolean {
        val inOfficialList = official.any { dl ->
            val from = dl.fromDate?.substringBefore(' ')?.takeIf { it.isNotBlank() } ?: return@any false
            val to = dl.toDate?.substringBefore(' ')?.takeIf { it.isNotBlank() } ?: from
            pending.date in from..to
        }
        if (inOfficialList) return true

        return timetable.any { day ->
            day.date == pending.date && day.sub.any { period ->
                if (!period.isDutyLeave) return@any false
                if (pending.selectedHours.isNotEmpty() && period.hour in pending.selectedHours) return@any true
                if (pending.subjectCode.isNullOrBlank()) return@any true
                clean(period.subjectCode) == clean(pending.subjectCode)
            }
        }
    }

    /** Returns the ids of pending leaves that should now be marked reconciled. */
    fun findNewlyReconciled(
        pending: List<PendingDutyLeave>,
        official: List<DutyLeave>,
        timetable: List<DayTimetable>,
    ): List<String> = pending
        .filter { !it.reconciled && isOfficiallyRecorded(it, official, timetable) }
        .map { it.id }
}
