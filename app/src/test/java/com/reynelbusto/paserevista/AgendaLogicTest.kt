package com.reynelbusto.paserevista

import com.reynelbusto.paserevista.presentation.agenda.AgendaDayInput
import com.reynelbusto.paserevista.presentation.agenda.BedMark
import com.reynelbusto.paserevista.presentation.agenda.agendaDayTitle
import com.reynelbusto.paserevista.presentation.agenda.computeDayMarks
import com.reynelbusto.paserevista.presentation.agenda.markBeds
import com.reynelbusto.paserevista.presentation.agenda.prettyDayEs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgendaLogicTest {

    @Test
    fun `markBeds distingue continuas de nuevas`() {
        val marks = markBeds(setOf("1", "2", "5"), setOf("1", "2", "3"))
        assertEquals(BedMark.CONTINUING, marks["1"])
        assertEquals(BedMark.CONTINUING, marks["2"])
        assertEquals(BedMark.NEW, marks["5"])
    }

    @Test
    fun `markBeds con día anterior vacío marca todo nuevo`() {
        val marks = markBeds(setOf("1", "2"), emptySet())
        assertTrue(marks.values.all { it == BedMark.NEW })
    }

    @Test
    fun `computeDayMarks compara cada día con el anterior de la lista`() {
        val days = listOf(
            // Hoy (lo más nuevo primero).
            AgendaDayInput("2026-10-08", setOf("1", "2", "3")),
            AgendaDayInput("2026-10-07", setOf("1", "2")),
            AgendaDayInput("2026-10-06", setOf("1", "4")),
        )
        val marks = computeDayMarks(days)
        // 8 oct: 1 y 2 vienen del 7; 3 es nueva.
        assertEquals(BedMark.CONTINUING, marks["2026-10-08"]!!["1"])
        assertEquals(BedMark.CONTINUING, marks["2026-10-08"]!!["2"])
        assertEquals(BedMark.NEW, marks["2026-10-08"]!!["3"])
        // 7 oct: 1 viene del 6; 2 es nueva.
        assertEquals(BedMark.CONTINUING, marks["2026-10-07"]!!["1"])
        assertEquals(BedMark.NEW, marks["2026-10-07"]!!["2"])
        // 6 oct: el más antiguo, sin anterior cargado → todo nuevo.
        assertTrue(marks["2026-10-06"]!!.values.all { it == BedMark.NEW })
    }

    @Test
    fun `computeDayMarks con lista vacía no falla`() {
        assertTrue(computeDayMarks(emptyList()).isEmpty())
    }

    @Test
    fun `prettyDayEs formatea en español`() {
        assertEquals("Jueves 8 de octubre", prettyDayEs("2026-10-08"))
        assertEquals("Miércoles 7 de octubre", prettyDayEs("2026-10-07"))
    }

    @Test
    fun `prettyDayEs con fecha inválida devuelve el texto tal cual`() {
        assertEquals("no-fecha", prettyDayEs("no-fecha"))
    }

    @Test
    fun `agendaDayTitle etiqueta Hoy y Ayer`() {
        assertEquals(
            "Hoy · Jueves 8 de octubre",
            agendaDayTitle("2026-10-08", "2026-10-08"),
        )
        assertEquals(
            "Ayer · Miércoles 7 de octubre",
            agendaDayTitle("2026-10-07", "2026-10-08"),
        )
        assertEquals(
            "Martes 6 de octubre",
            agendaDayTitle("2026-10-06", "2026-10-08"),
        )
    }
}
