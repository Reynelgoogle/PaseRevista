package com.reynelbusto.paserevista.presentation.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reynelbusto.paserevista.core.Clock
import com.reynelbusto.paserevista.di.AppContainer
import com.reynelbusto.paserevista.domain.model.Journey
import com.reynelbusto.paserevista.domain.model.Patient
import com.reynelbusto.paserevista.domain.model.Procedure
import com.reynelbusto.paserevista.domain.model.ProcedureState
import com.reynelbusto.paserevista.domain.repository.JourneyRepository
import com.reynelbusto.paserevista.domain.repository.PatientRepository
import com.reynelbusto.paserevista.domain.repository.ProcedureRepository
import com.reynelbusto.paserevista.domain.usecase.ProcedureUseCase
import com.reynelbusto.paserevista.presentation.components.ProcedureFormData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProcedureWithPatient(
    val procedure: Procedure,
    val patientName: String,
    val bed: String?,
)

data class BoardUiState(
    val isLoading: Boolean = true,
    val journey: Journey? = null,
    val pending: List<ProcedureWithPatient> = emptyList(),
    val preparation: List<ProcedureWithPatient> = emptyList(),
    val performed: List<ProcedureWithPatient> = emptyList(),
    val patients: List<Patient> = emptyList(),
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class BoardViewModel(
    private val serviceId: String,
    private val journeys: JourneyRepository,
    private val procedures: ProcedureRepository,
    private val patients: PatientRepository,
    private val procedureUseCase: ProcedureUseCase,
    private val clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<BoardUiState> =
        journeys.observeCurrentJourney(serviceId, clock.todayIso())
            .flatMapLatest { journey ->
                if (journey == null) return@flatMapLatest flowOf(BoardUiState(isLoading = false))
                combine(
                    procedures.observeByJourney(journey.id),
                    patients.observeActivePatients(serviceId),
                ) { procs, plist ->
                    buildState(journey, procs, plist)
                }
            }
            .catch { emit(BoardUiState(isLoading = false, error = "No se pudo cargar la pizarra.")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BoardUiState())

    private fun buildState(
        journey: Journey,
        procs: List<Procedure>,
        plist: List<Patient>,
    ): BoardUiState {
        // Pizarra = procedimientos de hoy + programados sin jornada asignada.
        val byPatient = plist.associateBy { it.id }
        fun wrap(p: Procedure) = ProcedureWithPatient(p, byPatient[p.patientId]?.fullName ?: "—", null)
        val order = compareBy<ProcedureWithPatient>(
            { it.procedure.priority != com.reynelbusto.paserevista.domain.model.ClinicalPriority.P1 },
            { it.procedure.scheduledTime == null },
            { it.procedure.scheduledTime },
        )
        return BoardUiState(
            isLoading = false,
            journey = journey,
            pending = procs.filter { it.status == ProcedureState.PENDING }.map(::wrap).sortedWith(order),
            preparation = procs.filter { it.status == ProcedureState.PREPARATION }.map(::wrap).sortedWith(order),
            performed = procs.filter { it.status == ProcedureState.PERFORMED }.map(::wrap).sortedWith(order),
            patients = plist,
        )
    }

    fun schedule(form: ProcedureFormData) {
        viewModelScope.launch {
            val patientId = form.patientId ?: return@launch
            procedureUseCase.schedule(
                patientId = patientId,
                kind = form.kind,
                scheduledDate = form.scheduledDate,
                scheduledTime = form.scheduledTime,
                priority = form.priority,
                journeyId = uiState.value.journey?.id,
                note = form.note,
            )
        }
    }

    fun toPreparation(id: String) {
        viewModelScope.launch { procedureUseCase.toPreparation(id) }
    }

    fun perform(id: String) {
        viewModelScope.launch {
            procedureUseCase.perform(id, uiState.value.journey?.id)
        }
    }

    fun cancel(id: String) {
        viewModelScope.launch { procedureUseCase.cancel(id) }
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BoardViewModel(
                serviceId = "urology",
                journeys = container.journeyRepository,
                procedures = container.procedureRepository,
                patients = container.patientRepository,
                procedureUseCase = container.procedures,
                clock = container.clock,
            ) as T
    }
}
