package com.reynelbusto.paserevista.domain.usecase

import com.reynelbusto.paserevista.core.Clock
import com.reynelbusto.paserevista.core.IsoDate
import com.reynelbusto.paserevista.core.newId
import com.reynelbusto.paserevista.domain.TransitionGuard
import com.reynelbusto.paserevista.domain.model.ClinicalPriority
import com.reynelbusto.paserevista.domain.model.DailyRecord
import com.reynelbusto.paserevista.domain.model.Patient
import com.reynelbusto.paserevista.domain.model.PatientState
import com.reynelbusto.paserevista.domain.model.Pending
import com.reynelbusto.paserevista.domain.repository.ClinicalUnitOfWork
import com.reynelbusto.paserevista.domain.repository.DailyRecordRepository
import com.reynelbusto.paserevista.domain.repository.JourneyRepository
import com.reynelbusto.paserevista.domain.repository.PatientRepository
import com.reynelbusto.paserevista.domain.repository.PendingRepository

/**
 * FASE 6 — Alta hospitalaria ("Alta" = solo egreso, nunca otro concepto).
 *
 * Casos:
 * - A: alta antes de crear la jornada → el paciente no entra al censo (filtro ACTIVE).
 * - B: alta con jornada ya creada → sale del censo futuro, conserva historia.
 * - C: alta descubierta durante la revisión → igual que B, desde la ficha.
 * - D: P1 abierto → BLOQUEA el alta silenciosa: exige resolver, cancelar con
 *   motivo o reasignar antes de continuar.
 */
class DischargeUseCase(
    private val unitOfWork: ClinicalUnitOfWork,
    private val patients: PatientRepository,
    private val pendings: PendingRepository,
    private val clock: Clock,
) {
    sealed interface Check {
        data object Ok : Check
        data class BlockedByP1(val pendings: List<Pending>) : Check
    }

    /** Pendientes P1 abiertos que bloquearían un alta silenciosa. */
    suspend fun blockingP1(patientId: String): List<Pending> =
        pendings.getOpenByPatient(patientId).filter { it.priority == ClinicalPriority.P1 }

    suspend fun check(patientId: String): Check {
        val p1 = blockingP1(patientId)
        return if (p1.isEmpty()) Check.Ok else Check.BlockedByP1(p1)
    }

    /**
     * Ejecuta el alta. Si hay P1 abiertos y [force] es falso, falla
     * (la UI debe mostrar el diálogo de decisión del caso D).
     */
    suspend fun discharge(
        patientId: String,
        dischargeDate: IsoDate = clock.todayIso(),
        reason: String? = null,
        force: Boolean = false,
    ): Patient = unitOfWork.atomic {
        val patient = patients.getPatient(patientId) ?: error("Paciente no encontrado")
        check(TransitionGuard.patient(patient.status, PatientState.DISCHARGED)) {
            "El paciente no está en estado ACTIVE"
        }
        val p1 = blockingP1(patientId)
        check(force || p1.isEmpty()) {
            "Alta bloqueada: hay ${p1.size} pendiente(s) P1 abierto(s)"
        }
        val updated = patient.copy(
            status = PatientState.DISCHARGED,
            dischargeDate = dischargeDate,
            dischargeReason = reason?.ifBlank { null },
        )
        patients.updatePatient(updated)
        updated
    }

    /** Traslado a otro servicio: mismo Patient, historia intacta. */
    suspend fun transfer(patientId: String, toServiceId: String): Patient = unitOfWork.atomic {
        val patient = patients.getPatient(patientId) ?: error("Paciente no encontrado")
        check(TransitionGuard.patient(patient.status, PatientState.TRANSFERRED)) {
            "El paciente no admite traslado desde ${patient.status}"
        }
        val updated = patient.copy(status = PatientState.TRANSFERRED, serviceId = toServiceId)
        patients.updatePatient(updated)
        updated
    }

    /**
     * Readmisión: nuevo episodio enlazado con previousEpisodeId, sin duplicar
     * la identidad del paciente. El episodio anterior debe estar cerrado
     * (alta/traslado) antes de readmitir.
     */
    suspend fun readmit(
        previousPatientId: String,
        newPatient: Patient,
        bed: String?,
        journeys: JourneyRepository,
        records: DailyRecordRepository,
    ): String = unitOfWork.atomic {
        val previous = patients.getPatient(previousPatientId)
            ?: error("Episodio anterior no encontrado")
        check(previous.status != PatientState.ACTIVE) {
            "El episodio anterior sigue activo: dale alta o trasládalo primero"
        }
        val linked = newPatient.copy(
            id = newId(),
            status = PatientState.ACTIVE,
            dischargeDate = null,
            dischargeReason = null,
            previousEpisodeId = previousPatientId,
        )
        patients.createPatient(linked)
        val journey = journeys.getOrCreateJourney(
            linked.serviceId,
            clock.todayIso(),
            com.reynelbusto.paserevista.domain.model.JourneyOrigin.AUTO.code,
        )
        val now = clock.nowMillis()
        records.create(
            DailyRecord(
                id = newId(),
                patientId = linked.id,
                journeyId = journey.id,
                bed = bed,
                createdAt = now,
                updatedAt = now,
            ),
        )
        linked.id
    }
}
