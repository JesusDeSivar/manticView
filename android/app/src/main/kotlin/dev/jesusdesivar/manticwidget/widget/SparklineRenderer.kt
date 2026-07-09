package dev.jesusdesivar.manticwidget.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

/**
 * Renders a probability history as a small sparkline bitmap. Glance has no
 * canvas primitives, so charts are drawn off-screen and shown via Image.
 */
object SparklineRenderer {

    fun render(
        history: List<Double>,
        widthPx: Int = 140,
        heightPx: Int = 40,
        color: Int,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        if (history.size < 2) return bitmap

        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = heightPx / 14f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val min = history.min()
        val max = history.max()
        // Give flat lines some vertical room so they don't hug an edge.
        val span = (max - min).coerceAtLeast(0.02)
        val pad = paint.strokeWidth

        val path = Path()
        history.forEachIndexed { i, value ->
            val x = pad + (widthPx - 2 * pad) * i / (history.size - 1).toFloat()
            val y = pad + (heightPx - 2 * pad) * (1f - ((value - min) / span).toFloat())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, paint)
        return bitmap
    }
}
