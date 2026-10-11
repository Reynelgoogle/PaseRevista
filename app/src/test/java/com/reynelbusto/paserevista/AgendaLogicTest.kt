package com.reynelbusto.paserevista

import com.reynelbusto.paserevista.presentation.agenda.AgendaDayInput
import com.reynelbusto.paserevista.presentation.agenda.BedMark
import com.reynelbusto.paserevista.presentation.agenda.DayEventType
import com.reynelbusto.paserevista.presentation.agenda.DayLayout
import com.reynelbusto.paserevista.presentation.agenda.agendaDayTitle
import com.reynelbusto.paserevista.presentation.agenda.buildDayEvents
import com.reynelbusto.paserevista.presentation.agenda.classifyHistoryEvent
import com.reynelbusto.paserevista.presentation.agenda.computeDayIndices
import com.reynelbusto.paserevista.presentation.agenda.computeDayMarks
import com.reynelbusto.paserevista.presentation.agenda.dayOfMonth
import com.reynelbusto.paserevista.presentation.agenda.daySummary
import com.reynelbusto.paserevista.presentation.agenda.formatHour
import com.reynelbusto.paserevista.presentation.agenda.markBeds
import com.reynelbusto.paserevista.presentation.agenda.prettyDayEs
import com.reynelbusto.paserevista.domain.model.CaseHistoryEntry
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

    /* ── Bitácora del día ── */

    @Test
    fun `classifyHistoryEvent por convenciones de texto`() {
        assertEquals(DayEventType.INGRESO, classifyHistoryEvent("Cama 5 añadida"))
        assertEquals(DayEventType.ALTA, classifyHistoryEvent("Alta — egreso"))
        assertEquals(DayEventType.ALTA, classifyHistoryEvent("Alta — a su domicilio"))
        assertEquals(
            DayEventType.PENDIENTE,
            classifyHistoryEvent("Pendiente completado: hemoglobina"),
        )
        assertEquals(
            DayEventType.PIZARRA,
            classifyHistoryEvent("Marcada LISTO — sube a la pizarra"),
        )
        assertEquals(
            DayEventType.PIZARRA,
            classifyHistoryEvent("Vuelve a Pendiente — baja de la pizarra"),
        )
        assertEquals(
            DayEventType.CAMBIO,
            classifyHistoryEvent("Diagnóstico: colecistitis → colelitiasis"),
        )
    }

    @Test
    fun `buildDayEvents ordena por hora y resuelve la cama`() {
        val entries = listOf(
            CaseHistoryEntry("h2", "p2", "j1", 2000L, "Alta — egreso"),
            CaseHistoryEntry("h1", "p1", "j1", 1000L, "Cama 5 añadida"),
            CaseHistoryEntry("h3", "p9", "j1", 1500L, "Diagnóstico: x"),
        )
        val events = buildDayEvents(entries, mapOf("p1" to "5", "p2" to "7"))
        assertEquals(3, events.size)
        assertEquals(1000L, events[0].occurredAt)
        assertEquals("5", events[0].bed)
        assertEquals(DayEventType.INGRESO, events[0].type)
        assertEquals(1500L, events[1].occurredAt)
        assertEquals(null, events[1].bed)
        assertEquals(DayEventType.CAMBIO, events[1].type)
        assertEquals(2000L, events[2].occurredAt)
        assertEquals("7", events[2].bed)
        assertEquals(DayEventType.ALTA, events[2].type)
    }

    /* ── Resumen del día ── */

    @Test
    fun `daySummary completo con plurales`() {
        assertEquals(
            "12 camas · 2 ingresos · 1 alta · 4 pendientes",
            daySummary(camas = 12, ingresos = 2, altas = 1, pendientes = 4),
        )
    }

    @Test
    fun `daySummary omite ceros y singulariza`() {
        assertEquals("1 cama", daySummary(camas = 1, ingresos = 0, altas = 0, pendientes = 0))
        assertEquals(
            "8 camas · 1 pendiente",
            daySummary(camas = 8, ingresos = 0, altas = 0, pendientes = 1),
        )
        assertEquals(
            "3 camas · 1 ingreso",
            daySummary(camas = 3, ingresos = 1, altas = 0, pendientes = 0),
        )
    }

    /* ── Hora y tira de fechas ── */

    @Test
    fun `formatHour devuelve HH-mm`() {
        // 2026-10-11T14:35:00Z en cualquier zona: formato de 5 caracteres.
        val h = formatHour(1760193300000L)
        assertTrue(h.matches(Regex("\\d{2}:\\d{2}")))
    }

    @Test
    fun `dayOfMonth extrae el día`() {
        assertEquals("8", dayOfMonth("2026-10-08"))
        assertEquals("1", dayOfMonth("2026-10-01"))
        assertEquals("no-fecha", dayOfMonth("no-fecha"))
    }

    @Test
    fun `computeDayIndices respeta el orden de ítems por día`() {
        // Día 1: cabecera + nota + etiqueta + 2 eventos + 3 camas = 8 ítems.
        // Día 2: cabecera + nota + 1 cama = 3 ítems (sin eventos).
        val indices = computeDayIndices(
            listOf(
                DayLayout("j1", hasEvents = true, eventCount = 2, rowCount = 3),
                DayLayout("j2", hasEvents = false, eventCount = 0, rowCount = 1),
            ),
        )
        assertEquals(0, indices["j1"])
        assertEquals(8, indices["j2"])
    }

    @Test
    fun `computeDayIndices con lista vacía no falla`() {
        assertTrue(computeDayIndices(emptyList()).isEmpty())
    }
}
