package com.lhzkml.jasmine

import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.lhzkml.jasmine.core.agent.observe.event.EventCategory

class EventHandlerConfigActivity : AppCompatActivity() {

    private lateinit var switchEnabled: SwitchCompat
    private lateinit var layoutConfigContent: LinearLayout
    private lateinit var tvSummary: TextView
    private lateinit var switches: Map<EventCategory, SwitchCompat>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_handler_config)

        val config = AppConfig.configRepo()

        findViewById<android.view.View>(R.id.btnBack).setOnClickListener { finish() }

        switchEnabled = findViewById(R.id.switchEnabled)
        layoutConfigContent = findViewById(R.id.layoutConfigContent)

        switchEnabled.isChecked = config.isEventHandlerEnabled()
        layoutConfigContent.visibility = if (switchEnabled.isChecked) View.VISIBLE else View.GONE
        switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            config.setEventHandlerEnabled(isChecked)
            layoutConfigContent.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        tvSummary = findViewById(R.id.tvSummary)

        switches = mapOf(
            EventCategory.AGENT to findViewById(R.id.switchEvAgent),
            EventCategory.TOOL to findViewById(R.id.switchEvTool),
            EventCategory.LLM to findViewById(R.id.switchEvLLM),
            EventCategory.STRATEGY to findViewById(R.id.switchEvStrategy),
            EventCategory.NODE to findViewById(R.id.switchEvNode),
            EventCategory.SUBGRAPH to findViewById(R.id.switchEvSubgraph),
            EventCategory.STREAMING to findViewById(R.id.switchEvStream)
        )

        val current = config.getEventHandlerFilter()
        for (entry in switches) {
            entry.value.isChecked = current.isEmpty() || entry.key in current
        }

        val listener = CompoundButton.OnCheckedChangeListener { _, _ ->
            saveFilter()
            refreshSummary()
        }
        for (entry in switches) {
            entry.value.setOnCheckedChangeListener(listener)
        }

        refreshSummary()
    }

    private fun saveFilter() {
        val config = AppConfig.configRepo()
        val checked = mutableSetOf<EventCategory>()
        for (entry in switches) {
            if (entry.value.isChecked) checked.add(entry.key)
        }
        val selected = if (checked.size == switches.size || checked.isEmpty()) {
            emptySet<EventCategory>()
        } else {
            checked.toSet()
        }
        config.setEventHandlerFilter(selected)
    }

    private fun refreshSummary() {
        val config = AppConfig.configRepo()
        val filter = config.getEventHandlerFilter()
        tvSummary.text = if (filter.isEmpty()) "监听全部事件" else "监听 ${filter.size} 类事件"
    }
}
