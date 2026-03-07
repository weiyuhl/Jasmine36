# explore 子代理工具调用方法完整参考

本文档记录 **explore** 子代理的全部工具调用格式、参数定义、限制条件及示例，禁止遗漏。

---

## 文档适用范围与工具集差异

### 对应子代理类型

本文档对应 **explore** 子代理（文件搜索/代码库探索 specialist）。

### 与 generalPurpose 子代理的工具集差异

| 对比项 | generalPurpose | explore |
|--------|----------------|---------|
| 角色定位 | 通用任务、多步骤执行 | 代码库探索、文件搜索、只读分析 |
| 工具数量 | 14 个 | 6 个**可用** + 8 个**不可用** |
| 写入/修改类工具 | 可用（readonly 模式下可限制） | **不可用**（无文件编辑权限） |
| mcp_task | 无（子代理均无） | 无 |

### explore 只读工具集说明

explore 子代理为 **READ-ONLY** 模式，职责为「搜索与分析现有代码」，**禁止**任何会改变系统状态的操作。

**可用工具（6 个）**：SemanticSearch、Grep、Glob、Read、WebSearch、mcp_web_fetch

**不可用工具（8 个）**：Write、StrReplace、Delete、EditNotebook、TodoWrite、Shell、ReadLints、GenerateImage

- **Write、StrReplace、Delete、EditNotebook**：系统提示明确「You do NOT have access to file editing tools」，调用会失败
- **TodoWrite**：explore 为快速探索型，不提供任务管理能力
- **Shell**：禁止执行会改变系统状态的命令；且应优先使用 Read、Grep、Glob 等专用工具
- **ReadLints**：explore 不负责编辑后诊断，通常不提供
- **GenerateImage**：会创建文件，与只读模式冲突

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
| number | 数字文本 | `<parameter name="num_results">10</parameter>` |
| boolean | `true`/`false` | `<parameter name="-i">true</parameter>` |
| array | JSON 数组 | `<parameter name="target_directories">["src/"]</parameter>` |

---

## 二、工具列表及参数（仅可用工具）

### 2.1 SemanticSearch

**功能**：按语义搜索代码，不依赖精确文本匹配。

**使用注意**：精确文本/符号匹配用 Grep；单词语义搜索效果差，应优先用 Grep。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `query` | string | 是 | 搜索问题（完整问句，如 "Where is user authentication handled?"） |
| `target_directories` | array | 否 | 限定目录，如 `["src/"]`；空则搜索整个仓库 |
| `num_results` | integer | 否 | 返回结果数量，默认 25，最大 25 |

**限制**：无。

**返回值**：语义匹配的代码片段及所在文件、行号。

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

**使用注意**：精确文本匹配用 Grep；按语义/含义搜索用 SemanticSearch。

**限制**：结果数量有上限，超出时可能被截断。

**返回值**：匹配行及上下文（依 output_mode 不同而不同）。

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

### 2.3 Glob

**功能**：按 glob 模式查找文件，按修改时间排序。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `glob_pattern` | string | 是 | 模式，如 `**/*.kt`、`**/test/**/*.ts`；不以 `**/` 开头时自动补全 |
| `target_directory` | string | 否 | 搜索根目录，默认工作区根目录 |

**限制**：无。

**返回值**：匹配的文件路径列表，按修改时间排序。

**示例**：
```
<invoke name="Glob">
<parameter name="glob_pattern">**/*.kt</parameter>
<parameter name="target_directory">app/src/main</parameter>
</invoke>
```

---

### 2.4 Read

**功能**：读取文件内容；支持图片（jpeg/jpg、png、gif、webp）。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `path` | string | 是 | 文件路径（绝对或相对工作区） |
| `offset` | integer | 否 | 起始行号 |
| `limit` | integer | 否 | 读取行数 |

**限制**：无。

**返回值**：文件内容；图片则返回可解析的图像数据。

**示例**：
```
<invoke name="Read">
<parameter name="path">app/build.gradle.kts</parameter>
</invoke>
```

---

### 2.5 WebSearch

**功能**：网络搜索。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `search_term` | string | 是 | 搜索关键词 |
| `explanation` | string | 否 | 搜索目的说明 |

**限制**：无。

**返回值**：搜索结果摘要。

**示例**：
```
<invoke name="WebSearch">
<parameter name="search_term">Kotlin Coroutines Flow 2024</parameter>
<parameter name="explanation">查找 Kotlin Flow 最新用法</parameter>
</invoke>
```

---

### 2.6 mcp_web_fetch

**功能**：抓取网页内容（只读），返回可读 Markdown 格式。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `url` | string | 是 | 完整 URL |

**限制**：不支持认证；不支持 localhost/私有 IP；404 返回错误；不支持二进制内容（图片、PDF 等）。

**返回值**：网页内容转成的 Markdown 文本。

**示例**：
```
<invoke name="mcp_web_fetch">
<parameter name="url">https://example.com/docs</parameter>
</invoke>
```

---

## 三、不可用工具速查（explore 子代理）

以下工具在 explore 子代理中**不可用**，调用会失败或不应使用：

| 工具 | 原因 |
|------|------|
| Write | 无文件编辑权限 |
| StrReplace | 无文件编辑权限 |
| Delete | 无文件编辑权限 |
| EditNotebook | 无文件编辑权限 |
| TodoWrite | explore 不提供任务管理 |
| Shell | 禁止改变系统状态；应优先用 Read、Grep、Glob |
| ReadLints | explore 不负责编辑后诊断 |
| GenerateImage | 会创建文件，与只读模式冲突 |
| mcp_task | 子代理均无，无法启动下级子代理 |

---

## 四、工具总览（按字母）

| 工具 | 主要用途 | explore 可用性 |
|------|----------|----------------|
| SemanticSearch | 语义搜索代码 | 可用 |
| Grep | 文本/正则搜索 | 可用 |
| Glob | 按模式查找文件 | 可用 |
| Read | 读取文件 | 可用 |
| WebSearch | 网络搜索 | 可用 |
| mcp_web_fetch | 抓取网页 | 可用 |
| Write | 写入文件 | 不可用 |
| StrReplace | 字符串替换 | 不可用 |
| Delete | 删除文件 | 不可用 |
| EditNotebook | 编辑 Jupyter Notebook | 不可用 |
| TodoWrite | 管理待办 | 不可用 |
| Shell | 执行终端命令 | 不可用 |
| ReadLints | 读取 Linter 诊断 | 不可用 |
| GenerateImage | 生成图片 | 不可用 |

---

## 五、使用建议

1. **优先专用工具**：读文件用 Read，搜索用 Grep/SemanticSearch，找文件用 Glob
2. **SemanticSearch vs Grep**：按含义搜用 SemanticSearch；精确文本/符号用 Grep
3. **Glob**：按文件名模式快速定位文件，结果按修改时间排序
4. **explore 职责**：仅做搜索与分析，不修改代码；需修改时由主 Agent 或 generalPurpose 子代理执行
5. **thoroughness**：可在 prompt 中指定 `quick` / `medium` / `very thorough` 控制探索深度

---

## 六、附录：agent_transcripts 目录

**路径**：`C:\Users\<用户名>\.cursor\projects\<项目名>/agent-transcripts`

**用途**：存储历史对话（Agent transcripts），供引用。

**引用规则**（explore 子代理）：
- 子代理通常不直接访问或引用 agent_transcripts
- 仅可引用父对话的 transcript，不可引用子代理的 transcript

---

## 七、工具使用禁忌速查

| 场景 | 应做 | 禁做 |
|------|------|------|
| 读文件 | Read | Shell cat/type |
| 搜索文本 | Grep | Shell grep/find |
| 按含义搜代码 | SemanticSearch | Grep（语义搜索） |
| 按模式找文件 | Glob | Shell find |
| 编辑文件 | 无（explore 无此能力） | Write/StrReplace（不可用） |
| 删除文件 | 无（explore 无此能力） | Delete（不可用） |
| 启动子代理 | 无（子代理无此能力） | 调用 mcp_task（不存在） |

---

*文档生成时间：2026-03*
*对应子代理类型：explore（只读工具集）*
