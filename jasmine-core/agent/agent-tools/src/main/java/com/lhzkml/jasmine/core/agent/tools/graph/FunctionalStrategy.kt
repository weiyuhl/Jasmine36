package com.lhzkml.jasmine.core.agent.tools.graph

/**
 * 函数式 Agent 策略
 * 移植自 koog 的 AIAgentFunctionalStrategy，
 * 允许通过一个挂起函数定义 Agent 的执行逻辑，而不是图结构。
 *
 * 适用于简单的循环逻辑，不需要图结构的场景。
 *
 * 使用方式：
 * ```kotlin
 * val strategy = functionalStrategy<String, String>("chat") { input ->
 *     session.appendPrompt { user(input) }
 *     var result = session.requestLLM()
 *     while (result.hasToolCalls) {
 *         for (call in result.toolCalls) {
 *             val toolResult = environment.executeTool(call)
 *             session.appendPrompt { message(ChatMessage.toolResult(...)) }
 *         }
 *         result = session.requestLLM()
 *     }
 *     result.content
 * }
 * ```
 *
 * @param TInput 输入类型
 * @param TOutput 输出类型
 * @param name 策略名称
 * @param func 执行函数，在 AgentGraphContext 中执行
 */
class FunctionalStrategy<TInput, TOutput>(
    val name: String,
    val func: suspend AgentGraphContext.(TInput) -> TOutput
) {
    /**
     * 执行策略
     */
    suspend fun execute(context: AgentGraphContext, input: TInput): TOutput {
        context.strategyName = name
        return context.func(input)
    }
}

/**
 * DSL 入口：构建函数式策略
 * 移植自 koog 的 functionalStrategy()
 */
fun <TInput, TOutput> functionalStrategy(
    name: String = "funStrategy",
    func: suspend AgentGraphContext.(input: TInput) -> TOutput
): FunctionalStrategy<TInput, TOutput> {
    return FunctionalStrategy(name, func)
}
