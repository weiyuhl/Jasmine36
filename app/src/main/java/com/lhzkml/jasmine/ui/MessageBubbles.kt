package com.lhzkml.jasmine.ui

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.graphics.Typeface
import android.widget.TextView
import android.graphics.drawable.AnimationDrawable
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.lhzkml.jasmine.ChatItem
import com.lhzkml.jasmine.ContentBlock
import com.lhzkml.jasmine.MarkdownRenderer
import com.lhzkml.jasmine.ui.components.CustomText
import com.lhzkml.jasmine.ui.theme.*

@Composable
fun UserBubble(item: ChatItem.UserMessage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.End
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(UserBubble, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            CustomText(
                text = item.content,
                color = UserBubbleText,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
        }
        if (item.time.isNotEmpty()) {
            CustomText(
                text = item.time,
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 3.dp, end = 4.dp)
            )
        }
    }
}

@Composable
fun AiBubble(item: ChatItem.AiMessage) {
    val hasContent = item.blocks.isNotEmpty()
    if (!hasContent && item.usageLine.isEmpty() && item.time.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        if (hasContent) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 4.dp)
            ) {
                AiContentBlocks(blocks = item.blocks, isStreaming = item.isStreaming)
            }
        }
        if (item.usageLine.isNotEmpty() || item.time.isNotEmpty()) {
            val meta = buildString {
                if (item.usageLine.isNotEmpty()) append(item.usageLine)
                if (item.time.isNotEmpty()) {
                    if (isNotEmpty()) append(" · ")
                    append(item.time)
                }
            }
            CustomText(
                text = meta,
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 3.dp, start = 4.dp)
            )
        }
    }
}

@Composable
fun AiContentBlocks(blocks: List<ContentBlock>, isStreaming: Boolean) {
    val context = LocalContext.current
    val mdRenderer = remember(context) { MarkdownRenderer(context) }

    // ✅ 缓存渲染结果，避免每次重组都重新渲染
    val renderedContent = remember(blocks, isStreaming) {
        renderContentBlocksCached(blocks, mdRenderer, isStreaming)
    }

    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                textSize = 15f
                setTextColor(android.graphics.Color.parseColor("#FF1A1A1A"))
                setLineSpacing(4f * ctx.resources.displayMetrics.density, 1f)
            }
        },
        update = { tv ->
            tv.text = renderedContent
            if (!isStreaming) {
                tv.movementMethod = LinkMovementMethod.getInstance()
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

// 缓存渲染结果的辅助函数
private fun renderContentBlocksCached(
    blocks: List<ContentBlock>,
    mdRenderer: MarkdownRenderer,
    isStreaming: Boolean
): CharSequence {
    // 创建临时 TextView 用于渲染
    val tv = TextView(mdRenderer.context).apply {
        textSize = 15f
        setTextColor(android.graphics.Color.parseColor("#FF1A1A1A"))
    }
    return renderContentBlocks(blocks, tv, mdRenderer, isStreaming)
}

private fun renderContentBlocks(
    blocks: List<ContentBlock>,
    tv: TextView,
    mdRenderer: MarkdownRenderer,
    isStreaming: Boolean
): CharSequence {
    if (blocks.isEmpty()) return ""

    if (blocks.size == 1 && blocks[0] is ContentBlock.Text) {
        return mdRenderer.render((blocks[0] as ContentBlock.Text).content, tv)
    }

    val sb = SpannableStringBuilder()
    for ((i, block) in blocks.withIndex()) {
        if (i > 0 && sb.isNotEmpty() && sb[sb.length - 1] != '\n') sb.append("\n")
        when (block) {
            is ContentBlock.Text -> sb.append(mdRenderer.render(block.content, tv))
            is ContentBlock.Thinking -> appendStyled(sb, "[Think] ${block.content}", 0xFF9E9E9E.toInt())
            is ContentBlock.ToolCall -> appendStyled(sb, "[Tool] ${block.toolName}(${block.arguments})", 0xFF42A5F5.toInt())
            is ContentBlock.ToolResult -> appendStyled(sb, "[Result] ${block.toolName}: ${block.result}", 0xFF66BB6A.toInt())
            is ContentBlock.Plan -> appendPlan(sb, block)
            is ContentBlock.GraphLog -> appendStyled(sb, block.content, 0xFF78909C.toInt(), mono = true)
            is ContentBlock.Error -> appendStyled(sb, block.message, 0xFFF44336.toInt(), bold = true)
            is ContentBlock.SystemLog -> appendSystemOrEventLog(sb, block.content)
            is ContentBlock.SubAgentStart -> appendStyled(sb, "[SubAgent] ${block.purpose} (${block.subagentType})", 0xFF7E57C2.toInt(), bold = true)
            is ContentBlock.SubAgentResult -> appendStyled(sb, "[SubAgent Result] ${block.purpose}: ${block.result}", 0xFF26A69A.toInt())
        }
    }
    return sb
}

private fun appendSystemOrEventLog(sb: SpannableStringBuilder, content: String) {
    val toolColor = 0xFF42A5F5.toInt()
    val resultColor = 0xFF66BB6A.toInt()
    val errorColor = 0xFFF44336.toInt()
    val llmColor = 0xFF78909C.toInt()
    val planColor = 0xFFAB47BC.toInt()
    val nodeColor = 0xFF26A69A.toInt()
    val systemColor = 0xFF9E9E9E.toInt()
    content.split('\n').forEachIndexed { i, line ->
        if (i > 0) sb.append("\n")
        val color = when {
            !line.startsWith("[EVENT]") -> systemColor
            line.contains("工具完成") -> resultColor
            line.contains("工具调用") -> toolColor
            line.contains("失败") || line.contains("验证失败") -> errorColor
            line.contains("策略") -> planColor
            line.contains("节点") || line.contains("子图") -> nodeColor
            line.contains("Agent ") -> toolColor
            line.contains("LLM ") -> llmColor
            else -> systemColor
        }
        appendStyled(sb, line, color)
    }
}

private fun appendStyled(
    sb: SpannableStringBuilder, text: String, color: Int,
    bold: Boolean = false, mono: Boolean = false
) {
    val start = sb.length
    sb.append(text)
    val end = sb.length
    sb.setSpan(ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    if (bold) sb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    if (mono) sb.setSpan(TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
}

private fun appendPlan(sb: SpannableStringBuilder, plan: ContentBlock.Plan) {
    val start = sb.length
    val text = buildString {
        append("[Plan] 目标: ${plan.goal}\n")
        plan.steps.forEachIndexed { i, step -> append("  ${i + 1}. $step\n") }
    }
    sb.append(text)
    sb.setSpan(ForegroundColorSpan(0xFFAB47BC.toInt()), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
}

@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        AndroidView(
            factory = { ctx ->
                ImageView(ctx).apply {
                    setImageResource(com.lhzkml.jasmine.R.drawable.typing_indicator_animated)
                    contentDescription = ctx.getString(com.lhzkml.jasmine.R.string.typing_indicator_content_description)
                    scaleType = ImageView.ScaleType.CENTER
                    adjustViewBounds = true
                    (drawable as? AnimationDrawable)?.start()
                }
            },
            modifier = Modifier.wrapContentWidth()
        )
    }
}
