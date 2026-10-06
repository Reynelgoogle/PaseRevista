package com.reynelbusto.paserevista.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.reynelbusto.paserevista.data.local.dao.DailyRecordDao
import com.reynelbusto.paserevista.data.local.dao.DeviceDao
import com.reynelbusto.paserevista.data.local.dao.JourneyDao
import com.reynelbusto.paserevista.data.local.dao.PatientDao
import com.reynelbusto.paserevista.data.local.dao.PendingDao
import com.reynelbusto.paserevista.data.local.dao.ProcedureDao
import com.reynelbusto.paserevista.data.local.dao.ResultDao
import com.reynelbusto.paserevista.data.local.dao.TreatmentDao
import com.reynelbusto.paserevista.data.local.dao.TreatmentEventDao
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
 * SEAM DE CIFRADO (fase posterior): el cifrado SQLCipher se conectará aquí
 * mediante un SupportFactory pasado a Room.databaseBuilder(...openHelperFactory(...)).
 * Ningún DAO ni repositorio cambiará cuando se active.
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
    ],
    version = 1,
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
}
