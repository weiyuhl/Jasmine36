package com.lhzkml.jasmine.oss

import android.content.Context
import android.content.res.Resources
import java.nio.charset.StandardCharsets

/**
 * 解析 oss-licenses-plugin 生成的许可元数据和正文。
 * 格式：third_party_license_metadata 每行 "offset:length library_name"
 */
data class OssLicenseEntry(
    val name: String,
    val offset: Long,
    val length: Int
)

/** 手动集成的库（如 NDK 编译的 MNN），oss-licenses-plugin 无法自动收集 */
data class ManualLicenseEntry(
    val name: String,
    val licenseUrl: String
)

object OssLicenseLoader {

    /** 手动添加的许可（非 Maven 依赖，如 NDK 编译的 MNN） */
    val manualLicenses: List<ManualLicenseEntry> = listOf(
        ManualLicenseEntry("MNN", "http://www.apache.org/licenses/LICENSE-2.0.txt")
    )

    private const val METADATA_RES = "third_party_license_metadata"
    private const val LICENSES_RES = "third_party_licenses"

    fun loadLicenseList(context: Context): List<OssLicenseEntry> {
        val res = context.resources
        val metadataId = res.getIdentifier(METADATA_RES, "raw", context.packageName)
        if (metadataId == 0) return emptyList()

        return res.openRawResource(metadataId).use { stream ->
            stream.bufferedReader(StandardCharsets.UTF_8).lineSequence()
                .mapNotNull { line ->
                    val sep = line.indexOf(' ')
                    if (sep <= 0) return@mapNotNull null
                    val range = line.substring(0, sep)
                    val name = line.substring(sep + 1).trim()
                    val colon = range.indexOf(':')
                    if (colon <= 0) return@mapNotNull null
                    val offset = range.substring(0, colon).toLongOrNull() ?: return@mapNotNull null
                    val length = range.substring(colon + 1).toIntOrNull() ?: return@mapNotNull null
                    OssLicenseEntry(name = name, offset = offset, length = length)
                }
                .toList()
        }
    }

    fun loadLicenseText(context: Context, entry: OssLicenseEntry): String? {
        val res = context.resources
        val licensesId = res.getIdentifier(LICENSES_RES, "raw", context.packageName)
        if (licensesId == 0) return null

        return res.openRawResource(licensesId).use { stream ->
            stream.skip(entry.offset)
            val bytes = ByteArray(entry.length)
            val read = stream.read(bytes)
            if (read <= 0) return@use null
            String(bytes, 0, read, StandardCharsets.UTF_8)
        }
    }

    fun hasLicenses(context: Context): Boolean {
        val res = context.resources
        return res.getIdentifier(METADATA_RES, "raw", context.packageName) != 0 &&
            res.getIdentifier(LICENSES_RES, "raw", context.packageName) != 0
    }
}
