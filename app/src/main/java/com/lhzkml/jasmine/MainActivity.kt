package com.lhzkml.jasmine

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lhzkml.jasmine.ui.ChatScreen
import com.lhzkml.jasmine.ui.ChatViewModel
import com.lhzkml.jasmine.ui.RightDrawerContent
import com.lhzkml.jasmine.ui.theme.JasmineTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CONVERSATION_ID = "conversation_id"
    }

    private lateinit var viewModel: ChatViewModel
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var mainContent: LinearLayout
    private lateinit var fileTreePanel: LinearLayout
    private lateinit var tvFileTreeRoot: TextView
    private lateinit var rvFileTree: RecyclerView
    private val fileTreeAdapter = FileTreeAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this)[ChatViewModel::class.java]

        drawerLayout = findViewById(R.id.drawerLayout)
        mainContent = findViewById(R.id.mainContent)
        fileTreePanel = findViewById(R.id.fileTreePanel)
        tvFileTreeRoot = findViewById(R.id.tvFileTreeRoot)
        rvFileTree = findViewById(R.id.rvFileTree)
        rvFileTree.layoutManager = LinearLayoutManager(this)
        rvFileTree.adapter = fileTreeAdapter
        fileTreeAdapter.onFileClick = { file ->
            Toast.makeText(this, file.absolutePath, Toast.LENGTH_SHORT).show()
        }

        val composeView = findViewById<ComposeView>(R.id.composeView)
        composeView.setContent {
            JasmineTheme {
                ChatScreen(viewModel = viewModel)
            }
        }

        val drawerComposeView = findViewById<ComposeView>(R.id.drawerComposeView)
        drawerComposeView.setContent {
            JasmineTheme {
                RightDrawerContent(
                    conversations = viewModel.drawerConversations,
                    isEmpty = viewModel.drawerEmpty,
                    onNewChat = {
                        viewModel.startNewConversation()
                        drawerLayout.closeDrawer(Gravity.END)
                    },
                    onConversationClick = { info ->
                        viewModel.loadConversation(info.id)
                        drawerLayout.closeDrawer(Gravity.END)
                    },
                    onConversationDelete = { info ->
                        AlertDialog.Builder(this)
                            .setMessage("确定删除这个对话吗？")
                            .setPositiveButton("删除") { _, _ -> viewModel.deleteConversation(info) }
                            .setNegativeButton("取消", null)
                            .show()
                    },
                    onSettings = {
                        drawerLayout.closeDrawer(Gravity.END)
                        viewModel.openSettings()
                    }
                )
            }
        }

        val drawerPanel = findViewById<LinearLayout>(R.id.drawerPanel)
        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                if (drawerView.id == R.id.drawerPanel) {
                    mainContent.translationX = -(drawerPanel.width * slideOffset)
                } else if (drawerView.id == R.id.fileTreePanel) {
                    mainContent.translationX = fileTreePanel.width * slideOffset
                }
                drawerView.bringToFront()
                mainContent.translationZ = 0f
                drawerView.translationZ = 10f
            }
            override fun onDrawerClosed(drawerView: View) {
                mainContent.translationX = 0f
            }
            override fun onDrawerOpened(drawerView: View) {
                if (drawerView.id == R.id.fileTreePanel) {
                    val path = ProviderManager.getWorkspacePath(this@MainActivity)
                    if (path.isNotEmpty()) fileTreeAdapter.loadRoot(path)
                }
            }
        })
        drawerLayout.setScrimColor(0x00000000)

        setupDrawerAccess()
        viewModel.initialize(this)
    }

    private fun setupDrawerAccess() {
        if (!ProviderManager.isAgentMode(this)) {
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.START)
        } else {
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.START)
            val path = ProviderManager.getWorkspacePath(this)
            if (path.isNotEmpty()) {
                tvFileTreeRoot.text = java.io.File(path).name
                fileTreeAdapter.loadRoot(path)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
        setupDrawerAccess()
    }

    override fun onPause() {
        super.onPause()
        viewModel.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.handleNewIntent(intent)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(Gravity.START)) {
            drawerLayout.closeDrawer(Gravity.START)
        } else if (drawerLayout.isDrawerOpen(Gravity.END)) {
            drawerLayout.closeDrawer(Gravity.END)
        } else {
            super.onBackPressed()
        }
    }

    fun openDrawerEnd() {
        drawerLayout.openDrawer(Gravity.END)
    }

    fun openDrawerStart() {
        drawerLayout.openDrawer(Gravity.START)
    }
}
