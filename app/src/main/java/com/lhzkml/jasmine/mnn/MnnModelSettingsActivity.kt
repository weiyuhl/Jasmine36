package com.lhzkml.jasmine.mnn

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.ui.components.*
import com.lhzkml.jasmine.ui.theme.BgPrimary
import com.lhzkml.jasmine.ui.theme.BgInput
import com.lhzkml.jasmine.ui.theme.JasmineTheme
import com.lhzkml.jasmine.ui.theme.TextPrimary
import com.lhzkml.jasmine.ui.theme.TextSecondary

class MnnModelSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val modelId = intent.getStringExtra("modelId") ?: ""
        setContent {
            JasmineTheme {
                MnnModelSettingsScreen(modelId = modelId, onBack = { finish() })
            }
        }
    }
}

@Composable
fun MnnModelSettingsScreen(modelId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val isGlobal = modelId == "__global_defaults__"
    val screenTitle = if (isGlobal) "默认推理设置" else "模型设置"
    var config by remember {
        mutableStateOf(MnnModelManager.getModelConfig(context, modelId) ?: MnnModelManager.defaultGlobalConfig())
    }
    var showResetDialog by remember { mutableStateOf(false) }

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
            CustomTextButton(onClick = onBack, contentColor = TextPrimary) {
                CustomText("← 返回", fontSize = 14.sp, color = TextPrimary)
            }
            CustomText(
                text = screenTitle,
                fontSize = 17.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            CustomTextButton(
                onClick = {
                    if (MnnModelManager.saveModelConfig(context, modelId, config)) {
                        Toast.makeText(context, "保存成功", Toast.LENGTH_SHORT).show()
                        onBack()
                    } else {
                        Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                    }
                },
                contentColor = TextPrimary
            ) {
                CustomText("保存", fontSize = 14.sp, color = TextPrimary)
            }
        }
        CustomHorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 基础设置
            MnnSectionHeader("基础设置")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    MnnDropdownSetting(
                        label = "后端类型",
                        description = "cpu：兼容性最好；opencl/vulkan：利用GPU加速，部分设备不支持",
                        value = config.backendType ?: "cpu",
                        options = listOf("cpu", "opencl", "vulkan"),
                        onValueChange = { config = config.copy(backendType = it) }
                    )
                    MnnDropdownSetting(
                        label = "精度",
                        description = "low：速度快、占用少，精度略低；high：精度高，速度较慢",
                        value = config.precision ?: "low",
                        options = listOf("low", "high"),
                        onValueChange = { config = config.copy(precision = it) }
                    )
                    MnnSliderSetting(
                        label = "线程数",
                        description = "推理使用的CPU线程数，一般设为CPU大核数量（4-6），过多反而会变慢",
                        value = config.threadNum?.toFloat() ?: 4f,
                        valueRange = 1f..8f,
                        steps = 6,
                        format = { it.toInt().toString() },
                        onValueChange = { config = config.copy(threadNum = it.toInt()) }
                    )
                    MnnDropdownSetting(
                        label = "内存模式",
                        description = "low：节省内存，适合小内存设备；high：占用更多内存但速度更快",
                        value = config.memory ?: "low",
                        options = listOf("low", "normal", "high"),
                        onValueChange = { config = config.copy(memory = it) }
                    )
                    MnnSwitchSetting(
                        label = "使用 mmap",
                        subtitle = "开启后通过内存映射加载模型，减少内存占用，首次加载可能略慢",
                        checked = config.useMmap ?: false,
                        onCheckedChange = { config = config.copy(useMmap = it) }
                    )
                }
            }

            // 采样器
            MnnSectionHeader("采样器")
            CustomText(
                "采样器控制模型生成文本的随机性和多样性",
                fontSize = 11.sp, color = TextSecondary,
                modifier = Modifier.padding(start = 4.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    MnnDropdownSetting(
                        label = "采样类型",
                        description = "greedy：始终选最高概率词，输出最确定；penalty：在greedy基础上惩罚重复；mixed：组合多种策略，效果最灵活",
                        value = config.samplerType ?: "mixed",
                        options = listOf("greedy", "penalty", "mixed"),
                        onValueChange = { config = config.copy(samplerType = it) }
                    )

                    val samplerType = config.samplerType ?: "mixed"
                    if (samplerType != "greedy") {
                        MnnSliderSetting(
                            label = "Temperature",
                            description = "控制输出随机性：值越低越确定、越高越有创意。0=贪心，1=标准，>1=更随机",
                            value = config.temperature ?: 0.6f,
                            valueRange = 0f..2f,
                            format = { String.format("%.2f", it) },
                            onValueChange = { config = config.copy(temperature = it) }
                        )
                    }

                    if (samplerType == "mixed") {
                        MnnSliderSetting(
                            label = "Top P（核采样）",
                            description = "从累积概率达到此值的最小词集中采样。0.9=保留90%概率的词，值越小输出越集中",
                            value = config.topP ?: 0.95f,
                            valueRange = 0f..1f,
                            format = { String.format("%.2f", it) },
                            onValueChange = { config = config.copy(topP = it) }
                        )
                        MnnSliderSetting(
                            label = "Top K",
                            description = "只从概率最高的K个词中选择。值越小越保守（如5），值越大越多样（如100）",
                            value = config.topK?.toFloat() ?: 20f,
                            valueRange = 1f..100f,
                            steps = 98,
                            format = { it.toInt().toString() },
                            onValueChange = { config = config.copy(topK = it.toInt()) }
                        )
                        MnnSliderSetting(
                            label = "Min P",
                            description = "过滤掉概率低于最高概率×此值的词。避免生成极低概率的奇怪内容",
                            value = config.minP ?: 0.05f,
                            valueRange = 0f..1f,
                            format = { String.format("%.2f", it) },
                            onValueChange = { config = config.copy(minP = it) }
                        )
                        MnnSliderSetting(
                            label = "TFS-Z（尾部自由采样）",
                            description = "通过分析概率分布的\"尾部\"来去除低质量词。1.0=不过滤，越小过滤越激进",
                            value = config.tfsZ ?: 1.0f,
                            valueRange = 0f..1f,
                            format = { String.format("%.2f", it) },
                            onValueChange = { config = config.copy(tfsZ = it) }
                        )
                        MnnSliderSetting(
                            label = "Typical（典型采样）",
                            description = "选择\"信息量\"接近预期的词，过滤过于意外或过于无聊的内容。1.0=不过滤",
                            value = config.typical ?: 0.95f,
                            valueRange = 0f..1f,
                            format = { String.format("%.2f", it) },
                            onValueChange = { config = config.copy(typical = it) }
                        )
                    }

                    if (samplerType == "penalty" || samplerType == "mixed") {
                        MnnSliderSetting(
                            label = "重复惩罚",
                            description = "对已出现过的词施加惩罚，减少重复。1.0=不惩罚，值越大惩罚越强",
                            value = config.penalty ?: 1.02f,
                            valueRange = 0.5f..2f,
                            format = { String.format("%.2f", it) },
                            onValueChange = { config = config.copy(penalty = it) }
                        )
                        MnnSliderSetting(
                            label = "N-gram 大小",
                            description = "检测重复时的窗口大小。越大会检查越长的重复片段",
                            value = config.nGram?.toFloat() ?: 8f,
                            valueRange = 1f..32f,
                            steps = 30,
                            format = { it.toInt().toString() },
                            onValueChange = { config = config.copy(nGram = it.toInt()) }
                        )
                        MnnSliderSetting(
                            label = "N-gram 因子",
                            description = "控制N-gram重复惩罚的强度系数",
                            value = config.nGramFactor ?: 1.02f,
                            valueRange = 0.5f..2f,
                            format = { String.format("%.2f", it) },
                            onValueChange = { config = config.copy(nGramFactor = it) }
                        )
                    }
                }
            }

            // 生成设置
            MnnSectionHeader("生成设置")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    MnnSliderSetting(
                        label = "最大生成长度",
                        description = "单次回复最多生成的Token数量。越大可生成越长的回复，但耗时也越长",
                        value = config.maxNewTokens?.toFloat() ?: 2048f,
                        valueRange = 128f..8192f,
                        steps = 62,
                        format = { it.toInt().toString() },
                        onValueChange = { config = config.copy(maxNewTokens = it.toInt()) }
                    )
                }
            }

            // 重置按钮
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .clickable { showResetDialog = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CustomText(
                        text = "恢复默认设置",
                        fontSize = 15.sp,
                        color = Color(0xFFF44336),
                        modifier = Modifier.weight(1f)
                    )
                    CustomText(text = "›", fontSize = 18.sp, color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showResetDialog) {
        CustomAlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                CustomText("恢复默认", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                CustomText("确定要恢复所有设置为默认值吗？", fontSize = 14.sp, color = TextPrimary)
            },
            confirmButton = {
                CustomTextButton(
                    onClick = {
                        config = MnnModelManager.defaultGlobalConfig()
                        showResetDialog = false
                        Toast.makeText(context, "已恢复默认设置", Toast.LENGTH_SHORT).show()
                    },
                    contentColor = Color(0xFFF44336)
                ) {
                    CustomText("恢复", fontSize = 14.sp, color = Color(0xFFF44336))
                }
            },
            dismissButton = {
                CustomTextButton(
                    onClick = { showResetDialog = false },
                    contentColor = TextSecondary
                ) {
                    CustomText("取消", fontSize = 14.sp, color = TextSecondary)
                }
            }
        )
    }
}

// 分节标题
@Composable
fun MnnSectionHeader(title: String) {
    CustomText(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = TextSecondary,
        modifier = Modifier.padding(start = 4.dp, bottom = 0.dp)
    )
}

@Composable
fun MnnDropdownSetting(
    label: String,
    description: String? = null,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            CustomText(text = label, fontSize = 14.sp, color = TextPrimary)
            Box {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(BgInput)
                        .clickable { expanded = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CustomText(text = value, fontSize = 13.sp, color = TextPrimary)
                        Spacer(modifier = Modifier.width(4.dp))
                        CustomText(text = "▼", fontSize = 9.sp, color = TextSecondary)
                    }
                }
                CustomDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    options.forEach { option ->
                        CustomDropdownMenuItem(
                            text = {
                                CustomText(
                                    text = option,
                                    fontSize = 14.sp,
                                    color = if (option == value) TextPrimary else TextSecondary
                                )
                            },
                            onClick = {
                                onValueChange(option)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
        if (description != null) {
            Spacer(modifier = Modifier.height(4.dp))
            CustomText(text = description, fontSize = 11.sp, color = TextSecondary)
        }
    }
}

@Composable
fun MnnSliderSetting(
    label: String,
    description: String? = null,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    format: (Float) -> String = { String.format("%.2f", it) },
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CustomText(text = label, fontSize = 14.sp, color = TextPrimary)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(BgInput)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                CustomText(
                    text = format(value),
                    fontSize = 13.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        if (description != null) {
            Spacer(modifier = Modifier.height(2.dp))
            CustomText(text = description, fontSize = 11.sp, color = TextSecondary)
        }
        Spacer(modifier = Modifier.height(4.dp))
        CustomSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}

@Composable
private fun CustomSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    activeColor: Color = TextPrimary,
    inactiveColor: Color = Color(0xFFE0E0E0),
    thumbColor: Color = TextPrimary
) {
    val density = LocalDensity.current
    var sliderSize by remember { mutableStateOf(IntSize.Zero) }
    val thumbRadius = with(density) { 8.dp.toPx() }

    val rangeSize = valueRange.endInclusive - valueRange.start
    val fraction = if (rangeSize > 0f) ((value - valueRange.start) / rangeSize).coerceIn(0f, 1f) else 0f

    fun snapToStep(raw: Float): Float {
        if (steps <= 0) return raw
        val totalSteps = steps + 1
        val stepFraction = 1f / totalSteps
        val snapped = (raw / stepFraction).let { kotlin.math.round(it) } * stepFraction
        return snapped.coerceIn(0f, 1f)
    }

    fun fractionToValue(f: Float): Float {
        return (valueRange.start + f * rangeSize).coerceIn(valueRange.start, valueRange.endInclusive)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .onSizeChanged { sliderSize = it }
            .pointerInput(valueRange, steps) {
                detectTapGestures { offset ->
                    val trackWidth = sliderSize.width - 2 * thumbRadius
                    if (trackWidth > 0) {
                        val rawFraction = ((offset.x - thumbRadius) / trackWidth).coerceIn(0f, 1f)
                        val snapped = snapToStep(rawFraction)
                        onValueChange(fractionToValue(snapped))
                    }
                }
            }
            .pointerInput(valueRange, steps) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val trackWidth = sliderSize.width - 2 * thumbRadius
                    if (trackWidth > 0) {
                        val rawFraction = ((change.position.x - thumbRadius) / trackWidth).coerceIn(0f, 1f)
                        val snapped = snapToStep(rawFraction)
                        onValueChange(fractionToValue(snapped))
                    }
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidth = sliderSize.width.toFloat() - 2 * thumbRadius
        val thumbOffset = with(density) { (thumbRadius + fraction * trackWidth).toDp() }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(inactiveColor)
        )
        if (trackWidth > 0) {
            Box(
                modifier = Modifier
                    .width(with(density) { (thumbRadius + fraction * trackWidth).toDp() })
                    .height(4.dp)
                    .padding(start = 8.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(activeColor)
            )
        }
        Box(
            modifier = Modifier
                .offset(x = thumbOffset - 8.dp)
                .size(16.dp)
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
}

// 开关设置
@Composable
fun MnnSwitchSetting(
    label: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            CustomText(text = label, fontSize = 14.sp, color = TextPrimary)
            if (subtitle != null) {
                CustomText(text = subtitle, fontSize = 11.sp, color = TextSecondary)
            }
        }
        CustomSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
