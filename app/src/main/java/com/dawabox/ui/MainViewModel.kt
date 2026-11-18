package com.dawabox.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dawabox.data.AlertSettings
import com.dawabox.data.DayCompliance
import com.dawabox.data.DoseItem
import com.dawabox.data.DoseLogEntity
import com.dawabox.data.DoseStatus
import com.dawabox.data.MedicationEntity
import com.dawabox.data.Repository
import com.dawabox.data.SettingsStore
import com.dawabox.data.complianceFor
import com.dawabox.data.dosesFor
import com.dawabox.reminders.CaregiverAlerts
import com.dawabox.reminders.Notifier
import com.dawabox.reminders.ReminderScheduler
import com.dawabox.voice.ChatLine
import com.dawabox.voice.Led
import com.dawabox.voice.PillboxAssistant
import com.dawabox.voice.PillboxDevice
import com.dawabox.voice.PillboxState
import com.dawabox.voice.SimulatedPillbox
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

data class TodayUi(
    val doses: List<DoseItem> = emptyList(),
    val week: List<DayCompliance> = emptyList(),
    val lowStock: List<MedicationEntity> = emptyList(),
) {
    val next get() = doses.firstOrNull { it.status == DoseStatus.DUE || it.status == DoseStatus.LATE }
        ?: doses.firstOrNull { it.status == DoseStatus.UPCOMING }
    val taken get() = doses.count { it.status == DoseStatus.TAKEN }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()
    private val repo = Repository.get(app)
    private val settingsStore = SettingsStore.get(app)

    /** Swap for a Bluetooth implementation when real hardware exists. */
    val pillbox: PillboxDevice = SimulatedPillbox(app)
    private val assistant = PillboxAssistant(app, pillbox)

    val settings: StateFlow<AlertSettings> = settingsStore.flow
    val search = MutableStateFlow("")

    private val clock = flow { while (true) { emit(LocalTime.now()); delay(30_000) } }

    val medications: StateFlow<List<MedicationEntity>> =
        repo.medications.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Keeps ~3 months of logs in memory for history and the weekly chart. */
    val logs: StateFlow<List<DoseLogEntity>> =
        repo.logsSince(LocalDate.now().minusDays(100)).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val today: StateFlow<TodayUi> = combine(medications, logs, clock, search, settings) { meds, logs, now, q, s ->
        val date = LocalDate.now()
        val doses = dosesFor(date, meds, logs, now).filter { q.isBlank() || it.med.name.contains(q.trim(), ignoreCase = true) }
        val week = (6 downTo 0).map { complianceFor(date.minusDays(it.toLong()), meds, logs, if (it == 0) now else null) }
        TodayUi(doses, week, meds.filter { it.stock <= s.lowStockAt })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TodayUi())

    val pillboxState: StateFlow<PillboxState> = pillbox.state
    val chat = MutableStateFlow(listOf(ChatLine(false, "مرحباً، أنا علبة الدواء. اضغط على الميكروفون وتحدث معي.")))

    init {
        viewModelScope.launch { pillbox.connect() }
        // Keep compartment LEDs in sync with today's doses.
        viewModelScope.launch {
            combine(medications, logs, clock) { meds, logs, now -> dosesFor(LocalDate.now(), meds, logs, now) }
                .collect { doses ->
                    val leds = (1..PillboxDevice.COMPARTMENTS).map { c ->
                        val inBox = doses.filter { it.med.compartment == c }
                        when {
                            inBox.isEmpty() -> Led.OFF
                            inBox.any { it.status == DoseStatus.DUE || it.status == DoseStatus.LATE } -> Led.DUE
                            inBox.any { it.status == DoseStatus.UPCOMING } -> Led.FULL
                            else -> Led.EMPTY
                        }
                    }
                    pillbox.setLeds(leds)
                }
        }
        // Re-arm all alarms on start (covers app updates and cleared alarms).
        viewModelScope.launch { ReminderScheduler.scheduleAll(ctx, repo.allMedications()) }
    }

    fun save(med: MedicationEntity, previous: MedicationEntity?) = viewModelScope.launch {
        previous?.let { ReminderScheduler.cancelAll(ctx, it) }
        val id = repo.save(med)
        ReminderScheduler.schedule(ctx, med.copy(id = id))
        pillbox.speak("تم حفظ ${med.name}. سأذكرك في مواعيده من الحجرة رقم ${med.compartment}.")
    }

    fun delete(med: MedicationEntity) = viewModelScope.launch {
        ReminderScheduler.cancelAll(ctx, med)
        repo.delete(med.id)
    }

    fun setStatus(dose: DoseItem, status: DoseStatus) = viewModelScope.launch {
        val date = dose.date
        when (status) {
            DoseStatus.TAKEN -> {
                if (repo.markTaken(dose.med.id, date, dose.time, source = "app")) {
                    pillbox.speak("أحسنت، تم تسجيل ${dose.med.name}.")
                }
            }
            DoseStatus.MISSED -> repo.markMissed(dose.med.id, date, dose.time, source = "app")
            else -> repo.clearStatus(dose.med.id, date, dose.time)
        }
        Notifier.cancelDose(ctx, dose.med.id, dose.time)
        ReminderScheduler.cancelMissedCheck(ctx, dose.med.id, date.toString(), dose.time)
    }

    fun talk(spoken: String) = viewModelScope.launch {
        chat.value = chat.value + ChatLine(true, spoken)
        val reply = assistant.handle(spoken)
        chat.value = chat.value + ChatLine(false, reply)
    }

    fun openCompartment(n: Int) {
        pillbox.openLid(n)
        val inBox = today.value.doses.filter { it.med.compartment == n }
        val text = if (inBox.isEmpty()) "الحجرة $n فارغة."
        else "الحجرة $n فيها " + inBox.joinToString("، ") { "${it.med.name} الساعة ${PillboxAssistant.spokenTime(it.time)}" }
        chat.value = chat.value + ChatLine(false, text)
        pillbox.speak(text)
    }

    fun updateSettings(s: AlertSettings) = settingsStore.update(s)

    fun testAlerts(onReport: (List<String>) -> Unit) = viewModelScope.launch {
        onReport(CaregiverAlerts.test(ctx).lines)
    }
}
