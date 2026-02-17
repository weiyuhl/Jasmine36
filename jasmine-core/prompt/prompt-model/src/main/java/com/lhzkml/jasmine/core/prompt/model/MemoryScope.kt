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
 * 记忆主题 — 定义记忆的上下文领域
 * 参考 koog 的 MemorySubject（abstract class + 注册机制）
 *
 * @param name 主题名称（如 "user", "project"）
 * @param promptDescription 发送给 LLM 的描述，帮助 LLM 理解该主题包含什么信息
 * @param priorityLevel 优先级，数字越小优先级越高。高优先级主题的信息优先于低优先级。
 */
@Serializable(with = MemorySubject.Serializer::class)
abstract class MemorySubject {
    abstract val name: String
    abstract val promptDescription: String
    abstract val priorityLevel: Int

    companion object {
        /** 内置主题（按 name 索引，确保始终可用） */
        private val builtInSubjects: Map<String, MemorySubject> by lazy {
            mapOf(
                User.name to User,
                Project.name to Project,
                Machine.name to Machine,
                Everything.name to Everything
            )
        }

        /** 所有已注册的主题（包括内置 + 自定义） */
        val registeredSubjects: MutableList<MemorySubject> = mutableListOf()

        /**
         * 按名称查找主题
         * 优先查内置主题（避免懒加载问题），再查注册表
         */
        fun findByName(name: String): MemorySubject? {
            return builtInSubjects[name]
                ?: registeredSubjects.find { it.name == name }
        }
    }

    init {
        // 自动注册（避免重复注册同名主题）
        if (registeredSubjects.none { it.name == this.name }) {
            registeredSubjects.add(this)
        }
    }

    /** 自定义序列化器 — 按 name 序列化/反序列化 */
    internal object Serializer : KSerializer<MemorySubject> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("MemorySubject", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: MemorySubject) {
            encoder.encodeString(value.name)
        }

        override fun deserialize(decoder: Decoder): MemorySubject {
            val name = decoder.decodeString()
            return findByName(name)
                ?: throw IllegalArgumentException("No MemorySubject found with name: $name")
        }
    }

    /** 用户偏好、设置、行为模式 */
    @Serializable
    data object User : MemorySubject() {
        override val name: String = "user"
        override val promptDescription: String =
            "User's preferences, settings, behavior patterns, expectations from the agent, preferred messaging style, etc."
        override val priorityLevel: Int = 10
    }

    /** 项目配置、依赖、构建设置 */
    @Serializable
    data object Project : MemorySubject() {
        override val name: String = "project"
        override val promptDescription: String =
            "Project configuration, dependencies, build settings, structure, and coding conventions"
        override val priorityLevel: Int = 20
    }

    /** 机器环境信息 */
    @Serializable
    data object Machine : MemorySubject() {
        override val name: String = "machine"
        override val promptDescription: String =
            "Machine environment, OS, installed tools, SDKs, and system configuration"
        override val priorityLevel: Int = 30
    }

    /** 通用 — 所有重要信息（最低优先级） */
    @Serializable
    data object Everything : MemorySubject() {
        override val name: String = "everything"
        override val promptDescription: String = "All important information and meaningful facts"
        override val priorityLevel: Int = Int.MAX_VALUE
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MemorySubject) return false
        return name == other.name
    }

    override fun hashCode(): Int = name.hashCode()

    override fun toString(): String = "MemorySubject($name)"
}

/**
 * 记忆作用域 — 定义记忆的可见性边界
 * 参考 koog 的 MemoryScope
 */
@Serializable
sealed interface MemoryScope {
    /** Agent 级别 — 仅当前 agent 可见 */
    @Serializable
    @SerialName("agent")
    data class Agent(val name: String) : MemoryScope

    /** Feature 级别 — 同一功能的所有 agent 可见 */
    @Serializable
    @SerialName("feature")
    data class Feature(val id: String) : MemoryScope

    /** Product 级别 — 同一产品内所有功能可见 */
    @Serializable
    @SerialName("product")
    data class Product(val name: String) : MemoryScope

    /** 跨产品 — 全局可见 */
    @Serializable
    @SerialName("cross_product")
    data object CrossProduct : MemoryScope
}

/**
 * 记忆作用域类型枚举
 * 参考 koog 的 MemoryScopeType
 */
@Serializable
enum class MemoryScopeType {
    /** 产品级别 */
    PRODUCT,
    /** Agent 级别 */
    AGENT,
    /** Feature 级别 */
    FEATURE,
    /** 组织级别 */
    ORGANIZATION
}

/**
 * 记忆作用域配置文件
 * 参考 koog 的 MemoryScopesProfile
 *
 * 维护 MemoryScopeType → 名称 的映射，用于将抽象的 scope type 转换为具体的 MemoryScope 实例。
 */
@Serializable
data class MemoryScopesProfile(
    val names: MutableMap<MemoryScopeType, String> = mutableMapOf()
) {
    constructor(vararg scopeNames: Pair<MemoryScopeType, String>) : this(
        scopeNames.toMap().toMutableMap()
    )

    /** 获取指定类型的名称 */
    fun nameOf(type: MemoryScopeType): String? = names[type]

    /** 将 MemoryScopeType 转换为具体的 MemoryScope 实例 */
    fun getScope(type: MemoryScopeType): MemoryScope? {
        val name = nameOf(type) ?: return null
        return when (type) {
            MemoryScopeType.PRODUCT -> MemoryScope.Product(name)
            MemoryScopeType.AGENT -> MemoryScope.Agent(name)
            MemoryScopeType.FEATURE -> MemoryScope.Feature(name)
            MemoryScopeType.ORGANIZATION -> MemoryScope.CrossProduct
        }
    }
}
