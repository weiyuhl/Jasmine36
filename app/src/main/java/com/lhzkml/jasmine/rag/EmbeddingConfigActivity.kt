package com.lhzkml.jasmine.rag

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.ui.theme.JasmineTheme
import com.lhzkml.jasmine.ProviderManager
import com.lhzkml.jasmine.ui.components.CustomHorizontalDivider
import com.lhzkml.jasmine.ui.components.CustomText
import com.lhzkml.jasmine.ui.components.CustomTextButton
import com.lhzkml.jasmine.ui.theme.*

class EmbeddingConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JasmineTheme {
                EmbeddingConfigScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun EmbeddingConfigScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var baseUrl by remember { mutableStateOf(ProviderManager.getRagEmbeddingBaseUrl(context)) }
    var apiKey by remember { mutableStateOf(ProviderManager.getRagEmbeddingApiKey(context)) }
    var model by remember { mutableStateOf(ProviderManager.getRagEmbeddingModel(context)) }

    DisposableEffect(Unit) {
        onDispose {
            ProviderManager.setRagEmbeddingBaseUrl(context, baseUrl.trim())
            ProviderManager.setRagEmbeddingApiKey(context, apiKey)
            ProviderManager.setRagEmbeddingModel(context, model.trim().ifBlank { "text-embedding-3-small" })
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
            CustomTextButton(onClick = onBack, contentColor = TextPrimary, contentPadding = PaddingValues(6.dp)) {
                CustomText("<- 返回", fontSize = 14.sp, color = TextPrimary)
            }
            CustomText(
                text = "Embedding 服务",
                fontSize = 17.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(56.dp))
        }

        CustomHorizontalDivider(color = Color(0xFFE8E8E8), thickness = 1.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                CustomText(
                    text = "使用 OpenAI 兼容的 /v1/embeddings 接口，供 RAG 知识库向量化使用",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                CustomText(text = "API 地址", fontSize = 14.sp, color = TextPrimary)
                CustomText(text = "例如 https://api.openai.com", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
                RagTextField(value = baseUrl, onValueChange = { baseUrl = it }, placeholder = "https://api.openai.com")

                Spacer(modifier = Modifier.height(16.dp))

                CustomText(text = "API Key", fontSize = 14.sp, color = TextPrimary)
                CustomText(text = "Bearer 认证", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
                RagTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    placeholder = "sk-...",
                    keyboardType = KeyboardType.Password
                )

                Spacer(modifier = Modifier.height(16.dp))

                CustomText(text = "模型名称", fontSize = 14.sp, color = TextPrimary)
                CustomText(text = "如 text-embedding-3-small（384 维）", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
                RagTextField(value = model, onValueChange = { model = it }, placeholder = "text-embedding-3-small")
            }
        }
    }
}

@Composable
private fun RagTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Uri
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = TextStyle(fontSize = 14.sp, color = TextPrimary),
            cursorBrush = SolidColor(TextPrimary),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    CustomText(placeholder, fontSize = 14.sp, color = TextSecondary)
                }
                innerTextField()
            }
        )
    }
}
