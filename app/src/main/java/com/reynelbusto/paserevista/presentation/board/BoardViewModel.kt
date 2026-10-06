package com.reynelbusto.paserevista.presentation.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reynelbusto.paserevista.di.AppContainer
import com.reynelbusto.paserevista.domain.model.BoardColumn
import com.reynelbusto.paserevista.domain.usecase.BoardItem
import com.reynelbusto.paserevista.domain.usecase.BoardUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class BoardUiState(
    val isLoading: Boolean = true,
    val proposed: List<BoardItem> = emptyList(),
    val scheduled: List<BoardItem> = emptyList(),
    val done: List<BoardItem> = emptyList(),
    val error: String? = null,
)

/**
 * Pizarra Kanban: Propuesto → Programado → Realizado.
 * Se alimenta sola de las tarjetas marcadas Listo.
 */
class BoardViewModel(private val board: BoardUseCase) : ViewModel() {

    private val _uiState = MutableStateFlow(BoardUiState())
    val uiState: StateFlow<BoardUiState> = _uiState

    init {
        viewModelScope.launch {
            board.observeBoard()
                .map { procedures ->
                    // Blindaje: cada ítem se enriquece aislado; si uno falla
                    // (dato corrupto, mapeo), se omite sin tumbar la pizarra.
                    procedures.mapNotNull { proc ->
                        runCatching { board.enrich(proc) }.getOrNull()
                    }
                }
                .catch { _uiState.value = BoardUiState(isLoading = false, error = "No se pudo cargar la pizarra.") }
                .collect { items ->
                    // columnOf es total: agrupa por columna; los no mapeados se omiten.
                    val byColumn = items.groupBy { board.columnOf(it.procedure) }
                    _uiState.value = BoardUiState(
                        isLoading = false,
                        proposed = byColumn[BoardColumn.PROPOSED].orEmpty(),
                        scheduled = byColumn[BoardColumn.SCHEDULED].orEmpty(),
                        done = byColumn[BoardColumn.DONE].orEmpty(),
                    )
                }
        }
    }

    fun moveTo(procedureId: String, column: BoardColumn) {
        viewModelScope.launch {
            try {
                board.moveTo(procedureId, column)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BoardViewModel(container.board) as T
    }
}
