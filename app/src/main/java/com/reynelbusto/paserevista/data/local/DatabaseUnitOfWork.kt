package com.reynelbusto.paserevista.data.local

import androidx.room.withTransaction
import com.reynelbusto.paserevista.domain.repository.ClinicalUnitOfWork

/**
 * Implementación Room de la unidad de trabajo. `withTransaction` de Room
 * garantiza atomicidad; las llamadas anidadas a DAO @Transaction se unen
 * a la transacción exterior.
 */
class DatabaseUnitOfWork(
    private val database: PaseRevistaDatabase,
) : ClinicalUnitOfWork {
    override suspend fun <T> atomic(block: suspend () -> T): T =
        database.withTransaction { block() }
}
