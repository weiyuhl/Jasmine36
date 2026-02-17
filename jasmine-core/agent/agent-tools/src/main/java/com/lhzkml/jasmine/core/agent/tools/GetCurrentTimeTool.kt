package com.lhzkml.jasmine.core.agent.tools

import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 获取当前时间工具
 * 参考 koog demo-compose-app 的 CurrentDatetimeTool
 */
object GetCurrentTimeTool : Tool() {

    private val json = Json { ignoreUnknownKeys = true }

    override val descriptor = ToolDescriptor(
        name = "get_current_time",
        description = "Gets the current date and time. Can optionally specify a timezone (e.g. 'Asia/Shanghai', 'America/New_York', 'UTC').",
        optionalParameters = listOf(
            ToolParameterDescriptor(
                name = "timezone",
                description = "IANA timezone ID (e.g. 'Asia/Shanghai', 'UTC'). Defaults to system timezone.",
                type = ToolParameterType.StringType
            )
        )
    )

    override suspend fun execute(arguments: String): String {
        val timezone = try {
            val obj = json.parseToJsonElement(arguments).jsonObject
            obj["timezone"]?.jsonPrimitive?.content
        } catch (_: Exception) { null }

        val zoneId = if (timezone != null) {
            try { ZoneId.of(timezone) }
            catch (_: Exception) { return "Error: Unknown timezone '$timezone'" }
        } else ZoneId.systemDefault()

        val now = LocalDateTime.now(zoneId)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        return "${now.format(formatter)} (${zoneId.id})"
    }
}
