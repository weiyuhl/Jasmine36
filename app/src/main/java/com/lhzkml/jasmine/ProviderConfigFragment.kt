package com.lhzkml.jasmine

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.lhzkml.jasmine.core.prompt.executor.DeepSeekClient
import com.lhzkml.jasmine.core.prompt.executor.SiliconFlowClient
import com.lhzkml.jasmine.core.prompt.llm.ChatClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProviderConfigFragment : Fragment() {

    private lateinit var provider: Provider
    private lateinit var etApiKey: EditText
    private lateinit var etBaseUrl: EditText
    private lateinit var etChatPath: EditText
    private lateinit var tvChatPathHint: TextView

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

    companion object {
        private const val ARG_PROVIDER_ID = "provider_id"

        fun newInstance(providerId: String): ProviderConfigFragment {
            return ProviderConfigFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PROVIDER_ID, providerId)
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_provider_config, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        val providerId = arguments?.getString(ARG_PROVIDER_ID) ?: return
        provider = ProviderManager.providers.find { it.id == providerId } ?: return

        etApiKey = view.findViewById(R.id.etApiKey)
        etBaseUrl = view.findViewById(R.id.etBaseUrl)
        etChatPath = view.findViewById(R.id.etChatPath)
        tvChatPathHint = view.findViewById(R.id.tvChatPathHint)

        // Vertex AI views
        layoutVertexAI = view.findViewById(R.id.layoutVertexAI)
        switchVertexAI = view.findViewById(R.id.switchVertexAI)
        layoutVertexFields = view.findViewById(R.id.layoutVertexFields)
        etVertexProjectId = view.findViewById(R.id.etVertexProjectId)
        etVertexLocation = view.findViewById(R.id.etVertexLocation)
        etVertexServiceAccountJson = view.findViewById(R.id.etVertexServiceAccountJson)

        // 所有供应商都显示 API 地址
        view.findViewById<LinearLayout>(R.id.layoutBaseUrl).visibility = View.VISIBLE

        // API 路径
        val layoutChatPath = view.findViewById<LinearLayout>(R.id.layoutChatPath)
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
            setupVertexAI(view)
        } else {
            layoutVertexAI.visibility = View.GONE
        }

        // 加载已保存的配置
        ProviderManager.getApiKey(ctx, provider.id)?.let { etApiKey.setText(it) }
        etBaseUrl.setText(ProviderManager.getBaseUrl(ctx, provider.id))

        // 加载 API 路径
        val savedPath = ProviderManager.getChatPath(ctx, provider.id)
        if (!savedPath.isNullOrEmpty()) {
            etChatPath.setText(savedPath)
        } else {
            etChatPath.hint = when (provider.apiType) {
                ApiType.OPENAI -> "/v1/chat/completions"
                ApiType.GEMINI -> "/v1beta/models/{model}:generateContent"
                ApiType.CLAUDE -> "/v1/messages"
            }
        }

        view.findViewById<MaterialButton>(R.id.btnSave).setOnClickListener { save() }

        // 余额查询
        layoutBalance = view.findViewById(R.id.layoutBalance)
        btnQueryBalance = view.findViewById(R.id.btnQueryBalance)
        tvBalanceInfo = view.findViewById(R.id.tvBalanceInfo)

        if (provider.id == "deepseek" || provider.id == "siliconflow") {
            layoutBalance.visibility = View.VISIBLE
            btnQueryBalance.setOnClickListener { queryBalance() }
        } else {
            layoutBalance.visibility = View.GONE
        }
    }

    private fun setupVertexAI(view: View) {
        val ctx = requireContext()
        val vertexEnabled = ProviderManager.isVertexAIEnabled(ctx, provider.id)
        switchVertexAI.isChecked = vertexEnabled
        layoutVertexFields.visibility = if (vertexEnabled) View.VISIBLE else View.GONE

        etVertexProjectId.setText(ProviderManager.getVertexProjectId(ctx, provider.id))
        etVertexLocation.setText(ProviderManager.getVertexLocation(ctx, provider.id))
        etVertexServiceAccountJson.setText(ProviderManager.getVertexServiceAccountJson(ctx, provider.id))

        updateVertexUIState(view, vertexEnabled)

        switchVertexAI.setOnCheckedChangeListener { _, isChecked ->
            layoutVertexFields.visibility = if (isChecked) View.VISIBLE else View.GONE
            updateVertexUIState(view, isChecked)
        }
    }

    private fun updateVertexUIState(view: View, vertexEnabled: Boolean) {
        val tvKeyLabel = view.findViewById<TextView>(R.id.tvKeyLabel)
        val layoutChatPath = view.findViewById<LinearLayout>(R.id.layoutChatPath)

        if (vertexEnabled) {
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

    private fun queryBalance() {
        val ctx = requireContext()
        val apiKey = etApiKey.text.toString().trim()
        if (apiKey.isEmpty()) {
            Toast.makeText(ctx, "请先输入 API Key", Toast.LENGTH_SHORT).show()
            return
        }
        val baseUrl = etBaseUrl.text.toString().trim()
        if (baseUrl.isEmpty()) {
            Toast.makeText(ctx, "请先输入 API 地址", Toast.LENGTH_SHORT).show()
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
                    if (!isAdded) return@withContext
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
                    if (!isAdded) return@withContext
                    btnQueryBalance.isEnabled = true
                    btnQueryBalance.text = "查询"
                    tvBalanceInfo.text = "查询失败: ${e.message}"
                }
            }
        }
    }

    private fun save() {
        val ctx = requireContext()
        val vertexEnabled = provider.apiType == ApiType.GEMINI && switchVertexAI.isChecked

        if (vertexEnabled) {
            val projectId = etVertexProjectId.text.toString().trim()
            val location = etVertexLocation.text.toString().trim()
            val saJson = etVertexServiceAccountJson.text.toString().trim()

            if (projectId.isEmpty()) {
                Toast.makeText(ctx, "请输入项目 ID", Toast.LENGTH_SHORT).show()
                return
            }
            if (location.isEmpty()) {
                Toast.makeText(ctx, "请输入区域 (Location)", Toast.LENGTH_SHORT).show()
                return
            }
            if (saJson.isEmpty()) {
                Toast.makeText(ctx, "请粘贴服务账号 JSON", Toast.LENGTH_SHORT).show()
                return
            }
            if (!saJson.contains("\"private_key\"") || !saJson.contains("\"client_email\"")) {
                Toast.makeText(ctx, "服务账号 JSON 格式不正确，需包含 private_key 和 client_email", Toast.LENGTH_LONG).show()
                return
            }

            val baseUrl = etBaseUrl.text.toString().trim()
            val apiKey = etApiKey.text.toString().trim()

            ProviderManager.saveConfig(ctx, provider.id, apiKey, baseUrl.ifEmpty { null }, null)
            ProviderManager.setVertexAIEnabled(ctx, provider.id, true)
            ProviderManager.setVertexProjectId(ctx, provider.id, projectId)
            ProviderManager.setVertexLocation(ctx, provider.id, location)
            ProviderManager.setVertexServiceAccountJson(ctx, provider.id, saJson)
        } else {
            val apiKey = etApiKey.text.toString().trim()
            if (apiKey.isEmpty()) {
                Toast.makeText(ctx, "请输入 API Key", Toast.LENGTH_SHORT).show()
                return
            }
            val baseUrl = etBaseUrl.text.toString().trim()
            if (baseUrl.isEmpty()) {
                Toast.makeText(ctx, "请输入 API 地址", Toast.LENGTH_SHORT).show()
                return
            }
            val model: String? = null
            ProviderManager.saveConfig(ctx, provider.id, apiKey, baseUrl, model)

            if (provider.apiType == ApiType.GEMINI) {
                ProviderManager.setVertexAIEnabled(ctx, provider.id, false)
            }
        }

        val chatPath = etChatPath.text.toString().trim()
        if (chatPath.isNotEmpty()) {
            ProviderManager.saveChatPath(ctx, provider.id, chatPath)
        }

        ProviderManager.setActive(ctx, provider.id)
        Toast.makeText(ctx, "已保存并启用 ${provider.name}", Toast.LENGTH_SHORT).show()
    }
}
