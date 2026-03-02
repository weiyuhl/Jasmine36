package com.lhzkml.jasmine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import android.view.View
import com.agog.mathdisplay.MTMathView

object LatexRenderer {

    private val cache = LruCache<String, Bitmap>(50)

    fun renderToBitmap(
        context: Context,
        latex: String,
        textSizeSp: Float,
        textColor: Int
    ): Bitmap? {
        val key = "$latex|$textSizeSp|$textColor"
        cache.get(key)?.let { return it }
        return try {
            val mathView = MTMathView(context)
            mathView.latex = latex.trim()
            mathView.fontSize = textSizeSp
            mathView.textColor = textColor

            val maxWidth = (context.resources.displayMetrics.widthPixels * 0.85f).toInt()
            val widthSpec = View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            mathView.measure(widthSpec, heightSpec)

            val w = mathView.measuredWidth.coerceAtLeast(1)
            val h = mathView.measuredHeight.coerceAtLeast(1)
            mathView.layout(0, 0, w, h)

            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            mathView.draw(canvas)

            cache.put(key, bitmap)
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    fun clearCache() {
        cache.evictAll()
    }
}
