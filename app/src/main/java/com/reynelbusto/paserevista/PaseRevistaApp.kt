package com.reynelbusto.paserevista

import android.app.Application
import com.reynelbusto.paserevista.di.AppContainer

class PaseRevistaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
