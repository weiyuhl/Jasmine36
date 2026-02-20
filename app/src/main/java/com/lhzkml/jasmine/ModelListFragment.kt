package com.lhzkml.jasmine

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.lhzkml.jasmine.core.prompt.executor.*
import com.lhzkml.jasmine.core.prompt.llm.ChatClient
import com.lhzkml.jasmine.core.prompt.llm.ModelRegistry
import com.lhzkml.jasmine.core.prompt.model.ModelInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ModelListFragment : Fragment() {

    companion object {
        private const val ARG_PROVIDER_ID = "provider_id"

        fun newInstance(providerId: String): ModelListFragment {
            return ModelListFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PROVIDER_ID, providerId)
                }
            }
        }
    }

    private lateinit var providerId: String
    private lateinit var provider: Provider
    private lateinit var tvStatus: TextView
    private lateinit var tvSelectedCount: TextView
    private lateinit var rvModels: RecyclerView
    private lateinit var btnRefresh: MaterialButton
    private lateinit var btnConfirm: MaterialButton
    private lateinit var tvSelectedNames: TextView

    private val adapter = ModelAdapter()
    private var models: List<ModelInfo> = emptyList()
    private val checkedModels = mutableSetOf<String>()
    private var currentModel: String = ""

    /** 模型选择确认后的回调 */
    var onModelsConfirmed: (() -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_model_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        providerId = arguments?.getString(ARG_PROVIDER_ID) ?: return
        provider = ProviderManager.providers.find { it.id == providerId } ?: return

        tvStatus = view.findViewById(R.id.tvStatus)
        tvSelectedCount = view.findViewById(R.id.tvSelectedCount)
        rvModels = view.findViewById(R.id.rvModels)
        btnRefresh = view.findViewById(R.id.btnRefresh)
        btnConfirm = view.findViewById(R.id.btnConfirm)
        tvSelectedNames = view.findViewById(R.id.tvSelectedNames)

        rvModels.layoutManager = LinearLayoutManager(ctx)
        rvModels.adapter = adapter

        // 加载已保存的选中模型
        currentModel = ProviderManager.getModel(ctx, providerId)
        val savedSelected = ProviderManager.getSelectedModels(ctx, providerId)
        checkedModels.addAll(savedSelected)
        if (currentModel.isNotEmpty()) {
            checkedModels.add(currentModel)
        }

        btnRefresh.setOnClickListener { fetchModels() }
        btnConfirm.setOnClickListener { confirmSelection() }

        updateSelectedCount()
        fetchModels()
    }

    private fun fetchModels() {
        val ctx = requireContext()
        val apiKey = ProviderManager.getApiKey(ctx, providerId) ?: ""
        val baseUrl = ProviderManager.getBaseUrl(ctx, providerId)

        val vertexEnabled = provider.apiType == ApiType.GEMINI
                && ProviderManager.isVertexAIEnabled(ctx, providerId)

        if (!vertexEnabled && apiKey.isEmpty()) {
            tvStatus.text = "请先在配置页输入 API Key"
            return
        }
        if (!vertexEnabled && baseUrl.isEmpty()) {
            tvStatus.text = "请先在配置页输入 API 地址"
            return
        }
        if (vertexEnabled) {
            tvStatus.text = "Vertex AI 不支持获取模型列表，请手动输入"
            return
        }

        btnRefresh.isEnabled = false
        tvStatus.text = "正在获取模型列表..."

        val chatPath = ProviderManager.getChatPath(ctx, providerId)
        val client: ChatClient = ChatClientFactory.create(ChatClientConfig(
            providerId = providerId,
            providerName = provider.name,
            apiKey = apiKey,
            baseUrl = baseUrl,
            apiType = provider.apiType,
            chatPath = chatPath
        ))

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = client.listModels()
                client.close()
                ModelRegistry.registerFromApi(client.provider, result)

                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    models = result
                    adapter.notifyDataSetChanged()
                    btnRefresh.isEnabled = true
                    tvStatus.text = "共 ${result.size} 个模型"
                }
            } catch (e: Exception) {
                client.close()
                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    btnRefresh.isEnabled = true
                    tvStatus.text = "获取失败: ${e.message}"
                }
            }
        }
    }

    private fun updateSelectedCount() {
        tvSelectedCount.text = "已选 ${checkedModels.size} 个"
        if (checkedModels.isNotEmpty()) {
            tvSelectedNames.visibility = View.VISIBLE
            tvSelectedNames.text = checkedModels.joinToString(", ") { it.substringAfterLast("/") }
        } else {
            tvSelectedNames.visibility = View.GONE
        }
    }

    private fun confirmSelection() {
        val ctx = requireContext()
        ProviderManager.setSelectedModels(ctx, providerId, checkedModels.toList())
        if (currentModel !in checkedModels && checkedModels.isNotEmpty()) {
            val newModel = checkedModels.first()
            ProviderManager.saveConfig(ctx, providerId,
                ProviderManager.getApiKey(ctx, providerId) ?: "",
                ProviderManager.getBaseUrl(ctx, providerId),
                newModel)
        }
        Toast.makeText(ctx, "已保存 ${checkedModels.size} 个模型", Toast.LENGTH_SHORT).show()
        onModelsConfirmed?.invoke()
    }

    private fun formatTokenCount(tokens: Int): String {
        return when {
            tokens >= 1_000_000 -> "${tokens / 1_000_000}M"
            tokens >= 1_000 -> "${tokens / 1_000}K"
            else -> tokens.toString()
        }
    }

    inner class ModelAdapter : RecyclerView.Adapter<ModelAdapter.VH>() {
        override fun getItemCount() = models.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_model_list, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val model = models[position]
            holder.cbModel.isChecked = model.id in checkedModels

            if (model.displayName != null && model.displayName != model.id) {
                holder.tvModelId.text = "${model.displayName}  (${model.id})"
            } else {
                holder.tvModelId.text = model.id
            }

            if (model.id == currentModel) {
                holder.tvModelId.setTextColor(resources.getColor(R.color.accent, null))
            } else {
                holder.tvModelId.setTextColor(resources.getColor(R.color.text_primary, null))
            }

            val parts = mutableListOf<String>()
            model.contextLength?.let { parts.add("输入 ${formatTokenCount(it)}") }
            model.maxOutputTokens?.let { parts.add("输出 ${formatTokenCount(it)}") }
            if (model.supportsThinking == true) parts.add("思考")
            val meta = parts.joinToString(" · ")
            if (meta.isNotEmpty()) {
                holder.tvModelMeta.text = meta
                holder.tvModelMeta.visibility = View.VISIBLE
            } else {
                holder.tvModelMeta.visibility = View.GONE
            }

            holder.itemView.setOnClickListener {
                if (model.id in checkedModels) {
                    checkedModels.remove(model.id)
                } else {
                    checkedModels.add(model.id)
                }
                holder.cbModel.isChecked = model.id in checkedModels
                updateSelectedCount()
            }
        }

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val cbModel: CheckBox = view.findViewById(R.id.cbModel)
            val tvModelId: TextView = view.findViewById(R.id.tvModelId)
            val tvModelMeta: TextView = view.findViewById(R.id.tvModelMeta)
        }
    }
}
