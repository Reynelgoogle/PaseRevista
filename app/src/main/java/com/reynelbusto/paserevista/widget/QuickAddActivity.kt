package com.reynelbusto.paserevista.widget

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.reynelbusto.paserevista.PaseRevistaApp
import com.reynelbusto.paserevista.domain.usecase.CaseCardPatch
import com.reynelbusto.paserevista.domain.usecase.DEFAULT_SERVICE_ID
import com.reynelbusto.paserevista.presentation.theme.PaseRevistaTheme
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Entrada rápida estilo Todoist Quick Add: overlay translúcido que se abre
 * desde el widget "Guardia rápida" (botón ＋ o micrófono).
 *
 * REGLA DE ORO: cero campos obligatorios. Si la cama va vacía se asigna la
 * primera libre del día (la cama es la identidad de la tarjeta).
 * Reutiliza [AddBedUseCase][com.reynelbusto.paserevista.domain.usecase.AddBedUseCase]
 * y [CaseCardUseCase.updateFields][com.reynelbusto.paserevista.domain.usecase.CaseCardUseCase.updateFields];
 * no duplica lógica de negocio.
 */
class QuickAddActivity : ComponentActivity() {

    companion object {
        /** Si es true, arranca el dictado por voz al abrir. */
        const val EXTRA_VOICE = "extra_voice"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PaseRevistaTheme {
                QuickAddScreen(
                    autoVoice = intent.getBooleanExtra(EXTRA_VOICE, false),
                    onClose = { finish() },
                    onSave = { bed, diagnosis, procedure -> save(bed, diagnosis, procedure) },
                )
            }
        }
    }

    private fun save(bed: String, diagnosis: String, procedure: String) {
        lifecycleScope.launch {
            // Blindaje: un fallo aquí nunca debe crashear; se avisa con Toast.
            try {
                val c = (application as PaseRevistaApp).container
                val journeyId = c.ensureDay(DEFAULT_SERVICE_ID)
                val usedBeds = c.caseCardRepository
                    .observeByJourney(journeyId).first().map { it.bed }
                val finalBed = bed.trim().ifEmpty { nextFreeBed(usedBeds) }
                // M3 vive en AddBedUseCase: si la cama ya existe hoy, lanza
                // IllegalStateException y se muestra el mensaje.
                val patientId = c.addBed(finalBed)
                val card = c.caseCardRepository.getByPatientAndJourney(patientId, journeyId)
                if (card != null) {
                    val touched = mutableSetOf<String>()
                    if (diagnosis.isNotBlank()) touched += "diagnosis"
                    if (procedure.isNotBlank()) touched += "scheduledProcedure"
                    if (touched.isNotEmpty()) {
                        c.caseCards.updateFields(
                            card.id,
                            CaseCardPatch(
                                diagnosis = diagnosis.trim().ifEmpty { null },
                                scheduledProcedure = procedure.trim().ifEmpty { null },
                                fieldsTouched = touched,
                            ),
                        )
                    }
                }
                WidgetRefresh.refreshAll(this@QuickAddActivity)
                Toast.makeText(
                    this@QuickAddActivity,
                    "Tarjeta guardada (cama $finalBed)",
                    Toast.LENGTH_SHORT,
                ).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(
                    this@QuickAddActivity,
                    e.message ?: "No se pudo guardar la tarjeta",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
}

@Composable
private fun QuickAddScreen(
    autoVoice: Boolean,
    onClose: () -> Unit,
    onSave: (bed: String, diagnosis: String, procedure: String) -> Unit,
) {
    val context = LocalContext.current
    var bed by remember { mutableStateOf("") }
    var diagnosis by remember { mutableStateOf("") }
    var procedure by remember { mutableStateOf("") }

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.let { diagnosis = it }
        }
    }

    fun startVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Dicta el diagnóstico")
        }
        try {
            voiceLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            toast("Reconocimiento de voz no disponible en este teléfono")
        }
    }

    LaunchedEffect(autoVoice) {
        if (autoVoice) startVoice()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable { onClose() },
        contentAlignment = Alignment.Center,
    ) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Nueva tarjeta",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClose) { Text("✕") }
                }
                OutlinedTextField(
                    value = bed,
                    onValueChange = { bed = it },
                    label = { Text("Cama (opcional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = diagnosis,
                    onValueChange = { diagnosis = it },
                    label = { Text("Diagnóstico (opcional)") },
                    trailingIcon = {
                        IconButton(onClick = { startVoice() }) {
                            Text("🎤", fontSize = 20.sp)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = procedure,
                    onValueChange = { procedure = it },
                    label = { Text("Proceder programado (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(onClick = onClose) { Text("Cancelar") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onSave(bed, diagnosis, procedure) }) {
                        Text("Guardar")
                    }
                }
            }
        }
    }
}
