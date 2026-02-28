# Kiro 工具定义原始信息

这是 Kiro 系统通过函数定义（function definitions）发送给我的工具信息。

---

## 工具定义传输格式

系统在每次对话开始时，通过 `<functions>` XML 标签发送所有可用工具的定义：

```xml
<functions>
<function>{JSON Schema 格式的工具定义}</function>
<function>{另一个工具定义}</function>
...
</functions>
```

每个工具定义包含：
- `name`: 工具名称（我调用时使用）
- `description`: 工具描述、使用规则、示例
- `parameters`: JSON Schema 格式的参数定义
  - `properties`: 所有参数及其类型、描述
  - `required`: 必需参数列表
  - `additionalProperties`: 是否允许额外参数

---

## 当前会话可用工具总览

### Shell 执行类（4 个）
1. `executePwsh` - 执行 PowerShell/CMD 命令
2. `controlPwshProcess` - 启动/停止后台进程
3. `listProcesses` - 列出所有后台进程
4. `getProcessOutput` - 读取进程输出

### 文件系统类（10 个）
5. `listDirectory` - 列出目录内容
6. `readFile` - 读取单个文件
7. `readMultipleFiles` - 读取多个文件
8. `readCode` - 智能读取代码（AST 分析）
9. `fsWrite` - 创建/覆盖文件
10. `fsAppend` - 追加内容到文件
11. `deleteFile` - 删除文件
12. `fileSearch` - 模糊搜索文件名
13. `grepSearch` - 正则搜索文件内容
14. `getDiagnostics` - 获取代码诊断信息

### 代码编辑类（4 个）
15. `editCode` - AST 级别代码编辑
16. `strReplace` - 字符串替换
17. `semanticRename` - 语义重命名（自动更新引用）
18. `smartRelocate` - 移动文件（自动更新 import）

### 网络类（3 个）
19. `remote_web_search` - 网络搜索
20. `webFetch` - 抓取网页内容
21. `mcp_fetch_fetch` - 获取 URL 内容转 Markdown

### GitHub 类（18 个）
22. `mcp_github_create_repository` - 创建仓库
23. `mcp_github_create_pull_request` - 创建 PR
24. `mcp_github_merge_pull_request` - 合并 PR
25. `mcp_github_create_issue` - 创建 Issue
26. `mcp_github_list_issues` - 列出 Issue
27. `mcp_github_update_issue` - 更新 Issue
28. `mcp_github_add_issue_comment` - 添加 Issue 评论
29. `mcp_github_get_issue` - 获取 Issue 详情
30. `mcp_github_get_pull_request` - 获取 PR 详情
31. `mcp_github_list_pull_requests` - 列出 PR
32. `mcp_github_get_pull_request_files` - 获取 PR 文件变更
33. `mcp_github_get_pull_request_status` - 获取 PR 状态
34. `mcp_github_create_pull_request_review` - 创建 PR 审查
35. `mcp_github_push_files` - 批量推送文件
36. `mcp_github_create_or_update_file` - 创建/更新单个文件
37. `mcp_github_get_file_contents` - 获取文件内容
38. `mcp_github_create_branch` - 创建分支
39. `mcp_github_fork_repository` - Fork 仓库
40. `mcp_github_search_repositories` - 搜索仓库
41. `mcp_github_search_code` - 搜索代码
42. `mcp_github_search_issues` - 搜索 Issue/PR
43. `mcp_github_search_users` - 搜索用户
44. `mcp_github_list_commits` - 列出提交

### Chrome DevTools 类（32 个）
45. `mcp_chrome_devtools_new_page` - 打开新页面
46. `mcp_chrome_devtools_list_pages` - 列出页面
47. `mcp_chrome_devtools_select_page` - 选择页面
48. `mcp_chrome_devtools_close_page` - 关闭页面
49. `mcp_chrome_devtools_navigate_page` - 导航页面
50. `mcp_chrome_devtools_take_snapshot` - 获取页面快照
51. `mcp_chrome_devtools_take_screenshot` - 截图
52. `mcp_chrome_devtools_click` - 点击元素
53. `mcp_chrome_devtools_fill` - 填写输入框
54. `mcp_chrome_devtools_fill_form` - 批量填写表单
55. `mcp_chrome_devtools_press_key` - 按键
56. `mcp_chrome_devtools_type_text` - 输入文本
57. `mcp_chrome_devtools_hover` - 悬停
58. `mcp_chrome_devtools_drag` - 拖拽
59. `mcp_chrome_devtools_evaluate_script` - 执行 JavaScript
60. `mcp_chrome_devtools_list_console_messages` - 列出控制台消息
61. `mcp_chrome_devtools_get_console_message` - 获取控制台消息详情
62. `mcp_chrome_devtools_list_network_requests` - 列出网络请求
63. `mcp_chrome_devtools_get_network_request` - 获取网络请求详情
64. `mcp_chrome_devtools_emulate` - 模拟设备/网络
65. `mcp_chrome_devtools_resize_page` - 调整页面尺寸
66. `mcp_chrome_devtools_performance_start_trace` - 开始性能录制
67. `mcp_chrome_devtools_performance_stop_trace` - 停止性能录制
68. `mcp_chrome_devtools_performance_analyze_insight` - 分析性能洞察
69. `mcp_chrome_devtools_take_memory_snapshot` - 内存快照
70. `mcp_chrome_devtools_handle_dialog` - 处理弹窗
71. `mcp_chrome_devtools_upload_file` - 上传文件
72. `mcp_chrome_devtools_wait_for` - 等待文本出现
73. `mcp_chrome_devtools_update_pull_request_branch` - 更新 PR 分支
74. `mcp_chrome_devtools_get_pull_request_comments` - 获取 PR 评论
75. `mcp_chrome_devtools_get_pull_request_reviews` - 获取 PR 审查

### Powers 类（1 个）
76. `kiroPowers` - Powers 管理工具（list/activate/use/readSteering/configure）

### 子代理类（1 个）
77. `invokeSubAgent` - 调用子代理（context-gatherer/general-task-execution/custom-agent-creator）

### Hooks 类（1 个）
78. `createHook` - 创建自动化钩子

### Steering 类（1 个）
79. `discloseContext` - 激活 skills 或 steering 文件

---

## 关键工具完整定义示例

### executePwsh

```json
{
  "name": "executePwsh",
  "description": "Execute the specified shell (CMD or Powershell) command.\\n\\n# Rules\\n- Avoid using cli commands for search and discovery like cat, find, grep, ls, and instead use the grepSearch, fileSearch, readFile, and readMultipleFiles tools\\n- Avoid using cli commands for file writing like mkdir or piping, instead using fsWrite (folders are managed for you)\\n- NEVER use the 'cd' command! If you wish to run a command in a subdirectory of the workspace, provide the relative path in the 'cwd' parameter.\\n- USING THE 'cd' command will result in a failure.\\n- AVOID long-running commands like development servers (npm run dev, yarn start), build watchers (webpack --watch), or interactive commands (vim, nano). These can block execution and cause issues. Run them using the controlPwshProcess tool with action \\"start\\" instead.\\n- If you are certain a command will complete quickly despite triggering a warning, you can use ignoreWarning: true to bypass the check.\\n- When using paths directly in the commands, ensure they are relative to the current working directory. BAD: cwd: src, command: mkdir src/tests GOOD: cwd: src, command mkdir tests\\n",
  "parameters": {
    "$schema": "http://json-schema.org/draft-07/schema#",
    "type": "object",
    "properties": {
      "command": {
        "type": "string",
        "description": "Shell command to execute"
      },
      "cwd": {
        "type": ["string", "null"],
        "description": "Current working directory. The command will run in this directory. Defaults to workspace root if omitted."
      },
      "timeout": {
        "type": ["number", "null"],
        "description": "Optional timeout in milliseconds for command execution. If not specified, commands will run without a timeout (infinite). If the command exceeds the specified timeout, the latest output will be returned and the agent can continue."
      },
      "ignoreWarning": {
        "type": ["boolean", "null"],
        "description": "Set to true to bypass long-running command warnings. Use only when you are certain the command will complete quickly."
      },
      "warning": {
        "type": ["string", "null"],
        "description": "Optional warning to display to the user alongside the command execution. Only use this field when running property based tests or running test suites that contain property based tests"
      }
    },
    "required": ["command"],
    "additionalProperties": false
  }
}
```

### readCode

```json
{
  "name": "readCode",
  "description": "Read and analyze code files. For small files (<10k chars), returns full content. For larger files, uses AST parsing to extract signatures.\\n\\nIMPORTANT: Prefer this tool over readFile for code files unless you need specific line ranges. This tool intelligently handles file size and provides structured code analysis.\\n\\nCORE FEATURES:\\n• Auto-detects file size and chooses best approach\\n• Small files: Returns complete file content\\n• Large files: Extracts function/class signatures via AST\\n• Fuzzy search for symbols (classes, functions, methods)\\n• Scoped method search (Class.method syntax)\\n• Supports: classes, functions, methods, arrow functions, generators\\n\\nBEHAVIOR:\\n• File <10k chars: Returns full file content (selector ignored)\\n• File ≥10k chars + no selector: Returns all signatures\\n• File ≥10k chars + selector: Searches for selector, shows implementation\\n• Directory: Returns signatures from code files\\n\\nSUPPORTED PATTERNS:\\n• Traditional functions: function foo() {}\\n• Arrow functions: const foo = () => {}\\n• Async functions: async function foo() {}, const foo = async () => {}\\n• Generator functions: function* foo() {}, *methodName() {}, async *methodName() {}\\n• Class properties with arrow functions: filter = (x) => x\\n• Getters/setters: get status() {}, set status(v) {}\\n• Abstract classes: abstract class Base {}\\n• Nested classes: class Outer { static Inner = class {} }\\n• Enums: enum Status { Active, Inactive }\\n• Namespaces: namespace Utils { export class Helper {} }\\n• Interfaces: interface IData { method(): void }\\n• Class methods and constructors\\n• Scoped lookup: ClassName.methodName (single-level only, e.g., MyClass.method)\\n\\nNOTE: Use readFile with line ranges for not supported patterns.\\n",
  "parameters": {
    "$schema": "http://json-schema.org/draft-07/schema#",
    "type": "object",
    "properties": {
      "path": {
        "type": "string",
        "description": "File or directory path to analyze"
      },
      "selector": {
        "type": ["string", "null"],
        "description": "Symbol name to search (supports Class.method syntax for scoped search)"
      },
      "language": {
        "type": "string",
        "default": "auto",
        "description": "Programming language (auto-detected if not specified)"
      },
      "explanation": {
        "type": "string",
        "description": "One sentence explanation as to why this tool is being used, and how it contributes to the goal."
      }
    },
    "required": ["path", "explanation"],
    "additionalProperties": false
  }
}
```

### editCode

```json
{
  "name": "editCode",
  "description": "**PRIMARY TOOL FOR CODE EDITS** - AST-based with fuzzy matching, auto-indentation, syntax validation. Supports 27 languages.\\n\\nOPERATIONS & SELECTORS:\\n- replace_node: Replace construct using selector (ClassName, functionName, ClassName.methodName)\\n- insert_node: Add code at location (functionName inserts after; ClassName inserts INSIDE class at end; use ClassName.end or start/end for positioning)\\n- delete_node: Remove construct using selector (ClassName, functionName, ClassName.methodName)\\n- replace_in_node: Replace content within construct (selector + old_str/new_str)\\n\\nSELECTOR FORMATS:\\n- \\"functionName\\" - Module-level function\\n- \\"ClassName\\" / \\"ClassName.methodName\\" - Class or scoped method (1 level nesting only)\\n- \\"ClassName.end\\" - End of class (max depth: OuterClass.InnerClass.end)\\n- \\"start\\" / \\"end\\" - File positions (insert_node ONLY, \\"start\\" inserts after file headers: shebang, comments, docstrings, package declarations, and imports)\\n- \\"propertyName\\", \\"methodName\\" - Unscoped names for object literals/nested methods\\n\\nCRITICAL RULES:\\n- INVALID: replace_node/delete_node + \\"start\\"/\\"end\\" (use insert_node or fsWrite)\\n- Use scoped selectors (ClassName.methodName) for precision\\n- Object literals: Unscoped names only, no .end selector\\n- replace_in_node: old_str must be unique and minimal\\n- PARALLEL CALLS: Invoke multiple times simultaneously for independent edits\\n- delete_node doesn't check references - verify before deleting\\n\\nFALLBACK (use old_str/new_str instead of operation/selector):\\n- Arrow function class properties: class Foo { bar = () => {} }\\n- Nested functions, lambda attributes, complex destructuring\\n- Deep nesting beyond 1 level (Outer.Inner.Deep.method)",
  "parameters": {
    "$schema": "http://json-schema.org/draft-07/schema#",
    "type": "object",
    "properties": {
      "path": {
        "type": "string",
        "description": "File path to modify"
      },
      "operation": {
        "type": "string",
        "enum": ["replace_node", "insert_node", "delete_node", "replace_in_node"],
        "description": "AST operation type. If omitted, falls back to string replacement"
      },
      "selector": {
        "type": "string",
        "description": "Target name or signature (ClassName, functionName, ClassName.methodName, start, end)"
      },
      "replacement": {
        "type": "string",
        "description": "New code for replace_node/insert_node operations"
      },
      "old_str": {
        "type": "string",
        "description": "Exact string to find (for replace_in_node or fallback)"
      },
      "new_str": {
        "type": "string",
        "description": "Replacement string (for replace_in_node or fallback)"
      },
      "language": {
        "type": "string",
        "default": "auto",
        "description": "Programming language (auto-detected if not specified)"
      }
    },
    "required": ["path"],
    "additionalProperties": false
  }
}
```

### invokeSubAgent

```json
{
  "name": "invokeSubAgent",
  "description": "Invoke a specialized agent to handle a delegated task.\\n\\nAvailable agents:\\n- general-task-execution: General-purpose sub-agent with access to all tools for executing arbitrary tasks\\n- context-gatherer: Analyzes repository structure to identify relevant files and content sections needed to address a user issue. Uses efficient exploration to provide focused context for problem-solving. Use for repository understanding, bug investigation, or when you need to understand how components interact before making changes. Do not use when you already know which specific files need modification.\\n- custom-agent-creator: Specialized agent for creating and configuring new custom agents\\n\\nThe agent will execute autonomously to complete assigned task and return the result to you.",
  "parameters": {
    "$schema": "http://json-schema.org/draft-07/schema#",
    "type": "object",
    "properties": {
      "name": {
        "type": "string",
        "minLength": 1,
        "description": "name of the agent to invoke (required)"
      },
      "prompt": {
        "type": "string",
        "description": "The instruction or question for the agent. This should be a clear, specific task description that the agent will execute."
      },
      "explanation": {
        "type": "string",
        "description": "One or two sentences explaining why this tool is being used and how it contributes to the goal. Describe what specific task is being delegated and why a agent is appropriate."
      }
    },
    "required": ["name", "prompt", "explanation"],
    "additionalProperties": false
  }
}
```

### kiroPowers

```json
{
  "name": "kiroPowers",
  "description": "Tool to manage and use Kiro Powers.\\n\\n**About Powers:** Powers package documentation, workflow guides (steering files), and optionally MCP servers. When a power includes MCP servers, their tools are accessed through this interface rather than exposed directly, loading tool definitions and more detailed instructions via \\"activate\\". This keeps your context focused while providing full access to capabilities on-demand. This approach provides:\\n\\n- **Minimal context**: See 5 actions for all powers instead of dozens of tools per power\\n- **Structured discovery**: Use \\"activate\\" to discover what capabilities a power provides on-demand - returns comprehensive documentation (POWER.md content), all available tools grouped by MCP server with their descriptions and input schemas, and available steering files for guided workflows\\n- **Full functionality**: \\"activate\\" provides all information needed to effectively use a power - documentation for understanding, steering file lists for workflows, and complete tool schemas with parameters for MCP servers contained in the power, enabling execution through the \\"use\\" action\\n- **Guided workflows**: POWER.md files and steering guides provide context for optimal usage\\n\\n**PROACTIVE POWER ACTIVATION:** When you see words or topics in the user's message that match the keywords for any installed power, you should **strongly consider activating that power immediately**. The keywords indicate the power's domain of expertise. For example, if a power has keywords like \\"docs\\", \\"documentation\\", \\"api\\" and the user asks about documentation, you should proactively activate that power to access its capabilities.\\n\\n**Currently Installed Powers:**\\n\\n• **supabase-hosted**\\n  Build fullstack applications with Supabase's Postgres database, authentication, storage, and real-time subscriptions\\n  Keywords: database, postgres, auth, storage, realtime, backend, supabase, rls   MCP Servers: supabase\\n\\n• **figma**\\n  Connect Figma designs to code components - automatically generate design system rules, map UI components to Figma designs, and maintain design-code consistency\\n  Keywords: ui, design, code, layout, mockup, frame, component, frontend   MCP Servers: figma\\n\\n\\n**IMPORTANT: Call action=\\"activate\\" with a powerName before using any power to understand its tools and parameters.**\\n\\n\\n**Important Note:** If the user asks about building or creating custom powers, check if the \\"Build a Power\\" power is installed in the list above. If not installed, use action=\\"configure\\" to open the configure power panel and ask the user to install the \\"Build a Power\\" power from the panel.\\n\\n# ACTIONS:\\n\\n1. **LIST** (\\"list\\") - See all installed powers\\n   **Parameters:** None\\n\\n   **Returns:** Formatted list with:\\n   - **name**: The power's identifier (used in other actions)\\n   - **description**: What the power does\\n   - **keywords**: Search terms describing the power's capabilities\\n   - **MCP servers**: Backend servers that provide the power's tools\\n\\n   **When to use:**\\n   - To discover what powers are currently installed\\n   - To find power names for use in other actions\\n   - To see what capabilities are available\\n\\n   **CRITICAL - KEYWORD MATCHING:** When you see ANY words in the user's message that match a power's keywords, you should **immediately and proactively activate that power**. Keywords are specifically chosen to indicate the power's domain. Don't wait for the user to explicitly ask - if they mention topics related to the keywords, activate the power right away. Examples:\\n   - User mentions \\"docs\\" or \\"documentation\\" → If a power has \\"docs\\" keywords, activate it immediately\\n   - User asks about \\"weather\\" → If a power has \\"weather\\", \\"forecast\\" keywords, activate it\\n   - User talks about \\"database\\" or \\"sql\\" → If a power has those keywords, activate it\\n\\n   The keyword match is your strongest signal to activate a power proactively!\\n\\n   **Example:** action=\\"list\\"\\n\\n2. **ACTIVATE** (\\"activate\\") - **IMPORTANT: ALWAYS call this FIRST when you need to use a power!**\\n   **Parameters:**\\n   - powerName (required): Name of the power to activate\\n\\n   **Returns:** Comprehensive documentation including:\\n   - overview: Complete POWER.md content with all documentation\\n   - toolsByServer: All MCP tools grouped by server with descriptions and input schemas\\n   - steeringFiles: List of available detailed workflow guides\\n   - powerMdFound: Whether POWER.md documentation was found\\n   - Metadata: powerName, displayName, keywords, description\\n\\n   Activating a power before using it helps you understand the correct tool names, required parameters, and optimal workflows needed to successfully use the power.\\n\\n   **Example:** action=\\"activate\\", powerName=\\"weather-power\\"\\n\\n3. **USE** (\\"use\\") - **IMPORTANT: Call action=\\"activate\\" FIRST before using this action!**\\n   **Parameters:**\\n   - powerName (required): The power to use\\n   - serverName (required): The MCP server within the power - get from toolsByServer keys in activate response\\n   - toolName (required): Specific tool within the server - get from toolsByServer[serverName] array in activate response\\n   - arguments (required): Tool parameters matching the inputSchema from activate response\\n\\n   **Returns:** Tool execution result from MCP server\\n\\n   **Workflow:**\\n   1. Call action=\\"activate\\" with powerName to get the power's toolsByServer map\\n   2. Review toolsByServer to identify which server has your desired tool\\n   3. Extract the serverName (key) and toolName from toolsByServer\\n   4. Check the tool's inputSchema to understand required parameters\\n   5. Call action=\\"use\\" with powerName, serverName, toolName, and arguments\\n\\n   **CRITICAL: You MUST provide serverName from the activate response. Powers can have multiple MCP servers, and tools may exist on different servers.**\\n\\n   **Example:** action=\\"use\\", powerName=\\"weather-power\\", serverName=\\"weather-api\\", toolName=\\"get_forecast\\", arguments={\\"location\\":\\"Seattle\\",\\"units\\":\\"imperial\\"}\\n\\n4. **READ_STEERING** (\\"readSteering\\") - Get detailed workflow guides\\n   **Parameters:**\\n   - powerName (required): Name of the power\\n   - steeringFile (required): Steering file name (including .md extension) - must be one from steeringFiles array in activate response\\n\\n   **Returns:** Markdown content with detailed instructions for specific workflows, advanced patterns, or specialized use cases.\\n\\n   **When to use:** After calling action=\\"activate\\" to get the steeringFiles array, call this action to read a specific steering file for detailed workflow guidance.\\n\\n   **Example:** action=\\"readSteering\\", powerName=\\"weather-power\\", steeringFile=\\"getting-started.md\\"\\n\\n5. **CONFIGURE** (\\"configure\\") - Open powers management panel\\n   **Parameters:** None\\n\\n   Opens the Powers side panel where users can browse, install, and manage powers through a visual interface. Returns a success confirmation message.\\n\\n   **When to use:**\\n   - When the user wants to install new powers\\n   - When the user wants to browse or discover available powers\\n   - When the user asks to see available powers in a UI\\n   - When the user wants to manage their installed powers\\n\\n   **IMPORTANT:** If the user asks to install a power or browse available powers, call this action immediately to open the management panel.\\n\\n   **Example:** action=\\"configure\\"\\n\\n# CRITICAL RULES:\\n- **NEVER call action=\\"use\\" without calling action=\\"activate\\" first** - you will fail if you guess\\n- Tool names and input schemas come from the activate response (don't guess!)\\n- Review toolsByServer and inputSchema from activate response before using\\n- Power names are case-sensitive\\n- Guessing server names, tool names, or parameters will fail",
  "parameters": {
    "$schema": "http://json-schema.org/draft-07/schema#",
    "type": "object",
    "properties": {
      "action": {
        "type": "string",
        "enum": ["list", "activate", "use", "readSteering", "configure"],
        "description": "The action to perform: \\"list\\" to discover installed powers, \\"activate\\" to get documentation, \\"use\\" to execute power tools, \\"readSteering\\" for guides, \\"configure\\" to open config file"
      },
      "powerName": {
        "type": ["string", "null"],
        "description": "Name of the power (required for activate, use, readSteering actions; not used for list or configure)"
      },
      "serverName": {
        "type": ["string", "null"],
        "description": "Name of the MCP server within the power (REQUIRED for use action - get from toolsByServer in activate response)"
      },
      "toolName": {
        "type": ["string", "null"],
        "description": "Name of the tool to execute within the power (required for use action)"
      },
      "arguments": {
        "type": ["object", "null"],
        "additionalProperties": {},
        "description": "Arguments object for the tool being called (use action only, get schema from activate action)"
      },
      "steeringFile": {
        "type": ["string", "null"],
        "description": "Name of the steering file to read (REQUIRED for readSteering action - get from steeringFiles array in activate response, including .md extension)"
      }
    },
    "required": ["action"],
    "additionalProperties": false
  }
}
```

---

## 工具调用示例

当我需要调用工具时，我会发送这样的 XML：

```xml
<function_calls>
<invoke name="readFile">
<parameter name="path">example.kt</parameter>
<parameter name="explanation">读取示例文件以了解当前实现</parameter>
</invoke>
</function_calls>
```

系统返回：

```xml
<function_results>
[文件内容]
</function_results>
```

并行调用多个工具：

```xml
<function_calls>
<invoke name="readFile">
<parameter name="path">file1.kt</parameter>
<parameter name="explanation">读取文件1</parameter>
</invoke>
<invoke name="readFile">
<parameter name="path">file2.kt</parameter>
<parameter name="explanation">读取文件2</parameter>
</invoke>
</function_calls>
```

---

## 总结

- 系统通过 `<functions>` 标签发送所有工具定义（JSON Schema 格式）
- 我通过 `<function_calls>` 和 `<invoke>` 标签调用工具
- 系统通过 `<function_results>` 返回执行结果
- 我可以在一个 `<function_calls>` 块中并行调用多个工具
- 每个工具的参数类型、必需性、描述都在 JSON Schema 中定义
- 当前会话共有 79+ 个可用工具
