package com.dawabox.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.dawabox.data.MedicationEntity
import com.dawabox.data.appliesOn
import com.dawabox.data.timeList
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Exact alarms (AlarmManager) fire the reminder at the dose time, even in
 * Doze. WorkManager then runs the missed-dose check, which survives app
 * restarts and reboots.
 */
object ReminderScheduler {

    private fun alarmManager(context: Context) = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || alarmManager(context).canScheduleExactAlarms()

    private fun requestCode(medId: Long, time: String) = Notifier.doseNotificationId(medId, time)

    private fun pending(context: Context, medId: Long, time: String, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context, requestCode(medId, time),
            Intent(context, ReminderReceiver::class.java).apply {
                action = ReminderReceiver.ACTION_FIRE
                putExtra(Notifier.EXTRA_MED_ID, medId)
                putExtra(Notifier.EXTRA_TIME, time)
            },
            flags or PendingIntent.FLAG_IMMUTABLE
        )

    /** Next moment (after now) at which [time] applies for [med]. */
    fun nextTrigger(med: MedicationEntity, time: String, from: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        val t = LocalTime.parse(time)
        var date: LocalDate = from.toLocalDate()
        repeat(8) {
            val candidate = LocalDateTime.of(date, t)
            if (candidate.isAfter(from) && med.appliesOn(date)) return candidate
            date = date.plusDays(1)
        }
        return null
    }

    fun scheduleAt(context: Context, medId: Long, time: String, at: LocalDateTime) {
        val pi = pending(context, medId, time, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val am = alarmManager(context)
        if (canScheduleExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    fun schedule(context: Context, med: MedicationEntity) {
        med.timeList.forEach { time ->
            val next = nextTrigger(med, time)
            if (next != null) scheduleAt(context, med.id, time, next) else cancel(context, med.id, time)
        }
    }

    fun scheduleAll(context: Context, meds: List<MedicationEntity>) = meds.forEach { schedule(context, it) }

    fun cancel(context: Context, medId: Long, time: String) {
        pending(context, medId, time, PendingIntent.FLAG_NO_CREATE)?.let {
            alarmManager(context).cancel(it); it.cancel()
        }
    }

    /** Cancels every alarm of [med] (call with the old version before editing its times). */
    fun cancelAll(context: Context, med: MedicationEntity) {
        med.timeList.forEach { cancel(context, med.id, it) }
    }

    fun snooze(context: Context, medId: Long, time: String, minutes: Long = 10) {
        scheduleAt(context, medId, time, LocalDateTime.now().plusMinutes(minutes))
    }

    // ---- WorkManager: missed dose check ----

    private fun missedWorkName(medId: Long, date: String, time: String) = "missed-$medId-$date-$time"

    fun enqueueMissedCheck(context: Context, medId: Long, date: String, time: String, afterMinutes: Long) {
        val req = OneTimeWorkRequestBuilder<MissedDoseWorker>()
            .setInitialDelay(afterMinutes, TimeUnit.MINUTES)
            .setInputData(
                Data.Builder()
                    .putLong(Notifier.EXTRA_MED_ID, medId)
                    .putString(Notifier.EXTRA_DATE, date)
                    .putString(Notifier.EXTRA_TIME, time)
                    .build()
            )
            .addTag("missed-check")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(missedWorkName(medId, date, time), ExistingWorkPolicy.KEEP, req)
    }

    fun cancelMissedCheck(context: Context, medId: Long, date: String, time: String) {
        WorkManager.getInstance(context).cancelUniqueWork(missedWorkName(medId, date, time))
    }
}

/** Alarms 24 h and 2 h before each doctor appointment. */
object AppointmentReminders {
    /** Minutes before the appointment. */
    val OFFSETS = listOf(24 * 60L, 120L)
    const val EXTRA_APT_ID = "apt_id"
    const val EXTRA_OFFSET = "offset_min"

    private fun pending(context: Context, aptId: Long, index: Int, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context, (700_000_000L + aptId * 10 + index).toInt(),
            Intent(context, AppointmentReceiver::class.java).apply {
                putExtra(EXTRA_APT_ID, aptId)
                putExtra(EXTRA_OFFSET, OFFSETS[index])
            },
            flags or PendingIntent.FLAG_IMMUTABLE
        )

    fun schedule(context: Context, apt: com.dawabox.data.AppointmentEntity) {
        val am = context.getSystemService(AlarmManager::class.java)
        val now = LocalDateTime.now()
        val at = runCatching { LocalDateTime.parse(apt.dateTime) }.getOrNull() ?: return
        OFFSETS.forEachIndexed { i, before ->
            val fire = at.minusMinutes(before)
            val pi = pending(context, apt.id, i, PendingIntent.FLAG_UPDATE_CURRENT) ?: return@forEachIndexed
            if (!fire.isAfter(now)) { am.cancel(pi); return@forEachIndexed }
            val millis = fire.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (ReminderScheduler.canScheduleExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    fun cancel(context: Context, aptId: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        OFFSETS.indices.forEach { i -> pending(context, aptId, i, PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it); it.cancel() } }
    }
}
