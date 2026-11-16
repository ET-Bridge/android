package me.nimd.etbridge.feature.timetable

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.nimd.etbridge.core.designsystem.EmptyState
import me.nimd.etbridge.core.designsystem.EtMotion
import me.nimd.etbridge.core.designsystem.Pill
import me.nimd.etbridge.core.designsystem.StatusBanner
import me.nimd.etbridge.core.designsystem.Tone
import me.nimd.etbridge.core.domain.Schedule
import me.nimd.etbridge.core.domain.isSubstitutionPeriod
import me.nimd.etbridge.core.model.DateStyle
import me.nimd.etbridge.core.model.formatIndianDate
import me.nimd.etbridge.ui.LocalAppActions
import me.nimd.etbridge.ui.PeriodRow
import me.nimd.etbridge.ui.StudentViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(vm: StudentViewModel = hiltViewModel()) {
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val app = LocalAppActions.current
    val scope = rememberCoroutineScope()
    val today = now.toLocalDate()

    // Every teaching day (Mon–Sat) from ~4 months back to 3 weeks ahead.
    val dates = remember(today) {
        generateSequence(today.minusDays(126)) { it.plusDays(1) }
            .takeWhile { !it.isAfter(today.plusDays(21)) }
            .filter { it.dayOfWeek != DayOfWeek.SUNDAY }
            .toList()
    }
    val todayIndex = remember(dates) { dates.indexOfFirst { !it.isBefore(today) }.coerceAtLeast(0) }
    val pager = rememberPagerState(initialPage = todayIndex) { dates.size }
    val current = dates[pager.currentPage.coerceIn(dates.indices)]

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Timetable")
                        Text(
                            "${current.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${current.year}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage - 6).coerceAtLeast(0)) } }) {
                        Icon(Icons.Rounded.ChevronLeft, "Previous week")
                    }
                    IconButton(onClick = { scope.launch { pager.animateScrollToPage(todayIndex) } }) {
                        Icon(Icons.Rounded.Today, "Today")
                    }
                    IconButton(onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage + 6).coerceAtMost(dates.lastIndex)) } }) {
                        Icon(Icons.Rounded.ChevronRight, "Next week")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize()) {
            StatusBanner(
                app.syncState.error, Modifier.padding(horizontal = 16.dp),
                offline = app.syncState.isOffline, onDismiss = app.dismissSyncError,
            )
            WeekStrip(
                week = Schedule.weekDays(current),
                selected = current,
                today = today,
                hasSubstitution = { d -> Schedule.periodsFor(d, snap.allDays)?.takeIf { it.date == d.toString() }?.sub?.any { it.isSubstitutionPeriod() } == true },
            ) { d ->
                val i = dates.indexOf(d)
                if (i >= 0) scope.launch { pager.animateScrollToPage(i, animationSpec = EtMotion.smooth()) }
            }
            PullToRefreshBox(isRefreshing = app.syncState.isRefreshing, onRefresh = app.refresh, modifier = Modifier.weight(1f)) {
                HorizontalPager(state = pager, beyondViewportPageCount = 1, key = { dates[it].toString() }) { page ->
                    DayPage(dates[page], snap.allDays, snap.profile?.timetable.orEmpty(), now, padding.calculateBottomPadding())
                }
            }
        }
    }
}

@Composable
private fun WeekStrip(
    week: List<LocalDate>,
    selected: LocalDate,
    today: LocalDate,
    hasSubstitution: (LocalDate) -> Boolean,
    onSelect: (LocalDate) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        week.forEach { d ->
            val isSel = d == selected
            val bg by animateColorAsState(
                if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer, EtMotion.smooth(), label = "bg",
            )
            val fg by animateColorAsState(
                if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, EtMotion.smooth(), label = "fg",
            )
            Column(
                Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(bg)
                    .clickable { onSelect(d) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.8f),
                )
                Text(d.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = fg)
                Box(
                    Modifier.padding(top = 3.dp).size(5.dp).clip(CircleShape).background(
                        when {
                            hasSubstitution(d) -> me.nimd.etbridge.core.designsystem.EtTheme.status.warning
                            d == today -> if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                            else -> androidx.compose.ui.graphics.Color.Transparent
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun DayPage(
    date: LocalDate,
    days: List<me.nimd.etbridge.core.model.DayTimetable>,
    profileToday: List<me.nimd.etbridge.core.model.TimetablePeriod>,
    now: java.time.LocalDateTime,
    bottomPadding: androidx.compose.ui.unit.Dp,
) {
    val entry = remember(date, days) { Schedule.periodsFor(date, days) }
    val exact = entry?.date == date.toString()
    val periods = remember(entry, date, profileToday) {
        val list = entry?.sub?.takeIf { it.isNotEmpty() }
            ?: if (date == now.toLocalDate()) profileToday else emptyList()
        Schedule.uniqueByHour(list)
    }
    val isToday = date == now.toLocalDate()
    val activeHour = if (isToday) Schedule.activeHour(now.toLocalTime()) else 0
    val subs = periods.count { it.isSubstitutionPeriod() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomPadding + 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item("head") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatIndianDate(date, DateStyle.DayLong), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (!exact && periods.isNotEmpty()) Pill("Regular schedule", tone = Tone.Neutral)
                if (subs > 0) Pill("$subs substitution${if (subs == 1) "" else "s"}", tone = Tone.Warning)
            }
        }
        if (entry?.allHoursSuspended == true) {
            item("susp") {
                EmptyState(Icons.Rounded.BeachAccess, "No classes", message = "College holiday or institutional suspension.")
            }
        } else if (periods.isEmpty()) {
            item("empty") {
                EmptyState(
                    Icons.Rounded.BeachAccess, "No periods scheduled",
                    message = if (date.isAfter(now.toLocalDate().plusDays(1))) "The timetable for this day isn't published yet." else "Enjoy your day off!",
                )
            }
        } else {
            periods.forEach { p ->
                item(p.hour) { PeriodRow(p, isActive = p.hour == activeHour && activeHour != 0, showStatus = !date.isAfter(now.toLocalDate())) }
            }
            item("foot") { Spacer(Modifier.height(8.dp)) }
        }
    }
}
