package me.nimd.etbridge.feature.dashboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Poll
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.nimd.etbridge.core.data.prefs.UserPreferencesRepository
import javax.inject.Inject

/**
 * Cards the user can place on Home. [id] is persisted (`home_sections` pref): never rename one.
 * The greeting header and the sync banner are always shown and aren't listed here.
 */
enum class HomeSection(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val defaultOn: Boolean,
) {
    RiskBanner("risk", "Attendance alert", "A warning when subjects fall below your target", Icons.Rounded.WarningAmber, true),
    NextClass("next_class", "Next class", "What's on now or next, with today's hours", Icons.Rounded.Schedule, true),
    Attendance("attendance", "Overall attendance", "Your percentage, bunk margin and quick duty-leave logging", Icons.Rounded.DonutLarge, true),
    Deadlines("deadlines", "Assignment deadlines", "Open assignments, soonest first, including overdue ones", Icons.AutoMirrored.Rounded.Assignment, true),
    Exams("exams", "Upcoming exams", "Scheduled series exams and tests", Icons.Rounded.Quiz, true),
    QuickLinks("quick_links", "Quick links", "Shortcuts to Courses, Analysis, Campus and more", Icons.Rounded.Apps, true),
    NeedsAttention("needs_attention", "Needs attention", "Your three lowest subjects", Icons.AutoMirrored.Rounded.TrendingDown, true),
    Notices("notices", "Recent notices", "The latest notices from your college", Icons.Rounded.Campaign, true),
    TodaySchedule("today", "Today's timetable", "Every hour of the day with attendance status", Icons.Rounded.ViewDay, false),
    LatestMarks("marks", "Latest marks", "Recently published exam marks", Icons.Rounded.Grade, false),
    Academic("academic", "CGPA & SGPA", "CGPA and latest SGPA, once results are published", Icons.Rounded.Insights, false),
    DutyLeaves("duty_leaves", "Duty leaves", "Pending duty leaves and how much they add", Icons.Rounded.EventAvailable, false),
    Materials("materials", "New study materials", "Notes and files your teachers uploaded", Icons.Rounded.FolderOpen, false),
    FeeDues("fees", "Fee dues", "Shown only while you have a balance to pay", Icons.Rounded.CurrencyRupee, false),
    ActivityPoints("activity", "Activity points", "Progress towards the 100 KTU activity points", Icons.Rounded.EmojiEvents, false),
    Surveys("surveys", "Open surveys", "Feedback surveys waiting for you", Icons.Rounded.Poll, false),
    ;

    companion object {
        private val byId = entries.associateBy { it.id }
        val DEFAULT: List<HomeSection> = entries.filter { it.defaultOn }

        /** Saved ids → sections, skipping unknown or repeated ids; null means "never customised". */
        fun resolve(ids: List<String>?): List<HomeSection> = ids?.mapNotNull { byId[it] }?.distinct() ?: DEFAULT
    }
}

/** Home layout shared by the Dashboard (read) and Customize Home (edit). Changes save immediately. */
@HiltViewModel
class HomeLayoutViewModel @Inject constructor(private val prefs: UserPreferencesRepository) : ViewModel() {
    /** null until preferences load, so Home doesn't flash the default layout first. */
    val sections: StateFlow<List<HomeSection>?> = prefs.preferences
        .map { HomeSection.resolve(it.homeSections) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isCustomised: StateFlow<Boolean> = prefs.preferences
        .map { it.homeSections != null && HomeSection.resolve(it.homeSections) != HomeSection.DEFAULT }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun save(list: List<HomeSection>) {
        viewModelScope.launch { prefs.setHomeSections(list.map { it.id }) }
    }

    fun add(section: HomeSection) = save(current() + section)
    fun remove(section: HomeSection) = save(current() - section)
    fun move(section: HomeSection, by: Int) {
        val list = current().toMutableList()
        val from = list.indexOf(section).takeIf { it >= 0 } ?: return
        val to = (from + by).coerceIn(0, list.lastIndex)
        if (from == to) return
        list.add(to, list.removeAt(from))
        save(list)
    }

    fun reset() {
        viewModelScope.launch { prefs.resetHomeSections() }
    }

    private fun current() = sections.value ?: HomeSection.DEFAULT
}
