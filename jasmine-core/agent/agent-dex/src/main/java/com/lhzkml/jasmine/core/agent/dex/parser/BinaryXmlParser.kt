package com.lhzkml.jasmine.core.agent.dex.parser

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Android 二进制 XML (AXML) 解析器
 * 用于解析 APK 中的 AndroidManifest.xml 等二进制 XML 文件
 *
 * 二进制 XML 格式：
 * - Header (magic + fileSize)
 * - StringPool chunk
 * - ResourceId chunk (可选)
 * - XML nodes (StartNamespace, StartElement, EndElement, EndNamespace, Text)
 */
object BinaryXmlParser {

    // Chunk types
    private const val CHUNK_AXML_FILE = 0x00080003
    private const val CHUNK_STRING_POOL = 0x001C0001
    private const val CHUNK_RESOURCE_IDS = 0x00080180
    private const val CHUNK_START_NAMESPACE = 0x00100100
    private const val CHUNK_END_NAMESPACE = 0x00100101
    private const val CHUNK_START_ELEMENT = 0x00100102
    private const val CHUNK_END_ELEMENT = 0x00100103
    private const val CHUNK_TEXT = 0x00100104

    // Attribute value types
    private const val TYPE_NULL = 0x00
    private const val TYPE_REFERENCE = 0x01
    private const val TYPE_ATTRIBUTE = 0x02
    private const val TYPE_STRING = 0x03
    private const val TYPE_FLOAT = 0x04
    private const val TYPE_DIMENSION = 0x05
    private const val TYPE_FRACTION = 0x06
    private const val TYPE_INT_DEC = 0x10
    private const val TYPE_INT_HEX = 0x11
    private const val TYPE_INT_BOOLEAN = 0x12

    /**
     * 解析二进制 XML 为结构化数据
     */
    fun parse(data: ByteArray): ParsedManifest {
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        // 验证 magic
        val magic = buf.int
        if (magic != CHUNK_AXML_FILE) {
            throw IllegalArgumentException("Not a valid binary XML file (magic: 0x${Integer.toHexString(magic)})")
        }
        val fileSize = buf.int

        // 解析字符串池
        val strings = mutableListOf<String>()
        val resourceIds = mutableListOf<Int>()
        val elements = mutableListOf<XmlElement>()
        val namespaces = mutableMapOf<String, String>() // uri -> prefix

        var packageName = ""
        var versionCode = ""
        var versionName = ""
        var minSdk = ""
        var targetSdk = ""
        var compileSdk = ""
        val permissions = mutableListOf<String>()
        val activities = mutableListOf<ComponentInfo>()
        val services = mutableListOf<ComponentInfo>()
        val receivers = mutableListOf<ComponentInfo>()
        val providers = mutableListOf<ComponentInfo>()
        var applicationClass = ""
        var debuggable = false

        while (buf.hasRemaining()) {
            val chunkStart = buf.position()
            if (buf.remaining() < 8) break

            val chunkType = buf.int
            val chunkSize = buf.int

            when (chunkType) {
                CHUNK_STRING_POOL -> {
                    parseStringPool(buf, chunkStart, chunkSize, strings)
                }
                CHUNK_RESOURCE_IDS -> {
                    val count = (chunkSize - 8) / 4
                    for (i in 0 until count) {
                        resourceIds.add(buf.int)
                    }
                }
                CHUNK_START_NAMESPACE -> {
                    // lineNumber, comment, prefix, uri
                    buf.int // lineNumber
                    buf.int // comment
                    val prefixIdx = buf.int
                    val uriIdx = buf.int
                    val prefix = strings.getOrElse(prefixIdx) { "" }
                    val uri = strings.getOrElse(uriIdx) { "" }
                    namespaces[uri] = prefix
                }
                CHUNK_END_NAMESPACE -> {
                    // lineNumber, comment, prefix, uri
                    buf.int; buf.int; buf.int; buf.int
                }
                CHUNK_START_ELEMENT -> {
                    val lineNumber = buf.int
                    buf.int // comment
                    val nsIdx = buf.int
                    val nameIdx = buf.int
                    buf.short // attrStart
                    buf.short // attrSize
                    val attrCount = buf.short.toInt() and 0xFFFF
                    buf.short // idIndex
                    buf.short // classIndex
                    buf.short // styleIndex

                    val elemName = strings.getOrElse(nameIdx) { "?" }
                    val attrs = mutableListOf<XmlAttribute>()

                    for (i in 0 until attrCount) {
                        val attrNsIdx = buf.int
                        val attrNameIdx = buf.int
                        val attrValueStringIdx = buf.int
                        val attrType = (buf.int shr 24) and 0xFF
                        val attrData = buf.int

                        val attrNs = if (attrNsIdx >= 0) strings.getOrElse(attrNsIdx) { "" } else ""
                        val attrName = strings.getOrElse(attrNameIdx) { "?" }
                        val attrValue = resolveAttributeValue(
                            attrType, attrData, attrValueStringIdx, strings
                        )

                        attrs.add(XmlAttribute(attrNs, attrName, attrValue, attrType))

                        // 提取关键信息
                        extractManifestInfo(
                            elemName, attrName, attrValue, attrType, attrData,
                            packageName, versionCode, versionName, minSdk, targetSdk,
                            compileSdk, permissions, activities, services, receivers,
                            providers, applicationClass, debuggable
                        )?.let { info ->
                            packageName = info.packageName
                            versionCode = info.versionCode
                            versionName = info.versionName
                            minSdk = info.minSdk
                            targetSdk = info.targetSdk
                            compileSdk = info.compileSdk
                            applicationClass = info.applicationClass
                            debuggable = info.debuggable
                        }
                    }

                    elements.add(XmlElement(elemName, attrs, lineNumber))
                }
                CHUNK_END_ELEMENT -> {
                    buf.int; buf.int; buf.int; buf.int // lineNumber, comment, ns, name
                }
                CHUNK_TEXT -> {
                    buf.int; buf.int; buf.int // lineNumber, comment, text
                    buf.int; buf.int // typedValue
                }
                else -> {
                    // 跳过未知 chunk
                    val remaining = chunkSize - 8
                    if (remaining > 0 && buf.remaining() >= remaining) {
                        buf.position(buf.position() + remaining)
                    }
                }
            }

            // 确保移动到 chunk 末尾
            val expectedEnd = chunkStart + chunkSize
            if (expectedEnd > buf.position() && expectedEnd <= buf.limit()) {
                buf.position(expectedEnd)
            }
        }

        // 从 elements 中提取信息
        for (elem in elements) {
            when (elem.name) {
                "manifest" -> {
                    for (attr in elem.attributes) {
                        when (attr.name) {
                            "package" -> if (packageName.isEmpty()) packageName = attr.value
                            "versionCode", "platformBuildVersionCode" ->
                                if (versionCode.isEmpty()) versionCode = attr.value
                            "versionName", "platformBuildVersionName" ->
                                if (versionName.isEmpty()) versionName = attr.value
                            "compileSdkVersion" ->
                                if (compileSdk.isEmpty()) compileSdk = attr.value
                        }
                    }
                }
                "uses-sdk" -> {
                    for (attr in elem.attributes) {
                        when (attr.name) {
                            "minSdkVersion" -> if (minSdk.isEmpty()) minSdk = attr.value
                            "targetSdkVersion" -> if (targetSdk.isEmpty()) targetSdk = attr.value
                        }
                    }
                }
                "uses-permission" -> {
                    elem.attributes.find { it.name == "name" }?.let {
                        if (it.value !in permissions) permissions.add(it.value)
                    }
                }
                "application" -> {
                    for (attr in elem.attributes) {
                        when (attr.name) {
                            "name" -> if (applicationClass.isEmpty()) applicationClass = attr.value
                            "debuggable" -> debuggable = attr.value == "true" || attr.value == "-1"
                        }
                    }
                }
                "activity" -> {
                    val name = elem.attributes.find { it.name == "name" }?.value ?: ""
                    val exported = elem.attributes.find { it.name == "exported" }?.value
                    if (name.isNotEmpty()) {
                        activities.add(ComponentInfo(name, exported))
                    }
                }
                "service" -> {
                    val name = elem.attributes.find { it.name == "name" }?.value ?: ""
                    val exported = elem.attributes.find { it.name == "exported" }?.value
                    if (name.isNotEmpty()) {
                        services.add(ComponentInfo(name, exported))
                    }
                }
                "receiver" -> {
                    val name = elem.attributes.find { it.name == "name" }?.value ?: ""
                    val exported = elem.attributes.find { it.name == "exported" }?.value
                    if (name.isNotEmpty()) {
                        receivers.add(ComponentInfo(name, exported))
                    }
                }
                "provider" -> {
                    val name = elem.attributes.find { it.name == "name" }?.value ?: ""
                    val exported = elem.attributes.find { it.name == "exported" }?.value
                    val authorities = elem.attributes.find { it.name == "authorities" }?.value
                    if (name.isNotEmpty()) {
                        providers.add(ComponentInfo(name, exported, authorities))
                    }
                }
            }
        }

        return ParsedManifest(
            packageName = packageName,
            versionCode = versionCode,
            versionName = versionName,
            minSdk = minSdk,
            targetSdk = targetSdk,
            compileSdk = compileSdk,
            permissions = permissions.distinct(),
            activities = activities,
            services = services,
            receivers = receivers,
            providers = providers,
            applicationClass = applicationClass,
            debuggable = debuggable,
            elements = elements,
            namespaces = namespaces
        )
    }

    /**
     * 搜索 Manifest 中的属性
     */
    fun searchAttributes(
        manifest: ParsedManifest,
        attrName: String?,
        value: String?,
        limit: Int = 50
    ): List<SearchMatch> {
        val results = mutableListOf<SearchMatch>()
        for (elem in manifest.elements) {
            if (results.size >= limit) break
            for (attr in elem.attributes) {
                if (results.size >= limit) break
                val nameMatch = attrName.isNullOrEmpty() || attr.name.contains(attrName, ignoreCase = true)
                val valueMatch = value.isNullOrEmpty() || attr.value.contains(value, ignoreCase = true)
                if (nameMatch && valueMatch) {
                    results.add(SearchMatch(
                        element = elem.name,
                        attribute = attr.name,
                        value = attr.value,
                        namespace = attr.namespace,
                        lineNumber = elem.lineNumber
                    ))
                }
            }
        }
        return results
    }

    // ========== 内部解析方法 ==========

    private fun parseStringPool(
        buf: ByteBuffer, chunkStart: Int, chunkSize: Int,
        strings: MutableList<String>
    ) {
        val stringCount = buf.int
        val styleCount = buf.int
        val flags = buf.int
        val stringsStart = buf.int
        val stylesStart = buf.int

        val isUtf8 = (flags and (1 shl 8)) != 0

        val offsets = IntArray(stringCount)
        for (i in 0 until stringCount) {
            offsets[i] = buf.int
        }
        // 跳过 style offsets
        for (i in 0 until styleCount) {
            buf.int
        }

        val dataStart = chunkStart + stringsStart
        for (i in 0 until stringCount) {
            val pos = dataStart + offsets[i]
            if (pos >= buf.limit()) {
                strings.add("")
                continue
            }
            buf.position(pos)
            val str = if (isUtf8) readUtf8String(buf) else readUtf16String(buf)
            strings.add(str)
        }

        // 移动到 chunk 末尾
        buf.position(chunkStart + chunkSize)
    }

    private fun readUtf8String(buf: ByteBuffer): String {
        // UTF-8 string: charCount (1-2 bytes), byteCount (1-2 bytes), data, null
        val charCount = readCompactSize(buf)
        val byteCount = readCompactSize(buf)
        if (byteCount == 0) return ""
        val bytes = ByteArray(byteCount)
        if (buf.remaining() >= byteCount) {
            buf.get(bytes)
        }
        return String(bytes, Charsets.UTF_8)
    }

    private fun readUtf16String(buf: ByteBuffer): String {
        // UTF-16 string: charCount (2 bytes or 4 bytes if high bit set), data, null
        var charCount = buf.short.toInt() and 0xFFFF
        if (charCount and 0x8000 != 0) {
            charCount = ((charCount and 0x7FFF) shl 16) or (buf.short.toInt() and 0xFFFF)
        }
        if (charCount == 0) return ""
        val bytes = ByteArray(charCount * 2)
        if (buf.remaining() >= bytes.size) {
            buf.get(bytes)
        }
        return String(bytes, Charsets.UTF_16LE)
    }

    private fun readCompactSize(buf: ByteBuffer): Int {
        val b = buf.get().toInt() and 0xFF
        return if (b and 0x80 != 0) {
            ((b and 0x7F) shl 8) or (buf.get().toInt() and 0xFF)
        } else {
            b
        }
    }

    private fun resolveAttributeValue(
        type: Int, data: Int, stringIdx: Int, strings: List<String>
    ): String {
        return when (type) {
            TYPE_STRING -> strings.getOrElse(stringIdx) { "?" }
            TYPE_INT_DEC -> data.toString()
            TYPE_INT_HEX -> "0x${Integer.toHexString(data)}"
            TYPE_INT_BOOLEAN -> if (data != 0) "true" else "false"
            TYPE_REFERENCE -> "@0x${Integer.toHexString(data)}"
            TYPE_ATTRIBUTE -> "?0x${Integer.toHexString(data)}"
            TYPE_FLOAT -> java.lang.Float.intBitsToFloat(data).toString()
            TYPE_NULL -> "null"
            else -> "0x${Integer.toHexString(data)}"
        }
    }

    private data class ExtractedInfo(
        val packageName: String,
        val versionCode: String,
        val versionName: String,
        val minSdk: String,
        val targetSdk: String,
        val compileSdk: String,
        val applicationClass: String,
        val debuggable: Boolean
    )

    private fun extractManifestInfo(
        elemName: String, attrName: String, attrValue: String,
        attrType: Int, attrData: Int,
        packageName: String, versionCode: String, versionName: String,
        minSdk: String, targetSdk: String, compileSdk: String,
        permissions: MutableList<String>,
        activities: MutableList<ComponentInfo>,
        services: MutableList<ComponentInfo>,
        receivers: MutableList<ComponentInfo>,
        providers: MutableList<ComponentInfo>,
        applicationClass: String, debuggable: Boolean
    ): ExtractedInfo? {
        // 这个方法在逐属性解析时调用，但主要提取逻辑在 parse() 的后处理中
        // 这里返回 null，让后处理统一处理
        return null
    }
}

// ========== 数据类 ==========

data class ParsedManifest(
    val packageName: String,
    val versionCode: String,
    val versionName: String,
    val minSdk: String,
    val targetSdk: String,
    val compileSdk: String,
    val permissions: List<String>,
    val activities: List<ComponentInfo>,
    val services: List<ComponentInfo>,
    val receivers: List<ComponentInfo>,
    val providers: List<ComponentInfo>,
    val applicationClass: String,
    val debuggable: Boolean,
    val elements: List<XmlElement>,
    val namespaces: Map<String, String>
)

data class ComponentInfo(
    val name: String,
    val exported: String? = null,
    val authorities: String? = null
)

data class XmlElement(
    val name: String,
    val attributes: List<XmlAttribute>,
    val lineNumber: Int
)

data class XmlAttribute(
    val namespace: String,
    val name: String,
    val value: String,
    val type: Int
)

data class SearchMatch(
    val element: String,
    val attribute: String,
    val value: String,
    val namespace: String,
    val lineNumber: Int
)
