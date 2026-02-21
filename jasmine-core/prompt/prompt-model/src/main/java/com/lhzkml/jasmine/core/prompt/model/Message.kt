package com.lhzkml.jasmine.core.prompt.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * 消息密封接口
 * 移植自 koog Message
 *
 * 表示与 LLM 交换的消息。消息按类型和角色分类，表示消息的用途和来源。
 * 同时表示发给 LLM 的消息和 LLM 返回的消息。
 *
 * 简化: 不移植 ContentPart 多媒体附件，只保留文本内容
 */
@Serializable
sealed interface Message {
    /**
     * 消息的文本内容
     */
    val content: String

    /**
     * 消息的角色
     */
    val role: MessageRole

    /**
     * 消息的元数据信息
     */
    val metaInfo: MessageMetaInfo

    /**
     * 请求消息 -- 发给 LLM 的消息
     */
    @Serializable
    sealed interface Request : Message {
        override val metaInfo: RequestMetaInfo
    }

    /**
     * 响应消息 -- LLM 返回的消息
     */
    @Serializable
    sealed interface Response : Message {
        override val metaInfo: ResponseMetaInfo

        /**
         * 使用更新后的元数据创建当前 Response 实例的副本
         */
        fun copy(updatedMetaInfo: ResponseMetaInfo): Response
    }

    /**
     * 用户消息
     * 移植自 koog Message.User
     *
     * @property content 用户消息内容
     * @property metaInfo 请求元数据
     */
    @Serializable
    data class User(
        override val content: String,
        override val metaInfo: RequestMetaInfo = RequestMetaInfo.Empty
    ) : Request {
        override val role: MessageRole = MessageRole.User
    }

    /**
     * 系统消息
     * 移植自 koog Message.System
     *
     * @property content 系统消息内容
     * @property metaInfo 请求元数据
     */
    @Serializable
    data class System(
        override val content: String,
        override val metaInfo: RequestMetaInfo = RequestMetaInfo.Empty
    ) : Request {
        override val role: MessageRole = MessageRole.System
    }

    /**
     * 助手消息
     * 移植自 koog Message.Assistant
     *
     * @property content 助手回复内容
     * @property metaInfo 响应元数据
     * @property finishReason 完成原因
     */
    @Serializable
    data class Assistant(
        override val content: String,
        override val metaInfo: ResponseMetaInfo = ResponseMetaInfo.Empty,
        val finishReason: String? = null
    ) : Response {
        override val role: MessageRole = MessageRole.Assistant

        override fun copy(updatedMetaInfo: ResponseMetaInfo): Assistant =
            this.copy(metaInfo = updatedMetaInfo)
    }

    /**
     * 推理/思考消息
     * 移植自 koog Message.Reasoning
     *
     * @property id 推理过程的可选标识符
     * @property encrypted 加密的推理内容
     * @property content 推理内容
     * @property metaInfo 响应元数据
     */
    @Serializable
    data class Reasoning(
        val id: String? = null,
        val encrypted: String? = null,
        override val content: String,
        override val metaInfo: ResponseMetaInfo = ResponseMetaInfo.Empty
    ) : Response {
        override val role: MessageRole = MessageRole.Reasoning

        override fun copy(updatedMetaInfo: ResponseMetaInfo): Reasoning =
            this.copy(metaInfo = updatedMetaInfo)
    }

    /**
     * 工具相关消息
     * 移植自 koog Message.Tool
     */
    @Serializable
    sealed interface Tool : Message {
        /**
         * 工具调用的唯一标识符
         */
        val id: String?

        /**
         * 工具名称
         */
        val tool: String

        /**
         * 工具调用消息 (LLM 发出的响应)
         * 移植自 koog Message.Tool.Call
         *
         * @property id 工具调用 ID
         * @property tool 工具名称
         * @property content 参数 JSON 字符串
         * @property metaInfo 响应元数据
         */
        @Serializable
        data class Call(
            override val id: String?,
            override val tool: String,
            override val content: String,
            override val metaInfo: ResponseMetaInfo = ResponseMetaInfo.Empty
        ) : Tool, Response {
            override val role: MessageRole = MessageRole.Tool

            /**
             * 惰性解析内容为 JSON 对象
             */
            val contentJsonResult: kotlin.Result<JsonObject> by lazy {
                runCatching { Json.parseToJsonElement(content).jsonObject }
            }

            /**
             * 将内容解析为 JSON 对象，解析失败时抛出异常
             */
            val contentJson: JsonObject
                get() = contentJsonResult.getOrThrow()

            override fun copy(updatedMetaInfo: ResponseMetaInfo): Call =
                this.copy(metaInfo = updatedMetaInfo)
        }

        /**
         * 工具执行结果消息 (回传给 LLM 的请求)
         * 移植自 koog Message.Tool.Result
         *
         * @property id 对应的工具调用 ID
         * @property tool 工具名称
         * @property content 执行结果内容
         * @property metaInfo 请求元数据
         */
        @Serializable
        data class Result(
            override val id: String?,
            override val tool: String,
            override val content: String,
            override val metaInfo: RequestMetaInfo = RequestMetaInfo.Empty
        ) : Tool, Request {
            override val role: MessageRole = MessageRole.Tool
        }
    }
}

/**
 * LLM 选择列表类型别名
 * 移植自 koog LLMChoice
 */
typealias LLMChoice = List<Message.Response>
