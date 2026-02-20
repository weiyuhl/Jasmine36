package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType.StringType

/**
 * 任务完成工具
 * 当 AI 认为已完成用户任务时调用此工具来显式终止 agent loop。
 * 实际的终止逻辑在 ToolExecutor 中处理，此工具仅提供描述符供 LLM 识别。
 *
 * 增强自 AetherLink attempt_completion 的设计理念，
 * 与 Jasmine 现有的隐式终止（LLM 不再调用工具）共存。
 */
object AttemptCompletionTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = ToolExecutor.COMPLETION_TOOL_NAME,
        description = "Call this tool when you have completed the user's task. " +
            "Provide a clear summary of what was done and any follow-up suggestions. " +
            "This is the only way to explicitly signal task completion in agentic mode.",
        requiredParameters = listOf(
            ToolParameterDescriptor("result", "Task completion summary explaining what was done", StringType)
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor("command", "Suggested follow-up action for the user", StringType)
        )
    )

    override suspend fun execute(arguments: String): String {
        // 实际不会被调用，ToolExecutor 会在调用前拦截
        return "Task completed"
    }
}
