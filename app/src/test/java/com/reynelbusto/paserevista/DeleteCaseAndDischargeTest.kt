package com.reynelbusto.paserevista

import com.reynelbusto.paserevista.domain.model.ClinicalResult
import com.reynelbusto.paserevista.domain.model.CustomField
import com.reynelbusto.paserevista.domain.model.DailyRecord
import com.reynelbusto.paserevista.domain.model.Device
import com.reynelbusto.paserevista.domain.model.DeviceKind
import com.reynelbusto.paserevista.domain.model.Patient
import com.reynelbusto.paserevista.domain.model.ResultType
import com.reynelbusto.paserevista.domain.model.Treatment
import com.reynelbusto.paserevista.domain.model.PatientState
import com.reynelbusto.paserevista.domain.model.Pending
import com.reynelbusto.paserevista.domain.model.PendingType
import com.reynelbusto.paserevista.domain.model.Procedure
import com.reynelbusto.paserevista.domain.model.ProcedureState
import com.reynelbusto.paserevista.domain.repository.CaseCardRepository
import com.reynelbusto.paserevista.domain.repository.ClinicalUnitOfWork
import com.reynelbusto.paserevista.domain.repository.CustomFieldRepository
import com.reynelbusto.paserevista.domain.repository.DailyRecordRepository
import com.reynelbusto.paserevista.domain.repository.DeviceRepository
import com.reynelbusto.paserevista.domain.repository.PatientRepository
import com.reynelbusto.paserevista.domain.repository.PendingRepository
import com.reynelbusto.paserevista.domain.repository.ProcedureRepository
import com.reynelbusto.paserevista.domain.repository.ResultRepository
import com.reynelbusto.paserevista.domain.repository.TreatmentRepository
import com.reynelbusto.paserevista.domain.usecase.DeleteCaseUseCase
import com.reynelbusto.paserevista.presentation.camas.isListedPatient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class DelUow : ClinicalUnitOfWork {
    override suspend fun <T> atomic(block: suspend () -> T): T = block()
}

private fun delPatient(id: String, status: PatientState = PatientState.ACTIVE) = Patient(
    id = id, fullName = null, birthDate = null, sex = null, hcNumber = null,
    serviceId = "urologia", admissionDate = "2026-10-06", mainDiagnosis = null,
    status = status, createdAt = 1L, updatedAt = 1L,
)

private open class DelPatients(val store: MutableMap<String, Patient>) : PatientRepository {
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
        store.values.toList()
    override suspend fun findByHc(serviceId: String, hcNumber: String): Patient? = null
    override suspend fun delete(id: String) {
        store.remove(id)
    }
}

private open class DelCards(val patientIds: MutableSet<String>) : CaseCardRepository {
    override fun observeByJourney(journeyId: String): Flow<List<com.reynelbusto.paserevista.domain.model.CaseCard>> =
        MutableStateFlow(emptyList())
    override suspend fun getByPatientAndJourney(patientId: String, journeyId: String) = null
    override suspend fun getById(id: String) = null
    override suspend fun findLatestBefore(patientId: String, clinicalDate: String) = null
    override suspend fun findLatestByPatient(patientId: String) = null
    override suspend fun create(card: com.reynelbusto.paserevista.domain.model.CaseCard): String = card.id
    override suspend fun update(card: com.reynelbusto.paserevista.domain.model.CaseCard) {}
    override suspend fun countAll(): Int = 0
    override suspend fun countDistinctBeds(): Int = 0
    override suspend fun deleteByPatient(patientId: String) {
        patientIds.remove(patientId)
    }
    override suspend fun findByJourneyAndBed(journeyId: String, bed: String) = null
    override suspend fun recentDiagnoses(limit: Int): List<String> = emptyList()
}

private open class DelFields(val byPatient: MutableMap<String, MutableList<CustomField>>) : CustomFieldRepository {
    override fun observeByPatient(patientId: String): Flow<List<CustomField>> =
        MutableStateFlow(byPatient[patientId].orEmpty())
    override suspend fun create(field: CustomField): String = field.id
    override suspend fun update(field: CustomField) {}
    override suspend fun delete(id: String) {}
    override suspend fun countByPatient(patientId: String): Int = byPatient[patientId]?.size ?: 0
    override suspend fun deleteByPatient(patientId: String) {
        byPatient.remove(patientId)
    }
    override suspend fun maxSortOrderByPatient(patientId: String): Int =
        (byPatient[patientId]?.maxOfOrNull { it.sortOrder } ?: -1)
}

private open class DelPendings(val byPatient: MutableMap<String, MutableList<Pending>>) : PendingRepository {
    override fun observeOpenByPatient(patientId: String): Flow<List<Pending>> =
        MutableStateFlow(byPatient[patientId].orEmpty())
    override fun observeOpenByService(serviceId: String): Flow<List<Pending>> =
        MutableStateFlow(emptyList())
    override fun observeRecentlyClosedByService(serviceId: String): Flow<List<Pending>> =
        MutableStateFlow(emptyList())
    override suspend fun getOpenByPatient(patientId: String): List<Pending> =
        byPatient[patientId].orEmpty()
    override suspend fun getPending(id: String): Pending? = null
    override suspend fun create(pending: Pending, idempotencyKey: String?): String = pending.id
    override suspend fun update(pending: Pending) {}
    override suspend fun deleteByPatient(patientId: String) {
        byPatient.remove(patientId)
    }
    override suspend fun findByIdempotencyKey(key: String): Pending? = null
}

private open class DelProcedures(val byPatient: MutableMap<String, MutableList<Procedure>>) : ProcedureRepository {
    override fun observeByJourney(journeyId: String): Flow<List<Procedure>> = MutableStateFlow(emptyList())
    override fun observeActiveAll(): Flow<List<Procedure>> = MutableStateFlow(emptyList())
    override fun observeRecentPerformed(limit: Int): Flow<List<Procedure>> = MutableStateFlow(emptyList())
    override suspend fun getProcedure(id: String): Procedure? = null
    override suspend fun findActiveByPatient(patientId: String): Procedure? = null
    override suspend fun create(procedure: Procedure): String = procedure.id
    override suspend fun update(procedure: Procedure) {}
    override suspend fun deleteByPatient(patientId: String) {
        byPatient.remove(patientId)
    }
}

/** Fake que registra el orden de los borrados (A2: hijos antes que el paciente). */
private class OrderLog {
    val calls = mutableListOf<String>()
    fun log(name: String) { calls += name }
}

private class DelRecords(val log: OrderLog) : DailyRecordRepository {
    override fun observeByJourney(journeyId: String): Flow<List<DailyRecord>> =
        MutableStateFlow(emptyList())
    override suspend fun getByPatientAndJourney(patientId: String, journeyId: String): DailyRecord? = null
    override suspend fun create(record: DailyRecord): String = record.id
    override suspend fun update(record: DailyRecord) {}
    override suspend fun createAll(records: List<DailyRecord>) {}
    override suspend fun findLatestBefore(patientId: String, clinicalDate: String): DailyRecord? = null
    override fun observeByPatient(patientId: String): Flow<List<DailyRecord>> =
        MutableStateFlow(emptyList())
    override suspend fun deleteByPatient(patientId: String) { log.log("records") }
}

private class DelTreatments(val log: OrderLog) : TreatmentRepository {
    override fun observeActiveByPatient(patientId: String): Flow<List<Treatment>> =
        MutableStateFlow(emptyList())
    override suspend fun getTreatment(id: String): Treatment? = null
    override suspend fun create(treatment: Treatment): String = treatment.id
    override suspend fun update(treatment: Treatment) {}
    override fun observeEvents(treatmentId: String) =
        MutableStateFlow(emptyList<com.reynelbusto.paserevista.domain.model.TreatmentEvent>())
    override suspend fun addEvent(event: com.reynelbusto.paserevista.domain.model.TreatmentEvent) {}
    override suspend fun deleteByPatient(patientId: String) { log.log("treatments") }
}

private class DelDevices(val log: OrderLog) : DeviceRepository {
    override fun observeActiveByPatient(patientId: String): Flow<List<Device>> =
        MutableStateFlow(emptyList())
    override suspend fun getDevice(id: String): Device? = null
    override suspend fun create(device: Device): String = device.id
    override suspend fun update(device: Device) {}
    override suspend fun deleteByPatient(patientId: String) { log.log("devices") }
}

private class DelResults(val log: OrderLog) : ResultRepository {
    override fun observeByPatient(patientId: String): Flow<List<ClinicalResult>> =
        MutableStateFlow(emptyList())
    override suspend fun create(result: ClinicalResult): String = result.id
    override suspend fun deleteByPatient(patientId: String) { log.log("results") }
}

/**
 * BUG 2: el alta debe sacar la tarjeta del listado de Camas.
 * FEATURE: borrado total del caso ("se cargó por error").
 */
class DeleteCaseAndDischargeTest {

    @Test
    fun `isListedPatient solo deja pasar activos`() {
        assertTrue(isListedPatient(delPatient("a", PatientState.ACTIVE)))
        assertFalse(isListedPatient(delPatient("b", PatientState.DISCHARGED)))
        assertFalse(isListedPatient(delPatient("c", PatientState.TRANSFERRED)))
    }

    @Test
    fun `borrar caso elimina tarjeta, paciente, apartados, pendientes y procedimientos`() = runTest {
        val patients = DelPatients(mutableMapOf("p1" to delPatient("p1"), "p2" to delPatient("p2")))
        val cards = DelCards(mutableSetOf("p1", "p2"))
        val fields = DelFields(mutableMapOf(
            "p1" to mutableListOf(CustomField("f1", "p1", "Alergias", "Penicilina", 0, 1L, 1L)),
        ))
        val pendings = DelPendings(mutableMapOf(
            "p1" to mutableListOf(Pending("pe1", "p1", "Cultivo", requestedAt = 1L, createdAt = 1L, updatedAt = 1L)),
        ))
        val procedures = DelProcedures(mutableMapOf(
            "p1" to mutableListOf(Procedure("pr1", "p1", "RTU", status = ProcedureState.PENDING, createdAt = 1L, updatedAt = 1L)),
        ))
        val log = OrderLog()
        val useCase = DeleteCaseUseCase(
            DelUow(), patients, cards, fields, pendings, procedures,
            DelRecords(log), DelTreatments(log), DelDevices(log), DelResults(log),
        )

        useCase.invoke("p1")

        assertNull(patients.getPatient("p1"))
        assertFalse(cards.patientIds.contains("p1"))
        assertTrue(fields.byPatient["p1"].isNullOrEmpty())
        assertTrue(pendings.byPatient["p1"].isNullOrEmpty())
        assertTrue(procedures.byPatient["p1"].isNullOrEmpty())
        // El otro caso queda intacto.
        assertEquals("p2", patients.getPatient("p2")?.id)
        assertTrue(cards.patientIds.contains("p2"))
    }

    @Test
    fun `A2 borrar caso elimina hijos antes que el paciente (orden FK-seguro)`() = runTest {
        val log = OrderLog()
        val patients = object : DelPatients(mutableMapOf("p1" to delPatient("p1"))) {
            override suspend fun delete(id: String) {
                log.log("patient")
                super.delete(id)
            }
        }
        val cards = object : DelCards(mutableSetOf("p1")) {
            override suspend fun deleteByPatient(patientId: String) {
                log.log("cards")
                super.deleteByPatient(patientId)
            }
        }
        val fields = object : DelFields(mutableMapOf()) {
            override suspend fun deleteByPatient(patientId: String) {
                log.log("fields")
                super.deleteByPatient(patientId)
            }
        }
        val pendings = object : DelPendings(mutableMapOf()) {
            override suspend fun deleteByPatient(patientId: String) {
                log.log("pendings")
                super.deleteByPatient(patientId)
            }
        }
        val procedures = object : DelProcedures(mutableMapOf()) {
            override suspend fun deleteByPatient(patientId: String) {
                log.log("procedures")
                super.deleteByPatient(patientId)
            }
        }
        val useCase = DeleteCaseUseCase(
            DelUow(), patients, cards, fields, pendings, procedures,
            DelRecords(log), DelTreatments(log), DelDevices(log), DelResults(log),
        )

        useCase.invoke("p1")

        val calls = log.calls
        // Todos los hijos (incl. los nuevos: records, treatments, devices, results)
        // se borran antes que el paciente.
        assertTrue(calls.contains("records"))
        assertTrue(calls.contains("treatments"))
        assertTrue(calls.contains("devices"))
        assertTrue(calls.contains("results"))
        assertTrue(calls.contains("procedures"))
        assertTrue(calls.contains("pendings"))
        assertTrue(calls.contains("fields"))
        assertTrue(calls.contains("cards"))
        assertEquals("patient", calls.last())
    }
}
