package com.lhzkml.jasmine.core.prompt.llm

import java.io.File

/**
 * 加密抽象接口
 * 参考 koog 的 Encryption，提供透明的数据加密/解密。
 */
interface Encryption {
    /**
     * 加密文本
     * @param text 明文
     * @return 密文
     */
    fun encrypt(text: String): String

    /**
     * 解密文本
     * @param text 密文
     * @return 明文
     */
    fun decrypt(text: String): String
}

/**
 * 存储抽象接口
 * 参考 koog 的 Storage<Path>，提供统一的文件操作接口。
 *
 * 使用 java.io.File 作为路径类型（Android 友好）。
 */
interface MemoryStorage {
    /** 检查文件/目录是否存在 */
    suspend fun exists(path: File): Boolean

    /** 读取文件内容，不存在返回 null */
    suspend fun read(path: File): String?

    /** 写入文件内容，自动创建父目录 */
    suspend fun write(path: File, content: String)

    /** 创建目录（含父目录） */
    suspend fun createDirectories(path: File)
}

/**
 * 简单存储实现 — 直接读写文件
 * 参考 koog 的 SimpleStorage
 */
class SimpleMemoryStorage : MemoryStorage {
    override suspend fun exists(path: File): Boolean = path.exists()

    override suspend fun read(path: File): String? {
        if (!path.exists()) return null
        return path.readText(Charsets.UTF_8)
    }

    override suspend fun write(path: File, content: String) {
        path.parentFile?.mkdirs()
        if (!path.exists()) {
            path.createNewFile()
        }
        path.writeText(content, Charsets.UTF_8)
    }

    override suspend fun createDirectories(path: File) {
        if (!path.exists()) {
            path.mkdirs()
        }
    }
}

/**
 * 加密存储实现 — 透明加密/解密文件内容
 * 参考 koog 的 EncryptedStorage
 *
 * @param encryption 加密/解密实现
 */
class EncryptedMemoryStorage(
    private val encryption: Encryption
) : MemoryStorage {
    override suspend fun exists(path: File): Boolean = path.exists()

    override suspend fun read(path: File): String? {
        if (!path.exists()) return null
        val content = path.readText(Charsets.UTF_8)
        return encryption.decrypt(content)
    }

    override suspend fun write(path: File, content: String) {
        path.parentFile?.mkdirs()
        val encrypted = encryption.encrypt(content)
        if (!path.exists()) {
            path.createNewFile()
        }
        path.writeText(encrypted, Charsets.UTF_8)
    }

    override suspend fun createDirectories(path: File) {
        if (!path.exists()) {
            path.mkdirs()
        }
    }
}
