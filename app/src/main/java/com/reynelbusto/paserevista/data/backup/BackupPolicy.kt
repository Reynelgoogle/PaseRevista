package com.reynelbusto.paserevista.data.backup

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Lógica pura del respaldo local: nombres, retención y sidecar JSON.
 * Sin dependencias de Android: testeable en JVM.
 *
 * Formato de archivo: `respaldo-YYYY-MM-DD-HHmm.db`
 * Sidecar:           `respaldo-YYYY-MM-DD-HHmm.json` (misma carpeta)
 */
object BackupPolicy {

    /** Carpeta dentro de Documents/ (MediaStore RELATIVE_PATH o dir público). */
    const val BACKUP_DIR = "EntregaGuardia"

    const val DB_EXTENSION = ".db"
    const val SIDECAR_EXTENSION = ".json"

    /** Cuántos respaldos se conservan (los más nuevos). */
    const val KEEP_COUNT = 5

    /** Intervalo del respaldo automático: 24 h. */
    const val AUTO_INTERVAL_MILLIS = 24L * 60 * 60 * 1000

    private val fileNameFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")

    private val fileNameRegex =
        Regex("""respaldo-(\d{4})-(\d{2})-(\d{2})-(\d{2})(\d{2})\.db""")

    /** `respaldo-2026-10-06-0615.db`. */
    fun backupFileName(now: LocalDateTime): String =
        "respaldo-${now.format(fileNameFormatter)}$DB_EXTENSION"

    /** Nombre del sidecar para un respaldo: cambia `.db` → `.json`. */
    fun sidecarFileName(backupFileName: String): String =
        backupFileName.removeSuffix(DB_EXTENSION) + SIDECAR_EXTENSION

    /** ¿Es un nombre de respaldo válido (nuestro formato)? */
    fun isBackupFileName(fileName: String): Boolean =
        fileNameRegex.matches(fileName)

    /**
     * Extrae la fecha del nombre. `null` si no sigue el formato
     * (esos archivos se ignoran en la retención: nunca se borran solos).
     */
    fun parseBackupDate(fileName: String): LocalDateTime? {
        val m = fileNameRegex.matchEntire(fileName) ?: return null
        return try {
            LocalDateTime.of(
                m.groupValues[1].toInt(),
                m.groupValues[2].toInt(),
                m.groupValues[3].toInt(),
                m.groupValues[4].toInt(),
                m.groupValues[5].toInt(),
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * De la lista de respaldos, devuelve los que hay que borrar para
     * conservar los [keep] más nuevos. Los nombres que no siguen el formato
     * se ignoran (no se borran automáticamente).
     */
    fun selectForRetention(
        backups: List<BackupFileInfo>,
        keep: Int = KEEP_COUNT,
    ): List<BackupFileInfo> {
        require(keep >= 1) { "keep debe ser >= 1" }
        val dated = backups.mapNotNull { info ->
            parseBackupDate(info.fileName)?.let { date -> date to info }
        }
        if (dated.size <= keep) return emptyList()
        val keepNames = dated
            .sortedByDescending { it.first }
            .take(keep)
            .map { it.second.fileName }
            .toSet()
        return dated.map { it.second }.filter { it.fileName !in keepNames }
    }

    /** Construye el JSON del sidecar (sin dependencias externas). */
    fun buildSidecarJson(
        fecha: String,
        schemaVersion: Int,
        tarjetas: Int,
        camas: Int,
        appVersion: String?,
    ): String {
        val app = appVersion?.let { ",\"appVersion\":${jsonString(it)}" } ?: ""
        return "{\"fecha\":${jsonString(fecha)}" +
            ",\"schemaVersion\":$schemaVersion" +
            ",\"tarjetas\":$tarjetas" +
            ",\"camas\":$camas$app}"
    }

    /** Lee un sidecar. `null` si el JSON no tiene el formato esperado. */
    fun parseSidecarJson(json: String): BackupSidecar? {
        val fecha = extractJsonString(json, "fecha") ?: return null
        val schemaVersion = extractJsonInt(json, "schemaVersion") ?: return null
        val tarjetas = extractJsonInt(json, "tarjetas") ?: return null
        val camas = extractJsonInt(json, "camas") ?: return null
        val appVersion = extractJsonString(json, "appVersion")
        return BackupSidecar(fecha, schemaVersion, tarjetas, camas, appVersion)
    }

    /** "12 KB", "3,4 MB". */
    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "${trim1(kb)} KB"
        return "${trim1(kb / 1024.0)} MB"
    }

    private fun trim1(v: Double): String {
        val s = "%.1f".format(v).replace('.', ',')
        return if (s.endsWith(",0")) s.dropLast(2) else s
    }

    private fun jsonString(s: String): String = buildString {
        append('"')
        for (c in s) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }

    private val stringFieldRegexCache = mutableMapOf<String, Regex>()

    private fun extractJsonString(json: String, field: String): String? {
        // "field" : "valor con \"escapes\" y \\uXXXX"
        val regex = stringFieldRegexCache.getOrPut(field) {
            Regex(""""$field"\s*:\s*"((?:[^"\\]|\\.)*)"""")
        }
        val raw = regex.find(json)?.groupValues?.get(1) ?: return null
        return unescapeJsonString(raw)
    }

    private fun extractJsonInt(json: String, field: String): Int? {
        val regex = Regex(""""$field"\s*:\s*(-?\d+)""")
        return regex.find(json)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun unescapeJsonString(raw: String): String {
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length) {
                when (val e = raw[i + 1]) {
                    '"' -> out.append('"')
                    '\\' -> out.append('\\')
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    't' -> out.append('\t')
                    'u' -> {
                        val hex = raw.substring(i + 2, (i + 6).coerceAtMost(raw.length))
                        out.append(hex.toIntOrNull(16)?.toChar() ?: '?')
                        i += 4
                    }
                    else -> out.append(e)
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}

/** Info mínima de un archivo de respaldo (sin Android). */
data class BackupFileInfo(
    val fileName: String,
    val sizeBytes: Long,
)

/** Contenido del sidecar `.json` que acompaña a cada respaldo. */
data class BackupSidecar(
    /** Fecha ISO-8601 local de creación, ej. `2026-10-06T06:15:00`. */
    val fecha: String,
    /** Versión del esquema Room con la que se creó. */
    val schemaVersion: Int,
    /** Número de tarjetas de caso respaldadas. */
    val tarjetas: Int,
    /** Número de camas distintas respaldadas. */
    val camas: Int,
    /** Versión de la app (puede ser null en respaldos viejos). */
    val appVersion: String?,
)
