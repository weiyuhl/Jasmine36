package com.lhzkml.jasmine

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class LauncherActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_OPEN_FOLDER = 2001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)

        ProviderManager.initialize(this)

        // 普通聊天：关闭工具，清除工作区
        findViewById<TextView>(R.id.btnChat).setOnClickListener {
            ProviderManager.setAgentMode(this, false)
            ProviderManager.setToolsEnabled(this, false)
            ProviderManager.setWorkspacePath(this, "")
            ProviderManager.setWorkspaceUri(this, "")
            startActivity(Intent(this, MainActivity::class.java))
        }

        // 选择工作区 -> Agent 模式
        findViewById<TextView>(R.id.btnSelectWorkspace).setOnClickListener {
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
                    return@setOnClickListener
                }
            }
            openFolderPicker()
        }
    }

    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_OPEN_FOLDER)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OPEN_FOLDER && resultCode == RESULT_OK) {
            val treeUri = data?.data ?: return
            // 持久化权限
            contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val displayPath = resolveTreeUriToPath(treeUri)
            // 设置 Agent 模式 + 工作区
            ProviderManager.setAgentMode(this, true)
            ProviderManager.setToolsEnabled(this, true)
            ProviderManager.setStreamEnabled(this, true)
            ProviderManager.setTraceEnabled(this, true)
            ProviderManager.setEventHandlerEnabled(this, true)
            ProviderManager.setWorkspacePath(this, displayPath)
            ProviderManager.setWorkspaceUri(this, treeUri.toString())

            Toast.makeText(this, "工作区: $displayPath", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
        }
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
