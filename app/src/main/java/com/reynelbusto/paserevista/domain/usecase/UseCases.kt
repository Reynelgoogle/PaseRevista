package com.reynelbusto.paserevista.domain.usecase

import com.reynelbusto.paserevista.core.Clock
import com.reynelbusto.paserevista.core.IsoDate
import com.reynelbusto.paserevista.core.newId
import com.reynelbusto.paserevista.domain.model.Journey
import com.reynelbusto.paserevista.domain.model.JourneyOrigin
import com.reynelbusto.paserevista.domain.model.JourneyState
import com.reynelbusto.paserevista.domain.model.Patient
import com.reynelbusto.paserevista.domain.model.PatientState
import com.reynelbusto.paserevista.domain.model.Sex
import com.reynelbusto.paserevista.domain.repository.JourneyRepository
import com.reynelbusto.paserevista.domain.repository.PatientRepository
import kotlinx.coroutines.flow.Flow

data class NewPatientInput(
    val fullName: String,
    val birthDate: IsoDate,
    val sex: Sex,
    val hcNumber: String,
    val bloodGroup: String?,
    val serviceId: String,
    val admissionReason: String?,
    val mainDiagnosis: String,
)

/** Crea un paciente con los datos esenciales (< 30 s). Valida lo mínimo obligatorio. */
class CreatePatientUseCase(
    private val patients: PatientRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(input: NewPatientInput): String {
        require(input.fullName.isNotBlank()) { "El nombre es obligatorio" }
        require(input.hcNumber.isNotBlank()) { "La historia clínica es obligatoria" }
        require(input.mainDiagnosis.isNotBlank()) { "El diagnóstico principal es obligatorio" }
        val now = clock.nowMillis()
        val patient = Patient(
            id = newId(),
            fullName = input.fullName.trim(),
            birthDate = input.birthDate,
            sex = input.sex,
            hcNumber = input.hcNumber.trim(),
            bloodGroup = input.bloodGroup?.trim()?.ifBlank { null },
            serviceId = input.serviceId,
            admissionDate = clock.todayIso(),
            admissionReason = input.admissionReason?.trim()?.ifBlank { null },
            mainDiagnosis = input.mainDiagnosis.trim(),
            status = PatientState.ACTIVE,
            createdAt = now,
            updatedAt = now,
        )
        return patients.createPatient(patient)
    }
}

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
 * FASE 5: crea solo la fila Journey. La continuidad (DailyRecords heredados)
 * pertenece a FASE 6 (ContinuityEngine).
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
