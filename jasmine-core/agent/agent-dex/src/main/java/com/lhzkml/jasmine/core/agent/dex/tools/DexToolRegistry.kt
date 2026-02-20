package com.lhzkml.jasmine.core.agent.dex.tools

import com.lhzkml.jasmine.core.agent.tools.Tool
import com.lhzkml.jasmine.core.agent.tools.ToolRegistry

/**
 * DEX 编辑工具注册
 * 提供所有 DEX/APK 编辑工具的集合，方便一次性注册到 ToolRegistry
 */
object DexToolRegistry {

    /**
     * 获取所有 DEX 编辑工具
     */
    fun allTools(): List<Tool> = listOf(
        // APK/DEX 会话管理
        DexOpenApkTool,
        DexOpenTool,
        DexSaveTool,
        DexCloseTool,
        DexListSessionsTool,
        // 类操作
        DexListClassesTool,
        DexSearchTool,
        DexGetClassTool,
        DexModifyClassTool,
        DexAddClassTool,
        DexDeleteClassTool,
        DexRenameClassTool,
        // 方法/字段操作
        DexGetMethodTool,
        DexModifyMethodTool,
        DexListMethodsTool,
        DexListFieldsTool,
        DexListStringsTool,
        // 分析工具
        DexFindMethodXrefsTool,
        DexFindFieldXrefsTool,
        DexSmaliToJavaTool,
        // APK 文件操作
        ApkListFilesTool,
        ApkReadFileTool,
        ApkSearchTextTool,
        ApkDeleteFileTool,
        ApkAddFileTool,
        ApkListResourcesTool,
        // Manifest 操作
        ApkGetManifestTool,
        ApkModifyManifestTool,
        ApkReplaceInManifestTool,
        // 资源操作
        ApkGetResourceTool,
        ApkModifyResourceTool,
        // ARSC 搜索
        ApkSearchArscStringsTool,
        ApkSearchArscResourcesTool,
        // 二进制解析（移植自 C++ 工具，纯 Kotlin 实现）
        ApkParseManifestTool,
        ApkSearchManifestTool,
        ApkParseArscTool
    )

    /**
     * 将所有 DEX 工具注册到 ToolRegistry
     */
    fun registerAll(registry: ToolRegistry) {
        allTools().forEach { registry.register(it) }
    }
}
