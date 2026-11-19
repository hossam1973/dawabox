package com.dawabox.data

import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Data exchange with the Tadhkira clinic platform, without a server.
 *
 *  - Clinic -> app: a prescription code "DAWA-RX:<base64 json>" holding
 *    medications and appointments. The patient pastes it in the app.
 *  - App -> clinic: a report code "DAWA-REPORT:<base64 json>" holding the
 *    last 7 days of adherence and current stock. The clinic pastes it in
 *    the platform.
 *
 * The same format is implemented in platform/index.html.
 */
object ClinicLink {
    const val RX_PREFIX = "DAWA-RX:"
    const val REPORT_PREFIX = "DAWA-REPORT:"

    data class Prescription(
        val patient: String,
        val clinic: String,
        val meds: List<MedicationEntity>,
        val appointments: List<AppointmentEntity>,
    )

    data class Report(
        val patient: String,
        val days: List<DayCompliance>,
        val meds: List<MedicationEntity>,
    )

    private fun b64(json: String) = Base64.getEncoder().encodeToString(json.toByteArray(StandardCharsets.UTF_8))
    private fun unb64(s: String) = String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8)

    /** Finds "<prefix><base64>" anywhere in [text] (e.g. inside a WhatsApp message). */
    private fun payload(text: String, prefix: String): String? {
        val i = text.indexOf(prefix)
        if (i < 0) return null
        return text.substring(i + prefix.length).trim().takeWhile { !it.isWhitespace() }.ifBlank { null }
    }

    fun decodePrescription(text: String): Prescription? = runCatching {
        val o = JSONObject(unb64(payload(text, RX_PREFIX) ?: return null))
        val meds = o.optJSONArray("meds").objects().map { m ->
            val times = m.optJSONArray("times")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: listOf("08:00")
            MedicationEntity(
                name = m.getString("name"),
                dosage = m.optString("dosage", ""),
                form = m.optString("form", MedForm.TABLET.name).takeIf { f -> MedForm.entries.any { it.name == f } } ?: MedForm.TABLET.name,
                frequency = m.optString("frequency", Frequency.DAILY.name).takeIf { f -> Frequency.entries.any { it.name == f } } ?: Frequency.DAILY.name,
                times = times.filter { it.matches(Regex("\\d{2}:\\d{2}")) }.joinToString(","),
                startDate = m.optString("startDate", java.time.LocalDate.now().toString()),
                compartment = m.optInt("compartment", 1).coerceIn(1, 7),
                instructions = m.optString("instructions", ""),
                stock = m.optInt("stock", 30),
                channels = m.optJSONArray("channels")?.let { a -> (0 until a.length()).map { a.getString(it) } }
                    ?.filter { c -> AlertChannel.entries.any { it.name == c } }?.joinToString(",")?.ifBlank { null }
                    ?: "APP,VOICE,SMS",
            )
        }
        val apts = o.optJSONArray("appointments").objects().map { a ->
            AppointmentEntity(
                doctor = a.getString("doctor"),
                place = a.optString("place", ""),
                dateTime = a.getString("dateTime").take(16),
                notes = a.optString("notes", ""),
            )
        }
        Prescription(o.optString("patient", ""), o.optString("clinic", ""), meds, apts)
    }.getOrNull()

    fun encodePrescription(p: Prescription): String {
        val o = JSONObject()
            .put("v", 1).put("type", "rx").put("patient", p.patient).put("clinic", p.clinic)
            .put("meds", JSONArray(p.meds.map { m ->
                JSONObject().put("name", m.name).put("dosage", m.dosage).put("form", m.form)
                    .put("frequency", m.frequency).put("times", JSONArray(m.timeList))
                    .put("startDate", m.startDate).put("compartment", m.compartment)
                    .put("instructions", m.instructions).put("stock", m.stock)
                    .put("channels", JSONArray(m.channelSet.map { it.name }))
            }))
            .put("appointments", JSONArray(p.appointments.map { a ->
                JSONObject().put("doctor", a.doctor).put("place", a.place).put("dateTime", a.dateTime).put("notes", a.notes)
            }))
        return RX_PREFIX + b64(o.toString())
    }

    fun encodeReport(r: Report): String {
        val o = JSONObject()
            .put("v", 1).put("type", "report").put("patient", r.patient)
            .put("days", JSONArray(r.days.map { d ->
                JSONObject().put("date", d.date.toString()).put("taken", d.taken).put("missed", d.missed).put("total", d.total)
            }))
            .put("meds", JSONArray(r.meds.map { m -> JSONObject().put("name", m.name).put("dosage", m.dosage).put("stock", m.stock) }))
        return REPORT_PREFIX + b64(o.toString())
    }

    fun decodeReport(text: String): Report? = runCatching {
        val o = JSONObject(unb64(payload(text, REPORT_PREFIX) ?: return null))
        Report(
            o.optString("patient", ""),
            o.optJSONArray("days").objects().map {
                DayCompliance(java.time.LocalDate.parse(it.getString("date")), it.optInt("taken"), it.optInt("missed"), it.optInt("total"))
            },
            o.optJSONArray("meds").objects().map { MedicationEntity(name = it.getString("name"), dosage = it.optString("dosage"), stock = it.optInt("stock")) },
        )
    }.getOrNull()

    /** Human-readable report to share by WhatsApp / email, with the code at the end. */
    fun reportMessage(r: Report): String {
        val total = r.days.sumOf { it.total }
        val taken = r.days.sumOf { it.taken }
        val pct = if (total == 0) 0 else taken * 100 / total
        return buildString {
            appendLine("تقرير الالتزام بالدواء" + if (r.patient.isNotBlank()) " – ${r.patient}" else "")
            appendLine("آخر 7 أيام: $pct% (أُخذت $taken من $total جرعة)")
            r.days.forEach { d -> appendLine("${d.date}: ${d.taken}/${d.total}") }
            if (r.meds.isNotEmpty()) appendLine("المخزون: " + r.meds.joinToString("، ") { "${it.name} ${it.stock}" })
            appendLine()
            appendLine("رمز التقرير لمنصة العيادة:")
            append(encodeReport(r))
        }
    }

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
}
