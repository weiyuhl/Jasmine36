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

/**
 * 工具管理界面
 * 独立 Activity，展示所有可用工具的启用/禁用状态。
 * 点击"保存"按钮才写入 SharedPreferences。
 */
class ToolConfigActivity : AppCompatActivity() {

    private val allTools = listOf(
        "calculator" to "计算器（四则运算/科学计算/进制转换/单位转换/统计）",
        "get_current_time" to "获取当前时间",
        "file_tools" to "文件操作（读写/编辑/搜索/压缩等 17 个工具）",
        "execute_shell_command" to "执行命令",
        "web_search" to "网络搜索/抓取（需要 BrightData Key）",
        "fetch_url" to "URL 抓取（本地直接请求，HTML/纯文本/JSON）",
        "dex_editor" to "DEX/APK 编辑（Smali 编辑/Manifest/资源/ARSC 等 38 个工具）",
        "attempt_completion" to "显式完成任务（Agent 模式）"
    )


    private lateinit var cbSelectAll: CheckBox
    private lateinit var layoutToolList: LinearLayout
    private lateinit var etBrightDataKey: EditText

    /** 工具名 → 勾选状态（纯数据，不依赖 View 状态） */
    private val toolStates = mutableMapOf<String, Boolean>()
    private val toolCheckBoxes = mutableMapOf<String, CheckBox>()
    private var ignoreCallbacks = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tool_config)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnSave).setOnClickListener { save() }

        cbSelectAll = findViewById(R.id.cbSelectAll)
        layoutToolList = findViewById(R.id.layoutToolList)
        etBrightDataKey = findViewById(R.id.etBrightDataKey)

        // 加载 BrightData Key
        val currentKey = ProviderManager.getBrightDataKey(this)
        if (currentKey.isNotEmpty()) etBrightDataKey.setText(currentKey)

        // 加载当前启用的工具
        val enabledTools = ProviderManager.getEnabledTools(this)
        val allEnabled = enabledTools.isEmpty()

        // 初始化状态：未配置过时全部开启
        for (tool in allTools) {
            toolStates[tool.first] = allEnabled || tool.first in enabledTools
        }

        // 构建工具列表 UI
        buildToolList()

        // 同步全选状态
        updateSelectAllCheckbox()

        // 全选 checkbox — 禁止直接点击，只通过行点击
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

            // 禁止 checkbox 自身响应点击，只通过行点击
            cb.isClickable = false
            cb.isFocusable = false

            toolCheckBoxes[tool.first] = cb

            // 点击整行切换
            itemView.setOnClickListener {
                val current = toolStates[tool.first] == true
                val newVal = !current
                toolStates[tool.first] = newVal
                cb.isChecked = newVal
                updateSelectAllCheckbox()
            }

            layoutToolList.addView(itemView)

            // 分隔线（最后一个不加）
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
        // 全选时存空集合（表示全部启用），否则存选中的
        if (selected.size == allTools.size) {
            ProviderManager.setEnabledTools(this, emptySet())
        } else {
            ProviderManager.setEnabledTools(this, selected)
        }
        // 保存 BrightData Key
        ProviderManager.setBrightDataKey(this, etBrightDataKey.text.toString().trim())

        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
