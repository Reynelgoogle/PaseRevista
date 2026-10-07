package com.reynelbusto.paserevista

import com.reynelbusto.paserevista.core.isoDateFromPickerMillis
import com.reynelbusto.paserevista.core.pickerMillisFromIso
import com.reynelbusto.paserevista.domain.model.ClinicalOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

/**
 * Agilización de entrada de datos: las listas viven en Kotlin puro y son testeables.
 * Regla de Reynel: todo desplegable abierto lleva "Otro…" con texto libre.
 */
class ClinicalOptionsTest {

    private fun assertCleanList(list: List<String>, name: String) {
        assertTrue("$name vacía", list.isNotEmpty())
        assertTrue("$name con duplicados", list.size == list.toSet().size)
        assertTrue("$name con blancos", list.none { it.isBlank() })
    }

    @Test fun `listas abiertas no vacias sin duplicados`() {
        assertCleanList(ClinicalOptions.PROCEDURES, "PROCEDURES")
        assertCleanList(ClinicalOptions.CURRENT_STATES, "CURRENT_STATES")
        assertCleanList(ClinicalOptions.ANTIBIOTICS, "ANTIBIOTICS")
        assertCleanList(ClinicalOptions.INTERCONSULT_SERVICES, "INTERCONSULT_SERVICES")
    }

    @Test fun `grupo sanguineo es conjunto cerrado de 8`() {
        assertEquals(8, ClinicalOptions.BLOOD_GROUPS.size)
        assertCleanList(ClinicalOptions.BLOOD_GROUPS, "BLOOD_GROUPS")
        assertFalse("grupo sanguíneo no lleva Otro…", ClinicalOptions.OTHER in ClinicalOptions.BLOOD_GROUPS)
        assertTrue("O+" in ClinicalOptions.BLOOD_GROUPS)
        assertTrue("AB−" in ClinicalOptions.BLOOD_GROUPS)
    }

    @Test fun `withOther agrega Otro al final`() {
        val with = ClinicalOptions.withOther(ClinicalOptions.ANTIBIOTICS)
        assertEquals(ClinicalOptions.OTHER, with.last())
        assertEquals(ClinicalOptions.ANTIBIOTICS.size + 1, with.size)
        // Idempotente: no duplica si ya está.
        assertEquals(with.size, ClinicalOptions.withOther(with).size)
    }

    @Test fun `matchOrOther resuelve seleccion y texto manual`() {
        val opts = ClinicalOptions.withOther(ClinicalOptions.CURRENT_STATES)
        // Valor en lista → selección directa, sin texto manual.
        assertEquals("Estable" to "", ClinicalOptions.matchOrOther("Estable", opts))
        // Valor manual previo → "Otro…" + texto.
        assertEquals(
            ClinicalOptions.OTHER to "En diálisis",
            ClinicalOptions.matchOrOther("En diálisis", opts),
        )
        // Vacío → nada seleccionado.
        assertEquals(null to "", ClinicalOptions.matchOrOther(null, opts))
        assertEquals(null to "", ClinicalOptions.matchOrOther("  ", opts))
    }

    @Test fun `effectiveValue guarda texto manual o seleccion`() {
        assertEquals("Estable", ClinicalOptions.effectiveValue("Estable", ""))
        assertEquals("En diálisis", ClinicalOptions.effectiveValue(ClinicalOptions.OTHER, "En diálisis"))
        assertNull(ClinicalOptions.effectiveValue(ClinicalOptions.OTHER, "  "))
        assertNull(ClinicalOptions.effectiveValue(null, ""))
    }

    @Test fun `fecha del DatePicker mapea a ISO sin corrimiento de dia`() {
        // 2026-10-06 00:00 UTC (lo que entrega el DatePicker) → "2026-10-06",
        // incluso en zonas al oeste de Greenwich.
        val millis = java.time.LocalDate.of(2026, 10, 6)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals("2026-10-06", isoDateFromPickerMillis(millis))
    }

    @Test fun `ida y vuelta ISO a millis del picker`() {
        listOf("2026-01-01", "2026-10-06", "2027-12-31").forEach { iso ->
            assertEquals(iso, isoDateFromPickerMillis(pickerMillisFromIso(iso)))
        }
    }

    @Test fun `procedimientos esperados presentes`() {
        assertTrue("RTU próstata" in ClinicalOptions.PROCEDURES)
        assertTrue("Catéter doble J (retirar)" in ClinicalOptions.PROCEDURES)
        assertTrue("Varicocelectomía" in ClinicalOptions.PROCEDURES)
    }

    @Test fun `antibioticos esperados presentes`() {
        assertTrue("Ceftriaxona" in ClinicalOptions.ANTIBIOTICS)
        assertTrue("Meropenem" in ClinicalOptions.ANTIBIOTICS)
    }
}
