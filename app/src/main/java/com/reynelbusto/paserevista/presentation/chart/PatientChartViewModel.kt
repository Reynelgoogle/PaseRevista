package com.reynelbusto.paserevista.presentation.chart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reynelbusto.paserevista.core.Clock
import com.reynelbusto.paserevista.core.IsoDate
import com.reynelbusto.paserevista.core.ageAt
import com.reynelbusto.paserevista.di.AppContainer
import com.reynelbusto.paserevista.domain.model.ClinicalEvolution
import com.reynelbusto.paserevista.domain.model.ClinicalPriority
import com.reynelbusto.paserevista.domain.model.ClinicalResult
import com.reynelbusto.paserevista.domain.model.ClinicalState
import com.reynelbusto.paserevista.domain.model.DailyRecord
import com.reynelbusto.paserevista.domain.model.Device
import com.reynelbusto.paserevista.domain.model.Journey
import com.reynelbusto.paserevista.domain.model.Patient
import com.reynelbusto.paserevista.domain.model.Pending
import com.reynelbusto.paserevista.domain.model.ReviewState
import com.reynelbusto.paserevista.domain.model.Treatment
import com.reynelbusto.paserevista.domain.repository.DailyRecordRepository
import com.reynelbusto.paserevista.domain.repository.DeviceRepository
import com.reynelbusto.paserevista.domain.repository.JourneyRepository
import com.reynelbusto.paserevista.domain.repository.PatientRepository
import com.reynelbusto.paserevista.domain.repository.PendingRepository
import com.reynelbusto.paserevista.domain.repository.ResultRepository
import com.reynelbusto.paserevista.domain.repository.TreatmentRepository
import com.reynelbusto.paserevista.domain.usecase.DischargeUseCase
import com.reynelbusto.paserevista.domain.usecase.ReviewUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChartUiState(
    val isLoading: Boolean = true,
    val patient: Patient? = null,
    val age: Int = 0,
    val journey: Journey? = null,
    val today: DailyRecord? = null,
    val yesterday: DailyRecord? = null,
    val openPendings: List<Pending> = emptyList(),
    val activeTreatments: List<Treatment> = emptyList(),
    val activeDevices: List<Device> = emptyList(),
    val results: List<ClinicalResult> = emptyList(),
    /** Ventana de Deshacer tras "Sin cambios" (5 s). */
    val showUndo: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
)

data class TimelineEntry(val date: IsoDate, val record: DailyRecord)

/**
 * Ficha del paciente: AYER (referencia, no editable) vs HOY (registro del día).
 * Orquesta casos de uso; sin SQL ni reglas clínicas.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PatientChartViewModel(
    private val patientId: String,
    private val serviceId: String,
    private val patients: PatientRepository,
    private val journeys: JourneyRepository,
    private val records: DailyRecordRepository,
    private val pendings: PendingRepository,
    private val treatments: TreatmentRepository,
    private val devices: DeviceRepository,
    private val results: ResultRepository,
    private val review: ReviewUseCase,
    private val discharge: DischargeUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val todayIso: IsoDate get() = clock.todayIso()
    private var undoJob: Job? = null
    private val undoVisible = MutableStateFlow(false)

    private val journeyFlow: Flow<Journey?> =
        journeys.observeCurrentJourney(serviceId, todayIso)

    /** Carga diferida: paciente + AYER (una vez, no necesitan reactividad). */
    private val staticData = MutableStateFlow<Pair<Patient?, DailyRecord?>>(null to null)

    init {
        viewModelScope.launch {
            val patient = patients.getPatient(patientId)
            val yesterday = records.findLatestBefore(patientId, todayIso)
            staticData.value = patient to yesterday
        }
    }

    private fun baseFlow(journey: Journey?): Flow<ChartUiState> {
        if (journey == null) return flowOf(ChartUiState(isLoading = false))
        return combine(
            records.observeByJourney(journey.id),
            pendings.observeOpenByPatient(patientId),
            treatments.observeActiveByPatient(patientId),
            devices.observeActiveByPatient(patientId),
            results.observeByPatient(patientId),
        ) { recs, pends, treats, devs, res ->
            ChartUiState(
                isLoading = false,
                journey = journey,
                today = recs.firstOrNull { it.patientId == patientId },
                openPendings = pends,
                activeTreatments = treats,
                activeDevices = devs,
                results = res,
            )
        }
    }

    /** Estado único de la UI. */
    val state: StateFlow<ChartUiState> = combine(
        journeyFlow.flatMapLatest { baseFlow(it) }
            .catch { emit(ChartUiState(isLoading = false, error = "No se pudo cargar la ficha.")) },
        staticData,
        undoVisible,
    ) { ui, static, undo ->
        val (patient, yesterday) = static
        ui.copy(
            patient = patient,
            age = patient?.let { ageAt(it.birthDate, todayIso) } ?: 0,
            yesterday = yesterday,
            showUndo = undo,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChartUiState())

    /**
     * Siguiente paciente del censo (orden P1 → cama) aún no revisado,
     * excluyendo el actual. NULL si el pase está completo.
     */
    val nextPatientId: StateFlow<String?> = journeyFlow.flatMapLatest { journey ->
        if (journey == null) return@flatMapLatest flowOf(null)
        combine(
            patients.observeActivePatients(serviceId),
            records.observeByJourney(journey.id),
        ) { plist, recs ->
            val byPatient = recs.associateBy { it.patientId }
            plist.sortedWith(
                compareBy<Patient>(
                    { byPatient[it.id]?.priority != com.reynelbusto.paserevista.domain.model.ClinicalPriority.P1 },
                    { byPatient[it.id]?.bed ?: "ZZZ" },
                    { it.fullName },
                ),
            )
                .firstOrNull {
                    it.id != patientId &&
                        (byPatient[it.id]?.reviewState ?: ReviewState.PENDING) != ReviewState.COMPLETED
                }?.id
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Timeline inverso del paciente (FASE 9): (fecha, registro), recientes primero.
     * Solo lectura.
     */
    val timeline: StateFlow<List<TimelineEntry>> = combine(
        records.observeByPatient(patientId),
        journeys.observeJourneys(serviceId),
    ) { recs, js ->
        val dates = js.associate { it.id to it.clinicalDate }
        recs.mapNotNull { r -> dates[r.journeyId]?.let { TimelineEntry(it, r) } }
            .sortedByDescending { it.date }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun journeyId(): String =
        state.value.journey?.id ?: error("Sin jornada activa")

    private fun runAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                // El error se expone; nunca se traga en silencio.
                _notice.value = e.message ?: "Error inesperado"
            }
        }
    }

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    fun clearNotice() { _notice.value = null }

    /** Tap de evolución: "¿Qué cambió desde ayer?" */
    fun onEvolutionTap(evolution: ClinicalEvolution) = runAction {
        val updated = review.recordEvolution(patientId, journeyId(), evolution)
        if (evolution == ClinicalEvolution.UNCHANGED && updated.reviewState == ReviewState.COMPLETED) {
            // Ventana de Deshacer (5 s).
            undoJob?.cancel()
            undoVisible.value = true
            undoJob = viewModelScope.launch {
                delay(5_000)
                undoVisible.value = false
            }
        }
    }

    fun undoUnchanged() = runAction {
        undoJob?.cancel()
        undoVisible.value = false
        review.undoUnchanged(patientId, journeyId())
    }

    fun onClinicalState(v: ClinicalState?) = runAction { review.saveClinicalState(patientId, journeyId(), v) }
    fun onPain(v: Int?) = runAction { review.savePain(patientId, journeyId(), v) }
    fun onFever(v: Boolean?) = runAction { review.saveFever(patientId, journeyId(), v) }
    fun onDiuresis(v: String?) = runAction { review.saveDiuresis(patientId, journeyId(), v) }
    fun onOtherSigns(v: String?) = runAction { review.saveOtherSigns(patientId, journeyId(), v) }
    fun onPriority(v: ClinicalPriority?) = runAction { review.savePriority(patientId, journeyId(), v) }
    fun onEvolutionText(v: String?) = runAction { review.saveEvolutionText(patientId, journeyId(), v) }
    fun onObservations(v: String?) = runAction { review.saveObservations(patientId, journeyId(), v) }
    fun onDischargePlanned(planned: Boolean, date: IsoDate?) =
        runAction { review.saveDischargePlanned(patientId, journeyId(), planned, date) }

    fun onMarkReviewed() = runAction { review.markReviewed(patientId, journeyId()) }
    fun onReopen() = runAction { review.reopen(patientId, journeyId()) }

    /** Alta con verificación P1: devuelve el bloqueo si existe. */
    fun onDischarge(reason: String?, onBlocked: (List<Pending>) -> Unit, onDone: () -> Unit) = runAction {
        when (val check = discharge.check(patientId)) {
            is DischargeUseCase.Check.BlockedByP1 -> onBlocked(check.pendings)
            DischargeUseCase.Check.Ok -> {
                discharge.discharge(patientId, reason = reason)
                onDone()
            }
        }
    }

    fun dischargeForced(reason: String?, onDone: () -> Unit) = runAction {
        discharge.discharge(patientId, reason = reason, force = true)
        onDone()
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(
        private val container: AppContainer,
        private val patientId: String,
        private val serviceId: String = "urology",
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PatientChartViewModel(
                patientId = patientId,
                serviceId = serviceId,
                patients = container.patientRepository,
                journeys = container.journeyRepository,
                records = container.dailyRecordRepository,
                pendings = container.pendingRepository,
                treatments = container.treatmentRepository,
                devices = container.deviceRepository,
                results = container.resultRepository,
                review = container.review,
                discharge = container.discharge,
                clock = container.clock,
            ) as T
    }
}
