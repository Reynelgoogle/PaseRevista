package com.reynelbusto.paserevista

import com.reynelbusto.paserevista.domain.model.ClinicalPriority
import com.reynelbusto.paserevista.domain.model.Procedure
import com.reynelbusto.paserevista.domain.model.ProcedureState
import com.reynelbusto.paserevista.domain.usecase.BoardItem
import com.reynelbusto.paserevista.presentation.board.dayTitle
import com.reynelbusto.paserevista.presentation.board.groupByDate
import com.reynelbusto.paserevista.presentation.board.monthGrid
import com.reynelbusto.paserevista.presentation.board.monthTitle
import com.reynelbusto.paserevista.presentation.board.sortedByBed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class BoardCalendarTest {

    private fun item(id: String, date: String?, bed: String? = "1"): BoardItem =
        BoardItem(
            procedure = Procedure(
                id = id,
                patientId = "p-$id",
                kind = "RTU próstata",
                scheduledDate = date,
                status = ProcedureState.PENDING,
                priority = ClinicalPriority.P3,
                createdAt = 0L,
                updatedAt = 0L,
            ),
            displayName = "Cama ${bed ?: "—"}",
            bed = bed,
        )

    @Test
    fun `monthGrid octubre 2026 tiene 42 celdas y empieza el lunes 28 de septiembre`() {
        val grid = monthGrid(2026, 10)
        assertEquals(42, grid.size)
        // Octubre 2026 empieza en jueves → la rejilla arranca el lunes 28/sep.
        assertEquals(LocalDate.of(2026, 9, 28), grid[0].date)
        assertFalse(grid[0].inMonth)
        assertEquals(LocalDate.of(2026, 10, 1), grid[3].date)
        assertTrue(grid[3].inMonth)
        assertEquals(LocalDate.of(2026, 11, 8), grid[41].date)
        assertFalse(grid[41].inMonth)
        // Cada fila empieza en lunes.
        grid.chunked(7).forEach { week ->
            assertEquals(7, week.size)
            assertEquals(DayOfWeek.MONDAY, week[0].date.dayOfWeek)
        }
    }

    @Test
    fun `monthGrid mes que empieza en lunes no tiene relleno previo`() {
        // Junio 2026 empieza en lunes.
        val grid = monthGrid(2026, 6)
        assertEquals(LocalDate.of(2026, 6, 1), grid[0].date)
        assertTrue(grid[0].inMonth)
    }

    @Test
    fun `monthGrid mes que empieza en domingo rellena 6 dias`() {
        // Febrero 2026 empieza en domingo.
        val grid = monthGrid(2026, 2)
        assertEquals(LocalDate.of(2026, 1, 26), grid[0].date)
        assertFalse(grid[0].inMonth)
        assertEquals(LocalDate.of(2026, 2, 1), grid[6].date)
        assertTrue(grid[6].inMonth)
    }

    @Test
    fun `monthTitle en espanol`() {
        assertEquals("Octubre 2026", monthTitle(2026, 10))
        assertEquals("Enero 2027", monthTitle(2027, 1))
    }

    @Test
    fun `dayTitle en espanol`() {
        assertEquals("12 de octubre", dayTitle("2026-10-12"))
    }

    @Test
    fun `groupByDate agrupa por fecha y separa sin fecha`() {
        val items = listOf(
            item("a", "2026-10-12"),
            item("b", "2026-10-12"),
            item("c", "2026-10-15"),
            item("d", null),
            item("e", ""),
            item("f", "   "),
            item("g", "no-es-fecha"),
        )
        val groups = groupByDate(items)
        assertEquals(2, groups.byDate.size)
        assertEquals(listOf("a", "b"), groups.byDate["2026-10-12"]!!.map { it.procedure.id })
        assertEquals(listOf("c"), groups.byDate["2026-10-15"]!!.map { it.procedure.id })
        assertEquals(listOf("d", "e", "f", "g"), groups.undated.map { it.procedure.id })
    }

    @Test
    fun `groupByDate normaliza fechas con ceros`() {
        val groups = groupByDate(listOf(item("a", "2026-1-5")))
        assertEquals(1, groups.byDate["2026-01-05"]!!.size)
        assertTrue(groups.undated.isEmpty())
    }

    @Test
    fun `sortedByBed numericas primero por numero`() {
        val items = listOf(
            item("a", null, "10"),
            item("b", null, "2"),
            item("c", null, "A"),
            item("d", null, null),
            item("e", null, "1"),
        )
        assertEquals(
            listOf("e", "b", "a", "c", "d"),
            items.sortedByBed().map { it.procedure.id },
        )
    }
}
