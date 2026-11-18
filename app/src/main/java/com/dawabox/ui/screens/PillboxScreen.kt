package com.dawabox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dawabox.ui.OkGreen
import com.dawabox.ui.SectionCard
import com.dawabox.ui.WarnAmber
import com.dawabox.ui.rememberVoiceInput
import com.dawabox.voice.ChatLine
import com.dawabox.voice.Led
import com.dawabox.voice.PillboxState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PillboxScreen(
    state: PillboxState,
    chat: List<ChatLine>,
    onTalk: (String) -> Unit,
    onOpen: (Int) -> Unit,
    contentPadding: PaddingValues,
) {
    val listen = rememberVoiceInput("تحدث مع علبة الدواء") { onTalk(it) }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 8.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(if (state.connected) Icons.Filled.Bluetooth else Icons.Filled.BluetoothDisabled, null,
                        tint = if (state.connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (state.connected) state.name else "جارٍ الاتصال بالعلبة…", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                }
                if (state.connected) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.BatteryFull, null, Modifier.size(20.dp)); Text(" ${state.battery}%") }
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Thermostat, null, Modifier.size(20.dp)); Text(" ${state.temperatureC}°") }
                    }
                }
            }
        }
        item {
            SectionCard {
                Text("حجرات العلبة", style = MaterialTheme.typography.titleMedium)
                Text("اضغط على أي حجرة لتسمع ما فيها.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.leds.forEachIndexed { i, led -> Compartment(i + 1, led, state.openCompartment == i + 1) { onOpen(i + 1) } }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LedLegend(WarnAmber, "حان موعدها"); LedLegend(OkGreen, "ممتلئة"); LedLegend(Color.Gray, "فارغة")
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                LargeFloatingActionButton(onClick = listen, shape = CircleShape, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Filled.Mic, "تحدث", Modifier.size(40.dp), tint = Color.White)
                }
                Spacer(Modifier.height(6.dp))
                Text("اضغط وتحدث", style = MaterialTheme.typography.titleMedium)
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("أخذت الدواء", "ما جرعتي التالية؟", "ما أدويتي اليوم؟", "كم بقي من الدواء؟").forEach { cmd ->
                    AssistChip(onClick = { onTalk(cmd) }, label = { Text(cmd) })
                }
            }
        }
        items(chat.reversed()) { line -> Bubble(line) }
    }
}

@Composable
private fun Compartment(n: Int, led: Led, open: Boolean, onClick: () -> Unit) {
    val color = when (led) {
        Led.DUE -> WarnAmber; Led.FULL -> OkGreen; Led.EMPTY -> Color.Gray; Led.OFF -> MaterialTheme.colorScheme.surfaceVariant
    }
    Column(
        Modifier.width(64.dp).clip(RoundedCornerShape(14.dp))
            .border(2.dp, if (open) MaterialTheme.colorScheme.primary else color.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.12f)).clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(color))
        Spacer(Modifier.height(4.dp))
        Text("$n", style = MaterialTheme.typography.titleLarge)
        Text(if (open) "مفتوحة" else "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun LedLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color)); Text(" $label", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Bubble(line: ChatLine) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (line.fromUser) Arrangement.Start else Arrangement.End) {
        Text(
            line.text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Start,
            color = if (line.fromUser) Color.White else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(16.dp))
                .background(if (line.fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

