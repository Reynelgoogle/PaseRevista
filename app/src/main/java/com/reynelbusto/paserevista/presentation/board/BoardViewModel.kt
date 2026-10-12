package com.reynelbusto.paserevista.presentation.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reynelbusto.paserevista.di.AppContainer
import com.reynelbusto.paserevista.core.IsoDate
import com.reynelbusto.paserevista.domain.model.BoardColumn
import com.reynelbusto.paserevista.domain.model.ClinicalPriority
import com.reynelbusto.paserevista.domain.model.Patient
import com.reynelbusto.paserevista.domain.repository.PatientRepository
import com.reynelbusto.paserevista.domain.usecase.BoardItem
import com.reynelbusto.paserevista.domain.usecase.BoardUseCase
import com.reynelbusto.paserevista.domain.usecase.DEFAULT_SERVICE_ID
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
    /** Texto del buscador flotante. */
    val query: String = "",
    /** Píldora de estado activa. null = todas. */
    val columnFilter: BoardColumn? = null,
    /** Diálogo de agendar (FAB +). */
    val showAddDialog: Boolean = false,
    /** Pacientes para el selector del diálogo. */
    val dialogPatients: List<Patient> = emptyList(),
    /** Errores transitorios (p. ej. mover tarjeta): van al Snackbar. */
    val error: String? = null,
    /** Fallo de la carga inicial (M7): se muestra con botón Reintentar. */
    val loadError: String? = null,
)

/**
 * Pizarra Kanban: Propuesto → Programado → Realizado.
 * Se alimenta sola de las tarjetas marcadas Listo.
 */
class BoardViewModel(
    private val board: BoardUseCase,
    private val patients: PatientRepository,
) : ViewModel() {

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

    /** Cambia la fecha programada (desde el calendario). El flujo refresca solo. */
    fun reschedule(procedureId: String, date: IsoDate) {
        viewModelScope.launch {
            try {
                board.reschedule(procedureId, date)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Error inesperado")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /** Buscador flotante. */
    fun setQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    /** Píldoras de estado: tocar filtra, tocar de nuevo limpia. */
    fun toggleColumnFilter(column: BoardColumn) {
        val current = _uiState.value
        current.copy(columnFilter = if (current.columnFilter == column) null else column)
            .let { _uiState.value = it }
    }

    /** FAB +: abre el diálogo de agendar y carga los pacientes. */
    fun openAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = true)
        viewModelScope.launch {
            try {
                val list = patients.getActivePatients(DEFAULT_SERVICE_ID)
                _uiState.value = _uiState.value.copy(dialogPatients = list)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Error inesperado")
            }
        }
    }

    fun closeAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = false)
    }

    /** Confirma el diálogo: crea el procedimiento y cierra. */
    fun confirmAdd(
        patientId: String?,
        kind: String,
        scheduledDate: IsoDate?,
        scheduledTime: String?,
        priority: ClinicalPriority,
        note: String?,
    ) {
        viewModelScope.launch {
            try {
                val pid = patientId ?: error("Seleccione el paciente")
                board.scheduleProcedure(
                    patientId = pid,
                    kind = kind,
                    scheduledDate = scheduledDate,
                    scheduledTime = scheduledTime,
                    priority = priority,
                    note = note,
                )
                _uiState.value = _uiState.value.copy(showAddDialog = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Error inesperado")
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BoardViewModel(container.board, container.patientRepository) as T
    }
}
