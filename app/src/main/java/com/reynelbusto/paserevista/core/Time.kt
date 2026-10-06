package com.reynelbusto.paserevista.core

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Fecha clínica ISO-8601 `yyyy-MM-dd`. La fecha clínica ordena la medicina. */
typealias IsoDate = String

private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

/** Reloj abstraído para que el dominio sea testeable sin Android. */
interface Clock {
    fun nowMillis(): Long
    fun todayIso(zone: ZoneId = ZoneId.systemDefault()): IsoDate
}

class SystemClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun todayIso(zone: ZoneId): IsoDate = LocalDate.now(zone).format(ISO)
}

/** Días calendario entre dos fechas ISO (inclusive). Requiere end >= start. */
fun daysBetweenInclusive(startIso: IsoDate, endIso: IsoDate): Int {
    val start = LocalDate.parse(startIso, ISO)
    val end = LocalDate.parse(endIso, ISO)
    require(!end.isBefore(start)) { "endIso ($endIso) anterior a startIso ($startIso)" }
    return (end.toEpochDay() - start.toEpochDay() + 1).toInt()
}

/**
 * Día de tratamiento derivado de la fecha real de inicio menos los días en suspensión.
 * NUNCA se almacena como texto ("D3"): siempre se calcula.
 *
 * @param suspensions rangos [inicio, fin] ISO, ambos inclusive, ya cerrados o en curso.
 */
fun treatmentDay(
    startIso: IsoDate,
    todayIso: IsoDate,
    suspensions: List<Pair<IsoDate, IsoDate>> = emptyList(),
): Int {
    var day = daysBetweenInclusive(startIso, todayIso)
    val today = LocalDate.parse(todayIso, ISO)
    val start = LocalDate.parse(startIso, ISO)
    for ((s, e) in suspensions) {
        val sDate = LocalDate.parse(s, ISO).coerceAtLeast(start)
        val eDate = LocalDate.parse(e, ISO).coerceAtMost(today)
        if (!eDate.isBefore(sDate)) {
            day -= (eDate.toEpochDay() - sDate.toEpochDay() + 1).toInt()
        }
    }
    return day.coerceAtLeast(1)
}

/** Edad en años cumplidos a partir de la fecha de nacimiento ISO. */
fun ageAt(birthIso: IsoDate, todayIso: IsoDate): Int {
    val birth = LocalDate.parse(birthIso, ISO)
    val today = LocalDate.parse(todayIso, ISO)
    var age = today.year - birth.year
    if (today.dayOfYear < birth.dayOfYear) age--
    return age.coerceAtLeast(0)
}
