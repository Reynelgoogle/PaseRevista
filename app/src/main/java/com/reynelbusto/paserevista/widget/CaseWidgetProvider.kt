package com.reynelbusto.paserevista.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.reynelbusto.paserevista.MainActivity
import com.reynelbusto.paserevista.R

/**
 * Widget de la entrega de guardia: lista de camas del día (cama + etiqueta
 * Pendiente/Listo) con botón Compartir (texto listo para WhatsApp).
 * 100% offline: lee Room en el hilo del RemoteViewsService.
 */
class CaseWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        /** Llamar tras cada cambio en Camas para refrescar el widget. */
        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, CaseWidgetProvider::class.java),
            )
            if (ids.isEmpty()) return
            manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
            // Re-ejecuta onUpdate para refrescar cabecera y botones.
            val intent = Intent(context, CaseWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }

        private fun updateWidgets(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray,
        ) {
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_case_list)
                views.setTextViewText(R.id.widget_title, "Entrega de guardia — Camas")

                // Lista de camas (colección).
                val serviceIntent = Intent(context, CaseWidgetService::class.java).apply {
                    data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME) + "#$id")
                }
                views.setRemoteAdapter(R.id.widget_list, serviceIntent)
                views.setEmptyView(R.id.widget_list, R.id.widget_empty)

                // Tap en un ítem ⇒ abrir la app.
                val openApp = PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                views.setPendingIntentTemplate(R.id.widget_list, openApp)

                // Botón compartir ⇒ activity transparente que lanza el Sharesheet.
                val share = PendingIntent.getActivity(
                    context, 1,
                    Intent(context, ShareDispatcherActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                views.setOnClickPendingIntent(R.id.widget_share, share)

                manager.updateAppWidget(id, views)
            }
        }
    }
}
