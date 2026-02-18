package com.lhzkml.jasmine.core.agent.tools.a2a.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * 消息或 Artifact 的内容部分
 * 完整移植 koog 的 Parts.kt
 */
sealed interface Part {
    val kind: String
    val metadata: JsonObject?
}

/** 文本部分 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class TextPart(
    val text: String,
    override val metadata: JsonObject? = null
) : Part {
    @EncodeDefault
    override val kind: String = "text"
}

/** 文件部分 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class FilePart(
    val file: FileContent,
    override val metadata: JsonObject? = null
) : Part {
    @EncodeDefault
    override val kind: String = "file"
}

/** 文件内容基接口 */
sealed interface FileContent {
    val name: String?
    val mimeType: String?
}

/** 内联文件（base64 编码） */
@Serializable
data class FileWithBytes(
    val bytes: String,
    override val name: String? = null,
    override val mimeType: String? = null
) : FileContent

/** URI 引用文件 */
@Serializable
data class FileWithUri(
    val uri: String,
    override val name: String? = null,
    override val mimeType: String? = null
) : FileContent

/** 结构化数据部分 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DataPart(
    val data: JsonObject,
    override val metadata: JsonObject? = null
) : Part {
    @EncodeDefault
    override val kind: String = "data"
}
