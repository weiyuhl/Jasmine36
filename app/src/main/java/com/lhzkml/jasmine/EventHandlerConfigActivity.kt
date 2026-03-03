package com.lhzkml.jasmine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.agent.observe.event.EventCategory
import com.lhzkml.jasmine.ui.theme.*
import com.lhzkml.jasmine.ui.components.*

class EventHandlerConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JasmineTheme {
                EventHandlerConfigScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
fun EventHandlerConfigScreen(onBack: () -> Unit) {
    val config = AppConfig.configRepo()
    var enabled by remember { mutableStateOf(config.isEventHandlerEnabled()) }
    
    val eventCategories = remember {
        listOf(
            EventCategory.AGENT to "Agent 生命周期",
            EventCategory.TOOL to "工具调用",
            EventCategory.LLM to "LLM 调用",
            EventCategory.STRATEGY to "策略执行",
            EventCategory.NODE to "节点执行",
            EventCategory.SUBGRAPH to "子图执行",
            EventCategory.STREAMING to "LLM 流式"
        )
    }
    
    val currentFilter = config.getEventHandlerFilter()
    val checkedStates = remember {
        mutableStateMapOf<EventCategory, Boolean>().apply {
            eventCategories.forEach { (category, _) ->
                this[category] = currentFilter.isEmpty() || category in currentFilter
            }
        }
    }
    
    var summaryText by remember { mutableStateOf("") }
    
    fun saveFilterAndRefresh() {
        val checked = mutableSetOf<EventCategory>()
        checkedStates.forEach { (category, isChecked) ->
            if (isChecked) checked.add(category)
        }
        val selected = if (checked.size == eventCategories.size || checked.isEmpty()) {
            emptySet()
        } else {
            checked
        }
        config.setEventHandlerFilter(selected)
        
        val filter = config.getEventHandlerFilter()
        summaryText = if (filter.isEmpty()) "监听全部事件" else "监听 ${filter.size} 类事件"
    }
    
    LaunchedEffect(Unit) {
        saveFilterAndRefresh()
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
                onClick = onBack,
                contentColor = TextPrimary,
                contentPadding = PaddingValues(6.dp)
            ) {
                CustomText("<- 返回", fontSize = 14.sp, color = TextPrimary)
            }
            
            CustomText(
                text = "事件处理器配置",
                fontSize = 17.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.width(56.dp))
        }

        CustomHorizontalDivider(color = Color(0xFFE8E8E8), thickness = 1.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // 启用开关
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    CustomText(
                        text = "启用事件处理器",
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                    CustomText(
                        text = "Agent 生命周期事件回调",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                
                CustomSwitch(
                    checked = enabled,
                    onCheckedChange = { 
                        enabled = it
                        config.setEventHandlerEnabled(it)
                    },
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Accent,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFFE0E0E0)
                )
            }

            if (enabled) {
                Spacer(modifier = Modifier.height(16.dp))
                
                CustomText(
                    text = "事件处理器负责在聊天界面实时显示 Agent 执行过程（[EVENT] 标签）。\n选择需要在 UI 中显示的事件类型，不选则显示全部。",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    eventCategories.forEachIndexed { index, (category, label) ->
                        if (index > 0) {
                            CustomHorizontalDivider(
                                color = Color(0xFFE8E8E8),
                                thickness = 1.dp
                            )
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CustomText(
                                text = label,
                                fontSize = 14.sp,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            
                            CustomSwitch(
                                checked = checkedStates[category] ?: false,
                                onCheckedChange = { checked ->
                                    checkedStates[category] = checked
                                    saveFilterAndRefresh()
                                },
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Accent,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFFE0E0E0)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 当前配置摘要
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    CustomText(
                        text = "当前配置",
                        fontSize = 15.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    
                    CustomText(
                        text = summaryText,
                        fontSize = 13.sp,
                        color = TextSecondary,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}
