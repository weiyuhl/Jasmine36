package com.lhzkml.jasmine

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.lhzkml.jasmine.core.config.ToolCatalog

/**
 * 工具管理界面
 * 独立 Activity，展示所有可用工具的启用/禁用状态。
 * 支持两种模式：
 * - 普通模式（默认）：管理 enabled_tools
 * - Agent 预设模式（EXTRA_AGENT_PRESET=true）：管理 agent_tool_preset
 */
class ToolConfigActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_AGENT_PRESET = "agent_preset"
    }

    private val allTools = ToolCatalog.allTools.map { it.id to it.description }

    private var isAgentPreset = false

    private lateinit var cbSelectAll: CheckBox
    private lateinit var layoutToolList: LinearLayout
    private lateinit var etBrightDataKey: EditText

    /** 工具名 -> 勾选状态 */
    private val toolStates = mutableMapOf<String, Boolean>()
    private val toolCheckBoxes = mutableMapOf<String, CheckBox>()
    private var ignoreCallbacks = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tool_config)

        isAgentPreset = intent.getBooleanExtra(EXTRA_AGENT_PRESET, false)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnSave).setOnClickListener { save() }

        // Agent 预设模式下修改标题
        if (isAgentPreset) {
            findViewById<TextView>(android.R.id.text1)?.text = "Agent 工具预设"
        }

        cbSelectAll = findViewById(R.id.cbSelectAll)
        layoutToolList = findViewById(R.id.layoutToolList)
        etBrightDataKey = findViewById(R.id.etBrightDataKey)

        val config = AppConfig.configRepo()
        
        // 加载 BrightData Key
        val currentKey = config.getBrightDataKey()
        if (currentKey.isNotEmpty()) etBrightDataKey.setText(currentKey)

        // 加载工具状态：根据模式读取不同的存储
        val enabledTools = if (isAgentPreset) {
            config.getAgentToolPreset()
        } else {
            config.getEnabledTools()
        }
        val allEnabled = enabledTools.isEmpty()

        for (tool in allTools) {
            toolStates[tool.first] = allEnabled || tool.first in enabledTools
        }

        buildToolList()
        updateSelectAllCheckbox()

        cbSelectAll.isClickable = false
        cbSelectAll.isFocusable = false
        findViewById<LinearLayout>(R.id.layoutSelectAll).setOnClickListener {
            val newState = !cbSelectAll.isChecked
            ignoreCallbacks = true
            for (tool in allTools) {
                toolStates[tool.first] = newState
                toolCheckBoxes[tool.first]?.isChecked = newState
            }
            cbSelectAll.isChecked = newState
            ignoreCallbacks = false
        }
    }

    private fun buildToolList() {
        val inflater = LayoutInflater.from(this)
        for ((index, tool) in allTools.withIndex()) {
            val itemView = inflater.inflate(R.layout.item_tool_config, layoutToolList, false)
            val tvName = itemView.findViewById<TextView>(R.id.tvToolName)
            val tvDesc = itemView.findViewById<TextView>(R.id.tvToolDesc)
            val cb = itemView.findViewById<CheckBox>(R.id.cbTool)

            tvName.text = tool.first
            tvDesc.text = tool.second
            cb.isChecked = toolStates[tool.first] == true

            cb.isClickable = false
            cb.isFocusable = false

            toolCheckBoxes[tool.first] = cb

            itemView.setOnClickListener {
                val current = toolStates[tool.first] == true
                val newVal = !current
                toolStates[tool.first] = newVal
                cb.isChecked = newVal
                updateSelectAllCheckbox()
            }

            layoutToolList.addView(itemView)

            if (index < allTools.size - 1) {
                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    ).apply { marginStart = 20.dp; marginEnd = 20.dp }
                    setBackgroundColor(getColor(R.color.divider))
                }
                layoutToolList.addView(divider)
            }
        }
    }

    private fun updateSelectAllCheckbox() {
        if (ignoreCallbacks) return
        val allChecked = allTools.all { toolStates[it.first] == true }
        cbSelectAll.isChecked = allChecked
    }

    private fun save() {
        val selected = mutableSetOf<String>()
        for (tool in allTools) {
            if (toolStates[tool.first] == true) selected.add(tool.first)
        }
        val toSave = if (selected.size == allTools.size) emptySet() else selected

        val config = AppConfig.configRepo()
        if (isAgentPreset) {
            config.setAgentToolPreset(toSave)
        } else {
            config.setEnabledTools(toSave)
        }
        // BrightData Key 两种模式都保存
        config.setBrightDataKey(etBrightDataKey.text.toString().trim())

        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
