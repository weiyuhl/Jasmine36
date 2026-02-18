package com.lhzkml.jasmine.core.agent.tools.planner

import com.lhzkml.jasmine.core.agent.tools.graph.AgentGraphContext

/**
 * Agent 规划器抽象基类
 * 完整移植 koog 的 AIAgentPlanner，定义规划-执行循环。
 *
 * 规划器工作流程：
 * 1. buildPlan — 构建计划
 * 2. executeStep — 执行计划中的一步
 * 3. 重复 1-2 直到 isPlanCompleted 返回 true
 *
 * @param State 状态类型
 * @param Plan 计划类型
 * @param maxIterations 最大迭代次数（防止死循环）
 */
abstract class AgentPlanner<State, Plan>(
    private val maxIterations: Int = 50
) {
    /**
     * 构建计划
     * @param context Agent 上下文
     * @param state 当前状态
     * @param plan 上一轮的计划（首次为 null）
     */
    protected abstract suspend fun buildPlan(
        context: AgentGraphContext,
        state: State,
        plan: Plan?
    ): Plan

    /**
     * 执行计划中的一步
     */
    protected abstract suspend fun executeStep(
        context: AgentGraphContext,
        state: State,
        plan: Plan
    ): State

    /**
     * 检查计划是否已完成
     */
    protected abstract suspend fun isPlanCompleted(
        context: AgentGraphContext,
        state: State,
        plan: Plan
    ): Boolean

    /**
     * 执行规划器主循环
     * @param context Agent 上下文
     * @param input 初始状态
     * @return 最终状态
     */
    suspend fun execute(context: AgentGraphContext, input: State): State {
        var state = input
        var plan: Plan = buildPlan(context, state, null)
        var iterations = 0

        while (!isPlanCompleted(context, state, plan)) {
            iterations++
            if (iterations > maxIterations) {
                throw IllegalStateException(
                    "Planner exceeded max iterations ($maxIterations)"
                )
            }

            state = executeStep(context, state, plan)
            plan = buildPlan(context, state, plan)
        }

        return state
    }
}
