package me.nimd.etbridge.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.nimd.etbridge.core.data.repo.DownloadedFile
import me.nimd.etbridge.core.data.repo.FileRepository
import me.nimd.etbridge.core.data.repo.SyncState
import javax.inject.Inject

/** App-wide actions and state handed to every screen. */
class AppActions(
    val syncState: SyncState,
    val lastSync: Long?,
    val isDemo: Boolean,
    val refresh: () -> Unit,
    val dismissSyncError: () -> Unit,
    val logout: () -> Unit,
    val download: (url: String, name: String?) -> Unit,
    val downloadingUrls: Set<String>,
    val navigate: (Any) -> Unit,
    val back: () -> Unit,
)

val LocalAppActions = compositionLocalOf<AppActions> { error("AppActions not provided") }
val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Shared-element bounds between list items and their detail screens (no-op if unavailable). */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedBounds(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedScope.current ?: return this
    return with(shared) {
        this@sharedBounds.sharedBounds(
            rememberSharedContentState(key),
            animatedVisibilityScope = animated,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedElement(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedScope.current ?: return this
    return with(shared) {
        this@sharedElement.sharedElement(rememberSharedContentState(key), animatedVisibilityScope = animated)
    }
}

sealed interface FileEvent {
    data class Opened(val file: DownloadedFile) : FileEvent
    data class Failed(val message: String) : FileEvent
}

/** Activity-scoped download manager: fetches with auth, then hands the file to a viewer app. */
@HiltViewModel
class FilesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val files: FileRepository,
) : ViewModel() {
    private val _downloading = MutableStateFlow<Set<String>>(emptySet())
    val downloading: StateFlow<Set<String>> = _downloading.asStateFlow()

    private val _events = Channel<FileEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun download(url: String, name: String?) {
        if (url in _downloading.value) return
        _downloading.update { it + url }
        viewModelScope.launch {
            try {
                val file = files.download(url, name)
                _events.send(FileEvent.Opened(file))
            } catch (e: Exception) {
                _events.send(FileEvent.Failed(e.message ?: "Download failed"))
            } finally {
                _downloading.update { it - url }
            }
        }
    }

    /** Opens a downloaded file; returns false if no app can handle it. */
    fun open(file: DownloadedFile): Boolean = try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(file.uri, file.mimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

fun Context.openUrl(url: String) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
