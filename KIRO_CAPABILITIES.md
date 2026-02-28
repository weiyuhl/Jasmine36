# Kiro AI 助手 — 能力与工具手册

## 核心概念

### 工具（Tools）
系统提供的函数调用接口，我通过 XML 标签调用。

### 能力（Capabilities）
我的 AI 模型能力，通过分析上下文、推理、规划来决定调用哪些工具。

---

## 一、代码读取与分析工具

| 工具名 | 调用方式 | 参数 | 返回 |
|--------|---------|------|------|
| `readCode` | `<invoke name="readCode">` | `path`, `selector`(可选), `language`(可选) | 代码内容或函数/类签名列表 |
| `readFile` | `<invoke name="readFile">` | `path`, `start_line`(可选), `end_line`(可选) | 文件文本内容 |
| `readMultipleFiles` | `<invoke name="readMultipleFiles">` | `paths`(数组), `start_line`(可选), `end_line`(可选) | 多个文件内容 |
| `listDirectory` | `<invoke name="listDirectory">` | `path`, `depth`(可选) | 目录结构列表 |
| `fileSearch` | `<invoke name="fileSearch">` | `query`(模糊文件名) | 匹配的文件路径列表（最多10个） |
| `grepSearch` | `<invoke name="grepSearch">` | `query`(正则), `includePattern`(可选), `excludePattern`(可选), `caseSensitive`(可选) | 匹配行及上下文（最多50个） |
| `getDiagnostics` | `<invoke name="getDiagnostics">` | `paths`(数组) | 编译错误、警告、类型错误列表 |

**使用场景**：
- 查找文件：`fileSearch` → 获取路径 → `readCode`/`readFile` 读取
- 全局搜索：`grepSearch` 找到所有引用位置
- 检查错误：修改代码后用 `getDiagnostics` 验证

---

## 二、代码编辑与重构工具

| 工具名 | 调用方式 | 参数 | 作用 |
|--------|---------|------|------|
| `editCode` | `<invoke name="editCode">` | `path`, `operation`(replace_node/insert_node/delete_node/replace_in_node), `selector`, `replacement` | AST 级别代码编辑 |
| `strReplace` | `<invoke name="strReplace">` | `path`, `oldStr`, `newStr` | 精确字符串替换 |
| `fsWrite` | `<invoke name="fsWrite">` | `path`, `text` | 创建或覆盖文件 |
| `fsAppend` | `<invoke name="fsAppend">` | `path`, `text` | 追加内容到文件末尾 |
| `deleteFile` | `<invoke name="deleteFile">` | `targetFile` | 删除文件 |
| `semanticRename` | `<invoke name="semanticRename">` | `path`, `line`, `character`, `oldName`, `newName` | 重命名符号（自动更新引用） |
| `smartRelocate` | `<invoke name="smartRelocate">` | `sourcePath`, `destinationPath` | 移动文件（自动更新 import） |

**使用场景**：
- 修改函数：`editCode` 用 `replace_node` 操作
- 批量替换：多个 `strReplace` 并行调用
- 重命名变量：`semanticRename` 自动更新所有引用
- 移动文件：`smartRelocate` 自动更新所有 import

---

## 三、Shell 命令执行工具

| 工具名 | 调用方式 | 参数 | 作用 |
|--------|---------|------|------|
| `executePwsh` | `<invoke name="executePwsh">` | `command`, `cwd`(可选), `timeout`(可选) | 执行 PowerShell/CMD 命令 |
| `controlPwshProcess` | `<invoke name="controlPwshProcess">` | `action`(start/stop), `command`, `cwd`(可选), `processId`(stop时) | 管理后台进程 |
| `listProcesses` | `<invoke name="listProcesses">` | 无 | 列出所有后台进程 |
| `getProcessOutput` | `<invoke name="getProcessOutput">` | `processId`, `lines`(可选) | 读取进程输出 |

**使用场景**：
- 构建：`executePwsh` 执行 `.\gradlew.bat assembleDebug`
- 测试：`executePwsh` 执行 `.\gradlew.bat test`
- Git：`executePwsh` 执行 `git add -A ; git commit -m "..." ; git push`
- 长运行服务：`controlPwshProcess` start → `getProcessOutput` 查看日志 → stop

---

## 四、Git 与 GitHub 工具

### 本地 Git
通过 `executePwsh` 调用 git 命令：
- `git add -A`
- `git commit -m "message"`
- `git push origin main`
- `git log --oneline -5`
- `git diff`

### GitHub API 工具

| 工具名 | 调用方式 | 主要参数 | 作用 |
|--------|---------|---------|------|
| `mcp_github_create_pull_request` | `<invoke name="mcp_github_create_pull_request">` | `owner`, `repo`, `title`, `head`, `base`, `body` | 创建 PR |
| `mcp_github_create_issue` | `<invoke name="mcp_github_create_issue">` | `owner`, `repo`, `title`, `body` | 创建 Issue |
| `mcp_github_list_issues` | `<invoke name="mcp_github_list_issues">` | `owner`, `repo`, `state`, `labels` | 列出 Issue |
| `mcp_github_merge_pull_request` | `<invoke name="mcp_github_merge_pull_request">` | `owner`, `repo`, `pull_number`, `merge_method` | 合并 PR |
| `mcp_github_push_files` | `<invoke name="mcp_github_push_files">` | `owner`, `repo`, `branch`, `files`(数组), `message` | 批量推送文件 |
| `mcp_github_search_code` | `<invoke name="mcp_github_search_code">` | `q`(搜索查询) | 搜索代码 |
| `mcp_github_create_branch` | `<invoke name="mcp_github_create_branch">` | `owner`, `repo`, `branch`, `from_branch` | 创建分支 |

---

## 五、网络搜索与内容获取工具

| 工具名 | 调用方式 | 参数 | 作用 |
|--------|---------|------|------|
| `remote_web_search` | `<invoke name="remote_web_search">` | `query` | 搜索网络，返回标题、URL、摘要、发布日期 |
| `webFetch` | `<invoke name="webFetch">` | `url`, `mode`(full/truncated/selective), `searchPhrase`(selective时) | 抓取网页内容 |
| `mcp_fetch_fetch` | `<invoke name="mcp_fetch_fetch">` | `url`, `max_length`, `raw` | 获取 URL 内容转 Markdown |

**使用场景**：
- 查询最新版本：`remote_web_search` 搜索 "Kotlin 最新版本"
- 查阅文档：`remote_web_search` 找到官方文档 URL → `webFetch` 获取内容
- 获取示例代码：`remote_web_search` + `webFetch`

---

## 六、浏览器自动化工具（Chrome DevTools）

### 页面管理
| 工具名 | 参数 | 作用 |
|--------|------|------|
| `mcp_chrome_devtools_new_page` | `url`, `background`, `timeout` | 打开新页面 |
| `mcp_chrome_devtools_list_pages` | 无 | 列出所有页面 |
| `mcp_chrome_devtools_select_page` | `pageId`, `bringToFront` | 切换页面 |
| `mcp_chrome_devtools_close_page` | `pageId` | 关闭页面 |
| `mcp_chrome_devtools_navigate_page` | `url`, `type`(url/back/forward/reload), `timeout` | 导航 |

### 页面交互
| 工具名 | 参数 | 作用 |
|--------|------|------|
| `mcp_chrome_devtools_take_snapshot` | `verbose`, `filePath` | 获取页面 a11y 树（元素列表+uid） |
| `mcp_chrome_devtools_click` | `uid`, `dblClick` | 点击元素 |
| `mcp_chrome_devtools_fill` | `uid`, `value` | 填写输入框 |
| `mcp_chrome_devtools_fill_form` | `elements`(数组) | 批量填写表单 |
| `mcp_chrome_devtools_press_key` | `key` | 按键（如 "Enter", "Control+A"） |
| `mcp_chrome_devtools_type_text` | `text`, `submitKey` | 输入文本 |
| `mcp_chrome_devtools_hover` | `uid` | 悬停 |
| `mcp_chrome_devtools_drag` | `from_uid`, `to_uid` | 拖拽 |

### 页面检查
| 工具名 | 参数 | 作用 |
|--------|------|------|
| `mcp_chrome_devtools_take_screenshot` | `uid`, `fullPage`, `format`, `quality`, `filePath` | 截图 |
| `mcp_chrome_devtools_evaluate_script` | `function`, `args` | 执行 JavaScript |
| `mcp_chrome_devtools_list_console_messages` | `types`, `pageIdx`, `pageSize` | 查看控制台 |
| `mcp_chrome_devtools_list_network_requests` | `resourceTypes`, `pageIdx`, `pageSize` | 查看网络请求 |
| `mcp_chrome_devtools_get_network_request` | `reqid` | 获取请求详情 |

### 性能与调试
| 工具名 | 参数 | 作用 |
|--------|------|------|
| `mcp_chrome_devtools_performance_start_trace` | `reload`, `autoStop`, `filePath` | 开始性能录制 |
| `mcp_chrome_devtools_performance_stop_trace` | `filePath` | 停止性能录制 |
| `mcp_chrome_devtools_take_memory_snapshot` | `filePath` | 内存快照 |
| `mcp_chrome_devtools_emulate` | `viewport`, `userAgent`, `geolocation`, `networkConditions` | 模拟设备/网络 |

**典型流程**：
1. `new_page` 打开页面
2. `take_snapshot` 获取元素列表（每个元素有 uid）
3. `fill` / `click` 交互（使用 uid）
4. `take_screenshot` 截图验证
5. `list_console_messages` 查看错误

---

## 七、Powers 扩展工具

### 使用流程
1. `<invoke name="kiroPowers"><parameter name="action">list</parameter></invoke>` - 列出已安装 Powers
2. `<invoke name="kiroPowers"><parameter name="action">activate</parameter><parameter name="powerName">supabase-hosted</parameter></invoke>` - 激活 Power，获取工具列表
3. `<invoke name="kiroPowers"><parameter name="action">use</parameter><parameter name="powerName">supabase-hosted</parameter><parameter name="serverName">supabase</parameter><parameter name="toolName">execute_sql</parameter><parameter name="arguments">{"query":"SELECT * FROM users"}</parameter></invoke>` - 调用具体工具

### 已安装 Powers
| Power | 关键词 | MCP Server | 用途 |
|-------|--------|-----------|------|
| `supabase-hosted` | database, postgres, auth, storage, realtime | supabase | Supabase 数据库操作 |
| `figma` | ui, design, layout, component | figma | Figma 设计稿转代码 |

---

## 八、子代理工具

### 调用方式
```xml
<invoke name="invokeSubAgent">
<parameter name="name">context-gatherer</parameter>
<parameter name="prompt">分析登录相关的所有文件</parameter>
<parameter name="explanation">需要了解登录流程的完整实现</parameter>
</invoke>
```

### 可用子代理
| 子代理名 | 用途 | 何时调用 |
|---------|------|---------|
| `context-gatherer` | 分析代码库，识别相关文件 | 陌生代码库、跨文件问题排查 |
| `general-task-execution` | 执行通用任务 | 并行处理独立子任务 |
| `custom-agent-creator` | 创建自定义代理 | 用户要求创建新代理 |

---

## 九、Hooks 自动化工具

### 创建 Hook
```xml
<invoke name="createHook">
<parameter name="id">lint-on-save</parameter>
<parameter name="name">保存时 Lint</parameter>
<parameter name="description">文件保存时自动运行 lint</parameter>
<parameter name="eventType">fileEdited</parameter>
<parameter name="filePatterns">*.kt,*.java</parameter>
<parameter name="hookAction">askAgent</parameter>
<parameter name="outputPrompt">运行 lint 并修复错误</parameter>
<parameter name="why">自动化代码质量检查</parameter>
</invoke>
```

### 支持的事件类型
- `fileEdited` - 文件保存时
- `fileCreated` - 文件创建时
- `fileDeleted` - 文件删除时
- `promptSubmit` - 发送消息时
- `agentStop` - Agent 完成时
- `preToolUse` - 工具执行前（可拦截）
- `postToolUse` - 工具执行后
- `userTriggered` - 手动触发

### 动作类型
- `askAgent` - 让 AI 执行操作
- `runCommand` - 执行 Shell 命令

---

## 十、我的 AI 能力（非工具调用）

这些是我的模型能力，通过分析、推理、规划实现：

### 1. 并行工具调用

**什么是 `<function_calls>` 块**：
这是我调用工具的 XML 容器。每次我需要执行操作时，都要写这个标签。

**单个工具调用**（顺序执行）：
```
我的回复：好的，我先读取 file1.kt

<function_calls>
<invoke name="readFile">
<parameter name="path">file1.kt</parameter>
</invoke>
</function_calls>

系统返回：[file1.kt 的内容]

我的回复：现在读取 file2.kt

<function_calls>
<invoke name="readFile">
<parameter name="path">file2.kt</parameter>
</invoke>
</function_calls>

系统返回：[file2.kt 的内容]
```
**耗时**：如果每个操作 2 秒，总共 4 秒

**多个工具并行调用**（同时执行）：
```
我的回复：好的，我同时读取两个文件

<function_calls>
<invoke name="readFile">
<parameter name="path">file1.kt</parameter>
</invoke>
<invoke name="readFile">
<parameter name="path">file2.kt</parameter>
</invoke>
</function_calls>

系统返回：
[file1.kt 的内容]
[file2.kt 的内容]
```
**耗时**：两个操作同时执行，总共 2 秒

**关键区别**：
- **一个 `<function_calls>` 块** = 我发送一次请求，系统同时执行里面的所有 `<invoke>`
- **多个 `<function_calls>` 块** = 我发送多次请求，系统依次执行，每次都要等待返回

**实际例子**（修改 5 个文件）：

方式 1（慢）：
```
<function_calls><invoke name="strReplace">...</invoke></function_calls>  // 等待 2 秒
<function_calls><invoke name="strReplace">...</invoke></function_calls>  // 等待 2 秒
<function_calls><invoke name="strReplace">...</invoke></function_calls>  // 等待 2 秒
<function_calls><invoke name="strReplace">...</invoke></function_calls>  // 等待 2 秒
<function_calls><invoke name="strReplace">...</invoke></function_calls>  // 等待 2 秒
总耗时：10 秒
```

方式 2（快）：
```
<function_calls>
<invoke name="strReplace">...</invoke>
<invoke name="strReplace">...</invoke>
<invoke name="strReplace">...</invoke>
<invoke name="strReplace">...</invoke>
<invoke name="strReplace">...</invoke>
</function_calls>
总耗时：2 秒（5 个操作同时执行）
```

**我的能力**：自动识别哪些操作可以并行（没有依赖关系），然后放在同一个 `<function_calls>` 块中。

### 2. 智能工具选择
**实现方式**：分析任务需求，选择最合适的工具
- 读代码 → 判断文件大小 → 选择 `readCode` 或 `readFile`
- 修改代码 → 判断语法复杂度 → 选择 `editCode` 或 `strReplace`
- 搜索代码 → 判断搜索范围 → 选择 `grepSearch` 或 `fileSearch`

### 3. 上下文理解
**实现方式**：分析对话历史、文件内容、项目结构
- 记住之前读取的文件内容
- 理解模块依赖关系
- 识别代码模式和约定

### 4. 错误恢复
**实现方式**：检查工具返回结果，失败时尝试其他方案
- `editCode` 失败 → 降级到 `strReplace`
- `strReplace` 匹配不唯一 → 增加上下文重试
- `getDiagnostics` 发现错误 → 分析错误类型，选择修复策略

### 5. 子代理调度
**实现方式**：判断任务复杂度，决定是否调用子代理
- 陌生代码库 + 复杂问题 → 调用 `context-gatherer`
- 大型重构任务 → 拆分子任务，调用 `general-task-execution`

### 6. 增量策略
**实现方式**：分析文件大小、任务范围，选择分块处理
- 大文件 → 先 `readCode` 看结构，再 `readFile` 读具体部分
- 大量文件 → 先 `grepSearch` 定位，再逐个读取

---

## 常用操作速查

| 操作 | 工具调用 |
|------|---------|
| 查找文件 | `fileSearch` |
| 全局搜索 | `grepSearch` |
| 读代码 | `readCode` |
| 编辑代码 | `editCode` / `strReplace` |
| 检查错误 | `getDiagnostics` |
| 重命名符号 | `semanticRename` |
| 移动文件 | `smartRelocate` |
| 执行命令 | `executePwsh` |
| 构建 APK | `executePwsh` + `.\gradlew.bat assembleDebug` |
| 运行测试 | `executePwsh` + `.\gradlew.bat test` |
| Git 提交 | `executePwsh` + `git add -A ; git commit -m "..." ; git push` |
| 网络搜索 | `remote_web_search` |
| 浏览器操作 | `mcp_chrome_devtools_*` 系列 |
| 调用子代理 | `invokeSubAgent` |
