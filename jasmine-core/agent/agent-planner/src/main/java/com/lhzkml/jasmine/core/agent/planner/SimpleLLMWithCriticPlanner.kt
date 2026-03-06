package com.lhzkml.jasmine.core.agent.planner

import com.lhzkml.jasmine.core.agent.graph.graph.AgentGraphContext
import com.lhzkml.jasmine.core.prompt.model.prompt
import kotlinx.serialization.Serializable

/**
 * 带 Critic 的 LLM 规划器
 *
 * 在 SimpleLLMPlanner 基础上增加计划质量验证：
 * - 在执行前评估计划是否可行、完整、具体
 * - 不通过则自动触发重新规划（最多 maxCriticRetries 次）
 * - 评估维度：可行性、完整性、步骤具体性、依赖顺序、工具匹配
 */
class SimpleLLMWithCriticPlanner(
    maxIterations: Int = 20,
    private val maxCriticRetries: Int = 2
) : SimpleLLMPlanner(maxIterations) {

    @Serializable
    data class PlanEvaluation(
        val score: Int,
        val isAcceptable: Boolean,
        val issues: List<String>,
        val suggestions: List<String>
    )

    /**
     * 公开的计划验证方法。
     * 生成计划后调用此方法评估质量，不通过则重新生成。
     *
     * @return Pair<最终计划, 评估结果（最后一次）>
     */
    suspend fun buildAndValidatePlan(
        context: AgentGraphContext,
        state: String
    ): Pair<SimplePlan, PlanEvaluation?> {
        var plan = buildPlanPublic(context, state, null)
        var lastEvaluation: PlanEvaluation? = null

        repeat(maxCriticRetries) {
            val evaluation = evaluatePlan(context, state, plan)
            lastEvaluation = evaluation

            if (evaluation.isAcceptable) return Pair(plan, evaluation)

            val reason = buildString {
                appendLine("Critic score: ${evaluation.score}/10")
                if (evaluation.issues.isNotEmpty()) {
                    appendLine("Issues:")
                    evaluation.issues.forEach { appendLine("- $it") }
                }
                if (evaluation.suggestions.isNotEmpty()) {
                    appendLine("Suggestions:")
                    evaluation.suggestions.forEach { appendLine("- $it") }
                }
            }
            plan = rebuildWithFeedback(context, state, plan, reason)
        }

        return Pair(plan, lastEvaluation)
    }

    private suspend fun rebuildWithFeedback(
        context: AgentGraphContext,
        state: String,
        currentPlan: SimplePlan,
        criticFeedback: String
    ): SimplePlan {
        val toolNames = context.toolRegistry.descriptors().map { it.name }

        context.session.rewritePrompt { _ ->
            prompt("planner-revision") {
                system(buildString {
                    appendLine("# Plan Revision")
                    appendLine("Your previous plan was evaluated by a critic and needs improvement.")
                    appendLine()

                    if (toolNames.isNotEmpty()) {
                        appendLine("## Available Tools")
                        appendLine(toolNames.joinToString(", "))
                        appendLine()
                    }

                    appendLine("## Previous Plan")
                    appendLine("Goal: ${currentPlan.goal}")
                    currentPlan.steps.forEachIndexed { i, step ->
                        appendLine("${i + 1}. [${step.type}] ${step.description}")
                    }
                    appendLine()

                    appendLine("## Critic Feedback")
                    appendLine(criticFeedback)
                    appendLine()

                    appendLine("## Instructions")
                    appendLine("Create an improved plan that addresses all the issues above.")
                    appendLine("Keep what works, fix what doesn't. Be more specific and actionable.")
                    appendLine()
                    appendLine("## User Request")
                    appendLine(state)
                })
            }
        }

        val result = context.session.requestLLMStructured(
            serializer = SimplePlan.serializer(),
            examples = listOf(
                SimplePlan(
                    goal = "Improved goal based on feedback",
                    steps = mutableListOf(
                        PlanStep("Specific research step with file paths", "research"),
                        PlanStep("Concrete action with tool references", "action"),
                        PlanStep("Verification step with expected outcome", "verify")
                    )
                )
            )
        ).getOrThrow()

        return SimplePlan(
            goal = result.data.goal,
            steps = result.data.steps.toMutableList()
        )
    }

    private suspend fun evaluatePlan(
        context: AgentGraphContext,
        state: String,
        plan: SimplePlan
    ): PlanEvaluation {
        val oldPrompt = context.session.prompt.copy()
        val toolNames = context.toolRegistry.descriptors().map { it.name }

        context.session.rewritePrompt { _ ->
            prompt("critic") {
                system(buildString {
                    appendLine("# Plan Quality Evaluation")
                    appendLine("You are a critical evaluator. Assess the plan below on a 1-10 scale.")
                    appendLine()
                    appendLine("## Evaluation Criteria")
                    appendLine("1. **Completeness** - Does the plan cover all aspects of the task?")
                    appendLine("2. **Specificity** - Are steps concrete and actionable (not vague)?")
                    appendLine("3. **Ordering** - Are steps in the right dependency order?")
                    appendLine("4. **Feasibility** - Can the available tools execute each step?")
                    appendLine("5. **Verification** - Does the plan include validation/testing steps?")
                    appendLine()

                    if (toolNames.isNotEmpty()) {
                        appendLine("## Available Tools")
                        appendLine(toolNames.joinToString(", "))
                        appendLine()
                    }

                    appendLine("## User Request")
                    appendLine(state)
                    appendLine()
                    appendLine("## Plan to Evaluate")
                    appendLine("Goal: ${plan.goal}")
                    plan.steps.forEachIndexed { i, step ->
                        appendLine("${i + 1}. [${step.type}] ${step.description}")
                    }
                    appendLine()
                    appendLine("## Scoring")
                    appendLine("- score 8-10: Acceptable, proceed to execution")
                    appendLine("- score 5-7: Marginal, has issues but workable")
                    appendLine("- score 1-4: Unacceptable, must be revised")
                    appendLine("- Set isAcceptable=true if score >= 7")
                })
            }
        }

        val result = context.session.requestLLMStructured(
            serializer = PlanEvaluation.serializer(),
            examples = listOf(
                PlanEvaluation(
                    score = 8,
                    isAcceptable = true,
                    issues = emptyList(),
                    suggestions = listOf("Consider adding a test step at the end")
                ),
                PlanEvaluation(
                    score = 4,
                    isAcceptable = false,
                    issues = listOf(
                        "Step 2 is too vague - 'implement feature' doesn't specify what to change",
                        "Missing research step - should read existing code first"
                    ),
                    suggestions = listOf(
                        "Add a step to read the relevant source files before making changes",
                        "Break step 2 into specific file edits"
                    )
                )
            )
        ).getOrThrow()

        context.session.rewritePrompt { oldPrompt }
        return result.data
    }

    override suspend fun assessPlan(
        context: AgentGraphContext,
        state: String,
        plan: SimplePlan?
    ): PlanAssessment<SimplePlan> {
        if (plan == null) return PlanAssessment.NoPlan()

        val evaluation = evaluatePlan(context, state, plan)
        return if (evaluation.isAcceptable) {
            PlanAssessment.Continue(plan)
        } else {
            val reason = buildString {
                appendLine("Score: ${evaluation.score}/10")
                evaluation.issues.forEach { appendLine("- $it") }
            }
            PlanAssessment.Replan(plan, reason)
        }
    }
}
