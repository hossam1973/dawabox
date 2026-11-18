package com.dawabox.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime

/** Dosage form: drives the icon shown next to each medication. */
enum class MedForm(val label: String) {
    TABLET("قرص"), CAPSULE("كبسولة"), SYRUP("شراب"), INJECTION("حقنة"),
    DROPS("قطرة"), INHALER("بخاخ")
}

enum class Frequency(val label: String) {
    DAILY("يومياً"), WEEKLY("أسبوعياً"), AS_NEEDED("عند اللزوم")
}

/** Alert channels a medication (or the whole app) can use. */
enum class AlertChannel(val label: String) {
    APP("إشعار الهاتف"), VOICE("صوت العلبة"), SMS("رسالة SMS"),
    EMAIL("بريد إلكتروني"), WHATSAPP("واتساب")
}

enum class DoseStatus { UPCOMING, DUE, LATE, TAKEN, MISSED }

@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dosage: String,
    val form: String = MedForm.TABLET.name,
    val frequency: String = Frequency.DAILY.name,
    /** Comma separated HH:mm list, e.g. "08:00,20:00". */
    val times: String = "08:00",
    /** ISO date, yyyy-MM-dd. */
    val startDate: String = LocalDate.now().toString(),
    /** Compartment number in the pillbox, 1..7. */
    val compartment: Int = 1,
    val instructions: String = "",
    val stock: Int = 30,
    /** Comma separated AlertChannel names. */
    val channels: String = "APP,VOICE,SMS",
    val active: Boolean = true,
)

@Entity(
    tableName = "dose_logs",
    indices = [Index(value = ["medId", "date", "time"], unique = true)]
)
data class DoseLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medId: Long,
    val medName: String,
    val date: String,
    val time: String,
    /** TAKEN or MISSED */
    val status: String,
    val at: Long = System.currentTimeMillis(),
    /** How the dose was logged: app, notification, voice, pillbox */
    val source: String = "app",
)

// ---- helpers (kept outside the entities so Room ignores them) ----

val MedicationEntity.timeList: List<String>
    get() = times.split(',').map { it.trim() }.filter { it.matches(Regex("\\d{2}:\\d{2}")) }.sorted()

val MedicationEntity.formEnum: MedForm
    get() = runCatching { MedForm.valueOf(form) }.getOrDefault(MedForm.TABLET)

val MedicationEntity.frequencyEnum: Frequency
    get() = runCatching { Frequency.valueOf(frequency) }.getOrDefault(Frequency.DAILY)

val MedicationEntity.channelSet: Set<AlertChannel>
    get() = channels.split(',').mapNotNull { runCatching { AlertChannel.valueOf(it.trim()) }.getOrNull() }.toSet()

fun MedicationEntity.appliesOn(date: LocalDate): Boolean {
    val start = runCatching { LocalDate.parse(startDate) }.getOrDefault(LocalDate.MIN)
    if (!active || date.isBefore(start)) return false
    return when (frequencyEnum) {
        Frequency.DAILY -> true
        Frequency.WEEKLY -> date.dayOfWeek == start.dayOfWeek
        Frequency.AS_NEEDED -> false
    }
}

fun defaultTimes(count: Int): List<String> = when (count) {
    1 -> listOf("08:00")
    2 -> listOf("08:00", "20:00")
    3 -> listOf("08:00", "14:00", "20:00")
    else -> listOf("06:00", "12:00", "18:00", "22:00")
}

/** One scheduled dose on a given day. */
data class DoseItem(
    val med: MedicationEntity,
    val date: LocalDate,
    val time: String,
    val status: DoseStatus,
) {
    val key get() = "${med.id}@$date@$time"
}

/** Minutes after the dose time before a dose counts as late / missed. */
const val LATE_AFTER_MIN = 30L

/**
 * Builds the dose list for [date]. [now] is only used for today, to tell
 * upcoming doses from late ones.
 */
fun dosesFor(
    date: LocalDate,
    meds: List<MedicationEntity>,
    logs: List<DoseLogEntity>,
    now: LocalTime? = null,
    today: LocalDate = LocalDate.now(),
): List<DoseItem> {
    val dayLogs = logs.filter { it.date == date.toString() }
    return meds.filter { it.appliesOn(date) }.flatMap { med ->
        med.timeList.map { t ->
            val log = dayLogs.firstOrNull { it.medId == med.id && it.time == t }
            val status = when {
                log?.status == "TAKEN" -> DoseStatus.TAKEN
                log?.status == "MISSED" -> DoseStatus.MISSED
                date.isBefore(today) -> DoseStatus.MISSED
                date.isAfter(today) || now == null -> DoseStatus.UPCOMING
                else -> {
                    val dt = LocalTime.parse(t)
                    when {
                        now.isBefore(dt) -> DoseStatus.UPCOMING
                        now.isBefore(dt.plusMinutes(LATE_AFTER_MIN)) -> DoseStatus.DUE
                        else -> DoseStatus.LATE
                    }
                }
            }
            DoseItem(med, date, t, status)
        }
    }.sortedBy { it.time }
}

/** Taken / scheduled for one day. */
data class DayCompliance(val date: LocalDate, val taken: Int, val missed: Int, val total: Int) {
    val percent: Int get() = if (total == 0) 0 else (taken * 100 / total)
}

fun complianceFor(date: LocalDate, meds: List<MedicationEntity>, logs: List<DoseLogEntity>, now: LocalTime?): DayCompliance {
    val d = dosesFor(date, meds, logs, now)
    return DayCompliance(
        date,
        taken = d.count { it.status == DoseStatus.TAKEN },
        missed = d.count { it.status == DoseStatus.MISSED || it.status == DoseStatus.LATE },
        total = d.size,
    )
}
