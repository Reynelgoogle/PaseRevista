package com.reynelbusto.paserevista.presentation.patients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reynelbusto.paserevista.core.Clock
import com.reynelbusto.paserevista.core.ageAt
import com.reynelbusto.paserevista.di.AppContainer
import com.reynelbusto.paserevista.domain.model.Patient
import com.reynelbusto.paserevista.domain.model.PatientState
import com.reynelbusto.paserevista.domain.model.Sex
import com.reynelbusto.paserevista.domain.repository.PatientRepository
import com.reynelbusto.paserevista.domain.usecase.AdmitPatientUseCase
import com.reynelbusto.paserevista.domain.usecase.NewPatientInput
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PatientListRow(
    val patient: Patient,
    val age: Int,
)

data class PatientsUiState(
    val isLoading: Boolean = true,
    val rows: List<PatientListRow> = emptyList(),
    val query: String = "",
    val showDischarged: Boolean = false,
    val error: String? = null,
)

data class NewAdmissionForm(
    val fullName: String,
    val birthDate: String,
    val sex: Sex,
    val hcNumber: String,
    val bloodGroup: String?,
    val admissionReason: String?,
    val mainDiagnosis: String,
    val bed: String?,
)

/**
 * FASE 9 — Pacientes: búsqueda global offline (nombre/HC/cama/diagnóstico),
 * toggle activos/dados de alta, nuevo ingreso.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class PatientsViewModel(
    private val serviceId: String,
    private val patients: PatientRepository,
    private val admitPatient: AdmitPatientUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val showDischarged = MutableStateFlow(false)

    val uiState: StateFlow<PatientsUiState> = combine(query, showDischarged) { q, d -> q to d }
        .debounce(250)
        .flatMapLatest { (q, d) ->
            combine(
                // Búsqueda: nombre/HC/diagnóstico vía DAO; cama se filtra en memoria
                // porque vive en el DailyRecord (no en Patient).
                patients.searchPatients(serviceId, q),
                patients.observeActivePatients(serviceId),
            ) { searched, active ->
                val today = clock.todayIso()
                // Base: si hay query, la búsqueda; si no, activos.
                // El toggle "dados de alta" muestra no-activos de la búsqueda.
                val base = if (q.isBlank()) {
                    if (d) searched else active
                } else {
                    searched.filter { p ->
                        if (d) p.status != PatientState.ACTIVE else p.status == PatientState.ACTIVE
                    }
                }
                PatientsUiState(
                    isLoading = false,
                    rows = base.sortedBy { it.fullName }
                        .map { PatientListRow(it, ageAt(it.birthDate, today)) },
                    query = q,
                    showDischarged = d,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PatientsUiState())

    fun onQueryChange(q: String) { query.value = q }
    fun onToggleDischarged() { showDischarged.value = !showDischarged.value }

    fun admit(form: NewAdmissionForm, onDone: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val id = admitPatient(
                    NewPatientInput(
                        fullName = form.fullName,
                        birthDate = form.birthDate,
                        sex = form.sex,
                        hcNumber = form.hcNumber,
                        bloodGroup = form.bloodGroup,
                        serviceId = serviceId,
                        admissionReason = form.admissionReason,
                        mainDiagnosis = form.mainDiagnosis,
                    ),
                    bed = form.bed,
                )
                onDone(id)
            } catch (e: Exception) {
                onError(e.message ?: "No se pudo registrar el ingreso")
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PatientsViewModel(
                serviceId = "urology",
                patients = container.patientRepository,
                admitPatient = container.admitPatient,
                clock = container.clock,
            ) as T
    }
}
