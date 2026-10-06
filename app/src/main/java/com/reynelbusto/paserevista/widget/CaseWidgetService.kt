package com.reynelbusto.paserevista.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.reynelbusto.paserevista.PaseRevistaApp
import com.reynelbusto.paserevista.R
import com.reynelbusto.paserevista.domain.model.displayName
import com.reynelbusto.paserevista.domain.usecase.DEFAULT_SERVICE_ID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Servicio de la colección del widget: las camas del día. */
class CaseWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        CaseViewsFactory(applicationContext)
}

private data class WidgetRow(val bed: String, val name: String, val ready: Boolean)

private class CaseViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private var rows: List<WidgetRow> = emptyList()

    private fun container() = (context.applicationContext as PaseRevistaApp).container

    override fun onCreate() = Unit
    override fun onDestroy() = Unit
    override fun getCount(): Int = rows.size
    override fun getViewTypeCount(): Int = 1
    override fun getLoadingView(): RemoteViews? = null
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false

    override fun onDataSetChanged() {
        // Hilo binder: runBlocking permitido.
        rows = runBlocking {
            val c = container()
            val journeyId = c.ensureDay(DEFAULT_SERVICE_ID)
            val cards = c.caseCardRepository.observeByJourney(journeyId).first()
            cards.map { card ->
                val patient = c.patientRepository.getPatient(card.patientId)
                WidgetRow(
                    bed = card.bed,
                    name = patient?.displayName(card.bed) ?: "Cama ${card.bed}",
                    ready = card.ready,
                )
            }.sortedWith(compareBy({ it.bed.toIntOrNull() }, { it.bed }))
        }
    }

    override fun getViewAt(position: Int): RemoteViews {
        val row = rows[position]
        return RemoteViews(context.packageName, R.layout.widget_case_item).apply {
            setTextViewText(R.id.widget_item_bed, "CAMA ${row.bed}")
            setTextViewText(R.id.widget_item_name, row.name)
            setTextViewText(
                R.id.widget_item_tag,
                if (row.ready) "LISTO" else "PENDIENTE",
            )
            // Tap ⇒ plantilla (abrir app).
            setOnClickFillInIntent(R.id.widget_item_root, Intent())
        }
    }
}
