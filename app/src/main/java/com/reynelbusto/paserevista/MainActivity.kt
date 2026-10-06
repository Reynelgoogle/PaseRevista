package com.reynelbusto.paserevista

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.reynelbusto.paserevista.navigation.AppNavGraph
import com.reynelbusto.paserevista.presentation.theme.PaseRevistaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PaseRevistaApp).container
        setContent {
            PaseRevistaTheme {
                AppNavGraph(container = container)
            }
        }
    }
}
