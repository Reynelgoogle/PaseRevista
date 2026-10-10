package com.reynelbusto.paserevista.presentation.camas

import com.reynelbusto.paserevista.di.AppContainer
import com.reynelbusto.paserevista.domain.model.CustomField
import com.reynelbusto.paserevista.domain.model.Sex
import com.reynelbusto.paserevista.domain.usecase.CaseCardPatch
import com.reynelbusto.paserevista.domain.usecase.DischargeUseCase
import com.reynelbusto.paserevista.presentation.components.PendingFormData

/**
 * Acciones clínicas sobre una tarjeta/paciente. Las comparten Camas y Agenda
 * para que editar desde cualquiera de las dos produzca exactamente lo mismo.
 */
class CardActions(private val container: AppContainer) {

    suspend fun toggleReady(cardId: String) = container.caseCards.toggleReady(cardId)

    suspend fun saveFields(cardId: String, patch: CaseCardPatch) =
        container.caseCards.updateFields(cardId, patch)

    suspend fun savePatientDetails(
        patientId: String,
        fullName: String?,
        hcNumber: String?,
        bloodGroup: String?,
        allergies: String?,
        address: String?,
        mainDiagnosis: String?,
        isOutOfService: Boolean?,
        sex: Sex?,
    ) = container.caseCards.updatePatientDetails(
        patientId, fullName, hcNumber, bloodGroup, allergies, address, mainDiagnosis, isOutOfService, sex,
    )

    suspend fun addCustomField(patientId: String, label: String, value: String) =
        container.customFields.add(patientId, label, value)

    suspend fun renameCustomField(field: CustomField, newLabel: String) =
        container.customFields.rename(field.id, field.patientId, newLabel, field)

    suspend fun setCustomFieldValue(field: CustomField, value: String) =
        container.customFields.setValue(field.id, value, field)

    suspend fun deleteCustomField(field: CustomField) =
        container.customFields.delete(field.id, field.patientId, field.label)

    suspend fun addPending(patientId: String, form: PendingFormData) =
        container.pendings.create(
            patientId = patientId,
            description = form.description,
            type = form.type,
            priority = form.priority,
            dueDate = form.dueDate?.ifBlank { null },
            assignee = form.assignee?.ifBlank { null },
            serviceDest = form.serviceDest?.ifBlank { null },
            interconsultNote = form.note?.ifBlank { null },
        )

    suspend fun completePending(pendingId: String) =
        container.pendings.complete(pendingId, journeyResolutionId = null)

    suspend fun deleteCase(patientId: String) = container.deleteCase.invoke(patientId)

    suspend fun checkDischarge(patientId: String): DischargeUseCase.Check =
        container.discharge.check(patientId)

    suspend fun discharge(patientId: String, force: Boolean = false) =
        container.discharge.discharge(patientId, force = force)
}
