package com.lhzkml.jasmine.proot

import android.os.Bundle
import android.widget.Toast
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.proot.PRootEnvironment
import com.lhzkml.jasmine.ui.components.*
import com.lhzkml.jasmine.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PRootManagementActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JasmineTheme {
                PRootManagementScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun PRootManagementScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val activity = context as? ComponentActivity
    val prootEnv = remember {
        PRootEnvironment(
            context.filesDir,
            context.cacheDir,
            activity?.getExternalFilesDir(null),
            File(context.applicationInfo.nativeLibraryDir)
        )
    }

    var isInstalled by remember { mutableStateOf(prootEnv.isInstalled) }
    var installing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var statusMessage by remember { mutableStateOf("") }

    var alpineVersion by remember { mutableStateOf("") }
    var prootVersion by remember { mutableStateOf("") }
    var diskUsage by remember { mutableStateOf("") }
    var installedPackages by remember { mutableStateOf<List<String>>(emptyList()) }
    var packagesLoading by remember { mutableStateOf(false) }

    var packageToInstall by remember { mutableStateOf("") }
    var packageInstalling by remember { mutableStateOf(false) }

    var showUninstallConfirm by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    var logContent by remember { mutableStateOf("") }

    fun refreshInfo() {
        isInstalled = prootEnv.isInstalled
        if (isInstalled) {
            scope.launch {
                try {
                    alpineVersion = withContext(Dispatchers.IO) {
                        try { prootEnv.getAlpineVersion() } catch (_: Exception) { "获取失败" }
                    }
                    prootVersion = withContext(Dispatchers.IO) {
                        try { prootEnv.getPRootVersion() } catch (_: Exception) { "获取失败" }
                    }
                    diskUsage = withContext(Dispatchers.IO) {
                        try { prootEnv.formatDiskUsage() } catch (_: Exception) { "计算失败" }
                    }
                } catch (_: Exception) { /* prevent crash */ }
            }
        }
    }

    fun loadPackages() {
        if (!isInstalled) return
        packagesLoading = true
        scope.launch {
            try {
                val pkgs = withContext(Dispatchers.IO) {
                    try { prootEnv.listInstalledPackages() } catch (_: Exception) { emptyList() }
                }
                installedPackages = pkgs
            } catch (_: Exception) { /* prevent crash */ }
            packagesLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshInfo()
        if (isInstalled) loadPackages()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(Color.White)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CustomTextButton(onClick = onBack, contentColor = TextPrimary, contentPadding = PaddingValues(6.dp)) {
                CustomText("<- 返回", fontSize = 14.sp, color = TextPrimary)
            }
            CustomText(
                text = "Linux 环境",
                fontSize = 17.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(56.dp))
        }
        CustomHorizontalDivider(color = Color(0xFFE8E8E8), thickness = 1.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Environment status card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    CustomText(
                        text = "Alpine Linux + PRoot",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isInstalled) StatusConnected.copy(alpha = 0.15f)
                                else TextSecondary.copy(alpha = 0.15f)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        CustomText(
                            text = if (installing) "安装中" else if (isInstalled) "已安装" else "未安装",
                            fontSize = 12.sp,
                            color = if (isInstalled) StatusConnected else TextSecondary
                        )
                    }
                }

                CustomText(
                    text = "在 Android 上运行完整 Alpine Linux 环境（无需 root）。" +
                            "支持 apk 包管理，可安装 Python、GCC、Git、Node.js 等工具。" +
                            "Agent 的 Shell 工具设置 usePRoot=true 即可使用。",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (installing) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Accent,
                        trackColor = Color(0xFFE8E8E8)
                    )
                    CustomText(
                        text = statusMessage,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                if (isInstalled) {
                    CustomHorizontalDivider(
                        color = Color(0xFFE8E8E8),
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    InfoRow("Alpine 版本", alpineVersion.ifEmpty { "加载中..." })
                    InfoRow("PRoot 版本", prootVersion.ifEmpty { "加载中..." })
                    InfoRow("磁盘占用", diskUsage.ifEmpty { "计算中..." })
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!isInstalled && !installing && statusMessage.isNotEmpty()) {
                    CustomText(
                        text = statusMessage,
                        fontSize = 12.sp,
                        color = ErrorColor,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    val logFile = prootEnv.getLatestLogFile()
                    if (logFile != null && logFile.exists()) {
                        CustomText(
                            text = "日志: ${logFile.absolutePath}",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        CustomTextButton(
                            onClick = {
                                scope.launch {
                                    logContent = withContext(Dispatchers.IO) {
                                        try { logFile.readText() } catch (_: Exception) { "无法读取日志" }
                                    }
                                    showLog = true
                                }
                            },
                            contentColor = Accent,
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
                        ) {
                            CustomText("查看安装日志", fontSize = 12.sp, color = Accent)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (!isInstalled && !installing) {
                    CustomButton(
                        onClick = {
                            installing = true
                            statusMessage = ""
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        prootEnv.install { p, msg ->
                                            progress = p
                                            statusMessage = msg
                                        }
                                    }
                                    installing = false
                                    isInstalled = prootEnv.isInstalled
                                    if (isInstalled) {
                                        refreshInfo()
                                        loadPackages()
                                        Toast.makeText(context, "Alpine Linux 安装完成", Toast.LENGTH_SHORT).show()
                                    } else {
                                        statusMessage = "安装异常：文件校验未通过，请卸载后重试"
                                        Toast.makeText(context, "安装异常，请卸载后重试", Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    installing = false
                                    isInstalled = false
                                    statusMessage = "安装失败: ${e.message}"
                                    Toast.makeText(context, "安装失败: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        colors = CustomButtonDefaults.buttonColors(
                            containerColor = Accent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        CustomText("安装 Alpine Linux", fontSize = 14.sp, color = Color.White)
                    }
                    CustomText(
                        text = "需要下载约 5MB（PRoot ~1.5MB + Alpine rootfs ~4MB）",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (isInstalled && !installing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CustomButton(
                            onClick = {
                                scope.launch {
                                    logContent = withContext(Dispatchers.IO) {
                                        try {
                                            val logs = buildString {
                                                val logDir = prootEnv.paths.logDir
                                                if (logDir.exists()) {
                                                    logDir.listFiles()
                                                        ?.sortedByDescending { it.lastModified() }
                                                        ?.take(3)
                                                        ?.forEach { f ->
                                                            appendLine("=== ${f.name} ===")
                                                            appendLine(f.readText().takeLast(10000))
                                                            appendLine()
                                                        }
                                                }
                                            }
                                            logs.ifEmpty { "暂无日志" }
                                        } catch (_: Exception) { "无法读取日志" }
                                    }
                                    showLog = true
                                }
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            colors = CustomButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF0F0F0),
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            CustomText("查看日志", fontSize = 14.sp, color = TextPrimary)
                        }
                        CustomButton(
                            onClick = { showUninstallConfirm = true },
                            modifier = Modifier.weight(1f).height(44.dp),
                            colors = CustomButtonDefaults.buttonColors(
                                containerColor = ErrorColor.copy(alpha = 0.1f),
                                contentColor = ErrorColor
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            CustomText("卸载环境", fontSize = 14.sp, color = ErrorColor)
                        }
                    }
                    CustomText(
                        text = "日志目录: ${prootEnv.paths.logDir.absolutePath}",
                        fontSize = 10.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            if (showUninstallConfirm) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ErrorColor.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                        .border(1.dp, ErrorColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    CustomText(
                        "确定卸载 Alpine Linux 环境？所有已安装的包和数据将被删除。",
                        fontSize = 13.sp,
                        color = TextPrimary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CustomButton(
                            onClick = { showUninstallConfirm = false },
                            modifier = Modifier.weight(1f).height(40.dp),
                            colors = CustomButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF0F0F0),
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            CustomText("取消", fontSize = 13.sp, color = TextPrimary)
                        }
                        CustomButton(
                            onClick = {
                                showUninstallConfirm = false
                                scope.launch {
                                    prootEnv.uninstall()
                                    isInstalled = false
                                    installedPackages = emptyList()
                                    alpineVersion = ""
                                    prootVersion = ""
                                    diskUsage = ""
                                    Toast.makeText(context, "已卸载", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f).height(40.dp),
                            colors = CustomButtonDefaults.buttonColors(
                                containerColor = ErrorColor,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            CustomText("确定卸载", fontSize = 13.sp, color = Color.White)
                        }
                    }
                }
            }

            // Log viewer
            if (showLog && logContent.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8F8F8), RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CustomText(
                            text = "安装日志",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        CustomTextButton(
                            onClick = { showLog = false },
                            contentColor = TextSecondary,
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            CustomText("收起", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E1E1E))
                            .padding(8.dp)
                    ) {
                        val hScroll = rememberScrollState()
                        val vScroll = rememberScrollState()
                        Text(
                            text = logContent,
                            fontSize = 11.sp,
                            color = Color(0xFFD4D4D4),
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .verticalScroll(vScroll)
                                .horizontalScroll(hScroll)
                        )
                    }
                }
            }

            // Package management
            if (isInstalled) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    CustomText(
                        text = "包管理",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    CustomText(
                        text = "输入包名安装 Alpine 软件包（如 python3、gcc、git、nodejs、curl）",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(BgInput)
                                .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            BasicTextField(
                                value = packageToInstall,
                                onValueChange = { packageToInstall = it },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 14.sp, color = TextPrimary),
                                cursorBrush = SolidColor(TextPrimary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { inner ->
                                    if (packageToInstall.isEmpty()) {
                                        CustomText("包名，如 python3", fontSize = 14.sp, color = TextSecondary)
                                    }
                                    inner()
                                }
                            )
                        }
                        CustomButton(
                            onClick = {
                                if (packageToInstall.isBlank()) return@CustomButton
                                val pkg = packageToInstall.trim()
                                focusManager.clearFocus()
                                packageInstalling = true
                                scope.launch {
                                    try {
                                        val result = withContext(Dispatchers.IO) {
                                            prootEnv.installPackage(pkg)
                                        }
                                        packageInstalling = false
                                        if (result.exitCode == 0) {
                                            packageToInstall = ""
                                            Toast.makeText(context, "$pkg 安装成功", Toast.LENGTH_SHORT).show()
                                            loadPackages()
                                            refreshInfo()
                                        } else {
                                            Toast.makeText(context, "安装失败: ${result.output.take(200)}", Toast.LENGTH_LONG).show()
                                        }
                                    } catch (e: Exception) {
                                        packageInstalling = false
                                        Toast.makeText(context, "安装异常: ${e.message?.take(100)}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = !packageInstalling && packageToInstall.isNotBlank(),
                            modifier = Modifier.height(44.dp),
                            colors = CustomButtonDefaults.buttonColors(
                                containerColor = Accent,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            CustomText(
                                if (packageInstalling) "安装中..." else "安装",
                                fontSize = 13.sp,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CustomTextButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        val result = withContext(Dispatchers.IO) { prootEnv.updateIndex() }
                                        if (result.exitCode == 0) {
                                            Toast.makeText(context, "索引更新成功", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "更新失败: ${result.output.take(150)}", Toast.LENGTH_LONG).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "更新异常: ${e.message?.take(100)}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            contentColor = Accent,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            CustomText("apk update", fontSize = 12.sp)
                        }
                        CustomTextButton(
                            onClick = { loadPackages() },
                            contentColor = Accent,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            CustomText("刷新列表", fontSize = 12.sp)
                        }
                    }

                    CustomHorizontalDivider(
                        color = Color(0xFFE8E8E8),
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    CustomText(
                        text = if (packagesLoading) "加载中..." else "已安装 ${installedPackages.size} 个包",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    if (installedPackages.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            items(installedPackages, key = { it }) { pkg ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CustomText(
                                        text = pkg,
                                        fontSize = 12.sp,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CustomText(
            text = label,
            fontSize = 13.sp,
            color = TextSecondary,
            modifier = Modifier.width(80.dp)
        )
        CustomText(
            text = value,
            fontSize = 13.sp,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
