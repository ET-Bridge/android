package me.nimd.etbridge.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.nimd.etbridge.core.domain.Schedule
import me.nimd.etbridge.core.model.DateStyle
import me.nimd.etbridge.core.model.DayTimetable
import me.nimd.etbridge.core.model.formatIndianDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val QUICK_REASONS = listOf(
    "NSS Special Camp",
    "Sports Tournament",
    "College Tech Fest Organizing",
    "Arts Fest / Cultural Event",
    "College Union Activity",
    "Department Symposium",
)

/**
 * Port of LogDutyLeaveModal: pick a date and the hours you were away; the sheet shows which
 * subject each hour was so the projected attendance is obvious.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogDutyLeaveSheet(
    days: List<DayTimetable>,
    onDismiss: () -> Unit,
    onSave: (reason: String, date: String, hours: List<Int>, notes: String?) -> Unit,
    initialDate: LocalDate = LocalDate.now(),
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var reason by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var dateIso by rememberSaveable { mutableStateOf(initialDate.toString()) }
    var hours by rememberSaveable { mutableStateOf(listOf(4, 5, 6)) }
    var error by remember { mutableStateOf<String?>(null) }
    var pickingDate by remember { mutableStateOf(false) }

    val date = LocalDate.parse(dateIso)
    val dayPeriods = remember(dateIso, days) { Schedule.periodsFor(date, days, days)?.sub.orEmpty() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Log duty leave", style = MaterialTheme.typography.headlineSmall)
            LocalOnlyNotice()

            OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.CalendarToday, null)
                Spacer(Modifier.padding(4.dp))
                Text(formatIndianDate(date, DateStyle.DayLong))
            }

            Text("Hours away", style = MaterialTheme.typography.titleSmall)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Schedule.slots.forEach { slot ->
                    val period = dayPeriods.firstOrNull { it.hour == slot.hour }
                    val selected = slot.hour in hours
                    val border by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        label = "b",
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .border(1.dp, border, MaterialTheme.shapes.medium)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceContainer,
                                MaterialTheme.shapes.medium,
                            )
                            .toggleable(value = selected, role = Role.Checkbox) {
                                hours = if (selected) hours - slot.hour else (hours + slot.hour).sorted()
                                error = null
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text("H${slot.hour}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                period?.let { it.subject.ifBlank { it.subjectCode } } ?: "No class data",
                                style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(slot.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        androidx.compose.material3.Checkbox(checked = selected, onCheckedChange = null)
                    }
                }
            }

            Text("Reason", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QUICK_REASONS.forEach { r ->
                    FilterChip(selected = reason == r, onClick = { reason = r; error = null }, label = { Text(r) })
                }
            }
            OutlinedTextField(
                value = reason, onValueChange = { reason = it; error = null },
                label = { Text("Event or reason") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            )
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2,
                shape = MaterialTheme.shapes.medium,
            )
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = {
                    when {
                        reason.isBlank() -> error = "Please provide a reason or event name."
                        hours.isEmpty() -> error = "Please select at least one hour of the day."
                        else -> {
                            onSave(reason, dateIso, hours, notes.takeIf { it.isNotBlank() })
                            scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.medium,
            ) { Text("Save ${hours.size} hour${if (hours.size == 1) "" else "s"}") }
        }
    }

    if (pickingDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        dateIso = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}
