cmake_minimum_required(VERSION 3.22.1)
project("jasmine_mnn")

# MNN 路径（头文件在 cpp/third_party，库在 jniLibs）
set(MNN_INCLUDE_DIR "${CMAKE_SOURCE_DIR}/third_party")
set(MNN_LLM_INCLUDE_DIR "${CMAKE_SOURCE_DIR}/third_party/transformers/llm/engine/include")

# 包含头文件
include_directories(${MNN_INCLUDE_DIR})
include_directories(${MNN_LLM_INCLUDE_DIR})

# 导入预编译的 MNN 库
add_library(MNN SHARED IMPORTED)
set_target_properties(MNN PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libMNN.so)

# 创建 JNI 库
add_library(${CMAKE_PROJECT_NAME} SHARED
    mnn_jni.cpp
)

# 链接库
target_link_libraries(${CMAKE_PROJECT_NAME}
    android
    log
    MNN
)
