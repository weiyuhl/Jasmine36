package com.lhzkml.jasmine

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
    private lateinit var switchStream: SwitchCompat
    private lateinit var switchTools: SwitchCompat
    private lateinit var switchCompression: SwitchCompat
    private lateinit var layoutToolConfig: LinearLayout
    private lateinit var layoutCompressionConfig: LinearLayout
    private lateinit var tvToolCount: TextView
    private lateinit var tvCompressionInfo: TextView
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
        switchStream = findViewById(R.id.switchStream)
        tvMaxTokens = findViewById(R.id.tvMaxTokens)
        tvSystemPrompt = findViewById(R.id.tvSystemPrompt)
        tvPromptTokens = findViewById(R.id.tvPromptTokens)
        tvCompletionTokens = findViewById(R.id.tvCompletionTokens)
        tvTotalTokens = findViewById(R.id.tvTotalTokens)

        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<LinearLayout>(R.id.layoutProviders).setOnClickListener {
            startActivity(Intent(this, ProviderListActivity::class.java))
        }

        // 流式输出开关
        switchStream.isChecked = ProviderManager.isStreamEnabled(this)
        switchStream.setOnCheckedChangeListener { _, isChecked ->
            ProviderManager.setStreamEnabled(this, isChecked)
        }

        // 工具调用开关
        switchTools = findViewById(R.id.switchTools)
        layoutToolConfig = findViewById(R.id.layoutToolConfig)
        tvToolCount = findViewById(R.id.tvToolCount)

        switchTools.isChecked = ProviderManager.isToolsEnabled(this)
        layoutToolConfig.visibility = if (switchTools.isChecked) android.view.View.VISIBLE else android.view.View.GONE
        switchTools.setOnCheckedChangeListener { _, isChecked ->
            ProviderManager.setToolsEnabled(this, isChecked)
            layoutToolConfig.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
        }

        layoutToolConfig.setOnClickListener {
            showToolConfigDialog()
        }

        // 智能上下文压缩开关
        switchCompression = findViewById(R.id.switchCompression)
        layoutCompressionConfig = findViewById(R.id.layoutCompressionConfig)
        tvCompressionInfo = findViewById(R.id.tvCompressionInfo)

        switchCompression.isChecked = ProviderManager.isCompressionEnabled(this)
        layoutCompressionConfig.visibility = if (switchCompression.isChecked) android.view.View.VISIBLE else android.view.View.GONE
        switchCompression.setOnCheckedChangeListener { _, isChecked ->
            ProviderManager.setCompressionEnabled(this, isChecked)
            layoutCompressionConfig.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
        }

        layoutCompressionConfig.setOnClickListener {
            showCompressionConfigDialog()
        }

        // 系统提示词编辑
        findViewById<LinearLayout>(R.id.layoutSystemPrompt).setOnClickListener {
            showSystemPromptDialog()
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
        refreshToolCount()
        refreshCompressionInfo()
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

    private fun refreshToolCount() {
        val enabled = ProviderManager.getEnabledTools(this)
        tvToolCount.text = if (enabled.isEmpty()) "全部工具已启用" else "已启用 ${enabled.size} 个工具"
    }

    private fun showToolConfigDialog() {
        // 所有可用工具名称和描述
        val allTools = listOf(
            "calculator" to "计算器（四则运算）",
            "get_current_time" to "获取当前时间",
            "read_file" to "读取文件",
            "write_file" to "写入文件",
            "edit_file" to "编辑文件",
            "list_directory" to "列出目录",
            "search_by_regex" to "正则搜索",
            "execute_shell_command" to "执行命令",
            "web_search" to "网络搜索",
            "web_scrape" to "网页抓取"
        )

        val enabledTools = ProviderManager.getEnabledTools(this).toMutableSet()
        val names = allTools.map { "${it.first} — ${it.second}" }.toTypedArray()
        // 空集合表示全部启用
        val checked = if (enabledTools.isEmpty()) {
            BooleanArray(allTools.size) { true }
        } else {
            BooleanArray(allTools.size) { allTools[it].first in enabledTools }
        }

        AlertDialog.Builder(this)
            .setTitle("选择启用的工具")
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("保存") { _, _ ->
                val selected = mutableSetOf<String>()
                for (i in allTools.indices) {
                    if (checked[i]) selected.add(allTools[i].first)
                }
                // 全选时存空集合（表示全部启用）
                if (selected.size == allTools.size) {
                    ProviderManager.setEnabledTools(this, emptySet())
                } else {
                    ProviderManager.setEnabledTools(this, selected)
                }
                refreshToolCount()
            }
            .setNeutralButton("BrightData Key") { _, _ ->
                showBrightDataKeyDialog()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showBrightDataKeyDialog() {
        val current = ProviderManager.getBrightDataKey(this)
        val editText = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            hint = "BrightData API Key（网络搜索需要）"
            if (current.isNotEmpty()) setText(current)
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle("BrightData API Key")
            .setMessage("网络搜索和网页抓取工具需要 BrightData SERP API Key")
            .setView(editText)
            .setPositiveButton("保存") { _, _ ->
                ProviderManager.setBrightDataKey(this, editText.text.toString().trim())
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun refreshCompressionInfo() {
        val strategy = ProviderManager.getCompressionStrategy(this)
        val info = when (strategy) {
            ProviderManager.CompressionStrategy.TOKEN_BUDGET -> {
                val maxTokens = ProviderManager.getCompressionMaxTokens(this)
                val threshold = ProviderManager.getCompressionThreshold(this)
                val tokenStr = if (maxTokens > 0) "${maxTokens}" else "跟随模型"
                "Token 预算 · $tokenStr · 阈值 ${threshold}%"
            }
            ProviderManager.CompressionStrategy.WHOLE_HISTORY -> "整体压缩"
            ProviderManager.CompressionStrategy.LAST_N -> {
                val n = ProviderManager.getCompressionLastN(this)
                "保留最后 ${n} 条"
            }
            ProviderManager.CompressionStrategy.CHUNKED -> {
                val size = ProviderManager.getCompressionChunkSize(this)
                "分块压缩 · 每块 ${size} 条"
            }
        }
        tvCompressionInfo.text = info
    }

    private fun showCompressionConfigDialog() {
        val strategies = ProviderManager.CompressionStrategy.entries.toTypedArray()
        val names = arrayOf(
            "Token 预算（推荐）— 超过阈值自动压缩",
            "整体压缩 — 整个历史生成摘要",
            "保留最后 N 条 — 只压缩最近消息",
            "分块压缩 — 按固定大小分块"
        )
        val current = ProviderManager.getCompressionStrategy(this)
        val checkedIndex = strategies.indexOf(current)

        AlertDialog.Builder(this)
            .setTitle("选择压缩策略")
            .setSingleChoiceItems(names, checkedIndex) { dialog, which ->
                val selected = strategies[which]
                ProviderManager.setCompressionStrategy(this, selected)
                dialog.dismiss()
                // 选完策略后弹出参数配置
                showStrategyParamsDialog(selected)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showStrategyParamsDialog(strategy: ProviderManager.CompressionStrategy) {
        when (strategy) {
            ProviderManager.CompressionStrategy.TOKEN_BUDGET -> {
                val layout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(48, 32, 48, 16)
                }

                val etMaxTokens = EditText(this).apply {
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER
                    hint = "0 表示跟随模型上下文窗口"
                    val current = ProviderManager.getCompressionMaxTokens(this@SettingsActivity)
                    if (current > 0) setText(current.toString())
                }
                layout.addView(TextView(this).apply {
                    text = "最大 Token 数"
                    textSize = 14f
                })
                layout.addView(etMaxTokens)

                val etThreshold = EditText(this).apply {
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER
                    hint = "1~99，默认 75"
                    setText(ProviderManager.getCompressionThreshold(this@SettingsActivity).toString())
                }
                layout.addView(TextView(this).apply {
                    text = "\n触发阈值（%）"
                    textSize = 14f
                })
                layout.addView(etThreshold)

                AlertDialog.Builder(this)
                    .setTitle("Token 预算参数")
                    .setView(layout)
                    .setPositiveButton("保存") { _, _ ->
                        val maxTokens = etMaxTokens.text.toString().trim().toIntOrNull() ?: 0
                        val threshold = (etThreshold.text.toString().trim().toIntOrNull() ?: 75).coerceIn(1, 99)
                        ProviderManager.setCompressionMaxTokens(this, maxTokens)
                        ProviderManager.setCompressionThreshold(this, threshold)
                        refreshCompressionInfo()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            ProviderManager.CompressionStrategy.LAST_N -> {
                val editText = EditText(this).apply {
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER
                    hint = "保留的消息数，默认 10"
                    setText(ProviderManager.getCompressionLastN(this@SettingsActivity).toString())
                    setPadding(48, 32, 48, 32)
                }
                AlertDialog.Builder(this)
                    .setTitle("保留最后 N 条消息")
                    .setMessage("压缩时只保留最近的 N 条消息用于生成摘要")
                    .setView(editText)
                    .setPositiveButton("保存") { _, _ ->
                        val n = (editText.text.toString().trim().toIntOrNull() ?: 10).coerceAtLeast(2)
                        ProviderManager.setCompressionLastN(this, n)
                        refreshCompressionInfo()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            ProviderManager.CompressionStrategy.CHUNKED -> {
                val editText = EditText(this).apply {
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER
                    hint = "每块消息数，默认 20"
                    setText(ProviderManager.getCompressionChunkSize(this@SettingsActivity).toString())
                    setPadding(48, 32, 48, 32)
                }
                AlertDialog.Builder(this)
                    .setTitle("分块大小")
                    .setMessage("将历史按固定大小分块，每块独立生成摘要")
                    .setView(editText)
                    .setPositiveButton("保存") { _, _ ->
                        val size = (editText.text.toString().trim().toIntOrNull() ?: 20).coerceAtLeast(5)
                        ProviderManager.setCompressionChunkSize(this, size)
                        refreshCompressionInfo()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            ProviderManager.CompressionStrategy.WHOLE_HISTORY -> {
                refreshCompressionInfo()
            }
        }
    }

    private fun formatNumber(n: Int): String {
        return when {
            n >= 1_000_000 -> String.format("%.1fM tokens", n / 1_000_000.0)
            n >= 1_000 -> String.format("%.1fK tokens", n / 1_000.0)
            else -> "$n tokens"
        }
    }
}
