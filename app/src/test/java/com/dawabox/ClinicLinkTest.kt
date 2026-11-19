package com.dawabox

import com.dawabox.data.AppointmentEntity
import com.dawabox.data.ClinicLink
import com.dawabox.data.DayCompliance
import com.dawabox.data.MedicationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Base64

class ClinicLinkTest {

    @Test fun prescriptionRoundTripKeepsArabicText() {
        val rx = ClinicLink.Prescription(
            patient = "أحمد العتيبي", clinic = "عيادة السكري",
            meds = listOf(MedicationEntity(name = "ميتفورمين", dosage = "500 ملغ", times = "08:00,20:00", instructions = "بعد الأكل", compartment = 2, stock = 60)),
            appointments = listOf(AppointmentEntity(doctor = "د. سارة", place = "العيادة 3", dateTime = "2026-11-25T10:30")),
        )
        val back = ClinicLink.decodePrescription("وصفتك من العيادة:\n" + ClinicLink.encodePrescription(rx) + "\nمع السلامة")
        assertNotNull(back)
        assertEquals("أحمد العتيبي", back!!.patient)
        assertEquals("ميتفورمين", back.meds[0].name)
        assertEquals("08:00,20:00", back.meds[0].times)
        assertEquals(2, back.meds[0].compartment)
        assertEquals("د. سارة", back.appointments[0].doctor)
        assertEquals("2026-11-25T10:30", back.appointments[0].dateTime)
    }

    /** The exact JSON the web platform produces (platform/index.html, rxCode()). */
    @Test fun readsCodeMadeByWebPlatform() {
        val json = """{"v":1,"type":"rx","patient":"فاطمة","clinic":"تذكرة","meds":[{"name":"أملوديبين","dosage":"5 ملغ","form":"TABLET","frequency":"DAILY","times":["09:00"],"startDate":"2026-11-19","compartment":1,"instructions":"","stock":30,"channels":["APP","SMS","EMAIL"]}],"appointments":[]}"""
        val code = "DAWA-RX:" + Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))
        val rx = ClinicLink.decodePrescription(code)!!
        assertEquals("أملوديبين", rx.meds[0].name)
        assertEquals("APP,SMS,EMAIL", rx.meds[0].channels)
    }

    @Test fun reportRoundTrip() {
        val r = ClinicLink.Report(
            "خالد", listOf(DayCompliance(LocalDate.of(2026, 11, 18), taken = 3, missed = 1, total = 4)),
            listOf(MedicationEntity(name = "بخاخ", dosage = "بختان", stock = 4))
        )
        val msg = ClinicLink.reportMessage(r)
        assertTrue(msg.contains("75%"))
        val back = ClinicLink.decodeReport(msg)!!
        assertEquals(3, back.days[0].taken)
        assertEquals(4, back.meds[0].stock)
    }

    @Test fun garbageIsRejected() {
        assertNull(ClinicLink.decodePrescription("hello"))
        assertNull(ClinicLink.decodePrescription("DAWA-RX:not-base64!!"))
    }
}
