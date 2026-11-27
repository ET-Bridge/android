package me.nimd.etbridge

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import me.nimd.etbridge.core.data.prefs.ThemeMode
import me.nimd.etbridge.core.designsystem.EtMotion
import me.nimd.etbridge.core.designsystem.EtbridgeTheme
import me.nimd.etbridge.feature.login.LoginScreen
import me.nimd.etbridge.lock.LockScreen
import me.nimd.etbridge.navigation.MainScaffold
import me.nimd.etbridge.sync.Notifications
import me.nimd.etbridge.update.UpdateDialog
import me.nimd.etbridge.update.UpdateManager
import me.nimd.etbridge.update.manifest
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: AppViewModel by viewModels()

    @Inject lateinit var updates: UpdateManager

    /** Tab requested by a notification tap; consumed by the navigation host. */
    private val pendingDestination = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { viewModel.state.value is AppState.Loading || viewModel.prefs.value == null }
        handleIntent(intent)

        // Update check runs off the main thread after start-up settles, so it never delays the first frame.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                delay(1_500)
                updates.checkIfDue()
            }
        }

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val prefs by viewModel.prefs.collectAsStateWithLifecycle()
            val locked by viewModel.locked.collectAsStateWithLifecycle()
            val p = prefs ?: return@setContent

            val dark = when (p.themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
            }
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            DisposableEffect(p.biometricLock) {
                // Hide app content in the recents switcher when the lock is on.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!p.biometricLock)
                onDispose {}
            }

            EtbridgeTheme(darkTheme = dark, dynamicColor = p.dynamicColor, vibrant = p.vibrant) {
                val screen = when {
                    state is AppState.SignedIn && locked -> "lock"
                    state is AppState.SignedIn -> "main"
                    else -> "login"
                }
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        (fadeIn(EtMotion.smooth()) + scaleIn(EtMotion.smooth(), initialScale = 0.96f))
                            .togetherWith(fadeOut(EtMotion.smooth()))
                    },
                    label = "root",
                ) { target ->
                    when (target) {
                        "lock" -> LockScreen(
                            onUnlocked = viewModel::unlock,
                            onLogout = viewModel::logout,
                        )
                        "main" -> MainScaffold(appViewModel = viewModel, pendingDestination = pendingDestination)
                        else -> LoginScreen()
                    }
                }

                // Never over the app lock. Optional updates wait for the signed-in app; a mandatory one
                // also shows on the login screen (an outdated build may be unable to sign in at all).
                val updateState by updates.state.collectAsStateWithLifecycle()
                val prompt by updates.prompt.collectAsStateWithLifecycle()
                val manifest = updateState.manifest
                val mandatory = manifest != null && updates.isMandatory(manifest)
                if (prompt && manifest != null && screen != "lock" && (screen == "main" || mandatory)) {
                    UpdateDialog(
                        state = updateState,
                        mandatory = mandatory,
                        onInstall = updates::install,
                        onLater = updates::later,
                        onSkip = updates::skip,
                        onCancelDownload = updates::cancelDownload,
                        onOpenInstallSettings = { runCatching { startActivity(updates.unknownSourcesSettingsIntent()) } },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updates.onResume()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(Notifications.EXTRA_DESTINATION)?.let { pendingDestination.value = it }
    }
}
