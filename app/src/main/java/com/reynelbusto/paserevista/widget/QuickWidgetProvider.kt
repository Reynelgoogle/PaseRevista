package com.reynelbusto.paserevista.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.reynelbusto.paserevista.R

/**
 * Segundo widget: "Guardia rápida", estilo Todoist.
 * El widget clásico (CaseWidgetProvider) no se toca.
 *
 * - Lista de camas del día: check de un toque (alterna Pendiente/Listo),
 *   "CAMA N · diagnóstico", conteo de pendientes, etiqueta PENDIENTE/LISTO.
 * - Cabecera: botón micrófono y botón ＋ ⇒ entrada rápida ([QuickAddActivity]).
 * - Toque en la fila ⇒ detalle rápido ([QuickDetailActivity]).
 */
class QuickWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        /** Llamar tras cada mutación para refrescar el widget. */
        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, QuickWidgetProvider::class.java),
            )
            if (ids.isEmpty()) return
            manager.notifyAppWidgetViewDataChanged(ids, R.id.quick_list)
            val intent = Intent(context, QuickWidgetProvider::class.java).apply {
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
                val views = RemoteViews(context.packageName, R.layout.quick_widget_list)
                views.setTextViewText(R.id.quick_title, "Guardia rápida")

                // Colección: las camas del día.
                val serviceIntent = Intent(context, QuickWidgetService::class.java).apply {
                    data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME) + "#$id")
                }
                views.setRemoteAdapter(R.id.quick_list, serviceIntent)
                views.setEmptyView(R.id.quick_list, R.id.quick_empty)

                // Plantilla única: los toques de cada fila van al receiver,
                // que distingue check/detalle por el extra KIND del fill-in.
                val template = PendingIntent.getBroadcast(
                    context, 0,
                    Intent(context, WidgetActionReceiver::class.java)
                        .setAction(WidgetActionReceiver.ACTION_ROW_CLICK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                views.setPendingIntentTemplate(R.id.quick_list, template)

                // ＋ ⇒ entrada rápida.
                val add = PendingIntent.getActivity(
                    context, 10,
                    Intent(context, QuickAddActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                views.setOnClickPendingIntent(R.id.quick_add, add)

                // Micrófono ⇒ entrada rápida con dictado por voz.
                val mic = PendingIntent.getActivity(
                    context, 11,
                    Intent(context, QuickAddActivity::class.java)
                        .putExtra(QuickAddActivity.EXTRA_VOICE, true),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                views.setOnClickPendingIntent(R.id.quick_mic, mic)

                manager.updateAppWidget(id, views)
            }
        }
    }
}
