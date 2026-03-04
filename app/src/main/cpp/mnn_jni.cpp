#include <jni.h>
#include <android/log.h>
#include <string>
#include <memory>
#include <sstream>
#include <MNN/Interpreter.hpp>
#include <MNN/MNNDefine.h>
#include <MNN/MNNForwardType.h>
#include <MNN/expr/ExecutorScope.hpp>
#include <llm/llm.hpp>

#define LOG_TAG "JasmineMNN"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace MNN;
using namespace MNN::Transformer;

// UTF-8 流处理器 - 处理流式输出的 UTF-8 字符
class Utf8StreamProcessor {
private:
    std::function<void(const std::string&)> callback_;
    std::string buffer_;
    
public:
    explicit Utf8StreamProcessor(std::function<void(const std::string&)> callback)
        : callback_(std::move(callback)) {}
    
    void processStream(const char* data, size_t len) {
        buffer_.append(data, len);
        
        size_t pos = 0;
        while (pos < buffer_.size()) {
            unsigned char c = buffer_[pos];
            size_t charLen = 1;
            
            // 判断 UTF-8 字符长度
            if ((c & 0x80) == 0) {
                charLen = 1;
            } else if ((c & 0xE0) == 0xC0) {
                charLen = 2;
            } else if ((c & 0xF0) == 0xE0) {
                charLen = 3;
            } else if ((c & 0xF8) == 0xF0) {
                charLen = 4;
            }
            
            // 检查是否有完整字符
            if (pos + charLen > buffer_.size()) {
                break;
            }
            
            // 提取完整字符并回调
            std::string utf8Char = buffer_.substr(pos, charLen);
            if (callback_) {
                callback_(utf8Char);
            }
            pos += charLen;
        }
        
        // 移除已处理的数据
        if (pos > 0) {
            buffer_.erase(0, pos);
        }
    }
};

// 流缓冲区 - 用于 std::ostream
class LlmStreamBuffer : public std::streambuf {
private:
    std::function<void(const char*, size_t)> callback_;
    
public:
    explicit LlmStreamBuffer(std::function<void(const char*, size_t)> callback)
        : callback_(std::move(callback)) {}
    
protected:
    std::streamsize xsputn(const char* s, std::streamsize n) override {
        if (callback_) {
            callback_(s, n);
        }
        return n;
    }
    
    int overflow(int c) override {
        if (c != EOF && callback_) {
            char ch = static_cast<char>(c);
            callback_(&ch, 1);
        }
        return c;
    }
};

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    LOGD("MNN JNI loaded");
    return JNI_VERSION_1_6;
}

JNIEXPORT jstring JNICALL
Java_com_lhzkml_jasmine_mnn_MnnBridge_getMnnVersion(JNIEnv *env, jclass clazz) {
    LOGD("Getting MNN version");
    return env->NewStringUTF(MNN_VERSION);
}

JNIEXPORT jboolean JNICALL
Java_com_lhzkml_jasmine_mnn_MnnBridge_testMnnInit(JNIEnv *env, jclass clazz) {
    LOGD("Testing MNN initialization");
    try {
        // 测试创建 Interpreter
        auto interpreter = std::shared_ptr<Interpreter>(Interpreter::createFromBuffer(nullptr, 0));
        LOGD("MNN library linked successfully");
        return JNI_TRUE;
    } catch (const std::exception& e) {
        LOGE("MNN test failed: %s", e.what());
        return JNI_FALSE;
    }
}

JNIEXPORT jlong JNICALL
Java_com_lhzkml_jasmine_mnn_MnnLlmSession_nativeInit(
    JNIEnv *env, 
    jobject thiz,
    jstring modelPath,
    jstring configJson
) {
    const char *model_path = env->GetStringUTFChars(modelPath, nullptr);
    const char *config_json = env->GetStringUTFChars(configJson, nullptr);
    
    LOGD("Initializing LLM session: %s", model_path);
    LOGD("Config: %s", config_json);
    
    try {
        // 创建 Executor
        MNN::BackendConfig backendConfig;
        auto executor = MNN::Express::Executor::newExecutor(MNN_FORWARD_CPU, backendConfig, 1);
        MNN::Express::ExecutorScope scope(executor);
        
        // 创建 LLM 实例
        auto llm = Llm::createLLM(model_path);
        if (!llm) {
            LOGE("Failed to create LLM instance");
            env->ReleaseStringUTFChars(modelPath, model_path);
            env->ReleaseStringUTFChars(configJson, config_json);
            return 0;
        }
        
        // 设置配置
        llm->set_config(config_json);
        LOGD("Config set: %s", llm->dump_config().c_str());
        
        // 加载模型
        llm->load();
        LOGD("LLM loaded successfully at %p", llm);
        
        env->ReleaseStringUTFChars(modelPath, model_path);
        env->ReleaseStringUTFChars(configJson, config_json);
        
        return reinterpret_cast<jlong>(llm);
    } catch (const std::exception& e) {
        LOGE("Failed to init LLM: %s", e.what());
        env->ReleaseStringUTFChars(modelPath, model_path);
        env->ReleaseStringUTFChars(configJson, config_json);
        return 0;
    }
}

JNIEXPORT void JNICALL
Java_com_lhzkml_jasmine_mnn_MnnLlmSession_nativeUpdateConfig(
    JNIEnv *env,
    jobject thiz,
    jlong sessionPtr,
    jstring configJson
) {
    auto *llm = reinterpret_cast<Llm*>(sessionPtr);
    if (!llm) return;
    const char *config_str = env->GetStringUTFChars(configJson, nullptr);
    try {
        llm->set_config(config_str);
        LOGD("Config updated: %s", config_str);
    } catch (const std::exception &e) {
        LOGE("Update config failed: %s", e.what());
    }
    env->ReleaseStringUTFChars(configJson, config_str);
}

JNIEXPORT void JNICALL
Java_com_lhzkml_jasmine_mnn_MnnLlmSession_nativeRelease(
    JNIEnv *env,
    jobject thiz,
    jlong sessionPtr
) {
    LOGD("Releasing LLM session at %p", reinterpret_cast<void*>(sessionPtr));
    auto *llm = reinterpret_cast<Llm*>(sessionPtr);
    if (llm) {
        delete llm;
        LOGD("LLM session released");
    }
}

JNIEXPORT jstring JNICALL
Java_com_lhzkml_jasmine_mnn_MnnLlmSession_nativeGenerate(
    JNIEnv *env,
    jobject thiz,
    jlong sessionPtr,
    jstring prompt,
    jobject callback
) {
    auto *llm = reinterpret_cast<Llm*>(sessionPtr);
    if (!llm) {
        LOGE("LLM session is null");
        return env->NewStringUTF("");
    }
    
    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);
    LOGD("Generating response for: %s", prompt_str);
    
    // 获取回调方法
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)Z");
    
    std::stringstream response_buffer;
    bool stop_requested = false;
    
    // UTF-8 流处理器
    Utf8StreamProcessor processor([&](const std::string& utf8Char) {
        response_buffer << utf8Char;
        
        // 回调到 Java
        if (callback && onTokenMethod) {
            jstring token = env->NewStringUTF(utf8Char.c_str());
            jboolean should_stop = env->CallBooleanMethod(callback, onTokenMethod, token);
            env->DeleteLocalRef(token);
            stop_requested = (bool)should_stop;
        }
    });
    
    // 流缓冲区
    LlmStreamBuffer stream_buffer([&processor](const char* str, size_t len) {
        processor.processStream(str, len);
    });
    std::ostream output_stream(&stream_buffer);
    
    // 准备历史消息
    std::vector<std::pair<std::string, std::string>> history;
    history.emplace_back("user", prompt_str);
    
    try {
        // 开始生成
        llm->response(history, &output_stream, "<eop>", 1);
        
        // 继续生成直到结束或停止
        int max_tokens = 2048;
        int current_token = 1;
        while (!stop_requested && current_token < max_tokens) {
            llm->generate(1);
            current_token++;
        }
        
        LOGD("Generation completed, tokens: %d", current_token);
    } catch (const std::exception& e) {
        LOGE("Generation failed: %s", e.what());
    }
    
    env->ReleaseStringUTFChars(prompt, prompt_str);
    
    std::string result = response_buffer.str();
    return env->NewStringUTF(result.c_str());
}

} // extern "C"
