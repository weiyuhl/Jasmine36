package com.lhzkml.jasmine

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.button.MaterialButton
import com.lhzkml.jasmine.core.prompt.executor.ClaudeClient
import com.lhzkml.jasmine.core.prompt.executor.DeepSeekClient
import com.lhzkml.jasmine.core.prompt.executor.GeminiClient
import com.lhzkml.jasmine.core.prompt.executor.GenericClaudeClient
import com.lhzkml.jasmine.core.prompt.executor.GenericGeminiClient
import com.lhzkml.jasmine.core.prompt.executor.GenericOpenAIClient
import com.lhzkml.jasmine.core.prompt.executor.OpenAIClient
import com.lhzkml.jasmine.core.prompt.executor.SiliconFlowClient
import com.lhzkml.jasmine.core.prompt.llm.ChatClient
import com.lhzkml.jasmine.core.prompt.llm.LLMProvider
import com.lhzkml.jasmine.core.prompt.llm.ModelRegistry
import com.lhzkml.jasmine.core.prompt.model.ModelInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProviderConfigActivity : AppCompatActivity() {

    private lateinit var provider: Provider
    private lateinit var etApiKey: EditText
    private lateinit var etBaseUrl: EditText
    private lateinit var etChatPath: EditText
    private lateinit var tvChatPathHint: TextView
    private lateinit var etSelectedModel: EditText
    private lateinit var tvModelStatus: TextView
    private lateinit var btnFetchModels: MaterialButton

    // Vertex AI
    private lateinit var layoutVertexAI: LinearLayout
    private lateinit var switchVertexAI: SwitchCompat
    private lateinit var layoutVertexFields: LinearLayout
    private lateinit var etVertexProjectId: EditText
    private lateinit var etVertexLocation: EditText
    private lateinit var etVertexServiceAccountJson: EditText

    // 余额查询
    private lateinit var layoutBalance: LinearLayout
    private lateinit var btnQueryBalance: MaterialButton
    private lateinit var tvBalanceInfo: TextView

    private var selectedModel: String = ""
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
        etChatPath = findViewById(R.id.etChatPath)
        tvChatPathHint = findViewById(R.id.tvChatPathHint)
        etSelectedModel = findViewById(R.id.tvSelectedModel)
        tvModelStatus = findViewById(R.id.tvModelStatus)
        btnFetchModels = findViewById(R.id.btnFetchModels)

        // Vertex AI views
        layoutVertexAI = findViewById(R.id.layoutVertexAI)
        switchVertexAI = findViewById(R.id.switchVertexAI)
        layoutVertexFields = findViewById(R.id.layoutVertexFields)
        etVertexProjectId = findViewById(R.id.etVertexProjectId)
        etVertexLocation = findViewById(R.id.etVertexLocation)
        etVertexServiceAccountJson = findViewById(R.id.etVertexServiceAccountJson)

        // 所有供应商都显示 API 地址
        findViewById<LinearLayout>(R.id.layoutBaseUrl).visibility = View.VISIBLE

        // API 路径：OpenAI 和 Gemini 类型显示
        val layoutChatPath = findViewById<LinearLayout>(R.id.layoutChatPath)
        when (provider.apiType) {
            ApiType.OPENAI -> {
                layoutChatPath.visibility = View.VISIBLE
                tvChatPathHint.text = "默认: /v1/chat/completions"
            }
            ApiType.GEMINI -> {
                layoutChatPath.visibility = View.VISIBLE
                tvChatPathHint.text = "默认: /v1beta/models/{model}:generateContent"
            }
            ApiType.CLAUDE -> {
                layoutChatPath.visibility = View.GONE
            }
        }

        // Vertex AI 开关：仅 Gemini 类型显示
        if (provider.apiType == ApiType.GEMINI) {
            layoutVertexAI.visibility = View.VISIBLE
            setupVertexAI()
        } else {
            layoutVertexAI.visibility = View.GONE
        }

        // 加载已保存的配置
        ProviderManager.getApiKey(this, provider.id)?.let { etApiKey.setText(it) }
        etBaseUrl.setText(ProviderManager.getBaseUrl(this, provider.id))
        selectedModel = ProviderManager.getModel(this, provider.id)
        etSelectedModel.setText(selectedModel.ifEmpty { "" })

        // 加载 API 路径
        val savedPath = ProviderManager.getChatPath(this, provider.id)
        if (!savedPath.isNullOrEmpty()) {
            etChatPath.setText(savedPath)
        } else {
            etChatPath.hint = when (provider.apiType) {
                ApiType.OPENAI -> "/v1/chat/completions"
                ApiType.GEMINI -> "/v1beta/models/{model}:generateContent"
                ApiType.CLAUDE -> "/v1/messages"
            }
        }

        btnFetchModels.setOnClickListener { fetchModels() }
        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener { save() }

        // 余额查询
        layoutBalance = findViewById(R.id.layoutBalance)
        btnQueryBalance = findViewById(R.id.btnQueryBalance)
        tvBalanceInfo = findViewById(R.id.tvBalanceInfo)

        // 仅 DeepSeek 和硅基流动支持余额查询
        if (provider.id == "deepseek" || provider.id == "siliconflow") {
            layoutBalance.visibility = View.VISIBLE
            btnQueryBalance.setOnClickListener { queryBalance() }
        } else {
            layoutBalance.visibility = View.GONE
        }
    }

    private fun setupVertexAI() {
        // 加载已保存的 Vertex AI 配置
        val vertexEnabled = ProviderManager.isVertexAIEnabled(this, provider.id)
        switchVertexAI.isChecked = vertexEnabled
        layoutVertexFields.visibility = if (vertexEnabled) View.VISIBLE else View.GONE

        etVertexProjectId.setText(ProviderManager.getVertexProjectId(this, provider.id))
        etVertexLocation.setText(ProviderManager.getVertexLocation(this, provider.id))
        etVertexServiceAccountJson.setText(ProviderManager.getVertexServiceAccountJson(this, provider.id))

        // 启用 Vertex AI 时隐藏 API Key 和 API 路径（Vertex AI 用服务账号认证）
        updateVertexUIState(vertexEnabled)

        switchVertexAI.setOnCheckedChangeListener { _, isChecked ->
            layoutVertexFields.visibility = if (isChecked) View.VISIBLE else View.GONE
            updateVertexUIState(isChecked)
        }
    }

    private fun updateVertexUIState(vertexEnabled: Boolean) {
        val tvKeyLabel = findViewById<TextView>(R.id.tvKeyLabel)
        val layoutChatPath = findViewById<LinearLayout>(R.id.layoutChatPath)

        if (vertexEnabled) {
            // Vertex AI 模式：API Key 变为可选，隐藏 API 路径
            tvKeyLabel.text = "API Key (可选)"
            etApiKey.hint = "Vertex AI 使用服务账号认证"
            layoutChatPath.visibility = View.GONE
        } else {
            tvKeyLabel.text = "API Key"
            etApiKey.hint = "sk-..."
            if (provider.apiType == ApiType.GEMINI || provider.apiType == ApiType.OPENAI) {
                layoutChatPath.visibility = View.VISIBLE
            }
        }
    }

    private fun fetchModels() {
        val vertexEnabled = provider.apiType == ApiType.GEMINI && switchVertexAI.isChecked

        val apiKey = etApiKey.text.toString().trim()
        if (!vertexEnabled && apiKey.isEmpty()) {
            Toast.makeText(this, "请先输入 API Key", Toast.LENGTH_SHORT).show()
            return
        }

        val baseUrl = etBaseUrl.text.toString().trim()
        if (!vertexEnabled && baseUrl.isEmpty()) {
            Toast.makeText(this, "请先输入 API 地址", Toast.LENGTH_SHORT).show()
            return
        }

        btnFetchModels.isEnabled = false
        btnFetchModels.text = "加载中..."
        tvModelStatus.visibility = View.VISIBLE
        tvModelStatus.text = "正在获取模型列表..."

        // Vertex AI 模式不支持 list models
        if (vertexEnabled) {
            btnFetchModels.isEnabled = true
            btnFetchModels.text = "获取列表"
            tvModelStatus.text = "Vertex AI 不支持获取模型列表，请手动输入模型名"
            return
        }

        val client: ChatClient = when (provider.apiType) {
            ApiType.OPENAI -> when (provider.id) {
                "openai" -> OpenAIClient(apiKey = apiKey, baseUrl = baseUrl)
                "deepseek" -> DeepSeekClient(apiKey = apiKey, baseUrl = baseUrl)
                "siliconflow" -> SiliconFlowClient(apiKey = apiKey, baseUrl = baseUrl)
                else -> GenericOpenAIClient(providerName = provider.name, apiKey = apiKey, baseUrl = baseUrl)
            }
            ApiType.CLAUDE -> when (provider.id) {
                "claude" -> ClaudeClient(apiKey = apiKey, baseUrl = baseUrl)
                else -> GenericClaudeClient(providerName = provider.name, apiKey = apiKey, baseUrl = baseUrl)
            }
            ApiType.GEMINI -> when (provider.id) {
                "gemini" -> GeminiClient(apiKey = apiKey, baseUrl = baseUrl)
                else -> GenericGeminiClient(providerName = provider.name, apiKey = apiKey, baseUrl = baseUrl)
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val models = client.listModels()
                client.close()

                // 动态注册模型元数据到 ModelRegistry
                val llmProvider = client.provider
                ModelRegistry.registerFromApi(llmProvider, models)

                withContext(Dispatchers.Main) {
                    cachedModels = models
                    btnFetchModels.isEnabled = true
                    btnFetchModels.text = "获取列表"
                    tvModelStatus.text = "共 ${models.size} 个模型"
                    if (models.isNotEmpty()) showModelPicker()
                    else tvModelStatus.text = "该供应商暂无可用模型"
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

    private fun showModelPicker() {
        val adapter = object : BaseAdapter() {
            override fun getCount() = cachedModels.size
            override fun getItem(position: Int) = cachedModels[position]
            override fun getItemId(position: Int) = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val view = convertView ?: LayoutInflater.from(this@ProviderConfigActivity)
                    .inflate(R.layout.item_model_picker, parent, false)
                val model = cachedModels[position]
                val tvId = view.findViewById<TextView>(R.id.tvModelId)
                val tvMeta = view.findViewById<TextView>(R.id.tvModelMeta)

                // 模型名：有 displayName 就显示 displayName + id，否则只显示 id
                if (model.displayName != null && model.displayName != model.id) {
                    tvId.text = "${model.displayName}  (${model.id})"
                } else {
                    tvId.text = model.id
                }

                // 当前选中的高亮
                if (model.id == selectedModel) {
                    tvId.setTextColor(resources.getColor(R.color.accent, null))
                } else {
                    tvId.setTextColor(resources.getColor(R.color.text_primary, null))
                }

                // 元数据
                val meta = buildModelMetaLine(model)
                if (meta.isNotEmpty()) {
                    tvMeta.text = meta
                    tvMeta.visibility = View.VISIBLE
                } else {
                    tvMeta.visibility = View.GONE
                }

                return view
            }
        }

        AlertDialog.Builder(this)
            .setTitle("选择模型 (${cachedModels.size})")
            .setAdapter(adapter) { dialog, which ->
                selectedModel = cachedModels[which].id
                etSelectedModel.setText(selectedModel)
                tvModelStatus.text = "已选择: $selectedModel"
                tvModelStatus.visibility = View.VISIBLE
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 构建模型元数据摘要行
     */
    private fun buildModelMetaLine(info: ModelInfo): String {
        val parts = mutableListOf<String>()
        info.contextLength?.let { parts.add("输入 ${formatTokenCount(it)}") }
        info.maxOutputTokens?.let { parts.add("输出 ${formatTokenCount(it)}") }
        if (info.supportsThinking == true) parts.add("✦思考")
        info.temperature?.let { t ->
            val maxT = info.maxTemperature?.let { "~$it" } ?: ""
            parts.add("温度 $t$maxT")
        }
        info.topK?.let { parts.add("topK $it") }

        return parts.joinToString("  ·  ")
    }

    private fun formatTokenCount(tokens: Int): String {
        return when {
            tokens >= 1_000_000 -> "${tokens / 1_000_000}M"
            tokens >= 1_000 -> "${tokens / 1_000}K"
            else -> tokens.toString()
        }
    }

    private fun queryBalance() {
        val apiKey = etApiKey.text.toString().trim()
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "请先输入 API Key", Toast.LENGTH_SHORT).show()
            return
        }

        val baseUrl = etBaseUrl.text.toString().trim()
        if (baseUrl.isEmpty()) {
            Toast.makeText(this, "请先输入 API 地址", Toast.LENGTH_SHORT).show()
            return
        }

        btnQueryBalance.isEnabled = false
        btnQueryBalance.text = "查询中..."
        tvBalanceInfo.visibility = View.VISIBLE
        tvBalanceInfo.text = "正在查询余额..."

        val client: ChatClient = when (provider.id) {
            "deepseek" -> DeepSeekClient(apiKey = apiKey, baseUrl = baseUrl)
            "siliconflow" -> SiliconFlowClient(apiKey = apiKey, baseUrl = baseUrl)
            else -> {
                btnQueryBalance.isEnabled = true
                btnQueryBalance.text = "查询"
                tvBalanceInfo.text = "该供应商不支持余额查询"
                return
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val balance = client.getBalance()
                client.close()
                withContext(Dispatchers.Main) {
                    btnQueryBalance.isEnabled = true
                    btnQueryBalance.text = "查询"
                    if (balance != null) {
                        val sb = StringBuilder()
                        for (detail in balance.balances) {
                            sb.append("${detail.currency}: ${detail.totalBalance}")
                            if (detail.grantedBalance != null && detail.toppedUpBalance != null) {
                                sb.append("（赠送 ${detail.grantedBalance} + 充值 ${detail.toppedUpBalance}）")
                            }
                            sb.append("\n")
                        }
                        tvBalanceInfo.text = sb.toString().trimEnd()
                    } else {
                        tvBalanceInfo.text = "该供应商不支持余额查询"
                    }
                }
            } catch (e: Exception) {
                client.close()
                withContext(Dispatchers.Main) {
                    btnQueryBalance.isEnabled = true
                    btnQueryBalance.text = "查询"
                    tvBalanceInfo.text = "查询失败: ${e.message}"
                }
            }
        }
    }

    private fun save() {
        val vertexEnabled = provider.apiType == ApiType.GEMINI && switchVertexAI.isChecked

        if (vertexEnabled) {
            // Vertex AI 模式：验证 Vertex AI 字段
            val projectId = etVertexProjectId.text.toString().trim()
            val location = etVertexLocation.text.toString().trim()
            val saJson = etVertexServiceAccountJson.text.toString().trim()

            if (projectId.isEmpty()) {
                Toast.makeText(this, "请输入项目 ID", Toast.LENGTH_SHORT).show()
                return
            }
            if (location.isEmpty()) {
                Toast.makeText(this, "请输入区域 (Location)", Toast.LENGTH_SHORT).show()
                return
            }
            if (saJson.isEmpty()) {
                Toast.makeText(this, "请粘贴服务账号 JSON", Toast.LENGTH_SHORT).show()
                return
            }

            // 简单验证 JSON 格式
            if (!saJson.contains("\"private_key\"") || !saJson.contains("\"client_email\"")) {
                Toast.makeText(this, "服务账号 JSON 格式不正确，需包含 private_key 和 client_email", Toast.LENGTH_LONG).show()
                return
            }

            val baseUrl = etBaseUrl.text.toString().trim()
            val model = etSelectedModel.text.toString().trim().ifEmpty { null }
            val apiKey = etApiKey.text.toString().trim()

            // 保存基本配置（API Key 可选）
            ProviderManager.saveConfig(this, provider.id, apiKey, baseUrl.ifEmpty { null }, model)

            // 保存 Vertex AI 配置
            ProviderManager.setVertexAIEnabled(this, provider.id, true)
            ProviderManager.setVertexProjectId(this, provider.id, projectId)
            ProviderManager.setVertexLocation(this, provider.id, location)
            ProviderManager.setVertexServiceAccountJson(this, provider.id, saJson)
        } else {
            // 普通模式
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

            val model = etSelectedModel.text.toString().trim().ifEmpty { null }
            ProviderManager.saveConfig(this, provider.id, apiKey, baseUrl, model)

            // 关闭 Vertex AI
            if (provider.apiType == ApiType.GEMINI) {
                ProviderManager.setVertexAIEnabled(this, provider.id, false)
            }
        }

        // 保存 API 路径
        val chatPath = etChatPath.text.toString().trim()
        if (chatPath.isNotEmpty()) {
            ProviderManager.saveChatPath(this, provider.id, chatPath)
        }

        ProviderManager.setActive(this, provider.id)
        Toast.makeText(this, "已保存并启用 ${provider.name}", Toast.LENGTH_SHORT).show()
        finish()
    }
}
