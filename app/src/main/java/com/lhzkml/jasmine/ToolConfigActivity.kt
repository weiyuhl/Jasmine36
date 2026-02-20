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
        "read_file" to "读取文件",
        "write_file" to "写入文件",
        "edit_file" to "编辑文件",
        "append_file" to "追加文件内容",
        "create_file" to "创建文件（覆盖保护）",
        "insert_content" to "插入内容到指定行",
        "replace_in_file" to "查找替换文件内容",
        "delete_file" to "删除文件",
        "move_file" to "移动文件",
        "copy_file" to "复制文件",
        "rename_file" to "重命名文件",
        "file_info" to "文件信息",
        "list_directory" to "列出目录",
        "find_files" to "按名称搜索文件",
        "search_by_regex" to "正则搜索",
        "create_directory" to "创建目录",
        "compress_files" to "文件压缩（ZIP）",
        "execute_shell_command" to "执行命令",
        "web_search" to "网络搜索",
        "web_scrape" to "网页抓取",
        "fetch_url_as_html" to "抓取网页返回 HTML",
        "fetch_url_as_text" to "抓取网页返回纯文本",
        "fetch_url_as_json" to "抓取网页返回 JSON",
        // DEX/APK 编辑工具
        "dex_open_apk" to "打开 APK 查看 DEX 列表",
        "dex_open" to "打开 DEX 文件编辑",
        "dex_save" to "保存 DEX 到 APK",
        "dex_close" to "关闭 DEX 编辑会话",
        "dex_list_sessions" to "列出 DEX 编辑会话",
        "dex_list_classes" to "列出 DEX 类",
        "dex_search" to "搜索 DEX 内容",
        "dex_get_class" to "获取类 Smali 代码",
        "dex_modify_class" to "修改类 Smali 代码",
        "dex_add_class" to "添加新类",
        "dex_delete_class" to "删除类",
        "dex_rename_class" to "重命名类",
        "dex_get_method" to "获取方法 Smali",
        "dex_modify_method" to "修改方法 Smali",
        "dex_list_methods" to "列出类的方法",
        "dex_list_fields" to "列出类的字段",
        "dex_list_strings" to "列出 DEX 字符串池",
        "dex_find_method_xrefs" to "查找方法交叉引用",
        "dex_find_field_xrefs" to "查找字段交叉引用",
        "dex_smali_to_java" to "Smali 转 Java 伪代码",
        "apk_list_files" to "列出 APK 文件",
        "apk_read_file" to "读取 APK 内文件",
        "apk_search_text" to "APK 内文本搜索",
        "apk_delete_file" to "删除 APK 内文件",
        "apk_add_file" to "添加文件到 APK",
        "apk_list_resources" to "列出 APK 资源",
        "apk_get_manifest" to "获取 AndroidManifest.xml",
        "apk_modify_manifest" to "修改 AndroidManifest.xml",
        "apk_patch_manifest" to "快速修改 Manifest 属性",
        "apk_replace_in_manifest" to "替换 Manifest 字符串",
        "apk_get_resource" to "获取资源文件内容",
        "apk_modify_resource" to "修改资源文件",
        "apk_search_arsc_strings" to "搜索 ARSC 字符串",
        "apk_search_arsc_resources" to "搜索 ARSC 资源",
        "apk_parse_manifest_cpp" to "解析二进制 Manifest（结构化）",
        "apk_search_manifest_cpp" to "搜索二进制 Manifest 属性",
        "apk_parse_arsc_cpp" to "解析 resources.arsc 概要",
        // Agent 控制工具
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

        // 初始化状态
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
