package com.reynelbusto.paserevista.domain.usecase

import com.reynelbusto.paserevista.core.Clock
import com.reynelbusto.paserevista.core.newId
import com.reynelbusto.paserevista.domain.model.DailyRecord
import com.reynelbusto.paserevista.domain.model.JourneyOrigin
import com.reynelbusto.paserevista.domain.model.Patient
import com.reynelbusto.paserevista.domain.model.PatientState
import com.reynelbusto.paserevista.domain.repository.ClinicalUnitOfWork
import com.reynelbusto.paserevista.domain.repository.DailyRecordRepository
import com.reynelbusto.paserevista.domain.repository.JourneyRepository
import com.reynelbusto.paserevista.domain.repository.PatientRepository

/**
 * FASE 6 — Nuevo ingreso en < 30 s: 7 campos esenciales.
 * Crea Patient + DailyRecord de hoy en UNA transacción, sin tocar el pasado.
 * Anti-duplicados: la HC no puede repetirse dentro del servicio.
 */
class AdmitPatientUseCase(
    private val unitOfWork: ClinicalUnitOfWork,
    private val patients: PatientRepository,
    private val journeys: JourneyRepository,
    private val records: DailyRecordRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(input: NewPatientInput, bed: String?): String =
        unitOfWork.atomic {
            require(input.fullName.isNotBlank()) { "El nombre es obligatorio" }
            require(input.hcNumber.isNotBlank()) { "La historia clínica es obligatoria" }
            require(input.mainDiagnosis.isNotBlank()) { "El diagnóstico principal es obligatorio" }
            val duplicate = patients.findByHc(input.serviceId, input.hcNumber)
            require(duplicate == null) {
                "Ya existe un paciente con HC ${input.hcNumber.trim()} en este servicio"
            }
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
            patients.createPatient(patient)
            val journey = journeys.getOrCreateJourney(
                input.serviceId,
                clock.todayIso(),
                JourneyOrigin.AUTO.code,
            )
            records.create(
                DailyRecord(
                    id = newId(),
                    patientId = patient.id,
                    journeyId = journey.id,
                    bed = bed?.ifBlank { null },
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            patient.id
        }
}

/**
 * Cambio de cama: solo toca el DailyRecord de hoy.
 * La historia (camas de días anteriores) queda intacta.
 */
class ChangeBedUseCase(
    private val records: DailyRecordRepository,
) {
    suspend operator fun invoke(patientId: String, journeyId: String, newBed: String?): DailyRecord {
        val record = records.getByPatientAndJourney(patientId, journeyId)
            ?: error("Sin DailyRecord para paciente=$patientId jornada=$journeyId")
        val updated = record.copy(bed = newBed?.ifBlank { null })
        records.update(updated)
        return updated
    }
}
