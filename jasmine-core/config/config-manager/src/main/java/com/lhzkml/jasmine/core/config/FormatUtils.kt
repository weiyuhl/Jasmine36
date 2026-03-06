package com.lhzkml.jasmine.core.config

/**
 * 通用格式化工具
 */
object FormatUtils {

    /**
     * 将 token 数量格式化为简短形式（如 1000000 → "1M"，2048 → "2K"）
     */
    fun formatTokenCount(tokens: Int): String = when {
        tokens >= 1_000_000 -> "${tokens / 1_000_000}M"
        tokens >= 1_000 -> "${tokens / 1_000}K"
        else -> tokens.toString()
    }

    /**
     * 将 token 数量格式化为带单位的可读字符串（如 1500000 → "1.5M tokens"）
     */
    fun formatTokensWithUnit(n: Int): String = when {
        n >= 1_000_000 -> String.format("%.1fM tokens", n / 1_000_000.0)
        n >= 1_000 -> String.format("%.1fK tokens", n / 1_000.0)
        else -> "$n tokens"
    }

    /**
     * 根据文件扩展名返回简短文本图标
     */
    fun getFileIcon(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "kt", "kts" -> "K"
            "java" -> "J"
            "xml" -> "X"
            "json" -> "{}"
            "md" -> "M"
            "txt" -> "T"
            "gradle" -> "G"
            "py" -> "Py"
            "js", "ts" -> "JS"
            "html" -> "H"
            "css" -> "C"
            "yaml", "yml" -> "Y"
            "sh", "bat" -> "$"
            "png", "jpg", "jpeg", "gif", "webp", "svg" -> "Img"
            "properties" -> "P"
            "toml" -> "T"
            else -> "."
        }
    }
}
