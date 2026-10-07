package com.reynelbusto.paserevista.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.reynelbusto.paserevista.domain.model.ClinicalOptions
import com.reynelbusto.paserevista.domain.model.CustomField
import com.reynelbusto.paserevista.domain.model.Sex
import com.reynelbusto.paserevista.domain.model.displayDescription
import com.reynelbusto.paserevista.domain.usecase.CaseCardPatch
import com.reynelbusto.paserevista.presentation.camas.CardRow
import com.reynelbusto.paserevista.presentation.theme.TextSecondary
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.reynelbusto.paserevista.presentation.theme.ClinicalCritical
import com.reynelbusto.paserevista.presentation.theme.ClinicalStable
import com.reynelbusto.paserevista.presentation.theme.TextPrimary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Tarjeta de cama: el corazón de la app.
 * Vista simple siempre visible + desplegables (datos del paciente, historial).
 * REGLA DE ORO: ningún campo es obligatorio.
 */
@Composable
fun CaseCardView(
    row: CardRow,
    displayName: String,
    recentDiagnoses: List<String>,
    onToggleReady: () -> Unit,
    onSaveFields: (CaseCardPatch) -> Unit,
    onSavePatientDetails: (
        fullName: String?, hcNumber: String?, bloodGroup: String?,
        address: String?, mainDiagnosis: String?, isOutOfService: Boolean?,
        sex: Sex?,
    ) -> Unit,
    onAddCustomField: (label: String, value: String) -> Unit,
    onRenameCustomField: (CustomField, String) -> Unit,
    onSetCustomFieldValue: (CustomField, String) -> Unit,
    onDeleteCustomField: (CustomField) -> Unit,
    onAddPending: (PendingFormData) -> Unit,
    onCompletePending: (String) -> Unit,
    onDischarge: () -> Unit,
    onShare: (complete: Boolean) -> Unit,
    onDeleteCase: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val card = row.card
    var showPatientData by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showEditFields by remember { mutableStateOf(false) }
    var showAddField by remember { mutableStateOf(false) }
    var showPendings by remember { mutableStateOf(false) }
    var showShareChoice by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showDischarge by remember { mutableStateOf(false) }
    var showDeleteCase by remember { mutableStateOf(false) }
    var renameField by remember { mutableStateOf<CustomField?>(null) }
    var expanded by remember { mutableStateOf(false) }

    val accent = if (card.ready) ClinicalStable else ClinicalCritical
    if (expanded) {
        // ── Tarjeta expandida nítida (propuesta 2): cabecera + filas
        // etiqueta/valor con divisores; pendientes y grupo en rojo negrita.
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(Modifier.padding(16.dp)) {
                // Cabecera: tocarla contrae la tarjeta.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = false },
                ) {
                    Text(
                        text = "CAMA ${card.bed}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    Spacer(Modifier.width(10.dp))
                    StatusPill(ready = card.ready)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Opciones")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Editar tarjeta") },
                            onClick = { showMenu = false; showEditFields = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Datos del paciente") },
                            onClick = { showMenu = false; showPatientData = true },
                        )
                        DropdownMenuItem(
                            text = { Text("＋ Apartado") },
                            onClick = { showMenu = false; showAddField = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Historial") },
                            onClick = { showMenu = false; showHistory = !showHistory },
                        )
                        DropdownMenuItem(
                            text = { Text("Dar de alta") },
                            onClick = { showMenu = false; showDischarge = true },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Borrar tarjeta",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = { showMenu = false; showDeleteCase = true },
                        )
                    }
                }
                if (displayName != "Cama ${card.bed}") {
                    Text(
                        displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                    )
                }
                MarcasRow(row)
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                DetailRow("Diagnóstico", card.diagnosis)
                DetailRow("Proceder programado", card.scheduledProcedure)
                DetailRow(
                    label = "Pendientes",
                    value = pendingsSummary(row),
                    highlight = row.openPendings.isNotEmpty(),
                    onClick = { showPendings = true },
                )
                DetailRow(
                    label = "Grupo sanguíneo",
                    value = row.patient.bloodGroup,
                    highlight = !row.patient.bloodGroup.isNullOrBlank(),
                )
                DetailRow("Estado actual", card.currentState)
                DetailRow("Antibiótico", card.antibiotic)
                // Apartados personalizados.
                row.customFields.forEach { field ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            DetailRow(field.label, field.value.ifBlank { "—" })
                        }
                        IconButton(onClick = { renameField = field }) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Renombrar apartado",
                                tint = TextSecondary,
                            )
                        }
                    }
                }
                // Historial desplegable.
                if (showHistory) {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text(
                        "Historial",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (row.history.isEmpty()) {
                        Text(
                            "Sin cambios registrados.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    } else {
                        row.history.forEach { entry ->
                            Text(
                                "• ${formatDay(entry.occurredAt)} — ${entry.summary}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                // Acciones principales.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onToggleReady,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (card.ready) "Volver a pendiente" else "✓ Marcar Listo")
                    }
                    OutlinedButton(
                        onClick = { showShareChoice = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Compartir")
                    }
                }
            }
        }
    } else {
        // ── Vista colapsada tipo lista (propuesta 2): franja de color a la
        // izquierda (roja = pendiente, verde = lista); tocarla abre la tarjeta.
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true },
            ) {
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .fillMaxHeight()
                        .background(accent),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "CAMA ${card.bed}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    val summary = listOfNotNull(
                        card.diagnosis?.takeIf { it.isNotBlank() },
                        card.scheduledProcedure?.takeIf { it.isNotBlank() },
                    ).joinToString(" · ")
                    Text(
                        text = summary.ifBlank { "—" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    val pendCount = row.openPendings.size
                    Text(
                        text = if (card.ready) {
                            "✓ LISTO"
                        } else if (pendCount > 0) {
                            "⚠ $pendCount pendiente${if (pendCount == 1) "" else "s"}"
                        } else {
                            "Pendiente"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                    )
                    MarcasRow(row)
                }
            }
        }
    }
    if (showEditFields) {
        EditFieldsDialog(
            card = row.card,
            recentDiagnoses = recentDiagnoses,
            onDismiss = { showEditFields = false },
            onConfirm = { patch -> showEditFields = false; onSaveFields(patch) },
        )
    }
    if (showPatientData) {
        PatientDataDialog(
            patient = row.patient,
            onDismiss = { showPatientData = false },
            onConfirm = { name, hc, group, addr, dx, out, sex ->
                showPatientData = false
                onSavePatientDetails(name, hc, group, addr, dx, out, sex)
            },
        )
    }
    if (showAddField) {
        CustomFieldDialog(
            title = "Nuevo apartado",
            onDismiss = { showAddField = false },
            onConfirm = { label, value -> showAddField = false; onAddCustomField(label, value) },
        )
    }
    renameField?.let { field ->
        var newLabel by remember(field.id) { mutableStateOf(field.label) }
        var newValue by remember(field.id) { mutableStateOf(field.value) }
        var confirmDelete by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { renameField = null },
            title = { Text("Apartado") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newLabel,
                        onValueChange = { newLabel = it },
                        label = { Text("Nombre del apartado") },
                    )
                    OutlinedTextField(
                        value = newValue,
                        onValueChange = { newValue = it },
                        label = { Text("Valor") },
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newLabel.isNotBlank() && newLabel != field.label) {
                        onRenameCustomField(field, newLabel)
                    }
                    if (newValue != field.value) onSetCustomFieldValue(field, newValue)
                    renameField = null
                }) { Text("Guardar") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Eliminar")
                    }
                    TextButton(onClick = { renameField = null }) { Text("Cerrar") }
                }
            },
        )
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Eliminar apartado") },
                text = { Text("¿Eliminar «${field.label}»?") },
                confirmButton = {
                    Button(onClick = {
                        confirmDelete = false
                        renameField = null
                        onDeleteCustomField(field)
                    }) { Text("Eliminar") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
                },
            )
        }
    }
    if (showPendings) {
        PendingsSheet(
            row = row,
            onDismiss = { showPendings = false },
            onAddPending = onAddPending,
            onCompletePending = onCompletePending,
        )
    }
    if (showShareChoice) {
        AlertDialog(
            onDismissRequest = { showShareChoice = false },
            title = { Text("Compartir tarjeta") },
            text = { Text("¿Versión simple o caso completo?") },
            confirmButton = {
                Button(onClick = { showShareChoice = false; onShare(false) }) {
                    Text("Simple")
                }
            },
            dismissButton = {
                Button(onClick = { showShareChoice = false; onShare(true) }) {
                    Text("Completa")
                }
            },
        )
    }
    if (showDischarge) {
        AlertDialog(
            onDismissRequest = { showDischarge = false },
            title = { Text("Dar de alta") },
            text = { Text("¿La cama ${card.bed} queda libre? La tarjeta sale del listado.") },
            confirmButton = {
                Button(onClick = { showDischarge = false; onDischarge() }) { Text("Dar de alta") }
            },
            dismissButton = {
                TextButton(onClick = { showDischarge = false }) { Text("Cancelar") }
            },
        )
    }
    if (showDeleteCase) {
        AlertDialog(
            onDismissRequest = { showDeleteCase = false },
            title = { Text("Borrar tarjeta") },
            text = {
                Text(
                    "Se eliminará por completo el caso de la cama ${card.bed} " +
                        "(tarjeta, paciente, apartados, pendientes y procedimientos). " +
                        "Esta acción es permanente.",
                )
            },
            confirmButton = {
                Button(
                    onClick = { showDeleteCase = false; onDeleteCase() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteCase = false }) { Text("Cancelar") }
            },
        )
    }
}

/** Píldora sólida de estado: roja PENDIENTE / verde LISTO (propuesta 2). */
@Composable
private fun StatusPill(ready: Boolean) {
    Surface(
        color = if (ready) ClinicalStable else ClinicalCritical,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = if (ready) "LISTO" else "PENDIENTE",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Fila etiqueta/valor de la tarjeta nítida, separada por un divisor fino. */
@Composable
private fun DetailRow(
    label: String,
    value: String?,
    highlight: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value?.takeIf { it.isNotBlank() } ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
                color = if (highlight) ClinicalCritical else TextPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

/** Marcas visibles en lista y tarjeta: fuera de servicio / interconsulta. */
@Composable
private fun MarcasRow(row: CardRow) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (row.patient.isOutOfService) {
            Text(
                "⚠ Fuera de servicio",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (row.hasInterconsult) {
            Text(
                "⚠ Interconsulta al servicio",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Resumen de pendientes para la fila destacada en rojo. */
private fun pendingsSummary(row: CardRow): String =
    if (row.openPendings.isEmpty()) "—"
    else row.openPendings.joinToString(", ") { it.description }

private fun formatDay(millis: Long): String = try {
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM HH:mm"))
} catch (e: Exception) {
    ""
}

/** Edición de los 6 campos de la tarjeta. Todo opcional. Con desplegables + "Otro…". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditFieldsDialog(
    card: com.reynelbusto.paserevista.domain.model.CaseCard,
    recentDiagnoses: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (CaseCardPatch) -> Unit,
) {
    var diagnosis by remember { mutableStateOf(card.diagnosis ?: "") }
    var procedure by remember { mutableStateOf(card.scheduledProcedure) }
    var state by remember { mutableStateOf(card.currentState) }
    var antibiotic by remember { mutableStateOf(card.antibiotic) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar tarjeta — Cama ${card.bed}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = diagnosis, onValueChange = { diagnosis = it },
                    label = { Text("Diagnóstico") },
                    modifier = Modifier.fillMaxWidth(),
                )
                val suggestions = recentDiagnoses.filter { it.isNotBlank() && it != diagnosis }
                if (suggestions.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        suggestions.take(6).forEach { s ->
                            SuggestionChip(
                                onClick = { diagnosis = s },
                                label = { Text(s, maxLines = 1) },
                            )
                        }
                    }
                }
                OptionField(
                    label = "Proceder programado",
                    options = ClinicalOptions.PROCEDURES,
                    value = procedure,
                    onValueChange = { procedure = it },
                )
                OptionField(
                    label = "Estado actual",
                    options = ClinicalOptions.CURRENT_STATES,
                    value = state,
                    onValueChange = { state = it },
                )
                OptionField(
                    label = "Antibiótico",
                    options = ClinicalOptions.ANTIBIOTICS,
                    value = antibiotic,
                    onValueChange = { antibiotic = it },
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(
                    CaseCardPatch(
                        diagnosis = diagnosis.ifBlank { null },
                        scheduledProcedure = procedure?.ifBlank { null },
                        currentState = state?.ifBlank { null },
                        antibiotic = antibiotic?.ifBlank { null },
                        fieldsTouched = setOf(
                            "diagnosis", "scheduledProcedure", "currentState", "antibiotic",
                        ),
                    ),
                )
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Desplegable "Datos del paciente". Todo opcional. */
@Composable
fun PatientDataDialog(
    patient: com.reynelbusto.paserevista.domain.model.Patient,
    onDismiss: () -> Unit,
    onConfirm: (
        fullName: String?, hcNumber: String?, bloodGroup: String?,
        address: String?, mainDiagnosis: String?, isOutOfService: Boolean?,
        sex: Sex?,
    ) -> Unit,
) {
    var name by remember { mutableStateOf(patient.fullName ?: "") }
    var hc by remember { mutableStateOf(patient.hcNumber ?: "") }
    var group by remember { mutableStateOf(patient.bloodGroup) }
    var address by remember { mutableStateOf(patient.address ?: "") }
    var dx by remember { mutableStateOf(patient.mainDiagnosis ?: "") }
    var outOfService by remember { mutableStateOf(patient.isOutOfService) }
    var sex by remember { mutableStateOf(patient.sex) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Datos del paciente") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nombre completo (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = hc, onValueChange = { hc = it },
                    label = { Text("Historia clínica (opcional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OptionField(
                    label = "Grupo sanguíneo (opcional)",
                    options = ClinicalOptions.BLOOD_GROUPS,
                    value = group,
                    onValueChange = { group = it },
                    allowOther = false,
                )
                SexSegmentedButton(selected = sex, onSelect = { sex = it })
                OutlinedTextField(
                    value = address, onValueChange = { address = it },
                    label = { Text("Dirección (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = dx, onValueChange = { dx = it },
                    label = { Text("Diagnóstico principal (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Fuera de servicio", modifier = Modifier.weight(1f))
                    TextButton(onClick = { outOfService = !outOfService }) {
                        Text(if (outOfService) "Sí" else "No")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(
                    name.ifBlank { null }, hc.ifBlank { null }, group,
                    address.ifBlank { null }, dx.ifBlank { null }, outOfService, sex,
                )
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Diálogo genérico para añadir un apartado personalizado. */
@Composable
fun CustomFieldDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (label: String, value: String) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label, onValueChange = { label = it },
                    label = { Text("Nombre del apartado (ej. Alergias)") },
                )
                OutlinedTextField(
                    value = value, onValueChange = { value = it },
                    label = { Text("Valor") },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(label, value) },
                enabled = label.isNotBlank(),
            ) { Text("Añadir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Hoja de pendientes de la tarjeta: ver, añadir, completar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingsSheet(
    row: CardRow,
    onDismiss: () -> Unit,
    onAddPending: (PendingFormData) -> Unit,
    onCompletePending: (String) -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Pendientes — Cama ${row.card.bed}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showAdd = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Añadir pendiente")
                }
            }
            if (row.openPendings.isEmpty()) {
                Text(
                    "Sin pendientes abiertos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            } else {
                row.openPendings.forEach { pending ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(pending.displayDescription(), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                pending.type.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                        TextButton(onClick = { onCompletePending(pending.id) }) {
                            Text("Hecho ✓")
                        }
                    }
                    HorizontalDivider()
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (showAdd) {
        AddPendingDialog(
            patients = emptyList(),
            preselectedPatientId = row.patient.id,
            onDismiss = { showAdd = false },
            onConfirm = { form -> showAdd = false; onAddPending(form) },
        )
    }
}
