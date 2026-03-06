package com.lhzkml.jasmine.core.agent.tools.planner

import com.lhzkml.jasmine.core.agent.tools.graph.AgentGraphContext
import com.lhzkml.jasmine.core.prompt.model.prompt
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

// ========== 数据模型 ==========

@Serializable
data class PlanStep(
    val description: String,
    val type: String = "action",
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    val isCompleted: Boolean = false
)

@Serializable
data class SimplePlan(
    val goal: String,
    val steps: MutableList<PlanStep>
)

sealed interface PlanAssessment<Plan> {
    class Replan<Plan>(val currentPlan: Plan, val reason: String) : PlanAssessment<Plan>
    class Continue<Plan>(val currentPlan: Plan) : PlanAssessment<Plan>
    class NoPlan<Plan> : PlanAssessment<Plan>
}

// ========== 规划器实现 ==========

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

                if (shouldReplan) {
                    context.session.prompt.messages
                        .filter { it.role != "system" }
                        .forEach { message(it) }
                }
            }
        }

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

        return SimplePlan(
            goal = structuredPlanResult.data.goal,
            steps = structuredPlanResult.data.steps.toMutableList()
        )
    }

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
