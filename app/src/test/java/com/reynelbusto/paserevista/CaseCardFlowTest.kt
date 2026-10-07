package com.reynelbusto.paserevista

import com.reynelbusto.paserevista.core.Clock
import com.reynelbusto.paserevista.domain.model.BoardColumn
import com.reynelbusto.paserevista.domain.model.CaseCard
import com.reynelbusto.paserevista.domain.model.CaseHistoryEntry
import com.reynelbusto.paserevista.domain.model.Journey
import com.reynelbusto.paserevista.domain.model.JourneyOrigin
import com.reynelbusto.paserevista.domain.model.Patient
import com.reynelbusto.paserevista.domain.model.Procedure
import com.reynelbusto.paserevista.domain.model.ProcedureState
import com.reynelbusto.paserevista.domain.repository.CaseCardRepository
import com.reynelbusto.paserevista.domain.repository.CaseHistoryRepository
import com.reynelbusto.paserevista.domain.repository.ClinicalUnitOfWork
import com.reynelbusto.paserevista.domain.repository.JourneyRepository
import com.reynelbusto.paserevista.domain.repository.PatientRepository
import com.reynelbusto.paserevista.domain.repository.ProcedureRepository
import com.reynelbusto.paserevista.domain.usecase.AddBedUseCase
import com.reynelbusto.paserevista.domain.usecase.BoardUseCase
import com.reynelbusto.paserevista.domain.usecase.CaseCardUseCase
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeClock : Clock {
    override fun nowMillis(): Long = 1_700_000_000_000L
    override fun todayIso(zone: ZoneId): String = "2026-10-06"
}

private class FakeUnitOfWork : ClinicalUnitOfWork {
    override suspend fun <T> atomic(block: suspend () -> T): T = block()
}

private class FakePatients : PatientRepository {
    val store = mutableMapOf<String, Patient>()
    override fun observeActivePatients(serviceId: String): Flow<List<Patient>> =
        MutableStateFlow(store.values.toList())

    override fun searchPatients(serviceId: String, query: String): Flow<List<Patient>> =
        MutableStateFlow(emptyList())

    override suspend fun getPatient(id: String): Patient? = store[id]
    override suspend fun createPatient(patient: Patient): String {
        store[patient.id] = patient
        return patient.id
    }

    override suspend fun updatePatient(patient: Patient) {
        store[patient.id] = patient
    }

    override suspend fun getActivePatients(serviceId: String): List<Patient> =
        store.values.filter { it.serviceId == serviceId }

    override suspend fun findByHc(serviceId: String, hcNumber: String): Patient? =
        store.values.firstOrNull { it.hcNumber == hcNumber }

    override suspend fun delete(id: String) {
        store.remove(id)
    }
}

private class FakeJourneys(private val clock: Clock) : JourneyRepository {
    val store = mutableMapOf<String, Journey>()
    override fun observeCurrentJourney(serviceId: String, clinicalDate: String): Flow<Journey?> =
        MutableStateFlow(store.values.firstOrNull())

    override suspend fun getJourney(serviceId: String, clinicalDate: String): Journey? =
        store.values.firstOrNull { it.clinicalDate == clinicalDate }

    override suspend fun getOrCreateJourney(
        serviceId: String,
        clinicalDate: String,
        originCode: String,
    ): Journey = getJourney(serviceId, clinicalDate) ?: Journey(
        id = "j-$clinicalDate", clinicalDate = clinicalDate, serviceId = serviceId,
        origin = JourneyOrigin.entries.first { it.code == originCode },
        createdAt = clock.nowMillis(), updatedAt = clock.nowMillis(),
    ).also { store[it.id] = it }

    override suspend fun findPreviousJourney(serviceId: String, clinicalDate: String): Journey? = null
    override fun observeJourneys(serviceId: String): Flow<List<Journey>> =
        MutableStateFlow(store.values.toList())
}

private class FakeCards : CaseCardRepository {
    val store = mutableMapOf<String, CaseCard>()
    override fun observeByJourney(journeyId: String): Flow<List<CaseCard>> =
        MutableStateFlow(store.values.filter { it.journeyId == journeyId })

    override suspend fun getByPatientAndJourney(patientId: String, journeyId: String): CaseCard? =
        store.values.firstOrNull { it.patientId == patientId && it.journeyId == journeyId }

    override suspend fun getById(id: String): CaseCard? = store[id]
    override suspend fun findLatestBefore(patientId: String, clinicalDate: String): CaseCard? = null
    override suspend fun findLatestByPatient(patientId: String): CaseCard? =
        store.values.firstOrNull { it.patientId == patientId }

    override suspend fun create(card: CaseCard): String {
        store[card.id] = card
        return card.id
    }

    override suspend fun update(card: CaseCard) {
        store[card.id] = card
    }

    override suspend fun countAll(): Int = store.size

    override suspend fun countDistinctBeds(): Int =
        store.values.map { it.bed }.distinct().size

    override suspend fun deleteByPatient(patientId: String) {
        store.entries.removeIf { it.value.patientId == patientId }
    }

    override suspend fun recentDiagnoses(limit: Int): List<String> =
        store.values.mapNotNull { it.diagnosis?.ifBlank { null } }.distinct().take(limit)
    override suspend fun findByJourneyAndBed(journeyId: String, bed: String): com.reynelbusto.paserevista.domain.model.CaseCard? = null
}

private class FakeHistory : CaseHistoryRepository {
    val entries = mutableListOf<CaseHistoryEntry>()
    override fun observeByPatient(patientId: String): Flow<List<CaseHistoryEntry>> =
        MutableStateFlow(entries.filter { it.patientId == patientId })

    override suspend fun log(patientId: String, journeyId: String?, summary: String) {
        entries += CaseHistoryEntry("h${entries.size}", patientId, journeyId, 0L, summary)
    }
}

private class FakeProcedures : ProcedureRepository {
    val store = mutableMapOf<String, Procedure>()
    override fun observeByJourney(journeyId: String): Flow<List<Procedure>> =
        MutableStateFlow(store.values.toList())

    override fun observeActiveAll(): Flow<List<Procedure>> =
        MutableStateFlow(store.values.filter { it.status != ProcedureState.PERFORMED })

    override fun observeRecentPerformed(limit: Int): Flow<List<Procedure>> =
        MutableStateFlow(emptyList())

    override suspend fun getProcedure(id: String): Procedure? = store[id]
    override suspend fun findActiveByPatient(patientId: String): Procedure? =
        store.values.firstOrNull {
            it.patientId == patientId &&
                (it.status == ProcedureState.PENDING || it.status == ProcedureState.PREPARATION)
        }

    override suspend fun create(procedure: Procedure): String {
        store[procedure.id] = procedure
        return procedure.id
    }

    override suspend fun update(procedure: Procedure) {
        store[procedure.id] = procedure
    }

    override suspend fun deleteByPatient(patientId: String) {
        store.entries.removeIf { it.value.patientId == patientId }
    }
}

class CaseCardFlowTest {
    private val clock = FakeClock()
    private val uow = FakeUnitOfWork()
    private val patients = FakePatients()
    private val journeys = FakeJourneys(clock)
    private val cards = FakeCards()
    private val history = FakeHistory()
    private val procedures = FakeProcedures()

    private val board = BoardUseCase(uow, procedures, patients, cards, history, clock)
    private val addBed = AddBedUseCase(uow, patients, journeys, cards, history, clock)
    private val caseCards = CaseCardUseCase(uow, cards, patients, history, board, clock)

    @Test
    fun `anadir cama solo pide el numero, sin obligatorios`() = runTest {
        val patientId = addBed("3")
        val patient = patients.getPatient(patientId)!!
        // REGLA DE ORO: todo personal null.
        assertNull(patient.fullName)
        assertNull(patient.hcNumber)
        assertNull(patient.mainDiagnosis)
        val card = cards.getByPatientAndJourney(patientId, "j-2026-10-06")!!
        assertEquals("3", card.bed)
        assertEquals(false, card.ready)
        assertTrue(history.entries.any { it.summary.contains("Cama 3") })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cama vacia falla`() = runTest {
        addBed("   ")
    }

    @Test
    fun `listo sube a pizarra y pendiente la baja`() = runTest {
        val patientId = addBed("7")
        val card = cards.getByPatientAndJourney(patientId, "j-2026-10-06")!!

        val nowReady = caseCards.toggleReady(card.id)
        assertTrue(nowReady)
        val proc = procedures.findActiveByPatient(patientId)!!
        assertEquals(ProcedureState.PENDING, proc.status)
        assertEquals(BoardColumn.PROPOSED, board.columnOf(proc))

        val backToPending = caseCards.toggleReady(card.id)
        assertEquals(false, backToPending)
        assertNull(procedures.findActiveByPatient(patientId))
        assertEquals(
            ProcedureState.CANCELED,
            procedures.store.values.first { it.patientId == patientId }.status,
        )
    }
}
