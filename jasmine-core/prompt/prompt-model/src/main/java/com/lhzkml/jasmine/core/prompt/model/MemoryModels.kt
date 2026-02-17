package com.lhzkml.jasmine.core.prompt.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * 事实类型 — 决定一个概念存储单值还是多值
 * 参考 koog 的 FactType
 */
@Serializable
enum class FactType {
    /** 单值：如"项目使用的构建系统" */
    SINGLE,
    /** 多值：如"项目依赖列表" */
    MULTIPLE
}

/**
 * 概念 — 记忆系统的基本知识单元
 * 参考 koog 的 Concept
 *
 * @param keyword 唯一标识符，用于存储和检索
 * @param description 自然语言描述，帮助 LLM 理解要提取什么信息
 * @param factType 决定存储单值还是多值
 */
@Serializable
data class Concept(
    val keyword: String,
    val description: String,
    val factType: FactType
)

/**
 * 事实 — 存储在记忆系统中的实际数据
 * 参考 koog 的 Fact sealed interface
 */
@Serializable
sealed interface Fact {
    val concept: Concept
    val timestamp: Long
}

/**
 * 单值事实
 * 例如："项目使用 Gradle 作为构建系统"
 */
@Serializable
@SerialName("single")
data class SingleFact(
    override val concept: Concept,
    override val timestamp: Long,
    val value: String
) : Fact

/**
 * 多值事实
 * 例如：项目依赖列表
 */
@Serializable
@SerialName("multiple")
data class MultipleFacts(
    override val concept: Concept,
    override val timestamp: Long,
    val values: List<String>
) : Fact
