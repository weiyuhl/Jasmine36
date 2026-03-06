package com.lhzkml.jasmine.core.agent.planner

import com.lhzkml.jasmine.core.agent.graph.graph.AgentGraphContext
import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.prompt
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * 简�?LLM 规划�?
 * 完整移植 koog �?SimpleLLMPlanner，使�?LLM 生成和执行计划�?
 *
 * 工作流程�?
 * 1. �?LLM 根据当前状态生成一个带步骤的计划（结构�?JSON 输出�?
 * 2. 逐步执行计划中的每一�?
 * 3. 每步执行后重新评估计划（可选：�?Critic�?
 * 4. 所有步骤完成后返回最终状�?
 */

// ========== 数据模型 ==========

/**
 * 计划步骤
 * 参�?koog �?PlanStep
 */
@Serializable
data class PlanStep(
    val description: String,
    val type: String = "action",
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    val isCompleted: Boolean = false
)

/**
 * 简单计�?
 * 参�?koog �?SimplePlan
 */
@Serializable
data class SimplePlan(
    val goal: String,
    val steps: MutableList<PlanStep>
)

/**
 * 计划评估结果
 * 参�?koog �?SimplePlanAssessment
 */
sealed interface PlanAssessment<Plan> {
    /** 需要重新规�?*/
    class Replan<Plan>(val currentPlan: Plan, val reason: String) : PlanAssessment<Plan>
    class Continue<Plan>(val currentPlan: Plan) : PlanAssessment<Plan>
    class NoPlan<Plan> : PlanAssessment<Plan>
}

// ========== 规划器实�?==========

/**
 * 简�?LLM 规划�?
 * 完整移植 koog �?SimpleLLMPlanner
 *
 * 使用 LLM 结构化输出生成计划，逐步执行，支持重新规划�?
 * 操作 String 状态�?
 */
open class SimpleLLMPlanner(
    maxIterations: Int = 20
) : AgentPlanner<String, SimplePlan>(maxIterations) {

    override suspend fun buildPlan(
        context: AgentGraphContext,
        state: String,
        plan: SimplePlan?
    ): SimplePlan {
        val planAssessment = assessPlan(context, state, plan)
        if (planAssessment is PlanAssessment.Continue) {
            return planAssessment.currentPlan
        }

        val shouldReplan = planAssessment is PlanAssessment.Replan
        val toolNames = context.toolRegistry.descriptors().map { it.name }

        // 参�?koog：使�?rewritePrompt 构建规划 prompt
        context.session.rewritePrompt { _ ->
            prompt("planner") {
                system(buildString {
                    appendLine("# Task Planning")
                    appendLine("You are a task planner. Analyze the user's request and create a structured, actionable plan.")
                    appendLine()

                    if (toolNames.isNotEmpty()) {
                        appendLine("## Available Tools")
                        appendLine("The agent executing this plan has the following tools:")
                        appendLine(toolNames.joinToString(", "))
                        appendLine()
                    }

                    appendLine("## Step Types")
                    appendLine("Each step must have a 'type' field:")
                    appendLine("- \"research\": gather information (read files, search code, explore)")
                    appendLine("- \"action\": make changes (write/edit files, run commands)")
                    appendLine("- \"verify\": validate results (run tests, check output, review changes)")
                    appendLine()

                    appendLine("## Planning Guidelines")
                    appendLine("1. Break down the task into 3-8 concrete, actionable steps.")
                    appendLine("2. Each step should be specific enough that an agent can execute it independently.")
                    appendLine("3. Start with research/analysis steps before making changes.")
                    appendLine("4. Include verification steps after significant changes.")
                    appendLine("5. Reference specific files, tools, or commands when possible.")
                    appendLine("6. Order steps by dependency — earlier steps should not depend on later ones.")
                    appendLine("7. The 'goal' should be a single-sentence summary of the overall objective.")
                    appendLine()

                    if (shouldReplan) {
                        val replanAssessment = planAssessment as PlanAssessment.Replan
                        appendLine("## Previous Plan (needs revision)")
                        appendLine("Goal: ${replanAssessment.currentPlan.goal}")
                        appendLine()
                        replanAssessment.currentPlan.steps.forEachIndexed { i, step ->
                            val status = if (step.isCompleted) "[DONE]" else "[TODO]"
                            appendLine("${i + 1}. $status [${step.type}] ${step.description}")
                        }
                        appendLine()
                        appendLine("**Revision reason:** ${replanAssessment.reason}")
                        appendLine()
                        appendLine("Create an improved plan that addresses the above issues.")
                    } else {
                        appendLine("## User Request")
                        appendLine(state)
                    }
                })

                // 参�?koog：如果是 replan，保留非 system 消息作为上下�?
                if (shouldReplan) {
                    context.session.prompt.messages
                        .filter { it.role != "system" }
                        .forEach { message(it) }
                }
            }
        }

        // 参�?koog：使�?requestLLMStructured 获取结构化计�?
        val structuredPlanResult = context.session.requestLLMStructured(
            serializer = SimplePlan.serializer(),
            examples = listOf(
                SimplePlan(
                    goal = "Add user authentication to the API",
                    steps = mutableListOf(
                        PlanStep("Read existing auth-related files and understand current architecture", "research"),
                        PlanStep("Create User model and database migration", "action"),
                        PlanStep("Implement JWT token generation and validation", "action"),
                        PlanStep("Add auth middleware to protected routes", "action"),
                        PlanStep("Run existing tests and verify no regressions", "verify")
                    )
                )
            )
        ).getOrThrow()

        val newPlan = structuredPlanResult.data

        // 参�?koog：重�?prompt 为执行模式，将计划写�?system 消息
        context.session.rewritePrompt { oldPrompt ->
            prompt("agent") {
                system(buildString {
                    appendLine("# Plan")
                    appendLine("You must follow the following plan to solve the problem:")
                    appendLine()
                    appendLine("## Main Goal:")
                    appendLine(newPlan.goal)
                    appendLine()
                    appendLine("## Plan Steps:")
                    newPlan.steps.forEachIndexed { index, step ->
                        if (step.isCompleted) {
                            appendLine("${index + 1}. [COMPLETED!] ${step.description}")
                        } else {
                            appendLine("${index + 1}. ${step.description}")
                        }
                    }
                })

                oldPrompt.messages
                    .filter { it.role != "system" }
                    .forEach { message(it) }
            }
        }

        return SimplePlan(goal = newPlan.goal, steps = newPlan.steps.toMutableList())
    }

    /**
     * 评估当前计划是否需要重新规�?
     * 默认实现：如果有计划就继续执�?
     * 子类可以覆盖此方法实�?Critic 评估
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
            ?: return "All steps completed."

        // 参�?koog：执行当前步�?
        context.session.appendPrompt {
            user("Execute step: ${currentStep.description}\nCurrent state: $state")
        }

        val result = context.session.requestLLMWithoutTools()
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
     * 公开�?buildPlan 方法，供应用层直接调�?
     */
    suspend fun buildPlanPublic(
        context: AgentGraphContext,
        state: String,
        plan: SimplePlan?
    ): SimplePlan = buildPlan(context, state, plan)

    /**
     * 将计划格式化为可注入 Agent system prompt 的文本
     */
    companion object {
        fun formatPlanForPrompt(plan: SimplePlan): String = buildString {
            appendLine("<task_plan>")
            appendLine("You MUST follow this plan to complete the task. Execute steps in order, skipping completed ones.")
            appendLine()
            appendLine("Goal: ${plan.goal}")
            appendLine()
            plan.steps.forEachIndexed { i, step ->
                val status = if (step.isCompleted) "DONE" else "TODO"
                appendLine("${i + 1}. [$status] [${step.type}] ${step.description}")
            }
            appendLine()
            appendLine("After completing each step, proceed to the next. If a step is blocked, explain why and adapt.")
            appendLine("</task_plan>")
        }
    }
}
