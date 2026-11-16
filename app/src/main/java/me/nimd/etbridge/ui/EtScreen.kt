package me.nimd.etbridge.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.nimd.etbridge.core.designsystem.StatusBanner
import me.nimd.etbridge.core.model.formatRelativeTime

/**
 * Standard screen: collapsing large app bar, pull-to-refresh and a LazyColumn of cards.
 * The sync/offline banner is injected automatically at the top of the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EtScreen(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    refreshable: Boolean = true,
    listState: LazyListState = rememberLazyListState(),
    titleModifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val app = LocalAppActions.current
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = {
                    Column(titleModifier) {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val sub = subtitle ?: app.lastSync?.let { "Synced ${formatRelativeTime(it)}" }
                        AnimatedVisibility(sub != null && scroll.state.collapsedFraction < 0.5f, enter = fadeIn(), exit = fadeOut()) {
                            Text(
                                sub.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    }
                },
                actions = actions,
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        val list = @Composable {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = 4.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "__sync_banner", contentType = "banner") {
                    StatusBanner(
                        message = app.syncState.error,
                        offline = app.syncState.isOffline,
                        onDismiss = app.dismissSyncError,
                        actionLabel = if (app.syncState.sessionExpired) "Sign in" else null,
                        onAction = if (app.syncState.sessionExpired) app.logout else null,
                    )
                }
                content()
            }
        }
        if (refreshable) {
            PullToRefreshBox(
                isRefreshing = app.syncState.isRefreshing,
                onRefresh = app.refresh,
                modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
            ) { list() }
        } else {
            Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) { list() }
        }
    }
}
