package dev.jesusdesivar.manticwidget.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import dev.jesusdesivar.manticwidget.work.RefreshWorker

class ManticWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = ManticWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // First widget placed on the home screen: start the periodic refresh.
        RefreshWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Last widget removed: stop background work to save battery.
        RefreshWorker.cancel(context)
    }
}
