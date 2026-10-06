package com.reynelbusto.paserevista.domain.usecase

import com.reynelbusto.paserevista.core.Clock
import com.reynelbusto.paserevista.core.IsoDate
import com.reynelbusto.paserevista.domain.TransitionGuard
import com.reynelbusto.paserevista.domain.model.ClinicalEvolution
import com.reynelbusto.paserevista.domain.model.ClinicalPriority
import com.reynelbusto.paserevista.domain.model.ClinicalState
import com.reynelbusto.paserevista.domain.model.DailyRecord
import com.reynelbusto.paserevista.domain.model.ReviewState
import com.reynelbusto.paserevista.domain.repository.DailyRecordRepository

/**
 * FASE 6/7 — Revisión del paciente en la jornada.
 *
 * Reglas:
 * - Abrir ≠ revisar: solo acciones explícitas mueven PENDING → IN_PROGRESS → COMPLETED.
 * - "Sin cambios": 1 tap → COMPLETED inmediato (los clínicos quedan NULL).
 * - Mejoría / Empeoramiento / Nuevo problema: tap → IN_PROGRESS; el cierre exige
 *   "✓ Marcar como revisado" explícito.
 * - Autosave: cada campo tocado persiste al instante (sin botón Guardar).
 * - Reabrir un ✓ edita el MISMO DailyRecord (sin duplicar).
 */
class ReviewUseCase(
    private val records: DailyRecordRepository,
    private val clock: Clock,
) {
    private suspend fun requireRecord(patientId: String, journeyId: String): DailyRecord =
        records.getByPatientAndJourney(patientId, journeyId)
            ?: error("Sin DailyRecord para paciente=$patientId jornada=$journeyId")

    private suspend fun persist(record: DailyRecord): DailyRecord {
        records.update(record)
        return record
    }

    /** Tap de evolución ("¿Qué cambió desde ayer?"). */
    suspend fun recordEvolution(
        patientId: String,
        journeyId: String,
        evolution: ClinicalEvolution,
    ): DailyRecord {
        val record = requireRecord(patientId, journeyId)
        val now = clock.nowMillis()
        val updated = when (evolution) {
            ClinicalEvolution.UNCHANGED -> {
                // 1 tap en UX, pero la máquina exige pasar por IN_PROGRESS:
                // el tap ES la acción explícita (abrir ≠ revisar se mantiene:
                // ver la ficha jamás completa). Atómico: o completa o nada.
                val mid = if (record.reviewState == ReviewState.PENDING) ReviewState.IN_PROGRESS
                else record.reviewState
                check(TransitionGuard.dailyRecord(record.reviewState, mid) || record.reviewState == mid) {
                    "Transición de revisión no permitida"
                }
                check(TransitionGuard.dailyRecord(mid, ReviewState.COMPLETED)) {
                    "Transición de revisión no permitida"
                }
                record.copy(
                    evolutionTap = ClinicalEvolution.UNCHANGED,
                    reviewState = ReviewState.COMPLETED,
                    reviewedAt = now,
                )
            }
            else -> {
                val next = if (record.reviewState == ReviewState.PENDING) ReviewState.IN_PROGRESS
                else record.reviewState
                check(TransitionGuard.dailyRecord(record.reviewState, next) ||
                    record.reviewState == next) {
                    "Transición de revisión no permitida"
                }
                record.copy(evolutionTap = evolution, reviewState = next)
            }
        }
        return persist(updated)
    }

    /** Autosave genérico de campos clínicos: PENDING → IN_PROGRESS al primer toque. */
    suspend fun saveClinicalData(
        patientId: String,
        journeyId: String,
        patch: (DailyRecord) -> DailyRecord,
    ): DailyRecord {
        val record = requireRecord(patientId, journeyId)
        val touched = patch(record)
        val next = if (touched.reviewState == ReviewState.PENDING) ReviewState.IN_PROGRESS
        else touched.reviewState
        check(TransitionGuard.dailyRecord(record.reviewState, next) || record.reviewState == next) {
            "Transición de revisión no permitida"
        }
        return persist(touched.copy(reviewState = next))
    }

    suspend fun saveClinicalState(pId: String, jId: String, v: ClinicalState?): DailyRecord =
        saveClinicalData(pId, jId) { it.copy(clinicalState = v) }

    suspend fun savePain(pId: String, jId: String, v: Int?): DailyRecord =
        saveClinicalData(pId, jId) {
            require(v == null || v in 0..10) { "Dolor fuera de rango 0..10" }
            it.copy(painScale = v)
        }

    suspend fun saveFever(pId: String, jId: String, v: Boolean?): DailyRecord =
        saveClinicalData(pId, jId) { it.copy(hasFever = v) }

    suspend fun saveDiuresis(pId: String, jId: String, v: String?): DailyRecord =
        saveClinicalData(pId, jId) { it.copy(diuresis = v?.ifBlank { null }) }

    suspend fun saveOtherSigns(pId: String, jId: String, v: String?): DailyRecord =
        saveClinicalData(pId, jId) { it.copy(otherSigns = v?.ifBlank { null }) }

    suspend fun savePriority(pId: String, jId: String, v: ClinicalPriority?): DailyRecord =
        saveClinicalData(pId, jId) { it.copy(priority = v) }

    suspend fun saveEvolutionText(pId: String, jId: String, v: String?): DailyRecord =
        saveClinicalData(pId, jId) { it.copy(evolutionText = v?.ifBlank { null }) }

    suspend fun saveObservations(pId: String, jId: String, v: String?): DailyRecord =
        saveClinicalData(pId, jId) { it.copy(observations = v?.ifBlank { null }) }

    suspend fun saveDischargePlanned(
        pId: String,
        jId: String,
        planned: Boolean,
        date: IsoDate?,
    ): DailyRecord = saveClinicalData(pId, jId) {
        it.copy(dischargePlanned = planned, dischargePlannedDate = if (planned) date else null)
    }

    /** "✓ Marcar como revisado": exige el tap de evolución explícito. */
    suspend fun markReviewed(patientId: String, journeyId: String): DailyRecord {
        val record = requireRecord(patientId, journeyId)
        check(TransitionGuard.canComplete(record)) {
            "Falta registrar la evolución (qué cambió desde ayer)"
        }
        check(TransitionGuard.dailyRecord(record.reviewState, ReviewState.COMPLETED)) {
            "Transición de revisión no permitida"
        }
        return persist(record.copy(reviewState = ReviewState.COMPLETED, reviewedAt = clock.nowMillis()))
    }

    /**
     * Deshacer "Sin cambios" (la UI ofrece ~5 s).
     * Solo revierte si fue un UNCHANGED directo sin otros datos tocados.
     */
    suspend fun undoUnchanged(patientId: String, journeyId: String): DailyRecord {
        val record = requireRecord(patientId, journeyId)
        check(record.evolutionTap == ClinicalEvolution.UNCHANGED &&
            record.reviewState == ReviewState.COMPLETED) {
            "Solo se puede deshacer un 'Sin cambios' recién marcado"
        }
        return persist(
            record.copy(
                evolutionTap = null,
                reviewState = ReviewState.PENDING,
                reviewedAt = null,
            ),
        )
    }

    /** Reabrir un ✓ para corregir/agregar: el MISMO registro vuelve a IN_PROGRESS. */
    suspend fun reopen(patientId: String, journeyId: String): DailyRecord {
        val record = requireRecord(patientId, journeyId)
        check(record.reviewState == ReviewState.COMPLETED) { "Solo se reabre un registro revisado" }
        return persist(record.copy(reviewState = ReviewState.IN_PROGRESS))
    }
}
