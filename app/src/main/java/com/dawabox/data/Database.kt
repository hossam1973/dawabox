package com.dawabox.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications WHERE active = 1 ORDER BY name")
    fun observeMedications(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications WHERE active = 1")
    suspend fun allMedications(): List<MedicationEntity>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun medication(id: Long): MedicationEntity?

    @Query("SELECT * FROM medications WHERE active = 1 AND name LIKE '%' || :query || '%' ORDER BY name")
    fun search(query: String): Flow<List<MedicationEntity>>

    /** REPLACE keeps the given id, so this both inserts and updates. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(med: MedicationEntity): Long

    @Query("DELETE FROM medications WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE medications SET stock = MAX(stock + :delta, 0) WHERE id = :id")
    suspend fun changeStock(id: Long, delta: Int)
}

@Dao
interface DoseLogDao {
    @Query("SELECT * FROM dose_logs WHERE date >= :from ORDER BY date DESC, time DESC")
    fun observeSince(from: String): Flow<List<DoseLogEntity>>

    @Query("SELECT * FROM dose_logs WHERE medId = :medId AND date = :date AND time = :time LIMIT 1")
    suspend fun find(medId: Long, date: String, time: String): DoseLogEntity?

    @Query("SELECT * FROM dose_logs WHERE date = :date")
    suspend fun forDate(date: String): List<DoseLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(log: DoseLogEntity)

    @Query("DELETE FROM dose_logs WHERE medId = :medId AND date = :date AND time = :time")
    suspend fun remove(medId: Long, date: String, time: String)
}

@Database(entities = [MedicationEntity::class, DoseLogEntity::class, AppointmentEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medications(): MedicationDao
    abstract fun doseLogs(): DoseLogDao
    abstract fun appointments(): AppointmentDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** v2 adds doctor appointments; keeps existing medications and logs. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `appointments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`doctor` TEXT NOT NULL, `place` TEXT NOT NULL, `dateTime` TEXT NOT NULL, `notes` TEXT NOT NULL)"
                )
            }
        }
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "dawabox.db")
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
        }
    }
}
