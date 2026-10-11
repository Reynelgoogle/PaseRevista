package com.reynelbusto.paserevista.presentation.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reynelbusto.paserevista.core.Clock
import com.reynelbusto.paserevista.core.IsoDate
import com.reynelbusto.paserevista.di.AppContainer
import com.reynelbusto.paserevista.domain.model.Journey
import com.reynelbusto.paserevista.domain.usecase.DEFAULT_SERVICE_ID
import com.reynelbusto.paserevista.presentation.camas.CardRow
import com.reynelbusto.paserevista.presentation.camas.loadCardRows
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** Una "página" de la agenda: un día con sus camas. */
data class AgendaDaySection(
    val journeyId: String,
    val date: IsoDate,
    val title: String,
    val rows: List<CardRow>,
    /** cardId → marca de continuidad. */
    val marks: Map<String, BedMark>,
)

data class AgendaUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val sections: List<AgendaDaySection> = emptyList(),
    val canLoadMore: Boolean = false,
)

/**
 * Agenda: timeline vertical de los días, lo más nuevo arriba, como hojear
 * una libreta. Solo lectura: los días pasados se miran, no se editan
 * (para trabajar está Camas).
 *
 * Datos: `observeJourneys` (recientes primero) + filas por jornada con el
 * cargador compartido de Camas. Los días sin camas se omiten.
 */
class AgendaViewModel(
    private val container: AppContainer,
    private val clock: Clock = container.clock,
) : ViewModel() {

    companion object {
        const val PAGE_SIZE = 30
    }

    private val _uiState = MutableStateFlow(AgendaUiState())
    val uiState: StateFlow<AgendaUiState> = _uiState

    private var collectJob: Job? = null
    private var visibleCount = PAGE_SIZE
    private var journeys: List<Journey> = emptyList()
    /** Caché por jornada: los días pasados son inmutables en la práctica. */
    private val rowCache = mutableMapOf<String, List<CardRow>>()

    init {
        subscribe()
    }

    fun retry() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        subscribe()
    }

    private fun subscribe() {
        collectJob?.cancel()
        collectJob = viewModelScope.launch {
            // El motor invisible asegura el día (idempotente): si se abre la
            // agenda antes que Camas, hoy existe igualmente.
            val todayId = try {
                container.ensureDay(DEFAULT_SERVICE_ID)
            } catch (e: Exception) {
                null
            }
            // Las tarjetas de HOY se observan en vivo: si se editan en Camas,
            // la agenda se actualiza sin recarga manual.
            val todayCards = todayId?.let {
                container.caseCardRepository.observeByJourney(it)
            } ?: flowOf(emptyList())
            combine(
                container.journeyRepository.observeJourneys(DEFAULT_SERVICE_ID),
                todayCards,
            ) { journeyList, _ -> journeyList }
                .catch {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "No se pudo cargar la agenda.",
                    )
                }
                .collect { list ->
                    val ids = list.map { it.id }
                    if (ids != journeys.map { it.id }) {
                        journeys = list
                        rebuild()
                    } else if (todayId != null) {
                        // Cambiaron tarjetas de hoy sin cambiar jornadas:
                        // se invalida el caché de hoy y se reconstruye.
                        rowCache.remove(todayId)
                        rebuild()
                    }
                }
        }
    }

    /** Carga la siguiente página de días (al llegar al final del timeline). */
    fun loadMore() {
        if (journeys.size <= visibleCount) return
        visibleCount += PAGE_SIZE
        viewModelScope.launch { rebuild() }
    }

    private suspend fun rebuild() {
        val firstLoad = _uiState.value.sections.isEmpty()
        _uiState.value = _uiState.value.copy(
            isLoading = firstLoad,
            error = null,
        )
        try {
            val today = clock.todayIso()
            val visible = journeys.take(visibleCount)
            val withRows = visible.mapNotNull { journey ->
                val rows = rowCache.getOrPut(journey.id) {
                    container.loadCardRows(journey.id, onlyActive = false)
                }
                // Días sin camas: se omiten, sin ruido.
                if (rows.isEmpty()) null else journey to rows
            }
            val marksByDate = computeDayMarks(
                withRows.map { (journey, rows) ->
                    AgendaDayInput(journey.clinicalDate, rows.map { it.card.bed }.toSet())
                },
            )
            val sections = withRows.map { (journey, rows) ->
                val bedMarks = marksByDate[journey.clinicalDate].orEmpty()
                AgendaDaySection(
                    journeyId = journey.id,
                    date = journey.clinicalDate,
                    title = agendaDayTitle(journey.clinicalDate, today),
                    rows = rows,
                    marks = rows.associate { it.card.id to (bedMarks[it.card.bed] ?: BedMark.NEW) },
                )
            }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                sections = sections,
                canLoadMore = journeys.size > visibleCount,
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = e.message ?: "Error al cargar",
            )
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AgendaViewModel(container) as T
    }
}
