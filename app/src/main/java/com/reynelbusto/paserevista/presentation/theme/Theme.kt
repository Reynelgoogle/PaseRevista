package com.reynelbusto.paserevista.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val PaseRevistaLightColors = lightColorScheme(
    primary = PaseRevistaPrimary,
    onPrimary = TextOnPrimary,
    primaryContainer = PaseRevistaLightBlue,
    onPrimaryContainer = PaseRevistaPrimary,
    secondary = PaseRevistaTurquoise,
    onSecondary = TextOnPrimary,
    background = PaseRevistaBackground,
    onBackground = TextPrimary,
    surface = TextOnPrimary,
    onSurface = TextPrimary,
    surfaceVariant = PaseRevistaLightBlue,
    onSurfaceVariant = TextSecondary,
    error = ClinicalCritical,
    onError = TextOnPrimary,
)

/**
 * Tema PaseRevista: medicina + precisión + tranquilidad.
 * Claro primero (jornada hospitalaria diurna); oscuro queda para fase posterior.
 */
@Composable
fun PaseRevistaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PaseRevistaLightColors,
        typography = PaseRevistaTypography,
        content = content,
    )
}
