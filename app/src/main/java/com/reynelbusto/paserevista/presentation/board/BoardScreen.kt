package com.reynelbusto.paserevista.presentation.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.reynelbusto.paserevista.domain.model.ProcedureState
import com.reynelbusto.paserevista.presentation.components.AddProcedureDialog
import com.reynelbusto.paserevista.presentation.components.EmptyState
import com.reynelbusto.paserevista.presentation.components.PriorityChip
import com.reynelbusto.paserevista.presentation.theme.TextSecondary

/**
 * FASE 8 — Pizarra quirúrgica: kanban PENDIENTES | PREPARACIÓN
 * (+ REALIZADOS en tablet). Misma entidad Procedure en todas las columnas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(container: AppContainer, onOpenPatient: (String) -> Unit) {
    val vm: BoardViewModel = viewModel(factory = BoardViewModel.Factory(container))
    val state by vm.uiState.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Pizarra quirúrgica") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Agendar procedimiento")
            }
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingBoard(Modifier.padding(padding))
            state.journey == null -> EmptyState(
                title = "Sin jornada hoy",
                message = "Cree la jornada del día desde Hoy para usar la pizarra.",
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
            else -> BoardContent(
                state = state,
                onToPreparation = vm::toPreparation,
                onPerform = vm::perform,
                onCancel = vm::cancel,
                onOpenPatient = onOpenPatient,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (showAdd) {
        AddProcedureDialog(
            patients = state.patients,
            preselectedPatientId = null,
            defaultDate = state.journey?.clinicalDate ?: "",
            onDismiss = { showAdd = false },
            onConfirm = { form -> vm.schedule(form); showAdd = false },
        )
    }
}

@Composable
private fun LoadingBoard(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { CircularProgressIndicator() }
}

@Composable
private fun BoardContent(
    state: BoardUiState,
    onToPreparation: (String) -> Unit,
    onPerform: (String) -> Unit,
    onCancel: (String) -> Unit,
    onOpenPatient: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = state.pending.size + state.preparation.size + state.performed.size
    if (total == 0) {
        EmptyState(
            title = "Pizarra vacía",
            message = "Agende procedimientos para la jornada con el botón +.",
            modifier = modifier.fillMaxSize(),
        )
        return
    }
    // Responsive: 3 columnas en tablet, 2 en teléfono (+ realizados colapsable).
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wide = maxWidth > 600.dp
        if (wide) {
            Row(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BoardColumn(
                    title = "Pendientes",
                    items = state.pending,
                    onToPreparation = onToPreparation,
                    onPerform = onPerform,
                    onCancel = onCancel,
                    onOpenPatient = onOpenPatient,
                    modifier = Modifier.weight(1f),
                )
                BoardColumn(
                    title = "Preparación",
                    items = state.preparation,
                    onToPreparation = onToPreparation,
                    onPerform = onPerform,
                    onCancel = onCancel,
                    onOpenPatient = onOpenPatient,
                    modifier = Modifier.weight(1f),
                )
                BoardColumn(
                    title = "Realizados",
                    items = state.performed,
                    onToPreparation = onToPreparation,
                    onPerform = onPerform,
                    onCancel = onCancel,
                    onOpenPatient = onOpenPatient,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                    BoardColumn(
                        title = "Pendientes",
                        items = state.pending,
                        onToPreparation = onToPreparation,
                        onPerform = onPerform,
                        onCancel = onCancel,
                        onOpenPatient = onOpenPatient,
                        modifier = Modifier.weight(1f),
                    )
                    BoardColumn(
                        title = "Preparación",
                        items = state.preparation,
                        onToPreparation = onToPreparation,
                        onPerform = onPerform,
                        onCancel = onCancel,
                        onOpenPatient = onOpenPatient,
                        modifier = Modifier.weight(1f),
                    )
                }
                PerformedSection(
                    items = state.performed,
                    onOpenPatient = onOpenPatient,
                )
            }
        }
    }
}

@Composable
private fun BoardColumn(
    title: String,
    items: List<ProcedureWithPatient>,
    onToPreparation: (String) -> Unit,
    onPerform: (String) -> Unit,
    onCancel: (String) -> Unit,
    onOpenPatient: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "$title (${items.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(items, key = { it.procedure.id }) { item ->
                ProcedureCard(
                    item = item,
                    onToPreparation = { onToPreparation(item.procedure.id) },
                    onPerform = { onPerform(item.procedure.id) },
                    onCancel = { onCancel(item.procedure.id) },
                    onOpenPatient = { onOpenPatient(item.procedure.patientId) },
                )
            }
        }
    }
}

@Composable
private fun PerformedSection(items: List<ProcedureWithPatient>, onOpenPatient: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Realizados (${items.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Ocultar" else "Ver")
                }
            }
            if (expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.forEach { item ->
                        PerformedRow(item = item, onOpenPatient = { onOpenPatient(item.procedure.patientId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PerformedRow(item: ProcedureWithPatient, onOpenPatient: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("✓", color = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(item.procedure.kind, style = MaterialTheme.typography.bodyMedium)
            Text(item.patientName, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        TextButton(onClick = onOpenPatient) { Text("Ver") }
    }
}

@Composable
private fun ProcedureCard(
    item: ProcedureWithPatient,
    onToPreparation: () -> Unit,
    onPerform: () -> Unit,
    onCancel: () -> Unit,
    onOpenPatient: () -> Unit,
) {
    val p = item.procedure
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (p.status == ProcedureState.PERFORMED)
                MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PriorityChip(p.priority)
                p.scheduledTime?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                }
            }
            Text(p.kind, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(item.patientName, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            p.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            when (p.status) {
                ProcedureState.PENDING -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(onClick = onToPreparation) { Text("Preparar") }
                        TextButton(onClick = onCancel) { Text("Cancelar") }
                    }
                }
                ProcedureState.PREPARATION -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(onClick = onPerform) { Text("Realizado") }
                        TextButton(onClick = onCancel) { Text("Cancelar") }
                    }
                }
                else -> {
                    TextButton(onClick = onOpenPatient) { Text("Ver paciente") }
                }
            }
        }
    }
}
