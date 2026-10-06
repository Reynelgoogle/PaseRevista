package com.reynelbusto.paserevista.presentation.camas

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reynelbusto.paserevista.di.AppContainer
import com.reynelbusto.paserevista.domain.model.displayDescription
import com.reynelbusto.paserevista.presentation.components.CaseCardView
import com.reynelbusto.paserevista.presentation.components.EmptyState
import com.reynelbusto.paserevista.presentation.theme.TextSecondary

/**
 * CAMAS — pantalla principal. El listado de tarjetas es el corazón de la app.
 * Añadir cama = solo el número. Nada es obligatorio.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CamasScreen(container: AppContainer) {
    val context = LocalContext.current
    val vm: CamasViewModel = viewModel(factory = CamasViewModel.Factory(container, context))
    val state by vm.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showAddBed by remember { mutableStateOf(false) }

    fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir"))
    }

    val error = state.error
    if (error != null) {
        LaunchedEffect(error) {
            snackbar.showSnackbar(error)
            vm.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Entrega de guardia",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            state.dateLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                },
                actions = {
                    if (state.rows.isNotEmpty()) {
                        IconButton(onClick = { shareText(vm.shareListText()) }) {
                            Icon(Icons.Filled.Share, contentDescription = "Compartir listado")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddBed = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Añadir cama")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // M6: refresco no bloqueante — barra sutil arriba, la lista sigue visible.
            if (state.isRefreshing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            when {
                state.isLoading -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { CircularProgressIndicator() }

                // M7: si falla la carga inicial, error con Reintentar
                // (no el "Sin camas" engañoso).
                state.rows.isEmpty() && state.error != null -> EmptyState(
                    title = "No se pudo cargar",
                    message = state.error ?: "Error al cargar",
                    actionLabel = "Reintentar",
                    onAction = { vm.refresh() },
                    modifier = Modifier.fillMaxSize(),
                )

                state.rows.isEmpty() -> EmptyState(
                    title = "Sin camas",
                    message = "Añada la primera cama con el botón ＋. Solo necesita el número.",
                    actionLabel = "Añadir cama",
                    onAction = { showAddBed = true },
                    modifier = Modifier.fillMaxSize(),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.rows, key = { it.card.id }) { row ->
                        CaseCardView(
                            row = row,
                            displayName = vm.displayName(row),
                            recentDiagnoses = state.recentDiagnoses,
                            onToggleReady = { vm.toggleReady(row.card.id) },
                            onSaveFields = { patch -> vm.saveFields(row.card.id, patch) },
                            onSavePatientDetails = { name, hc, group, addr, dx, out, sex ->
                                vm.savePatientDetails(
                                    row.patient.id, name, hc, group, addr, dx, out, sex,
                                )
                            },
                            onAddCustomField = { label, value ->
                                vm.addCustomField(row.patient.id, label, value)
                            },
                            onRenameCustomField = { field, newLabel ->
                                vm.renameCustomField(field, newLabel)
                            },
                            onSetCustomFieldValue = { field, value ->
                                vm.setCustomFieldValue(field, value)
                            },
                            onDeleteCustomField = { field -> vm.deleteCustomField(field) },
                            onAddPending = { form -> vm.addPending(row.patient.id, form) },
                            onCompletePending = { id -> vm.completePending(id) },
                            onDischarge = { vm.dischargeBed(row.patient.id) },
                            onShare = { complete -> shareText(vm.shareCardText(row, complete)) },
                            onDeleteCase = { vm.deleteCase(row.patient.id) },
                        )
                    }
                }
            }
        }
    }

    if (showAddBed) {
        AddBedDialog(
            onDismiss = { showAddBed = false },
            onConfirm = { bed ->
                showAddBed = false
                vm.addBed(bed)
            },
        )
    }

    // A3: alta bloqueada por P1 — diálogo con la lista y confirmación explícita.
    val confirm = state.dischargeConfirm
    if (confirm != null) {
        AlertDialog(
            onDismissRequest = { vm.dismissDischargeConfirm() },
            title = { Text("Alta con pendientes P1") },
            text = {
                Column {
                    Text(
                        "«${confirm.patientLabel}» tiene pendientes P1 abiertos. " +
                            "El alta los dejará sin resolver:",
                    )
                    confirm.blockingPendings.forEach { p ->
                        Text("• ${p.displayDescription()}", modifier = Modifier.padding(top = 4.dp))
                    }
                }
            },
            confirmButton = {
                Button(onClick = { vm.confirmDischarge() }) {
                    Text("Dar alta igual")
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissDischargeConfirm() }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

/** Añadir cama: SOLO el número. Nada más. */
@Composable
fun AddBedDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var bed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir cama") },
        text = {
            OutlinedTextField(
                value = bed,
                onValueChange = { bed = it },
                label = { Text("Número de cama") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(bed) },
                enabled = bed.isNotBlank(),
            ) { Text("Añadir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
