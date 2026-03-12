package com.lhzkml.jasmine

import com.lhzkml.jasmine.config.AppConfig
import com.lhzkml.jasmine.config.ProviderManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.agent.observe.snapshot.AgentCheckpoint
import com.lhzkml.jasmine.core.agent.runtime.CheckpointService
import com.lhzkml.jasmine.core.conversation.storage.ConversationRepository
import com.lhzkml.jasmine.ui.components.*
import com.lhzkml.jasmine.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.lhzkml.jasmine.repository.CheckpointRepository
import org.koin.android.ext.android.inject

class CheckpointDetailActivity : ComponentActivity() {
    private val checkpointRepository: CheckpointRepository by inject()

    companion object {
        const val EXTRA_AGENT_ID = "agent_id"
        const val EXTRA_CHECKPOINT_ID = "checkpoint_id"
        const val RESULT_DELETED = 100
        const val RESULT_RESTORED = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val agentId = intent.getStringExtra(EXTRA_AGENT_ID) ?: ""
        val checkpointId = intent.getStringExtra(EXTRA_CHECKPOINT_ID) ?: ""

        if (agentId.isEmpty() || checkpointId.isEmpty()) {
            Toast.makeText(this, "参数错误", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            CheckpointDetailScreen(
                agentId = agentId,
                checkpointId = checkpointId,
                repository = checkpointRepository,
                onBack = { finish() },
                onRestored = {
                    setResult(RESULT_RESTORED)
                    startActivity(it)
                    finish()
                },
                onDeleted = {
                    setResult(RESULT_DELETED)
                    finish()
                }
            )
        }
    }
}

@Composable
fun CheckpointDetailScreen(
    agentId: String,
    checkpointId: String,
    repository: CheckpointRepository,
    onBack: () -> Unit,
    onRestored: (Intent) -> Unit,
    onDeleted: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var checkpoint by remember { mutableStateOf<AgentCheckpoint?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val cp = repository.getCheckpoint(agentId, checkpointId)
        if (cp == null) {
            Toast.makeText(context, "检查点不存在", Toast.LENGTH_SHORT).show()
            onBack()
        } else {
            checkpoint = cp
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
    ) {
        // 顶部栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(Color.White)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CustomTextButton(
                onClick = onBack,
                colors = CustomButtonDefaults.textButtonColors(contentColor = TextPrimary)
            ) {
                CustomText("← 返回", fontSize = 14.sp)
            }
            CustomText(
                text = "检查点详情",
                fontSize = 17.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(56.dp))
        }

        CustomHorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CustomText("加载中...", fontSize = 15.sp, color = TextSecondary)
            }
        } else {
            val cp = checkpoint ?: return
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // 基本信息卡片
                InfoCard(agentId = agentId, checkpoint = cp)

                Spacer(modifier = Modifier.height(12.dp))

                // 消息历史
                MessageHistoryCard(checkpoint = cp)

                Spacer(modifier = Modifier.height(16.dp))

                // 恢复、删除按钮（并行显示，紧凑，无图标）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF2196F3), RoundedCornerShape(8.dp))
                            .background(Color(0xFF2196F3).copy(alpha = 0.06f))
                            .clickable { showRestoreDialog = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CustomText("恢复到新对话", fontSize = 13.sp, color = Color(0xFF2196F3))
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFE53935), RoundedCornerShape(8.dp))
                            .background(Color(0xFFE53935).copy(alpha = 0.06f))
                            .clickable { showDeleteDialog = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CustomText("删除此检查点", fontSize = 13.sp, color = Color(0xFFE53935))
                    }
                }
            }
        }
    }

    // 恢复确认对话框
    if (showRestoreDialog) {
        val cp = checkpoint ?: return
        val timeFormat = remember { SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault()) }
        CustomAlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            containerColor = Color.White,
            title = { CustomText("恢复到新对话", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                CustomText(
                    "节点: ${cp.nodePath}\n" +
                    "时间: ${timeFormat.format(Date(cp.createdAt))}\n" +
                    "消息: ${cp.messageHistory.size} 条\n\n" +
                    "将创建新对话并加载此检查点的消息历史。",
                    color = TextPrimary, fontSize = 14.sp
                )
            },
            confirmButton = {
                CustomTextButton(
                    onClick = {
                        showRestoreDialog = false
                        scope.launch {
                            try {
                                val repo = ConversationRepository(context)
                                val timeStr = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(cp.createdAt))
                                val config = ProviderManager.getActiveConfig()
                                val title = "[恢复] ${cp.nodePath} $timeStr"
                                val systemPrompt = ProviderManager.getDefaultSystemPrompt(context)
                                val conversationId = withContext(Dispatchers.IO) {
                                    val cid = repo.createConversation(
                                        title = title,
                                        providerId = config?.providerId ?: "unknown",
                                        model = config?.model ?: "unknown",
                                        systemPrompt = systemPrompt,
                                        workspacePath = if (ProviderManager.isAgentMode(context))
                                            ProviderManager.getWorkspacePath(context) else ""
                                    )
                                    val rebuiltHistory = repository.rebuildHistory(
                                        agentId = agentId,
                                        upToCheckpointId = cp.checkpointId,
                                        systemPrompt = systemPrompt
                                    )
                                    for (msg in rebuiltHistory) {
                                        repo.addMessage(cid, msg)
                                    }
                                    cid
                                }
                                Toast.makeText(context, "已恢复到新对话", Toast.LENGTH_SHORT).show()
                                val intent = Intent(context, MainActivity::class.java)
                                intent.putExtra(MainActivity.EXTRA_CONVERSATION_ID, conversationId)
                                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                onRestored(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "恢复失败: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = CustomButtonDefaults.textButtonColors(contentColor = Color(0xFF2196F3))
                ) { CustomText("恢复", fontSize = 14.sp) }
            },
            dismissButton = {
                CustomTextButton(
                    onClick = { showRestoreDialog = false },
                    colors = CustomButtonDefaults.textButtonColors(contentColor = TextSecondary)
                ) { CustomText("取消", fontSize = 14.sp) }
            }
        )
    }

    // 删除确认对话框
    if (showDeleteDialog) {
        val cp = checkpoint ?: return
        CustomAlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = Color.White,
            title = { CustomText("删除检查点", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = { CustomText("删除此检查点？\n${cp.nodePath}\n此操作不可撤销。", color = TextPrimary, fontSize = 14.sp) },
            confirmButton = {
                CustomTextButton(
                    onClick = {
                        showDeleteDialog = false
                        scope.launch {
                            repository.deleteCheckpoint(agentId, cp.checkpointId)
                            Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                            onDeleted()
                        }
                    },
                    colors = CustomButtonDefaults.textButtonColors(contentColor = Color(0xFFE53935))
                ) { CustomText("删除", fontSize = 14.sp) }
            },
            dismissButton = {
                CustomTextButton(
                    onClick = { showDeleteDialog = false },
                    colors = CustomButtonDefaults.textButtonColors(contentColor = TextSecondary)
                ) { CustomText("取消", fontSize = 14.sp) }
            }
        )
    }
}

@Composable
private fun InfoCard(agentId: String, checkpoint: AgentCheckpoint) {
    val timeFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()) }
    val info = buildString {
        append("节点路径: ${checkpoint.nodePath}\n")
        append("创建时间: ${timeFormat.format(Date(checkpoint.createdAt))}\n")
        append("版本: ${checkpoint.version}\n")
        append("消息数: ${checkpoint.messageHistory.size}\n")
        append("检查点 ID: ${checkpoint.checkpointId}\n")
        append("会话 ID: $agentId")
        if (checkpoint.lastInput != null) {
            append("\n最后输入: ${checkpoint.lastInput}")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(16.dp)
    ) {
        CustomText("基本信息", fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        CustomText(text = info, fontSize = 13.sp, color = TextSecondary, lineHeight = 20.sp)
    }
}

@Composable
private fun MessageHistoryCard(checkpoint: AgentCheckpoint) {
    val headerText = if (checkpoint.messageHistory.isEmpty()) {
        "消息历史 (无)"
    } else {
        "消息历史 (${checkpoint.messageHistory.size} 条)"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(16.dp)
    ) {
        CustomText(headerText, fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Bold)

        if (checkpoint.messageHistory.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            checkpoint.messageHistory.forEachIndexed { index, msg ->
                MessageCard(index = index, role = msg.role, content = msg.content)
                if (index < checkpoint.messageHistory.lastIndex) {
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun MessageCard(index: Int, role: String, content: String) {
    val roleLabel = when (role) {
        "system" -> "System"
        "user" -> "User"
        "assistant" -> "AI"
        "tool" -> "Tool"
        "agent_log" -> "Log"
        else -> role
    }
    val roleColor = when (role) {
        "user" -> Color(0xFF2196F3)
        "assistant" -> Color(0xFF4CAF50)
        "system" -> Color(0xFF9E9E9E)
        "tool" -> Color(0xFFFF9800)
        else -> Color(0xFF757575)
    }
    val bgColor = when (role) {
        "user" -> Color(0xFFF3F8FE)
        "assistant" -> Color(0xFFF3FBF3)
        "system" -> Color(0xFFF5F5F5)
        "tool" -> Color(0xFFFFF8E1)
        else -> Color(0xFFFAFAFA)
    }

    var expanded by remember { mutableStateOf(false) }
    val isLong = content.length > 200

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .then(if (isLong) Modifier.clickable { expanded = !expanded } else Modifier)
    ) {
        // 左侧角色色条
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(roleColor, RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 角色标签
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(roleColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    CustomText(
                        text = roleLabel,
                        fontSize = 11.sp,
                        color = roleColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                CustomText(
                    text = "#${index + 1}",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            CustomText(
                text = content,
                fontSize = 13.sp,
                color = TextPrimary,
                lineHeight = 19.sp,
                maxLines = if (isLong && !expanded) 4 else Int.MAX_VALUE
            )
            if (isLong) {
                CustomText(
                    text = if (expanded) "收起 ▲" else "展开全部 (${content.length} 字符) ▼",
                    fontSize = 11.sp,
                    color = Color(0xFF2196F3),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
