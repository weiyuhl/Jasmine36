package com.lhzkml.jasmine

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.mnn.MnnBridge

class TestMnnActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "TestMnnActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TestMnnScreen()
                }
            }
        }
    }
}

@Composable
fun TestMnnScreen() {
    var testResult by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "MNN 测试",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = {
                isLoading = true
                testResult = runMnnTest()
                isLoading = false
            },
            enabled = !isLoading
        ) {
            Text("运行测试")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (isLoading) {
            CircularProgressIndicator()
        }
        
        if (testResult.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = testResult,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private fun runMnnTest(): String {
    val result = StringBuilder()
    
    try {
        // 测试 1: 检查 MNN 是否可用
        result.append("1. MNN 可用性: ")
        if (MnnBridge.isAvailable()) {
            result.append("✅ 成功\n")
        } else {
            result.append("❌ 失败\n")
            return result.toString()
        }
        
        // 测试 2: 获取版本
        result.append("2. MNN 版本: ")
        try {
            val version = MnnBridge.getMnnVersion()
            result.append("$version ✅\n")
        } catch (e: Exception) {
            result.append("❌ ${e.message}\n")
        }
        
        // 测试 3: 测试初始化
        result.append("3. MNN 初始化: ")
        try {
            val initResult = MnnBridge.testMnnInit()
            if (initResult) {
                result.append("✅ 成功\n")
            } else {
                result.append("❌ 失败\n")
            }
        } catch (e: Exception) {
            result.append("❌ ${e.message}\n")
        }
        
        // 测试 4: LLM 会话测试（需要模型文件）
        result.append("4. LLM 会话: ")
        result.append("⏭️ 跳过（需要模型文件）\n")
        // 如果有模型文件，可以这样测试：
        // val session = MnnLlmSession("/path/to/model")
        // if (session.init()) {
        //     val response = session.generate("Hello")
        //     result.append("✅ 成功: $response\n")
        //     session.release()
        // }
        
        result.append("\n所有测试完成！")
        
    } catch (e: Exception) {
        result.append("\n\n错误: ${e.message}")
        Log.e("TestMnnActivity", "Test failed", e)
    }
    
    return result.toString()
}
