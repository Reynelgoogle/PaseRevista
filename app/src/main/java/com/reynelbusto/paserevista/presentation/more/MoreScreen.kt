package com.reynelbusto.paserevista.presentation.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reynelbusto.paserevista.BuildConfig
import com.reynelbusto.paserevista.di.AppContainer
import com.reynelbusto.paserevista.presentation.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * MÁS — configuración y utilidades.
 * La carga de datos de prueba solo existe en builds debug.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var seeding by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Más") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxWidth(),
        ) {
            Text("Acerca de", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Entrega de guardia (nombre provisional) v${BuildConfig.VERSION_NAME} · " +
                    "100% offline. Los datos nunca salen del dispositivo.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
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
}
