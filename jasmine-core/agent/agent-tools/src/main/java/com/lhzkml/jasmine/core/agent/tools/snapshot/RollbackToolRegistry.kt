package com.lhzkml.jasmine.core.agent.tools.snapshot

/**
 * 回滚工具注册表
 * 完整移植 koog 的 RollbackToolRegistry，管理工具与其回滚对应工具的关联。
 *
 * 当 Agent 回滚到之前的检查点时，如果某些工具有副作用（如文件写入、API 调用等），
 * 需要执行对应的回滚工具来撤销这些副作用。
 *
 * 使用方式：
 * ```kotlin
 * val registry = RollbackToolRegistry {
 *     registerRollback(
 *         toolName = "writeFile",
 *         rollbackToolName = "deleteFile",
 *         rollbackExecutor = { args -> deleteFile(args) }
 *     )
 * }
 * ```
 */
class RollbackToolRegistry private constructor(
    rollbackToolsMap: Map<String, RollbackToolEntry> = emptyMap()
) {
    /**
     * 回滚工具条目
     * @param originalToolName 原始工具名称
     * @param rollbackToolName 回滚工具名称
     * @param rollbackExecutor 回滚执行函数
     */
    data class RollbackToolEntry(
        val originalToolName: String,
        val rollbackToolName: String,
        val rollbackExecutor: suspend (args: String?) -> Unit
    )

    private val _rollbackToolsMap: MutableMap<String, RollbackToolEntry> =
        rollbackToolsMap.toMutableMap()

    /** 只读视图 */
    val rollbackToolsMap: Map<String, RollbackToolEntry>
        get() = _rollbackToolsMap.toMap()

    /**
     * 根据工具名称获取回滚工具
     * @param toolName 原始工具名称
     * @return 回滚工具条目，如果没有注册则返回 null
     */
    fun getRollbackTool(toolName: String): RollbackToolEntry? =
        _rollbackToolsMap[toolName]

    /**
     * 合并两个注册表
     */
    operator fun plus(other: RollbackToolRegistry): RollbackToolRegistry =
        RollbackToolRegistry(this.rollbackToolsMap + other.rollbackToolsMap)

    /**
     * 添加回滚工具
     * @param toolName 原始工具名称
     * @param entry 回滚工具条目
     */
    fun add(toolName: String, entry: RollbackToolEntry) {
        if (!_rollbackToolsMap.containsKey(toolName)) {
            _rollbackToolsMap[toolName] = entry
        }
    }

    /**
     * 构建器
     */
    class Builder internal constructor() {
        private val rollbackToolsMap = mutableMapOf<String, RollbackToolEntry>()

        /**
         * 注册回滚工具
         * @param toolName 原始工具名称
         * @param rollbackToolName 回滚工具名称
         * @param rollbackExecutor 回滚执行函数
         */
        fun registerRollback(
            toolName: String,
            rollbackToolName: String,
            rollbackExecutor: suspend (args: String?) -> Unit
        ) {
            require(toolName !in rollbackToolsMap) { "Tool \"$toolName\" is already defined" }
            rollbackToolsMap[toolName] = RollbackToolEntry(
                originalToolName = toolName,
                rollbackToolName = rollbackToolName,
                rollbackExecutor = rollbackExecutor
            )
        }

        internal fun build(): RollbackToolRegistry = RollbackToolRegistry(rollbackToolsMap)
    }

    companion object {
        /** DSL 构建 */
        operator fun invoke(init: Builder.() -> Unit): RollbackToolRegistry =
            Builder().apply(init).build()

        /** 空注册表 */
        val EMPTY: RollbackToolRegistry = RollbackToolRegistry(emptyMap())
    }
}
