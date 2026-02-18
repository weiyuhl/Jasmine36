package com.lhzkml.jasmine

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Agent 策略选择界面
 * 展示可选策略卡片 + 图流程可视化，点击选择。
 */
class AgentStrategyActivity : AppCompatActivity() {

    private lateinit var cardSimpleLoop: LinearLayout
    private lateinit var cardGraphStrategy: LinearLayout
    private lateinit var tvSimpleLoopCheck: TextView
    private lateinit var tvGraphCheck: TextView
    private lateinit var tvStrategyDesc: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_agent_strategy)

        findViewById<android.view.View>(R.id.btnBack).setOnClickListener { finish() }

        cardSimpleLoop = findViewById(R.id.cardSimpleLoop)
        cardGraphStrategy = findViewById(R.id.cardGraphStrategy)
        tvSimpleLoopCheck = findViewById(R.id.tvSimpleLoopCheck)
        tvGraphCheck = findViewById(R.id.tvGraphCheck)
        tvStrategyDesc = findViewById(R.id.tvStrategyDesc)

        cardSimpleLoop.setOnClickListener {
            ProviderManager.setAgentStrategy(this, ProviderManager.AgentStrategyType.SIMPLE_LOOP)
            refreshSelection()
        }

        cardGraphStrategy.setOnClickListener {
            ProviderManager.setAgentStrategy(this, ProviderManager.AgentStrategyType.SINGLE_RUN_GRAPH)
            refreshSelection()
        }

        refreshSelection()
    }

    private fun refreshSelection() {
        val current = ProviderManager.getAgentStrategy(this)

        when (current) {
            ProviderManager.AgentStrategyType.SIMPLE_LOOP -> {
                cardSimpleLoop.setBackgroundResource(R.drawable.bg_strategy_card_selected)
                cardGraphStrategy.setBackgroundResource(R.drawable.bg_strategy_card)
                tvSimpleLoopCheck.visibility = android.view.View.VISIBLE
                tvGraphCheck.visibility = android.view.View.GONE
                tvStrategyDesc.text = buildString {
                    append("简单循环模式使用 ToolExecutor，通过 while 循环实现 Agent：\n\n")
                    append("1. 发送消息给 LLM（附带工具描述）\n")
                    append("2. LLM 返回 tool_calls → 执行工具 → 追加结果 → 回到 1\n")
                    append("3. LLM 返回文本 → 结束循环\n\n")
                    append("优点：实现简单，适合大多数场景\n")
                    append("缺点：无法条件分支、无子图嵌套")
                }
            }
            ProviderManager.AgentStrategyType.SINGLE_RUN_GRAPH -> {
                cardSimpleLoop.setBackgroundResource(R.drawable.bg_strategy_card)
                cardGraphStrategy.setBackgroundResource(R.drawable.bg_strategy_card_selected)
                tvSimpleLoopCheck.visibility = android.view.View.GONE
                tvGraphCheck.visibility = android.view.View.VISIBLE
                tvStrategyDesc.text = buildString {
                    append("图策略模式使用 GraphAgent，参考 koog 的 singleRunStrategy：\n\n")
                    append("执行流程按节点图结构运行：\n")
                    append("  Start → nodeLLMRequest → 条件分支\n")
                    append("    ├─ tool_calls → nodeExecuteTool → nodeSendToolResult → 循环\n")
                    append("    └─ assistant → Finish\n\n")
                    append("优点：支持条件分支、追踪每个节点、可扩展子图\n")
                    append("缺点：略有额外开销")
                }
            }
        }
    }
}
