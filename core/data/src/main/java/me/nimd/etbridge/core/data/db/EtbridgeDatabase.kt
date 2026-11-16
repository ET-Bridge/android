package me.nimd.etbridge.core.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import me.nimd.etbridge.core.model.PendingDutyLeave

/**
 * One cached, already-normalized API payload. Mirrors the webapp's `etbridge_cached_<name>`
 * localStorage entries plus their `_time` stamp, but observable via Flow.
 */
@Entity(tableName = "cache_entries")
data class CacheEntry(
    @PrimaryKey val key: String,
    val json: String,
    val fetchedAt: Long,
)

@Dao
interface CacheDao {
    @Query("SELECT * FROM cache_entries WHERE `key` = :key")
    fun observe(key: String): Flow<CacheEntry?>

    @Query("SELECT * FROM cache_entries WHERE `key` = :key")
    suspend fun get(key: String): CacheEntry?

    @Query("SELECT fetchedAt FROM cache_entries WHERE `key` = :key")
    suspend fun fetchedAt(key: String): Long?

    @Upsert
    suspend fun upsert(entry: CacheEntry)

    @Query("DELETE FROM cache_entries")
    suspend fun clear()
}

@Entity(tableName = "pending_duty_leaves")
data class PendingDutyLeaveEntity(
    @PrimaryKey val id: String,
    /** Username the leave belongs to, so different accounts on one device stay separate. */
    val owner: String,
    val reason: String,
    val date: String,
    val endDate: String?,
    val hours: Int,
    val selectedHours: List<Int>,
    val subjectCode: String?,
    val subjectName: String?,
    val notes: String?,
    val createdAt: String,
    val reconciled: Boolean,
    val reconciledAt: String?,
) {
    fun toModel() = PendingDutyLeave(
        id = id, reason = reason, date = date, endDate = endDate, hours = hours,
        selectedHours = selectedHours, subjectCode = subjectCode, subjectName = subjectName,
        notes = notes, createdAt = createdAt, reconciled = reconciled, reconciledAt = reconciledAt,
    )

    companion object {
        fun from(owner: String, m: PendingDutyLeave) = PendingDutyLeaveEntity(
            id = m.id, owner = owner, reason = m.reason, date = m.date, endDate = m.endDate,
            hours = m.hours, selectedHours = m.selectedHours, subjectCode = m.subjectCode,
            subjectName = m.subjectName, notes = m.notes, createdAt = m.createdAt,
            reconciled = m.reconciled, reconciledAt = m.reconciledAt,
        )
    }
}

@Dao
interface PendingDutyLeaveDao {
    @Query("SELECT * FROM pending_duty_leaves WHERE owner = :owner ORDER BY date DESC, createdAt DESC")
    fun observe(owner: String): Flow<List<PendingDutyLeaveEntity>>

    @Query("SELECT * FROM pending_duty_leaves WHERE owner = :owner")
    suspend fun all(owner: String): List<PendingDutyLeaveEntity>

    @Upsert
    suspend fun upsert(entity: PendingDutyLeaveEntity)

    @Query("UPDATE pending_duty_leaves SET reconciled = 1, reconciledAt = :at WHERE id IN (:ids)")
    suspend fun markReconciled(ids: List<String>, at: String)

    @Query("DELETE FROM pending_duty_leaves WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM pending_duty_leaves WHERE owner = :owner AND reconciled = 1")
    suspend fun clearReconciled(owner: String)

    @Query("DELETE FROM pending_duty_leaves")
    suspend fun clear()
}

class Converters {
    @TypeConverter
    fun intsToString(value: List<Int>): String = value.joinToString(",")

    @TypeConverter
    fun stringToInts(value: String): List<Int> =
        if (value.isBlank()) emptyList() else value.split(',').mapNotNull { it.trim().toIntOrNull() }
}

@Database(
    entities = [CacheEntry::class, PendingDutyLeaveEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class EtbridgeDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
    abstract fun pendingDutyLeaveDao(): PendingDutyLeaveDao
}
