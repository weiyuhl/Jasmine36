package com.lhzkml.jasmine.core.proot

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream

object AlpineBootstrap {

    private var logFile: File? = null

    private fun log(msg: String) {
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val line = "[$ts] $msg\n"
        try {
            logFile?.appendText(line)
        } catch (_: Exception) {}
    }

    suspend fun install(
        paths: PRootPaths,
        cacheDir: File,
        onProgress: (Float, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val logDir = File(paths.baseDir, "logs")
        logDir.mkdirs()
        logFile = File(logDir, "install_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.log")
        logFile?.writeText("")

        log("=== PRoot Alpine Install Start ===")
        log("baseDir: ${paths.baseDir.absolutePath}")
        log("rootfsDir: ${paths.rootfsDir.absolutePath}")
        log("prootBinary: ${paths.prootBinary.absolutePath}")
        log("cacheDir: ${cacheDir.absolutePath}")

        paths.baseDir.mkdirs()
        paths.rootfsDir.mkdirs()
        paths.homeDir.mkdirs()

        // Step 1: PRoot static binary
        if (!paths.prootBinary.exists() || paths.prootBinary.length() < 1024) {
            onProgress(0.05f, "正在下载 PRoot 静态二进制...")
            val apkFile = File(cacheDir, AlpineConstants.PROOT_STATIC_APK_FILENAME)
            log("Downloading PRoot APK from ${AlpineConstants.PROOT_STATIC_APK_URL}")
            downloadFile(
                AlpineConstants.PROOT_STATIC_APK_URL,
                apkFile,
                cacheDir
            ) { progress ->
                onProgress(0.05f + progress * 0.15f, "正在下载 PRoot 静态二进制...")
            }
            log("PRoot APK downloaded: ${apkFile.length()} bytes")

            onProgress(0.22f, "正在提取 PRoot 二进制...")
            extractBinaryFromApk(apkFile, AlpineConstants.PROOT_BINARY_PATH_IN_APK, paths.prootBinary)
            apkFile.delete()

            paths.prootBinary.setExecutable(true, false)
            log("PRoot binary: exists=${paths.prootBinary.exists()}, size=${paths.prootBinary.length()}, exec=${paths.prootBinary.canExecute()}")

            if (!paths.prootBinary.exists() || paths.prootBinary.length() < 1024) {
                val err = "PRoot 二进制提取失败：文件不存在或大小异常 (${paths.prootBinary.length()} bytes)"
                log("ERROR: $err")
                throw RuntimeException(err)
            }
        } else {
            log("PRoot binary already exists: ${paths.prootBinary.length()} bytes")
        }

        // Step 2: Alpine minirootfs
        val tarGz = File(cacheDir, AlpineConstants.MINIROOTFS_FILENAME)
        val markerFile = File(paths.rootfsDir, ".jasmine_installed")
        if (!markerFile.exists()) {
            if (!tarGz.exists() || tarGz.length() < 1024) {
                onProgress(0.30f, "正在下载 Alpine minirootfs...")
                log("Downloading Alpine rootfs from ${AlpineConstants.MINIROOTFS_URL}")
                downloadFile(
                    AlpineConstants.MINIROOTFS_URL,
                    tarGz,
                    cacheDir
                ) { progress ->
                    onProgress(0.30f + progress * 0.30f, "正在下载 Alpine minirootfs...")
                }
            }
            log("Alpine rootfs tar.gz: ${tarGz.length()} bytes")

            onProgress(0.65f, "正在解压 rootfs...")
            extractRootfs(tarGz, paths.rootfsDir)

            val binSh = File(paths.rootfsDir, "bin/sh")
            log("After extraction: bin/sh exists=${binSh.exists()}")
            logDirectoryContents(paths.rootfsDir, 0)

            if (!binSh.exists()) {
                val err = buildString {
                    appendLine("rootfs 解压失败：bin/sh 不存在")
                    appendLine("tar.gz 大小: ${tarGz.length()} bytes")
                    appendLine("rootfsDir: ${paths.rootfsDir.absolutePath}")
                    val topFiles = paths.rootfsDir.listFiles()
                    appendLine("rootfsDir 内容: ${topFiles?.map { "${it.name}(${if (it.isDirectory) "dir" else "${it.length()}"})" } ?: "null"}")
                }
                log("ERROR: $err")
                tarGz.delete()
                throw RuntimeException(err)
            }
            tarGz.delete()

            onProgress(0.85f, "正在配置环境...")
            configureEnvironment(paths)
            markerFile.writeText("installed")
            log("Installation marker written")
        } else {
            log("rootfs marker already exists, skipping extraction")
        }

        log("=== Install Complete ===")
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

    /**
     * 获取最新的安装日志文件路径。
     */
    fun getLatestLogFile(paths: PRootPaths): File? {
        val logDir = File(paths.baseDir, "logs")
        return logDir.listFiles()
            ?.filter { it.name.startsWith("install_") && it.name.endsWith(".log") }
            ?.maxByOrNull { it.lastModified() }
    }

    // ─── Download ───

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

    // ─── PRoot APK extraction ───

    private fun extractBinaryFromApk(apkFile: File, entryPath: String, target: File) {
        val tmpDir = File(apkFile.parentFile, "proot_extract_tmp")
        tmpDir.mkdirs()

        try {
            log("APK extraction: trying system tar (specific file)...")
            val extracted = trySystemTarExtract(apkFile, entryPath, tmpDir)
            if (extracted != null && extracted.exists() && extracted.length() > 1024) {
                log("System tar (specific) succeeded: ${extracted.length()} bytes")
                target.parentFile?.mkdirs()
                extracted.copyTo(target, overwrite = true)
                return
            }

            log("APK extraction: trying system tar (extract all)...")
            val extractedAll = trySystemTarExtractAll(apkFile, tmpDir)
            if (extractedAll) {
                val binary = File(tmpDir, entryPath)
                if (binary.exists() && binary.length() > 1024) {
                    log("System tar (all) succeeded: ${binary.length()} bytes")
                    target.parentFile?.mkdirs()
                    binary.copyTo(target, overwrite = true)
                    return
                }
                log("System tar (all) extracted but binary not found. Contents: ${tmpDir.walkTopDown().take(20).map { it.name }.toList()}")
            }

            log("APK extraction: trying Java tar parser...")
            val javaExtracted = extractWithJavaTarParser(apkFile, entryPath, target)
            if (javaExtracted) {
                log("Java tar parser succeeded: ${target.length()} bytes")
                return
            }

            val err = "所有提取方法均失败。apk=${apkFile.length()}bytes, tmpDir=${tmpDir.listFiles()?.map { "${it.name}(${it.length()})" }}"
            log("ERROR: $err")
            throw RuntimeException(err)
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
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            log("tar (specific) exit=$exitCode output=${output.take(200)}")
            val f = File(tmpDir, entryPath)
            if (f.exists()) f else null
        } catch (e: Exception) {
            log("tar (specific) exception: ${e.message}")
            null
        }
    }

    private fun trySystemTarExtractAll(apkFile: File, tmpDir: File): Boolean {
        return try {
            val process = ProcessBuilder(
                "tar", "xzf", apkFile.absolutePath,
                "-C", tmpDir.absolutePath
            ).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            log("tar (all) exit=$exitCode output=${output.take(200)}")
            exitCode == 0
        } catch (e: Exception) {
            log("tar (all) exception: ${e.message}")
            false
        }
    }

    private fun extractWithJavaTarParser(apkFile: File, entryPath: String, target: File): Boolean {
        return try {
            FileInputStream(apkFile).buffered().use { rawInput ->
                val multiGzipStream = MultiStreamGZIPInputStream(BufferedInputStream(rawInput, 65536))
                extractTarEntry(multiGzipStream, entryPath, target)
            }
        } catch (e: Exception) {
            log("Java tar parser exception: ${e.message}")
            false
        }
    }

    // ─── Rootfs extraction ───

    /**
     * 解压 Alpine minirootfs tar.gz。
     * 先尝试系统 tar，失败则用 Java 纯解压。
     */
    private fun extractRootfs(tarGz: File, targetDir: File) {
        targetDir.mkdirs()

        // Method 1: system tar
        log("rootfs: trying system tar...")
        val process = ProcessBuilder(
            "tar", "xzf", tarGz.absolutePath,
            "-C", targetDir.absolutePath
        ).redirectErrorStream(true).start()
        val tarOutput = process.inputStream.bufferedReader().readText()
        val tarExit = process.waitFor()
        log("rootfs tar: exit=$tarExit output=${tarOutput.take(300)}")

        if (tarExit == 0 && File(targetDir, "bin/sh").exists()) {
            log("rootfs: system tar succeeded")
            return
        }

        // Method 2: Java GZIPInputStream + tar parser
        log("rootfs: system tar failed or incomplete, trying Java extraction...")
        extractTarGzWithJava(tarGz, targetDir)
        log("rootfs: Java extraction done. bin/sh exists=${File(targetDir, "bin/sh").exists()}")
    }

    /**
     * 纯 Java 解压 .tar.gz 到目标目录（完整解压所有文件）。
     */
    private fun extractTarGzWithJava(tarGz: File, targetDir: File) {
        var fileCount = 0
        FileInputStream(tarGz).buffered().use { fileIn ->
            GZIPInputStream(fileIn).use { gzIn ->
                val headerBuf = ByteArray(512)
                while (true) {
                    val headerRead = readFully(gzIn, headerBuf)
                    if (headerRead < 512) break

                    if (headerBuf.all { it == 0.toByte() }) break

                    val name = parseTarName(headerBuf)
                    val size = parseTarSize(headerBuf)
                    val typeFlag = headerBuf[156]
                    val linkName = headerBuf.copyOfRange(157, 257)
                        .takeWhile { it != 0.toByte() }.toByteArray().toString(Charsets.UTF_8)

                    val cleanName = name.removePrefix("./").removePrefix("/")
                    if (cleanName.isEmpty()) {
                        if (size > 0) skipFully(gzIn, ((size + 511) / 512) * 512)
                        continue
                    }

                    val outFile = File(targetDir, cleanName)

                    when {
                        typeFlag == '5'.code.toByte() || name.endsWith("/") -> {
                            outFile.mkdirs()
                        }
                        typeFlag == '2'.code.toByte() -> {
                            // Symlink: create as regular copy or skip
                            val linkTarget = linkName.removePrefix("./").removePrefix("/")
                            outFile.parentFile?.mkdirs()
                            try {
                                val targetPath = java.nio.file.Paths.get(targetDir.absolutePath, linkTarget)
                                val linkPath = java.nio.file.Paths.get(outFile.absolutePath)
                                java.nio.file.Files.deleteIfExists(linkPath)
                                java.nio.file.Files.createSymbolicLink(
                                    linkPath,
                                    linkPath.parent.relativize(targetPath).normalize()
                                        .let { java.nio.file.Paths.get(linkName) }
                                )
                            } catch (_: Exception) {
                                // Symlink creation may fail; write a placeholder
                                outFile.writeText(linkName)
                            }
                        }
                        typeFlag == '1'.code.toByte() -> {
                            // Hard link
                            outFile.parentFile?.mkdirs()
                            val linkTarget = File(targetDir, linkName.removePrefix("./").removePrefix("/"))
                            if (linkTarget.exists()) {
                                linkTarget.copyTo(outFile, overwrite = true)
                            }
                        }
                        typeFlag == 0.toByte() || typeFlag == '0'.code.toByte() -> {
                            outFile.parentFile?.mkdirs()
                            if (size > 0) {
                                FileOutputStream(outFile).use { fos ->
                                    val buf = ByteArray(8192)
                                    var remaining = size
                                    while (remaining > 0) {
                                        val toRead = minOf(remaining, buf.size.toLong()).toInt()
                                        val read = gzIn.read(buf, 0, toRead)
                                        if (read <= 0) break
                                        fos.write(buf, 0, read)
                                        remaining -= read
                                    }
                                    // Skip padding
                                    val written = size - remaining
                                    val padding = ((size + 511) / 512) * 512 - written
                                    if (padding > 0) skipFully(gzIn, padding)
                                }
                            }
                            fileCount++
                        }
                        else -> {
                            if (size > 0) skipFully(gzIn, ((size + 511) / 512) * 512)
                        }
                    }

                    // Set executable for bin/ files
                    if (outFile.exists() && outFile.isFile &&
                        (cleanName.startsWith("bin/") || cleanName.startsWith("sbin/") ||
                                cleanName.startsWith("usr/bin/") || cleanName.startsWith("usr/sbin/"))) {
                        outFile.setExecutable(true, false)
                    }
                }
            }
        }
        log("Java tar extraction: $fileCount files extracted")
    }

    // ─── Tar parsing helpers ───

    private fun extractTarEntry(input: java.io.InputStream, entryPath: String, target: File): Boolean {
        val headerBuf = ByteArray(512)
        while (true) {
            val headerRead = readFully(input, headerBuf)
            if (headerRead < 512) return false
            if (headerBuf.all { it == 0.toByte() }) {
                val nextBlock = ByteArray(512)
                val nextRead = readFully(input, nextBlock)
                if (nextRead < 512 || nextBlock.all { it == 0.toByte() }) return false
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

            if (size > 0) skipFully(input, ((size + 511) / 512) * 512)
        }
    }

    private fun parseTarName(header: ByteArray): String {
        val prefix = header.copyOfRange(345, 500).takeWhile { it != 0.toByte() }
            .toByteArray().toString(Charsets.UTF_8)
        val name = header.copyOfRange(0, 100).takeWhile { it != 0.toByte() }
            .toByteArray().toString(Charsets.UTF_8)
        return if (prefix.isNotEmpty()) "$prefix/$name" else name
    }

    private fun parseTarSize(header: ByteArray): Long {
        val sizeBytes = header.copyOfRange(124, 136)
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

    private fun logDirectoryContents(dir: File, depth: Int) {
        if (depth > 2) return
        val files = dir.listFiles() ?: return
        for (f in files.take(30)) {
            val prefix = "  ".repeat(depth)
            if (f.isDirectory) {
                log("$prefix ${f.name}/")
                logDirectoryContents(f, depth + 1)
            } else {
                log("$prefix ${f.name} (${f.length()} bytes)")
            }
        }
    }

    // ─── Environment config ───

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
