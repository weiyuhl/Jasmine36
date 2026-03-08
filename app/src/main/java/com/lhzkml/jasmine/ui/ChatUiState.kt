package com.lhzkml.jasmine.ui

import com.lhzkml.jasmine.core.conversation.storage.ConversationInfo

/**
 * 聊天界面统一状态
 *
 * 将原本分散在 ChatViewModel 中的 30+ 个状态变量统一管理，
 * 通过 StateFlow 驱动 UI 重组。
 */
data class ChatUiState(
    val isGenerating: Boolean = false,

    val currentModelDisplay: String = "",
    val modelList: List<String> = emptyList(),
    val currentModel: String = "",
    val supportsThinkingMode: Boolean = false,
    val isThinkingModeEnabled: Boolean = true,

    val isAgentMode: Boolean = false,
    val workspacePath: String = "",
    val workspaceLabel: String = "",
    val showFileTree: Boolean = false,

    val conversations: List<ConversationInfo> = emptyList(),
    val conversationsEmpty: Boolean = true,

    val userScrolledUp: Boolean = false,
    val scrollToBottomTrigger: Int = 0,

    val checkpointRecoveryDialog: CheckpointRecoveryDialogState? = null,
    val startupRecoveryDialog: StartupRecoveryDialogState? = null,
    val toolDialog: ToolDialogState? = null,

    val navigationEvent: NavigationEvent? = null,
    val toastMessage: String? = null,

    /** 抽屉打开请求（一次性，UI 消费后需清除） */
    val requestOpenDrawerEnd: Boolean = false,
    val requestOpenDrawerStart: Boolean = false,

    val error: String? = null,
    val isLoading: Boolean = false
)

data class CheckpointRecoveryDialogState(
    val title: String,
    val message: String,
    val labels: List<String>,
    val onSelect: (Int?) -> Unit
)

data class StartupRecoveryDialogState(
    val title: String,
    val message: String,
    val onConfirm: (Boolean) -> Unit
)

/**
 * 工具交互对话框状态——Agent 工具通过 ViewModel 设置此状态，
 * Compose UI 渲染对话框并通过回调返回结果。
 */
sealed class ToolDialogState {
    data class ShellConfirmation(
        val command: String,
        val purpose: String,
        val onResult: (Boolean) -> Unit
    ) : ToolDialogState()

    data class AskUser(
        val question: String,
        val onResult: (String) -> Unit
    ) : ToolDialogState()

    data class SingleSelect(
        val question: String,
        val options: List<String>,
        val onResult: (String) -> Unit
    ) : ToolDialogState()

    data class MultiSelect(
        val question: String,
        val options: List<String>,
        val onResult: (List<String>) -> Unit
    ) : ToolDialogState()

    data class RankPriorities(
        val question: String,
        val items: List<String>,
        val onResult: (List<String>) -> Unit
    ) : ToolDialogState()

    data class AskMultipleQuestions(
        val questions: List<String>,
        val onResult: (List<String>) -> Unit
    ) : ToolDialogState()
}

/**
 * 导航事件——ViewModel 通过更新此字段通知 UI 层执行导航，
 * UI 层处理后调用 ClearNavigationEvent 清除。
 */
sealed class NavigationEvent {
    object Settings : NavigationEvent()
    data class ProviderConfig(val providerId: String, val tab: Int = 0) : NavigationEvent()
    object Launcher : NavigationEvent()
}

sealed class ChatUiEvent {
    data class SendMessage(val text: String) : ChatUiEvent()
    object StopGeneration : ChatUiEvent()

    data class SelectModel(val model: String) : ChatUiEvent()
    data class SetThinkingMode(val enabled: Boolean) : ChatUiEvent()

    data class LoadConversation(val id: String) : ChatUiEvent()
    object NewConversation : ChatUiEvent()
    data class DeleteConversation(val info: ConversationInfo) : ChatUiEvent()

    object CloseWorkspace : ChatUiEvent()

    object OpenSettings : ChatUiEvent()
    object OpenDrawerEnd : ChatUiEvent()
    object OpenDrawerStart : ChatUiEvent()
    object ClearDrawerRequestEnd : ChatUiEvent()
    object ClearDrawerRequestStart : ChatUiEvent()

    data class UserScrolledUp(val scrolledUp: Boolean) : ChatUiEvent()
    object ClearNavigationEvent : ChatUiEvent()
    object ClearToastMessage : ChatUiEvent()
}
