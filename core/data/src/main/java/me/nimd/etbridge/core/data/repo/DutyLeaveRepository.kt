package me.nimd.etbridge.core.data.repo

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import me.nimd.etbridge.core.data.db.PendingDutyLeaveDao
import me.nimd.etbridge.core.data.db.PendingDutyLeaveEntity
import me.nimd.etbridge.core.data.session.SessionStore
import me.nimd.etbridge.core.domain.DutyLeaveReconciler
import me.nimd.etbridge.core.model.DayTimetable
import me.nimd.etbridge.core.model.DutyLeave
import me.nimd.etbridge.core.model.PendingDutyLeave
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/** Self-reported duty leaves, stored per account (port of the AuthContext pending-DL state). */
@Singleton
class DutyLeaveRepository @Inject constructor(
    private val dao: PendingDutyLeaveDao,
    private val sessionStore: SessionStore,
) {
    private val owner: String? get() = sessionStore.current?.let { "${it.instituteId}:${it.username}" }

    @OptIn(ExperimentalCoroutinesApi::class)
    val pending: Flow<List<PendingDutyLeave>> = sessionStore.session.flatMapLatest { s ->
        if (s == null) flowOf(emptyList())
        else dao.observe("${s.instituteId}:${s.username}").map { list -> list.map { it.toModel() } }
    }

    suspend fun add(
        reason: String,
        date: String,
        selectedHours: List<Int>,
        subjectCode: String? = null,
        subjectName: String? = null,
        notes: String? = null,
    ) {
        val owner = owner ?: return
        val hours = selectedHours.distinct().sorted()
        val item = PendingDutyLeave(
            id = "pdl_${System.currentTimeMillis()}_${Random.nextInt(0x10000).toString(36)}",
            reason = reason.trim(),
            date = date,
            hours = hours.size.coerceAtLeast(1),
            selectedHours = hours,
            subjectCode = subjectCode,
            subjectName = subjectName,
            notes = notes?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = Instant.now().toString(),
        )
        dao.upsert(PendingDutyLeaveEntity.from(owner, item))
    }

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun clearReconciled() {
        owner?.let { dao.clearReconciled(it) }
    }

    /** Marks pending leaves that ETLab now records officially. Returns how many changed. */
    suspend fun reconcile(official: List<DutyLeave>, timetable: List<DayTimetable>): Int {
        val owner = owner ?: return 0
        val pending = dao.all(owner).map { it.toModel() }
        val ids = DutyLeaveReconciler.findNewlyReconciled(pending, official, timetable)
        if (ids.isNotEmpty()) dao.markReconciled(ids, Instant.now().toString())
        return ids.size
    }

    suspend fun clearAll() = dao.clear()
}
