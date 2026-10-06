package com.reynelbusto.paserevista

import com.reynelbusto.paserevista.core.treatmentDay
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * El día de tratamiento se DERIVA de la fecha real de inicio menos suspensiones.
 * Jamás se almacena como texto ("D3").
 */
class TreatmentDayTest {

    @Test
    fun `dia 1 es la fecha de inicio`() {
        assertEquals(1, treatmentDay("2026-10-01", "2026-10-01"))
    }

    @Test
    fun `el dia avanza con el calendario`() {
        // 04/10 inicio → 06/10 es D3, sin que nadie lo escriba.
        assertEquals(3, treatmentDay("2026-10-04", "2026-10-06"))
    }

    @Test
    fun `la suspension congela el dia`() {
        // Suspendido el 06/10 (D3); al reanudar el 08/10 sigue D3.
        val suspensions = listOf("2026-10-06" to "2026-10-07")
        assertEquals(3, treatmentDay("2026-10-04", "2026-10-08", suspensions))
    }

    @Test
    fun `suspension parcial solo resta los dias solapados`() {
        // Suspensión 10/10–12/10; hoy 10/10 → solo resta 1 día.
        val suspensions = listOf("2026-10-10" to "2026-10-12")
        assertEquals(9, treatmentDay("2026-10-01", "2026-10-10", suspensions))
    }

    @Test
    fun `suspension fuera del rango no afecta`() {
        val suspensions = listOf("2026-09-01" to "2026-09-05")
        assertEquals(5, treatmentDay("2026-10-01", "2026-10-05", suspensions))
    }
}
