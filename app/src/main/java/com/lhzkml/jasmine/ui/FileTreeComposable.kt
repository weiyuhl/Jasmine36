package com.lhzkml.jasmine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.config.FormatUtils
import com.lhzkml.jasmine.ui.components.CustomText
import com.lhzkml.jasmine.ui.theme.Accent
import com.lhzkml.jasmine.ui.theme.BgInput
import com.lhzkml.jasmine.ui.theme.TextPrimary
import com.lhzkml.jasmine.ui.theme.TextSecondary
import java.io.File

/**
 * 文件树 Composable — 类似 VSCode 的资源管理器
 * 支持展开/折叠文件夹，缩进显示层级。
 */
data class FileNode(
    val file: File,
    val depth: Int,
    val isDirectory: Boolean,
    var expanded: Boolean = false
)

@Composable
fun FileTreeComposable(
    rootPath: String,
    onFileClick: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleNodes = remember { mutableStateListOf<FileNode>() }

    LaunchedEffect(rootPath) {
        visibleNodes.clear()
        if (rootPath.isEmpty()) return@LaunchedEffect
        val root = File(rootPath)
        if (root.exists() && root.isDirectory) {
            visibleNodes.addAll(loadChildren(root, 0))
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .background(BgInput)
            .padding(bottom = 16.dp)
    ) {
        itemsIndexed(
            items = visibleNodes,
            key = { _, node -> "${node.file.absolutePath}_${node.depth}" }
        ) { index, node ->
            FileTreeNode(
                node = node,
                onToggle = {
                    if (node.isDirectory) {
                        toggleExpand(visibleNodes, index, node)
                    }
                },
                onFileClick = {
                    if (node.isDirectory) {
                        toggleExpand(visibleNodes, index, node)
                    } else {
                        onFileClick(node.file)
                    }
                }
            )
        }
    }
}

@Composable
private fun FileTreeNode(
    node: FileNode,
    onToggle: () -> Unit,
    onFileClick: () -> Unit
) {
    val indent = node.depth * 16
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onFileClick() }
            .padding(start = (indent + 8).dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Box(modifier = Modifier.width(16.dp)) {
            CustomText(
                text = if (node.isDirectory) (if (node.expanded) "v" else ">") else "",
                color = TextSecondary,
                fontSize = 10.sp
            )
        }
        Box(modifier = Modifier.width(20.dp)) {
            CustomText(
                text = if (node.isDirectory) {
                    if (node.expanded) "[-]" else "[+]"
                } else {
                    FormatUtils.getFileIcon(node.file.name)
                },
                color = TextPrimary,
                fontSize = 13.sp
            )
        }
        CustomText(
            text = node.file.name,
            color = TextPrimary,
            fontSize = 13.sp,
            maxLines = 1,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

private fun loadChildren(dir: File, depth: Int): List<FileNode> {
    val children = dir.listFiles()
        ?.filter { !it.name.startsWith(".") }
        ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
        ?: emptyList()

    return children.map { file ->
        FileNode(
            file = file,
            depth = depth,
            isDirectory = file.isDirectory,
            expanded = false
        )
    }
}

private fun toggleExpand(visibleNodes: MutableList<FileNode>, position: Int, node: FileNode) {
    if (!node.isDirectory) return

    if (node.expanded) {
        node.expanded = false
        val removeStart = position + 1
        var removeCount = 0
        while (removeStart + removeCount < visibleNodes.size
            && visibleNodes[removeStart + removeCount].depth > node.depth) {
            removeCount++
        }
        if (removeCount > 0) {
            repeat(removeCount) { visibleNodes.removeAt(removeStart) }
        }
    } else {
        node.expanded = true
        val children = loadChildren(node.file, node.depth + 1)
        if (children.isNotEmpty()) {
            visibleNodes.addAll(position + 1, children)
        }
    }
}
