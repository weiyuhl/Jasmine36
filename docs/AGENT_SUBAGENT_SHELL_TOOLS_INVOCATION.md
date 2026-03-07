# Shell 子代理工具调用方法完整参考

本文档对应 **shell** 子代理，记录其**全部 1 个工具**（Shell）的调用格式、参数定义、限制条件及示例，禁止遗漏。

---

## 文档说明

### 本文档对应子代理类型

- **shell**：命令执行专用子代理，侧重 bash、git、终端操作、构建、安装依赖等

### 与 generalPurpose 子代理的工具集差异

| 对比项 | generalPurpose 子代理 | shell 子代理 |
|--------|----------------------|--------------|
| 工具数量 | 14 个 | **1 个** |
| 可用工具 | SemanticSearch、Grep、Read、Write、StrReplace、Delete、Glob、ReadLints、EditNotebook、TodoWrite、WebSearch、mcp_web_fetch、GenerateImage、Shell | **仅 Shell** |
| 文件读取 | Read 工具 | 必须通过 Shell 执行 Get-Content、type 等命令 |
| 文本搜索 | Grep、SemanticSearch | 必须通过 Shell 执行 rg、Select-String 等命令 |
| 文件编辑 | Write、StrReplace | 不可用（需主 Agent 或 generalPurpose 处理） |

### shell 子代理的定位

- **侧重**：命令执行，高效、安全地运行终端命令
- **依赖**：Shell 工具及 terminals 目录（命令输出持久化）
- **限制**：无文件读写、搜索、编辑工具，读文件或查结果需通过 Shell 命令或 terminals 目录

---

## 一、调用格式

### 1.1 标准格式（XML-like）

```
<invoke name="Shell">
<parameter name="command">要执行的命令</parameter>
<parameter name="working_directory">d:\Jasmine</parameter>
<parameter name="timeout">30000</parameter>
<parameter name="is_background">false</parameter>
<parameter name="description">命令简短描述（5-10 词）</parameter>
</invoke>
```

### 1.2 调用规则

- 每个工具调用由 `<invoke>` 包裹，`name` 指定工具名（Shell）
- 参数通过 `<parameter>` 传递，`name` 为参数名，标签内容为参数值
- 有依赖关系的多个命令需顺序执行，等待前一次返回后再发起下一次调用

### 1.3 参数值类型

| 类型 | 传递方式 | 示例 |
|------|----------|------|
| string | 直接文本 | `<parameter name="command">git status</parameter>` |
| number | 数字文本 | `<parameter name="timeout">120000</parameter>` |
| boolean | true/false | `<parameter name="is_background">false</parameter>` |

---

## 二、工具列表及参数

### 2.1 Shell（唯一工具）

**功能**：在 shell 会话中执行给定命令，支持超时与后台运行。

**使用前建议**：
1. **检查运行中的进程**：在启动 dev server 或长时间运行进程前，先列出 terminals 目录，确认是否已有相同命令在运行
2. **路径含空格**：必须用双引号包裹，如 cd "path with spaces/file.txt"
3. **多命令连接**：使用 ; 或 && 连接，不要用换行分隔（引号内换行除外）

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| command | string | **是** | 要执行的命令 |
| working_directory | string | 否 | 执行命令的工作目录（绝对路径），默认当前目录 |
| timeout | number | 否 | 超时时间（毫秒），默认 30000，最大 600000（10 分钟） |
| is_background | boolean | 否 | 是否后台运行，默认 false |
| description | string | 否 | 命令简短描述（5-10 词），便于理解用途 |

**返回值**：
- **同步执行**（is_background 未设置或 false）：返回命令的**完整** stdout + stderr 输出；超时则返回超时前已产生的输出
- **后台执行**（is_background 为 true）：不等待命令结束，返回任务启动状态；完整输出写入 terminals 目录对应文件

**限制条件**：
- 默认超时 30 秒，长任务需显式设置 timeout
- Windows PowerShell 不支持 && 连接命令，使用 ; 替代
- 路径含空格必须用双引号包裹

**使用禁忌**（针对有 Read/Grep 的 Agent；shell 子代理无此工具，需用命令替代）：
- 主 Agent / generalPurpose：读文件用 Read，不用 cat/type；搜索用 Grep，不用 grep/find
- shell 子代理：无 Read/Grep，读文件需用 Get-Content、type；搜索可用 rg、Select-String

**示例 1：同步执行简单命令**
```
<invoke name="Shell">
<parameter name="command">git status</parameter>
<parameter name="description">查看 Git 工作区状态</parameter>
</invoke>
```

**示例 2：指定工作目录与超时**
```
<invoke name="Shell">
<parameter name="command">./gradlew assembleRelease</parameter>
<parameter name="working_directory">d:\Jasmine</parameter>
<parameter name="timeout">120000</parameter>
<parameter name="description">构建 Release APK</parameter>
</invoke>
```

**示例 3：后台运行**
```
<invoke name="Shell">
<parameter name="command">npm run dev</parameter>
<parameter name="working_directory">d:\Jasmine</parameter>
<parameter name="is_background">true</parameter>
<parameter name="description">启动开发服务器</parameter>
</invoke>
```

**示例 4：多命令连接（PowerShell 用 ;）**
```
<invoke name="Shell">
<parameter name="command">cd d:\Jasmine; npm install</parameter>
<parameter name="description">进入项目目录并安装依赖</parameter>
</invoke>
```

**示例 5：路径含空格**
```
<invoke name="Shell">
<parameter name="command">Get-Content "d:\My Documents\file.txt"</parameter>
<parameter name="description">读取含空格路径的文件</parameter>
</invoke>
```

---

## 三、工具总览

| 工具 | 主要用途 |
|------|----------|
| Shell | 执行终端命令（构建、git、安装、测试等） |

**shell 子代理工具集**：仅 Shell，无 mcp_task、Read、Grep、Write 等。

---

## 四、使用建议

1. **优先使用绝对路径**：减少 cd，保持工作目录稳定
2. **长任务设置 timeout**：构建、安装等可设为 120000 或更高
3. **后台任务**：dev server 等用 is_background=true，输出在 terminals 目录查看
4. **terminals 目录**：检查是否有命令在运行、查看完整输出，可用 head -n 10 *.txt 快速查看元数据

---

## 五、附录：terminals 目录（Shell 子代理核心依赖）

**路径**：C:\Users\<用户>\.cursor\projects\<项目>/terminals

**用途**：存储终端会话的持久化输出，用于查看 Shell 工具及 IDE 终端的命令执行结果。

**文件格式**：每个终端会话对应一个文本文件（如 3.txt），包含：
- pid：进程 ID
- cwd：当前工作目录
- last_command：最后执行的命令
- exit_code：退出码
- 完整 stdout/stderr 输出

**查看方式**：
- 在 terminals 目录下执行 Get-Content *.txt -TotalCount 10 或 head -n 10 *.txt 快速查看各会话元数据
- 读取完整输出：Get-Content "C:\Users\USER228466\.cursor\projects\d-Jasmine\terminals\3.txt"

**Shell 返回值与 terminals 的关系**：
- **同步 Shell 调用**：工具直接返回完整输出，terminals 文件也会更新
- **后台 Shell 调用**：工具不等待完成，完整输出仅保存在 terminals 文件中

---

## 六、附录：agent_transcripts 目录

**路径**：C:\Users\<用户>\.cursor\projects\<项目>/agent-transcripts

**用途**：存储历史对话（Agent transcripts），供引用。

**说明**：子代理通常不直接访问或引用 agent_transcripts；仅主 Agent 可引用父对话的 transcript。

---

## 七、工具使用禁忌速查

| 场景 | 应做 | 禁做 |
|------|------|------|
| 执行命令 | Shell 工具 | 无其他选择 |
| 读文件（shell 子代理） | Shell + Get-Content/type | 无 Read 工具 |
| 搜索（shell 子代理） | Shell + rg/Select-String | 无 Grep 工具 |
| Windows 多命令 | ; 连接 | &&（PowerShell 不支持） |
| 路径含空格 | 双引号包裹 | 裸路径 |
| 启动子代理 | 无（shell 子代理无 mcp_task） | 调用 mcp_task（不存在） |
| 编辑文件 | 不可用 | 无 Write/StrReplace |

---

*文档生成时间：2026-03*
*对应子代理类型：shell*