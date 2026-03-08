package com.lhzkml.jasmine.core.termux

import java.io.File

/**
 * Termux 路径管理
 * 
 * 管理 Termux 环境的所有路径，参考 Termux 的目录结构。
 */
data class TermuxPaths(
    /** 基础目录 (context.filesDir) */
    val baseDir: File,
    
    /** PREFIX 目录 (/data/data/com.yourapp/files/usr) */
    val prefixDir: File,
    
    /** STAGING PREFIX 目录（安装时使用） */
    val stagingPrefixDir: File,
    
    /** HOME 目录 (/data/data/com.yourapp/files/home) */
    val homeDir: File,
    
    /** TMP 目录 (/data/data/com.yourapp/files/tmp) */
    val tmpDir: File,
    
    /** 日志目录 */
    val logDir: File,
    
    /** STORAGE 目录（用于存储符号链接） */
    val storageDir: File
) {
    companion object {
        fun from(filesDir: File): TermuxPaths {
            return TermuxPaths(
                baseDir = filesDir,
                prefixDir = File(filesDir, "usr"),
                stagingPrefixDir = File(filesDir, "usr-staging"),
                homeDir = File(filesDir, "home"),
                tmpDir = File(filesDir, "tmp"),
                logDir = File(filesDir, "logs"),
                storageDir = File(File(filesDir, "home"), "storage")
            )
        }
    }
}
