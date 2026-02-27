package com.lhzkml.jasmine.core.config

/**
 * Agent 执行策略类型
 * - SIMPLE_LOOP: 简单 while 循环（ToolExecutor），默认
 * - SINGLE_RUN_GRAPH: 图策略（GraphAgent + singleRunStrategy）
 */
enum class AgentStrategyType {
    SIMPLE_LOOP,
    SINGLE_RUN_GRAPH
}

/**
 * 图策略工具调用模式
 */
enum class GraphToolCallMode {
    SEQUENTIAL,
    PARALLEL,
    SINGLE_RUN_SEQUENTIAL
}

/**
 * 工具选择策略类型
 */
enum class ToolSelectionStrategyType {
    ALL,
    NONE,
    BY_NAME,
    AUTO_SELECT_FOR_TASK
}

/**
 * ToolChoice 模式
 */
enum class ToolChoiceMode {
    DEFAULT,
    AUTO,
    REQUIRED,
    NONE,
    NAMED
}

/**
 * 快照存储方式
 */
enum class SnapshotStorageType {
    MEMORY,
    FILE
}
