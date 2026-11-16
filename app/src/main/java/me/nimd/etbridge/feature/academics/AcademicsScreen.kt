package me.nimd.etbridge.feature.academics

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import me.nimd.etbridge.core.designsystem.AttendanceBar
import me.nimd.etbridge.core.designsystem.SectionHeader
import me.nimd.etbridge.core.model.formatPercent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.SubcomposeAsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.nimd.etbridge.core.data.repo.EtlabRepository
import me.nimd.etbridge.core.data.repo.FileRepository
import me.nimd.etbridge.core.designsystem.EmptyState
import me.nimd.etbridge.core.designsystem.EtCard
import me.nimd.etbridge.core.designsystem.InitialsAvatar
import me.nimd.etbridge.core.designsystem.NumberStyle
import me.nimd.etbridge.core.designsystem.Pill
import me.nimd.etbridge.core.designsystem.Tone
import me.nimd.etbridge.core.designsystem.attendanceColor
import me.nimd.etbridge.core.model.Assignment
import me.nimd.etbridge.core.model.DateStyle
import me.nimd.etbridge.core.model.SeriesExam
import me.nimd.etbridge.core.model.daysRemaining
import me.nimd.etbridge.core.model.formatCompact
import me.nimd.etbridge.core.model.formatIndianDate
import me.nimd.etbridge.core.model.formatSemester
import me.nimd.etbridge.core.model.parseLenientDouble
import me.nimd.etbridge.ui.LocalAppActions
import me.nimd.etbridge.ui.LocalSnackbar
import me.nimd.etbridge.ui.StudentSnapshot
import me.nimd.etbridge.ui.StudentViewModel
import me.nimd.etbridge.ui.TabbedScreen
import javax.inject.Inject

@HiltViewModel
class AssignmentViewModel @Inject constructor(
    private val files: FileRepository,
    private val repo: EtlabRepository,
) : ViewModel() {
    private val _busy = MutableStateFlow<String?>(null)
    val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun describe(uri: Uri) = files.describe(uri)

    fun upload(assignmentId: String, uri: Uri) = run(assignmentId, "Assignment submitted") { files.uploadAssignment(assignmentId, uri) }
    fun delete(assignmentId: String) = run(assignmentId, "Submission deleted") { files.deleteAssignment(assignmentId) }

    private fun run(id: String, success: String, block: suspend () -> Unit) {
        _busy.value = id
        viewModelScope.launch {
            _message.value = try {
                block(); repo.refreshAll(force = true); success
            } catch (e: Exception) {
                e.message ?: "Something went wrong"
            } finally {
                _busy.value = null
            }
        }
    }

    fun consumeMessage() { _message.value = null }
}

private val TABS = listOf("Exams", "Assignments", "Materials", "Results", "Faculty")

@Composable
fun AcademicsScreen(vm: StudentViewModel = hiltViewModel(), avm: AssignmentViewModel = hiltViewModel()) {
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val message by avm.message.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    var openAssignment by rememberSaveable { mutableStateOf<String?>(null) }
    var assignmentFilter by rememberSaveable { mutableStateOf(0) }
    var examFilter by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); avm.consumeMessage() }
    }

    // Class averages per exam come from /academicanalysis (keyed by exam id).
    val classAverages = remember(snap.analysis) {
        snap.analysis?.semesters.orEmpty().flatMap { it.subjects }.flatMap { it.evaluations }
            .filter { it.examId != null && it.classAvg > 0 }
            .associate { it.examId!! to it.classAvg }
    }

    TabbedScreen("Academics", TABS) { tab ->
        when (tab) {
            0 -> exams(snap, examFilter, { examFilter = it }, classAverages)
            1 -> assignments(snap, assignmentFilter, { assignmentFilter = it }) { openAssignment = it.id }
            2 -> materials(snap)
            3 -> results(snap)
            4 -> faculty(snap)
        }
    }

    val all = snap.evaluations?.let { it.assignments + it.otherWork.map { (_, a) -> a } }.orEmpty()
    val selected = all.firstOrNull { it.id == openAssignment }
    if (selected != null) {
        AssignmentSheet(selected, avm) { openAssignment = null }
    }
}

private val EXAM_FILTERS = listOf("All", "Series 1", "Series 2", "Model / retest")

private fun SeriesExam.matchesFilter(f: Int): Boolean {
    val t = title.lowercase()
    return when (f) {
        1 -> t.contains("1") || t.contains("first")
        2 -> t.contains("2") || t.contains("second")
        3 -> t.contains("model") || t.contains("retest")
        else -> true
    }
}

private fun LazyListScope.exams(snap: StudentSnapshot, filter: Int, onFilter: (Int) -> Unit, averages: Map<String, Double>) {
    val all = snap.evaluations?.let { it.seriesExams + it.moduleTests }.orEmpty()
    if (all.isEmpty()) {
        item { EmptyState(Icons.Rounded.Quiz, "No exams yet", message = "Series exams and their marks appear here once ETLab schedules or publishes them.") }
        return
    }
    item("ef") {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(EXAM_FILTERS.size) { i ->
                FilterChip(selected = filter == i, onClick = { onFilter(i) }, label = { Text(EXAM_FILTERS[i]) })
            }
        }
    }
    val exams = all.filter { it.matchesFilter(filter) }
    val upcoming = exams.filter { !it.isPublished }.sortedWith(compareBy({ it.date }, { it.period }))
    val published = exams.filter { it.isPublished }.sortedByDescending { it.date }
    if (upcoming.isNotEmpty()) {
        item("up-h") { SectionHeader("Upcoming", subtitle = "${upcoming.size} scheduled") }
        upcoming.forEach { e -> item("up-${e.id}") { UpcomingExamCard(e, Modifier.animateItem()) } }
    }
    if (published.isNotEmpty()) {
        item("pub-h") {
            val pcts = published.mapNotNull { e -> e.mark.toDoubleOrNull()?.takeIf { e.maximumMark > 0 }?.let { it / e.maximumMark * 100 } }
            SectionHeader("Published marks", subtitle = if (pcts.isNotEmpty()) "Average ${pcts.average().formatPercent()}% across ${pcts.size} papers" else null)
        }
        published.forEach { e -> item("ex-${e.id}") { ExamCard(e, averages[e.id], Modifier.animateItem()) } }
    }
    if (exams.isEmpty()) item("none") { EmptyState(Icons.Rounded.Quiz, "No exams match this filter") }
}

@Composable
private fun UpcomingExamCard(e: SeriesExam, modifier: Modifier = Modifier) {
    val left = daysRemaining(e.date)
    EtCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${e.subjectCode} · ${e.title}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(e.subjectName ?: e.subject, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        formatIndianDate(e.date, DateStyle.DayShort),
                        e.period?.takeIf { it.isNotBlank() }?.let { p -> if (p.contains(',')) "Hours $p" else "Hour $p" },
                        "${e.maximumMark.formatCompact()} marks".takeIf { e.maximumMark > 0 },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Pill(
                when {
                    left.isOverdue -> e.status.ifBlank { "Awaiting marks" }
                    left.text == "Due today" -> "Today"
                    left.text == "1 day left" -> "Tomorrow"
                    else -> left.text.replace("left", "away").ifBlank { e.status }
                },
                tone = if (left.isUrgent && !left.isOverdue) Tone.Warning else Tone.Neutral,
            )
        }
    }
}

@Composable
private fun ExamCard(e: SeriesExam, classAvg: Double?, modifier: Modifier = Modifier) {
    val mark = parseLenientDouble(e.mark, Double.NaN)
    val pct = if (!mark.isNaN() && e.maximumMark > 0) mark / e.maximumMark * 100 else null
    EtCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${e.subjectCode} · ${e.title}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(e.subjectName ?: e.subject, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(formatIndianDate(e.date, DateStyle.Short).takeIf { e.date.isNotBlank() }, e.examType?.lowercase()?.replaceFirstChar { it.uppercase() }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (pct != null) mark.formatCompact() else e.mark.ifBlank { "—" },
                    style = MaterialTheme.typography.headlineSmall.merge(NumberStyle), fontWeight = FontWeight.Bold,
                    color = pct?.let { attendanceColor(it, 50.0) } ?: MaterialTheme.colorScheme.onSurface,
                )
                Text("/ ${e.maximumMark.formatCompact()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (pct != null) {
            Spacer(Modifier.height(10.dp))
            AttendanceBar(pct, target = classAvg?.let { it / e.maximumMark * 100 } ?: 50.0)
            Text(
                buildString {
                    append("${pct.formatPercent()}%")
                    if (classAvg != null) {
                        val diff = mark - classAvg
                        append(" · class average ${classAvg.formatCompact()} (")
                        append(if (diff >= 0) "+${diff.formatCompact()}" else diff.formatCompact())
                        append(")")
                    }
                },
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private val ASSIGNMENT_FILTERS = listOf("Open", "Submitted", "Marked", "All", "Other work")

private fun Assignment.bucket(): Int = when {
    isMarked -> 2
    isSubmitted -> 1
    else -> 0
}

private fun LazyListScope.assignments(snap: StudentSnapshot, filter: Int, onFilter: (Int) -> Unit, onOpen: (Assignment) -> Unit) {
    val all = snap.evaluations?.assignments.orEmpty()
    val other = snap.evaluations?.otherWork.orEmpty()
    item("af") {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ASSIGNMENT_FILTERS.size) { i ->
                val count = when (i) { 3 -> all.size; 4 -> other.size; else -> all.count { it.bucket() == i } }
                if (i == 4 && count == 0) return@items
                FilterChip(selected = filter == i, onClick = { onFilter(i) }, label = { Text("${ASSIGNMENT_FILTERS[i]} ($count)") })
            }
        }
    }
    val list: List<Pair<String?, Assignment>> = when (filter) {
        3 -> all.map { null to it }
        4 -> other.map { (k, a) -> k to a }
        else -> all.filter { it.bucket() == filter }.map { null to it }
    }.sortedWith(compareBy<Pair<String?, Assignment>> { if (filter == 0) it.second.lastDate else "" }.thenByDescending { it.second.lastDate })
    if (list.isEmpty()) {
        item {
            EmptyState(
                Icons.AutoMirrored.Rounded.Assignment,
                if (filter == 0) "Nothing open" else "No assignments here",
                message = if (filter == 0) "You're all caught up." else null,
            )
        }
        return
    }
    list.forEach { (kind, a) ->
        item("as-${kind}-${a.id}") {
            val left = daysRemaining(a.lastDate)
            EtCard(Modifier.animateItem(), onClick = { onOpen(a) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        listOfNotNull(a.subjectCode, kind).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f),
                    )
                    when {
                        a.isMarked -> Pill("${a.mark}${a.maxMarkOrNull?.let { " / $it" } ?: ""}", tone = Tone.Safe)
                        a.isSubmitted -> Pill("Submitted", tone = Tone.Brand)
                        left.isOverdue -> Pill(a.status.lowercase().replaceFirstChar { it.uppercase() }.ifBlank { "Overdue" }, tone = Tone.Danger)
                        left.text.isNotBlank() -> Pill(left.text, tone = if (left.isUrgent) Tone.Warning else Tone.Neutral)
                    }
                }
                Text(a.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
                Text(
                    listOfNotNull(a.subjectName ?: a.subject, "Due ${formatIndianDate(a.lastDate, DateStyle.Short)}").joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** ETLab sends assignment details as HTML; render it with links and basic formatting. */
@Composable
private fun HtmlText(html: String, modifier: Modifier = Modifier) {
    val text = remember(html) { AnnotatedString.fromHtml(html.trim()) }
    Text(text, modifier, style = MaterialTheme.typography.bodyMedium)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssignmentSheet(a: Assignment, avm: AssignmentViewModel, onDismiss: () -> Unit) {
    val app = LocalAppActions.current
    val busy by avm.busy.collectAsStateWithLifecycle()
    var picked by remember { mutableStateOf<Uri?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> picked = uri }
    val working = busy == a.id

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(a.subjectCode, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(a.title, style = MaterialTheme.typography.headlineSmall)
            Text(a.subjectName ?: a.subject, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                a.issueDate?.let { Pill("Issued ${formatIndianDate(it, DateStyle.Short)}") }
                Pill("Due ${formatIndianDate(a.lastDate, DateStyle.Short)}", tone = if (a.isOpen && daysRemaining(a.lastDate).isUrgent) Tone.Danger else Tone.Neutral)
                when {
                    a.isMarked -> Pill("Marks ${a.mark}${a.maxMarkOrNull?.let { " / $it" } ?: ""}", tone = Tone.Safe)
                    a.isSubmitted -> Pill("Submitted", tone = Tone.Brand)
                    else -> Pill(a.status.lowercase().replaceFirstChar { it.uppercase() }, tone = Tone.Warning)
                }
                if (!a.isMarked) a.maxMarkOrNull?.let { Pill("Max $it") }
            }
            a.submissionMode?.let { Text("Submission: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (!a.details.isNullOrBlank()) HtmlText(a.details!!)
            HorizontalDivider()
            if (!a.url.isNullOrBlank() && a.canDownload) {
                val downloading = a.url in app.downloadingUrls
                OutlinedButton(
                    onClick = { app.download(a.url!!, "${a.subjectCode}_${a.title.replace(Regex("[^A-Za-z0-9_-]"), "_")}.pdf") },
                    enabled = !downloading, modifier = Modifier.fillMaxWidth(),
                ) {
                    if (downloading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.Download, null)
                    Spacer(Modifier.width(8.dp)); Text("Download attachment")
                }
            }
            if (a.upload && a.canSubmit) {
                val info = picked?.let { avm.describe(it) }
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("application/pdf", "image/*", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/zip")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.UploadFile, null); Spacer(Modifier.width(8.dp))
                    Text(info?.name ?: "Choose a file", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button(onClick = { picked?.let { avm.upload(a.id, it) } }, enabled = picked != null && !working, modifier = Modifier.fillMaxWidth()) {
                    if (working) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Submit")
                }
            } else if (a.isOpen && a.submissionMode?.contains("manual", true) == true) {
                Text(
                    "This assignment is submitted in person, not through ETLab.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (a.canDeleteSubmission) {
                TextButton(onClick = { confirmDelete = true }, enabled = !working) {
                    Text("Delete my current submission", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete submission?") },
            text = { Text("Your uploaded file for “${a.title}” will be removed from ETLab.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; avm.delete(a.id) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private fun LazyListScope.materials(snap: StudentSnapshot) {
    if (snap.materials.isEmpty()) {
        item { EmptyState(Icons.Rounded.FolderOpen, "No study materials", message = "Notes and files your teachers upload show up here.") }
        return
    }
    snap.materials.groupBy { it.subject.ifBlank { "Other" } }.forEach { (subject, list) ->
        item("mh-$subject") { Text(subject, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp)) }
        list.forEach { m ->
            item("m-${m.id}") {
                val app = LocalAppActions.current
                val link = m.link
                val downloading = link != null && link in app.downloadingUrls
                EtCard(onClick = link?.let { { app.download(it, m.fileName?.substringAfterLast('/')) } }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Description, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                listOfNotNull(m.module?.let { "Module $it" }, m.teacher, formatIndianDate(m.addedOn, DateStyle.Short).takeIf { m.addedOn.isNotBlank() })
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!m.details.isNullOrBlank()) Text(m.details!!, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        if (downloading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else if (link != null) Icon(Icons.Rounded.Download, "Download")
                    }
                }
            }
        }
    }
}

private fun LazyListScope.results(snap: StudentSnapshot) {
    if (snap.results.isEmpty()) {
        item { EmptyState(Icons.Rounded.EmojiEvents, "No university results yet") }
        return
    }
    snap.results.forEach { r ->
        item("r-${r.examId}-${r.examName}") {
            EtCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.examName, style = MaterialTheme.typography.titleSmall)
                        r.semester?.let { Text(formatSemester(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        r.sgpa?.let { Text("SGPA $it", style = MaterialTheme.typography.titleMedium.merge(NumberStyle), fontWeight = FontWeight.Bold) }
                        r.cgpa?.let { Text("CGPA $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                if (r.results.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    r.results.forEach { g ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(g.courseName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${g.courseCode} · ${g.credits.formatCompact()} cr", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Pill(g.grade, tone = gradeTone(g.grade))
                        }
                    }
                }
            }
        }
    }
}

private fun gradeTone(grade: String): Tone = when (grade.trim().uppercase()) {
    "S", "O", "A+", "A" -> Tone.Safe
    "B+", "B", "C+", "C" -> Tone.Brand
    "D", "P", "E" -> Tone.Warning
    "F", "FE", "AB", "I" -> Tone.Danger
    else -> Tone.Neutral
}

private fun LazyListScope.faculty(snap: StudentSnapshot) {
    if (snap.teachers.isEmpty()) {
        item { EmptyState(Icons.Rounded.Groups, "No faculty listed") }
        return
    }
    snap.teachers.sortedByDescending { (if (it.isHod) 4 else 0) + (if (it.isStaffAdvisor) 2 else 0) + (if (it.isMentor) 1 else 0) }.forEach { t ->
        item("t-${t.id}-${t.name}") {
            val context = LocalContext.current
            EtCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (t.photo.isNullOrBlank()) InitialsAvatar(t.name, size = 48.dp)
                    else SubcomposeAsyncImage(
                        model = t.photo, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp).clip(CircleShape),
                        error = { InitialsAvatar(t.name, size = 48.dp) }, loading = { InitialsAvatar(t.name, size = 48.dp) },
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOfNotNull(t.roleLabel ?: t.designation, t.subject).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                            if (t.isHod) Pill("HOD", tone = Tone.Brand)
                            if (t.isStaffAdvisor) Pill("Staff advisor", tone = Tone.Pending)
                            if (t.isMentor) Pill("Mentor", tone = Tone.Safe)
                        }
                    }
                    t.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                        IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$phone".toUri())) }) {
                            Icon(Icons.Rounded.Call, "Call ${t.name}")
                        }
                    }
                    t.email?.takeIf { it.isNotBlank() }?.let { email ->
                        IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_SENDTO, "mailto:$email".toUri())) }) {
                            Icon(Icons.Rounded.Email, "Email ${t.name}")
                        }
                    }
                }
            }
        }
    }
}
