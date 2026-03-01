package com.lhzkml.jasmine.viewmodel

import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.Usage

/**
 * 聊天界面 UI 状态
 */
sealed class ChatUiState {
    /** 空闲状态 */
    object Idle : ChatUiState()
    
    /** 发送中 */
    object Sending : ChatUiState()
    
    /** 流式输出中 */
    data class Streaming(val content: String) : ChatUiState()
    
    /** 压缩历史中 */
    object Compressing : ChatUiState()
    
    /** 错误 */
    data class Error(val message: String, val exception: Exception? = null) : ChatUiState()
    
    /** Agent 执行中 */
    data class AgentExecuting(val status: AgentStatus) : ChatUiState()
}

/**
 * Agent 执行状态
 */
data class AgentStatus(
    val currentTool: String? = null,
    val progress: String = "",
    val logs: List<String> = emptyList(),
    val planSteps: List<String> = emptyList()
)

/**
 * 消息流事件
 */
sealed class MessageStreamEvent {
    /** 文本块 */
    data class TextChunk(val content: String, val role: String = "assistant") : MessageStreamEvent()
    
    /** 思考内容 */
    data class Thinking(val content: String) : MessageStreamEvent()
    
    /** 工具调用开始 */
    data class ToolCallStart(val toolName: String, val arguments: String) : MessageStreamEvent()
    
    /** 工具调用结果 */
    data class ToolCallResult(val toolName: String, val result: String) : MessageStreamEvent()
    
    /** 节点进入（图策略） */
    data class NodeEnter(val nodeName: String) : MessageStreamEvent()
    
    /** 节点退出（图策略） */
    data class NodeExit(val nodeName: String, val success: Boolean) : MessageStreamEvent()
    
    /** 边转换（图策略） */
    data class Edge(val from: String, val to: String, val label: String) : MessageStreamEvent()
    
    /** 完成 */
    data class Complete(val message: ChatMessage, val usage: Usage?) : MessageStreamEvent()
    
    /** 错误 */
    data class Error(val exception: Exception) : MessageStreamEvent()
}

/**
 * 消息发送结果
 */
data class MessageSendResult(
    val message: ChatMessage,
    val usage: Usage?,
    val agentLog: String = ""
)

/**
 * 按钮状态
 */
enum class ButtonState {
    IDLE,
    GENERATING,
    COMPRESSING
}
