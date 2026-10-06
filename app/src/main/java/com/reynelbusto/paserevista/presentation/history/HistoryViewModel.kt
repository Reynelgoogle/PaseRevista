package com.reynelbusto.paserevista.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reynelbusto.paserevista.di.AppContainer
import com.reynelbusto.paserevista.domain.model.DailyRecord
import com.reynelbusto.paserevista.domain.model.Journey
import com.reynelbusto.paserevista.domain.model.Patient
import com.reynelbusto.paserevista.domain.model.ReviewState
import com.reynelbusto.paserevista.domain.repository.DailyRecordRepository
import com.reynelbusto.paserevista.domain.repository.JourneyRepository
import com.reynelbusto.paserevista.domain.repository.PatientRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HistoryUiState(
    val isLoading: Boolean = true,
    /** Mes ("2026-10") → jornadas, recientes primero. */
    val byMonth: Map<String, List<Journey>> = emptyMap(),
    val error: String? = null,
)

data class JourneyDetailState(
    val journey: Journey,
    val total: Int = 0,
    val reviewed: Int = 0,
    val rows: List<HistoryPatientRow> = emptyList(),
)

data class HistoryPatientRow(
    val patientName: String,
    val bed: String?,
    val reviewState: ReviewState,
    val evolution: String?,
)

/**
 * FASE 9 — Historial de jornadas: agrupado por mes, solo lectura.
 * Las correcciones son enmiendas visibles, nunca sobrescritura.
 */
class HistoryViewModel(
    private val serviceId: String,
    private val journeys: JourneyRepository,
    private val records: DailyRecordRepository,
    private val patients: PatientRepository,
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = journeys.observeJourneys(serviceId)
        .map { list ->
            HistoryUiState(
                isLoading = false,
                byMonth = list.groupBy { it.clinicalDate.substring(0, 7) },
            )
        }
        .catch { emit(HistoryUiState(isLoading = false, error = "No se pudo cargar el historial.")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    /** Detalle de una jornada: pacientes + registros (solo lectura). */
    fun detailFlow(journey: Journey): Flow<JourneyDetailState> = combine(
        records.observeByJourney(journey.id),
        patients.observeActivePatients(serviceId),
    ) { recs, plist ->
        val names = plist.associate { it.id to it.fullName }
        JourneyDetailState(
            journey = journey,
            total = recs.size,
            reviewed = recs.count { it.reviewState == ReviewState.COMPLETED },
            rows = recs.map {
                HistoryPatientRow(
                    patientName = names[it.patientId] ?: "Paciente",
                    bed = it.bed,
                    reviewState = it.reviewState,
                    evolution = it.evolutionTap?.name,
                )
            }.sortedBy { it.bed ?: "ZZZ" },
        )
    }

    companion object {
        fun monthLabel(monthKey: String): String = try {
            val date = LocalDate.parse("$monthKey-01")
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("es"))
                .format(date).replaceFirstChar { it.uppercase() }
        } catch (e: Exception) {
            monthKey
        }

        fun prettyDate(iso: String): String = try {
            val date = LocalDate.parse(iso)
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es"))
                .format(date).replaceFirstChar { it.uppercase() }
        } catch (e: Exception) {
            iso
        }
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HistoryViewModel(
                serviceId = "urology",
                journeys = container.journeyRepository,
                records = container.dailyRecordRepository,
                patients = container.patientRepository,
            ) as T
    }
}
