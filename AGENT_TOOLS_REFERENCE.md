# Agent 完整工具清单

本文档列出当前 Agent 可用的**全部**工具，包括子代理（Subagent）类型，便于查阅。

---

## 一、文件操作

| 工具名 | 功能 | 主要参数 |
|-------|------|----------|
| **Read** | 读取文件内容 | `path`, `offset`, `limit`；可读取图片 |
| **Write** | 写入/覆盖文件 | `path`, `contents` |
| **StrReplace** | 精确字符串替换 | `path`, `old_string`, `new_string`, `replace_all` |
| **Delete** | 删除文件 | `path` |
| **Glob** | 按 glob 模式查找文件 | `glob_pattern`, `target_directory` |
| **Grep** | 文本/正则搜索 | `pattern`, `path`, `output_mode`, `-A/-B/-C`, `multiline` 等 |

---

## 二、代码搜索与理解

| 工具名 | 功能 | 主要参数 |
|-------|------|----------|
| **SemanticSearch** | 语义搜索（按含义查代码） | `query`, `target_directories`, `num_results` |
| **ReadLints** | 读取 Linter 诊断 | `paths`（可选） |

---

## 三、编辑与任务

| 工具名 | 功能 | 主要参数 |
|-------|------|----------|
| **EditNotebook** | 编辑 Jupyter Notebook 的 cell | `target_notebook`, `cell_idx`, `is_new_cell`, `cell_language`, `old_string`, `new_string` |
| **TodoWrite** | 管理待办任务列表 | `todos`, `merge` |

---

## 四、网络与外部

| 工具名 | 功能 | 主要参数 |
|-------|------|----------|
| **WebSearch** | 网络搜索 | `search_term`, `explanation` |
| **mcp_web_fetch** | 抓取网页内容（只读） | `url` |

---

## 五、其他

| 工具名 | 功能 | 主要参数 |
|-------|------|----------|
| **GenerateImage** | 根据描述生成图片 | `description`, `filename`, `reference_image_paths` |
| **Shell** | 执行终端命令 | `command`, `working_directory`, `timeout`, `is_background` |

---

## 六、子代理（mcp_task）

用于启动**子 Agent** 处理复杂、多步骤任务。子 Agent 在独立上下文中运行。

| 参数 | 说明 |
|------|------|
| `description` | 任务简述（3–5 词） |
| `prompt` | 详细任务描述（子 Agent 看不到主对话，需写清上下文） |
| `subagent_type` | 子代理类型（见下表） |
| `model` | 可选，如 `fast` |
| `resume` | 可选，用于恢复之前的 Agent |
| `readonly` | 可选，只读模式 |
| `attachments` | 可选，传入文件路径（如视频） |
| `run_in_background` | 可选，后台运行 |

### 子代理类型（subagent_type）

| 类型 | 用途 |
|------|------|
| **generalPurpose** | 通用：研究复杂问题、搜索代码、执行多步骤任务 |
| **explore** | 代码库探索：按模式找文件、按关键词搜代码、了解项目结构；可指定 thoroughness（quick / medium / very thorough） |
| **shell** | 命令执行：bash、git、终端操作等 |

### 使用建议

- 窄而具体的问题：直接用 Grep、Read、SemanticSearch
- 需要广泛探索代码库：用 `explore` 子代理
- 需要执行 git、构建等命令：用 `shell` 子代理或主 Agent 的 Shell 工具
- 可并行启动多个子代理（建议不超过 4 个）

---

## 七、工具总览（按字母）

| 工具 | 类别 |
|------|------|
| Delete | 文件 |
| EditNotebook | 编辑 |
| GenerateImage | 其他 |
| Glob | 文件 |
| Grep | 文件 |
| mcp_task | 子代理 |
| mcp_web_fetch | 网络 |
| Read | 文件 |
| ReadLints | 代码 |
| SemanticSearch | 代码 |
| Shell | 其他 |
| StrReplace | 文件 |
| TodoWrite | 任务 |
| WebSearch | 网络 |
| Write | 文件 |

---

## 八、使用注意事项

1. **优先用专用工具，少用 Shell**
   - 读文件 → Read
   - 搜索文本 → Grep
   - 按含义搜代码 → SemanticSearch
   - 找文件 → Glob
   - 编辑 → StrReplace / Write

2. **StrReplace** 只做精确匹配，不支持正则；编码异常可能导致匹配失败。

3. **Shell** 适合：构建、git、安装依赖、运行测试等。

4. **用户禁止脚本/命令时**：用 StrReplace 或 Write 做编辑，不要用 Shell 做批量替换。

---

*文档更新时间：2026-03*
