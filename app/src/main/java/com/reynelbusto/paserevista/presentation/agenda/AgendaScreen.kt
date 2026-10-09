package com.reynelbusto.paserevista.presentation.agenda

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reynelbusto.paserevista.di.AppContainer
import com.reynelbusto.paserevista.domain.model.displayName
import com.reynelbusto.paserevista.domain.usecase.ShareFormatter
import com.reynelbusto.paserevista.presentation.components.CaseCardView
import com.reynelbusto.paserevista.presentation.components.EmptyState
import kotlinx.coroutines.launch

/**
 * AGENDA — timeline vertical de los días, lo más nuevo arriba, como hojear
 * una libreta. Solo lectura: para trabajar está Camas.
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
    val showTodayFab by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 2 }
    }

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
            // El error se conserva hasta el próximo rebuild con éxito.
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Agenda") }) },
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
                    item(key = "day-${section.journeyId}") {
                        Text(
                            section.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    items(section.rows, key = { "card-${it.card.id}" }) { row ->
                        CaseCardView(
                            row = row,
                            displayName = row.patient.displayName(row.card.bed),
                            recentDiagnoses = emptyList(),
                            readOnly = true,
                            originMark = when (section.marks[row.card.id]) {
                                BedMark.CONTINUING -> "viene de ayer"
                                BedMark.NEW -> "nuevo hoy"
                                null -> null
                            },
                            onToggleReady = {},
                            onSaveFields = {},
                            onSavePatientDetails = { _, _, _, _, _, _, _ -> },
                            onAddCustomField = { _, _ -> },
                            onRenameCustomField = { _, _ -> },
                            onSetCustomFieldValue = { _, _ -> },
                            onDeleteCustomField = {},
                            onAddPending = {},
                            onCompletePending = {},
                            onDischarge = {},
                            onShare = { complete ->
                                shareText(
                                    if (complete) ShareFormatter.cardComplete(row.shareData())
                                    else ShareFormatter.cardSimple(row.shareData()),
                                )
                            },
                            onDeleteCase = {},
                            modifier = Modifier.fillMaxWidth(),
                        )
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
}
