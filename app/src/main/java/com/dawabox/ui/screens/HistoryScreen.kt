package com.dawabox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dawabox.data.DoseLogEntity
import com.dawabox.data.DoseStatus
import com.dawabox.data.MedicationEntity
import com.dawabox.data.complianceFor
import com.dawabox.data.dosesFor
import com.dawabox.data.formEnum
import com.dawabox.ui.BadRed
import com.dawabox.ui.FormBadge
import com.dawabox.ui.OkGreen
import com.dawabox.ui.SectionCard
import com.dawabox.ui.StatusPill
import com.dawabox.ui.WarnAmber
import com.dawabox.ui.formatTime12
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Calendar of adherence: each day coloured by the share of doses taken. Tap a day for its log. */
@Composable
fun HistoryScreen(meds: List<MedicationEntity>, logs: List<DoseLogEntity>, contentPadding: PaddingValues) {
    val today = LocalDate.now()
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(today) }
    val ar = Locale("ar")

    val monthDays = (1..month.lengthOfMonth()).map { month.atDay(it) }
    val stats = monthDays.associateWith { d ->
        if (d.isAfter(today)) null else complianceFor(d, meds, logs, if (d == today) LocalTime.now() else null)
    }
    val past = stats.values.filterNotNull().filter { it.total > 0 }
    val monthPct = if (past.sumOf { it.total } == 0) null else past.sumOf { it.taken } * 100 / past.sumOf { it.total }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 8.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "الشهر السابق") }
                    Text(
                        "${month.month.getDisplayName(TextStyle.FULL, ar)} ${month.year}",
                        style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), textAlign = TextAlign.Center
                    )
                    IconButton(onClick = { month = month.plusMonths(1) }, enabled = month < YearMonth.now()) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "الشهر التالي")
                    }
                }
                Text(
                    monthPct?.let { "نسبة الالتزام هذا الشهر: $it%" } ?: "لا توجد جرعات مسجلة في هذا الشهر",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(10.dp))
                // Week starts on Saturday, as in most Arab countries.
                val headers = listOf(6, 7, 1, 2, 3, 4, 5).map { java.time.DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, ar) }
                Row(Modifier.fillMaxWidth()) {
                    headers.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium) }
                }
                val offset = (month.atDay(1).dayOfWeek.value + 1) % 7 // Saturday = 0
                val cells: List<LocalDate?> = List(offset) { null } + monthDays
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        (0 until 7).forEach { i ->
                            val d = week.getOrNull(i)
                            Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp)) {
                                if (d != null) DayCell(d, stats[d]?.let { if (it.total == 0) null else it.percent }, d == selected, d == today) { selected = d }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Legend(OkGreen, "كاملة"); Legend(WarnAmber, "جزئية"); Legend(BadRed, "فائتة")
                }
            }
        }
        item {
            Text(
                "${selected.dayOfWeek.getDisplayName(TextStyle.FULL, ar)} ${selected.dayOfMonth} ${selected.month.getDisplayName(TextStyle.FULL, ar)}",
                style = MaterialTheme.typography.titleLarge
            )
        }
        val dayDoses = dosesFor(selected, meds, logs, if (selected == today) LocalTime.now() else null)
        val dayLogs = logs.filter { it.date == selected.toString() }
        if (dayDoses.isEmpty() && dayLogs.isEmpty()) item {
            Text("لا توجد جرعات في هذا اليوم.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(dayDoses, key = { it.key }) { d ->
            val log = dayLogs.firstOrNull { it.medId == d.med.id && it.time == d.time }
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FormBadge(d.med.formEnum, 38)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("${d.med.name} · ${d.med.dosage}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "موعدها ${formatTime12(d.time)}" + (log?.takeIf { d.status == DoseStatus.TAKEN }?.let {
                                val at = java.time.Instant.ofEpochMilli(it.at).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
                                " · أُخذت ${formatTime12("%02d:%02d".format(at.hour, at.minute))} (${sourceLabel(it.source)})"
                            } ?: ""),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    StatusPill(d.status)
                }
            }
        }
        // Logs of medications that were later deleted still show up here.
        items(dayLogs.filter { l -> dayDoses.none { it.med.id == l.medId && it.time == l.time } }) { l ->
            SectionCard {
                Text("${l.medName} · ${formatTime12(l.time)} · ${if (l.status == "TAKEN") "أُخذت" else "فائتة"}", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

private fun sourceLabel(s: String) = when (s) {
    "voice" -> "بالصوت"; "notification" -> "من الإشعار"; "pillbox" -> "من العلبة"; else -> "من التطبيق"
}

@Composable
private fun DayCell(d: LocalDate, pct: Int?, selected: Boolean, isToday: Boolean, onClick: () -> Unit) {
    val color = when {
        pct == null -> Color.Transparent
        pct >= 100 -> OkGreen
        pct > 0 -> WarnAmber
        else -> BadRed
    }
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp))
            .background(if (pct == null) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else color.copy(alpha = 0.22f))
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "${d.dayOfMonth}",
            style = if (isToday) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = if (pct == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.padding(end = 4.dp).height(10.dp).aspectRatio(1f).clip(RoundedCornerShape(3.dp)).background(color))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
