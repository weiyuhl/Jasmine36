package com.lhzkml.jasmine

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
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
    }

    override fun onResume() {
        super.onResume()
        refreshProviderStatus()
        refreshSystemPrompt()
        refreshMaxTokens()
        refreshUsageStats()
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
