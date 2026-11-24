package me.nimd.etbridge.core.domain

import me.nimd.etbridge.core.model.AttendanceRecord
import me.nimd.etbridge.core.model.DayTimetable
import me.nimd.etbridge.core.model.TimetablePeriod
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Overlays ETLab's attendance records (POST /attendancebyday) onto the scheduled timetable
 * (/timetablenew). Port of webapp/src/api/attendanceMerge.ts.
 *
 * /timetablenew is the *planned* timetable: "Minor" slots, lab blocks and swapped hours show
 * the scheduled subject, and many marked hours come back as "N/A". The attendance records say
 * which subject was actually taught and how it was marked, so for every marked hour they
 * replace the scheduled period's subject, code, type, status and topics. Unmarked hours keep
 * the timetable entry (with its substitution/suspension info); marked hours with no timetable
 * slot are added.
 */
object AttendanceMerge {

    /** Maps a record to the timetable's attendance_status vocabulary (mirrors ETLab's client). */
    fun status(r: AttendanceRecord): String {
        val status = r.status.trim().lowercase()
        val type = r.attendanceType.orEmpty().trim().lowercase()
        return when {
            status.contains("duty") || status == "on duty" || type.contains("duty") -> "duty_leave"
            status == "present" || status == "p" -> "present"
            status == "absent" || status == "a" -> "absent"
            r.countsAsPresentOrNull != null -> if (r.countsAsPresentOrNull == true) "present" else "absent"
            status.isNotEmpty() -> status
            else -> "not_marked"
        }
    }

    private fun cleanCode(v: String?) = v.orEmpty().filter { it.isLetterOrDigit() }.uppercase()

    private fun typeFor(r: AttendanceRecord) =
        if (r.subjectType.orEmpty().contains("practical", ignoreCase = true)) "P" else "T"

    fun merge(timetable: List<DayTimetable>, days: Map<String, List<AttendanceRecord>>?): List<DayTimetable> {
        if (days.isNullOrEmpty()) return timetable
        val byDate = LinkedHashMap<String, DayTimetable>()
        timetable.forEach { if (it.date.isNotBlank()) byDate[it.date] = it }

        for ((date, records) in days) {
            if (records.isEmpty()) continue
            val existing = byDate[date]
            val periods = existing?.sub.orEmpty().toMutableList()
            val consumed = BooleanArray(periods.size + records.size)

            for (r in records) {
                val hour = r.hour
                if (hour <= 0) continue
                val candidates = periods.indices.filter { periods[it].hour == hour && !consumed[it] }
                val recCode = cleanCode(r.subjectCode)
                val sameCodeIdx = candidates.firstOrNull { i ->
                    val c = cleanCode(periods[i].subjectCode)
                    c.isNotEmpty() && c == recCode
                }
                val idx = sameCodeIdx ?: candidates.firstOrNull()
                val subject = r.sub.trim()
                val topics = r.topics
                val status = status(r)

                if (idx != null) {
                    consumed[idx] = true
                    val slot = periods[idx]
                    val sameName = subject.equals(slot.subject.trim(), ignoreCase = true)
                    val changed = sameCodeIdx == null && !sameName
                    periods[idx] = if (changed) {
                        // The timetable's teacher and substitution notes describe the scheduled class.
                        slot.copy(
                            subject = subject.ifBlank { slot.subject },
                            subjectCode = r.subjectCode,
                            subjectType = r.subjectType ?: slot.subjectType,
                            type = typeFor(r),
                            teacherName = "",
                            attendanceStatus = status,
                            coveredTopics = topics,
                            change = null,
                            inPlaceOf = null,
                            regularSubject = null,
                            suspended = false,
                            suspensionReason = null,
                        )
                    } else {
                        slot.copy(
                            subject = subject.ifBlank { slot.subject },
                            subjectCode = r.subjectCode.ifBlank { slot.subjectCode },
                            subjectType = r.subjectType ?: slot.subjectType,
                            attendanceStatus = status,
                            // Same class: keep the timetable's topic list, which splits cleanly.
                            coveredTopics = slot.coveredTopics.ifEmpty { topics },
                            suspended = false,
                            suspensionReason = null,
                        )
                    }
                } else {
                    periods += TimetablePeriod(
                        hour = hour,
                        date = date,
                        day = existing?.day,
                        type = typeFor(r),
                        subjectType = r.subjectType,
                        subject = subject,
                        subjectCode = r.subjectCode,
                        teacherName = "",
                        attendanceStatus = status,
                        coveredTopics = topics,
                    )
                    consumed[periods.lastIndex] = true
                }
            }

            val day = existing?.day?.takeIf { it.isNotBlank() } ?: runCatching {
                LocalDate.parse(date).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            }.getOrDefault("")
            byDate[date] = (existing ?: DayTimetable(date = date, day = day)).copy(
                day = day,
                sub = periods.sortedBy { it.hour },
            )
        }
        return byDate.values.sortedBy { it.date }
    }
}
