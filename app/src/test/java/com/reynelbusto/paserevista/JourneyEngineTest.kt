package com.reynelbusto.paserevista

import com.reynelbusto.paserevista.core.Clock
import com.reynelbusto.paserevista.core.IsoDate
import com.reynelbusto.paserevista.core.newId
import com.reynelbusto.paserevista.domain.model.ClinicalEvolution
import com.reynelbusto.paserevista.domain.model.DailyRecord
import com.reynelbusto.paserevista.domain.model.Journey
import com.reynelbusto.paserevista.domain.model.JourneyOrigin
import com.reynelbusto.paserevista.domain.model.JourneyState
import com.reynelbusto.paserevista.domain.model.Patient
import com.reynelbusto.paserevista.domain.model.PatientState
import com.reynelbusto.paserevista.domain.model.Pending
import com.reynelbusto.paserevista.domain.model.ReviewState
import com.reynelbusto.paserevista.domain.model.Sex
import com.reynelbusto.paserevista.domain.repository.ClinicalUnitOfWork
import com.reynelbusto.paserevista.domain.repository.DailyRecordRepository
import com.reynelbusto.paserevista.domain.repository.JourneyRepository
import com.reynelbusto.paserevista.domain.repository.PatientRepository
import com.reynelbusto.paserevista.domain.repository.PendingRepository
import com.reynelbusto.paserevista.domain.usecase.DischargeUseCase
import com.reynelbusto.paserevista.domain.usecase.NewPatientInput
import com.reynelbusto.paserevista.domain.usecase.AdmitPatientUseCase
import com.reynelbusto.paserevista.domain.usecase.ReviewUseCase
import com.reynelbusto.paserevista.domain.usecase.StartJourneyUseCase
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Reloj falso con fecha fija. */
class FakeClock(var today: IsoDate = "2026-10-05") : Clock {
    var now: Long = 1_000_000L
    override fun nowMillis(): Long = now++
    override fun todayIso(zone: ZoneId): IsoDate = today
}

class FakeUnitOfWork : ClinicalUnitOfWork {
    override suspend fun <T> atomic(block: suspend () -> T): T = block()
}

open class FakePatientRepo(val clock: Clock) : PatientRepository {
    val store = mutableMapOf<String, Patient>()
    override fun observeActivePatients(serviceId: String): Flow<List<Patient>> =
        flowOf(store.values.filter { it.serviceId == serviceId && it.status == PatientState.ACTIVE })
    override fun searchPatients(serviceId: String, query: String): Flow<List<Patient>> = flowOf(emptyList())
    override suspend fun getPatient(id: String): Patient? = store[id]
    override suspend fun createPatient(patient: Patient): String { store[patient.id] = patient; return patient.id }
    override suspend fun updatePatient(patient: Patient) { store[patient.id] = patient }
    override suspend fun getActivePatients(serviceId: String): List<Patient> =
        store.values.filter { it.serviceId == serviceId && it.status == PatientState.ACTIVE }
    override suspend fun findByHc(serviceId: String, hcNumber: String): Patient? =
        store.values.firstOrNull { it.serviceId == serviceId && it.hcNumber == hcNumber.trim() }
}

open class FakeJourneyRepo(val clock: Clock) : JourneyRepository {
    val store = mutableMapOf<String, Journey>()
    override fun observeCurrentJourney(serviceId: String, clinicalDate: String): Flow<Journey?> =
        flowOf(store.values.firstOrNull {
            it.serviceId == serviceId && it.clinicalDate == clinicalDate && it.status == JourneyState.CURRENT
        })
    override suspend fun getJourney(serviceId: String, clinicalDate: String): Journey? =
        store.values.firstOrNull { it.serviceId == serviceId && it.clinicalDate == clinicalDate }
    override suspend fun getOrCreateJourney(serviceId: String, clinicalDate: String, originCode: String): Journey {
        getJourney(serviceId, clinicalDate)?.let { return it }
        val j = Journey(
            id = newId(), clinicalDate = clinicalDate, serviceId = serviceId,
            status = JourneyState.CURRENT,
            origin = JourneyOrigin.entries.first { it.code == originCode },
            createdAt = clock.nowMillis(), updatedAt = clock.nowMillis(),
        )
        store[j.id] = j
        return j
    }
    override suspend fun findPreviousJourney(serviceId: String, clinicalDate: String): Journey? =
        store.values.filter { it.serviceId == serviceId && it.clinicalDate < clinicalDate }
            .maxByOrNull { it.clinicalDate }
    override fun observeJourneys(serviceId: String): Flow<List<Journey>> =
        flowOf(store.values.filter { it.serviceId == serviceId }.sortedByDescending { it.clinicalDate })
}

open class FakeRecordRepo : DailyRecordRepository {
    val store = mutableMapOf<String, DailyRecord>()
    override fun observeByJourney(journeyId: String): Flow<List<DailyRecord>> =
        flowOf(store.values.filter { it.journeyId == journeyId })
    override suspend fun getByPatientAndJourney(patientId: String, journeyId: String): DailyRecord? =
        store.values.firstOrNull { it.patientId == patientId && it.journeyId == journeyId }
    override suspend fun create(record: DailyRecord): String { store[record.id] = record; return record.id }
    override suspend fun update(record: DailyRecord) { store[record.id] = record }
    override suspend fun createAll(records: List<DailyRecord>) { records.forEach { store[it.id] = it } }
    override suspend fun findLatestBefore(patientId: String, clinicalDate: String): DailyRecord? = null
    override fun observeByPatient(patientId: String): Flow<List<DailyRecord>> =
        flowOf(store.values.filter { it.patientId == patientId })
}

open class FakePendingRepo : PendingRepository {
    val store = mutableMapOf<String, Pending>()
    private val flow = MutableStateFlow<List<Pending>>(emptyList())
    override fun observeOpenByPatient(patientId: String): Flow<List<Pending>> = flowOf(open(patientId))
    override fun observeOpenByService(serviceId: String): Flow<List<Pending>> = flowOf(emptyList())
    override fun observeRecentlyClosedByService(serviceId: String): Flow<List<Pending>> = flowOf(emptyList())
    override suspend fun getOpenByPatient(patientId: String): List<Pending> = open(patientId)
    override suspend fun getPending(id: String): Pending? = store[id]
    private fun open(patientId: String) = store.values.filter {
        it.patientId == patientId && (it.status == com.reynelbusto.paserevista.domain.model.PendingState.PENDING ||
            it.status == com.reynelbusto.paserevista.domain.model.PendingState.IN_PROGRESS)
    }
    override suspend fun create(pending: Pending): String { store[pending.id] = pending; return pending.id }
    override suspend fun update(pending: Pending) { store[pending.id] = pending }
}

fun samplePatient(serviceId: String = "uro", hc: String = "HC-1", name: String = "Juan Pérez"): Patient =
    Patient(
        id = newId(), fullName = name, birthDate = "1970-01-01", sex = Sex.M,
        hcNumber = hc, serviceId = serviceId, admissionDate = "2026-10-01",
        mainDiagnosis = "Litiasis renal", status = PatientState.ACTIVE,
        createdAt = 1L, updatedAt = 1L,
    )

/**
 * FASE 6 — Motor de jornadas: idempotencia, HERENCIA≠COPIA, revisión, altas.
 */
class JourneyEngineTest {
    private val clock = FakeClock("2026-10-05")
    private val uow = FakeUnitOfWork()

    private fun engine(
        patients: FakePatientRepo = FakePatientRepo(clock),
        journeys: FakeJourneyRepo = FakeJourneyRepo(clock),
        records: FakeRecordRepo = FakeRecordRepo(),
    ) = StartJourneyUseCase(uow, journeys, patients, records, clock) to Triple(patients, journeys, records)

    @Test
    fun `doble inicio crea una sola jornada y cero registros duplicados`() = runTest {
        val (useCase, t) = engine()
        val (patients, journeys, records) = t
        patients.createPatient(samplePatient())
        val j1 = useCase("uro")
        val j2 = useCase("uro")
        assertEquals(j1.id, j2.id)
        assertEquals(1, journeys.store.size)
        assertEquals(1, records.store.size)
    }

    @Test
    fun `la jornada nueva no copia datos clinicos (HERENCIA DISTINTO COPIA)`() = runTest {
        val (useCase, t) = engine()
        val (patients, journeys, records) = t
        val p = samplePatient()
        patients.createPatient(p)
        // Día 1: jornada con datos clínicos registrados.
        clock.today = "2026-10-04"
        val j1 = useCase("uro", "2026-10-04")
        val review = ReviewUseCase(records, clock)
        review.recordEvolution(p.id, j1.id, ClinicalEvolution.WORSENED)
        review.savePain(p.id, j1.id, 8)
        review.saveFever(p.id, j1.id, true)
        review.savePriority(p.id, j1.id, com.reynelbusto.paserevista.domain.model.ClinicalPriority.P1)
        review.markReviewed(p.id, j1.id)
        // Día 2: jornada nueva.
        clock.today = "2026-10-05"
        val j2 = useCase("uro", "2026-10-05")
        val r2 = records.getByPatientAndJourney(p.id, j2.id)!!
        assertNull("estado clínico debe nacer NULL", r2.clinicalState)
        assertNull("dolor debe nacer NULL", r2.painScale)
        assertNull("fiebre debe nacer NULL", r2.hasFever)
        assertNull("evolución debe nacer NULL", r2.evolutionTap)
        assertNull("prioridad debe nacer NULL", r2.priority)
        assertNull("observaciones deben nacer NULL", r2.observations)
        assertEquals(ReviewState.PENDING, r2.reviewState)
        assertEquals("el enlace a AYER debe existir", records.getByPatientAndJourney(p.id, j1.id)!!.id, r2.previousRecordId)
    }

    @Test
    fun `sin cambios completa con un tap y deja signos en NULL`() = runTest {
        val (useCase, t) = engine()
        val (_, _, records) = t
        val p = samplePatient()
        t.first.createPatient(p)
        val j = useCase("uro")
        val review = ReviewUseCase(records, clock)
        val done = review.recordEvolution(p.id, j.id, ClinicalEvolution.UNCHANGED)
        assertEquals(ReviewState.COMPLETED, done.reviewState)
        assertEquals(ClinicalEvolution.UNCHANGED, done.evolutionTap)
        assertNull(done.painScale)
        assertNull(done.hasFever)
        // Deshacer revierte a PENDING.
        val undone = review.undoUnchanged(p.id, j.id)
        assertEquals(ReviewState.PENDING, undone.reviewState)
        assertNull(undone.evolutionTap)
    }

    @Test
    fun `mejoria exige marcar como revisado explicito`() = runTest {
        val (useCase, t) = engine()
        val (_, _, records) = t
        val p = samplePatient()
        t.first.createPatient(p)
        val j = useCase("uro")
        val review = ReviewUseCase(records, clock)
        // El tap de mejoría NO completa: queda en curso.
        val mid = review.recordEvolution(p.id, j.id, ClinicalEvolution.IMPROVED)
        assertEquals(ReviewState.IN_PROGRESS, mid.reviewState)
        // Solo el "Marcar como revisado" explícito cierra.
        val done = review.markReviewed(p.id, j.id)
        assertEquals(ReviewState.COMPLETED, done.reviewState)
    }

    @Test
    fun `marcar revisado sin evolucion falla`() = runTest {
        val (useCase, t) = engine()
        val (_, _, records) = t
        val p = samplePatient()
        t.first.createPatient(p)
        val j = useCase("uro")
        val review = ReviewUseCase(records, clock)
        try {
            review.markReviewed(p.id, j.id)
            fail("debió fallar sin tap de evolución")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("evolución"))
        }
    }

    @Test
    fun `alta con P1 abierto se bloquea (caso D)`() = runTest {
        val patients = FakePatientRepo(clock)
        val pendings = FakePendingRepo()
        val discharge = DischargeUseCase(uow, patients, pendings, clock)
        val p = samplePatient()
        patients.createPatient(p)
        pendings.create(
            Pending(
                id = newId(), patientId = p.id, description = "Urocultivo",
                priority = com.reynelbusto.paserevista.domain.model.ClinicalPriority.P1,
                requestedAt = 1L, createdAt = 1L, updatedAt = 1L,
            ),
        )
        val check = discharge.check(p.id)
        assertTrue(check is DischargeUseCase.Check.BlockedByP1)
        try {
            discharge.discharge(p.id)
            fail("debió bloquearse por P1")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("P1"))
        }
        // Con force (decisión explícita) procede.
        val out = discharge.discharge(p.id, force = true, reason = "Egreso")
        assertEquals(PatientState.DISCHARGED, out.status)
    }

    @Test
    fun `nuevo ingreso crea paciente y registro de hoy sin duplicar HC`() = runTest {
        val patients = FakePatientRepo(clock)
        val journeys = FakeJourneyRepo(clock)
        val records = FakeRecordRepo()
        val admit = AdmitPatientUseCase(uow, patients, journeys, records, clock)
        val input = NewPatientInput(
            fullName = "Ana Gómez", birthDate = "1985-05-05", sex = Sex.F,
            hcNumber = "HC-9", bloodGroup = null, serviceId = "uro",
            admissionReason = "Cólico nefrítico", mainDiagnosis = "Litiasis ureteral",
        )
        val id = admit(input, bed = "12A")
        assertNotNull(patients.getPatient(id))
        val journey = journeys.getJourney("uro", "2026-10-05")!!
        val record = records.getByPatientAndJourney(id, journey.id)!!
        assertEquals("12A", record.bed)
        try {
            admit(input, bed = "13B")
            fail("debió rechazar HC duplicada")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("HC"))
        }
    }

    @Test
    fun `reabrir un revisado edita el mismo registro`() = runTest {
        val (useCase, t) = engine()
        val (_, _, records) = t
        val p = samplePatient()
        t.first.createPatient(p)
        val j = useCase("uro")
        val review = ReviewUseCase(records, clock)
        review.recordEvolution(p.id, j.id, ClinicalEvolution.UNCHANGED)
        val before = records.getByPatientAndJourney(p.id, j.id)!!
        val reopened = review.reopen(p.id, j.id)
        assertEquals(before.id, reopened.id)
        assertEquals(ReviewState.IN_PROGRESS, reopened.reviewState)
        assertEquals(1, records.store.size)
    }
}
