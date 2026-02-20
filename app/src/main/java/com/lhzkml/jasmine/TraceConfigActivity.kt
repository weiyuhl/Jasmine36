package com.lhzkml.jasmine

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import android.widget.TextView
import com.lhzkml.jasmine.core.agent.tools.trace.TraceEventCategory

/**
 * 追踪配置界面
 * 追踪系统专注于数据记录（Android Log + 文件），不负责 UI 显示。
 * UI 实时通知由事件处理器(EventHandler)负责。
 */
class TraceConfigActivity : AppCompatActivity() {

    private lateinit var switchEnabled: SwitchCompat
    private lateinit var layoutConfigContent: LinearLayout
    private lateinit var switchFileOutput: SwitchCompat
    private lateinit var tvFileOutputPath: TextView
    private lateinit var tvConfigSummary: TextView

    // 事件过滤开关
    private lateinit var switchFilterAgent: SwitchCompat
    private lateinit var switchFilterLLM: SwitchCompat
    private lateinit var switchFilterTool: SwitchCompat
    private lateinit var switchFilterStrategy: SwitchCompat
    private lateinit var switchFilterNode: SwitchCompat
    private lateinit var switchFilterSubgraph: SwitchCompat
    private lateinit var switchFilterCompression: SwitchCompat

    private val categoryMap by lazy {
        mapOf(
            TraceEventCategory.AGENT to ::switchFilterAgent,
            TraceEventCategory.LLM to ::switchFilterLLM,
            TraceEventCategory.TOOL to ::switchFilterTool,
            TraceEventCategory.STRATEGY to ::switchFilterStrategy,
            TraceEventCategory.NODE to ::switchFilterNode,
            TraceEventCategory.SUBGRAPH to ::switchFilterSubgraph,
            TraceEventCategory.COMPRESSION to ::switchFilterCompression
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trace_config)

        findViewById<android.view.View>(R.id.btnBack).setOnClickListener { finish() }

        switchEnabled = findViewById(R.id.switchEnabled)
        layoutConfigContent = findViewById(R.id.layoutConfigContent)

        switchEnabled.isChecked = ProviderManager.isTraceEnabled(this)
        layoutConfigContent.visibility = if (switchEnabled.isChecked) View.VISIBLE else View.GONE
        switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            ProviderManager.setTraceEnabled(this, isChecked)
            layoutConfigContent.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        switchFileOutput = findViewById(R.id.switchFileOutput)
        tvFileOutputPath = findViewById(R.id.tvFileOutputPath)
        tvConfigSummary = findViewById(R.id.tvConfigSummary)

        switchFilterAgent = findViewById(R.id.switchFilterAgent)
        switchFilterLLM = findViewById(R.id.switchFilterLLM)
        switchFilterTool = findViewById(R.id.switchFilterTool)
        switchFilterStrategy = findViewById(R.id.switchFilterStrategy)
        switchFilterNode = findViewById(R.id.switchFilterNode)
        switchFilterSubgraph = findViewById(R.id.switchFilterSubgraph)
        switchFilterCompression = findViewById(R.id.switchFilterCompression)

        // 隐藏内联显示开关（追踪不再负责 UI 显示，由 EventHandler 负责）
        // 内联显示卡片已从布局中移除

        // 加载当前状态
        switchFileOutput.isChecked = ProviderManager.isTraceFileEnabled(this)

        val currentFilter = ProviderManager.getTraceEventFilter(this)
        for ((cat, switchGetter) in categoryMap) {
            switchGetter.get().isChecked = currentFilter.isEmpty() || cat in currentFilter
        }

        // 输出方式监听
        switchFileOutput.setOnCheckedChangeListener { _, isChecked ->
            ProviderManager.setTraceFileEnabled(this, isChecked)
            updateFileOutputPath()
            refreshSummary()
        }

        // 事件过滤监听
        val filterChangeListener = { _: android.widget.CompoundButton, _: Boolean ->
            saveEventFilter()
            refreshSummary()
        }
        for ((_, switchGetter) in categoryMap) {
            switchGetter.get().setOnCheckedChangeListener(filterChangeListener)
        }

        updateFileOutputPath()
        refreshSummary()
    }

    private fun updateFileOutputPath() {
        if (switchFileOutput.isChecked) {
            val traceDir = getExternalFilesDir("traces")
            tvFileOutputPath.text = "保存到: ${traceDir?.absolutePath ?: "未知路径"}"
        } else {
            tvFileOutputPath.text = "保存追踪日志到本地文件"
        }
    }

    private fun saveEventFilter() {
        val allChecked = categoryMap.all { (_, switchGetter) -> switchGetter.get().isChecked }
        val noneChecked = categoryMap.none { (_, switchGetter) -> switchGetter.get().isChecked }

        val selected = if (allChecked || noneChecked) {
            // 全选或全不选 = 空集合（追踪全部）
            emptySet()
        } else {
            categoryMap.filter { (_, switchGetter) -> switchGetter.get().isChecked }.keys
        }
        ProviderManager.setTraceEventFilter(this, selected)
    }

    private fun refreshSummary() {
        val file = switchFileOutput.isChecked
        val filter = ProviderManager.getTraceEventFilter(this)

        val sb = StringBuilder()

        // 输出方式
        sb.append("输出: Android Log（默认）")
        if (file) sb.append(" + 文件")

        // 事件过滤
        sb.append("\n过滤: ")
        if (filter.isEmpty()) {
            sb.append("全部事件")
        } else {
            val names = filter.map { cat ->
                when (cat) {
                    TraceEventCategory.AGENT -> "Agent"
                    TraceEventCategory.LLM -> "LLM"
                    TraceEventCategory.TOOL -> "Tool"
                    TraceEventCategory.STRATEGY -> "Strategy"
                    TraceEventCategory.NODE -> "Node"
                    TraceEventCategory.SUBGRAPH -> "Subgraph"
                    TraceEventCategory.COMPRESSION -> "Compression"
                }
            }
            sb.append(names.joinToString(", "))
        }

        tvConfigSummary.text = sb.toString()
    }
}
