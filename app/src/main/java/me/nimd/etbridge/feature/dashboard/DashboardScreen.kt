package me.nimd.etbridge.feature.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.rounded.Tune
import me.nimd.etbridge.core.designsystem.IconBubble
import me.nimd.etbridge.navigation.HomeLayoutRoute
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import me.nimd.etbridge.core.designsystem.AttendanceRing
import me.nimd.etbridge.core.designsystem.EtCard
import me.nimd.etbridge.core.designsystem.EtTheme
import me.nimd.etbridge.core.designsystem.InitialsAvatar
import me.nimd.etbridge.core.designsystem.NumberStyle
import me.nimd.etbridge.core.designsystem.Pill
import me.nimd.etbridge.core.designsystem.SkeletonBlock
import me.nimd.etbridge.core.designsystem.StatusBanner
import me.nimd.etbridge.core.designsystem.Tone
import me.nimd.etbridge.core.domain.Schedule
import me.nimd.etbridge.core.domain.ScheduleFocus
import me.nimd.etbridge.core.domain.isSubstitutionPeriod
import me.nimd.etbridge.core.model.DateStyle
import me.nimd.etbridge.core.model.formatIndianDate
import me.nimd.etbridge.core.model.formatPercent
import me.nimd.etbridge.core.model.formatRelativeTime
import me.nimd.etbridge.navigation.AcademicsRoute
import me.nimd.etbridge.navigation.AnalysisRoute
import me.nimd.etbridge.navigation.CampusRoute
import me.nimd.etbridge.navigation.CoursesRoute
import me.nimd.etbridge.navigation.NoticesRoute
import me.nimd.etbridge.navigation.SettingsRoute
import me.nimd.etbridge.ui.HourChip
import me.nimd.etbridge.ui.LocalAppActions
import me.nimd.etbridge.ui.LogDutyLeaveSheet
import me.nimd.etbridge.ui.StudentSnapshot
import me.nimd.etbridge.ui.StudentViewModel
import java.time.Duration
import java.time.LocalDateTime
import me.nimd.etbridge.ui.keyedUniquely

@Composable
fun DashboardScreen(vm: StudentViewModel = hiltViewModel(), layout: HomeLayoutViewModel = hiltViewModel()) {
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val sections by layout.sections.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val app = LocalAppActions.current
    var logging by rememberSaveable { mutableStateOf(false) }

    val focus = remember(snap.timetable, snap.semesterTimetable, snap.profile, now.hour, now.minute) {
        Schedule.focus(now, snap.timetable, snap.semesterTimetable, snap.profile?.timetable.orEmpty())
    }
    val firstLoad = !snap.loaded || (snap.profile == null && snap.attendance == null)

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        PullToRefreshBox(
            isRefreshing = app.syncState.isRefreshing,
            onRefresh = app.refresh,
            modifier = Modifier.padding(top = padding.calculateTopPadding()),
        ) {
            androidx.compose.foundation.lazy.LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 12.dp, bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item("header") { Header(snap, now) { app.navigate(SettingsRoute) } }
                item("banner") {
                    StatusBanner(
                        message = app.syncState.error,
                        offline = app.syncState.isOffline,
                        onDismiss = app.dismissSyncError,
                        actionLabel = if (app.syncState.sessionExpired) "Sign in" else null,
                        onAction = if (app.syncState.sessionExpired) app.logout else null,
                    )
                }
                if (firstLoad) {
                    item("sk1") { SkeletonBlock(height = 190.dp) }
                    item("sk2") { SkeletonBlock(height = 150.dp) }
                    item("sk3") { SkeletonBlock(height = 120.dp) }
                    return@LazyColumn
                }

                val list = sections ?: return@LazyColumn
                val ctx = HomeContext(snap, focus, now, app.navigate) { logging = true }
                list.forEach { section -> with(ctx) { section(section) } }
                item("customize") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        TextButton(onClick = { app.navigate(HomeLayoutRoute) }) {
                            Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Customize Home")
                        }
                    }
                }
            }
        }
    }

    if (logging) {
        LogDutyLeaveSheet(
            days = snap.allDays,
            onDismiss = { logging = false },
            onSave = { reason, date, hours, notes -> vm.logDutyLeave(reason, date, hours, notes = notes) },
        )
    }
}

@Composable
private fun Header(snap: StudentSnapshot, now: LocalDateTime, onAvatar: () -> Unit) {
    val app = LocalAppActions.current
    val name = snap.profile?.name?.takeIf { it.isNotBlank() } ?: "Student"
    val first = name.split(' ').firstOrNull { it.length > 1 }?.lowercase()?.replaceFirstChar { it.uppercase() } ?: name
    val greeting = when (now.hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "$greeting,", style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(first, style = MaterialTheme.typography.headlineLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    formatIndianDate(now.toLocalDate(), DateStyle.DayShort),
                    app.lastSync?.let { "synced ${formatRelativeTime(it)}" },
                    "demo".takeIf { app.isDemo },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(Modifier.clip(CircleShape).clickable(onClick = onAvatar)) {
            val photo = snap.profile?.photo
            if (photo.isNullOrBlank()) {
                InitialsAvatar(name, size = 52.dp)
            } else {
                SubcomposeAsyncImage(
                    model = photo, contentDescription = "Profile", contentScale = ContentScale.Crop,
                    modifier = Modifier.size(52.dp).clip(CircleShape),
                    error = { InitialsAvatar(name, size = 52.dp) },
                    loading = { InitialsAvatar(name, size = 52.dp) },
                )
            }
        }
    }
}

@Composable
internal fun RiskBanner(count: Int, onClick: () -> Unit) {
    val s = EtTheme.status
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(s.dangerContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.WarningAmber, null, tint = s.onDangerContainer)
        Spacer(Modifier.width(10.dp))
        Text(
            "$count subject${if (count == 1) " is" else "s are"} below your attendance target",
            color = s.onDangerContainer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
        )
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = s.onDangerContainer, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun LiveClassCard(focus: ScheduleFocus, now: LocalDateTime, onOpen: () -> Unit) {
    val period = focus.activePeriod ?: focus.nextPeriod
    EtCard(onClick = onOpen, color = MaterialTheme.colorScheme.surfaceContainerHigh, brush = EtTheme.heroBrush) {
        val label = when {
            focus.activePeriod != null -> "Happening now"
            focus.isTomorrow -> "Tomorrow · ${Schedule.dayName(focus.date)}"
            period != null -> "Up next"
            else -> "Today"
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(8.dp).clip(CircleShape).background(
                    if (focus.activePeriod != null) EtTheme.status.safe else MaterialTheme.colorScheme.primary,
                ),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text("Full timetable", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        AnimatedContent(
            targetState = period,
            transitionSpec = { (fadeIn() + slideInVertically { it / 3 }).togetherWith(fadeOut()) },
            label = "period",
        ) { p ->
            if (p == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WbSunny, null, tint = EtTheme.status.warning)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("No more classes", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (focus.periods.isEmpty()) "Nothing scheduled — enjoy the day." else "You're done for today.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Column {
                    Text(
                        p.subject.ifBlank { p.subjectCode }, style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    val slot = Schedule.slotFor(p.hour)
                    Text(
                        listOfNotNull("Hour ${p.hour}", slot?.label, p.teacherName.takeIf { it.isNotBlank() }).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (p.isSubstitutionPeriod()) {
                        Spacer(Modifier.height(6.dp)); Pill("Substitution", tone = Tone.Warning)
                    }
                    if (focus.activePeriod != null && slot != null) {
                        val total = Duration.between(slot.start, slot.end).seconds.toFloat()
                        val done = Duration.between(slot.start, now.toLocalTime()).seconds.toFloat()
                        val left = Duration.between(now.toLocalTime(), slot.end).toMinutes()
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { (done / total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        )
                        Text(
                            "$left min left", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
        if (focus.periods.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(focus.periods.keyedUniquely { "${it.hour}" }, key = { it.first }) { (_, p) ->
                    HourChip(p, active = p.hour == focus.activeHour && focus.activeHour != 0, showStatus = !focus.isTomorrow)
                }
            }
        }
    }
}

@Composable
internal fun OverallCard(snap: StudentSnapshot, onOpen: () -> Unit, onLog: () -> Unit) {
    val o = snap.overall
    val hasPending = o.pendingDlHours > 0
    EtCard(onClick = onOpen, brush = EtTheme.heroBrush) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AttendanceRing(
                percentage = o.percentage,
                projected = if (hasPending) o.projectedPercentage else null,
                target = snap.target,
                size = 112.dp,
                strokeWidth = 11.dp,
                labelStyle = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text("Overall attendance", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${o.percentage.formatPercent()}%",
                    style = MaterialTheme.typography.displaySmall.merge(NumberStyle),
                )
                if (hasPending) {
                    Text(
                        "→ ${o.projectedPercentage.formatPercent()}% with ${o.pendingDlHours}h duty leave",
                        style = MaterialTheme.typography.bodySmall, color = EtTheme.status.pending,
                    )
                }
                Spacer(Modifier.height(6.dp))
                if (o.total > 0) {
                    if (o.margin.isSafe) Pill("Can bunk ${o.margin.canMiss} hrs", tone = if (o.margin.canMiss > 0) Tone.Safe else Tone.Warning)
                    else Pill("Attend ${o.margin.mustAttend} more", tone = Tone.Danger)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(onClick = onLog, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Log duty leave")
            }
            FilledTonalButton(onClick = onOpen, modifier = Modifier.weight(1f)) {
                Text("Subjects"); Spacer(Modifier.width(6.dp)); Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp))
            }
        }
    }
}

@Composable
internal fun QuickLinks(navigate: (Any) -> Unit) {
    data class Link(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val route: Any)
    val links = listOf(
        Link("Courses", Icons.AutoMirrored.Rounded.MenuBook, CoursesRoute),
        Link("Analysis", Icons.Rounded.Insights, AnalysisRoute),
        Link("Campus", Icons.Rounded.AccountBalance, CampusRoute),
        Link("Notices", Icons.Rounded.Campaign, NoticesRoute),
        Link("Marks", Icons.AutoMirrored.Rounded.Assignment, AcademicsRoute),
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(links, key = { _, l -> l.label }) { i, l ->
            EtCard(Modifier.widthIn(min = 96.dp), onClick = { navigate(l.route) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                IconBubble(l.icon, index = i, size = 36.dp)
                Spacer(Modifier.height(8.dp))
                Text(l.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            }
        }
    }
}
