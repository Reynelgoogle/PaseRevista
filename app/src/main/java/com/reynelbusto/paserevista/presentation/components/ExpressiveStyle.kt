package com.reynelbusto.paserevista.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reynelbusto.paserevista.presentation.theme.MapsBlue
import com.reynelbusto.paserevista.presentation.theme.TextPrimary

/**
 * Lenguaje "Identidad fija" estilo Maps, compartido por toda la app.
 * - Diálogos y hojas: esquinas de 28dp.
 * - Cabeceras con icono en círculo tintado.
 * - Botón primario: azul Maps, grande.
 */

/** Esquinas grandes para todos los diálogos y ventanas emergentes. */
val DialogShape28 = RoundedCornerShape(28.dp)

/** Colores del botón primario: azul Maps siempre igual. */
@Composable
fun mapsPrimaryColors() = ButtonDefaults.buttonColors(containerColor = MapsBlue)

/**
 * Icono en círculo tintado (el sello de la identidad fija).
 * Se usa en cabeceras de diálogos y en tarjetas.
 */
@Composable
fun TintedCircleIcon(
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier,
    circleSize: Dp = 48.dp,
    iconSize: Dp = 24.dp,
) {
    Box(
        modifier = modifier
            .size(circleSize)
            .clip(CircleShape)
            .background(iconColor.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** Cabecera de diálogo: icono en círculo tintado + título en negrita. */
@Composable
fun ExpressiveDialogTitle(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TintedCircleIcon(icon = icon, iconColor = iconColor)
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )
    }
}
