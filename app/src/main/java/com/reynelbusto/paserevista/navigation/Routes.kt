package com.reynelbusto.paserevista.navigation

/** Destinos principales (bottom bar). Rutas por nombre; sin objetos clínicos en la ruta. */
object Routes {
    const val HOME = "home"
    const val BOARD = "board"
    const val PATIENTS = "patients"
    const val PENDINGS = "pendings"
    const val MORE = "more"

    /** Ficha del paciente: detalle fuera de la bottom bar. */
    const val CHART = "chart/{patientId}"
    const val HISTORY = "history"

    fun chart(patientId: String): String = "chart/$patientId"
}
