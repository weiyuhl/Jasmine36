#!/bin/bash

echo "=== MNN 迁移验证脚本 ==="
echo ""

# 检查框架层文件
echo "1. 检查框架层文件..."
echo ""

echo "✓ 检查 build.gradle.kts"
[ -f "jasmine-core/prompt/prompt-mnn/build.gradle.kts" ] && echo "  ✅ build.gradle.kts 存在" || echo "  ❌ build.gradle.kts 缺失"

echo "✓ 检查 C++ 文件"
[ -f "jasmine-core/prompt/prompt-mnn/src/main/cpp/mnn_jni.cpp" ] && echo "  ✅ mnn_jni.cpp 存在" || echo "  ❌ mnn_jni.cpp 缺失"
[ -f "jasmine-core/prompt/prompt-mnn/src/main/cpp/CMakeLists.txt" ] && echo "  ✅ CMakeLists.txt 存在" || echo "  ❌ CMakeLists.txt 缺失"

echo "✓ 检查 MNN 库"
[ -f "jasmine-core/prompt/prompt-mnn/src/main/jniLibs/arm64-v8a/libMNN.so" ] && echo "  ✅ libMNN.so 存在" || echo "  ❌ libMNN.so 缺失"

echo "✓ 检查 Kotlin 文件"
[ -f "jasmine-core/prompt/prompt-mnn/src/main/java/com/lhzkml/jasmine/core/prompt/mnn/MnnBridge.kt" ] && echo "  ✅ MnnBridge.kt 存在" || echo "  ❌ MnnBridge.kt 缺失"
[ -f "jasmine-core/prompt/prompt-mnn/src/main/java/com/lhzkml/jasmine/core/prompt/mnn/MnnLlmSession.kt" ] && echo "  ✅ MnnLlmSession.kt 存在" || echo "  ❌ MnnLlmSession.kt 缺失"
[ -f "jasmine-core/prompt/prompt-mnn/src/main/java/com/lhzkml/jasmine/core/prompt/mnn/MnnChatClient.kt" ] && echo "  ✅ MnnChatClient.kt 存在" || echo "  ❌ MnnChatClient.kt 缺失"
[ -f "jasmine-core/prompt/prompt-mnn/src/main/java/com/lhzkml/jasmine/core/prompt/mnn/MnnModelManager.kt" ] && echo "  ✅ MnnModelManager.kt 存在" || echo "  ❌ MnnModelManager.kt 缺失"

echo ""
echo "2. 检查配置文件..."
echo ""

echo "✓ 检查 settings.gradle.kts"
grep -q "prompt-mnn" settings.gradle.kts && echo "  ✅ prompt-mnn 已添加到 settings.gradle.kts" || echo "  ❌ prompt-mnn 未添加到 settings.gradle.kts"

echo "✓ 检查 app/build.gradle.kts"
grep -q "prompt-mnn" app/build.gradle.kts && echo "  ✅ prompt-mnn 依赖已添加到 app/build.gradle.kts" || echo "  ❌ prompt-mnn 依赖未添加到 app/build.gradle.kts"

echo ""
echo "3. 文件统计..."
echo ""

KOTLIN_FILES=$(find jasmine-core/prompt/prompt-mnn/src/main/java -name "*.kt" | wc -l)
echo "  Kotlin 文件数: $KOTLIN_FILES"

CPP_FILES=$(find jasmine-core/prompt/prompt-mnn/src/main/cpp -name "*.cpp" -o -name "*.h" | wc -l)
echo "  C++ 文件数: $CPP_FILES"

SO_FILES=$(find jasmine-core/prompt/prompt-mnn/src/main/jniLibs -name "*.so" | wc -l)
echo "  SO 库文件数: $SO_FILES"

echo ""
echo "=== 验证完成 ==="
