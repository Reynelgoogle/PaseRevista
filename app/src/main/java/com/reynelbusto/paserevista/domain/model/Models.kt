package com.reynelbusto.paserevista.domain.model

import com.reynelbusto.paserevista.core.IsoDate

/**
 * Modelos de dominio. Separados de las entidades Room a propósito:
 * el dominio habla en enums tipados y nunca en códigos TEXT.
 */

/** Paciente: identidad clínica relativamente estable.
 * Pivot entrega de guardia — REGLA DE ORO: ningún dato es obligatorio.
 * Los campos personales pueden ser null hasta que se conozcan. */
data class Patient(
    val id: String,
    val fullName: String?,
    val birthDate: IsoDate?,
    val sex: Sex?,
    val hcNumber: String?,
    val bloodGroup: String? = null,
    val address: String? = null,
    val serviceId: String,
    val admissionDate: IsoDate,
    val admissionReason: String? = null,
    val mainDiagnosis: String?,
    val comorbidities: List<String> = emptyList(),
    val status: PatientState = PatientState.ACTIVE,
    val dischargeDate: IsoDate? = null,
    val dischargeReason: String? = null,
    /** Episodio anterior en readmisiones: enlaza sin duplicar identidad. */
    val previousEpisodeId: String? = null,
    val isOutOfService: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Tarjeta de cama: el corazón de la app de entrega de guardia.
 * Una por paciente y jornada. Al abrir una jornada nueva los valores se heredan
 * (nada se reescribe): solo se edita el delta.
 */
data class CaseCard(
    val id: String,
    val patientId: String,
    val journeyId: String,
    val bed: String,
    val diagnosis: String? = null,
    val scheduledProcedure: String? = null,
    val currentState: String? = null,
    val antibiotic: String? = null,
    /** Etiqueta Pendiente/Listo. Listo ⇒ la tarjeta sube a la pizarra Kanban. */
    val ready: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Apartado personalizado de la tarjeta ("＋ Apartado"): nombre libre, renombrable. */
data class CustomField(
    val id: String,
    val patientId: String,
    val label: String,
    val value: String,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Entrada del historial de la tarjeta: qué cambió y cuándo (agenda real). */
data class CaseHistoryEntry(
    val id: String,
    val patientId: String,
    val journeyId: String?,
    val occurredAt: Long,
    val summary: String,
)

/** Columnas de la pizarra Kanban. Mapean a ProcedureState. */
enum class BoardColumn(val title: String, val state: ProcedureState) {
    PROPOSED("Propuesto", ProcedureState.PENDING),
    SCHEDULED("Programado", ProcedureState.PREPARATION),
    DONE("Realizado", ProcedureState.PERFORMED),
}

/** Nombre visible del paciente: nombre si se conoce, si no "Cama N". */
fun Patient.displayName(bed: String?): String =
    fullName?.takeIf { it.isNotBlank() } ?: "Cama ${bed ?: "—"}"

/** Descripción visible del pendiente: la cargada, o aviso si va vacía. */
fun Pending.displayDescription(): String =
    description.takeIf { it.isNotBlank() } ?: "(sin descripción)"

/** Jornada: el pase de revista de un día concreto del servicio. */
data class Journey(
    val id: String,
    val clinicalDate: IsoDate,
    val serviceId: String,
    val status: JourneyState = JourneyState.CURRENT,
    val origin: JourneyOrigin,
    /** Hito informativo "pase completado". NULL = en curso. No es un estado bloqueante. */
    val roundCompletedAt: Long? = null,
    val reviewSummary: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Registro diario: estado clínico de un paciente durante una jornada.
 * HERENCIA≠COPIA: los clínicos (estado, signos, evolución, prioridad, observaciones)
 * nacen NULL. NULL = "no informado hoy", distinto de "normal".
 */
data class DailyRecord(
    val id: String,
    val patientId: String,
    val journeyId: String,
    /** La cama pertenece al registro diario: puede cambiar cada día. */
    val bed: String? = null,
    val clinicalState: ClinicalState? = null,
    val evolutionTap: ClinicalEvolution? = null,
    val evolutionText: String? = null,
    val painScale: Int? = null, // 0..10
    val hasFever: Boolean? = null,
    val diuresis: String? = null,
    val otherSigns: String? = null,
    val priority: ClinicalPriority? = null,
    val dischargePlanned: Boolean = false,
    val dischargePlannedDate: IsoDate? = null,
    val observations: String? = null,
    val reviewState: ReviewState = ReviewState.PENDING,
    val previousRecordId: String? = null,
    val reviewedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Pendiente: entidad longitudinal con identidad única entre jornadas. */
data class Pending(
    val id: String,
    val patientId: String,
    val description: String,
    val type: PendingType = PendingType.GENERAL,
    val priority: ClinicalPriority = ClinicalPriority.P3,
    val status: PendingState = PendingState.PENDING,
    val requestedAt: Long,
    val dueDate: IsoDate? = null,
    val assignee: String? = null,
    /** Solo interconsulta. */
    val serviceDest: String? = null,
    val interconsultNote: String? = null,
    val journeyOriginId: String? = null,
    val journeyResolutionId: String? = null,
    val resolvedAt: Long? = null,
    val cancelReason: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Tratamiento: entidad longitudinal. El día se deriva, nunca se almacena. */
data class Treatment(
    val id: String,
    val patientId: String,
    val drug: String,
    val dose: String,
    val route: String,
    val frequency: String,
    val startDate: IsoDate,
    val endDate: IsoDate? = null,
    val status: TreatmentState = TreatmentState.ACTIVE,
    val suspendReason: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

data class TreatmentEvent(
    val id: String,
    val treatmentId: String,
    val journeyId: String? = null,
    val eventType: TreatmentEventType,
    val previousValue: String? = null, // snapshot JSON {drug,dose,route,frequency}
    val newValue: String? = null,
    val occurredAt: Long,
    val note: String? = null,
)

/** Dispositivo: una sola entidad con ciclo de vida. Nunca una copia diaria. */
data class Device(
    val id: String,
    val patientId: String,
    val kind: DeviceKind,
    val label: String? = null,
    val placedDate: IsoDate? = null,
    val status: DeviceState = DeviceState.ACTIVE,
    val removedAt: Long? = null,
    val removalJourneyId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Resultado clínico con fecha clínica propia (soporta tardíos). */
data class ClinicalResult(
    val id: String,
    val patientId: String,
    val dailyRecordId: String? = null,
    val resultDate: IsoDate,
    val type: ResultType,
    val summary: String,
    val detail: String? = null,
    val registeredJourneyId: String? = null,
    val createdAt: Long,
)

/** Procedimiento: lo que vive en la pizarra quirúrgica. */
data class Procedure(
    val id: String,
    val patientId: String,
    val kind: String,
    val scheduledDate: IsoDate? = null,
    val scheduledTime: String? = null,
    val status: ProcedureState = ProcedureState.PENDING,
    val priority: ClinicalPriority = ClinicalPriority.P3,
    val journeyId: String? = null,
    val performedAt: Long? = null,
    val note: String? = null,
    /** Pendiente que originó/agenda este procedimiento (misma tarea exacta). */
    val pendingId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
