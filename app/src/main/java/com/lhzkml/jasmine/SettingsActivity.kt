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

    private fun formatNumber(n: Int): String {
        return when {
            n >= 1_000_000 -> String.format("%.1fM tokens", n / 1_000_000.0)
            n >= 1_000 -> String.format("%.1fK tokens", n / 1_000.0)
            else -> "$n tokens"
        }
    }
}
