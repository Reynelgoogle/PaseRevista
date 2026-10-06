package com.reynelbusto.paserevista.presentation.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reynelbusto.paserevista.core.Clock
import com.reynelbusto.paserevista.core.IsoDate
import com.reynelbusto.paserevista.di.AppContainer
import com.reynelbusto.paserevista.domain.model.ClinicalPriority
import com.reynelbusto.paserevista.domain.model.Patient
import com.reynelbusto.paserevista.domain.model.Pending
import com.reynelbusto.paserevista.domain.model.PendingState
import com.reynelbusto.paserevista.domain.model.PendingType
import com.reynelbusto.paserevista.domain.repository.JourneyRepository
import com.reynelbusto.paserevista.domain.repository.PatientRepository
import com.reynelbusto.paserevista.domain.repository.PendingRepository
import com.reynelbusto.paserevista.domain.usecase.PendingUseCase
import com.reynelbusto.paserevista.presentation.components.PendingFormData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PendingStatusFilter { ALL, OPEN, IN_PROGRESS, CLOSED }
enum class PendingPriorityFilter { ALL, P1, P2, P3 }
enum class PendingTypeFilter { ALL, GENERAL, STUDY, PROCEDURE, INTERCONSULTATION }
enum class PendingDueFilter { ALL, TODAY, OVERDUE }

data class PendingsUiState(
    val isLoading: Boolean = true,
    val pendings: List<PendingWithPatient> = emptyList(),
    val statusFilter: PendingStatusFilter = PendingStatusFilter.OPEN,
    val priorityFilter: PendingPriorityFilter = PendingPriorityFilter.ALL,
    val typeFilter: PendingTypeFilter = PendingTypeFilter.ALL,
    val dueFilter: PendingDueFilter = PendingDueFilter.ALL,
    val patients: List<Patient> = emptyList(),
    val error: String? = null,
)

data class PendingWithPatient(val pending: Pending, val patientName: String, val bed: String?)

@OptIn(ExperimentalCoroutinesApi::class)
class PendingsViewModel(
    private val serviceId: String,
    private val pendings: PendingRepository,
    private val patients: PatientRepository,
    private val journeys: JourneyRepository,
    private val pendingUseCase: PendingUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val statusFilter = MutableStateFlow(PendingStatusFilter.OPEN)
    private val priorityFilter = MutableStateFlow(PendingPriorityFilter.ALL)
    private val typeFilter = MutableStateFlow(PendingTypeFilter.ALL)
    private val dueFilter = MutableStateFlow(PendingDueFilter.ALL)

    val uiState: StateFlow<PendingsUiState> = combine(
        statusFilter, priorityFilter, typeFilter, dueFilter,
    ) { s, p, t, d -> FilterSet(s, p, t, d) }
        .flatMapLatest { filters ->
            combine(
                pendings.observeOpenByService(serviceId),
                pendings.observeRecentlyClosedByService(serviceId),
                patients.observeActivePatients(serviceId),
            ) { open, closed, plist ->
                buildState(open, closed, plist, filters)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PendingsUiState())

    private data class FilterSet(
        val status: PendingStatusFilter,
        val priority: PendingPriorityFilter,
        val type: PendingTypeFilter,
        val due: PendingDueFilter,
    )

    private fun buildState(
        open: List<Pending>,
        closed: List<Pending>,
        plist: List<Patient>,
        f: FilterSet,
    ): PendingsUiState {
        val today: IsoDate = clock.todayIso()
        val pool = when (f.status) {
            PendingStatusFilter.ALL -> open + closed
            PendingStatusFilter.OPEN -> open.filter { it.status == PendingState.PENDING }
            PendingStatusFilter.IN_PROGRESS -> open.filter { it.status == PendingState.IN_PROGRESS }
            PendingStatusFilter.CLOSED -> closed
        }
        val byPatient = plist.associateBy { it.id }
        val filtered = pool.filter { p ->
            (f.priority == PendingPriorityFilter.ALL || p.priority.name == f.priority.name) &&
                (f.type == PendingTypeFilter.ALL || p.type.name == f.type.name) &&
                when (f.due) {
                    PendingDueFilter.ALL -> true
                    // Vencido = badge informativo; jamás cambia la prioridad automáticamente.
                    PendingDueFilter.OVERDUE -> p.dueDate != null && p.dueDate < today &&
                        (p.status == PendingState.PENDING || p.status == PendingState.IN_PROGRESS)
                    PendingDueFilter.TODAY -> p.dueDate == today
                }
        }.sortedWith(
            compareBy<Pending>(
                { it.priority != ClinicalPriority.P1 },
                { it.dueDate == null },
                { it.dueDate },
            ),
        )
        return PendingsUiState(
            isLoading = false,
            pendings = filtered.map {
                PendingWithPatient(it, byPatient[it.patientId]?.fullName ?: "—", null)
            },
            statusFilter = f.status,
            priorityFilter = f.priority,
            typeFilter = f.type,
            dueFilter = f.due,
            patients = plist,
        )
    }

    fun setStatusFilter(f: PendingStatusFilter) { statusFilter.value = f }
    fun setPriorityFilter(f: PendingPriorityFilter) { priorityFilter.value = f }
    fun setTypeFilter(f: PendingTypeFilter) { typeFilter.value = f }
    fun setDueFilter(f: PendingDueFilter) { dueFilter.value = f }

    fun createPending(form: PendingFormData) {
        viewModelScope.launch {
            val patientId = form.patientId ?: return@launch
            pendingUseCase.create(
                patientId = patientId,
                description = form.description,
                type = form.type,
                priority = form.priority,
                dueDate = form.dueDate,
                assignee = form.assignee,
                serviceDest = form.serviceDest,
                interconsultNote = form.note,
            )
        }
    }

    fun completePending(id: String) {
        viewModelScope.launch {
            val journey = journeys.getJourney(serviceId, clock.todayIso())
            pendingUseCase.complete(id, journey?.id)
        }
    }

    fun cancelPending(id: String, reason: String) {
        viewModelScope.launch { pendingUseCase.cancel(id, reason) }
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PendingsViewModel(
                serviceId = "urology",
                pendings = container.pendingRepository,
                patients = container.patientRepository,
                journeys = container.journeyRepository,
                pendingUseCase = container.pendings,
                clock = container.clock,
            ) as T
    }
}
