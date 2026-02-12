package com.lhzkml.jasmine

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.lhzkml.jasmine.core.api.ChatClient
import com.lhzkml.jasmine.core.client.DeepSeekClient
import com.lhzkml.jasmine.core.client.SiliconFlowClient
import com.lhzkml.jasmine.core.model.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var etInput: EditText
    private lateinit var btnSend: MaterialButton
    private lateinit var tvOutput: TextView
    private lateinit var scrollView: ScrollView

    private var chatClient: ChatClient? = null
    private var currentProviderId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etInput = findViewById(R.id.etInput)
        btnSend = findViewById(R.id.btnSend)
        tvOutput = findViewById(R.id.tvOutput)
        scrollView = findViewById(R.id.scrollView)

        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnSend.setOnClickListener {
            val msg = etInput.text.toString().trim()
            if (msg.isNotEmpty()) sendMessage(msg)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        chatClient?.close()
    }

    /**
     * 根据当前配置获取或创建 ChatClient。
     * 如果供应商切换了，会关闭旧客户端并创建新的。
     */
    private fun getOrCreateClient(config: ProviderManager.ActiveConfig): ChatClient {
        if (currentProviderId == config.providerId) {
            chatClient?.let { return it }
        }

        // 关闭旧客户端
        chatClient?.close()

        val client = when (config.providerId) {
            "deepseek" -> DeepSeekClient(
                apiKey = config.apiKey,
                baseUrl = config.baseUrl
            )
            "siliconflow" -> SiliconFlowClient(
                apiKey = config.apiKey,
                baseUrl = config.baseUrl
            )
            else -> SiliconFlowClient(
                apiKey = config.apiKey,
                baseUrl = config.baseUrl
            )
        }

        chatClient = client
        currentProviderId = config.providerId
        return client
    }

    private fun sendMessage(message: String) {
        val config = ProviderManager.getActiveConfig(this)
        if (config == null) {
            Toast.makeText(this, "请先在设置中配置模型供应商", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }

        btnSend.isEnabled = false
        tvOutput.append("You: $message\n\n")
        etInput.text.clear()

        val client = getOrCreateClient(config)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val messages = listOf(
                    ChatMessage.system("You are a helpful assistant."),
                    ChatMessage.user(message)
                )
                val result = client.chat(messages, config.model)
                withContext(Dispatchers.Main) {
                    tvOutput.append("AI: $result\n\n")
                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvOutput.append("Error: ${e.message}\n\n")
                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    btnSend.isEnabled = true
                }
            }
        }
    }
}
