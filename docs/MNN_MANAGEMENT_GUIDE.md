# MNN 本地模型管理系统使用指南

## 概述

Jasmine 应用集成了完整的 MNN 本地 LLM 推理引擎管理系统，参考阿里巴巴官方 MNN Chat 应用实现。

## 功能特性

### 1. 本地模型管理
- 查看所有已下载的本地模型
- 显示模型大小、配置信息
- 删除不需要的模型
- 配置模型参数

### 2. 模型市场
- 浏览可下载的模型列表
- 支持多个下载源切换（HuggingFace、ModelScope、Modelers.cn）
- 查看模型详细信息（大小、标签、描述）
- 一键下载模型到本地

### 3. 模型配置
- **基础设置**
  - 后端类型：cpu / opencl / vulkan
  - 精度：low / normal / high
  - 线程数：1-8
  - 内存模式：low / normal / high
  - 使用 mmap：开/关

- **采样参数**
  - Temperature：0-2（控制随机性）
  - Top P：0-1（核采样）
  - Top K：1-100（候选词数量）
  - Max Tokens：128-4096（最大生成长度）

- **系统提示词**
  - 自定义模型的系统提示词

## 使用流程

### 入口
设置 → 本地 MNN → 进入管理界面

### 下载模型
1. 点击"模型市场"按钮
2. 选择下载源（可选）
3. 浏览模型列表
4. 点击"下载"按钮下载模型

### 配置模型
1. 在本地模型列表中找到目标模型
2. 点击"设置"图标（齿轮）
3. 调整各项参数
4. 点击"保存"

### 删除模型
1. 在本地模型列表中找到目标模型
2. 点击"删除"图标（垃圾桶）
3. 确认删除

## 技术架构

### 核心组件

1. **MnnModelManager**
   - 模型文件管理
   - 配置读写
   - 模型删除

2. **MnnManagementActivity**
   - 本地模型列表展示
   - 模型操作入口

3. **MnnModelMarketActivity**
   - 模型市场展示
   - 下载源切换
   - 模型下载

4. **MnnModelSettingsActivity**
   - 模型参数配置
   - 配置保存

### 数据模型

- **MnnModelConfig**: 模型配置（JSON 格式）
- **MnnModelInfo**: 本地模型信息
- **MnnMarketModel**: 市场模型信息

### 文件结构

```
app/files/
└── mnn_models/
    ├── qwen2-1.5b/
    │   ├── model.mnn
    │   └── config.json
    ├── qwen2-0.5b/
    │   ├── model.mnn
    │   └── config.json
    └── ...
```

## 配置文件格式

```json
{
  "llm_model": "model.mnn",
  "llm_weight": "model.mnn",
  "backend_type": "cpu",
  "thread_num": 4,
  "precision": "low",
  "use_mmap": false,
  "memory": "low",
  "system_prompt": "You are a helpful assistant.",
  "temperature": 0.6,
  "topP": 0.95,
  "topK": 20,
  "max_new_tokens": 2048
}
```

## 与官方实现的对比

### 参考的功能
- ✅ 模型市场界面
- ✅ 下载源切换
- ✅ 模型配置系统
- ✅ 采样参数调节
- ✅ 本地模型管理

### 简化的部分
- ❌ 多模态支持（图像/音频）
- ❌ 实时下载进度
- ❌ 模型标签过滤
- ❌ 内置模型系统
- ❌ TTS/ASR 模型支持

### 未来扩展
- [ ] 实现真实的模型下载功能
- [ ] 添加下载进度显示
- [ ] 支持模型搜索和过滤
- [ ] 集成到对话界面
- [ ] 添加模型性能测试

## 注意事项

1. 当前模型市场数据为示例数据，需要连接真实的模型仓库
2. 下载功能需要实现网络请求和文件管理
3. 模型文件较大，建议在 WiFi 环境下下载
4. 不同设备性能差异较大，建议根据设备选择合适的模型大小

## 开发参考

官方实现位置：
- `MNN-master/apps/Android/MnnLlmChat/app/src/main/java/com/alibaba/mnnllm/android/`
  - `modelmarket/` - 模型市场
  - `modelsettings/` - 模型设置
  - `modelist/` - 模型列表
