package me.nimd.etbridge.feature.courses

import androidx.compose.runtime.Composable
import me.nimd.etbridge.navigation.CourseRoute
import me.nimd.etbridge.ui.EtScreen

@Composable fun CoursesScreen() = EtScreen("Courses") {}
@Composable fun CourseDetailScreen(route: CourseRoute) = EtScreen(route.name) {}
