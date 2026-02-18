package com.lhzkml.jasmine.core.agent.tools.planner

import com.lhzkml.jasmine.core.agent.tools.graph.AgentGraphContext

/**
 * 带 Critic 的 LLM 规划器
 * 完整移植 koog 的 SimpleLLMWithCriticPlanner。
 *
 * 在 SimpleLLMPlanner 基础上增加了计划评估步骤：
 * 每次执行步骤后，让 LLM 作为 Critic 评估当前计划是否仍然有效，
 * 如果无效则触发重新规划。
 */
class SimpleLLMWithCriticPlanner(
    maxIterations: Int = 20
) : SimpleLLMPlanner(maxIterations) {

    override suspend fun assessPlan(
        context: AgentGraphContext,
        state: String,
        plan: SimplePlan?
    ): PlanAssessment<SimplePlan> {
        if (plan == null) return PlanAssessment.NoPlan()

        // 构建 Critic 评估 prompt
        val criticPrompt = buildString {
            appendLine("## Plan Evaluation Task")
            appendLine("You are a critical evaluator. Assess whether the current plan should continue or be replanned.")
            appendLine()
            appendLine("## Current Plan")
            appendLine("Goal: ${plan.goal}")
            appendLine("Steps:")
            plan.steps.forEach { step ->
                val status = if (step.isCompleted) "[COMPLETED]" else "[PENDING]"
                appendLine("  $status ${step.description}")
            }
            appendLine()
            appendLine("## Current State")
            appendLine(state)
            appendLine()
            appendLine("## Evaluation Criteria")
            appendLine("- Is the plan still aligned with the goal?")
            appendLine("- Are the remaining steps sufficient?")
            appendLine("- Has new information made the plan obsolete?")
            appendLine("- Are there logical flaws or inefficiencies?")
            appendLine()
            appendLine("## Response Format")
            appendLine("First line: CONTINUE or REPLAN")
            appendLine("Second line onwards: Your reasoning")
        }

        context.session.appendPrompt { user(criticPrompt) }
        val result = context.session.requestLLMWithoutTools()

        val response = result.content.trim()
        val firstLine = response.lines().firstOrNull()?.trim()?.uppercase() ?: "CONTINUE"

        return if (firstLine.startsWith("REPLAN")) {
            val reason = response.lines().drop(1).joinToString("\n").trim()
                .ifEmpty { "Critic determined replanning is needed" }
            PlanAssessment.Replan(plan, reason)
        } else {
            PlanAssessment.Continue(plan)
        }
    }
}
