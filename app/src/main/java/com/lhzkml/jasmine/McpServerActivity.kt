package com.lhzkml.jasmine

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lhzkml.jasmine.core.agent.tools.mcp.HttpMcpClient
import com.lhzkml.jasmine.core.agent.tools.mcp.SseMcpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MCP 服务器管理界面
 * 显示已配置的 MCP 服务器列表，支持添加/编辑/删除/测试连接。
 */
class McpServerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EDIT_INDEX = "edit_index"
        const val REQUEST_EDIT = 1001
    }

    private lateinit var rvServers: RecyclerView
    private lateinit var tvEmpty: TextView
    private val adapter = McpServerAdapter()

    /** 每个服务器的连接状态：null=未测试, true=连接成功, false=连接失败 */
    private val connectionStatus = mutableMapOf<Int, Boolean?>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mcp_server)

        rvServers = findViewById(R.id.rvServers)
        tvEmpty = findViewById(R.id.tvEmpty)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnAdd).setOnClickListener {
            startActivityForResult(
                Intent(this, McpServerEditActivity::class.java),
                REQUEST_EDIT
            )
        }

        rvServers.layoutManager = LinearLayoutManager(this)
        rvServers.adapter = adapter

        adapter.onTestClick = { index, _ -> testConnection(index) }
        adapter.onMoreClick = { index, server -> showServerActions(index, server) }
        adapter.onItemClick = { index, _ ->
            val intent = Intent(this, McpServerEditActivity::class.java)
            intent.putExtra(EXTRA_EDIT_INDEX, index)
            startActivityForResult(intent, REQUEST_EDIT)
        }

        refreshList()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_EDIT && resultCode == RESULT_OK) {
            connectionStatus.clear()
            refreshList()
        }
    }

    private fun refreshList() {
        val servers = ProviderManager.getMcpServers(this)
        adapter.submitList(servers, connectionStatus)
        tvEmpty.visibility = if (servers.isEmpty()) View.VISIBLE else View.GONE
        rvServers.visibility = if (servers.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun testConnection(index: Int) {
        val servers = ProviderManager.getMcpServers(this)
        if (index !in servers.indices) return
        val server = servers[index]

        // 标记为测试中（用 null 表示）
        connectionStatus[index] = null
        adapter.submitList(servers, connectionStatus)

        CoroutineScope(Dispatchers.IO).launch {
            val success = try {
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
                true
            } catch (_: Exception) {
                false
            }

            withContext(Dispatchers.Main) {
                connectionStatus[index] = success
                adapter.submitList(ProviderManager.getMcpServers(this@McpServerActivity), connectionStatus)
                val msg = if (success) "✅ ${server.name} 连接成功" else "❌ ${server.name} 连接失败"
                Toast.makeText(this@McpServerActivity, msg, Toast.LENGTH_SHORT).show()
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
                        startActivityForResult(intent, REQUEST_EDIT)
                    }
                    1 -> {
                        ProviderManager.updateMcpServer(this, index, server.copy(enabled = !server.enabled))
                        connectionStatus.remove(index)
                        refreshList()
                    }
                    2 -> {
                        AlertDialog.Builder(this)
                            .setMessage("确定删除 ${server.name}？")
                            .setPositiveButton("删除") { _, _ ->
                                ProviderManager.removeMcpServer(this, index)
                                connectionStatus.clear()
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
        private var statusMap = mapOf<Int, Boolean?>()

        var onTestClick: ((Int, ProviderManager.McpServerConfig) -> Unit)? = null
        var onMoreClick: ((Int, ProviderManager.McpServerConfig) -> Unit)? = null
        var onItemClick: ((Int, ProviderManager.McpServerConfig) -> Unit)? = null

        fun submitList(list: List<ProviderManager.McpServerConfig>, status: Map<Int, Boolean?>) {
            items = list
            statusMap = status.toMap()
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
            holder.tvName.text = server.name
            holder.tvUrl.text = server.url

            val transportLabel = when (server.transportType) {
                ProviderManager.McpTransportType.STREAMABLE_HTTP -> "Streamable HTTP"
                ProviderManager.McpTransportType.SSE -> "SSE"
            }
            val enabledLabel = if (server.enabled) "" else " · 已禁用"
            holder.tvTransport.text = "$transportLabel$enabledLabel"

            // 设置状态指示灯颜色（圆形）
            val statusColor = when {
                !server.enabled -> holder.itemView.context.getColor(R.color.status_unknown)
                position in statusMap -> {
                    val connected = statusMap[position]
                    when (connected) {
                        true -> holder.itemView.context.getColor(R.color.status_connected)
                        false -> holder.itemView.context.getColor(R.color.status_failed)
                        null -> holder.itemView.context.getColor(R.color.status_testing)
                    }
                }
                else -> holder.itemView.context.getColor(R.color.status_unknown)
            }
            val dot = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(statusColor)
            }
            holder.viewStatus.background = dot

            // 禁用的服务器半透明
            holder.itemView.alpha = if (server.enabled) 1f else 0.5f

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
        }
    }
}
