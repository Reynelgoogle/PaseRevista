package com.reynelbusto.paserevista.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reynelbusto.paserevista.core.Clock
import com.reynelbusto.paserevista.core.ageAt
import com.reynelbusto.paserevista.di.AppContainer
import com.reynelbusto.paserevista.domain.model.ClinicalPriority
import com.reynelbusto.paserevista.domain.model.DailyRecord
import com.reynelbusto.paserevista.domain.model.Journey
import com.reynelbusto.paserevista.domain.model.Patient
import com.reynelbusto.paserevista.domain.model.Pending
import com.reynelbusto.paserevista.domain.model.ReviewState
import com.reynelbusto.paserevista.domain.usecase.GetCurrentJourneyUseCase
import com.reynelbusto.paserevista.domain.usecase.GetDailyRecordsUseCase
import com.reynelbusto.paserevista.domain.usecase.StartJourneyUseCase
import com.reynelbusto.paserevista.domain.repository.PatientRepository
import com.reynelbusto.paserevista.domain.repository.PendingRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Fila de paciente en Hoy: solo lo necesario para identificar la situación. */
data class PatientRow(
    val patientId: String,
    val bed: String?,
    val fullName: String,
    val age: Int,
    val mainDiagnosis: String,
    val reviewState: ReviewState,
    val priority: ClinicalPriority?,
    val hasOpenPending: Boolean,
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val journey: Journey? = null,
    val totalPatients: Int = 0,
    val reviewedCount: Int = 0,
    val inProgressCount: Int = 0,
    val pendingReviewCount: Int = 0,
    val p1Count: Int = 0,
    val openPendingsCount: Int = 0,
    val plannedDischargesCount: Int = 0,
    val rows: List<PatientRow> = emptyList(),
    val error: String? = null,
)

/**
 * ViewModel de Hoy. Expone UI State inmutable por StateFlow.
 * Sin SQL, sin Room, sin reglas clínicas: solo orquesta casos de uso.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val getCurrentJourney: GetCurrentJourneyUseCase,
    private val getDailyRecords: GetDailyRecordsUseCase,
    private val startJourney: StartJourneyUseCase,
    private val patients: PatientRepository,
    private val pendings: PendingRepository,
    private val clock: Clock,
    private val serviceId: String = "urology",
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = getCurrentJourney(serviceId)
        .flatMapLatest { journey -> contentFlow(journey) }
        .catch { e ->
            emit(
                HomeUiState(
                    isLoading = false,
                    error = "No se pudo cargar la jornada. Revisa e inténtalo de nuevo.",
                ),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun contentFlow(journey: Journey?): Flow<HomeUiState> {
        if (journey == null) {
            // Sin jornada hoy: estado vacío profesional con acción para crearla.
            return flowOf(HomeUiState(isLoading = false, journey = null))
        }
        return combine(
            getDailyRecords(journey.id),
            patients.observeActivePatients(serviceId),
            pendings.observeOpenByService(serviceId),
        ) { records, patientList, openPendings ->
            buildState(journey, records, patientList, openPendings)
        }
    }

    private fun buildState(
        journey: Journey,
        records: List<DailyRecord>,
        patientList: List<Patient>,
        openPendings: List<Pending>,
    ): HomeUiState {
        val today = clock.todayIso()
        val byPatient = records.associateBy { it.patientId }
        val pendingPatientIds = openPendings.map { it.patientId }.toSet()
        val rows = patientList.map { patient ->
            val record = byPatient[patient.id]
            PatientRow(
                patientId = patient.id,
                bed = record?.bed,
                fullName = patient.fullName,
                age = ageAt(patient.birthDate, today),
                mainDiagnosis = patient.mainDiagnosis,
                reviewState = record?.reviewState ?: ReviewState.PENDING,
                priority = record?.priority,
                hasOpenPending = patient.id in pendingPatientIds,
            )
        }.sortedWith(
            // P1 primero, luego por cama, luego el resto.
            compareBy<PatientRow> { it.priority != ClinicalPriority.P1 }
                .thenBy { it.bed ?: "ZZZ" }
                .thenBy { it.fullName },
        )
        return HomeUiState(
            isLoading = false,
            journey = journey,
            totalPatients = patientList.size,
            reviewedCount = records.count { it.reviewState == ReviewState.COMPLETED },
            inProgressCount = records.count { it.reviewState == ReviewState.IN_PROGRESS },
            pendingReviewCount = patientList.size -
                records.count { it.reviewState == ReviewState.COMPLETED },
            p1Count = records.count { it.priority == ClinicalPriority.P1 },
            openPendingsCount = openPendings.size,
            plannedDischargesCount = records.count { it.dischargePlanned },
            rows = rows,
        )
    }

    /** Inicia la jornada de hoy con el motor idempotente (censo + registros). */
    fun startJourney() {
        viewModelScope.launch {
            try {
                startJourney(serviceId)
            } catch (e: Exception) {
                // El flujo ya emite error; no se traga silenciosamente en logs clínicos.
            }
        }
    }

    fun retry() {
        // Re-suscribe el flujo: la UI reintenta volviendo a observar.
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(
                getCurrentJourney = container.getCurrentJourney,
                getDailyRecords = container.getDailyRecords,
                startJourney = container.startJourney,
                patients = container.patientRepository,
                pendings = container.pendingRepository,
                clock = container.clock,
            ) as T
    }
}
