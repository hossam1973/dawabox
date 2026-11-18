package com.dawabox.voice

import android.content.Context
import com.dawabox.data.DoseStatus
import com.dawabox.data.Repository
import com.dawabox.data.SettingsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/** LED colour of one compartment. */
enum class Led { OFF, FULL, DUE, EMPTY }

data class PillboxState(
    val connected: Boolean = false,
    val name: String = "",
    val battery: Int = 0,
    val temperatureC: Double = 0.0,
    val leds: List<Led> = List(PillboxDevice.COMPARTMENTS) { Led.OFF },
    val openCompartment: Int? = null,
)

/**
 * The contract a real Bluetooth pillbox must implement. The app only talks
 * to this interface, so swapping [SimulatedPillbox] for a BLE implementation
 * needs no UI changes.
 */
interface PillboxDevice {
    val state: StateFlow<PillboxState>
    suspend fun connect()
    fun disconnect()
    fun setLeds(leds: List<Led>)
    fun openLid(compartment: Int)
    /** Plays [text] through the box speaker. */
    fun speak(text: String, onDone: (() -> Unit)? = null)

    companion object { const val COMPARTMENTS = 7 }
}

/** Simulated pillbox: phone speaker for audio, in-memory LEDs and sensors. */
class SimulatedPillbox(private val context: Context) : PillboxDevice {
    private val _state = MutableStateFlow(PillboxState())
    override val state: StateFlow<PillboxState> = _state.asStateFlow()

    override suspend fun connect() {
        delay(800) // pretend to pair over Bluetooth
        _state.update { it.copy(connected = true, name = "علبة الدواء (محاكاة)", battery = 94, temperatureC = 24.5) }
    }

    override fun disconnect() { _state.update { it.copy(connected = false) } }

    override fun setLeds(leds: List<Led>) { _state.update { it.copy(leds = leds) } }

    override fun openLid(compartment: Int) { _state.update { it.copy(openCompartment = compartment) } }

    override fun speak(text: String, onDone: (() -> Unit)?) = Speaker.say(context, text, onDone)
}

/** One line in the conversation with the pillbox. */
data class ChatLine(val fromUser: Boolean, val text: String)

/**
 * Understands spoken commands and answers them using the medication data.
 * Commands: "أخذت الدواء", "ما جرعتي التالية؟", "ما أدويتي اليوم؟", "كم بقي من الدواء؟".
 */
class PillboxAssistant(private val context: Context, private val device: PillboxDevice) {
    private val repo = Repository.get(context)

    suspend fun handle(spoken: String): String {
        val reply = when (VoiceParser.intentOf(spoken)) {
            PillboxIntent.TAKEN -> recordTaken()
            PillboxIntent.NEXT_DOSE -> nextDose()
            PillboxIntent.TODAY_LIST -> todayList()
            PillboxIntent.STOCK -> stock()
            PillboxIntent.ADD_MEDICATION -> addMedication(spoken)
            PillboxIntent.UNKNOWN -> "لم أفهم. يمكنك أن تقول: أخذت الدواء، أو ما جرعتي التالية، أو ما أدويتي اليوم."
        }
        device.speak(reply)
        return reply
    }

    private suspend fun recordTaken(): String {
        val doses = repo.todayDoses()
        val target = doses.firstOrNull { it.status == DoseStatus.DUE || it.status == DoseStatus.LATE }
            ?: doses.firstOrNull { it.status == DoseStatus.UPCOMING }
            ?: return "لا توجد جرعات متبقية اليوم. أحسنت!"
        repo.markTaken(target.med.id, LocalDate.now(), target.time, source = "voice")
        device.openLid(target.med.compartment)
        val next = repo.todayDoses().firstOrNull { it.status == DoseStatus.UPCOMING || it.status == DoseStatus.DUE }
        val nextText = next?.let { " جرعتك التالية ${it.med.name} الساعة ${spokenTime(it.time)}." } ?: " انتهت جرعات اليوم."
        return "تم تسجيل ${target.med.name} ${target.med.dosage}.$nextText"
    }

    private suspend fun nextDose(): String {
        val pending = repo.todayDoses().filter { it.status != DoseStatus.TAKEN && it.status != DoseStatus.MISSED }
        val d = pending.firstOrNull() ?: return "لا توجد جرعات متبقية اليوم."
        val when_ = if (d.status == DoseStatus.UPCOMING) "الساعة ${spokenTime(d.time)}" else "الآن، موعدها كان ${spokenTime(d.time)}"
        val extra = if (d.med.instructions.isNotBlank()) "، ${d.med.instructions}" else ""
        return "جرعتك التالية ${d.med.name} ${d.med.dosage} $when_ من الحجرة رقم ${d.med.compartment}$extra."
    }

    private suspend fun todayList(): String {
        val doses = repo.todayDoses()
        if (doses.isEmpty()) return "لا توجد أدوية مجدولة اليوم."
        val taken = doses.count { it.status == DoseStatus.TAKEN }
        val list = doses.joinToString("، ") { "${it.med.name} ${spokenTime(it.time)}" }
        return "لديك ${doses.size} جرعات اليوم، أخذت منها $taken. $list."
    }

    private suspend fun stock(): String {
        val meds = repo.allMedications()
        if (meds.isEmpty()) return "لم تُضف أي أدوية بعد."
        val low = SettingsStore.get(context).current.lowStockAt
        return meds.joinToString("، ") { m ->
            "${m.name}: ${m.stock}" + if (m.stock <= low) " وهو قليل، اطلب عبوة جديدة" else ""
        } + "."
    }

    private suspend fun addMedication(spoken: String): String {
        val p = VoiceParser.parse(spoken)
        if (!p.isUseful) return "قل اسم الدواء والجرعة، مثلاً: أضف بانادول 500 ملغ مرتين يومياً."
        return "فهمت: ${p.name}${if (p.dosage.isNotBlank()) " " + p.dosage else ""}. افتح شاشة إضافة دواء لمراجعته وحفظه."
    }

    companion object {
        fun spokenTime(t: String): String {
            val (h, m) = t.split(':').map { it.toInt() }
            val period = when (h) { in 0..11 -> "صباحاً"; in 12..16 -> "ظهراً"; else -> "مساءً" }
            val h12 = if (h % 12 == 0) 12 else h % 12
            return if (m == 0) "$h12 $period" else "$h12 و$m دقيقة $period"
        }
    }
}
