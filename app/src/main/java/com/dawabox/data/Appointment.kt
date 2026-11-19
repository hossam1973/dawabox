package com.dawabox.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** A doctor / clinic visit. Reminded 24 hours and 2 hours before. */
@Entity(tableName = "appointments")
data class AppointmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val doctor: String,
    val place: String = "",
    /** ISO local date-time, minutes precision: yyyy-MM-ddTHH:mm */
    val dateTime: String,
    val notes: String = "",
)

val AppointmentEntity.at: LocalDateTime
    get() = runCatching { LocalDateTime.parse(dateTime) }.getOrDefault(LocalDateTime.MIN)

fun LocalDateTime.isoMinutes(): String = truncatedTo(ChronoUnit.MINUTES).toString().take(16)

@Dao
interface AppointmentDao {
    @Query("SELECT * FROM appointments WHERE dateTime >= :from ORDER BY dateTime")
    fun observeFrom(from: String): Flow<List<AppointmentEntity>>

    @Query("SELECT * FROM appointments WHERE dateTime >= :from ORDER BY dateTime")
    suspend fun upcoming(from: String): List<AppointmentEntity>

    @Query("SELECT * FROM appointments WHERE id = :id")
    suspend fun get(id: Long): AppointmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(a: AppointmentEntity): Long

    @Query("DELETE FROM appointments WHERE id = :id")
    suspend fun delete(id: Long)
}
