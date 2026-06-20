package com.crazystudio.sportrecorder.probe

import androidx.room.ConstructedBy
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

// THROWAWAY: probes whether Room-KMP + KSP compile/link on the iOS native targets at this
// toolchain (AGP 9.2 / Kotlin 2.3). If the macOS CI builds this, the real schema migration is
// safe to do; this file is then deleted.

@Entity
data class ProbeRow(@PrimaryKey val id: Long, val value: String)

@Dao
interface ProbeDao {
    @Insert
    suspend fun insert(row: ProbeRow)

    @Query("SELECT value FROM ProbeRow WHERE id = :id")
    suspend fun valueOf(id: Long): String?
}

@Database(entities = [ProbeRow::class], version = 1)
@ConstructedBy(ProbeDatabaseConstructor::class)
abstract class ProbeDatabase : RoomDatabase() {
    abstract fun probeDao(): ProbeDao
}

// Room's KSP generates the per-platform actual for this expect object.
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object ProbeDatabaseConstructor : RoomDatabaseConstructor<ProbeDatabase> {
    override fun initialize(): ProbeDatabase
}
