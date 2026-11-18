package com.dawabox.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dawabox.R
import com.dawabox.data.MedicationEntity
import com.dawabox.ui.MainActivity

object Notifier {
    const val CH_DOSE = "dose_reminders"
    const val CH_CAREGIVER = "caregiver_alerts"
    const val CH_STOCK = "low_stock"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        nm.createNotificationChannel(
            NotificationChannel(CH_DOSE, "مواعيد الجرعات", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "تنبيه عند حلول موعد كل دواء"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 300, 600)
                setSound(alarmSound, attrs)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_CAREGIVER, "تنبيهات المرافق", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "عند فوات جرعة: إرسال إلى المرافق عبر SMS أو البريد أو واتساب"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_STOCK, "نفاد الدواء", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun doseNotificationId(medId: Long, time: String): Int =
        (medId * 10_000 + time.replace(":", "").toLong()).toInt()

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun showDose(context: Context, med: MedicationEntity, date: String, time: String) {
        if (!canNotify(context)) return
        val id = doseNotificationId(med.id, time)
        fun action(act: String, code: Int) = PendingIntent.getBroadcast(
            context, id * 10 + code,
            Intent(context, DoseActionReceiver::class.java).apply {
                action = act
                putExtra(EXTRA_MED_ID, med.id); putExtra(EXTRA_DATE, date); putExtra(EXTRA_TIME, time)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val body = buildString {
            append(med.dosage)
            append(" · الحجرة ").append(med.compartment)
            if (med.instructions.isNotBlank()) append(" · ").append(med.instructions)
        }
        val n = NotificationCompat.Builder(context, CH_DOSE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("حان موعد ${med.name}")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(false)
            .setOngoing(true)
            .addAction(0, "أخذته", action(DoseActionReceiver.ACTION_TAKEN, 1))
            .addAction(0, "بعد 10 دقائق", action(DoseActionReceiver.ACTION_SNOOZE, 2))
            .addAction(0, "تخطي", action(DoseActionReceiver.ACTION_SKIP, 3))
            .build()
        try { NotificationManagerCompat.from(context).notify(id, n) } catch (_: SecurityException) {}
    }

    fun cancelDose(context: Context, medId: Long, time: String) {
        NotificationManagerCompat.from(context).cancel(doseNotificationId(medId, time))
    }

    /** Caregiver alert with optional tap-to-send actions (email / WhatsApp / SMS app). */
    fun showCaregiver(context: Context, id: Int, title: String, text: String, actions: List<Pair<String, Intent>>) {
        if (!canNotify(context)) return
        val b = NotificationCompat.Builder(context, CH_CAREGIVER)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
        actions.forEachIndexed { i, (label, intent) ->
            val pi = PendingIntent.getActivity(
                context, id * 10 + i, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            b.addAction(0, label, pi)
        }
        try { NotificationManagerCompat.from(context).notify(id, b.build()) } catch (_: SecurityException) {}
    }

    fun showLowStock(context: Context, med: MedicationEntity) {
        if (!canNotify(context)) return
        val n = NotificationCompat.Builder(context, CH_STOCK)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${med.name} على وشك النفاد")
            .setContentText("المتبقي ${med.stock}. اطلب عبوة جديدة من الصيدلية.")
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(context).notify(900_000 + med.id.toInt(), n) } catch (_: SecurityException) {}
    }

    const val EXTRA_MED_ID = "med_id"
    const val EXTRA_DATE = "date"
    const val EXTRA_TIME = "time"
}
