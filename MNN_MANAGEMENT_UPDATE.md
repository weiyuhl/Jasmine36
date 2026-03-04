# MNN 本地模型管理系统 - 更新说明

## 更新时间
2026-03-04

## 新增功能

### 1. 本地 MNN 模型管理系统
参考阿里巴巴官方 MNN Chat 应用，实现了完整的本地模型管理功能。

#### 入口位置
设置 → 本地 MNN → 进入管理界面

#### 主要功能

**本地模型管理界面** (`MnnManagementActivity`)
- 显示所有已下载的本地模型
- 查看模型大小和配置信息
- 快速进入模型设置
- 删除不需要的模型
- 一键进入模型市场

**模型市场** (`MnnModelMarketActivity`)
- 浏览可下载的模型列表
- 支持三个下载源切换：
  - HuggingFace
  - ModelScope
  - Modelers.cn
- 显示模型详细信息（名称、厂商、大小、标签、描述）
- 一键下载模型（待实现网络功能）

**模型配置界面** (`MnnModelSettingsActivity`)
- **基础设置**
  - 后端类型：cpu / opencl / vulkan
  - 精度：low / normal / high
  - 线程数：1-8 可调
  - 内存模式：low / normal / high
  - 使用 mmap 开关

- **采样参数**
  - Temperature：0-2（控制随机性）
  - Top P：0-1（核采样概率）
  - Top K：1-100（候选词数量）
  - Max Tokens：128-4096（最大生成长度）

- **系统提示词**
  - 自定义模型的系统提示词

### 2. 数据管理

**MnnModelManager**
- 统一管理模型文件和配置
- 支持 JSON 格式配置文件
- 自动计算模型大小
- 提供配置读写接口

**数据模型**
- `MnnModelConfig`: 模型配置
- `MnnModelInfo`: 本地模型信息
- `MnnMarketModel`: 市场模型信息

### 3. 文件结构

```
app/files/mnn_models/
├── qwen2-1.5b/
│   ├── model.mnn
│   └── config.json
├── qwen2-0.5b/
│   ├── model.mnn
│   └── config.json
└── ...
```

## 技术实现

### 新增文件
- `app/src/main/java/com/lhzkml/jasmine/mnn/MnnModel.kt` - 数据模型
- `app/src/main/java/com/lhzkml/jasmine/mnn/MnnModelManager.kt` - 模型管理器
- `app/src/main/java/com/lhzkml/jasmine/mnn/MnnManagementActivity.kt` - 管理界面
- `app/src/main/java/com/lhzkml/jasmine/mnn/MnnModelMarketActivity.kt` - 模型市场
- `app/src/main/java/com/lhzkml/jasmine/mnn/MnnModelSettingsActivity.kt` - 配置界面
- `docs/MNN_MANAGEMENT_GUIDE.md` - 使用指南

### 修改文件
- `app/src/main/java/com/lhzkml/jasmine/SettingsActivity.kt` - 添加入口
- `app/src/main/AndroidManifest.xml` - 注册新 Activity
- `app/build.gradle.kts` - 添加 Gson 依赖

### 依赖更新
- 添加 Gson 2.10.1 用于 JSON 解析

## 构建信息
- APK 大小：31.56 MB
- 构建时间：2026-03-04 17:05:47
- 构建状态：成功

## 待实现功能

### 高优先级
- [ ] 实现真实的模型下载功能（网络请求 + 文件管理）
- [ ] 添加下载进度显示
- [ ] 集成到对话界面使用本地模型

### 中优先级
- [ ] 模型搜索和过滤功能
- [ ] 模型性能测试工具
- [ ] 下载队列管理

### 低优先级
- [ ] 模型标签系统
- [ ] 模型评分和评论
- [ ] 模型更新检测

## 使用说明

1. 打开应用 → 设置 → 本地 MNN
2. 点击"模型市场"浏览可用模型
3. 选择下载源（可选）
4. 点击"下载"按钮下载模型（当前为示例数据）
5. 返回管理界面查看已下载模型
6. 点击设置图标配置模型参数
7. 点击删除图标移除不需要的模型

## 注意事项

1. 当前模型市场为示例数据，需要连接真实模型仓库
2. 下载功能需要实现网络请求和文件下载
3. 建议在 WiFi 环境下下载大模型
4. 根据设备性能选择合适的模型大小和配置

## 参考实现

官方 MNN Chat 应用：
- 位置：`MNN-master/apps/Android/MnnLlmChat/`
- 参考模块：
  - `modelmarket/` - 模型市场实现
  - `modelsettings/` - 模型配置实现
  - `modelist/` - 模型列表实现
