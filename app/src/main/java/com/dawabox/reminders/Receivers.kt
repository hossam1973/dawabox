package com.dawabox.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dawabox.data.AlertChannel
import com.dawabox.data.Repository
import com.dawabox.data.SettingsStore
import com.dawabox.data.channelSet
import com.dawabox.voice.Speaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalDateTime

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** Runs [block] off the main thread while keeping the receiver alive. */
private fun BroadcastReceiver.async(block: suspend () -> Unit) {
    val result = goAsync()
    receiverScope.launch {
        try { block() } finally { result.finish() }
    }
}

/** Fires at the dose time: notification + spoken reminder, then schedules the next one. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = async {
        val medId = intent.getLongExtra(Notifier.EXTRA_MED_ID, -1)
        val time = intent.getStringExtra(Notifier.EXTRA_TIME) ?: return@async
        val repo = Repository.get(context)
        val med = repo.medication(medId) ?: return@async
        val today = LocalDate.now()

        // Schedule the next occurrence first so a crash below never breaks the chain.
        ReminderScheduler.nextTrigger(med, time, LocalDateTime.now().plusMinutes(1))
            ?.let { ReminderScheduler.scheduleAt(context, med.id, time, it) }

        if (repo.statusOf(med.id, today, time) == "TAKEN") return@async

        val settings = SettingsStore.get(context).current
        val channels = med.channelSet intersect settings.enabled

        if (AlertChannel.APP in channels || channels.isEmpty()) {
            Notifier.showDose(context, med, today.toString(), time)
        }
        if (AlertChannel.VOICE in channels) {
            val who = settings.patientName.ifBlank { "" }
            val text = buildString {
                append("تنبيه")
                if (who.isNotBlank()) append(" يا ").append(who)
                append(". حان موعد ").append(med.name).append(" ").append(med.dosage)
                append(" من الحجرة رقم ").append(med.compartment).append(". ")
                if (med.instructions.isNotBlank()) append(med.instructions).append(". ")
                append("بعد أخذه قل: أخذت الدواء، أو اضغط زر أخذته.")
            }
            // Wait (max 20 s) for speech to finish before the receiver ends.
            withTimeoutOrNull(20_000) {
                var done = false
                Speaker.say(context, text) { done = true }
                while (!done) delay(200)
            }
        }
        ReminderScheduler.enqueueMissedCheck(context, med.id, today.toString(), time, settings.missedAfterMinutes.toLong())
    }
    companion object {
        const val ACTION_FIRE = "com.dawabox.FIRE"
    }
}

/** Notification buttons: taken / snooze / skip. */
class DoseActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = async {
        val medId = intent.getLongExtra(Notifier.EXTRA_MED_ID, -1)
        val time = intent.getStringExtra(Notifier.EXTRA_TIME) ?: return@async
        val date = intent.getStringExtra(Notifier.EXTRA_DATE) ?: LocalDate.now().toString()
        val repo = Repository.get(context)
        Notifier.cancelDose(context, medId, time)
        when (intent.action) {
            ACTION_TAKEN -> {
                repo.markTaken(medId, LocalDate.parse(date), time, source = "notification")
                ReminderScheduler.cancelMissedCheck(context, medId, date, time)
                repo.medication(medId)?.let { med ->
                    if (med.stock <= SettingsStore.get(context).current.lowStockAt) Notifier.showLowStock(context, med)
                }
            }
            ACTION_SNOOZE -> ReminderScheduler.snooze(context, medId, time, 10)
            ACTION_SKIP -> {
                repo.markMissed(medId, LocalDate.parse(date), time, source = "skipped")
                ReminderScheduler.cancelMissedCheck(context, medId, date, time)
                repo.medication(medId)?.let { CaregiverAlerts.doseMissed(context, it, time, skipped = true) }
            }
        }
    }

    companion object {
        const val ACTION_TAKEN = "com.dawabox.TAKEN"
        const val ACTION_SNOOZE = "com.dawabox.SNOOZE"
        const val ACTION_SKIP = "com.dawabox.SKIP"
    }
}

/** Alarms are wiped on reboot / update: put them back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = async {
        ReminderScheduler.scheduleAll(context, Repository.get(context).allMedications())
    }
}
