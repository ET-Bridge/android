package me.nimd.etbridge.core.domain

import me.nimd.etbridge.core.model.DayTimetable
import me.nimd.etbridge.core.model.SubjectAttendance
import me.nimd.etbridge.core.model.TimetablePeriod

enum class PeriodStatus { Present, Absent, DutyLeave, Suspended, Other }

fun TimetablePeriod.status(): PeriodStatus {
    if (suspended) return PeriodStatus.Suspended
    val s = attendanceStatus.orEmpty().lowercase().trim()
    return when {
        s == "p" || s.contains("present") -> PeriodStatus.Present
        s == "a" || s.contains("absent") -> PeriodStatus.Absent
        s == "dl" || s.contains("duty") -> PeriodStatus.DutyLeave
        s.contains("suspend") || s.contains("holiday") -> PeriodStatus.Suspended
        else -> PeriodStatus.Other
    }
}

data class ClassRecord(val date: String, val day: String, val period: TimetablePeriod) {
    val status: PeriodStatus get() = period.status()
}

data class SubjectHistoryResult(
    val past: List<ClassRecord>,
    val upcoming: List<ClassRecord>,
) {
    val absent get() = past.filter { it.status == PeriodStatus.Absent }
    val present get() = past.filter { it.status == PeriodStatus.Present }
    val dutyLeave get() = past.filter { it.status == PeriodStatus.DutyLeave }
    /** Topics taught so far, most recent first, de-duplicated. */
    val coveredTopics: List<Pair<String, String>>
        get() = past.flatMap { r -> r.period.coveredTopics.filter { it.isNotBlank() }.map { r.date to it } }
            .distinctBy { it.second.lowercase() }
}

data class DaySummary(val date: String, val present: Int, val absent: Int, val dutyLeave: Int, val suspended: Boolean) {
    val marked: Int get() = present + absent + dutyLeave
}

/** Port of SubjectAttendanceModal's matching: which timetable periods belong to a subject. */
object SubjectHistory {

    private fun cleanCode(v: String?) = v.orEmpty().filter { it.isLetterOrDigit() }.uppercase()

    private fun normalize(v: String?) = v.orEmpty().lowercase()
        .replace("&", " and ")
        .replace(Regex("[^a-z0-9]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun matches(subject: SubjectAttendance, period: TimetablePeriod): Boolean {
        val tCode = cleanCode(subject.code.ifBlank { subject.displayCode })
        val tName = normalize(subject.subject)
        val pCode = cleanCode(period.subjectCode)
        val pName = normalize(period.subject)
        val pRegular = normalize(period.regularSubject)

        if (tCode.isNotEmpty() && pCode.isNotEmpty()) {
            if (tCode == pCode) return true
            if (tCode.length >= 4 && pCode.length >= 4 && (tCode.contains(pCode) || pCode.contains(tCode))) return true
            if (tCode.length >= 5 && pCode.length >= 5) {
                return tName.isNotEmpty() && pRegular.isNotEmpty() && (tName == pRegular || pRegular.contains(tName))
            }
        }
        if (tName.isNotEmpty() && pName.isNotEmpty()) {
            if (tName == pName) return true
            if ((pCode.isEmpty() || tCode.isEmpty()) && tName.length > 5 && pName.length > 5 &&
                (tName.contains(pName) || pName.contains(tName))
            ) return true
            if (tCode.isEmpty() || pCode.isEmpty() || tCode == pCode) {
                val tw = tName.split(' ').filter { it.length > 2 }
                val pw = pName.split(' ').filter { it.length > 2 }
                if (tw.isNotEmpty() && pw.isNotEmpty()) {
                    val overlap = tw.count { it in pw }
                    if (overlap.toDouble() / maxOf(tw.size, pw.size) >= 0.7) return true
                }
            }
        }
        if (tName.isNotEmpty() && pRegular.isNotEmpty()) {
            if (tName == pRegular || tName.contains(pRegular) || pRegular.contains(tName)) return true
        }
        return false
    }

    fun forSubject(subject: SubjectAttendance, days: List<DayTimetable>, today: String): SubjectHistoryResult {
        val records = days.distinctBy { it.date }.flatMap { d ->
            d.sub.filter { matches(subject, it) }.map { ClassRecord(d.date, d.day, it) }
        }.sortedWith(compareByDescending<ClassRecord> { it.date }.thenBy { it.period.hour })
        val past = records.filter { it.date < today || (it.date == today && it.status != PeriodStatus.Other) }
        val upcoming = records.filter { it !in past }.sortedWith(compareBy<ClassRecord> { it.date }.thenBy { it.period.hour })
        return SubjectHistoryResult(past, upcoming)
    }

    fun daySummaries(days: List<DayTimetable>): Map<String, DaySummary> = days.distinctBy { it.date }.associate { d ->
        val periods = d.sub
        d.date to DaySummary(
            date = d.date,
            present = periods.count { it.status() == PeriodStatus.Present },
            absent = periods.count { it.status() == PeriodStatus.Absent },
            dutyLeave = periods.count { it.status() == PeriodStatus.DutyLeave },
            suspended = d.allHoursSuspended || (periods.isNotEmpty() && periods.all { it.suspended }),
        )
    }
}
