package com.lhzkml.jasmine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.agent.mcp.HttpMcpClient
import com.lhzkml.jasmine.core.agent.mcp.SseMcpClient
import com.lhzkml.jasmine.core.config.McpServerConfig
import com.lhzkml.jasmine.core.config.McpTransportType
import com.lhzkml.jasmine.ui.theme.BgInput
import com.lhzkml.jasmine.ui.theme.BgPrimary
import com.lhzkml.jasmine.ui.theme.JasmineTheme
import com.lhzkml.jasmine.ui.theme.TextPrimary
import com.lhzkml.jasmine.ui.theme.TextSecondary
import com.lhzkml.jasmine.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.lhzkml.jasmine.repository.McpRepository
import org.koin.android.ext.android.inject

/**
 * MCP 服务器编辑界面 (Compose 版本)
 * 添加或编辑单个 MCP 服务器配置。
 */
class McpServerEditActivity : ComponentActivity() {
    private val mcpRepository: McpRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val editIndex = intent.getIntExtra(McpServerActivity.EXTRA_EDIT_INDEX, -1)
        
        setContent {
            JasmineTheme {
                McpServerEditScreen(
                    repository = mcpRepository,
                    editIndex = editIndex,
                    onCancel = { finish() },
                    onSave = { configChanged ->
                        val resultIntent = android.content.Intent()
                        resultIntent.putExtra("config_changed", configChanged)
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun McpServerEditScreen(
    repository: McpRepository,
    editIndex: Int,
    onCancel: () -> Unit,
    onSave: (Boolean) -> Unit  // 参数表示配置是否有变化
) {
    val scope = rememberCoroutineScope()
    
    // 加载已有配置
    val existingServer = remember {
        if (editIndex >= 0) {
            val servers = repository.getMcpServers()
            servers.getOrNull(editIndex)
        } else null
    }
    
    var name by remember { mutableStateOf(existingServer?.name ?: "") }
    var url by remember { mutableStateOf(existingServer?.url ?: "") }
    var transportType by remember { 
        mutableStateOf(existingServer?.transportType ?: McpTransportType.STREAMABLE_HTTP) 
    }
    var headerName by remember { mutableStateOf(existingServer?.headerName ?: "") }
    var headerValue by remember { mutableStateOf(existingServer?.headerValue ?: "") }
    
    var testResult by remember { mutableStateOf<String?>(null) }
    var testResultColor by remember { mutableStateOf(TextSecondary) }
    var isTesting by remember { mutableStateOf(false) }
    
    // 检查配置是否有变化
    fun hasConfigChanged(): Boolean {
        if (editIndex < 0) {
            // 新添加的服务器，肯定有变化
            return true
        }
        
        val existing = existingServer ?: return true
        
        return name.trim() != existing.name ||
               url.trim() != existing.url ||
               transportType != existing.transportType ||
               headerName.trim() != existing.headerName ||
               headerValue.trim() != existing.headerValue
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
                onClick = onCancel,
                colors = CustomButtonDefaults.textButtonColors(contentColor = TextSecondary)
            ) {
                CustomText("取消", fontSize = 14.sp)
            }
            
            CustomText(
                text = if (editIndex >= 0) "编辑 MCP 服务器" else "添加 MCP 服务器",
                fontSize = 17.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            
            CustomTextButton(
                onClick = {
                    val finalName = name.trim().ifEmpty { "MCP Server" }
                    val finalUrl = url.trim()
                    
                    if (finalUrl.isEmpty()) {
                        return@CustomTextButton
                    }
                    
                    val configChanged = hasConfigChanged()
                    
                    val serverConfig = McpServerConfig(
                        name = finalName,
                        url = finalUrl,
                        transportType = transportType,
                        headerName = headerName.trim(),
                        headerValue = headerValue.trim(),
                        enabled = true
                    )
                    
                    if (editIndex >= 0) {
                        val servers = repository.getMcpServers()
                        val oldEnabled = servers.getOrNull(editIndex)?.enabled ?: true
                        repository.updateMcpServer(editIndex, serverConfig.copy(enabled = oldEnabled))
                    } else {
                        repository.addMcpServer(serverConfig)
                    }
                    
                    onSave(configChanged)
                },
                colors = CustomButtonDefaults.textButtonColors(contentColor = Color(0xFF4CAF50))
            ) {
                CustomText("保存", fontSize = 14.sp)
            }
        }
        
        CustomHorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)
        
        // 表单内容
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 名称
            Column {
                CustomText(
                    "名称",
                    fontSize = 14.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                McpInputField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "如：DeepWiki"
                )
            }
            
            // URL
            Column {
                CustomText(
                    "服务器 URL",
                    fontSize = 14.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                McpInputField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = "https://example.com/mcp"
                )
            }
            
            // 传输类型
            Column {
                CustomText(
                    "传输类型",
                    fontSize = 14.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(8.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .selectable(
                                    selected = transportType == McpTransportType.STREAMABLE_HTTP,
                                    onClick = { transportType = McpTransportType.STREAMABLE_HTTP }
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CustomRadioButton(
                                selected = transportType == McpTransportType.STREAMABLE_HTTP,
                                onClick = { transportType = McpTransportType.STREAMABLE_HTTP }
                            )
                            CustomText(
                                "Streamable HTTP",
                                fontSize = 13.sp,
                                color = TextPrimary
                            )
                        }
                        
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .selectable(
                                    selected = transportType == McpTransportType.SSE,
                                    onClick = { transportType = McpTransportType.SSE }
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CustomRadioButton(
                                selected = transportType == McpTransportType.SSE,
                                onClick = { transportType = McpTransportType.SSE }
                            )
                            CustomText(
                                "SSE",
                                fontSize = 13.sp,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
            
            // 请求头名称
            Column {
                CustomText(
                    "请求头名称",
                    fontSize = 14.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                McpInputField(
                    value = headerName,
                    onValueChange = { headerName = it },
                    placeholder = "如 Authorization"
                )
            }
            
            // 请求头值
            Column {
                CustomText(
                    "请求头值",
                    fontSize = 14.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                McpInputField(
                    value = headerValue,
                    onValueChange = { headerValue = it },
                    placeholder = "如 Bearer xxxxxx"
                )
            }
            
            // 测试连接按钮
            CustomButton(
                onClick = {
                    if (url.trim().isEmpty()) {
                        return@CustomButton
                    }
                    
                    isTesting = true
                    testResult = "正在连接..."
                    testResultColor = TextSecondary
                    
                    scope.launch {
                        try {
                            val headers = mutableMapOf<String, String>()
                            val hName = headerName.trim()
                            val hValue = headerValue.trim()
                            if (hName.isNotBlank() && hValue.isNotBlank()) {
                                headers[hName] = hValue
                            }
                            
                            val client = when (transportType) {
                                McpTransportType.SSE ->
                                    SseMcpClient(url.trim(), customHeaders = headers)
                                McpTransportType.STREAMABLE_HTTP ->
                                    HttpMcpClient(url.trim(), headers)
                            }
                            
                            withContext(Dispatchers.IO) {
                                client.connect()
                                val tools = client.listTools()
                                client.close()
                                
                                withContext(Dispatchers.Main) {
                                    val sb = StringBuilder()
                                    sb.appendLine("连接成功，发现 ${tools.size} 个工具：")
                                    tools.forEach { tool ->
                                        val desc = tool.description?.let { d ->
                                            if (d.length > 50) d.take(50) + "..." else d
                                        } ?: ""
                                        if (desc.isNotEmpty()) {
                                            sb.appendLine("  ${tool.name} — $desc")
                                        } else {
                                            sb.appendLine("  ${tool.name}")
                                        }
                                    }
                                    testResult = sb.toString().trimEnd()
                                    testResultColor = Color(0xFF4CAF50)
                                    isTesting = false
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                testResult = "连接失败: ${e.message}"
                                testResultColor = Color(0xFFF44336)
                                isTesting = false
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = !isTesting,
                colors = CustomButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2D2D2D),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                CustomText(
                    if (isTesting) "连接中..." else "测试连接",
                    fontSize = 15.sp
                )
            }
            
            // 测试结果
            testResult?.let { result ->
                CustomText(
                    result,
                    fontSize = 13.sp,
                    color = testResultColor
                )
            }
        }
    }
}

@Composable
fun McpInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 14.sp,
                color = TextPrimary
            ),
            cursorBrush = SolidColor(TextPrimary),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    CustomText(
                        placeholder,
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                }
                innerTextField()
            }
        )
    }
}
