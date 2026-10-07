package com.reynelbusto.paserevista.widget

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.reynelbusto.paserevista.PaseRevistaApp
import com.reynelbusto.paserevista.R
import com.reynelbusto.paserevista.domain.usecase.DEFAULT_SERVICE_ID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Servicio de la colección del widget "Guardia rápida": las camas del día. */
class QuickWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        QuickViewsFactory(applicationContext)
}

private data class QuickRow(
    val cardId: String,
    val bed: String,
    val diagnosis: String?,
    val openPendings: Int,
    val ready: Boolean,
)

private class QuickViewsFactory(private val context: Context) :
    RemoteViewsService.RemoteViewsFactory {

    private var rows: List<QuickRow> = emptyList()
    /** La última carga falló: `rows` son las filas anteriores (pueden estar vacías). */
    private var loadFailed: Boolean = false

    private fun container() = (context.applicationContext as PaseRevistaApp).container

    override fun onCreate() = Unit
    override fun onDestroy() = Unit
    override fun getViewTypeCount(): Int = 1
    override fun getLoadingView(): RemoteViews? = null
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false

    override fun onDataSetChanged() {
        // Blindaje total (igual que CaseWidgetService): una excepción aquí
        // nunca debe propagarse al sistema. Se conservan las filas anteriores.
        try {
            // Hilo binder: runBlocking permitido.
            rows = runBlocking {
                val c = container()
                val journeyId = c.ensureDay(DEFAULT_SERVICE_ID)
                val cards = c.caseCardRepository.observeByJourney(journeyId).first()
                cards.map { card ->
                    val open = c.pendingRepository.getOpenByPatient(card.patientId).size
                    QuickRow(
                        cardId = card.id,
                        bed = card.bed,
                        diagnosis = card.diagnosis,
                        openPendings = open,
                        ready = card.ready,
                    )
                }.sortedWith(compareBy({ it.bed.toIntOrNull() }, { it.bed }))
            }
            loadFailed = false
        } catch (e: Exception) {
            loadFailed = true
        }
    }

    override fun getCount(): Int =
        if (loadFailed && rows.isEmpty()) 1 else rows.size

    /** Fill-in para el check (toggle) o para el cuerpo de la fila (detalle). */
    private fun click(kind: String, cardId: String): Intent =
        Intent().apply {
            putExtra(WidgetActionReceiver.EXTRA_KIND, kind)
            putExtra(WidgetActionReceiver.EXTRA_CARD_ID, cardId)
        }

    override fun getViewAt(position: Int): RemoteViews {
        if (loadFailed && rows.isEmpty()) {
            return RemoteViews(context.packageName, R.layout.quick_widget_item).apply {
                setTextViewText(R.id.quick_item_title, "AVISO")
                setTextViewText(R.id.quick_item_sub, "No se pudieron cargar las camas")
                setTextViewText(R.id.quick_item_check, "")
                setTextViewText(R.id.quick_item_tag, "ERROR")
                setOnClickFillInIntent(R.id.quick_item_body, Intent())
            }
        }
        val row = rows[position]
        return RemoteViews(context.packageName, R.layout.quick_widget_item).apply {
            setTextViewText(
                R.id.quick_item_title,
                quickRowTitle(row.bed, row.diagnosis),
            )
            setTextViewText(
                R.id.quick_item_sub,
                quickRowSubtitle(row.openPendings),
            )
            setTextViewText(R.id.quick_item_check, if (row.ready) "✓" else "○")
            setTextViewText(
                R.id.quick_item_tag,
                if (row.ready) "LISTO" else "PENDIENTE",
            )
            setTextColor(
                R.id.quick_item_tag,
                if (row.ready) Color.parseColor("#A5D6A7") else Color.parseColor("#FF8A80"),
            )
            // Check ⇒ alternar Listo; cuerpo ⇒ abrir detalle rápido.
            setOnClickFillInIntent(
                R.id.quick_item_check,
                click(WidgetActionReceiver.KIND_TOGGLE, row.cardId),
            )
            setOnClickFillInIntent(
                R.id.quick_item_body,
                click(WidgetActionReceiver.KIND_DETAIL, row.cardId),
            )
        }
    }
}
