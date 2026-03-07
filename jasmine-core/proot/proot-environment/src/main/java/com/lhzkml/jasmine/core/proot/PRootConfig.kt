package com.lhzkml.jasmine.core.proot

import java.io.File

data class PRootResult(
    val output: String,
    val exitCode: Int,
    val timedOut: Boolean = false
)

data class PRootPaths(
    val baseDir: File,
    val rootfsDir: File,
    val prootBinary: File,
    val homeDir: File
) {
    companion object {
        fun from(filesDir: File): PRootPaths {
            val baseDir = File(filesDir, "proot")
            val rootfsDir = File(baseDir, "alpine-rootfs")
            return PRootPaths(
                baseDir = baseDir,
                rootfsDir = rootfsDir,
                prootBinary = File(baseDir, "proot-arm64"),
                homeDir = File(rootfsDir, "root")
            )
        }
    }
}

object AlpineConstants {
    const val ALPINE_VERSION = "3.21"
    const val MINIROOTFS_FILENAME = "alpine-minirootfs-3.21.3-aarch64.tar.gz"
    const val MINIROOTFS_URL =
        "https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/aarch64/$MINIROOTFS_FILENAME"
    const val PROOT_DOWNLOAD_URL =
        "https://raw.githubusercontent.com/proot-me/proot-static-build/master/static/proot-aarch64"

    val DEFAULT_BIND_PATHS = listOf(
        "/dev",
        "/proc",
        "/sys"
    )

    val DEFAULT_REPOSITORIES = listOf(
        "https://dl-cdn.alpinelinux.org/alpine/v$ALPINE_VERSION/main",
        "https://dl-cdn.alpinelinux.org/alpine/v$ALPINE_VERSION/community"
    )

    const val DEFAULT_DNS = "9.9.9.9"
}
