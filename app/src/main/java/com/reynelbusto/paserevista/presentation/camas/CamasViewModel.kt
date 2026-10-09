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
import com.reynelbusto.paserevista.domain.model.Sex
import com.reynelbusto.paserevista.domain.model.displayName
import com.reynelbusto.paserevista.domain.usecase.CardShareData
import com.reynelbusto.paserevista.domain.usecase.CaseCardPatch
import com.reynelbusto.paserevista.domain.usecase.DEFAULT_SERVICE_ID
import com.reynelbusto.paserevista.domain.usecase.DischargeUseCase
import com.reynelbusto.paserevista.domain.usecase.ShareFormatter
import com.reynelbusto.paserevista.presentation.components.PendingFormData
import com.reynelbusto.paserevista.widget.CaseWidgetProvider
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    /** B2: la tarjeta muestra nombre propio (no el "Cama N" genérico). */
    val hasCustomName: Boolean
        get() = patient.fullName?.isNotBlank() == true

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

/** Diálogo de confirmación de alta cuando hay P1 abiertos (caso D). */
data class DischargeConfirm(
    val patientId: String,
    val patientLabel: String,
    val blockingPendings: List<Pending>,
)

data class CamasUiState(
    val isLoading: Boolean = true,
    /** M6: refresco en segundo plano (mutaciones) — indicador sutil, sin tapar la lista. */
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val rows: List<CardRow> = emptyList(),
    val dateLabel: String = "",
    /** Últimos diagnósticos usados (sugerencias al editar la tarjeta). */
    val recentDiagnoses: List<String> = emptyList(),
    /** Alta pendiente de confirmación explícita por P1 abiertos. */
    val dischargeConfirm: DischargeConfirm? = null,
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
            // M5: no borrar el error de una mutación fallida que aún no se
            // mostró (el Snackbar lo limpia con clearError() al mostrarlo).
            val pendingError = _uiState.value.error
            val pendingConfirm = _uiState.value.dischargeConfirm
            // M6: spinner a pantalla completa solo en la carga inicial; en
            // refrescos (mutaciones) la lista sigue visible con indicador sutil.
            val initial = _uiState.value.rows.isEmpty()
            _uiState.value = _uiState.value.copy(
                isLoading = initial,
                isRefreshing = !initial,
                error = null,
            )
            try {
                val journeyId = container.ensureDay(DEFAULT_SERVICE_ID)
                val rows = container.loadCardRows(journeyId)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    rows = rows,
                    dateLabel = prettyDate(clock.todayIso()),
                    recentDiagnoses = container.caseCardRepository.recentDiagnoses(8),
                    error = pendingError,
                    dischargeConfirm = pendingConfirm,
                )
                CaseWidgetProvider.requestUpdate(appContext)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
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
                _uiState.value = _uiState.value.copy(error = e.message ?: "Error inesperado")
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
        sex: Sex?,
    ) = mutate {
        container.caseCards.updatePatientDetails(
            patientId, fullName, hcNumber, bloodGroup, address, mainDiagnosis, isOutOfService, sex,
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

    /**
     * Alta de la cama. Primero verifica P1 abiertos: si los hay, NO usa
     * force=true a ciegas; muestra el diálogo con la lista para confirmación
     * explícita (A3).
     */
    fun dischargeBed(patientId: String) {
        viewModelScope.launch {
            try {
                when (val check = container.discharge.check(patientId)) {
                    is DischargeUseCase.Check.Ok -> mutate {
                        container.discharge.discharge(patientId)
                    }
                    is DischargeUseCase.Check.BlockedByP1 -> {
                        val row = _uiState.value.rows.firstOrNull { it.patient.id == patientId }
                        _uiState.value = _uiState.value.copy(
                            dischargeConfirm = DischargeConfirm(
                                patientId = patientId,
                                patientLabel = row?.let { displayName(it) }
                                    ?: "Cama ${row?.card?.bed ?: "?"}",
                                blockingPendings = check.pendings,
                            ),
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Error inesperado")
            }
        }
    }

    /** Confirmación explícita del diálogo: solo aquí se usa force=true. */
    fun confirmDischarge() {
        val confirm = _uiState.value.dischargeConfirm ?: return
        _uiState.value = _uiState.value.copy(dischargeConfirm = null)
        mutate { container.discharge.discharge(confirm.patientId, force = true) }
    }

    fun dismissDischargeConfirm() {
        _uiState.value = _uiState.value.copy(dischargeConfirm = null)
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
