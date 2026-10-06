package com.reynelbusto.paserevista.core

import java.util.UUID

/** Genera identificadores UUID v4 (TEXT 36) en cliente. Sin dependencia de la BD. */
fun newId(): String = UUID.randomUUID().toString()
