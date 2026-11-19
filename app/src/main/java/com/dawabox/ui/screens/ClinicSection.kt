package com.dawabox.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dawabox.data.AppointmentEntity
import com.dawabox.data.at
import com.dawabox.data.isoMinutes
import com.dawabox.ui.BadRed
import com.dawabox.ui.SectionCard
import com.dawabox.ui.formatTime12
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

fun appointmentWhen(a: AppointmentEntity): String {
    val at = a.at
    val days = ChronoUnit.DAYS.between(LocalDate.now(), at.toLocalDate())
    val day = when (days) {
        0L -> "اليوم"
        1L -> "غداً"
        else -> "${at.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("ar"))} ${at.dayOfMonth} ${at.month.getDisplayName(TextStyle.FULL, Locale("ar"))}"
    }
    return "$day · ${formatTime12("%02d:%02d".format(at.hour, at.minute))}"
}

/** Card on the Today screen for the nearest appointment in the next 7 days. */
@Composable
fun NextAppointmentCard(a: AppointmentEntity) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(14.dp))
            Icon(Icons.Filled.EventAvailable, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
            Column(Modifier.weight(1f).heightIn(min = 64.dp), verticalArrangement = Arrangement.Center) {
                Text("  موعد الطبيب: ${appointmentWhen(a)}", style = MaterialTheme.typography.titleMedium)
                Text("  ${a.doctor}" + if (a.place.isNotBlank()) " · ${a.place}" else "", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Appointment list + import-from-clinic button, shown at the top of "أدويتي". */
@Composable
fun ClinicSection(
    appointments: List<AppointmentEntity>,
    onSave: (AppointmentEntity) -> Unit,
    onDelete: (AppointmentEntity) -> Unit,
    onImport: suspend (String) -> String,
) {
    var editing by remember { mutableStateOf<AppointmentEntity?>(null) }
    var importing by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LocalHospital, null, tint = MaterialTheme.colorScheme.primary)
            Text("  الربط مع العيادة", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
        Text(
            "الصق رمز الوصفة الذي ترسله العيادة من منصة تذكرة، فتُضاف الأدوية والمواعيد وتنبيهاتها تلقائياً.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { importing = true }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Icon(Icons.Filled.ContentPaste, null); Spacer(Modifier.width(8.dp)); Text("استيراد وصفة من العيادة")
        }
    }

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("مواعيد الطبيب", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                editing = AppointmentEntity(doctor = "", dateTime = LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(10, 0)).isoMinutes())
            }) { Text("+ إضافة موعد") }
        }
        if (appointments.isEmpty()) {
            Text("لا توجد مواعيد قادمة. سنذكّرك قبل كل موعد بيوم وبساعتين.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        appointments.forEach { a ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(a.doctor, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        appointmentWhen(a) + if (a.place.isNotBlank()) " · ${a.place}" else "",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { editing = a }) { Icon(Icons.Filled.Edit, "تعديل الموعد") }
                IconButton(onClick = { onDelete(a) }) { Icon(Icons.Filled.Delete, "حذف الموعد", tint = BadRed) }
            }
        }
    }

    }

    editing?.let { a -> AppointmentDialog(a, onDismiss = { editing = null }, onSave = { onSave(it); editing = null }) }
    if (importing) ImportDialog(onImport, onDismiss = { importing = false })
}

@Composable
private fun AppointmentDialog(initial: AppointmentEntity, onDismiss: () -> Unit, onSave: (AppointmentEntity) -> Unit) {
    val context = LocalContext.current
    var doctor by remember { mutableStateOf(initial.doctor) }
    var place by remember { mutableStateOf(initial.place) }
    var notes by remember { mutableStateOf(initial.notes) }
    var at by remember { mutableStateOf(initial.at.takeIf { it != LocalDateTime.MIN } ?: LocalDateTime.now().plusDays(1)) }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "موعد جديد" else "تعديل الموعد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(doctor, { doctor = it }, label = { Text("الطبيب أو العيادة") }, singleLine = true, isError = error && doctor.isBlank())
                OutlinedTextField(place, { place = it }, label = { Text("المكان (اختياري)") }, singleLine = true)
                OutlinedButton(onClick = {
                    DatePickerDialog(context, { _, y, m, d -> at = at.withYear(y).withMonth(m + 1).withDayOfMonth(d) }, at.year, at.monthValue - 1, at.dayOfMonth).show()
                }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.CalendarMonth, null); Text("  ${at.toLocalDate()}") }
                OutlinedButton(onClick = {
                    TimePickerDialog(context, { _, h, m -> at = at.withHour(h).withMinute(m) }, at.hour, at.minute, false).show()
                }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Schedule, null); Text("  " + formatTime12("%02d:%02d".format(at.hour, at.minute))) }
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات (صائم، أحضر التحاليل…)") })
                if (error && !at.isAfter(LocalDateTime.now())) Text("اختر وقتاً في المستقبل.", color = BadRed)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = true
                if (doctor.isNotBlank() && at.isAfter(LocalDateTime.now())) {
                    onSave(initial.copy(doctor = doctor.trim(), place = place.trim(), notes = notes.trim(), dateTime = at.isoMinutes()))
                }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun ImportDialog(onImport: suspend (String) -> String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("استيراد وصفة من العيادة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (result == null) {
                    Text("الصق الرمز الذي يبدأ بـ DAWA-RX: كما وصلك من العيادة (واتساب أو رسالة).", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(code, { code = it }, label = { Text("رمز الوصفة") }, minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.let { code = it }
                    }) { Icon(Icons.Filled.ContentPaste, null); Text("  لصق من الحافظة") }
                } else {
                    Text(result!!, style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        confirmButton = {
            if (result == null) TextButton(enabled = code.isNotBlank() && !busy, onClick = {
                busy = true
                scope.launch { result = onImport(code); busy = false }
            }) { Text(if (busy) "جارٍ الاستيراد…" else "استيراد") }
            else TextButton(onClick = onDismiss) { Text("تم") }
        },
        dismissButton = { if (result == null) TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
