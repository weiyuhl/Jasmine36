package com.lhzkml.jasmine

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.lhzkml.jasmine.core.agent.tools.snapshot.AgentCheckpoint
import com.lhzkml.jasmine.core.agent.tools.snapshot.FilePersistenceStorageProvider
import com.lhzkml.jasmine.core.conversation.storage.ConversationRepository
import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 检查点详情页 — 展示完整的检查点信息和消息历史预览
 */
class CheckpointDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_AGENT_ID = "agent_id"
        const val EXTRA_CHECKPOINT_ID = "checkpoint_id"
        const val RESULT_DELETED = 100
        const val RESULT_RESTORED = 101
    }

    private var agentId: String = ""
    private var checkpointId: String = ""
    private var checkpoint: AgentCheckpoint? = null
    private var provider: FilePersistenceStorageProvider? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_checkpoint_detail)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        agentId = intent.getStringExtra(EXTRA_AGENT_ID) ?: ""
        checkpointId = intent.getStringExtra(EXTRA_CHECKPOINT_ID) ?: ""

        if (agentId.isEmpty() || checkpointId.isEmpty()) {
            Toast.makeText(this, "参数错误", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadCheckpoint()
    }

    private fun loadCheckpoint() {
        val snapshotDir = getExternalFilesDir("snapshots") ?: return
        provider = FilePersistenceStorageProvider(snapshotDir)

        CoroutineScope(Dispatchers.IO).launch {
            val cp = provider?.getCheckpoints(agentId)
                ?.firstOrNull { it.checkpointId == checkpointId }

            withContext(Dispatchers.Main) {
                if (cp == null) {
                    Toast.makeText(this@CheckpointDetailActivity, "检查点不存在", Toast.LENGTH_SHORT).show()
                    finish()
                    return@withContext
                }
                checkpoint = cp
                renderInfo(cp)
                renderMessages(cp)
                setupButtons(cp)
            }
        }
    }

    private fun renderInfo(cp: AgentCheckpoint) {
        val timeFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
        val sb = StringBuilder()
        sb.append("节点路径: ${cp.nodePath}\n")
        sb.append("创建时间: ${timeFormat.format(Date(cp.createdAt))}\n")
        sb.append("版本: ${cp.version}\n")
        sb.append("消息数: ${cp.messageHistory.size}\n")
        sb.append("检查点 ID: ${cp.checkpointId}\n")
        sb.append("会话 ID: $agentId")
        if (cp.lastInput != null) {
            sb.append("\n最后输入: ${cp.lastInput}")
        }
        findViewById<TextView>(R.id.tvInfo).text = sb.toString()
    }

    private fun renderMessages(cp: AgentCheckpoint) {
        val container = findViewById<LinearLayout>(R.id.layoutMessages)
        val header = findViewById<TextView>(R.id.tvMsgHeader)
        container.removeAllViews()

        if (cp.messageHistory.isEmpty()) {
            header.text = "消息历史 (无)"
            return
        }

        header.text = "消息历史 (${cp.messageHistory.size} 条)"

        for ((index, msg) in cp.messageHistory.withIndex()) {
            // 消息卡片
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = dp(6)
                layoutParams = lp
            }

            // 角色标签 + 序号
            val roleLabel = when (msg.role) {
                "system" -> "[System]"
                "user" -> "[User]"
                "assistant" -> "[AI]"
                "tool" -> "[Tool]"
                "agent_log" -> "[Log]"
                else -> "[${msg.role}]"
            }

            val roleColor = when (msg.role) {
                "user" -> 0xFF2196F3.toInt()
                "assistant" -> 0xFF4CAF50.toInt()
                "system" -> 0xFF9E9E9E.toInt()
                "tool" -> 0xFFFF9800.toInt()
                else -> 0xFF757575.toInt()
            }

            val tvRole = TextView(this).apply {
                text = "#${index + 1} $roleLabel"
                textSize = 12f
                setTextColor(roleColor)
                setTypeface(null, Typeface.BOLD)
            }
            card.addView(tvRole)

            // 消息内容
            val content = msg.content
            val tvContent = TextView(this).apply {
                text = content
                textSize = 13f
                setTextColor(getColor(R.color.text_primary))
                val contentLp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                contentLp.topMargin = dp(3)
                layoutParams = contentLp
                // 默认折叠长消息
                if (content.length > 200) {
                    maxLines = 4
                    setOnClickListener {
                        maxLines = if (maxLines == 4) Int.MAX_VALUE else 4
                    }
                }
            }
            card.addView(tvContent)

            // 长消息提示
            if (content.length > 200) {
                val tvHint = TextView(this).apply {
                    text = "[点击展开/收起, ${content.length} 字符]"
                    textSize = 11f
                    setTextColor(0xFF2196F3.toInt())
                    val hintLp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    hintLp.topMargin = dp(2)
                    layoutParams = hintLp
                }
                tvHint.setOnClickListener {
                    tvContent.maxLines = if (tvContent.maxLines == 4) Int.MAX_VALUE else 4
                }
                card.addView(tvHint)
            }

            // 背景色区分角色
            val bgColor = when (msg.role) {
                "user" -> 0xFFF3F8FE.toInt()
                "assistant" -> 0xFFF3FBF3.toInt()
                "system" -> 0xFFF5F5F5.toInt()
                "tool" -> 0xFFFFF8E1.toInt()
                else -> 0xFFFAFAFA.toInt()
            }
            card.setBackgroundColor(bgColor)

            container.addView(card)
        }
    }

    private fun setupButtons(cp: AgentCheckpoint) {
        val btnRestore = findViewById<View>(R.id.btnRestore)
        val btnDelete = findViewById<View>(R.id.btnDelete)

        btnRestore.setOnClickListener { confirmRestore(cp) }
        btnDelete.setOnClickListener { confirmDelete(cp) }
    }

    private fun confirmRestore(cp: AgentCheckpoint) {
        val timeFormat = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())
        AlertDialog.Builder(this)
            .setTitle("恢复到新对话")
            .setMessage(
                "节点: ${cp.nodePath}\n" +
                "时间: ${timeFormat.format(Date(cp.createdAt))}\n" +
                "消息: ${cp.messageHistory.size} 条\n\n" +
                "将创建新对话并加载此检查点的消息历史。"
            )
            .setPositiveButton("恢复") { _, _ -> doRestore(cp) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun doRestore(cp: AgentCheckpoint) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = ConversationRepository(this@CheckpointDetailActivity)
                val timeFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                val timeStr = timeFormat.format(Date(cp.createdAt))

                val config = ProviderManager.getActiveConfig(this@CheckpointDetailActivity)
                val title = "[恢复] ${cp.nodePath} $timeStr"
                val systemPrompt = ProviderManager.getDefaultSystemPrompt(this@CheckpointDetailActivity)
                val conversationId = repo.createConversation(
                    title = title,
                    providerId = config?.providerId ?: "unknown",
                    model = config?.model ?: "unknown",
                    systemPrompt = systemPrompt
                )

                // 重建完整历史：system prompt + 所有检查点到当前轮次的消息
                val allCheckpoints = provider?.getCheckpoints(agentId)
                    ?.sortedBy { it.createdAt } ?: emptyList()
                val cpIndex = allCheckpoints.indexOfFirst { it.checkpointId == cp.checkpointId }
                val relevantCps = if (cpIndex >= 0) allCheckpoints.take(cpIndex + 1) else listOf(cp)

                // 写入 system prompt
                repo.addMessage(conversationId, ChatMessage.system(systemPrompt))
                // 按顺序写入每轮对话
                for (turnCp in relevantCps) {
                    for (msg in turnCp.messageHistory) {
                        repo.addMessage(conversationId, msg)
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CheckpointDetailActivity, "已恢复到新对话", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@CheckpointDetailActivity, MainActivity::class.java)
                    intent.putExtra(MainActivity.EXTRA_CONVERSATION_ID, conversationId)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    setResult(RESULT_RESTORED)
                    startActivity(intent)
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CheckpointDetailActivity, "恢复失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun confirmDelete(cp: AgentCheckpoint) {
        AlertDialog.Builder(this)
            .setMessage("删除此检查点？\n${cp.nodePath}\n此操作不可撤销。")
            .setPositiveButton("删除") { _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    provider?.deleteCheckpoint(agentId, cp.checkpointId)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@CheckpointDetailActivity, "已删除", Toast.LENGTH_SHORT).show()
                        setResult(RESULT_DELETED)
                        finish()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
