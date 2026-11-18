package com.dawabox.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.LocalTime

/** Single entry point for medication and dose data. */
class Repository private constructor(context: Context) {
    private val db = AppDatabase.get(context)
    private val meds = db.medications()
    private val logs = db.doseLogs()

    val medications: Flow<List<MedicationEntity>> = meds.observeMedications()
    fun logsSince(from: LocalDate): Flow<List<DoseLogEntity>> = logs.observeSince(from.toString())
    fun search(query: String) = meds.search(query)

    suspend fun allMedications() = meds.allMedications()
    suspend fun medication(id: Long) = meds.medication(id)
    suspend fun save(med: MedicationEntity): Long = meds.save(med)
    suspend fun delete(id: Long) = meds.delete(id)

    suspend fun statusOf(medId: Long, date: LocalDate, time: String) = logs.find(medId, date.toString(), time)?.status

    /** Records a taken dose and takes one unit out of stock. Returns false if it was already taken. */
    suspend fun markTaken(medId: Long, date: LocalDate, time: String, source: String): Boolean {
        val med = meds.medication(medId) ?: return false
        val existing = logs.find(medId, date.toString(), time)
        if (existing?.status == "TAKEN") return false
        logs.put(DoseLogEntity(id = existing?.id ?: 0, medId = medId, medName = med.name, date = date.toString(), time = time, status = "TAKEN", source = source))
        meds.changeStock(medId, -1)
        return true
    }

    suspend fun markMissed(medId: Long, date: LocalDate, time: String, source: String = "system") {
        val med = meds.medication(medId) ?: return
        val existing = logs.find(medId, date.toString(), time)
        if (existing?.status == "TAKEN") meds.changeStock(medId, +1)
        logs.put(DoseLogEntity(id = existing?.id ?: 0, medId = medId, medName = med.name, date = date.toString(), time = time, status = "MISSED", source = source))
    }

    suspend fun clearStatus(medId: Long, date: LocalDate, time: String) {
        val existing = logs.find(medId, date.toString(), time) ?: return
        if (existing.status == "TAKEN") meds.changeStock(medId, +1)
        logs.remove(medId, date.toString(), time)
    }

    suspend fun todayDoses(): List<DoseItem> {
        val today = LocalDate.now()
        return dosesFor(today, meds.allMedications(), logs.forDate(today.toString()), LocalTime.now())
    }

    companion object {
        @Volatile private var instance: Repository? = null
        fun get(context: Context): Repository = instance ?: synchronized(this) {
            instance ?: Repository(context.applicationContext).also { instance = it }
        }
    }
}

/** Patient + caregiver contact details and the global alert channel switches. */
data class AlertSettings(
    val patientName: String = "",
    val caregiverName: String = "",
    val caregiverPhone: String = "",
    val caregiverEmail: String = "",
    val enabled: Set<AlertChannel> = setOf(AlertChannel.APP, AlertChannel.VOICE, AlertChannel.SMS),
    val missedAfterMinutes: Int = 30,
    val lowStockAt: Int = 5,
)

class SettingsStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("alert_settings", Context.MODE_PRIVATE)
    private val _flow = MutableStateFlow(read())
    val flow: StateFlow<AlertSettings> = _flow
    val current: AlertSettings get() = _flow.value

    private fun read() = AlertSettings(
        patientName = prefs.getString("patient", "") ?: "",
        caregiverName = prefs.getString("cg_name", "") ?: "",
        caregiverPhone = prefs.getString("cg_phone", "") ?: "",
        caregiverEmail = prefs.getString("cg_email", "") ?: "",
        enabled = (prefs.getString("channels", null) ?: "APP,VOICE,SMS").split(',')
            .mapNotNull { runCatching { AlertChannel.valueOf(it) }.getOrNull() }.toSet(),
        missedAfterMinutes = prefs.getInt("missed_after", 30),
        lowStockAt = prefs.getInt("low_stock", 5),
    )

    fun update(s: AlertSettings) {
        prefs.edit()
            .putString("patient", s.patientName.trim())
            .putString("cg_name", s.caregiverName.trim())
            .putString("cg_phone", s.caregiverPhone.trim())
            .putString("cg_email", s.caregiverEmail.trim())
            .putString("channels", s.enabled.joinToString(",") { it.name })
            .putInt("missed_after", s.missedAfterMinutes)
            .putInt("low_stock", s.lowStockAt)
            .apply()
        _flow.value = s
    }

    companion object {
        @Volatile private var instance: SettingsStore? = null
        fun get(context: Context): SettingsStore = instance ?: synchronized(this) {
            instance ?: SettingsStore(context.applicationContext).also { instance = it }
        }
    }
}
