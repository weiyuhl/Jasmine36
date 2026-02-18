package com.lhzkml.jasmine.core.agent.tools.planner

import com.lhzkml.jasmine.core.agent.tools.graph.AgentGraphContext

/**
 * GOAP (Goal-Oriented Action Planning) 规划器
 * 完整移植 koog 的 GOAPPlanner，使用 A* 搜索算法找到最优动作序列。
 *
 * GOAP 核心概念：
 * - Action（动作）：有前置条件、效果（belief）、成本、执行函数
 * - Goal（目标）：有完成条件、成本估算、价值函数
 * - Plan（计划）：一系列动作，从当前状态到达目标状态
 *
 * 使用方式：
 * ```kotlin
 * val planner = goap<MyState> {
 *     action(
 *         name = "search",
 *         precondition = { !it.hasResults },
 *         belief = { it.copy(hasResults = true) },
 *         cost = { 1.0 },
 *         execute = { ctx, state -> /* 执行搜索 */ state.copy(hasResults = true) }
 *     )
 *     goal(
 *         name = "findAnswer",
 *         condition = { it.hasAnswer },
 *         cost = { if (it.hasResults) 0.5 else 2.0 }
 *     )
 * }
 * ```
 */

// ========== 实体定义 ==========

/**
 * GOAP 动作
 * 参考 koog 的 Action
 *
 * @param name 动作名称
 * @param description 动作描述
 * @param precondition 前置条件（返回 true 表示可执行）
 * @param belief 乐观估计：执行后状态会变成什么样
 * @param cost 执行成本估算
 * @param execute 实际执行函数
 */
class GOAPAction<State>(
    val name: String,
    val description: String? = null,
    val precondition: (State) -> Boolean,
    val belief: (State) -> State,
    val cost: (State) -> Double,
    val execute: suspend (AgentGraphContext, State) -> State
)

/**
 * GOAP 目标
 * 参考 koog 的 Goal
 *
 * @param name 目标名称
 * @param description 目标描述
 * @param value 目标价值函数（基于达成成本计算价值）
 * @param cost 到达目标的启发式成本估算
 * @param condition 目标完成条件
 */
class GOAPGoal<State>(
    val name: String,
    val description: String? = null,
    val value: (Double) -> Double = { cost -> kotlin.math.exp(-cost) },
    val cost: (State) -> Double = { 1.0 },
    val condition: (State) -> Boolean
)

/**
 * GOAP 计划
 * 参考 koog 的 GOAPPlan
 */
class GOAPPlan<State>(
    val goal: GOAPGoal<State>,
    val actions: List<GOAPAction<State>>,
    val value: Double
)

// ========== 规划器实现 ==========

/**
 * GOAP 规划器
 * 完整移植 koog 的 GOAPPlanner，包含 A* 搜索算法。
 */
class GOAPPlanner<State>(
    private val actions: List<GOAPAction<State>>,
    private val goals: List<GOAPGoal<State>>,
    maxIterations: Int = 50
) : AgentPlanner<State, GOAPPlan<State>>(maxIterations) {

    override suspend fun buildPlan(
        context: AgentGraphContext,
        state: State,
        plan: GOAPPlan<State>?
    ): GOAPPlan<State> = goals
        .mapNotNull { goal -> buildPlanForGoal(state, goal, actions) }
        .minByOrNull { it.value }
        ?: throw IllegalStateException("No valid plan found for state: $state")

    override suspend fun executeStep(
        context: AgentGraphContext,
        state: State,
        plan: GOAPPlan<State>
    ): State {
        val firstAction = plan.actions.firstOrNull()
            ?: throw IllegalStateException("Plan has no actions")
        for (action in actions) {
            if (action === firstAction) return action.execute(context, state)
        }
        throw IllegalStateException("Action not available: ${firstAction.name}")
    }

    override suspend fun isPlanCompleted(
        context: AgentGraphContext,
        state: State,
        plan: GOAPPlan<State>
    ): Boolean = plan.goal.condition(state)

    // ========== A* 搜索 ==========

    private class AStarStep<State>(
        val from: State,
        val action: GOAPAction<State>,
        val cost: Double
    )

    /**
     * A* 搜索算法：为给定目标找到最优动作序列
     * 完整移植 koog 的 buildPlanForGoal
     */
    private fun buildPlanForGoal(
        state: State,
        goal: GOAPGoal<State>,
        actions: List<GOAPAction<State>>
    ): GOAPPlan<State>? {
        val gScore = mutableMapOf<State, Double>().withDefault { Double.MAX_VALUE }
        val fScore = mutableMapOf<State, Double>().withDefault { Double.MAX_VALUE }
        val incomingStep = mutableMapOf<State, AStarStep<State>>()
        val openSet = mutableSetOf<State>()

        gScore[state] = 0.0
        fScore[state] = goal.cost(state)
        openSet.add(state)

        while (openSet.isNotEmpty()) {
            val currentState = openSet.minBy { fScore.getValue(it) }
            openSet.remove(currentState)

            if (goal.condition(currentState)) {
                val plannedActions = mutableListOf<GOAPAction<State>>()
                var step = incomingStep[currentState]
                var cost = 0.0
                while (step != null) {
                    plannedActions.add(step.action)
                    cost += step.cost
                    step = incomingStep[step.from]
                }
                return GOAPPlan(goal, plannedActions.reversed(), goal.value(cost))
            }

            for (action in actions.filter { it.precondition(currentState) }) {
                val newState = action.belief(currentState)
                val stepCost = action.cost(currentState)
                val newGScore = gScore.getValue(currentState) + stepCost

                if (newGScore < gScore.getValue(newState)) {
                    gScore[newState] = newGScore
                    fScore[newState] = newGScore + goal.cost(newState)
                    incomingStep[newState] = AStarStep(currentState, action, stepCost)
                    openSet.add(newState)
                }
            }
        }

        return null
    }
}
