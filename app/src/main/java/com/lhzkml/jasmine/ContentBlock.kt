package com.lhzkml.jasmine

sealed class ContentBlock {
    data class Text(val content: String) : ContentBlock()
    data class Thinking(val content: String) : ContentBlock()
    data class ToolCall(val toolName: String, val arguments: String) : ContentBlock()
    data class ToolResult(val toolName: String, val result: String) : ContentBlock()
    data class Plan(val goal: String, val steps: List<String>) : ContentBlock()
    data class GraphLog(val content: String) : ContentBlock()
    data class Error(val message: String) : ContentBlock()
    data class SystemLog(val content: String) : ContentBlock()
}
