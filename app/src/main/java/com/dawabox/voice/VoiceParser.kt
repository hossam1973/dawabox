package com.dawabox.voice

import com.dawabox.data.Frequency
import com.dawabox.data.MedForm
import com.dawabox.data.defaultTimes
import java.time.LocalDate

/** Result of parsing a spoken sentence such as "أضف بانادول 500 ملغ مرتين يومياً". */
data class ParsedMedication(
    val name: String = "",
    val dosage: String = "",
    val form: MedForm? = null,
    val frequency: Frequency? = null,
    val times: List<String> = emptyList(),
    val startDate: LocalDate? = null,
) {
    val isUseful get() = name.isNotBlank()
}

/** What the user asked the pillbox to do. */
enum class PillboxIntent { TAKEN, NEXT_DOSE, TODAY_LIST, STOCK, ADD_MEDICATION, UNKNOWN }

/** Pure-Kotlin parser, unit tested without Android. */
object VoiceParser {

    private val arabicDigits = mapOf(
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9', '٫' to '.'
    )

    fun normalize(text: String): String = text
        .map { arabicDigits[it] ?: it }.joinToString("")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ى', 'ي').replace('ة', 'ه')
        .replace(Regex("[\\u064B-\\u0652]"), "") // tashkeel
        .lowercase()
        .trim()

    fun intentOf(raw: String): PillboxIntent {
        val t = normalize(raw)
        return when {
            listOf("اضف", "ضيف", "سجل دواء", "دواء جديد", "add").any { t.contains(it) } -> PillboxIntent.ADD_MEDICATION
            listOf("اخذت", "تناولت", "خذيت", "شربت", "بلعت", "took", "taken").any { t.contains(it) } -> PillboxIntent.TAKEN
            listOf("التاليه", "التالي", "القادمه", "الجايه", "الجاي", "متي", "next").any { t.contains(it) } -> PillboxIntent.NEXT_DOSE
            listOf("كم باقي", "كم بقي", "المخزون", "باقي", "متبقي", "stock").any { t.contains(it) } -> PillboxIntent.STOCK
            listOf("اليوم", "جدول", "ادويتي", "today").any { t.contains(it) } -> PillboxIntent.TODAY_LIST
            else -> PillboxIntent.UNKNOWN
        }
    }

    private val dosageRegex = Regex(
        "(\\d+(?:\\.\\d+)?)\\s*(ملغ|ملجم|مجم|ملغم|ملي|مل|وحدات|وحده|اقراص|قرص|حبات|حبه|حبتين|نقط|نقطه|بخات|بخه|mg|ml|units?)"
    )
    private val timeRegex = Regex("(?:الساعه|ساعه|at)\\s*(\\d{1,2})(?::(\\d{2}))?(?:\\s*(صباحا|الصبح|صباح|ص|مساءا|مساء|المساء|بالليل|ليلا|م|am|pm)(?=\\s|$))?")

    private val fillerWords = setOf(
        "اضف", "ضيف", "سجل", "دواء", "جديد", "الدواء", "اسمه", "add", "medication", "من", "فضلك", "لو", "سمحت", "علاج"
    )
    private val stopWords = setOf(
        "جرعه", "مره", "مرتين", "ثلاث", "اربع", "يوميا", "يومي", "كل", "الساعه", "ساعه", "اسبوعيا",
        "بدءا", "ابتداء", "من", "اليوم", "غدا", "بكره", "قبل", "بعد", "عند", "twice", "once", "daily", "at"
    )

    fun parse(raw: String, today: LocalDate = LocalDate.now()): ParsedMedication {
        val t = normalize(raw)

        val dose = dosageRegex.find(t)
        val dosage = dose?.let { m ->
            val unit = when (m.groupValues[2]) {
                "ملجم", "مجم", "ملغم", "mg" -> "ملغ"
                "ملي", "ml" -> "مل"
                "وحده", "unit", "units" -> "وحدات"
                "حبه", "حبات" -> "حبة"
                "اقراص" -> "قرص"
                else -> m.groupValues[2]
            }
            "${m.groupValues[1]} $unit"
        } ?: when {
            t.contains("حبتين") || t.contains("قرصين") -> "2 قرص"
            t.contains("نص حبه") || t.contains("نصف قرص") -> "نصف قرص"
            else -> ""
        }

        val form = when {
            listOf("حقن", "ابره", "انسولين", "injection").any { t.contains(it) } -> MedForm.INJECTION
            listOf("شراب", "مل", "syrup").any { Regex("(^|\\s|\\d)$it(\\s|$)").containsMatchIn(t) } -> MedForm.SYRUP
            listOf("قطره", "نقط", "drops").any { t.contains(it) } -> MedForm.DROPS
            listOf("بخاخ", "بخه", "inhaler").any { t.contains(it) } -> MedForm.INHALER
            listOf("كبسول", "capsule").any { t.contains(it) } -> MedForm.CAPSULE
            listOf("قرص", "حبه", "حبات", "اقراص", "tablet").any { t.contains(it) } -> MedForm.TABLET
            else -> null
        }

        val perDay = when {
            listOf("اربع مرات", "4 مرات", "كل 6 ساعات").any { t.contains(it) } -> 4
            listOf("ثلاث مرات", "3 مرات", "كل 8 ساعات", "مع الوجبات", "three times").any { t.contains(it) } -> 3
            listOf("مرتين", "2 مره", "كل 12 ساعه", "twice").any { t.contains(it) } -> 2
            listOf("مره", "once", "يوميا", "يومي", "daily").any { t.contains(it) } -> 1
            else -> null
        }
        val frequency = when {
            t.contains("اسبوع") || t.contains("weekly") -> Frequency.WEEKLY
            t.contains("عند اللزوم") || t.contains("عند الحاجه") -> Frequency.AS_NEEDED
            perDay != null -> Frequency.DAILY
            else -> null
        }

        val explicitTimes = timeRegex.findAll(t).mapNotNull { m ->
            var h = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val min = m.groupValues[2].toIntOrNull() ?: 0
            val period = m.groupValues[3]
            val pm = period in setOf("مساء", "مساءا", "المساء", "بالليل", "ليلا", "م", "pm")
            if (pm && h < 12) h += 12
            if (!pm && period.isNotEmpty() && h == 12) h = 0
            if (h > 23 || min > 59) null else "%02d:%02d".format(h, min)
        }.toList().distinct().sorted()

        val times = when {
            explicitTimes.isNotEmpty() -> explicitTimes
            perDay != null -> defaultTimes(perDay)
            else -> emptyList()
        }

        val start = when {
            t.contains("غدا") || t.contains("بكره") || t.contains("tomorrow") -> today.plusDays(1)
            t.contains("اليوم") || t.contains("today") -> today
            else -> null
        }

        // Name: words before the first number / stop word, minus filler words.
        val words = t.split(Regex("\\s+")).filter { it.isNotBlank() }
        val nameWords = mutableListOf<String>()
        for (w in words) {
            if (w in fillerWords && nameWords.isEmpty()) continue
            if (w.first().isDigit() || w in stopWords || dosageRegex.matches(w)) break
            if (w in fillerWords) continue
            nameWords += w
            if (nameWords.size == 3) break
        }
        val name = restoreOriginal(raw, nameWords.joinToString(" "))

        return ParsedMedication(name, dosage, form, frequency, times, start)
    }

    /** Returns the matching words from the original sentence so the saved name keeps its spelling. */
    private fun restoreOriginal(raw: String, normalizedName: String): String {
        if (normalizedName.isBlank()) return ""
        val target = normalizedName.split(' ')
        val original = raw.trim().split(Regex("\\s+"))
        for (i in original.indices) {
            if (i + target.size > original.size) break
            val slice = original.subList(i, i + target.size)
            if (slice.map { normalize(it) } == target) return slice.joinToString(" ")
        }
        return normalizedName
    }
}
