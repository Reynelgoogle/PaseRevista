package com.reynelbusto.paserevista.domain.usecase

import com.reynelbusto.paserevista.core.Clock
import com.reynelbusto.paserevista.core.IsoDate
import com.reynelbusto.paserevista.core.newId
import com.reynelbusto.paserevista.domain.TransitionGuard
import com.reynelbusto.paserevista.domain.model.ClinicalPriority
import com.reynelbusto.paserevista.domain.model.Pending
import com.reynelbusto.paserevista.domain.model.PendingState
import com.reynelbusto.paserevista.domain.model.PendingType
import com.reynelbusto.paserevista.domain.repository.PendingRepository

/**
 * FASE 8 — Pendientes: entidad longitudinal con identidad única.
 * Sin DELETE físico: completar o cancelar con trazabilidad.
 * Vencido = badge en UI; jamás cambia la prioridad automáticamente.
 */
class PendingUseCase(
    private val pendings: PendingRepository,
    private val clock: Clock,
) {
    suspend fun create(
        patientId: String,
        description: String,
        type: PendingType = PendingType.GENERAL,
        priority: ClinicalPriority = ClinicalPriority.P3,
        dueDate: IsoDate? = null,
        assignee: String? = null,
        serviceDest: String? = null,
        interconsultNote: String? = null,
        journeyOriginId: String? = null,
        idempotencyKey: String? = null,
    ): Pending {
        require(description.isNotBlank()) { "La descripción es obligatoria" }
        if (type == PendingType.INTERCONSULTATION) {
            require(!serviceDest.isNullOrBlank()) { "La interconsulta exige servicio destino" }
        }
        val now = clock.nowMillis()
        val pending = Pending(
            id = newId(),
            patientId = patientId,
            description = description.trim(),
            type = type,
            priority = priority,
            status = PendingState.PENDING,
            requestedAt = now,
            dueDate = dueDate,
            assignee = assignee?.ifBlank { null },
            serviceDest = serviceDest?.ifBlank { null },
            interconsultNote = interconsultNote?.ifBlank { null },
            journeyOriginId = journeyOriginId,
            createdAt = now,
            updatedAt = now,
        )
        // Idempotencia ante doble tap: si la clave ya existe, no duplicar.
        // (La BD tiene UNIQUE(idempotency_key); aquí evitamos el throw.)
        pendings.create(pending)
        return pending
    }

    suspend fun startProgress(id: String): Pending = transition(id, PendingState.IN_PROGRESS)

    /**
     * Completar: misma entidad + fecha/hora + jornada de resolución.
     * La identidad persiste para el historial.
     */
    suspend fun complete(id: String, journeyResolutionId: String?): Pending {
        val pending = requirePending(id)
        check(TransitionGuard.pending(pending.status, PendingState.COMPLETED)) {
            "El pendiente no admite completarse desde ${pending.status}"
        }
        val updated = pending.copy(
            status = PendingState.COMPLETED,
            journeyResolutionId = journeyResolutionId,
            resolvedAt = clock.nowMillis(),
        )
        pendings.update(updated)
        return updated
    }

    suspend fun cancel(id: String, reason: String): Pending {
        require(reason.isNotBlank()) { "Cancelar exige motivo" }
        val pending = requirePending(id)
        check(TransitionGuard.pending(pending.status, PendingState.CANCELED)) {
            "El pendiente no admite cancelarse desde ${pending.status}"
        }
        val updated = pending.copy(
            status = PendingState.CANCELED,
            cancelReason = reason.trim(),
            resolvedAt = clock.nowMillis(),
        )
        pendings.update(updated)
        return updated
    }

    /** Reasignar responsable/fecha sin cambiar la identidad. */
    suspend fun reassign(id: String, assignee: String?, dueDate: IsoDate?): Pending {
        val pending = requirePending(id)
        check(pending.status == PendingState.PENDING || pending.status == PendingState.IN_PROGRESS) {
            "Solo se reasigna un pendiente abierto"
        }
        val updated = pending.copy(assignee = assignee?.ifBlank { null }, dueDate = dueDate)
        pendings.update(updated)
        return updated
    }

    private suspend fun requirePending(id: String): Pending =
        pendings.getPending(id) ?: error("Pendiente no encontrado")

    private suspend fun transition(id: String, to: PendingState): Pending {
        val pending = requirePending(id)
        check(TransitionGuard.pending(pending.status, to)) {
            "Transición no permitida: ${pending.status} → $to"
        }
        val updated = pending.copy(status = to)
        pendings.update(updated)
        return updated
    }
}
