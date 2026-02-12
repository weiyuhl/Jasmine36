package com.lhzkml.jasmine

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.lhzkml.jasmine.core.prompt.executor.DeepSeekClient
import com.lhzkml.jasmine.core.prompt.executor.SiliconFlowClient
import com.lhzkml.jasmine.core.prompt.model.ModelInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProviderConfigActivity : AppCompatActivity() {

    private lateinit var provider: Provider
    private lateinit var etApiKey: EditText
    private lateinit var etBaseUrl: EditText
    private lateinit var tvSelectedModel: TextView
    private lateinit var tvModelStatus: TextView
    private lateinit var btnFetchModels: MaterialButton

    /** 当前选中的模型 ID */
    private var selectedModel: String = ""

    /** 缓存的模型列表 */
    private var cachedModels: List<ModelInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_provider_config)

        val providerId = intent.getStringExtra("provider_id") ?: run { finish(); return }
        provider = ProviderManager.providers.find { it.id == providerId } ?: run { finish(); return }

        findViewById<TextView>(R.id.tvTitle).text = provider.name
        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }

        etApiKey = findViewById(R.id.etApiKey)
        etBaseUrl = findViewById(R.id.etBaseUrl)
        tvSelectedModel = findViewById(R.id.tvSelectedModel)
        tvModelStatus = findViewById(R.id.tvModelStatus)
        btnFetchModels = findViewById(R.id.btnFetchModels)

        // 所有供应商都显示 API 地址字段
        findViewById<LinearLayout>(R.id.layoutBaseUrl).visibility = View.VISIBLE

        // 加载已保存的配置
        ProviderManager.getApiKey(this, provider.id)?.let { etApiKey.setText(it) }
        etBaseUrl.setText(ProviderManager.getBaseUrl(this, provider.id))
        selectedModel = ProviderManager.getModel(this, provider.id)
        tvSelectedModel.text = selectedModel.ifEmpty { "" }

        // 点击模型文本也可以从缓存中选择
        tvSelectedModel.setOnClickListener {
            if (cachedModels.isNotEmpty()) {
                showModelPicker()
            } else {
                fetchModels()
            }
        }

        btnFetchModels.setOnClickListener { fetchModels() }

        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener { save() }
    }

    /**
     * 从供应商 API 获取模型列表
     */
    private fun fetchModels() {
        val apiKey = etApiKey.text.toString().trim()
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "请先输入 API Key", Toast.LENGTH_SHORT).show()
            return
        }

        val baseUrl = etBaseUrl.text.toString().trim().also {
            if (it.isEmpty()) {
                Toast.makeText(this, "请先输入 API 地址", Toast.LENGTH_SHORT).show()
                return
            }
        }

        // 显示加载状态
        btnFetchModels.isEnabled = false
        btnFetchModels.text = "加载中..."
        tvModelStatus.visibility = View.VISIBLE
        tvModelStatus.text = "正在获取模型列表..."

        val client = when (provider.id) {
            "deepseek" -> DeepSeekClient(apiKey = apiKey, baseUrl = baseUrl)
            "siliconflow" -> SiliconFlowClient(apiKey = apiKey, baseUrl = baseUrl)
            else -> SiliconFlowClient(apiKey = apiKey, baseUrl = baseUrl)
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val models = client.listModels()
                client.close()

                withContext(Dispatchers.Main) {
                    cachedModels = models
                    btnFetchModels.isEnabled = true
                    btnFetchModels.text = "获取列表"
                    tvModelStatus.text = "共 ${models.size} 个模型"

                    if (models.isNotEmpty()) {
                        showModelPicker()
                    } else {
                        tvModelStatus.text = "该供应商暂无可用模型"
                    }
                }
            } catch (e: Exception) {
                client.close()
                withContext(Dispatchers.Main) {
                    btnFetchModels.isEnabled = true
                    btnFetchModels.text = "获取列表"
                    tvModelStatus.text = "获取失败: ${e.message}"
                }
            }
        }
    }

    /**
     * 显示模型选择对话框
     */
    private fun showModelPicker() {
        val modelIds = cachedModels.map { it.id }.toTypedArray()
        val currentIndex = modelIds.indexOf(selectedModel).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("选择模型")
            .setSingleChoiceItems(modelIds, currentIndex) { dialog, which ->
                selectedModel = modelIds[which]
                tvSelectedModel.text = selectedModel
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun save() {
        val apiKey = etApiKey.text.toString().trim()
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "请输入 API Key", Toast.LENGTH_SHORT).show()
            return
        }

        val baseUrl = etBaseUrl.text.toString().trim()
        if (baseUrl.isEmpty()) {
            Toast.makeText(this, "请输入 API 地址", Toast.LENGTH_SHORT).show()
            return
        }

        val model = selectedModel.ifEmpty { null }

        ProviderManager.saveConfig(this, provider.id, apiKey, baseUrl, model)
        ProviderManager.setActive(this, provider.id)
        Toast.makeText(this, "已保存并启用 ${provider.name}", Toast.LENGTH_SHORT).show()
        finish()
    }
}
