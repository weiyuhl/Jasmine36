package com.lhzkml.jasmine

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import android.widget.TextView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin

class MarkdownRenderer(internal val context: Context) {

    private val markwon: Markwon by lazy {
        Markwon.builder(context)
            .usePlugin(ImagesPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .build()
    }

    companion object {
        private val BLOCK_LATEX = Regex("""\$\$(.+?)\$\$""", RegexOption.DOT_MATCHES_ALL)
        private val INLINE_LATEX = Regex("""\$(.+?)\$""")
        private const val PLACEHOLDER_TEMPLATE = "{{LATEX_%d}}"
    }

    fun render(text: String, textView: TextView): CharSequence {
        if (text.isBlank()) return ""

        val latexMap = mutableMapOf<String, LatexEntry>()
        val processed = extractLatex(text, latexMap)

        val spanned = markwon.toMarkdown(processed)

        if (latexMap.isEmpty()) {
            return spanned
        }

        val sb = SpannableStringBuilder(spanned)
        injectLatexSpans(sb, latexMap, textView)
        return sb
    }

    private data class LatexEntry(val latex: String, val isBlock: Boolean)

    private fun extractLatex(text: String, map: MutableMap<String, LatexEntry>): String {
        var counter = 0
        var result = text

        result = BLOCK_LATEX.replace(result) { match ->
            val placeholder = PLACEHOLDER_TEMPLATE.format(counter)
            map[placeholder] = LatexEntry(match.groupValues[1].trim(), isBlock = true)
            counter++
            "\n$placeholder\n"
        }

        result = INLINE_LATEX.replace(result) { match ->
            val content = match.groupValues[1].trim()
            if (content.isEmpty() || content.contains('\n')) {
                match.value
            } else {
                val placeholder = PLACEHOLDER_TEMPLATE.format(counter)
                map[placeholder] = LatexEntry(content, isBlock = false)
                counter++
                placeholder
            }
        }

        return result
    }

    private fun injectLatexSpans(
        sb: SpannableStringBuilder,
        latexMap: Map<String, LatexEntry>,
        textView: TextView
    ) {
        val textColor = textView.currentTextColor
        val res = context.resources
        val dm = res.displayMetrics
        val scaledDensity = if (android.os.Build.VERSION.SDK_INT >= 34) {
            dm.density * res.configuration.fontScale
        } else {
            @Suppress("DEPRECATION")
            dm.scaledDensity
        }
        val textSizeSp = textView.textSize / scaledDensity

        for ((placeholder, entry) in latexMap) {
            val idx = sb.indexOf(placeholder)
            if (idx < 0) continue

            val bitmap = LatexRenderer.renderToBitmap(
                context, entry.latex, textSizeSp, textColor
            )

            if (bitmap != null) {
                val span = ImageSpan(context, bitmap, ImageSpan.ALIGN_BASELINE)
                sb.setSpan(span, idx, idx + placeholder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                val fallback = if (entry.isBlock) "\$\$${entry.latex}\$\$" else "\$${entry.latex}\$"
                sb.replace(idx, idx + placeholder.length, fallback)
            }
        }
    }

    private fun SpannableStringBuilder.indexOf(text: String): Int {
        val str = this.toString()
        return str.indexOf(text)
    }
}
