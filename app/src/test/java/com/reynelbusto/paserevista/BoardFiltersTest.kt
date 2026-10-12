package com.reynelbusto.paserevista

import com.reynelbusto.paserevista.domain.model.BoardColumn
import com.reynelbusto.paserevista.domain.model.Procedure
import com.reynelbusto.paserevista.domain.model.ProcedureState
import com.reynelbusto.paserevista.presentation.board.BoardFilters
import com.reynelbusto.paserevista.presentation.board.BoardUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests del buscador flotante y las píldoras de estado de la pizarra. */
class BoardFiltersTest {

    private fun item(
        id: String,
        kind: String,
        status: ProcedureState,
        bed: String? = "12",
        name: String = "Carlos Mendez",
    ) = com.reynelbusto.paserevista.domain.usecase.BoardItem(
        procedure = Procedure(
            id = id,
            patientId = "p-$id",
            kind = kind,
            status = status,
            createdAt = 1L,
            updatedAt = 1L,
        ),
        displayName = name,
        bed = bed,
    )

    private val proposed = item("1", "Apendicectomía", ProcedureState.PENDING, bed = "12")
    private val scheduled = item("2", "Colecistectomía", ProcedureState.PREPARATION, bed = "05", name = "José Pérez")
    private val done = item("3", "Hernioplastia", ProcedureState.PERFORMED, bed = "08", name = "Luis Torres")
    private val canceled = item("4", "Biopsia", ProcedureState.CANCELED, bed = "21")

    private val all = listOf(proposed, scheduled, done)

    // --- Buscador ---

    @Test fun `consulta vacia devuelve todo`() {
        assertEquals(all, BoardFilters.byQuery(all, ""))
        assertEquals(all, BoardFilters.byQuery(all, "   "))
    }

    @Test fun `filtra por cama`() {
        val r = BoardFilters.byQuery(all, "05")
        assertEquals(listOf(scheduled), r)
    }

    @Test fun `filtra por paciente sin importar mayusculas`() {
        val r = BoardFilters.byQuery(all, "cArLoS")
        assertEquals(listOf(proposed), r)
    }

    @Test fun `filtra por procedimiento`() {
        val r = BoardFilters.byQuery(all, "hernio")
        assertEquals(listOf(done), r)
    }

    @Test fun `ignora tildes`() {
        // "José" se encuentra buscando "jose".
        val r = BoardFilters.byQuery(all, "jose")
        assertEquals(listOf(scheduled), r)
        // Y al revés: la tilde en la consulta también funciona.
        val r2 = BoardFilters.byQuery(all, "apendicectomía")
        assertEquals(listOf(proposed), r2)
    }

    @Test fun `sin coincidencias devuelve vacio`() {
        assertTrue(BoardFilters.byQuery(all, "zzz").isEmpty())
    }

    @Test fun `cama nula no tumba el filtro`() {
        val noBed = item("9", "Sutura", ProcedureState.PENDING, bed = null, name = "Ana")
        val r = BoardFilters.byQuery(listOf(noBed), "ana")
        assertEquals(listOf(noBed), r)
    }

    // --- Píldoras ---

    @Test fun `pildora null devuelve todo`() {
        assertEquals(all, BoardFilters.byColumn(all, null))
    }

    @Test fun `pildora propuesto solo pendientes`() {
        assertEquals(listOf(proposed), BoardFilters.byColumn(all, BoardColumn.PROPOSED))
    }

    @Test fun `pildora programado solo en preparacion`() {
        assertEquals(listOf(scheduled), BoardFilters.byColumn(all, BoardColumn.SCHEDULED))
    }

    @Test fun `pildora realizado solo realizados`() {
        assertEquals(listOf(done), BoardFilters.byColumn(all, BoardColumn.DONE))
    }

    @Test fun `cancelado nunca pasa el filtro de columna`() {
        val withCanceled = all + canceled
        assertTrue(BoardFilters.byColumn(withCanceled, BoardColumn.PROPOSED).none { it.procedure.id == "4" })
        assertTrue(BoardFilters.byColumn(withCanceled, BoardColumn.DONE).none { it.procedure.id == "4" })
    }

    // --- Combinado ---

    @Test fun `apply combina buscador y pildora por lista`() {
        val state = BoardUiState(
            proposed = listOf(proposed),
            scheduled = listOf(scheduled),
            done = listOf(done),
        )
        // Buscador "0" coincide con las tres camas (12, 05, 08); píldora Programado deja una.
        val r = BoardFilters.apply(state, "0", BoardColumn.SCHEDULED)
        assertEquals(emptyList<com.reynelbusto.paserevista.domain.usecase.BoardItem>(), r.proposed)
        assertEquals(listOf(scheduled), r.scheduled)
        assertEquals(emptyList<com.reynelbusto.paserevista.domain.usecase.BoardItem>(), r.done)
        // El estado original no se toca (las píldoras cuentan el total real).
        assertEquals(1, state.proposed.size)
        assertEquals(1, state.done.size)
    }

    @Test fun `apply sin filtros devuelve las mismas listas`() {
        val state = BoardUiState(proposed = listOf(proposed), scheduled = listOf(scheduled), done = listOf(done))
        val r = BoardFilters.apply(state, "", null)
        assertEquals(state.proposed, r.proposed)
        assertEquals(state.scheduled, r.scheduled)
        assertEquals(state.done, r.done)
    }
}
