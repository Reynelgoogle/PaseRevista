package com.reynelbusto.paserevista.widget

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reynelbusto.paserevista.MainActivity
import com.reynelbusto.paserevista.PaseRevistaApp
import com.reynelbusto.paserevista.domain.model.CaseCard
import com.reynelbusto.paserevista.domain.model.Pending
import com.reynelbusto.paserevista.presentation.theme.PaseRevistaTheme
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * Detalle rápido estilo Todoist: overlay translúcido que se abre al tocar
 * una cama del widget "Guardia rápida".
 *
 * - Cabecera "CAMA N" + etiqueta PENDIENTE/LISTO.
 * - Pendientes como subtareas: lista + campo inline "Añadir pendiente".
 * - Botón Marcar Listo / Volver a Pendiente (mismo use case que la app).
 * - Botón "Abrir en la app" (deep link a la tarjeta en MainActivity).
 * Todo reutiliza los use cases existentes; no duplica lógica.
 */
class QuickDetailActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cardId = intent.getStringExtra(WidgetActionReceiver.EXTRA_CARD_ID)
        if (cardId.isNullOrBlank()) {
            finish()
            return
        }
        setContent {
            PaseRevistaTheme {
                QuickDetailScreen(cardId = cardId, onClose = { finish() })
            }
        }
    }
}

@Composable
private fun QuickDetailScreen(cardId: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as PaseRevistaApp).container }
    val scope = rememberCoroutineScope()

    var card by remember { mutableStateOf<CaseCard?>(null) }
    var pendings by remember { mutableStateOf<List<Pending>>(emptyList()) }
    var newPending by remember { mutableStateOf("") }

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }

    suspend fun reload() {
        val loaded = container.caseCardRepository.getById(cardId)
        card = loaded
        pendings = if (loaded != null) {
            container.pendingRepository.getOpenByPatient(loaded.patientId)
        } else {
            emptyList()
        }
    }

    LaunchedEffect(cardId) {
        try {
            reload()
            if (card == null) {
                toast("La tarjeta ya no existe")
                onClose()
            }
        } catch (e: Exception) {
            toast("No se pudo cargar la tarjeta")
            onClose()
        }
    }

    fun afterMutation() {
        scope.launch {
            try {
                reload()
            } catch (e: Exception) {
                toast("No se pudo actualizar")
            }
            WidgetRefresh.refreshAll(context)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable { onClose() },
        contentAlignment = Alignment.Center,
    ) {
        val current = card
        Card(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Cabecera.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "CAMA ${current?.bed ?: "…"}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    if (current != null) {
                        Text(
                            text = if (current.ready) "LISTO" else "PENDIENTE",
                            color = if (current.ready) Color(0xFF2E7D32) else Color(0xFFD32F2F),
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(onClick = onClose) { Text("✕") }
                }

                if (current != null) {
                    Text(
                        text = current.diagnosis?.takeIf { it.isNotBlank() }
                            ?: "Sin diagnóstico",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                    )
                }

                HorizontalDivider()

                // Pendientes como subtareas.
                Text(
                    text = "Pendientes (${pendings.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(pendings, key = { it.id }) { pending ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = pending.description.ifBlank { "(sin descripción)" },
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            container.pendings.complete(
                                                pending.id,
                                                journeyResolutionId = null,
                                            )
                                        } catch (e: Exception) {
                                            toast("No se pudo completar")
                                        }
                                        afterMutation()
                                    }
                                },
                            ) { Text("✓") }
                        }
                    }
                }
                // Campo inline para añadir pendiente.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = newPending,
                        onValueChange = { newPending = it },
                        label = { Text("Añadir pendiente…") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val text = newPending.trim()
                            if (text.isEmpty() || current == null) return@Button
                            newPending = ""
                            scope.launch {
                                try {
                                    container.pendings.create(
                                        patientId = current.patientId,
                                        description = text,
                                        // Idempotencia ante doble tap (M4).
                                        idempotencyKey = UUID.randomUUID().toString(),
                                    )
                                } catch (e: Exception) {
                                    toast("No se pudo añadir")
                                }
                                afterMutation()
                            }
                        },
                    ) { Text("＋") }
                }

                HorizontalDivider()

                // Acciones.
                if (current != null) {
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    container.caseCards.toggleReady(cardId)
                                } catch (e: Exception) {
                                    toast(e.message ?: "No se pudo cambiar")
                                }
                                afterMutation()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (current.ready) "↩ Volver a Pendiente" else "✓ Marcar Listo")
                    }
                }
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(context, MainActivity::class.java).apply {
                                putExtra(MainActivity.EXTRA_CARD_ID, cardId)
                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            },
                        )
                        onClose()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Abrir en la app") }
            }
        }
    }
}
