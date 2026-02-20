package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * 获取当前时间工具
 * 增强自 AetherLink @aether/time 的 get_current_time
 * 支持多种格式（locale 本地化、iso ISO 8601、timestamp Unix 时间戳）和时区设置
 */
object GetCurrentTimeTool : Tool() {

    private val json = Json { ignoreUnknownKeys = true }

    override val descriptor = ToolDescriptor(
        name = "get_current_time",
        description = "Gets the current date and time. Supports multiple output formats and timezone.",
        optionalParameters = listOf(
            ToolParameterDescriptor(
                name = "format",
                description = "Output format: locale (localized), iso (ISO 8601), timestamp (Unix timestamp). Defaults to locale.",
                type = ToolParameterType.EnumType(listOf("locale", "iso", "timestamp"))
            ),
            ToolParameterDescriptor(
                name = "timezone",
                description = "IANA timezone ID (e.g. 'Asia/Shanghai', 'UTC'). Defaults to system timezone.",
                type = ToolParameterType.StringType
            )
        )
    )

    override suspend fun execute(arguments: String): String {
        val (format, timezone) = try {
            val obj = json.parseToJsonElement(arguments).jsonObject
            val f = obj["format"]?.jsonPrimitive?.content ?: "locale"
            val tz = obj["timezone"]?.jsonPrimitive?.content
            f to tz
        } catch (_: Exception) {
            "locale" to null
        }

        val zoneId = if (timezone != null) {
            try { ZoneId.of(timezone) }
            catch (_: Exception) { return "Error: Unknown timezone '$timezone'" }
        } else ZoneId.systemDefault()

        val now = ZonedDateTime.now(zoneId)

        val timeString = when (format) {
            "iso" -> now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            "timestamp" -> now.toInstant().toEpochMilli().toString()
            else -> now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        }

        return buildString {
            appendLine("{")
            appendLine("  \"currentTime\": \"$timeString\",")
            appendLine("  \"format\": \"$format\",")
            appendLine("  \"timezone\": \"${zoneId.id}\",")
            appendLine("  \"year\": ${now.year},")
            appendLine("  \"month\": ${now.monthValue},")
            appendLine("  \"day\": ${now.dayOfMonth},")
            appendLine("  \"weekday\": \"${now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINESE)}\",")
            appendLine("  \"hour\": ${now.hour},")
            appendLine("  \"minute\": ${now.minute},")
            appendLine("  \"second\": ${now.second}")
            if (format == "timestamp") {
                // 移除最后的换行和末尾，追加额外字段
                deleteCharAt(length - 1) // 去掉换行
                deleteCharAt(length - 1) // 去掉换行
                appendLine(",")
                appendLine("  \"milliseconds\": ${now.toInstant().toEpochMilli()},")
                appendLine("  \"seconds\": ${now.toInstant().epochSecond}")
            }
            append("}")
        }
    }
}
