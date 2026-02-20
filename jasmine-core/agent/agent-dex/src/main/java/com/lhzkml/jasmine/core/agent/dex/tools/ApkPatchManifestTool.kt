package com.lhzkml.jasmine.core.agent.dex.tools

import com.lhzkml.jasmine.core.agent.dex.DexSessionManager
import com.lhzkml.jasmine.core.agent.tools.Tool
import com.lhzkml.jasmine.core.prompt.model.ToolDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterDescriptor
import com.lhzkml.jasmine.core.prompt.model.ToolParameterType.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 快速修改 AndroidManifest.xml 的特定属性，无需提供完整 XML
 * 移植自 AetherLink apk_patch_manifest
 */
object ApkPatchManifestTool : Tool() {
    override val descriptor = ToolDescriptor(
        name = "apk_patch_manifest",
        description = "Quickly patches AndroidManifest.xml attributes without providing full XML. " +
            "Supports modifying package, versionCode, versionName, minSdk, targetSdk, debuggable, " +
            "permissions, and components (activity, service, receiver, provider).",
        requiredParameters = listOf(
            ToolParameterDescriptor("apkPath", "APK file path", StringType),
            ToolParameterDescriptor("patches", "Array of patch operations",
                ListType(ObjectType(
                    properties = listOf(
                        ToolParameterDescriptor("type", "Patch type",
                            EnumType(listOf(
                                "package", "versionCode", "versionName",
                                "minSdk", "targetSdk", "application",
                                "permission", "activity", "service",
                                "receiver", "provider", "debuggable"
                            ))),
                        ToolParameterDescriptor("action", "Action type",
                            EnumType(listOf("set", "add", "remove"))),
                        ToolParameterDescriptor("value", "New value", StringType),
                        ToolParameterDescriptor("attributes", "Extra attributes (for component modifications)", StringType)
                    ),
                    requiredProperties = listOf("type", "action")
                )))
        )
    )

    override suspend fun execute(arguments: String): String {
        val obj = Json.parseToJsonElement(arguments).jsonObject
        val apkPath = obj["apkPath"]?.jsonPrimitive?.content
            ?: return "Error: Missing parameter 'apkPath'"
        val patchesArr = obj["patches"]?.jsonArray
            ?: return "Error: Missing parameter 'patches'"

        return try {
            val manifest = DexSessionManager.getManifest(apkPath)
            var xml = manifest.content

            if (xml.startsWith("Binary AndroidManifest.xml")) {
                return "Error: Manifest is binary format. Use apk_parse_manifest_cpp to read, " +
                    "or apk_replace_in_manifest for binary string replacement."
            }

            var appliedCount = 0

            for (patchElement in patchesArr) {
                val patch = patchElement.jsonObject
                val type = patch["type"]?.jsonPrimitive?.content ?: continue
                val action = patch["action"]?.jsonPrimitive?.content ?: continue
                val value = patch["value"]?.jsonPrimitive?.content ?: ""

                val result = applyPatch(xml, type, action, value)
                if (result != null) {
                    xml = result
                    appliedCount++
                }
            }

            if (appliedCount > 0) {
                DexSessionManager.modifyManifest(apkPath, xml)
            }

            "Applied $appliedCount patch(es) to AndroidManifest.xml. APK needs re-signing."
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun applyPatch(xml: String, type: String, action: String, value: String): String? {
        return when (type) {
            "package" -> if (action == "set") {
                xml.replace(Regex("""package="[^"]*""""), """package="$value"""")
            } else null

            "versionCode" -> if (action == "set") {
                xml.replace(
                    Regex("""android:versionCode="[^"]*""""),
                    """android:versionCode="$value""""
                )
            } else null

            "versionName" -> if (action == "set") {
                xml.replace(
                    Regex("""android:versionName="[^"]*""""),
                    """android:versionName="$value""""
                )
            } else null

            "minSdk" -> if (action == "set") {
                xml.replace(
                    Regex("""android:minSdkVersion="[^"]*""""),
                    """android:minSdkVersion="$value""""
                )
            } else null

            "targetSdk" -> if (action == "set") {
                xml.replace(
                    Regex("""android:targetSdkVersion="[^"]*""""),
                    """android:targetSdkVersion="$value""""
                )
            } else null

            "debuggable" -> if (action == "set") {
                if (xml.contains("android:debuggable=")) {
                    xml.replace(
                        Regex("""android:debuggable="[^"]*""""),
                        """android:debuggable="$value""""
                    )
                } else {
                    xml.replace(
                        "<application",
                        """<application android:debuggable="$value""""
                    )
                }
            } else null

            "permission" -> when (action) {
                "add" -> {
                    if (!xml.contains("""android.permission.$value""") &&
                        !xml.contains("""android:name="$value"""")) {
                        val permTag = """    <uses-permission android:name="$value" />"""
                        xml.replace("</manifest>", "$permTag\n</manifest>")
                    } else xml
                }
                "remove" -> {
                    xml.replace(Regex("""[ \t]*<uses-permission[^>]*$value[^>]*/>\s*"""), "")
                }
                else -> null
            }

            "activity", "service", "receiver", "provider" -> when (action) {
                "add" -> {
                    val tag = """        <$type android:name="$value" />"""
                    xml.replace("</application>", "$tag\n    </application>")
                }
                "remove" -> {
                    // 移除自闭合标签
                    var result = xml.replace(
                        Regex("""[ \t]*<$type[^>]*android:name="$value"[^/]*/>\s*"""), ""
                    )
                    // 移除带子元素的标签
                    result = result.replace(
                        Regex("""[ \t]*<$type[^>]*android:name="$value"[^>]*>.*?</$type>\s*""",
                            RegexOption.DOT_MATCHES_ALL), ""
                    )
                    result
                }
                else -> null
            }

            "application" -> if (action == "set") {
                if (xml.contains("android:name=", ignoreCase = false) &&
                    xml.contains("<application")) {
                    xml.replace(
                        Regex("""(<application[^>]*?)android:name="[^"]*""""),
                        """$1android:name="$value""""
                    )
                } else {
                    xml.replace("<application", """<application android:name="$value"""")
                }
            } else null

            else -> null
        }
    }
}
