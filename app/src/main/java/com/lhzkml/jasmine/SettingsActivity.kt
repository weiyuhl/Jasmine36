package com.lhzkml.jasmine

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.button.MaterialButton
import com.lhzkml.jasmine.core.conversation.storage.ConversationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var tvActiveProvider: TextView
    private lateinit var switchStream: SwitchCompat
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
    }

    override fun onResume() {
        super.onResume()
        refreshProviderStatus()
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
