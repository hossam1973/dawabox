package com.dawabox.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dawabox.data.AlertChannel
import com.dawabox.data.AlertSettings
import com.dawabox.reminders.CaregiverAlerts
import com.dawabox.reminders.Notifier
import com.dawabox.reminders.ReminderScheduler
import com.dawabox.ui.ChipRow
import com.dawabox.ui.OkGreen
import com.dawabox.ui.SectionCard
import com.dawabox.ui.WarnAmber

@Composable
fun SettingsScreen(
    settings: AlertSettings,
    onChange: (AlertSettings) -> Unit,
    onTest: ((List<String>) -> Unit) -> Unit,
    contentPadding: PaddingValues,
    clinicReport: () -> String = { "" },
) {
    val context = LocalContext.current
    var draft by remember(settings) { mutableStateOf(settings) }
    var report by remember { mutableStateOf<List<String>>(emptyList()) }
    var refresh by remember { mutableStateOf(0) }
    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 8.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionCard {
                Text("المريض والمرافق", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Field("اسم المريض", draft.patientName) { draft = draft.copy(patientName = it) }
                Field("اسم المرافق", draft.caregiverName) { draft = draft.copy(caregiverName = it) }
                Field("جوال المرافق (مع رمز الدولة)", draft.caregiverPhone, KeyboardType.Phone) { draft = draft.copy(caregiverPhone = it) }
                Field("بريد المرافق", draft.caregiverEmail, KeyboardType.Email) { draft = draft.copy(caregiverEmail = it) }
                Button(onClick = { onChange(draft) }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text("حفظ") }
            }
        }
        item {
            SectionCard {
                Text("وسائل التنبيه", style = MaterialTheme.typography.titleMedium)
                Text("تُستخدم للتذكير بالجرعة، ولإبلاغ المرافق عند فوات جرعة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AlertChannel.entries.forEach { ch ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(ch.label, style = MaterialTheme.typography.bodyLarge)
                            Text(channelHint(ch), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = ch in settings.enabled, onCheckedChange = { on ->
                            onChange(settings.copy(enabled = if (on) settings.enabled + ch else settings.enabled - ch))
                        })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("اعتبر الجرعة فائتة بعد", style = MaterialTheme.typography.bodyLarge)
                ChipRow(listOf(15, 30, 60, 120), { it == settings.missedAfterMinutes }, { "$it دقيقة" }) {
                    onChange(settings.copy(missedAfterMinutes = it))
                }
                Spacer(Modifier.height(10.dp))
                Button(onClick = { onTest { report = it } }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                    Text("جرّب كل وسائل التنبيه الآن")
                }
                report.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
        }
        item {
            SectionCard {
                Text("مشاركة التقرير مع العيادة", style = MaterialTheme.typography.titleMedium)
                Text(
                    "يرسل ملخص التزامك لآخر 7 أيام ومخزون أدويتك، مع رمز تلصقه العيادة في منصة تذكرة فيُحدَّث ملفك عندهم.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, "تقرير الالتزام بالدواء")
                        .putExtra(Intent.EXTRA_TEXT, clinicReport())
                    context.startActivity(Intent.createChooser(send, "إرسال التقرير إلى العيادة"))
                }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                    Text("إرسال تقرير الالتزام")
                }
            }
        }
        item {
          key(refresh) {
            SectionCard {
                Text("الأذونات", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                PermissionRow("الإشعارات", Notifier.canNotify(context)) {
                    if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
                PermissionRow("المنبّه في الوقت الدقيق", ReminderScheduler.canScheduleExact(context)) {
                    if (Build.VERSION.SDK_INT >= 31) context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                    )
                }
                PermissionRow("إرسال SMS تلقائياً", CaregiverAlerts.hasSmsPermission(context)) {
                    smsPermission.launch(Manifest.permission.SEND_SMS)
                }
                TextButton(onClick = { refresh++ }) { Text("تحديث الحالة") }
            }
          }
        }
    }
}

private fun channelHint(ch: AlertChannel) = when (ch) {
    AlertChannel.APP -> "إشعار بصوت المنبه واهتزاز، مع أزرار أخذته / تأجيل"
    AlertChannel.VOICE -> "العلبة تنطق اسم الدواء والجرعة والحجرة"
    AlertChannel.SMS -> "رسالة تلقائية لجوال المرافق عند فوات الجرعة"
    AlertChannel.EMAIL -> "رسالة جاهزة للمرافق بضغطة من الإشعار"
    AlertChannel.WHATSAPP -> "رسالة واتساب جاهزة بضغطة من الإشعار"
}

@Composable
private fun Field(label: String, value: String, type: KeyboardType = KeyboardType.Text, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onValue, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type), modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onRequest: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (granted) Text("مفعّل", color = OkGreen, style = MaterialTheme.typography.labelLarge)
        else OutlinedButton(onClick = onRequest) { Text("تفعيل", color = WarnAmber) }
    }
}
