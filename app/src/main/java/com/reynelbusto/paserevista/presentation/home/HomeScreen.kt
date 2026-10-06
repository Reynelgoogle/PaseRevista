package com.reynelbusto.paserevista.presentation.home

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reynelbusto.paserevista.di.AppContainer
import com.reynelbusto.paserevista.domain.model.ReviewState
import com.reynelbusto.paserevista.presentation.components.EmptyState
import com.reynelbusto.paserevista.presentation.components.PriorityChip
import com.reynelbusto.paserevista.presentation.components.ReviewBadge
import com.reynelbusto.paserevista.presentation.theme.TextSecondary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * HOY — la agenda del pase de revista.
 * En < 5 segundos: cuántos pacientes, cuáles prioritarios, qué pendientes,
 * cuáles nuevos, próximos al alta. Sin formularios: es el punto de partida.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(container: AppContainer, onOpenPatient: (String) -> Unit) {
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory(container))
    val state by vm.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Pase de revista", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = state.journey?.clinicalDate?.let(::prettyDate)
                                ?: prettyDate(LocalDate.now().toString()),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingBody(Modifier.padding(padding))
            state.error != null -> ErrorBody(
                message = state.error!!,
                onRetry = { vm.retry() },
                modifier = Modifier.padding(padding),
            )
            state.journey == null -> EmptyState(
                title = "Aún no hay jornada hoy",
                message = "Crea la jornada del día para comenzar el pase de revista. " +
                    "Los pacientes activos se incorporarán automáticamente.",
                actionLabel = "Comenzar pase de revista",
                onAction = { vm.startJourney() },
                modifier = Modifier.padding(padding),
            )
            else -> HomeContent(
                state = state,
                onOpenPatient = onOpenPatient,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun LoadingBody(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { CircularProgressIndicator() }
}

@Composable
private fun ErrorBody(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        title = "No se pudo cargar",
        message = message,
        actionLabel = "Reintentar",
        onAction = onRetry,
        modifier = modifier,
    )
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onOpenPatient: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.rows.isEmpty()) {
        EmptyState(
            title = "Jornada sin pacientes",
            message = "No hay pacientes activos en el censo de hoy. " +
                "Agrega un nuevo ingreso para comenzar.",
            modifier = modifier,
        )
        return
    }
    // "Tienes un pase en curso": el primer no revisado en orden P1 → cama.
    val nextPending = state.rows.firstOrNull { it.reviewState != ReviewState.COMPLETED }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SummaryRow(state)
            Spacer(Modifier.height(4.dp))
        }
        if (nextPending != null) {
            item {
                Button(
                    onClick = { onOpenPatient(nextPending.patientId) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (state.reviewedCount == 0) "Comenzar pase de revista"
                        else "Continuar pase de revista",
                    )
                }
            }
        }
        items(state.rows, key = { it.patientId }) { row ->
            PatientCard(row = row, onClick = { onOpenPatient(row.patientId) })
        }
    }
}

@Composable
private fun SummaryRow(state: HomeUiState) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SummaryChip("${state.totalPatients}", "Pacientes") }
        item { SummaryChip("${state.reviewedCount}", "Revisados") }
        item { SummaryChip("${state.p1Count}", "P1") }
        item { SummaryChip("${state.openPendingsCount}", "Pendientes") }
        item { SummaryChip("${state.plannedDischargesCount}", "Altas prev.") }
    }
}

@Composable
private fun SummaryChip(value: String, label: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun PatientCard(row: PatientRow, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ReviewBadge(state = row.reviewState)
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (row.bed != null) {
                        Text(
                            text = "CAMA ${row.bed}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    row.priority?.let { PriorityChip(priority = it) }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${row.fullName}, ${row.age} años",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = row.mainDiagnosis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                if (row.hasOpenPending) {
                    Text(
                        text = "Tiene pendientes abiertos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private fun prettyDate(iso: String): String = try {
    val date = LocalDate.parse(iso)
    val fmt = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es"))
    date.format(fmt).replaceFirstChar { it.uppercase() }
} catch (e: Exception) {
    iso
}
