package com.telezen.app.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

object EventType {
    const val SHORT_BLOCK = "SHORT_BLOCK"
    const val SESSION_START = "SESSION_START"
    const val PAUSE_ABANDONED = "PAUSE_ABANDONED"
    const val SESSION_END = "SESSION_END"
    const val LIMIT_HIT = "LIMIT_HIT"
    const val SCHEDULE_BLOCK = "SCHEDULE_BLOCK"
    const val EMERGENCY_UNLOCK = "EMERGENCY_UNLOCK"
}

@Entity(tableName = "events", indices = [Index("day", "type"), Index("pkg")])
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val day: String,
    val pkg: String,
    val type: String,
    val targetId: String? = null,
    val extra: String? = null,
)

@Entity(tableName = "day_summary")
data class DaySummary(
    @PrimaryKey val day: String,
    val trackedMinutes: Int,
    val goalMinutes: Int,
    val opens: Int,
    val abandoned: Int,
    val blocks: Int,
)

fun DaySummary.goalMet(): Boolean = trackedMinutes <= goalMinutes

data class KeyCount(val name: String?, val count: Int)

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: EventEntity)

    @Query("SELECT COUNT(*) FROM events WHERE day = :day AND pkg = :pkg AND type = :type")
    suspend fun count(day: String, pkg: String, type: String): Int

    @Query("SELECT COUNT(*) FROM events WHERE day = :day AND type = :type")
    suspend fun countDay(day: String, type: String): Int

    @Query("SELECT COUNT(*) FROM events WHERE day >= :from AND day <= :to AND type = :type")
    fun countRange(from: String, to: String, type: String): Flow<Int>

    @Query("SELECT pkg AS name, COUNT(*) AS count FROM events WHERE day >= :from AND day <= :to AND type = :type GROUP BY pkg")
    fun countByPkg(from: String, to: String, type: String): Flow<List<KeyCount>>

    @Query("SELECT targetId AS name, COUNT(*) AS count FROM events WHERE day >= :from AND day <= :to AND type = 'SHORT_BLOCK' GROUP BY targetId")
    fun blocksByTarget(from: String, to: String): Flow<List<KeyCount>>

    @Query("SELECT extra AS name, COUNT(*) AS count FROM events WHERE day >= :from AND day <= :to AND type = 'SESSION_START' AND extra IS NOT NULL GROUP BY extra ORDER BY count DESC")
    fun reasons(from: String, to: String): Flow<List<KeyCount>>

    @Query("DELETE FROM events WHERE ts < :before")
    suspend fun prune(before: Long)
}

@Dao
interface SummaryDao {
    @Upsert
    suspend fun upsert(summary: DaySummary)

    @Query("SELECT * FROM day_summary WHERE day = :day")
    suspend fun get(day: String): DaySummary?

    @Query("SELECT * FROM day_summary WHERE day >= :from AND day <= :to ORDER BY day")
    fun range(from: String, to: String): Flow<List<DaySummary>>

    @Query("SELECT * FROM day_summary ORDER BY day DESC")
    fun all(): Flow<List<DaySummary>>
}

@Database(entities = [EventEntity::class, DaySummary::class], version = 1, exportSchema = false)
abstract class TeleZenDatabase : RoomDatabase() {
    abstract fun events(): EventDao
    abstract fun summaries(): SummaryDao

    companion object {
        fun build(context: Context): TeleZenDatabase =
            Room.databaseBuilder(context, TeleZenDatabase::class.java, "telezen.db").build()
    }
}
