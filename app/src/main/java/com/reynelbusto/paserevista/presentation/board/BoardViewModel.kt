package com.reynelbusto.paserevista.presentation.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reynelbusto.paserevista.di.AppContainer
import com.reynelbusto.paserevista.domain.model.BoardColumn
import com.reynelbusto.paserevista.domain.usecase.BoardItem
import com.reynelbusto.paserevista.domain.usecase.BoardUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

data class BoardUiState(
    val isLoading: Boolean = true,
    val proposed: List<BoardItem> = emptyList(),
    val scheduled: List<BoardItem> = emptyList(),
    val done: List<BoardItem> = emptyList(),
    /** Errores transitorios (p. ej. mover tarjeta): van al Snackbar. */
    val error: String? = null,
    /** Fallo de la carga inicial (M7): se muestra con botón Reintentar. */
    val loadError: String? = null,
)

/**
 * Pizarra Kanban: Propuesto → Programado → Realizado.
 * Se alimenta sola de las tarjetas marcadas Listo.
 */
class BoardViewModel(private val board: BoardUseCase) : ViewModel() {

    private val _uiState = MutableStateFlow(BoardUiState())
    val uiState: StateFlow<BoardUiState> = _uiState
    private var collectJob: Job? = null

    init {
        subscribe()
    }

    /**
     * M8: (re)suscribe el flujo de la pizarra. `retryWhen` reintenta hasta 3
     * veces con espera creciente ante fallos transitorios; si se agotan, el
     * `catch` deja `loadError` y el botón Reintentar vuelve a suscribir.
     * Sin esto, tras el primer `.catch` el Flow moría en silencio.
     */
    fun retry() {
        _uiState.value = _uiState.value.copy(isLoading = true, loadError = null)
        subscribe()
    }

    private fun subscribe() {
        collectJob?.cancel()
        collectJob = viewModelScope.launch {
            board.observeBoard()
                .map { procedures ->
                    // Blindaje: cada ítem se enriquece aislado; si uno falla
                    // (dato corrupto, mapeo), se omite sin tumbar la pizarra.
                    procedures.mapNotNull { proc ->
                        runCatching { board.enrich(proc) }.getOrNull()
                    }
                }
                .retryWhen { _, attempt ->
                    if (attempt < 3) {
                        delay(500L * (attempt + 1))
                        true
                    } else {
                        false
                    }
                }
                .catch {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        loadError = "No se pudo cargar la pizarra.",
                    )
                }
                .collect { items ->
                    // columnOf es total: agrupa por columna; los no mapeados se omiten.
                    val byColumn = items.groupBy { board.columnOf(it.procedure) }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        loadError = null,
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
                _uiState.value = _uiState.value.copy(error = e.message ?: "Error inesperado")
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
