package com.reynelbusto.paserevista.presentation.agenda

import com.reynelbusto.paserevista.core.IsoDate
import com.reynelbusto.paserevista.domain.model.CaseHistoryEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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

/* ── Bitácora del día ("Lo que pasó") ─────────────────────────────── */

/** Tipo de evento de la bitácora: define el color del punto en la línea de tiempo. */
enum class DayEventType { INGRESO, ALTA, PENDIENTE, PIZARRA, CAMBIO }

/** Un evento de la bitácora de un día. */
data class DayEvent(
    val occurredAt: Long,
    /** Número de cama si se pudo resolver desde las tarjetas del día. */
    val bed: String?,
    val text: String,
    val type: DayEventType,
)

/**
 * Clasifica un resumen del historial en un tipo de evento, por convenciones
 * de texto de los use cases ("Cama N añadida", "Alta — …",
 * "Pendiente completado: …", "… pizarra").
 */
fun classifyHistoryEvent(summary: String): DayEventType {
    val s = summary.trim()
    return when {
        s.startsWith("Cama ") && s.endsWith("añadida") -> DayEventType.INGRESO
        s.startsWith("Alta") -> DayEventType.ALTA
        s.startsWith("Pendiente completado") -> DayEventType.PENDIENTE
        s.contains("pizarra", ignoreCase = true) -> DayEventType.PIZARRA
        else -> DayEventType.CAMBIO
    }
}

/**
 * Construye la bitácora de un día: entradas del historial ordenadas por hora,
 * con la cama resuelta cuando se conoce.
 */
fun buildDayEvents(
    entries: List<CaseHistoryEntry>,
    bedByPatient: Map<String, String>,
): List<DayEvent> = entries
    .sortedBy { it.occurredAt }
    .map { e ->
        DayEvent(
            occurredAt = e.occurredAt,
            bed = bedByPatient[e.patientId],
            text = e.summary,
            type = classifyHistoryEvent(e.summary),
        )
    }

/* ── Formato de hora y tira de fechas ─────────────────────────────── */

private val HOUR_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** millis → "14:35" en la zona horaria del dispositivo. */
fun formatHour(millis: Long): String = try {
    HOUR_FORMAT.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
} catch (e: Exception) {
    ""
}

/** "2026-10-08" → "8" (número del día para la tira de fechas). */
fun dayOfMonth(dateIso: IsoDate): String = try {
    LocalDate.parse(dateIso).dayOfMonth.toString()
} catch (e: Exception) {
    dateIso
}

/** Lo que la pantalla necesita para calcular el índice Lazy de cada día. */
data class DayLayout(
    val journeyId: String,
    val hasEvents: Boolean,
    val eventCount: Int,
    val rowCount: Int,
)

/**
 * Índice del ítem de cabecera de cada día en el LazyColumn de la agenda.
 * Orden de ítems por día: cabecera(1) + nota(1) +
 * ["Lo que pasó"(1) + eventos si hay] + camas.
 */
fun computeDayIndices(days: List<DayLayout>): Map<String, Int> {
    val result = LinkedHashMap<String, Int>(days.size)
    var index = 0
    for (day in days) {
        result[day.journeyId] = index
        index += 2 + (if (day.hasEvents) 1 + day.eventCount else 0) + day.rowCount
    }
    return result
}
