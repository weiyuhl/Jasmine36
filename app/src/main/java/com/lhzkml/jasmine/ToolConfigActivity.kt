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
        "calculator" to "计算器（四则运算）",
        "get_current_time" to "获取当前时间",
        "read_file" to "读取文件",
        "write_file" to "写入文件",
        "edit_file" to "编辑文件",
        "list_directory" to "列出目录",
        "search_by_regex" to "正则搜索",
        "execute_shell_command" to "执行命令",
        "web_search" to "网络搜索",
        "web_scrape" to "网页抓取"
    )

    private lateinit var cbSelectAll: CheckBox
    private lateinit var layoutToolList: LinearLayout
    private lateinit var etBrightDataKey: EditText
    private val checkBoxes = mutableListOf<Pair<String, CheckBox>>()
    private var updatingSelectAll = false

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

        // 动态创建工具列表
        val inflater = LayoutInflater.from(this)
        for ((index, tool) in allTools.withIndex()) {
            val itemView = inflater.inflate(R.layout.item_tool_config, layoutToolList, false)
            val tvName = itemView.findViewById<TextView>(R.id.tvToolName)
            val tvDesc = itemView.findViewById<TextView>(R.id.tvToolDesc)
            val cb = itemView.findViewById<CheckBox>(R.id.cbTool)

            tvName.text = tool.first
            tvDesc.text = tool.second
            cb.isChecked = allEnabled || tool.first in enabledTools

            // checkbox 变化时只同步"全部启用"状态，不保存
            cb.setOnCheckedChangeListener { _, _ ->
                if (!updatingSelectAll) syncSelectAll()
            }

            // 点击整行时切换 checkbox（禁止 checkbox 自身的点击穿透）
            cb.isClickable = false
            itemView.setOnClickListener { cb.isChecked = !cb.isChecked }

            checkBoxes.add(tool.first to cb)
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

        // 全选 checkbox
        syncSelectAll()
        cbSelectAll.setOnCheckedChangeListener { _, isChecked ->
            if (!updatingSelectAll) {
                updatingSelectAll = true
                for ((_, cb) in checkBoxes) {
                    cb.isChecked = isChecked
                }
                updatingSelectAll = false
            }
        }

        findViewById<LinearLayout>(R.id.layoutSelectAll).setOnClickListener {
            cbSelectAll.isChecked = !cbSelectAll.isChecked
        }
    }

    private fun save() {
        val selected = mutableSetOf<String>()
        for ((name, cb) in checkBoxes) {
            if (cb.isChecked) selected.add(name)
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

    private fun syncSelectAll() {
        val allChecked = checkBoxes.all { it.second.isChecked }
        updatingSelectAll = true
        cbSelectAll.isChecked = allChecked
        updatingSelectAll = false
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
