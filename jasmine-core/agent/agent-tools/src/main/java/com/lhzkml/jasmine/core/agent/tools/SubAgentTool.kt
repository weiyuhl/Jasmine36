package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.llm.ChatClient
import com.lhzkml.jasmine.core.prompt.llm.LLMWriteSession
import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.Prompt
import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 子代理工具（对标 Cursor Task 工具）
 *
 * 允许主 Agent 动态创建独立的子 Agent 处理子任务。
 * 子代理拥有独立的 LLMWriteSession 和 ToolExecutor 循环，
 * 根据 subagent_type 获得不同的工具子集，执行完成后将结果返回给父 Agent。
 *
 * @param clientProvider 延迟获取 ChatClient（构建时可能尚未就绪）
 * @param modelProvider 延迟获取当前使用的模型名
 * @param parentRegistry 父级完整工具注册表（用于筛选子集）
 * @param config 子代理配置
 * @param currentDepth 当前嵌套深度（0 = 顶层 Agent 直接调用）
 * @param eventListener 事件监听器，转发子代理中间事件给 UI
 * @param onSubAgentStart 子代理开始执行时回调
 * @param onSubAgentResult 子代理完成时回调
 */
class SubAgentTool(
    private val clientProvider: () -> ChatClient,
    private val modelProvider: () -> String,
    private val parentRegistry: ToolRegistry,
    private val config: SubAgentConfig = SubAgentConfig(),
    private val currentDepth: Int = 0,
    private val eventListener: AgentEventListener? = null,
    private val onSubAgentStart: (suspend (purpose: String, type: String) -> Unit)? = null,
    private val onSubAgentResult: (suspend (purpose: String, result: String) -> Unit)? = null
) : Tool() {

    companion object {
        const val TOOL_NAME = "invoke_subagent"

        private const val SUBAGENT_SYSTEM_PROMPT_TEMPLATE =
            "You are a sub-agent spawned to handle a specific task. " +
            "Complete the task thoroughly and return a clear, concise result. " +
            "Do NOT ask the user questions — work autonomously with the tools available.\n\n" +
            "Task: %s"
    }

    override val descriptor = ToolDescriptor(
        name = TOOL_NAME,
        description = "Launch a new sub-agent to handle complex, multi-step tasks autonomously. " +
            "Each sub-agent runs its own tool-call loop with a dedicated context and returns the result. " +
            "Use this when a task can be decomposed into independent pieces, when exploring different areas " +
            "of the codebase in parallel, or when a specialized tool subset is needed. " +
            "Always include a short description (3-5 words) summarizing what the agent will do. " +
            "The sub-agent does not have access to the user's message or prior context — " +
            "provide a highly detailed task description with all necessary context. " +
            "Specify exactly what information the agent should return.",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                "task",
                "The detailed task for the sub-agent to perform. Be specific about what to do, " +
                    "what files to look at, what information to return, etc. " +
                    "The sub-agent has no prior context, so include all necessary information",
                ToolParameterType.StringType
            ),
            ToolParameterDescriptor(
                "purpose",
                "Brief explanation of why a sub-agent is needed " +
                    "(e.g. 'Explore the authentication module to find where tokens are validated')",
                ToolParameterType.StringType
            )
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor(
                "description",
                "A short (3-5 word) description of what the sub-agent will do (e.g. 'explore auth module')",
                ToolParameterType.StringType
            ),
            ToolParameterDescriptor(
                "subagent_type",
                "Sub-agent type determining available tools: " +
                    "'general' (all tools, default — for researching complex questions and multi-step tasks), " +
                    "'explore' (read-only: read_file, list_directory, find_files, search_by_regex, file_info — " +
                    "fast agent for exploring codebases, finding files, searching code), " +
                    "'shell' (execute_shell_command only — for git operations, command execution, terminal tasks), " +
                    "'web' (web_search, web_scrape, fetch_url — for web research and URL fetching). Default: general",
                ToolParameterType.StringType
            ),
            ToolParameterDescriptor(
                "model",
                "Optional model override for this sub-agent. If not specified, uses the same model as the parent agent",
                ToolParameterType.StringType
            ),
            ToolParameterDescriptor(
                "readonly",
                "If true, restricts the sub-agent to read-only tools regardless of type. Default: false",
                ToolParameterType.BooleanType
            )
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = try {
            Json.parseToJsonElement(arguments).jsonObject
        } catch (e: Exception) {
            return "Error: Invalid JSON arguments: ${e.message}"
        }

        val task = obj["task"]?.jsonPrimitive?.content
            ?: return "Error: Missing required parameter 'task'"
        val purpose = obj["purpose"]?.jsonPrimitive?.content
            ?: return "Error: Missing required parameter 'purpose'"
        val description = obj["description"]?.jsonPrimitive?.content ?: ""
        val subagentType = obj["subagent_type"]?.jsonPrimitive?.content ?: SubAgentType.GENERAL
        val modelOverride = obj["model"]?.jsonPrimitive?.content
        val readonly = obj["readonly"]?.jsonPrimitive?.booleanOrNull ?: false

        if (subagentType !in config.enabledTypes) {
            return "Error: Sub-agent type '$subagentType' is not enabled. Available: ${config.enabledTypes}"
        }

        val childRegistry = buildChildRegistry(subagentType, readonly)
        if (childRegistry.allTools().isEmpty()) {
            return "Error: No tools available for sub-agent type '$subagentType'"
        }

        val displayType = if (description.isNotEmpty()) "$description ($subagentType)" else subagentType
        onSubAgentStart?.invoke(purpose, displayType)

        val client = clientProvider()
        val model = modelOverride ?: modelProvider()

        val systemPrompt = String.format(SUBAGENT_SYSTEM_PROMPT_TEMPLATE, task)
        val prompt = Prompt.build("subagent-$subagentType") {
            system(systemPrompt)
            user(task)
        }

        val childEventListener = if (eventListener != null) {
            object : AgentEventListener {
                override suspend fun onToolCallStart(toolName: String, arguments: String) {
                    eventListener.onToolCallStart("[SubAgent] $toolName", arguments)
                }
                override suspend fun onToolCallResult(toolName: String, result: String) {
                    eventListener.onToolCallResult("[SubAgent] $toolName", result)
                }
                override suspend fun onThinking(content: String) {
                    eventListener.onThinking(content)
                }
                override suspend fun onCompletion(result: String, command: String?) {
                    eventListener.onCompletion(result, command)
                }
            }
        } else null

        val childExecutor = ToolExecutor(
            client = client,
            registry = childRegistry,
            maxIterations = config.maxIterationsPerSubAgent,
            eventListener = childEventListener
        )

        return try {
            val result = childExecutor.execute(prompt, model)
            val resultText = result.content.ifEmpty { "(sub-agent returned empty result)" }
            onSubAgentResult?.invoke(purpose, resultText)
            resultText
        } catch (e: Exception) {
            val errorMsg = "Sub-agent failed: ${e.message}"
            onSubAgentResult?.invoke(purpose, errorMsg)
            errorMsg
        }
    }

    private fun buildChildRegistry(type: String, readonly: Boolean): ToolRegistry {
        val allowedNames = when (type) {
            SubAgentType.EXPLORE -> SubAgentType.EXPLORE_TOOLS
            SubAgentType.SHELL -> SubAgentType.SHELL_TOOLS
            SubAgentType.WEB -> SubAgentType.WEB_TOOLS
            SubAgentType.GENERAL -> {
                val names = parentRegistry.allTools().map { it.name }.toMutableSet()
                if (currentDepth + 1 >= config.maxDepth) {
                    names.remove(TOOL_NAME)
                }
                names
            }
            else -> return ToolRegistry.build {}
        }

        val filteredNames = if (readonly) {
            allowedNames.intersect(SubAgentType.EXPLORE_TOOLS)
        } else {
            allowedNames
        }

        return parentRegistry.subset(filteredNames)
    }
}
