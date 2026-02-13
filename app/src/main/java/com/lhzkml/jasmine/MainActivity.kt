package com.lhzkml.jasmine

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.lhzkml.jasmine.core.prompt.llm.ChatClient
import com.lhzkml.jasmine.core.prompt.executor.DeepSeekClient
import com.lhzkml.jasmine.core.prompt.executor.SiliconFlowClient
import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.conversation.storage.ConversationInfo
import com.lhzkml.jasmine.core.conversation.storage.ConversationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CONVERSATION_ID = "conversation_id"
    }

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var etInput: EditText
    private lateinit var btnSend: MaterialButton
    private lateinit var tvOutput: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var tvDrawerEmpty: TextView
    private lateinit var rvDrawerConversations: RecyclerView

    private var chatClient: ChatClient? = null
    private var currentProviderId: String? = null

    private lateinit var conversationRepo: ConversationRepository
    private var currentConversationId: String? = null
    private val messageHistory = mutableListOf<ChatMessage>()
    private val drawerAdapter = DrawerConversationAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        conversationRepo = ConversationRepository(this)

        drawerLayout = findViewById(R.id.drawerLayout)
        etInput = findViewById(R.id.etInput)
        btnSend = findViewById(R.id.btnSend)
        tvOutput = findViewById(R.id.tvOutput)
        scrollView = findViewById(R.id.scrollView)
        tvDrawerEmpty = findViewById(R.id.tvDrawerEmpty)
        rvDrawerConversations = findViewById(R.id.rvDrawerConversations)

        // 标题栏按钮
        findViewById<ImageButton>(R.id.btnDrawer).setOnClickListener {
            drawerLayout.openDrawer(Gravity.END)
        }

        // 侧边栏底部：设置
        findViewById<TextView>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            drawerLayout.closeDrawer(Gravity.END)
        }

        // 设置右侧边缘滑动区域：全屏高度，宽度增加到 40dp
        try {
            val draggerField = drawerLayout.javaClass.getDeclaredField("mRightDragger")
            draggerField.isAccessible = true
            val dragger = draggerField.get(drawerLayout)
            val edgeField = dragger.javaClass.getDeclaredField("mEdgeSize")
            edgeField.isAccessible = true
            val edgeWidth = (40 * resources.displayMetrics.density).toInt()
            edgeField.setInt(dragger, edgeWidth)
        } catch (_: Exception) {
            // 反射失败时使用默认边缘大小
        }

        // 侧边栏：新对话
        findViewById<TextView>(R.id.btnNewChat).setOnClickListener {
            startNewConversation()
            drawerLayout.closeDrawer(Gravity.END)
        }

        // 侧边栏：历史对话列表
        rvDrawerConversations.layoutManager = LinearLayoutManager(this)
        rvDrawerConversations.adapter = drawerAdapter

        drawerAdapter.onItemClick = { info ->
            loadConversation(info.id)
            drawerLayout.closeDrawer(Gravity.END)
        }
        drawerAdapter.onDeleteClick = { info ->
            AlertDialog.Builder(this)
                .setMessage("确定删除这个对话吗？")
                .setPositiveButton("删除") { _, _ ->
                    CoroutineScope(Dispatchers.IO).launch {
                        conversationRepo.deleteConversation(info.id)
                        // 如果删的是当前对话，清空界面
                        if (info.id == currentConversationId) {
                            withContext(Dispatchers.Main) { startNewConversation() }
                        }
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }

        // 实时观察对话列表
        CoroutineScope(Dispatchers.Main).launch {
            conversationRepo.observeConversations().collectLatest { list ->
                drawerAdapter.submitList(list)
                tvDrawerEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                rvDrawerConversations.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        // 发送按钮
        btnSend.setOnClickListener {
            val msg = etInput.text.toString().trim()
            if (msg.isNotEmpty()) sendMessage(msg)
        }

        // 从历史对话进入
        intent.getStringExtra(EXTRA_CONVERSATION_ID)?.let { loadConversation(it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_CONVERSATION_ID)?.let { loadConversation(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        chatClient?.close()
    }

    private fun startNewConversation() {
        currentConversationId = null
        messageHistory.clear()
        tvOutput.text = ""
    }

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

                val sb = StringBuilder()
                for (msg in messages) {
                    when (msg.role) {
                        "user" -> sb.append("You: ${msg.content}\n\n")
                        "assistant" -> sb.append("AI: ${msg.content}\n\n")
                    }
                }
                tvOutput.text = sb.toString()
                scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
            }
        }
    }

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
                if (currentConversationId == null) {
                    val title = if (message.length > 20) message.substring(0, 20) + "..." else message
                    currentConversationId = conversationRepo.createConversation(
                        title = title,
                        providerId = config.providerId,
                        model = config.model
                    )
                    val systemMsg = ChatMessage.system("You are a helpful assistant.")
                    messageHistory.add(systemMsg)
                    conversationRepo.addMessage(currentConversationId!!, systemMsg)
                }

                messageHistory.add(userMsg)
                conversationRepo.addMessage(currentConversationId!!, userMsg)

                val result = client.chat(messageHistory.toList(), config.model)

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
                withContext(Dispatchers.Main) { btnSend.isEnabled = true }
            }
        }
    }

    /** 侧边栏对话列表适配器 */
    private class DrawerConversationAdapter : RecyclerView.Adapter<DrawerConversationAdapter.VH>() {

        private var items = listOf<ConversationInfo>()
        var onItemClick: ((ConversationInfo) -> Unit)? = null
        var onDeleteClick: ((ConversationInfo) -> Unit)? = null

        fun submitList(list: List<ConversationInfo>) {
            items = list
            notifyDataSetChanged()
        }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_drawer_conversation, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val info = items[position]
            holder.tvTitle.text = info.title
            val providerName = ProviderManager.providers
                .find { it.id == info.providerId }?.name ?: info.providerId
            val dateStr = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                .format(Date(info.updatedAt))
            holder.tvMeta.text = "$providerName · $dateStr"
            holder.itemView.setOnClickListener { onItemClick?.invoke(info) }
            holder.btnDelete.setOnClickListener { onDeleteClick?.invoke(info) }
        }

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tvTitle)
            val tvMeta: TextView = view.findViewById(R.id.tvMeta)
            val btnDelete: TextView = view.findViewById(R.id.btnDelete)
        }
    }
}
