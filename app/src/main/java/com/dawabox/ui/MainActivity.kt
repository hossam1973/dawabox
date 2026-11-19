package com.dawabox.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dawabox.data.MedicationEntity
import com.dawabox.data.at
import com.dawabox.ui.screens.EditMedicationScreen
import com.dawabox.ui.screens.HistoryScreen
import com.dawabox.ui.screens.MedicationsScreen
import com.dawabox.ui.screens.PillboxScreen
import com.dawabox.ui.screens.SettingsScreen
import com.dawabox.ui.screens.TodayScreen

enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("اليوم", Icons.Filled.Today),
    PILLBOX("العلبة", Icons.Filled.Inventory2),
    MEDS("أدويتي", Icons.Filled.Medication),
    HISTORY("السجل", Icons.Filled.CalendarMonth),
    SETTINGS("التنبيهات", Icons.Filled.Settings),
}

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            DawaTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { App(vm) }
            }
        }
    }
}

/** Which medication is open in the editor, and whether to start with the microphone. */
private data class Editing(val med: MedicationEntity, val voice: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(vm: MainViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var editing by androidx.compose.runtime.remember { mutableStateOf<Editing?>(null) }

    val today by vm.today.collectAsStateWithLifecycle()
    val meds by vm.medications.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val box by vm.pillboxState.collectAsStateWithLifecycle()
    val chat by vm.chat.collectAsStateWithLifecycle()
    val appointments by vm.appointments.collectAsStateWithLifecycle()

    editing?.let { e ->
        BackHandler { editing = null }
        EditMedicationScreen(
            initial = e.med,
            startWithVoice = e.voice,
            onSave = { vm.save(it, previous = e.med.takeIf { m -> m.id != 0L }); editing = null },
            onClose = { editing = null }
        )
        return
    }

    fun newMed(voice: Boolean) {
        val used = meds.map { it.compartment }.toSet()
        val free = (1..7).firstOrNull { it !in used } ?: 1
        editing = Editing(MedicationEntity(name = "", dosage = "", compartment = free), voice)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("علبة الدواء", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t, onClick = { tab = t },
                        icon = { Icon(t.icon, null) }, label = { Text(t.label) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == Tab.TODAY || tab == Tab.MEDS) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FloatingActionButton(onClick = { newMed(voice = true) }, containerColor = MaterialTheme.colorScheme.secondary) {
                        Icon(Icons.Filled.Mic, "إضافة دواء بالصوت", tint = Color.White)
                    }
                    ExtendedFloatingActionButton(
                        onClick = { newMed(voice = false) },
                        icon = { Icon(Icons.Filled.Add, null, Modifier.size(26.dp)) },
                        text = { Text("إضافة دواء", style = MaterialTheme.typography.labelLarge) }
                    )
                }
            }
        }
    ) { pad ->
        when (tab) {
            Tab.TODAY -> TodayScreen(
                today, settings.patientName, search, { vm.search.value = it }, { d, s -> vm.setStatus(d, s) }, pad,
                nextAppointment = appointments.firstOrNull { it.at.isBefore(java.time.LocalDateTime.now().plusDays(7)) }
            )
            Tab.PILLBOX -> PillboxScreen(box, chat, { vm.talk(it) }, { vm.openCompartment(it) }, pad)
            Tab.MEDS -> MedicationsScreen(
                meds, settings.lowStockAt, { editing = Editing(it, false) }, { vm.delete(it) }, pad,
                appointments = appointments,
                onSaveAppointment = { vm.saveAppointment(it) },
                onDeleteAppointment = { vm.deleteAppointment(it) },
                onImport = { vm.importFromClinic(it) },
            )
            Tab.HISTORY -> HistoryScreen(meds, logs, pad)
            Tab.SETTINGS -> SettingsScreen(settings, { vm.updateSettings(it) }, { cb -> vm.testAlerts(cb) }, pad, clinicReport = { vm.clinicReport() })
        }
    }
}
