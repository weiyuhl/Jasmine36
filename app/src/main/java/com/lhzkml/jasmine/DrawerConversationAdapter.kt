package com.lhzkml.jasmine

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lhzkml.jasmine.core.conversation.storage.ConversationInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DrawerConversationAdapter : RecyclerView.Adapter<DrawerConversationAdapter.VH>() {
    private var items = listOf<ConversationInfo>()
    var onItemClick: ((ConversationInfo) -> Unit)? = null
    var onDeleteClick: ((ConversationInfo) -> Unit)? = null

    fun submitList(list: List<ConversationInfo>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_drawer_conversation, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val info = items[position]
        holder.tvTitle.text = info.title
        val providerName = ProviderManager.getAllProviders()
            .find { it.id == info.providerId }?.name ?: info.providerId
        val dateStr = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
            .format(Date(info.updatedAt))
        holder.tvMeta.text = "$providerName · $dateStr"
        holder.itemView.setOnClickListener { onItemClick?.invoke(info) }
        holder.btnDelete.setOnClickListener { onDeleteClick?.invoke(info) }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvMeta: TextView = view.findViewById(R.id.tvMeta)
        val btnDelete: TextView = view.findViewById(R.id.btnDelete)
    }
}
