package com.lhzkml.jasmine.core.agent.graph.graph

import com.lhzkml.jasmine.core.agent.tools.Tool
import com.lhzkml.jasmine.core.prompt.model.Prompt
import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.atomic.AtomicInteger

/**
 * Agent-as-Tool
 * 移植�?koog �?AIAgentTool，将一�?Agent 包装为工具，
 * 使其可以被其�?Agent 作为工具调用，实现多 Agent 协作�?
 *
 * 使用方式�?
 * ```kotlin
 * val subAgentService = GraphAgentService(
 *     client = client,
 *     model = "gpt-4",
 *     toolRegistry = subToolRegistry,
 *     strategy = subStrategy
 * )
 *
 * val agentTool = AgentAsTool(
 *     agentService = subAgentService,
 *     agentName = "code_reviewer",
 *     agentDescription = "Reviews code and provides feedback",
 *     prompt = reviewPrompt
 * )
 *
 * // 注册到主 Agent 的工具列�?
 * mainToolRegistry.register(agentTool)
 * ```
 *
 * @param agentService Agent 服务，用于创建和运行�?Agent
 * @param agentName 工具名称（其�?Agent 看到的名称）
 * @param agentDescription 工具描述（其�?Agent 看到的描述）
 * @param prompt �?Agent 使用�?Prompt
 * @param inputParamName 输入参数名称，默�?"input"
 * @param inputParamDescription 输入参数描述
 * @param parentAgentId �?Agent ID（可选，用于生成�?Agent ID�?
 */
class AgentAsTool(
    private val agentService: AgentService<String, String>,
    private val agentName: String,
    private val agentDescription: String,
    private val prompt: Prompt,
    private val inputParamName: String = "input",
    private val inputParamDescription: String = "Input for the agent",
    private val parentAgentId: String? = null
) : Tool() {

    private val toolCallNumber = AtomicInteger(0)

    override val descriptor = ToolDescriptor(
        name = agentName,
        description = agentDescription,
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = inputParamName,
                description = inputParamDescription,
                type = ToolParameterType.StringType
            )
        )
    )

    private fun nextToolAgentId(): String {
        val num = toolCallNumber.getAndIncrement()
        return if (parentAgentId != null) "$parentAgentId.$num" else "agent-tool-$num"
    }

    override suspend fun execute(arguments: String): String {
        return try {
            val json = Json.parseToJsonElement(arguments).jsonObject
            val input = json[inputParamName]?.jsonPrimitive?.content ?: arguments

            val result = agentService.createAgentAndRun(
                agentInput = input,
                prompt = prompt,
                id = nextToolAgentId()
            )

            result ?: "Agent completed with no result"
        } catch (e: Exception) {
            "Agent execution failed: ${e::class.simpleName}(${e.message})"
        }
    }
}

/**
 * 扩展函数：从 AgentService 创建 Agent-as-Tool
 * 移植�?koog �?createAgentTool()
 */
fun AgentService<String, String>.createAgentTool(
    agentName: String,
    agentDescription: String,
    prompt: Prompt,
    inputParamName: String = "input",
    inputParamDescription: String = "Input for the agent",
    parentAgentId: String? = null
): AgentAsTool {
    return AgentAsTool(
        agentService = this,
        agentName = agentName,
        agentDescription = agentDescription,
        prompt = prompt,
        inputParamName = inputParamName,
        inputParamDescription = inputParamDescription,
        parentAgentId = parentAgentId
    )
}
