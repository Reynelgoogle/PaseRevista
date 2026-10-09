package com.reynelbusto.paserevista.presentation.board

import com.reynelbusto.paserevista.core.IsoDate
import com.reynelbusto.paserevista.domain.usecase.BoardItem
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * Lógica pura de las vistas de la pizarra (calendario + orden de la lista).
 * Sin dependencias de Android/Compose: testeable en JVM.
 */
private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val ES: Locale = Locale("es")

/**
 * Acepta yyyy-MM-dd y variantes sin ceros a la izquierda (p. ej. 2026-1-5);
 * cualquier otra cosa se rechaza. Una cirugía con fecha válida nunca debe
 * perderse por un detalle de formato.
 */
private val LENIENT_ISO: DateTimeFormatter =
    DateTimeFormatter.ofPattern("uuuu-M-d").withResolverStyle(ResolverStyle.STRICT)

private fun parseIsoDate(raw: String): LocalDate? =
    runCatching { LocalDate.parse(raw, LENIENT_ISO) }.getOrNull()

/** Letras de la cabecera semanal (la semana empieza en lunes). */
val WEEKDAY_LETTERS: List<String> = listOf("L", "M", "M", "J", "V", "S", "D")

/** Una celda de la rejilla mensual. */
data class CalendarDay(val date: LocalDate, val inMonth: Boolean)

/**
 * Rejilla del mes: 42 celdas (6 filas × 7), semana empezando en lunes.
 * Incluye los días de relleno del mes anterior/siguiente ([CalendarDay.inMonth] = false).
 */
fun monthGrid(year: Int, month: Int): List<CalendarDay> {
    val first = LocalDate.of(year, month, 1)
    // dayOfWeek: lunes=1 … domingo=7. Retrocedemos hasta el lunes de esa semana.
    val back = (first.dayOfWeek.value - 1).toLong()
    val start = first.minusDays(back)
    return (0 until 42).map { i ->
        val d = start.plusDays(i.toLong())
        CalendarDay(date = d, inMonth = d.monthValue == month)
    }
}

/** "Octubre 2026": mes en español con la primera letra en mayúscula. */
fun monthTitle(year: Int, month: Int): String {
    val name = Month.of(month).getDisplayName(TextStyle.FULL, ES)
    return name.replaceFirstChar { it.uppercaseChar() } + " $year"
}

/** "12 de octubre": cabecera del día seleccionado. */
fun dayTitle(iso: IsoDate): String {
    val d = LocalDate.parse(iso, ISO)
    val month = d.month.getDisplayName(TextStyle.FULL, ES)
    return "${d.dayOfMonth} de $month"
}

/** Procedimientos agrupados por fecha programada, más los sin fecha. */
data class DateGroups(
    val byDate: Map<IsoDate, List<BoardItem>>,
    val undated: List<BoardItem>,
)

/**
 * Agrupa por `scheduledDate` (formato yyyy-MM-dd). Los procedimientos sin
 * fecha, con fecha en blanco o con fecha inválida van a [DateGroups.undated]
 * (sección "Sin fecha programada").
 */
fun groupByDate(items: List<BoardItem>): DateGroups {
    val byDate = mutableMapOf<IsoDate, MutableList<BoardItem>>()
    val undated = mutableListOf<BoardItem>()
    for (item in items) {
        val iso = item.procedure.scheduledDate
            ?.takeIf { it.isNotBlank() }
            ?.let { raw -> parseIsoDate(raw)?.format(ISO) }
        if (iso == null) {
            undated.add(item)
        } else {
            byDate.getOrPut(iso) { mutableListOf() }.add(item)
        }
    }
    return DateGroups(byDate = byDate, undated = undated)
}

/**
 * Orden de la vista Lista: camas numéricas primero (por número),
 * luego las no numéricas por texto y las nulas al final.
 */
fun List<BoardItem>.sortedByBed(): List<BoardItem> =
    sortedWith(
        compareBy<BoardItem> { it.bed?.toIntOrNull() == null }
            .thenBy { it.bed?.toIntOrNull() ?: Int.MAX_VALUE }
            .thenBy { it.bed ?: "\uFFFF" }
            .thenBy { it.displayName },
    )
