package com.reynelbusto.paserevista.presentation.patients

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reynelbusto.paserevista.di.AppContainer
import com.reynelbusto.paserevista.domain.model.PatientState
import com.reynelbusto.paserevista.domain.model.Sex
import com.reynelbusto.paserevista.presentation.components.EmptyState
import com.reynelbusto.paserevista.presentation.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * FASE 9 — Pacientes: búsqueda global offline (nombre/HC/cama/diagnóstico),
 * toggle activos/dados de alta, nuevo ingreso en < 30 s.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientsScreen(container: AppContainer, onOpenPatient: (String) -> Unit) {
    val vm: PatientsViewModel = viewModel(factory = PatientsViewModel.Factory(container))
    val state by vm.uiState.collectAsState()
    var showAdmit by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Pacientes") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdmit = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Nuevo ingreso")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::onQueryChange,
                    label = { Text("Buscar") },
                    placeholder = { Text("Nombre, HC, cama, diagnóstico…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(Modifier.padding(horizontal = 16.dp)) {
                FilterChip(
                    selected = state.showDischarged,
                    onClick = vm::onToggleDischarged,
                    label = { Text("Dados de alta") },
                )
            }
            Spacer(Modifier.height(4.dp))
            if (state.isLoading) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { CircularProgressIndicator() }
            } else if (state.rows.isEmpty()) {
                EmptyState(
                    title = if (state.query.isBlank()) "Sin pacientes" else "Sin resultados",
                    message = if (state.query.isBlank())
                        "Use + para registrar un nuevo ingreso."
                    else "Ningún paciente coincide con la búsqueda.",
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.rows, key = { it.patient.id }) { row ->
                        PatientListCard(
                            row = row,
                            onClick = { onOpenPatient(row.patient.id) },
                        )
                    }
                }
            }
        }
    }

    if (showAdmit) {
        NewAdmissionDialog(
            onDismiss = { showAdmit = false },
            onConfirm = { form ->
                vm.admit(
                    form,
                    onDone = { id ->
                        showAdmit = false
                        onOpenPatient(id)
                    },
                    onError = { msg ->
                        scope.launch { snackbar.showSnackbar(msg) }
                    },
                )
            },
        )
    }
}

@Composable
private fun PatientListCard(row: PatientListRow, onClick: () -> Unit) {
    val p = row.patient
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${p.fullName}, ${row.age} años",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (p.status != PatientState.ACTIVE) {
                    Text(
                        if (p.status == PatientState.DISCHARGED) "De alta" else "Trasladado",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                    )
                }
            }
            Text(p.mainDiagnosis, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Text("HC: ${p.hcNumber}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

/** Nuevo ingreso: 7 campos esenciales, < 30 s. */
@Composable
private fun NewAdmissionDialog(
    onDismiss: () -> Unit,
    onConfirm: (NewAdmissionForm) -> Unit,
) {
    var fullName by remember { mutableStateOf("") }
    var birthDate by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf(Sex.M) }
    var hcNumber by remember { mutableStateOf("") }
    var bloodGroup by remember { mutableStateOf("") }
    var admissionReason by remember { mutableStateOf("") }
    var mainDiagnosis by remember { mutableStateOf("") }
    var bed by remember { mutableStateOf("") }

    val valid = fullName.isNotBlank() && hcNumber.isNotBlank() && mainDiagnosis.isNotBlank() &&
        birthDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo ingreso") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Nombre completo *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = birthDate, onValueChange = { birthDate = it }, label = { Text("Nacimiento (aaaa-mm-dd) *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = sex == Sex.M, onClick = { sex = Sex.M }, label = { Text("Masculino") })
                    FilterChip(selected = sex == Sex.F, onClick = { sex = Sex.F }, label = { Text("Femenino") })
                }
                OutlinedTextField(value = hcNumber, onValueChange = { hcNumber = it }, label = { Text("Historia clínica *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = mainDiagnosis, onValueChange = { mainDiagnosis = it }, label = { Text("Diagnóstico principal *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = admissionReason, onValueChange = { admissionReason = it }, label = { Text("Motivo de ingreso") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = bloodGroup, onValueChange = { bloodGroup = it }, label = { Text("Grupo sanguíneo") }, placeholder = { Text("Ej.: O+") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = bed, onValueChange = { bed = it }, label = { Text("Cama") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        NewAdmissionForm(
                            fullName = fullName.trim(), birthDate = birthDate.trim(), sex = sex,
                            hcNumber = hcNumber.trim(), bloodGroup = bloodGroup.ifBlank { null },
                            admissionReason = admissionReason.ifBlank { null },
                            mainDiagnosis = mainDiagnosis.trim(), bed = bed.ifBlank { null },
                        ),
                    )
                },
                enabled = valid,
            ) { Text("Ingresar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
