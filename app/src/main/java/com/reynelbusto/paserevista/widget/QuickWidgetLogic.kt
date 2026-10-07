package com.reynelbusto.paserevista.widget

/**
 * Lógica pura del widget "Guardia rápida" (sin dependencias Android):
 * testeable en JVM con kotlinc directo.
 */

/**
 * Primera cama libre: el menor entero positivo no usado en la jornada.
 * Las camas no numéricas (o <= 0) se ignoran para el cálculo.
 */
fun nextFreeBed(usedBeds: Collection<String>): String {
    val used = usedBeds
        .mapNotNull { it.trim().toIntOrNull()?.takeIf { n -> n > 0 } }
        .toSet()
    var n = 1
    while (n in used) n++
    return n.toString()
}

/** Título de la fila: "CAMA 1 · HBP" (sin diagnóstico ⇒ solo "CAMA 1"). */
fun quickRowTitle(bed: String, diagnosis: String?): String {
    val b = bed.trim()
    val dx = diagnosis?.trim().orEmpty()
    return if (dx.isEmpty()) "CAMA $b" else "CAMA $b · $dx"
}

/** Subtítulo de la fila: "Sin pendientes" / "1 pendiente" / "3 pendientes". */
fun quickRowSubtitle(openCount: Int): String = when {
    openCount <= 0 -> "Sin pendientes"
    openCount == 1 -> "1 pendiente"
    else -> "$openCount pendientes"
}
