package com.reynelbusto.paserevista.presentation.agenda

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reynelbusto.paserevista.di.AppContainer
import com.reynelbusto.paserevista.domain.model.PatientState
import com.reynelbusto.paserevista.domain.model.displayName
import com.reynelbusto.paserevista.domain.model.displayDescription
import com.reynelbusto.paserevista.domain.usecase.ShareFormatter
import com.reynelbusto.paserevista.presentation.camas.AddBedDialog
import com.reynelbusto.paserevista.presentation.camas.CardRow
import com.reynelbusto.paserevista.presentation.components.CaseCardView
import com.reynelbusto.paserevista.presentation.components.DialogShape28
import com.reynelbusto.paserevista.presentation.components.EmptyState
import com.reynelbusto.paserevista.presentation.components.ExpressiveDialogTitle
import com.reynelbusto.paserevista.presentation.components.TintedCircleIcon
import com.reynelbusto.paserevista.presentation.components.mapsPrimaryColors
import com.reynelbusto.paserevista.presentation.theme.ClinicalWatch
import com.reynelbusto.paserevista.presentation.theme.MapsBlue
import com.reynelbusto.paserevista.presentation.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * AGENDA — timeline vertical de los días, lo más nuevo arriba, como hojear
 * una libreta. Es también un espacio de trabajo: hoy se edita directo y los
 * días pasados con "Corregir día". Comparte acciones con Camas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaScreen(container: AppContainer) {
    val vm: AgendaViewModel = viewModel(factory = AgendaViewModel.Factory(container))
    val state by vm.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showAddBed by remember { mutableStateOf(false) }
    val showTodayFab by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 2 }
    }
    // Diálogo de nota del día: qué jornada se está editando.
    var noteTarget by remember { mutableStateOf<AgendaDaySection?>(null) }

    // Índice Lazy de la cabecera de cada día (para la tira de fechas).
    // Orden por día: cabecera(1) + nota(1) + ["Lo que pasó"(1) + eventos] + camas.
    val dayIndices = remember(state.sections) {
        computeDayIndices(
            state.sections.map {
                DayLayout(it.journeyId, it.events.isNotEmpty(), it.events.size, it.rows.size)
            },
        )
    }

    fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir"))
    }

    val actionError = state.actionError
    if (actionError != null) {
        LaunchedEffect(actionError) {
            snackbar.showSnackbar(actionError)
            vm.clearActionError()
        }
    }

    val loadError = state.error
    if (loadError != null) {
        LaunchedEffect(loadError) { snackbar.showSnackbar(loadError) }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Agenda") },
                    actions = {
                        IconButton(onClick = { showAddBed = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "Añadir cama")
                        }
                    },
                )
                if (state.sections.isNotEmpty()) {
                    DateStrip(
                        sections = state.sections,
                        dayIndices = dayIndices,
                        onSelectDay = { index ->
                            scope.launch { listState.animateScrollToItem(index) }
                        },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (showTodayFab) {
                FloatingActionButton(onClick = {
                    scope.launch { listState.animateScrollToItem(0) }
                }) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = "Volver a hoy",
                    )
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator() }

            state.sections.isEmpty() && state.error != null -> EmptyState(
                title = "No se pudo cargar",
                message = state.error ?: "Error al cargar",
                actionLabel = "Reintentar",
                onAction = { vm.retry() },
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            )

            state.sections.isEmpty() -> EmptyState(
                title = "Sin días todavía",
                message = "Añada camas en la pantalla Camas y aparecerán aquí por día.",
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            )

            else -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.sections.forEach { section ->
                    // Cabecera de Reynel: título + resumen + compartir + "Corregir día".
                    item(key = "day-${section.journeyId}") {
                        DayHeader(
                            section = section,
                            editing = section.journeyId in state.editingJourneyIds,
                            onToggleEditing = { vm.toggleEditing(section.journeyId) },
                            onShareDay = { shareText(vm.shareDayText(section)) },
                        )
                    }
                    // Nota libre del día.
                    item(key = "note-${section.journeyId}") {
                        DayNoteRow(
                            note = section.note,
                            onEdit = { noteTarget = section },
                        )
                    }
                    // "Lo que pasó": bitácora cronológica del día.
                    if (section.events.isNotEmpty()) {
                        item(key = "events-label-${section.journeyId}") {
                            Text(
                                "Lo que pasó",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                            )
                        }
                        itemsIndexed(
                            section.events,
                            key = { index, _ -> "event-${section.journeyId}-$index" },
                        ) { index, event ->
                            DayEventRow(
                                event = event,
                                isLast = index == section.events.lastIndex,
                            )
                        }
                    }
                    // Camas: filas compactas; el toque expande la tarjeta inline,
                    // editable cuando el día lo permite (hoy o "Corregir día").
                    val editable = section.isToday || section.journeyId in state.editingJourneyIds
                    items(section.rows, key = { "bedrow-${it.card.id}" }) { row ->
                        var expanded by remember { mutableStateOf(false) }
                        if (expanded) {
                            ExpandedAgendaCard(
                                vm = vm,
                                row = row,
                                section = section,
                                editable = editable,
                                recentDiagnoses = state.recentDiagnoses,
                                onShareText = ::shareText,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            AgendaBedRow(
                                row = row,
                                originMark = when (section.marks[row.card.id]) {
                                    BedMark.CONTINUING -> "viene de ayer"
                                    BedMark.NEW -> "nuevo hoy"
                                    null -> null
                                },
                                onExpand = { expanded = true },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                if (state.canLoadMore) {
                    item(key = "load-more") {
                        LaunchedEffect(Unit) { vm.loadMore() }
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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

    // A3: alta bloqueada por P1 — confirmación explícita (misma política que Camas).
    val confirm = state.dischargeConfirm
    if (confirm != null) {
        AlertDialog(
            onDismissRequest = { vm.dismissDischargeConfirm() },
            shape = DialogShape28,
            title = {
                ExpressiveDialogTitle(
                    icon = Icons.Filled.Warning,
                    iconColor = ClinicalWatch,
                    title = "Alta con pendientes P1",
                )
            },
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
                Button(
                    onClick = { vm.confirmDischarge() },
                    colors = mapsPrimaryColors(),
                ) {
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

    // Nota libre del día (única edición propia de la agenda).
    val target = noteTarget
    if (target != null) {
        NoteEditDialog(
            initial = target.note ?: "",
            onSave = { text ->
                vm.saveNote(target.journeyId, text)
                noteTarget = null
            },
            onClear = {
                vm.saveNote(target.journeyId, null)
                noteTarget = null
            },
            onDismiss = { noteTarget = null },
        )
    }
}

/** Tarjeta completa inline al expandir una fila compacta: conserva toda la edición de Reynel. */
@Composable
private fun ExpandedAgendaCard(
    vm: AgendaViewModel,
    row: CardRow,
    section: AgendaDaySection,
    editable: Boolean,
    recentDiagnoses: List<String>,
    onShareText: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    CaseCardView(
        row = row,
        displayName = vm.displayName(row),
        recentDiagnoses = recentDiagnoses,
        initiallyExpanded = true,
        readOnly = !editable,
        originMark = when (section.marks[row.card.id]) {
            BedMark.CONTINUING -> "viene de ayer"
            BedMark.NEW -> "nuevo hoy"
            null -> null
        },
        onToggleReady = { vm.toggleReady(row.card.id) },
        onSaveFields = { patch -> vm.saveFields(row.card.id, patch) },
        onSavePatientDetails = { name, hc, group, allergies, addr, dx, out, sex ->
            vm.savePatientDetails(
                row.patient.id, name, hc, group, allergies, addr, dx, out, sex,
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
        onShare = { complete ->
            onShareText(
                if (complete) ShareFormatter.cardComplete(row.shareData())
                else ShareFormatter.cardSimple(row.shareData()),
            )
        },
        onDeleteCase = { vm.deleteCase(row.patient.id) },
        modifier = modifier,
    )
}

/** Cabecera de un día: título + resumen + compartir el día + "Corregir". */
@Composable
private fun DayHeader(
    section: AgendaDaySection,
    editing: Boolean,
    onToggleEditing: () -> Unit,
    onShareDay: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        TintedCircleIcon(
            icon = Icons.Filled.DateRange,
            iconColor = MapsBlue,
            circleSize = 40.dp,
            iconSize = 22.dp,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                section.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                daySummary(section),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        IconButton(onClick = onShareDay) {
            Icon(Icons.Filled.Share, contentDescription = "Compartir el día")
        }
        if (!section.isToday) {
            TextButton(onClick = onToggleEditing) {
                Text(if (editing) "Listo" else "Corregir día")
            }
        }
    }
}

/** "12 camas · 5 listos · 3 con pendientes · 2 altas". */
private fun daySummary(section: AgendaDaySection): String {
    val beds = section.rows.size
    val ready = section.rows.count { it.card.ready }
    val withPendings = section.rows.count { it.openPendings.isNotEmpty() }
    val discharged = section.rows.count { it.patient.status != PatientState.ACTIVE }
    val parts = buildList {
        add("$beds camas")
        add("$ready listos")
        if (withPendings > 0) add("$withPendings con pendientes")
        if (discharged > 0) add("$discharged altas")
    }
    return parts.joinToString(" · ")
}
