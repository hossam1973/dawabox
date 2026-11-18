package com.dawabox

import com.dawabox.data.DoseLogEntity
import com.dawabox.data.DoseStatus
import com.dawabox.data.Frequency
import com.dawabox.data.MedForm
import com.dawabox.data.MedicationEntity
import com.dawabox.data.dosesFor
import com.dawabox.voice.PillboxIntent
import com.dawabox.voice.VoiceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class VoiceParserTest {
    private val today = LocalDate.of(2026, 11, 18)

    @Test fun parsesNameDoseAndTwiceDaily() {
        val p = VoiceParser.parse("أضف بانادول 500 ملغ مرتين يومياً", today)
        assertEquals("بانادول", p.name)
        assertEquals("500 ملغ", p.dosage)
        assertEquals(Frequency.DAILY, p.frequency)
        assertEquals(listOf("08:00", "20:00"), p.times)
    }

    @Test fun parsesArabicIndicDigitsAndExplicitEveningTime() {
        val p = VoiceParser.parse("ميتفورمين ٨٥٠ مجم الساعة ٩ مساء", today)
        assertEquals("ميتفورمين", p.name)
        assertEquals("850 ملغ", p.dosage)
        assertEquals(listOf("21:00"), p.times)
    }

    @Test fun parsesInsulinAsInjectionStartingTomorrow() {
        val p = VoiceParser.parse("انسولين لانتوس 10 وحدات مرة يوميا الساعة 10 مساء من بكره", today)
        assertEquals("انسولين لانتوس", p.name)
        assertEquals("10 وحدات", p.dosage)
        assertEquals(MedForm.INJECTION, p.form)
        assertEquals(listOf("22:00"), p.times)
        assertEquals(today.plusDays(1), p.startDate)
    }

    @Test fun parsesSyrupAndWeekly() {
        val p = VoiceParser.parse("فيتامين د 10 مل اسبوعيا", today)
        assertEquals(MedForm.SYRUP, p.form)
        assertEquals(Frequency.WEEKLY, p.frequency)
    }

    @Test fun morningSuffixDoesNotSwallowNextWord() {
        val p = VoiceParser.parse("اوميبرازول 20 ملغ الساعة 7 مع الفطور", today)
        assertEquals(listOf("07:00"), p.times)
    }

    @Test fun detectsPillboxIntents() {
        assertEquals(PillboxIntent.TAKEN, VoiceParser.intentOf("أخذت الدواء"))
        assertEquals(PillboxIntent.NEXT_DOSE, VoiceParser.intentOf("ما جرعتي التالية؟"))
        assertEquals(PillboxIntent.TODAY_LIST, VoiceParser.intentOf("ما أدويتي اليوم؟"))
        assertEquals(PillboxIntent.STOCK, VoiceParser.intentOf("كم بقي من الدواء؟"))
        assertEquals(PillboxIntent.ADD_MEDICATION, VoiceParser.intentOf("أضف دواء جديد"))
        assertEquals(PillboxIntent.UNKNOWN, VoiceParser.intentOf("مرحبا"))
    }

    @Test fun unknownSentenceGivesNoName() {
        assertNull(VoiceParser.parse("", today).form)
    }
}

class ScheduleTest {
    private val day = LocalDate.of(2026, 11, 18)
    private val med = MedicationEntity(id = 1, name = "أملوديبين", dosage = "5 ملغ", times = "08:00,20:00", startDate = "2026-11-01")

    @Test fun statusesFollowTimeAndLogs() {
        val logs = listOf(DoseLogEntity(medId = 1, medName = "أملوديبين", date = day.toString(), time = "08:00", status = "TAKEN"))
        val doses = dosesFor(day, listOf(med), logs, LocalTime.of(20, 10), today = day)
        assertEquals(DoseStatus.TAKEN, doses[0].status)
        assertEquals(DoseStatus.DUE, doses[1].status)
        val later = dosesFor(day, listOf(med), logs, LocalTime.of(21, 0), today = day)
        assertEquals(DoseStatus.LATE, later[1].status)
    }

    @Test fun weeklyMedicationOnlyOnItsWeekday() {
        val weekly = med.copy(frequency = Frequency.WEEKLY.name, startDate = "2026-11-11") // a Wednesday
        assertEquals(2, dosesFor(day, listOf(weekly), emptyList(), LocalTime.NOON, today = day).size) // 18th is Wednesday
        assertEquals(0, dosesFor(day.plusDays(1), listOf(weekly), emptyList(), null, today = day).size)
    }

    @Test fun nothingBeforeStartDate() {
        assertEquals(0, dosesFor(LocalDate.of(2026, 10, 30), listOf(med), emptyList(), null, today = day).size)
    }
}
