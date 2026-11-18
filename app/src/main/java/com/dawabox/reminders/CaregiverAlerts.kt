package com.dawabox.reminders

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dawabox.data.AlertChannel
import com.dawabox.data.AlertSettings
import com.dawabox.data.MedicationEntity
import com.dawabox.data.Repository
import com.dawabox.data.SettingsStore
import com.dawabox.data.channelSet
import java.time.LocalDate

/** Runs N minutes after a dose time. If the dose still isn't taken, it is marked missed and the caregiver is told. */
class MissedDoseWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val medId = inputData.getLong(Notifier.EXTRA_MED_ID, -1)
        val date = inputData.getString(Notifier.EXTRA_DATE) ?: return Result.failure()
        val time = inputData.getString(Notifier.EXTRA_TIME) ?: return Result.failure()
        val repo = Repository.get(applicationContext)
        val med = repo.medication(medId) ?: return Result.success()
        if (repo.statusOf(medId, LocalDate.parse(date), time) == "TAKEN") return Result.success()
        repo.markMissed(medId, LocalDate.parse(date), time)
        Notifier.cancelDose(applicationContext, medId, time)
        CaregiverAlerts.doseMissed(applicationContext, med, time, skipped = false)
        return Result.success()
    }
}

/** Results of one dispatch, shown in the settings screen after a test. */
data class DispatchReport(val lines: List<String>)

/**
 * Sends caregiver alerts over every enabled channel.
 *  - SMS: sent automatically when SEND_SMS is granted (no server needed).
 *  - Email / WhatsApp: Android does not allow silent sending, so the alert
 *    notification carries one-tap buttons that open the ready-to-send message.
 *    Automatic email needs a backend (see README).
 */
object CaregiverAlerts {

    fun message(settings: AlertSettings, med: MedicationEntity, time: String, skipped: Boolean): String {
        val patient = settings.patientName.ifBlank { "المريض" }
        val what = if (skipped) "تخطّى" else "لم يأخذ"
        return "تنبيه من علبة الدواء: $patient $what جرعة ${med.name} ${med.dosage} المقررة الساعة $time. يرجى الاطمئنان عليه."
    }

    fun doseMissed(context: Context, med: MedicationEntity, time: String, skipped: Boolean): DispatchReport {
        val settings = SettingsStore.get(context).current
        val channels = med.channelSet intersect settings.enabled
        return dispatch(context, settings, channels, "جرعة فائتة: ${med.name}", message(settings, med, time, skipped), med.id.toInt())
    }

    fun test(context: Context): DispatchReport {
        val settings = SettingsStore.get(context).current
        val msg = "رسالة تجريبية من علبة الدواء: التنبيهات تعمل بشكل صحيح."
        return dispatch(context, settings, settings.enabled, "تجربة التنبيهات", msg, 777)
    }

    private fun dispatch(
        context: Context, s: AlertSettings, channels: Set<AlertChannel>,
        title: String, msg: String, id: Int,
    ): DispatchReport {
        val report = mutableListOf<String>()
        val actions = mutableListOf<Pair<String, Intent>>()
        val phone = s.caregiverPhone.filter { it.isDigit() || it == '+' }

        if (AlertChannel.SMS in channels) {
            when {
                phone.isBlank() -> report += "SMS: لم يُحدد رقم المرافق"
                hasSmsPermission(context) && sendSms(context, phone, msg) -> report += "SMS: أُرسلت إلى $phone"
                else -> {
                    actions += "إرسال SMS" to Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).putExtra("sms_body", msg)
                    report += "SMS: بحاجة لإذن الإرسال، أُضيف زر إرسال للإشعار"
                }
            }
        }
        if (AlertChannel.WHATSAPP in channels) {
            if (phone.isBlank()) report += "واتساب: لم يُحدد رقم المرافق"
            else {
                val digits = phone.trimStart('+').trimStart('0')
                actions += "واتساب" to Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits?text=${Uri.encode(msg)}"))
                report += "واتساب: زر إرسال في الإشعار"
            }
        }
        if (AlertChannel.EMAIL in channels) {
            if (s.caregiverEmail.isBlank()) report += "البريد: لم يُحدد بريد المرافق"
            else {
                actions += "بريد" to Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(s.caregiverEmail))
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    putExtra(Intent.EXTRA_TEXT, msg)
                }
                report += "البريد: زر إرسال في الإشعار"
            }
        }
        if (AlertChannel.APP in channels || actions.isNotEmpty()) {
            Notifier.showCaregiver(context, 500_000 + id, title, msg, actions.take(3))
            if (AlertChannel.APP in channels) report += "إشعار الهاتف: ظهر"
        }
        if (AlertChannel.VOICE in channels) {
            com.dawabox.voice.Speaker.say(context, msg)
            report += "صوت العلبة: تم النطق"
        }
        return DispatchReport(report)
    }

    fun hasSmsPermission(context: Context) =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun sendSms(context: Context, phone: String, msg: String): Boolean = try {
        val sms: SmsManager = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java)
        else SmsManager.getDefault()
        sms.sendMultipartTextMessage(phone, null, sms.divideMessage(msg), null, null)
        true
    } catch (e: Exception) {
        false
    }
}
