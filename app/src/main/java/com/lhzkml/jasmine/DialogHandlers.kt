package com.lhzkml.jasmine

import com.lhzkml.jasmine.core.agent.runtime.ToolRegistryBuilder
import com.lhzkml.jasmine.ui.ChatUiState
import com.lhzkml.jasmine.ui.ToolDialogState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 将 ToolRegistryBuilder 所需的各类用户交互对话框注册到 builder 上。
 *
 * 不再依赖 Activity，改为通过 [MutableStateFlow] 设置对话框状态，
 * 由 Compose UI 层渲染对话框并回传结果。
 */
object DialogHandlers {

    fun register(uiState: MutableStateFlow<ChatUiState>, builder: ToolRegistryBuilder) {
        builder.shellConfirmationHandler = { command, purpose, _ ->
            val deferred = CompletableDeferred<Boolean>()
            uiState.value = uiState.value.copy(
                toolDialog = ToolDialogState.ShellConfirmation(command, purpose) { result ->
                    deferred.complete(result)
                }
            )
            try { deferred.await() } finally {
                uiState.value = uiState.value.copy(toolDialog = null)
            }
        }

        builder.askUserHandler = { question ->
            val deferred = CompletableDeferred<String>()
            uiState.value = uiState.value.copy(
                toolDialog = ToolDialogState.AskUser(question) { answer ->
                    deferred.complete(answer.ifEmpty { "(无回复)" })
                }
            )
            try { deferred.await() } finally {
                uiState.value = uiState.value.copy(toolDialog = null)
            }
        }

        builder.singleSelectHandler = { question, options ->
            val deferred = CompletableDeferred<String>()
            uiState.value = uiState.value.copy(
                toolDialog = ToolDialogState.SingleSelect(question, options) { result ->
                    deferred.complete(result)
                }
            )
            try { deferred.await() } finally {
                uiState.value = uiState.value.copy(toolDialog = null)
            }
        }

        builder.multiSelectHandler = { question, options ->
            val deferred = CompletableDeferred<List<String>>()
            uiState.value = uiState.value.copy(
                toolDialog = ToolDialogState.MultiSelect(question, options) { result ->
                    deferred.complete(result.ifEmpty { listOf("(未选择)") })
                }
            )
            try { deferred.await() } finally {
                uiState.value = uiState.value.copy(toolDialog = null)
            }
        }

        builder.rankPrioritiesHandler = { question, items ->
            val deferred = CompletableDeferred<List<String>>()
            uiState.value = uiState.value.copy(
                toolDialog = ToolDialogState.RankPriorities(question, items) { result ->
                    deferred.complete(result)
                }
            )
            try { deferred.await() } finally {
                uiState.value = uiState.value.copy(toolDialog = null)
            }
        }

        builder.askMultipleQuestionsHandler = { questions ->
            val deferred = CompletableDeferred<List<String>>()
            uiState.value = uiState.value.copy(
                toolDialog = ToolDialogState.AskMultipleQuestions(questions) { answers ->
                    deferred.complete(answers)
                }
            )
            try { deferred.await() } finally {
                uiState.value = uiState.value.copy(toolDialog = null)
            }
        }
    }
}
