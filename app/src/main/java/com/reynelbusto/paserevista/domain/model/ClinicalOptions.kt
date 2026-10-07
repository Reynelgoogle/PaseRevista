package com.reynelbusto.paserevista.domain.model

/**
 * Listas de opciones estandarizadas para agilizar la entrada de datos.
 * Kotlin puro (sin Android): testeable en JVM.
 *
 * Regla de Reynel: todo desplegable lleva siempre "Otro…" con texto libre,
 * salvo conjuntos cerrados (grupo sanguíneo, sexo).
 */
object ClinicalOptions {
    /** Opción que revela un campo de texto libre. */
    const val OTHER = "Otro…"

    val PROCEDURES: List<String> = listOf(
        "RTU próstata",
        "RTU vejiga",
        "Nefrolitotomía percutánea",
        "Ureteroscopia",
        "Cistoscopia",
        "Catéter doble J (colocar)",
        "Catéter doble J (retirar)",
        "Prostatectomía",
        "Nefrectomía",
        "Orquiectomía",
        "Cistectomía",
        "Pieloplastia",
        "LEC",
        "Sonda vesical",
        "Cistostomía",
        "Circuncisión",
        "Varicocelectomía",
    )

    val CURRENT_STATES: List<String> = listOf(
        "Estable",
        "En observación",
        "Delicado",
        "Grave",
        "Preoperatorio",
        "Postoperatorio inmediato",
        "En espera de complementarios",
        "Listo para alta",
    )

    val ANTIBIOTICS: List<String> = listOf(
        "Ceftriaxona",
        "Ciprofloxacino",
        "Amikacina",
        "Gentamicina",
        "Meropenem",
        "Piperacilina/tazobactam",
        "Vancomicina",
        "Nitrofurantoína",
        "Fosfomicina",
        "Metronidazol",
        "Cefazolina",
        "Levofloxacino",
    )

    /** Conjunto cerrado: sin "Otro…". */
    val BLOOD_GROUPS: List<String> = listOf(
        "O−", "O+", "A−", "A+", "B−", "B+", "AB−", "AB+",
    )

    val INTERCONSULT_SERVICES: List<String> = listOf(
        "Nefrología",
        "Medicina Interna",
        "Cirugía",
        "Anestesia",
        "Imagenología",
        "Laboratorio",
        "Microbiología",
        "Cardiología",
        "UTI",
    )

    /** Opciones + "Otro…" al final (para desplegables abiertos). */
    fun withOther(options: List<String>): List<String> =
        if (OTHER in options) options else options + OTHER

    /**
     * Resuelve el estado inicial del desplegable desde un valor guardado.
     * Devuelve Pair(selección, textoManual): si el valor no está en la lista,
     * la selección es "Otro…" y el valor va al texto manual.
     */
    fun matchOrOther(value: String?, options: List<String>): Pair<String?, String> =
        when {
            value.isNullOrBlank() -> null to ""
            value in options -> value to ""
            else -> OTHER to value
        }

    /**
     * Valor efectivo a guardar: si la selección es "Otro…", el texto manual
     * (null si está vacío); en otro caso, la selección misma (null = vacío).
     */
    fun effectiveValue(selection: String?, otherText: String): String? =
        if (selection == OTHER) otherText.ifBlank { null } else selection
}
