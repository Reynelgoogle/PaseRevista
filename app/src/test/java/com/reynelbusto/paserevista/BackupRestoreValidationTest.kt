package com.reynelbusto.paserevista

import com.reynelbusto.paserevista.data.backup.BackupSidecar
import com.reynelbusto.paserevista.data.backup.isSqliteDatabaseHeader
import com.reynelbusto.paserevista.data.backup.userVersionFromHeader
import com.reynelbusto.paserevista.data.backup.validateRestoreCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** C3/A6: la restauración valida el candidato ANTES de tocar la BD viva. */
class BackupRestoreValidationTest {

    private fun header(userVersion: Int): ByteArray {
        val h = ByteArray(100)
        "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII).copyInto(h)
        h[60] = (userVersion shr 24).toByte()
        h[61] = (userVersion shr 16).toByte()
        h[62] = (userVersion shr 8).toByte()
        h[63] = userVersion.toByte()
        return h
    }

    private fun sidecar(v: Int) = BackupSidecar(
        fecha = "2026-10-06T06:15:00",
        schemaVersion = v,
        tarjetas = 3,
        camas = 2,
        appVersion = "1.0",
    )

    @Test
    fun `cabecera sqlite valida pasa`() {
        assertTrue(isSqliteDatabaseHeader(header(2)))
    }

    @Test
    fun `cabecera corta o corrupta no pasa`() {
        assertFalse(isSqliteDatabaseHeader(ByteArray(50)))
        assertFalse(isSqliteDatabaseHeader(ByteArray(100)))
        val bad = header(2).also { it[0] = 'X'.code.toByte() }
        assertFalse(isSqliteDatabaseHeader(bad))
    }

    @Test
    fun `user_version se lee big-endian del offset 60`() {
        assertEquals(2, userVersionFromHeader(header(2)))
        assertEquals(257, userVersionFromHeader(header(257)))
        assertEquals(0, userVersionFromHeader(header(0)))
    }

    @Test
    fun `candidato valido no devuelve error`() {
        assertNull(validateRestoreCandidate(header(2), sidecar(2), 2))
        // esquema anterior: Room migra al abrir
        assertNull(validateRestoreCandidate(header(1), sidecar(1), 2))
        // sin sidecar: vale el user_version
        assertNull(validateRestoreCandidate(header(2), null, 2))
    }

    @Test
    fun `archivo que no es sqlite se rechaza`() {
        val msg = validateRestoreCandidate(ByteArray(100), null, 2)
        assertTrue(msg!!.contains("no es una base de datos válida"))
    }

    @Test
    fun `user_version 0 se rechaza (no es de la app)`() {
        val msg = validateRestoreCandidate(header(0), null, 2)
        assertTrue(msg!!.contains("no es una base de datos de la app"))
    }

    @Test
    fun `esquema mas nuevo se rechaza con mensaje claro`() {
        val msg = validateRestoreCandidate(header(3), sidecar(3), 2)
        assertTrue(msg!!.contains("versión más nueva"))
        assertTrue(msg.contains("esquema 3"))
    }

    @Test
    fun `sidecar mas nuevo tambien se rechaza`() {
        val msg = validateRestoreCandidate(header(2), sidecar(5), 2)
        assertTrue(msg!!.contains("versión más nueva"))
    }
}
