package com.reynelbusto.paserevista.domain.usecase

import com.reynelbusto.paserevista.core.Clock
import com.reynelbusto.paserevista.core.newId
import com.reynelbusto.paserevista.domain.model.BoardColumn
import com.reynelbusto.paserevista.domain.model.CaseCard
import com.reynelbusto.paserevista.domain.model.ClinicalPriority
import com.reynelbusto.paserevista.domain.model.CustomField
import com.reynelbusto.paserevista.domain.model.JourneyOrigin
import com.reynelbusto.paserevista.domain.model.Patient
import com.reynelbusto.paserevista.domain.model.PatientState
import com.reynelbusto.paserevista.domain.model.Procedure
import com.reynelbusto.paserevista.domain.model.ProcedureState
import com.reynelbusto.paserevista.domain.model.displayName
import com.reynelbusto.paserevista.domain.repository.CaseCardRepository
import com.reynelbusto.paserevista.domain.repository.CaseHistoryRepository
import com.reynelbusto.paserevista.domain.repository.ClinicalUnitOfWork
import com.reynelbusto.paserevista.domain.repository.CustomFieldRepository
import com.reynelbusto.paserevista.domain.repository.DailyRecordRepository
import com.reynelbusto.paserevista.domain.repository.JourneyRepository
import com.reynelbusto.paserevista.domain.repository.PatientRepository
import com.reynelbusto.paserevista.domain.repository.PendingRepository
import com.reynelbusto.paserevista.domain.repository.ProcedureRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Servicio único del dispositivo (v1). */
const val DEFAULT_SERVICE_ID = "urology"

/**
 * Añadir cama: SOLO el número. REGLA DE ORO — ningún otro dato es obligatorio.
 * Crea el Patient mínimo + su tarjeta del día + entrada de historial.
 */
class AddBedUseCase(
    private val unitOfWork: ClinicalUnitOfWork,
    private val patients: PatientRepository,
    private val journeys: JourneyRepository,
    private val cards: CaseCardRepository,
    private val history: CaseHistoryRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(bed: String, serviceId: String = DEFAULT_SERVICE_ID): String =
        unitOfWork.atomic {
            require(bed.isNotBlank()) { "Indique el número de cama" }
            val now = clock.nowMillis()
            val patient = Patient(
                id = newId(),
                fullName = null,
                birthDate = null,
                sex = null,
                hcNumber = null,
                serviceId = serviceId,
                admissionDate = clock.todayIso(),
                mainDiagnosis = null,
                status = PatientState.ACTIVE,
                createdAt = now,
                updatedAt = now,
            )
            patients.createPatient(patient)
            val journey = journeys.getOrCreateJourney(
                serviceId, clock.todayIso(), JourneyOrigin.AUTO.code,
            )
            val card = CaseCard(
                id = newId(),
                patientId = patient.id,
                journeyId = journey.id,
                bed = bed.trim(),
                createdAt = now,
                updatedAt = now,
            )
            cards.create(card)
            history.log(patient.id, journey.id, "Cama ${bed.trim()} añadida")
            patient.id
        }
}

/**
 * Motor invisible: asegura la jornada de hoy y que cada paciente activo tenga
 * su tarjeta del día. Los valores se heredan de la última tarjeta (nada se
 * reescribe): solo se edita el delta.
 */
class EnsureDayUseCase(
    private val unitOfWork: ClinicalUnitOfWork,
    private val journeys: JourneyRepository,
    private val patients: PatientRepository,
    private val cards: CaseCardRepository,
    private val dailyRecords: DailyRecordRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(serviceId: String = DEFAULT_SERVICE_ID): String =
        unitOfWork.atomic {
            val today = clock.todayIso()
            val journey = journeys.getOrCreateJourney(serviceId, today, JourneyOrigin.AUTO.code)
            val now = clock.nowMillis()
            patients.getActivePatients(serviceId).forEach { patient ->
                if (cards.getByPatientAndJourney(patient.id, journey.id) == null) {
                    val prev = cards.findLatestBefore(patient.id, today)
                    // Compatibilidad v1: si no hay tarjeta previa, hereda la cama del DailyRecord.
                    val prevBed = prev?.bed
                        ?: dailyRecords.findLatestBefore(patient.id, today)?.bed
                    val card = CaseCard(
                        id = newId(),
                        patientId = patient.id,
                        journeyId = journey.id,
                        bed = prevBed ?: "?",
                        diagnosis = prev?.diagnosis,
                        scheduledProcedure = prev?.scheduledProcedure,
                        currentState = prev?.currentState,
                        antibiotic = prev?.antibiotic,
                        ready = prev?.ready ?: false,
                        createdAt = now,
                        updatedAt = now,
                    )
                    cards.create(card)
                }
            }
            journey.id
        }
}

/** Parche de edición de la tarjeta: todo opcional. */
data class CaseCardPatch(
    val diagnosis: String? = null,
    val scheduledProcedure: String? = null,
    val currentState: String? = null,
    val antibiotic: String? = null,
    val bed: String? = null,
    /** null = no tocar; true/false = fijar. null aquí significa "sin cambio". */
    val fieldsTouched: Set<String> = emptySet(),
)

/**
 * Operaciones sobre la tarjeta: edición con historial, alternar Pendiente/Listo
 * (sincroniza la pizarra) y datos del paciente (desplegable, todo opcional).
 */
class CaseCardUseCase(
    private val unitOfWork: ClinicalUnitOfWork,
    private val cards: CaseCardRepository,
    private val patients: PatientRepository,
    private val history: CaseHistoryRepository,
    private val board: BoardUseCase,
    private val clock: Clock,
) {
    suspend fun updateFields(cardId: String, patch: CaseCardPatch): CaseCard =
        unitOfWork.atomic {
            val card = cards.getById(cardId) ?: error("Tarjeta no encontrada")
            val changes = mutableListOf<String>()
            fun touched(name: String, old: String?, new: String?, label: String) {
                if (name in patch.fieldsTouched && old != new) {
                    changes += "$label: ${old ?: "—"} → ${new ?: "—"}"
                }
            }
            touched("diagnosis", card.diagnosis, patch.diagnosis, "Diagnóstico")
            touched("scheduledProcedure", card.scheduledProcedure, patch.scheduledProcedure, "Proceder")
            touched("currentState", card.currentState, patch.currentState, "Estado")
            touched("antibiotic", card.antibiotic, patch.antibiotic, "Antibiótico")
            touched("bed", card.bed, patch.bed, "Cama")
            val updated = card.copy(
                diagnosis = if ("diagnosis" in patch.fieldsTouched) patch.diagnosis else card.diagnosis,
                scheduledProcedure = if ("scheduledProcedure" in patch.fieldsTouched)
                    patch.scheduledProcedure else card.scheduledProcedure,
                currentState = if ("currentState" in patch.fieldsTouched)
                    patch.currentState else card.currentState,
                antibiotic = if ("antibiotic" in patch.fieldsTouched)
                    patch.antibiotic else card.antibiotic,
                bed = if ("bed" in patch.fieldsTouched && !patch.bed.isNullOrBlank())
                    patch.bed.trim() else card.bed,
            )
            cards.update(updated)
            if (changes.isNotEmpty()) {
                history.log(card.patientId, card.journeyId, changes.joinToString("; "))
            }
            updated
        }

    /** Alterna Pendiente ↔ Listo. Listo ⇒ sube a la pizarra; Pendiente ⇒ baja. */
    suspend fun toggleReady(cardId: String): Boolean = unitOfWork.atomic {
        val card = cards.getById(cardId) ?: error("Tarjeta no encontrada")
        val updated = card.copy(ready = !card.ready)
        cards.update(updated)
        if (updated.ready) {
            board.ensureFromCard(updated)
            history.log(card.patientId, card.journeyId, "Marcada LISTO — sube a la pizarra")
        } else {
            board.withdrawFromCard(updated.patientId)
            history.log(card.patientId, card.journeyId, "Vuelve a Pendiente — baja de la pizarra")
        }
        updated.ready
    }

    /** Datos del paciente (desplegable): todo opcional, sin requires. */
    suspend fun updatePatientDetails(
        patientId: String,
        fullName: String?,
        hcNumber: String?,
        bloodGroup: String?,
        address: String?,
        mainDiagnosis: String?,
        isOutOfService: Boolean?,
    ): Patient = unitOfWork.atomic {
        val patient = patients.getPatient(patientId) ?: error("Paciente no encontrado")
        val changes = mutableListOf<String>()
        if (fullName != null && fullName.trim() != (patient.fullName ?: "")) {
            changes += "Nombre: ${patient.fullName ?: "—"} → ${fullName.trim().ifBlank { "—" }}"
        }
        if (hcNumber != null && hcNumber.trim() != (patient.hcNumber ?: "")) {
            changes += "HC actualizada"
        }
        val updated = patient.copy(
            fullName = fullName?.trim()?.ifBlank { null } ?: patient.fullName,
            hcNumber = hcNumber?.trim()?.ifBlank { null } ?: patient.hcNumber,
            bloodGroup = bloodGroup?.trim()?.ifBlank { null } ?: patient.bloodGroup,
            address = address?.trim()?.ifBlank { null } ?: patient.address,
            mainDiagnosis = mainDiagnosis?.trim()?.ifBlank { null } ?: patient.mainDiagnosis,
            isOutOfService = isOutOfService ?: patient.isOutOfService,
        )
        patients.updatePatient(updated)
        if (changes.isNotEmpty()) history.log(patientId, null, changes.joinToString("; "))
        updated
    }
}

/**
 * Borrado total de un caso ("esto se cargó por error"): elimina tarjetas,
 * paciente, apartados personalizados, pendientes y procedimientos del
 * paciente en UNA transacción. Sin el paciente, la herencia de jornada
 * no puede resucitar el caso; sin el procedimiento, no queda fantasma
 * en la pizarra. El historial se conserva como auditoría.
 */
class DeleteCaseUseCase(
    private val unitOfWork: ClinicalUnitOfWork,
    private val patients: PatientRepository,
    private val cards: CaseCardRepository,
    private val fields: CustomFieldRepository,
    private val pendings: PendingRepository,
    private val procedures: ProcedureRepository,
) {
    suspend fun invoke(patientId: String) = unitOfWork.atomic {
        procedures.deleteByPatient(patientId)
        pendings.deleteByPatient(patientId)
        fields.deleteByPatient(patientId)
        cards.deleteByPatient(patientId)
        patients.delete(patientId)
    }
}

/** Apartados personalizados ("＋ Apartado"): nombre libre, renombrables. */
class CustomFieldUseCase(
    private val unitOfWork: ClinicalUnitOfWork,
    private val fields: CustomFieldRepository,
    private val history: CaseHistoryRepository,
    private val clock: Clock,
) {
    suspend fun add(patientId: String, label: String, value: String): CustomField =
        unitOfWork.atomic {
            require(label.isNotBlank()) { "El apartado necesita un nombre" }
            val now = clock.nowMillis()
            val field = CustomField(
                id = newId(),
                patientId = patientId,
                label = label.trim(),
                value = value.trim(),
                sortOrder = fields.countByPatient(patientId),
                createdAt = now,
                updatedAt = now,
            )
            fields.create(field)
            history.log(patientId, null, "Apartado «${field.label}» añadido")
            field
        }

    suspend fun rename(id: String, patientId: String, newLabel: String, current: CustomField) =
        unitOfWork.atomic {
            require(newLabel.isNotBlank()) { "El apartado necesita un nombre" }
            fields.update(current.copy(label = newLabel.trim()))
            history.log(patientId, null, "Apartado «${current.label}» → «${newLabel.trim()}»")
        }

    suspend fun setValue(id: String, value: String, current: CustomField) =
        unitOfWork.atomic {
            fields.update(current.copy(value = value))
        }

    suspend fun delete(id: String, patientId: String, label: String) = unitOfWork.atomic {
        fields.delete(id)
        history.log(patientId, null, "Apartado «$label» eliminado")
    }

    fun observe(patientId: String): Flow<List<CustomField>> = fields.observeByPatient(patientId)
}

/** Ítem de la pizarra con datos para mostrar. */
data class BoardItem(
    val procedure: Procedure,
    val displayName: String,
    val bed: String?,
)

/**
 * Pizarra Kanban: Propuesto → Programado → Realizado.
 * Se alimenta de las tarjetas marcadas Listo (un procedimiento activo por paciente).
 */
class BoardUseCase(
    private val unitOfWork: ClinicalUnitOfWork,
    private val procedures: ProcedureRepository,
    private val patients: PatientRepository,
    private val cards: CaseCardRepository,
    private val history: CaseHistoryRepository,
    private val clock: Clock,
) {
    fun observeBoard(): Flow<List<Procedure>> =
        combine(
            procedures.observeActiveAll(),
            procedures.observeRecentPerformed(),
        ) { active, done -> active + done }

    suspend fun enrich(procedure: Procedure): BoardItem {
        val patient = patients.getPatient(procedure.patientId)
        val card = cards.findLatestByPatient(procedure.patientId)
        return BoardItem(
            procedure = procedure,
            displayName = patient?.displayName(card?.bed) ?: "Cama ${card?.bed ?: "—"}",
            bed = card?.bed,
        )
    }

    /**
     * Columna Kanban de un procedimiento. TOTAL: estados no mapeados
     * (p. ej. CANCELED) devuelven null — el pipeline los omite,
     * nunca lanza, nunca tumba la app.
     */
    fun columnOf(procedure: Procedure): BoardColumn? =
        BoardColumn.entries.firstOrNull { it.state == procedure.status }

    /** La tarjeta marcada Listo asegura su procedimiento en Propuesto. */
    suspend fun ensureFromCard(card: CaseCard) = unitOfWork.atomic {
        if (procedures.findActiveByPatient(card.patientId) != null) return@atomic
        val now = clock.nowMillis()
        procedures.create(
            Procedure(
                id = newId(),
                patientId = card.patientId,
                kind = card.scheduledProcedure?.ifBlank { null } ?: "Proceder quirúrgico",
                status = ProcedureState.PENDING,
                priority = ClinicalPriority.P3,
                journeyId = card.journeyId,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    /** La tarjeta vuelve a Pendiente: el procedimiento sale de la pizarra. */
    suspend fun withdrawFromCard(patientId: String) = unitOfWork.atomic {
        val active = procedures.findActiveByPatient(patientId) ?: return@atomic
        procedures.update(active.copy(status = ProcedureState.CANCELED))
        history.log(patientId, null, "Proceder retirado de la pizarra")
    }

    suspend fun moveTo(procedureId: String, column: BoardColumn) = unitOfWork.atomic {
        val procedure = procedures.getProcedure(procedureId) ?: error("Procedimiento no encontrado")
        procedures.update(procedure.copy(status = column.state))
        history.log(
            procedure.patientId, null,
            "En pizarra: ${procedure.kind} → ${column.title}",
        )
    }
}

/** Datos para compartir una tarjeta. */
data class CardShareData(
    val bed: String,
    val ready: Boolean,
    val diagnosis: String?,
    val scheduledProcedure: String?,
    val openPendings: List<String>,
    val bloodGroup: String?,
    val currentState: String?,
    val antibiotic: String?,
    val patientName: String?,
    val hcNumber: String?,
    val address: String?,
    val customFields: List<Pair<String, String>> = emptyList(),
    val outOfService: Boolean = false,
    val hasInterconsult: Boolean = false,
)

/**
 * Formatea tarjetas para compartir por WhatsApp (texto plano vía Sharesheet).
 * Funciones puras: testeables sin Android.
 */
object ShareFormatter {
    private fun CardShareData.header(): String {
        val tag = if (ready) "[LISTO]" else "[PENDIENTE]"
        val name = patientName?.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""
        return "CAMA $bed$tag$name"
    }

    private fun CardShareData.bodyLines(): List<String> = buildList {
        diagnosis?.takeIf { it.isNotBlank() }?.let { add("Dx: $it") }
        scheduledProcedure?.takeIf { it.isNotBlank() }?.let { add("Proceder: $it") }
        if (openPendings.isNotEmpty()) add("Pendientes: ${openPendings.joinToString("; ")}")
        bloodGroup?.takeIf { it.isNotBlank() }?.let { add("Grupo: $it") }
        currentState?.takeIf { it.isNotBlank() }?.let { add("Estado: $it") }
        antibiotic?.takeIf { it.isNotBlank() }?.let { add("ATB: $it") }
        if (outOfService) add("⚠ Fuera de servicio")
        if (hasInterconsult) add("⚠ Interconsulta al servicio")
    }

    /** Tarjeta simple: lo visible en la tarjeta. */
    fun cardSimple(d: CardShareData): String =
        (listOf(d.header()) + d.bodyLines()).joinToString("\n")

    /** Caso completo: simple + datos del paciente + apartados. */
    fun cardComplete(d: CardShareData): String = buildList {
        add(d.header())
        addAll(d.bodyLines())
        val details = buildList {
            d.patientName?.takeIf { it.isNotBlank() }?.let { add("Nombre: $it") }
            d.hcNumber?.takeIf { it.isNotBlank() }?.let { add("HC: $it") }
            d.address?.takeIf { it.isNotBlank() }?.let { add("Dirección: $it") }
        }
        if (details.isNotEmpty()) {
            add("— Datos del paciente —")
            addAll(details)
        }
        d.customFields.forEach { (label, value) -> add("$label: $value") }
    }.joinToString("\n")

    /** Listado completo del día. */
    fun cardList(items: List<CardShareData>, dateLabel: String): String = buildList {
        add("ENTREGA DE GUARDIA — $dateLabel")
        add("")
        items.forEach { add(cardSimple(it) + "\n") }
    }.joinToString("\n").trimEnd()
}
