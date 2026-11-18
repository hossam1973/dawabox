package com.dawabox.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MedicationLiquid
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dawabox.data.DayCompliance
import com.dawabox.data.DoseStatus
import com.dawabox.data.MedForm
import java.time.format.TextStyle
import java.util.Locale

fun MedForm.icon(): ImageVector = when (this) {
    MedForm.TABLET -> Icons.Filled.Medication
    MedForm.CAPSULE -> Icons.Filled.MedicationLiquid
    MedForm.SYRUP -> Icons.Filled.LocalDrink
    MedForm.INJECTION -> Icons.Filled.Vaccines
    MedForm.DROPS -> Icons.Filled.WaterDrop
    MedForm.INHALER -> Icons.Filled.Air
}

fun MedForm.tint(): Color = when (this) {
    MedForm.TABLET -> Color(0xFF0F7A68)
    MedForm.CAPSULE -> Color(0xFF6A5ACD)
    MedForm.SYRUP -> Color(0xFFB7791F)
    MedForm.INJECTION -> Color(0xFFC2453A)
    MedForm.DROPS -> Color(0xFF3B6F8F)
    MedForm.INHALER -> Color(0xFF5E8C31)
}

/** Round, coloured badge with the dosage-form icon, for quick visual scanning. */
@Composable
fun FormBadge(form: MedForm, size: Int = 44) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(form.tint().copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) { Icon(form.icon(), contentDescription = form.label, tint = form.tint(), modifier = Modifier.size((size * 0.55).dp)) }
}

@Composable
fun StatusPill(status: DoseStatus) {
    val (label, color) = when (status) {
        DoseStatus.TAKEN -> "تم" to OkGreen
        DoseStatus.MISSED -> "فائتة" to BadRed
        DoseStatus.LATE -> "متأخرة" to WarnAmber
        DoseStatus.DUE -> "الآن" to MaterialTheme.colorScheme.primary
        DoseStatus.UPCOMING -> "قادمة" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        label, color = color, style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.13f)).padding(horizontal = 10.dp, vertical = 2.dp)
    )
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) { Column(Modifier.padding(16.dp)) { content() } }
}

fun formatTime12(t: String): String {
    val (h, m) = t.split(':').map { it.toInt() }
    val ap = if (h < 12) "ص" else "م"
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "$h12:%02d $ap".format(m)
}

/** Weekly compliance: stacked bars (taken on time vs missed), tap a day for details. */
@Composable
fun WeeklyChart(week: List<DayCompliance>) {
    var selected by remember { mutableStateOf<Int?>(null) }
    val maxBar = 120.dp
    Column {
        Row(
            Modifier.fillMaxWidth().height(maxBar + 28.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            week.forEachIndexed { i, d ->
                val takenFrac = if (d.total == 0) 0f else d.taken / d.total.toFloat()
                val missedFrac = if (d.total == 0) 0f else d.missed / d.total.toFloat()
                Column(
                    Modifier.width(34.dp).fillMaxHeight().clickable { selected = if (selected == i) null else i },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(if (d.total == 0) "—" else "${d.percent}%", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    Column(
                        Modifier.width(26.dp).height(maxBar).clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        if (missedFrac > 0f) Box(Modifier.fillMaxWidth().height(maxBar * missedFrac).background(BadRed.copy(alpha = 0.8f)))
                        if (takenFrac > 0f) Box(Modifier.fillMaxWidth().height(maxBar * takenFrac).background(if (selected == i) Teal else OkGreen))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            week.forEach { d ->
                Text(
                    d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ar")),
                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(34.dp), textAlign = TextAlign.Center
                )
            }
        }
        selected?.let { i ->
            val d = week[i]
            Spacer(Modifier.height(8.dp))
            Text(
                "${d.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("ar"))}: أُخذت ${d.taken} من ${d.total} جرعات، وفاتت ${d.missed}.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(OkGreen, "في الموعد"); LegendDot(BadRed, "فائتة")
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipRow(options: List<T>, selected: (T) -> Boolean, label: (T) -> String, onClick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> FilterChip(selected = selected(o), onClick = { onClick(o) }, label = { Text(label(o)) }) }
    }
}

/**
 * Returns a function that opens the system speech recogniser (Arabic first)
 * and passes the recognised text to [onText]. Uses RecognizerIntent, so no
 * microphone permission is needed in the app itself.
 */
@Composable
fun rememberVoiceInput(prompt: String, onText: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onText)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(callback)
        }
    }
    return {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        }
        try { launcher.launch(intent) } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "التعرف على الصوت غير متاح. ثبّت تطبيق Google.", Toast.LENGTH_LONG).show()
        }
    }
}
