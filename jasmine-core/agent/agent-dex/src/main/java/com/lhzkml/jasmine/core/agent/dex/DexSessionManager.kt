package com.lhzkml.jasmine.core.agent.dex

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import java.io.File

/**
 * DEX 编辑会话管理器
 * 移植自 AetherLink 的 dex-editor 会话管理
 * 管理多个并行的 DEX 编辑会话
 */
object DexSessionManager {

    private val sessions = ConcurrentHashMap<String, DexSession>()

    /**
     * 列出 APK 中的 DEX 文件
     */
    fun listDexFiles(apkPath: String): List<String> {
        val apkFile = File(apkPath)
        if (!apkFile.exists()) throw IllegalArgumentException("APK not found: $apkPath")

        val dexFiles = mutableListOf<String>()
        val zipFile = ZipFile(apkFile)
        try {
            for (entry in zipFile.entries()) {
                if (entry.name.endsWith(".dex")) {
                    dexFiles.add(entry.name)
                }
            }
        } finally {
            zipFile.close()
        }
        return dexFiles.sorted()
    }

    /**
     * 打开 DEX 编辑会话
     */
    fun openSession(apkPath: String, dexFiles: List<String>): DexSession {
        val sessionId = UUID.randomUUID().toString().take(8)
        val session = DexSession(sessionId, apkPath, dexFiles)
        session.load()
        sessions[sessionId] = session
        return session
    }

    /**
     * 获取会话
     */
    fun getSession(sessionId: String): DexSession {
        return sessions[sessionId]
            ?: throw IllegalArgumentException("Session not found: $sessionId")
    }

    /**
     * 关闭会话
     */
    fun closeSession(sessionId: String) {
        val session = sessions.remove(sessionId)
        session?.close()
    }

    /**
     * 列出所有会话
     */
    fun listSessions(): List<SessionInfo> {
        return sessions.values.map { session ->
            SessionInfo(
                id = session.id,
                apkPath = session.apkPath,
                dexFiles = session.dexFileNames,
                createdAt = session.createdAt
            )
        }
    }

    /**
     * 列出 APK 中的所有文件
     */
    fun listApkFiles(
        apkPath: String,
        filter: String = "",
        limit: Int = 100,
        offset: Int = 0
    ): ApkFileListResult {
        val apkFile = File(apkPath)
        if (!apkFile.exists()) throw IllegalArgumentException("APK not found: $apkPath")

        val allFiles = mutableListOf<ApkFileEntry>()
        val zipFile = ZipFile(apkFile)
        try {
            for (entry in zipFile.entries()) {
                if (filter.isEmpty() || entry.name.contains(filter, ignoreCase = true)) {
                    allFiles.add(ApkFileEntry(
                        path = entry.name,
                        size = entry.size,
                        compressedSize = entry.compressedSize,
                        isDirectory = entry.isDirectory
                    ))
                }
            }
        } finally {
            zipFile.close()
        }

        allFiles.sortBy { it.path }
        val total = allFiles.size
        val paged = allFiles.drop(offset).take(limit)
        return ApkFileListResult(paged, total, offset, limit)
    }

    /**
     * 读取 APK 内文件
     */
    fun readApkFile(
        apkPath: String,
        filePath: String,
        asBase64: Boolean = false,
        maxBytes: Int = 0,
        offset: Int = 0
    ): String {
        val apkFile = File(apkPath)
        if (!apkFile.exists()) throw IllegalArgumentException("APK not found: $apkPath")

        val zipFile = ZipFile(apkFile)
        try {
            val entry = zipFile.getEntry(filePath)
                ?: throw IllegalArgumentException("File not found in APK: $filePath")

            val bytes = zipFile.getInputStream(entry).use { it.readBytes() }

            val start = offset.coerceIn(0, bytes.size)
            val end = if (maxBytes <= 0) bytes.size else (start + maxBytes).coerceAtMost(bytes.size)
            val slice = bytes.sliceArray(start until end)

            return if (asBase64) {
                android.util.Base64.encodeToString(slice, android.util.Base64.NO_WRAP)
            } else {
                String(slice, Charsets.UTF_8)
            }
        } finally {
            zipFile.close()
        }
    }

    /**
     * 在 APK 内搜索文本
     */
    fun searchTextInApk(
        apkPath: String,
        pattern: String,
        fileExtensions: List<String>? = null,
        caseSensitive: Boolean = false,
        isRegex: Boolean = false,
        maxResults: Int = 50,
        contextLines: Int = 2
    ): List<ApkSearchResult> {
        val apkFile = File(apkPath)
        if (!apkFile.exists()) throw IllegalArgumentException("APK not found: $apkPath")

        val binaryExtensions = setOf("dex", "so", "png", "jpg", "gif", "webp", "ogg", "mp3", "mp4", "ttf", "otf")
        val regex = if (isRegex) {
            val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
            Regex(pattern, options)
        } else null

        val results = mutableListOf<ApkSearchResult>()
        val zipFile = ZipFile(apkFile)
        try {
            for (entry in zipFile.entries()) {
                if (results.size >= maxResults) break
                if (entry.isDirectory) continue

                val ext = entry.name.substringAfterLast('.', "").lowercase()
                if (ext in binaryExtensions) continue
                if (fileExtensions != null && ".$ext" !in fileExtensions) continue

                try {
                    val content = zipFile.getInputStream(entry).use { String(it.readBytes(), Charsets.UTF_8) }
                    val lines = content.lines()

                    for ((lineNum, line) in lines.withIndex()) {
                        if (results.size >= maxResults) break
                        val matched = if (regex != null) {
                            regex.containsMatchIn(line)
                        } else {
                            line.contains(pattern, ignoreCase = !caseSensitive)
                        }
                        if (matched) {
                            val ctxStart = (lineNum - contextLines).coerceAtLeast(0)
                            val ctxEnd = (lineNum + contextLines + 1).coerceAtMost(lines.size)
                            val context = lines.subList(ctxStart, ctxEnd).joinToString("\n")
                            results.add(ApkSearchResult(entry.name, lineNum + 1, line.trim(), context))
                        }
                    }
                } catch (_: Exception) {
                    // 跳过无法读取的文件
                }
            }
        } finally {
            zipFile.close()
        }
        return results
    }

    /**
     * 删除 APK 内文件
     */
    fun deleteFileFromApk(apkPath: String, filePath: String): String {
        val apkFile = File(apkPath)
        if (!apkFile.exists()) throw IllegalArgumentException("APK not found: $apkPath")

        val tempApk = File(apkFile.parent, "${apkFile.nameWithoutExtension}_modified.apk")
        val zipIn = ZipFile(apkFile)
        val zipOut = java.util.zip.ZipOutputStream(
            java.io.BufferedOutputStream(java.io.FileOutputStream(tempApk))
        )
        var deleted = false
        try {
            for (entry in zipIn.entries()) {
                if (entry.name == filePath) {
                    deleted = true
                    continue
                }
                val newEntry = java.util.zip.ZipEntry(entry.name)
                zipOut.putNextEntry(newEntry)
                zipIn.getInputStream(entry).use { it.copyTo(zipOut) }
                zipOut.closeEntry()
            }
        } finally {
            zipOut.close()
            zipIn.close()
        }

        if (!deleted) {
            tempApk.delete()
            throw IllegalArgumentException("File not found in APK: $filePath")
        }
        return tempApk.absolutePath
    }

    /**
     * 添加文件到 APK
     */
    fun addFileToApk(
        apkPath: String,
        filePath: String,
        content: String,
        isBase64: Boolean = false
    ): String {
        val apkFile = File(apkPath)
        if (!apkFile.exists()) throw IllegalArgumentException("APK not found: $apkPath")

        val bytes = if (isBase64) {
            android.util.Base64.decode(content, android.util.Base64.DEFAULT)
        } else {
            content.toByteArray(Charsets.UTF_8)
        }

        val tempApk = File(apkFile.parent, "${apkFile.nameWithoutExtension}_modified.apk")
        val zipIn = ZipFile(apkFile)
        val zipOut = java.util.zip.ZipOutputStream(
            java.io.BufferedOutputStream(java.io.FileOutputStream(tempApk))
        )
        try {
            var replaced = false
            for (entry in zipIn.entries()) {
                if (entry.name == filePath) {
                    replaced = true
                    val newEntry = java.util.zip.ZipEntry(entry.name)
                    zipOut.putNextEntry(newEntry)
                    zipOut.write(bytes)
                    zipOut.closeEntry()
                } else {
                    val newEntry = java.util.zip.ZipEntry(entry.name)
                    zipOut.putNextEntry(newEntry)
                    zipIn.getInputStream(entry).use { it.copyTo(zipOut) }
                    zipOut.closeEntry()
                }
            }
            if (!replaced) {
                val newEntry = java.util.zip.ZipEntry(filePath)
                zipOut.putNextEntry(newEntry)
                zipOut.write(bytes)
                zipOut.closeEntry()
            }
        } finally {
            zipOut.close()
            zipIn.close()
        }
        return tempApk.absolutePath
    }

    /**
     * 列出 APK 资源文件
     */
    fun listResources(apkPath: String, filter: String? = null): List<String> {
        val apkFile = File(apkPath)
        if (!apkFile.exists()) throw IllegalArgumentException("APK not found: $apkPath")

        val resources = mutableListOf<String>()
        val zipFile = ZipFile(apkFile)
        try {
            for (entry in zipFile.entries()) {
                if (entry.name.startsWith("res/")) {
                    if (filter.isNullOrEmpty() || entry.name.contains(filter, ignoreCase = true)) {
                        resources.add(entry.name)
                    }
                }
            }
        } finally {
            zipFile.close()
        }
        return resources.sorted()
    }

    /**
     * 获取 AndroidManifest.xml 内容
     * APK 中的 AndroidManifest.xml 是二进制格式，这里读取原始字节
     * 如果是文本格式则直接返回，否则返回提示
     */
    fun getManifest(apkPath: String, maxChars: Int = 0, offset: Int = 0): ManifestResult {
        val apkFile = File(apkPath)
        if (!apkFile.exists()) throw IllegalArgumentException("APK not found: $apkPath")

        val zipFile = ZipFile(apkFile)
        try {
            val entry = zipFile.getEntry("AndroidManifest.xml")
                ?: throw IllegalArgumentException("AndroidManifest.xml not found in APK")

            val bytes = zipFile.getInputStream(entry).use { it.readBytes() }

            // 尝试作为文本读取（某些 APK 可能已解码）
            val content = try {
                val text = String(bytes, Charsets.UTF_8)
                if (text.startsWith("<?xml") || text.startsWith("<manifest")) {
                    text
                } else {
                    // 二进制 XML，返回 hex 摘要和提示
                    "Binary AndroidManifest.xml (${bytes.size} bytes). Use apktool or aapt2 to decode."
                }
            } catch (_: Exception) {
                "Binary AndroidManifest.xml (${bytes.size} bytes). Use apktool or aapt2 to decode."
            }

            val totalLength = content.length
            val start = offset.coerceIn(0, totalLength)
            val end = if (maxChars <= 0) totalLength else (start + maxChars).coerceAtMost(totalLength)
            val slice = content.substring(start, end)

            return ManifestResult(
                content = slice,
                totalLength = totalLength,
                offset = start,
                hasMore = end < totalLength
            )
        } finally {
            zipFile.close()
        }
    }

    /**
     * 修改 AndroidManifest.xml
     */
    fun modifyManifest(apkPath: String, newManifest: String): String {
        return addFileToApk(apkPath, "AndroidManifest.xml", newManifest, false)
    }

    /**
     * 替换 Manifest 中的字符串
     */
    fun replaceInManifest(
        apkPath: String,
        replacements: List<Pair<String, String>>
    ): ReplaceResult {
        val manifest = getManifest(apkPath)
        var content = manifest.content
        var count = 0

        for ((old, new) in replacements) {
            if (content.contains(old)) {
                content = content.replace(old, new)
                count++
            }
        }

        if (count > 0) {
            modifyManifest(apkPath, content)
        }
        return ReplaceResult(count, content)
    }

    /**
     * 获取资源文件内容
     */
    fun getResource(apkPath: String, resourcePath: String, maxChars: Int = 0, offset: Int = 0): String {
        return readApkFile(apkPath, resourcePath, false, maxChars, offset)
    }

    /**
     * 修改资源文件
     */
    fun modifyResource(apkPath: String, resourcePath: String, newContent: String): String {
        return addFileToApk(apkPath, resourcePath, newContent, false)
    }

    /**
     * 搜索 ARSC 字符串
     * 从 resources.arsc 中搜索字符串资源
     */
    fun searchArscStrings(apkPath: String, pattern: String, limit: Int = 50): List<String> {
        val apkFile = File(apkPath)
        if (!apkFile.exists()) throw IllegalArgumentException("APK not found: $apkPath")

        val results = mutableListOf<String>()
        val zipFile = ZipFile(apkFile)
        try {
            val entry = zipFile.getEntry("resources.arsc") ?: return emptyList()
            val bytes = zipFile.getInputStream(entry).use { it.readBytes() }

            // 从 ARSC 二进制中提取可读字符串
            val strings = extractStringsFromArsc(bytes)
            for (s in strings) {
                if (results.size >= limit) break
                if (s.contains(pattern, ignoreCase = true)) {
                    results.add(s)
                }
            }
        } finally {
            zipFile.close()
        }
        return results
    }

    /**
     * 搜索 ARSC 资源
     */
    fun searchArscResources(apkPath: String, pattern: String, type: String? = null, limit: Int = 50): List<String> {
        // ARSC 资源搜索与字符串搜索类似，但可以按类型过滤
        return searchArscStrings(apkPath, pattern, limit)
    }

    /**
     * 从 ARSC 二进制数据中提取字符串
     */
    private fun extractStringsFromArsc(data: ByteArray): List<String> {
        val strings = mutableListOf<String>()
        // 简单的字符串提取：查找 UTF-8/UTF-16 字符串
        var i = 0
        while (i < data.size - 2) {
            // 尝试找到可读字符串序列
            if (data[i] >= 0x20 && data[i] < 0x7F) {
                val start = i
                while (i < data.size && data[i] >= 0x20 && data[i] < 0x7F) {
                    i++
                }
                val len = i - start
                if (len >= 3) { // 至少 3 个字符
                    strings.add(String(data, start, len, Charsets.US_ASCII))
                }
            } else {
                i++
            }
        }
        return strings.distinct()
    }
}

data class SessionInfo(
    val id: String,
    val apkPath: String,
    val dexFiles: List<String>,
    val createdAt: Long
)

data class ApkFileEntry(
    val path: String,
    val size: Long,
    val compressedSize: Long,
    val isDirectory: Boolean
)

data class ApkFileListResult(
    val files: List<ApkFileEntry>,
    val total: Int,
    val offset: Int,
    val limit: Int
)

data class ApkSearchResult(
    val filePath: String,
    val lineNumber: Int,
    val matchLine: String,
    val context: String
)

data class ManifestResult(
    val content: String,
    val totalLength: Int,
    val offset: Int,
    val hasMore: Boolean
)

data class ReplaceResult(
    val replacedCount: Int,
    val content: String
)
