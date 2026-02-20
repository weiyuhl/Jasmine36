package com.lhzkml.jasmine.core.agent.dex

import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.iface.ClassDef
import org.jf.dexlib2.iface.DexFile
import org.jf.dexlib2.writer.io.MemoryDataStore
import org.jf.dexlib2.writer.pool.DexPool
import org.jf.smali.Smali
import org.jf.smali.SmaliOptions
import org.jf.baksmali.BaksmaliOptions
import org.jf.baksmali.Adaptors.ClassDefinition
import org.jf.baksmali.formatter.BaksmaliWriter
import java.io.File
import java.io.StringWriter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import java.io.FileOutputStream
import java.io.BufferedOutputStream

/**
 * DEX 编辑会话
 * 移植自 AetherLink 的 dex-editor 概念
 * 管理一个 APK 中多个 DEX 文件的编辑状态
 */
class DexSession(
    val id: String,
    val apkPath: String,
    val dexFileNames: List<String>
) {
    /** 所有已加载的类 (className -> ClassEntry) */
    private val classes = ConcurrentHashMap<String, ClassEntry>()

    /** 已修改的类名集合 */
    private val modifiedClasses = mutableSetOf<String>()

    /** 已添加的类名集合 */
    private val addedClasses = mutableSetOf<String>()

    /** 已删除的类名集合 */
    private val deletedClasses = mutableSetOf<String>()

    /** 原始 DexFile 对象 (dexFileName -> DexFile) */
    private val dexFiles = mutableMapOf<String, DexFile>()

    /** 类名到 DEX 文件名的映射 */
    private val classToFile = mutableMapOf<String, String>()

    val createdAt: Long = System.currentTimeMillis()

    data class ClassEntry(
        val classDef: ClassDef,
        var smaliContent: String? = null,
        val dexFileName: String
    )

    /**
     * 加载 DEX 文件
     */
    fun load() {
        val apkFile = File(apkPath)
        if (!apkFile.exists()) throw IllegalArgumentException("APK not found: $apkPath")

        val zipFile = ZipFile(apkFile)
        try {
            for (dexName in dexFileNames) {
                val entry = zipFile.getEntry(dexName)
                    ?: throw IllegalArgumentException("DEX not found in APK: $dexName")

                // 提取 DEX 到临时文件
                val tempDex = File.createTempFile("jasmine_dex_", ".dex")
                tempDex.deleteOnExit()
                zipFile.getInputStream(entry).use { input ->
                    tempDex.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val dexFile = DexFileFactory.loadDexFile(tempDex, Opcodes.getDefault())
                dexFiles[dexName] = dexFile

                for (classDef in dexFile.classes) {
                    val className = normalizeClassName(classDef.type)
                    classes[className] = ClassEntry(classDef, null, dexName)
                    classToFile[className] = dexName
                }

                tempDex.delete()
            }
        } finally {
            zipFile.close()
        }
    }

    /**
     * 列出所有类
     */
    fun listClasses(
        packageFilter: String? = null,
        offset: Int = 0,
        limit: Int = 100
    ): ClassListResult {
        var filtered = classes.keys.toList().sorted()
        if (!packageFilter.isNullOrEmpty()) {
            filtered = filtered.filter { it.startsWith(packageFilter) }
        }
        val total = filtered.size
        val paged = filtered.drop(offset).take(limit)
        return ClassListResult(paged, total, offset, limit)
    }

    /**
     * 搜索
     */
    fun search(
        query: String,
        searchType: String,
        caseSensitive: Boolean = false,
        maxResults: Int = 50
    ): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val q = if (caseSensitive) query else query.lowercase()

        for ((className, entry) in classes) {
            if (results.size >= maxResults) break
            val cn = if (caseSensitive) className else className.lowercase()

            when (searchType) {
                "class" -> {
                    if (cn.contains(q)) {
                        results.add(SearchResult("class", className, className))
                    }
                }
                "package" -> {
                    val pkg = className.substringBeforeLast('.', "")
                    val p = if (caseSensitive) pkg else pkg.lowercase()
                    if (p.contains(q)) {
                        results.add(SearchResult("package", className, pkg))
                    }
                }
                "method" -> {
                    for (method in entry.classDef.methods) {
                        if (results.size >= maxResults) break
                        val mn = if (caseSensitive) method.name else method.name.lowercase()
                        if (mn.contains(q)) {
                            results.add(SearchResult("method", className, "${className}.${method.name}"))
                        }
                    }
                }
                "field" -> {
                    for (field in entry.classDef.fields) {
                        if (results.size >= maxResults) break
                        val fn = if (caseSensitive) field.name else field.name.lowercase()
                        if (fn.contains(q)) {
                            results.add(SearchResult("field", className, "${className}.${field.name}"))
                        }
                    }
                }
                "string" -> {
                    val smali = getClassSmali(className)
                    val s = if (caseSensitive) smali else smali.lowercase()
                    if (s.contains(q)) {
                        results.add(SearchResult("string", className, "Found in $className"))
                    }
                }
                "code" -> {
                    val smali = getClassSmali(className)
                    val s = if (caseSensitive) smali else smali.lowercase()
                    if (s.contains(q)) {
                        results.add(SearchResult("code", className, "Found in $className"))
                    }
                }
            }
        }
        return results
    }

    /**
     * 获取类的 Smali 代码
     */
    fun getClassSmali(className: String, maxChars: Int = 0, offset: Int = 0): String {
        val normalName = normalizeClassName(className)
        val entry = classes[normalName]
            ?: throw IllegalArgumentException("Class not found: $className")

        if (entry.smaliContent == null) {
            entry.smaliContent = disassembleClass(entry.classDef)
        }

        val content = entry.smaliContent!!
        if (maxChars <= 0 && offset <= 0) return content

        val start = offset.coerceIn(0, content.length)
        val end = if (maxChars <= 0) content.length else (start + maxChars).coerceAtMost(content.length)
        return content.substring(start, end)
    }

    /**
     * 修改类的 Smali 代码
     */
    fun modifyClass(className: String, smaliContent: String) {
        val normalName = normalizeClassName(className)
        val entry = classes[normalName]
            ?: throw IllegalArgumentException("Class not found: $className")

        // 验证 Smali 语法（尝试组装）
        val newClassDef = assembleSmali(smaliContent)
            ?: throw IllegalArgumentException("Invalid Smali code")

        classes[normalName] = ClassEntry(newClassDef, smaliContent, entry.dexFileName)
        modifiedClasses.add(normalName)
    }

    /**
     * 添加新类
     */
    fun addClass(className: String, smaliContent: String) {
        val normalName = normalizeClassName(className)
        if (classes.containsKey(normalName)) {
            throw IllegalArgumentException("Class already exists: $className")
        }

        val classDef = assembleSmali(smaliContent)
            ?: throw IllegalArgumentException("Invalid Smali code")

        val targetDex = dexFileNames.firstOrNull() ?: "classes.dex"
        classes[normalName] = ClassEntry(classDef, smaliContent, targetDex)
        classToFile[normalName] = targetDex
        addedClasses.add(normalName)
    }

    /**
     * 删除类
     */
    fun deleteClass(className: String) {
        val normalName = normalizeClassName(className)
        if (!classes.containsKey(normalName)) {
            throw IllegalArgumentException("Class not found: $className")
        }
        classes.remove(normalName)
        classToFile.remove(normalName)
        deletedClasses.add(normalName)
        modifiedClasses.remove(normalName)
        addedClasses.remove(normalName)
    }

    /**
     * 获取方法 Smali
     */
    fun getMethod(className: String, methodName: String, methodSignature: String? = null): String {
        val smali = getClassSmali(className)
        return extractMethod(smali, methodName, methodSignature)
            ?: throw IllegalArgumentException("Method not found: $methodName in $className")
    }

    /**
     * 修改方法
     */
    fun modifyMethod(
        className: String, methodName: String,
        methodSignature: String? = null, newMethodCode: String
    ) {
        val smali = getClassSmali(className)
        val newSmali = replaceMethod(smali, methodName, methodSignature, newMethodCode)
            ?: throw IllegalArgumentException("Method not found: $methodName in $className")
        modifyClass(className, newSmali)
    }

    /**
     * 列出方法
     */
    fun listMethods(className: String): List<MethodInfo> {
        val normalName = normalizeClassName(className)
        val entry = classes[normalName]
            ?: throw IllegalArgumentException("Class not found: $className")

        return entry.classDef.methods.map { method ->
            MethodInfo(
                name = method.name,
                returnType = method.returnType,
                parameters = method.parameterTypes.map { it.toString() },
                accessFlags = method.accessFlags
            )
        }
    }

    /**
     * 列出字段
     */
    fun listFields(className: String): List<FieldInfo> {
        val normalName = normalizeClassName(className)
        val entry = classes[normalName]
            ?: throw IllegalArgumentException("Class not found: $className")

        return entry.classDef.fields.map { field ->
            FieldInfo(
                name = field.name,
                type = field.type,
                accessFlags = field.accessFlags
            )
        }
    }

    /**
     * 重命名类
     */
    fun renameClass(oldClassName: String, newClassName: String) {
        val oldNormal = normalizeClassName(oldClassName)
        val newNormal = normalizeClassName(newClassName)

        val entry = classes[oldNormal]
            ?: throw IllegalArgumentException("Class not found: $oldClassName")

        // 获取 Smali 并替换类名引用
        val smali = getClassSmali(oldClassName)
        val oldType = "L${oldNormal.replace('.', '/')};"
        val newType = "L${newNormal.replace('.', '/')};"
        val newSmali = smali.replace(oldType, newType)

        // 删除旧类，添加新类
        val dexFileName = entry.dexFileName
        classes.remove(oldNormal)
        classToFile.remove(oldNormal)

        val newClassDef = assembleSmali(newSmali)
            ?: throw IllegalArgumentException("Failed to assemble renamed class")

        classes[newNormal] = ClassEntry(newClassDef, newSmali, dexFileName)
        classToFile[newNormal] = dexFileName
        modifiedClasses.add(newNormal)
        deletedClasses.add(oldNormal)
    }

    /**
     * 查找方法交叉引用
     * 在所有类的 Smali 代码中搜索对指定方法的调用
     */
    fun findMethodXrefs(className: String, methodName: String): List<XrefResult> {
        val normalName = normalizeClassName(className)
        val targetType = "L${normalName.replace('.', '/')};"
        val invokePattern = "$targetType->$methodName("
        val results = mutableListOf<XrefResult>()

        for ((cn, entry) in classes) {
            if (cn == normalName) continue
            val smali = getClassSmali(cn)
            val lines = smali.lines()
            for ((lineNum, line) in lines.withIndex()) {
                if (line.contains(invokePattern)) {
                    results.add(XrefResult(
                        fromClass = cn,
                        lineNumber = lineNum + 1,
                        instruction = line.trim()
                    ))
                }
            }
        }
        return results
    }

    /**
     * 查找字段交叉引用
     * 在所有类的 Smali 代码中搜索对指定字段的访问
     */
    fun findFieldXrefs(className: String, fieldName: String): List<XrefResult> {
        val normalName = normalizeClassName(className)
        val targetType = "L${normalName.replace('.', '/')};"
        val fieldPattern = "$targetType->$fieldName:"
        val results = mutableListOf<XrefResult>()

        for ((cn, entry) in classes) {
            if (cn == normalName) continue
            val smali = getClassSmali(cn)
            val lines = smali.lines()
            for ((lineNum, line) in lines.withIndex()) {
                if (line.contains(fieldPattern)) {
                    results.add(XrefResult(
                        fromClass = cn,
                        lineNumber = lineNum + 1,
                        instruction = line.trim()
                    ))
                }
            }
        }
        return results
    }

    /**
     * Smali 转 Java 伪代码
     * 简单的 Smali -> Java 转换，提供可读性更好的代码视图
     */
    fun smaliToJava(className: String): String {
        val smali = getClassSmali(className)
        return SmaliToJavaConverter.convert(smali)
    }

    /**
     * 列出字符串池
     */
    fun listStrings(filter: String? = null, limit: Int = 100): List<String> {
        val strings = mutableSetOf<String>()
        for (dexFile in dexFiles.values) {
            // dexlib2 没有直接的字符串池 API，从 Smali 中提取
            for (classDef in dexFile.classes) {
                val smali = disassembleClass(classDef)
                val regex = Regex(""""([^"]*?)"""")
                for (match in regex.findAll(smali)) {
                    strings.add(match.groupValues[1])
                }
            }
        }
        var result = strings.toList()
        if (!filter.isNullOrEmpty()) {
            result = result.filter { it.contains(filter, ignoreCase = true) }
        }
        return result.take(limit)
    }

    /**
     * 保存修改到 APK
     */
    fun saveToApk(): String {
        if (modifiedClasses.isEmpty() && addedClasses.isEmpty() && deletedClasses.isEmpty()) {
            return "No changes to save"
        }

        val apkFile = File(apkPath)
        val tempApk = File(apkFile.parent, "${apkFile.nameWithoutExtension}_modified.apk")

        // 按 DEX 文件分组重建
        val classesByDex = mutableMapOf<String, MutableList<ClassDef>>()
        for ((className, entry) in classes) {
            classesByDex.getOrPut(entry.dexFileName) { mutableListOf() }.add(entry.classDef)
        }

        // 重建 DEX 文件
        val newDexFiles = mutableMapOf<String, ByteArray>()
        for ((dexName, classList) in classesByDex) {
            val pool = DexPool(Opcodes.getDefault())
            for (classDef in classList) {
                pool.internClass(classDef)
            }
            val dataStore = MemoryDataStore()
            pool.writeTo(dataStore)
            newDexFiles[dexName] = dataStore.data
        }

        // 复制 APK 并替换 DEX
        val zipIn = ZipFile(apkFile)
        val zipOut = ZipOutputStream(BufferedOutputStream(FileOutputStream(tempApk)))
        try {
            for (entry in zipIn.entries()) {
                if (entry.name in newDexFiles) {
                    // 替换 DEX
                    val newEntry = ZipEntry(entry.name)
                    zipOut.putNextEntry(newEntry)
                    zipOut.write(newDexFiles[entry.name]!!)
                    zipOut.closeEntry()
                } else {
                    // 复制原文件
                    val newEntry = ZipEntry(entry.name)
                    zipOut.putNextEntry(newEntry)
                    zipIn.getInputStream(entry).use { it.copyTo(zipOut) }
                    zipOut.closeEntry()
                }
            }
        } finally {
            zipOut.close()
            zipIn.close()
        }

        modifiedClasses.clear()
        addedClasses.clear()
        deletedClasses.clear()

        return tempApk.absolutePath
    }

    /**
     * 关闭会话释放资源
     */
    fun close() {
        classes.clear()
        dexFiles.clear()
        classToFile.clear()
        modifiedClasses.clear()
        addedClasses.clear()
        deletedClasses.clear()
    }

    // ========== 内部辅助 ==========

    private fun disassembleClass(classDef: ClassDef): String {
        val sw = StringWriter()
        val writer = BaksmaliWriter(sw)
        val options = BaksmaliOptions()
        val classDefImpl = ClassDefinition(options, classDef)
        classDefImpl.writeTo(writer)
        writer.close()
        return sw.toString()
    }

    private fun assembleSmali(smaliContent: String): ClassDef? {
        return try {
            val tempFile = File.createTempFile("jasmine_smali_", ".smali")
            tempFile.deleteOnExit()
            tempFile.writeText(smaliContent)

            val options = SmaliOptions()
            options.apiLevel = 30

            val tempDex = File.createTempFile("jasmine_asm_", ".dex")
            tempDex.deleteOnExit()
            options.outputDexFile = tempDex.absolutePath

            val success = Smali.assemble(options, listOf(tempFile.absolutePath))
            if (!success) {
                tempFile.delete()
                tempDex.delete()
                return null
            }

            val dexFile = DexFileFactory.loadDexFile(tempDex, Opcodes.getDefault())
            val result = dexFile.classes.firstOrNull()

            tempFile.delete()
            tempDex.delete()
            result
        } catch (e: Exception) {
            null
        }
    }

    private fun extractMethod(smali: String, methodName: String, signature: String?): String? {
        val lines = smali.lines()
        var inMethod = false
        var found = false
        val result = StringBuilder()

        for (line in lines) {
            if (line.trimStart().startsWith(".method ") && line.contains(methodName)) {
                if (signature == null || line.contains(signature)) {
                    inMethod = true
                    found = true
                }
            }
            if (inMethod) {
                result.appendLine(line)
                if (line.trimStart().startsWith(".end method")) {
                    break
                }
            }
        }
        return if (found) result.toString().trimEnd() else null
    }

    private fun replaceMethod(
        smali: String, methodName: String,
        signature: String?, newMethodCode: String
    ): String? {
        val lines = smali.lines()
        val result = StringBuilder()
        var inMethod = false
        var replaced = false

        for (line in lines) {
            if (!inMethod && line.trimStart().startsWith(".method ") && line.contains(methodName)) {
                if (signature == null || line.contains(signature)) {
                    inMethod = true
                    replaced = true
                    result.appendLine(newMethodCode)
                    continue
                }
            }
            if (inMethod) {
                if (line.trimStart().startsWith(".end method")) {
                    inMethod = false
                }
                continue
            }
            result.appendLine(line)
        }
        return if (replaced) result.toString().trimEnd() else null
    }

    companion object {
        fun normalizeClassName(name: String): String {
            // Lcom/example/Foo; -> com.example.Foo
            var n = name
            if (n.startsWith("L") && n.endsWith(";")) {
                n = n.substring(1, n.length - 1)
            }
            return n.replace('/', '.')
        }
    }
}

data class ClassListResult(
    val classes: List<String>,
    val total: Int,
    val offset: Int,
    val limit: Int
)

data class SearchResult(
    val type: String,
    val className: String,
    val match: String
)

data class MethodInfo(
    val name: String,
    val returnType: String,
    val parameters: List<String>,
    val accessFlags: Int
)

data class FieldInfo(
    val name: String,
    val type: String,
    val accessFlags: Int
)

data class XrefResult(
    val fromClass: String,
    val lineNumber: Int,
    val instruction: String
)

/**
 * 简单的 Smali -> Java 伪代码转换器
 * 移植自 AetherLink 的 smaliToJava 功能
 */
object SmaliToJavaConverter {
    fun convert(smali: String): String {
        val sb = StringBuilder()
        val lines = smali.lines()
        var inMethod = false
        var methodIndent = ""

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith(".class ") -> {
                    val parts = trimmed.removePrefix(".class ").trim()
                    val accessAndName = parts.replace("L", "").replace(";", "").replace("/", ".")
                    sb.appendLine("// Decompiled from Smali")
                    sb.appendLine("$accessAndName {")
                    sb.appendLine()
                }
                trimmed.startsWith(".super ") -> {
                    val superClass = trimmed.removePrefix(".super ").trim()
                        .removePrefix("L").removeSuffix(";").replace("/", ".")
                    sb.appendLine("    // extends $superClass")
                }
                trimmed.startsWith(".implements ") -> {
                    val iface = trimmed.removePrefix(".implements ").trim()
                        .removePrefix("L").removeSuffix(";").replace("/", ".")
                    sb.appendLine("    // implements $iface")
                }
                trimmed.startsWith(".field ") -> {
                    val fieldDecl = trimmed.removePrefix(".field ").trim()
                    sb.appendLine("    $fieldDecl;")
                }
                trimmed.startsWith(".method ") -> {
                    inMethod = true
                    val methodDecl = trimmed.removePrefix(".method ").trim()
                    sb.appendLine()
                    sb.appendLine("    $methodDecl {")
                    methodIndent = "        "
                }
                trimmed.startsWith(".end method") -> {
                    inMethod = false
                    sb.appendLine("    }")
                }
                inMethod -> {
                    when {
                        trimmed.startsWith("invoke-") -> {
                            val call = trimmed.substringAfter("}, ")
                                .replace("L", "").replace(";", "").replace("/", ".")
                            sb.appendLine("$methodIndent// call: $call")
                        }
                        trimmed.startsWith("const-string") -> {
                            val str = trimmed.substringAfter(", \"").removeSuffix("\"")
                            val reg = trimmed.substringAfter(" ").substringBefore(",")
                            sb.appendLine("$methodIndent$reg = \"$str\";")
                        }
                        trimmed.startsWith("return") -> {
                            sb.appendLine("$methodIndent$trimmed;")
                        }
                        trimmed.startsWith("new-instance") -> {
                            val type = trimmed.substringAfter(", ")
                                .removePrefix("L").removeSuffix(";").replace("/", ".")
                            val reg = trimmed.substringAfter(" ").substringBefore(",")
                            sb.appendLine("$methodIndent$reg = new $type();")
                        }
                        trimmed.startsWith("iget") || trimmed.startsWith("iput") ||
                        trimmed.startsWith("sget") || trimmed.startsWith("sput") -> {
                            sb.appendLine("$methodIndent// $trimmed")
                        }
                        trimmed.startsWith("if-") || trimmed.startsWith("goto") -> {
                            sb.appendLine("$methodIndent// $trimmed")
                        }
                        trimmed.startsWith(":") -> {
                            sb.appendLine("$methodIndent$trimmed:")
                        }
                        trimmed.startsWith(".") -> {
                            // skip directives
                        }
                        trimmed.isNotEmpty() -> {
                            sb.appendLine("$methodIndent// $trimmed")
                        }
                    }
                }
            }
        }
        sb.appendLine("}")
        return sb.toString()
    }
}
