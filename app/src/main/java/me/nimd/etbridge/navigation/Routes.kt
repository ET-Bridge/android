package me.nimd.etbridge.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.School
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

@Serializable data object DashboardRoute
@Serializable data object AttendanceRoute
@Serializable data object TimetableRoute
@Serializable data object AcademicsRoute
@Serializable data object MoreRoute

@Serializable data class SubjectRoute(val key: String)
@Serializable data object DutyLeavesRoute
@Serializable data object CoursesRoute
@Serializable data class CourseRoute(val id: String, val name: String, val code: String)
@Serializable data object CampusRoute
@Serializable data object NoticesRoute
@Serializable data object AnalysisRoute
@Serializable data object SettingsRoute
@Serializable data object AboutRoute
@Serializable data object AppInfoRoute
@Serializable data object HomeLayoutRoute

enum class TopLevel(
    val route: Any,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    /** Name used by notification deep links. */
    val linkName: String,
) {
    Dashboard(DashboardRoute, "Home", Icons.Outlined.Dashboard, Icons.Rounded.Dashboard, "dashboard"),
    Attendance(AttendanceRoute, "Attendance", Icons.Outlined.DonutLarge, Icons.Rounded.DonutLarge, "attendance"),
    Timetable(TimetableRoute, "Timetable", Icons.Outlined.CalendarMonth, Icons.Rounded.CalendarMonth, "timetable"),
    Academics(AcademicsRoute, "Academics", Icons.Outlined.School, Icons.Rounded.School, "academics"),
    More(MoreRoute, "More", Icons.Outlined.GridView, Icons.Rounded.GridView, "more"),
}
