package com.reynelbusto.paserevista

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.reynelbusto.paserevista.navigation.AppNavGraph
import com.reynelbusto.paserevista.presentation.theme.PaseRevistaTheme

class MainActivity : ComponentActivity() {

    companion object {
        /** Deep link: abre la app con esta tarjeta expandida en Camas. */
        const val EXTRA_CARD_ID = "extra_card_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PaseRevistaApp).container
        val deepLinkCardId = intent.getStringExtra(EXTRA_CARD_ID)
        setContent {
            PaseRevistaTheme {
                AppNavGraph(
                    container = container,
                    initialExpandedCardId = deepLinkCardId,
                )
            }
        }
    }
}
