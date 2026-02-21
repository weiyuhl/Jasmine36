package com.lhzkml.jasmine

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Agent 策略选择界面
 * 展示可选策略卡片 + 图策略子选项（工具调用模式、工具选择策略、ToolChoice）。
 */
class AgentStrategyActivity : AppCompatActivity() {

    private lateinit var cardSimpleLoop: LinearLayout
    private lateinit var cardGraphStrategy: LinearLayout
    private lateinit var tvSimpleLoopCheck: TextView
    private lateinit var tvGraphCheck: TextView
    private lateinit var tvStrategyDesc: TextView

    // 图策略子选项区域
    private lateinit var layoutGraphOptions: LinearLayout

    // 工具调用模式
    private lateinit var cardModeSequential: LinearLayout
    private lateinit var cardModeParallel: LinearLayout
    private lateinit var cardModeSingle: LinearLayout
    private lateinit var tvModeSeqCheck: TextView
    private lateinit var tvModeParCheck: TextView
    private lateinit var tvModeSingleCheck: TextView

    // 工具选择策略
    private lateinit var cardSelAll: LinearLayout
    private lateinit var cardSelNone: LinearLayout
    private lateinit var cardSelByName: LinearLayout
    private lateinit var cardSelAuto: LinearLayout
    private lateinit var tvSelAllCheck: TextView
    private lateinit var tvSelNoneCheck: TextView
    private lateinit var tvSelByNameCheck: TextView
    private lateinit var tvSelAutoCheck: TextView
    private lateinit var layoutSelByNameInput: LinearLayout
    private lateinit var etSelByNameTools: EditText
    private lateinit var layoutSelAutoInput: LinearLayout
    private lateinit var etSelAutoTaskDesc: EditText

    // ToolChoice
    private lateinit var cardTcDefault: LinearLayout
    private lateinit var cardTcAuto: LinearLayout
    private lateinit var cardTcRequired: LinearLayout
    private lateinit var cardTcNone: LinearLayout
    private lateinit var cardTcNamed: LinearLayout
    private lateinit var tvTcDefaultCheck: TextView
    private lateinit var tvTcAutoCheck: TextView
    private lateinit var tvTcRequiredCheck: TextView
    private lateinit var tvTcNoneCheck: TextView
    private lateinit var tvTcNamedCheck: TextView
    private lateinit var layoutTcNamedInput: LinearLayout
    private lateinit var etTcNamedTool: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_agent_strategy)

        findViewById<View>(R.id.btnBack).setOnClickListener { save(); finish() }

        // 策略卡片
        cardSimpleLoop = findViewById(R.id.cardSimpleLoop)
        cardGraphStrategy = findViewById(R.id.cardGraphStrategy)
        tvSimpleLoopCheck = findViewById(R.id.tvSimpleLoopCheck)
        tvGraphCheck = findViewById(R.id.tvGraphCheck)
        tvStrategyDesc = findViewById(R.id.tvStrategyDesc)

        // 图策略子选项
        layoutGraphOptions = findViewById(R.id.layoutGraphOptions)

        // 工具调用模式
        cardModeSequential = findViewById(R.id.cardModeSequential)
        cardModeParallel = findViewById(R.id.cardModeParallel)
        cardModeSingle = findViewById(R.id.cardModeSingle)
        tvModeSeqCheck = findViewById(R.id.tvModeSeqCheck)
        tvModeParCheck = findViewById(R.id.tvModeParCheck)
        tvModeSingleCheck = findViewById(R.id.tvModeSingleCheck)

        // 工具选择策略
        cardSelAll = findViewById(R.id.cardSelAll)
        cardSelNone = findViewById(R.id.cardSelNone)
        cardSelByName = findViewById(R.id.cardSelByName)
        cardSelAuto = findViewById(R.id.cardSelAuto)
        tvSelAllCheck = findViewById(R.id.tvSelAllCheck)
        tvSelNoneCheck = findViewById(R.id.tvSelNoneCheck)
        tvSelByNameCheck = findViewById(R.id.tvSelByNameCheck)
        tvSelAutoCheck = findViewById(R.id.tvSelAutoCheck)
        layoutSelByNameInput = findViewById(R.id.layoutSelByNameInput)
        etSelByNameTools = findViewById(R.id.etSelByNameTools)
        layoutSelAutoInput = findViewById(R.id.layoutSelAutoInput)
        etSelAutoTaskDesc = findViewById(R.id.etSelAutoTaskDesc)

        // ToolChoice
        cardTcDefault = findViewById(R.id.cardTcDefault)
        cardTcAuto = findViewById(R.id.cardTcAuto)
        cardTcRequired = findViewById(R.id.cardTcRequired)
        cardTcNone = findViewById(R.id.cardTcNone)
        cardTcNamed = findViewById(R.id.cardTcNamed)
        tvTcDefaultCheck = findViewById(R.id.tvTcDefaultCheck)
        tvTcAutoCheck = findViewById(R.id.tvTcAutoCheck)
        tvTcRequiredCheck = findViewById(R.id.tvTcRequiredCheck)
        tvTcNoneCheck = findViewById(R.id.tvTcNoneCheck)
        tvTcNamedCheck = findViewById(R.id.tvTcNamedCheck)
        layoutTcNamedInput = findViewById(R.id.layoutTcNamedInput)
        etTcNamedTool = findViewById(R.id.etTcNamedTool)

        // 加载已保存的值到输入框
        etSelByNameTools.setText(ProviderManager.getToolSelectionNames(this).joinToString(","))
        etSelAutoTaskDesc.setText(ProviderManager.getToolSelectionTaskDesc(this))
        etTcNamedTool.setText(ProviderManager.getToolChoiceNamedTool(this))

        // 策略卡片点击
        cardSimpleLoop.setOnClickListener {
            ProviderManager.setAgentStrategy(this, ProviderManager.AgentStrategyType.SIMPLE_LOOP)
            refreshAll()
        }
        cardGraphStrategy.setOnClickListener {
            ProviderManager.setAgentStrategy(this, ProviderManager.AgentStrategyType.SINGLE_RUN_GRAPH)
            refreshAll()
        }

        // 工具调用模式点击
        cardModeSequential.setOnClickListener {
            ProviderManager.setGraphToolCallMode(this, ProviderManager.GraphToolCallMode.SEQUENTIAL)
            refreshToolCallMode()
        }
        cardModeParallel.setOnClickListener {
            ProviderManager.setGraphToolCallMode(this, ProviderManager.GraphToolCallMode.PARALLEL)
            refreshToolCallMode()
        }
        cardModeSingle.setOnClickListener {
            ProviderManager.setGraphToolCallMode(this, ProviderManager.GraphToolCallMode.SINGLE_RUN_SEQUENTIAL)
            refreshToolCallMode()
        }

        // 工具选择策略点击
        cardSelAll.setOnClickListener {
            ProviderManager.setToolSelectionStrategy(this, ProviderManager.ToolSelectionStrategyType.ALL)
            refreshToolSelectionStrategy()
        }
        cardSelNone.setOnClickListener {
            ProviderManager.setToolSelectionStrategy(this, ProviderManager.ToolSelectionStrategyType.NONE)
            refreshToolSelectionStrategy()
        }
        cardSelByName.setOnClickListener {
            ProviderManager.setToolSelectionStrategy(this, ProviderManager.ToolSelectionStrategyType.BY_NAME)
            refreshToolSelectionStrategy()
        }
        cardSelAuto.setOnClickListener {
            ProviderManager.setToolSelectionStrategy(this, ProviderManager.ToolSelectionStrategyType.AUTO_SELECT_FOR_TASK)
            refreshToolSelectionStrategy()
        }

        // ToolChoice 点击
        cardTcDefault.setOnClickListener {
            ProviderManager.setToolChoiceMode(this, ProviderManager.ToolChoiceMode.DEFAULT)
            refreshToolChoice()
        }
        cardTcAuto.setOnClickListener {
            ProviderManager.setToolChoiceMode(this, ProviderManager.ToolChoiceMode.AUTO)
            refreshToolChoice()
        }
        cardTcRequired.setOnClickListener {
            ProviderManager.setToolChoiceMode(this, ProviderManager.ToolChoiceMode.REQUIRED)
            refreshToolChoice()
        }
        cardTcNone.setOnClickListener {
            ProviderManager.setToolChoiceMode(this, ProviderManager.ToolChoiceMode.NONE)
            refreshToolChoice()
        }
        cardTcNamed.setOnClickListener {
            ProviderManager.setToolChoiceMode(this, ProviderManager.ToolChoiceMode.NAMED)
            refreshToolChoice()
        }

        refreshAll()
    }

    private fun refreshAll() {
        refreshSelection()
        refreshGraphOptions()
        refreshToolCallMode()
        refreshToolSelectionStrategy()
        refreshToolChoice()
    }

    private fun refreshSelection() {
        val current = ProviderManager.getAgentStrategy(this)
        val isSimple = current == ProviderManager.AgentStrategyType.SIMPLE_LOOP
        val isGraph = current == ProviderManager.AgentStrategyType.SINGLE_RUN_GRAPH

        tvSimpleLoopCheck.visibility = if (isSimple) View.VISIBLE else View.GONE
        tvSimpleLoopCheck.text = "[*]"
        tvGraphCheck.visibility = if (isGraph) View.VISIBLE else View.GONE
        tvGraphCheck.text = "[*]"

        cardSimpleLoop.setBackgroundResource(
            if (isSimple) R.drawable.bg_strategy_card_selected else R.drawable.bg_strategy_card
        )
        cardGraphStrategy.setBackgroundResource(
            if (isGraph) R.drawable.bg_strategy_card_selected else R.drawable.bg_strategy_card
        )

        tvStrategyDesc.text = if (isSimple) {
            "简单循环模式使用 ToolExecutor 的 while 循环执行工具调用。\n" +
            "流程：发送消息 -> LLM 回复 -> 检查工具调用 -> 执行工具 -> 循环直到无工具调用。\n" +
            "适合简单场景，开销小。"
        } else {
            "图策略模式使用 GraphAgent 按节点图执行。\n" +
            "移植自 koog 的 singleRunStrategy，支持条件分支、子图嵌套。\n" +
            "可配置工具调用模式、工具选择策略和 ToolChoice。"
        }
    }

    private fun refreshGraphOptions() {
        val isGraph = ProviderManager.getAgentStrategy(this) == ProviderManager.AgentStrategyType.SINGLE_RUN_GRAPH
        layoutGraphOptions.visibility = if (isGraph) View.VISIBLE else View.GONE
    }

    private fun refreshToolCallMode() {
        val mode = ProviderManager.getGraphToolCallMode(this)
        val checks = mapOf(
            ProviderManager.GraphToolCallMode.SEQUENTIAL to tvModeSeqCheck,
            ProviderManager.GraphToolCallMode.PARALLEL to tvModeParCheck,
            ProviderManager.GraphToolCallMode.SINGLE_RUN_SEQUENTIAL to tvModeSingleCheck
        )
        val cards = mapOf(
            ProviderManager.GraphToolCallMode.SEQUENTIAL to cardModeSequential,
            ProviderManager.GraphToolCallMode.PARALLEL to cardModeParallel,
            ProviderManager.GraphToolCallMode.SINGLE_RUN_SEQUENTIAL to cardModeSingle
        )
        for ((m, tv) in checks) {
            tv.visibility = if (m == mode) View.VISIBLE else View.GONE
            tv.text = "[*]"
        }
        for ((m, card) in cards) {
            card.setBackgroundResource(
                if (m == mode) R.drawable.bg_strategy_card_selected else R.drawable.bg_strategy_card
            )
        }
    }

    private fun refreshToolSelectionStrategy() {
        val strategy = ProviderManager.getToolSelectionStrategy(this)
        val checks = mapOf(
            ProviderManager.ToolSelectionStrategyType.ALL to tvSelAllCheck,
            ProviderManager.ToolSelectionStrategyType.NONE to tvSelNoneCheck,
            ProviderManager.ToolSelectionStrategyType.BY_NAME to tvSelByNameCheck,
            ProviderManager.ToolSelectionStrategyType.AUTO_SELECT_FOR_TASK to tvSelAutoCheck
        )
        val cards = mapOf(
            ProviderManager.ToolSelectionStrategyType.ALL to cardSelAll,
            ProviderManager.ToolSelectionStrategyType.NONE to cardSelNone,
            ProviderManager.ToolSelectionStrategyType.BY_NAME to cardSelByName,
            ProviderManager.ToolSelectionStrategyType.AUTO_SELECT_FOR_TASK to cardSelAuto
        )
        for ((s, tv) in checks) {
            tv.visibility = if (s == strategy) View.VISIBLE else View.GONE
            tv.text = "[*]"
        }
        for ((s, card) in cards) {
            card.setBackgroundResource(
                if (s == strategy) R.drawable.bg_strategy_card_selected else R.drawable.bg_strategy_card
            )
        }
        layoutSelByNameInput.visibility =
            if (strategy == ProviderManager.ToolSelectionStrategyType.BY_NAME) View.VISIBLE else View.GONE
        layoutSelAutoInput.visibility =
            if (strategy == ProviderManager.ToolSelectionStrategyType.AUTO_SELECT_FOR_TASK) View.VISIBLE else View.GONE
    }

    private fun refreshToolChoice() {
        val mode = ProviderManager.getToolChoiceMode(this)
        val checks = mapOf(
            ProviderManager.ToolChoiceMode.DEFAULT to tvTcDefaultCheck,
            ProviderManager.ToolChoiceMode.AUTO to tvTcAutoCheck,
            ProviderManager.ToolChoiceMode.REQUIRED to tvTcRequiredCheck,
            ProviderManager.ToolChoiceMode.NONE to tvTcNoneCheck,
            ProviderManager.ToolChoiceMode.NAMED to tvTcNamedCheck
        )
        val cards = mapOf(
            ProviderManager.ToolChoiceMode.DEFAULT to cardTcDefault,
            ProviderManager.ToolChoiceMode.AUTO to cardTcAuto,
            ProviderManager.ToolChoiceMode.REQUIRED to cardTcRequired,
            ProviderManager.ToolChoiceMode.NONE to cardTcNone,
            ProviderManager.ToolChoiceMode.NAMED to cardTcNamed
        )
        for ((m, tv) in checks) {
            tv.visibility = if (m == mode) View.VISIBLE else View.GONE
            tv.text = "[*]"
        }
        for ((m, card) in cards) {
            card.setBackgroundResource(
                if (m == mode) R.drawable.bg_strategy_card_selected else R.drawable.bg_strategy_card
            )
        }
        layoutTcNamedInput.visibility =
            if (mode == ProviderManager.ToolChoiceMode.NAMED) View.VISIBLE else View.GONE
    }

    private fun save() {
        // 保存 ByName 工具列表
        val byNameText = etSelByNameTools.text.toString().trim()
        if (byNameText.isNotEmpty()) {
            ProviderManager.setToolSelectionNames(this, byNameText.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet())
        } else {
            ProviderManager.setToolSelectionNames(this, emptySet())
        }
        // 保存 AutoSelect 任务描述
        ProviderManager.setToolSelectionTaskDesc(this, etSelAutoTaskDesc.text.toString().trim())
        // 保存 Named 工具名
        ProviderManager.setToolChoiceNamedTool(this, etTcNamedTool.text.toString().trim())
    }

    override fun onBackPressed() {
        save()
        super.onBackPressed()
    }
}
