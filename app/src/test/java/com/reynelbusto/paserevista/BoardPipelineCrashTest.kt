package com.reynelbusto.paserevista

import com.reynelbusto.paserevista.core.Clock
import com.reynelbusto.paserevista.domain.model.BoardColumn
import com.reynelbusto.paserevista.domain.model.CaseCard
import com.reynelbusto.paserevista.domain.model.Journey
import com.reynelbusto.paserevista.domain.model.JourneyOrigin
import com.reynelbusto.paserevista.domain.model.Patient
import com.reynelbusto.paserevista.domain.model.PatientState
import com.reynelbusto.paserevista.domain.model.Procedure
import com.reynelbusto.paserevista.domain.model.ProcedureState
import com.reynelbusto.paserevista.domain.repository.CaseCardRepository
import com.reynelbusto.paserevista.domain.repository.CaseHistoryRepository
import com.reynelbusto.paserevista.domain.repository.ClinicalUnitOfWork
import com.reynelbusto.paserevista.domain.repository.PatientRepository
import com.reynelbusto.paserevista.domain.repository.ProcedureRepository
import com.reynelbusto.paserevista.domain.usecase.BoardUseCase
import com.reynelbusto.paserevista.presentation.board.BoardViewModel
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regresión del bug crítico: al marcar Listo, la pizarra quedaba en blanco
 * y la app se cerraba. Causa: una excepción dentro del `collect` del
 * BoardViewModel (columnOf con first{} ante un estado no mapeado, o un
 * enrich que falla) no la atrapaba el `.catch` y mataba el scope.
 */
private class CrashClock : Clock {
    override fun nowMillis(): Long = 1_700_000_000_000L
    override fun todayIso(zone: ZoneId): String = "2026-10-06"
}

private class CrashUow : ClinicalUnitOfWork {
    override suspend fun <T> atomic(block: suspend () -> T): T = block()
}

private fun testPatient(id: String) = Patient(
    id = id, fullName = null, birthDate = null, sex = null, hcNumber = null,
    serviceId = "urologia", admissionDate = "2026-10-06", mainDiagnosis = null,
    status = PatientState.ACTIVE, createdAt = 1L, updatedAt = 1L,
)

private fun testProcedure(id: String, patientId: String, status: ProcedureState) = Procedure(
    id = id, patientId = patientId, kind = "Nefrolitotomía",
    status = status, createdAt = 1L, updatedAt = 1L,
)

private open class CrashProcedures(val flow: MutableStateFlow<List<Procedure>>) : ProcedureRepository {
    override fun observeByJourney(journeyId: String): Flow<List<Procedure>> = flow
    override fun observeActiveAll(): Flow<List<Procedure>> = flow
    override fun observeRecentPerformed(limit: Int): Flow<List<Procedure>> = MutableStateFlow(emptyList())
    override suspend fun getProcedure(id: String): Procedure? = null
    override suspend fun findActiveByPatient(patientId: String): Procedure? = null
    override suspend fun create(procedure: Procedure): String = procedure.id
    override suspend fun update(procedure: Procedure) {}
    override suspend fun deleteByPatient(patientId: String) {}
}

private class CrashPatients(
    private val store: Map<String, Patient>,
    private val throwOn: Set<String> = emptySet(),
) : PatientRepository {
    override fun observeActivePatients(serviceId: String): Flow<List<Patient>> =
        MutableStateFlow(store.values.toList())
    override fun searchPatients(serviceId: String, query: String): Flow<List<Patient>> =
        MutableStateFlow(emptyList())
    override suspend fun getPatient(id: String): Patient? {
        if (id in throwOn) error("fallo simulado de lectura del paciente")
        return store[id]
    }
    override suspend fun createPatient(patient: Patient): String = patient.id
    override suspend fun updatePatient(patient: Patient) {}
    override suspend fun getActivePatients(serviceId: String): List<Patient> =
        store.values.toList()
    override suspend fun findByHc(serviceId: String, hcNumber: String): Patient? = null
    override suspend fun delete(id: String) {}
}

private class CrashCards(private val bedByPatient: Map<String, String>) : CaseCardRepository {
    override fun observeByJourney(journeyId: String): Flow<List<CaseCard>> =
        MutableStateFlow(emptyList())
    override suspend fun getByPatientAndJourney(patientId: String, journeyId: String): CaseCard? = null
    override suspend fun getById(id: String): CaseCard? = null
    override suspend fun findLatestBefore(patientId: String, clinicalDate: String): CaseCard? = null
    override suspend fun findLatestByPatient(patientId: String): CaseCard? = null
    override suspend fun create(card: CaseCard): String = card.id
    override suspend fun update(card: CaseCard) {}
    override suspend fun countAll(): Int = 0
    override suspend fun countDistinctBeds(): Int = 0
    override suspend fun deleteByPatient(patientId: String) {}
    override suspend fun recentDiagnoses(limit: Int): List<String> = emptyList()
    override suspend fun findByJourneyAndBed(journeyId: String, bed: String): com.reynelbusto.paserevista.domain.model.CaseCard? = null
}

private class CrashHistory : CaseHistoryRepository {
    override fun observeByPatient(patientId: String): Flow<List<com.reynelbusto.paserevista.domain.model.CaseHistoryEntry>> =
        MutableStateFlow(emptyList())
    override suspend fun log(patientId: String, journeyId: String?, summary: String) {}
}

class BoardPipelineCrashTest {
    private val testDispatcher = StandardTestDispatcher()
    private val clock = CrashClock()
    private val uow = CrashUow()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun boardUseCase(
        procedures: List<Procedure>,
        patients: Map<String, Patient>,
        throwOnPatient: Set<String> = emptySet(),
    ): BoardUseCase {
        val flow = MutableStateFlow(procedures)
        return BoardUseCase(
            uow,
            CrashProcedures(flow),
            CrashPatients(patients, throwOnPatient),
            CrashCards(emptyMap()),
            CrashHistory(),
            clock,
        )
    }

    /** Ejecuta el pipeline REAL del BoardViewModel capturando excepciones no controladas. */
    private suspend fun runBoardPipeline(board: BoardUseCase): Pair<BoardViewModel, List<Throwable>> {
        val captured = mutableListOf<Throwable>()
        val prev = Thread.currentThread().uncaughtExceptionHandler
        Thread.currentThread().uncaughtExceptionHandler =
            Thread.UncaughtExceptionHandler { _, e -> captured.add(e) }
        val vm: BoardViewModel
        try {
            vm = BoardViewModel(board)
            testDispatcher.scheduler.advanceUntilIdle()
        } finally {
            Thread.currentThread().uncaughtExceptionHandler = prev
        }
        return vm to captured
    }

    @Test
    fun `columnOf es total y nunca lanza`() {
        val board = boardUseCase(emptyList(), emptyMap())
        assertEquals(BoardColumn.PROPOSED, board.columnOf(testProcedure("p1", "x", ProcedureState.PENDING)))
        assertEquals(BoardColumn.SCHEDULED, board.columnOf(testProcedure("p2", "x", ProcedureState.PREPARATION)))
        assertEquals(BoardColumn.DONE, board.columnOf(testProcedure("p3", "x", ProcedureState.PERFORMED)))
        // Estado no mapeado (CANCELED): null en vez de NoSuchElementException.
        assertNull(board.columnOf(testProcedure("p4", "x", ProcedureState.CANCELED)))
    }

    @Test
    fun `procedimiento con estado no mapeado no tumba la pizarra`() = runTest(testDispatcher) {
        val board = boardUseCase(
            procedures = listOf(
                testProcedure("ok", "pat-ok", ProcedureState.PENDING),
                testProcedure("bad", "pat-bad", ProcedureState.CANCELED),
            ),
            patients = mapOf("pat-ok" to testPatient("pat-ok"), "pat-bad" to testPatient("pat-bad")),
        )
        val (vm, captured) = runBoardPipeline(board)
        assertTrue("ninguna excepción debe escapar del pipeline, capturadas: $captured", captured.isEmpty())
        assertEquals(1, vm.uiState.value.proposed.size)
        assertEquals("ok", vm.uiState.value.proposed.first().procedure.id)
    }

    @Test
    fun `enrich que falla en un item no tumba la pizarra`() = runTest(testDispatcher) {
        val board = boardUseCase(
            procedures = listOf(
                testProcedure("ok", "pat-ok", ProcedureState.PENDING),
                testProcedure("roto", "pat-roto", ProcedureState.PENDING),
            ),
            patients = mapOf("pat-ok" to testPatient("pat-ok"), "pat-roto" to testPatient("pat-roto")),
            throwOnPatient = setOf("pat-roto"),
        )
        val (vm, captured) = runBoardPipeline(board)
        assertTrue("ninguna excepción debe escapar del pipeline, capturadas: $captured", captured.isEmpty())
        assertEquals(1, vm.uiState.value.proposed.size)
        assertEquals("ok", vm.uiState.value.proposed.first().procedure.id)
    }

    @Test
    fun `flujo del tablero que falla muestra error sin crashear`() = runTest(testDispatcher) {
        val failing = object : CrashProcedures(MutableStateFlow(emptyList())) {
            override fun observeActiveAll(): Flow<List<Procedure>> =
                kotlinx.coroutines.flow.flow { throw IllegalStateException("BD caída") }
        }
        val board = BoardUseCase(uow, failing, CrashPatients(emptyMap()), CrashCards(emptyMap()), CrashHistory(), clock)
        val (vm, captured) = runBoardPipeline(board)
        assertTrue("ninguna excepción debe escapar del pipeline, capturadas: $captured", captured.isEmpty())
        // M7/M8: el fallo de carga deja loadError (con Reintentar), no el error transitorio.
        assertTrue(vm.uiState.value.loadError != null)
    }

    @Test
    fun `flujo feliz - listo aparece en propuesto`() = runTest(testDispatcher) {
        val board = boardUseCase(
            procedures = listOf(testProcedure("p1", "pat-1", ProcedureState.PENDING)),
            patients = mapOf("pat-1" to testPatient("pat-1")),
        )
        val (vm, captured) = runBoardPipeline(board)
        assertTrue(captured.isEmpty())
        val s = vm.uiState.value
        assertEquals(false, s.isLoading)
        assertEquals(1, s.proposed.size)
        assertEquals("p1", s.proposed.first().procedure.id)
    }
}
