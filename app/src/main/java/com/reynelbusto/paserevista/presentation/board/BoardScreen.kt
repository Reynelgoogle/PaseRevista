package com.reynelbusto.paserevista.presentation.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.reynelbusto.paserevista.di.AppContainer
import com.reynelbusto.paserevista.domain.model.BoardColumn
import com.reynelbusto.paserevista.domain.usecase.BoardItem
import com.reynelbusto.paserevista.presentation.components.EmptyState
import com.reynelbusto.paserevista.presentation.theme.TextSecondary

/**
 * Pizarra quirúrgica Kanban: Propuesto → Programado → Realizado.
 * Las tarjetas marcadas Listo en Camas llegan aquí solas.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
        topBar = { TopAppBar(title = { Text("Pizarra quirúrgica") }) },
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
                onMove = vm::moveTo,
                modifier = Modifier.padding(padding),
            )
        }
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
    onMove: (String, BoardColumn) -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = state.proposed.size + state.scheduled.size + state.done.size
    if (total == 0) {
        EmptyState(
            title = "Pizarra vacía",
            message = "Marque Listo en una tarjeta de Camas y aparecerá aquí, en Propuesto.",
            modifier = modifier.fillMaxSize(),
        )
        return
    }
    // rememberSaveable no persiste enums: se guarda el ordinal.
    var viewOrdinal by rememberSaveable { mutableIntStateOf(BoardView.PANEL.ordinal) }
    Column(modifier = modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            BoardView.entries.forEachIndexed { index, view ->
                SegmentedButton(
                    selected = index == viewOrdinal,
                    onClick = { viewOrdinal = index },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = BoardView.entries.size,
                    ),
                    label = { Text(view.title) },
                )
            }
        }
        when (BoardView.entries[viewOrdinal]) {
            BoardView.PANEL -> BoardPanelView(
                state = state,
                onMove = onMove,
                modifier = Modifier.fillMaxSize(),
            )
            BoardView.LIST -> BoardListView(
                state = state,
                onMove = onMove,
                modifier = Modifier.fillMaxSize(),
            )
            BoardView.CALENDAR -> BoardCalendarView(
                state = state,
                onMove = onMove,
                modifier = Modifier.fillMaxSize(),
            )
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

@Composable
internal fun ProcedureCard(
    item: BoardItem,
    onMove: (String, BoardColumn) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = item.procedure
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (p.status == BoardColumn.DONE.state)
                MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (item.bed != null) {
                Text(
                    "CAMA ${item.bed}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
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
            Spacer(Modifier.height(2.dp))
            when (p.status) {
                BoardColumn.PROPOSED.state -> {
                    Button(onClick = { onMove(p.id, BoardColumn.SCHEDULED) }) {
                        Text("Programar →")
                    }
                }
                BoardColumn.SCHEDULED.state -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(onClick = { onMove(p.id, BoardColumn.DONE) }) {
                            Text("Realizado ✓")
                        }
                        TextButton(onClick = { onMove(p.id, BoardColumn.PROPOSED) }) {
                            Text("← Propuesto")
                        }
                    }
                }
                else -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
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
