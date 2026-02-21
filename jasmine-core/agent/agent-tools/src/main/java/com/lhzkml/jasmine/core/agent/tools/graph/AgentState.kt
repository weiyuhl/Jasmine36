package com.lhzkml.jasmine.core.agent.tools.graph

/**
 * Agent 生命周期状态
 * 移植自 koog 的 AIAgent.Companion.State，表示 Agent 在执行过程中的状态。
 */
sealed interface AgentState<Output> {
    fun copy(): AgentState<Output>

    /** 尚未启动 */
    class NotStarted<Output> : AgentState<Output> {
        override fun copy(): AgentState<Output> = NotStarted()
    }

    /** 正在启动 */
    class Starting<Output> : AgentState<Output> {
        override fun copy(): AgentState<Output> = Starting()
    }

    /** 正在运行 */
    class Running<Output> : AgentState<Output> {
        override fun copy(): AgentState<Output> = Running()
    }

    /** 已完成 */
    class Finished<Output>(val result: Output) : AgentState<Output> {
        override fun copy(): AgentState<Output> = Finished(result)
    }

    /** 已失败 */
    class Failed<Output>(val exception: Throwable) : AgentState<Output> {
        override fun copy(): AgentState<Output> = Failed(exception)
    }
}
