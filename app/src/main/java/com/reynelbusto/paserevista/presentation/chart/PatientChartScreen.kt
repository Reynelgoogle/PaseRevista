package com.reynelbusto.paserevista.presentation.chart

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reynelbusto.paserevista.di.AppContainer
import com.reynelbusto.paserevista.domain.model.ClinicalEvolution
import com.reynelbusto.paserevista.domain.model.ClinicalPriority
import com.reynelbusto.paserevista.domain.model.ClinicalState
import com.reynelbusto.paserevista.domain.model.DailyRecord
import com.reynelbusto.paserevista.domain.model.Pending
import com.reynelbusto.paserevista.domain.model.ReviewState
import com.reynelbusto.paserevista.presentation.components.ClinicalDot
import com.reynelbusto.paserevista.presentation.components.PriorityChip
import com.reynelbusto.paserevista.presentation.components.ReviewBadge
import com.reynelbusto.paserevista.presentation.theme.ClinicalCritical
import com.reynelbusto.paserevista.presentation.theme.ClinicalStable
import com.reynelbusto.paserevista.presentation.theme.ClinicalWatch
import com.reynelbusto.paserevista.presentation.theme.TextSecondary

/**
 * FASE 7 — Ficha del paciente: el flujo estrella.
 *
 * IDENTIFICACIÓN + AYER (referencia NO editable, visualmente diferenciado)
 * + HOY (campos vacíos hasta el registro) + "¿Qué cambió desde ayer?"
 * con las 4 acciones + "✓ Marcar como revisado" explícito.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientChartScreen(
    container: AppContainer,
    patientId: String,
    onBack: () -> Unit,
    onOpenPatient: (String) -> Unit,
) {
    val vm: PatientChartViewModel = viewModel(
        factory = PatientChartViewModel.Factory(container, patientId),
    )
    val state by vm.state.collectAsState()
    val nextId by vm.nextPatientId.collectAsState()
    val timeline by vm.timeline.collectAsState()
    val notice by vm.notice.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(notice) {
        notice?.let {
            snackbar.showSnackbar(it)
            vm.clearNotice()
        }
    }
    // Deshacer "Sin cambios" (ventana de 5 s).
    LaunchedEffect(state.showUndo) {
        if (state.showUndo) {
            val r = snackbar.showSnackbar("Sin cambios registrado", actionLabel = "Deshacer")
            if (r == SnackbarResult.ActionPerformed) vm.undoUnchanged()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                title = {
                    Column {
                        Text(
                            state.patient?.fullName ?: "Ficha del paciente",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        val bed = state.today?.bed
                        Text(
                            text = buildString {
                                if (bed != null) append("Cama $bed · ")
                                append("${state.age} años")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                },
                actions = {
                    state.today?.let { ReviewBadge(state = it.reviewState) }
                    Spacer(Modifier.width(8.dp))
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.isLoading -> LoadingChart(Modifier.padding(padding))
            state.patient == null -> ErrorChart("No se encontró el paciente.", Modifier.padding(padding))
            state.journey == null -> ErrorChart(
                "No hay jornada activa hoy. Créala desde Hoy para revisar pacientes.",
                Modifier.padding(padding),
            )
            state.today == null -> ErrorChart(
                "Este paciente no tiene registro en la jornada de hoy.",
                Modifier.padding(padding),
            )
            else -> ChartContent(
                state = state,
                vm = vm,
                nextPatientId = nextId,
                timeline = timeline,
                onOpenPatient = onOpenPatient,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun LoadingChart(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { CircularProgressIndicator() }
}

@Composable
private fun ErrorChart(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { Text(message, color = TextSecondary) }
}

@Composable
private fun ChartContent(
    state: ChartUiState,
    vm: PatientChartViewModel,
    nextPatientId: String?,
    timeline: List<TimelineEntry>,
    onOpenPatient: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val patient = state.patient!!
    val today = state.today!!
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { IdentificationCard(patient = patient, age = state.age) }
        item { YesterdayCard(record = state.yesterday) }
        item {
            TodayCard(
                record = today,
                onEvolutionTap = vm::onEvolutionTap,
                onClinicalState = vm::onClinicalState,
                onPain = vm::onPain,
                onFever = vm::onFever,
                onDiuresis = vm::onDiuresis,
                onOtherSigns = vm::onOtherSigns,
                onPriority = vm::onPriority,
                onEvolutionText = vm::onEvolutionText,
                onObservations = vm::onObservations,
                onMarkReviewed = vm::onMarkReviewed,
                onReopen = vm::onReopen,
            )
        }
        if (today.reviewState == ReviewState.COMPLETED) {
            item {
                NextPatientRow(
                    nextPatientId = nextPatientId,
                    onOpenPatient = onOpenPatient,
                )
            }
        }
        item {
            ModuleSections(
                openPendings = state.openPendings,
                treatments = state.activeTreatments,
                devices = state.activeDevices,
                results = state.results,
            )
        }
        item {
            PatientTimeline(entries = timeline)
        }
        item {
            DischargeSection(
                onDischarge = { reason, onBlocked, onDone ->
                    vm.onDischarge(reason, onBlocked, onDone)
                },
                onDischargeForced = { reason, onDone -> vm.dischargeForced(reason, onDone) },
            )
        }
    }
}

@Composable
private fun IdentificationCard(
    patient: com.reynelbusto.paserevista.domain.model.Patient,
    age: Int,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionLabel("Identificación")
            Text("${patient.fullName}, $age años", style = MaterialTheme.typography.titleMedium)
            InfoLine("HC", patient.hcNumber)
            InfoLine("Sexo", if (patient.sex.code == "M") "Masculino" else "Femenino")
            patient.bloodGroup?.let { InfoLine("Grupo sanguíneo", it) }
            InfoLine("Ingreso", patient.admissionDate)
            InfoLine("Diagnóstico principal", patient.mainDiagnosis)
            patient.admissionReason?.let { InfoLine("Motivo de ingreso", it) }
            if (patient.comorbidities.isNotEmpty()) {
                InfoLine("Comorbilidades", patient.comorbidities.joinToString(", "))
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("$label:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
}

/**
 * AYER: referencia de solo lectura, visualmente diferenciado (tarjeta con borde,
 * fondo tenue). AYER ≠ HOY: nada aquí es editable ni se copia.
 */
@Composable
private fun YesterdayCard(record: DailyRecord?) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionLabel("Ayer · referencia (no editable)")
            if (record == null) {
                Text(
                    "Sin registro el día anterior.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            } else {
                YesterdayLine("Evolución", record.evolutionTap?.let(::evolutionLabel))
                YesterdayLine("Estado", record.clinicalState?.let(::clinicalStateLabel))
                YesterdayLine("Dolor", record.painScale?.let { "$it/10" })
                YesterdayLine("Fiebre", record.hasFever?.let { if (it) "Sí" else "No" })
                YesterdayLine("Diuresis", record.diuresis)
                YesterdayLine("Prioridad", record.priority?.name)
                YesterdayLine("Observaciones", record.observations)
            }
        }
    }
}

@Composable
private fun YesterdayLine(label: String, value: String?) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("$label:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(
            value ?: "—",
            style = MaterialTheme.typography.bodySmall,
            color = if (value == null) TextSecondary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun evolutionLabel(e: ClinicalEvolution): String = when (e) {
    ClinicalEvolution.UNCHANGED -> "Sin cambios"
    ClinicalEvolution.IMPROVED -> "Mejoría"
    ClinicalEvolution.WORSENED -> "Empeoramiento"
    ClinicalEvolution.NEW_PROBLEM -> "Nuevo problema"
}

private fun clinicalStateLabel(s: ClinicalState): String = when (s) {
    ClinicalState.STABLE -> "Estable"
    ClinicalState.WATCH -> "Vigilancia"
    ClinicalState.CRITICAL -> "Crítico"
}

/** HOY: lo que se registra en esta jornada. Campos vacíos = "sin registrar". */
@Composable
private fun TodayCard(
    record: DailyRecord,
    onEvolutionTap: (ClinicalEvolution) -> Unit,
    onClinicalState: (ClinicalState?) -> Unit,
    onPain: (Int?) -> Unit,
    onFever: (Boolean?) -> Unit,
    onDiuresis: (String?) -> Unit,
    onOtherSigns: (String?) -> Unit,
    onPriority: (ClinicalPriority?) -> Unit,
    onEvolutionText: (String?) -> Unit,
    onObservations: (String?) -> Unit,
    onMarkReviewed: () -> Unit,
    onReopen: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SectionLabel("Hoy · registro del día")
                Spacer(Modifier.weight(1f))
                ReviewBadge(state = record.reviewState)
            }

            Text(
                "¿Qué cambió desde ayer?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            EvolutionActions(
                current = record.evolutionTap,
                enabled = record.reviewState != ReviewState.COMPLETED,
                onTap = onEvolutionTap,
            )

            // Campos clínicos: solo cobran sentido tras un delta; "Sin cambios"
            // completa directo. Siempre visibles pero vacíos = sin registrar.
            if (record.evolutionTap != null && record.evolutionTap != ClinicalEvolution.UNCHANGED) {
                HorizontalDivider()
                ClinicalStateSelector(current = record.clinicalState, onSelect = onClinicalState)
                PainSelector(current = record.painScale, onSelect = onPain)
                FeverSelector(current = record.hasFever, onSelect = onFever)
                TextFieldRow(
                    label = "Diuresis",
                    value = record.diuresis,
                    onChange = onDiuresis,
                    placeholder = "Sin registrar",
                )
                PrioritySelector(current = record.priority, onSelect = onPriority)
                TextFieldRow(
                    label = "Evolución",
                    value = record.evolutionText,
                    onChange = onEvolutionText,
                    placeholder = "Describa el cambio…",
                    singleLine = false,
                )
                TextFieldRow(
                    label = "Otros signos",
                    value = record.otherSigns,
                    onChange = onOtherSigns,
                    placeholder = "Sin registrar",
                )
                TextFieldRow(
                    label = "Observaciones",
                    value = record.observations,
                    onChange = onObservations,
                    placeholder = "Sin registrar",
                    singleLine = false,
                )
            }

            when (record.reviewState) {
                ReviewState.IN_PROGRESS -> {
                    Button(
                        onClick = onMarkReviewed,
                        enabled = record.evolutionTap != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("✓ Marcar como revisado") }
                    if (record.evolutionTap == null) {
                        Text(
                            "Registre qué cambió desde ayer para poder marcar como revisado.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                }
                ReviewState.COMPLETED -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ClinicalDot(color = ClinicalStable, label = "Revisado")
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onReopen) { Text("Reabrir") }
                    }
                }
                ReviewState.PENDING -> {
                    Text(
                        "Sin registrar: toque una de las 4 acciones para comenzar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EvolutionActions(
    current: ClinicalEvolution?,
    enabled: Boolean,
    onTap: (ClinicalEvolution) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EvolutionButton("Sin cambios", current == ClinicalEvolution.UNCHANGED, enabled) {
            onTap(ClinicalEvolution.UNCHANGED)
        }
        EvolutionButton("Mejoría", current == ClinicalEvolution.IMPROVED, enabled) {
            onTap(ClinicalEvolution.IMPROVED)
        }
        EvolutionButton("Empeoramiento", current == ClinicalEvolution.WORSENED, enabled) {
            onTap(ClinicalEvolution.WORSENED)
        }
        EvolutionButton("Nuevo problema", current == ClinicalEvolution.NEW_PROBLEM, enabled) {
            onTap(ClinicalEvolution.NEW_PROBLEM)
        }
    }
}

@Composable
private fun EvolutionButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick, enabled = enabled) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled) { Text(label) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClinicalStateSelector(current: ClinicalState?, onSelect: (ClinicalState?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel("Estado clínico")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StateChip("Estable", ClinicalStable, current == ClinicalState.STABLE) {
                onSelect(if (current == ClinicalState.STABLE) null else ClinicalState.STABLE)
            }
            StateChip("Vigilancia", ClinicalWatch, current == ClinicalState.WATCH) {
                onSelect(if (current == ClinicalState.WATCH) null else ClinicalState.WATCH)
            }
            StateChip("Crítico", ClinicalCritical, current == ClinicalState.CRITICAL) {
                onSelect(if (current == ClinicalState.CRITICAL) null else ClinicalState.CRITICAL)
            }
        }
    }
}

@Composable
private fun StateChip(label: String, color: androidx.compose.ui.graphics.Color, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            ClinicalDot(color = color, label = "")
        },
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PainSelector(current: Int?, onSelect: (Int?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel("Dolor (0–10)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (0..10).forEach { v ->
                FilterChip(
                    selected = current == v,
                    onClick = { onSelect(if (current == v) null else v) },
                    label = { Text("$v") },
                )
            }
        }
    }
}

@Composable
private fun FeverSelector(current: Boolean?, onSelect: (Boolean?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel("Fiebre")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = current == true, onClick = { onSelect(if (current == true) null else true) }, label = { Text("Sí") })
            FilterChip(selected = current == false, onClick = { onSelect(if (current == false) null else false) }, label = { Text("No") })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrioritySelector(current: ClinicalPriority?, onSelect: (ClinicalPriority?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel("Prioridad del día")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClinicalPriority.entries.forEach { p ->
                FilterChip(
                    selected = current == p,
                    onClick = { onSelect(if (current == p) null else p) },
                    label = { Text(p.name) },
                )
            }
        }
        if (current == null) {
            Text("Sin prioridad", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun TextFieldRow(
    label: String,
    value: String?,
    onChange: (String?) -> Unit,
    placeholder: String,
    singleLine: Boolean = true,
) {
    var text by remember(value) { mutableStateOf(value ?: "") }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel(label)
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onChange(it.ifBlank { null })
            },
            placeholder = { Text(placeholder) },
            singleLine = singleLine,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NextPatientRow(nextPatientId: String?, onOpenPatient: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (nextPatientId != null) "Paciente revisado." else "Pase completo: todos revisados.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (nextPatientId != null) {
                Button(onClick = { onOpenPatient(nextPatientId) }) { Text("Siguiente paciente") }
            }
        }
    }
}

@Composable
private fun ModuleSections(
    openPendings: List<Pending>,
    treatments: List<com.reynelbusto.paserevista.domain.model.Treatment>,
    devices: List<com.reynelbusto.paserevista.domain.model.Device>,
    results: List<com.reynelbusto.paserevista.domain.model.ClinicalResult>,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Situación actual")
            ModuleLine("Pendientes abiertos", "${openPendings.size}")
            openPendings.take(3).forEach { p ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PriorityChip(p.priority)
                    Text(p.description, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                }
            }
            HorizontalDivider()
            ModuleLine("Tratamientos activos", "${treatments.size}")
            treatments.take(3).forEach { t ->
                Text(
                    "• ${t.drug} ${t.dose} (${t.frequency})",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            HorizontalDivider()
            ModuleLine("Dispositivos", "${devices.size}")
            devices.take(3).forEach { d ->
                Text(
                    "• ${d.kind.name.lowercase().replace('_', ' ')}${d.label?.let { " ($it)" } ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            HorizontalDivider()
            ModuleLine("Resultados", "${results.size}")
            results.take(3).forEach { r ->
                Text(
                    "• ${r.resultDate}: ${r.summary}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun ModuleLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    }
}

/**
 * Historial del paciente: timeline inverso, solo lectura.
 * Tendencia simple: conteo de evoluciones recientes.
 */
@Composable
private fun PatientTimeline(entries: List<TimelineEntry>) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Historial del paciente")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Ocultar" else "Ver (${entries.size})")
                }
            }
            if (expanded) {
                if (entries.isEmpty()) {
                    Text(
                        "Sin registros anteriores.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                } else {
                    // Tendencia simple: últimas evoluciones.
                    val recent = entries.take(7)
                    val worsened = recent.count { it.record.evolutionTap == ClinicalEvolution.WORSENED }
                    if (worsened >= 2) {
                        Text(
                            "Tendencia: $worsened empeoramientos en los últimos ${recent.size} registros.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ClinicalCritical,
                        )
                    }
                    entries.take(15).forEach { entry ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ReviewBadge(state = entry.record.reviewState)
                            Column(Modifier.weight(1f)) {
                                Text(entry.date, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                Text(
                                    entry.record.evolutionTap?.let(::evolutionLabel) ?: "Sin evolución registrada",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                entry.record.priority?.let { PriorityChip(priority = it) }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun DischargeSection(
    onDischarge: (reason: String?, onBlocked: (List<Pending>) -> Unit, onDone: () -> Unit) -> Unit,
    onDischargeForced: (reason: String?, onDone: () -> Unit) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    var blockedPendings by remember { mutableStateOf<List<Pending>>(emptyList()) }
    var reason by remember { mutableStateOf("") }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Egreso")
            Text(
                "El alta excluye al paciente del censo futuro. Su historia se conserva.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            OutlinedButton(
                onClick = { showDialog = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Dar de alta") }
        }
    }

    if (showDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDialog = false; blockedPendings = emptyList() },
            title = { Text("Dar de alta") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (blockedPendings.isEmpty()) {
                        Text("Confirme el egreso del paciente.")
                        OutlinedTextField(
                            value = reason,
                            onValueChange = { reason = it },
                            label = { Text("Motivo (opcional)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            "Hay ${blockedPendings.size} pendiente(s) P1 abierto(s). " +
                                "Resuélvalos, cancélelos con motivo o reasígnelos antes del alta, " +
                                "o continúe bajo su responsabilidad.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        blockedPendings.forEach { p ->
                            Text("• ${p.description}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                if (blockedPendings.isEmpty()) {
                    Button(onClick = {
                        onDischarge(
                            reason.ifBlank { null },
                            { blocked -> blockedPendings = blocked },
                            { showDialog = false },
                        )
                    }) { Text("Confirmar alta") }
                } else {
                    Button(onClick = {
                        onDischargeForced(reason.ifBlank { null }) { showDialog = false }
                    }) { Text("Continuar de todos modos") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false; blockedPendings = emptyList() }) {
                    Text("Cancelar")
                }
            },
        )
    }
}
