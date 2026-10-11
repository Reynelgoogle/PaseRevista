package com.reynelbusto.paserevista.presentation.more

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.reynelbusto.paserevista.BuildConfig
import com.reynelbusto.paserevista.data.backup.BackupManager
import com.reynelbusto.paserevista.data.backup.BackupPolicy
import com.reynelbusto.paserevista.di.AppContainer
import com.reynelbusto.paserevista.presentation.theme.TextSecondary
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * MÁS — configuración y utilidades.
 * Incluye el respaldo local de la base de datos (Documents/EntregaGuardia/).
 * La carga de datos de prueba solo existe en builds debug.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val backupManager = remember { container.backupManager }

    var seeding by remember { mutableStateOf(false) }
    var backups by remember { mutableStateOf<List<BackupManager.BackupEntry>>(emptyList()) }
    var working by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var autoEnabled by remember { mutableStateOf(backupManager.autoBackupEnabled) }
    var pendingRestore by remember { mutableStateOf<BackupManager.BackupEntry?>(null) }

    fun refreshBackups() {
        scope.launch {
            backups = try {
                backupManager.listBackups()
            } catch (e: Exception) {
                snackbar.showSnackbar("No se pudieron listar los respaldos")
                emptyList()
            }
        }
    }

    fun doExport() {
        working = true
        statusMessage = null
        scope.launch {
            try {
                val entry = backupManager.exportBackup()
                statusMessage = "Respaldo creado: Documents/EntregaGuardia/${entry.fileName}"
                refreshBackups()
            } catch (e: Exception) {
                statusMessage = "No se pudo crear el respaldo: ${e.message ?: "error desconocido"}"
            } finally {
                working = false
            }
        }
    }

    // En Android < 10 hace falta WRITE_EXTERNAL_STORAGE para la carpeta pública.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) doExport()
        else scope.launch { snackbar.showSnackbar("Sin permiso no se puede crear el respaldo") }
    }

    /**
     * Restaurar un archivo elegido por el usuario (selector del sistema).
     * El Uri elegido trae permiso de lectura aunque el archivo sea de
     * OTRA app: es la vía para migrar datos debug → release (distinto
     * applicationId = sandbox distinto, la lectura directa entre apps
     * la bloquea el sistema).
     */
    val openDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        working = true
        scope.launch {
            try {
                backupManager.restoreFromUri(uri, displayNameOf(context, uri))
                restartApp(context)
            } catch (e: Exception) {
                statusMessage = null
                snackbar.showSnackbar(
                    "No se pudo restaurar: ${e.message ?: "error desconocido"}",
                )
            } finally {
                working = false
            }
        }
    }

    fun createBackup() {
        if (Build.VERSION.SDK_INT < 29 &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            doExport()
        }
    }

    LaunchedEffect(Unit) { refreshBackups() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Más") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(),
        ) {
            Text("Acerca de", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Entrega de guardia (nombre provisional) v${BuildConfig.VERSION_NAME} · " +
                    "100% offline. Los datos nunca salen del dispositivo.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )

            Spacer(Modifier.height(24.dp))
            Text("Respaldo local", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Copias de la base de datos en Documents/EntregaGuardia/. " +
                    "Sobreviven a actualizaciones y desinstalaciones.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(8.dp))

            Button(
                onClick = ::createBackup,
                enabled = !working,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (working) CircularProgressIndicator() else Text("Crear respaldo ahora")
            }

            statusMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { openDocumentLauncher.launch(arrayOf("*/*")) },
                enabled = !working,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Restaurar desde archivo…")
            }
            Text(
                text = "Para traer datos de la versión debug u otro archivo: " +
                    "elija el .db de Documents/EntregaGuardia/ en el selector.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Respaldo automático diario",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = autoEnabled,
                    onCheckedChange = {
                        autoEnabled = it
                        backupManager.autoBackupEnabled = it
                    },
                )
            }

            Spacer(Modifier.height(12.dp))
            if (backups.isEmpty()) {
                Text(
                    "Aún no hay respaldos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            } else {
                backups.forEach { entry ->
                    BackupRow(
                        entry = entry,
                        onShare = {
                            val share = backupManager.shareIntent(entry)
                            context.startActivity(
                                Intent.createChooser(share, "Compartir respaldo"),
                            )
                        },
                        onRestore = { pendingRestore = entry },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (BuildConfig.SEED_ENABLED) {
                Spacer(Modifier.height(24.dp))
                Text("Desarrollo", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        seeding = true
                        scope.launch {
                            try {
                                container.debugSeed.seedTestData()
                                snackbar.showSnackbar("Datos de prueba cargados ([PRUEBA])")
                            } catch (e: Exception) {
                                snackbar.showSnackbar("No se pudieron cargar los datos de prueba")
                            } finally {
                                seeding = false
                            }
                        }
                    },
                    enabled = !seeding,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (seeding) CircularProgressIndicator() else Text("Cargar datos de prueba")
                }
            }
        }
    }

    pendingRestore?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restaurar respaldo") },
            text = {
                Text(
                    "Se reemplazarán los datos actuales por el respaldo " +
                        "${entry.fileName}. Esta acción no se puede deshacer. " +
                        "La app se reiniciará.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRestore = null
                        scope.launch {
                            try {
                                backupManager.restoreBackup(entry)
                                restartApp(context)
                            } catch (e: Exception) {
                                snackbar.showSnackbar(
                                    "No se pudo restaurar: ${e.message ?: "error desconocido"}",
                                )
                            }
                        }
                    },
                ) { Text("Restaurar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun BackupRow(
    entry: BackupManager.BackupEntry,
    onShare: () -> Unit,
    onRestore: () -> Unit,
) {
    val dateLabel = remember(entry.fileName) {
        BackupPolicy.parseBackupDate(entry.fileName)?.format(
            DateTimeFormatter.ofPattern("d 'de' MMMM yyyy · HH:mm", Locale("es")),
        )?.replaceFirstChar { it.uppercase() } ?: entry.fileName
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(dateLabel, style = MaterialTheme.typography.bodyMedium)
            Text(
                BackupPolicy.formatSize(entry.sizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onShare) { Text("Compartir") }
                OutlinedButton(onClick = onRestore) { Text("Restaurar") }
            }
        }
    }
}

/**
 * Nombre visible del Uri elegido (para mensajes y búsqueda del sidecar).
 * Si el proveedor no lo informa, se usa el último segmento del Uri.
 */
private fun displayNameOf(context: Context, uri: Uri): String {
    context.contentResolver.query(
        uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null,
    )?.use { c ->
        if (c.moveToFirst()) {
            c.getString(0)?.takeIf { it.isNotBlank() }?.let { return it }
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?: "respaldo.db"
}

/**
 * Reinicia la app tras restaurar un respaldo: los repositorios en memoria
 * apuntaban a la base anterior.
 */
private fun restartApp(context: Context) {
    val pm = context.packageManager
    val launch = pm.getLaunchIntentForPackage(context.packageName)
    val restart = Intent.makeRestartActivityTask(launch?.component)
    context.startActivity(restart)
    Runtime.getRuntime().exit(0)
}
