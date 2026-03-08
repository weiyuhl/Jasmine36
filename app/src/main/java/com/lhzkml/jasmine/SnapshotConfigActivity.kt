package com.lhzkml.jasmine

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.lhzkml.jasmine.core.agent.observe.snapshot.RollbackStrategy
import com.lhzkml.jasmine.core.config.SnapshotStorageType
import com.lhzkml.jasmine.ui.theme.*
import com.lhzkml.jasmine.ui.components.*

class SnapshotConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JasmineTheme {
                SnapshotConfigScreen(
                    onBack = { finish() },
                    onViewCheckpoints = {
                        val config = AppConfig.configRepo()
                        if (config.getSnapshotStorage() != SnapshotStorageType.FILE) {
                            Toast.makeText(this, "内存存储模式下无法查看检查点，请切换到文件存储", Toast.LENGTH_SHORT).show()
                        } else {
                            startActivity(Intent(this, CheckpointManagerActivity::class.java))
                        }
                    },
                    onClearCheckpoints = { /* 由 SnapshotConfigScreen 内部处理 */ },
                    onPerformClear = { afterClear ->
                        val snapshotDir = getExternalFilesDir("snapshots")
                        if (snapshotDir != null && snapshotDir.exists()) {
                            snapshotDir.deleteRecursively()
                            snapshotDir.mkdirs()
                        }
                        Toast.makeText(this, "已清除", Toast.LENGTH_SHORT).show()
                        afterClear()
                    },
                    getCheckpointCount = { getCheckpointCountText() }
                )
            }
        }
    }

    private fun getCheckpointCountText(): String {
        val config = AppConfig.configRepo()
        return if (config.getSnapshotStorage() == SnapshotStorageType.FILE) {
            val snapshotDir = getExternalFilesDir("snapshots")
            if (snapshotDir != null && snapshotDir.exists()) {
                val agentDirs = snapshotDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
                var totalCheckpoints = 0
                for (dir in agentDirs) {
                    totalCheckpoints += dir.listFiles()?.count { it.extension == "json" } ?: 0
                }
                "文件存储: ${agentDirs.size} 个会话, $totalCheckpoints 个检查点"
            } else {
                "文件存储: 暂无检查点"
            }
        } else {
            "内存存储: 检查点仅在运行时有效"
        }
    }

}

@Composable
fun SnapshotConfigScreen(
    onBack: () -> Unit,
    onViewCheckpoints: () -> Unit,
    onClearCheckpoints: () -> Unit,
    onPerformClear: ((() -> Unit) -> Unit) = { it() },
    getCheckpointCount: () -> String
) {
    val config = AppConfig.configRepo()
    
    var enabled by remember { mutableStateOf(config.isSnapshotEnabled()) }
    var showMemoryInfoDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var storageType by remember { mutableStateOf(config.getSnapshotStorage()) }
    var autoCheckpoint by remember { mutableStateOf(config.isSnapshotAutoCheckpoint()) }
    var rollbackStrategy by remember { mutableStateOf(config.getSnapshotRollbackStrategy()) }
    var checkpointCount by remember { mutableStateOf(getCheckpointCount()) }

    // 监听 onResume 事件刷新检查点计数
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkpointCount = getCheckpointCount()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            config.setSnapshotEnabled(enabled)
            config.setSnapshotStorage(storageType)
            config.setSnapshotAutoCheckpoint(autoCheckpoint)
            config.setSnapshotRollbackStrategy(rollbackStrategy)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
    ) {
        // 顶部栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(Color.White)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CustomTextButton(
                onClick = onBack,
                contentColor = TextPrimary
            ) {
                CustomText("<- 返回", fontSize = 14.sp, color = TextPrimary)
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            CustomText(
                text = "快照配置",
                fontSize = 17.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.weight(1f))
        }

        CustomHorizontalDivider(color = Color(0xFFE8E8E8), thickness = 1.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // 启用开关
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    CustomText(
                        text = "启用执行快照",
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                    CustomText(
                        text = "Agent 执行过程中自动创建检查点",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                
                CustomSwitch(
                    checked = enabled,
                    onCheckedChange = { 
                        enabled = it
                        config.setSnapshotEnabled(it)
                    },
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Accent,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFFE0E0E0)
                )
            }

            if (enabled) {
                Spacer(modifier = Modifier.height(16.dp))

                // 存储方式标题
                CustomText(
                    text = "存储方式",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // 内存存储
                StorageCard(
                    title = "内存存储",
                    description = "应用关闭后丢失，速度快",
                    isSelected = storageType == SnapshotStorageType.MEMORY,
                    onClick = { 
                        storageType = SnapshotStorageType.MEMORY
                        config.setSnapshotStorage(SnapshotStorageType.MEMORY)
                        checkpointCount = getCheckpointCount()
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 文件存储
                StorageCard(
                    title = "文件存储",
                    description = "持久化到本地，可跨会话恢复",
                    isSelected = storageType == SnapshotStorageType.FILE,
                    onClick = { 
                        storageType = SnapshotStorageType.FILE
                        config.setSnapshotStorage(SnapshotStorageType.FILE)
                        checkpointCount = getCheckpointCount()
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
                CustomHorizontalDivider(color = Color(0xFFE8E8E8), thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // 自动检查点
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        CustomText(
                            text = "自动检查点",
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        CustomText(
                            text = "每个节点执行后自动创建检查点",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    
                    CustomSwitch(
                        checked = autoCheckpoint,
                        onCheckedChange = { 
                            autoCheckpoint = it
                            config.setSnapshotAutoCheckpoint(it)
                        },
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Accent,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFE0E0E0)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                CustomHorizontalDivider(color = Color(0xFFE8E8E8), thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // 回滚策略标题
                CustomText(
                    text = "回滚策略",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // 从节点重启
                RollbackStrategyCard(
                    title = "从节点重启",
                    description = "回滚到检查点并重新执行该节点",
                    isSelected = rollbackStrategy == RollbackStrategy.RESTART_FROM_NODE,
                    onClick = { 
                        rollbackStrategy = RollbackStrategy.RESTART_FROM_NODE
                        config.setSnapshotRollbackStrategy(RollbackStrategy.RESTART_FROM_NODE)
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 跳过节点
                RollbackStrategyCard(
                    title = "跳过节点",
                    description = "回滚到检查点并跳过该节点",
                    isSelected = rollbackStrategy == RollbackStrategy.SKIP_NODE,
                    onClick = { 
                        rollbackStrategy = RollbackStrategy.SKIP_NODE
                        config.setSnapshotRollbackStrategy(RollbackStrategy.SKIP_NODE)
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 使用默认输出
                RollbackStrategyCard(
                    title = "使用默认输出",
                    description = "回滚到检查点并使用默认输出",
                    isSelected = rollbackStrategy == RollbackStrategy.USE_DEFAULT_OUTPUT,
                    onClick = { 
                        rollbackStrategy = RollbackStrategy.USE_DEFAULT_OUTPUT
                        config.setSnapshotRollbackStrategy(RollbackStrategy.USE_DEFAULT_OUTPUT)
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
                CustomHorizontalDivider(color = Color(0xFFE8E8E8), thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // 检查点管理标题
                CustomText(
                    text = "检查点管理",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                CustomText(
                    text = checkpointCount,
                    fontSize = 14.sp,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Accent, RoundedCornerShape(8.dp))
                            .background(Accent.copy(alpha = 0.06f))
                            .clickable { onViewCheckpoints() }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CustomText("查看检查点", fontSize = 13.sp, color = Accent)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFE53935), RoundedCornerShape(8.dp))
                            .background(Color(0xFFE53935).copy(alpha = 0.06f))
                            .clickable {
                                if (config.getSnapshotStorage() != SnapshotStorageType.FILE) {
                                    showMemoryInfoDialog = true
                                } else {
                                    showClearConfirmDialog = true
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CustomText("清除全部", fontSize = 13.sp, color = Color(0xFFE53935))
                    }
                }
            }
        }

        if (showMemoryInfoDialog) {
            CustomAlertDialog(
                onDismissRequest = { showMemoryInfoDialog = false },
                containerColor = Color.White,
                title = { CustomText("提示", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                text = { CustomText("内存存储模式下，检查点会在 APP 关闭时自动清除。", color = TextPrimary, fontSize = 14.sp) },
                confirmButton = {
                    CustomTextButton(
                        onClick = { showMemoryInfoDialog = false },
                        colors = CustomButtonDefaults.textButtonColors(contentColor = Accent)
                    ) { CustomText("确定", fontSize = 14.sp) }
                },
                dismissButton = null
            )
        }

        if (showClearConfirmDialog) {
            CustomAlertDialog(
                onDismissRequest = { showClearConfirmDialog = false },
                containerColor = Color.White,
                title = { CustomText("清除检查点", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                text = { CustomText("确定要删除所有已保存的检查点吗？此操作不可撤销。", color = TextPrimary, fontSize = 14.sp) },
                confirmButton = {
                    CustomTextButton(
                        onClick = {
                            showClearConfirmDialog = false
                            onPerformClear { checkpointCount = getCheckpointCount() }
                        },
                        colors = CustomButtonDefaults.textButtonColors(contentColor = Color(0xFFE53935))
                    ) { CustomText("删除", fontSize = 14.sp) }
                },
                dismissButton = {
                    CustomTextButton(
                        onClick = { showClearConfirmDialog = false },
                        colors = CustomButtonDefaults.textButtonColors(contentColor = TextSecondary)
                    ) { CustomText("取消", fontSize = 14.sp) }
                }
            )
        }
    }
}

@Composable
fun StorageCard(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) Color(0xFFF5F5F5) else Color.White
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Accent else Color(0xFFE8E8E8),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            CustomText(
                text = title,
                fontSize = 15.sp,
                color = TextPrimary
            )
            CustomText(
                text = description,
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        
        if (isSelected) {
            CustomText(
                text = "✓",
                fontSize = 16.sp,
                color = Accent
            )
        }
    }
}

@Composable
fun RollbackStrategyCard(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) Color(0xFFF5F5F5) else Color.White
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Accent else Color(0xFFE8E8E8),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            CustomText(
                text = title,
                fontSize = 15.sp,
                color = TextPrimary
            )
            CustomText(
                text = description,
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        
        if (isSelected) {
            CustomText(
                text = "✓",
                fontSize = 16.sp,
                color = Accent
            )
        }
    }
}
