package com.dawabox.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dawabox.data.AlertChannel
import com.dawabox.data.AppointmentEntity
import com.dawabox.data.Frequency
import com.dawabox.data.MedForm
import com.dawabox.data.MedicationEntity
import com.dawabox.data.channelSet
import com.dawabox.data.defaultTimes
import com.dawabox.data.formEnum
import com.dawabox.data.frequencyEnum
import com.dawabox.data.timeList
import com.dawabox.ui.BadRed
import com.dawabox.ui.ChipRow
import com.dawabox.ui.FormBadge
import com.dawabox.ui.SectionCard
import com.dawabox.ui.WarnAmber
import com.dawabox.ui.formatTime12
import com.dawabox.ui.rememberVoiceInput
import com.dawabox.voice.VoiceParser
import java.time.LocalDate

@Composable
fun MedicationsScreen(
    meds: List<MedicationEntity>,
    lowStockAt: Int,
    onEdit: (MedicationEntity) -> Unit,
    onDelete: (MedicationEntity) -> Unit,
    contentPadding: PaddingValues,
    appointments: List<AppointmentEntity> = emptyList(),
    onSaveAppointment: (AppointmentEntity) -> Unit = {},
    onDeleteAppointment: (AppointmentEntity) -> Unit = {},
    onImport: suspend (String) -> String = { "" },
) {
    var confirm by remember { mutableStateOf<MedicationEntity?>(null) }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 8.dp, bottom = contentPadding.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ClinicSection(appointments, onSaveAppointment, onDeleteAppointment, onImport) }
        item { Text("الأدوية", style = MaterialTheme.typography.titleLarge) }
        if (meds.isEmpty()) item {
            SectionCard {
                Text("لا توجد أدوية بعد", style = MaterialTheme.typography.titleMedium)
                Text("أضف دواءك الأول بزر «إضافة دواء».", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(meds, key = { it.id }) { m ->
            SectionCard(Modifier.clickable { onEdit(m) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormBadge(m.formEnum)
                    Column(Modifier.weight(1f)) {
                        Text(m.name, style = MaterialTheme.typography.titleMedium)
                        Text("${m.dosage} · ${m.formEnum.label} · ${m.frequencyEnum.label}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(m.timeList.joinToString("، ") { formatTime12(it) } + " · الحجرة ${m.compartment}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "المخزون: ${m.stock}" + if (m.stock <= lowStockAt) " (اطلب عبوة جديدة)" else "",
                            color = if (m.stock <= lowStockAt) WarnAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    IconButton(onClick = { onEdit(m) }) { Icon(Icons.Filled.Edit, "تعديل") }
                    IconButton(onClick = { confirm = m }) { Icon(Icons.Filled.Delete, "حذف", tint = BadRed) }
                }
            }
        }
    }
    confirm?.let { m ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("حذف ${m.name}؟") },
            text = { Text("ستتوقف تذكيرات هذا الدواء. يبقى سجل الجرعات السابقة.") },
            confirmButton = { TextButton(onClick = { onDelete(m); confirm = null }) { Text("حذف", color = BadRed) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("إلغاء") } }
        )
    }
}

/**
 * Add / edit screen: name, dosage, form, frequency, times, start date (date
 * picker), pillbox compartment, stock, instructions and alert channels.
 * The microphone fills the form from a spoken sentence.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditMedicationScreen(
    initial: MedicationEntity,
    onSave: (MedicationEntity) -> Unit,
    onClose: () -> Unit,
    startWithVoice: Boolean = false,
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var dosage by rememberSaveable { mutableStateOf(initial.dosage) }
    var form by rememberSaveable { mutableStateOf(initial.formEnum) }
    var frequency by rememberSaveable { mutableStateOf(initial.frequencyEnum) }
    val times = remember { mutableStateListOf<String>().apply { addAll(initial.timeList.ifEmpty { listOf("08:00") }) } }
    var startDate by rememberSaveable { mutableStateOf(initial.startDate) }
    var compartment by rememberSaveable { mutableStateOf(initial.compartment) }
    var stock by rememberSaveable { mutableStateOf(initial.stock.toString()) }
    var instructions by rememberSaveable { mutableStateOf(initial.instructions) }
    val channels = remember { mutableStateListOf<AlertChannel>().apply { addAll(initial.channelSet) } }
    var heard by remember { mutableStateOf("") }
    var showErrors by remember { mutableStateOf(false) }

    val voice = rememberVoiceInput("مثال: ميتفورمين 500 ملغ مرتين يومياً بعد الأكل") { spoken ->
        heard = spoken
        val p = VoiceParser.parse(spoken)
        if (p.name.isNotBlank()) name = p.name
        if (p.dosage.isNotBlank()) dosage = p.dosage
        p.form?.let { form = it }
        p.frequency?.let { frequency = it }
        if (p.times.isNotEmpty()) { times.clear(); times.addAll(p.times) }
        p.startDate?.let { startDate = it.toString() }
        if (spoken.contains("بعد الأكل")) instructions = "بعد الأكل"
        if (spoken.contains("قبل الأكل")) instructions = "قبل الأكل"
        if (spoken.contains("قبل النوم")) instructions = "قبل النوم"
    }
    LaunchedEffect(Unit) { if (startWithVoice) voice() }

    fun pickTime(index: Int?) {
        val cur = index?.let { times[it] } ?: "08:00"
        val (h, m) = cur.split(':').map { it.toInt() }
        TimePickerDialog(context, { _, hh, mm ->
            val t = "%02d:%02d".format(hh, mm)
            if (index == null) { if (t !in times) times.add(t) } else times[index] = t
            times.sortWith(naturalOrder())
        }, h, m, false).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initial.id == 0L) "إضافة دواء" else "تعديل ${initial.name}") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "إغلاق") } },
                actions = { IconButton(onClick = voice) { Icon(Icons.Filled.Mic, "إدخال بالصوت") } }
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedButton(onClick = voice, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Filled.Mic, null); Spacer(Modifier.width(8.dp)); Text("املأ النموذج بصوتك")
            }
            if (heard.isNotBlank()) Text("سمعت: «$heard». راجع الحقول قبل الحفظ.", color = MaterialTheme.colorScheme.primary)

            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text("اسم الدواء") }, singleLine = true,
                isError = showErrors && name.isBlank(),
                supportingText = { if (showErrors && name.isBlank()) Text("اكتب اسم الدواء") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = dosage, onValueChange = { dosage = it }, label = { Text("الجرعة (مثال: 500 ملغ، قرص واحد)") }, singleLine = true,
                isError = showErrors && dosage.isBlank(), modifier = Modifier.fillMaxWidth()
            )
            ChipRow(listOf("قرص واحد", "نصف قرص", "500 ملغ", "10 مل", "قطرتين", "بختان"), { it == dosage }, { it }) { dosage = it }

            Label("شكل الدواء")
            ChipRow(MedForm.entries, { it == form }, { it.label }) { form = it }

            Label("التكرار")
            ChipRow(Frequency.entries, { it == frequency }, { it.label }) { frequency = it }

            if (frequency != Frequency.AS_NEEDED) {
                Label("عدد المرات يومياً")
                ChipRow(listOf(1, 2, 3, 4), { it == times.size }, { "$it" }) { n -> times.clear(); times.addAll(defaultTimes(n)) }
                Label("أوقات التناول")
                ChipRow(times.indices.toList(), { true }, { formatTime12(times[it]) }) { pickTime(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickTime(null) }) { Icon(Icons.Filled.Schedule, null); Spacer(Modifier.width(6.dp)); Text("إضافة وقت") }
                    if (times.size > 1) TextButton(onClick = { times.removeAt(times.lastIndex) }) { Text("حذف آخر وقت") }
                }
            }

            Label("تاريخ البدء")
            OutlinedButton(onClick = {
                val d = runCatching { LocalDate.parse(startDate) }.getOrDefault(LocalDate.now())
                DatePickerDialog(context, { _, y, mo, day -> startDate = LocalDate.of(y, mo + 1, day).toString() }, d.year, d.monthValue - 1, d.dayOfMonth).show()
            }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Filled.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text(startDate)
            }

            Label("حجرة العلبة")
            ChipRow((1..7).toList(), { it == compartment }, { "$it" }) { compartment = it }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = stock, onValueChange = { v -> stock = v.filter { it.isDigit() }.take(4) },
                    label = { Text("الكمية المتوفرة") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(value = instructions, onValueChange = { instructions = it }, label = { Text("تعليمات (بعد الأكل، قبل النوم…)") }, modifier = Modifier.fillMaxWidth())
            ChipRow(listOf("بعد الأكل", "قبل الأكل", "قبل النوم", "على معدة فارغة"), { it == instructions }, { it }) { instructions = it }

            Label("كيف نذكّرك ونبلّغ المرافق؟")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AlertChannel.entries.forEach { ch ->
                    InputChip(selected = ch in channels, onClick = { if (ch in channels) channels.remove(ch) else channels.add(ch) }, label = { Text(ch.label) })
                }
            }

            Button(
                onClick = {
                    showErrors = true
                    if (name.isBlank() || dosage.isBlank()) return@Button
                    onSave(
                        initial.copy(
                            name = name.trim(), dosage = dosage.trim(), form = form.name, frequency = frequency.name,
                            times = if (frequency == Frequency.AS_NEEDED) "" else times.joinToString(","),
                            startDate = startDate, compartment = compartment,
                            stock = stock.toIntOrNull() ?: 0, instructions = instructions.trim(),
                            channels = channels.joinToString(",") { it.name }.ifBlank { AlertChannel.APP.name }
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(16.dp)
            ) { Text("حفظ الدواء", style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Label(text: String) = Text(text, style = MaterialTheme.typography.titleMedium)
