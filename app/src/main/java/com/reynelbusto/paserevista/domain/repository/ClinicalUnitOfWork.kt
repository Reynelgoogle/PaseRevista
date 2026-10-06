package com.reynelbusto.paserevista.domain.repository

/**
 * Unidad de trabajo clínica: agrupa varias operaciones de repositorio en una
 * única transacción atómica. El motor de jornadas la usa para que
 * "crear jornada + registros diarios" sea todo o nada.
 */
interface ClinicalUnitOfWork {
    suspend fun <T> atomic(block: suspend () -> T): T
}
