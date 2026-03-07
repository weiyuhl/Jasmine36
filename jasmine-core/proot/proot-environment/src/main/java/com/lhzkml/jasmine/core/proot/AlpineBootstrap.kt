package com.lhzkml.jasmine.core.proot

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.readAvailable
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
 * 1. 下载 PRoot arm64 静态二进制
 * 2. 下载 Alpine minirootfs tar.gz
 * 3. 解压 rootfs
 * 4. 配置 DNS / APK 镜像源
 */
object AlpineBootstrap {

    private val httpClient by lazy { HttpClient(OkHttp) }

    suspend fun install(
        paths: PRootPaths,
        cacheDir: File,
        onProgress: (Float, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        paths.baseDir.mkdirs()
        paths.rootfsDir.mkdirs()
        paths.homeDir.mkdirs()

        // Step 1: PRoot binary
        if (!paths.prootBinary.exists() || paths.prootBinary.length() < 1024) {
            onProgress(0.05f, "正在下载 PRoot 二进制...")
            downloadFile(
                AlpineConstants.PROOT_DOWNLOAD_URL,
                paths.prootBinary,
                cacheDir
            ) { progress ->
                onProgress(0.05f + progress * 0.20f, "正在下载 PRoot 二进制...")
            }
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
            tmpFile.renameTo(target)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 解压 .tar.gz 到目标目录。
     * 使用 Android 系统自带的 tar 命令（busybox/toybox），
     * 因为 Java 的 TarInputStream 不在标准库中。
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
        // DNS
        val resolv = File(paths.rootfsDir, "etc/resolv.conf")
        resolv.parentFile?.mkdirs()
        resolv.writeText("nameserver ${AlpineConstants.DEFAULT_DNS}\nnameserver 8.8.8.8\n")

        // APK repositories
        val repos = File(paths.rootfsDir, "etc/apk/repositories")
        repos.parentFile?.mkdirs()
        repos.writeText(AlpineConstants.DEFAULT_REPOSITORIES.joinToString("\n") + "\n")

        // Minimal /etc/passwd and /etc/group for root
        val passwd = File(paths.rootfsDir, "etc/passwd")
        if (!passwd.exists() || !passwd.readText().contains("root")) {
            passwd.writeText("root:x:0:0:root:/root:/bin/sh\n")
        }
        val group = File(paths.rootfsDir, "etc/group")
        if (!group.exists() || !group.readText().contains("root")) {
            group.writeText("root:x:0:root\n")
        }

        // Ensure /tmp exists
        File(paths.rootfsDir, "tmp").mkdirs()

        // Profile for interactive shells
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
