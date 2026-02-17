package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType

/**
 * 参考 koog 的 ExitTool，用于结束对话
 */
object ExitTool : Tool() {

    override val descriptor = ToolDescriptor(
        name = "exit",
        description = "Service tool, used by the agent to end conversation on user request or agent decision.",
        requiredParameters = listOf(
            ToolParameterDescriptor("message", "Final message of the agent", ToolParameterType.StringType)
        )
    )

    override suspend fun execute(arguments: String): String {
        return "DONE"
    }
}
