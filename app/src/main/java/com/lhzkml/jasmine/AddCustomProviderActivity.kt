package com.lhzkml.jasmine

import com.lhzkml.jasmine.config.AppConfig
import android.app.Activity
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.config.ProviderConfig
import com.lhzkml.jasmine.core.prompt.executor.ApiType
import com.lhzkml.jasmine.ui.components.*
import com.lhzkml.jasmine.ui.theme.*

class AddCustomProviderActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AddCustomProviderScreen(onBack = { finish() })
        }
    }
}

@Composable
fun AddCustomProviderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val config = AppConfig.configRepo()
    val registry = AppConfig.providerRegistry()

    var selectedApiType by remember { mutableStateOf(ApiType.OPENAI) }
    var providerId by remember { mutableStateOf("") }
    var providerName by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }

    fun addProvider() {
        focusManager.clearFocus()
        when {
            providerId.isEmpty() -> {
                Toast.makeText(context, "请输入供应商 ID", Toast.LENGTH_SHORT).show()
            }
            providerName.isEmpty() -> {
                Toast.makeText(context, "请输入供应商名称", Toast.LENGTH_SHORT).show()
            }
            baseUrl.isEmpty() -> {
                Toast.makeText(context, "请输入 API 地址", Toast.LENGTH_SHORT).show()
            }
            model.isEmpty() -> {
                Toast.makeText(context, "请输入默认模型", Toast.LENGTH_SHORT).show()
            }
            else -> {
                val provider = ProviderConfig(
                    id = providerId.trim(),
                    name = providerName.trim(),
                    defaultBaseUrl = baseUrl.trim(),
                    defaultModel = model.trim(),
                    apiType = selectedApiType,
                    isCustom = true
                )
                val success = registry.registerProviderPersistent(provider)
                if (success) {
                    config.saveProviderCredentials(provider.id, "", baseUrl.trim(), model.trim())
                    Toast.makeText(context, "添加成功", Toast.LENGTH_SHORT).show()
                    (context as? ComponentActivity)?.setResult(Activity.RESULT_OK)
                    (context as? ComponentActivity)?.finish()
                } else {
                    Toast.makeText(context, "供应商 ID 已存在", Toast.LENGTH_SHORT).show()
                }
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
                colors = CustomButtonDefaults.textButtonColors(contentColor = TextPrimary)
            ) {
                CustomText("← 返回", fontSize = 14.sp)
            }
            CustomText(
                text = "添加自定义供应商",
                fontSize = 17.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(56.dp))
        }

        CustomHorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)

        // 表单内容
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // API 渠道类型
            Column {
                CustomText("API 渠道类型", fontSize = 14.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))

                var expanded by remember { mutableStateOf(false) }
                val apiTypes = listOf(
                    "OpenAI 兼容" to ApiType.OPENAI,
                    "Claude" to ApiType.CLAUDE,
                    "Gemini" to ApiType.GEMINI,
                )

                Box {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = true }
                            .background(Color.Transparent, RoundedCornerShape(4.dp))
                            .then(Modifier.padding(1.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CustomText(
                                text = apiTypes.find { it.second == selectedApiType }?.first ?: "OpenAI 兼容",
                                color = TextPrimary,
                                fontSize = 14.sp
                            )
                            CustomText(text = "▼", fontSize = 12.sp, color = TextPrimary)
                        }
                    }

                    CustomDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        apiTypes.forEach { (label, type) ->
                            CustomDropdownMenuItem(
                                text = { CustomText(label, fontSize = 14.sp, color = TextPrimary) },
                                onClick = {
                                    selectedApiType = type
                                    if (baseUrl.isEmpty()) {
                                        baseUrl = when (type) {
                                            ApiType.OPENAI -> ""
                                            ApiType.CLAUDE -> "https://api.anthropic.com"
                                            ApiType.GEMINI -> "https://generativelanguage.googleapis.com"
                                            else -> ""
                                        }
                                    }
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            // 供应商 ID
            Column {
                CustomText("供应商 ID", fontSize = 14.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                CustomOutlinedTextField(
                    value = providerId,
                    onValueChange = { providerId = it },
                    placeholder = { CustomText("例如: openai", color = TextSecondary, fontSize = 14.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    focusedBorderColor = TextPrimary,
                    unfocusedBorderColor = TextSecondary
                )
            }

            // 供应商名称
            Column {
                CustomText("供应商名称", fontSize = 14.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                CustomOutlinedTextField(
                    value = providerName,
                    onValueChange = { providerName = it },
                    placeholder = { CustomText("例如: OpenAI", color = TextSecondary, fontSize = 14.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    focusedBorderColor = TextPrimary,
                    unfocusedBorderColor = TextSecondary
                )
            }

            // API 地址
            Column {
                CustomText("API 地址", fontSize = 14.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                CustomOutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    placeholder = { CustomText("例如: https://api.openai.com", color = TextSecondary, fontSize = 14.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    focusedBorderColor = TextPrimary,
                    unfocusedBorderColor = TextSecondary
                )
            }

            // 默认模型
            Column {
                CustomText("默认模型", fontSize = 14.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                CustomOutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    placeholder = { CustomText("例如: gpt-4", color = TextSecondary, fontSize = 14.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    focusedBorderColor = TextPrimary,
                    unfocusedBorderColor = TextSecondary
                )
            }
        }

        // 底部添加按钮
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
        ) {
            CustomButton(
                onClick = { addProvider() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(48.dp),
                colors = CustomButtonDefaults.buttonColors(
                    containerColor = TextPrimary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                CustomText("添加", fontSize = 15.sp)
            }
        }
    }
}
