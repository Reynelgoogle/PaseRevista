package com.reynelbusto.paserevista.domain.usecase

import com.reynelbusto.paserevista.core.Clock
import com.reynelbusto.paserevista.core.IsoDate
import com.reynelbusto.paserevista.core.newId
import com.reynelbusto.paserevista.core.treatmentDay
import com.reynelbusto.paserevista.domain.TransitionGuard
import com.reynelbusto.paserevista.domain.model.Treatment
import com.reynelbusto.paserevista.domain.model.TreatmentEvent
import com.reynelbusto.paserevista.domain.model.TreatmentEventType
import com.reynelbusto.paserevista.domain.model.TreatmentState
import com.reynelbusto.paserevista.domain.repository.ClinicalUnitOfWork
import com.reynelbusto.paserevista.domain.repository.TreatmentRepository
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first

/**
 * FASE 8 — Tratamientos: ciclo completo con eventos append-only.
 * El DÍA se deriva (fechaInicio − suspensiones), jamás se almacena.
 * Suspender congela el día; reiniciar retoma el MISMO tratamiento.
 */
class TreatmentUseCase(
    private val unitOfWork: ClinicalUnitOfWork,
    private val treatments: TreatmentRepository,
    private val clock: Clock,
) {
    suspend fun start(
        patientId: String,
        drug: String,
        dose: String,
        route: String,
        frequency: String,
        startDate: IsoDate = clock.todayIso(),
        journeyId: String? = null,
    ): Treatment = unitOfWork.atomic {
        require(drug.isNotBlank()) { "El medicamento es obligatorio" }
        val now = clock.nowMillis()
        val treatment = Treatment(
            id = newId(),
            patientId = patientId,
            drug = drug.trim(),
            dose = dose.trim(),
            route = route.trim(),
            frequency = frequency.trim(),
            startDate = startDate,
            createdAt = now,
            updatedAt = now,
        )
        treatments.create(treatment)
        treatments.addEvent(
            TreatmentEvent(
                id = newId(), treatmentId = treatment.id, journeyId = journeyId,
                eventType = TreatmentEventType.STARTED, occurredAt = now,
            ),
        )
        treatment
    }

    /** Modificar dosis/vía/frecuencia/medicamento: evento con antes→después. */
    suspend fun modify(
        id: String,
        drug: String? = null,
        dose: String? = null,
        route: String? = null,
        frequency: String? = null,
        journeyId: String? = null,
        note: String? = null,
    ): Treatment = unitOfWork.atomic {
        val t = requireTreatment(id)
        check(t.status == TreatmentState.ACTIVE) { "Solo se modifica un tratamiento activo" }
        val now = clock.nowMillis()
        val updated = t.copy(
            drug = drug?.ifBlank { null } ?: t.drug,
            dose = dose?.ifBlank { null } ?: t.dose,
            route = route?.ifBlank { null } ?: t.route,
            frequency = frequency?.ifBlank { null } ?: t.frequency,
        )
        treatments.update(updated)
        val changes = buildList {
            if (updated.drug != t.drug) add(TreatmentEventType.DRUG_CHANGED)
            if (updated.dose != t.dose) add(TreatmentEventType.DOSE_CHANGED)
            if (updated.route != t.route) add(TreatmentEventType.ROUTE_CHANGED)
            if (updated.frequency != t.frequency) add(TreatmentEventType.FREQUENCY_CHANGED)
        }
        for (type in changes) {
            treatments.addEvent(
                TreatmentEvent(
                    id = newId(), treatmentId = id, journeyId = journeyId,
                    eventType = type,
                    previousValue = snapshot(t), newValue = snapshot(updated),
                    occurredAt = now, note = note,
                ),
            )
        }
        updated
    }

    suspend fun suspend(id: String, reason: String, journeyId: String? = null): Treatment =
        unitOfWork.atomic {
            val t = requireTreatment(id)
            check(TransitionGuard.treatment(t.status, TreatmentState.SUSPENDED)) {
                "No se puede suspender desde ${t.status}"
            }
            require(reason.isNotBlank()) { "Suspender exige motivo" }
            val now = clock.nowMillis()
            val updated = t.copy(status = TreatmentState.SUSPENDED, suspendReason = reason.trim())
            treatments.update(updated)
            treatments.addEvent(
                TreatmentEvent(
                    id = newId(), treatmentId = id, journeyId = journeyId,
                    eventType = TreatmentEventType.SUSPENDED,
                    occurredAt = now, note = reason.trim(),
                ),
            )
            updated
        }

    /** Reiniciar: retoma el MISMO tratamiento (no crea uno nuevo). */
    suspend fun resume(id: String, journeyId: String? = null): Treatment = unitOfWork.atomic {
        val t = requireTreatment(id)
        check(TransitionGuard.treatment(t.status, TreatmentState.ACTIVE)) {
            "No se puede reiniciar desde ${t.status}"
        }
        val now = clock.nowMillis()
        val updated = t.copy(status = TreatmentState.ACTIVE, suspendReason = null)
        treatments.update(updated)
        treatments.addEvent(
            TreatmentEvent(
                id = newId(), treatmentId = id, journeyId = journeyId,
                eventType = TreatmentEventType.RESUMED, occurredAt = now,
            ),
        )
        updated
    }

    suspend fun finalize(id: String, endDate: IsoDate = clock.todayIso(), journeyId: String? = null): Treatment =
        unitOfWork.atomic {
            val t = requireTreatment(id)
            check(TransitionGuard.treatment(t.status, TreatmentState.FINALIZED)) {
                "No se puede finalizar desde ${t.status}"
            }
            val now = clock.nowMillis()
            val updated = t.copy(status = TreatmentState.FINALIZED, endDate = endDate)
            treatments.update(updated)
            treatments.addEvent(
                TreatmentEvent(
                    id = newId(), treatmentId = id, journeyId = journeyId,
                    eventType = TreatmentEventType.FINALIZED, occurredAt = now,
                ),
            )
            updated
        }

    /**
     * Día de tratamiento derivado: días desde fechaInicio menos días en suspensión.
     * Los rangos de suspensión se derivan de los eventos (SUSPENDED→RESUMED/FINALIZED).
     */
    suspend fun currentDay(id: String, today: IsoDate = clock.todayIso(), zone: ZoneId = ZoneId.systemDefault()): Int {
        val t = requireTreatment(id)
        val events = treatments.observeEvents(id).first()
        val suspensions = suspensionRanges(events, today, zone)
        return treatmentDay(t.startDate, today, suspensions)
    }

    private suspend fun requireTreatment(id: String): Treatment =
        treatments.getTreatment(id) ?: error("Tratamiento no encontrado")

    private fun snapshot(t: Treatment): String =
        """{"drug":"${t.drug}","dose":"${t.dose}","route":"${t.route}","frequency":"${t.frequency}"}"""

    private fun suspensionRanges(
        events: List<TreatmentEvent>,
        today: IsoDate,
        zone: ZoneId,
    ): List<Pair<IsoDate, IsoDate>> {
        val ranges = mutableListOf<Pair<IsoDate, IsoDate>>()
        var open: IsoDate? = null
        for (e in events.sortedBy { it.occurredAt }) {
            val date = Instant.ofEpochMilli(e.occurredAt).atZone(zone).toLocalDate().toString()
            when (e.eventType) {
                TreatmentEventType.SUSPENDED -> if (open == null) open = date
                TreatmentEventType.RESUMED, TreatmentEventType.FINALIZED -> {
                    if (open != null) {
                        ranges += open to date
                        open = null
                    }
                }
                else -> Unit
            }
        }
        if (open != null) ranges += open to today
        return ranges
    }
}
