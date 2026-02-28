package com.lhzkml.jasmine.core.agent.planner

import com.lhzkml.jasmine.core.agent.graph.graph.AgentGraphContext
import kotlin.math.exp

/**
 * GOAP 规划�?DSL 构建�?
 * 完整移植 koog �?GOAPPlannerBuilder
 */
@DslMarker
annotation class GOAPBuilderDsl

@GOAPBuilderDsl
class GOAPPlannerBuilder<State> {
    private val actions = mutableListOf<GOAPAction<State>>()
    private val goals = mutableListOf<GOAPGoal<State>>()

    /**
     * 定义一个动�?
     *
     * @param name 动作名称
     * @param description 动作描述
     * @param precondition 前置条件
     * @param belief 乐观估计的状态变�?
     * @param cost 执行成本
     * @param execute 实际执行函数
     */
    fun action(
        name: String,
        description: String? = null,
        precondition: (State) -> Boolean,
        belief: (State) -> State,
        cost: (State) -> Double = { 1.0 },
        execute: suspend (AgentGraphContext, State) -> State
    ) {
        actions.add(GOAPAction(name, description, precondition, belief, cost, execute))
    }

    /**
     * 定义一个目�?
     *
     * @param name 目标名称
     * @param description 目标描述
     * @param value 价值函数（基于成本�?
     * @param cost 启发式成本估�?
     * @param condition 完成条件
     */
    fun goal(
        name: String,
        description: String? = null,
        value: (Double) -> Double = { cost -> exp(-cost) },
        cost: (State) -> Double = { 1.0 },
        condition: (State) -> Boolean
    ) {
        goals.add(GOAPGoal(name, description, value, cost, condition))
    }

    fun build(): GOAPPlanner<State> = GOAPPlanner(actions, goals)
}

/**
 * DSL 入口：构�?GOAP 规划�?
 */
fun <State> goap(
    init: GOAPPlannerBuilder<State>.() -> Unit
): GOAPPlanner<State> {
    return GOAPPlannerBuilder<State>().apply(init).build()
}
