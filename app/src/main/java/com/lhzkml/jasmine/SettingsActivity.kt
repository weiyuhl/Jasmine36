package com.lhzkml.jasmine

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.button.MaterialButton

class SettingsActivity : AppCompatActivity() {

    private lateinit var tvActiveProvider: TextView
    private lateinit var switchStream: SwitchCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        tvActiveProvider = findViewById(R.id.tvActiveProvider)
        switchStream = findViewById(R.id.switchStream)

        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<LinearLayout>(R.id.layoutProviders).setOnClickListener {
            startActivity(Intent(this, ProviderListActivity::class.java))
        }

        // 流式输出开关
        switchStream.isChecked = ProviderManager.isStreamEnabled(this)
        switchStream.setOnCheckedChangeListener { _, isChecked ->
            ProviderManager.setStreamEnabled(this, isChecked)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshProviderStatus()
    }

    private fun refreshProviderStatus() {
        val config = ProviderManager.getActiveConfig(this)
        if (config != null) {
            val provider = ProviderManager.providers.find { it.id == config.providerId }
            tvActiveProvider.text = provider?.name ?: config.providerId
        } else {
            tvActiveProvider.text = "未配置"
        }
    }
}
