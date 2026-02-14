package com.lhzkml.jasmine

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.button.MaterialButton

class ProviderListActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private val switches = mutableMapOf<String, SwitchCompat>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_provider_list)

        // 初始化 ProviderManager，加载自定义供应商
        ProviderManager.initialize(this)

        container = findViewById(R.id.providerContainer)
        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnAddCustomProvider).setOnClickListener {
            showAddCustomProviderDialog()
        }

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

            // 自定义供应商显示删除按钮
            val btnDelete = view.findViewById<ImageButton>(R.id.btnDeleteProvider)
            if (provider.isCustom) {
                btnDelete.visibility = View.VISIBLE
                btnDelete.setOnClickListener {
                    showDeleteProviderDialog(provider)
                }
            } else {
                btnDelete.visibility = View.GONE
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

    private fun showAddCustomProviderDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_custom_provider, null)
        val spinnerApiType = dialogView.findViewById<Spinner>(R.id.spinnerApiType)
        val etId = dialogView.findViewById<EditText>(R.id.etProviderId)
        val etName = dialogView.findViewById<EditText>(R.id.etProviderName)
        val etBaseUrl = dialogView.findViewById<EditText>(R.id.etProviderBaseUrl)
        val etModel = dialogView.findViewById<EditText>(R.id.etProviderModel)

        // 渠道类型选项
        val apiTypeLabels = arrayOf("OpenAI 兼容", "Claude", "Gemini")
        val apiTypeValues = arrayOf(ApiType.OPENAI, ApiType.CLAUDE, ApiType.GEMINI)
        spinnerApiType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, apiTypeLabels)

        // 选择渠道类型时自动填充默认 base URL
        spinnerApiType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (etBaseUrl.text.isNullOrBlank()) {
                    etBaseUrl.setText(when (apiTypeValues[position]) {
                        ApiType.OPENAI -> ""
                        ApiType.CLAUDE -> "https://api.anthropic.com"
                        ApiType.GEMINI -> "https://generativelanguage.googleapis.com"
                    })
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        AlertDialog.Builder(this)
            .setTitle("添加自定义供应商")
            .setView(dialogView)
            .setPositiveButton("添加") { _, _ ->
                val selectedApiType = apiTypeValues[spinnerApiType.selectedItemPosition]
                val id = etId.text.toString().trim()
                val name = etName.text.toString().trim()
                val baseUrl = etBaseUrl.text.toString().trim()
                val model = etModel.text.toString().trim()

                when {
                    id.isEmpty() -> {
                        Toast.makeText(this, "请输入供应商 ID", Toast.LENGTH_SHORT).show()
                    }
                    name.isEmpty() -> {
                        Toast.makeText(this, "请输入供应商名称", Toast.LENGTH_SHORT).show()
                    }
                    baseUrl.isEmpty() -> {
                        Toast.makeText(this, "请输入 API 地址", Toast.LENGTH_SHORT).show()
                    }
                    model.isEmpty() -> {
                        Toast.makeText(this, "请输入默认模型", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        val provider = Provider(
                            id = id,
                            name = name,
                            defaultBaseUrl = baseUrl,
                            defaultModel = model,
                            apiType = selectedApiType,
                            isCustom = true
                        )
                        val success = ProviderManager.registerProviderPersistent(this, provider)
                        if (success) {
                            Toast.makeText(this, "添加成功", Toast.LENGTH_SHORT).show()
                            buildList()
                            refreshStatus()
                        } else {
                            Toast.makeText(this, "供应商 ID 已存在", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeleteProviderDialog(provider: Provider) {
        AlertDialog.Builder(this)
            .setTitle("删除供应商")
            .setMessage("确定要删除「${provider.name}」吗？\n删除后配置信息将保留，但供应商将从列表中移除。")
            .setPositiveButton("删除") { _, _ ->
                // 如果是当前激活的供应商，先取消激活
                if (ProviderManager.getActiveId(this) == provider.id) {
                    ProviderManager.setActive(this, "")
                }
                
                val success = ProviderManager.unregisterProviderPersistent(this, provider.id)
                if (success) {
                    Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
                    buildList()
                    refreshStatus()
                } else {
                    Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
