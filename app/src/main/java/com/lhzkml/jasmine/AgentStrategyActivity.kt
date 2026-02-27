package com.lhzkml.jasmine

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.lhzkml.jasmine.core.config.AgentStrategyType
import com.lhzkml.jasmine.core.config.GraphToolCallMode
import com.lhzkml.jasmine.core.config.ToolSelectionStrategyType
import com.lhzkml.jasmine.core.config.ToolChoiceMode

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

        val config = AppConfig.configRepo()

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
        etSelByNameTools.setText(config.getToolSelectionNames().joinToString(","))
        etSelAutoTaskDesc.setText(config.getToolSelectionTaskDesc())
        etTcNamedTool.setText(config.getToolChoiceNamedTool())

        // 策略卡片点击
        cardSimpleLoop.setOnClickListener {
            config.setAgentStrategy(AgentStrategyType.SIMPLE_LOOP)
            refreshAll()
        }
        cardGraphStrategy.setOnClickListener {
            config.setAgentStrategy(AgentStrategyType.SINGLE_RUN_GRAPH)
            refreshAll()
        }

        // 工具调用模式点击
        cardModeSequential.setOnClickListener {
            config.setGraphToolCallMode(GraphToolCallMode.SEQUENTIAL)
            refreshToolCallMode()
        }
        cardModeParallel.setOnClickListener {
            config.setGraphToolCallMode(GraphToolCallMode.PARALLEL)
            refreshToolCallMode()
        }
        cardModeSingle.setOnClickListener {
            config.setGraphToolCallMode(GraphToolCallMode.SINGLE_RUN_SEQUENTIAL)
            refreshToolCallMode()
        }

        // 工具选择策略点击
        cardSelAll.setOnClickListener {
            config.setToolSelectionStrategy(ToolSelectionStrategyType.ALL)
            refreshToolSelectionStrategy()
        }
        cardSelNone.setOnClickListener {
            config.setToolSelectionStrategy(ToolSelectionStrategyType.NONE)
            refreshToolSelectionStrategy()
        }
        cardSelByName.setOnClickListener {
            config.setToolSelectionStrategy(ToolSelectionStrategyType.BY_NAME)
            refreshToolSelectionStrategy()
        }
        cardSelAuto.setOnClickListener {
            config.setToolSelectionStrategy(ToolSelectionStrategyType.AUTO_SELECT_FOR_TASK)
            refreshToolSelectionStrategy()
        }

        // ToolChoice 点击
        cardTcDefault.setOnClickListener {
            config.setToolChoiceMode(ToolChoiceMode.DEFAULT)
            refreshToolChoice()
        }
        cardTcAuto.setOnClickListener {
            config.setToolChoiceMode(ToolChoiceMode.AUTO)
            refreshToolChoice()
        }
        cardTcRequired.setOnClickListener {
            config.setToolChoiceMode(ToolChoiceMode.REQUIRED)
            refreshToolChoice()
        }
        cardTcNone.setOnClickListener {
            config.setToolChoiceMode(ToolChoiceMode.NONE)
            refreshToolChoice()
        }
        cardTcNamed.setOnClickListener {
            config.setToolChoiceMode(ToolChoiceMode.NAMED)
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
        val config = AppConfig.configRepo()
        val current = config.getAgentStrategy()
        val isSimple = current == AgentStrategyType.SIMPLE_LOOP
        val isGraph = current == AgentStrategyType.SINGLE_RUN_GRAPH

        tvSimpleLoopCheck.visibility = if (isSimple) View.VISIBLE else View.GONE
        tvGraphCheck.visibility = if (isGraph) View.VISIBLE else View.GONE

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
        val config = AppConfig.configRepo()
        val isGraph = config.getAgentStrategy() == AgentStrategyType.SINGLE_RUN_GRAPH
        layoutGraphOptions.visibility = if (isGraph) View.VISIBLE else View.GONE
    }

    private fun refreshToolCallMode() {
        val config = AppConfig.configRepo()
        val mode = config.getGraphToolCallMode()
        val checks = mapOf(
            GraphToolCallMode.SEQUENTIAL to tvModeSeqCheck,
            GraphToolCallMode.PARALLEL to tvModeParCheck,
            GraphToolCallMode.SINGLE_RUN_SEQUENTIAL to tvModeSingleCheck
        )
        val cards = mapOf(
            GraphToolCallMode.SEQUENTIAL to cardModeSequential,
            GraphToolCallMode.PARALLEL to cardModeParallel,
            GraphToolCallMode.SINGLE_RUN_SEQUENTIAL to cardModeSingle
        )
        for ((m, tv) in checks) {
            tv.visibility = if (m == mode) View.VISIBLE else View.GONE
        }
        for ((m, card) in cards) {
            card.setBackgroundResource(
                if (m == mode) R.drawable.bg_strategy_card_selected else R.drawable.bg_strategy_card
            )
        }
    }

    private fun refreshToolSelectionStrategy() {
        val config = AppConfig.configRepo()
        val strategy = config.getToolSelectionStrategy()
        val checks = mapOf(
            ToolSelectionStrategyType.ALL to tvSelAllCheck,
            ToolSelectionStrategyType.NONE to tvSelNoneCheck,
            ToolSelectionStrategyType.BY_NAME to tvSelByNameCheck,
            ToolSelectionStrategyType.AUTO_SELECT_FOR_TASK to tvSelAutoCheck
        )
        val cards = mapOf(
            ToolSelectionStrategyType.ALL to cardSelAll,
            ToolSelectionStrategyType.NONE to cardSelNone,
            ToolSelectionStrategyType.BY_NAME to cardSelByName,
            ToolSelectionStrategyType.AUTO_SELECT_FOR_TASK to cardSelAuto
        )
        for ((s, tv) in checks) {
            tv.visibility = if (s == strategy) View.VISIBLE else View.GONE
        }
        for ((s, card) in cards) {
            card.setBackgroundResource(
                if (s == strategy) R.drawable.bg_strategy_card_selected else R.drawable.bg_strategy_card
            )
        }
        layoutSelByNameInput.visibility =
            if (strategy == ToolSelectionStrategyType.BY_NAME) View.VISIBLE else View.GONE
        layoutSelAutoInput.visibility =
            if (strategy == ToolSelectionStrategyType.AUTO_SELECT_FOR_TASK) View.VISIBLE else View.GONE
    }

    private fun refreshToolChoice() {
        val config = AppConfig.configRepo()
        val mode = config.getToolChoiceMode()
        val checks = mapOf(
            ToolChoiceMode.DEFAULT to tvTcDefaultCheck,
            ToolChoiceMode.AUTO to tvTcAutoCheck,
            ToolChoiceMode.REQUIRED to tvTcRequiredCheck,
            ToolChoiceMode.NONE to tvTcNoneCheck,
            ToolChoiceMode.NAMED to tvTcNamedCheck
        )
        val cards = mapOf(
            ToolChoiceMode.DEFAULT to cardTcDefault,
            ToolChoiceMode.AUTO to cardTcAuto,
            ToolChoiceMode.REQUIRED to cardTcRequired,
            ToolChoiceMode.NONE to cardTcNone,
            ToolChoiceMode.NAMED to cardTcNamed
        )
        for ((m, tv) in checks) {
            tv.visibility = if (m == mode) View.VISIBLE else View.GONE
        }
        for ((m, card) in cards) {
            card.setBackgroundResource(
                if (m == mode) R.drawable.bg_strategy_card_selected else R.drawable.bg_strategy_card
            )
        }
        layoutTcNamedInput.visibility =
            if (mode == ToolChoiceMode.NAMED) View.VISIBLE else View.GONE
    }

    private fun save() {
        val config = AppConfig.configRepo()
        // 保存 ByName 工具列表
        val byNameText = etSelByNameTools.text.toString().trim()
        if (byNameText.isNotEmpty()) {
            config.setToolSelectionNames(byNameText.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet())
        } else {
            config.setToolSelectionNames(emptySet())
        }
        // 保存 AutoSelect 任务描述
        config.setToolSelectionTaskDesc(etSelAutoTaskDesc.text.toString().trim())
        // 保存 Named 工具名
        config.setToolChoiceNamedTool(etTcNamedTool.text.toString().trim())
    }

    override fun onBackPressed() {
        save()
        super.onBackPressed()
    }
}
