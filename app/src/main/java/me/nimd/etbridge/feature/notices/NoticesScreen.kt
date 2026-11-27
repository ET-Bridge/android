package me.nimd.etbridge.feature.notices

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.nimd.etbridge.core.designsystem.EmptyState
import me.nimd.etbridge.core.designsystem.EtCard
import me.nimd.etbridge.core.model.CircularItem
import me.nimd.etbridge.core.model.DateStyle
import me.nimd.etbridge.core.model.NoticeItem
import me.nimd.etbridge.core.model.formatIndianDate
import me.nimd.etbridge.ui.LocalAppActions
import me.nimd.etbridge.ui.StudentViewModel
import me.nimd.etbridge.ui.TabbedScreen
import me.nimd.etbridge.ui.keyedUniquely

@Composable
fun NoticesScreen(vm: StudentViewModel = hiltViewModel()) {
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val app = LocalAppActions.current
    var query by rememberSaveable { mutableStateOf("") }
    val notices = snap.profile?.notices.orEmpty().filter {
        query.isBlank() || it.title.contains(query, true) || it.content.orEmpty().contains(query, true) || it.postedBy.orEmpty().contains(query, true)
    }
    val circulars = snap.profile?.circulars.orEmpty().filter {
        query.isBlank() || it.title.contains(query, true) || it.description.orEmpty().contains(query, true)
    }

    TabbedScreen("Notices", listOf("Notices (${notices.size})", "Circulars (${circulars.size})"), onBack = app.back) { tab ->
        item("search") {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true,
                shape = MaterialTheme.shapes.medium, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
        }
        if (tab == 0) noticeList(notices) else circularList(circulars)
    }
}

private fun LazyListScope.noticeList(list: List<NoticeItem>) {
    if (list.isEmpty()) item { EmptyState(Icons.Rounded.Campaign, "No notices") }
    list.sortedByDescending { it.date }.keyedUniquely { "n-${it.id}" }.forEach { (k, n) ->
        item(k) {
            var expanded by rememberSaveable { mutableStateOf(false) }
            EtCard(Modifier.animateContentSize(), onClick = { expanded = !expanded }) {
                Text(n.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(formatIndianDate(n.date, DateStyle.DayShort), n.postedBy, n.source).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                if (!n.content.isNullOrBlank()) {
                    Text(
                        n.content!!, style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun LazyListScope.circularList(list: List<CircularItem>) {
    if (list.isEmpty()) item { EmptyState(Icons.Rounded.Description, "No circulars") }
    list.sortedByDescending { it.date }.keyedUniquely { "c-${it.id}" }.forEach { (k, c) ->
        item(k) {
            val app = LocalAppActions.current
            val url = c.url?.takeIf { it.isNotBlank() && it != "#" }
            EtCard(onClick = url?.let { { app.download(it, null) } }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Description, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        Text(c.title, style = MaterialTheme.typography.titleSmall)
                        Text(formatIndianDate(c.date, DateStyle.DayShort), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!c.description.isNullOrBlank()) Text(c.description!!, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    if (url != null) {
                        if (url in app.downloadingUrls) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.Download, "Download")
                    }
                }
            }
        }
    }
}
