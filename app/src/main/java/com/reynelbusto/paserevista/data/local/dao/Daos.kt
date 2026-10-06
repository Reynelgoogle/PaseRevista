package com.reynelbusto.paserevista.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.reynelbusto.paserevista.data.local.entity.CaseCardEntity
import com.reynelbusto.paserevista.data.local.entity.CaseHistoryEntity
import com.reynelbusto.paserevista.data.local.entity.CustomFieldEntity
import com.reynelbusto.paserevista.data.local.entity.DailyRecordEntity
import com.reynelbusto.paserevista.data.local.entity.DeviceEntity
import com.reynelbusto.paserevista.data.local.entity.JourneyEntity
import com.reynelbusto.paserevista.data.local.entity.PatientComorbidityEntity
import com.reynelbusto.paserevista.data.local.entity.PatientEntity
import com.reynelbusto.paserevista.data.local.entity.PendingEntity
import com.reynelbusto.paserevista.data.local.entity.ProcedureEntity
import com.reynelbusto.paserevista.data.local.entity.ResultEntity
import com.reynelbusto.paserevista.data.local.entity.TreatmentEntity
import com.reynelbusto.paserevista.data.local.entity.TreatmentEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(patient: PatientEntity)

    @Update
    suspend fun update(patient: PatientEntity)

    @Query("SELECT * FROM patient WHERE id = :id AND deleted_at IS NULL")
    suspend fun getById(id: String): PatientEntity?

    @Query(
        """SELECT * FROM patient
           WHERE service_id = :serviceId AND status = 'active' AND deleted_at IS NULL
           ORDER BY full_name""",
    )
    fun observeActiveByService(serviceId: String): Flow<List<PatientEntity>>

    @Query(
        """SELECT * FROM patient
           WHERE service_id = :serviceId AND deleted_at IS NULL
             AND (full_name LIKE '%' || :query || '%'
               OR hc_number LIKE '%' || :query || '%'
               OR main_diagnosis LIKE '%' || :query || '%')
           ORDER BY full_name""",
    )
    fun search(serviceId: String, query: String): Flow<List<PatientEntity>>

    /**
     * Búsqueda global incluyendo cama (vive en el DailyRecord de la jornada dada).
     * Offline y tolerante: LIKE por nombre, HC, diagnóstico o cama.
     */
    @Query(
        """SELECT DISTINCT p.* FROM patient p
           LEFT JOIN daily_record dr ON dr.patient_id = p.id
           LEFT JOIN journey j ON j.id = dr.journey_id
             AND j.service_id = :serviceId AND j.clinical_date = :clinicalDate
           WHERE p.service_id = :serviceId AND p.deleted_at IS NULL
             AND (p.full_name LIKE '%' || :query || '%'
               OR p.hc_number LIKE '%' || :query || '%'
               OR p.main_diagnosis LIKE '%' || :query || '%'
               OR dr.bed LIKE '%' || :query || '%')
           ORDER BY p.full_name""",
    )
    fun searchWithBed(serviceId: String, clinicalDate: String, query: String): Flow<List<PatientEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComorbidities(rows: List<PatientComorbidityEntity>)

    @Query("SELECT label FROM patient_comorbidity WHERE patient_id = :patientId ORDER BY label")
    suspend fun getComorbidities(patientId: String): List<String>

    /** Censo para el motor de jornadas: solo activos, sin borrados lógicos. */
    @Query(
        """SELECT * FROM patient
           WHERE service_id = :serviceId AND status = 'active' AND deleted_at IS NULL
           ORDER BY full_name""",
    )
    suspend fun getActiveByService(serviceId: String): List<PatientEntity>

    /** Control anti-duplicados por historia clínica dentro del servicio. */
    @Query(
        """SELECT * FROM patient
           WHERE service_id = :serviceId AND hc_number = :hc AND deleted_at IS NULL""",
    )
    suspend fun findByServiceAndHc(serviceId: String, hc: String): PatientEntity?
}

@Dao
interface JourneyDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(journey: JourneyEntity)

    @Update
    suspend fun update(journey: JourneyEntity)

    @Query(
        """SELECT * FROM journey
           WHERE service_id = :serviceId AND clinical_date = :clinicalDate""",
    )
    suspend fun findByServiceAndDate(serviceId: String, clinicalDate: String): JourneyEntity?

    @Query(
        """SELECT * FROM journey
           WHERE service_id = :serviceId AND clinical_date = :clinicalDate AND status = 'current'""",
    )
    fun observeCurrent(serviceId: String, clinicalDate: String): Flow<JourneyEntity?>

    /** Idempotente: UNIQUE(service_id, clinical_date) + relectura ante carrera. */
    @Transaction
    suspend fun getOrCreate(journey: JourneyEntity): JourneyEntity {
        val existing = findByServiceAndDate(journey.serviceId, journey.clinicalDate)
        if (existing != null) return existing
        try {
            insert(journey)
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            return findByServiceAndDate(journey.serviceId, journey.clinicalDate)
                ?: throw e
        }
        return journey
    }

    /** Jornada anterior más reciente: referencia para AYER y para enlazar registros. */
    @Query(
        """SELECT * FROM journey
           WHERE service_id = :serviceId AND clinical_date < :clinicalDate
           ORDER BY clinical_date DESC LIMIT 1""",
    )
    suspend fun findPrevious(serviceId: String, clinicalDate: String): JourneyEntity?

    /** Historial de jornadas del servicio (FASE 9), más recientes primero. */
    @Query(
        """SELECT * FROM journey
           WHERE service_id = :serviceId
           ORDER BY clinical_date DESC""",
    )
    fun observeAllByService(serviceId: String): Flow<List<JourneyEntity>>
}

@Dao
interface DailyRecordDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: DailyRecordEntity)

    @Update
    suspend fun update(record: DailyRecordEntity)

    @Query("SELECT * FROM daily_record WHERE journey_id = :journeyId ORDER BY bed, patient_id")
    fun observeByJourney(journeyId: String): Flow<List<DailyRecordEntity>>

    @Query(
        """SELECT * FROM daily_record
           WHERE patient_id = :patientId AND journey_id = :journeyId""",
    )
    suspend fun getByPatientAndJourney(patientId: String, journeyId: String): DailyRecordEntity?

    @Query(
        """SELECT COUNT(*) FROM daily_record
           WHERE journey_id = :journeyId AND review_state = 'completed'""",
    )
    fun observeCompletedCount(journeyId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(records: List<DailyRecordEntity>)

    /**
     * Registro más reciente del paciente anterior a una fecha clínica.
     * Es la referencia AYER (solo lectura en la ficha).
     */
    @Query(
        """SELECT dr.* FROM daily_record dr
           INNER JOIN journey j ON j.id = dr.journey_id
           WHERE dr.patient_id = :patientId AND j.clinical_date < :clinicalDate
           ORDER BY j.clinical_date DESC LIMIT 1""",
    )
    suspend fun findLatestBefore(patientId: String, clinicalDate: String): DailyRecordEntity?

    /** Timeline del paciente (FASE 9): todos sus registros, recientes primero. */
    @Query(
        """SELECT dr.* FROM daily_record dr
           INNER JOIN journey j ON j.id = dr.journey_id
           WHERE dr.patient_id = :patientId
           ORDER BY j.clinical_date DESC""",
    )
    fun observeByPatient(patientId: String): Flow<List<DailyRecordEntity>>
}

@Dao
interface PendingDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(pending: PendingEntity)

    @Update
    suspend fun update(pending: PendingEntity)

    @Query(
        """SELECT * FROM pending
           WHERE patient_id = :patientId AND status IN ('pending','in_progress')
           ORDER BY
             CASE priority WHEN 'p1' THEN 0 WHEN 'p2' THEN 1 ELSE 2 END,
             due_date IS NULL, due_date""",
    )
    fun observeOpenByPatient(patientId: String): Flow<List<PendingEntity>>

    @Query(
        """SELECT p.* FROM pending p
           INNER JOIN patient pt ON pt.id = p.patient_id
           WHERE pt.service_id = :serviceId
             AND pt.status = 'active'
             AND p.status IN ('pending','in_progress')
           ORDER BY
             CASE p.priority WHEN 'p1' THEN 0 WHEN 'p2' THEN 1 ELSE 2 END,
             p.due_date IS NULL, p.due_date""",
    )
    fun observeOpenByService(serviceId: String): Flow<List<PendingEntity>>

    @Query("SELECT * FROM pending WHERE id = :id")
    suspend fun getById(id: String): PendingEntity?

    @Query(
        """SELECT p.* FROM pending p
           INNER JOIN patient pt ON pt.id = p.patient_id
           WHERE pt.service_id = :serviceId
             AND pt.status = 'active'
             AND p.status IN ('completed','canceled')
           ORDER BY p.resolved_at DESC LIMIT 100""",
    )
    fun observeRecentlyClosedByService(serviceId: String): Flow<List<PendingEntity>>

    @Query(
        """SELECT * FROM pending
           WHERE patient_id = :patientId AND status IN ('pending','in_progress')
           ORDER BY
             CASE priority WHEN 'p1' THEN 0 WHEN 'p2' THEN 1 ELSE 2 END,
             due_date IS NULL, due_date""",
    )
    suspend fun getOpenByPatient(patientId: String): List<PendingEntity>
}

@Dao
interface TreatmentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(treatment: TreatmentEntity)

    @Update
    suspend fun update(treatment: TreatmentEntity)

    @Query(
        """SELECT * FROM treatment
           WHERE patient_id = :patientId AND status = 'active'
           ORDER BY start_date""",
    )
    fun observeActiveByPatient(patientId: String): Flow<List<TreatmentEntity>>

    @Query("SELECT * FROM treatment WHERE id = :id")
    suspend fun getById(id: String): TreatmentEntity?
}

@Dao
interface TreatmentEventDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: TreatmentEventEntity)

    @Query(
        """SELECT * FROM treatment_event
           WHERE treatment_id = :treatmentId
           ORDER BY occurred_at""",
    )
    fun observeByTreatment(treatmentId: String): Flow<List<TreatmentEventEntity>>

    @Query(
        """SELECT * FROM treatment_event
           WHERE treatment_id = :treatmentId
           ORDER BY occurred_at""",
    )
    suspend fun getByTreatment(treatmentId: String): List<TreatmentEventEntity>
}

@Dao
interface DeviceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(device: DeviceEntity)

    @Update
    suspend fun update(device: DeviceEntity)

    @Query(
        """SELECT * FROM device
           WHERE patient_id = :patientId AND status = 'active'
           ORDER BY placed_date""",
    )
    fun observeActiveByPatient(patientId: String): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM device WHERE id = :id")
    suspend fun getById(id: String): DeviceEntity?
}

@Dao
interface ResultDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(result: ResultEntity)

    @Query(
        """SELECT * FROM clinical_result
           WHERE patient_id = :patientId
           ORDER BY result_date DESC""",
    )
    fun observeByPatient(patientId: String): Flow<List<ResultEntity>>
}

@Dao
interface ProcedureDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(procedure: ProcedureEntity)

    @Update
    suspend fun update(procedure: ProcedureEntity)

    @Query(
        """SELECT * FROM `procedure`
           WHERE journey_id = :journeyId AND status IN ('pending','preparation')
           ORDER BY scheduled_time IS NULL, scheduled_time""",
    )
    fun observeActiveByJourney(journeyId: String): Flow<List<ProcedureEntity>>

    @Query("SELECT * FROM `procedure` WHERE id = :id")
    suspend fun getById(id: String): ProcedureEntity?

    /** Pizarra Kanban: procedimientos activos de todas las jornadas. */
    @Query(
        """SELECT * FROM `procedure`
           WHERE status IN ('pending','preparation')
           ORDER BY created_at""",
    )
    fun observeActiveAll(): Flow<List<ProcedureEntity>>

    @Query(
        """SELECT * FROM `procedure`
           WHERE status = 'performed'
           ORDER BY performed_at DESC LIMIT :limit""",
    )
    fun observeRecentPerformed(limit: Int = 50): Flow<List<ProcedureEntity>>

    /** Procedimiento activo (no realizado/cancelado) de un paciente, si existe. */
    @Query(
        """SELECT * FROM `procedure`
           WHERE patient_id = :patientId AND status IN ('pending','preparation')
           ORDER BY created_at DESC LIMIT 1""",
    )
    suspend fun findActiveByPatient(patientId: String): ProcedureEntity?
}

@Dao
interface CaseCardDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(card: CaseCardEntity)

    @Update
    suspend fun update(card: CaseCardEntity)

    @Query("SELECT * FROM case_card WHERE journey_id = :journeyId ORDER BY bed")
    fun observeByJourney(journeyId: String): Flow<List<CaseCardEntity>>

    @Query("SELECT * FROM case_card WHERE patient_id = :patientId AND journey_id = :journeyId")
    suspend fun getByPatientAndJourney(patientId: String, journeyId: String): CaseCardEntity?

    /** Última tarjeta del paciente antes de una fecha (para heredar al día nuevo). */
    @Query(
        """SELECT cc.* FROM case_card cc
           JOIN journey j ON j.id = cc.journey_id
           WHERE cc.patient_id = :patientId AND j.clinical_date < :clinicalDate
           ORDER BY j.clinical_date DESC LIMIT 1""",
    )
    suspend fun findLatestBefore(patientId: String, clinicalDate: String): CaseCardEntity?

    @Query("SELECT * FROM case_card WHERE id = :id")
    suspend fun getById(id: String): CaseCardEntity?

    /** Última tarjeta del paciente (para nombre de cama en la pizarra). */
    @Query("SELECT * FROM case_card WHERE patient_id = :patientId ORDER BY created_at DESC LIMIT 1")
    suspend fun findLatestByPatient(patientId: String): CaseCardEntity?
}

@Dao
interface CustomFieldDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(field: CustomFieldEntity)

    @Update
    suspend fun update(field: CustomFieldEntity)

    @Query("DELETE FROM custom_field WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query(
        """SELECT * FROM custom_field
           WHERE patient_id = :patientId
           ORDER BY sort_order, created_at""",
    )
    fun observeByPatient(patientId: String): Flow<List<CustomFieldEntity>>

    @Query("SELECT COUNT(*) FROM custom_field WHERE patient_id = :patientId")
    suspend fun countByPatient(patientId: String): Int
}

@Dao
interface CaseHistoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: CaseHistoryEntity)

    @Query(
        """SELECT * FROM case_history
           WHERE patient_id = :patientId
           ORDER BY occurred_at DESC""",
    )
    fun observeByPatient(patientId: String): Flow<List<CaseHistoryEntity>>
}
