package com.lhzkml.jasmine

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.config.ToolCatalog
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.TextFieldValue
import com.lhzkml.jasmine.ui.theme.BgInput
import com.lhzkml.jasmine.ui.theme.BgPrimary
import com.lhzkml.jasmine.ui.theme.TextPrimary
import com.lhzkml.jasmine.ui.theme.TextSecondary

/**
 * 工具管理界面
 * 独立 Activity，展示所有可用工具的启用/禁用状态。
 * 支持两种模式：
 * - 普通模式（默认）：管理 enabled_tools
 * - Agent 预设模式（EXTRA_AGENT_PRESET=true）：管理 agent_tool_preset
 */
class ToolConfigActivity : ComponentActivity() {

    companion object {
        const val EXTRA_AGENT_PRESET = "agent_preset"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isAgentPreset = intent.getBooleanExtra(EXTRA_AGENT_PRESET, false)
        
        setContent {
            ToolConfigScreen(
                isAgentPreset = isAgentPreset,
                onBack = { finish() },
                onSave = {
                    setResult(RESULT_OK)
                    finish()
                }
            )
        }
    }
}


@Composable
fun ToolConfigScreen(
    isAgentPreset: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    val config = AppConfig.configRepo()
    
    val allTools = remember { ToolCatalog.allTools.map { it.id to it.description } }
    
    // 加载工具状态
    val enabledTools = remember {
        if (isAgentPreset) {
            config.getAgentToolPreset()
        } else {
            config.getEnabledTools()
        }
    }
    
    val allEnabled = enabledTools.isEmpty()
    val toolStates = remember {
        mutableStateMapOf<String, Boolean>().apply {
            allTools.forEach { (id, _) ->
                this[id] = allEnabled || id in enabledTools
            }
        }
    }
    
    var brightDataKey by remember { mutableStateOf(config.getBrightDataKey()) }
    
    val allChecked = toolStates.values.all { it }
    
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
            TextButton(
                onClick = onBack,
                colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary)
            ) {
                Text("← 返回", fontSize = 14.sp)
            }
            
            Text(
                text = if (isAgentPreset) "Agent 工具预设" else "工具管理",
                fontSize = 17.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            
            TextButton(
                onClick = {
                    val selected = mutableSetOf<String>()
                    toolStates.forEach { (id, checked) ->
                        if (checked) selected.add(id)
                    }
                    val toSave = if (selected.size == allTools.size) emptySet() else selected
                    
                    if (isAgentPreset) {
                        config.setAgentToolPreset(toSave)
                    } else {
                        config.setEnabledTools(toSave)
                    }
                    config.setBrightDataKey(brightDataKey.trim())
                    
                    Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                    onSave()
                },
                colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary)
            ) {
                Text("保存", fontSize = 14.sp)
            }
        }
        
        HorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)
        
        // 滚动内容
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 全选/全不选
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable {
                        val newState = !allChecked
                        toolStates.keys.forEach { id ->
                            toolStates[id] = newState
                        }
                    },
                color = Color.White,
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "全部启用",
                        fontSize = 15.sp,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Checkbox(
                        checked = allChecked,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(
                            checkedColor = TextPrimary,
                            uncheckedColor = TextSecondary,
                            checkmarkColor = Color.White
                        )
                    )
                }
            }
            
            HorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)
            
            // 工具列表
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shape = MaterialTheme.shapes.medium
            ) {
                Column {
                    allTools.forEachIndexed { index, (id, description) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clickable {
                                    toolStates[id] = !(toolStates[id] ?: false)
                                }
                                .padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = id,
                                    fontSize = 14.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = description,
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                            
                            Checkbox(
                                checked = toolStates[id] ?: false,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = TextPrimary,
                                    uncheckedColor = TextSecondary,
                                    checkmarkColor = Color.White
                                )
                            )
                        }
                        
                        if (index < allTools.size - 1) {
                            HorizontalDivider(
                                color = Color(0xFFE0E0E0),
                                thickness = 1.dp,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                        }
                    }
                }
            }
            
            HorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
            
            // BrightData Key
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = "BrightData API Key",
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "网络搜索和网页抓取工具需要此 Key",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BgInput, MaterialTheme.shapes.small)
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        BasicTextField(
                            value = brightDataKey,
                            onValueChange = { brightDataKey = it },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(
                                fontSize = 14.sp,
                                color = TextPrimary
                            ),
                            cursorBrush = SolidColor(TextPrimary),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { innerTextField ->
                                if (brightDataKey.isEmpty()) {
                                    Text(
                                        "输入 BrightData SERP API Key",
                                        fontSize = 14.sp,
                                        color = TextSecondary
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }
            }
        }
    }
}
