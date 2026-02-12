package com.lhzkml.jasmine

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.button.MaterialButton

class ProviderListActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private val switches = mutableMapOf<String, SwitchCompat>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_provider_list)

        container = findViewById(R.id.providerContainer)
        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }

        buildList()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun buildList() {
        container.removeAllViews()
        switches.clear()
        val inflater = LayoutInflater.from(this)

        for (provider in ProviderManager.providers) {
            val view = inflater.inflate(R.layout.item_provider, container, false)

            view.findViewById<TextView>(R.id.tvProviderName).text = provider.name

            val tagView = view.findViewById<TextView>(R.id.tvBuiltInTag)
            if (provider.isBuiltIn) {
                tagView.visibility = View.VISIBLE
            }

            val switch = view.findViewById<SwitchCompat>(R.id.switchProvider)
            switches[provider.id] = switch

            switch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    // 检查是否已配置 Key
                    val key = ProviderManager.getApiKey(this, provider.id)
                    if (key == null) {
                        switch.isChecked = false
                        // 跳转到配置页
                        startActivity(Intent(this, ProviderConfigActivity::class.java).apply {
                            putExtra("provider_id", provider.id)
                        })
                        return@setOnCheckedChangeListener
                    }
                    // 关闭其他开关
                    ProviderManager.setActive(this, provider.id)
                    switches.forEach { (id, sw) ->
                        if (id != provider.id && sw.isChecked) {
                            sw.setOnCheckedChangeListener(null)
                            sw.isChecked = false
                            bindSwitchListener(sw, ProviderManager.providers.find { it.id == id }!!)
                        }
                    }
                } else {
                    // 如果关闭的是当前激活的，清除激活状态
                    if (ProviderManager.getActiveId(this) == provider.id) {
                        ProviderManager.setActive(this, "")
                    }
                }
            }

            // 点击卡片进入配置
            view.findViewById<LinearLayout>(R.id.providerInfo).setOnClickListener {
                startActivity(Intent(this, ProviderConfigActivity::class.java).apply {
                    putExtra("provider_id", provider.id)
                })
            }

            container.addView(view)
        }
    }

    private fun bindSwitchListener(switch: SwitchCompat, provider: Provider) {
        switch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val key = ProviderManager.getApiKey(this, provider.id)
                if (key == null) {
                    switch.isChecked = false
                    startActivity(Intent(this, ProviderConfigActivity::class.java).apply {
                        putExtra("provider_id", provider.id)
                    })
                    return@setOnCheckedChangeListener
                }
                ProviderManager.setActive(this, provider.id)
                switches.forEach { (id, sw) ->
                    if (id != provider.id && sw.isChecked) {
                        sw.setOnCheckedChangeListener(null)
                        sw.isChecked = false
                        bindSwitchListener(sw, ProviderManager.providers.find { it.id == id }!!)
                    }
                }
            } else {
                if (ProviderManager.getActiveId(this) == provider.id) {
                    ProviderManager.setActive(this, "")
                }
            }
        }
    }

    private fun refreshStatus() {
        val activeId = ProviderManager.getActiveId(this)
        for (provider in ProviderManager.providers) {
            val switch = switches[provider.id] ?: continue
            val statusView = (switch.parent as View).findViewById<TextView>(R.id.tvStatus)

            val hasKey = ProviderManager.getApiKey(this, provider.id) != null
            statusView.text = if (hasKey) "已配置 · ${ProviderManager.getModel(this, provider.id)}" else "未配置"

            switch.setOnCheckedChangeListener(null)
            switch.isChecked = provider.id == activeId
            bindSwitchListener(switch, provider)
        }
    }
}
