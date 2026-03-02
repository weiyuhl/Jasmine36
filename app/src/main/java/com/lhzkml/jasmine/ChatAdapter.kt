package com.lhzkml.jasmine

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_AI = 1
        private const val TYPE_LOG = 2
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
        is ChatItem.LogMessage -> TYPE_LOG
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_USER -> UserViewHolder(inflater.inflate(R.layout.item_chat_user, parent, false))
            TYPE_AI -> AiViewHolder(inflater.inflate(R.layout.item_chat_ai, parent, false))
            TYPE_LOG -> LogViewHolder(inflater.inflate(R.layout.item_chat_log, parent, false))
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
            is ChatItem.LogMessage -> (holder as LogViewHolder).bind(item)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder === streamingViewHolder) streamingViewHolder = null
    }

    // ========== Public API for MainActivity ==========

    fun addItem(item: ChatItem) {
        items.add(item)
        notifyItemInserted(items.size - 1)
    }

    fun appendToStreamingAi(chunk: String) {
        val idx = findStreamingAiIndex()
        if (idx == null) {
            items.add(ChatItem.AiMessage(content = chunk, isStreaming = true))
            notifyItemInserted(items.size - 1)
            return
        }
        val item = items[idx] as ChatItem.AiMessage
        items[idx] = item.copy(content = item.content + chunk)
        scheduleStreamRender(idx)
    }

    private fun findStreamingAiIndex(): Int? {
        for (i in items.lastIndex downTo 0) {
            val item = items[i]
            if (item is ChatItem.AiMessage && item.isStreaming) return i
        }
        return null
    }

    fun appendLog(text: String) {
        val lastIndex = items.lastIndex
        if (lastIndex >= 0) {
            val last = items[lastIndex]
            if (last is ChatItem.LogMessage) {
                items[lastIndex] = ChatItem.LogMessage(last.content + text)
                notifyItemChanged(lastIndex)
                return
            }
        }
        addItem(ChatItem.LogMessage(text))
    }

    fun finalizeStreamingAi(usageLine: String, time: String) {
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

        fun bind(item: ChatItem.AiMessage) {
            tvContent.text = item.content

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
    }

    class LogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvLog: TextView = view.findViewById(R.id.tvLog)

        fun bind(item: ChatItem.LogMessage) {
            tvLog.text = item.content
        }
    }
}
