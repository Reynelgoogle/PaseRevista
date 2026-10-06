package com.reynelbusto.paserevista.domain.usecase

import com.reynelbusto.paserevista.core.Clock
import com.reynelbusto.paserevista.core.IsoDate
import com.reynelbusto.paserevista.core.newId
import com.reynelbusto.paserevista.domain.model.DailyRecord
import com.reynelbusto.paserevista.domain.model.Journey
import com.reynelbusto.paserevista.domain.model.JourneyOrigin
import com.reynelbusto.paserevista.domain.repository.ClinicalUnitOfWork
import com.reynelbusto.paserevista.domain.repository.DailyRecordRepository
import com.reynelbusto.paserevista.domain.repository.JourneyRepository
import com.reynelbusto.paserevista.domain.repository.PatientRepository

/**
 * FASE 6 — Motor de jornadas idempotente.
 *
 * `StartJourney(servicio, fecha)`:
 * 1. Get-or-create de la Journey (UNIQUE(fecha, servicio) en BD).
 * 2. Censo = pacientes ACTIVE (altas/traslados quedan fuera por filtro).
 * 3. En UNA transacción: un DailyRecord por paciente del censo SIN registro hoy.
 *
 * HERENCIA≠COPIA: los clínicos nacen NULL. La cama (logística, no clínica)
 * parte de la última conocida para no pedirla cada día; todo lo demás
 * (estado, signos, evolución, prioridad, observaciones) empieza vacío.
 * `previousRecordId` enlaza con AYER solo como referencia de lectura.
 *
 * Doble pulsación de "Comenzar pase de revista" → una sola jornada y cero
 * registros duplicados (UNIQUE + get-or-create + filtro de existentes).
 */
class StartJourneyUseCase(
    private val unitOfWork: ClinicalUnitOfWork,
    private val journeys: JourneyRepository,
    private val patients: PatientRepository,
    private val records: DailyRecordRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        serviceId: String,
        clinicalDate: IsoDate = clock.todayIso(),
        origin: JourneyOrigin = JourneyOrigin.MANUAL,
    ): Journey = unitOfWork.atomic {
        val journey = journeys.getOrCreateJourney(serviceId, clinicalDate, origin.code)
        val previousJourney = journeys.findPreviousJourney(serviceId, clinicalDate)
        val now = clock.nowMillis()

        val fresh = patients.getActivePatients(serviceId).mapNotNull { patient ->
            // Idempotencia fina: si ya tiene registro hoy (carrera/reintento), no duplicar.
            if (records.getByPatientAndJourney(patient.id, journey.id) != null) return@mapNotNull null
            val previousRecord = previousJourney?.let {
                records.getByPatientAndJourney(patient.id, it.id)
            }
            DailyRecord(
                id = newId(),
                patientId = patient.id,
                journeyId = journey.id,
                bed = previousRecord?.bed,
                previousRecordId = previousRecord?.id,
                createdAt = now,
                updatedAt = now,
            )
        }
        records.createAll(fresh)
        journey
    }
}
