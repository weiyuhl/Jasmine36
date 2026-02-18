package com.lhzkml.jasmine.core.agent.tools.planner

import com.lhzkml.jasmine.core.agent.tools.graph.AgentGraphContext

/**
 * 简单 LLM 规划器
 * 完整移植 koog 的 SimpleLLMPlanner，使用 LLM 生成和执行计划。
 *
 * 工作流程：
 * 1. 让 LLM 根据当前状态生成一个带步骤的计划
 * 2. 逐步执行计划中的每一步
 * 3. 每步执行后重新评估计划（可选：带 Critic）
 * 4. 所有步骤完成后返回最终状态
 */

// ========== 数据模型 ==========

/**
 * 计划步骤
 * 参考 koog 的 PlanStep
 */
data class PlanStep(
    val description: String,
    val isCompleted: Boolean = false
)

/**
 * 简单计划
 * 参考 koog 的 SimplePlan
 */
data class SimplePlan(
    val goal: String,
    val steps: MutableList<PlanStep>
)

/**
 * 计划评估结果
 * 参考 koog 的 SimplePlanAssessment
 */
sealed interface PlanAssessment<Plan> {
    /** 需要重新规划 */
    class Replan<Plan>(val currentPlan: Plan, val reason: String) : PlanAssessment<Plan>
    /** 继续执行当前计划 */
    class Continue<Plan>(val currentPlan: Plan) : PlanAssessment<Plan>
    /** 没有计划 */
    class NoPlan<Plan> : PlanAssessment<Plan>
}

// ========== 规划器实现 ==========

/**
 * 简单 LLM 规划器
 * 完整移植 koog 的 SimpleLLMPlanner
 *
 * 使用 LLM 生成计划，逐步执行，支持重新规划。
 * 操作 String 状态。
 */
open class SimpleLLMPlanner(
    maxIterations: Int = 20
) : AgentPlanner<String, SimplePlan>(maxIterations) {

    override suspend fun buildPlan(
        context: AgentGraphContext,
        state: String,
        plan: SimplePlan?
    ): SimplePlan {
        val assessment = assessPlan(context, state, plan)
        if (assessment is PlanAssessment.Continue) {
            return assessment.currentPlan
        }

        val shouldReplan = assessment is PlanAssessment.Replan

        // 构建规划 prompt
        val planPrompt = buildString {
            appendLine("You are a planning agent. Create a detailed plan with steps.")
            appendLine()
            if (shouldReplan) {
                val replanAssessment = assessment as PlanAssessment.Replan
                appendLine("## Previous Plan (failed)")
                appendLine("Goal: ${replanAssessment.currentPlan.goal}")
                appendLine("Steps:")
                replanAssessment.currentPlan.steps.forEach { step ->
                    val status = if (step.isCompleted) "[COMPLETED]" else "[PENDING]"
                    appendLine("  $status ${step.description}")
                }
                appendLine()
                appendLine("Reason for replan: ${replanAssessment.reason}")
                appendLine()
            }
            appendLine("## Task")
            appendLine(state)
            appendLine()
            appendLine("## Instructions")
            appendLine("Create a plan with clear, actionable steps.")
            appendLine("Format each step on a new line, prefixed with '- '")
            appendLine("First line should be the goal, prefixed with 'GOAL: '")
        }

        // 请求 LLM 生成计划
        context.session.appendPrompt { user(planPrompt) }
        val result = context.session.requestLLMWithoutTools()

        // 解析 LLM 响应为 SimplePlan
        return parsePlanFromResponse(result.content)
    }

    /**
     * 评估当前计划是否需要重新规划
     * 默认实现：如果有计划就继续执行
     * 子类可以覆盖此方法实现 Critic 评估
     */
    protected open suspend fun assessPlan(
        context: AgentGraphContext,
        state: String,
        plan: SimplePlan?
    ): PlanAssessment<SimplePlan> {
        return if (plan == null) PlanAssessment.NoPlan() else PlanAssessment.Continue(plan)
    }

    override suspend fun executeStep(
        context: AgentGraphContext,
        state: String,
        plan: SimplePlan
    ): String {
        val currentStep = plan.steps.firstOrNull { !it.isCompleted }
            ?: return "All steps completed!"

        // 执行当前步骤
        context.session.appendPrompt {
            user(buildString {
                appendLine("Execute the following step in the plan:")
                appendLine("Goal: ${plan.goal}")
                appendLine("Current step: ${currentStep.description}")
                appendLine("Current state: $state")
            })
        }

        val result = context.session.requestLLMWithoutTools()

        // 标记步骤完成
        val stepIndex = plan.steps.indexOf(currentStep)
        plan.steps[stepIndex] = currentStep.copy(isCompleted = true)

        return result.content
    }

    override suspend fun isPlanCompleted(
        context: AgentGraphContext,
        state: String,
        plan: SimplePlan
    ): Boolean = plan.steps.all { it.isCompleted }

    /**
     * 从 LLM 响应解析计划
     */
    private fun parsePlanFromResponse(response: String): SimplePlan {
        val lines = response.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val goal = lines.firstOrNull { it.startsWith("GOAL:", ignoreCase = true) }
            ?.substringAfter(":")?.trim()
            ?: lines.firstOrNull()
            ?: "Complete the task"

        val steps = lines
            .filter { it.startsWith("- ") || it.startsWith("* ") || it.matches(Regex("^\\d+\\..*")) }
            .map { line ->
                val desc = line
                    .removePrefix("- ")
                    .removePrefix("* ")
                    .replace(Regex("^\\d+\\.\\s*"), "")
                    .trim()
                PlanStep(description = desc)
            }
            .ifEmpty {
                // 如果没有解析到步骤，把整个响应作为一个步骤
                listOf(PlanStep(description = response.take(200)))
            }

        return SimplePlan(goal = goal, steps = steps.toMutableList())
    }
}
