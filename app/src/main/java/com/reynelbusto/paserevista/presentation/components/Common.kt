package com.reynelbusto.paserevista.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.reynelbusto.paserevista.domain.model.ClinicalPriority
import com.reynelbusto.paserevista.presentation.theme.PriorityP1
import com.reynelbusto.paserevista.presentation.theme.PriorityP2
import com.reynelbusto.paserevista.presentation.theme.PriorityP3
import com.reynelbusto.paserevista.presentation.theme.TextSecondary
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

/**
 * Estado vacío profesional: qué falta y qué hacer, sin tecnicismos.
 */
@Composable
fun EmptyState(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/**
 * Etiqueta de la tarjeta: PENDIENTE / LISTO.
 * Listo ⇒ la tarjeta sube a la pizarra Kanban.
 */
@Composable
fun ReadyTag(ready: Boolean, modifier: Modifier = Modifier) {
    val (label, color) = if (ready) {
        "LISTO" to com.reynelbusto.paserevista.presentation.theme.ClinicalStable
    } else {
        "PENDIENTE" to PriorityP2
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.16f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

/** Chip compacto de prioridad clínica P1/P2/P3. */
@Composable
fun PriorityChip(priority: ClinicalPriority, modifier: Modifier = Modifier) {
    val color = when (priority) {
        ClinicalPriority.P1 -> PriorityP1
        ClinicalPriority.P2 -> PriorityP2
        ClinicalPriority.P3 -> PriorityP3
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.14f),
    ) {
        Text(
            text = priority.name,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

/** Punto de color para estado clínico, siempre con etiqueta textual (no solo color). */
@Composable
fun ClinicalDot(color: Color, label: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}
