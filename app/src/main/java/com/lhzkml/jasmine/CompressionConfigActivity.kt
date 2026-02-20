package com.lhzkml.jasmine

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class CompressionConfigActivity : AppCompatActivity() {

    private lateinit var switchEnabled: SwitchCompat
    private lateinit var layoutConfigContent: LinearLayout
    private lateinit var cardTokenBudget: LinearLayout
    private lateinit var cardWholeHistory: LinearLayout
    private lateinit var cardLastN: LinearLayout
    private lateinit var cardChunked: LinearLayout
    private lateinit var tvTokenBudgetCheck: TextView
    private lateinit var tvWholeHistoryCheck: TextView
    private lateinit var tvLastNCheck: TextView
    private lateinit var tvChunkedCheck: TextView

    private lateinit var layoutParams: LinearLayout
    private lateinit var tvParamsTitle: TextView
    private lateinit var layoutTokenBudgetParams: LinearLayout
    private lateinit var layoutLastNParams: LinearLayout
    private lateinit var layoutChunkedParams: LinearLayout
    private lateinit var etMaxTokens: EditText
    private lateinit var etThreshold: EditText
    private lateinit var etLastN: EditText
    private lateinit var etChunkSize: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compression_config)

        findViewById<View>(R.id.btnBack).setOnClickListener { save(); finish() }

        switchEnabled = findViewById(R.id.switchEnabled)
        layoutConfigContent = findViewById(R.id.layoutConfigContent)

        switchEnabled.isChecked = ProviderManager.isCompressionEnabled(this)
        layoutConfigContent.visibility = if (switchEnabled.isChecked) View.VISIBLE else View.GONE
        switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            ProviderManager.setCompressionEnabled(this, isChecked)
            layoutConfigContent.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        cardTokenBudget = findViewById(R.id.cardTokenBudget)
        cardWholeHistory = findViewById(R.id.cardWholeHistory)
        cardLastN = findViewById(R.id.cardLastN)
        cardChunked = findViewById(R.id.cardChunked)
        tvTokenBudgetCheck = findViewById(R.id.tvTokenBudgetCheck)
        tvWholeHistoryCheck = findViewById(R.id.tvWholeHistoryCheck)
        tvLastNCheck = findViewById(R.id.tvLastNCheck)
        tvChunkedCheck = findViewById(R.id.tvChunkedCheck)

        layoutParams = findViewById(R.id.layoutParams)
        tvParamsTitle = findViewById(R.id.tvParamsTitle)
        layoutTokenBudgetParams = findViewById(R.id.layoutTokenBudgetParams)
        layoutLastNParams = findViewById(R.id.layoutLastNParams)
        layoutChunkedParams = findViewById(R.id.layoutChunkedParams)
        etMaxTokens = findViewById(R.id.etMaxTokens)
        etThreshold = findViewById(R.id.etThreshold)
        etLastN = findViewById(R.id.etLastN)
        etChunkSize = findViewById(R.id.etChunkSize)

        // 加载当前参数值
        val maxTokens = ProviderManager.getCompressionMaxTokens(this)
        if (maxTokens > 0) etMaxTokens.setText(maxTokens.toString())
        etThreshold.setText(ProviderManager.getCompressionThreshold(this).toString())
        etLastN.setText(ProviderManager.getCompressionLastN(this).toString())
        etChunkSize.setText(ProviderManager.getCompressionChunkSize(this).toString())

        cardTokenBudget.setOnClickListener { selectStrategy(ProviderManager.CompressionStrategy.TOKEN_BUDGET) }
        cardWholeHistory.setOnClickListener { selectStrategy(ProviderManager.CompressionStrategy.WHOLE_HISTORY) }
        cardLastN.setOnClickListener { selectStrategy(ProviderManager.CompressionStrategy.LAST_N) }
        cardChunked.setOnClickListener { selectStrategy(ProviderManager.CompressionStrategy.CHUNKED) }

        refreshSelection()
    }

    private fun selectStrategy(strategy: ProviderManager.CompressionStrategy) {
        ProviderManager.setCompressionStrategy(this, strategy)
        refreshSelection()
    }

    private fun refreshSelection() {
        val current = ProviderManager.getCompressionStrategy(this)

        data class CardInfo(val card: LinearLayout, val check: TextView, val strategy: ProviderManager.CompressionStrategy)
        val cards = listOf(
            CardInfo(cardTokenBudget, tvTokenBudgetCheck, ProviderManager.CompressionStrategy.TOKEN_BUDGET),
            CardInfo(cardWholeHistory, tvWholeHistoryCheck, ProviderManager.CompressionStrategy.WHOLE_HISTORY),
            CardInfo(cardLastN, tvLastNCheck, ProviderManager.CompressionStrategy.LAST_N),
            CardInfo(cardChunked, tvChunkedCheck, ProviderManager.CompressionStrategy.CHUNKED)
        )
        for (info in cards) {
            val selected = info.strategy == current
            info.card.setBackgroundResource(if (selected) R.drawable.bg_strategy_card_selected else R.drawable.bg_strategy_card)
            info.check.visibility = if (selected) View.VISIBLE else View.GONE
        }

        // 显示对应参数区域
        layoutTokenBudgetParams.visibility = View.GONE
        layoutLastNParams.visibility = View.GONE
        layoutChunkedParams.visibility = View.GONE

        when (current) {
            ProviderManager.CompressionStrategy.TOKEN_BUDGET -> {
                layoutParams.visibility = View.VISIBLE
                tvParamsTitle.text = "Token 预算参数"
                layoutTokenBudgetParams.visibility = View.VISIBLE
            }
            ProviderManager.CompressionStrategy.LAST_N -> {
                layoutParams.visibility = View.VISIBLE
                tvParamsTitle.text = "保留最后 N 条参数"
                layoutLastNParams.visibility = View.VISIBLE
            }
            ProviderManager.CompressionStrategy.CHUNKED -> {
                layoutParams.visibility = View.VISIBLE
                tvParamsTitle.text = "分块压缩参数"
                layoutChunkedParams.visibility = View.VISIBLE
            }
            ProviderManager.CompressionStrategy.WHOLE_HISTORY -> {
                layoutParams.visibility = View.GONE
            }
        }
    }

    private fun save() {
        val strategy = ProviderManager.getCompressionStrategy(this)
        when (strategy) {
            ProviderManager.CompressionStrategy.TOKEN_BUDGET -> {
                val maxTokens = etMaxTokens.text.toString().trim().toIntOrNull() ?: 0
                val threshold = (etThreshold.text.toString().trim().toIntOrNull() ?: 75).coerceIn(1, 99)
                ProviderManager.setCompressionMaxTokens(this, maxTokens)
                ProviderManager.setCompressionThreshold(this, threshold)
            }
            ProviderManager.CompressionStrategy.LAST_N -> {
                val n = (etLastN.text.toString().trim().toIntOrNull() ?: 10).coerceAtLeast(2)
                ProviderManager.setCompressionLastN(this, n)
            }
            ProviderManager.CompressionStrategy.CHUNKED -> {
                val size = (etChunkSize.text.toString().trim().toIntOrNull() ?: 20).coerceAtLeast(5)
                ProviderManager.setCompressionChunkSize(this, size)
            }
            ProviderManager.CompressionStrategy.WHOLE_HISTORY -> { /* 无参数 */ }
        }
    }
}
