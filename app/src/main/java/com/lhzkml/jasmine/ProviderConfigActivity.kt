package com.lhzkml.jasmine

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class ProviderConfigActivity : AppCompatActivity() {

    private lateinit var provider: Provider
    private lateinit var etApiKey: EditText
    private lateinit var etBaseUrl: EditText
    private lateinit var etModel: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_provider_config)

        val providerId = intent.getStringExtra("provider_id") ?: run { finish(); return }
        provider = ProviderManager.providers.find { it.id == providerId } ?: run { finish(); return }

        findViewById<TextView>(R.id.tvTitle).text = provider.name
        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }

        etApiKey = findViewById(R.id.etApiKey)
        etBaseUrl = findViewById(R.id.etBaseUrl)
        etModel = findViewById(R.id.etModel)

        // 非内置供应商才显示 baseUrl 和 model 字段
        if (provider.needsBaseUrl) {
            findViewById<LinearLayout>(R.id.layoutBaseUrl).visibility = View.VISIBLE
            findViewById<LinearLayout>(R.id.layoutModel).visibility = View.VISIBLE
        }

        // 如果是内置供应商，API Key label 上方不需要额外 margin
        if (provider.isBuiltIn) {
            findViewById<TextView>(R.id.tvKeyLabel).apply {
                val lp = layoutParams as LinearLayout.LayoutParams
                lp.topMargin = 0
                layoutParams = lp
            }
        }

        // 加载已保存的配置
        ProviderManager.getApiKey(this, provider.id)?.let { etApiKey.setText(it) }
        etBaseUrl.setText(ProviderManager.getBaseUrl(this, provider.id))
        etModel.setText(ProviderManager.getModel(this, provider.id))

        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener { save() }
    }

    private fun save() {
        val apiKey = etApiKey.text.toString().trim()
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "请输入 API Key", Toast.LENGTH_SHORT).show()
            return
        }

        val baseUrl = if (provider.needsBaseUrl) etBaseUrl.text.toString().trim() else null
        val model = if (provider.needsBaseUrl) etModel.text.toString().trim() else null

        if (provider.needsBaseUrl && baseUrl.isNullOrEmpty()) {
            Toast.makeText(this, "请输入 API 地址", Toast.LENGTH_SHORT).show()
            return
        }

        ProviderManager.saveConfig(this, provider.id, apiKey, baseUrl, model)
        ProviderManager.setActive(this, provider.id)
        Toast.makeText(this, "已保存并启用 ${provider.name}", Toast.LENGTH_SHORT).show()
        finish()
    }
}
