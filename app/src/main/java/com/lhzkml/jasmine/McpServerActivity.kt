package com.lhzkml.jasmine

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lhzkml.jasmine.core.agent.tools.mcp.HttpMcpClient
import com.lhzkml.jasmine.core.agent.tools.mcp.McpToolDefinition
import com.lhzkml.jasmine.core.agent.tools.mcp.SseMcpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MCP 服务器管理界面
 * 进入时自动连接所有启用的服务器，显示连接状态和工具列表。
 */
class McpServerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EDIT_INDEX = "edit_index"
        const val REQUEST_EDIT = 1001
    }

    private lateinit var rvServers: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var switchEnabled: androidx.appcompat.widget.SwitchCompat
    private lateinit var layoutConfigContent: LinearLayout
    private val adapter = McpServerAdapter()

    /**
     * 连接结果：
     * - 不在 map 中 = 未测试
     * - ConnectionResult(true, tools) = 连接成功
     * - ConnectionResult(false, error=...) = 连接失败
     * - ConnectionResult(null) = 连接中
     */
    data class ConnectionResult(
        val success: Boolean? = null, // null=连接中, true=成功, false=失败
        val tools: List<McpToolDefinition> = emptyList(),
        val error: String? = null
    )

    private val connectionResults = mutableMapOf<Int, ConnectionResult>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mcp_server)

        rvServers = findViewById(R.id.rvServers)
        tvEmpty = findViewById(R.id.tvEmpty)
        switchEnabled = findViewById(R.id.switchEnabled)
        layoutConfigContent = findViewById(R.id.layoutConfigContent)

        switchEnabled.isChecked = ProviderManager.isMcpEnabled(this)
        layoutConfigContent.visibility = if (switchEnabled.isChecked) View.VISIBLE else View.GONE
        switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            ProviderManager.setMcpEnabled(this, isChecked)
            layoutConfigContent.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnAdd).setOnClickListener {
            @Suppress("DEPRECATION")
            startActivityForResult(
                Intent(this, McpServerEditActivity::class.java),
                REQUEST_EDIT
            )
        }

        rvServers.layoutManager = LinearLayoutManager(this)
        rvServers.adapter = adapter

        adapter.onTestClick = { index, _ -> connectServer(index) }
        adapter.onMoreClick = { index, server -> showServerActions(index, server) }
        adapter.onItemClick = { index, _ ->
            val intent = Intent(this, McpServerEditActivity::class.java)
            intent.putExtra(EXTRA_EDIT_INDEX, index)
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_EDIT)
        }

        refreshList()
        // 自动连接所有启用的服务器
        autoConnectAll()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_EDIT && resultCode == RESULT_OK) {
            connectionResults.clear()
            refreshList()
            autoConnectAll()
        }
    }

    private fun refreshList() {
        val servers = ProviderManager.getMcpServers(this)
        adapter.submitList(servers, connectionResults)
        tvEmpty.visibility = if (servers.isEmpty()) View.VISIBLE else View.GONE
        rvServers.visibility = if (servers.isEmpty()) View.GONE else View.VISIBLE
    }

    /** 自动连接所有启用的服务器（跳过已有缓存的） */
    private fun autoConnectAll() {
        val servers = ProviderManager.getMcpServers(this)
        val cache = MainActivity.mcpConnectionCache

        servers.forEachIndexed { index, server ->
            if (server.enabled && server.url.isNotBlank()) {
                // 如果全局缓存中已有该服务器的连接结果，直接复用
                val cached = cache[server.name]
                if (cached != null) {
                    connectionResults[index] = ConnectionResult(
                        success = cached.success,
                        tools = cached.tools,
                        error = cached.error
                    )
                } else {
                    connectServer(index)
                }
            }
        }
        refreshList()
    }

    private fun connectServer(index: Int) {
        val servers = ProviderManager.getMcpServers(this)
        if (index !in servers.indices) return
        val server = servers[index]

        // 标记为连接中
        connectionResults[index] = ConnectionResult(success = null)
        refreshList()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val headers = buildHeaders(server)
                val client = when (server.transportType) {
                    ProviderManager.McpTransportType.SSE ->
                        SseMcpClient(server.url, customHeaders = headers)
                    ProviderManager.McpTransportType.STREAMABLE_HTTP ->
                        HttpMcpClient(server.url, headers)
                }
                client.connect()
                val tools = client.listTools()
                client.close()

                // 更新全局缓存
                MainActivity.Companion.mcpConnectionCache[server.name] = MainActivity.Companion.McpServerStatus(
                    success = true,
                    tools = tools
                )

                withContext(Dispatchers.Main) {
                    connectionResults[index] = ConnectionResult(
                        success = true,
                        tools = tools
                    )
                    refreshList()
                }
            } catch (e: Exception) {
                // 更新全局缓存
                MainActivity.Companion.mcpConnectionCache[server.name] = MainActivity.Companion.McpServerStatus(
                    success = false,
                    error = e.message ?: "未知错误"
                )

                withContext(Dispatchers.Main) {
                    connectionResults[index] = ConnectionResult(
                        success = false,
                        error = e.message ?: "未知错误"
                    )
                    refreshList()
                }
            }
        }
    }

    private fun showServerActions(index: Int, server: ProviderManager.McpServerConfig) {
        val actions = arrayOf(
            "编辑",
            if (server.enabled) "禁用" else "启用",
            "删除"
        )
        AlertDialog.Builder(this)
            .setTitle(server.name)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> {
                        val intent = Intent(this, McpServerEditActivity::class.java)
                        intent.putExtra(EXTRA_EDIT_INDEX, index)
                        @Suppress("DEPRECATION")
                        startActivityForResult(intent, REQUEST_EDIT)
                    }
                    1 -> {
                        ProviderManager.updateMcpServer(this, index, server.copy(enabled = !server.enabled))
                        connectionResults.remove(index)
                        refreshList()
                        // 如果刚启用，自动连接
                        if (!server.enabled) connectServer(index)
                    }
                    2 -> {
                        AlertDialog.Builder(this)
                            .setMessage("确定删除 ${server.name}？")
                            .setPositiveButton("删除") { _, _ ->
                                ProviderManager.removeMcpServer(this, index)
                                connectionResults.clear()
                                refreshList()
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun buildHeaders(server: ProviderManager.McpServerConfig): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        if (server.headerName.isNotBlank() && server.headerValue.isNotBlank()) {
            headers[server.headerName] = server.headerValue
        }
        return headers
    }

    // ========== RecyclerView Adapter ==========

    private class McpServerAdapter : RecyclerView.Adapter<McpServerAdapter.VH>() {
        private var items = listOf<ProviderManager.McpServerConfig>()
        private var resultMap = mapOf<Int, ConnectionResult>()

        var onTestClick: ((Int, ProviderManager.McpServerConfig) -> Unit)? = null
        var onMoreClick: ((Int, ProviderManager.McpServerConfig) -> Unit)? = null
        var onItemClick: ((Int, ProviderManager.McpServerConfig) -> Unit)? = null

        fun submitList(list: List<ProviderManager.McpServerConfig>, results: Map<Int, ConnectionResult>) {
            items = list
            resultMap = results.toMap()
            notifyDataSetChanged()
        }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_mcp_server, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val server = items[position]
            val result = resultMap[position]
            val ctx = holder.itemView.context

            holder.tvName.text = server.name
            holder.tvUrl.text = server.url

            val transportLabel = when (server.transportType) {
                ProviderManager.McpTransportType.STREAMABLE_HTTP -> "Streamable HTTP"
                ProviderManager.McpTransportType.SSE -> "SSE"
            }
            val enabledLabel = if (server.enabled) "" else " · 已禁用"
            holder.tvTransport.text = "$transportLabel$enabledLabel"

            // 状态指示灯
            val statusColor = when {
                !server.enabled -> ctx.getColor(R.color.status_unknown)
                result == null -> ctx.getColor(R.color.status_unknown)
                result.success == null -> ctx.getColor(R.color.status_testing)
                result.success -> ctx.getColor(R.color.status_connected)
                else -> ctx.getColor(R.color.status_failed)
            }
            holder.viewStatus.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(statusColor)
            }

            holder.itemView.alpha = if (server.enabled) 1f else 0.5f

            // 工具列表（连接成功时显示）
            if (result != null && result.success == true && result.tools.isNotEmpty()) {
                holder.layoutTools.visibility = View.VISIBLE
                holder.tvError.visibility = View.GONE
                holder.tvToolsHeader.text = "可用工具 (${result.tools.size})"
                holder.tvToolsList.text = result.tools.joinToString("\n") { tool ->
                    val desc = tool.description?.let { d ->
                        if (d.length > 60) d.take(60) + "..." else d
                    } ?: ""
                    if (desc.isNotEmpty()) "${tool.name} — $desc" else tool.name
                }
            } else if (result != null && result.success == false) {
                holder.layoutTools.visibility = View.GONE
                holder.tvError.visibility = View.VISIBLE
                holder.tvError.text = "连接失败: ${result.error}"
            } else if (result != null && result.success == null) {
                holder.layoutTools.visibility = View.GONE
                holder.tvError.visibility = View.VISIBLE
                holder.tvError.setTextColor(ctx.getColor(R.color.status_testing))
                holder.tvError.text = "连接中..."
            } else {
                holder.layoutTools.visibility = View.GONE
                holder.tvError.visibility = View.GONE
            }

            // 失败状态恢复颜色
            if (result?.success == false) {
                holder.tvError.setTextColor(ctx.getColor(R.color.status_failed))
            }

            holder.btnTest.setOnClickListener { onTestClick?.invoke(position, server) }
            holder.btnMore.setOnClickListener { onMoreClick?.invoke(position, server) }
            holder.itemView.setOnClickListener { onItemClick?.invoke(position, server) }
        }

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val viewStatus: View = view.findViewById(R.id.viewStatus)
            val tvName: TextView = view.findViewById(R.id.tvName)
            val tvUrl: TextView = view.findViewById(R.id.tvUrl)
            val tvTransport: TextView = view.findViewById(R.id.tvTransport)
            val btnTest: TextView = view.findViewById(R.id.btnTest)
            val btnMore: TextView = view.findViewById(R.id.btnMore)
            val layoutTools: LinearLayout = view.findViewById(R.id.layoutTools)
            val tvToolsHeader: TextView = view.findViewById(R.id.tvToolsHeader)
            val tvToolsList: TextView = view.findViewById(R.id.tvToolsList)
            val tvError: TextView = view.findViewById(R.id.tvError)
        }
    }
}
