package com.lhzkml.jasmine

import android.graphics.drawable.AnimationDrawable
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_AI = 1
        private const val TYPE_TYPING = 2
        private const val DEBOUNCE_MS = 50L
    }

    private val items = mutableListOf<ChatItem>()

    private val handler = Handler(Looper.getMainLooper())
    private var pendingStreamRender: Runnable? = null
    private var streamingViewHolder: AiViewHolder? = null
    var onStreamLayoutComplete: (() -> Unit)? = null

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is ChatItem.UserMessage -> TYPE_USER
        is ChatItem.AiMessage -> TYPE_AI
        is ChatItem.TypingIndicator -> TYPE_TYPING
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_USER -> UserViewHolder(inflater.inflate(R.layout.item_chat_user, parent, false))
            TYPE_AI -> AiViewHolder(inflater.inflate(R.layout.item_chat_ai, parent, false))
            TYPE_TYPING -> TypingViewHolder(inflater.inflate(R.layout.item_chat_typing, parent, false))
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ChatItem.UserMessage -> (holder as UserViewHolder).bind(item)
            is ChatItem.AiMessage -> {
                val aiHolder = holder as AiViewHolder
                aiHolder.bind(item)
                if (item.isStreaming) streamingViewHolder = aiHolder
            }
            is ChatItem.TypingIndicator -> (holder as TypingViewHolder).bind()
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder === streamingViewHolder) streamingViewHolder = null
    }

    // ========== Public API ==========

    fun addItem(item: ChatItem) {
        items.add(item)
        notifyItemInserted(items.size - 1)
    }

    fun updateStreamingAi(blocks: List<ContentBlock>) {
        removeTypingIndicatorIfPresent()
        val idx = findStreamingAiIndex()
        if (idx == null) {
            items.add(ChatItem.AiMessage(blocks = blocks, isStreaming = true))
            notifyItemInserted(items.size - 1)
            return
        }
        val item = items[idx] as ChatItem.AiMessage
        items[idx] = item.copy(blocks = blocks)
        scheduleStreamRender(idx)
    }

    fun addTypingIndicator() {
        if (findTypingIndicatorIndex() >= 0) return
        items.add(ChatItem.TypingIndicator)
        notifyItemInserted(items.size - 1)
    }

    fun removeTypingIndicator() {
        val idx = findTypingIndicatorIndex()
        if (idx >= 0) {
            items.removeAt(idx)
            notifyItemRemoved(idx)
        }
    }

    private fun removeTypingIndicatorIfPresent() {
        removeTypingIndicator()
    }

    fun finalizeStreamingAi(usageLine: String, time: String) {
        removeTypingIndicatorIfPresent()
        flushPendingRender()
        val idx = findStreamingAiIndex() ?: return
        val item = items[idx] as ChatItem.AiMessage
        items[idx] = item.copy(
            isStreaming = false,
            usageLine = usageLine,
            time = time
        )
        streamingViewHolder = null
        notifyItemChanged(idx)
    }

    fun clearAll() {
        flushPendingRender()
        streamingViewHolder = null
        val size = items.size
        items.clear()
        notifyItemRangeRemoved(0, size)
    }

    fun getItems(): List<ChatItem> = items.toList()

    private fun findStreamingAiIndex(): Int? {
        for (i in items.lastIndex downTo 0) {
            val item = items[i]
            if (item is ChatItem.AiMessage && item.isStreaming) return i
        }
        return null
    }

    private fun findTypingIndicatorIndex(): Int {
        return items.indexOfFirst { it is ChatItem.TypingIndicator }
    }

    // ========== Debounced stream rendering ==========

    private fun scheduleStreamRender(position: Int) {
        pendingStreamRender?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            pendingStreamRender = null
            val vh = streamingViewHolder
            if (vh != null && vh.bindingAdapterPosition == position) {
                val item = items.getOrNull(position) as? ChatItem.AiMessage ?: return@Runnable
                vh.bind(item)
                vh.itemView.post { onStreamLayoutComplete?.invoke() }
            } else {
                notifyItemChanged(position)
                handler.post { onStreamLayoutComplete?.invoke() }
            }
        }
        pendingStreamRender = runnable
        handler.postDelayed(runnable, DEBOUNCE_MS)
    }

    private fun flushPendingRender() {
        pendingStreamRender?.let {
            handler.removeCallbacks(it)
            it.run()
            pendingStreamRender = null
        }
    }

    // ========== ViewHolders ==========

    class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvContent: TextView = view.findViewById(R.id.tvContent)
        private val tvTime: TextView = view.findViewById(R.id.tvTime)

        fun bind(item: ChatItem.UserMessage) {
            tvContent.text = item.content
            tvTime.text = item.time
        }
    }

    class AiViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvContent: TextView = view.findViewById(R.id.tvContent)
        private val tvMeta: TextView = view.findViewById(R.id.tvMeta)
        private val mdRenderer: MarkdownRenderer by lazy { MarkdownRenderer(view.context) }

        fun bind(item: ChatItem.AiMessage) {
            renderBlocks(item.blocks, item.isStreaming)

            if (item.usageLine.isNotEmpty() || item.time.isNotEmpty()) {
                tvMeta.visibility = View.VISIBLE
                val meta = buildString {
                    if (item.usageLine.isNotEmpty()) append(item.usageLine)
                    if (item.time.isNotEmpty()) {
                        if (isNotEmpty()) append(" · ")
                        append(item.time)
                    }
                }
                tvMeta.text = meta
            } else {
                tvMeta.visibility = View.GONE
            }
        }

        private fun renderBlocks(blocks: List<ContentBlock>, isStreaming: Boolean) {
            if (blocks.isEmpty()) {
                tvContent.text = ""
                return
            }

            if (blocks.size == 1 && blocks[0] is ContentBlock.Text) {
                val text = (blocks[0] as ContentBlock.Text).content
                tvContent.text = mdRenderer.render(text, tvContent)
                tvContent.movementMethod = LinkMovementMethod.getInstance()
                return
            }

            val sb = SpannableStringBuilder()
            for ((i, block) in blocks.withIndex()) {
                if (i > 0 && sb.isNotEmpty() && sb[sb.length - 1] != '\n') {
                    sb.append("\n")
                }
                when (block) {
                    is ContentBlock.Text -> {
                        val rendered = mdRenderer.render(block.content, tvContent)
                        sb.append(rendered)
                    }
                    is ContentBlock.Thinking -> appendStyled(sb, "[Think] ${block.content}", COLOR_THINKING)
                    is ContentBlock.ToolCall -> appendStyled(sb, "[Tool] ${block.toolName}(${block.arguments})", COLOR_TOOL)
                    is ContentBlock.ToolResult -> appendStyled(sb, "[Result] ${block.toolName}: ${block.result}", COLOR_RESULT)
                    is ContentBlock.Plan -> appendPlan(sb, block)
                    is ContentBlock.GraphLog -> appendStyled(sb, block.content, COLOR_GRAPH, mono = true)
                    is ContentBlock.Error -> appendStyled(sb, block.message, COLOR_ERROR, bold = true)
                    is ContentBlock.SystemLog -> appendStyled(sb, block.content, COLOR_SYSTEM_LOG)
                }
            }
            tvContent.text = sb
            if (!isStreaming) {
                tvContent.movementMethod = LinkMovementMethod.getInstance()
            }
        }

        private fun appendStyled(
            sb: SpannableStringBuilder,
            text: String,
            color: Int,
            bold: Boolean = false,
            mono: Boolean = false
        ) {
            val start = sb.length
            sb.append(text)
            val end = sb.length
            sb.setSpan(ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (bold) {
                sb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (mono) {
                sb.setSpan(TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        private fun appendPlan(sb: SpannableStringBuilder, plan: ContentBlock.Plan) {
            val start = sb.length
            val text = buildString {
                append("[Plan] 目标: ${plan.goal}\n")
                plan.steps.forEachIndexed { i, step -> append("  ${i + 1}. $step\n") }
            }
            sb.append(text)
            sb.setSpan(ForegroundColorSpan(COLOR_PLAN), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        companion object {
            private const val COLOR_THINKING = 0xFF9E9E9E.toInt()
            private const val COLOR_TOOL = 0xFF42A5F5.toInt()
            private const val COLOR_RESULT = 0xFF66BB6A.toInt()
            private const val COLOR_PLAN = 0xFFAB47BC.toInt()
            private const val COLOR_GRAPH = 0xFF78909C.toInt()
            private const val COLOR_ERROR = 0xFFF44336.toInt()
            private const val COLOR_SYSTEM_LOG = 0xFF9E9E9E.toInt()
        }
    }

    class TypingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivAnimation: ImageView = view.findViewById(R.id.ivTypingAnimation)

        fun bind() {
            val drawable = ivAnimation.drawable
            if (drawable is AnimationDrawable) {
                drawable.start()
            }
        }
    }
}
