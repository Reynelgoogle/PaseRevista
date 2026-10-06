package com.reynelbusto.paserevista.presentation.camas

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reynelbusto.paserevista.core.Clock
import com.reynelbusto.paserevista.di.AppContainer
import com.reynelbusto.paserevista.domain.model.CaseCard
import com.reynelbusto.paserevista.domain.model.CaseHistoryEntry
import com.reynelbusto.paserevista.domain.model.CustomField
import com.reynelbusto.paserevista.domain.model.Pending
import com.reynelbusto.paserevista.domain.model.PendingType
import com.reynelbusto.paserevista.domain.model.Patient
import com.reynelbusto.paserevista.domain.model.PatientState
import com.reynelbusto.paserevista.domain.model.displayName
import com.reynelbusto.paserevista.domain.usecase.CardShareData
import com.reynelbusto.paserevista.domain.usecase.CaseCardPatch
import com.reynelbusto.paserevista.domain.usecase.DEFAULT_SERVICE_ID
import com.reynelbusto.paserevista.domain.usecase.ShareFormatter
import com.reynelbusto.paserevista.presentation.components.PendingFormData
import com.reynelbusto.paserevista.widget.CaseWidgetProvider
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Fila de la lista: tarjeta + datos para mostrar y compartir. */
data class CardRow(
    val card: CaseCard,
    val patient: Patient,
    val openPendings: List<Pending>,
    val customFields: List<CustomField>,
    val history: List<CaseHistoryEntry>,
) {
    val hasInterconsult: Boolean
        get() = openPendings.any { it.type == PendingType.INTERCONSULTATION }

    fun shareData(): CardShareData = CardShareData(
        bed = card.bed,
        ready = card.ready,
        diagnosis = card.diagnosis,
        scheduledProcedure = card.scheduledProcedure,
        openPendings = openPendings.map { it.description },
        bloodGroup = patient.bloodGroup,
        currentState = card.currentState,
        antibiotic = card.antibiotic,
        patientName = patient.fullName,
        hcNumber = patient.hcNumber,
        address = patient.address,
        customFields = customFields.map { it.label to it.value },
        outOfService = patient.isOutOfService,
        hasInterconsult = hasInterconsult,
    )
}

/** Predicado del listado de Camas: solo pacientes activos (el alta saca la tarjeta). */
internal fun isListedPatient(patient: Patient): Boolean =
    patient.status == PatientState.ACTIVE

data class CamasUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val rows: List<CardRow> = emptyList(),
    val dateLabel: String = "",
)

/**
 * Pantalla Camas: el listado de tarjetas es el corazón de la app.
 * El motor de jornadas es invisible: al abrir se asegura el día y las
 * tarjetas se heredan (nada se reescribe).
 */
class CamasViewModel(
    private val container: AppContainer,
    private val appContext: Context,
    private val clock: Clock = container.clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CamasUiState())
    val uiState: StateFlow<CamasUiState> = _uiState

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val journeyId = container.ensureDay(DEFAULT_SERVICE_ID)
                val cards = container.caseCardRepository.observeByJourney(journeyId).first()
                val rows = cards.map { card ->
                    val patient = container.patientRepository.getPatient(card.patientId)
                        ?: return@map null
                    // BUG 2: el alta saca la tarjeta del listado (el historial se conserva).
                    if (!isListedPatient(patient)) return@map null
                    CardRow(
                        card = card,
                        patient = patient,
                        openPendings = container.pendingRepository
                            .getOpenByPatient(card.patientId),
                        customFields = container.customFieldRepository
                            .observeByPatient(card.patientId).first(),
                        history = container.caseHistoryRepository
                            .observeByPatient(card.patientId).first(),
                    )
                }.filterNotNull().sortedWith(
                    compareBy({ it.card.bed.toIntOrNull() }, { it.card.bed }),
                )
                _uiState.value = CamasUiState(
                    isLoading = false,
                    rows = rows,
                    dateLabel = prettyDate(clock.todayIso()),
                )
                CaseWidgetProvider.requestUpdate(appContext)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Error al cargar",
                )
            }
        }
    }

    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
            refresh()
        }
    }

    fun addBed(bed: String) = mutate { container.addBed(bed) }

    fun toggleReady(cardId: String) = mutate { container.caseCards.toggleReady(cardId) }

    fun saveFields(cardId: String, patch: CaseCardPatch) =
        mutate { container.caseCards.updateFields(cardId, patch) }

    fun savePatientDetails(
        patientId: String,
        fullName: String?,
        hcNumber: String?,
        bloodGroup: String?,
        address: String?,
        mainDiagnosis: String?,
        isOutOfService: Boolean?,
    ) = mutate {
        container.caseCards.updatePatientDetails(
            patientId, fullName, hcNumber, bloodGroup, address, mainDiagnosis, isOutOfService,
        )
    }

    fun addCustomField(patientId: String, label: String, value: String) =
        mutate { container.customFields.add(patientId, label, value) }

    fun renameCustomField(field: CustomField, newLabel: String) =
        mutate { container.customFields.rename(field.id, field.patientId, newLabel, field) }

    fun setCustomFieldValue(field: CustomField, value: String) =
        mutate { container.customFields.setValue(field.id, value, field) }

    fun deleteCustomField(field: CustomField) =
        mutate { container.customFields.delete(field.id, field.patientId, field.label) }

    fun addPending(patientId: String, form: PendingFormData) = mutate {
        container.pendings.create(
            patientId = patientId,
            description = form.description,
            type = form.type,
            priority = form.priority,
            dueDate = form.dueDate?.ifBlank { null },
            assignee = form.assignee?.ifBlank { null },
            serviceDest = form.serviceDest?.ifBlank { null },
            interconsultNote = form.note?.ifBlank { null },
        )
    }

    fun completePending(pendingId: String) = mutate {
        container.pendings.complete(pendingId, journeyResolutionId = null)
    }

    fun dischargeBed(patientId: String) = mutate {
        container.discharge.discharge(patientId, force = true)
    }

    /** Borrado total del caso ("se cargó por error"). */
    fun deleteCase(patientId: String) = mutate {
        container.deleteCase.invoke(patientId)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun shareCardText(row: CardRow, complete: Boolean): String =
        if (complete) ShareFormatter.cardComplete(row.shareData())
        else ShareFormatter.cardSimple(row.shareData())

    fun shareListText(): String =
        ShareFormatter.cardList(_uiState.value.rows.map { it.shareData() }, _uiState.value.dateLabel)

    fun displayName(row: CardRow): String = row.patient.displayName(row.card.bed)

    private fun prettyDate(iso: String): String = try {
        val date = LocalDate.parse(iso)
        DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es"))
            .format(date).replaceFirstChar { it.uppercase() }
    } catch (e: Exception) {
        iso
    }

    class Factory(
        private val container: AppContainer,
        private val appContext: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CamasViewModel(container, appContext.applicationContext) as T
    }
}
