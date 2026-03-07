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
        try { logFile?.appendText(line) } catch (_: Exception) {}
    }

    suspend fun install(
        paths: PRootPaths,
        cacheDir: File,
        onProgress: (Float, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        paths.logDir.mkdirs()
        logFile = File(paths.logDir, "install_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.log")
        logFile?.writeText("")

        log("=== PRoot Alpine Install Start ===")
        log("baseDir: ${paths.baseDir.absolutePath}")
        log("rootfsDir: ${paths.rootfsDir.absolutePath}")
        log("prootBinary: ${paths.prootBinary.absolutePath}")
        log("logDir: ${paths.logDir.absolutePath}")
        log("cacheDir: ${cacheDir.absolutePath}")

        paths.baseDir.mkdirs()
        paths.rootfsDir.mkdirs()
        paths.homeDir.mkdirs()

        // Step 1: PRoot binary (bundled as libproot.so in nativeLibraryDir)
        onProgress(0.05f, "正在检查 PRoot 二进制...")
        log("PRoot binary: path=${paths.prootBinary.absolutePath}")
        log("PRoot binary: exists=${paths.prootBinary.exists()}, size=${paths.prootBinary.length()}, canExec=${paths.prootBinary.canExecute()}")
        log("PRoot loader: path=${paths.prootLoader.absolutePath}, exists=${paths.prootLoader.exists()}")
        if (!paths.prootBinary.exists() || paths.prootBinary.length() < 1024) {
            val err = "PRoot 二进制不可用：${paths.prootBinary.absolutePath}\n" +
                    "exists=${paths.prootBinary.exists()}, size=${paths.prootBinary.length()}\n" +
                    "PRoot 应以 libproot.so 形式打包在 APK 的 jniLibs 中"
            log("ERROR: $err")
            throw RuntimeException(err)
        }

        // Step 1.5: Prepare libtalloc.so.2 symlink for Termux PRoot dynamic linking
        prepareTallocLib(paths)

        // Step 2: Alpine minirootfs
        val tarGz = File(cacheDir, AlpineConstants.MINIROOTFS_FILENAME)
        val markerFile = File(paths.rootfsDir, ".jasmine_installed")
        if (!markerFile.exists()) {
            if (!tarGz.exists() || tarGz.length() < 1024) {
                onProgress(0.30f, "正在下载 Alpine minirootfs...")
                log("Downloading rootfs from ${AlpineConstants.MINIROOTFS_URL}")
                downloadFile(AlpineConstants.MINIROOTFS_URL, tarGz, cacheDir) { progress ->
                    onProgress(0.30f + progress * 0.30f, "正在下载 Alpine minirootfs...")
                }
            }
            log("rootfs tar.gz: ${tarGz.length()} bytes")

            onProgress(0.65f, "正在解压 rootfs...")
            extractRootfs(tarGz, paths.rootfsDir)

            val binBusybox = File(paths.rootfsDir, "bin/busybox")
            val binSh = File(paths.rootfsDir, "bin/sh")
            val binShLinkExists = java.nio.file.Files.exists(
                binSh.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS
            )
            log("After extraction: bin/busybox exists=${binBusybox.exists()} size=${binBusybox.length()}")
            log("After extraction: bin/sh linkExists=$binShLinkExists exists=${binSh.exists()}")
            logDirectoryTree(File(paths.rootfsDir, "bin"), "bin/", 0)

            if (!binBusybox.exists() || binBusybox.length() < 1024) {
                val topFiles = paths.rootfsDir.listFiles()
                val binFiles = File(paths.rootfsDir, "bin").listFiles()
                val err = buildString {
                    appendLine("rootfs 解压失败：bin/busybox 不存在或大小异常")
                    appendLine("tar.gz 大小: ${tarGz.length()} bytes")
                    appendLine("rootfsDir top: ${topFiles?.map { "${it.name}(${if (it.isDirectory) "dir" else "${it.length()}"})" }}")
                    appendLine("bin/ 内容 (${binFiles?.size ?: 0} 项): ${binFiles?.take(20)?.map { "${it.name}(${it.length()})" }}")
                }
                log("ERROR: $err")
                tarGz.delete()
                throw RuntimeException(err)
            }
            tarGz.delete()

            onProgress(0.85f, "正在配置环境...")
            configureEnvironment(paths)
            markerFile.writeText("installed")
            log("Marker written, installation complete")
        } else {
            log("Marker already exists, skipping extraction")
        }

        log("=== Install Complete ===")
        onProgress(1.0f, "安装完成")
    }

    suspend fun uninstall(paths: PRootPaths) = withContext(Dispatchers.IO) {
        if (paths.baseDir.exists()) paths.baseDir.deleteRecursively()
    }

    fun isInstalled(paths: PRootPaths): Boolean {
        return paths.prootBinary.exists() &&
                paths.prootBinary.length() > 1024 &&
                File(paths.rootfsDir, ".jasmine_installed").exists() &&
                File(paths.rootfsDir, "bin/busybox").let { it.exists() && it.length() > 1024 }
    }

    fun getLatestLogFile(paths: PRootPaths): File? {
        return paths.logDir.listFiles()
            ?.filter { it.name.startsWith("install_") && it.name.endsWith(".log") }
            ?.maxByOrNull { it.lastModified() }
    }

    // ─── Download ───

    private fun downloadFile(urlStr: String, target: File, cacheDir: File, onProgress: (Float) -> Unit) {
        val tmpFile = File(cacheDir, target.name + ".tmp")
        tmpFile.parentFile?.mkdirs()
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 30_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = true
        try {
            conn.connect()
            val code = conn.responseCode
            if (code != 200) throw RuntimeException("HTTP $code from $urlStr")
            val totalBytes = conn.contentLengthLong
            var downloaded = 0L
            conn.inputStream.buffered().use { input ->
                FileOutputStream(tmpFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalBytes > 0) onProgress((downloaded.toFloat() / totalBytes).coerceAtMost(1f))
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
            log("APK: trying system tar (specific)...")
            val extracted = trySystemTarExtract(apkFile, entryPath, tmpDir)
            if (extracted != null && extracted.exists() && extracted.length() > 1024) {
                log("APK: system tar (specific) OK: ${extracted.length()} bytes")
                target.parentFile?.mkdirs()
                extracted.copyTo(target, overwrite = true)
                return
            }
            log("APK: trying system tar (all)...")
            val extractedAll = trySystemTarExtractAll(apkFile, tmpDir)
            if (extractedAll) {
                val binary = File(tmpDir, entryPath)
                if (binary.exists() && binary.length() > 1024) {
                    log("APK: system tar (all) OK: ${binary.length()} bytes")
                    target.parentFile?.mkdirs()
                    binary.copyTo(target, overwrite = true)
                    return
                }
            }
            log("APK: trying Java parser...")
            val ok = extractWithJavaTarParser(apkFile, entryPath, target)
            if (ok) {
                log("APK: Java parser OK: ${target.length()} bytes")
                return
            }
            throw RuntimeException("所有提取方法均失败 apk=${apkFile.length()} bytes")
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    private fun trySystemTarExtract(apkFile: File, entryPath: String, tmpDir: File): File? {
        return try {
            val p = ProcessBuilder("tar", "xzf", apkFile.absolutePath, "-C", tmpDir.absolutePath, entryPath)
                .redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            val exit = p.waitFor()
            log("tar(specific) exit=$exit out=${out.take(200)}")
            File(tmpDir, entryPath).takeIf { it.exists() }
        } catch (e: Exception) { log("tar(specific) err: ${e.message}"); null }
    }

    private fun trySystemTarExtractAll(apkFile: File, tmpDir: File): Boolean {
        return try {
            val p = ProcessBuilder("tar", "xzf", apkFile.absolutePath, "-C", tmpDir.absolutePath)
                .redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            val exit = p.waitFor()
            log("tar(all) exit=$exit out=${out.take(200)}")
            exit == 0
        } catch (e: Exception) { log("tar(all) err: ${e.message}"); false }
    }

    private fun extractWithJavaTarParser(apkFile: File, entryPath: String, target: File): Boolean {
        return try {
            FileInputStream(apkFile).buffered().use { raw ->
                val ms = MultiStreamGZIPInputStream(BufferedInputStream(raw, 65536))
                extractSingleTarEntry(ms, entryPath, target)
            }
        } catch (e: Exception) { log("Java parser err: ${e.message}"); false }
    }

    // ─── Rootfs extraction ───

    private fun extractRootfs(tarGz: File, targetDir: File) {
        targetDir.mkdirs()

        // Method 1: system tar
        log("rootfs: trying system tar...")
        val p = ProcessBuilder("tar", "xzf", tarGz.absolutePath, "-C", targetDir.absolutePath)
            .redirectErrorStream(true).start()
        val tarOut = p.inputStream.bufferedReader().readText()
        val tarExit = p.waitFor()
        log("rootfs tar: exit=$tarExit out=${tarOut.take(300)}")

        if (tarExit == 0 && File(targetDir, "bin/sh").exists()) {
            log("rootfs: system tar succeeded, bin/sh exists")
            return
        }
        log("rootfs: system tar incomplete (bin/sh missing), using Java extraction")

        // Method 2: Java extraction with full link handling
        extractTarGzFullJava(tarGz, targetDir)
    }

    /**
     * 纯 Java 完整解压 tar.gz，支持目录/普通文件/符号链接/硬链接。
     * 链接条目延迟到第二遍处理，确保目标文件已存在。
     */
    private fun extractTarGzFullJava(tarGz: File, targetDir: File) {
        data class DeferredLink(val cleanName: String, val linkName: String, val isSymlink: Boolean)

        val deferredLinks = mutableListOf<DeferredLink>()
        var fileCount = 0
        var dirCount = 0
        var entryIndex = 0
        var longName: String? = null

        FileInputStream(tarGz).buffered().use { fileIn ->
            GZIPInputStream(fileIn, 65536).use { gzIn ->
                val hdr = ByteArray(512)
                var consecutiveZero = 0

                while (true) {
                    val n = readFully(gzIn, hdr)
                    if (n < 512) { log("Java: incomplete header at #$entryIndex (read $n)"); break }

                    if (hdr.all { it == 0.toByte() }) {
                        consecutiveZero++
                        if (consecutiveZero >= 2) break
                        continue
                    }
                    consecutiveZero = 0
                    entryIndex++

                    val rawName = parseTarName(hdr)
                    val size = parseTarSize(hdr)
                    val typeFlag = hdr[156]
                    val rawLink = hdr.copyOfRange(157, 257)
                        .takeWhile { it != 0.toByte() }.toByteArray().toString(Charsets.UTF_8)

                    val typeCh = if (typeFlag in 32..126) typeFlag.toInt().toChar() else '?'

                    // GNU long name extension (type 'L')
                    if (typeFlag == 'L'.code.toByte()) {
                        val paddedSize = ((size + 511) / 512) * 512
                        val buf = ByteArray(paddedSize.toInt())
                        readFully(gzIn, buf)
                        longName = buf.copyOf(size.toInt()).toString(Charsets.UTF_8).trimEnd('\u0000')
                        continue
                    }
                    // GNU long link extension (type 'K')
                    if (typeFlag == 'K'.code.toByte()) {
                        val paddedSize = ((size + 511) / 512) * 512
                        skipFully(gzIn, paddedSize)
                        continue
                    }
                    // PAX headers
                    if (typeFlag == 'x'.code.toByte() || typeFlag == 'g'.code.toByte()) {
                        val paddedSize = ((size + 511) / 512) * 512
                        skipFully(gzIn, paddedSize)
                        continue
                    }

                    val name = (longName ?: rawName)
                    longName = null
                    val cleanName = name.removePrefix("./").removePrefix("/")

                    if (cleanName.isEmpty()) {
                        if (size > 0) skipFully(gzIn, ((size + 511) / 512) * 512)
                        continue
                    }

                    if (entryIndex <= 30 || entryIndex % 100 == 0) {
                        log("  #$entryIndex $typeCh '$cleanName' size=$size link='$rawLink'")
                    }

                    val outFile = File(targetDir, cleanName)

                    when {
                        // Directory
                        typeFlag == '5'.code.toByte() || cleanName.endsWith("/") -> {
                            outFile.mkdirs()
                            dirCount++
                            if (size > 0) skipFully(gzIn, ((size + 511) / 512) * 512)
                        }
                        // Symlink
                        typeFlag == '2'.code.toByte() -> {
                            deferredLinks.add(DeferredLink(cleanName, rawLink, isSymlink = true))
                        }
                        // Hardlink
                        typeFlag == '1'.code.toByte() -> {
                            deferredLinks.add(DeferredLink(cleanName, rawLink, isSymlink = false))
                        }
                        // Regular file
                        typeFlag == 0.toByte() || typeFlag == '0'.code.toByte() -> {
                            outFile.parentFile?.mkdirs()
                            val paddedSize = ((size + 511) / 512) * 512
                            if (size > 0) {
                                FileOutputStream(outFile).use { fos ->
                                    val buf = ByteArray(8192)
                                    var remaining = size
                                    while (remaining > 0) {
                                        val toRead = minOf(remaining, buf.size.toLong()).toInt()
                                        val rd = gzIn.read(buf, 0, toRead)
                                        if (rd <= 0) break
                                        fos.write(buf, 0, rd)
                                        remaining -= rd
                                    }
                                }
                                val padding = paddedSize - size
                                if (padding > 0) skipFully(gzIn, padding)
                            }
                            setExecIfNeeded(cleanName, outFile)
                            fileCount++
                        }
                        else -> {
                            if (size > 0) skipFully(gzIn, ((size + 511) / 512) * 512)
                        }
                    }
                }
            }
        }

        log("Java tar pass1: $fileCount files, $dirCount dirs, ${deferredLinks.size} deferred links, $entryIndex total entries")

        // Pass 2: create all links
        var linkOk = 0
        var linkFail = 0
        for (link in deferredLinks) {
            val outFile = File(targetDir, link.cleanName)
            outFile.parentFile?.mkdirs()

            if (link.isSymlink) {
                val ok = createSymlink(outFile, link.linkName, targetDir)
                if (ok) linkOk++ else linkFail++
            } else {
                val targetFile = File(targetDir, link.linkName.removePrefix("./").removePrefix("/"))
                if (targetFile.exists()) {
                    try {
                        targetFile.copyTo(outFile, overwrite = true)
                        setExecIfNeeded(link.cleanName, outFile)
                        linkOk++
                    } catch (e: Exception) {
                        log("hardlink copy fail: ${link.cleanName} -> ${link.linkName}: ${e.message}")
                        linkFail++
                    }
                } else {
                    log("hardlink target missing: ${link.cleanName} -> ${link.linkName}")
                    linkFail++
                }
            }
        }
        log("Java tar pass2: $linkOk links OK, $linkFail links failed")
    }

    private fun createSymlink(outFile: File, linkTarget: String, targetDir: File): Boolean {
        val linkPath = outFile.toPath()

        // Convert absolute symlink targets to relative paths.
        // e.g. bin/sh -> /bin/busybox becomes bin/sh -> busybox
        // This ensures File.exists() works correctly (doesn't resolve to host FS).
        val effectiveTarget = if (linkTarget.startsWith("/")) {
            val absInRootfs = File(targetDir, linkTarget.removePrefix("/"))
            try {
                val relPath = outFile.parentFile.toPath().relativize(absInRootfs.toPath())
                relPath.toString().replace('\\', '/')
            } catch (_: Exception) {
                linkTarget
            }
        } else {
            linkTarget
        }

        // Try native symlink with the (possibly relative) target
        try {
            java.nio.file.Files.deleteIfExists(linkPath)
            java.nio.file.Files.createSymbolicLink(linkPath, java.nio.file.Paths.get(effectiveTarget))
            return true
        } catch (e: Exception) {
            log("symlink native fail: ${outFile.name} -> $effectiveTarget: ${e.message}")
        }

        // Fallback: copy the target file from within rootfs
        val resolvedFile = if (linkTarget.startsWith("/")) {
            File(targetDir, linkTarget.removePrefix("/"))
        } else {
            File(outFile.parentFile, linkTarget)
        }
        if (resolvedFile.exists() && resolvedFile.isFile) {
            try {
                resolvedFile.copyTo(outFile, overwrite = true)
                setExecIfNeeded(outFile.name, outFile)
                return true
            } catch (e: Exception) {
                log("symlink copy fail: ${outFile.name} -> $linkTarget: ${e.message}")
            }
        }

        log("symlink all methods fail: ${outFile.name} -> $linkTarget (effective: $effectiveTarget)")
        return false
    }

    private fun setExecIfNeeded(cleanName: String, file: File) {
        if (cleanName.startsWith("bin/") || cleanName.startsWith("sbin/") ||
            cleanName.startsWith("usr/bin/") || cleanName.startsWith("usr/sbin/") ||
            cleanName.startsWith("usr/local/bin/")) {
            ensureExecutable(file)
        }
    }

    private fun ensureExecutable(file: File) {
        file.setExecutable(true, false)
        file.setReadable(true, false)
        if (file.canExecute()) return

        try {
            val p = Runtime.getRuntime().exec(arrayOf("chmod", "755", file.absolutePath))
            p.waitFor()
            log("chmod 755 ${file.name}: exit=${p.exitValue()}, canExec=${file.canExecute()}")
        } catch (e: Exception) {
            log("chmod failed for ${file.name}: ${e.message}")
        }

        if (!file.canExecute()) {
            try {
                val p = Runtime.getRuntime().exec(arrayOf("chmod", "+x", file.absolutePath))
                p.waitFor()
                log("chmod +x ${file.name}: exit=${p.exitValue()}, canExec=${file.canExecute()}")
            } catch (e: Exception) {
                log("chmod +x failed for ${file.name}: ${e.message}")
            }
        }
    }

    private fun prepareTallocLib(paths: PRootPaths) {
        val libDir = paths.libSearchDir
        libDir.mkdirs()
        val tallocTarget = File(libDir, "libtalloc.so.2")
        if (tallocTarget.exists()) {
            log("libtalloc.so.2 already exists: ${tallocTarget.absolutePath}")
            return
        }
        val nativeDir = paths.nativeLibDir ?: return
        val source = File(nativeDir, "libtalloc.so")
        if (!source.exists()) {
            log("WARNING: libtalloc.so not found in nativeLibDir: ${nativeDir.absolutePath}")
            return
        }
        source.copyTo(tallocTarget, overwrite = true)
        log("Copied libtalloc.so -> libtalloc.so.2 (${tallocTarget.length()} bytes) at ${libDir.absolutePath}")
    }

    // ─── Single entry extraction (for APK) ───

    private fun extractSingleTarEntry(input: java.io.InputStream, entryPath: String, target: File): Boolean {
        val hdr = ByteArray(512)
        while (true) {
            val n = readFully(input, hdr)
            if (n < 512) return false
            if (hdr.all { it == 0.toByte() }) {
                val next = ByteArray(512)
                val nr = readFully(input, next)
                if (nr < 512 || next.all { it == 0.toByte() }) return false
                continue
            }
            val name = parseTarName(hdr)
            val size = parseTarSize(hdr)
            val typeFlag = hdr[156]
            val isFile = typeFlag == 0.toByte() || typeFlag == '0'.code.toByte()
            val normalized = name.removePrefix("./").removePrefix("/")
            val normalizedEntry = entryPath.removePrefix("./").removePrefix("/")

            if (isFile && normalized == normalizedEntry && size > 0) {
                target.parentFile?.mkdirs()
                FileOutputStream(target).use { out ->
                    val buf = ByteArray(8192)
                    var remaining = size
                    while (remaining > 0) {
                        val toRead = minOf(remaining, buf.size.toLong()).toInt()
                        val rd = input.read(buf, 0, toRead)
                        if (rd <= 0) break
                        out.write(buf, 0, rd)
                        remaining -= rd
                    }
                }
                return target.exists() && target.length() > 0
            }
            if (size > 0) skipFully(input, ((size + 511) / 512) * 512)
        }
    }

    // ─── Tar parsing helpers ───

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
            for (i in 4 until 12) size = (size shl 8) or (sizeBytes[i].toLong() and 0xFF)
            return size
        }
        val sizeStr = sizeBytes.takeWhile { it != 0.toByte() && it != ' '.code.toByte() }
            .toByteArray().toString(Charsets.UTF_8).trim()
        return if (sizeStr.isEmpty()) 0L else sizeStr.toLongOrNull(8) ?: 0L
    }

    private fun readFully(input: java.io.InputStream, buf: ByteArray): Int {
        var off = 0
        while (off < buf.size) {
            val r = input.read(buf, off, buf.size - off)
            if (r <= 0) return off
            off += r
        }
        return off
    }

    private fun skipFully(input: java.io.InputStream, bytes: Long) {
        var remaining = bytes
        val buf = ByteArray(8192)
        while (remaining > 0) {
            val toRead = minOf(remaining, buf.size.toLong()).toInt()
            val r = input.read(buf, 0, toRead)
            if (r <= 0) break
            remaining -= r
        }
    }

    private fun logDirectoryTree(dir: File, prefix: String, depth: Int) {
        if (depth > 1 || !dir.exists()) return
        val files = dir.listFiles() ?: return
        for (f in files.take(30)) {
            if (f.isDirectory) {
                log("  $prefix${f.name}/")
                logDirectoryTree(f, "$prefix${f.name}/", depth + 1)
            } else {
                val extra = if (f.length() > 0) "${f.length()}b" else "empty"
                log("  $prefix${f.name} ($extra)")
            }
        }
        if (files.size > 30) log("  $prefix... and ${files.size - 30} more")
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

        setupFakeProcSysData(paths)
    }

    private fun setupFakeProcSysData(paths: PRootPaths) {
        val procDir = File(paths.rootfsDir, "proc")
        procDir.mkdirs()

        File(procDir, ".loadavg").writeText("0.12 0.07 0.02 2/165 765\n")

        File(procDir, ".stat").writeText(buildString {
            appendLine("cpu  1050008 127632 898432 43586190 175486 462292 205338 0 0 0")
            for (i in 0..7) {
                appendLine("cpu$i  131251 15954 112304 5448274 21936 57787 25667 0 0 0")
            }
            appendLine("intr 53261351 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0")
            appendLine("ctxt 38014093")
            appendLine("btime ${System.currentTimeMillis() / 1000 - 3600}")
            appendLine("processes 26442")
            appendLine("procs_running 1")
            appendLine("procs_blocked 0")
            appendLine("softirq 12739328 23 5765792 39 1439 1231 0 88 3292182 0 3679534")
        })

        File(procDir, ".uptime").writeText("124689.08 993073.57\n")

        File(procDir, ".version").writeText(
            "Linux version 6.17.0-PRoot-Distro (proot@termux) (gcc (GCC) 13.3.0, GNU ld (GNU Binutils) 2.42) #1 SMP PREEMPT_DYNAMIC Fri, 10 Oct 2025 00:00:00 +0000\n"
        )

        File(procDir, ".vmstat").writeText(buildString {
            appendLine("nr_free_pages 146031")
            appendLine("nr_zone_inactive_anon 196744")
            appendLine("nr_zone_active_anon 301503")
            appendLine("nr_zone_inactive_file 60829")
            appendLine("nr_zone_active_file 309462")
            appendLine("nr_zone_unevictable 164")
            appendLine("nr_zone_write_pending 8")
            appendLine("nr_mlock 34")
            appendLine("nr_bounce 0")
            appendLine("pgpgin 41674710")
            appendLine("pgpgout 18250656")
        })

        File(procDir, ".sysctl_entry_cap_last_cap").writeText("40\n")
        File(procDir, ".sysctl_inotify_max_user_watches").writeText("524288\n")

        val sysDir = File(paths.rootfsDir, "sys/.empty")
        sysDir.mkdirs()

        log("Fake /proc and /sys data created")
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
        if (b1 == 0x1f && b2 == 0x8b) currentGzip = GZIPInputStream(rawInput)
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
            val rd = gz.read(buf, off, len)
            if (rd > 0) return rd
            if (rd == -1 && !tryNextStream()) return -1
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
