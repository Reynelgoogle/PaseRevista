package com.reynelbusto.paserevista.domain.usecase

import com.reynelbusto.paserevista.core.Clock
import com.reynelbusto.paserevista.core.IsoDate
import com.reynelbusto.paserevista.core.newId
import com.reynelbusto.paserevista.domain.TransitionGuard
import com.reynelbusto.paserevista.domain.model.ClinicalPriority
import com.reynelbusto.paserevista.domain.model.ClinicalResult
import com.reynelbusto.paserevista.domain.model.Device
import com.reynelbusto.paserevista.domain.model.DeviceKind
import com.reynelbusto.paserevista.domain.model.DeviceState
import com.reynelbusto.paserevista.domain.model.PendingState
import com.reynelbusto.paserevista.domain.model.Procedure
import com.reynelbusto.paserevista.domain.model.ProcedureState
import com.reynelbusto.paserevista.domain.model.ResultType
import com.reynelbusto.paserevista.domain.repository.ClinicalUnitOfWork
import com.reynelbusto.paserevista.domain.repository.DeviceRepository
import com.reynelbusto.paserevista.domain.repository.PendingRepository
import com.reynelbusto.paserevista.domain.repository.ProcedureRepository
import com.reynelbusto.paserevista.domain.repository.ResultRepository

/**
 * FASE 8 — Dispositivos: una sola entidad con ciclo de vida.
 * Colocación → activo → retirada con fecha. Recolocar = nuevo Device.
 */
class DeviceUseCase(
    private val devices: DeviceRepository,
    private val clock: Clock,
) {
    suspend fun place(
        patientId: String,
        kind: DeviceKind,
        label: String? = null,
        placedDate: IsoDate = clock.todayIso(),
    ): Device {
        val now = clock.nowMillis()
        val device = Device(
            id = newId(), patientId = patientId, kind = kind,
            label = label?.ifBlank { null }, placedDate = placedDate,
            createdAt = now, updatedAt = now,
        )
        devices.create(device)
        return device
    }

    suspend fun remove(id: String, journeyId: String? = null): Device {
        val device = devices.getDevice(id) ?: error("Dispositivo no encontrado")
        check(TransitionGuard.device(device.status, DeviceState.REMOVED)) {
            "El dispositivo no está activo"
        }
        val updated = device.copy(
            status = DeviceState.REMOVED,
            removedAt = clock.nowMillis(),
            removalJourneyId = journeyId,
        )
        devices.update(updated)
        return updated
    }
}

/**
 * FASE 8 — Resultados: crear/ver con fecha clínica propia.
 * Un resultado tardío se registra con su fecha real SIN reabrir la jornada.
 */
class ResultUseCase(
    private val results: ResultRepository,
    private val clock: Clock,
) {
    suspend fun register(
        patientId: String,
        resultDate: IsoDate,
        type: ResultType,
        summary: String,
        detail: String? = null,
        dailyRecordId: String? = null,
        registeredJourneyId: String? = null,
    ): ClinicalResult {
        require(summary.isNotBlank()) { "El resumen es obligatorio" }
        val result = ClinicalResult(
            id = newId(), patientId = patientId, dailyRecordId = dailyRecordId,
            resultDate = resultDate, type = type, summary = summary.trim(),
            detail = detail?.ifBlank { null }, registeredJourneyId = registeredJourneyId,
            createdAt = clock.nowMillis(),
        )
        results.create(result)
        return result
    }
}

/**
 * FASE 8 — Procedimientos y pizarra quirúrgica.
 * Kanban: PENDIENTES → PREPARACIÓN → REALIZADOS.
 * Relación procedimiento↔pendiente: al realizar, el pendiente enlazado se
 * completa SOLO si es exactamente la misma tarea (enlace explícito pendingId).
 */
class ProcedureUseCase(
    private val unitOfWork: ClinicalUnitOfWork,
    private val procedures: ProcedureRepository,
    private val pendings: PendingRepository,
    private val clock: Clock,
) {
    suspend fun schedule(
        patientId: String,
        kind: String,
        scheduledDate: IsoDate? = null,
        scheduledTime: String? = null,
        priority: ClinicalPriority = ClinicalPriority.P3,
        pendingId: String? = null,
        journeyId: String? = null,
        note: String? = null,
    ): Procedure {
        require(kind.isNotBlank()) { "La descripción del procedimiento es obligatoria" }
        val now = clock.nowMillis()
        val procedure = Procedure(
            id = newId(), patientId = patientId, kind = kind.trim(),
            scheduledDate = scheduledDate, scheduledTime = scheduledTime,
            priority = priority, journeyId = journeyId, note = note?.ifBlank { null },
            pendingId = pendingId,
            createdAt = now, updatedAt = now,
        )
        procedures.create(procedure)
        return procedure
    }

    suspend fun toPreparation(id: String): Procedure = transition(id, ProcedureState.PREPARATION)

    /**
     * Realizar: cierra el procedimiento y, si nació de un pendiente concreto
     * (pendingId), completa ESE pendiente (misma tarea exacta).
     */
    suspend fun perform(id: String, journeyResolutionId: String?): Procedure =
        unitOfWork.atomic {
            val procedure = transition(id, ProcedureState.PERFORMED)
            val pendingId = procedure.pendingId
            if (pendingId != null) {
                val pending = pendings.getPending(pendingId)
                if (pending != null &&
                    (pending.status == PendingState.PENDING || pending.status == PendingState.IN_PROGRESS)
                ) {
                    check(TransitionGuard.pending(pending.status, PendingState.COMPLETED))
                    pendings.update(
                        pending.copy(
                            status = PendingState.COMPLETED,
                            journeyResolutionId = journeyResolutionId,
                            resolvedAt = clock.nowMillis(),
                        ),
                    )
                }
            }
            procedure.copy(performedAt = clock.nowMillis()).also { procedures.update(it) }
        }

    suspend fun cancel(id: String): Procedure = transition(id, ProcedureState.CANCELED)

    private suspend fun transition(id: String, to: ProcedureState): Procedure {
        val procedure = procedures.getProcedure(id) ?: error("Procedimiento no encontrado")
        check(TransitionGuard.procedure(procedure.status, to)) {
            "Transición no permitida: ${procedure.status} → $to"
        }
        val updated = procedure.copy(status = to)
        procedures.update(updated)
        return updated
    }
}
