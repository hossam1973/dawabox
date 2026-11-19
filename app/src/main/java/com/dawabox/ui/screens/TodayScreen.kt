package com.dawabox.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dawabox.data.AppointmentEntity
import com.dawabox.data.DoseItem
import com.dawabox.data.DoseStatus
import com.dawabox.data.formEnum
import com.dawabox.ui.BadRed
import com.dawabox.ui.FormBadge
import com.dawabox.ui.OkGreen
import com.dawabox.ui.SectionCard
import com.dawabox.ui.StatusPill
import com.dawabox.ui.TodayUi
import com.dawabox.ui.WarnAmber
import com.dawabox.ui.WeeklyChart
import com.dawabox.ui.formatTime12
import com.dawabox.ui.rememberVoiceInput

@Composable
fun TodayScreen(
    ui: TodayUi,
    patientName: String,
    search: String,
    onSearch: (String) -> Unit,
    onStatus: (DoseItem, DoseStatus) -> Unit,
    contentPadding: PaddingValues,
    nextAppointment: AppointmentEntity? = null,
) {
    val voiceSearch = rememberVoiceInput("قل اسم الدواء") { onSearch(it) }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 8.dp, bottom = contentPadding.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            OutlinedTextField(
                value = search, onValueChange = onSearch, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("ابحث عن دواء بالاسم") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    Row {
                        if (search.isNotEmpty()) IconButton(onClick = { onSearch("") }) { Icon(Icons.Filled.Close, "مسح البحث") }
                        IconButton(onClick = voiceSearch) { Icon(Icons.Filled.Mic, "بحث بالصوت") }
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )
        }
        item { NextDoseCard(ui, patientName, onStatus) }
        nextAppointment?.let { a -> item { NextAppointmentCard(a) } }

        if (ui.lowStock.isNotEmpty()) item {
            Card(colors = CardDefaults.cardColors(containerColor = WarnAmber.copy(alpha = 0.14f)), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.Warning, null, tint = WarnAmber)
                    Text("على وشك النفاد: " + ui.lowStock.joinToString("، ") { "${it.name} (${it.stock})" }, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("أدوية اليوم", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text("${ui.taken} من ${ui.doses.size}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { if (ui.doses.isEmpty()) 0f else ui.taken / ui.doses.size.toFloat() },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)),
                color = OkGreen
            )
        }
        if (ui.doses.isEmpty()) item {
            SectionCard {
                Text(if (search.isBlank()) "لا توجد أدوية مجدولة اليوم." else "لا يوجد دواء بهذا الاسم.", style = MaterialTheme.typography.titleMedium)
                if (search.isBlank()) Text("اضغط زر «إضافة دواء» بالأسفل، أو زر الميكروفون وقل مثلاً: بانادول 500 ملغ مرتين يومياً.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(ui.doses, key = { it.key }) { DoseRow(it, onStatus) }

        item {
            SectionCard {
                Text("الالتزام هذا الأسبوع", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                WeeklyChart(ui.week)
            }
        }
    }
}

@Composable
private fun NextDoseCard(ui: TodayUi, patientName: String, onStatus: (DoseItem, DoseStatus) -> Unit) {
    val next = ui.next
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text(if (patientName.isBlank()) "مرحباً" else "مرحباً، $patientName", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            if (next == null) {
                Text(if (ui.doses.isEmpty()) "لا جرعات اليوم" else "أنهيت جرعات اليوم، أحسنت!", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            } else {
                Text("الجرعة التالية", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                Text("${next.med.name} · ${next.med.dosage}", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${formatTime12(next.time)} · الحجرة ${next.med.compartment}" + if (next.med.instructions.isNotBlank()) " · ${next.med.instructions}" else "",
                    color = Color.White, style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { onStatus(next, DoseStatus.TAKEN) },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Filled.Check, null); Spacer(Modifier.width(8.dp))
                    Text("أخذت الدواء", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun DoseRow(dose: DoseItem, onStatus: (DoseItem, DoseStatus) -> Unit) {
    val done = dose.status == DoseStatus.TAKEN || dose.status == DoseStatus.MISSED
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FormBadge(dose.med.formEnum)
            Column(Modifier.weight(1f)) {
                Text(dose.med.name, style = MaterialTheme.typography.titleMedium)
                Text("${dose.med.dosage} · ${formatTime12(dose.time)} · الحجرة ${dose.med.compartment}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp)); StatusPill(dose.status)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!done) {
                Button(onClick = { onStatus(dose, DoseStatus.TAKEN) }, modifier = Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = OkGreen)) {
                    Icon(Icons.Filled.Check, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("تم")
                }
                OutlinedButton(onClick = { onStatus(dose, DoseStatus.MISSED) }, modifier = Modifier.weight(1f).height(48.dp)) {
                    Icon(Icons.Filled.Close, null, Modifier.size(20.dp), tint = BadRed); Spacer(Modifier.width(6.dp)); Text("فائتة", color = BadRed)
                }
            } else {
                OutlinedButton(onClick = { onStatus(dose, DoseStatus.UPCOMING) }, modifier = Modifier.height(44.dp)) {
                    Icon(Icons.Filled.Undo, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("تراجع")
                }
            }
        }
    }
}
