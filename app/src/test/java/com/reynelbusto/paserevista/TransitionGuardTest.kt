package com.reynelbusto.paserevista

import com.reynelbusto.paserevista.domain.TransitionGuard
import com.reynelbusto.paserevista.domain.model.DeviceState
import com.reynelbusto.paserevista.domain.model.JourneyState
import com.reynelbusto.paserevista.domain.model.PatientState
import com.reynelbusto.paserevista.domain.model.PendingState
import com.reynelbusto.paserevista.domain.model.ProcedureState
import com.reynelbusto.paserevista.domain.model.ReviewState
import com.reynelbusto.paserevista.domain.model.TreatmentState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las máquinas de estado del dominio. Regla crítica: abrir ≠ revisar.
 */
class TransitionGuardTest {

    @Test
    fun `dailyRecord pending puede pasar a in_progress al abrir`() {
        assertTrue(TransitionGuard.dailyRecord(ReviewState.PENDING, ReviewState.IN_PROGRESS))
    }

    @Test
    fun `dailyRecord pending NO puede pasar directo a completed`() {
        // Abrir un paciente no significa revisarlo.
        assertFalse(TransitionGuard.dailyRecord(ReviewState.PENDING, ReviewState.COMPLETED))
    }

    @Test
    fun `dailyRecord in_progress puede completarse`() {
        assertTrue(TransitionGuard.dailyRecord(ReviewState.IN_PROGRESS, ReviewState.COMPLETED))
    }

    @Test
    fun `dailyRecord completed no retrocede`() {
        assertFalse(TransitionGuard.dailyRecord(ReviewState.COMPLETED, ReviewState.IN_PROGRESS))
        assertFalse(TransitionGuard.dailyRecord(ReviewState.COMPLETED, ReviewState.PENDING))
    }

    @Test
    fun `pending puede completarse directo o cancelarse`() {
        assertTrue(TransitionGuard.pending(PendingState.PENDING, PendingState.COMPLETED))
        assertTrue(TransitionGuard.pending(PendingState.PENDING, PendingState.CANCELED))
        assertTrue(TransitionGuard.pending(PendingState.IN_PROGRESS, PendingState.COMPLETED))
    }

    @Test
    fun `pending completado no se reabre`() {
        assertFalse(TransitionGuard.pending(PendingState.COMPLETED, PendingState.PENDING))
        assertFalse(TransitionGuard.pending(PendingState.CANCELED, PendingState.IN_PROGRESS))
    }

    @Test
    fun `treatment soporta suspension y reinicio`() {
        assertTrue(TransitionGuard.treatment(TreatmentState.ACTIVE, TreatmentState.SUSPENDED))
        assertTrue(TransitionGuard.treatment(TreatmentState.SUSPENDED, TreatmentState.ACTIVE))
        assertTrue(TransitionGuard.treatment(TreatmentState.ACTIVE, TreatmentState.FINALIZED))
    }

    @Test
    fun `treatment finalizado no se reabre`() {
        assertFalse(TransitionGuard.treatment(TreatmentState.FINALIZED, TreatmentState.ACTIVE))
    }

    @Test
    fun `device retirado no vuelve a activo`() {
        assertTrue(TransitionGuard.device(DeviceState.ACTIVE, DeviceState.REMOVED))
        assertFalse(TransitionGuard.device(DeviceState.REMOVED, DeviceState.ACTIVE))
    }

    @Test
    fun `journey es append-only`() {
        assertTrue(TransitionGuard.journey(JourneyState.FUTURE, JourneyState.CURRENT))
        assertTrue(TransitionGuard.journey(JourneyState.CURRENT, JourneyState.COMPLETED))
        assertFalse(TransitionGuard.journey(JourneyState.COMPLETED, JourneyState.CURRENT))
    }

    @Test
    fun `patient dado de alta no se reactiva como el mismo episodio`() {
        assertTrue(TransitionGuard.patient(PatientState.ACTIVE, PatientState.DISCHARGED))
        assertFalse(TransitionGuard.patient(PatientState.DISCHARGED, PatientState.ACTIVE))
    }

    @Test
    fun `procedure sigue pending-preparation-performed`() {
        assertTrue(TransitionGuard.procedure(ProcedureState.PENDING, ProcedureState.PREPARATION))
        assertTrue(TransitionGuard.procedure(ProcedureState.PREPARATION, ProcedureState.PERFORMED))
        assertFalse(TransitionGuard.procedure(ProcedureState.PERFORMED, ProcedureState.PENDING))
    }
}
