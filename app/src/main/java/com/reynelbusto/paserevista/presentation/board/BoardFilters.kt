package com.reynelbusto.paserevista.presentation.board

import com.reynelbusto.paserevista.domain.model.BoardColumn
import com.reynelbusto.paserevista.domain.usecase.BoardItem
import java.text.Normalizer

/**
 * Filtros de la pizarra: buscador flotante + píldoras de estado.
 * Lógica pura, sin Android: testeable en JVM.
 */
object BoardFilters {

    /**
     * Filtra ítems por texto libre: coincide con cama, paciente o
     * procedimiento. Insensible a mayúsculas/minúsculas y a tildes
     * ("jose" encuentra "José"). Consulta vacía = todo.
     */
    fun byQuery(items: List<BoardItem>, query: String): List<BoardItem> {
        val q = fold(query).trim()
        if (q.isEmpty()) return items
        return items.filter { item ->
            fold(item.bed.orEmpty()).contains(q) ||
                fold(item.displayName).contains(q) ||
                fold(item.procedure.kind).contains(q)
        }
    }

    /**
     * Filtra por columna de estado (píldoras). `null` = todas.
     * Los estados sin columna (p. ej. CANCELED) nunca pasan el filtro:
     * igual que en la pizarra, se omiten.
     */
    fun byColumn(items: List<BoardItem>, column: BoardColumn?): List<BoardItem> {
        if (column == null) return items
        return items.filter { item ->
            BoardColumn.entries.firstOrNull { it.state == item.procedure.status } == column
        }
    }

    /**
     * Aplica buscador y píldora sobre las tres listas del estado de la
     * pizarra. Devuelve una copia filtrada (el estado original no cambia,
     * así las píldoras siempre cuentan el total real).
     */
    fun apply(state: BoardUiState, query: String, column: BoardColumn?): BoardUiState =
        state.copy(
            proposed = byColumn(byQuery(state.proposed, query), column),
            scheduled = byColumn(byQuery(state.scheduled, query), column),
            done = byColumn(byQuery(state.done, query), column),
        )

    /** Minúsculas + sin tildes, para comparar texto clínico. */
    internal fun fold(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .lowercase()
}
