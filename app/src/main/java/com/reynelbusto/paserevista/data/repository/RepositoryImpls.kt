package com.reynelbusto.paserevista.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.reynelbusto.paserevista.core.Clock
import com.reynelbusto.paserevista.core.newId
import com.reynelbusto.paserevista.data.local.dao.CaseCardDao
import com.reynelbusto.paserevista.data.local.dao.CaseHistoryDao
import com.reynelbusto.paserevista.data.local.dao.CustomFieldDao
import com.reynelbusto.paserevista.data.local.dao.DailyRecordDao
import com.reynelbusto.paserevista.domain.model.CaseCard
import com.reynelbusto.paserevista.domain.model.CaseHistoryEntry
import com.reynelbusto.paserevista.domain.model.CustomField
import com.reynelbusto.paserevista.domain.repository.CaseCardRepository
import com.reynelbusto.paserevista.domain.repository.CaseHistoryRepository
import com.reynelbusto.paserevista.domain.repository.CustomFieldRepository
import com.reynelbusto.paserevista.data.local.dao.DeviceDao
import com.reynelbusto.paserevista.data.local.dao.JourneyDao
import com.reynelbusto.paserevista.data.local.dao.PatientDao
import com.reynelbusto.paserevista.data.local.dao.PendingDao
import com.reynelbusto.paserevista.data.local.dao.ProcedureDao
import com.reynelbusto.paserevista.data.local.dao.ResultDao
import com.reynelbusto.paserevista.data.local.dao.TreatmentDao
import com.reynelbusto.paserevista.data.local.dao.TreatmentEventDao
import com.reynelbusto.paserevista.data.local.entity.CaseHistoryEntity
import com.reynelbusto.paserevista.data.local.entity.PatientComorbidityEntity
import com.reynelbusto.paserevista.domain.model.ClinicalResult
import com.reynelbusto.paserevista.domain.model.DailyRecord
import com.reynelbusto.paserevista.domain.model.Device
import com.reynelbusto.paserevista.domain.model.Journey
import com.reynelbusto.paserevista.domain.model.JourneyOrigin
import com.reynelbusto.paserevista.domain.model.JourneyState
import com.reynelbusto.paserevista.domain.model.Patient
import com.reynelbusto.paserevista.domain.model.Pending
import com.reynelbusto.paserevista.domain.model.Procedure
import com.reynelbusto.paserevista.domain.model.Treatment
import com.reynelbusto.paserevista.domain.model.TreatmentEvent
import com.reynelbusto.paserevista.domain.repository.DailyRecordRepository
import com.reynelbusto.paserevista.domain.repository.DeviceRepository
import com.reynelbusto.paserevista.domain.repository.JourneyRepository
import com.reynelbusto.paserevista.domain.repository.PatientRepository
import com.reynelbusto.paserevista.domain.repository.PendingRepository
import com.reynelbusto.paserevista.domain.repository.ProcedureRepository
import com.reynelbusto.paserevista.domain.repository.ResultRepository
import com.reynelbusto.paserevista.domain.repository.TreatmentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Implementaciones Room de los repositorios del dominio.
 * - Todo acceso a disco en Dispatchers.IO.
 * - `updated_at` lo toca el repositorio (touch centralizado); la UI y los
 *   casos de uso no lo manipulan.
 * - Sin reglas clínicas aquí: se aplican, no se definen.
 */

class PatientRepositoryImpl(
    private val dao: PatientDao,
    private val clock: Clock,
) : PatientRepository {
    override fun observeActivePatients(serviceId: String): Flow<List<Patient>> =
        dao.observeActiveByService(serviceId).map { list -> list.map { it.toDomain() } }

    override fun searchPatients(serviceId: String, query: String): Flow<List<Patient>> =
        dao.searchWithBed(serviceId, clock.todayIso(), query.trim())
            .map { list -> list.map { it.toDomain() } }

    override suspend fun getPatient(id: String): Patient? = withContext(Dispatchers.IO) {
        val entity = dao.getById(id) ?: return@withContext null
        entity.toDomain(dao.getComorbidities(id))
    }

    override suspend fun createPatient(patient: Patient): String = withContext(Dispatchers.IO) {
        dao.insert(patient.toEntity())
        dao.insertComorbidities(
            patient.comorbidities.map { PatientComorbidityEntity(patient.id, it) },
        )
        patient.id
    }

    override suspend fun updatePatient(patient: Patient) = withContext(Dispatchers.IO) {
        dao.update(patient.copy(updatedAt = clock.nowMillis()).toEntity())
    }

    override suspend fun getActivePatients(serviceId: String): List<Patient> =
        withContext(Dispatchers.IO) {
            dao.getActiveByService(serviceId).map { it.toDomain() }
        }

    override suspend fun findByHc(serviceId: String, hcNumber: String): Patient? =
        withContext(Dispatchers.IO) {
            val entity = dao.findByServiceAndHc(serviceId, hcNumber.trim()) ?: return@withContext null
            entity.toDomain(dao.getComorbidities(entity.id))
        }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dao.deleteById(id)
    }
}

class JourneyRepositoryImpl(
    private val dao: JourneyDao,
    private val clock: Clock,
) : JourneyRepository {
    override fun observeCurrentJourney(serviceId: String, clinicalDate: String): Flow<Journey?> =
        dao.observeCurrent(serviceId, clinicalDate).map { it?.toDomain() }

    override suspend fun getJourney(serviceId: String, clinicalDate: String): Journey? =
        withContext(Dispatchers.IO) {
            dao.findByServiceAndDate(serviceId, clinicalDate)?.toDomain()
        }

    override suspend fun getOrCreateJourney(
        serviceId: String,
        clinicalDate: String,
        originCode: String,
    ): Journey = withContext(Dispatchers.IO) {
        val now = clock.nowMillis()
        val candidate = Journey(
            id = newId(),
            clinicalDate = clinicalDate,
            serviceId = serviceId,
            status = JourneyState.CURRENT,
            origin = originCode.let { JourneyOrigin.entries.first { o -> o.code == it } },
            createdAt = now,
            updatedAt = now,
        )
        // Idempotente: UNIQUE(service_id, clinical_date) + relectura ante carrera.
        try {
            dao.getOrCreate(candidate.toEntity()).toDomain()
        } catch (e: SQLiteConstraintException) {
            dao.findByServiceAndDate(serviceId, clinicalDate)?.toDomain()
                ?: throw e
        }
    }

    override suspend fun findPreviousJourney(
        serviceId: String,
        clinicalDate: String,
    ): Journey? = withContext(Dispatchers.IO) {
        dao.findPrevious(serviceId, clinicalDate)?.toDomain()
    }

    override fun observeJourneys(serviceId: String): Flow<List<Journey>> =
        dao.observeAllByService(serviceId).map { list -> list.map { it.toDomain() } }
}

class DailyRecordRepositoryImpl(
    private val dao: DailyRecordDao,
    private val clock: Clock,
) : DailyRecordRepository {
    override fun observeByJourney(journeyId: String): Flow<List<DailyRecord>> =
        dao.observeByJourney(journeyId).map { list -> list.map { it.toDomain() } }

    override suspend fun getByPatientAndJourney(
        patientId: String,
        journeyId: String,
    ): DailyRecord? = withContext(Dispatchers.IO) {
        dao.getByPatientAndJourney(patientId, journeyId)?.toDomain()
    }

    override suspend fun create(record: DailyRecord): String = withContext(Dispatchers.IO) {
        dao.insert(record.toEntity())
        record.id
    }

    override suspend fun update(record: DailyRecord) = withContext(Dispatchers.IO) {
        dao.update(record.copy(updatedAt = clock.nowMillis()).toEntity())
    }

    override suspend fun createAll(records: List<DailyRecord>) = withContext(Dispatchers.IO) {
        if (records.isNotEmpty()) dao.insertAll(records.map { it.toEntity() })
    }

    override suspend fun findLatestBefore(
        patientId: String,
        clinicalDate: String,
    ): DailyRecord? = withContext(Dispatchers.IO) {
        dao.findLatestBefore(patientId, clinicalDate)?.toDomain()
    }

    override fun observeByPatient(patientId: String): Flow<List<DailyRecord>> =
        dao.observeByPatient(patientId).map { list -> list.map { it.toDomain() } }
}

class PendingRepositoryImpl(
    private val dao: PendingDao,
    private val clock: Clock,
) : PendingRepository {
    override fun observeOpenByPatient(patientId: String): Flow<List<Pending>> =
        dao.observeOpenByPatient(patientId).map { list -> list.map { it.toDomain() } }

    override fun observeOpenByService(serviceId: String): Flow<List<Pending>> =
        dao.observeOpenByService(serviceId).map { list -> list.map { it.toDomain() } }

    override fun observeRecentlyClosedByService(serviceId: String): Flow<List<Pending>> =
        dao.observeRecentlyClosedByService(serviceId).map { list -> list.map { it.toDomain() } }

    override suspend fun getOpenByPatient(patientId: String): List<Pending> =
        withContext(Dispatchers.IO) {
            dao.getOpenByPatient(patientId).map { it.toDomain() }
        }

    override suspend fun create(pending: Pending): String = withContext(Dispatchers.IO) {
        dao.insert(pending.toEntity())
        pending.id
    }

    override suspend fun getPending(id: String): Pending? = withContext(Dispatchers.IO) {
        dao.getById(id)?.toDomain()
    }

    override suspend fun update(pending: Pending) = withContext(Dispatchers.IO) {
        dao.update(pending.copy(updatedAt = clock.nowMillis()).toEntity())
    }

    override suspend fun deleteByPatient(patientId: String) = withContext(Dispatchers.IO) {
        dao.deleteByPatient(patientId)
    }
}

class TreatmentRepositoryImpl(
    private val dao: TreatmentDao,
    private val eventDao: TreatmentEventDao,
    private val clock: Clock,
) : TreatmentRepository {
    override fun observeActiveByPatient(patientId: String): Flow<List<Treatment>> =
        dao.observeActiveByPatient(patientId).map { list -> list.map { it.toDomain() } }

    override suspend fun create(treatment: Treatment): String = withContext(Dispatchers.IO) {
        dao.insert(treatment.toEntity())
        treatment.id
    }

    override suspend fun getTreatment(id: String): Treatment? = withContext(Dispatchers.IO) {
        dao.getById(id)?.toDomain()
    }

    override suspend fun update(treatment: Treatment) = withContext(Dispatchers.IO) {
        dao.update(treatment.copy(updatedAt = clock.nowMillis()).toEntity())
    }

    override fun observeEvents(treatmentId: String): Flow<List<TreatmentEvent>> =
        eventDao.observeByTreatment(treatmentId).map { list -> list.map { it.toDomain() } }

    override suspend fun addEvent(event: TreatmentEvent) = withContext(Dispatchers.IO) {
        eventDao.insert(event.toEntity())
    }
}

class DeviceRepositoryImpl(
    private val dao: DeviceDao,
    private val clock: Clock,
) : DeviceRepository {
    override fun observeActiveByPatient(patientId: String): Flow<List<Device>> =
        dao.observeActiveByPatient(patientId).map { list -> list.map { it.toDomain() } }

    override suspend fun create(device: Device): String = withContext(Dispatchers.IO) {
        dao.insert(device.toEntity())
        device.id
    }

    override suspend fun getDevice(id: String): Device? = withContext(Dispatchers.IO) {
        dao.getById(id)?.toDomain()
    }

    override suspend fun update(device: Device) = withContext(Dispatchers.IO) {
        dao.update(device.copy(updatedAt = clock.nowMillis()).toEntity())
    }
}

class ResultRepositoryImpl(
    private val dao: ResultDao,
) : ResultRepository {
    override fun observeByPatient(patientId: String): Flow<List<ClinicalResult>> =
        dao.observeByPatient(patientId).map { list -> list.map { it.toDomain() } }

    override suspend fun create(result: ClinicalResult): String = withContext(Dispatchers.IO) {
        dao.insert(result.toEntity())
        result.id
    }
}

class ProcedureRepositoryImpl(
    private val dao: ProcedureDao,
    private val clock: Clock,
) : ProcedureRepository {
    override fun observeByJourney(journeyId: String): Flow<List<Procedure>> =
        dao.observeActiveByJourney(journeyId).map { list -> list.map { it.toDomain() } }

    override fun observeActiveAll(): Flow<List<Procedure>> =
        dao.observeActiveAll().map { list -> list.map { it.toDomain() } }

    override fun observeRecentPerformed(limit: Int): Flow<List<Procedure>> =
        dao.observeRecentPerformed(limit).map { list -> list.map { it.toDomain() } }

    override suspend fun findActiveByPatient(patientId: String): Procedure? =
        withContext(Dispatchers.IO) {
            dao.findActiveByPatient(patientId)?.toDomain()
        }

    override suspend fun create(procedure: Procedure): String = withContext(Dispatchers.IO) {
        dao.insert(procedure.toEntity())
        procedure.id
    }

    override suspend fun getProcedure(id: String): Procedure? = withContext(Dispatchers.IO) {
        dao.getById(id)?.toDomain()
    }

    override suspend fun update(procedure: Procedure) = withContext(Dispatchers.IO) {
        dao.update(procedure.copy(updatedAt = clock.nowMillis()).toEntity())
    }

    override suspend fun deleteByPatient(patientId: String) = withContext(Dispatchers.IO) {
        dao.deleteByPatient(patientId)
    }
}

class CaseCardRepositoryImpl(
    private val dao: CaseCardDao,
    private val clock: Clock,
) : CaseCardRepository {
    override fun observeByJourney(journeyId: String): Flow<List<CaseCard>> =
        dao.observeByJourney(journeyId).map { list -> list.map { it.toDomain() } }

    override suspend fun getByPatientAndJourney(
        patientId: String,
        journeyId: String,
    ): CaseCard? = withContext(Dispatchers.IO) {
        dao.getByPatientAndJourney(patientId, journeyId)?.toDomain()
    }

    override suspend fun getById(id: String): CaseCard? = withContext(Dispatchers.IO) {
        dao.getById(id)?.toDomain()
    }

    override suspend fun findLatestBefore(
        patientId: String,
        clinicalDate: String,
    ): CaseCard? = withContext(Dispatchers.IO) {
        dao.findLatestBefore(patientId, clinicalDate)?.toDomain()
    }

    override suspend fun findLatestByPatient(patientId: String): CaseCard? =
        withContext(Dispatchers.IO) {
            dao.findLatestByPatient(patientId)?.toDomain()
        }

    override suspend fun create(card: CaseCard): String = withContext(Dispatchers.IO) {
        dao.insert(card.toEntity())
        card.id
    }

    override suspend fun update(card: CaseCard) = withContext(Dispatchers.IO) {
        dao.update(card.copy(updatedAt = clock.nowMillis()).toEntity())
    }

    override suspend fun deleteByPatient(patientId: String) = withContext(Dispatchers.IO) {
        dao.deleteByPatient(patientId)
    }

    override suspend fun countAll(): Int = withContext(Dispatchers.IO) {
        dao.countAll()
    }

    override suspend fun countDistinctBeds(): Int = withContext(Dispatchers.IO) {
        dao.countDistinctBeds()
    }
}

class CustomFieldRepositoryImpl(
    private val dao: CustomFieldDao,
    private val clock: Clock,
) : CustomFieldRepository {
    override fun observeByPatient(patientId: String): Flow<List<CustomField>> =
        dao.observeByPatient(patientId).map { list -> list.map { it.toDomain() } }

    override suspend fun create(field: CustomField): String = withContext(Dispatchers.IO) {
        dao.insert(field.toEntity())
        field.id
    }

    override suspend fun update(field: CustomField) = withContext(Dispatchers.IO) {
        dao.update(field.copy(updatedAt = clock.nowMillis()).toEntity())
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dao.deleteById(id)
    }

    override suspend fun deleteByPatient(patientId: String) = withContext(Dispatchers.IO) {
        dao.deleteByPatient(patientId)
    }

    override suspend fun countByPatient(patientId: String): Int = withContext(Dispatchers.IO) {
        dao.countByPatient(patientId)
    }
}

class CaseHistoryRepositoryImpl(
    private val dao: CaseHistoryDao,
    private val clock: Clock,
) : CaseHistoryRepository {
    override fun observeByPatient(patientId: String): Flow<List<CaseHistoryEntry>> =
        dao.observeByPatient(patientId).map { list -> list.map { it.toDomain() } }

    override suspend fun log(patientId: String, journeyId: String?, summary: String) =
        withContext(Dispatchers.IO) {
            dao.insert(
                CaseHistoryEntity(
                    id = newId(),
                    patientId = patientId,
                    journeyId = journeyId,
                    occurredAt = clock.nowMillis(),
                    summary = summary,
                ),
            )
        }
}
