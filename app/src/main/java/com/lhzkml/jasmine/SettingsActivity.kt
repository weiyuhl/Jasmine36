package com.lhzkml.jasmine

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.lhzkml.jasmine.core.prompt.executor.ApiType
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.button.MaterialButton
import com.lhzkml.jasmine.core.conversation.storage.ConversationRepository
import com.lhzkml.jasmine.core.prompt.llm.SystemPromptManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var tvActiveProvider: TextView
    private lateinit var switchTools: SwitchCompat
    private lateinit var layoutAgentToolPreset: LinearLayout
    private lateinit var tvAgentToolPreset: TextView
    private lateinit var layoutAgentStrategy: LinearLayout
    private lateinit var tvAgentStrategy: TextView
    private lateinit var tvCompressionInfo: TextView
    private lateinit var tvTraceInfo: TextView
    private lateinit var tvPlannerInfo: TextView
    private lateinit var tvSnapshotInfo: TextView
    private lateinit var tvEventHandlerInfo: TextView
    private lateinit var tvMcpInfo: TextView
    private lateinit var tvTimeoutInfo: TextView
    private lateinit var tvMaxTokens: TextView
    private lateinit var tvSystemPrompt: TextView
    private lateinit var tvPromptTokens: TextView
    private lateinit var tvCompletionTokens: TextView
    private lateinit var tvTotalTokens: TextView
    private lateinit var conversationRepo: ConversationRepository

    // 采样参数
    private lateinit var seekTemperature: SeekBar
    private lateinit var tvTemperatureValue: TextView
    private lateinit var seekTopP: SeekBar
    private lateinit var tvTopPValue: TextView
    private lateinit var seekTopK: SeekBar
    private lateinit var tvTopKValue: TextView
    private lateinit var layoutTopK: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        conversationRepo = ConversationRepository(this)

        tvActiveProvider = findViewById(R.id.tvActiveProvider)
        tvMaxTokens = findViewById(R.id.tvMaxTokens)
        tvSystemPrompt = findViewById(R.id.tvSystemPrompt)
        tvPromptTokens = findViewById(R.id.tvPromptTokens)
        tvCompletionTokens = findViewById(R.id.tvCompletionTokens)
        tvTotalTokens = findViewById(R.id.tvTotalTokens)

        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<LinearLayout>(R.id.layoutProviders).setOnClickListener {
            startActivity(Intent(this, ProviderListActivity::class.java))
        }

        // 工具调用开关
        switchTools = findViewById(R.id.switchTools)

        switchTools.isChecked = ProviderManager.isToolsEnabled(this)
        switchTools.setOnCheckedChangeListener { _, isChecked ->
            ProviderManager.setToolsEnabled(this, isChecked)
            layoutAgentToolPreset.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
            layoutAgentStrategy.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
        }

        // Agent 工具预设入口
        layoutAgentToolPreset = findViewById(R.id.layoutAgentToolPreset)
        tvAgentToolPreset = findViewById(R.id.tvAgentToolPreset)
        layoutAgentToolPreset.visibility = if (switchTools.isChecked) android.view.View.VISIBLE else android.view.View.GONE
        layoutAgentToolPreset.setOnClickListener {
            startActivity(Intent(this, ToolConfigActivity::class.java).apply {
                putExtra(ToolConfigActivity.EXTRA_AGENT_PRESET, true)
            })
        }

        // Agent 策略选择
        layoutAgentStrategy = findViewById(R.id.layoutAgentStrategy)
        tvAgentStrategy = findViewById(R.id.tvAgentStrategy)
        layoutAgentStrategy.visibility = if (switchTools.isChecked) android.view.View.VISIBLE else android.view.View.GONE
        layoutAgentStrategy.setOnClickListener {
            startActivity(Intent(this, AgentStrategyActivity::class.java))
        }

        // MCP 工具入口
        tvMcpInfo = findViewById(R.id.tvMcpInfo)
        findViewById<LinearLayout>(R.id.layoutMcpEntry).setOnClickListener {
            startActivity(Intent(this, McpServerActivity::class.java))
        }

        // Shell 命令策略入口
        findViewById<LinearLayout>(R.id.layoutShellPolicyEntry).setOnClickListener {
            startActivity(Intent(this, ShellPolicyActivity::class.java))
        }

        // 智能上下文压缩入口
        tvCompressionInfo = findViewById(R.id.tvCompressionInfo)
        findViewById<LinearLayout>(R.id.layoutCompressionEntry).setOnClickListener {
            startActivity(Intent(this, CompressionConfigActivity::class.java))
        }

        // 执行追踪入口
        tvTraceInfo = findViewById(R.id.tvTraceInfo)
        findViewById<LinearLayout>(R.id.layoutTraceEntry).setOnClickListener {
            startActivity(Intent(this, TraceConfigActivity::class.java))
        }

        // 任务规划入口
        tvPlannerInfo = findViewById(R.id.tvPlannerInfo)
        findViewById<LinearLayout>(R.id.layoutPlannerEntry).setOnClickListener {
            startActivity(Intent(this, PlannerConfigActivity::class.java))
        }

        // 执行快照入口
        tvSnapshotInfo = findViewById(R.id.tvSnapshotInfo)
        findViewById<LinearLayout>(R.id.layoutSnapshotEntry).setOnClickListener {
            startActivity(Intent(this, SnapshotConfigActivity::class.java))
        }

        // 事件处理器入口
        tvEventHandlerInfo = findViewById(R.id.tvEventHandlerInfo)
        findViewById<LinearLayout>(R.id.layoutEventHandlerEntry).setOnClickListener {
            startActivity(Intent(this, EventHandlerConfigActivity::class.java))
        }

        // 系统提示词编辑
        findViewById<LinearLayout>(R.id.layoutSystemPrompt).setOnClickListener {
            showSystemPromptDialog()
        }

        // 超时与续传配置
        tvTimeoutInfo = findViewById(R.id.tvTimeoutInfo)
        findViewById<LinearLayout>(R.id.layoutTimeoutEntry).setOnClickListener {
            startActivity(Intent(this, TimeoutConfigActivity::class.java))
        }

        // 最大回复 Token 数
        findViewById<LinearLayout>(R.id.layoutMaxTokens).setOnClickListener {
            showMaxTokensDialog()
        }

        // 采样参数
        seekTemperature = findViewById(R.id.seekTemperature)
        tvTemperatureValue = findViewById(R.id.tvTemperatureValue)
        seekTopP = findViewById(R.id.seekTopP)
        tvTopPValue = findViewById(R.id.tvTopPValue)
        seekTopK = findViewById(R.id.seekTopK)
        tvTopKValue = findViewById(R.id.tvTopKValue)
        layoutTopK = findViewById(R.id.layoutTopK)
        setupSamplingParams()
    }

    override fun onResume() {
        super.onResume()
        refreshProviderStatus()
        refreshSystemPrompt()
        refreshMaxTokens()
        refreshUsageStats()
        refreshTopKVisibility()
        refreshAgentToolPreset()
        refreshAgentStrategy()
        refreshCompressionInfo()
        refreshMcpInfo()
        refreshTraceInfo()
        refreshPlannerInfo()
        refreshSnapshotInfo()
        refreshEventHandlerInfo()
        refreshShellPolicyInfo()
        refreshTimeoutInfo()
    }

    private fun refreshTopKVisibility() {
        val config = ProviderManager.getActiveConfig(this)
        // top_k 仅 Claude 和 Gemini 支持
        val supportsTopK = config?.apiType == ApiType.CLAUDE || config?.apiType == ApiType.GEMINI
        layoutTopK.visibility = if (supportsTopK) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun refreshProviderStatus() {
        val config = ProviderManager.getActiveConfig(this)
        if (config != null) {
            val provider = ProviderManager.providers.find { it.id == config.providerId }
            tvActiveProvider.text = provider?.name ?: config.providerId
        } else {
            tvActiveProvider.text = "未配置"
        }
    }

    private fun refreshSystemPrompt() {
        val prompt = ProviderManager.getDefaultSystemPrompt(this)
        tvSystemPrompt.text = if (prompt.length > 30) prompt.substring(0, 30) + "..." else prompt
    }

    private fun refreshMaxTokens() {
        val maxTokens = ProviderManager.getMaxTokens(this)
        tvMaxTokens.text = if (maxTokens > 0) "$maxTokens" else "不限制"
    }

    private fun setupSamplingParams() {
        // Temperature: seekBar 0~200 → 0.0~2.0, 0 位置表示"默认"
        val savedTemp = ProviderManager.getTemperature(this)
        if (savedTemp >= 0f) {
            seekTemperature.progress = (savedTemp * 100).toInt()
            tvTemperatureValue.text = String.format("%.2f", savedTemp)
        } else {
            seekTemperature.progress = 0
            tvTemperatureValue.text = "默认"
        }
        seekTemperature.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                if (progress == 0) {
                    tvTemperatureValue.text = "默认"
                    ProviderManager.setTemperature(this@SettingsActivity, -1f)
                } else {
                    val value = progress / 100f
                    tvTemperatureValue.text = String.format("%.2f", value)
                    ProviderManager.setTemperature(this@SettingsActivity, value)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Top P: seekBar 0~100 → 0.0~1.0, 0 位置表示"默认"
        val savedTopP = ProviderManager.getTopP(this)
        if (savedTopP >= 0f) {
            seekTopP.progress = (savedTopP * 100).toInt()
            tvTopPValue.text = String.format("%.2f", savedTopP)
        } else {
            seekTopP.progress = 0
            tvTopPValue.text = "默认"
        }
        seekTopP.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                if (progress == 0) {
                    tvTopPValue.text = "默认"
                    ProviderManager.setTopP(this@SettingsActivity, -1f)
                } else {
                    val value = progress / 100f
                    tvTopPValue.text = String.format("%.2f", value)
                    ProviderManager.setTopP(this@SettingsActivity, value)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Top K: seekBar 0~100, 0 位置表示"默认"
        val savedTopK = ProviderManager.getTopK(this)
        if (savedTopK >= 0) {
            seekTopK.progress = savedTopK
            tvTopKValue.text = savedTopK.toString()
        } else {
            seekTopK.progress = 0
            tvTopKValue.text = "默认"
        }
        seekTopK.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                if (progress == 0) {
                    tvTopKValue.text = "默认"
                    ProviderManager.setTopK(this@SettingsActivity, -1)
                } else {
                    tvTopKValue.text = progress.toString()
                    ProviderManager.setTopK(this@SettingsActivity, progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun showMaxTokensDialog() {
        val current = ProviderManager.getMaxTokens(this)
        val editText = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "0 表示不限制"
            if (current > 0) setText(current.toString())
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("最大回复 Token 数")
            .setMessage("设置每条 AI 回复的最大 token 数量，0 或留空表示不限制。常用值：512、1024、2048、4096")
            .setView(editText)
            .setPositiveButton("保存") { _, _ ->
                val value = editText.text.toString().trim().toIntOrNull() ?: 0
                ProviderManager.setMaxTokens(this, value)
                refreshMaxTokens()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showSystemPromptDialog() {
        val currentPrompt = ProviderManager.getDefaultSystemPrompt(this)

        val editText = EditText(this).apply {
            setText(currentPrompt)
            setSelection(currentPrompt.length)
            minLines = 3
            maxLines = 8
            setPadding(48, 32, 48, 32)
        }

        // 构建预设选项
        val presetNames = SystemPromptManager.presets.map { it.name }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("系统提示词")
            .setView(editText)
            .setPositiveButton("保存") { _, _ ->
                val newPrompt = editText.text.toString().trim()
                if (newPrompt.isNotEmpty()) {
                    ProviderManager.setDefaultSystemPrompt(this, newPrompt)
                    refreshSystemPrompt()
                }
            }
            .setNeutralButton("预设模板") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("选择预设模板")
                    .setItems(presetNames) { _, which ->
                        val preset = SystemPromptManager.presets[which]
                        ProviderManager.setDefaultSystemPrompt(this, preset.prompt)
                        refreshSystemPrompt()
                    }
                    .show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun refreshUsageStats() {
        CoroutineScope(Dispatchers.IO).launch {
            val stats = conversationRepo.getTotalUsage()
            withContext(Dispatchers.Main) {
                tvPromptTokens.text = formatNumber(stats.promptTokens)
                tvCompletionTokens.text = formatNumber(stats.completionTokens)
                tvTotalTokens.text = formatNumber(stats.totalTokens)
            }
        }
    }

    private fun refreshAgentToolPreset() {
        val preset = ProviderManager.getAgentToolPreset(this)
        tvAgentToolPreset.text = if (preset.isEmpty()) "全部工具已启用（默认）" else "已启用 ${preset.size} 个工具"
    }

    private fun refreshAgentStrategy() {
        val strategy = ProviderManager.getAgentStrategy(this)
        tvAgentStrategy.text = when (strategy) {
            ProviderManager.AgentStrategyType.SIMPLE_LOOP -> "简单循环（ToolExecutor）"
            ProviderManager.AgentStrategyType.SINGLE_RUN_GRAPH -> "图策略（GraphAgent）"
        }
    }

    private fun refreshCompressionInfo() {
        if (!ProviderManager.isCompressionEnabled(this)) {
            tvCompressionInfo.text = "已关闭"
            return
        }
        val strategy = ProviderManager.getCompressionStrategy(this)
        val info = when (strategy) {
            com.lhzkml.jasmine.core.prompt.llm.CompressionStrategyType.TOKEN_BUDGET -> {
                val maxTokens = ProviderManager.getCompressionMaxTokens(this)
                val threshold = ProviderManager.getCompressionThreshold(this)
                val tokenStr = if (maxTokens > 0) "${maxTokens}" else "跟随模型"
                "Token 预算 · $tokenStr · 阈值 ${threshold}%"
            }
            com.lhzkml.jasmine.core.prompt.llm.CompressionStrategyType.WHOLE_HISTORY -> "整体压缩"
            com.lhzkml.jasmine.core.prompt.llm.CompressionStrategyType.LAST_N -> {
                val n = ProviderManager.getCompressionLastN(this)
                "保留最后 ${n} 条"
            }
            com.lhzkml.jasmine.core.prompt.llm.CompressionStrategyType.CHUNKED -> {
                val size = ProviderManager.getCompressionChunkSize(this)
                "分块压缩 · 每块 ${size} 条"
            }
        }
        tvCompressionInfo.text = info
    }

    private fun refreshMcpInfo() {
        if (!ProviderManager.isMcpEnabled(this)) {
            tvMcpInfo.text = "已关闭"
            return
        }
        val servers = ProviderManager.getMcpServers(this)
        val enabledCount = servers.count { it.enabled }
        tvMcpInfo.text = if (servers.isEmpty()) "已开启 · 未配置服务器" else "已配置 ${servers.size} 个 · 启用 $enabledCount 个"
    }

    // ========== 追踪配置 ==========

    private fun refreshTraceInfo() {
        if (!ProviderManager.isTraceEnabled(this)) {
            tvTraceInfo.text = "已关闭"
            return
        }
        val file = ProviderManager.isTraceFileEnabled(this)
        val filter = ProviderManager.getTraceEventFilter(this)
        val outputParts = mutableListOf<String>()
        outputParts.add("Android Log")
        if (file) outputParts.add("文件输出")
        val filterStr = if (filter.isEmpty()) "全部事件" else "${filter.size} 类事件"
        tvTraceInfo.text = "${outputParts.joinToString(" · ")} · $filterStr"
    }

    // ========== 规划配置 ==========

    private fun refreshPlannerInfo() {
        if (!ProviderManager.isPlannerEnabled(this)) {
            tvPlannerInfo.text = "已关闭"
            return
        }
        val maxIter = ProviderManager.getPlannerMaxIterations(this)
        val critic = ProviderManager.isPlannerCriticEnabled(this)
        val criticStr = if (critic) "Critic 评估" else "无 Critic"
        tvPlannerInfo.text = "迭代 $maxIter 次 · $criticStr"
    }

    // ========== 快照配置 ==========

    private fun refreshSnapshotInfo() {
        if (!ProviderManager.isSnapshotEnabled(this)) {
            tvSnapshotInfo.text = "已关闭"
            return
        }
        val storage = ProviderManager.getSnapshotStorage(this)
        val auto = ProviderManager.isSnapshotAutoCheckpoint(this)
        val rollback = ProviderManager.getSnapshotRollbackStrategy(this)
        val storageName = when (storage) {
            ProviderManager.SnapshotStorage.MEMORY -> "内存存储"
            ProviderManager.SnapshotStorage.FILE -> "文件存储"
        }
        val autoStr = if (auto) "自动检查点" else "手动检查点"
        val rollbackName = when (rollback) {
            com.lhzkml.jasmine.core.agent.tools.snapshot.RollbackStrategy.RESTART_FROM_NODE -> "从节点重启"
            com.lhzkml.jasmine.core.agent.tools.snapshot.RollbackStrategy.SKIP_NODE -> "跳过节点"
            com.lhzkml.jasmine.core.agent.tools.snapshot.RollbackStrategy.USE_DEFAULT_OUTPUT -> "默认输出"
        }
        tvSnapshotInfo.text = "$storageName · $autoStr · $rollbackName"
    }

    // ========== 事件处理器配置 ==========

    private fun refreshEventHandlerInfo() {
        if (!ProviderManager.isEventHandlerEnabled(this)) {
            tvEventHandlerInfo.text = "已关闭"
            return
        }
        val filter = ProviderManager.getEventHandlerFilter(this)
        tvEventHandlerInfo.text = if (filter.isEmpty()) "全部事件" else "${filter.size} 类事件"
    }

    private fun refreshShellPolicyInfo() {
        val policy = ProviderManager.getShellPolicy(this)
        findViewById<TextView>(R.id.tvShellPolicyInfo).text = when (policy) {
            com.lhzkml.jasmine.core.agent.tools.ShellPolicy.MANUAL -> "手动确认"
            com.lhzkml.jasmine.core.agent.tools.ShellPolicy.BLACKLIST -> "黑名单模式"
            com.lhzkml.jasmine.core.agent.tools.ShellPolicy.WHITELIST -> "白名单模式"
        }
    }

    private fun refreshTimeoutInfo() {
        val reqTimeout = ProviderManager.getRequestTimeout(this)
        val socketTimeout = ProviderManager.getSocketTimeout(this)
        val connectTimeout = ProviderManager.getConnectTimeout(this)
        val resumeEnabled = ProviderManager.isStreamResumeEnabled(this)

        val parts = mutableListOf<String>()
        if (reqTimeout > 0) parts.add("请求 ${reqTimeout}s")
        if (socketTimeout > 0) parts.add("读取 ${socketTimeout}s")
        if (connectTimeout > 0) parts.add("连接 ${connectTimeout}s")

        val timeoutStr = if (parts.isEmpty()) "默认" else parts.joinToString(" · ")
        val resumeStr = if (resumeEnabled) "续传开启" else "续传关闭"
        tvTimeoutInfo.text = "$timeoutStr · $resumeStr"
    }

    private fun formatNumber(n: Int): String {
        return when {
            n >= 1_000_000 -> String.format("%.1fM tokens", n / 1_000_000.0)
            n >= 1_000 -> String.format("%.1fK tokens", n / 1_000.0)
            else -> "$n tokens"
        }
    }
}
