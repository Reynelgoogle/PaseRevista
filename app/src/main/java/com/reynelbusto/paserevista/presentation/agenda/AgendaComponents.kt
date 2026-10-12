package com.reynelbusto.paserevista.presentation.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reynelbusto.paserevista.presentation.camas.CardRow
import com.reynelbusto.paserevista.presentation.components.DialogShape28
import com.reynelbusto.paserevista.presentation.components.ExpressiveDialogTitle
import com.reynelbusto.paserevista.presentation.components.TintedCircleIcon
import com.reynelbusto.paserevista.presentation.components.mapsPrimaryColors
import com.reynelbusto.paserevista.presentation.theme.ClinicalCritical
import com.reynelbusto.paserevista.presentation.theme.ClinicalStable
import com.reynelbusto.paserevista.presentation.theme.ClinicalWatch
import com.reynelbusto.paserevista.presentation.theme.MapsBlue
import com.reynelbusto.paserevista.presentation.theme.PaseRevistaPrimary
import com.reynelbusto.paserevista.presentation.theme.TextPrimary
import com.reynelbusto.paserevista.presentation.theme.TextSecondary

/* ── Tira de fechas ─────────────────────────────────────────────── */

/**
 * Tira horizontal con los días cargados para saltar a cualquiera de un toque.
 * El día de hoy va resaltado.
 */
@Composable
fun DateStrip(
    sections: List<AgendaDaySection>,
    dayIndices: Map<String, Int>,
    onSelectDay: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(sections, key = { "strip-${it.journeyId}" }) { section ->
            FilterChip(
                selected = section.isToday,
                onClick = { dayIndices[section.journeyId]?.let(onSelectDay) },
                label = {
                    Text(
                        if (section.isToday) "Hoy" else dayOfMonth(section.date),
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                leadingIcon = null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MapsBlue,
                    selectedLabelColor = Color.White,
                ),
            )
        }
    }
}

/* ── Línea de tiempo ──────────────────────────────────────────── */

/** Punto de color sobre la línea vertical de la bitácora. */
@Composable
fun TimelineRail(
    color: Color,
    isLast: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(24.dp)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        if (!isLast) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(color.copy(alpha = 0.35f)),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
fun eventColor(type: DayEventType): Color = when (type) {
    DayEventType.INGRESO -> ClinicalStable
    DayEventType.ALTA -> TextSecondary
    DayEventType.PENDIENTE -> ClinicalWatch
    DayEventType.PIZARRA -> PaseRevistaPrimary
    DayEventType.CAMBIO -> MaterialTheme.colorScheme.outline
}

/** Una entrada de "Lo que pasó": hora + texto sobre la línea de tiempo. */
@Composable
fun DayEventRow(
    event: DayEvent,
    isLast: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        TimelineRail(color = eventColor(event.type), isLast = isLast)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 10.dp),
        ) {
            Text(
                formatHour(event.occurredAt),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
            Text(
                buildString {
                    if (event.bed != null) append("Cama ${event.bed} · ")
                    append(event.text)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
            )
        }
    }
}

/* ── Fila compacta de cama ────────────────────────────────────── */

/**
 * Una cama como entrada de la bitácora: punto en la línea de tiempo + una
 * línea ("CAMA 4 · colecistectomía · ⚠2"). El toque la expande inline a la
 * tarjeta completa (lo maneja la pantalla).
 */
@Composable
fun AgendaBedRow(
    row: CardRow,
    originMark: String?,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (row.card.ready) ClinicalStable else ClinicalCritical
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onExpand),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Franja de color a la izquierda, como en la pizarra.
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(56.dp)
                    .background(accent),
            )
            Spacer(Modifier.width(10.dp))
            TintedCircleIcon(
                icon = Icons.Filled.Person,
                iconColor = accent,
                circleSize = 36.dp,
                iconSize = 20.dp,
            )
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "CAMA ${row.card.bed}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    if (!row.card.diagnosis.isNullOrBlank()) {
                        Text(
                            " · ${row.card.diagnosis}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (row.openPendings.isNotEmpty()) {
                        Text(
                            "⚠ ${row.openPendings.size} pendiente" +
                                if (row.openPendings.size == 1) "" else "s",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = ClinicalCritical,
                        )
                    }
                    if (originMark != null) {
                        Text(
                            (if (row.openPendings.isNotEmpty()) " · " else "") + originMark,
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = TextSecondary,
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
        }
    }
}

/* ── Nota del día ─────────────────────────────────────────────── */

/** Renglón de la nota libre del día (o insinuación sutil para agregarla). */
@Composable
fun DayNoteRow(
    note: String?,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (note != null) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = null,
                tint = MapsBlue,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                note,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = TextPrimary,
            )
        }
    } else {
        TextButton(onClick = onEdit) {
            Text(
                "＋ Nota del día",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
}

/** Diálogo simple para escribir la nota del día. Todo opcional (regla de oro). */
@Composable
fun NoteEditDialog(
    initial: String,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = DialogShape28,
        title = {
            ExpressiveDialogTitle(
                icon = Icons.Filled.Edit,
                iconColor = MapsBlue,
                title = "Nota del día",
            )
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Ej.: hoy faltó el anestesista") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(text) },
                colors = mapsPrimaryColors(),
            ) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (initial.isNotBlank()) {
                    TextButton(onClick = onClear) { Text("Limpiar") }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}
