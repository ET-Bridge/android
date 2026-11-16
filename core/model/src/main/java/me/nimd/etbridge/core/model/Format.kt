package me.nimd.etbridge.core.model

import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.floor

/** Shared JSON configuration for ETLab payloads and local caches. */
val EtlabJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
    explicitNulls = false
    encodeDefaults = true
}

private val YMD = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})")
private val DMY = Regex("^(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})")

/** Port of parseDateSafe: accepts yyyy-MM-dd[ HH:mm:ss] and dd/MM/yyyy (or dd-MM-yyyy). */
fun parseDateSafe(input: String?): LocalDate? {
    val str = input?.trim().orEmpty()
    if (str.isEmpty()) return null
    return runCatching {
        YMD.find(str)?.let { m ->
            val (y, mo, d) = m.destructured
            return LocalDate.of(y.toInt(), mo.toInt(), d.toInt())
        }
        DMY.find(str)?.let { m ->
            val (d, mo, y) = m.destructured
            return LocalDate.of(y.toInt(), mo.toInt(), d.toInt())
        }
        null
    }.getOrNull()
}

enum class DateStyle(internal val pattern: String) {
    /** 31/10/2026 */
    Numeric("dd/MM/yyyy"),
    /** 31 Oct 2026 */
    Short("d MMM yyyy"),
    /** Sat, 31 Oct 2026 */
    DayShort("EEE, d MMM yyyy"),
    /** 31 October 2026 */
    Long("d MMMM yyyy"),
    /** Saturday, 31 October 2026 */
    DayLong("EEEE, d MMMM yyyy"),
    /** 31 Oct */
    DayMonth("d MMM"),
}

private val formatters = DateStyle.entries.associateWith { DateTimeFormatter.ofPattern(it.pattern, Locale.ENGLISH) }

/** Indian date conventions; unparseable strings are returned as-is (like the webapp). */
fun formatIndianDate(input: String?, style: DateStyle = DateStyle.Numeric): String {
    val date = parseDateSafe(input) ?: return input.orEmpty()
    return formatters.getValue(style).format(date)
}

fun formatIndianDate(date: LocalDate, style: DateStyle = DateStyle.Numeric): String =
    formatters.getValue(style).format(date)

data class DaysRemaining(val text: String, val isUrgent: Boolean, val isOverdue: Boolean)

fun daysRemaining(lastDate: String?, today: LocalDate = LocalDate.now()): DaysRemaining {
    if (lastDate.isNullOrBlank()) return DaysRemaining("", isUrgent = false, isOverdue = false)
    val due = parseDateSafe(lastDate)
        ?: return DaysRemaining(formatIndianDate(lastDate), isUrgent = false, isOverdue = false)
    val diff = ChronoUnit.DAYS.between(today, due)
    return when {
        diff < 0 -> DaysRemaining("Overdue", isUrgent = true, isOverdue = true)
        diff == 0L -> DaysRemaining("Due today", isUrgent = true, isOverdue = false)
        diff == 1L -> DaysRemaining("1 day left", isUrgent = true, isOverdue = false)
        else -> DaysRemaining("$diff days left", isUrgent = diff <= 3, isOverdue = false)
    }
}

private val ROMAN = Regex("^(viii|vii|vi|v|iv|iii|ii|i)(?:st|nd|rd|th)?(?:\\s+semester)?", RegexOption.IGNORE_CASE)
private val WORD = Regex("^(first|second|third|fourth|fifth|sixth|seventh|eighth)", RegexOption.IGNORE_CASE)
private val romanMap = mapOf("i" to 1, "ii" to 2, "iii" to 3, "iv" to 4, "v" to 5, "vi" to 6, "vii" to 7, "viii" to 8)
private val wordMap = mapOf(
    "first" to 1, "second" to 2, "third" to 3, "fourth" to 4,
    "fifth" to 5, "sixth" to 6, "seventh" to 7, "eighth" to 8,
)

/** Port of formatSemester: "Ist Semester" -> "Semester 1", "S5" -> "Semester 5". */
fun formatSemester(value: String?, prefix: String = "Semester"): String {
    fun withPrefix(v: String) = if (prefix.isEmpty()) v else "$prefix $v"
    val str = value?.trim().orEmpty()
    if (str.isEmpty()) return withPrefix("1")
    ROMAN.find(str)?.let { m -> romanMap[m.groupValues[1].lowercase()]?.let { return withPrefix(it.toString()) } }
    WORD.find(str)?.let { m -> wordMap[m.groupValues[1].lowercase()]?.let { return withPrefix(it.toString()) } }
    Regex("(\\d+)").find(str)?.let { return withPrefix(it.groupValues[1]) }
    return withPrefix(str.replace(Regex("semester", RegexOption.IGNORE_CASE), "").trim())
}

/** "just now", "15m ago", "2h ago", "3d ago". */
fun formatRelativeTime(epochMillis: Long?, now: Long = System.currentTimeMillis()): String {
    if (epochMillis == null || epochMillis <= 0) return ""
    val sec = (now - epochMillis) / 1000
    return when {
        sec < 60 -> "just now"
        sec < 3600 -> "${sec / 60}m ago"
        sec < 86_400 -> "${sec / 3600}h ago"
        else -> "${sec / 86_400}d ago"
    }
}

/** One decimal place, e.g. 77.4 -> "77.4", 80.0 -> "80.0". */
fun Double.formatPercent(): String = String.format(Locale.ENGLISH, "%.1f", this)

/** Drops a trailing ".0": 3.0 -> "3", 41.5 -> "41.5". */
fun Double.formatCompact(): String =
    if (this == floor(this) && !isInfinite()) toLong().toString() else String.format(Locale.ENGLISH, "%.1f", this)

private val inr = java.text.NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")).apply {
    maximumFractionDigits = 0
}

/** ₹45,000 */
fun Double.formatRupees(): String = inr.format(this)
