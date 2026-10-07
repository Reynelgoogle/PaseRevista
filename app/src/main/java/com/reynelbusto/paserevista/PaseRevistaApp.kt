package com.reynelbusto.paserevista

import android.app.Application
import com.reynelbusto.paserevista.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PaseRevistaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Respaldo automático silencioso si pasaron >24 h desde el último.
        CoroutineScope(Dispatchers.IO).launch {
            container.backupManager.autoBackupIfNeeded()
        }
    }
}
