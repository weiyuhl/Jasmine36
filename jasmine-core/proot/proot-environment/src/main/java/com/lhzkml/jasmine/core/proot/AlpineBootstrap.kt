package com.lhzkml.jasmine.core.proot

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Alpine minirootfs + PRoot 二进制的下载与初始化。
 *
 * 安装流程：
 * 1. 从 Alpine 官方下载 proot-static .apk 包并提取静态二进制
 * 2. 从 Alpine 官方下载 minirootfs tar.gz
 * 3. 解压 rootfs
 * 4. 配置 DNS / APK 镜像源
 */
object AlpineBootstrap {

    suspend fun install(
        paths: PRootPaths,
        cacheDir: File,
        onProgress: (Float, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        paths.baseDir.mkdirs()
        paths.rootfsDir.mkdirs()
        paths.homeDir.mkdirs()

        // Step 1: PRoot static binary
        if (!paths.prootBinary.exists() || paths.prootBinary.length() < 1024) {
            onProgress(0.05f, "正在下载 PRoot 静态二进制...")
            val apkFile = File(cacheDir, AlpineConstants.PROOT_STATIC_APK_FILENAME)
            downloadFile(
                AlpineConstants.PROOT_STATIC_APK_URL,
                apkFile,
                cacheDir
            ) { progress ->
                onProgress(0.05f + progress * 0.15f, "正在下载 PRoot 静态二进制...")
            }

            onProgress(0.22f, "正在提取 PRoot 二进制...")
            extractBinaryFromApk(apkFile, AlpineConstants.PROOT_BINARY_PATH_IN_APK, paths.prootBinary)
            apkFile.delete()

            paths.prootBinary.setExecutable(true, false)

            if (!paths.prootBinary.exists() || paths.prootBinary.length() < 1024) {
                throw RuntimeException("PRoot 二进制提取失败：文件不存在或大小异常")
            }
        }

        // Step 2: Alpine minirootfs
        val tarGz = File(cacheDir, AlpineConstants.MINIROOTFS_FILENAME)
        val markerFile = File(paths.rootfsDir, ".jasmine_installed")
        if (!markerFile.exists()) {
            if (!tarGz.exists() || tarGz.length() < 1024) {
                onProgress(0.30f, "正在下载 Alpine minirootfs...")
                downloadFile(
                    AlpineConstants.MINIROOTFS_URL,
                    tarGz,
                    cacheDir
                ) { progress ->
                    onProgress(0.30f + progress * 0.30f, "正在下载 Alpine minirootfs...")
                }
            }

            onProgress(0.65f, "正在解压 rootfs...")
            extractTarGz(tarGz, paths.rootfsDir)
            tarGz.delete()

            if (!File(paths.rootfsDir, "bin/sh").exists()) {
                throw RuntimeException("rootfs 解压失败：bin/sh 不存在")
            }

            onProgress(0.85f, "正在配置环境...")
            configureEnvironment(paths)

            markerFile.writeText("installed")
        }

        onProgress(1.0f, "安装完成")
    }

    suspend fun uninstall(paths: PRootPaths) = withContext(Dispatchers.IO) {
        if (paths.baseDir.exists()) {
            paths.baseDir.deleteRecursively()
        }
    }

    fun isInstalled(paths: PRootPaths): Boolean {
        return paths.prootBinary.exists() &&
                paths.prootBinary.length() > 1024 &&
                File(paths.rootfsDir, ".jasmine_installed").exists() &&
                File(paths.rootfsDir, "bin/sh").exists()
    }

    private fun downloadFile(
        urlStr: String,
        target: File,
        cacheDir: File,
        onProgress: (Float) -> Unit
    ) {
        val tmpFile = File(cacheDir, target.name + ".tmp")
        tmpFile.parentFile?.mkdirs()

        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 30_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = true
        try {
            conn.connect()
            val code = conn.responseCode
            if (code != 200) {
                throw RuntimeException("HTTP $code from $urlStr")
            }
            val totalBytes = conn.contentLengthLong
            var downloaded = 0L
            conn.inputStream.buffered().use { input ->
                FileOutputStream(tmpFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalBytes > 0) {
                            onProgress((downloaded.toFloat() / totalBytes).coerceAtMost(1f))
                        }
                    }
                }
            }
            if (!tmpFile.renameTo(target)) {
                tmpFile.copyTo(target, overwrite = true)
                tmpFile.delete()
            }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 从 Alpine .apk 包中提取指定二进制。
     *
     * Alpine .apk 是多段 gzip 拼接（签名 + 控制 + 数据），
     * Android toybox tar 可能只读第一段。
     * 先尝试系统 tar，失败则用 Java 纯解析（多段 gzip + tar 格式）。
     */
    private fun extractBinaryFromApk(apkFile: File, entryPath: String, target: File) {
        val tmpDir = File(apkFile.parentFile, "proot_extract_tmp")
        tmpDir.mkdirs()

        try {
            // Method 1: try system tar (works if tar handles multi-stream gzip)
            val extracted = trySystemTarExtract(apkFile, entryPath, tmpDir)
            if (extracted != null && extracted.exists() && extracted.length() > 1024) {
                target.parentFile?.mkdirs()
                extracted.copyTo(target, overwrite = true)
                return
            }

            // Method 2: try extracting everything with system tar
            val extractedAll = trySystemTarExtractAll(apkFile, tmpDir)
            if (extractedAll) {
                val binary = File(tmpDir, entryPath)
                if (binary.exists() && binary.length() > 1024) {
                    target.parentFile?.mkdirs()
                    binary.copyTo(target, overwrite = true)
                    return
                }
            }

            // Method 3: pure Java multi-stream gzip + tar parsing
            val javaExtracted = extractWithJavaTarParser(apkFile, entryPath, target)
            if (javaExtracted) return

            throw RuntimeException(
                "所有提取方法均失败。apk 大小: ${apkFile.length()} 字节，" +
                "tmpDir 内容: ${tmpDir.listFiles()?.map { "${it.name}(${it.length()})" }}"
            )
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    private fun trySystemTarExtract(apkFile: File, entryPath: String, tmpDir: File): File? {
        return try {
            val process = ProcessBuilder(
                "tar", "xzf", apkFile.absolutePath,
                "-C", tmpDir.absolutePath,
                entryPath
            ).redirectErrorStream(true).start()
            process.inputStream.bufferedReader().readText()
            process.waitFor()
            val f = File(tmpDir, entryPath)
            if (f.exists()) f else null
        } catch (_: Exception) {
            null
        }
    }

    private fun trySystemTarExtractAll(apkFile: File, tmpDir: File): Boolean {
        return try {
            val process = ProcessBuilder(
                "tar", "xzf", apkFile.absolutePath,
                "-C", tmpDir.absolutePath
            ).redirectErrorStream(true).start()
            process.inputStream.bufferedReader().readText()
            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 纯 Java 实现：读取多段 gzip 拼接的 tar 归档，提取指定文件。
     * Alpine .apk = gzip(signature.tar) + gzip(control.tar) + gzip(data.tar)
     */
    private fun extractWithJavaTarParser(apkFile: File, entryPath: String, target: File): Boolean {
        FileInputStream(apkFile).buffered().use { rawInput ->
            val multiGzipStream = MultiStreamGZIPInputStream(rawInput)
            return extractTarEntry(multiGzipStream, entryPath, target)
        }
    }

    /**
     * 从 tar 流中查找并提取指定路径的文件。
     * tar 格式：每个条目 = 512 字节 header + 数据（512 字节对齐填充）
     */
    private fun extractTarEntry(input: java.io.InputStream, entryPath: String, target: File): Boolean {
        val headerBuf = ByteArray(512)
        while (true) {
            val headerRead = readFully(input, headerBuf)
            if (headerRead < 512) return false

            if (headerBuf.all { it == 0.toByte() }) {
                val nextBlock = ByteArray(512)
                val nextRead = readFully(input, nextBlock)
                if (nextRead < 512 || nextBlock.all { it == 0.toByte() }) {
                    return false
                }
                continue
            }

            val name = parseTarName(headerBuf)
            val size = parseTarSize(headerBuf)
            val typeFlag = headerBuf[156]

            val isFile = typeFlag == 0.toByte() || typeFlag == '0'.code.toByte()

            val normalizedName = name.removePrefix("./").removePrefix("/")
            val normalizedEntry = entryPath.removePrefix("./").removePrefix("/")

            if (isFile && normalizedName == normalizedEntry && size > 0) {
                target.parentFile?.mkdirs()
                FileOutputStream(target).use { out ->
                    val buf = ByteArray(8192)
                    var remaining = size
                    while (remaining > 0) {
                        val toRead = minOf(remaining, buf.size.toLong()).toInt()
                        val read = input.read(buf, 0, toRead)
                        if (read <= 0) break
                        out.write(buf, 0, read)
                        remaining -= read
                    }
                }
                return target.exists() && target.length() > 0
            }

            // Skip file data (padded to 512 byte boundary)
            if (size > 0) {
                val paddedSize = ((size + 511) / 512) * 512
                skipFully(input, paddedSize)
            }
        }
    }

    private fun parseTarName(header: ByteArray): String {
        // POSIX: prefix at offset 345 (155 bytes) + name at offset 0 (100 bytes)
        val prefix = header.copyOfRange(345, 500).takeWhile { it != 0.toByte() }
            .toByteArray().toString(Charsets.UTF_8)
        val name = header.copyOfRange(0, 100).takeWhile { it != 0.toByte() }
            .toByteArray().toString(Charsets.UTF_8)
        return if (prefix.isNotEmpty()) "$prefix/$name" else name
    }

    private fun parseTarSize(header: ByteArray): Long {
        val sizeBytes = header.copyOfRange(124, 136)
        // Check for binary size encoding (high bit set)
        if (sizeBytes[0].toInt() and 0x80 != 0) {
            var size = 0L
            for (i in 4 until 12) {
                size = (size shl 8) or (sizeBytes[i].toLong() and 0xFF)
            }
            return size
        }
        val sizeStr = sizeBytes.takeWhile { it != 0.toByte() && it != ' '.code.toByte() }
            .toByteArray().toString(Charsets.UTF_8).trim()
        return if (sizeStr.isEmpty()) 0L else sizeStr.toLongOrNull(8) ?: 0L
    }

    private fun readFully(input: java.io.InputStream, buf: ByteArray): Int {
        var offset = 0
        while (offset < buf.size) {
            val read = input.read(buf, offset, buf.size - offset)
            if (read <= 0) return offset
            offset += read
        }
        return offset
    }

    private fun skipFully(input: java.io.InputStream, bytes: Long) {
        var remaining = bytes
        val buf = ByteArray(8192)
        while (remaining > 0) {
            val toRead = minOf(remaining, buf.size.toLong()).toInt()
            val read = input.read(buf, 0, toRead)
            if (read <= 0) break
            remaining -= read
        }
    }

    private fun extractTarGz(tarGz: File, targetDir: File) {
        targetDir.mkdirs()
        val process = ProcessBuilder(
            "tar", "xzf", tarGz.absolutePath,
            "-C", targetDir.absolutePath
        ).redirectErrorStream(true).start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw RuntimeException("tar extraction failed (exit $exitCode): $output")
        }
    }

    private fun configureEnvironment(paths: PRootPaths) {
        val resolv = File(paths.rootfsDir, "etc/resolv.conf")
        resolv.parentFile?.mkdirs()
        resolv.writeText("nameserver ${AlpineConstants.DEFAULT_DNS}\nnameserver 8.8.8.8\n")

        val repos = File(paths.rootfsDir, "etc/apk/repositories")
        repos.parentFile?.mkdirs()
        repos.writeText(AlpineConstants.DEFAULT_REPOSITORIES.joinToString("\n") + "\n")

        val passwd = File(paths.rootfsDir, "etc/passwd")
        if (!passwd.exists() || !passwd.readText().contains("root")) {
            passwd.writeText("root:x:0:0:root:/root:/bin/sh\n")
        }
        val group = File(paths.rootfsDir, "etc/group")
        if (!group.exists() || !group.readText().contains("root")) {
            group.writeText("root:x:0:root\n")
        }

        File(paths.rootfsDir, "tmp").mkdirs()

        val profile = File(paths.rootfsDir, "root/.profile")
        if (!profile.exists()) {
            profile.writeText(
                """
                export HOME=/root
                export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
                export LANG=C.UTF-8
                export TERM=xterm-256color
                """.trimIndent() + "\n"
            )
        }
    }
}

/**
 * 处理多段 gzip 拼接流（如 Alpine .apk 格式）。
 * 标准 GZIPInputStream 只读第一个 gzip 段，
 * 此包装器在一段结束后自动开始解压下一段。
 */
private class MultiStreamGZIPInputStream(
    private val rawInput: BufferedInputStream
) : java.io.InputStream() {

    private var currentGzip: GZIPInputStream? = null

    init {
        rawInput.mark(2)
        val b1 = rawInput.read()
        val b2 = rawInput.read()
        rawInput.reset()
        if (b1 == 0x1f && b2 == 0x8b) {
            currentGzip = GZIPInputStream(rawInput)
        }
    }

    override fun read(): Int {
        while (true) {
            val gz = currentGzip ?: return -1
            val b = gz.read()
            if (b != -1) return b
            if (!tryNextStream()) return -1
        }
    }

    override fun read(buf: ByteArray, off: Int, len: Int): Int {
        while (true) {
            val gz = currentGzip ?: return -1
            val read = gz.read(buf, off, len)
            if (read > 0) return read
            if (read == -1 && !tryNextStream()) return -1
        }
    }

    private fun tryNextStream(): Boolean {
        rawInput.mark(2)
        val b1 = rawInput.read()
        val b2 = rawInput.read()
        if (b1 == 0x1f && b2 == 0x8b) {
            rawInput.reset()
            currentGzip = GZIPInputStream(rawInput)
            return true
        }
        return false
    }

    override fun close() {
        currentGzip?.close()
        rawInput.close()
    }
}
