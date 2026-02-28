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
    /** 继续执行当前计划 */
    class Continue<Plan>(val currentPlan: Plan) : PlanAssessment<Plan>
    /** 没有计划 */
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

        // 参�?koog：使�?rewritePrompt 构建规划 prompt
        context.session.rewritePrompt { _ ->
            prompt("planner") {
                system(buildString {
                    appendLine("# Main Goal -- Create a Plan")
                    appendLine("You are a planning agent. Your task is to create a detailed plan with steps.")
                    appendLine()

                    if (shouldReplan) {
                        val replanAssessment = planAssessment as PlanAssessment.Replan
                        appendLine("# Previous Plan (failed)")
                        appendLine("Previously it was attempted to solve the problem with another plan, but it has failed")
                        appendLine("Below you'll see the previous plan with the reason for replan")
                        appendLine()
                        appendLine("## Previous Plan Overview")
                        appendLine("Previously, the following plan has been tried:")
                        appendLine()
                        appendLine("### Previous Plan Goal")
                        appendLine("The goal of the previous plan was:")
                        appendLine(replanAssessment.currentPlan.goal)
                        appendLine()
                        appendLine("### Previous Plan Steps")
                        appendLine("The previous plan consisted of the following consecutive steps:")
                        replanAssessment.currentPlan.steps.forEach { step ->
                            if (step.isCompleted) {
                                appendLine("- [COMPLETED!] ${step.description}")
                            } else {
                                appendLine("- ${step.description}")
                            }
                        }
                        appendLine()
                        appendLine("## Reason(s) to Replan")
                        appendLine("The previous plan needs to be revised for the following reason")
                        appendLine("> ${replanAssessment.reason}")
                        appendLine()
                    }

                    appendLine("# What to do next?")
                    appendLine("You need to create a new plan with steps that will solve the user's problem:")
                    appendLine("> $state")

                    if (shouldReplan) {
                        appendLine()
                        appendLine("**Note: Below you'll see some observations from the previous attempt**")
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
                    goal = "The main goal to be achieved by the system",
                    steps = mutableListOf(
                        PlanStep("First step description", isCompleted = true),
                        PlanStep("Second step description", isCompleted = true),
                        PlanStep("Some other action", isCompleted = false),
                        PlanStep("Action to be performed on the step 4", isCompleted = false),
                        PlanStep("Next high-level goal (5)", isCompleted = false),
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
            ?: return "All steps of the plan are completed!"

        // 参�?koog：执行当前步�?
        context.session.appendPrompt {
            system("You are executing a step in a plan. The goal is: ${plan.goal}")
            user("Execute the following step: ${currentStep.description}")
            user("Current state: $state")
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
     * 公开�?buildPlan 方法，供应用层直接调�?
     */
    suspend fun buildPlanPublic(
        context: AgentGraphContext,
        state: String,
        plan: SimplePlan?
    ): SimplePlan = buildPlan(context, state, plan)
}
