package com.reynelbusto.paserevista.widget

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.reynelbusto.paserevista.PaseRevistaApp
import com.reynelbusto.paserevista.domain.model.PendingType
import com.reynelbusto.paserevista.domain.usecase.CardShareData
import com.reynelbusto.paserevista.domain.usecase.DEFAULT_SERVICE_ID
import com.reynelbusto.paserevista.domain.usecase.ShareFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity transparente: el botón Compartir del widget lanza el Sharesheet
 * de Android con el listado del día (texto listo para WhatsApp). Sin UI.
 */
class ShareDispatcherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            // A4: blindaje total — un fallo aquí nunca debe crashear la app.
            try {
                val text = withContext(Dispatchers.IO) { buildListText() }
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                startActivity(Intent.createChooser(share, "Compartir entrega de guardia"))
            } catch (e: Exception) {
                Toast.makeText(
                    this@ShareDispatcherActivity,
                    "No se pudo preparar el texto para compartir",
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                finish()
            }
        }
    }

    private suspend fun buildListText(): String {
        val container = (application as PaseRevistaApp).container
        val journeyId = container.ensureDay(DEFAULT_SERVICE_ID)
        val cards = container.caseCardRepository.observeByJourney(journeyId).first()
        val items = cards.map { card ->
            val patient = container.patientRepository.getPatient(card.patientId)
            val pendings = container.pendingRepository.getOpenByPatient(card.patientId)
            CardShareData(
                bed = card.bed,
                ready = card.ready,
                diagnosis = card.diagnosis,
                scheduledProcedure = card.scheduledProcedure,
                openPendings = pendings.map { it.description },
                bloodGroup = patient?.bloodGroup,
                currentState = card.currentState,
                antibiotic = card.antibiotic,
                patientName = patient?.fullName,
                hcNumber = patient?.hcNumber,
                address = patient?.address,
                customFields = container.customFieldRepository
                    .observeByPatient(card.patientId).first()
                    .map { it.label to it.value },
                outOfService = patient?.isOutOfService == true,
                hasInterconsult = pendings.any { it.type == PendingType.INTERCONSULTATION },
            )
        }.sortedWith(compareBy({ it.bed.toIntOrNull() }, { it.bed }))
        val dateLabel = try {
            LocalDate.now().format(
                DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es")),
            ).replaceFirstChar { it.uppercase() }
        } catch (e: Exception) {
            LocalDate.now().toString()
        }
        return ShareFormatter.cardList(items, dateLabel)
    }
}
