package me.nimd.etbridge.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.sp
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.MutableStateFlow
import me.nimd.etbridge.AppState
import me.nimd.etbridge.AppViewModel
import me.nimd.etbridge.core.designsystem.EtMotion
import me.nimd.etbridge.feature.academics.AcademicsScreen
import me.nimd.etbridge.feature.analysis.AnalysisScreen
import me.nimd.etbridge.feature.attendance.AttendanceScreen
import me.nimd.etbridge.feature.attendance.DutyLeavesScreen
import me.nimd.etbridge.feature.attendance.SubjectDetailScreen
import me.nimd.etbridge.feature.campus.CampusScreen
import me.nimd.etbridge.feature.courses.CourseDetailScreen
import me.nimd.etbridge.feature.courses.CoursesScreen
import me.nimd.etbridge.feature.dashboard.DashboardScreen
import me.nimd.etbridge.feature.more.AboutScreen
import me.nimd.etbridge.feature.more.MoreScreen
import me.nimd.etbridge.feature.notices.NoticesScreen
import me.nimd.etbridge.feature.settings.SettingsScreen
import me.nimd.etbridge.feature.timetable.TimetableScreen
import me.nimd.etbridge.ui.AppActions
import me.nimd.etbridge.ui.FileEvent
import me.nimd.etbridge.ui.FilesViewModel
import me.nimd.etbridge.ui.LocalAppActions
import me.nimd.etbridge.ui.LocalNavAnimatedScope
import me.nimd.etbridge.ui.LocalSharedTransitionScope
import me.nimd.etbridge.ui.LocalSnackbar
import kotlin.reflect.KClass

private val topLevelClasses: List<KClass<*>> = TopLevel.entries.map { it.route::class }

private fun NavBackStackEntry.isTopLevel(): Boolean =
    topLevelClasses.any { cls -> destination.hierarchy.any { it.hasRoute(cls) } }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MainScaffold(appViewModel: AppViewModel, pendingDestination: MutableStateFlow<String?>) {
    val nav = rememberNavController()
    val files: FilesViewModel = hiltViewModel()
    val snackbar = remember { SnackbarHostState() }

    val appState by appViewModel.state.collectAsStateWithLifecycle()
    val sync by appViewModel.syncState.collectAsStateWithLifecycle()
    val lastSync by appViewModel.lastSync.collectAsStateWithLifecycle()
    val downloading by files.downloading.collectAsStateWithLifecycle()
    val backStack by nav.currentBackStackEntryAsState()
    val pending by pendingDestination.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        files.events.collect { e ->
            when (e) {
                is FileEvent.Opened -> if (!files.open(e.file)) {
                    snackbar.showSnackbar("Saved ${e.file.displayName} to Downloads")
                }
                is FileEvent.Failed -> snackbar.showSnackbar(e.message)
            }
        }
    }
    LaunchedEffect(pending) {
        val target = TopLevel.entries.firstOrNull { it.linkName == pending } ?: return@LaunchedEffect
        nav.navigateTopLevel(target)
        pendingDestination.value = null
    }

    val actions = AppActions(
        syncState = sync,
        lastSync = lastSync,
        isDemo = (appState as? AppState.SignedIn)?.session?.isDemo == true,
        refresh = { appViewModel.refresh(force = true) },
        dismissSyncError = appViewModel::dismissSyncError,
        logout = appViewModel::logout,
        download = files::download,
        downloadingUrls = downloading,
        navigate = { route ->
            // Shortcuts to a tab (e.g. dashboard "Subjects") must behave exactly like tapping the tab,
            // otherwise the tab ends up stacked on Home and the Home tab can't return to the dashboard.
            val tab = TopLevel.entries.firstOrNull { it.route == route }
            if (tab != null) nav.navigateTopLevel(tab) else nav.navigate(route) { launchSingleTop = true }
        },
        back = { nav.popBackStack() },
    )

    CompositionLocalProvider(LocalAppActions provides actions, LocalSnackbar provides snackbar) {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                val currentTab = backStack?.let { nav.currentTab() } ?: TopLevel.Dashboard
                TopLevel.entries.forEach { dest ->
                    val selected = dest == currentTab
                    item(
                        selected = selected,
                        onClick = { nav.navigateTopLevel(dest) },
                        icon = { Icon(if (selected) dest.selectedIcon else dest.icon, contentDescription = null) },
                        // Labels never wrap; at large font sizes they shrink a little instead.
                        label = {
                            BasicText(
                                dest.label,
                                maxLines = 1,
                                style = MaterialTheme.typography.labelMedium.copy(color = LocalContentColor.current),
                                autoSize = TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = MaterialTheme.typography.labelMedium.fontSize),
                            )
                        },
                    )
                }
            },
        ) {
            Box(Modifier.fillMaxSize()) {
                SharedTransitionLayout {
                    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                        NavHost(
                            navController = nav,
                            startDestination = DashboardRoute,
                            enterTransition = { enterFor(this) },
                            exitTransition = { exitFor(this) },
                            popEnterTransition = { popEnterFor(this) },
                            popExitTransition = { popExitFor(this) },
                        ) { graph() }
                    }
                }
                SnackbarHost(
                    snackbar,
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                ) { data ->
                    androidx.compose.material3.Snackbar(data)
                }
            }
        }
    }
}

/** Tab currently shown: the deepest top-level destination in the back stack (Home if none). */
private fun NavHostController.currentTab(): TopLevel {
    val stack = currentBackStack.value
    for (entry in stack.asReversed()) {
        TopLevel.entries.firstOrNull { t -> entry.destination.hasRoute(t.route::class) }?.let { return it }
    }
    return TopLevel.Dashboard
}

private fun NavHostController.navigateTopLevel(dest: TopLevel) {
    val current = currentTab()
    when {
        // Home is the start destination: pop back to it, keeping the tab we leave restorable.
        dest == TopLevel.Dashboard -> {
            if (!popBackStack(DashboardRoute, inclusive = false, saveState = current != TopLevel.Dashboard)) {
                navigate(DashboardRoute) { launchSingleTop = true }
            }
        }
        // Re-selecting the current tab returns to its root screen.
        dest == current -> popBackStack(dest.route, inclusive = false)
        else -> navigate(dest.route) {
            popUpTo(graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
}

private fun NavGraphBuilder.screen(
    route: KClass<*>,
    content: @Composable (NavBackStackEntry) -> Unit,
) {
    @Suppress("UNCHECKED_CAST")
    composable(route as KClass<Any>, typeMap = emptyMap(), deepLinks = emptyList()) { entry ->
        // While a screen animates out (e.g. during the predictive-back peek) it becomes a rounded
        // card, matching the system back animation.
        val corner by transition.animateDp(
            // Reach the full radius within the first third of the gesture so the card reads as a card.
            transitionSpec = { tween(PEEK_MS / 3, easing = LinearEasing) },
            label = "corner",
        ) { state -> if (state == EnterExitState.PostExit) 32.dp else 0.dp }
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val r = corner.toPx()
                clip = r > 0f
                shape = RoundedCornerShape(r)
            },
        ) {
            CompositionLocalProvider(LocalNavAnimatedScope provides this@composable) { content(entry) }
        }
    }
}

private fun NavGraphBuilder.graph() {
    screen(DashboardRoute::class) { DashboardScreen() }
    screen(AttendanceRoute::class) { AttendanceScreen() }
    screen(TimetableRoute::class) { TimetableScreen() }
    screen(AcademicsRoute::class) { AcademicsScreen() }
    screen(MoreRoute::class) { MoreScreen() }
    screen(SubjectRoute::class) { SubjectDetailScreen(it.toRoute<SubjectRoute>().key) }
    screen(DutyLeavesRoute::class) { DutyLeavesScreen() }
    screen(CoursesRoute::class) { CoursesScreen() }
    screen(CourseRoute::class) { CourseDetailScreen(it.toRoute<CourseRoute>()) }
    screen(CampusRoute::class) { CampusScreen() }
    screen(NoticesRoute::class) { NoticesScreen() }
    screen(AnalysisRoute::class) { AnalysisScreen() }
    screen(SettingsRoute::class) { SettingsScreen() }
    screen(AboutRoute::class) { AboutScreen() }
}

// Top-level tabs fade through; detail screens slide in from the side (with shared elements).
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch() =
    initialState.isTopLevel() && targetState.isTopLevel()

private fun enterFor(s: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition =
    if (s.isTabSwitch()) fadeIn(tween(220, delayMillis = 60)) + scaleIn(tween(280), initialScale = 0.97f)
    else slideInHorizontally(EtMotion.smooth()) { it / 4 } + fadeIn(tween(220))

private fun exitFor(s: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition =
    if (s.isTabSwitch()) fadeOut(tween(90))
    else slideOutHorizontally(EtMotion.smooth()) { -it / 8 } + fadeOut(tween(200))

/*
 * Back (including the predictive-back peek, which scrubs these by gesture progress): the screen
 * being left shrinks into a rounded card and only fades at the very end, while the previous
 * screen settles in from slightly behind. Tweens with linear-ish easing keep the scrub 1:1
 * with the finger; springs would lag behind the gesture.
 */
private const val PEEK_MS = 340
private val PeekEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private fun popEnterFor(@Suppress("UNUSED_PARAMETER") s: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition =
    scaleIn(tween(PEEK_MS, easing = PeekEasing), initialScale = 0.95f) +
        fadeIn(tween(PEEK_MS, easing = LinearEasing), initialAlpha = 0.45f)

private fun popExitFor(@Suppress("UNUSED_PARAMETER") s: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition =
    scaleOut(tween(PEEK_MS, easing = PeekEasing), targetScale = 0.88f) +
        fadeOut(tween(PEEK_MS / 3, delayMillis = PEEK_MS * 2 / 3, easing = LinearEasing))

