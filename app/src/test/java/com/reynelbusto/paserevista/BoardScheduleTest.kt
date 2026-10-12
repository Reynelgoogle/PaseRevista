package com.reynelbusto.paserevista

import com.reynelbusto.paserevista.core.Clock
import com.reynelbusto.paserevista.domain.model.CaseCard
import com.reynelbusto.paserevista.domain.model.ClinicalPriority
import com.reynelbusto.paserevista.domain.model.Patient
import com.reynelbusto.paserevista.domain.model.Procedure
import com.reynelbusto.paserevista.domain.model.ProcedureState
import com.reynelbusto.paserevista.domain.repository.CaseCardRepository
import com.reynelbusto.paserevista.domain.repository.CaseHistoryRepository
import com.reynelbusto.paserevista.domain.repository.ClinicalUnitOfWork
import com.reynelbusto.paserevista.domain.repository.PatientRepository
import com.reynelbusto.paserevista.domain.repository.ProcedureRepository
import com.reynelbusto.paserevista.domain.usecase.BoardUseCase
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private class SchedClock : Clock {
    override fun nowMillis(): Long = 42L
    override fun todayIso(zone: ZoneId): String = "2026-10-12"
}

private class SchedUow : ClinicalUnitOfWork {
    override suspend fun <T> atomic(block: suspend () -> T): T = block()
}

private class SchedProcedures : ProcedureRepository {
    val store = mutableMapOf<String, Procedure>()
    override fun observeByJourney(journeyId: String): Flow<List<Procedure>> = MutableStateFlow(emptyList())
    override fun observeActiveAll(): Flow<List<Procedure>> = MutableStateFlow(emptyList())
    override fun observeRecentPerformed(limit: Int): Flow<List<Procedure>> = MutableStateFlow(emptyList())
    override suspend fun getProcedure(id: String): Procedure? = store[id]
    override suspend fun findActiveByPatient(patientId: String): Procedure? = null
    override suspend fun findPerformedByPatientInJourney(patientId: String, journeyId: String): Procedure? = null
    override suspend fun create(procedure: Procedure): String {
        store[procedure.id] = procedure
        return procedure.id
    }
    override suspend fun update(procedure: Procedure) {
        store[procedure.id] = procedure
    }
    override suspend fun deleteByPatient(patientId: String) {}
}

private class SchedHistory : CaseHistoryRepository {
    val entries = mutableListOf<String>()
    override fun observeByPatient(patientId: String): Flow<List<com.reynelbusto.paserevista.domain.model.CaseHistoryEntry>> =
        MutableStateFlow(emptyList())
    override suspend fun getByJourney(journeyId: String): List<com.reynelbusto.paserevista.domain.model.CaseHistoryEntry> =
        emptyList()
    override suspend fun log(patientId: String, journeyId: String?, summary: String) {
        entries += summary
    }
}

private object SchedPatients : PatientRepository {
    override fun observeActivePatients(serviceId: String): Flow<List<Patient>> = MutableStateFlow(emptyList())
    override fun searchPatients(serviceId: String, query: String): Flow<List<Patient>> = MutableStateFlow(emptyList())
    override suspend fun getPatient(id: String): Patient? = null
    override suspend fun createPatient(patient: Patient): String = patient.id
    override suspend fun updatePatient(patient: Patient) {}
    override suspend fun getActivePatients(serviceId: String): List<Patient> = emptyList()
    override suspend fun findByHc(serviceId: String, hcNumber: String): Patient? = null
    override suspend fun delete(id: String) {}
}

private object SchedCards : CaseCardRepository {
    override fun observeByJourney(journeyId: String): Flow<List<CaseCard>> = MutableStateFlow(emptyList())
    override suspend fun getByPatientAndJourney(patientId: String, journeyId: String): CaseCard? = null
    override suspend fun getById(id: String): CaseCard? = null
    override suspend fun findLatestBefore(patientId: String, clinicalDate: String): CaseCard? = null
    override suspend fun findLatestByPatient(patientId: String): CaseCard? = null
    override suspend fun findByJourneyAndBed(journeyId: String, bed: String): CaseCard? = null
    override suspend fun findAllByJourneyAndBed(journeyId: String, bed: String): List<CaseCard> = emptyList()
    override suspend fun create(card: CaseCard): String = card.id
    override suspend fun update(card: CaseCard) {}
    override suspend fun deleteByPatient(patientId: String) {}
    override suspend fun countAll(): Int = 0
    override suspend fun countDistinctBeds(): Int = 0
    override suspend fun recentDiagnoses(limit: Int): List<String> = emptyList()
}

/** Tests de agendar procedimiento desde el FAB de la pizarra. */
class BoardScheduleTest {
    private val procedures = SchedProcedures()
    private val history = SchedHistory()
    private val board = BoardUseCase(SchedUow(), procedures, SchedPatients, SchedCards, history, SchedClock())

    @Test fun `con fecha queda Programado`() = runTest {
        board.scheduleProcedure(
            patientId = "p1", kind = "Apendicectomía",
            scheduledDate = "2026-10-13", scheduledTime = "08:30",
            priority = ClinicalPriority.P2, note = "ayuno",
        )
        val created = procedures.store.values.single()
        assertEquals(ProcedureState.PREPARATION, created.status)
        assertEquals("2026-10-13", created.scheduledDate)
        assertEquals("08:30", created.scheduledTime)
        assertEquals(ClinicalPriority.P2, created.priority)
        assertEquals("ayuno", created.note)
        assertEquals("p1", created.patientId)
        assertTrue(created.id.isNotBlank())
        assertTrue(history.entries.any { it.contains("Apendicectomía") })
    }

    @Test fun `sin fecha queda Propuesto`() = runTest {
        board.scheduleProcedure(
            patientId = "p1", kind = "Sutura",
            scheduledDate = null, scheduledTime = null,
            priority = ClinicalPriority.P3, note = null,
        )
        val created = procedures.store.values.single()
        assertEquals(ProcedureState.PENDING, created.status)
        assertNull(created.scheduledDate)
    }

    @Test fun `fecha en blanco es sin fecha`() = runTest {
        board.scheduleProcedure(
            patientId = "p1", kind = "Sutura",
            scheduledDate = "  ", scheduledTime = null,
            priority = ClinicalPriority.P3, note = null,
        )
        val created = procedures.store.values.single()
        assertEquals(ProcedureState.PENDING, created.status)
        assertNull(created.scheduledDate)
    }

    @Test fun `procedimiento vacio no se crea`() = runTest {
        try {
            board.scheduleProcedure(
                patientId = "p1", kind = "   ",
                scheduledDate = null, scheduledTime = null,
                priority = ClinicalPriority.P3, note = null,
            )
            fail("debió lanzar")
        } catch (e: IllegalArgumentException) {
            // Esperado.
        }
        assertTrue(procedures.store.isEmpty())
    }

    @Test fun `recorta espacios del nombre`() = runTest {
        board.scheduleProcedure(
            patientId = "p1", kind = "  Limpieza  ",
            scheduledDate = null, scheduledTime = null,
            priority = ClinicalPriority.P3, note = null,
        )
        assertEquals("Limpieza", procedures.store.values.single().kind)
    }
}
