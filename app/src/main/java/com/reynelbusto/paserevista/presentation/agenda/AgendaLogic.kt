package com.reynelbusto.paserevista.presentation.agenda

import com.reynelbusto.paserevista.core.IsoDate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Lógica pura de la vista Agenda (timeline vertical), testeable en JVM.
 *
 * La agenda hojea los días como una libreta: lo más nuevo arriba. Cada cama
 * lleva su marca de continuidad respecto al día anterior con datos:
 * - CONTINUING ("viene de ayer"): la cama ya estaba el día anterior.
 * - NEW ("nuevo hoy"): la cama aparece por primera vez ese día.
 */
enum class BedMark { CONTINUING, NEW }

/** Clasifica cada cama del día según estuviera o no el día anterior. */
fun markBeds(current: Set<String>, previous: Set<String>): Map<String, BedMark> =
    current.associateWith { bed ->
        if (bed in previous) BedMark.CONTINUING else BedMark.NEW
    }

/** Un día del timeline con sus camas (número de cama). */
data class AgendaDayInput(val date: IsoDate, val beds: Set<String>)

/**
 * Calcula las marcas de cada día comparando con el día anterior de la lista.
 * `daysNewestFirst[0]` es hoy. El día más antiguo (sin anterior cargado) marca
 * todas sus camas como NEW.
 */
fun computeDayMarks(
    daysNewestFirst: List<AgendaDayInput>,
): Map<IsoDate, Map<String, BedMark>> {
    val result = LinkedHashMap<IsoDate, Map<String, BedMark>>(daysNewestFirst.size)
    daysNewestFirst.forEachIndexed { index, day ->
        val previousBeds = daysNewestFirst.getOrNull(index + 1)?.beds ?: emptySet()
        result[day.date] = markBeds(day.beds, previousBeds)
    }
    return result
}

private val DAY_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es"))

/** "2026-10-08" → "Jueves 8 de octubre". Si falla el parseo, devuelve el ISO. */
fun prettyDayEs(dateIso: IsoDate): String = try {
    DAY_FORMAT.format(LocalDate.parse(dateIso)).replaceFirstChar { it.uppercase() }
} catch (e: Exception) {
    dateIso
}

/**
 * Título de la sección del día: "Hoy · Jueves 8 de octubre",
 * "Ayer · Miércoles 7 de octubre" o la fecha sola.
 */
fun agendaDayTitle(dateIso: IsoDate, todayIso: IsoDate): String {
    val pretty = prettyDayEs(dateIso)
    val yesterdayIso = try {
        LocalDate.parse(todayIso).minusDays(1).toString()
    } catch (e: Exception) {
        ""
    }
    return when (dateIso) {
        todayIso -> "Hoy · $pretty"
        yesterdayIso -> "Ayer · $pretty"
        else -> pretty
    }
}
