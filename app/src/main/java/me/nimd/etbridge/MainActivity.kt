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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import me.nimd.etbridge.core.data.prefs.ThemeMode
import me.nimd.etbridge.core.designsystem.EtMotion
import me.nimd.etbridge.core.designsystem.EtbridgeTheme
import me.nimd.etbridge.feature.login.LoginScreen
import me.nimd.etbridge.lock.LockScreen
import me.nimd.etbridge.navigation.MainScaffold
import me.nimd.etbridge.sync.Notifications

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: AppViewModel by viewModels()

    /** Tab requested by a notification tap; consumed by the navigation host. */
    private val pendingDestination = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { viewModel.state.value is AppState.Loading || viewModel.prefs.value == null }
        handleIntent(intent)

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

            EtbridgeTheme(darkTheme = dark, dynamicColor = p.dynamicColor) {
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
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(Notifications.EXTRA_DESTINATION)?.let { pendingDestination.value = it }
    }
}
