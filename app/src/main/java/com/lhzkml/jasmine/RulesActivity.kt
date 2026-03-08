package com.lhzkml.jasmine

import android.os.Bundle
import com.lhzkml.jasmine.config.AppConfig
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import com.lhzkml.jasmine.ui.theme.*
import com.lhzkml.jasmine.ui.components.*

class RulesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JasmineTheme {
                RulesScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun RulesScreen(onBack: () -> Unit) {
    val config = AppConfig.configRepo()

    var personalRules by remember { mutableStateOf(config.getPersonalRules()) }

    val wsPath = config.getWorkspacePath()
    val hasWorkspace = wsPath.isNotBlank()
    var projectRules by remember {
        mutableStateOf(if (hasWorkspace) config.getProjectRules(wsPath) else "")
    }

    DisposableEffect(Unit) {
        onDispose {
            config.setPersonalRules(personalRules.trim())
            if (hasWorkspace) {
                config.setProjectRules(wsPath, projectRules.trim())
            }
        }
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
                contentColor = TextPrimary
            ) {
                CustomText("<- 返回", fontSize = 14.sp, color = TextPrimary)
            }

            Spacer(modifier = Modifier.weight(1f))

            CustomText(
                text = "Rules 规则",
                fontSize = 17.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.weight(1f))
        }

        CustomHorizontalDivider(color = Color(0xFFE8E8E8), thickness = 1.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // ─── 个人 Rules ───
            CustomText(
                text = "个人 Rules",
                fontSize = 16.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            CustomText(
                text = "全局行为规则，切换项目后依然生效。",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )

            RulesTextField(
                value = personalRules,
                onValueChange = { personalRules = it },
                placeholder = "每行一条规则，例如：\nAlways respond in Chinese\n代码生成时添加注释"
            )

            // 清空按钮
            if (personalRules.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    CustomTextButton(onClick = { personalRules = "" }) {
                        CustomText("清空", fontSize = 13.sp, color = TextSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ─── 项目 Rules ───
            CustomText(
                text = "项目 Rules",
                fontSize = 16.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )

            if (hasWorkspace) {
                CustomText(
                    text = "当前项目: ${wsPath.substringAfterLast("/")}，仅在此工作区下生效。",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )

                RulesTextField(
                    value = projectRules,
                    onValueChange = { projectRules = it },
                    placeholder = "每行一条规则，例如：\n使用 Kotlin 协程而非 RxJava\n遵循 MVVM 架构模式"
                )

                if (projectRules.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        CustomTextButton(onClick = { projectRules = "" }) {
                            CustomText("清空", fontSize = 13.sp, color = TextSecondary)
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(12.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CustomText(
                        text = "未设置工作区",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                    CustomText(
                        text = "请先在 Agent 模式下设置工作区路径",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RulesTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp)
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                fontSize = 14.sp,
                color = TextPrimary,
                lineHeight = 22.sp
            ),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    CustomText(
                        text = placeholder,
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                }
                innerTextField()
            }
        )
    }
}
