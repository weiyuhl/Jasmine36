package com.lhzkml.jasmine.core.agent.graph.graph

/**
 * Agent 生命周期状�?
 * 移植�?koog �?AIAgent.Companion.State，表�?Agent 在执行过程中的状态�?
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

    /** 已完�?*/
    class Finished<Output>(val result: Output) : AgentState<Output> {
        override fun copy(): AgentState<Output> = Finished(result)
    }

    /** 已失�?*/
    class Failed<Output>(val exception: Throwable) : AgentState<Output> {
        override fun copy(): AgentState<Output> = Failed(exception)
    }
}
