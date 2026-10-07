package com.reynelbusto.paserevista.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.reynelbusto.paserevista.PaseRevistaApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Refresca los dos widgets: el clásico de camas y "Guardia rápida".
 * Llamar tras cada mutación (acciones del widget, entrada rápida, detalle).
 */
object WidgetRefresh {
    fun refreshAll(context: Context) {
        val appContext = context.applicationContext
        CaseWidgetProvider.requestUpdate(appContext)
        QuickWidgetProvider.requestUpdate(appContext)
    }
}

/**
 * Recibe los toques de las filas del widget "Guardia rápida".
 * - check ⇒ alterna Pendiente/Listo con el use case existente
 *   ([CaseCardUseCase.toggleReady]: mismas reglas que en la app, incl. pizarra).
 * - fila ⇒ abre el detalle rápido ([QuickDetailActivity]).
 * Blindado: un fallo nunca debe tumbar el receiver.
 */
class WidgetActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ROW_CLICK =
            "com.reynelbusto.paserevista.widget.action.ROW_CLICK"
        const val EXTRA_KIND = "extra_kind"
        const val EXTRA_CARD_ID = "extra_card_id"
        const val KIND_TOGGLE = "toggle"
        const val KIND_DETAIL = "detail"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ROW_CLICK) return
        val cardId = intent.getStringExtra(EXTRA_CARD_ID) ?: return
        when (intent.getStringExtra(EXTRA_KIND)) {
            KIND_DETAIL -> {
                val detail = Intent(context, QuickDetailActivity::class.java).apply {
                    putExtra(EXTRA_CARD_ID, cardId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(detail)
                } catch (e: Exception) {
                    toastOnMain(context, "No se pudo abrir el detalle")
                }
            }
            KIND_TOGGLE -> {
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        val app = context.applicationContext as PaseRevistaApp
                        app.container.caseCards.toggleReady(cardId)
                        WidgetRefresh.refreshAll(context)
                    } catch (e: Exception) {
                        toastOnMain(context, "No se pudo cambiar la tarjeta")
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            else -> Unit
        }
    }

    private fun toastOnMain(context: Context, msg: String) {
        Handler(Looper.getMainLooper()).post {
            try {
                Toast.makeText(context.applicationContext, msg, Toast.LENGTH_SHORT).show()
            } catch (ignored: Exception) {
            }
        }
    }
}
