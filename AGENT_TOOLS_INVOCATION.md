# Agent 工具调用方法完整参考

本文档记录 Agent 全部工具的调用格式、参数定义及示例。

---

## 一、调用格式

### 1.1 标准格式（XML-like）

```
<invoke name="工具名">
<parameter name="参数名">参数值</parameter>
<parameter name="参数名2">参数值2</parameter>
...
</invoke>
```

### 1.2 调用规则

- 每个工具调用用 `<invoke>` 包裹，`name` 指定工具名
- 参数用 `<parameter>` 传递，`name` 为参数名，标签内容为参数值
- 无依赖关系的多个工具可在同一批中并行调用
- 有依赖关系的调用需等待前一次返回后再发起

---

## 二、工具列表及参数

### 2.1 SemanticSearch

**功能**：按语义搜索代码，不依赖精确文本匹配。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `query` | string | 是 | 搜索问题（完整问句，如 "Where is user authentication handled?"） |
| `target_directories` | array | 否 | 限定目录，如 `["src/"]`；空则搜索整个仓库 |
| `num_results` | integer | 否 | 返回结果数量，默认 25，最大 25 |

**示例**：
```
<invoke name="SemanticSearch">
<parameter name="query">Where is the login API endpoint defined?</parameter>
<parameter name="target_directories">["app/src/main/"]</parameter>
<parameter name="num_results">10</parameter>
</invoke>
```

---

### 2.2 Grep

**功能**：文本/正则搜索，支持 ripgrep 语法。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `pattern` | string | 是 | 搜索模式（支持正则） |
| `path` | string | 否 | 限定路径或文件 |
| `output_mode` | string | 否 | `content`（默认）/ `files_with_matches` / `count` |
| `-A` | number | 否 | 匹配行后显示行数 |
| `-B` | number | 否 | 匹配行前显示行数 |
| `-C` | number | 否 | 匹配行前后各显示行数 |
| `multiline` | boolean | 否 | 是否跨行匹配 |
| `type` | string | 否 | 文件类型过滤，如 `py`、`ts` |
| `glob` | string | 否 | glob 过滤 |
| `head_limit` | number | 否 | 结果数量上限 |
| `offset` | number | 否 | 跳过前 N 个匹配 |

**示例**：
```
<invoke name="Grep">
<parameter name="pattern">class.*Activity</parameter>
<parameter name="path">app/src/main/java</parameter>
<parameter name="output_mode">content</parameter>
<parameter name="-A">2</parameter>
</invoke>
```

---

### 2.3 Read

**功能**：读取文件内容，支持图片。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `path` | string | 是 | 文件路径（绝对或相对工作区） |
| `offset` | integer | 否 | 起始行号 |
| `limit` | integer | 否 | 读取行数 |

**示例**：
```
<invoke name="Read">
<parameter name="path">app/build.gradle.kts</parameter>
</invoke>
```

---

### 2.4 Write

**功能**：写入或覆盖文件。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `path` | string | 是 | 文件路径 |
| `contents` | string | 是 | 文件内容 |

**示例**：
```
<invoke name="Write">
<parameter name="path">README.md</parameter>
<parameter name="contents"># Project Title\n\nDescription...</parameter>
</invoke>
```

---

### 2.5 StrReplace

**功能**：精确字符串替换，不支持正则。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `path` | string | 是 | 文件路径 |
| `old_string` | string | 是 | 被替换字符串（需精确匹配） |
| `new_string` | string | 是 | 替换后字符串 |
| `replace_all` | boolean | 否 | 是否替换全部出现，默认 false |

**示例**：
```
<invoke name="StrReplace">
<parameter name="path">app/build.gradle.kts</parameter>
<parameter name="old_string">versionCode 1</parameter>
<parameter name="new_string">versionCode 2</parameter>
</invoke>
```

---

### 2.6 Delete

**功能**：删除文件。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `path` | string | 是 | 要删除的文件路径 |

**示例**：
```
<invoke name="Delete">
<parameter name="path">temp/output.txt</parameter>
</invoke>
```

---

### 2.7 Glob

**功能**：按 glob 模式查找文件。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `glob_pattern` | string | 是 | 模式，如 `**/*.kt`、`**/test/**/*.ts` |
| `target_directory` | string | 否 | 搜索根目录，默认工作区根目录 |

**示例**：
```
<invoke name="Glob">
<parameter name="glob_pattern">**/*.kt</parameter>
<parameter name="target_directory">app/src/main</parameter>
</invoke>
```

---

### 2.8 ReadLints

**功能**：读取 Linter 诊断信息。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `paths` | array | 否 | 文件或目录路径；不传则读取整个工作区 |

**示例**：
```
<invoke name="ReadLints">
<parameter name="paths">["app/src/main/java/com/example/MainActivity.kt"]</parameter>
</invoke>
```

---

### 2.9 EditNotebook

**功能**：编辑 Jupyter Notebook 的 cell。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `target_notebook` | string | 是 | 笔记本文件路径 |
| `cell_idx` | integer | 是 | cell 索引（从 0 开始） |
| `is_new_cell` | boolean | 是 | 是否新建 cell |
| `cell_language` | string | 是 | 语言：`python`/`markdown`/`javascript`/`typescript`/`r`/`sql`/`shell`/`raw`/`other` |
| `old_string` | string | 是 | 要替换的内容（编辑已有 cell 时） |
| `new_string` | string | 是 | 替换后的内容或新 cell 内容 |

**示例**：
```
<invoke name="EditNotebook">
<parameter name="target_notebook">analysis.ipynb</parameter>
<parameter name="cell_idx">0</parameter>
<parameter name="is_new_cell">false</parameter>
<parameter name="cell_language">python</parameter>
<parameter name="old_string">print("hello")</parameter>
<parameter name="new_string">print("hello world")</parameter>
</invoke>
```

---

### 2.10 TodoWrite

**功能**：管理待办任务列表。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `todos` | array | 是 | 任务列表，每项含 `id`、`content`、`status` |
| `merge` | boolean | 是 | 是否与现有任务合并 |

**status 取值**：`pending` | `in_progress` | `completed` | `cancelled`

**示例**：
```
<invoke name="TodoWrite">
<parameter name="todos">[{"id": "1", "content": "实现登录功能", "status": "in_progress"}, {"id": "2", "content": "编写测试", "status": "pending"}]</parameter>
<parameter name="merge">false</parameter>
</invoke>
```

---

### 2.11 WebSearch

**功能**：网络搜索。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `search_term` | string | 是 | 搜索关键词 |
| `explanation` | string | 否 | 搜索目的说明 |

**示例**：
```
<invoke name="WebSearch">
<parameter name="search_term">Kotlin Coroutines Flow 2024</parameter>
<parameter name="explanation">查找 Kotlin Flow 最新用法</parameter>
</invoke>
```

---

### 2.12 mcp_web_fetch

**功能**：抓取网页内容（只读）。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `url` | string | 是 | 完整 URL |

**示例**：
```
<invoke name="mcp_web_fetch">
<parameter name="url">https://example.com/docs</parameter>
</invoke>
```

---

### 2.13 GenerateImage

**功能**：根据描述生成图片。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `description` | string | 是 | 图片描述 |
| `filename` | string | 否 | 输出文件名 |
| `reference_image_paths` | array | 否 | 参考图片路径 |

**示例**：
```
<invoke name="GenerateImage">
<parameter name="description">Minimal app icon, flat vector style, blue gradient</parameter>
<parameter name="filename">app_icon.png</parameter>
</invoke>
```

---

### 2.14 Shell

**功能**：执行终端命令。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `command` | string | 是 | 要执行的命令 |
| `working_directory` | string | 否 | 工作目录，默认当前目录 |
| `timeout` | number | 否 | 超时时间（毫秒），默认 30000 |
| `is_background` | boolean | 否 | 是否后台运行 |
| `description` | string | 否 | 命令简短描述（5–10 词） |

**示例**：
```
<invoke name="Shell">
<parameter name="command">./gradlew assembleRelease</parameter>
<parameter name="working_directory">d:\Jasmine</parameter>
<parameter name="timeout">120000</parameter>
<parameter name="description">构建 Release APK</parameter>
</invoke>
```

---

### 2.15 mcp_task

**功能**：启动子代理处理复杂任务。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `description` | string | 是 | 任务简述（3–5 词） |
| `prompt` | string | 是 | 详细任务描述（子代理无主对话上下文） |
| `subagent_type` | string | 是 | `generalPurpose` / `explore` / `shell` |
| `model` | string | 否 | 如 `fast` |
| `resume` | string | 否 | 恢复之前的 Agent ID |
| `readonly` | boolean | 否 | 只读模式 |
| `attachments` | array | 否 | 附加文件路径（如视频） |
| `run_in_background` | boolean | 否 | 是否后台运行 |

**subagent_type 说明**：
- `generalPurpose`：通用，研究、搜索、多步骤任务
- `explore`：代码库探索，可指定 thoroughness（quick/medium/very thorough）
- `shell`：命令执行

**示例**：
```
<invoke name="mcp_task">
<parameter name="description">探索 API 端点定义</parameter>
<parameter name="prompt">在 app/src/main 目录下查找所有 REST API 端点的定义，列出路径和对应的 Controller/Activity...</parameter>
<parameter name="subagent_type">explore</parameter>
</invoke>
```

---

## 三、工具总览（按字母）

| 工具 | 主要用途 |
|------|----------|
| Delete | 删除文件 |
| EditNotebook | 编辑 Jupyter Notebook |
| GenerateImage | 生成图片 |
| Glob | 按模式查找文件 |
| Grep | 文本/正则搜索 |
| mcp_task | 启动子代理 |
| mcp_web_fetch | 抓取网页 |
| Read | 读取文件 |
| ReadLints | 读取 Linter 诊断 |
| SemanticSearch | 语义搜索代码 |
| Shell | 执行终端命令 |
| StrReplace | 字符串替换 |
| TodoWrite | 管理待办 |
| WebSearch | 网络搜索 |
| Write | 写入文件 |

---

## 四、使用建议

1. **优先专用工具**：读文件用 Read，搜索用 Grep/SemanticSearch，编辑用 StrReplace/Write
2. **StrReplace**：仅精确匹配，不支持正则；注意编码问题
3. **Shell**：适合构建、git、安装依赖、运行测试
4. **mcp_task**：窄问题用主 Agent 工具；需广泛探索用 `explore`；可并行启动，建议不超过 4 个

---

*文档生成时间：2026-03*
