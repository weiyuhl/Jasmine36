package com.lhzkml.jasmine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import com.lhzkml.jasmine.core.conversation.storage.ConversationRepository
import com.lhzkml.jasmine.ui.theme.*
import com.lhzkml.jasmine.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TokenManagementActivity : ComponentActivity() {

    private lateinit var conversationRepo: ConversationRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        conversationRepo = ConversationRepository(this)
        
        setContent {
            JasmineTheme {
                TokenManagementScreen(
                    conversationRepo = conversationRepo,
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
fun TokenManagementScreen(
    conversationRepo: ConversationRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    var maxTokens by remember { 
        val value = ProviderManager.getMaxTokens(context)
        mutableStateOf(if (value > 0) value.toString() else "")
    }
    
    DisposableEffect(Unit) {
        onDispose {
            val tokens = maxTokens.trim().toIntOrNull() ?: 0
            ProviderManager.setMaxTokens(context, tokens)
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
                contentColor = TextPrimary,
                contentPadding = PaddingValues(6.dp)
            ) {
                CustomText("<- 返回", fontSize = 14.sp, color = TextPrimary)
            }
            
            CustomText(
                text = "Token 管理",
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
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 最大回复 Token 设置
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                CustomText(
                    text = "最大回复 Token 数",
                    fontSize = 15.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                
                CustomText(
                    text = "设置每条 AI 回复的最大 token 数量，0 或留空表示不限制",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                
                CustomText(
                    text = "常用值：512、1024、2048、4096",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                CustomText(
                    text = "最大 Token 数",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                
                MaxTokensInputField(
                    value = maxTokens,
                    onValueChange = { maxTokens = it },
                    placeholder = "0"
                )
            }
            
            // Token 用量统计
            TokenUsageCard(conversationRepo)
        }
    }
}

@Composable
fun MaxTokensInputField(
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
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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


@Composable
fun TokenUsageCard(conversationRepo: ConversationRepository) {
    var promptTokens by remember { mutableStateOf(0) }
    var completionTokens by remember { mutableStateOf(0) }
    var totalTokens by remember { mutableStateOf(0) }
    
    LaunchedEffect(Unit) {
        val stats = withContext(Dispatchers.IO) {
            conversationRepo.getTotalUsage()
        }
        promptTokens = stats.promptTokens
        completionTokens = stats.completionTokens
        totalTokens = stats.totalTokens
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            CustomText(
                text = "Token 用量统计",
                fontSize = 15.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    CustomText(
                        text = formatNumber(promptTokens),
                        fontSize = 18.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    CustomText(
                        text = "提示",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    CustomText(
                        text = formatNumber(completionTokens),
                        fontSize = 18.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    CustomText(
                        text = "回复",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    CustomText(
                        text = formatNumber(totalTokens),
                        fontSize = 18.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    CustomText(
                        text = "总计",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

private fun formatNumber(n: Int): String {
    return when {
        n >= 1_000_000 -> String.format("%.1fM tokens", n / 1_000_000.0)
        n >= 1_000 -> String.format("%.1fK tokens", n / 1_000.0)
        else -> "$n tokens"
    }
}
