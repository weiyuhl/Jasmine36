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
import com.lhzkml.jasmine.core.prompt.llm.ChatClient
import com.lhzkml.jasmine.core.prompt.executor.DeepSeekClient
import com.lhzkml.jasmine.core.prompt.executor.SiliconFlowClient
import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.conversation.storage.ConversationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    companion object {
        /** 从历史对话列表传入的对话 ID */
        const val EXTRA_CONVERSATION_ID = "conversation_id"
    }

    private lateinit var etInput: EditText
    private lateinit var btnSend: MaterialButton
    private lateinit var tvOutput: TextView
    private lateinit var scrollView: ScrollView

    private var chatClient: ChatClient? = null
    private var currentProviderId: String? = null

    private lateinit var conversationRepo: ConversationRepository
    /** 当前对话 ID，null 表示还没创建对话 */
    private var currentConversationId: String? = null
    /** 内存中的消息历史，用于多轮对话 */
    private val messageHistory = mutableListOf<ChatMessage>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        conversationRepo = ConversationRepository(this)

        etInput = findViewById(R.id.etInput)
        btnSend = findViewById(R.id.btnSend)
        tvOutput = findViewById(R.id.tvOutput)
        scrollView = findViewById(R.id.scrollView)

        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // 新对话按钮
        findViewById<ImageButton>(R.id.btnNewChat).setOnClickListener {
            startNewConversation()
        }

        // 历史对话按钮
        findViewById<ImageButton>(R.id.btnHistory).setOnClickListener {
            startActivity(Intent(this, ConversationHistoryActivity::class.java))
        }

        btnSend.setOnClickListener {
            val msg = etInput.text.toString().trim()
            if (msg.isNotEmpty()) sendMessage(msg)
        }

        // 如果是从历史对话进入，加载该对话
        val loadId = intent.getStringExtra(EXTRA_CONVERSATION_ID)
        if (loadId != null) {
            loadConversation(loadId)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val loadId = intent.getStringExtra(EXTRA_CONVERSATION_ID)
        if (loadId != null) {
            loadConversation(loadId)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        chatClient?.close()
    }

    /** 开始新对话，清空界面和历史 */
    private fun startNewConversation() {
        currentConversationId = null
        messageHistory.clear()
        tvOutput.text = ""
    }

    /** 从数据库加载历史对话 */
    private fun loadConversation(conversationId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val info = conversationRepo.getConversation(conversationId)
            val messages = conversationRepo.getMessages(conversationId)
            withContext(Dispatchers.Main) {
                if (info == null) {
                    Toast.makeText(this@MainActivity, "对话不存在", Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                currentConversationId = conversationId
                messageHistory.clear()
                messageHistory.addAll(messages)

                // 渲染历史消息到界面
                val sb = StringBuilder()
                for (msg in messages) {
                    when (msg.role) {
                        "user" -> sb.append("You: ${msg.content}\n\n")
                        "assistant" -> sb.append("AI: ${msg.content}\n\n")
                        // system 消息不显示
                    }
                }
                tvOutput.text = sb.toString()
                scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
            }
        }
    }

    /**
     * 根据当前配置获取或创建 ChatClient
     */
    private fun getOrCreateClient(config: ProviderManager.ActiveConfig): ChatClient {
        if (currentProviderId == config.providerId) {
            chatClient?.let { return it }
        }
        chatClient?.close()

        val client = when (config.providerId) {
            "deepseek" -> DeepSeekClient(apiKey = config.apiKey, baseUrl = config.baseUrl)
            "siliconflow" -> SiliconFlowClient(apiKey = config.apiKey, baseUrl = config.baseUrl)
            else -> SiliconFlowClient(apiKey = config.apiKey, baseUrl = config.baseUrl)
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
        val userMsg = ChatMessage.user(message)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 如果是第一条消息，创建新对话并持久化
                if (currentConversationId == null) {
                    val title = if (message.length > 20) message.substring(0, 20) + "..." else message
                    currentConversationId = conversationRepo.createConversation(
                        title = title,
                        providerId = config.providerId,
                        model = config.model
                    )
                    // 添加 system 消息
                    val systemMsg = ChatMessage.system("You are a helpful assistant.")
                    messageHistory.add(systemMsg)
                    conversationRepo.addMessage(currentConversationId!!, systemMsg)
                }

                // 添加用户消息到历史和数据库
                messageHistory.add(userMsg)
                conversationRepo.addMessage(currentConversationId!!, userMsg)

                // 发送完整历史给 LLM（多轮对话）
                val result = client.chat(messageHistory.toList(), config.model)

                // 保存 AI 回复
                val assistantMsg = ChatMessage.assistant(result)
                messageHistory.add(assistantMsg)
                conversationRepo.addMessage(currentConversationId!!, assistantMsg)

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
