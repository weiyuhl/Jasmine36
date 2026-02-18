package com.lhzkml.jasmine.core.agent.tools.planner

import com.lhzkml.jasmine.core.agent.tools.graph.AgentGraphContext
import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.prompt
import kotlinx.serialization.Serializable

/**
 * 带 Critic 的 LLM 规划器
 * 完整移植 koog 的 SimpleLLMWithCriticPlanner。
 *
 * 在 SimpleLLMPlanner 基础上增加了计划评估步骤：
 * 每次执行步骤后，让 LLM 作为 Critic 评估当前计划是否仍然有效，
 * 如果无效则触发重新规划。
 *
 * 使用结构化 JSON 输出（requestLLMStructured）替代文本解析。
 */
class SimpleLLMWithCriticPlanner(
    maxIterations: Int = 20
) : SimpleLLMPlanner(maxIterations) {

    /**
     * 计划评估结构化输出
     * 参考 koog 的 PlanEvaluation
     */
    @Serializable
    private data class PlanEvaluation(
        val shouldContinue: Boolean,
        val reason: String
    )

    override suspend fun assessPlan(
        context: AgentGraphContext,
        state: String,
        plan: SimplePlan?
    ): PlanAssessment<SimplePlan> {
        if (plan == null) return PlanAssessment.NoPlan()

        // 参考 koog：保存原始 prompt，评估后恢复
        val oldPrompt = context.session.prompt.copy()

        // 参考 koog：使用 rewritePrompt 构建 Critic prompt
        context.session.rewritePrompt { _ ->
            prompt("critic") {
                system(buildString {
                    appendLine("# Plan Evaluation Task")
                    appendLine("You are a critical evaluator of plans. Your job is to assess whether the current plan is still valid and should be continued, or if it needs to be replanned.")
                    appendLine()
                    appendLine("## Current Plan")
                    appendLine("Goal: ${plan.goal}")
                    appendLine()
                    appendLine("### Steps:")
                    plan.steps.forEachIndexed { index, step ->
                        if (step.isCompleted) {
                            appendLine("${index + 1}. [COMPLETED] ${step.description}")
                        } else {
                            appendLine("${index + 1}. ${step.description}")
                        }
                    }
                    appendLine()
                    appendLine("## Current State")
                    appendLine("Current state value: $state")
                    appendLine()
                    appendLine("## Evaluation Instructions")
                    appendLine("Please evaluate this plan carefully. Consider:")
                    appendLine("- Is the plan still aligned with the goal?")
                    appendLine("- Are the remaining steps sufficient to achieve the goal?")
                    appendLine("- Has any new information emerged that makes the plan obsolete?")
                    appendLine("- Are there any logical flaws or inefficiencies in the plan?")
                    appendLine("- Are there any steps that might be impossible to execute?")
                    appendLine()
                    appendLine("Provide a structured response with your decision and reasoning.")
                })

                // 参考 koog：保留非 system 消息作为上下文
                oldPrompt.messages
                    .filter { it.role != "system" }
                    .forEach { message(it) }
            }
        }

        // 参考 koog：使用 requestLLMStructured 获取结构化评估
        val evaluationResult = context.session.requestLLMStructured(
            serializer = PlanEvaluation.serializer(),
            examples = listOf(
                PlanEvaluation(
                    shouldContinue = true,
                    reason = "The plan is well-structured and all steps are logical and achievable. " +
                        "The completed steps have made good progress toward the goal and nothing prevents " +
                        "the plan from continuing. " +
                        "Newly discovered observations do not invalidate the plan."
                ),
                PlanEvaluation(
                    shouldContinue = false,
                    reason = "The plan needs to be revised because step 3 is no longer feasible " +
                        "given the new information in the current state.\n" +
                        "Specific information is: ........\n"
                )
            )
        ).getOrThrow()

        // 参考 koog：恢复原始 prompt
        context.session.rewritePrompt { oldPrompt }

        val evaluation = evaluationResult.data

        return if (evaluation.shouldContinue) {
            PlanAssessment.Continue(plan)
        } else {
            PlanAssessment.Replan(plan, reason = evaluation.reason)
        }
    }
}
