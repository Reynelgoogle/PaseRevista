package com.reynelbusto.paserevista.domain.repository

import com.reynelbusto.paserevista.domain.model.CaseCard
import com.reynelbusto.paserevista.domain.model.CaseHistoryEntry
import com.reynelbusto.paserevista.domain.model.ClinicalResult
import com.reynelbusto.paserevista.domain.model.CustomField
import com.reynelbusto.paserevista.domain.model.DailyRecord
import com.reynelbusto.paserevista.domain.model.Device
import com.reynelbusto.paserevista.domain.model.Journey
import com.reynelbusto.paserevista.domain.model.Patient
import com.reynelbusto.paserevista.domain.model.Pending
import com.reynelbusto.paserevista.domain.model.Procedure
import com.reynelbusto.paserevista.domain.model.Treatment
import kotlinx.coroutines.flow.Flow

/** Contratos del dominio. Implementaciones en data/repository. */

interface PatientRepository {
    fun observeActivePatients(serviceId: String): Flow<List<Patient>>
    fun searchPatients(serviceId: String, query: String): Flow<List<Patient>>
    suspend fun getPatient(id: String): Patient?
    suspend fun createPatient(patient: Patient): String
    suspend fun updatePatient(patient: Patient)
    /** Censo activo para el motor de jornadas (suspend, no Flow). */
    suspend fun getActivePatients(serviceId: String): List<Patient>
    /** Anti-duplicados: busca por historia clínica dentro del servicio. */
    suspend fun findByHc(serviceId: String, hcNumber: String): Patient?
    /** Borrado total del caso ("se cargó por error"). */
    suspend fun delete(id: String)
}

interface JourneyRepository {
    fun observeCurrentJourney(serviceId: String, clinicalDate: String): Flow<Journey?>
    suspend fun getJourney(serviceId: String, clinicalDate: String): Journey?
    /** Idempotente: si ya existe para (servicio, fecha), devuelve la existente. */
    suspend fun getOrCreateJourney(serviceId: String, clinicalDate: String, originCode: String): Journey
    /** Jornada anterior más reciente (para AYER y continuidad). */
    suspend fun findPreviousJourney(serviceId: String, clinicalDate: String): Journey?
    /** Historial de jornadas del servicio, recientes primero. */
    fun observeJourneys(serviceId: String): Flow<List<Journey>>
}

interface DailyRecordRepository {
    fun observeByJourney(journeyId: String): Flow<List<DailyRecord>>
    suspend fun getByPatientAndJourney(patientId: String, journeyId: String): DailyRecord?
    suspend fun create(record: DailyRecord): String
    suspend fun update(record: DailyRecord)
    suspend fun createAll(records: List<DailyRecord>)
    /** Registro más reciente del paciente anterior a una fecha (referencia AYER). */
    suspend fun findLatestBefore(patientId: String, clinicalDate: String): DailyRecord?
    fun observeByPatient(patientId: String): Flow<List<DailyRecord>>
}

interface PendingRepository {
    fun observeOpenByPatient(patientId: String): Flow<List<Pending>>
    fun observeOpenByService(serviceId: String): Flow<List<Pending>>
    fun observeRecentlyClosedByService(serviceId: String): Flow<List<Pending>>
    suspend fun getOpenByPatient(patientId: String): List<Pending>
    suspend fun getPending(id: String): Pending?
    suspend fun create(pending: Pending): String
    suspend fun update(pending: Pending)
    suspend fun deleteByPatient(patientId: String)
}

interface TreatmentRepository {
    fun observeActiveByPatient(patientId: String): Flow<List<Treatment>>
    suspend fun getTreatment(id: String): Treatment?
    suspend fun create(treatment: Treatment): String
    suspend fun update(treatment: Treatment)
    fun observeEvents(treatmentId: String): Flow<List<com.reynelbusto.paserevista.domain.model.TreatmentEvent>>
    suspend fun addEvent(event: com.reynelbusto.paserevista.domain.model.TreatmentEvent)
}

interface DeviceRepository {
    fun observeActiveByPatient(patientId: String): Flow<List<Device>>
    suspend fun getDevice(id: String): Device?
    suspend fun create(device: Device): String
    suspend fun update(device: Device)
}

interface ResultRepository {
    fun observeByPatient(patientId: String): Flow<List<ClinicalResult>>
    suspend fun create(result: ClinicalResult): String
}

interface ProcedureRepository {
    fun observeByJourney(journeyId: String): Flow<List<Procedure>>
    fun observeActiveAll(): Flow<List<Procedure>>
    fun observeRecentPerformed(limit: Int = 50): Flow<List<Procedure>>
    suspend fun getProcedure(id: String): Procedure?
    suspend fun findActiveByPatient(patientId: String): Procedure?
    suspend fun create(procedure: Procedure): String
    suspend fun update(procedure: Procedure)
    suspend fun deleteByPatient(patientId: String)
}

/** Tarjetas de cama (pivot entrega de guardia). */
interface CaseCardRepository {
    fun observeByJourney(journeyId: String): Flow<List<CaseCard>>
    suspend fun getByPatientAndJourney(patientId: String, journeyId: String): CaseCard?
    suspend fun getById(id: String): CaseCard?
    suspend fun findLatestBefore(patientId: String, clinicalDate: String): CaseCard?
    suspend fun findLatestByPatient(patientId: String): CaseCard?
    suspend fun create(card: CaseCard): String
    suspend fun update(card: CaseCard)
    suspend fun deleteByPatient(patientId: String)
    /** Conteos para el sidecar del respaldo. */
    suspend fun countAll(): Int
    suspend fun countDistinctBeds(): Int
    /** Últimos diagnósticos usados (sugerencias al editar la tarjeta). */
    suspend fun recentDiagnoses(limit: Int = 8): List<String>
}

/** Apartados personalizados de la tarjeta. */
interface CustomFieldRepository {
    fun observeByPatient(patientId: String): Flow<List<CustomField>>
    suspend fun create(field: CustomField): String
    suspend fun update(field: CustomField)
    suspend fun delete(id: String)
    suspend fun deleteByPatient(patientId: String)
    suspend fun countByPatient(patientId: String): Int
}

/** Historial de cambios de la tarjeta (append-only). */
interface CaseHistoryRepository {
    fun observeByPatient(patientId: String): Flow<List<CaseHistoryEntry>>
    suspend fun log(patientId: String, journeyId: String?, summary: String)
}
