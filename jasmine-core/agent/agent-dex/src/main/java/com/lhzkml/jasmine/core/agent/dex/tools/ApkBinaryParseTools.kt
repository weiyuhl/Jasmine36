package com.lhzkml.jasmine.core.agent.dex.tools

import com.lhzkml.jasmine.core.agent.dex.parser.ArscParser
import com.lhzkml.jasmine.core.agent.dex.parser.BinaryXmlParser
import com.lhzkml.jasmine.core.agent.tools.Tool
import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.zip.ZipFile

/**
 * 解析二进制 AndroidManifest.xml，返回结构化信息
 * 移植自 AetherLink apk_parse_manifest_cpp
 * 用纯 Kotlin 实现二进制 AXML 解析，替代原 C++ native 实现
 */
object ApkParseManifestTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = "apk_parse_manifest_cpp",
        description = "Parses binary AndroidManifest.xml and returns structured info: " +
            "package name, version, SDK versions, permissions, components (activities, services, receivers, providers).",
        requiredParameters = listOf(
            ToolParameterDescriptor("apkPath", "APK file path", StringType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val apkPath = obj["apkPath"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'apkPath'"

        return try {
            val apkFile = File(apkPath)
            if (!apkFile.exists()) return "Error: APK not found: $apkPath"

            val zipFile = ZipFile(apkFile)
            val manifestBytes = try {
                val entry = zipFile.getEntry("AndroidManifest.xml")
                    ?: return "Error: AndroidManifest.xml not found in APK"
                zipFile.getInputStream(entry).use { it.readBytes() }
            } finally {
                zipFile.close()
            }

            val manifest = BinaryXmlParser.parse(manifestBytes)

            buildString {
                appendLine("Package: ${manifest.packageName}")
                appendLine("Version: ${manifest.versionName} (code: ${manifest.versionCode})")
                appendLine("SDK: min=${manifest.minSdk}, target=${manifest.targetSdk}, compile=${manifest.compileSdk}")
                appendLine("Debuggable: ${manifest.debuggable}")
                if (manifest.applicationClass.isNotEmpty()) {
                    appendLine("Application: ${manifest.applicationClass}")
                }
                appendLine()
                appendLine("Permissions (${manifest.permissions.size}):")
                manifest.permissions.forEach { appendLine("  $it") }
                appendLine()
                appendLine("Activities (${manifest.activities.size}):")
                manifest.activities.forEach { a ->
                    val exp = if (a.exported != null) " [exported=${ a.exported}]" else ""
                    appendLine("  ${a.name}$exp")
                }
                appendLine()
                appendLine("Services (${manifest.services.size}):")
                manifest.services.forEach { s ->
                    val exp = if (s.exported != null) " [exported=${s.exported}]" else ""
                    appendLine("  ${s.name}$exp")
                }
                appendLine()
                appendLine("Receivers (${manifest.receivers.size}):")
                manifest.receivers.forEach { r ->
                    val exp = if (r.exported != null) " [exported=${r.exported}]" else ""
                    appendLine("  ${r.name}$exp")
                }
                appendLine()
                appendLine("Providers (${manifest.providers.size}):")
                manifest.providers.forEach { p ->
                    val auth = if (p.authorities != null) " [authorities=${p.authorities}]" else ""
                    appendLine("  ${p.name}$auth")
                }
            }.trimEnd()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

/**
 * 搜索二进制 AndroidManifest.xml 中的属性和值
 * 移植自 AetherLink apk_search_manifest_cpp
 * 用纯 Kotlin 实现二进制 AXML 解析，替代原 C++ native 实现
 */
object ApkSearchManifestTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = "apk_search_manifest_cpp",
        description = "Searches binary AndroidManifest.xml for attributes and values. " +
            "Can filter by attribute name, value, or both.",
        requiredParameters = listOf(
            ToolParameterDescriptor("apkPath", "APK file path", StringType)
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor("attrName", "Attribute name to search (partial match)", StringType),
            ToolParameterDescriptor("value", "Value to search (partial match)", StringType),
            ToolParameterDescriptor("limit", "Max results. Default 50", IntegerType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val apkPath = obj["apkPath"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'apkPath'"
        val attrName = obj["attrName"]?.jsonPrimitive?.content
        val value = obj["value"]?.jsonPrimitive?.content
        val limit = obj["limit"]?.jsonPrimitive?.int ?: 50

        if (attrName.isNullOrEmpty() && value.isNullOrEmpty()) {
            return "Error: At least one of 'attrName' or 'value' must be specified"
        }

        return try {
            val apkFile = File(apkPath)
            if (!apkFile.exists()) return "Error: APK not found: $apkPath"

            val zipFile = ZipFile(apkFile)
            val manifestBytes = try {
                val entry = zipFile.getEntry("AndroidManifest.xml")
                    ?: return "Error: AndroidManifest.xml not found in APK"
                zipFile.getInputStream(entry).use { it.readBytes() }
            } finally {
                zipFile.close()
            }

            val manifest = BinaryXmlParser.parse(manifestBytes)
            val results = BinaryXmlParser.searchAttributes(manifest, attrName, value, limit)

            if (results.isEmpty()) return "No matches found."
            buildString {
                appendLine("Found ${results.size} match(es):")
                results.forEach { m ->
                    appendLine("  <${m.element}> ${m.attribute}=\"${m.value}\"")
                }
            }.trimEnd()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

/**
 * 解析 resources.arsc，返回资源概要信息
 * 移植自 AetherLink apk_parse_arsc_cpp
 * 用纯 Kotlin 实现 ARSC 解析，替代原 C++ native 实现
 */
object ApkParseArscTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = "apk_parse_arsc_cpp",
        description = "Parses resources.arsc and returns resource summary: " +
            "packages, resource types, entry counts, string pool size.",
        requiredParameters = listOf(
            ToolParameterDescriptor("apkPath", "APK file path", StringType)
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val apkPath = obj["apkPath"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'apkPath'"

        return try {
            val apkFile = File(apkPath)
            if (!apkFile.exists()) return "Error: APK not found: $apkPath"

            val zipFile = ZipFile(apkFile)
            val arscBytes = try {
                val entry = zipFile.getEntry("resources.arsc")
                    ?: return "Error: resources.arsc not found in APK"
                zipFile.getInputStream(entry).use { it.readBytes() }
            } finally {
                zipFile.close()
            }

            val summary = ArscParser.parse(arscBytes)

            buildString {
                appendLine("resources.arsc Summary:")
                appendLine("  File size: ${formatSize(summary.fileSize.toLong())}")
                appendLine("  Package count: ${summary.packageCount}")
                appendLine("  Global string pool: ${summary.globalStringCount} strings")
                appendLine()
                for (pkg in summary.packages) {
                    appendLine("Package: ${pkg.name} (id=0x${Integer.toHexString(pkg.id)})")
                    appendLine("  Type names: ${pkg.typeCount}")
                    appendLine("  Key names: ${pkg.keyCount}")
                    appendLine("  Resource types:")
                    for (type in pkg.types) {
                        appendLine("    ${type.name} (id=${type.id}): ${type.entryCount} entries")
                    }
                    appendLine()
                }
            }.trimEnd()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun formatSize(size: Long): String {
        return when {
            size < 1024 -> "${size}B"
            size < 1024 * 1024 -> "${size / 1024}KB"
            else -> "${size / (1024 * 1024)}MB"
        }
    }
}
