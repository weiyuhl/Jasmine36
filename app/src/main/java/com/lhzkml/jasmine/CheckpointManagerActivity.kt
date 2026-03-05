package com.lhzkml.jasmine

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.agent.observe.snapshot.AgentCheckpoint
import com.lhzkml.jasmine.core.agent.runtime.CheckpointService
import com.lhzkml.jasmine.core.agent.runtime.SessionCheckpoints
import com.lhzkml.jasmine.ui.components.*
import com.lhzkml.jasmine.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CheckpointManagerActivity : ComponentActivity() {

    private val checkpointService: CheckpointService? get() = AppConfig.checkpointService()

    private var refreshTrigger = mutableIntStateOf(0)

    private val detailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == CheckpointDetailActivity.RESULT_DELETED) {
            refreshTrigger.intValue++
        } else if (result.resultCode == CheckpointDetailActivity.RESULT_RESTORED) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CheckpointManagerScreen(
                service = checkpointService,
                refreshTrigger = refreshTrigger.intValue,
                onBack = { finish() },
                onOpenDetail = { agentId, cp ->
                    val intent = Intent(this, CheckpointDetailActivity::class.java)
                    intent.putExtra(CheckpointDetailActivity.EXTRA_AGENT_ID, agentId)
                    intent.putExtra(CheckpointDetailActivity.EXTRA_CHECKPOINT_ID, cp.checkpointId)
                    detailLauncher.launch(intent)
                },
                onDeleteSession = { agentId ->
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                        checkpointService?.deleteSession(agentId)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@CheckpointManagerActivity, "会话已清除", Toast.LENGTH_SHORT).show()
                            refreshTrigger.intValue++
                        }
                    }
                },
                onClearAll = {
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                        checkpointService?.clearAll()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@CheckpointManagerActivity, "已清除", Toast.LENGTH_SHORT).show()
                            refreshTrigger.intValue++
                        }
                    }
                }
            )
        }
    }
}

sealed class CheckpointListItem {
    data class SessionHeader(
        val agentId: String,
        val checkpointCount: Int
    ) : CheckpointListItem()

    data class CheckpointEntry(
        val agentId: String,
        val checkpoint: AgentCheckpoint
    ) : CheckpointListItem()
}

@Composable
fun CheckpointManagerScreen(
    service: CheckpointService?,
    refreshTrigger: Int,
    onBack: () -> Unit,
    onOpenDetail: (String, AgentCheckpoint) -> Unit,
    onDeleteSession: (String) -> Unit,
    onClearAll: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<CheckpointListItem>>(emptyList()) }
    var statsText by remember { mutableStateOf("加载中...") }
    var isEmpty by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var sessionToDelete by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshTrigger) {
        if (service == null) {
            isEmpty = true; statsText = "暂无检查点"; return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            val sessions = service.listAllSessions()
            val stats = service.getStats()
            val list = mutableListOf<CheckpointListItem>()
            for (session in sessions) {
                list.add(CheckpointListItem.SessionHeader(session.agentId, session.checkpoints.size))
                for (cp in session.checkpoints) {
                    list.add(CheckpointListItem.CheckpointEntry(session.agentId, cp))
                }
            }
            withContext(Dispatchers.Main) {
                if (list.isEmpty()) {
                    isEmpty = true; statsText = "暂无检查点"; items = emptyList()
                } else {
                    isEmpty = false
                    statsText = "${stats.totalSessions} 个会话, ${stats.totalCheckpoints} 轮对话检查点"
                    items = list
                }
            }
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
                text = "检查点管理",
                fontSize = 17.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            CustomTextButton(
                onClick = { showClearDialog = true },
                colors = CustomButtonDefaults.textButtonColors(contentColor = Color(0xFFE53935))
            ) {
                CustomText("清除全部", fontSize = 13.sp)
            }
        }

        CustomHorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)

        // 统计信息
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CustomText(text = statsText, fontSize = 13.sp, color = TextSecondary)
        }

        CustomHorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)

        // 内容
        if (isEmpty) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CustomText("暂无检查点数据", fontSize = 15.sp, color = TextSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(items, key = { item ->
                    when (item) {
                        is CheckpointListItem.SessionHeader -> "session_${item.agentId}"
                        is CheckpointListItem.CheckpointEntry -> "cp_${item.agentId}_${item.checkpoint.checkpointId}"
                    }
                }) { item ->
                    when (item) {
                        is CheckpointListItem.SessionHeader -> SessionHeaderItem(
                            agentId = item.agentId,
                            count = item.checkpointCount,
                            onDelete = { sessionToDelete = item.agentId }
                        )
                        is CheckpointListItem.CheckpointEntry -> CheckpointCardItem(
                            agentId = item.agentId,
                            checkpoint = item.checkpoint,
                            onClick = { onOpenDetail(item.agentId, item.checkpoint) }
                        )
                    }
                }
            }
        }
    }

    // 清除全部确认对话框
    if (showClearDialog) {
        CustomAlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = Color.White,
            title = { CustomText("清除全部", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = { CustomText("删除所有检查点？此操作不可撤销。", color = TextPrimary, fontSize = 14.sp) },
            confirmButton = {
                CustomTextButton(
                    onClick = { showClearDialog = false; onClearAll() },
                    colors = CustomButtonDefaults.textButtonColors(contentColor = Color(0xFFE53935))
                ) { CustomText("删除", fontSize = 14.sp) }
            },
            dismissButton = {
                CustomTextButton(
                    onClick = { showClearDialog = false },
                    colors = CustomButtonDefaults.textButtonColors(contentColor = TextSecondary)
                ) { CustomText("取消", fontSize = 14.sp) }
            }
        )
    }

    // 删除会话确认对话框
    sessionToDelete?.let { agentId ->
        val displayId = if (agentId.length > 20) agentId.take(20) + "..." else agentId
        CustomAlertDialog(
            onDismissRequest = { sessionToDelete = null },
            containerColor = Color.White,
            title = { CustomText("删除会话", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = { CustomText("删除会话 $displayId 的全部检查点？", color = TextPrimary, fontSize = 14.sp) },
            confirmButton = {
                CustomTextButton(
                    onClick = { sessionToDelete = null; onDeleteSession(agentId) },
                    colors = CustomButtonDefaults.textButtonColors(contentColor = Color(0xFFE53935))
                ) { CustomText("删除", fontSize = 14.sp) }
            },
            dismissButton = {
                CustomTextButton(
                    onClick = { sessionToDelete = null },
                    colors = CustomButtonDefaults.textButtonColors(contentColor = TextSecondary)
                ) { CustomText("取消", fontSize = 14.sp) }
            }
        )
    }
}

@Composable
private fun SessionHeaderItem(
    agentId: String,
    count: Int,
    onDelete: () -> Unit
) {
    val displayId = if (agentId.length > 24) agentId.take(24) + "..." else agentId
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Accent)
        )
        CustomText(
            text = "会话: $displayId",
            fontSize = 13.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 8.dp)
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Accent.copy(alpha = 0.1f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            CustomText(
                text = "$count 个检查点",
                fontSize = 11.sp,
                color = Accent
            )
        }
        CustomText(
            text = "删除",
            fontSize = 12.sp,
            color = Color(0xFFE53935),
            modifier = Modifier
                .padding(start = 10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFE53935).copy(alpha = 0.08f))
                .clickable { onDelete() }
                .padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun CheckpointCardItem(
    agentId: String,
    checkpoint: AgentCheckpoint,
    onClick: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(start = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧色条
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(64.dp)
                .background(Accent, RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // 节点路径 + 时间
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CustomText(
                    text = checkpoint.nodePath,
                    fontSize = 14.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                CustomText(
                    text = timeFormat.format(Date(checkpoint.createdAt)),
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            // 元信息标签
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                InfoTag("${checkpoint.messageHistory.size} 条消息")
                InfoTag("v${checkpoint.version}")
            }

            // 消息预览
            val lastUser = checkpoint.messageHistory.lastOrNull { it.role == "user" }
            val lastAssistant = checkpoint.messageHistory.lastOrNull { it.role == "assistant" }
            if (lastUser != null || lastAssistant != null) {
                val preview = buildString {
                    lastUser?.let { append("[User] ${it.content.take(40)}") }
                    if (lastUser != null && lastAssistant != null) append("\n")
                    lastAssistant?.let { append("[AI] ${it.content.take(40)}") }
                }
                CustomText(
                    text = preview,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // 右箭头
        CustomText(
            text = "›",
            fontSize = 20.sp,
            color = TextSecondary,
            modifier = Modifier.padding(end = 14.dp)
        )
    }
}

@Composable
private fun InfoTag(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFF0F0F0))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        CustomText(text = text, fontSize = 10.sp, color = TextSecondary)
    }
}
