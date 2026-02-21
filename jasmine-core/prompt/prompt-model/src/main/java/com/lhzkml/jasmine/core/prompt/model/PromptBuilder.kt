package com.lhzkml.jasmine.core.prompt.model

/**
 * 提示词 DSL 构建器
 * 参考 koog 的 PromptBuilder，支持 system/user/assistant/tool 消息构建
 *
 * ```kotlin
 * val prompt = Prompt.build("chat") {
 *     system("You are a helpful assistant.")
 *     user("What is 2+2?")
 *     assistant("2+2 equals 4.")
 *     tool {
 *         call(id = "call_1", name = "calculator_plus", arguments = """{"a":2,"b":2}""")
 *         result(callId = "call_1", name = "calculator_plus", content = "4.0")
 *     }
 * }
 * ```
 */
@DslMarker
annotation class PromptDSL

@PromptDSL
class PromptBuilder internal constructor(
    private val id: String,
    private val samplingParams: SamplingParams = SamplingParams.DEFAULT,
    private val maxTokens: Int? = null,
    private val toolChoice: ToolChoice? = null
) {
    private val messages = mutableListOf<ChatMessage>()

    internal companion object {
        fun from(prompt: Prompt): PromptBuilder = PromptBuilder(
            prompt.id, prompt.samplingParams, prompt.maxTokens, prompt.toolChoice
        ).apply {
            messages.addAll(prompt.messages)
        }
    }

    /** 添加系统消息 */
    fun system(content: String) {
        messages.add(ChatMessage.system(content))
    }

    /** 添加用户消息 */
    fun user(content: String) {
        messages.add(ChatMessage.user(content))
    }

    /** 添加助手消息 */
    fun assistant(content: String) {
        messages.add(ChatMessage.assistant(content))
    }

    /** 添加带工具调用的助手消息 */
    fun assistantWithToolCalls(toolCalls: List<ToolCall>, content: String = "") {
        messages.add(ChatMessage.assistantWithToolCalls(toolCalls, content))
    }

    /** 添加任意消息 */
    fun message(msg: ChatMessage) {
        messages.add(msg)
    }

    /** 批量添加消息 */
    fun messages(msgs: List<ChatMessage>) {
        messages.addAll(msgs)
    }

    /** 添加 Message 类型的消息 */
    fun message(msg: Message) {
        messages.add(msg.toChatMessage())
    }

    /** 批量添加 Message 类型的消息 */
    @JvmName("addTypedMessages")
    fun messages(msgs: List<Message>) {
        messages.addAll(msgs.map { it.toChatMessage() })
    }

    /** 添加推理/思考消息 */
    fun reasoning(content: String) {
        messages.add(ChatMessage("assistant", content))
    }

    /** 工具消息构建器 */
    @PromptDSL
    inner class ToolMessageBuilder {
        /** 添加工具调用消息（assistant 发出的） */
        fun call(id: String, name: String, arguments: String) {
            val toolCall = ToolCall(id = id, name = name, arguments = arguments)
            this@PromptBuilder.messages.add(
                ChatMessage.assistantWithToolCalls(listOf(toolCall))
            )
        }

        /**
         * 添加 Message.Tool.Call 对象
         * 移植自 koog ToolMessageBuilder.call(Message.Tool.Call)
         */
        fun call(toolCall: Message.Tool.Call) {
            call(toolCall.id ?: "", toolCall.tool, toolCall.content)
        }

        /** 添加工具结果消息 */
        fun result(callId: String, name: String, content: String) {
            this@PromptBuilder.messages.add(
                ChatMessage.toolResult(ToolResult(callId = callId, name = name, content = content))
            )
        }

        /** 添加 ToolResult 对象 */
        fun result(toolResult: ToolResult) {
            this@PromptBuilder.messages.add(ChatMessage.toolResult(toolResult))
        }

        /**
         * 添加 Message.Tool.Result 对象
         * 移植自 koog ToolMessageBuilder.result(Message.Tool.Result)
         *
         * 包含自动合成缺失 tool call 的逻辑:
         * 如果对应的 tool call 不存在于消息列表中，会自动合成一个。
         */
        fun result(toolResult: Message.Tool.Result) {
            val existingCallIndex = this@PromptBuilder.messages
                .indexOfLast { msg ->
                    msg.role == "assistant" &&
                    msg.toolCalls?.any { it.id == toolResult.id } == true
                }

            val resultChatMessage = ChatMessage.toolResult(
                ToolResult(
                    callId = toolResult.id ?: "",
                    name = toolResult.tool,
                    content = toolResult.content
                )
            )

            if (existingCallIndex != -1) {
                this@PromptBuilder.messages.add(existingCallIndex + 1, resultChatMessage)
            } else {
                if (toolResult.id != null) {
                    val synthesizedCall = ChatMessage.assistantWithToolCalls(
                        listOf(ToolCall(
                            id = toolResult.id,
                            name = toolResult.tool,
                            arguments = "Synthesized call for result"
                        ))
                    )
                    this@PromptBuilder.messages.add(synthesizedCall)
                }
                this@PromptBuilder.messages.add(resultChatMessage)
            }
        }
    }

    private val toolBuilder = ToolMessageBuilder()

    /** 工具消息 DSL */
    fun tool(init: ToolMessageBuilder.() -> Unit) {
        toolBuilder.init()
    }

    internal fun build(): Prompt = Prompt(
        messages = messages.toList(),
        id = id,
        samplingParams = samplingParams,
        maxTokens = maxTokens,
        toolChoice = toolChoice
    )
}

/**
 * 顶层 DSL 函数，快捷构建 Prompt
 * ```kotlin
 * val p = prompt("chat") {
 *     system("You are helpful.")
 *     user("Hi!")
 * }
 * ```
 */
fun prompt(
    id: String,
    samplingParams: SamplingParams = SamplingParams.DEFAULT,
    maxTokens: Int? = null,
    init: PromptBuilder.() -> Unit
): Prompt = Prompt.build(id, samplingParams, maxTokens, init)

/**
 * 基于已有 Prompt 追加消息的顶层 DSL
 */
fun prompt(base: Prompt, init: PromptBuilder.() -> Unit): Prompt =
    Prompt.build(base, init)
