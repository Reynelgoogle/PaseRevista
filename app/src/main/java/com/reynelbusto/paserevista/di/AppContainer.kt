package com.reynelbusto.paserevista.di

import android.content.Context
import androidx.room.Room
import com.reynelbusto.paserevista.core.Clock
import com.reynelbusto.paserevista.core.SystemClock
import com.reynelbusto.paserevista.data.local.DatabaseUnitOfWork
import com.reynelbusto.paserevista.data.local.PaseRevistaDatabase
import com.reynelbusto.paserevista.data.repository.DailyRecordRepositoryImpl
import com.reynelbusto.paserevista.data.repository.DeviceRepositoryImpl
import com.reynelbusto.paserevista.data.repository.JourneyRepositoryImpl
import com.reynelbusto.paserevista.data.repository.PatientRepositoryImpl
import com.reynelbusto.paserevista.data.repository.PendingRepositoryImpl
import com.reynelbusto.paserevista.data.repository.ProcedureRepositoryImpl
import com.reynelbusto.paserevista.data.repository.ResultRepositoryImpl
import com.reynelbusto.paserevista.data.repository.TreatmentRepositoryImpl
import com.reynelbusto.paserevista.data.debug.DebugSeed
import com.reynelbusto.paserevista.domain.repository.ClinicalUnitOfWork
import com.reynelbusto.paserevista.domain.repository.DailyRecordRepository
import com.reynelbusto.paserevista.domain.repository.DeviceRepository
import com.reynelbusto.paserevista.domain.repository.JourneyRepository
import com.reynelbusto.paserevista.domain.repository.PatientRepository
import com.reynelbusto.paserevista.domain.repository.PendingRepository
import com.reynelbusto.paserevista.domain.repository.ProcedureRepository
import com.reynelbusto.paserevista.domain.repository.ResultRepository
import com.reynelbusto.paserevista.domain.repository.TreatmentRepository
import com.reynelbusto.paserevista.domain.usecase.AdmitPatientUseCase
import com.reynelbusto.paserevista.domain.usecase.ChangeBedUseCase
import com.reynelbusto.paserevista.domain.usecase.CreateJourneyUseCase
import com.reynelbusto.paserevista.domain.usecase.CreatePatientUseCase
import com.reynelbusto.paserevista.domain.usecase.DischargeUseCase
import com.reynelbusto.paserevista.domain.usecase.GetCurrentJourneyUseCase
import com.reynelbusto.paserevista.domain.usecase.GetDailyRecordsUseCase
import com.reynelbusto.paserevista.domain.usecase.GetPatientsUseCase
import com.reynelbusto.paserevista.domain.usecase.PendingUseCase
import com.reynelbusto.paserevista.domain.usecase.ProcedureUseCase
import com.reynelbusto.paserevista.domain.usecase.DeviceUseCase
import com.reynelbusto.paserevista.domain.usecase.ResultUseCase
import com.reynelbusto.paserevista.domain.usecase.TreatmentUseCase
import com.reynelbusto.paserevista.domain.usecase.ReviewUseCase
import com.reynelbusto.paserevista.domain.usecase.StartJourneyUseCase

/**
 * Inyección manual de dependencias (sin Hilt en FASE 5: menos riesgo de build).
 * Un solo punto de construcción; la UI solo recibe lo que necesita.
 *
 * SEAM DE CIFRADO: [openDatabase] es el único lugar donde se construye Room.
 * SQLCipher se conectará aquí con un SupportFactory, sin tocar DAOs ni repos.
 */
class AppContainer(context: Context) {

    val clock: Clock = SystemClock()

    private val database: PaseRevistaDatabase = openDatabase(context.applicationContext)

    val unitOfWork: ClinicalUnitOfWork = DatabaseUnitOfWork(database)

    private fun openDatabase(appContext: Context): PaseRevistaDatabase =
        Room.databaseBuilder(
            appContext,
            PaseRevistaDatabase::class.java,
            "paserevista.db",
        ).build()

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

    // Casos de uso
    val createPatient = CreatePatientUseCase(patientRepository, clock)
    val admitPatient = AdmitPatientUseCase(
        unitOfWork, patientRepository, journeyRepository, dailyRecordRepository, clock,
    )
    val getPatients = GetPatientsUseCase(patientRepository)
    val getCurrentJourney = GetCurrentJourneyUseCase(journeyRepository, clock)
    val createJourney = CreateJourneyUseCase(journeyRepository, clock)
    val startJourney = StartJourneyUseCase(
        unitOfWork, journeyRepository, patientRepository, dailyRecordRepository, clock,
    )
    val getDailyRecords = GetDailyRecordsUseCase(dailyRecordRepository)
    val review = ReviewUseCase(dailyRecordRepository, clock)
    val discharge = DischargeUseCase(unitOfWork, patientRepository, pendingRepository, clock)
    val changeBed = ChangeBedUseCase(dailyRecordRepository)
    // FASE 8 — módulos operativos
    val pendings = PendingUseCase(pendingRepository, clock)
    val treatments = TreatmentUseCase(unitOfWork, treatmentRepository, clock)
    val devices = DeviceUseCase(deviceRepository, clock)
    val results = ResultUseCase(resultRepository, clock)
    val procedures = ProcedureUseCase(unitOfWork, procedureRepository, pendingRepository, clock)

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
