package com.lhzkml.jasmine

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

/**
 * 文件树适配器 — 类似 VSCode 的资源管理器
 * 支持展开/折叠文件夹，缩进显示层级。
 */
class FileTreeAdapter : RecyclerView.Adapter<FileTreeAdapter.VH>() {

    data class FileNode(
        val file: File,
        val depth: Int,
        val isDirectory: Boolean,
        var expanded: Boolean = false
    )

    private val visibleNodes = mutableListOf<FileNode>()
    private var rootPath: String = ""

    var onFileClick: ((File) -> Unit)? = null

    fun loadRoot(path: String) {
        rootPath = path
        visibleNodes.clear()
        val root = File(path)
        if (root.exists() && root.isDirectory) {
            visibleNodes.addAll(loadChildren(root, 0))
        }
        notifyDataSetChanged()
    }

    private fun loadChildren(dir: File, depth: Int): List<FileNode> {
        val children = dir.listFiles()
            ?.filter { !it.name.startsWith(".") }
            ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            ?: emptyList()

        return children.map { file ->
            FileNode(
                file = file,
                depth = depth,
                isDirectory = file.isDirectory,
                expanded = false
            )
        }
    }

    private fun toggleExpand(position: Int) {
        val node = visibleNodes[position]
        if (!node.isDirectory) return

        if (node.expanded) {
            // 折叠：移除该节点下所有子节点
            node.expanded = false
            val removeStart = position + 1
            var removeCount = 0
            while (removeStart + removeCount < visibleNodes.size
                && visibleNodes[removeStart + removeCount].depth > node.depth) {
                removeCount++
            }
            if (removeCount > 0) {
                visibleNodes.subList(removeStart, removeStart + removeCount).clear()
                notifyItemRangeRemoved(removeStart, removeCount)
            }
            notifyItemChanged(position)
        } else {
            // 展开：插入子节点
            node.expanded = true
            val children = loadChildren(node.file, node.depth + 1)
            if (children.isNotEmpty()) {
                visibleNodes.addAll(position + 1, children)
                notifyItemRangeInserted(position + 1, children.size)
            }
            notifyItemChanged(position)
        }
    }

    override fun getItemCount() = visibleNodes.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file_tree, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val node = visibleNodes[position]

        // 缩进
        val indent = node.depth * 16
        holder.itemView.setPadding(
            indent + 8, holder.itemView.paddingTop,
            holder.itemView.paddingRight, holder.itemView.paddingBottom
        )

        // 展开/折叠指示器
        if (node.isDirectory) {
            holder.tvIndicator.text = if (node.expanded) "v" else ">"
            holder.tvIndicator.visibility = View.VISIBLE
        } else {
            holder.tvIndicator.text = ""
            holder.tvIndicator.visibility = View.INVISIBLE
        }

        // 图标
        holder.tvIcon.text = if (node.isDirectory) {
            if (node.expanded) "[-]" else "[+]"
        } else {
            getFileIcon(node.file.name)
        }

        // 文件名
        holder.tvName.text = node.file.name

        // 点击事件
        holder.itemView.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            val n = visibleNodes[pos]
            if (n.isDirectory) {
                toggleExpand(pos)
            } else {
                onFileClick?.invoke(n.file)
            }
        }
    }

    /**
     * 根据文件扩展名返回简单文本图标
     */
    private fun getFileIcon(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "kt", "kts" -> "K"
            "java" -> "J"
            "xml" -> "X"
            "json" -> "{}"
            "md" -> "M"
            "txt" -> "T"
            "gradle" -> "G"
            "py" -> "Py"
            "js", "ts" -> "JS"
            "html" -> "H"
            "css" -> "C"
            "yaml", "yml" -> "Y"
            "sh", "bat" -> "$"
            "png", "jpg", "jpeg", "gif", "webp", "svg" -> "Img"
            "properties" -> "P"
            "toml" -> "T"
            else -> "."
        }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvIndicator: TextView = view.findViewById(R.id.tvIndicator)
        val tvIcon: TextView = view.findViewById(R.id.tvIcon)
        val tvName: TextView = view.findViewById(R.id.tvName)
    }
}
