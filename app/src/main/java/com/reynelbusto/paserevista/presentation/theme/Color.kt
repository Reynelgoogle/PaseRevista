package com.reynelbusto.paserevista.presentation.theme

import androidx.compose.ui.graphics.Color

/** Identidad visual PaseRevista — paleta del sistema de diseño aprobado. */
val PaseRevistaPrimary = Color(0xFF0B5E7A) // Azul principal
val PaseRevistaTurquoise = Color(0xFF00B4D8) // Turquesa (acentos, nunca texto clínico)
val PaseRevistaLightBlue = Color(0xFFE6F7FB) // Azul claro (fondos suaves)
val PaseRevistaBackground = Color(0xFFF5F7FA) // Gris fondo

// Estados clínicos: pocos colores, siempre el mismo significado.
val ClinicalStable = Color(0xFF10B981) // Verde — Estable
val ClinicalWatch = Color(0xFFF59E0B) // Ámbar — Vigilancia (solo acentos/gráficos)
val ClinicalCritical = Color(0xFFEF4444) // Rojo — Crítico

// Prioridades clínicas P1/P2/P3.
val PriorityP1 = Color(0xFFEF4444)
val PriorityP2 = Color(0xFFF59E0B)
val PriorityP3 = Color(0xFF6B7280)

// Texto clínico: nunca turquesa ni ámbar sobre blanco (contraste).
val TextPrimary = Color(0xFF111827)
val TextSecondary = Color(0xFF4B5563)
val TextOnPrimary = Color(0xFFFFFFFF)
