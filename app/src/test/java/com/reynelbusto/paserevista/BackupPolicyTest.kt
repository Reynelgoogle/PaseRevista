package com.reynelbusto.paserevista

import com.reynelbusto.paserevista.data.backup.BackupFileInfo
import com.reynelbusto.paserevista.data.backup.BackupPolicy
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPolicyTest {

    // ---------- nombres ----------

    @Test
    fun `nombre con formato respaldo-YYYY-MM-DD-HHmm-db`() {
        val name = BackupPolicy.backupFileName(LocalDateTime.of(2026, 10, 6, 6, 15))
        assertEquals("respaldo-2026-10-06-0615.db", name)
    }

    @Test
    fun `nombre con minutos de un digito lleva cero`() {
        val name = BackupPolicy.backupFileName(LocalDateTime.of(2026, 1, 2, 3, 5))
        assertEquals("respaldo-2026-01-02-0305.db", name)
    }

    @Test
    fun `parse round-trip del nombre`() {
        val t = LocalDateTime.of(2026, 10, 6, 6, 15)
        assertEquals(t, BackupPolicy.parseBackupDate(BackupPolicy.backupFileName(t)))
    }

    @Test
    fun `parse devuelve null con nombres invalidos`() {
        assertNull(BackupPolicy.parseBackupDate("respaldo.db"))
        assertNull(BackupPolicy.parseBackupDate("respaldo-2026-13-06-0615.db"))
        assertNull(BackupPolicy.parseBackupDate("foto-2026-10-06-0615.db"))
        assertNull(BackupPolicy.parseBackupDate(""))
    }

    @Test
    fun `sidecar cambia db por json`() {
        assertEquals(
            "respaldo-2026-10-06-0615.json",
            BackupPolicy.sidecarFileName("respaldo-2026-10-06-0615.db"),
        )
    }

    // ---------- retención ----------

    private fun info(day: Int, hour: Int = 6): BackupFileInfo =
        BackupFileInfo(
            "respaldo-2026-10-%02d-%02d15.db".format(day, hour),
            1024,
        )

    @Test
    fun `con 7 respaldos borra los 2 mas viejos`() {
        val all = (1..7).map { info(it) }
        val toDelete = BackupPolicy.selectForRetention(all, keep = 5)
        assertEquals(2, toDelete.size)
        val names = toDelete.map { it.fileName }.toSet()
        assertTrue("respaldo-2026-10-01-0615.db" in names)
        assertTrue("respaldo-2026-10-02-0615.db" in names)
    }

    @Test
    fun `con 5 respaldos no borra nada`() {
        val all = (1..5).map { info(it) }
        assertTrue(BackupPolicy.selectForRetention(all, keep = 5).isEmpty())
    }

    @Test
    fun `con menos de 5 no borra nada`() {
        val all = (1..3).map { info(it) }
        assertTrue(BackupPolicy.selectForRetention(all, keep = 5).isEmpty())
    }

    @Test
    fun `nombres no validos nunca se borran solos`() {
        val all = (1..7).map { info(it) } +
            BackupFileInfo("respaldo-manual-viejo.db", 512) +
            BackupFileInfo("notas.txt", 100)
        val toDelete = BackupPolicy.selectForRetention(all, keep = 5)
        val names = toDelete.map { it.fileName }
        assertFalse("respaldo-manual-viejo.db" in names)
        assertFalse("notas.txt" in names)
        assertEquals(2, toDelete.size)
    }

    @Test
    fun `mismo dia ordena por hora`() {
        val all = listOf(info(6, 6), info(6, 18), info(5), info(4), info(3), info(2), info(1))
        val toDelete = BackupPolicy.selectForRetention(all, keep = 5)
        // se conservan los 5 más nuevos: día 6 (06 y 18), 5, 4, 3
        val names = toDelete.map { it.fileName }.toSet()
        assertEquals(
            setOf("respaldo-2026-10-01-0615.db", "respaldo-2026-10-02-0615.db"),
            names,
        )
    }

    // ---------- sidecar ----------

    @Test
    fun `sidecar round-trip conserva los campos`() {
        val json = BackupPolicy.buildSidecarJson(
            fecha = "2026-10-06T06:15:00",
            schemaVersion = 2,
            tarjetas = 12,
            camas = 8,
            appVersion = "1.0",
        )
        val parsed = BackupPolicy.parseSidecarJson(json)
        assertEquals("2026-10-06T06:15:00", parsed?.fecha)
        assertEquals(2, parsed?.schemaVersion)
        assertEquals(12, parsed?.tarjetas)
        assertEquals(8, parsed?.camas)
        assertEquals("1.0", parsed?.appVersion)
    }

    @Test
    fun `sidecar sin appVersion`() {
        val json = BackupPolicy.buildSidecarJson(
            fecha = "2026-10-06T06:15:00",
            schemaVersion = 2,
            tarjetas = 0,
            camas = 0,
            appVersion = null,
        )
        val parsed = BackupPolicy.parseSidecarJson(json)
        assertEquals(null, parsed?.appVersion)
        assertEquals(0, parsed?.tarjetas)
    }

    @Test
    fun `sidecar escapa comillas en appVersion`() {
        val json = BackupPolicy.buildSidecarJson(
            fecha = "2026-10-06T06:15:00",
            schemaVersion = 2,
            tarjetas = 1,
            camas = 1,
            appVersion = "1.0 \"beta\"",
        )
        assertEquals("1.0 \"beta\"", BackupPolicy.parseSidecarJson(json)?.appVersion)
    }

    @Test
    fun `parse de json invalido devuelve null`() {
        assertNull(BackupPolicy.parseSidecarJson("no es json"))
        assertNull(BackupPolicy.parseSidecarJson("{\"fecha\":\"x\"}"))
        assertNull(BackupPolicy.parseSidecarJson("{}"))
    }

    // ---------- formato de tamaño ----------

    @Test
    fun `formato de tamano`() {
        assertEquals("512 B", BackupPolicy.formatSize(512))
        assertEquals("12 KB", BackupPolicy.formatSize(12 * 1024))
        assertEquals("1,5 KB", BackupPolicy.formatSize(1536))
        assertEquals("3,4 MB", BackupPolicy.formatSize((3.4 * 1024 * 1024).toLong()))
    }
}
