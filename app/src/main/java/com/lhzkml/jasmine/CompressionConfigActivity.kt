package com.lhzkml.jasmine

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.lhzkml.jasmine.core.prompt.llm.CompressionStrategyType

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

        val config = AppConfig.configRepo()

        findViewById<View>(R.id.btnBack).setOnClickListener { save(); finish() }

        switchEnabled = findViewById(R.id.switchEnabled)
        layoutConfigContent = findViewById(R.id.layoutConfigContent)

        switchEnabled.isChecked = config.isCompressionEnabled()
        layoutConfigContent.visibility = if (switchEnabled.isChecked) View.VISIBLE else View.GONE
        switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            config.setCompressionEnabled(isChecked)
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
        val maxTokens = config.getCompressionMaxTokens()
        if (maxTokens > 0) etMaxTokens.setText(maxTokens.toString())
        etThreshold.setText(config.getCompressionThreshold().toString())
        etLastN.setText(config.getCompressionLastN().toString())
        etChunkSize.setText(config.getCompressionChunkSize().toString())

        cardTokenBudget.setOnClickListener { selectStrategy(CompressionStrategyType.TOKEN_BUDGET) }
        cardWholeHistory.setOnClickListener { selectStrategy(CompressionStrategyType.WHOLE_HISTORY) }
        cardLastN.setOnClickListener { selectStrategy(CompressionStrategyType.LAST_N) }
        cardChunked.setOnClickListener { selectStrategy(CompressionStrategyType.CHUNKED) }

        refreshSelection()
    }

    private fun selectStrategy(strategy: CompressionStrategyType) {
        val config = AppConfig.configRepo()
        config.setCompressionStrategy(strategy)
        refreshSelection()
    }

    private fun refreshSelection() {
        val config = AppConfig.configRepo()
        val current = config.getCompressionStrategy()

        data class CardInfo(val card: LinearLayout, val check: TextView, val strategy: CompressionStrategyType)
        val cards = listOf(
            CardInfo(cardTokenBudget, tvTokenBudgetCheck, CompressionStrategyType.TOKEN_BUDGET),
            CardInfo(cardWholeHistory, tvWholeHistoryCheck, CompressionStrategyType.WHOLE_HISTORY),
            CardInfo(cardLastN, tvLastNCheck, CompressionStrategyType.LAST_N),
            CardInfo(cardChunked, tvChunkedCheck, CompressionStrategyType.CHUNKED)
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
            CompressionStrategyType.TOKEN_BUDGET -> {
                layoutParams.visibility = View.VISIBLE
                tvParamsTitle.text = "Token 预算参数"
                layoutTokenBudgetParams.visibility = View.VISIBLE
            }
            CompressionStrategyType.LAST_N -> {
                layoutParams.visibility = View.VISIBLE
                tvParamsTitle.text = "保留最后 N 条参数"
                layoutLastNParams.visibility = View.VISIBLE
            }
            CompressionStrategyType.CHUNKED -> {
                layoutParams.visibility = View.VISIBLE
                tvParamsTitle.text = "分块压缩参数"
                layoutChunkedParams.visibility = View.VISIBLE
            }
            CompressionStrategyType.WHOLE_HISTORY -> {
                layoutParams.visibility = View.GONE
            }
        }
    }

    private fun save() {
        val config = AppConfig.configRepo()
        val strategy = config.getCompressionStrategy()
        when (strategy) {
            CompressionStrategyType.TOKEN_BUDGET -> {
                val maxTokens = etMaxTokens.text.toString().trim().toIntOrNull() ?: 0
                val threshold = (etThreshold.text.toString().trim().toIntOrNull() ?: 75).coerceIn(1, 99)
                config.setCompressionMaxTokens(maxTokens)
                config.setCompressionThreshold(threshold)
            }
            CompressionStrategyType.LAST_N -> {
                val n = (etLastN.text.toString().trim().toIntOrNull() ?: 10).coerceAtLeast(2)
                config.setCompressionLastN(n)
            }
            CompressionStrategyType.CHUNKED -> {
                val size = (etChunkSize.text.toString().trim().toIntOrNull() ?: 20).coerceAtLeast(5)
                config.setCompressionChunkSize(size)
            }
            CompressionStrategyType.WHOLE_HISTORY -> { /* 无参数 */ }
        }
    }
}
