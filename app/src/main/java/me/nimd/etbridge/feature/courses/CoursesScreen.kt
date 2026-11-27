package me.nimd.etbridge.feature.courses

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.nimd.etbridge.core.data.repo.EtlabRepository
import me.nimd.etbridge.core.designsystem.AttendanceRing
import me.nimd.etbridge.core.designsystem.EmptyState
import me.nimd.etbridge.core.designsystem.EtCard
import me.nimd.etbridge.core.designsystem.EtTheme
import me.nimd.etbridge.core.designsystem.Pill
import me.nimd.etbridge.core.designsystem.SkeletonBlock
import me.nimd.etbridge.core.designsystem.Tone
import me.nimd.etbridge.core.model.CourseDetails
import me.nimd.etbridge.core.model.DateStyle
import me.nimd.etbridge.core.model.formatCompact
import me.nimd.etbridge.core.model.formatIndianDate
import me.nimd.etbridge.navigation.CourseRoute
import me.nimd.etbridge.ui.EtScreen
import me.nimd.etbridge.ui.LocalAppActions
import me.nimd.etbridge.ui.StudentViewModel
import me.nimd.etbridge.ui.TabbedScreen
import me.nimd.etbridge.ui.sharedBounds
import javax.inject.Inject
import me.nimd.etbridge.ui.keyedUniquely

@Composable
fun CoursesScreen(vm: StudentViewModel = hiltViewModel()) {
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val app = LocalAppActions.current
    var query by rememberSaveable { mutableStateOf("") }
    val courses = snap.courses.filter {
        query.isBlank() || it.name.contains(query, true) || it.code.contains(query, true) ||
            it.teacherName.orEmpty().contains(query, true)
    }

    EtScreen("Courses", onBack = app.back, subtitle = "${snap.courses.size} registered courses") {
        item("search") {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search courses or faculty") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true, shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
        }
        if (!snap.loaded) {
            items(4) { SkeletonBlock(height = 130.dp) }
            return@EtScreen
        }
        if (courses.isEmpty()) {
            item { EmptyState(Icons.AutoMirrored.Rounded.MenuBook, if (query.isBlank()) "No courses yet" else "No matches") }
        }
        courses.keyedUniquely { "c-${it.id}-${it.code}" }.forEach { (k, c) ->
            item(k) {
                EtCard(
                    Modifier.animateItem().sharedBounds("course-${c.id}"),
                    onClick = { app.navigate(CourseRoute(c.id, c.name, c.code)) },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(c.name.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.code, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (c.attenPer > 0) AttendanceRing(c.attenPer, size = 48.dp, strokeWidth = 5.dp, labelStyle = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (c.type.isNotBlank()) Pill(c.type, tone = Tone.Brand)
                        c.slot?.takeIf { it.isNotBlank() }?.let { Pill("Slot $it") }
                        if (c.credits > 0) Pill("${c.credits.formatCompact()} credits")
                        if (c.hours > 0) Pill("${c.hours} hrs/wk")
                    }
                    c.teacherName?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            listOfNotNull(it, c.attenText).joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@HiltViewModel
class CourseDetailViewModel @Inject constructor(private val repo: EtlabRepository) : ViewModel() {
    private val id = MutableStateFlow<String?>(null)
    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val details = id.flatMapLatest { it?.let(repo::observeCourseDetails) ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun load(courseId: String) {
        if (id.value == courseId) return
        id.value = courseId
        viewModelScope.launch {
            _loading.value = true
            _error.value = repo.refreshCourseDetails(courseId).exceptionOrNull()?.let { "Couldn't load the syllabus. Showing saved data if available." }
            _loading.value = false
        }
    }
}

@Composable
fun CourseDetailScreen(route: CourseRoute, vm: CourseDetailViewModel = hiltViewModel()) {
    LaunchedEffect(route.id) { vm.load(route.id) }
    val details by vm.details.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val app = LocalAppActions.current
    val d = details

    val tabs = buildList {
        add("Syllabus")
        if (d?.outcomes?.isNotEmpty() == true) add("Outcomes")
        if (d?.execution?.isNotEmpty() == true) add("Execution")
        if (d?.sourceBooks?.isNotEmpty() == true) add("Books")
    }
    TabbedScreen("${route.code} · ${route.name}", tabs, onBack = app.back) { i ->
        if (error != null) item("err") { Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (d == null) {
            if (loading) items(5) { SkeletonBlock(height = 64.dp) }
            else item { EmptyState(Icons.AutoMirrored.Rounded.MenuBook, "No syllabus available") }
            return@TabbedScreen
        }
        when (tabs.getOrNull(i)) {
            "Syllabus" -> syllabus(d)
            "Outcomes" -> d.outcomes.keyedUniquely { "o-${it.id}" }.forEach { (k, o) ->
                item(k) {
                    EtCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Pill(o.code, tone = Tone.Brand); Spacer(Modifier.width(8.dp))
                            o.bloomLevel?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        Text(o.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            "Execution" -> d.execution.sortedByDescending { it.date }.keyedUniquely { "e-${it.id}" }.forEach { (k, e) ->
                item(k) {
                    EtCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${formatIndianDate(e.date, DateStyle.Short)} · Hour ${e.hour}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                            Pill(e.status, tone = if (e.status.contains("conduct", true)) Tone.Safe else Tone.Neutral)
                        }
                        Text(e.topic, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            "Books" -> d.sourceBooks.keyedUniquely { "b-${it.id}" }.forEach { (k, b) ->
                item(k) {
                    EtCard {
                        Text(b.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOfNotNull(b.author, b.publisher, b.edition).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (b.isReference) Pill("Reference", Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}

private fun LazyListScope.syllabus(d: CourseDetails) {
    if (d.syllabus.isEmpty()) {
        item { EmptyState(Icons.AutoMirrored.Rounded.MenuBook, "No syllabus topics published") }
        return
    }
    val covered = d.syllabus.count { it.covered }
    item("progress") {
        EtCard {
            Text("Coverage", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$covered of ${d.syllabus.size} topics covered", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { covered.toFloat() / d.syllabus.size },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(MaterialTheme.shapes.small),
            )
        }
    }
    // Module 0 holds ETLab's "Extra Topic" placeholders; list real modules first.
    d.syllabus.groupBy { it.module }.toSortedMap(compareBy { if (it == 0) Int.MAX_VALUE else it }).forEach { (module, topics) ->
        item("m-$module") {
            EtCard {
                Text(if (module > 0) "Module $module" else "Extra topics", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                topics.forEach { t ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            if (t.covered) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                            tint = if (t.covered) EtTheme.status.safe else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp).padding(top = 2.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(t.topic, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        if (t.hours > 0) Text("${t.hours.formatCompact()}h", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
