package com.lhzkml.jasmine

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class SettingsActivity : AppCompatActivity() {

    private lateinit var tvActiveProvider: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        tvActiveProvider = findViewById(R.id.tvActiveProvider)

        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<LinearLayout>(R.id.layoutProviders).setOnClickListener {
            startActivity(Intent(this, ProviderListActivity::class.java))
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
