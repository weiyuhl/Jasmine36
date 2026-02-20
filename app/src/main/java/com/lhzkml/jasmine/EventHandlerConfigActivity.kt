package com.lhzkml.jasmine

import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.lhzkml.jasmine.core.agent.tools.event.EventCategory

class EventHandlerConfigActivity : AppCompatActivity() {

    private lateinit var switchEnabled: SwitchCompat
    private lateinit var layoutConfigContent: LinearLayout
    private lateinit var tvSummary: TextView
    private lateinit var switches: Map<EventCategory, SwitchCompat>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_handler_config)

        findViewById<android.view.View>(R.id.btnBack).setOnClickListener { finish() }

        switchEnabled = findViewById(R.id.switchEnabled)
        layoutConfigContent = findViewById(R.id.layoutConfigContent)

        switchEnabled.isChecked = ProviderManager.isEventHandlerEnabled(this)
        layoutConfigContent.visibility = if (switchEnabled.isChecked) View.VISIBLE else View.GONE
        switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            ProviderManager.setEventHandlerEnabled(this, isChecked)
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

        val current = ProviderManager.getEventHandlerFilter(this)
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
        val checked = mutableSetOf<EventCategory>()
        for (entry in switches) {
            if (entry.value.isChecked) checked.add(entry.key)
        }
        val selected = if (checked.size == switches.size || checked.isEmpty()) {
            emptySet<EventCategory>()
        } else {
            checked.toSet()
        }
        ProviderManager.setEventHandlerFilter(this, selected)
    }

    private fun refreshSummary() {
        val filter = ProviderManager.getEventHandlerFilter(this)
        tvSummary.text = if (filter.isEmpty()) "监听全部事件" else "监听 ${filter.size} 类事件"
    }
}
