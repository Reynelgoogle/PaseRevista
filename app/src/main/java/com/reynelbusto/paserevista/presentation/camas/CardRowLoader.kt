package com.reynelbusto.paserevista.presentation.camas

import com.reynelbusto.paserevista.di.AppContainer
import kotlinx.coroutines.flow.first

/**
 * Carga las filas de tarjetas de una jornada: tarjetas + paciente + pendientes
 * abiertos + apartados + historial, ordenadas por cama (numéricas primero).
 *
 * Extraído de CamasViewModel para reutilizarlo en la Agenda sin duplicar
 * lógica. `onlyActive = true` replica el listado de Camas (el alta saca la
 * tarjeta); la Agenda lo usa en false para mostrar el día tal como fue.
 */
suspend fun AppContainer.loadCardRows(
    journeyId: String,
    onlyActive: Boolean = true,
): List<CardRow> {
    val cards = caseCardRepository.observeByJourney(journeyId).first()
    return cards.mapNotNull { card ->
        val patient = patientRepository.getPatient(card.patientId) ?: return@mapNotNull null
        // El alta saca la tarjeta del listado (el historial se conserva).
        if (onlyActive && !isListedPatient(patient)) return@mapNotNull null
        CardRow(
            card = card,
            patient = patient,
            openPendings = pendingRepository.getOpenByPatient(card.patientId),
            customFields = customFieldRepository.observeByPatient(card.patientId).first(),
            history = caseHistoryRepository.observeByPatient(card.patientId).first(),
        )
    }.sortedWith(
        compareBy({ it.card.bed.toIntOrNull() }, { it.card.bed }),
    )
}
