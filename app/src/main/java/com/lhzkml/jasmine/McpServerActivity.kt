package com.lhzkml.jasmine

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.config.AppConfig
import com.lhzkml.jasmine.core.agent.mcp.McpToolDefinition
import com.lhzkml.jasmine.core.config.McpServerConfig
import com.lhzkml.jasmine.core.config.McpTransportType
import com.lhzkml.jasmine.ui.theme.Accent
import com.lhzkml.jasmine.ui.theme.BgPrimary
import com.lhzkml.jasmine.ui.theme.JasmineTheme
import com.lhzkml.jasmine.ui.theme.TextPrimary
import com.lhzkml.jasmine.ui.theme.TextSecondary
import com.lhzkml.jasmine.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class McpServerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_EDIT_INDEX = "edit_index"
        const val REQUEST_EDIT = 1001
    }
    
    private var refreshCallback: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JasmineTheme {
                McpServerScreen(
                    onBack = { finish() },
                    onAddServer = {
                        @Suppress("DEPRECATION")
                        startActivityForResult(
                            Intent(this, McpServerEditActivity::class.java),
                            REQUEST_EDIT
                        )
                    },
                    onEditServer = { index ->
                        val intent = Intent(this, McpServerEditActivity::class.java)
                        intent.putExtra(EXTRA_EDIT_INDEX, index)
                        @Suppress("DEPRECATION")
                        startActivityForResult(intent, REQUEST_EDIT)
                    },
                    onRefreshCallbackSet = { callback ->
                        refreshCallback = callback
                    }
                )
            }
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_EDIT && resultCode == RESULT_OK) {
            val configChanged = data?.getBooleanExtra("config_changed", true) ?: true
            
            if (configChanged) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    refreshCallback?.invoke()
                    
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        AppConfig.mcpConnectionManager().reconnect { 
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                refreshCallback?.invoke()
                            }
                        }
                    }
                }
            } else {
                refreshCallback?.invoke()
            }
        }
    }
}

@Composable
fun McpServerScreen(
    onBack: () -> Unit,
    onAddServer: () -> Unit,
    onEditServer: (Int) -> Unit,
    onRefreshCallbackSet: ((()->Unit) -> Unit)? = null
) {
    val config = AppConfig.configRepo()
    val scope = rememberCoroutineScope()
    
    var mcpEnabled by remember { mutableStateOf(config.isMcpEnabled()) }
    var servers by remember { mutableStateOf(config.getMcpServers()) }
    var connectionResults by remember { mutableStateOf<Map<Int, ConnectionResult>>(emptyMap()) }
    var connectingServers by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var showDeleteDialog by remember { mutableStateOf<Pair<Int, McpServerConfig>?>(null) }
    var showActionsDialog by remember { mutableStateOf<Pair<Int, McpServerConfig>?>(null) }
    
    val refresh: () -> Unit = {
        servers = config.getMcpServers()
        
        scope.launch {
            val manager = AppConfig.mcpConnectionManager()
            val cache = manager.getConnectionCache()
            
            val results = mutableMapOf<Int, ConnectionResult>()
            servers.forEachIndexed { index, server ->
                if (!server.enabled) return@forEachIndexed
                
                val cached = cache[server.name]
                if (cached != null) {
                    results[index] = ConnectionResult(
                        success = cached.success,
                        tools = cached.tools,
                        error = cached.error
                    )
                }
            }
            
            connectionResults = results
        }
    }
    
    LaunchedEffect(Unit) {
        onRefreshCallbackSet?.invoke(refresh)
    }
    
    LaunchedEffect(Unit) {
        val manager = AppConfig.mcpConnectionManager()
        val cache = manager.getConnectionCache()
        
        val results = mutableMapOf<Int, ConnectionResult>()
        val connecting = mutableSetOf<Int>()
        var needsConnect = false
        
        servers.forEachIndexed { index, server ->
            if (server.enabled && server.url.isNotBlank()) {
                val cached = cache[server.name]
                if (cached != null) {
                    results[index] = ConnectionResult(
                        success = cached.success,
                        tools = cached.tools,
                        error = cached.error
                    )
                } else {
                    connecting.add(index)
                    needsConnect = true
                }
            }
        }
        
        connectionResults = results
        connectingServers = connecting
        
        if (needsConnect) {
            withContext(Dispatchers.IO) {
                manager.preconnect()
            }
            
            val updatedCache = manager.getConnectionCache()
            servers.forEachIndexed { index, server ->
                val cached = updatedCache[server.name]
                if (cached != null) {
                    results[index] = ConnectionResult(
                        success = cached.success,
                        tools = cached.tools,
                        error = cached.error
                    )
                }
            }
            
            connectionResults = results
            connectingServers = emptySet()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
    ) {
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
                colors = CustomButtonDefaults.textButtonColors(contentColor = TextPrimary)
            ) {
                CustomText("← 返回", fontSize = 14.sp)
            }
            
            CustomText(
                text = "MCP 服务器管理",
                fontSize = 17.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            
            CustomTextButton(
                onClick = onAddServer,
                colors = CustomButtonDefaults.textButtonColors(contentColor = Color(0xFF4CAF50))
            ) {
                CustomText("+ 添加", fontSize = 14.sp)
            }
        }
        
        CustomHorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .background(Color.White, RoundedCornerShape(8.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    CustomText(
                        "启用 MCP 工具",
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                    CustomText(
                        "从 MCP 服务器加载远程工具",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                
                CustomSwitch(
                    checked = mcpEnabled,
                    onCheckedChange = { enabled ->
                        mcpEnabled = enabled
                        config.setMcpEnabled(enabled)
                    },
                    colors = CustomSwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Accent,
                        uncheckedThumbColor = Color.White
                    )
                )
            }
        }
        
        if (mcpEnabled) {
            if (servers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CustomText(
                        "暂无 MCP 服务器\n点击右上角添加",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(servers) { index, server ->
                        McpServerItem(
                            server = server,
                            result = connectionResults[index],
                            isConnecting = connectingServers.contains(index),
                            onTestClick = {
                                scope.launch {
                                    connectingServers = connectingServers + index
                                    
                                    withContext(Dispatchers.IO) {
                                        AppConfig.mcpConnectionManager().connectSingleServerByName(server.name)
                                    }
                                    
                                    val cached = AppConfig.mcpConnectionManager().getServerStatus(server.name)
                                    if (cached != null) {
                                        connectionResults = connectionResults + (index to ConnectionResult(
                                            success = cached.success,
                                            tools = cached.tools,
                                            error = cached.error
                                        ))
                                    }
                                    
                                    connectingServers = connectingServers - index
                                }
                            },
                            onMoreClick = {
                                showActionsDialog = index to server
                            },
                            onItemClick = {
                                onEditServer(index)
                            }
                        )
                    }
                }
            }
        }
    }
    
    showActionsDialog?.let { (index, server) ->
        CustomAlertDialog(
            onDismissRequest = { showActionsDialog = null },
            title = { 
                CustomText(
                    server.name,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    CustomTextButton(
                        onClick = {
                            showActionsDialog = null
                            onEditServer(index)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CustomButtonDefaults.textButtonColors(contentColor = TextPrimary)
                    ) {
                        CustomText(
                            "编辑",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start,
                            fontSize = 16.sp
                        )
                    }
                    
                    CustomTextButton(
                        onClick = {
                            showActionsDialog = null
                            config.updateMcpServer(index, server.copy(enabled = !server.enabled))
                            servers = config.getMcpServers()
                            connectionResults = connectionResults - index
                            if (!server.enabled) {
                                scope.launch {
                                    connectingServers = connectingServers + index
                                    
                                    withContext(Dispatchers.IO) {
                                        AppConfig.mcpConnectionManager().connectSingleServerByName(server.copy(enabled = true).name)
                                    }
                                    
                                    val cached = AppConfig.mcpConnectionManager().getServerStatus(server.name)
                                    if (cached != null) {
                                        connectionResults = connectionResults + (index to ConnectionResult(
                                            success = cached.success,
                                            tools = cached.tools,
                                            error = cached.error
                                        ))
                                    }
                                    
                                    connectingServers = connectingServers - index
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CustomButtonDefaults.textButtonColors(contentColor = TextPrimary)
                    ) {
                        CustomText(
                            if (server.enabled) "禁用" else "启用",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start,
                            fontSize = 16.sp
                        )
                    }
                    
                    CustomTextButton(
                        onClick = {
                            showActionsDialog = null
                            showDeleteDialog = index to server
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CustomButtonDefaults.textButtonColors(contentColor = Color(0xFFF44336))
                    ) {
                        CustomText(
                            "删除",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start,
                            fontSize = 16.sp
                        )
                    }
                }
            },
            confirmButton = {
                CustomTextButton(
                    onClick = { showActionsDialog = null },
                    colors = CustomButtonDefaults.textButtonColors(contentColor = TextPrimary)
                ) {
                    CustomText("取消")
                }
            }
        )
    }
    
    showDeleteDialog?.let { (index, server) ->
        CustomAlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { 
                CustomText(
                    "确认删除",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = { 
                CustomText(
                    "确定删除 ${server.name}？",
                    color = TextPrimary,
                    fontSize = 16.sp
                )
            },
            confirmButton = {
                CustomTextButton(
                    onClick = {
                        showDeleteDialog = null
                        AppConfig.mcpConnectionManager().clearServerCache(server.name)
                        config.removeMcpServer(index)
                        servers = config.getMcpServers()
                        connectionResults = emptyMap()
                        connectingServers = emptySet()
                    },
                    colors = CustomButtonDefaults.textButtonColors(contentColor = Color(0xFFF44336))
                ) {
                    CustomText("删除")
                }
            },
            dismissButton = {
                CustomTextButton(
                    onClick = { showDeleteDialog = null },
                    colors = CustomButtonDefaults.textButtonColors(contentColor = TextPrimary)
                ) {
                    CustomText("取消")
                }
            }
        )
    }
}

@Composable
fun McpServerItem(
    server: McpServerConfig,
    result: ConnectionResult?,
    isConnecting: Boolean,
    onTestClick: () -> Unit,
    onMoreClick: () -> Unit,
    onItemClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(8.dp))
            .clickable(onClick = onItemClick)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val statusColor = when {
                    !server.enabled -> Color(0xFF9E9E9E)
                    isConnecting -> Color(0xFFFFC107)
                    result == null -> Color(0xFF9E9E9E)
                    result.success == true -> Color(0xFF4CAF50)
                    else -> Color(0xFFF44336)
                }
                
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(statusColor, CircleShape)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    CustomText(
                        server.name,
                        fontSize = 15.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    CustomText(
                        server.url,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    
                    val transportLabel = when (server.transportType) {
                        McpTransportType.STREAMABLE_HTTP -> "Streamable HTTP"
                        McpTransportType.SSE -> "SSE"
                    }
                    val enabledLabel = if (server.enabled) "" else " · 已禁用"
                    
                    CustomText(
                        "$transportLabel$enabledLabel",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                
                CustomTextButton(
                    onClick = onTestClick,
                    colors = CustomButtonDefaults.textButtonColors(contentColor = Color(0xFF4CAF50))
                ) {
                    CustomText("重连", fontSize = 13.sp)
                }
                
                CustomTextButton(
                    onClick = onMoreClick,
                    colors = CustomButtonDefaults.textButtonColors(contentColor = TextSecondary)
                ) {
                    CustomText("⋮", fontSize = 20.sp)
                }
            }
            
            if (server.enabled && isConnecting) {
                CustomText(
                    "连接中...",
                    fontSize = 12.sp,
                    color = Color(0xFFFFC107),
                    modifier = Modifier.padding(
                        start = 38.dp,
                        end = 16.dp,
                        bottom = 12.dp
                    )
                )
            }
            
            if (server.enabled && !isConnecting && result != null && result.success == true && result.tools.isNotEmpty()) {
                CustomHorizontalDivider(
                    color = Color(0xFFE0E0E0),
                    thickness = 1.dp,
                    modifier = Modifier.padding(start = 38.dp, end = 16.dp)
                )
                
                Column(
                    modifier = Modifier.padding(
                        start = 38.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 12.dp
                    )
                ) {
                    CustomText(
                        "可用工具 (${result.tools.size})",
                        fontSize = 12.sp,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    
                    CustomText(
                        result.tools.joinToString("\n") { tool ->
                            val desc = tool.description?.let { d ->
                                if (d.length > 60) d.take(60) + "..." else d
                            } ?: ""
                            if (desc.isNotEmpty()) "${tool.name} — $desc" else tool.name
                        },
                        fontSize = 12.sp,
                        color = TextPrimary,
                        lineHeight = 16.sp
                    )
                }
            }
            
            if (server.enabled && !isConnecting && result != null && result.success == false) {
                CustomText(
                    "连接失败: ${result.error}",
                    fontSize = 12.sp,
                    color = Color(0xFFF44336),
                    modifier = Modifier.padding(
                        start = 38.dp,
                        end = 16.dp,
                        bottom = 12.dp
                    )
                )
            }
        }
    }
}

data class ConnectionResult(
    val success: Boolean? = null,
    val tools: List<McpToolDefinition> = emptyList(),
    val error: String? = null
)
