package com.lhzkml.jasmine.core.agent.dex.parser

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Android resources.arsc 解析器
 * 解析 APK 中的 resources.arsc 文件，提取资源概要信息
 *
 * ARSC 格式：
 * - Table header
 * - Global StringPool (所有值字符串)
 * - Package chunks (每个包含 typeStrings, keyStrings, type specs, types)
 */
object ArscParser {

    // Chunk types
    private const val RES_TABLE_TYPE = 0x0002
    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_TABLE_PACKAGE_TYPE = 0x0200
    private const val RES_TABLE_TYPE_SPEC_TYPE = 0x0202
    private const val RES_TABLE_TYPE_TYPE = 0x0201

    /**
     * 解析 resources.arsc 返回概要信息
     */
    fun parse(data: ByteArray): ArscSummary {
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        // Table header
        val headerType = buf.short.toInt() and 0xFFFF
        val headerSize = buf.short.toInt() and 0xFFFF
        val tableSize = buf.int
        val packageCount = buf.int

        val globalStrings = mutableListOf<String>()
        val packages = mutableListOf<ArscPackage>()

        // 解析全局字符串池
        if (buf.remaining() >= 8) {
            val chunkStart = buf.position()
            val chunkType = buf.short.toInt() and 0xFFFF
            val chunkHeaderSize = buf.short.toInt() and 0xFFFF
            val chunkSize = buf.int

            if (chunkType == RES_STRING_POOL_TYPE) {
                parseStringPool(buf, chunkStart, chunkSize, chunkHeaderSize, globalStrings)
            }
            buf.position(chunkStart + chunkSize)
        }

        // 解析 Package chunks
        for (p in 0 until packageCount) {
            if (buf.remaining() < 8) break
            val pkgStart = buf.position()
            val pkgType = buf.short.toInt() and 0xFFFF
            val pkgHeaderSize = buf.short.toInt() and 0xFFFF
            val pkgSize = buf.int

            if (pkgType != RES_TABLE_PACKAGE_TYPE) {
                if (pkgSize > 0) buf.position(pkgStart + pkgSize)
                continue
            }

            val pkgId = buf.int
            // 包名: 128 个 UTF-16 字符
            val nameBytes = ByteArray(256)
            buf.get(nameBytes)
            val pkgName = String(nameBytes, Charsets.UTF_16LE).trimEnd('\u0000')

            val typeStringsOffset = buf.int
            val lastPublicType = buf.int
            val keyStringsOffset = buf.int
            val lastPublicKey = buf.int

            // 解析 type strings
            val typeStrings = mutableListOf<String>()
            val keyStrings = mutableListOf<String>()

            val typeStringsPos = pkgStart + typeStringsOffset
            if (typeStringsPos < buf.limit() && typeStringsPos >= buf.position()) {
                buf.position(typeStringsPos)
                if (buf.remaining() >= 8) {
                    val tsStart = buf.position()
                    val tsType = buf.short.toInt() and 0xFFFF
                    val tsHeaderSize = buf.short.toInt() and 0xFFFF
                    val tsSize = buf.int
                    if (tsType == RES_STRING_POOL_TYPE) {
                        parseStringPool(buf, tsStart, tsSize, tsHeaderSize, typeStrings)
                    }
                    buf.position(tsStart + tsSize)
                }
            }

            val keyStringsPos = pkgStart + keyStringsOffset
            if (keyStringsPos < buf.limit() && keyStringsPos >= buf.position()) {
                buf.position(keyStringsPos)
                if (buf.remaining() >= 8) {
                    val ksStart = buf.position()
                    val ksType = buf.short.toInt() and 0xFFFF
                    val ksHeaderSize = buf.short.toInt() and 0xFFFF
                    val ksSize = buf.int
                    if (ksType == RES_STRING_POOL_TYPE) {
                        parseStringPool(buf, ksStart, ksSize, ksHeaderSize, keyStrings)
                    }
                    buf.position(ksStart + ksSize)
                }
            }

            // 解析 TypeSpec 和 Type chunks
            val typeInfos = mutableMapOf<Int, ArscTypeInfo>()
            while (buf.position() < pkgStart + pkgSize && buf.remaining() >= 8) {
                val cStart = buf.position()
                val cType = buf.short.toInt() and 0xFFFF
                val cHeaderSize = buf.short.toInt() and 0xFFFF
                val cSize = buf.int

                when (cType) {
                    RES_TABLE_TYPE_SPEC_TYPE -> {
                        if (buf.remaining() >= 4) {
                            val typeId = buf.get().toInt() and 0xFF
                            buf.get() // res0
                            buf.short // res1
                            val entryCount = buf.int
                            val typeName = typeStrings.getOrElse(typeId - 1) { "type_$typeId" }
                            typeInfos.getOrPut(typeId) {
                                ArscTypeInfo(typeId, typeName, 0)
                            }.let {
                                typeInfos[typeId] = it.copy(entryCount = entryCount)
                            }
                        }
                    }
                    RES_TABLE_TYPE_TYPE -> {
                        if (buf.remaining() >= 4) {
                            val typeId = buf.get().toInt() and 0xFF
                            val typeName = typeStrings.getOrElse(typeId - 1) { "type_$typeId" }
                            if (!typeInfos.containsKey(typeId)) {
                                buf.get(); buf.short
                                val entryCount = buf.int
                                typeInfos[typeId] = ArscTypeInfo(typeId, typeName, entryCount)
                            }
                        }
                    }
                }

                val nextPos = cStart + cSize
                if (nextPos > buf.position() && nextPos <= buf.limit()) {
                    buf.position(nextPos)
                } else {
                    break
                }
            }

            packages.add(ArscPackage(
                id = pkgId,
                name = pkgName,
                typeCount = typeStrings.size,
                keyCount = keyStrings.size,
                types = typeInfos.values.sortedBy { it.id },
                typeNames = typeStrings,
                keyNames = keyStrings
            ))

            // 移动到包末尾
            val pkgEnd = pkgStart + pkgSize
            if (pkgEnd <= buf.limit()) {
                buf.position(pkgEnd)
            }
        }

        return ArscSummary(
            fileSize = data.size,
            packageCount = packageCount,
            globalStringCount = globalStrings.size,
            packages = packages,
            globalStrings = globalStrings
        )
    }

    private fun parseStringPool(
        buf: ByteBuffer, chunkStart: Int, chunkSize: Int,
        headerSize: Int, strings: MutableList<String>
    ) {
        val poolStart = chunkStart + 4 + 4 // type(2) + headerSize(2) + size(4) 已经读过了
        // 但 buf 当前位置在 size 之后，即 chunkStart + 8
        val stringCount = buf.int
        val styleCount = buf.int
        val flags = buf.int
        val stringsOffset = buf.int
        val stylesOffset = buf.int

        val isUtf8 = (flags and (1 shl 8)) != 0

        val offsets = IntArray(stringCount)
        for (i in 0 until stringCount) {
            offsets[i] = buf.int
        }
        // 跳过 style offsets
        for (i in 0 until styleCount) {
            buf.int
        }

        val dataStart = chunkStart + headerSize + stringsOffset
        for (i in 0 until stringCount) {
            val pos = dataStart + offsets[i]
            if (pos >= buf.limit() || pos < 0) {
                strings.add("")
                continue
            }
            buf.position(pos)
            try {
                val str = if (isUtf8) readUtf8String(buf) else readUtf16String(buf)
                strings.add(str)
            } catch (_: Exception) {
                strings.add("")
            }
        }
    }

    private fun readUtf8String(buf: ByteBuffer): String {
        val charCount = readCompactSize(buf)
        val byteCount = readCompactSize(buf)
        if (byteCount == 0) return ""
        val bytes = ByteArray(byteCount.coerceAtMost(buf.remaining()))
        buf.get(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun readUtf16String(buf: ByteBuffer): String {
        var charCount = buf.short.toInt() and 0xFFFF
        if (charCount and 0x8000 != 0) {
            charCount = ((charCount and 0x7FFF) shl 16) or (buf.short.toInt() and 0xFFFF)
        }
        if (charCount == 0) return ""
        val byteCount = charCount * 2
        val bytes = ByteArray(byteCount.coerceAtMost(buf.remaining()))
        buf.get(bytes)
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
}

// ========== 数据类 ==========

data class ArscSummary(
    val fileSize: Int,
    val packageCount: Int,
    val globalStringCount: Int,
    val packages: List<ArscPackage>,
    val globalStrings: List<String>
)

data class ArscPackage(
    val id: Int,
    val name: String,
    val typeCount: Int,
    val keyCount: Int,
    val types: List<ArscTypeInfo>,
    val typeNames: List<String>,
    val keyNames: List<String>
)

data class ArscTypeInfo(
    val id: Int,
    val name: String,
    val entryCount: Int
)
