package com.reynelbusto.paserevista.data.debug

import com.reynelbusto.paserevista.core.Clock
import com.reynelbusto.paserevista.core.IsoDate
import com.reynelbusto.paserevista.core.newId
import com.reynelbusto.paserevista.domain.model.ClinicalPriority
import com.reynelbusto.paserevista.domain.model.DailyRecord
import com.reynelbusto.paserevista.domain.model.Device
import com.reynelbusto.paserevista.domain.model.DeviceKind
import com.reynelbusto.paserevista.domain.model.JourneyOrigin
import com.reynelbusto.paserevista.domain.model.Patient
import com.reynelbusto.paserevista.domain.model.PatientState
import com.reynelbusto.paserevista.domain.model.Pending
import com.reynelbusto.paserevista.domain.model.PendingState
import com.reynelbusto.paserevista.domain.model.PendingType
import com.reynelbusto.paserevista.domain.model.ReviewState
import com.reynelbusto.paserevista.domain.model.Sex
import com.reynelbusto.paserevista.domain.model.Treatment
import com.reynelbusto.paserevista.domain.repository.DailyRecordRepository
import com.reynelbusto.paserevista.domain.repository.DeviceRepository
import com.reynelbusto.paserevista.domain.repository.JourneyRepository
import com.reynelbusto.paserevista.domain.repository.PatientRepository
import com.reynelbusto.paserevista.domain.repository.PendingRepository
import com.reynelbusto.paserevista.domain.repository.TreatmentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Datos de prueba SOLO para desarrollo.
 *
 * - Se ejecuta únicamente desde builds debug (puerta: BuildConfig.SEED_ENABLED).
 * - Todo lo insertado está marcado como prueba: nombres "[PRUEBA]" y HC "TEST-*".
 * - Nunca se mezcla con datos reales: es un volcado manual y visible, no un seed
 *   automático al arrancar.
 */
class DebugSeed(
    private val patientRepository: PatientRepository,
    private val journeyRepository: JourneyRepository,
    private val dailyRecordRepository: DailyRecordRepository,
    private val pendingRepository: PendingRepository,
    private val treatmentRepository: TreatmentRepository,
    private val deviceRepository: DeviceRepository,
    private val clock: Clock,
) {
    suspend fun seedTestData(serviceId: String = "urology"): Unit = withContext(Dispatchers.IO) {
        val today: IsoDate = clock.todayIso()
        val now = clock.nowMillis()

        val journey = journeyRepository.getOrCreateJourney(
            serviceId = serviceId,
            clinicalDate = today,
            originCode = JourneyOrigin.MANUAL.code,
        )

        val patients = listOf(
            Triple("[PRUEBA] Juan Pérez", "TEST-001", "12") to "Litiasis ureteral",
            Triple("[PRUEBA] María López", "TEST-002", "14") to "Hiperplasia prostática benigna",
            Triple("[PRUEBA] Carlos García", "TEST-003", "18") to "Cólico nefrítico",
        ).map { (info, diagnosis) ->
            val (name, hc, bed) = info
            val patient = Patient(
                id = newId(),
                fullName = name,
                birthDate = "1958-03-12",
                sex = Sex.M,
                hcNumber = hc,
                bloodGroup = "O+",
                serviceId = serviceId,
                admissionDate = today,
                admissionReason = "Dato de prueba",
                mainDiagnosis = diagnosis,
                status = PatientState.ACTIVE,
                createdAt = now,
                updatedAt = now,
            )
            patientRepository.createPatient(patient)
            dailyRecordRepository.create(
                DailyRecord(
                    id = newId(),
                    patientId = patient.id,
                    journeyId = journey.id,
                    bed = bed,
                    reviewState = ReviewState.PENDING,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            patient
        }

        // Un pendiente de prueba (misma entidad longitudinal).
        pendingRepository.create(
            Pending(
                id = newId(),
                patientId = patients[0].id,
                description = "[PRUEBA] Revisar hemograma",
                type = PendingType.STUDY,
                priority = ClinicalPriority.P2,
                status = PendingState.PENDING,
                requestedAt = now,
                journeyOriginId = journey.id,
                createdAt = now,
                updatedAt = now,
            ),
        )

        // Un tratamiento de prueba (día derivado de startDate).
        treatmentRepository.create(
            Treatment(
                id = newId(),
                patientId = patients[0].id,
                drug = "[PRUEBA] Ceftriaxona",
                dose = "1 g",
                route = "IV",
                frequency = "c/12 h",
                startDate = today,
                createdAt = now,
                updatedAt = now,
            ),
        )

        // Un dispositivo de prueba (entidad única).
        deviceRepository.create(
            Device(
                id = newId(),
                patientId = patients[0].id,
                kind = DeviceKind.JJ_STENT,
                label = "[PRUEBA] Sonda JJ derecha",
                placedDate = today,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }
}
