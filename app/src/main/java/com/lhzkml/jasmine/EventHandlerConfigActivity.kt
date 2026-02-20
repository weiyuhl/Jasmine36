package com.lhzkml.jasmine

import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class EventHandlerConfigActivity : AppCompatActivity() {

    private lateinit var switchEnabled: SwitchCompat
    private lateinit var layoutConfigContent: LinearLayout
    private lateinit var tvSummary: TextView
    private lateinit var switches: Map<ProviderManager.EventCategory, SwitchCompat>

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
            ProviderManager.EventCategory.AGENT to findViewById(R.id.switchEvAgent),
            ProviderManager.EventCategory.TOOL to findViewById(R.id.switchEvTool),
            ProviderManager.EventCategory.LLM to findViewById(R.id.switchEvLLM),
            ProviderManager.EventCategory.STRATEGY to findViewById(R.id.switchEvStrategy),
            ProviderManager.EventCategory.NODE to findViewById(R.id.switchEvNode),
            ProviderManager.EventCategory.SUBGRAPH to findViewById(R.id.switchEvSubgraph),
            ProviderManager.EventCategory.STREAMING to findViewById(R.id.switchEvStream)
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
        val checked = mutableSetOf<ProviderManager.EventCategory>()
        for (entry in switches) {
            if (entry.value.isChecked) checked.add(entry.key)
        }
        val selected = if (checked.size == switches.size || checked.isEmpty()) {
            emptySet<ProviderManager.EventCategory>()
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
