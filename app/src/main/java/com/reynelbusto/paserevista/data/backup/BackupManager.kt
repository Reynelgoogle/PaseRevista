package com.reynelbusto.paserevista.data.backup

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.reynelbusto.paserevista.core.Clock
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Resultado de `PRAGMA wal_checkpoint(TRUNCATE)`.
 * Puro: testeable en JVM.
 *
 * @param busy true si el checkpoint no pudo completarse (BD ocupada).
 */
data class WalCheckpointResult(
    val busy: Boolean,
    val logFrames: Int,
    val checkpointedFrames: Int,
)

private val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

/**
 * true si los primeros bytes corresponden a una base de datos SQLite.
 * Puro: testeable en JVM.
 */
fun isSqliteDatabaseHeader(header: ByteArray): Boolean {
    if (header.size < 100) return false
    return header.copyOf(SQLITE_MAGIC.size).contentEquals(SQLITE_MAGIC)
}

/**
 * Lee `PRAGMA user_version` de la cabecera (offset 60, 4 bytes big-endian).
 * Requiere al menos 100 bytes. Puro: testeable en JVM.
 */
fun userVersionFromHeader(header: ByteArray): Int {
    require(header.size >= 100) { "cabecera incompleta" }
    return ((header[60].toInt() and 0xFF) shl 24) or
        ((header[61].toInt() and 0xFF) shl 16) or
        ((header[62].toInt() and 0xFF) shl 8) or
        (header[63].toInt() and 0xFF)
}

/**
 * Valida un respaldo ANTES de reemplazar la base de datos viva.
 * Devuelve null si es válido, o el mensaje de error en español.
 * Puro: testeable en JVM.
 */
fun validateRestoreCandidate(
    header: ByteArray,
    sidecar: BackupSidecar?,
    currentSchemaVersion: Int,
): String? {
    if (!isSqliteDatabaseHeader(header)) {
        return "El archivo no es una base de datos válida"
    }
    val dbVersion = userVersionFromHeader(header)
    if (dbVersion <= 0) {
        return "El archivo no es una base de datos de la app"
    }
    if (dbVersion > currentSchemaVersion) {
        return "El respaldo es de una versión más nueva (esquema $dbVersion) y no " +
            "se puede restaurar en esta app (esquema $currentSchemaVersion)"
    }
    val sidecarVersion = sidecar?.schemaVersion
    if (sidecarVersion != null && sidecarVersion > currentSchemaVersion) {
        return "El respaldo es de una versión más nueva (esquema $sidecarVersion) y no " +
            "se puede restaurar en esta app (esquema $currentSchemaVersion)"
    }
    return null
}

/**
 * Respaldo local de la base de datos Room.
 *
 * - Destino: almacenamiento COMPARTIDO `Documents/EntregaGuardia/`
 *   (sobrevive a desinstalaciones y actualizaciones; NO usa getExternalFilesDir).
 * - Android 10+ (API 29+): MediaStore, sin permisos extra.
 * - API 26-28: carpeta pública Documents/ + WRITE_EXTERNAL_STORAGE (maxSdkVersion 28).
 * - Cada respaldo lleva un sidecar `.json` con fecha, esquema y conteos.
 * - Retención: se conservan los últimos [BackupPolicy.KEEP_COUNT].
 *
 * Restaurar es ATÓMICO: el respaldo se copia y valida en un temporal y solo
 * entonces reemplaza al .db vivo con un rename atómico; además se conserva
 * una copia de seguridad del .db actual por si algo falla.
 */
class BackupManager(
    private val appContext: Context,
    private val clock: Clock,
    private val databaseFile: () -> File,
    private val checkpointWal: () -> WalCheckpointResult,
    private val closeDatabase: () -> Unit,
    private val schemaVersion: Int,
    private val countCards: suspend () -> Int,
    private val countBeds: suspend () -> Int,
    private val appVersion: String,
) {

    /** Un respaldo listado, listo para mostrar / restaurar / compartir. */
    data class BackupEntry(
        val fileName: String,
        val dateMillis: Long,
        val sizeBytes: Long,
        val uri: Uri,
    )

    /** Serializa exportar/restaurar entre sí (nunca en paralelo). */
    private val backupMutex = Mutex()

    private val prefs by lazy {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var autoBackupEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_ENABLED, value).apply()

    /**
     * Crea un respaldo ahora: checkpoint del WAL (verificado) + copia del .db
     * + sidecar. Devuelve la entrada creada. Lanza excepción si algo falla.
     */
    suspend fun exportBackup(): BackupEntry = withContext(Dispatchers.IO) {
        backupMutex.withLock {
            checkpointWithRetry()
            val dbFile = databaseFile()
            require(dbFile.exists()) { "No hay base de datos que respaldar" }

            val now = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(clock.nowMillis()), ZoneId.systemDefault(),
            )
            val fileName = BackupPolicy.backupFileName(now)
            val sidecarJson = BackupPolicy.buildSidecarJson(
                fecha = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                schemaVersion = schemaVersion,
                tarjetas = countCards(),
                camas = countBeds(),
                appVersion = appVersion,
            )

            val uri = writeBackupFile(fileName, dbFile)
            writeSidecarFile(BackupPolicy.sidecarFileName(fileName), sidecarJson)

            prefs.edit().putLong(KEY_LAST_BACKUP, clock.nowMillis()).apply()
            enforceRetention()

            val dateMillis = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            BackupEntry(fileName, dateMillis, dbFile.length(), uri)
        }
    }

    /** Lista los respaldos (más nuevos primero). */
    suspend fun listBackups(): List<BackupEntry> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= 29) listViaMediaStore() else listViaLegacyDir()
    }

    /**
     * Restaura un respaldo de la lista interna de forma ATÓMICA:
     * 1. Copia el respaldo a un temporal (la BD viva no se toca).
     * 2. Valida el temporal (cabecera SQLite, esquema, integridad).
     * 3. Copia de seguridad del .db actual.
     * 4. Reemplazo con rename atómico.
     * Si algo falla antes del paso 4, la BD viva queda intacta.
     * La UI debe reiniciar la app después (los repositorios en memoria
     * apuntan a la base anterior).
     *
     * NOTA: en Android 10+ la app solo puede LEER los bytes de sus
     * propios archivos. Los respaldos de OTRA app (p. ej. la variante
     * debug) se listan pero su lectura directa falla: para esos casos
     * usar [restoreFromUri] con un archivo elegido por el usuario (SAF),
     * que sí trae permiso de lectura.
     */
    suspend fun restoreBackup(entry: BackupEntry) = withContext(Dispatchers.IO) {
        backupMutex.withLock { restoreLocked(entry.uri, entry.fileName) }
    }

    /**
     * Restaura desde cualquier Uri legible: un archivo elegido con el
     * selector del sistema (SAF), un respaldo compartido por Drive, etc.
     * El Uri elegido por el usuario trae permiso de lectura aunque el
     * archivo sea de otra app. Misma atomicidad que [restoreBackup].
     */
    suspend fun restoreFromUri(uri: Uri, fileName: String) = withContext(Dispatchers.IO) {
        backupMutex.withLock { restoreLocked(uri, fileName) }
    }

    private fun restoreLocked(uri: Uri, fileName: String) {
        val dbFile = databaseFile()
        val parent = dbFile.parentFile
            ?: error("No se pudo determinar la carpeta de la base de datos")
        val tmp = File(parent, "${dbFile.name}.restore-tmp")
        try {
            // 1. Copiar a temporal sin tocar la BD viva.
            try {
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    tmp.outputStream().use { out -> input.copyTo(out) }
                } ?: error("No se pudo leer el respaldo $fileName")
            } catch (e: SecurityException) {
                error(
                    "Sin permiso para leer ese archivo (es de otra app). " +
                        "Use «Restaurar desde archivo…» y elíjalo de nuevo.",
                )
            }

            // 2. Validar ANTES de reemplazar nada.
            val header = readHeader(tmp)
            val sidecar = readSidecar(fileName)
            validateRestoreCandidate(header, sidecar, schemaVersion)?.let { error(it) }
            if (!sqliteIntegrityOk(tmp)) {
                error("El respaldo está dañado (falló la verificación de integridad)")
            }

            // 3. BD viva: checkpoint, cerrar y copia de seguridad.
            checkpointWithRetry()
            closeDatabase()
            val safety = File(parent, "${dbFile.name}.pre-restore-bak")
            if (dbFile.exists()) dbFile.copyTo(safety, overwrite = true)
            try {
                File(dbFile.path + "-wal").delete()
                File(dbFile.path + "-shm").delete()
                // 4. Reemplazo atómico (mismo directorio → rename atómico).
                Files.move(
                    tmp.toPath(), dbFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
                safety.delete()
            } catch (e: Exception) {
                // La copia .pre-restore-bak queda para rescate manual.
                throw e
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    /** Intent ACTION_SEND para compartir un respaldo (Drive, email…). */
    fun shareIntent(entry: BackupEntry): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = MIME_DB
            putExtra(Intent.EXTRA_STREAM, entry.uri)
            putExtra(Intent.EXTRA_SUBJECT, entry.fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    /**
     * Respaldo automático silencioso al iniciar: si pasaron más de 24 h
     * desde el último respaldo (y el toggle está activo), crea uno.
     * Nunca rompe el arranque: los errores se ignoran (pero una
     * cancelación de corrutina siempre se propaga).
     */
    suspend fun autoBackupIfNeeded() {
        if (!autoBackupEnabled) return
        val last = prefs.getLong(KEY_LAST_BACKUP, 0L)
        if (clock.nowMillis() - last < BackupPolicy.AUTO_INTERVAL_MILLIS) return
        try {
            exportBackup()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Silencioso: el respaldo no puede impedir que la app arranque.
        }
    }

    // ---------- checkpoint ----------

    /**
     * Checkpoint del WAL verificando el resultado. Si la BD está ocupada
     * (busy=1) reintenta; si sigue ocupada, falla con mensaje claro en vez
     * de crear un respaldo incompleto en silencio.
     */
    private fun checkpointWithRetry(maxAttempts: Int = 3) {
        repeat(maxAttempts) { attempt ->
            val r = checkpointWal()
            if (!r.busy) return
            if (attempt < maxAttempts - 1) Thread.sleep(250)
        }
        error("La base de datos está ocupada; intente el respaldo de nuevo")
    }

    // ---------- validación de restauración ----------

    /** Lee los primeros 100 bytes de un archivo (cabecera SQLite). */
    private fun readHeader(file: File): ByteArray {
        val buf = ByteArray(100)
        FileInputStream(file).use { ins ->
            var off = 0
            while (off < buf.size) {
                val n = ins.read(buf, off, buf.size - off)
                if (n <= 0) break
                off += n
            }
            return if (off < buf.size) buf.copyOf(off) else buf
        }
    }

    /** Lee el sidecar .json del respaldo, si existe. Null si no hay o falla. */
    private fun readSidecar(backupFileName: String): BackupSidecar? {
        return try {
            val json = if (Build.VERSION.SDK_INT >= 29) {
                val uri = findSidecarUri(backupFileName) ?: return null
                appContext.contentResolver.openInputStream(uri)?.use { ins ->
                    ins.readBytes().toString(Charsets.UTF_8)
                }
            } else {
                val f = File(legacyDir(), BackupPolicy.sidecarFileName(backupFileName))
                if (f.exists()) f.readText(Charsets.UTF_8) else null
            } ?: return null
            BackupPolicy.parseSidecarJson(json)
        } catch (e: Exception) {
            null
        }
    }

    /** `PRAGMA integrity_check` sobre el archivo temporal (solo lectura). */
    private fun sqliteIntegrityOk(file: File): Boolean {
        return try {
            SQLiteDatabase.openDatabase(
                file.path, null, SQLiteDatabase.OPEN_READONLY,
            ).use { db ->
                db.rawQuery("PRAGMA integrity_check", null).use { c ->
                    c.moveToFirst() && c.getString(0).equals("ok", ignoreCase = true)
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    // ---------- escritura ----------

    private fun writeBackupFile(fileName: String, dbFile: File): Uri =
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, MIME_DB)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOCUMENTS}/${BackupPolicy.BACKUP_DIR}/",
                )
            }
            val collection =
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = appContext.contentResolver.insert(collection, values)
                ?: error("No se pudo crear el archivo de respaldo")
            try {
                appContext.contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(dbFile).use { input -> input.copyTo(out) }
                } ?: error("No se pudo escribir el respaldo")
            } catch (e: Exception) {
                appContext.contentResolver.delete(uri, null, null)
                throw e
            }
            uri
        } else {
            val dir = legacyDir().also { it.mkdirs() }
            val dest = File(dir, fileName)
            dbFile.copyTo(dest, overwrite = true)
            FileProvider.getUriForFile(
                appContext, "${appContext.packageName}.fileprovider", dest,
            )
        }

    private fun writeSidecarFile(fileName: String, json: String) {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, MIME_JSON)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOCUMENTS}/${BackupPolicy.BACKUP_DIR}/",
                )
            }
            val collection =
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = appContext.contentResolver.insert(collection, values)
                ?: error("No se pudo crear el sidecar")
            try {
                appContext.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                } ?: error("No se pudo escribir el sidecar")
            } catch (e: Exception) {
                appContext.contentResolver.delete(uri, null, null)
                throw e
            }
        } else {
            File(legacyDir(), fileName).writeText(json, Charsets.UTF_8)
        }
    }

    private fun legacyDir(): File =
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            BackupPolicy.BACKUP_DIR,
        )

    // ---------- listado ----------

    private fun listViaMediaStore(): List<BackupEntry> {
        val out = mutableListOf<BackupEntry>()
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
        )
        appContext.contentResolver.query(
            collection,
            projection,
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
            arrayOf("Documents/${BackupPolicy.BACKUP_DIR}/%"),
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            while (c.moveToNext()) {
                val name = c.getString(nameCol) ?: continue
                if (!BackupPolicy.isBackupFileName(name)) continue
                val date = BackupPolicy.parseBackupDate(name) ?: continue
                val millis = date.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                out.add(
                    BackupEntry(
                        fileName = name,
                        dateMillis = millis,
                        sizeBytes = c.getLong(sizeCol),
                        uri = ContentUris.withAppendedId(collection, c.getLong(idCol)),
                    ),
                )
            }
        }
        return out.sortedByDescending { it.dateMillis }
    }

    private fun listViaLegacyDir(): List<BackupEntry> {
        val dir = legacyDir()
        return (dir.listFiles { f -> BackupPolicy.isBackupFileName(f.name) } ?: emptyArray())
            .mapNotNull { f ->
                val date = BackupPolicy.parseBackupDate(f.name) ?: return@mapNotNull null
                val millis = date.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                BackupEntry(
                    fileName = f.name,
                    dateMillis = millis,
                    sizeBytes = f.length(),
                    uri = FileProvider.getUriForFile(
                        appContext, "${appContext.packageName}.fileprovider", f,
                    ),
                )
            }
            .sortedByDescending { it.dateMillis }
    }

    // ---------- retención ----------

    private suspend fun enforceRetention() {
        val current = listBackups()
        val toDelete = BackupPolicy.selectForRetention(
            current.map { BackupFileInfo(it.fileName, it.sizeBytes) },
        )
        if (toDelete.isEmpty()) return
        val byName = current.associateBy { it.fileName }
        toDelete.forEach { info ->
            byName[info.fileName]?.let { deleteBackup(it) }
        }
    }

    private fun deleteBackup(entry: BackupEntry) {
        if (Build.VERSION.SDK_INT >= 29) {
            appContext.contentResolver.delete(entry.uri, null, null)
            findSidecarUri(entry.fileName)?.let {
                appContext.contentResolver.delete(it, null, null)
            }
        } else {
            File(legacyDir(), entry.fileName).delete()
            File(legacyDir(), BackupPolicy.sidecarFileName(entry.fileName)).delete()
        }
    }

    private fun findSidecarUri(backupFileName: String): Uri? {
        val sidecarName = BackupPolicy.sidecarFileName(backupFileName)
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        appContext.contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND " +
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf("Documents/${BackupPolicy.BACKUP_DIR}/%", sidecarName),
            null,
        )?.use { c ->
            if (c.moveToFirst()) {
                return ContentUris.withAppendedId(
                    collection, c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)),
                )
            }
        }
        return null
    }

    private companion object {
        const val PREFS_NAME = "backup_prefs"
        const val KEY_LAST_BACKUP = "last_backup_millis"
        const val KEY_AUTO_ENABLED = "auto_enabled"
        const val MIME_DB = "application/x-sqlite3"
        const val MIME_JSON = "application/json"
    }
}
