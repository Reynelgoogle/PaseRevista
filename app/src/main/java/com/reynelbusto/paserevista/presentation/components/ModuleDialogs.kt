package com.reynelbusto.paserevista.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reynelbusto.paserevista.core.IsoDate
import com.reynelbusto.paserevista.domain.model.ClinicalOptions
import com.reynelbusto.paserevista.domain.model.ClinicalPriority
import com.reynelbusto.paserevista.domain.model.DeviceKind
import com.reynelbusto.paserevista.domain.model.Patient
import com.reynelbusto.paserevista.domain.model.PendingType
import com.reynelbusto.paserevista.domain.model.ResultType
import com.reynelbusto.paserevista.presentation.theme.TextSecondary
import androidx.compose.material3.MaterialTheme

/** Selector de paciente para diálogos creados fuera de una ficha. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientPicker(
    patients: List<Patient>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = patients.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected?.fullName ?: "Sin nombre",
            onValueChange = {},
            readOnly = true,
            label = { Text("Paciente") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(type = androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            patients.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.fullName ?: "Sin nombre") },
                    onClick = { onSelect(p.id); expanded = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PriorityPicker(selected: ClinicalPriority, onSelect: (ClinicalPriority) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ClinicalPriority.entries.forEach { p ->
            FilterChip(
                selected = selected == p,
                onClick = { onSelect(p) },
                label = { Text(p.name) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PendingTypePicker(selected: PendingType, onSelect: (PendingType) -> Unit) {
    val labels = mapOf(
        PendingType.GENERAL to "General",
        PendingType.STUDY to "Estudio",
        PendingType.PROCEDURE to "Procedimiento",
        PendingType.INTERCONSULTATION to "Interconsulta",
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PendingType.entries.forEach { t ->
            FilterChip(
                selected = selected == t,
                onClick = { onSelect(t) },
                label = { Text(labels[t] ?: t.name) },
            )
        }
    }
}

data class PendingFormData(
    val patientId: String?,
    val description: String,
    val type: PendingType,
    val priority: ClinicalPriority,
    val dueDate: IsoDate?,
    val assignee: String?,
    val serviceDest: String?,
    val note: String?,
)

/** Diálogo para crear un pendiente (desde ficha o desde Pendientes). */
@Composable
fun AddPendingDialog(
    patients: List<Patient>,
    preselectedPatientId: String?,
    onDismiss: () -> Unit,
    onConfirm: (PendingFormData) -> Unit,
) {
    var patientId by remember { mutableStateOf(preselectedPatientId) }
    var description by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(PendingType.GENERAL) }
    var priority by remember { mutableStateOf(ClinicalPriority.P3) }
    var dueDate by remember { mutableStateOf("") }
    var assignee by remember { mutableStateOf("") }
    var serviceDest by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val valid = patientId != null && description.isNotBlank() &&
        (type != PendingType.INTERCONSULTATION || serviceDest.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo pendiente") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (preselectedPatientId == null) {
                    PatientPicker(patients, patientId, { patientId = it })
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción *") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Tipo", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                PendingTypePicker(type, { type = it })
                Text("Prioridad", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                PriorityPicker(priority, { priority = it })
                DatePickerField(
                    label = "Fecha objetivo",
                    isoDate = dueDate.ifBlank { null },
                    onSelect = { dueDate = it ?: "" },
                )
                OutlinedTextField(
                    value = assignee,
                    onValueChange = { assignee = it },
                    label = { Text("Responsable") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (type == PendingType.INTERCONSULTATION) {
                    OptionField(
                        label = "Servicio destino",
                        options = ClinicalOptions.INTERCONSULT_SERVICES,
                        value = serviceDest.ifBlank { null },
                        onValueChange = { serviceDest = it ?: "" },
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Motivo de interconsulta") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        PendingFormData(
                            patientId = patientId,
                            description = description.trim(),
                            type = type,
                            priority = priority,
                            dueDate = dueDate.ifBlank { null },
                            assignee = assignee.ifBlank { null },
                            serviceDest = serviceDest.ifBlank { null },
                            note = note.ifBlank { null },
                        ),
                    )
                },
                enabled = valid,
            ) { Text("Crear") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

data class TreatmentFormData(
    val drug: String,
    val dose: String,
    val route: String,
    val frequency: String,
)

/** Diálogo para iniciar un tratamiento. */
@Composable
fun AddTreatmentDialog(onDismiss: () -> Unit, onConfirm: (TreatmentFormData) -> Unit) {
    var drug by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("") }
    var route by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf("") }
    val valid = drug.isNotBlank() && dose.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo tratamiento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = drug, onValueChange = { drug = it }, label = { Text("Medicamento *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = dose, onValueChange = { dose = it }, label = { Text("Dosis *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = route, onValueChange = { route = it }, label = { Text("Vía") }, placeholder = { Text("Ej.: IV, VO, IM") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = frequency, onValueChange = { frequency = it }, label = { Text("Frecuencia") }, placeholder = { Text("Ej.: cada 8 h") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(TreatmentFormData(drug.trim(), dose.trim(), route.trim(), frequency.trim())) },
                enabled = valid,
            ) { Text("Iniciar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

data class DeviceFormData(val kind: DeviceKind, val label: String?)

/** Diálogo para registrar un dispositivo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddDeviceDialog(onDismiss: () -> Unit, onConfirm: (DeviceFormData) -> Unit) {
    var kind by remember { mutableStateOf(DeviceKind.FOLEY_CATHETER) }
    var label by remember { mutableStateOf("") }
    val labels = mapOf(
        DeviceKind.FOLEY_CATHETER to "Sonda vesical",
        DeviceKind.JJ_STENT to "Sonda JJ",
        DeviceKind.DRAIN to "Drenaje",
        DeviceKind.CATHETER to "Catéter",
        DeviceKind.STENT to "Stent",
        DeviceKind.OTHER to "Otro",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo dispositivo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeviceKind.entries.forEach { k ->
                        FilterChip(
                            selected = kind == k,
                            onClick = { kind = k },
                            label = { Text(labels[k] ?: k.name) },
                        )
                    }
                }
                OutlinedTextField(
                    value = label, onValueChange = { label = it },
                    label = { Text("Etiqueta (opcional)") },
                    placeholder = { Text("Ej.: lado derecho") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(DeviceFormData(kind, label.ifBlank { null })) }) { Text("Registrar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

data class ResultFormData(val type: ResultType, val resultDate: IsoDate, val summary: String, val detail: String?)

/** Diálogo para registrar un resultado (soporta tardíos con fecha real). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddResultDialog(
    defaultDate: IsoDate,
    onDismiss: () -> Unit,
    onConfirm: (ResultFormData) -> Unit,
) {
    var type by remember { mutableStateOf(ResultType.LAB) }
    var resultDate by remember { mutableStateOf(defaultDate) }
    var summary by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    val labels = mapOf(
        ResultType.LAB to "Laboratorio",
        ResultType.IMAGING to "Imagen",
        ResultType.ANATOMY to "Anatomía patológica",
        ResultType.MICRO to "Microbiología",
        ResultType.OTHER to "Otro",
    )
    val valid = summary.isNotBlank() && resultDate.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo resultado") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ResultType.entries.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(labels[t] ?: t.name) })
                    }
                }
                OutlinedTextField(
                    value = resultDate, onValueChange = { resultDate = it },
                    label = { Text("Fecha del resultado (aaaa-mm-dd)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                OutlinedTextField(
                    value = summary, onValueChange = { summary = it },
                    label = { Text("Resumen *") }, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = detail, onValueChange = { detail = it },
                    label = { Text("Detalle (opcional)") }, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(ResultFormData(type, resultDate.trim(), summary.trim(), detail.ifBlank { null }))
                },
                enabled = valid,
            ) { Text("Registrar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

data class ProcedureFormData(
    val patientId: String?,
    val kind: String,
    val scheduledDate: IsoDate?,
    val scheduledTime: String?,
    val priority: ClinicalPriority,
    val note: String?,
)

/** Diálogo para agendar un procedimiento en pizarra. */
@Composable
fun AddProcedureDialog(
    patients: List<Patient>,
    preselectedPatientId: String?,
    defaultDate: IsoDate,
    onDismiss: () -> Unit,
    onConfirm: (ProcedureFormData) -> Unit,
) {
    var patientId by remember { mutableStateOf(preselectedPatientId) }
    var kind by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(defaultDate) }
    var time by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(ClinicalPriority.P3) }
    var note by remember { mutableStateOf("") }
    val valid = patientId != null && kind.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agendar procedimiento") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (preselectedPatientId == null) {
                    PatientPicker(patients, patientId, { patientId = it })
                }
                OutlinedTextField(value = kind, onValueChange = { kind = it }, label = { Text("Procedimiento *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Fecha (aaaa-mm-dd)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = time, onValueChange = { time = it }, label = { Text("Hora (opcional)") }, placeholder = { Text("Ej.: 08:30") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("Prioridad", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                PriorityPicker(priority, { priority = it })
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Nota (opcional)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        ProcedureFormData(
                            patientId = patientId, kind = kind.trim(),
                            scheduledDate = date.ifBlank { null }, scheduledTime = time.ifBlank { null },
                            priority = priority, note = note.ifBlank { null },
                        ),
                    )
                },
                enabled = valid,
            ) { Text("Agendar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
