package com.reynelbusto.paserevista.presentation.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reynelbusto.paserevista.core.Clock
import com.reynelbusto.paserevista.core.IsoDate
import com.reynelbusto.paserevista.di.AppContainer
import com.reynelbusto.paserevista.domain.model.CustomField
import com.reynelbusto.paserevista.domain.model.Journey
import com.reynelbusto.paserevista.domain.model.Sex
import com.reynelbusto.paserevista.domain.model.displayName
import com.reynelbusto.paserevista.domain.usecase.CaseCardPatch
import com.reynelbusto.paserevista.domain.usecase.DEFAULT_SERVICE_ID
import com.reynelbusto.paserevista.domain.usecase.DischargeUseCase
import com.reynelbusto.paserevista.domain.usecase.ShareFormatter
import com.reynelbusto.paserevista.presentation.camas.CardActions
import com.reynelbusto.paserevista.presentation.camas.CardRow
import com.reynelbusto.paserevista.presentation.camas.DischargeConfirm
import com.reynelbusto.paserevista.presentation.camas.loadCardRows
import com.reynelbusto.paserevista.presentation.components.PendingFormData
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
    /** Jornada de hoy: se edita siempre, sin pulsar "Corregir". */
    val isToday: Boolean,
)

data class AgendaUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    /** Error de una mutación (Snackbar): se limpia al mostrarlo. */
    val actionError: String? = null,
    val sections: List<AgendaDaySection> = emptyList(),
    val canLoadMore: Boolean = false,
    /** Sugerencias de diagnóstico al editar la tarjeta. */
    val recentDiagnoses: List<String> = emptyList(),
    /** Días pasados habilitados para edición ("Corregir día"). */
    val editingJourneyIds: Set<String> = emptySet(),
    /** Alta pendiente de confirmación explícita por P1 abiertos. */
    val dischargeConfirm: DischargeConfirm? = null,
)

/**
 * Agenda: timeline vertical de los días, lo más nuevo arriba, como hojear
 * una libreta. Permitir trabajar desde aquí: hoy es editable por defecto y
 * cualquier día pasado se habilita con "Corregir día". Comparte con Camas
 * el cargador de filas y las acciones clínicas (CardActions).
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

    private val actions = CardActions(container)

    private var collectJob: Job? = null
    private var visibleCount = PAGE_SIZE
    private var journeys: List<Journey> = emptyList()

    /** Caché por jornada: los días sin edición son inmutables en la práctica. */
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
                    isToday = journey.clinicalDate == today,
                )
            }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                sections = sections,
                canLoadMore = journeys.size > visibleCount,
                recentDiagnoses = container.caseCardRepository.recentDiagnoses(8),
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = e.message ?: "Error al cargar",
            )
        }
    }

    // ── Edición ────────────────────────────────────────────────────────────

    /** Habilita/deshabilita la edición de un día pasado. */
    fun toggleEditing(journeyId: String) {
        val current = _uiState.value.editingJourneyIds
        val next = if (journeyId in current) current - journeyId else current + journeyId
        _uiState.value = _uiState.value.copy(editingJourneyIds = next)
    }

    /** Ejecuta una mutación, invalida la caché y reconstruye el timeline. */
    private fun runAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    actionError = e.message ?: "Error inesperado",
                )
            }
            rowCache.clear()
            rebuild()
        }
    }

    fun addBed(bed: String) = runAction { container.addBed(bed) }

    fun toggleReady(cardId: String) = runAction { actions.toggleReady(cardId) }

    fun saveFields(cardId: String, patch: CaseCardPatch) =
        runAction { actions.saveFields(cardId, patch) }

    fun savePatientDetails(
        patientId: String,
        fullName: String?,
        hcNumber: String?,
        bloodGroup: String?,
        allergies: String?,
        address: String?,
        mainDiagnosis: String?,
        isOutOfService: Boolean?,
        sex: Sex?,
    ) = runAction {
        actions.savePatientDetails(
            patientId, fullName, hcNumber, bloodGroup, allergies, address, mainDiagnosis, isOutOfService, sex,
        )
    }

    fun addCustomField(patientId: String, label: String, value: String) =
        runAction { actions.addCustomField(patientId, label, value) }

    fun renameCustomField(field: CustomField, newLabel: String) =
        runAction { actions.renameCustomField(field, newLabel) }

    fun setCustomFieldValue(field: CustomField, value: String) =
        runAction { actions.setCustomFieldValue(field, value) }

    fun deleteCustomField(field: CustomField) =
        runAction { actions.deleteCustomField(field) }

    fun addPending(patientId: String, form: PendingFormData) =
        runAction { actions.addPending(patientId, form) }

    fun completePending(pendingId: String) = runAction { actions.completePending(pendingId) }

    fun deleteCase(patientId: String) = runAction { actions.deleteCase(patientId) }

    fun dischargeBed(patientId: String) {
        viewModelScope.launch {
            try {
                when (val check = actions.checkDischarge(patientId)) {
                    is DischargeUseCase.Check.Ok -> runAction { actions.discharge(patientId) }
                    is DischargeUseCase.Check.BlockedByP1 -> {
                        _uiState.value = _uiState.value.copy(
                            dischargeConfirm = DischargeConfirm(
                                patientId = patientId,
                                patientLabel = labelOf(patientId),
                                blockingPendings = check.pendings,
                            ),
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    actionError = e.message ?: "Error inesperado",
                )
            }
        }
    }

    fun confirmDischarge() {
        val confirm = _uiState.value.dischargeConfirm ?: return
        _uiState.value = _uiState.value.copy(dischargeConfirm = null)
        runAction { actions.discharge(confirm.patientId, force = true) }
    }

    fun dismissDischargeConfirm() {
        _uiState.value = _uiState.value.copy(dischargeConfirm = null)
    }

    fun clearActionError() {
        _uiState.value = _uiState.value.copy(actionError = null)
    }

    // ── Compartir / presentación ────────────────────────────────────────────

    fun shareDayText(section: AgendaDaySection): String =
        ShareFormatter.cardList(section.rows.map { it.shareData() }, prettyDayEs(section.date))

    fun displayName(row: CardRow): String = row.patient.displayName(row.card.bed)

    private fun labelOf(patientId: String): String {
        val row = _uiState.value.sections
            .asSequence()
            .flatMap { it.rows.asSequence() }
            .firstOrNull { it.patient.id == patientId }
        return row?.let { displayName(it) } ?: "Cama ?"
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AgendaViewModel(container) as T
    }
}
