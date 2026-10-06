package com.reynelbusto.paserevista.domain

import com.reynelbusto.paserevista.domain.model.DailyRecord
import com.reynelbusto.paserevista.domain.model.DeviceState
import com.reynelbusto.paserevista.domain.model.JourneyState
import com.reynelbusto.paserevista.domain.model.PatientState
import com.reynelbusto.paserevista.domain.model.PendingState
import com.reynelbusto.paserevista.domain.model.ProcedureState
import com.reynelbusto.paserevista.domain.model.ReviewState
import com.reynelbusto.paserevista.domain.model.TreatmentState

/**
 * Componente único de verdad para transiciones de estado.
 * Las reglas clínicas de transición viven aquí, no en la UI ni en Room.
 */
object TransitionGuard {

    fun dailyRecord(from: ReviewState, to: ReviewState): Boolean = when (from) {
        ReviewState.PENDING -> to == ReviewState.IN_PROGRESS
        // PENDING → COMPLETED prohibido: abrir ≠ revisar.
        ReviewState.IN_PROGRESS -> to == ReviewState.COMPLETED || to == ReviewState.IN_PROGRESS
        ReviewState.COMPLETED -> false // ediciones aditivas no cambian el estado
    }

    fun pending(from: PendingState, to: PendingState): Boolean = when (from) {
        PendingState.PENDING -> to == PendingState.IN_PROGRESS ||
            to == PendingState.COMPLETED ||
            to == PendingState.CANCELED
        PendingState.IN_PROGRESS -> to == PendingState.COMPLETED ||
            to == PendingState.CANCELED
        PendingState.COMPLETED, PendingState.CANCELED -> false
    }

    fun treatment(from: TreatmentState, to: TreatmentState): Boolean = when (from) {
        TreatmentState.ACTIVE -> to == TreatmentState.SUSPENDED || to == TreatmentState.FINALIZED
        TreatmentState.SUSPENDED -> to == TreatmentState.ACTIVE || to == TreatmentState.FINALIZED
        TreatmentState.FINALIZED -> false
    }

    fun device(from: DeviceState, to: DeviceState): Boolean = when (from) {
        DeviceState.ACTIVE -> to == DeviceState.REMOVED
        DeviceState.REMOVED -> false // recolocar = nuevo Device
    }

    fun procedure(from: ProcedureState, to: ProcedureState): Boolean = when (from) {
        ProcedureState.PENDING -> to == ProcedureState.PREPARATION ||
            to == ProcedureState.PERFORMED ||
            to == ProcedureState.CANCELED
        ProcedureState.PREPARATION -> to == ProcedureState.PERFORMED ||
            to == ProcedureState.CANCELED
        ProcedureState.PERFORMED, ProcedureState.CANCELED -> false
    }

    fun journey(from: JourneyState, to: JourneyState): Boolean = when (from) {
        JourneyState.FUTURE -> to == JourneyState.CURRENT
        JourneyState.CURRENT -> to == JourneyState.COMPLETED
        JourneyState.COMPLETED -> false // append-only
    }

    fun patient(from: PatientState, to: PatientState): Boolean = when (from) {
        PatientState.ACTIVE -> to == PatientState.DISCHARGED || to == PatientState.TRANSFERRED
        PatientState.DISCHARGED -> false // reingreso = nuevo episodio, no transición
        PatientState.TRANSFERRED -> to == PatientState.ACTIVE || to == PatientState.DISCHARGED
    }

    /**
     * ¿Puede este DailyRecord marcarse como revisado? Exige el tap de evolución
     * explícito. "Sin cambios" completa con 1 tap (clínicos en NULL); los 3 deltas
     * requieren "Marcar como revisado" tras registrar el cambio.
     */
    fun canComplete(record: DailyRecord): Boolean = record.evolutionTap != null
}
