package com.lhzkml.jasmine

import android.content.Intent
import com.lhzkml.jasmine.config.ProviderManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.ui.theme.*
import com.lhzkml.jasmine.ui.components.*

class LauncherActivity : ComponentActivity() {

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            // 持久化权限
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val displayPath = resolveTreeUriToPath(it)
            // 设置 Agent 模式 + 工作区
            ProviderManager.setAgentMode(this, true)
            ProviderManager.setToolsEnabled(this, true)
            ProviderManager.setTraceEnabled(this, true)
            ProviderManager.setEventHandlerEnabled(this, true)
            ProviderManager.setWorkspacePath(this, displayPath)
            ProviderManager.setWorkspaceUri(this, it.toString())

            Toast.makeText(this, "工作区: $displayPath", Toast.LENGTH_SHORT).show()
            ProviderManager.setLastSession(this, true)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ProviderManager.initialize(this)

        // 恢复上次的模式：如果上次没有主动退出，直接跳转到 MainActivity
        val hasLastSession = ProviderManager.hasLastSession(this)
        if (hasLastSession) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContent {
            val showPermissionDialog = remember { mutableStateOf(false) }
            JasmineTheme {
                LauncherScreen(
                    onChatClick = { startChatMode() },
                    onWorkspaceClick = {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R &&
                            !android.os.Environment.isExternalStorageManager()) {
                            showPermissionDialog.value = true
                        } else {
                            folderPickerLauncher.launch(null)
                        }
                    },
                    showPermissionDialog = showPermissionDialog.value,
                    onDismissPermissionDialog = { showPermissionDialog.value = false },
                    onGoToSettings = {
                        showPermissionDialog.value = false
                        val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = android.net.Uri.parse("package:$packageName")
                        startActivity(intent)
                    }
                )
            }
        }
    }

    private fun startChatMode() {
        ProviderManager.setAgentMode(this, false)
        ProviderManager.setToolsEnabled(this, false)
        ProviderManager.setWorkspacePath(this, "")
        ProviderManager.setWorkspaceUri(this, "")
        ProviderManager.setLastSession(this, true)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun resolveTreeUriToPath(treeUri: android.net.Uri): String {
        val docId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
        val parts = docId.split(":")
        return if (parts.size == 2) {
            if (parts[0] == "primary") {
                "/storage/emulated/0/${parts[1]}"
            } else {
                "/storage/${parts[0]}/${parts[1]}"
            }
        } else {
            docId
        }
    }
}

@Composable
fun LauncherScreen(
    onChatClick: () -> Unit,
    onWorkspaceClick: () -> Unit,
    showPermissionDialog: Boolean = false,
    onDismissPermissionDialog: () -> Unit = {},
    onGoToSettings: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo / 标题
            CustomText(
                text = "Jasmine",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            CustomText(
                text = "AI Agent Framework",
                fontSize = 14.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(64.dp))

            // 普通聊天按钮
            CustomButton(
                onClick = onChatClick,
                modifier = Modifier
                    .width(220.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CustomButtonDefaults.buttonColors(
                    containerColor = BgInput,
                    contentColor = TextPrimary
                )
            ) {
                CustomText(
                    text = "普通聊天",
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 选择工作区按钮
            CustomButton(
                onClick = onWorkspaceClick,
                modifier = Modifier
                    .width(220.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CustomButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = BgPrimary
                )
            ) {
                CustomText(
                    text = "选择工作区",
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            CustomText(
                text = "选择文件夹后自动进入 Agent 模式",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
    }

    if (showPermissionDialog) {
        CustomAlertDialog(
            onDismissRequest = onDismissPermissionDialog,
            containerColor = Color.White,
            title = { CustomText("需要文件访问权限", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = { CustomText("Agent 模式需要访问设备文件。请在设置中授予\"所有文件访问\"权限。", color = TextPrimary, fontSize = 14.sp) },
            confirmButton = {
                CustomTextButton(onClick = onGoToSettings, colors = CustomButtonDefaults.textButtonColors(contentColor = Accent)) {
                    CustomText("去设置", fontSize = 14.sp)
                }
            },
            dismissButton = {
                CustomTextButton(onClick = onDismissPermissionDialog, colors = CustomButtonDefaults.textButtonColors(contentColor = TextSecondary)) {
                    CustomText("取消", fontSize = 14.sp)
                }
            }
        )
    }
}
