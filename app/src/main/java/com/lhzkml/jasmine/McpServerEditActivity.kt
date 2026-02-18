package com.lhzkml.jasmine

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.lhzkml.jasmine.core.agent.tools.mcp.HttpMcpClient
import com.lhzkml.jasmine.core.agent.tools.mcp.SseMcpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MCP 服务器编辑界面
 * 添加或编辑单个 MCP 服务器配置。
 */
class McpServerEditActivity : AppCompatActivity() {

    private var editIndex = -1

    private lateinit var tvTitle: TextView
    private lateinit var etName: EditText
    private lateinit var etUrl: EditText
    private lateinit var rgTransport: RadioGroup
    private lateinit var rbStreamableHttp: RadioButton
    private lateinit var rbSse: RadioButton
    private lateinit var etHeaderName: EditText
    private lateinit var etHeaderValue: EditText
    private lateinit var btnTestConnection: MaterialButton
    private lateinit var tvTestResult: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mcp_server_edit)

        editIndex = intent.getIntExtra(McpServerActivity.EXTRA_EDIT_INDEX, -1)

        tvTitle = findViewById(R.id.tvTitle)
        etName = findViewById(R.id.etName)
        etUrl = findViewById(R.id.etUrl)
        rgTransport = findViewById(R.id.rgTransport)
        rbStreamableHttp = findViewById(R.id.rbStreamableHttp)
        rbSse = findViewById(R.id.rbSse)
        etHeaderName = findViewById(R.id.etHeaderName)
        etHeaderValue = findViewById(R.id.etHeaderValue)
        btnTestConnection = findViewById(R.id.btnTestConnection)
        tvTestResult = findViewById(R.id.tvTestResult)

        tvTitle.text = if (editIndex >= 0) "编辑 MCP 服务器" else "添加 MCP 服务器"

        // 加载已有配置
        if (editIndex >= 0) {
            val servers = ProviderManager.getMcpServers(this)
            if (editIndex in servers.indices) {
                val server = servers[editIndex]
                etName.setText(server.name)
                etUrl.setText(server.url)
                when (server.transportType) {
                    ProviderManager.McpTransportType.STREAMABLE_HTTP -> rbStreamableHttp.isChecked = true
                    ProviderManager.McpTransportType.SSE -> rbSse.isChecked = true
                }
                etHeaderName.setText(server.headerName)
                etHeaderValue.setText(server.headerValue)
            }
        }

        findViewById<View>(R.id.btnCancel).setOnClickListener { finish() }
        findViewById<View>(R.id.btnSave).setOnClickListener { save() }
        btnTestConnection.setOnClickListener { testConnection() }
    }

    private fun save() {
        val name = etName.text.toString().trim().ifEmpty { "MCP Server" }
        val url = etUrl.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(this, "URL 不能为空", Toast.LENGTH_SHORT).show()
            return
        }

        val transportType = if (rbSse.isChecked) {
            ProviderManager.McpTransportType.SSE
        } else {
            ProviderManager.McpTransportType.STREAMABLE_HTTP
        }

        val headerName = etHeaderName.text.toString().trim()
        val headerValue = etHeaderValue.text.toString().trim()

        val config = ProviderManager.McpServerConfig(
            name = name,
            url = url,
            transportType = transportType,
            headerName = headerName,
            headerValue = headerValue,
            enabled = true
        )

        if (editIndex >= 0) {
            // 保留原来的 enabled 状态
            val servers = ProviderManager.getMcpServers(this)
            val oldEnabled = servers.getOrNull(editIndex)?.enabled ?: true
            ProviderManager.updateMcpServer(this, editIndex, config.copy(enabled = oldEnabled))
        } else {
            ProviderManager.addMcpServer(this, config)
        }

        setResult(RESULT_OK)
        finish()
    }

    private fun testConnection() {
        val url = etUrl.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(this, "请先填写 URL", Toast.LENGTH_SHORT).show()
            return
        }

        btnTestConnection.isEnabled = false
        btnTestConnection.text = "连接中..."
        tvTestResult.visibility = View.VISIBLE
        tvTestResult.text = "正在连接..."
        tvTestResult.setTextColor(getColor(R.color.text_secondary))

        val transportType = if (rbSse.isChecked) {
            ProviderManager.McpTransportType.SSE
        } else {
            ProviderManager.McpTransportType.STREAMABLE_HTTP
        }

        val headers = mutableMapOf<String, String>()
        val hName = etHeaderName.text.toString().trim()
        val hValue = etHeaderValue.text.toString().trim()
        if (hName.isNotBlank() && hValue.isNotBlank()) {
            headers[hName] = hValue
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = when (transportType) {
                    ProviderManager.McpTransportType.SSE ->
                        SseMcpClient(url, customHeaders = headers)
                    ProviderManager.McpTransportType.STREAMABLE_HTTP ->
                        HttpMcpClient(url, headers)
                }
                client.connect()
                val tools = client.listTools()
                client.close()

                withContext(Dispatchers.Main) {
                    tvTestResult.text = "✅ 连接成功，发现 ${tools.size} 个工具"
                    tvTestResult.setTextColor(getColor(R.color.status_connected))
                    btnTestConnection.isEnabled = true
                    btnTestConnection.text = "测试连接"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvTestResult.text = "❌ 连接失败: ${e.message}"
                    tvTestResult.setTextColor(getColor(R.color.status_failed))
                    btnTestConnection.isEnabled = true
                    btnTestConnection.text = "测试连接"
                }
            }
        }
    }
}
