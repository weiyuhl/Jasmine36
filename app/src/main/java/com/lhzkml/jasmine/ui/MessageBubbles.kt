package com.lhzkml.jasmine.ui

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.graphics.Typeface
import android.widget.TextView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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

    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                textSize = 15f
                setTextColor(android.graphics.Color.parseColor("#FF1A1A1A"))
                setLineSpacing(4f * ctx.resources.displayMetrics.density, 1f)
                setTextIsSelectable(!isStreaming)
            }
        },
        update = { tv ->
            tv.setTextIsSelectable(!isStreaming)
            val rendered = renderContentBlocks(blocks, tv, mdRenderer, isStreaming)
            tv.text = rendered
            if (!isStreaming) {
                tv.movementMethod = LinkMovementMethod.getInstance()
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
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
            is ContentBlock.SystemLog -> appendStyled(sb, block.content, 0xFF9E9E9E.toInt())
        }
    }
    return sb
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
    val transition = rememberInfiniteTransition(label = "typing")
    val dot1Alpha by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "dot1"
    )
    val dot2Alpha by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 200, easing = LinearEasing), RepeatMode.Reverse),
        label = "dot2"
    )
    val dot3Alpha by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 400, easing = LinearEasing), RepeatMode.Reverse),
        label = "dot3"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(dot1Alpha, dot2Alpha, dot3Alpha).forEach { alpha ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .alpha(alpha)
                    .background(TextSecondary, CircleShape)
            )
        }
    }
}
