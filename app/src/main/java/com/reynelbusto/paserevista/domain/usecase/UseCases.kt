package com.reynelbusto.paserevista.domain.usecase

import com.reynelbusto.paserevista.core.Clock
import com.reynelbusto.paserevista.core.IsoDate
import com.reynelbusto.paserevista.domain.model.Journey
import com.reynelbusto.paserevista.domain.model.JourneyOrigin
import com.reynelbusto.paserevista.domain.model.Patient
import com.reynelbusto.paserevista.domain.repository.JourneyRepository
import com.reynelbusto.paserevista.domain.repository.PatientRepository
import kotlinx.coroutines.flow.Flow

class GetPatientsUseCase(private val patients: PatientRepository) {
    operator fun invoke(serviceId: String, query: String = ""): Flow<List<Patient>> =
        if (query.isBlank()) patients.observeActivePatients(serviceId)
        else patients.searchPatients(serviceId, query)
}

class GetCurrentJourneyUseCase(
    private val journeys: JourneyRepository,
    private val clock: Clock,
) {
    operator fun invoke(serviceId: String): Flow<Journey?> =
        journeys.observeCurrentJourney(serviceId, clock.todayIso())
}

/**
 * Obtiene o crea la jornada del día (idempotente).
 * Pivot: el motor es invisible — la UI la asegura al abrir vía EnsureDayUseCase.
 */
class CreateJourneyUseCase(
    private val journeys: JourneyRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        serviceId: String,
        clinicalDate: IsoDate = clock.todayIso(),
        origin: JourneyOrigin = JourneyOrigin.MANUAL,
    ): Journey = journeys.getOrCreateJourney(serviceId, clinicalDate, origin.code)
}
