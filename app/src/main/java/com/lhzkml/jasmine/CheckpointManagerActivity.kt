package com.lhzkml.jasmine

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lhzkml.jasmine.core.agent.tools.snapshot.AgentCheckpoint
import com.lhzkml.jasmine.core.agent.tools.snapshot.FilePersistenceStorageProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CheckpointManagerActivity : AppCompatActivity() {

    private lateinit var rvCheckpoints: RecyclerView
    private lateinit var tvStats: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: CheckpointListAdapter
    private var provider: FilePersistenceStorageProvider? = null

    private val detailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // 详情页删除或恢复后刷新列表
        if (result.resultCode == CheckpointDetailActivity.RESULT_DELETED) {
            loadCheckpoints()
        } else if (result.resultCode == CheckpointDetailActivity.RESULT_RESTORED) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_checkpoint_manager)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnClearAll).setOnClickListener { confirmClearAll() }

        tvStats = findViewById(R.id.tvStats)
        tvEmpty = findViewById(R.id.tvEmpty)
        rvCheckpoints = findViewById(R.id.rvCheckpoints)

        adapter = CheckpointListAdapter(
            onItemClick = { agentId, cp -> openDetail(agentId, cp) },
            onDeleteSessionClick = { agentId -> confirmDeleteSession(agentId) }
        )
        rvCheckpoints.layoutManager = LinearLayoutManager(this)
        rvCheckpoints.adapter = adapter

        loadCheckpoints()
    }

    private fun openDetail(agentId: String, checkpoint: AgentCheckpoint) {
        val intent = Intent(this, CheckpointDetailActivity::class.java)
        intent.putExtra(CheckpointDetailActivity.EXTRA_AGENT_ID, agentId)
        intent.putExtra(CheckpointDetailActivity.EXTRA_CHECKPOINT_ID, checkpoint.checkpointId)
        detailLauncher.launch(intent)
    }

    private fun loadCheckpoints() {
        val snapshotDir = getExternalFilesDir("snapshots")
        if (snapshotDir == null || !snapshotDir.exists()) {
            showEmpty()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val fp = FilePersistenceStorageProvider(snapshotDir)
            provider = fp
            val agentDirs = snapshotDir.listFiles()?.filter { it.isDirectory } ?: emptyList()

            val items = mutableListOf<ListItem>()
            var totalSessions = 0
            var totalCheckpoints = 0

            for (dir in agentDirs) {
                val agentId = dir.name
                val checkpoints = fp.getCheckpoints(agentId)
                if (checkpoints.isEmpty()) continue

                totalSessions++
                val nonTombstone = checkpoints.filter { !it.isTombstone() }
                totalCheckpoints += nonTombstone.size

                val completed = checkpoints.any { it.isTombstone() }
                items.add(ListItem.SessionHeader(agentId, nonTombstone.size, completed))

                for (cp in checkpoints.sortedByDescending { it.createdAt }) {
                    items.add(ListItem.CheckpointItem(agentId, cp))
                }
            }

            withContext(Dispatchers.Main) {
                if (items.isEmpty()) {
                    showEmpty()
                } else {
                    tvEmpty.visibility = View.GONE
                    rvCheckpoints.visibility = View.VISIBLE
                    tvStats.text = "$totalSessions 个会话, $totalCheckpoints 个检查点"
                    adapter.submitList(items)
                }
            }
        }
    }

    private fun showEmpty() {
        tvEmpty.visibility = View.VISIBLE
        rvCheckpoints.visibility = View.GONE
        tvStats.text = "暂无检查点"
        adapter.submitList(emptyList())
    }

    private fun confirmDeleteSession(agentId: String) {
        val displayId = if (agentId.length > 20) agentId.take(20) + "..." else agentId
        AlertDialog.Builder(this)
            .setMessage("删除会话 $displayId 的全部检查点？")
            .setPositiveButton("删除") { _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    provider?.deleteCheckpoints(agentId)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@CheckpointManagerActivity, "会话已清除", Toast.LENGTH_SHORT).show()
                        loadCheckpoints()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmClearAll() {
        AlertDialog.Builder(this)
            .setTitle("清除全部")
            .setMessage("删除所有检查点？此操作不可撤销。")
            .setPositiveButton("删除") { _, _ ->
                val snapshotDir = getExternalFilesDir("snapshots")
                if (snapshotDir != null && snapshotDir.exists()) {
                    snapshotDir.deleteRecursively()
                    snapshotDir.mkdirs()
                }
                showEmpty()
                Toast.makeText(this, "已清除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ========== 列表数据模型 ==========

    sealed class ListItem {
        data class SessionHeader(
            val agentId: String,
            val checkpointCount: Int,
            val completed: Boolean
        ) : ListItem()

        data class CheckpointItem(
            val agentId: String,
            val checkpoint: AgentCheckpoint
        ) : ListItem()
    }

    // ========== RecyclerView Adapter ==========

    class CheckpointListAdapter(
        private val onItemClick: (String, AgentCheckpoint) -> Unit,
        private val onDeleteSessionClick: (String) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            const val TYPE_SESSION = 0
            const val TYPE_CHECKPOINT = 1
        }

        private var items = listOf<ListItem>()

        fun submitList(list: List<ListItem>) {
            items = list
            notifyDataSetChanged()
        }

        override fun getItemCount() = items.size

        override fun getItemViewType(position: Int) = when (items[position]) {
            is ListItem.SessionHeader -> TYPE_SESSION
            is ListItem.CheckpointItem -> TYPE_CHECKPOINT
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                TYPE_SESSION -> SessionVH(inflater.inflate(R.layout.item_checkpoint_session, parent, false))
                else -> CheckpointVH(inflater.inflate(R.layout.item_checkpoint, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val timeFormat = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())

            when (val item = items[position]) {
                is ListItem.SessionHeader -> {
                    val vh = holder as SessionVH
                    val displayId = if (item.agentId.length > 24)
                        item.agentId.take(24) + "..." else item.agentId
                    vh.tvSessionId.text = "会话: $displayId"
                    vh.tvSessionStatus.text = if (item.completed)
                        "[已完成] ${item.checkpointCount} 个检查点"
                    else
                        "[进行中] ${item.checkpointCount} 个检查点"
                    vh.btnDeleteSession.setOnClickListener {
                        onDeleteSessionClick(item.agentId)
                    }
                }
                is ListItem.CheckpointItem -> {
                    val vh = holder as CheckpointVH
                    val cp = item.checkpoint
                    val isTombstone = cp.isTombstone()

                    vh.tvNodePath.text = if (isTombstone) "[墓碑] ${cp.nodePath}" else cp.nodePath
                    vh.tvTime.text = timeFormat.format(Date(cp.createdAt))

                    val metaParts = mutableListOf<String>()
                    metaParts.add("${cp.messageHistory.size} 条消息")
                    metaParts.add("v${cp.version}")
                    cp.lastInput?.take(40)?.let { metaParts.add("输入: $it") }
                    vh.tvMeta.text = metaParts.joinToString(" | ")

                    // 消息预览（始终显示最后一条消息摘要）
                    if (cp.messageHistory.isNotEmpty()) {
                        val lastMsg = cp.messageHistory.last()
                        val role = when (lastMsg.role) {
                            "user" -> "[User]"
                            "assistant" -> "[AI]"
                            "system" -> "[Sys]"
                            else -> "[${lastMsg.role}]"
                        }
                        vh.tvPreview.text = "$role ${lastMsg.content.take(60)}"
                        vh.tvPreview.visibility = View.VISIBLE
                    } else {
                        vh.tvPreview.visibility = View.GONE
                    }

                    // 隐藏操作按钮行（操作移到详情页）
                    vh.layoutActions.visibility = View.GONE

                    // 点击打开详情页
                    vh.itemView.setOnClickListener {
                        onItemClick(item.agentId, cp)
                    }
                }
            }
        }

        class SessionVH(view: View) : RecyclerView.ViewHolder(view) {
            val tvSessionId: TextView = view.findViewById(R.id.tvSessionId)
            val tvSessionStatus: TextView = view.findViewById(R.id.tvSessionStatus)
            val btnDeleteSession: TextView = view.findViewById(R.id.btnDeleteSession)
        }

        class CheckpointVH(view: View) : RecyclerView.ViewHolder(view) {
            val tvNodePath: TextView = view.findViewById(R.id.tvNodePath)
            val tvTime: TextView = view.findViewById(R.id.tvTime)
            val tvMeta: TextView = view.findViewById(R.id.tvMeta)
            val tvPreview: TextView = view.findViewById(R.id.tvPreview)
            val layoutActions: LinearLayout = view.findViewById(R.id.layoutActions)
        }
    }
}
