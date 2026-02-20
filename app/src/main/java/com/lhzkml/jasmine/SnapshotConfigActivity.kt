package com.lhzkml.jasmine

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.lhzkml.jasmine.core.agent.tools.snapshot.RollbackStrategy

class SnapshotConfigActivity : AppCompatActivity() {

    private lateinit var switchEnabled: SwitchCompat
    private lateinit var layoutConfigContent: LinearLayout
    private lateinit var cardMemory: LinearLayout
    private lateinit var cardFile: LinearLayout
    private lateinit var tvMemoryCheck: TextView
    private lateinit var tvFileCheck: TextView
    private lateinit var switchAutoCheckpoint: SwitchCompat
    private lateinit var cardRestartFromNode: LinearLayout
    private lateinit var cardSkipNode: LinearLayout
    private lateinit var cardDefaultOutput: LinearLayout
    private lateinit var tvRestartCheck: TextView
    private lateinit var tvSkipCheck: TextView
    private lateinit var tvDefaultCheck: TextView
    private lateinit var tvCheckpointCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_snapshot_config)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        switchEnabled = findViewById(R.id.switchEnabled)
        layoutConfigContent = findViewById(R.id.layoutConfigContent)

        switchEnabled.isChecked = ProviderManager.isSnapshotEnabled(this)
        layoutConfigContent.visibility = if (switchEnabled.isChecked) View.VISIBLE else View.GONE
        switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            ProviderManager.setSnapshotEnabled(this, isChecked)
            layoutConfigContent.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        cardMemory = findViewById(R.id.cardMemory)
        cardFile = findViewById(R.id.cardFile)
        tvMemoryCheck = findViewById(R.id.tvMemoryCheck)
        tvFileCheck = findViewById(R.id.tvFileCheck)
        switchAutoCheckpoint = findViewById(R.id.switchAutoCheckpoint)
        cardRestartFromNode = findViewById(R.id.cardRestartFromNode)
        cardSkipNode = findViewById(R.id.cardSkipNode)
        cardDefaultOutput = findViewById(R.id.cardDefaultOutput)
        tvRestartCheck = findViewById(R.id.tvRestartCheck)
        tvSkipCheck = findViewById(R.id.tvSkipCheck)
        tvDefaultCheck = findViewById(R.id.tvDefaultCheck)
        tvCheckpointCount = findViewById(R.id.tvCheckpointCount)

        switchAutoCheckpoint.isChecked = ProviderManager.isSnapshotAutoCheckpoint(this)
        switchAutoCheckpoint.setOnCheckedChangeListener { _, isChecked ->
            ProviderManager.setSnapshotAutoCheckpoint(this, isChecked)
        }

        cardMemory.setOnClickListener {
            ProviderManager.setSnapshotStorage(this, ProviderManager.SnapshotStorage.MEMORY)
            refreshStorage()
        }
        cardFile.setOnClickListener {
            ProviderManager.setSnapshotStorage(this, ProviderManager.SnapshotStorage.FILE)
            refreshStorage()
        }

        cardRestartFromNode.setOnClickListener {
            ProviderManager.setSnapshotRollbackStrategy(this, RollbackStrategy.RESTART_FROM_NODE)
            refreshRollback()
        }
        cardSkipNode.setOnClickListener {
            ProviderManager.setSnapshotRollbackStrategy(this, RollbackStrategy.SKIP_NODE)
            refreshRollback()
        }
        cardDefaultOutput.setOnClickListener {
            ProviderManager.setSnapshotRollbackStrategy(this, RollbackStrategy.USE_DEFAULT_OUTPUT)
            refreshRollback()
        }

        // 检查点管理 — 跳转到独立界面
        findViewById<View>(R.id.btnViewCheckpoints).setOnClickListener {
            if (ProviderManager.getSnapshotStorage(this) != ProviderManager.SnapshotStorage.FILE) {
                Toast.makeText(this, "内存存储模式下无法查看检查点，请切换到文件存储", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, CheckpointManagerActivity::class.java))
        }
        findViewById<View>(R.id.btnClearCheckpoints).setOnClickListener { confirmClearCheckpoints() }

        refreshStorage()
        refreshRollback()
        refreshCheckpointCount()
    }

    override fun onResume() {
        super.onResume()
        refreshCheckpointCount()
    }

    private fun refreshStorage() {
        val current = ProviderManager.getSnapshotStorage(this)
        val isMem = current == ProviderManager.SnapshotStorage.MEMORY
        cardMemory.setBackgroundResource(if (isMem) R.drawable.bg_strategy_card_selected else R.drawable.bg_strategy_card)
        cardFile.setBackgroundResource(if (!isMem) R.drawable.bg_strategy_card_selected else R.drawable.bg_strategy_card)
        tvMemoryCheck.visibility = if (isMem) View.VISIBLE else View.GONE
        tvFileCheck.visibility = if (!isMem) View.VISIBLE else View.GONE
    }

    private fun refreshRollback() {
        val current = ProviderManager.getSnapshotRollbackStrategy(this)
        val cards = mapOf(
            RollbackStrategy.RESTART_FROM_NODE to Triple(cardRestartFromNode, tvRestartCheck, true),
            RollbackStrategy.SKIP_NODE to Triple(cardSkipNode, tvSkipCheck, true),
            RollbackStrategy.USE_DEFAULT_OUTPUT to Triple(cardDefaultOutput, tvDefaultCheck, true)
        )
        for ((strategy, triple) in cards) {
            val (card, check, _) = triple
            val selected = strategy == current
            card.setBackgroundResource(if (selected) R.drawable.bg_strategy_card_selected else R.drawable.bg_strategy_card)
            check.visibility = if (selected) View.VISIBLE else View.GONE
        }
    }

    private fun refreshCheckpointCount() {
        if (ProviderManager.getSnapshotStorage(this) == ProviderManager.SnapshotStorage.FILE) {
            val snapshotDir = getExternalFilesDir("snapshots")
            if (snapshotDir != null && snapshotDir.exists()) {
                val agentDirs = snapshotDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
                var totalCheckpoints = 0
                for (dir in agentDirs) {
                    totalCheckpoints += dir.listFiles()?.count { it.extension == "json" } ?: 0
                }
                tvCheckpointCount.text = "文件存储: ${agentDirs.size} 个会话, $totalCheckpoints 个检查点"
            } else {
                tvCheckpointCount.text = "文件存储: 暂无检查点"
            }
        } else {
            tvCheckpointCount.text = "内存存储: 检查点仅在运行时有效"
        }
    }

    private fun confirmClearCheckpoints() {
        if (ProviderManager.getSnapshotStorage(this) != ProviderManager.SnapshotStorage.FILE) {
            AlertDialog.Builder(this)
                .setMessage("内存存储模式下，检查点会在 APP 关闭时自动清除。")
                .setPositiveButton("确定", null)
                .show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("清除检查点")
            .setMessage("确定要删除所有已保存的检查点吗？此操作不可撤销。")
            .setPositiveButton("删除") { _, _ ->
                val snapshotDir = getExternalFilesDir("snapshots")
                if (snapshotDir != null && snapshotDir.exists()) {
                    snapshotDir.deleteRecursively()
                    snapshotDir.mkdirs()
                }
                refreshCheckpointCount()
                Toast.makeText(this, "已清除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
