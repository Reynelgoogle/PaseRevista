package com.reynelbusto.paserevista.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.reynelbusto.paserevista.data.local.dao.CaseCardDao
import com.reynelbusto.paserevista.data.local.dao.CaseHistoryDao
import com.reynelbusto.paserevista.data.local.dao.CustomFieldDao
import com.reynelbusto.paserevista.data.local.dao.DailyRecordDao
import com.reynelbusto.paserevista.data.local.dao.DeviceDao
import com.reynelbusto.paserevista.data.local.dao.JourneyDao
import com.reynelbusto.paserevista.data.local.dao.PatientDao
import com.reynelbusto.paserevista.data.local.dao.PendingDao
import com.reynelbusto.paserevista.data.local.dao.ProcedureDao
import com.reynelbusto.paserevista.data.local.dao.ResultDao
import com.reynelbusto.paserevista.data.local.dao.TreatmentDao
import com.reynelbusto.paserevista.data.local.dao.TreatmentEventDao
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

/**
 * Única fuente de verdad. SQLite local, sin red.
 *
 * v2 (pivot entrega de guardia): `patient` permite nulos en datos personales
 * (REGLA DE ORO: nada obligatorio) + columna `address`; nuevas tablas
 * `case_card`, `custom_field`, `case_history`.
 */
@Database(
    entities = [
        PatientEntity::class,
        PatientComorbidityEntity::class,
        JourneyEntity::class,
        DailyRecordEntity::class,
        PendingEntity::class,
        TreatmentEntity::class,
        TreatmentEventEntity::class,
        DeviceEntity::class,
        ResultEntity::class,
        ProcedureEntity::class,
        CaseCardEntity::class,
        CustomFieldEntity::class,
        CaseHistoryEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class PaseRevistaDatabase : RoomDatabase() {
    abstract fun patientDao(): PatientDao
    abstract fun journeyDao(): JourneyDao
    abstract fun dailyRecordDao(): DailyRecordDao
    abstract fun pendingDao(): PendingDao
    abstract fun treatmentDao(): TreatmentDao
    abstract fun treatmentEventDao(): TreatmentEventDao
    abstract fun deviceDao(): DeviceDao
    abstract fun resultDao(): ResultDao
    abstract fun procedureDao(): ProcedureDao
    abstract fun caseCardDao(): CaseCardDao
    abstract fun customFieldDao(): CustomFieldDao
    abstract fun caseHistoryDao(): CaseHistoryDao
}

/**
 * Migración 1 → 2 (pivot entrega de guardia).
 * - `patient`: rebuild para nullabilidad + `address`.
 * - Tablas nuevas: case_card, custom_field, case_history.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1) Rebuild de patient con columnas nullables + address.
        db.execSQL(
            """CREATE TABLE patient_new (
                id TEXT NOT NULL PRIMARY KEY,
                full_name TEXT,
                birth_date TEXT,
                sex TEXT,
                hc_number TEXT,
                blood_group TEXT,
                address TEXT,
                service_id TEXT NOT NULL,
                admission_date TEXT NOT NULL,
                admission_reason TEXT,
                main_diagnosis TEXT,
                status TEXT NOT NULL,
                discharge_date TEXT,
                discharge_reason TEXT,
                previous_episode_id TEXT,
                is_out_of_service INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                deleted_at INTEGER
            )""",
        )
        db.execSQL(
            """INSERT INTO patient_new
               (id, full_name, birth_date, sex, hc_number, blood_group, address,
                service_id, admission_date, admission_reason, main_diagnosis, status,
                discharge_date, discharge_reason, previous_episode_id,
                is_out_of_service, created_at, updated_at, deleted_at)
               SELECT id, full_name, birth_date, sex, hc_number, blood_group, NULL,
                service_id, admission_date, admission_reason, main_diagnosis, status,
                discharge_date, discharge_reason, previous_episode_id,
                is_out_of_service, created_at, updated_at, deleted_at
               FROM patient""",
        )
        db.execSQL("DROP TABLE patient")
        db.execSQL("ALTER TABLE patient_new RENAME TO patient")
        db.execSQL(
            "CREATE UNIQUE INDEX index_patient_service_id_hc_number " +
                "ON patient(service_id, hc_number)",
        )
        db.execSQL("CREATE INDEX index_patient_service_id_status ON patient(service_id, status)")
        db.execSQL("CREATE INDEX index_patient_full_name ON patient(full_name)")

        // 2) case_card.
        db.execSQL(
            """CREATE TABLE case_card (
                id TEXT NOT NULL PRIMARY KEY,
                patient_id TEXT NOT NULL REFERENCES patient(id) ON DELETE CASCADE,
                journey_id TEXT NOT NULL REFERENCES journey(id) ON DELETE CASCADE,
                bed TEXT NOT NULL,
                diagnosis TEXT,
                scheduled_procedure TEXT,
                current_state TEXT,
                antibiotic TEXT,
                ready INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )""",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX index_case_card_patient_id_journey_id " +
                "ON case_card(patient_id, journey_id)",
        )
        db.execSQL("CREATE INDEX index_case_card_journey_id ON case_card(journey_id)")

        // 3) custom_field.
        db.execSQL(
            """CREATE TABLE custom_field (
                id TEXT NOT NULL PRIMARY KEY,
                patient_id TEXT NOT NULL REFERENCES patient(id) ON DELETE CASCADE,
                label TEXT NOT NULL,
                value TEXT NOT NULL,
                sort_order INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )""",
        )
        db.execSQL(
            "CREATE INDEX index_custom_field_patient_id_sort_order " +
                "ON custom_field(patient_id, sort_order)",
        )

        // 4) case_history.
        db.execSQL(
            """CREATE TABLE case_history (
                id TEXT NOT NULL PRIMARY KEY,
                patient_id TEXT NOT NULL REFERENCES patient(id) ON DELETE CASCADE,
                journey_id TEXT,
                occurred_at INTEGER NOT NULL,
                summary TEXT NOT NULL
            )""",
        )
        db.execSQL(
            "CREATE INDEX index_case_history_patient_id_occurred_at " +
                "ON case_history(patient_id, occurred_at)",
        )
    }
}
