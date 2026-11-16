package me.nimd.etbridge.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.nimd.etbridge.core.data.repo.DutyLeaveRepository
import java.time.LocalDateTime
import javax.inject.Inject

/** Read access to the shared student snapshot plus duty-leave actions. */
@HiltViewModel
class StudentViewModel @Inject constructor(
    hub: StudentDataHub,
    private val dutyLeaves: DutyLeaveRepository,
) : ViewModel() {

    val snapshot: StateFlow<StudentSnapshot> = hub.snapshot

    val now: StateFlow<LocalDateTime> =
        hub.clock.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalDateTime.now())

    fun logDutyLeave(
        reason: String,
        date: String,
        hours: List<Int>,
        subjectCode: String? = null,
        subjectName: String? = null,
        notes: String? = null,
    ) {
        viewModelScope.launch { dutyLeaves.add(reason, date, hours, subjectCode, subjectName, notes) }
    }

    fun deleteDutyLeave(id: String) {
        viewModelScope.launch { dutyLeaves.delete(id) }
    }

    fun clearReconciled() {
        viewModelScope.launch { dutyLeaves.clearReconciled() }
    }
}
