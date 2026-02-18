package com.lhzkml.jasmine.core.agent.tools.a2a.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * A2A Artifact — Agent 在任务执行过程中生成的资源
 * 完整移植 koog 的 Artifact.kt
 *
 * @param artifactId Artifact 唯一标识
 * @param name 可选的人类可读名称
 * @param description 可选描述
 * @param parts 组成 Artifact 的内容部分列表
 * @param extensions 相关扩展 URI 列表
 * @param metadata 扩展元数据
 */
@Serializable
data class Artifact(
    val artifactId: String,
    val name: String? = null,
    val description: String? = null,
    val parts: List<Part>,
    val extensions: List<String>? = null,
    val metadata: JsonObject? = null
)
