package com.reynelbusto.paserevista.presentation.pending

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reynelbusto.paserevista.di.AppContainer
import com.reynelbusto.paserevista.domain.model.ClinicalPriority
import com.reynelbusto.paserevista.domain.model.PendingState
import com.reynelbusto.paserevista.domain.model.PendingType
import com.reynelbusto.paserevista.presentation.components.AddPendingDialog
import com.reynelbusto.paserevista.presentation.components.EmptyState
import com.reynelbusto.paserevista.presentation.components.PriorityChip
import com.reynelbusto.paserevista.presentation.theme.TextSecondary

/**
 * FASE 8 — Pendientes: filtros combinables + tarjeta con identidad persistente.
 * Sin borrado físico: completar o cancelar con trazabilidad.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingsScreen(container: AppContainer, onOpenPatient: (String) -> Unit) {
    val vm: PendingsViewModel = viewModel(factory = PendingsViewModel.Factory(container))
    val state by vm.uiState.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var cancelTarget by remember { mutableStateOf<String?>(null) }
    var cancelReason by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Pendientes") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Nuevo pendiente")
            }
        },
    ) { padding ->
        if (state.isLoading) {
            Column(
                Modifier.padding(padding).fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(Modifier.padding(padding).fillMaxSize()) {
            FilterBar(state = state, vm = vm)
            if (state.pendings.isEmpty()) {
                EmptyState(
                    title = "Sin pendientes",
                    message = "No hay pendientes con estos filtros.",
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.pendings, key = { it.pending.id }) { item ->
                        PendingCard(
                            item = item,
                            onOpenPatient = { onOpenPatient(item.pending.patientId) },
                            onComplete = { vm.completePending(item.pending.id) },
                            onCancel = { cancelTarget = item.pending.id; cancelReason = "" },
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddPendingDialog(
            patients = state.patients,
            preselectedPatientId = null,
            onDismiss = { showAdd = false },
            onConfirm = { form -> vm.createPending(form); showAdd = false },
        )
    }
    cancelTarget?.let { id ->
        AlertDialog(
            onDismissRequest = { cancelTarget = null },
            title = { Text("Cancelar pendiente") },
            text = {
                OutlinedTextField(
                    value = cancelReason,
                    onValueChange = { cancelReason = it },
                    label = { Text("Motivo *") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = { vm.cancelPending(id, cancelReason); cancelTarget = null },
                    enabled = cancelReason.isNotBlank(),
                ) { Text("Cancelar pendiente") }
            },
            dismissButton = { TextButton(onClick = { cancelTarget = null }) { Text("Volver") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterBar(state: PendingsUiState, vm: PendingsViewModel) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusChip("Abiertos", state.statusFilter == PendingStatusFilter.OPEN) {
                vm.setStatusFilter(PendingStatusFilter.OPEN)
            }
            StatusChip("En curso", state.statusFilter == PendingStatusFilter.IN_PROGRESS) {
                vm.setStatusFilter(PendingStatusFilter.IN_PROGRESS)
            }
            StatusChip("Hoy", state.dueFilter == PendingDueFilter.TODAY) {
                vm.setDueFilter(if (state.dueFilter == PendingDueFilter.TODAY) PendingDueFilter.ALL else PendingDueFilter.TODAY)
            }
            StatusChip("Vencidos", state.dueFilter == PendingDueFilter.OVERDUE) {
                vm.setDueFilter(if (state.dueFilter == PendingDueFilter.OVERDUE) PendingDueFilter.ALL else PendingDueFilter.OVERDUE)
            }
            StatusChip("Cerrados", state.statusFilter == PendingStatusFilter.CLOSED) {
                vm.setStatusFilter(PendingStatusFilter.CLOSED)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusChip("P1", state.priorityFilter == PendingPriorityFilter.P1) {
                vm.setPriorityFilter(toggle(state.priorityFilter, PendingPriorityFilter.P1))
            }
            StatusChip("P2", state.priorityFilter == PendingPriorityFilter.P2) {
                vm.setPriorityFilter(toggle(state.priorityFilter, PendingPriorityFilter.P2))
            }
            StatusChip("P3", state.priorityFilter == PendingPriorityFilter.P3) {
                vm.setPriorityFilter(toggle(state.priorityFilter, PendingPriorityFilter.P3))
            }
            Spacer(Modifier.width(4.dp))
            StatusChip("Interconsultas", state.typeFilter == PendingTypeFilter.INTERCONSULTATION) {
                vm.setTypeFilter(toggle(state.typeFilter, PendingTypeFilter.INTERCONSULTATION))
            }
        }
    }
}

// Toggles para filtros con valor ALL.
private fun toggle(current: PendingPriorityFilter, target: PendingPriorityFilter): PendingPriorityFilter =
    if (current == target) PendingPriorityFilter.ALL else target
private fun toggle(current: PendingTypeFilter, target: PendingTypeFilter): PendingTypeFilter =
    if (current == target) PendingTypeFilter.ALL else target

@Composable
private fun StatusChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun PendingCard(
    item: PendingWithPatient,
    onOpenPatient: () -> Unit,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
) {
    val p = item.pending
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PriorityChip(p.priority)
                TypeLabel(p.type)
                Spacer(Modifier.weight(1f))
                StatusLabel(p.status)
            }
            Text(p.description, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                item.patientName,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            val meta = buildList {
                p.dueDate?.let { add("Objetivo: $it") }
                p.assignee?.let { add("Resp.: $it") }
                if (p.type == PendingType.INTERCONSULTATION) p.serviceDest?.let { add("→ $it") }
            }
            if (meta.isNotEmpty()) {
                Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            if (p.status == PendingState.PENDING || p.status == PendingState.IN_PROGRESS) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onComplete) { Text("Completar") }
                    TextButton(onClick = onCancel) { Text("Cancelar") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onOpenPatient) { Text("Ver paciente") }
                }
            } else {
                p.cancelReason?.let {
                    Text("Cancelado: $it", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun TypeLabel(type: PendingType) {
    val label = when (type) {
        PendingType.GENERAL -> "General"
        PendingType.STUDY -> "Estudio"
        PendingType.PROCEDURE -> "Procedimiento"
        PendingType.INTERCONSULTATION -> "Interconsulta"
    }
    Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
}

@Composable
private fun StatusLabel(status: PendingState) {
    val label = when (status) {
        PendingState.PENDING -> "Pendiente"
        PendingState.IN_PROGRESS -> "En curso"
        PendingState.COMPLETED -> "Completado"
        PendingState.CANCELED -> "Cancelado"
    }
    val color = when (status) {
        PendingState.COMPLETED -> MaterialTheme.colorScheme.primary
        PendingState.CANCELED -> TextSecondary
        else -> MaterialTheme.colorScheme.tertiary
    }
    Text(label, style = MaterialTheme.typography.labelMedium, color = color)
}
