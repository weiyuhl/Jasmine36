package com.lhzkml.jasmine.core.proot

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Alpine minirootfs + PRoot 二进制的下载与初始化。
 *
 * 安装流程：
 * 1. 从 Alpine 官方 CDN 下载 proot-static .apk 包并提取静态二进制
 * 2. 从 Alpine 官方 CDN 下载 minirootfs tar.gz
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

        // Step 1: PRoot static binary (from Alpine proot-static .apk package)
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

            // Step 3: Extract
            onProgress(0.65f, "正在解压 rootfs...")
            extractTarGz(tarGz, paths.rootfsDir)
            tarGz.delete()

            // Step 4: Configure
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
                paths.prootBinary.canExecute() &&
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
     * 从 Alpine .apk 包中提取指定文件。
     * Alpine .apk 实际上是 gzipped tar 归档，可直接用 tar 解压。
     */
    private fun extractBinaryFromApk(apkFile: File, entryPath: String, target: File) {
        val tmpDir = File(apkFile.parentFile, "proot_extract_tmp")
        tmpDir.mkdirs()

        try {
            val process = ProcessBuilder(
                "tar", "xzf", apkFile.absolutePath,
                "-C", tmpDir.absolutePath,
                entryPath
            ).redirectErrorStream(true).start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw RuntimeException("Failed to extract PRoot from .apk (exit $exitCode): $output")
            }

            val extracted = File(tmpDir, entryPath)
            if (!extracted.exists()) {
                throw RuntimeException("PRoot binary not found in .apk at $entryPath")
            }

            target.parentFile?.mkdirs()
            extracted.copyTo(target, overwrite = true)
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    /**
     * 解压 .tar.gz 到目标目录。
     * 使用 Android 系统自带的 tar 命令（busybox/toybox）。
     */
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
