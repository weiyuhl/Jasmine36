package com.lhzkml.jasmine

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import com.lhzkml.jasmine.ui.theme.BgInput
import com.lhzkml.jasmine.ui.theme.BgPrimary
import com.lhzkml.jasmine.ui.theme.TextPrimary
import com.lhzkml.jasmine.ui.theme.TextSecondary
import com.lhzkml.jasmine.ui.components.*

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
            CustomTextButton(
                onClick = onBack,
                colors = CustomButtonDefaults.textButtonColors(contentColor = TextPrimary)
            ) {
                CustomText("← 返回", fontSize = 14.sp)
            }
            
            CustomText(
                text = if (isAgentPreset) "Agent 工具预设" else "工具管理",
                fontSize = 17.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            
            CustomTextButton(
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
                colors = CustomButtonDefaults.textButtonColors(contentColor = TextPrimary)
            ) {
                CustomText("保存", fontSize = 14.sp)
            }
        }
        
        CustomHorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)
        
        // 滚动内容
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 全选/全不选
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .clickable {
                        val newState = !allChecked
                        toolStates.keys.forEach { id ->
                            toolStates[id] = newState
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CustomText(
                        text = "全部启用",
                        fontSize = 15.sp,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    
                    CustomCheckbox(
                        checked = allChecked,
                        onCheckedChange = { }
                    )
                }
            }
            
            CustomHorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)
            
            // 工具列表
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(8.dp))
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
                                CustomText(
                                    text = id,
                                    fontSize = 14.sp,
                                    color = TextPrimary
                                )
                                CustomText(
                                    text = description,
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                            
                            CustomCheckbox(
                                checked = toolStates[id] ?: false,
                                onCheckedChange = { }
                            )
                        }
                        
                        if (index < allTools.size - 1) {
                            CustomHorizontalDivider(
                                color = Color(0xFFE0E0E0),
                                thickness = 1.dp,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                        }
                    }
                }
            }
            
            CustomHorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
            
            // BrightData Key
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(8.dp))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    CustomText(
                        text = "BrightData API Key",
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                    CustomText(
                        text = "网络搜索和网页抓取工具需要此 Key",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BgInput, RoundedCornerShape(4.dp))
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        BasicTextField(
                            value = brightDataKey,
                            onValueChange = { brightDataKey = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 14.sp,
                                color = TextPrimary
                            ),
                            cursorBrush = SolidColor(TextPrimary),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { innerTextField ->
                                if (brightDataKey.isEmpty()) {
                                    CustomText(
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
