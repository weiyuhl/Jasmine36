package com.lhzkml.jasmine

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.ui.theme.*

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
            ProviderManager.setWorkspaceUri(it.toString())

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
            JasmineTheme {
                LauncherScreen(
                    onChatClick = { startChatMode() },
                    onWorkspaceClick = { requestWorkspaceAccess() }
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

    private fun requestWorkspaceAccess() {
        // Android 11+ 需要 MANAGE_EXTERNAL_STORAGE 权限
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                AlertDialog.Builder(this)
                    .setTitle("需要文件访问权限")
                    .setMessage("Agent 模式需要访问设备文件。请在设置中授予\"所有文件访问\"权限。")
                    .setPositiveButton("去设置") { _, _ ->
                        val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = android.net.Uri.parse("package:$packageName")
                        startActivity(intent)
                    }
                    .setNegativeButton("取消", null)
                    .show()
                return
            }
        }
        folderPickerLauncher.launch(null)
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
    onWorkspaceClick: () -> Unit
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
            Text(
                text = "Jasmine",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "AI Agent Framework",
                fontSize = 14.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(64.dp))

            // 普通聊天按钮
            Button(
                onClick = onChatClick,
                modifier = Modifier
                    .width(220.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BgInput,
                    contentColor = TextPrimary
                )
            ) {
                Text(
                    text = "普通聊天",
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 选择工作区按钮
            Button(
                onClick = onWorkspaceClick,
                modifier = Modifier
                    .width(220.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = BgPrimary
                )
            ) {
                Text(
                    text = "选择工作区",
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "选择文件夹后自动进入 Agent 模式",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
    }
}
