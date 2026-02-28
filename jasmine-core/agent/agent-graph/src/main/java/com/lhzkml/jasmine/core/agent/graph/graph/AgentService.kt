package com.lhzkml.jasmine.core.agent.graph.graph

import com.lhzkml.jasmine.core.agent.tools.ToolRegistry
import com.lhzkml.jasmine.core.agent.observe.trace.Tracing
import com.lhzkml.jasmine.core.prompt.llm.ChatClient
import com.lhzkml.jasmine.core.prompt.model.Prompt
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Agent 服务接口
 * 移植�?koog �?AIAgentService，管理同�?Agent 的创建、运行和生命周期�?
 *
 * 一�?AgentService 实例管理一类统一�?Agent，它们服务于相同的目的，
 * 解决相同类型的用户任务。适用于并行创建、管理和跟踪多个 Agent�?
 *
 * @param TInput Agent 输入类型
 * @param TOutput Agent 输出类型
 */
interface AgentService<TInput, TOutput> {
    val client: ChatClient
    val model: String
    val toolRegistry: ToolRegistry
    val tracing: Tracing?

    /** 创建一个新�?Agent */
    suspend fun createAgent(
        id: String? = null,
        prompt: Prompt,
        additionalToolRegistry: ToolRegistry? = null
    ): ManagedAgent<TInput, TOutput>

    /** 创建并运行一�?Agent */
    suspend fun createAgentAndRun(
        agentInput: TInput,
        prompt: Prompt,
        id: String? = null,
        additionalToolRegistry: ToolRegistry? = null
    ): TOutput?

    /** 移除 Agent */
    suspend fun removeAgent(id: String): Boolean

    /** 根据 ID 获取 Agent */
    suspend fun agentById(id: String): ManagedAgent<TInput, TOutput>?

    /** 列出所�?Agent */
    suspend fun listAllAgents(): List<ManagedAgent<TInput, TOutput>>

    /** 列出活跃�?Agent */
    suspend fun listActiveAgents(): List<ManagedAgent<TInput, TOutput>>

    /** 列出已完成的 Agent */
    suspend fun listFinishedAgents(): List<ManagedAgent<TInput, TOutput>>
}

/**
 * 被管理的 Agent 实例
 * 包含 Agent �?ID、状态和执行能力�?
 */
class ManagedAgent<TInput, TOutput>(
    val id: String,
    private val graphAgent: GraphAgent<TInput, TOutput>?,
    private val functionalAgent: FunctionalAgent<TInput, TOutput>?,
    private val prompt: Prompt
) {
    var state: AgentState<TOutput> = AgentState.NotStarted()
        internal set

    suspend fun run(input: TInput): TOutput? {
        state = AgentState.Running()
        return try {
            val result = graphAgent?.run(prompt, input)
                ?: functionalAgent?.run(prompt, input)
            if (result != null) {
                state = AgentState.Finished(result)
            }
            result
        } catch (e: Exception) {
            state = AgentState.Failed(e)
            throw e
        }
    }

    fun isRunning(): Boolean = state is AgentState.Running
    fun isFinished(): Boolean = state is AgentState.Finished
}

/**
 * 图策�?Agent 服务
 * 移植�?koog �?GraphAIAgentService
 */
class GraphAgentService<TInput, TOutput>(
    override val client: ChatClient,
    override val model: String,
    override val toolRegistry: ToolRegistry,
    override val tracing: Tracing? = null,
    private val strategy: AgentStrategy<TInput, TOutput>,
    private val agentId: String = "graph-agent"
) : AgentService<TInput, TOutput> {

    private val managedAgents = mutableMapOf<String, ManagedAgent<TInput, TOutput>>()
    private val mutex = Mutex()

    override suspend fun createAgent(
        id: String?,
        prompt: Prompt,
        additionalToolRegistry: ToolRegistry?
    ): ManagedAgent<TInput, TOutput> = mutex.withLock {
        val agentInstanceId = id ?: UUID.randomUUID().toString()
        val mergedRegistry = if (additionalToolRegistry != null) {
            ToolRegistry.build {
                toolRegistry.allTools().forEach { register(it) }
                additionalToolRegistry.allTools().forEach { register(it) }
            }
        } else {
            toolRegistry
        }

        val graphAgent = GraphAgent(
            client = client,
            model = model,
            strategy = strategy,
            toolRegistry = mergedRegistry,
            tracing = tracing,
            agentId = agentInstanceId
        )

        val managed = ManagedAgent<TInput, TOutput>(
            id = agentInstanceId,
            graphAgent = graphAgent,
            functionalAgent = null,
            prompt = prompt
        )
        managedAgents[agentInstanceId] = managed
        managed
    }

    override suspend fun createAgentAndRun(
        agentInput: TInput,
        prompt: Prompt,
        id: String?,
        additionalToolRegistry: ToolRegistry?
    ): TOutput? {
        val agent = createAgent(id, prompt, additionalToolRegistry)
        return agent.run(agentInput)
    }

    override suspend fun removeAgent(id: String): Boolean = mutex.withLock {
        managedAgents.remove(id) != null
    }

    override suspend fun agentById(id: String): ManagedAgent<TInput, TOutput>? = mutex.withLock {
        managedAgents[id]
    }

    override suspend fun listAllAgents(): List<ManagedAgent<TInput, TOutput>> = mutex.withLock {
        managedAgents.values.toList()
    }

    override suspend fun listActiveAgents(): List<ManagedAgent<TInput, TOutput>> = mutex.withLock {
        managedAgents.values.filter { it.isRunning() }
    }

    override suspend fun listFinishedAgents(): List<ManagedAgent<TInput, TOutput>> = mutex.withLock {
        managedAgents.values.filter { it.isFinished() }
    }
}

/**
 * 函数式策�?Agent 服务
 * 移植�?koog �?FunctionalAIAgentService
 */
class FunctionalAgentService<TInput, TOutput>(
    override val client: ChatClient,
    override val model: String,
    override val toolRegistry: ToolRegistry,
    override val tracing: Tracing? = null,
    private val strategy: FunctionalStrategy<TInput, TOutput>,
    private val agentId: String = "functional-agent"
) : AgentService<TInput, TOutput> {

    private val managedAgents = mutableMapOf<String, ManagedAgent<TInput, TOutput>>()
    private val mutex = Mutex()

    override suspend fun createAgent(
        id: String?,
        prompt: Prompt,
        additionalToolRegistry: ToolRegistry?
    ): ManagedAgent<TInput, TOutput> = mutex.withLock {
        val agentInstanceId = id ?: UUID.randomUUID().toString()
        val mergedRegistry = if (additionalToolRegistry != null) {
            ToolRegistry.build {
                toolRegistry.allTools().forEach { register(it) }
                additionalToolRegistry.allTools().forEach { register(it) }
            }
        } else {
            toolRegistry
        }

        val functionalAgent = FunctionalAgent(
            client = client,
            model = model,
            strategy = strategy,
            toolRegistry = mergedRegistry,
            tracing = tracing,
            agentId = agentInstanceId
        )

        val managed = ManagedAgent<TInput, TOutput>(
            id = agentInstanceId,
            graphAgent = null,
            functionalAgent = functionalAgent,
            prompt = prompt
        )
        managedAgents[agentInstanceId] = managed
        managed
    }

    override suspend fun createAgentAndRun(
        agentInput: TInput,
        prompt: Prompt,
        id: String?,
        additionalToolRegistry: ToolRegistry?
    ): TOutput? {
        val agent = createAgent(id, prompt, additionalToolRegistry)
        return agent.run(agentInput)
    }

    override suspend fun removeAgent(id: String): Boolean = mutex.withLock {
        managedAgents.remove(id) != null
    }

    override suspend fun agentById(id: String): ManagedAgent<TInput, TOutput>? = mutex.withLock {
        managedAgents[id]
    }

    override suspend fun listAllAgents(): List<ManagedAgent<TInput, TOutput>> = mutex.withLock {
        managedAgents.values.toList()
    }

    override suspend fun listActiveAgents(): List<ManagedAgent<TInput, TOutput>> = mutex.withLock {
        managedAgents.values.filter { it.isRunning() }
    }

    override suspend fun listFinishedAgents(): List<ManagedAgent<TInput, TOutput>> = mutex.withLock {
        managedAgents.values.filter { it.isFinished() }
    }
}
