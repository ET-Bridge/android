package me.nimd.etbridge.core.domain

import me.nimd.etbridge.core.model.DayTimetable
import me.nimd.etbridge.core.model.TimetablePeriod
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/** A teaching hour's wall-clock slot. */
data class PeriodSlot(val hour: Int, val start: LocalTime, val end: LocalTime) {
    val label: String get() = "${start.fmt()} – ${end.fmt()}"
    operator fun contains(time: LocalTime): Boolean = !time.isBefore(start) && time.isBefore(end)
}

private fun LocalTime.fmt(): String {
    val h = if (hour % 12 == 0) 12 else hour % 12
    return String.format(Locale.ENGLISH, "%d:%02d %s", h, minute, if (hour < 12) "AM" else "PM")
}

fun TimetablePeriod.isSubstitutionPeriod(): Boolean {
    val ch = change.orEmpty().trim().lowercase()
    return ch == "substitution" || !regularSubject.isNullOrBlank() || !inPlaceOf.isNullOrBlank()
}

fun TimetablePeriod.isSpecialPeriod(): Boolean = change.orEmpty().trim().lowercase() == "special"

/** Which day the dashboard is focusing on and where "now" falls inside it. */
data class ScheduleFocus(
    val date: LocalDate,
    val isTomorrow: Boolean,
    val periods: List<TimetablePeriod>,
    val activePeriod: TimetablePeriod?,
    val nextPeriod: TimetablePeriod?,
    val activeHour: Int,
)

object Schedule {

    /** Standard KTU-college bell schedule used by the webapp (TimetablePage periodTimes). */
    val slots: List<PeriodSlot> = listOf(
        PeriodSlot(1, LocalTime.of(9, 0), LocalTime.of(9, 55)),
        PeriodSlot(2, LocalTime.of(9, 55), LocalTime.of(10, 50)),
        PeriodSlot(3, LocalTime.of(11, 5), LocalTime.of(12, 0)),
        PeriodSlot(4, LocalTime.of(12, 0), LocalTime.of(12, 55)),
        PeriodSlot(5, LocalTime.of(13, 45), LocalTime.of(14, 40)),
        PeriodSlot(6, LocalTime.of(14, 40), LocalTime.of(15, 35)),
    )

    /** College day is considered over after 4:15 PM; the dashboard then shows tomorrow. */
    val dayEnd: LocalTime = LocalTime.of(16, 15)

    fun slotFor(hour: Int): PeriodSlot? = slots.firstOrNull { it.hour == hour }

    fun activeHour(time: LocalTime): Int = slots.firstOrNull { time in it }?.hour ?: 0

    fun dayName(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

    /** Resolves a date's periods: exact date match first, then same weekday in the month data. */
    fun periodsFor(
        date: LocalDate,
        monthly: List<DayTimetable>,
        semester: List<DayTimetable> = emptyList(),
    ): DayTimetable? {
        val iso = date.toString()
        return monthly.firstOrNull { it.date == iso }
            ?: semester.firstOrNull { it.date == iso }
            ?: monthly.firstOrNull { it.day.equals(dayName(date), ignoreCase = true) }
    }

    /** Collapses duplicate hour entries and sorts by hour. */
    fun uniqueByHour(periods: List<TimetablePeriod>): List<TimetablePeriod> =
        periods.distinctBy { it.hour }.sortedBy { it.hour }

    fun focus(
        now: LocalDateTime,
        monthly: List<DayTimetable>,
        semester: List<DayTimetable>,
        profileToday: List<TimetablePeriod>,
    ): ScheduleFocus {
        val today = now.toLocalDate()
        val todayEntry = periodsFor(today, monthly, semester)
        val todayPeriods = todayEntry?.sub?.takeIf { it.isNotEmpty() } ?: profileToday

        val tomorrow = today.plusDays(1)
        val tomorrowEntry = periodsFor(tomorrow, monthly, semester)
        val showTomorrow = !now.toLocalTime().isBefore(dayEnd) && !tomorrowEntry?.sub.isNullOrEmpty()

        val periods = uniqueByHour(if (showTomorrow) tomorrowEntry.sub else todayPeriods)
        val activeHour = if (showTomorrow) 0 else activeHour(now.toLocalTime())
        val active = periods.firstOrNull { it.hour == activeHour && activeHour != 0 }
        val next = if (showTomorrow) {
            periods.firstOrNull()
        } else {
            val t = now.toLocalTime()
            periods.firstOrNull { p ->
                if (activeHour != 0) p.hour > activeHour
                else slotFor(p.hour)?.start?.isAfter(t) ?: (p.hour > 0)
            }
        }
        return ScheduleFocus(
            date = if (showTomorrow) tomorrow else today,
            isTomorrow = showTomorrow,
            periods = periods,
            activePeriod = active,
            nextPeriod = next,
            activeHour = activeHour,
        )
    }

    /** Mon–Sat of the week containing [date], shifted by [weekOffset] weeks. */
    fun weekDays(date: LocalDate, weekOffset: Int = 0): List<LocalDate> {
        val monday = date.plusWeeks(weekOffset.toLong()).with(DayOfWeek.MONDAY)
        return (0L until 6L).map { monday.plusDays(it) }
    }
}
