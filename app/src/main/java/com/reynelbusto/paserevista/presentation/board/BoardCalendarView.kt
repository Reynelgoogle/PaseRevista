package com.reynelbusto.paserevista.presentation.board

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.reynelbusto.paserevista.core.IsoDate
import com.reynelbusto.paserevista.domain.model.BoardColumn
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

/**
 * Vista Calendario de la pizarra: rejilla mensual (semana desde lunes) con
 * los procedimientos agrupados por fecha programada. Los botones de mover
 * de cada tarjeta funcionan igual que en el resto de vistas.
 */
@Composable
fun BoardCalendarView(
    state: BoardUiState,
    onMove: (String, BoardColumn) -> Unit,
    modifier: Modifier = Modifier,
) {
    val all = remember(state) { state.proposed + state.scheduled + state.done }
    val groups = remember(all) { groupByDate(all) }
    val today = remember { LocalDate.now() }

    var year by rememberSaveable { mutableIntStateOf(today.year) }
    var month by rememberSaveable { mutableIntStateOf(today.monthValue) }
    var selectedIso by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedDate = selectedIso
        ?.let { runCatching { LocalDate.parse(it, ISO) }.getOrNull() }
        ?: today
    val selectedKey: IsoDate = selectedDate.format(ISO)
    val dayItems = groups.byDate[selectedKey].orEmpty().sortedByBed()

    fun shiftMonth(delta: Int) {
        val shifted = LocalDate.of(year, month, 1).plusMonths(delta.toLong())
        year = shifted.year
        month = shifted.monthValue
    }

    val cells = remember(year, month) { monthGrid(year, month) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Cabecera del mes: ← Octubre 2026 →
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { shiftMonth(-1) }) {
                    Icon(
                        Icons.Filled.KeyboardArrowLeft,
                        contentDescription = "Mes anterior",
                    )
                }
                Text(
                    monthTitle(year, month),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = { shiftMonth(1) }) {
                    Icon(
                        Icons.Filled.KeyboardArrowRight,
                        contentDescription = "Mes siguiente",
                    )
                }
            }
        }
        // Letras L M M J V S D
        item {
            Row(Modifier.fillMaxWidth()) {
                WEEKDAY_LETTERS.forEach { letter ->
                    Text(
                        letter,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        // Rejilla 6×7
        items(cells.chunked(7)) { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    val key = day.date.format(ISO)
                    CalendarCell(
                        day = day,
                        count = groups.byDate[key]?.size ?: 0,
                        isToday = day.date == today,
                        isSelected = key == selectedKey,
                        onSelect = { selectedIso = key },
                    )
                }
            }
        }
        // Procedimientos del día seleccionado
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                dayTitle(selectedKey),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (dayItems.isEmpty()) {
            item {
                Text(
                    "Sin procedimientos este día.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        } else {
            items(dayItems, key = { it.procedure.id }) { item ->
                ProcedureCard(
                    item = item,
                    onMove = onMove,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        // Sin fecha programada
        if (groups.undated.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Sin fecha programada (${groups.undated.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items(groups.undated.sortedByBed(), key = { it.procedure.id }) { item ->
                ProcedureCard(
                    item = item,
                    onMove = onMove,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun RowScope.CalendarCell(
    day: CalendarDay,
    count: Int,
    isToday: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    val background = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isToday -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .alpha(if (day.inMonth) 1f else 0.35f)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onSelect),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
            )
            if (count > 0) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            } else {
                Spacer(Modifier.size(8.dp))
            }
        }
    }
}
