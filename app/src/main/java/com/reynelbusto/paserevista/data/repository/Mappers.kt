package com.reynelbusto.paserevista.data.repository

import com.reynelbusto.paserevista.data.local.entity.CaseCardEntity
import com.reynelbusto.paserevista.data.local.entity.CaseHistoryEntity
import com.reynelbusto.paserevista.data.local.entity.CustomFieldEntity
import com.reynelbusto.paserevista.data.local.entity.DailyRecordEntity
import com.reynelbusto.paserevista.domain.model.CaseCard
import com.reynelbusto.paserevista.domain.model.CaseHistoryEntry
import com.reynelbusto.paserevista.domain.model.CustomField
import com.reynelbusto.paserevista.data.local.entity.DeviceEntity
import com.reynelbusto.paserevista.data.local.entity.JourneyEntity
import com.reynelbusto.paserevista.data.local.entity.PatientEntity
import com.reynelbusto.paserevista.data.local.entity.PendingEntity
import com.reynelbusto.paserevista.data.local.entity.ProcedureEntity
import com.reynelbusto.paserevista.data.local.entity.ResultEntity
import com.reynelbusto.paserevista.data.local.entity.TreatmentEntity
import com.reynelbusto.paserevista.data.local.entity.TreatmentEventEntity
import com.reynelbusto.paserevista.domain.model.ClinicalEvolution
import com.reynelbusto.paserevista.domain.model.ClinicalPriority
import com.reynelbusto.paserevista.domain.model.ClinicalResult
import com.reynelbusto.paserevista.domain.model.ClinicalState
import com.reynelbusto.paserevista.domain.model.DailyRecord
import com.reynelbusto.paserevista.domain.model.Device
import com.reynelbusto.paserevista.domain.model.DeviceKind
import com.reynelbusto.paserevista.domain.model.DeviceState
import com.reynelbusto.paserevista.domain.model.Journey
import com.reynelbusto.paserevista.domain.model.JourneyOrigin
import com.reynelbusto.paserevista.domain.model.JourneyState
import com.reynelbusto.paserevista.domain.model.Patient
import com.reynelbusto.paserevista.domain.model.PatientState
import com.reynelbusto.paserevista.domain.model.Pending
import com.reynelbusto.paserevista.domain.model.PendingState
import com.reynelbusto.paserevista.domain.model.PendingType
import com.reynelbusto.paserevista.domain.model.Procedure
import com.reynelbusto.paserevista.domain.model.ProcedureState
import com.reynelbusto.paserevista.domain.model.ResultType
import com.reynelbusto.paserevista.domain.model.ReviewState
import com.reynelbusto.paserevista.domain.model.Sex
import com.reynelbusto.paserevista.domain.model.Treatment
import com.reynelbusto.paserevista.domain.model.TreatmentEvent
import com.reynelbusto.paserevista.domain.model.TreatmentEventType
import com.reynelbusto.paserevista.domain.model.TreatmentState

/**
 * Mapeo entidad ↔ dominio. Los enums viajan como `code` (TEXT) en la BD.
 * Un código desconocido es un error de datos: falla rápido en lugar de
 * inventar un valor por defecto (nunca "adivinar" un estado clínico).
 */
private fun String.toSex(): Sex = Sex.entries.first { it.code == this }
private fun String.toPatientState(): PatientState = PatientState.entries.first { it.code == this }
private fun String.toJourneyState(): JourneyState = JourneyState.entries.first { it.code == this }
private fun String.toJourneyOrigin(): JourneyOrigin = JourneyOrigin.entries.first { it.code == this }
private fun String.toReviewState(): ReviewState = ReviewState.entries.first { it.code == this }
private fun String.toClinicalEvolution(): ClinicalEvolution =
    ClinicalEvolution.entries.first { it.code == this }
private fun String.toClinicalState(): ClinicalState = ClinicalState.entries.first { it.code == this }
private fun String.toClinicalPriority(): ClinicalPriority =
    ClinicalPriority.entries.first { it.code == this }
private fun String.toPendingType(): PendingType = PendingType.entries.first { it.code == this }
private fun String.toPendingState(): PendingState = PendingState.entries.first { it.code == this }
private fun String.toTreatmentState(): TreatmentState =
    TreatmentState.entries.first { it.code == this }
private fun String.toTreatmentEventType(): TreatmentEventType =
    TreatmentEventType.entries.first { it.code == this }
private fun String.toDeviceKind(): DeviceKind = DeviceKind.entries.first { it.code == this }
private fun String.toDeviceState(): DeviceState = DeviceState.entries.first { it.code == this }
private fun String.toResultType(): ResultType = ResultType.entries.first { it.code == this }
private fun String.toProcedureState(): ProcedureState =
    ProcedureState.entries.first { it.code == this }

internal fun PatientEntity.toDomain(comorbidities: List<String> = emptyList()): Patient = Patient(
    id = id,
    fullName = fullName?.ifBlank { null },
    birthDate = birthDate?.ifBlank { null },
    sex = sex?.let { s -> Sex.entries.firstOrNull { it.code == s } },
    hcNumber = hcNumber?.ifBlank { null },
    bloodGroup = bloodGroup,
    allergies = allergies?.ifBlank { null },
    address = address?.ifBlank { null },
    serviceId = serviceId,
    admissionDate = admissionDate,
    admissionReason = admissionReason,
    mainDiagnosis = mainDiagnosis?.ifBlank { null },
    comorbidities = comorbidities,
    status = status.toPatientState(),
    dischargeDate = dischargeDate,
    dischargeReason = dischargeReason,
    previousEpisodeId = previousEpisodeId,
    isOutOfService = isOutOfService,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun Patient.toEntity(): PatientEntity = PatientEntity(
    id = id,
    fullName = fullName,
    birthDate = birthDate,
    sex = sex?.code,
    hcNumber = hcNumber,
    bloodGroup = bloodGroup,
    allergies = allergies,
    address = address,
    serviceId = serviceId,
    admissionDate = admissionDate,
    admissionReason = admissionReason,
    mainDiagnosis = mainDiagnosis,
    status = status.code,
    dischargeDate = dischargeDate,
    dischargeReason = dischargeReason,
    previousEpisodeId = previousEpisodeId,
    isOutOfService = isOutOfService,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = null,
)

internal fun CaseCardEntity.toDomain(): CaseCard = CaseCard(
    id = id,
    patientId = patientId,
    journeyId = journeyId,
    bed = bed,
    diagnosis = diagnosis,
    scheduledProcedure = scheduledProcedure,
    currentState = currentState,
    antibiotic = antibiotic,
    ready = ready,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun CaseCard.toEntity(): CaseCardEntity = CaseCardEntity(
    id = id,
    patientId = patientId,
    journeyId = journeyId,
    bed = bed,
    diagnosis = diagnosis,
    scheduledProcedure = scheduledProcedure,
    currentState = currentState,
    antibiotic = antibiotic,
    ready = ready,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun CustomFieldEntity.toDomain(): CustomField = CustomField(
    id = id,
    patientId = patientId,
    label = label,
    value = value,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun CustomField.toEntity(): CustomFieldEntity = CustomFieldEntity(
    id = id,
    patientId = patientId,
    label = label,
    value = value,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun CaseHistoryEntity.toDomain(): CaseHistoryEntry = CaseHistoryEntry(
    id = id,
    patientId = patientId,
    journeyId = journeyId,
    occurredAt = occurredAt,
    summary = summary,
)

internal fun JourneyEntity.toDomain(): Journey = Journey(
    id = id,
    clinicalDate = clinicalDate,
    serviceId = serviceId,
    status = status.toJourneyState(),
    origin = origin.toJourneyOrigin(),
    roundCompletedAt = roundCompletedAt,
    reviewSummary = reviewSummary,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun Journey.toEntity(): JourneyEntity = JourneyEntity(
    id = id,
    clinicalDate = clinicalDate,
    serviceId = serviceId,
    status = status.code,
    origin = origin.code,
    roundCompletedAt = roundCompletedAt,
    reviewSummary = reviewSummary,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun DailyRecordEntity.toDomain(): DailyRecord = DailyRecord(
    id = id,
    patientId = patientId,
    journeyId = journeyId,
    bed = bed,
    clinicalState = clinicalState?.toClinicalState(),
    evolutionTap = evolutionTap?.toClinicalEvolution(),
    evolutionText = evolutionText,
    painScale = painScale,
    hasFever = hasFever,
    diuresis = diuresis,
    otherSigns = otherSigns,
    priority = priority?.toClinicalPriority(),
    dischargePlanned = dischargePlanned,
    dischargePlannedDate = dischargePlannedDate,
    observations = observations,
    reviewState = reviewState.toReviewState(),
    previousRecordId = previousRecordId,
    reviewedAt = reviewedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun DailyRecord.toEntity(): DailyRecordEntity = DailyRecordEntity(
    id = id,
    patientId = patientId,
    journeyId = journeyId,
    bed = bed,
    clinicalState = clinicalState?.code,
    evolutionTap = evolutionTap?.code,
    evolutionText = evolutionText,
    painScale = painScale,
    hasFever = hasFever,
    diuresis = diuresis,
    otherSigns = otherSigns,
    priority = priority?.code,
    dischargePlanned = dischargePlanned,
    dischargePlannedDate = dischargePlannedDate,
    observations = observations,
    reviewState = reviewState.code,
    previousRecordId = previousRecordId,
    reviewedAt = reviewedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun PendingEntity.toDomain(): Pending = Pending(
    id = id,
    patientId = patientId,
    description = description,
    type = type.toPendingType(),
    priority = priority.toClinicalPriority(),
    status = status.toPendingState(),
    requestedAt = requestedAt,
    dueDate = dueDate,
    assignee = assignee,
    serviceDest = serviceDest,
    interconsultNote = interconsultNote,
    journeyOriginId = journeyOriginId,
    journeyResolutionId = journeyResolutionId,
    resolvedAt = resolvedAt,
    cancelReason = cancelReason,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun Pending.toEntity(idempotencyKey: String? = null): PendingEntity = PendingEntity(
    id = id,
    patientId = patientId,
    description = description,
    type = type.code,
    priority = priority.code,
    status = status.code,
    requestedAt = requestedAt,
    dueDate = dueDate,
    assignee = assignee,
    serviceDest = serviceDest,
    interconsultNote = interconsultNote,
    journeyOriginId = journeyOriginId,
    journeyResolutionId = journeyResolutionId,
    resolvedAt = resolvedAt,
    cancelReason = cancelReason,
    idempotencyKey = idempotencyKey,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun TreatmentEntity.toDomain(): Treatment = Treatment(
    id = id,
    patientId = patientId,
    drug = drug,
    dose = dose,
    route = route,
    frequency = frequency,
    startDate = startDate,
    endDate = endDate,
    status = status.toTreatmentState(),
    suspendReason = suspendReason,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun Treatment.toEntity(): TreatmentEntity = TreatmentEntity(
    id = id,
    patientId = patientId,
    drug = drug,
    dose = dose,
    route = route,
    frequency = frequency,
    startDate = startDate,
    endDate = endDate,
    status = status.code,
    suspendReason = suspendReason,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun TreatmentEventEntity.toDomain(): TreatmentEvent = TreatmentEvent(
    id = id,
    treatmentId = treatmentId,
    journeyId = journeyId,
    eventType = eventType.toTreatmentEventType(),
    previousValue = previousValue,
    newValue = newValue,
    occurredAt = occurredAt,
    note = note,
)

internal fun TreatmentEvent.toEntity(): TreatmentEventEntity = TreatmentEventEntity(
    id = id,
    treatmentId = treatmentId,
    journeyId = journeyId,
    eventType = eventType.code,
    previousValue = previousValue,
    newValue = newValue,
    occurredAt = occurredAt,
    note = note,
)

internal fun DeviceEntity.toDomain(): Device = Device(
    id = id,
    patientId = patientId,
    kind = kind.toDeviceKind(),
    label = label,
    placedDate = placedDate,
    status = status.toDeviceState(),
    removedAt = removedAt,
    removalJourneyId = removalJourneyId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun Device.toEntity(): DeviceEntity = DeviceEntity(
    id = id,
    patientId = patientId,
    kind = kind.code,
    label = label,
    placedDate = placedDate,
    status = status.code,
    removedAt = removedAt,
    removalJourneyId = removalJourneyId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun ResultEntity.toDomain(): ClinicalResult = ClinicalResult(
    id = id,
    patientId = patientId,
    dailyRecordId = dailyRecordId,
    resultDate = resultDate,
    type = type.toResultType(),
    summary = summary,
    detail = detail,
    registeredJourneyId = registeredJourneyId,
    createdAt = createdAt,
)

internal fun ClinicalResult.toEntity(): ResultEntity = ResultEntity(
    id = id,
    patientId = patientId,
    dailyRecordId = dailyRecordId,
    resultDate = resultDate,
    type = type.code,
    summary = summary,
    detail = detail,
    registeredJourneyId = registeredJourneyId,
    createdAt = createdAt,
)

internal fun ProcedureEntity.toDomain(): Procedure = Procedure(
    id = id,
    patientId = patientId,
    kind = kind,
    scheduledDate = scheduledDate,
    scheduledTime = scheduledTime,
    status = status.toProcedureState(),
    priority = priority.toClinicalPriority(),
    journeyId = journeyId,
    performedAt = performedAt,
    note = note,
    pendingId = pendingId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun Procedure.toEntity(): ProcedureEntity = ProcedureEntity(
    id = id,
    patientId = patientId,
    kind = kind,
    scheduledDate = scheduledDate,
    scheduledTime = scheduledTime,
    status = status.code,
    priority = priority.code,
    journeyId = journeyId,
    performedAt = performedAt,
    note = note,
    pendingId = pendingId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
