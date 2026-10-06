package com.reynelbusto.paserevista.presentation.history

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.reynelbusto.paserevista.domain.model.Journey
import com.reynelbusto.paserevista.presentation.components.EmptyState
import com.reynelbusto.paserevista.presentation.components.ReviewBadge
import com.reynelbusto.paserevista.presentation.theme.TextSecondary

/**
 * FASE 9 — Historial: jornadas agrupadas por mes, solo lectura.
 * Sin ciclos de navegación: el detalle es una vista, no un destino.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory(container))
    val state by vm.uiState.collectAsState()
    var selected by remember { mutableStateOf<Journey?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        if (selected != null) selected = null else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                title = { Text(if (selected != null) "Detalle de jornada" else "Historial") },
            )
        },
    ) { padding ->
        val journey = selected
        if (journey != null) {
            JourneyDetail(vm = vm, journey = journey, modifier = Modifier.padding(padding))
        } else {
            when {
                state.isLoading -> LoadingHistory(Modifier.padding(padding))
                state.byMonth.isEmpty() -> EmptyState(
                    title = "Sin historial",
                    message = "Las jornadas completadas aparecerán aquí, agrupadas por mes.",
                    modifier = Modifier.padding(padding).fillMaxSize(),
                )
                else -> LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    state.byMonth.toSortedMap(compareByDescending { it }).forEach { (month, journeys) ->
                        item {
                            Text(
                                HistoryViewModel.monthLabel(month),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        items(journeys, key = { it.id }) { j ->
                            JourneyCard(journey = j, onClick = { selected = j })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingHistory(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { CircularProgressIndicator() }
}

@Composable
private fun JourneyCard(journey: Journey, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    HistoryViewModel.prettyDate(journey.clinicalDate),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Origen: ${if (journey.origin.code == "auto") "automático" else "manual"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            Text("Ver", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun JourneyDetail(vm: HistoryViewModel, journey: Journey, modifier: Modifier = Modifier) {
    val detail by vm.detailFlow(journey).collectAsState(initial = JourneyDetailState(journey))
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                "${detail.reviewed}/${detail.total} revisados",
                style = MaterialTheme.typography.titleSmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(4.dp))
        }
        items(detail.rows, key = { it.patientName + it.bed }) { row ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ReviewBadge(state = row.reviewState)
                    Column(Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.bed?.let {
                                Text(
                                    "CAMA $it",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(row.patientName, style = MaterialTheme.typography.bodyMedium)
                        }
                        row.evolution?.let {
                            Text(
                                it.lowercase().replace('_', ' '),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}
