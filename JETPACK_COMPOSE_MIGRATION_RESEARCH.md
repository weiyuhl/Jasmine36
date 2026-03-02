# Jasmine 应用技术栈研究报告（基于 IMA 反编译分析）

> **文档版本**：3.0（基于 smali 源码逐项验证）  
> **更新日期**：2026-03-02  
> **状态**：✅ 已通过反编译 smali 源码实际验证

## 一、IMA 应用反编译分析结果

### 1.1 关键发现

通过对腾讯 IMA 应用（版本 2.3.1.1736）的反编译 smali 代码**逐目录验证**，确认以下事实：

#### ✅ IMA 使用了 Jetpack Compose

**源码证据**：

1. **MainActivity 继承 ComponentActivity**（Compose 必需基类）：
   ```
   文件: smali_classes12/com/tencent/ima/MainActivity.smali
   .class public final Lcom/tencent/ima/MainActivity;
   .super Landroidx/activity/ComponentActivity;
   ```

2. **MainActivity 调用 setContent**（Compose 入口标志）：
   ```
   invoke-static {p0, v4, p1, v2, v4},
     Landroidx/activity/compose/ComponentActivityKt;->setContent$default(...)V
   ```

3. **QaScreen（主聊天界面）是 Composable 函数**：
   ```
   文件: smali_classes12/com/tencent/ima/business/chat/ui/z7.smali
   check-cast v8, Landroidx/compose/runtime/Composer;
   invoke-static/range {v0..v9}, Lcom/tencent/ima/business/chat/ui/QaScreenKt;->d(...)
   ```

4. **AndroidManifest.xml 中声明 Compose PreviewActivity**：
   ```xml
   <activity android:name="androidx.compose.ui.tooling.PreviewActivity"/>
   ```

5. **MarkdownTextView 带有 Compose 注解**：
   ```
   .annotation build Landroidx/compose/runtime/internal/StabilityInferred;
   ```

> ⚠️ 修正：v2.0 版本文档声称"IMA 并未使用 Jetpack Compose"、"在 smali 中搜索 `androidx.compose` 结果为 0 匹配"，这是**错误的**。实际在 `smali_classes6`、`smali_classes10`、`smali_classes12` 等目录中均存在大量 Compose 相关代码。

### 1.2 IMA 实际使用的技术栈（已验证）

#### UI 框架：Compose 为主 + 传统 View 辅助

IMA 采用**混合 UI 架构**：

| 层次 | 技术 | 用途 |
|------|------|------|
| 主框架 | **Jetpack Compose** | Activity、导航、聊天列表（LazyColumn）、输入栏 |
| Markdown 渲染 | **传统 View**（MarkdownTextView） | 通过 `AndroidView` 嵌入 Compose |
| 表格/代码块 | **XML 布局** | `adapter_node_table_block.xml` 等 148 个布局文件 |

**关键证据**：
- `MainActivity` 不调用 `setContentView`（传统 View 标志），而是调用 `setContent`（Compose 标志）
- `QaScreen` 接收 `Composer` 参数，是 Composable 函数
- `res/layout/` 目录存在 148 个 XML 布局文件，主要用于 Markdown 渲染组件

#### Markdown 渲染方案：Markwon 全套生态

**核心框架**：`Markwon (io.noties.markwon)`（已验证存在于 `smali_classes6/io/noties/markwon/`）

| Markwon 模块 | 位置 | 用途 |
|-------------|------|------|
| core | `markwon/core/CorePlugin.smali` | 核心 Markdown 解析渲染 |
| ext-tables | `markwon/ext/tables/TablePlugin.smali` | GFM 表格 |
| ext-strikethrough | `markwon/ext/strikethrough/` | 删除线 |
| ext-latex | `markwon/ext/latex/JLatexMathPlugin.smali` | LaTeX 公式 |
| image | `markwon/image/ImagesPlugin.smali` | 图片（含 SVG/GIF/网络） |
| html | `markwon/html/` | HTML 支持 |
| linkify | `markwon/linkify/` | 链接自动检测 |
| syntax-highlight | `markwon/syntax/SyntaxHighlight.smali` | 代码语法高亮 |
| recycler | `markwon/recycler/MarkwonAdapter.smali` | RecyclerView 分块渲染 |
| inline-parser | `markwon/inlineparser/` | 自定义行内解析 |

**底层解析**：`org.commonmark`（CommonMark 规范实现，Markwon 内部依赖）

> ⚠️ 修正：v2.0 版本文档声称 IMA 使用 `org.intellij.markdown` 作为主要 Markdown 解析器，经验证该库在反编译代码中**不存在**。实际使用的是 **Markwon**。

**IMA 自定义扩展**（`smali_classes12/com/tencent/ima/business/chat/markdown/`）：
```
markdown/
├── text/
│   ├── MarkdownTextView.smali        # 自定义 TextView（继承 ImaTextView）
│   └── menu/
│       ├── FloatingMenu.smali        # 浮动选择菜单
│       ├── MarkdownMenuInflater.smali
│       └── MenuActions.smali
├── code/
│   └── FencedCodeController.smali    # 代码块控制器（Tab 切换、复制）
├── grammar/
│   ├── a.smali                       # 自定义 Prism4j GrammarLocator
│   └── languages/                    # 14+ 编程语言语法定义
├── latex/
│   ├── a.smali                       # \(...\) / \[...\] InlineProcessor
│   ├── b.smali                       # $...$ / $$...$$ InlineProcessor
│   └── e.smali                       # LatexDrawableCache（200MB LRU）
├── link/
│   ├── LinkHandler.smali
│   └── LinkResolver.smali
├── image/
├── lineheight/
└── recyclerview/
    └── SelectableRecyclerView.smali  # 支持文本选择的 RecyclerView
```

#### 代码语法高亮：Prism4j

**已验证存在**（`smali_classes6/io/noties/prism4j/`）

支持语言：JavaScript, Swift, C-like, Makefile, YAML, JSON, Java, CSS, Go, C, Python, Kotlin, Groovy, C#

#### LaTeX 公式：JLatexMathPlugin + microtex

- `JLatexMathPlugin.smali`（Markwon ext-latex 插件）
- `microtex/`（io.nano.tex 渲染引擎，字体资源在 `unknown/io/nano/tex/res/`）
- 自定义 InlineProcessor 支持 `$...$`、`$$...$$`、`\(...\)`、`\[...\]` 四种格式
- 200MB LRU 缓存（LatexDrawableCache）

#### 其他依赖（已验证）

| 依赖 | 状态 | 位置 |
|------|------|------|
| OkHttp3 + SSE | ✅ 存在 | smali_classes7/okhttp3/ |
| OkHttp WebSocket | ✅ 存在 | smali_classes7/okhttp3/internal/ws/ |
| Kotlin Serialization | ✅ 存在 | smali_classes7/kotlinx/serialization/ |
| Room | ✅ 存在 | smali_classes10/androidx/room/ |
| Media3 ExoPlayer | ✅ 存在 | smali_classes10/androidx/media3/ |
| ~~Coil~~ | ❌ 不存在 | 未发现 |
| ~~Glide~~ | ❌ 不存在 | 未发现 |
| ~~Gson~~ | ❌ 不存在 | 使用 Kotlin Serialization |
| ~~X5 WebView~~ | ❌ 不存在 | 未发现 |
| ~~Retrofit~~ | ❌ 不存在 | 直接使用 OkHttp |
| ~~org.intellij.markdown~~ | ❌ 不存在 | 使用 Markwon + CommonMark |

## 二、Jasmine 当前技术栈对比

### 2.1 对比表

| 技术 | Jasmine | IMA | 差异 |
|------|---------|-----|------|
| UI 框架 | 传统 View | **Compose + View 混合** | ⚠️ IMA 更现代 |
| Markdown 渲染框架 | Markwon | **Markwon（全套插件）** | ✅ 相同框架 |
| 代码语法高亮 | 无 | **Prism4j** | ❌ Jasmine 缺失 |
| LaTeX 公式 | AndroidMath (MTMathView) | **JLatexMathPlugin + microtex** | ⚠️ 不同方案 |
| LaTeX 缓存 | 50 条 LruCache | **200MB LRU Drawable 缓存** | ⚠️ IMA 更大 |
| Markdown 分块渲染 | 无 | **Markwon RecyclerView** | ❌ Jasmine 缺失 |
| 网络库 | Ktor Client | OkHttp3 | ⚠️ 不同但都支持 SSE |
| JSON 序列化 | Kotlin Serialization | Kotlin Serialization | ✅ 相同 |
| 数据库 | Room | Room | ✅ 相同 |
| 图片加载 | Markwon ImagesPlugin | Markwon ImagesPlugin | ✅ 相同 |
| 音频播放 | 无 | Media3 ExoPlayer | ❌ Jasmine 不需要 |

### 2.2 关键发现

1. **IMA 和 Jasmine 使用相同的 Markdown 核心框架**（Markwon），但 IMA 使用了更多插件和大量自定义扩展
2. **IMA 使用 Compose 作为主框架**，Markdown 渲染组件通过 `AndroidView` 嵌入
3. **IMA 没有使用 Coil/Glide 图片库**，而是使用 Markwon 自带的 ImagesPlugin
4. **IMA 的 LaTeX 方案（JLatexMathPlugin）与 Markwon 深度集成**，比 Jasmine 的 AndroidMath 预处理方案更优雅

## 三、Jasmine 可从 IMA 借鉴的改进方向

### 3.1 优先级 1：增强 Markwon 插件（立即可做）

Jasmine 当前的 Markwon 配置缺少关键插件，可参考 IMA 补齐：

**当前 Jasmine 配置**：
```kotlin
Markwon.builder(context)
    .usePlugin(ImagesPlugin.create())
    .usePlugin(TablePlugin.create(context))
    .usePlugin(StrikethroughPlugin.create())
    .usePlugin(TaskListPlugin.create(context))
    .usePlugin(HtmlPlugin.create())
    .usePlugin(LinkifyPlugin.create())
    .build()
```

**参考 IMA 应补充的**：
```kotlin
Markwon.builder(context)
    // ... 现有插件 ...
    .usePlugin(SyntaxHighlightPlugin.create(
        Prism4j(CustomGrammarLocator()),    // 代码语法高亮
        Prism4jThemeDarkula.create()
    ))
    .usePlugin(JLatexMathPlugin.create(textSize) { builder ->
        builder.inlinesEnabled(true)        // LaTeX 行内公式
        builder.theme()
            .textColor(textColor)
    })
    .build()
```

**所需新依赖**：
```gradle
// 代码语法高亮
implementation "io.noties.markwon:syntax-highlight:4.6.2"
implementation "io.noties:prism4j:2.0.0"
annotationProcessor "io.noties:prism4j-bundler:2.0.0"

// LaTeX（替换当前 AndroidMath 方案）
implementation "io.noties.markwon:ext-latex:4.6.2"

// Markwon RecyclerView 分块渲染（长文本优化）
implementation "io.noties.markwon:recycler:4.6.2"

// 自定义行内解析（自定义 LaTeX 语法等）
implementation "io.noties.markwon:inline-parser:4.6.2"
```

### 3.2 优先级 2：LaTeX 渲染方案迁移

**当前方案**（Jasmine）：
- 手动正则提取 LaTeX → AndroidMath (MTMathView) 渲染为 Bitmap → ImageSpan 替换占位符
- 问题：与 Markwon 解析管道割裂，占位符可能被 Markdown 解析干扰

**IMA 方案**：
- Markwon ext-latex (JLatexMathPlugin) + microtex 引擎
- 通过自定义 InlineProcessor 直接在 Markwon 解析管道中处理 LaTeX
- 200MB LRU Drawable 缓存

**建议**：考虑迁移到 JLatexMathPlugin，与 Markwon 深度集成，避免当前占位符被干扰的问题。

### 3.3 优先级 3：Markwon RecyclerView 分块渲染

IMA 使用 `MarkwonAdapter`（Markwon 的 RecyclerView 集成），将长 Markdown 内容拆分为多个 ViewHolder：
- 文本段落 → SimpleEntry
- 代码块 → 专用 ViewHolder（带水平滚动、Tab 切换、复制按钮）
- 表格 → 专用 ViewHolder（带水平滚动、卡片样式）

这对于长 AI 回复内容的渲染性能有显著提升。

### 3.4 优先级 4：代码块增强

IMA 的代码块功能（通过 FencedCodeController 实现）：
- Prism4j 语法高亮（14+ 语言）
- 水平滚动（DrawerAwareHorizontalScrollView）
- 代码/图片 Tab 切换
- 复制按钮
- 等宽字体 `monospace`

## 四、关于 Jetpack Compose 迁移

### 4.1 IMA 的混合架构启示

IMA 的实践证明了**混合架构是可行的**：
- 主框架使用 Compose（Activity、导航、列表）
- Markdown 渲染使用传统 View（通过 `AndroidView` 嵌入）
- 两者可以良好共存

这意味着 Jasmine 如果要迁移到 Compose：
- **不需要重写 Markdown 渲染**，保持现有 Markwon + MarkdownRenderer 即可
- **聊天列表可以迁移到 LazyColumn**，获得更好的虚拟滚动性能
- **Markdown 内容通过 AndroidView 嵌入 Compose**，与 IMA 方式一致

### 4.2 迁移价值重新评估

| 方面 | 迁移前（传统 View） | 迁移后（Compose 混合） | 收益 |
|------|-------------------|---------------------|------|
| 聊天列表 | RecyclerView + Adapter | LazyColumn | 代码更简洁 |
| 状态管理 | 手动 UI 更新 | State 驱动自动重组 | 减少 Bug |
| 动画 | 手动实现 | Compose 动画 API | 更流畅 |
| Markdown | Markwon (View) | Markwon (AndroidView 包装) | 无变化 |
| 开发效率 | XML + Kotlin | 纯 Kotlin | 提升 |

### 4.3 建议的迁移策略

**如果要迁移**，建议参考 IMA 的混合架构：

1. **阶段 1**：新页面使用 Compose，聊天页面保持不变
2. **阶段 2**：聊天页面迁移到 Compose LazyColumn，Markdown 内容通过 AndroidView 嵌入
3. **阶段 3**：优化 Compose 与 View 的交互性能

**如果不迁移**，优先做以下改进：

1. ✅ 补齐 Markwon 插件（Prism4j 语法高亮、JLatexMathPlugin）
2. ✅ 添加 Markwon RecyclerView 分块渲染
3. ✅ 代码块增强（水平滚动、复制、语言标签）
4. ✅ 优化 LaTeX 缓存策略

## 五、总结

### 5.1 关键纠正

| 原文档（v2.0）说法 | 实际验证结果 |
|-------------------|-------------|
| ❌ IMA 不使用 Compose | ✅ IMA 使用 Compose 作为主框架 |
| ❌ IMA 使用 intellij-markdown | ✅ IMA 使用 Markwon（与 Jasmine 相同） |
| ❌ IMA 使用 Coil 图片库 | ✅ IMA 不使用 Coil，使用 Markwon ImagesPlugin |
| ❌ IMA 使用 Gson | ✅ IMA 使用 Kotlin Serialization |
| ❌ IMA 使用 X5 WebView | ✅ IMA 不使用 X5 |
| ❌ Compose 迁移无法参考 IMA | ✅ IMA 的混合架构是很好的参考 |

### 5.2 核心结论

1. **IMA 和 Jasmine 使用相同的 Markdown 核心框架（Markwon）**，IMA 的优势在于更完整的插件配置和大量自定义扩展
2. **IMA 采用 Compose + View 混合架构**，证明了 Compose 主框架 + Markwon View 渲染的可行性
3. **Jasmine 最紧迫的改进方向**不是框架迁移，而是补齐 Markwon 插件生态（代码高亮、LaTeX 集成、分块渲染）

### 5.3 行动建议

**立即可做**（无需迁移框架）：
1. ✅ 添加 Prism4j 代码语法高亮
2. ✅ 迁移 LaTeX 到 JLatexMathPlugin（与 Markwon 深度集成）
3. ✅ 添加 Markwon RecyclerView 分块渲染
4. ✅ 代码块增强（水平滚动、复制按钮）

**可选的长期目标**：
- 参考 IMA 的混合架构，逐步迁移到 Compose
- Compose 不影响 Markdown 渲染（通过 AndroidView 嵌入）

---

**文档版本**：3.0（基于 smali 源码逐项验证，纠正 v2.0 中的多项错误）  
**更新日期**：2026-03-02  
**验证方式**：反编译 smali 源码逐目录验证  
**APK 版本**：com.tencent.ima 2.3.1.1736
