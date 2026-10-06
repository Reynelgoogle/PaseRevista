package com.reynelbusto.paserevista.di

import android.content.Context
import androidx.room.Room
import com.reynelbusto.paserevista.BuildConfig
import com.reynelbusto.paserevista.core.Clock
import com.reynelbusto.paserevista.core.SystemClock
import com.reynelbusto.paserevista.data.backup.BackupManager
import com.reynelbusto.paserevista.data.local.DATABASE_NAME
import com.reynelbusto.paserevista.data.local.DatabaseUnitOfWork
import com.reynelbusto.paserevista.data.local.MIGRATION_1_2
import com.reynelbusto.paserevista.data.local.PaseRevistaDatabase
import com.reynelbusto.paserevista.data.local.SCHEMA_VERSION
import com.reynelbusto.paserevista.data.repository.CaseCardRepositoryImpl
import com.reynelbusto.paserevista.data.repository.CaseHistoryRepositoryImpl
import com.reynelbusto.paserevista.data.repository.CustomFieldRepositoryImpl
import com.reynelbusto.paserevista.data.repository.DailyRecordRepositoryImpl
import com.reynelbusto.paserevista.data.repository.DeviceRepositoryImpl
import com.reynelbusto.paserevista.data.repository.JourneyRepositoryImpl
import com.reynelbusto.paserevista.data.repository.PatientRepositoryImpl
import com.reynelbusto.paserevista.data.repository.PendingRepositoryImpl
import com.reynelbusto.paserevista.data.repository.ProcedureRepositoryImpl
import com.reynelbusto.paserevista.data.repository.ResultRepositoryImpl
import com.reynelbusto.paserevista.data.repository.TreatmentRepositoryImpl
import com.reynelbusto.paserevista.data.debug.DebugSeed
import com.reynelbusto.paserevista.domain.repository.CaseCardRepository
import com.reynelbusto.paserevista.domain.repository.CaseHistoryRepository
import com.reynelbusto.paserevista.domain.repository.ClinicalUnitOfWork
import com.reynelbusto.paserevista.domain.repository.CustomFieldRepository
import com.reynelbusto.paserevista.domain.repository.DailyRecordRepository
import com.reynelbusto.paserevista.domain.repository.DeviceRepository
import com.reynelbusto.paserevista.domain.repository.JourneyRepository
import com.reynelbusto.paserevista.domain.repository.PatientRepository
import com.reynelbusto.paserevista.domain.repository.PendingRepository
import com.reynelbusto.paserevista.domain.repository.ProcedureRepository
import com.reynelbusto.paserevista.domain.repository.ResultRepository
import com.reynelbusto.paserevista.domain.repository.TreatmentRepository
import com.reynelbusto.paserevista.domain.usecase.AddBedUseCase
import com.reynelbusto.paserevista.domain.usecase.BoardUseCase
import com.reynelbusto.paserevista.domain.usecase.CaseCardUseCase
import com.reynelbusto.paserevista.domain.usecase.CreateJourneyUseCase
import com.reynelbusto.paserevista.domain.usecase.CustomFieldUseCase
import com.reynelbusto.paserevista.domain.usecase.DischargeUseCase
import com.reynelbusto.paserevista.domain.usecase.EnsureDayUseCase
import com.reynelbusto.paserevista.domain.usecase.GetCurrentJourneyUseCase
import com.reynelbusto.paserevista.domain.usecase.GetDailyRecordsUseCase
import com.reynelbusto.paserevista.domain.usecase.GetPatientsUseCase
import com.reynelbusto.paserevista.domain.usecase.PendingUseCase
import com.reynelbusto.paserevista.domain.usecase.ProcedureUseCase
import com.reynelbusto.paserevista.domain.usecase.DeviceUseCase
import com.reynelbusto.paserevista.domain.usecase.ResultUseCase
import com.reynelbusto.paserevista.domain.usecase.TreatmentUseCase

/**
 * Inyección manual de dependencias (sin Hilt: menos riesgo de build).
 * Pivot entrega de guardia: motor de jornadas invisible + tarjetas de cama.
 */
class AppContainer(context: Context) {

    val clock: Clock = SystemClock()

    private val database: PaseRevistaDatabase = openDatabase(context.applicationContext)

    val unitOfWork: ClinicalUnitOfWork = DatabaseUnitOfWork(database)

    private fun openDatabase(appContext: Context): PaseRevistaDatabase =
        Room.databaseBuilder(
            appContext,
            PaseRevistaDatabase::class.java,
            DATABASE_NAME,
        ).addMigrations(MIGRATION_1_2).build()

    /**
     * Cierra Room para poder reemplazar el archivo .db (restaurar respaldo).
     * La UI debe reiniciar la app después: los repositorios en memoria
     * apuntan a la base anterior.
     */
    fun closeDatabase() {
        database.close()
    }

    // Repositorios
    val patientRepository: PatientRepository =
        PatientRepositoryImpl(database.patientDao(), clock)
    val journeyRepository: JourneyRepository =
        JourneyRepositoryImpl(database.journeyDao(), clock)
    val dailyRecordRepository: DailyRecordRepository =
        DailyRecordRepositoryImpl(database.dailyRecordDao(), clock)
    val pendingRepository: PendingRepository =
        PendingRepositoryImpl(database.pendingDao(), clock)
    val treatmentRepository: TreatmentRepository =
        TreatmentRepositoryImpl(database.treatmentDao(), database.treatmentEventDao(), clock)
    val deviceRepository: DeviceRepository =
        DeviceRepositoryImpl(database.deviceDao(), clock)
    val resultRepository: ResultRepository =
        ResultRepositoryImpl(database.resultDao())
    val procedureRepository: ProcedureRepository =
        ProcedureRepositoryImpl(database.procedureDao(), clock)
    val caseCardRepository: CaseCardRepository =
        CaseCardRepositoryImpl(database.caseCardDao(), clock)
    val customFieldRepository: CustomFieldRepository =
        CustomFieldRepositoryImpl(database.customFieldDao(), clock)
    val caseHistoryRepository: CaseHistoryRepository =
        CaseHistoryRepositoryImpl(database.caseHistoryDao(), clock)

    // Casos de uso — entrega de guardia
    val board = BoardUseCase(
        unitOfWork, procedureRepository, patientRepository, caseCardRepository,
        caseHistoryRepository, clock,
    )
    val addBed = AddBedUseCase(
        unitOfWork, patientRepository, journeyRepository, caseCardRepository,
        caseHistoryRepository, clock,
    )
    val ensureDay = EnsureDayUseCase(
        unitOfWork, journeyRepository, patientRepository, caseCardRepository,
        dailyRecordRepository, clock,
    )
    val caseCards = CaseCardUseCase(
        unitOfWork, caseCardRepository, patientRepository, caseHistoryRepository, board, clock,
    )
    val customFields = CustomFieldUseCase(
        unitOfWork, customFieldRepository, caseHistoryRepository, clock,
    )
    val discharge = DischargeUseCase(unitOfWork, patientRepository, pendingRepository, clock)

    // Compatibilidad / módulos
    val getPatients = GetPatientsUseCase(patientRepository)
    val getCurrentJourney = GetCurrentJourneyUseCase(journeyRepository, clock)
    val createJourney = CreateJourneyUseCase(journeyRepository, clock)
    val getDailyRecords = GetDailyRecordsUseCase(dailyRecordRepository)
    val pendings = PendingUseCase(pendingRepository, clock)
    val treatments = TreatmentUseCase(unitOfWork, treatmentRepository, clock)
    val devices = DeviceUseCase(deviceRepository, clock)
    val results = ResultUseCase(resultRepository, clock)
    val procedures = ProcedureUseCase(unitOfWork, procedureRepository, pendingRepository, clock)

    // Respaldo local de la base de datos (Documents/EntregaGuardia/).
    val backupManager = BackupManager(
        appContext = context.applicationContext,
        clock = clock,
        databaseFile = { context.applicationContext.getDatabasePath(DATABASE_NAME) },
        checkpointWal = {
            database.openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(TRUNCATE)").close()
        },
        closeDatabase = { closeDatabase() },
        schemaVersion = SCHEMA_VERSION,
        countCards = { caseCardRepository.countAll() },
        countBeds = { caseCardRepository.countDistinctBeds() },
        appVersion = BuildConfig.VERSION_NAME,
    )

    // Solo-debug: datos de prueba claramente marcados.
    val debugSeed = DebugSeed(
        patientRepository = patientRepository,
        journeyRepository = journeyRepository,
        dailyRecordRepository = dailyRecordRepository,
        pendingRepository = pendingRepository,
        treatmentRepository = treatmentRepository,
        deviceRepository = deviceRepository,
        clock = clock,
    )
}
