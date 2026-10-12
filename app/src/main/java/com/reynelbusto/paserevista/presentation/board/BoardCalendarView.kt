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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
 * Vista Calendario de la pizarra: ya no es un póster. Rejilla mensual o
 * semanal (lunes primero), con puntos/conteos por día. Tocar un día abre
 * su detalle en una hoja inferior; los "Sin fecha programada" se programan
 * tocando el día destino. Sin drag&drop por decisión explícita.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardCalendarView(
    state: BoardUiState,
    onMove: (String, BoardColumn) -> Unit,
    onReschedule: (String, IsoDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val all = remember(state) { state.proposed + state.scheduled + state.done }
    val groups = remember(all) { groupByDate(all) }
    val today = remember { LocalDate.now() }
    val todayIso: IsoDate = remember(today) { today.format(ISO) }

    var weekMode by rememberSaveable { mutableStateOf(false) }
    var year by rememberSaveable { mutableIntStateOf(today.year) }
    var month by rememberSaveable { mutableIntStateOf(today.monthValue) }
    var weekAnchorIso by rememberSaveable { mutableStateOf(todayIso) }
    var selectedIso by rememberSaveable { mutableStateOf(todayIso) }
    var sheetIso by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedKey: IsoDate = selectedIso

    fun shiftMonth(delta: Int) {
        val shifted = LocalDate.of(year, month, 1).plusMonths(delta.toLong())
        year = shifted.year
        month = shifted.monthValue
    }

    fun shiftWeek(delta: Int) {
        weekAnchorIso = LocalDate.parse(weekAnchorIso, ISO)
            .plusWeeks(delta.toLong()).format(ISO)
    }

    fun goToday() {
        year = today.year
        month = today.monthValue
        weekAnchorIso = todayIso
        selectedIso = todayIso
    }

    fun selectDay(key: IsoDate) {
        selectedIso = key
        sheetIso = key
    }

    val weekDays = remember(weekAnchorIso) {
        weekOf(LocalDate.parse(weekAnchorIso, ISO))
    }
    val monthCells = remember(year, month) { monthGrid(year, month) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Cabecera: ← título →
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { if (weekMode) shiftWeek(-1) else shiftMonth(-1) }) {
                    Icon(
                        Icons.Filled.KeyboardArrowLeft,
                        contentDescription = "Anterior",
                    )
                }
                Text(
                    if (weekMode) weekTitle(weekDays) else monthTitle(year, month),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = { if (weekMode) shiftWeek(1) else shiftMonth(1) }) {
                    Icon(
                        Icons.Filled.KeyboardArrowRight,
                        contentDescription = "Siguiente",
                    )
                }
            }
        }
        // Mes | Semana + Hoy
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { weekMode = false }) {
                    Text(
                        "Mes",
                        fontWeight = if (!weekMode) FontWeight.Bold else FontWeight.Normal,
                        color = if (!weekMode) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { weekMode = true }) {
                    Text(
                        "Semana",
                        fontWeight = if (weekMode) FontWeight.Bold else FontWeight.Normal,
                        color = if (weekMode) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { goToday() }) {
                    Text("Hoy")
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
        // Rejilla: 6×7 en mes, 7 días en semana
        if (weekMode) {
            item {
                Row(Modifier.fillMaxWidth()) {
                    weekDays.forEach { date ->
                        val key = date.format(ISO)
                        CalendarCell(
                            day = CalendarDay(date = date, inMonth = true),
                            count = groups.byDate[key]?.size ?: 0,
                            isToday = date == today,
                            isSelected = key == selectedKey,
                            onSelect = { selectDay(key) },
                        )
                    }
                }
            }
        } else {
            items(monthCells.chunked(7)) { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        val key = day.date.format(ISO)
                        CalendarCell(
                            day = day,
                            count = groups.byDate[key]?.size ?: 0,
                            isToday = day.date == today,
                            isSelected = key == selectedKey,
                            onSelect = { selectDay(key) },
                        )
                    }
                }
            }
        }
        // Sin fecha programada: cada tarjeta se programa tocando el día destino
        if (groups.undated.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Sin fecha programada (${groups.undated.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Toca un día del calendario para programar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(groups.undated.sortedByBed(), key = { it.procedure.id }) { item ->
                Column {
                    ProcedureCard(
                        item = item,
                        onMove = onMove,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        onClick = { onReschedule(item.procedure.id, selectedKey) },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("Programar el ${dayTitle(selectedKey)}")
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }

    // Detalle del día: hoja inferior con las tarjetas (botones intactos)
    val sheetKey = sheetIso
    if (sheetKey != null) {
        val dayItems = groups.byDate[sheetKey].orEmpty().sortedByBed()
        ModalBottomSheet(onDismissRequest = { sheetIso = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    dayTitle(sheetKey),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (dayItems.isEmpty()) {
                    Text(
                        "Sin procedimientos este día.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    dayItems.forEach { item ->
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
            val label = dayCountLabel(count)
            if (count > 0) {
                if (label.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                } else {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                Spacer(Modifier.size(8.dp))
            }
        }
    }
}
