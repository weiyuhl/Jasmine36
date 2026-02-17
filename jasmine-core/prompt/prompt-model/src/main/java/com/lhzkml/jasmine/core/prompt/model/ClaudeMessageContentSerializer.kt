package com.lhzkml.jasmine.core.prompt.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * ClaudeMessageContent 的自定义序列化器
 * - Text → 序列化为 JSON 字符串
 * - Blocks → 序列化为 JSON 数组
 */
object ClaudeMessageContentSerializer : KSerializer<ClaudeMessageContent> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ClaudeMessageContent")

    override fun serialize(encoder: Encoder, value: ClaudeMessageContent) {
        val jsonEncoder = encoder as JsonEncoder
        when (value) {
            is ClaudeMessageContent.Text -> jsonEncoder.encodeJsonElement(JsonPrimitive(value.text))
            is ClaudeMessageContent.Blocks -> jsonEncoder.encodeJsonElement(
                kotlinx.serialization.json.Json.encodeToJsonElement(
                    ListSerializer(ClaudeContentBlock.serializer()), value.blocks
                )
            )
        }
    }

    override fun deserialize(decoder: Decoder): ClaudeMessageContent {
        val jsonDecoder = decoder as JsonDecoder
        val element = jsonDecoder.decodeJsonElement()
        return when {
            element is JsonPrimitive -> ClaudeMessageContent.Text(element.jsonPrimitive.content)
            else -> {
                val blocks = element.jsonArray.map {
                    kotlinx.serialization.json.Json.decodeFromJsonElement(ClaudeContentBlock.serializer(), it)
                }
                ClaudeMessageContent.Blocks(blocks)
            }
        }
    }
}
