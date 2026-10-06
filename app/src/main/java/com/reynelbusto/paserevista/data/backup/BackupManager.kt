package com.reynelbusto.paserevista.data.backup

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.reynelbusto.paserevista.core.Clock
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 * Restaurar cierra Room y reemplaza el .db; al reabrir, las migraciones de
 * Room se aplican si el respaldo es de un esquema anterior.
 */
class BackupManager(
    private val appContext: Context,
    private val clock: Clock,
    private val databaseFile: () -> File,
    private val checkpointWal: () -> Unit,
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

    private val prefs by lazy {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var autoBackupEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_ENABLED, value).apply()

    /**
     * Crea un respaldo ahora: checkpoint del WAL + copia del .db + sidecar.
     * Devuelve la entrada creada. Lanza excepción si algo falla.
     */
    suspend fun exportBackup(): BackupEntry = withContext(Dispatchers.IO) {
        checkpointWal()
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

    /** Lista los respaldos (más nuevos primero). */
    suspend fun listBackups(): List<BackupEntry> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= 29) listViaMediaStore() else listViaLegacyDir()
    }

    /**
     * Restaura un respaldo: cierra Room y reemplaza el .db actual.
     * La UI debe reiniciar la app después (los repositorios en memoria
     * apuntan a la base anterior).
     */
    suspend fun restoreBackup(entry: BackupEntry) = withContext(Dispatchers.IO) {
        closeDatabase()
        val dbFile = databaseFile()
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
        appContext.contentResolver.openInputStream(entry.uri)?.use { input ->
            FileOutputStream(dbFile).use { output -> input.copyTo(output) }
        } ?: error("No se pudo leer el respaldo ${entry.fileName}")
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
     * Nunca rompe el arranque: los errores se ignoran.
     */
    suspend fun autoBackupIfNeeded() {
        if (!autoBackupEnabled) return
        val last = prefs.getLong(KEY_LAST_BACKUP, 0L)
        if (clock.nowMillis() - last < BackupPolicy.AUTO_INTERVAL_MILLIS) return
        try {
            exportBackup()
        } catch (e: Exception) {
            // Silencioso: el respaldo no puede impedir que la app arranque.
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
