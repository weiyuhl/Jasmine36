package com.lhzkml.jasmine

import android.content.Context
import com.lhzkml.jasmine.config.AppConfig
import com.lhzkml.jasmine.config.ProviderManager
import com.lhzkml.jasmine.core.agent.observe.snapshot.AgentCheckpoint
import com.lhzkml.jasmine.core.agent.observe.snapshot.Persistence
import com.lhzkml.jasmine.core.conversation.storage.ConversationRepository
import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 快照/检查点恢复逻辑。
 * 处理执行失败后的检查点恢复选择，以及启动时的检查点恢复提示。
 */
class CheckpointRecovery(
    private val activity: Context,
    private val chatStateManager: ChatStateManager,
    private val messageHistory: MutableList<ChatMessage>,
    private val conversationRepo: ConversationRepository,
    private val autoScroll: () -> Unit,
    private val sendMessage: (String) -> Unit,
    private val showCheckpointRecoveryDialog: suspend (String, String, List<String>) -> Int?,
    private val showStartupRecoveryDialog: suspend (String, String) -> Boolean
) {

    suspend fun tryOfferCheckpointRecovery(
        persistence: Persistence?,
        conversationId: String?,
        error: Exception,
        originalMessage: String
    ) {
        val p = persistence ?: return
        val agentId = conversationId ?: return

        val allCheckpoints = p.getCheckpoints(agentId)
        if (allCheckpoints.isEmpty()) return

        val rollbackStrategy = ProviderManager.getSnapshotRollbackStrategy(activity)
        val strategyName = when (rollbackStrategy) {
            com.lhzkml.jasmine.core.agent.observe.snapshot.RollbackStrategy.RESTART_FROM_NODE -> "重新执行"
            com.lhzkml.jasmine.core.agent.observe.snapshot.RollbackStrategy.SKIP_NODE -> "仅恢复上下文"
            com.lhzkml.jasmine.core.agent.observe.snapshot.RollbackStrategy.USE_DEFAULT_OUTPUT -> "使用默认输出"
        }

        val timeFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        val sortedCps = allCheckpoints.sortedBy { it.createdAt }
        val sortedCpsDesc = sortedCps.reversed()
        val checkpointLabels = sortedCpsDesc.map { cp ->
            val time = timeFormat.format(java.util.Date(cp.createdAt))
            val userMsg = cp.messageHistory.firstOrNull { it.role == "user" }?.content?.take(30) ?: ""
            "[$time] ${cp.nodePath} - $userMsg"
        }
        val errorMsg = "执行失败: ${error.message?.take(100)}\n\n选择要恢复到的对话轮次:"

        val selectedIndex = showCheckpointRecoveryDialog("恢复到历史对话轮次 [$strategyName]", errorMsg, checkpointLabels) ?: return
        val selectedCheckpoint = sortedCpsDesc[selectedIndex]

        val selectedIndexInAsc = sortedCps.indexOf(selectedCheckpoint)
        val rebuiltHistory = rebuildHistoryFromCheckpoints(sortedCps.take(selectedIndexInAsc + 1))

        messageHistory.clear()
        messageHistory.addAll(rebuiltHistory)

        val totalMsgs = rebuiltHistory.size
        val line = "[Snapshot] 恢复到 ${selectedCheckpoint.nodePath} [$totalMsgs 条消息]\n"
        withContext(Dispatchers.Main) {
            chatStateManager.handleSystemLog(line)
            autoScroll()
        }

        when (rollbackStrategy) {
            com.lhzkml.jasmine.core.agent.observe.snapshot.RollbackStrategy.RESTART_FROM_NODE -> {
                withContext(Dispatchers.Main) {
                    sendMessage(originalMessage)
                }
            }
            com.lhzkml.jasmine.core.agent.observe.snapshot.RollbackStrategy.SKIP_NODE -> {
                val skipLine = "[Snapshot] 已恢复到该轮对话状态，可继续发送新消息。\n"
                withContext(Dispatchers.Main) {
                    chatStateManager.handleSystemLog(skipLine)
                }
            }
            com.lhzkml.jasmine.core.agent.observe.snapshot.RollbackStrategy.USE_DEFAULT_OUTPUT -> {
                val defaultReply = "抱歉，之前的处理过程中遇到了问题。已恢复到 [${selectedCheckpoint.nodePath}]。请重新描述您的需求。"
                val assistantMsg = ChatMessage.assistant(defaultReply)
                messageHistory.add(assistantMsg)

                withContext(Dispatchers.Main) {
                    chatStateManager.handleSystemLog("[Snapshot] 使用默认输出恢复\n")
                    chatStateManager.addHistoryAiMessage(
                        blocks = listOf(ContentBlock.Text(defaultReply)),
                        usageLine = "",
                        time = formatTime(System.currentTimeMillis())
                    )
                    autoScroll()
                }
                conversationRepo.addMessage(agentId, assistantMsg)
            }
        }
    }

    suspend fun tryOfferStartupRecovery(conversationId: String) {
        val service = AppConfig.checkpointService() ?: return

        val allCheckpoints = service.getCheckpoints(conversationId)
        if (allCheckpoints.isEmpty()) return

        val totalTurns = allCheckpoints.size
        val currentTurns = messageHistory.count { it.role == "user" }
        if (totalTurns <= currentTurns) return

        val timeFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        val latest = allCheckpoints.maxByOrNull { it.createdAt } ?: return
        val timeStr = timeFormat.format(java.util.Date(latest.createdAt))

        val title = "检测到可恢复的对话状态"
        val message = "此对话有更完整的检查点记录:\n\n" +
                "共 $totalTurns 轮对话检查点\n" +
                "最后轮次: ${latest.nodePath}\n" +
                "时间: $timeStr\n\n" +
                "是否从检查点恢复完整对话？"

        if (!showStartupRecoveryDialog(title, message)) return

        val systemPrompt = ProviderManager.getDefaultSystemPrompt(activity)
        val rebuilt = service.rebuildHistory(conversationId, systemPrompt = systemPrompt)

        withContext(Dispatchers.Main) {
            messageHistory.clear()
            messageHistory.addAll(rebuilt)

            val line = "[Snapshot] 启动恢复: 从 $totalTurns 个检查点重建对话历史 [${rebuilt.size} 条消息]\n\n"
            chatStateManager.handleSystemLog(line)
            autoScroll()
        }
    }

    private fun rebuildHistoryFromCheckpoints(checkpoints: List<AgentCheckpoint>): List<ChatMessage> {
        val systemPrompt = ProviderManager.getDefaultSystemPrompt(activity)
        return Persistence.rebuildHistoryFromCheckpoints(checkpoints, systemPrompt)
    }

    private fun formatTime(timestamp: Long): String {
        return java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    }
}
