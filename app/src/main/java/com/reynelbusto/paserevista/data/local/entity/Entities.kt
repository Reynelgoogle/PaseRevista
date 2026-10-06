package com.reynelbusto.paserevista.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entidades Room. Los enums del dominio se persisten como TEXT (su `code`).
 * La validación de vocabulario vive en la capa de mapeo (domain), no en CHECKs SQL:
 * Room no genera restricciones CHECK y los valores inválidos se rechazan al mapear.
 */

@Entity(
    tableName = "patient",
    indices = [
        Index(value = ["service_id", "hc_number"], unique = true),
        Index(value = ["service_id", "status"]),
        Index(value = ["full_name"]),
    ],
)
data class PatientEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "full_name") val fullName: String,
    @ColumnInfo(name = "birth_date") val birthDate: String,
    val sex: String,
    @ColumnInfo(name = "hc_number") val hcNumber: String,
    @ColumnInfo(name = "blood_group") val bloodGroup: String?,
    @ColumnInfo(name = "service_id") val serviceId: String,
    @ColumnInfo(name = "admission_date") val admissionDate: String,
    @ColumnInfo(name = "admission_reason") val admissionReason: String?,
    @ColumnInfo(name = "main_diagnosis") val mainDiagnosis: String,
    val status: String,
    @ColumnInfo(name = "discharge_date") val dischargeDate: String?,
    @ColumnInfo(name = "discharge_reason") val dischargeReason: String?,
    @ColumnInfo(name = "previous_episode_id") val previousEpisodeId: String?,
    @ColumnInfo(name = "is_out_of_service") val isOutOfService: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long?,
)

@Entity(
    tableName = "patient_comorbidity",
    primaryKeys = ["patient_id", "label"],
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["id"],
            childColumns = ["patient_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["patient_id"])],
)
data class PatientComorbidityEntity(
    @ColumnInfo(name = "patient_id") val patientId: String,
    val label: String,
)

@Entity(
    tableName = "journey",
    indices = [
        Index(value = ["service_id", "clinical_date"], unique = true),
        Index(value = ["clinical_date"]),
        Index(value = ["status"]),
    ],
)
data class JourneyEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "clinical_date") val clinicalDate: String,
    @ColumnInfo(name = "service_id") val serviceId: String,
    val status: String,
    val origin: String,
    @ColumnInfo(name = "round_completed_at") val roundCompletedAt: Long?,
    @ColumnInfo(name = "review_summary") val reviewSummary: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "daily_record",
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["id"],
            childColumns = ["patient_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = JourneyEntity::class,
            parentColumns = ["id"],
            childColumns = ["journey_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["patient_id", "journey_id"], unique = true),
        Index(value = ["journey_id", "review_state"]),
        Index(value = ["patient_id"]),
    ],
)
data class DailyRecordEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "patient_id") val patientId: String,
    @ColumnInfo(name = "journey_id") val journeyId: String,
    val bed: String?,
    @ColumnInfo(name = "clinical_state") val clinicalState: String?,
    @ColumnInfo(name = "evolution_tap") val evolutionTap: String?,
    @ColumnInfo(name = "evolution_text") val evolutionText: String?,
    @ColumnInfo(name = "pain_scale") val painScale: Int?,
    @ColumnInfo(name = "has_fever") val hasFever: Boolean?,
    val diuresis: String?,
    @ColumnInfo(name = "other_signs") val otherSigns: String?,
    val priority: String?,
    @ColumnInfo(name = "discharge_planned") val dischargePlanned: Boolean,
    @ColumnInfo(name = "discharge_planned_date") val dischargePlannedDate: String?,
    val observations: String?,
    @ColumnInfo(name = "review_state") val reviewState: String,
    @ColumnInfo(name = "previous_record_id") val previousRecordId: String?,
    @ColumnInfo(name = "reviewed_at") val reviewedAt: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "pending",
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["id"],
            childColumns = ["patient_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["patient_id", "status"]),
        Index(value = ["status", "due_date"]),
        Index(value = ["type", "status"]),
        Index(value = ["idempotency_key"], unique = true),
    ],
)
data class PendingEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "patient_id") val patientId: String,
    val description: String,
    val type: String,
    val priority: String,
    val status: String,
    @ColumnInfo(name = "requested_at") val requestedAt: Long,
    @ColumnInfo(name = "due_date") val dueDate: String?,
    val assignee: String?,
    @ColumnInfo(name = "service_dest") val serviceDest: String?,
    @ColumnInfo(name = "interconsult_note") val interconsultNote: String?,
    @ColumnInfo(name = "journey_origin_id") val journeyOriginId: String?,
    @ColumnInfo(name = "journey_resolution_id") val journeyResolutionId: String?,
    @ColumnInfo(name = "resolved_at") val resolvedAt: Long?,
    @ColumnInfo(name = "cancel_reason") val cancelReason: String?,
    @ColumnInfo(name = "idempotency_key") val idempotencyKey: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "treatment",
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["id"],
            childColumns = ["patient_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["patient_id", "status"])],
)
data class TreatmentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "patient_id") val patientId: String,
    val drug: String,
    val dose: String,
    val route: String,
    val frequency: String,
    @ColumnInfo(name = "start_date") val startDate: String,
    @ColumnInfo(name = "end_date") val endDate: String?,
    val status: String,
    @ColumnInfo(name = "suspend_reason") val suspendReason: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "treatment_event",
    foreignKeys = [
        ForeignKey(
            entity = TreatmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["treatment_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["treatment_id", "occurred_at"])],
)
data class TreatmentEventEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "treatment_id") val treatmentId: String,
    @ColumnInfo(name = "journey_id") val journeyId: String?,
    @ColumnInfo(name = "event_type") val eventType: String,
    @ColumnInfo(name = "previous_value") val previousValue: String?,
    @ColumnInfo(name = "new_value") val newValue: String?,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    val note: String?,
)

@Entity(
    tableName = "device",
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["id"],
            childColumns = ["patient_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["patient_id", "status"])],
)
data class DeviceEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "patient_id") val patientId: String,
    val kind: String,
    val label: String?,
    @ColumnInfo(name = "placed_date") val placedDate: String?,
    val status: String,
    @ColumnInfo(name = "removed_at") val removedAt: Long?,
    @ColumnInfo(name = "removal_journey_id") val removalJourneyId: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "clinical_result",
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["id"],
            childColumns = ["patient_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["patient_id", "result_date"]),
        Index(value = ["daily_record_id"]),
    ],
)
data class ResultEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "patient_id") val patientId: String,
    @ColumnInfo(name = "daily_record_id") val dailyRecordId: String?,
    @ColumnInfo(name = "result_date") val resultDate: String,
    val type: String,
    val summary: String,
    val detail: String?,
    @ColumnInfo(name = "registered_journey_id") val registeredJourneyId: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "procedure",
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["id"],
            childColumns = ["patient_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["journey_id", "status"]),
        Index(value = ["scheduled_date", "status"]),
    ],
)
data class ProcedureEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "patient_id") val patientId: String,
    val kind: String,
    @ColumnInfo(name = "scheduled_date") val scheduledDate: String?,
    @ColumnInfo(name = "scheduled_time") val scheduledTime: String?,
    val status: String,
    val priority: String,
    @ColumnInfo(name = "journey_id") val journeyId: String?,
    @ColumnInfo(name = "performed_at") val performedAt: Long?,
    val note: String?,
    @ColumnInfo(name = "pending_id") val pendingId: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
