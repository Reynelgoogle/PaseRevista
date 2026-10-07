package com.reynelbusto.paserevista

import com.reynelbusto.paserevista.core.Clock
import com.reynelbusto.paserevista.domain.model.Pending
import com.reynelbusto.paserevista.domain.model.displayDescription
import com.reynelbusto.paserevista.domain.repository.PendingRepository
import com.reynelbusto.paserevista.domain.usecase.PendingUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class IdemClock : Clock {
    override fun nowMillis(): Long = 1L
    override fun todayIso(zone: java.time.ZoneId): String = "2026-10-06"
}

private fun idemPending(id: String, description: String) = Pending(
    id = id, patientId = "p1", description = description,
    requestedAt = 1L, createdAt = 1L, updatedAt = 1L,
)

private class IdemPendings : PendingRepository {
    val store = mutableMapOf<String, Pending>()
    val byKey = mutableMapOf<String, Pending>()
    var creates = 0

    override fun observeOpenByPatient(patientId: String): Flow<List<Pending>> =
        MutableStateFlow(emptyList())
    override fun observeOpenByService(serviceId: String): Flow<List<Pending>> =
        MutableStateFlow(emptyList())
    override fun observeRecentlyClosedByService(serviceId: String): Flow<List<Pending>> =
        MutableStateFlow(emptyList())
    override suspend fun getOpenByPatient(patientId: String): List<Pending> = emptyList()
    override suspend fun getPending(id: String): Pending? = store[id]
    override suspend fun create(pending: Pending, idempotencyKey: String?): String {
        creates++
        store[pending.id] = pending
        if (idempotencyKey != null) byKey[idempotencyKey] = pending
        return pending.id
    }
    override suspend fun update(pending: Pending) { store[pending.id] = pending }
    override suspend fun deleteByPatient(patientId: String) {}
    override suspend fun findByIdempotencyKey(key: String): Pending? = byKey[key]
}

/**
 * M4: descripción opcional (cero obligatorios) e idempotencyKey real
 * (doble tap no duplica).
 */
class PendingIdempotencyTest {

    @Test
    fun `crear pendiente acepta descripcion vacia`() = runTest {
        val repo = IdemPendings()
        val useCase = PendingUseCase(repo, IdemClock())

        val created = useCase.create(patientId = "p1", description = "   ")

        assertEquals("", created.description)
        assertEquals(1, repo.creates)
    }

    @Test
    fun `doble tap con la misma clave no duplica`() = runTest {
        val repo = IdemPendings()
        val useCase = PendingUseCase(repo, IdemClock())
        val key = "tap-123"

        val first = useCase.create(
            patientId = "p1", description = "Hemograma", idempotencyKey = key,
        )
        val second = useCase.create(
            patientId = "p1", description = "Hemograma", idempotencyKey = key,
        )

        assertEquals(first.id, second.id)
        assertEquals(1, repo.creates)
        assertEquals(1, repo.store.size)
    }

    @Test
    fun `claves distintas si crean pendientes distintos`() = runTest {
        val repo = IdemPendings()
        val useCase = PendingUseCase(repo, IdemClock())

        useCase.create(patientId = "p1", description = "A", idempotencyKey = "k1")
        useCase.create(patientId = "p1", description = "B", idempotencyKey = "k2")

        assertEquals(2, repo.creates)
        assertEquals(2, repo.store.size)
    }

    @Test
    fun `displayDescription usa fallback con descripcion vacia`() {
        assertEquals("(sin descripción)", idemPending("x", "").displayDescription())
        assertEquals("(sin descripción)", idemPending("x", "  ").displayDescription())
        assertEquals("Hemograma", idemPending("x", "Hemograma").displayDescription())
    }
}
