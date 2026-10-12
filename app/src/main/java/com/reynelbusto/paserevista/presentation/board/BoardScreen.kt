package com.reynelbusto.paserevista.presentation.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.reynelbusto.paserevista.core.IsoDate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import com.reynelbusto.paserevista.di.AppContainer
import com.reynelbusto.paserevista.domain.model.BoardColumn
import com.reynelbusto.paserevista.domain.model.ProcedureState
import com.reynelbusto.paserevista.domain.usecase.BoardItem
import com.reynelbusto.paserevista.presentation.components.AddProcedureDialog
import com.reynelbusto.paserevista.presentation.components.EmptyState
import com.reynelbusto.paserevista.presentation.theme.ClinicalStable
import com.reynelbusto.paserevista.presentation.theme.ClinicalWatch
import com.reynelbusto.paserevista.presentation.theme.MapsBlue
import com.reynelbusto.paserevista.presentation.theme.PaseRevistaPrimary
import com.reynelbusto.paserevista.presentation.theme.TextPrimary
import com.reynelbusto.paserevista.presentation.theme.TextSecondary

/**
 * Pizarra quirúrgica Kanban: Propuesto → Programado → Realizado.
 * Las tarjetas marcadas Listo en Camas llegan aquí solas.
 *
 * Diseño "Identidad fija" estilo Maps: buscador flotante, píldoras de
 * estado con conteos, hoja inferior con el resumen del día y FAB para
 * agendar. El azul Maps es fijo (no sigue los colores del sistema).
 */
@Composable
fun BoardScreen(container: AppContainer) {
    val vm: BoardViewModel = viewModel(factory = BoardViewModel.Factory(container))
    val state by vm.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    val error = state.error
    if (error != null) {
        LaunchedEffect(error) {
            snackbar.showSnackbar(error)
            vm.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.isLoading -> Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator() }

            // M7: fallo de carga → error con Reintentar (no "Pizarra vacía" engañosa).
            state.loadError != null -> EmptyState(
                title = "No se pudo cargar",
                message = state.loadError ?: "Error al cargar",
                actionLabel = "Reintentar",
                onAction = { vm.retry() },
                modifier = Modifier.padding(padding).fillMaxSize(),
            )

            else -> BoardContent(
                state = state,
                onQuery = vm::setQuery,
                onToggleColumn = vm::toggleColumnFilter,
                onMove = vm::moveTo,
                onReschedule = vm::reschedule,
                onAdd = vm::openAddDialog,
                modifier = Modifier.padding(padding),
            )
        }
    }

    // FAB +: diálogo existente de agendar procedimiento en pizarra.
    if (state.showAddDialog) {
        val todayIso: IsoDate =
            remember { LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) }
        AddProcedureDialog(
            patients = state.dialogPatients,
            preselectedPatientId = null,
            defaultDate = todayIso,
            onDismiss = vm::closeAddDialog,
            onConfirm = { data ->
                vm.confirmAdd(
                    data.patientId,
                    data.kind,
                    data.scheduledDate,
                    data.scheduledTime,
                    data.priority,
                    data.note,
                )
            },
        )
    }
}

/** Vistas de la pizarra. Por defecto Panel (la experiencia actual no cambia). */
private enum class BoardView(val title: String) {
    LIST("Lista"),
    PANEL("Panel"),
    CALENDAR("Calendario"),
}

@Composable
private fun BoardContent(
    state: BoardUiState,
    onQuery: (String) -> Unit,
    onToggleColumn: (BoardColumn) -> Unit,
    onMove: (String, BoardColumn) -> Unit,
    onReschedule: (String, IsoDate) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // rememberSaveable no persiste enums: se guarda el ordinal.
    var viewOrdinal by rememberSaveable { mutableIntStateOf(BoardView.PANEL.ordinal) }
    val view = BoardView.entries[viewOrdinal]

    // El buscador filtra en las tres vistas; la píldora solo donde hay mezcla
    // (en Panel las columnas ya separan por estado).
    val queried = BoardFilters.apply(state, state.query, null)
    val filtered = if (view == BoardView.PANEL) queried
    else BoardFilters.apply(state, state.query, state.columnFilter)

    val total = state.proposed.size + state.scheduled.size + state.done.size

    Column(modifier = modifier.fillMaxSize()) {
        FloatingSearchBar(
            query = state.query,
            onQuery = onQuery,
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            BoardView.entries.forEachIndexed { index, v ->
                SegmentedButton(
                    selected = index == viewOrdinal,
                    onClick = { viewOrdinal = index },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = BoardView.entries.size,
                    ),
                    label = { Text(v.title) },
                )
            }
        }
        // Píldoras de estado con conteos: Lista y Calendario.
        if (view != BoardView.PANEL && total > 0) {
            StatusPills(
                proposed = state.proposed.size,
                scheduled = state.scheduled.size,
                done = state.done.size,
                selected = state.columnFilter,
                onToggle = onToggleColumn,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (total == 0) {
                EmptyState(
                    title = "Pizarra vacía",
                    message = "Marque Listo en una tarjeta de Camas y aparecerá aquí, en Propuesto.",
                    modifier = Modifier.fillMaxSize(),
                )
            } else when (view) {
                BoardView.PANEL -> BoardPanelView(
                    state = queried,
                    onMove = onMove,
                    modifier = Modifier.fillMaxSize(),
                )
                BoardView.LIST -> BoardListView(
                    state = filtered,
                    onMove = onMove,
                    modifier = Modifier.fillMaxSize(),
                )
                BoardView.CALENDAR -> BoardCalendarView(
                    state = filtered,
                    onMove = onMove,
                    onReschedule = onReschedule,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // Hoja inferior: resumen del día + FAB para agendar.
        DaySummarySheet(
            proposed = state.proposed.size,
            scheduled = state.scheduled.size,
            done = state.done.size,
            onAdd = onAdd,
        )
    }
}

/** Buscador flotante estilo Maps: filtra cama, paciente o procedimiento. */
@Composable
private fun FloatingSearchBar(
    query: String,
    onQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text("Buscar cama, paciente…") },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MapsBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "Buscar",
                    tint = MapsBlue,
                )
            }
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Limpiar búsqueda",
                        tint = TextSecondary,
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(6.dp, RoundedCornerShape(28.dp)),
    )
}

/**
 * Píldoras de estado con conteos. Tocar filtra por ese estado, tocar de
 * nuevo limpia. Sin filtro las tres se ven sólidas (mockup); con filtro,
 * la activa queda sólida y las otras claras.
 */
@Composable
private fun StatusPills(
    proposed: Int,
    scheduled: Int,
    done: Int,
    selected: BoardColumn?,
    onToggle: (BoardColumn) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusPill(
            column = BoardColumn.PROPOSED,
            count = proposed,
            icon = Icons.Filled.Edit,
            color = ClinicalWatch,
            // Ámbar con texto blanco no contrasta: texto oscuro.
            solidContent = TextPrimary,
            selected = selected,
            onToggle = onToggle,
            modifier = Modifier.weight(1f),
        )
        StatusPill(
            column = BoardColumn.SCHEDULED,
            count = scheduled,
            icon = Icons.Filled.CalendarMonth,
            color = MapsBlue,
            solidContent = Color.White,
            selected = selected,
            onToggle = onToggle,
            modifier = Modifier.weight(1f),
        )
        StatusPill(
            column = BoardColumn.DONE,
            count = done,
            icon = Icons.Filled.CheckCircle,
            color = ClinicalStable,
            solidContent = Color.White,
            selected = selected,
            onToggle = onToggle,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatusPill(
    column: BoardColumn,
    count: Int,
    icon: ImageVector,
    color: Color,
    solidContent: Color,
    selected: BoardColumn?,
    onToggle: (BoardColumn) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelected = selected == column
    val solid = selected == null || isSelected
    val container = if (solid) color else Color.White
    val content = if (solid) solidContent else color
    val border = if (solid) Modifier else Modifier.border(1.dp, color, CircleShape)
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(container)
            .then(border)
            .clickable { onToggle(column) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "${column.title} ($count)",
            color = content,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * Hoja inferior que asoma: "Hoy - {fecha}" con la mini tira del día
 * (puntos por estado con conteos) y el FAB azul para agendar.
 */
@Composable
private fun DaySummarySheet(
    proposed: Int,
    scheduled: Int,
    done: Int,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }
    val title = remember(today) {
        val fmt = DateTimeFormatter.ofPattern("d MMM", Locale("es"))
        "Hoy - ${today.format(fmt)}"
    }
    Box(modifier = modifier.fillMaxWidth()) {
        Card(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Tirador.
                Box(
                    Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE0E0E0)),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(10.dp))
                // Mini tira del día: puntos por estado con conteos.
                DayDots(proposed = proposed, scheduled = scheduled, done = done)
                Spacer(Modifier.height(8.dp))
            }
        }
        FloatingActionButton(
            onClick = onAdd,
            containerColor = MapsBlue,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 24.dp)
                .size(64.dp),
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "Agendar procedimiento",
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun DayDots(
    proposed: Int,
    scheduled: Int,
    done: Int,
    modifier: Modifier = Modifier,
) {
    val nodes = listOf(
        ClinicalWatch to proposed,
        MapsBlue to scheduled,
        ClinicalStable to done,
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp),
        verticalAlignment = Alignment.Top,
    ) {
        nodes.forEachIndexed { index, (color, count) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(color),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "$count",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                )
            }
            if (index < nodes.lastIndex) {
                Box(
                    Modifier
                        .weight(1f)
                        .padding(top = 6.dp)
                        .height(2.dp)
                        .background(Color(0xFFE0E0E0)),
                )
            }
        }
    }
}

/** Panel: el Kanban actual, intacto (responsivo ancho/teléfono como siempre). */
@Composable
private fun BoardPanelView(
    state: BoardUiState,
    onMove: (String, BoardColumn) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wide = maxWidth > 600.dp
        if (wide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BoardColumnView(
                    column = BoardColumn.PROPOSED,
                    items = state.proposed,
                    onMove = onMove,
                    modifier = Modifier.weight(1f),
                    scrollable = true,
                )
                BoardColumnView(
                    column = BoardColumn.SCHEDULED,
                    items = state.scheduled,
                    onMove = onMove,
                    modifier = Modifier.weight(1f),
                    scrollable = true,
                )
                BoardColumnView(
                    column = BoardColumn.DONE,
                    items = state.done,
                    onMove = onMove,
                    modifier = Modifier.weight(1f),
                    scrollable = true,
                )
            }
        } else {
            // Teléfono: Kanban auténtico tipo Trello — 3 páginas de ancho
            // completo con swipe horizontal (antes: columnas apiladas, casi
            // idénticas a la vista Lista).
            BoardPagerView(
                state = state,
                onMove = onMove,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Panel en teléfono: Kanban real tipo Trello. Tres páginas de ancho completo
 * (Propuesto → Programado → Realizado) con desplazamiento horizontal; cada
 * página desplaza su propia lista vertical de tarjetas.
 */
@Composable
private fun BoardPagerView(
    state: BoardUiState,
    onMove: (String, BoardColumn) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages = listOf(
        BoardColumn.PROPOSED to state.proposed,
        BoardColumn.SCHEDULED to state.scheduled,
        BoardColumn.DONE to state.done,
    )
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    Column(modifier = modifier.fillMaxSize()) {
        // Indicador de páginas: títulos con contador, tocables.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            pages.forEachIndexed { index, (column, items) ->
                val selected = pagerState.currentPage == index
                TextButton(onClick = {
                    scope.launch { pagerState.animateScrollToPage(index) }
                }) {
                    Text(
                        "${column.title} (${items.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else TextSecondary,
                    )
                }
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            val (_, items) = pages[page]
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.procedure.id }) { item ->
                    ProcedureCard(
                        item = item,
                        onMove = onMove,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** Lista: todos los procedimientos agrupados por estado, ordenados por cama. */
@Composable
private fun BoardListView(
    state: BoardUiState,
    onMove: (String, BoardColumn) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        boardSection(BoardColumn.PROPOSED, state.proposed.sortedByBed(), onMove)
        boardSection(BoardColumn.SCHEDULED, state.scheduled.sortedByBed(), onMove)
        boardSection(BoardColumn.DONE, state.done.sortedByBed(), onMove)
    }
}

private fun LazyListScope.boardSection(
    column: BoardColumn,
    items: List<BoardItem>,
    onMove: (String, BoardColumn) -> Unit,
) {
    item(key = "header-${column.name}") {
        Text(
            "${column.title} (${items.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    items(items, key = { "card-${it.procedure.id}" }) { item ->
        ProcedureCard(
            item = item,
            onMove = onMove,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BoardColumnView(
    column: BoardColumn,
    items: List<BoardItem>,
    onMove: (String, BoardColumn) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * true: la columna tiene altura acotada (tablet) y puede desplazar su
     * propia lista. false: vive dentro de un LazyColumn padre (teléfono);
     * un LazyColumn anidado recibiría altura infinita y tumba la app
     * (IllegalStateException al medir), así que se lista con Column.
     */
    scrollable: Boolean,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "${column.title} (${items.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        if (!scrollable || column == BoardColumn.DONE) {
            // Sin desplazamiento propio: lista directa.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { item ->
                    ProcedureCard(item = item, onMove = onMove, modifier = Modifier.fillMaxWidth())
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.procedure.id }) { item ->
                    ProcedureCard(item = item, onMove = onMove, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** Píldora de estado pequeña en la tarjeta ("Pendiente"/"Completado"). */
@Composable
private fun StatusTag(
    text: String,
    icon: ImageVector,
    color: Color,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

@Composable
internal fun ProcedureCard(
    item: BoardItem,
    onMove: (String, BoardColumn) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = item.procedure
    val column = BoardColumn.entries.firstOrNull { it.state == p.status }
    val stateColor = boardStripeColor(p.status)
    // Botones de acción más grandes (el comportamiento onMove no cambia).
    val bigButtonPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (p.status == BoardColumn.DONE.state)
                MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            // Cinta de estado: el color identifica la columna de un vistazo.
            Box(
                modifier = Modifier
                    .width(10.dp)
                    .fillMaxHeight()
                    .background(stateColor),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.bed != null) {
                        Text(
                            "CAMA ${item.bed}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    // Icono circular tintado con el color del estado.
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(stateColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = when (column) {
                                BoardColumn.PROPOSED -> Icons.Filled.Edit
                                BoardColumn.SCHEDULED -> Icons.Filled.CalendarMonth
                                else -> Icons.Filled.CheckCircle
                            },
                            contentDescription = null,
                            tint = stateColor,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Text(
                    p.kind,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    item.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                p.scheduledTime?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                }
                // Píldora de estado (mockup): Pendiente en Programado,
                // Completado en Realizado.
                when (column) {
                    BoardColumn.SCHEDULED -> StatusTag(
                        text = "Pendiente",
                        icon = Icons.Filled.HourglassEmpty,
                        color = stateColor,
                    )
                    BoardColumn.DONE -> StatusTag(
                        text = "Completado",
                        icon = Icons.Filled.CheckCircle,
                        color = stateColor,
                    )
                    else -> {}
                }
                Spacer(Modifier.height(2.dp))
                when (p.status) {
                    BoardColumn.PROPOSED.state -> {
                        Button(
                            onClick = { onMove(p.id, BoardColumn.SCHEDULED) },
                            colors = ButtonDefaults.buttonColors(containerColor = MapsBlue),
                            contentPadding = bigButtonPadding,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Programar")
                        }
                    }
                    BoardColumn.SCHEDULED.state -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onMove(p.id, BoardColumn.DONE) },
                                colors = ButtonDefaults.buttonColors(containerColor = MapsBlue),
                                contentPadding = bigButtonPadding,
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Text("Realizado ✓")
                            }
                            TextButton(onClick = { onMove(p.id, BoardColumn.PROPOSED) }) {
                                Text("← Propuesto")
                            }
                        }
                    }
                    else -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "✓ Completado",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                            TextButton(onClick = { onMove(p.id, BoardColumn.SCHEDULED) }) {
                                Text("← Programado")
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Color de la cinta lateral según la columna (paleta del sistema de diseño). */
private fun boardStripeColor(status: ProcedureState): Color = when (status) {
    BoardColumn.PROPOSED.state -> ClinicalWatch
    BoardColumn.SCHEDULED.state -> PaseRevistaPrimary
    else -> ClinicalStable
}
