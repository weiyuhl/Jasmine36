package com.lhzkml.jasmine.proot

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
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

private const val TAG = "PRootMgmt"

private fun logToFile(context: Context, level: String, msg: String, e: Throwable? = null) {
    try {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "proot/logs")
        dir.mkdirs()
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val file = File(dir, "proot_mgmt_debug_${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.log")
        val line = "[$ts] [$level] $msg" + (e?.let { "\n${it.stackTraceToString()}" } ?: "") + "\n"
        file.appendText(line)
    } catch (_: Exception) {}
}

private fun logDebug(context: Context, level: String, msg: String, e: Throwable? = null) {
    when (level) {
        "D" -> Log.d(TAG, msg)
        "W" -> Log.w(TAG, msg)
        "E" -> if (e != null) Log.e(TAG, msg, e) else Log.e(TAG, msg)
    }
    logToFile(context, level, msg, e)
}

class PRootManagementActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logDebug(this, "D", "onCreate")
        val appCtx = applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            logDebug(appCtx, "E", "UncaughtException thread=${t.name}", e)
            defaultHandler?.uncaughtException(t, e)
        }
        setContent {
            JasmineTheme {
                PRootManagementScreen(onBack = { finish() })
            }
        }
    }

    override fun onDestroy() {
        logDebug(this, "D", "onDestroy isFinishing=$isFinishing")
        super.onDestroy()
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

    var apkUpdating by remember { mutableStateOf(false) }
    var apkUpdateOutput by remember { mutableStateOf("") }

    var commandInput by remember { mutableStateOf("") }
    var commandRunning by remember { mutableStateOf(false) }
    var commandOutput by remember { mutableStateOf("") }

    var packageInstallOutput by remember { mutableStateOf("") }
    var installingToolApk by remember { mutableStateOf<String?>(null) }
    var uninstallingToolApk by remember { mutableStateOf<String?>(null) }
    var uninstallingAllTools by remember { mutableStateOf(false) }

    val commonTools = remember {
        listOf(
            "git" to "Git",
            "nodejs" to "Node.js",
            "python3" to "Python",
            "gcc" to "GCC",
            "make" to "Make",
            "curl" to "curl",
            "openssh" to "OpenSSH"
        )
    }

    fun getToolVersion(apkName: String): String? {
        val line = installedPackages.firstOrNull { it.startsWith("$apkName-") } ?: return null
        return line.removePrefix("$apkName-").substringBefore(" ").take(20)
    }

    fun isToolInstalled(apkName: String): Boolean =
        installedPackages.any { it.startsWith("$apkName-") }

    var showUninstallConfirm by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    var logContent by remember { mutableStateOf("") }

    fun refreshInfo() {
        logDebug(context, "D", "refreshInfo start isInstalled=$isInstalled")
        isInstalled = prootEnv.isInstalled
        if (isInstalled) {
            scope.launch {
                try {
                    alpineVersion = withContext(Dispatchers.IO) {
                        try { prootEnv.getAlpineVersion() } catch (e: Exception) {
                            logDebug(context, "E", "refreshInfo getAlpineVersion", e)
                            "获取失败"
                        }
                    }
                    prootVersion = withContext(Dispatchers.IO) {
                        try { prootEnv.getPRootVersion() } catch (e: Exception) {
                            logDebug(context, "E", "refreshInfo getPRootVersion", e)
                            "获取失败"
                        }
                    }
                    diskUsage = withContext(Dispatchers.IO) {
                        try { prootEnv.formatDiskUsage() } catch (e: Exception) {
                            logDebug(context, "E", "refreshInfo formatDiskUsage", e)
                            "计算失败"
                        }
                    }
                    logDebug(context, "D", "refreshInfo done alpine=$alpineVersion")
                } catch (e: Exception) {
                    logDebug(context, "E", "refreshInfo crash", e)
                }
            }
        }
    }

    fun loadPackages() {
        if (!isInstalled) return
        logDebug(context, "D", "loadPackages start")
        packagesLoading = true
        scope.launch {
            try {
                val pkgs = withContext(Dispatchers.IO) {
                    try { prootEnv.listInstalledPackages() } catch (e: Exception) {
                        logDebug(context, "E", "loadPackages listInstalledPackages", e)
                        emptyList()
                    }
                }
                installedPackages = pkgs
                logDebug(context, "D", "loadPackages done count=${pkgs.size}")
            } catch (e: Exception) {
                logDebug(context, "E", "loadPackages crash", e)
            }
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
                                    val act = context as? ComponentActivity
                                    withContext(Dispatchers.IO) {
                                        prootEnv.install { p, msg ->
                                            if (act?.isDestroyed != true) {
                                                act?.runOnUiThread {
                                                    if (!act.isDestroyed) {
                                                        progress = p
                                                        statusMessage = msg
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    installing = false
                                    isInstalled = prootEnv.isInstalled
                                    logDebug(context, "D", "install DONE isInstalled=$isInstalled actDestroyed=${act?.isDestroyed}")
                                    if (isInstalled) {
                                        logDebug(context, "D", "install SUCCESS")
                                        Toast.makeText(context, "Alpine Linux 安装完成", Toast.LENGTH_SHORT).show()
                                        scope.launch {
                                            kotlinx.coroutines.delay(500)
                                            logDebug(context, "D", "install delay done actDestroyed=${act?.isDestroyed}")
                                            if (act?.isDestroyed != true) {
                                                try {
                                                    refreshInfo()
                                                    loadPackages()
                                                    logDebug(context, "D", "install post-delay refreshInfo+loadPackages OK")
                                                } catch (e: Exception) {
                                                    logDebug(context, "E", "install post-delay refreshInfo/loadPackages FAIL", e)
                                                }
                                            } else {
                                                logDebug(context, "W", "install post-delay SKIP Activity already destroyed")
                                            }
                                        }
                                    } else {
                                        statusMessage = "安装异常：文件校验未通过，请卸载后重试"
                                        Toast.makeText(context, "安装异常，请卸载后重试", Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    logDebug(context, "E", "install FAILED", e)
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
                        text = "日志: ${prootEnv.paths.logDir.absolutePath}",
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
                        "确定要卸载 Alpine Linux 环境吗？所有数据将丢失。",
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
                            CustomText("确认卸载", fontSize = 13.sp, color = Color.White)
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
                            text = "运行日志",
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
                            CustomText("关闭", fontSize = 12.sp, color = TextSecondary)
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
                        text = "软件包管理",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    CustomText(
                        text = "常用工具",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    val installedTools = commonTools.filter { isToolInstalled(it.first) }
                    if (installedTools.isNotEmpty() && !uninstallingAllTools) {
                        CustomTextButton(
                            onClick = {
                                uninstallingAllTools = true
                                packageInstallOutput = "正在卸载全部...\n"
                                scope.launch {
                                    var successCount = 0
                                    for ((apkName, displayName) in installedTools) {
                                        try {
                                            val result = withContext(Dispatchers.IO) {
                                                prootEnv.removePackage(apkName)
                                            }
                                            packageInstallOutput += "✓ $displayName: ${if (result.exitCode == 0) "成功" else "失败"}\n"
                                            if (result.exitCode == 0) successCount++
                                        } catch (e: Exception) {
                                            packageInstallOutput += "✗ $displayName 失败: ${e.message}\n"
                                        }
                                    }
                                    uninstallingAllTools = false
                                    packageInstallOutput += "\n✓ 完成 $successCount/${installedTools.size} 个成功"
                                    loadPackages()
                                    refreshInfo()
                                    Toast.makeText(context, "已卸载 $successCount 个", Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentColor = ErrorColor,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            CustomText("一键卸载全部", fontSize = 12.sp, color = ErrorColor)
                        }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp),
                        userScrollEnabled = false,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(commonTools) { (apkName, displayName) ->
                            val installed = isToolInstalled(apkName)
                            val version = getToolVersion(apkName)
                            val isInstalling = installingToolApk == apkName
                            val isUninstalling = uninstallingToolApk == apkName || uninstallingAllTools
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(88.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (installed) StatusConnected.copy(alpha = 0.12f)
                                        else if (isInstalling) Color(0xFFE8E8E8)
                                        else Accent.copy(alpha = 0.08f)
                                    )
                                    .border(
                                        1.dp,
                                        if (installed) StatusConnected.copy(alpha = 0.4f)
                                        else Color(0xFFE0E0E0),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .then(
                                        if (!installed && !isInstalling) Modifier.clickable {
                                            installingToolApk = apkName
                                            packageInstallOutput = "正在安装 $displayName ($apkName) ...\n"
                                            scope.launch {
                                                try {
                                                    val result = withContext(Dispatchers.IO) {
                                                        prootEnv.installPackage(apkName)
                                                    }
                                                    installingToolApk = null
                                                    packageInstallOutput += result.output
                                                    if (result.exitCode == 0) {
                                                        packageInstallOutput += "\n\n✓ $displayName 安装成功"
                                                        loadPackages()
                                                        refreshInfo()
                                                        Toast.makeText(context, "$displayName 安装成功", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        packageInstallOutput += "\n\n✗ 安装失败 (exit: ${result.exitCode})"
                                                        Toast.makeText(context, "$displayName 安装成功", Toast.LENGTH_SHORT).show()
                                                    }
                                                } catch (e: Exception) {
                                                    installingToolApk = null
                                                    packageInstallOutput += "\n\n✗ 安装失败: ${e.message}"
                                                    Toast.makeText(context, "安装失败: ${e.message?.take(80)}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        } else Modifier
                                    )
                                    .padding(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    CustomText(
                                        text = displayName,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    CustomText(
                                        text = when {
                                            isInstalling -> "安装中..."
                                            isUninstalling -> "卸载中..."
                                            installed -> version ?: "已安装"
                                            else -> "安装"
                                        },
                                        fontSize = 11.sp,
                                        color = when {
                                            isInstalling || isUninstalling -> TextSecondary
                                            installed -> StatusConnected
                                            else -> TextSecondary
                                        }
                                    )
                                    if (installed && !isUninstalling) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        CustomTextButton(
                                            onClick = {
                                                uninstallingToolApk = apkName
                                                packageInstallOutput = "正在安装 $displayName ($apkName) ...\n"
                                                scope.launch {
                                                    try {
                                                        val result = withContext(Dispatchers.IO) {
                                                            prootEnv.removePackage(apkName)
                                                        }
                                                        uninstallingToolApk = null
                                                        packageInstallOutput += result.output
                                                        if (result.exitCode == 0) {
                                                            packageInstallOutput += "\n\n✓ $displayName 卸载成功"
                                                            loadPackages()
                                                            refreshInfo()
                                                            Toast.makeText(context, "$displayName 卸载成功", Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            packageInstallOutput += "\n\n✗ 卸载失败 (exit: ${result.exitCode})"
                                                            Toast.makeText(context, "卸载失败", Toast.LENGTH_SHORT).show()
                                                        }
                                                    } catch (e: Exception) {
                                                        uninstallingToolApk = null
                                                        packageInstallOutput += "\n\n✗ 卸载失败: ${e.message}"
                                                        Toast.makeText(context, "卸载失败: ${e.message?.take(80)}", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            },
                                            contentColor = ErrorColor,
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            CustomText("卸载", fontSize = 11.sp, color = ErrorColor)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    CustomText(
                        text = "安装基础 Alpine 软件包，如 python3、gcc、git、nodejs、curl 等",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)
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
                                        CustomText("输入包名，如 python3", fontSize = 14.sp, color = TextSecondary)
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
                                packageInstallOutput = "正在安装 $pkg ...\n"
                                scope.launch {
                                    try {
                                        val result = withContext(Dispatchers.IO) {
                                            prootEnv.installPackage(pkg)
                                        }
                                        packageInstalling = false
                                        packageInstallOutput += result.output
                                        if (result.exitCode == 0) {
                                            packageInstallOutput += "\n\n✓ $pkg 安装成功"
                                            packageToInstall = ""
                                            Toast.makeText(context, "$pkg 安装成功", Toast.LENGTH_SHORT).show()
                                            loadPackages()
                                            refreshInfo()
                                        } else {
                                            packageInstallOutput += "\n\n✗ 安装失败 (exit: ${result.exitCode})"
                                            Toast.makeText(context, "安装失败", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        packageInstalling = false
                                        packageInstallOutput += "\n\n✗ 安装失败: ${e.message}"
                                        Toast.makeText(context, "安装失败: ${e.message?.take(100)}", Toast.LENGTH_LONG).show()
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

                    if (packageInstallOutput.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .background(Color(0xFFF8F8F8), RoundedCornerShape(16.dp))
                                .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CustomText(
                                    text = "安装输出",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                CustomTextButton(
                                    onClick = {
                                        val clip = android.content.ClipData.newPlainText("proot_install", packageInstallOutput)
                                        (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager)?.setPrimaryClip(clip)
                                        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                                    },
                                    contentColor = Accent,
                                    contentPadding = PaddingValues(4.dp)
                                ) {
                                    CustomText("复制", fontSize = 12.sp, color = Accent)
                                }
                                CustomTextButton(
                                    onClick = { packageInstallOutput = "" },
                                    contentColor = TextSecondary,
                                    contentPadding = PaddingValues(4.dp)
                                ) {
                                    CustomText("清空", fontSize = 12.sp, color = TextSecondary)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E1E1E))
                                    .padding(8.dp)
                            ) {
                                Column {
                                    if (packageInstalling) {
                                        LinearProgressIndicator(
                                            modifier = Modifier.fillMaxWidth().height(2.dp),
                                            color = Accent,
                                            trackColor = Color(0xFF333333)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }
                                    val vScroll = rememberScrollState()
                                    val hScroll = rememberScrollState()
                                    Text(
                                        text = packageInstallOutput,
                                        fontSize = 11.sp,
                                        color = Color(0xFFD4D4D4),
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier
                                            .heightIn(max = 200.dp)
                                            .verticalScroll(vScroll)
                                            .horizontalScroll(hScroll)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CustomButton(
                            onClick = {
                                apkUpdating = true
                                apkUpdateOutput = "正在更新软件包索引...\n"
                                scope.launch {
                                    try {
                                        val result = withContext(Dispatchers.IO) { prootEnv.updateIndex() }
                                        apkUpdateOutput += result.output
                                        if (result.exitCode == 0) {
                                            apkUpdateOutput += "\n\n✓ 更新完成"
                                        } else {
                                            apkUpdateOutput += "\n\n✗ 更新失败 (exit: ${result.exitCode})"
                                        }
                                    } catch (e: Exception) {
                                        apkUpdateOutput += "\n\n✗ 更新失败: ${e.message}"
                                    }
                                    apkUpdating = false
                                }
                            },
                            enabled = !apkUpdating,
                            modifier = Modifier.height(36.dp),
                            colors = CustomButtonDefaults.buttonColors(
                                containerColor = if (apkUpdating) Color(0xFFE8E8E8) else Accent.copy(alpha = 0.1f),
                                contentColor = Accent
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            CustomText(
                                if (apkUpdating) "更新中..." else "apk update",
                                fontSize = 12.sp,
                                color = if (apkUpdating) TextSecondary else Accent
                            )
                        }
                        CustomTextButton(
                            onClick = { loadPackages() },
                            contentColor = Accent,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            CustomText("刷新", fontSize = 12.sp)
                        }
                    }

                    if (apkUpdateOutput.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .background(Color(0xFFF8F8F8), RoundedCornerShape(16.dp))
                                .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CustomText(
                                    text = "更新输出",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                CustomTextButton(
                                    onClick = {
                                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                        cm?.setPrimaryClip(android.content.ClipData.newPlainText("apk_update", apkUpdateOutput))
                                        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                                    },
                                    contentColor = Accent,
                                    contentPadding = PaddingValues(4.dp)
                                ) {
                                    CustomText("复制", fontSize = 12.sp, color = Accent)
                                }
                                CustomTextButton(
                                    onClick = { apkUpdateOutput = "" },
                                    contentColor = TextSecondary,
                                    contentPadding = PaddingValues(4.dp)
                                ) {
                                    CustomText("清空", fontSize = 12.sp, color = TextSecondary)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E1E1E))
                                    .padding(8.dp)
                            ) {
                                Column {
                                    if (apkUpdating) {
                                        LinearProgressIndicator(
                                            modifier = Modifier.fillMaxWidth().height(2.dp),
                                            color = Accent,
                                            trackColor = Color(0xFF333333)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }
                                    val vScroll = rememberScrollState()
                                    val hScroll = rememberScrollState()
                                    Text(
                                        text = apkUpdateOutput,
                                        fontSize = 11.sp,
                                        color = Color(0xFFD4D4D4),
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier
                                            .heightIn(max = 200.dp)
                                            .verticalScroll(vScroll)
                                            .horizontalScroll(hScroll)
                                    )
                                }
                            }
                        }
                    }

                    CustomHorizontalDivider(
                        color = Color(0xFFE8E8E8),
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CustomText(
                            text = if (packagesLoading) "加载中..." else "共 ${installedPackages.size} 个",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        if (installedPackages.isNotEmpty()) {
                            CustomTextButton(
                                onClick = {
                                    val exportText = installedPackages.joinToString("\n")
                                    val exportFile = File(
                                        (context as? ComponentActivity)?.getExternalFilesDir(null),
                                        "proot/installed_packages.txt"
                                    )
                                    exportFile.parentFile?.mkdirs()
                                    exportFile.writeText(exportText)
                                    Toast.makeText(context, "已导出到 ${exportFile.absolutePath}", Toast.LENGTH_LONG).show()
                                },
                                contentColor = Accent,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                CustomText("导出列表", fontSize = 12.sp)
                            }
                        }
                    }

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

                    CustomHorizontalDivider(
                        color = Color(0xFFE8E8E8),
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    CustomText(
                        text = "终端",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    CustomText(
                        text = "输入命令执行，如 ls、pwd、python3 --version 等",
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
                                value = commandInput,
                                onValueChange = { commandInput = it },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 14.sp, color = TextPrimary, fontFamily = FontFamily.Monospace),
                                cursorBrush = SolidColor(TextPrimary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    focusManager.clearFocus()
                                    val cmd = commandInput.trim()
                                    if (cmd.isNotBlank()) {
                                        commandRunning = true
                                        commandOutput = "$\u0020$cmd\n"
                                        scope.launch {
                                            try {
                                                val result = withContext(Dispatchers.IO) {
                                                    prootEnv.executeCommand(cmd, timeoutSeconds = 120)
                                                }
                                                commandOutput += result.output
                                                commandOutput += "\n[exit: ${result.exitCode}]"
                                                if (result.exitCode == 0) {
                                                    Toast.makeText(context, "执行完成", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "退出码: ${result.exitCode}", Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Exception) {
                                                commandOutput += "\n\n✗ 执行失败: ${e.message}"
                                                Toast.makeText(context, "执行失败: ${e.message?.take(80)}", Toast.LENGTH_LONG).show()
                                            }
                                            commandRunning = false
                                        }
                                    }
                                }),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { inner ->
                                    if (commandInput.isEmpty()) {
                                        CustomText("输入命令，如 ls -la", fontSize = 14.sp, color = TextSecondary)
                                    }
                                    inner()
                                }
                            )
                        }
                        CustomButton(
                            onClick = {
                                if (commandInput.isBlank()) return@CustomButton
                                focusManager.clearFocus()
                                val cmd = commandInput.trim()
                                commandRunning = true
                                commandOutput = "$\u0020$cmd\n"
                                scope.launch {
                                    try {
                                        val result = withContext(Dispatchers.IO) {
                                            prootEnv.executeCommand(cmd, timeoutSeconds = 120)
                                        }
                                        commandOutput += result.output
                                        commandOutput += "\n[exit: ${result.exitCode}]"
                                        if (result.exitCode == 0) {
                                            Toast.makeText(context, "执行完成", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "退出码: ${result.exitCode}", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        commandOutput += "\n\n✗ 执行失败: ${e.message}"
                                        Toast.makeText(context, "执行失败: ${e.message?.take(80)}", Toast.LENGTH_LONG).show()
                                    }
                                    commandRunning = false
                                }
                            },
                            enabled = !commandRunning && commandInput.isNotBlank(),
                            modifier = Modifier.height(44.dp),
                            colors = CustomButtonDefaults.buttonColors(
                                containerColor = Accent,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            CustomText(
                                if (commandRunning) "执行中..." else "执行",
                                fontSize = 13.sp,
                                color = Color.White
                            )
                        }
                    }

                    if (commandOutput.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .background(Color(0xFFF8F8F8), RoundedCornerShape(16.dp))
                                .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CustomText(
                                    text = "命令输出",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                CustomTextButton(
                                    onClick = {
                                        val clip = android.content.ClipData.newPlainText("proot_command", commandOutput)
                                        (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager)?.setPrimaryClip(clip)
                                        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                                    },
                                    contentColor = Accent,
                                    contentPadding = PaddingValues(4.dp)
                                ) {
                                    CustomText("复制", fontSize = 12.sp, color = Accent)
                                }
                                CustomTextButton(
                                    onClick = { commandOutput = "" },
                                    contentColor = TextSecondary,
                                    contentPadding = PaddingValues(4.dp)
                                ) {
                                    CustomText("清空", fontSize = 12.sp, color = TextSecondary)
                                }
                            }
                            if (commandRunning) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().height(2.dp),
                                    color = Accent,
                                    trackColor = Color(0xFF333333)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            val cmdVScroll = rememberScrollState()
                            val cmdHScroll = rememberScrollState()
                            Text(
                                text = commandOutput,
                                fontSize = 11.sp,
                                color = Color(0xFFD4D4D4),
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .verticalScroll(cmdVScroll)
                                    .horizontalScroll(cmdHScroll)
                            )
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
