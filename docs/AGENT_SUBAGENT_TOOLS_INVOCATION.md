# 子代理（Subagent）工具调用方法完整参考

本文档记录子代理**全部 14 个工具**的调用格式、参数定义、限制条件及示例，禁止遗漏。

**重要说明**：
- 子代理工具集与主 Agent **不同**：子代理**不包含** `mcp_task`（无法再启动子代理）
- 子代理类型（generalPurpose / explore / shell）可能具有不同工具集；本文档基于 **generalPurpose** 子代理的完整工具集编写
- 若以 `readonly=true` 启动，子代理的写操作工具（Write、StrReplace、Delete、EditNotebook、TodoWrite）可能被限制

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

### 1.3 参数值类型

| 类型 | 传递方式 | 示例 |
|------|----------|------|
| string | 直接文本 | `<parameter name="path">app/build.gradle.kts</parameter>` |
| number | 数字文本 | `<parameter name="timeout">120000</parameter>` |
| boolean | `true`/`false` | `<parameter name="replace_all">true</parameter>` |
| array | JSON 数组 | `<parameter name="target_directories">["src/"]</parameter>` |
| object | JSON 对象 | `<parameter name="todos">[{"id":"1","content":"任务","status":"pending"}]</parameter>` |

---

## 二、工具列表及参数

### 2.1 SemanticSearch

**功能**：按语义搜索代码，不依赖精确文本匹配。

**使用注意**：精确文本/符号匹配用 Grep；单词语义搜索效果差，应优先用 Grep。

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

**功能**：文本/正则搜索，支持 ripgrep 语法。结果数量有上限，超出时可能被截断。

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
| `-i` | boolean | 否 | 忽略大小写（等同 ripgrep -i） |

**使用注意**：精确文本匹配用 Grep；按语义/含义搜索用 SemanticSearch。单词语义搜索效果差，应优先用 Grep。

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

**功能**：读取文件内容；支持图片（jpeg/jpg、png、gif、webp）。

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

**限制**：`readonly` 模式下可能不可用。

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

**使用注意**：`old_string` 需与文件内容**完全一致**（含空格、换行）；编码异常可能导致匹配失败。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `path` | string | 是 | 文件路径 |
| `old_string` | string | 是 | 被替换字符串（需精确匹配） |
| `new_string` | string | 是 | 替换后字符串 |
| `replace_all` | boolean | 否 | 是否替换全部出现，默认 false |

**限制**：`readonly` 模式下可能不可用。

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

**功能**：删除文件。文件不存在或权限拒绝时操作会失败。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `path` | string | 是 | 要删除的文件路径 |

**限制**：`readonly` 模式下可能不可用。

**示例**：
```
<invoke name="Delete">
<parameter name="path">temp/output.txt</parameter>
</invoke>
```

---

### 2.7 Glob

**功能**：按 glob 模式查找文件，按修改时间排序。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `glob_pattern` | string | 是 | 模式，如 `**/*.kt`、`**/test/**/*.ts`；不以 `**/` 开头时自动补全 |
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

**功能**：读取 Linter 诊断信息（如错误、警告）。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `paths` | array | 否 | 文件或目录路径；不传则读取整个工作区 |

**使用建议**：优先指定已编辑的文件或目录，避免全工作区扫描。

**示例**：
```
<invoke name="ReadLints">
<parameter name="paths">["app/src/main/java/com/example/MainActivity.kt"]</parameter>
</invoke>
```

---

### 2.9 EditNotebook

**功能**：编辑 Jupyter Notebook 的 cell。**仅用于** Jupyter Notebook，其他文件用 StrReplace/Write。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `target_notebook` | string | 是 | 笔记本文件路径 |
| `cell_idx` | integer | 是 | cell 索引（从 0 开始） |
| `is_new_cell` | boolean | 是 | 是否新建 cell |
| `cell_language` | string | 是 | 语言：`python`/`markdown`/`javascript`/`typescript`/`r`/`sql`/`shell`/`raw`/`other` |
| `old_string` | string | 是 | 编辑已有 cell：要替换的内容；新建 cell：传空字符串 |
| `new_string` | string | 是 | 替换后的内容或新 cell 内容 |

**限制**：`readonly` 模式下可能不可用。

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
| `todos` | array | 是 | 任务列表，每项为 `{id, content, status}` 对象 |
| `merge` | boolean | 是 | 是否与现有任务合并（false 则替换全部） |

**status 取值**：`pending` | `in_progress` | `completed` | `cancelled`

**限制**：`readonly` 模式下可能不可用。

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

**功能**：抓取网页内容（只读），返回可读 Markdown 格式。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `url` | string | 是 | 完整 URL |

**限制**：不支持认证；不支持 localhost/私有 IP；404 返回错误；不支持二进制内容（图片、PDF 等）。

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

**使用限制**：仅在用户**明确要求**生成图片时使用。不用于数据图表、图表、表格（应用代码生成，如 matplotlib、图表库）或非图片类可视化。

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

**返回值**：
- **同步执行**（`is_background` 未设置或 false）：返回命令的**完整** stdout + stderr 输出；超时则返回超时前已产生的输出
- **后台执行**（`is_background` 为 true）：不等待命令结束，返回任务启动状态；完整输出写入 `terminals` 目录对应文件

**平台注意**：Windows PowerShell 不支持 `&&` 连接命令，使用 `;` 替代。

**terminals 目录**（命令输出持久化）：
- 路径：`C:\Users\<用户>\.cursor\projects\<项目>/terminals`
- 每个终端会话对应一个文本文件（如 `3.txt`）
- 文件内容：`pid`、`cwd`、`last_command`、`exit_code`、**完整输出**
- 用途：检查是否有命令在运行、查看命令完整输出
- 查看元数据：`head -n 10 *.txt`（在 terminals 目录下）

**限制**：`readonly` 模式下可能不可用（取决于子代理类型配置）。

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

## 三、工具总览（按字母）

| 工具 | 主要用途 |
|------|----------|
| Delete | 删除文件 |
| EditNotebook | 编辑 Jupyter Notebook |
| GenerateImage | 生成图片 |
| Glob | 按模式查找文件 |
| Grep | 文本/正则搜索 |
| mcp_web_fetch | 抓取网页 |
| Read | 读取文件 |
| ReadLints | 读取 Linter 诊断 |
| SemanticSearch | 语义搜索代码 |
| Shell | 执行终端命令 |
| StrReplace | 字符串替换 |
| TodoWrite | 管理待办 |
| WebSearch | 网络搜索 |
| Write | 写入文件 |

**子代理与主 Agent 工具差异**：子代理**无** `mcp_task`，无法启动下级子代理。

---

## 四、使用建议

1. **优先专用工具**：读文件用 Read，搜索用 Grep/SemanticSearch，编辑用 StrReplace/Write
2. **StrReplace**：仅精确匹配，不支持正则；注意编码问题
3. **Shell**：适合构建、git、安装依赖、运行测试
4. **子代理限制**：无法再启动子代理；复杂多步骤任务应由主 Agent 通过 mcp_task 分配
5. **readonly 模式**：若以只读模式启动，Write、StrReplace、Delete、EditNotebook、TodoWrite、Shell 可能不可用

---

## 五、附录：terminals 目录

**路径**：`C:\Users\<用户名>\.cursor\projects\<项目名>/terminals`

**用途**：存储终端会话的持久化输出，用于查看 Shell 工具或 IDE 终端的命令执行结果。

**文件格式**：每个终端会话对应一个文本文件（如 `3.txt`），包含：
- `pid`：进程 ID
- `cwd`：当前工作目录
- `last_command`：最后执行的命令
- `exit_code`：退出码
- 完整 stdout/stderr 输出

**查看方式**：
- 用 Read 工具读取对应 `.txt` 文件
- 在 terminals 目录下执行 `head -n 10 *.txt` 可快速查看各会话元数据

**Shell 返回值与 terminals 的关系**：
- 同步 Shell 调用：工具直接返回完整输出，terminals 文件也会更新
- 后台 Shell 调用：工具不等待完成，完整输出仅保存在 terminals 文件中

---

## 六、附录：agent_transcripts 目录

**路径**：`C:\Users\<用户名>\.cursor\projects\<项目名>/agent-transcripts`

**用途**：存储历史对话（Agent transcripts），供引用。

**文件格式**：`<uuid>.jsonl`

**引用规则**（主 Agent 适用）：
- 引用格式：`[标题（≤6 词）](uuid)`，不含 `.jsonl` 后缀
- 仅可引用父对话的 transcript，**不可引用子代理的 transcript**
- 子代理通常不直接访问或引用 agent_transcripts

---

## 七、工具使用禁忌速查

| 场景 | 应做 | 禁做 |
|------|------|------|
| 读文件 | Read | Shell cat/type |
| 搜索文本 | Grep | Shell grep/find |
| 按含义搜代码 | SemanticSearch | Grep（语义搜索） |
| 编辑文件 | StrReplace/Write | Shell sed/awk |
| 精确替换 | StrReplace（old_string 完全匹配） | 正则替换（不支持） |
| 数据图表 | 代码生成（matplotlib 等） | GenerateImage |
| 用户明确要图片 | GenerateImage | 忽略请求 |
| Windows 多命令 | `;` 连接 | `&&`（PowerShell 不支持） |
| 启动子代理 | 无（子代理无此能力） | 调用 mcp_task（不存在） |

---

*文档生成时间：2026-03*
*对应子代理类型：generalPurpose（explore/shell 类型工具集可能不同）*
