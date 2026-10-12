package com.reynelbusto.paserevista

import com.reynelbusto.paserevista.core.Clock
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private class ReschedClock : Clock {
    override fun nowMillis(): Long = 9L
    override fun todayIso(zone: ZoneId): String = "2026-10-11"
}

private class ReschedUow : ClinicalUnitOfWork {
    override suspend fun <T> atomic(block: suspend () -> T): T = block()
}

private class ReschedProcedures : ProcedureRepository {
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

private class ReschedHistory : CaseHistoryRepository {
    val entries = mutableListOf<String>()
    override fun observeByPatient(patientId: String): Flow<List<com.reynelbusto.paserevista.domain.model.CaseHistoryEntry>> =
        MutableStateFlow(emptyList())
    override suspend fun getByJourney(journeyId: String): List<com.reynelbusto.paserevista.domain.model.CaseHistoryEntry> =
        emptyList()
    override suspend fun log(patientId: String, journeyId: String?, summary: String) {
        entries += summary
    }
}

class BoardRescheduleTest {
    private val clock = ReschedClock()
    private val procedures = ReschedProcedures()
    private val history = ReschedHistory()
    private val board = BoardUseCase(
        ReschedUow(), procedures,
        object : PatientRepository {
            override fun observeActivePatients(serviceId: String): Flow<List<com.reynelbusto.paserevista.domain.model.Patient>> = MutableStateFlow(emptyList())
            override fun searchPatients(serviceId: String, query: String): Flow<List<com.reynelbusto.paserevista.domain.model.Patient>> = MutableStateFlow(emptyList())
            override suspend fun getPatient(id: String): com.reynelbusto.paserevista.domain.model.Patient? = null
            override suspend fun createPatient(patient: com.reynelbusto.paserevista.domain.model.Patient): String = patient.id
            override suspend fun updatePatient(patient: com.reynelbusto.paserevista.domain.model.Patient) {}
            override suspend fun getActivePatients(serviceId: String): List<com.reynelbusto.paserevista.domain.model.Patient> = emptyList()
            override suspend fun findByHc(serviceId: String, hcNumber: String): com.reynelbusto.paserevista.domain.model.Patient? = null
            override suspend fun delete(id: String) {}
        },
        object : CaseCardRepository {
            override fun observeByJourney(journeyId: String): Flow<List<com.reynelbusto.paserevista.domain.model.CaseCard>> = MutableStateFlow(emptyList())
            override suspend fun getByPatientAndJourney(patientId: String, journeyId: String): com.reynelbusto.paserevista.domain.model.CaseCard? = null
            override suspend fun getById(id: String): com.reynelbusto.paserevista.domain.model.CaseCard? = null
            override suspend fun findLatestBefore(patientId: String, clinicalDate: String): com.reynelbusto.paserevista.domain.model.CaseCard? = null
            override suspend fun findLatestByPatient(patientId: String): com.reynelbusto.paserevista.domain.model.CaseCard? = null
            override suspend fun findByJourneyAndBed(journeyId: String, bed: String): com.reynelbusto.paserevista.domain.model.CaseCard? = null
            override suspend fun findAllByJourneyAndBed(journeyId: String, bed: String): List<com.reynelbusto.paserevista.domain.model.CaseCard> = emptyList()
            override suspend fun create(card: com.reynelbusto.paserevista.domain.model.CaseCard): String = card.id
            override suspend fun update(card: com.reynelbusto.paserevista.domain.model.CaseCard) {}
            override suspend fun deleteByPatient(patientId: String) {}
            override suspend fun countAll(): Int = 0
            override suspend fun countDistinctBeds(): Int = 0
            override suspend fun recentDiagnoses(limit: Int): List<String> = emptyList()
        },
        history, clock,
    )

    private fun proc(id: String) = Procedure(
        id = id, patientId = "p1", kind = "Colecistectomía",
        status = ProcedureState.PENDING, journeyId = "j1",
        createdAt = 1L, updatedAt = 1L,
    )

    @Test
    fun `reschedule fija la fecha y registra historial`() = runTest {
        procedures.store["x"] = proc("x")
        board.reschedule("x", "2026-10-15")
        val updated = procedures.store["x"]!!
        assertEquals("2026-10-15", updated.scheduledDate)
        assertEquals(9L, updated.updatedAt)
        assertTrue(history.entries.any { it.contains("2026-10-15") })
    }

    @Test
    fun `reschedule con fecha vacia la quita`() = runTest {
        procedures.store["x"] = proc("x").copy(scheduledDate = "2026-10-15")
        board.reschedule("x", "")
        assertNull(procedures.store["x"]!!.scheduledDate)
        assertTrue(history.entries.any { it.contains("sin fecha") })
    }

    @Test
    fun `reschedule de procedimiento inexistente falla`() = runTest {
        try {
            board.reschedule("nope", "2026-10-15")
            fail("debió fallar")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("no encontrado"))
        }
    }
}
