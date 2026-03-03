# 移除 Material 3 完全迁移计划

本文档详细说明如何将 Jasmine 应用从 Material 3 完全迁移到 Compose + 自定义 UI，不使用任何 Material 3 组件。

---

## 📋 目标

- ✅ 保留 `app/src/main/java/com/lhzkml/jasmine/ui/theme` 主题文件
- ❌ 移除所有 Material 3 依赖
- ❌ 移除所有 Material 3 组件使用
- ✅ 使用纯 Compose + 自定义 UI 组件

---

## 🔍 当前 Material 3 使用情况分析

### 1. 依赖项（需要移除）

**文件**: `gradle/libs.versions.toml`

```toml
# 需要移除的依赖
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
```

**文件**: `app/build.gradle.kts`

```kotlin
// 需要移除的依赖引用
implementation(libs.compose.material3)
```

### 2. 使用 Material 3 的文件统计

根据代码分析，以下 **17 个文件**使用了 Material 3 组件：

#### Activity 文件（17个）
1. `AgentStrategyActivity.kt`
2. `CompressionConfigActivity.kt`
3. `EventHandlerConfigActivity.kt`
4. `LauncherActivity.kt`
5. `McpServerActivity.kt`
6. `McpServerEditActivity.kt`
7. `PlannerConfigActivity.kt`
8. `ProviderListActivity.kt`
9. `SamplingParamsConfigActivity.kt`
10. `SettingsActivity.kt`
11. `ShellPolicyActivity.kt`
12. `SnapshotConfigActivity.kt`
13. `SystemPromptConfigActivity.kt`
14. `TimeoutConfigActivity.kt`
15. `TokenManagementActivity.kt`
16. `ToolConfigActivity.kt`
17. `TraceConfigActivity.kt`

#### UI 组件文件（4个）
18. `ui/ChatInputBar.kt`
19. `ui/ChatScreen.kt`
20. `ui/DrawerContent.kt`
21. `ui/MessageBubbles.kt`

#### 主题文件（1个）
22. `ui/theme/Theme.kt` - ✅ 已修改为自定义主题

**总计**: 21 个文件需要修改

---

## 🎨 需要替换的 Material 3 组件清单

### 常用组件及替换方案

| Material 3 组件 | 使用频率 | 自定义替换组件 | 优先级 |
|----------------|---------|--------------|--------|
| `Button` | 高 | `CustomButton` | 🔴 高 |
| `TextButton` | 高 | `CustomTextButton` | 🔴 高 |
| `Text` | 极高 | `androidx.compose.foundation.text.BasicText` 或保持使用 | 🟡 中 |
| `Surface` | 高 | `Box` + `Modifier.background()` | 🔴 高 |
| `Switch` | 中 | `CustomSwitch` | 🟠 中高 |
| `AlertDialog` | 中 | `CustomDialog` | 🟠 中高 |
| `HorizontalDivider` | 中 | `Divider` (自定义) | 🟢 低 |
| `RadioButton` | 低 | `CustomRadioButton` | 🟢 低 |
| `Checkbox` | 低 | `CustomCheckbox` | 🟢 低 |
| `DropdownMenu` | 低 | `CustomDropdownMenu` | 🟢 低 |
| `DropdownMenuItem` | 低 | `CustomDropdownMenuItem` | 🟢 低 |
| `MaterialTheme` | 极高 | `JasmineTheme` | ✅ 已完成 |
| `MaterialTheme.shapes` | 中 | 直接使用 `RoundedCornerShape` | 🟢 低 |
| `MaterialTheme.colorScheme` | 高 | `JasmineTheme.colors` | ✅ 已完成 |

---

## 🛠️ 实施步骤

### 阶段 1: 创建自定义 UI 组件库 (优先级: 🔴 高)

在 `app/src/main/java/com/lhzkml/jasmine/ui/components/` 创建以下组件：

#### 1.1 CustomButton.kt

```kotlin
package com.lhzkml.jasmine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.ui.theme.JasmineTheme

@Composable
fun CustomButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = JasmineTheme.colors.accent,
    contentColor: Color = Color.White,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.5f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
fun CustomTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = JasmineTheme.colors.accent,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    Row(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
```

#### 1.2 CustomSwitch.kt

```kotlin
package com.lhzkml.jasmine.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.ui.theme.JasmineTheme

@Composable
fun CustomSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    checkedTrackColor: Color = JasmineTheme.colors.accent,
    uncheckedTrackColor: Color = Color(0xFFE0E0E0),
    checkedThumbColor: Color = Color.White,
    uncheckedThumbColor: Color = Color.White
) {
    val interactionSource = remember { MutableInteractionSource() }
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        label = "thumbOffset"
    )
    
    Box(
        modifier = modifier
            .width(52.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (checked) checkedTrackColor else uncheckedTrackColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = { onCheckedChange(!checked) }
            )
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .offset(x = thumbOffset)
                .clip(CircleShape)
                .background(if (checked) checkedThumbColor else uncheckedThumbColor)
        )
    }
}
```

#### 1.3 CustomDialog.kt

```kotlin
package com.lhzkml.jasmine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.lhzkml.jasmine.ui.theme.JasmineTheme

@Composable
fun CustomDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(24.dp)
        ) {
            content()
        }
    }
}

@Composable
fun CustomAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    CustomDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            title?.let {
                Box(modifier = Modifier.padding(bottom = 16.dp)) {
                    it()
                }
            }
            
            text?.let {
                Box(modifier = Modifier.padding(bottom = 24.dp)) {
                    it()
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                dismissButton?.let {
                    Box(modifier = Modifier.padding(end = 8.dp)) {
                        it()
                    }
                }
                confirmButton()
            }
        }
    }
}
```

#### 1.4 CustomDivider.kt

```kotlin
package com.lhzkml.jasmine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.ui.theme.JasmineTheme

@Composable
fun HorizontalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp,
    color: Color = JasmineTheme.colors.divider
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness)
            .background(color)
    )
}

@Composable
fun VerticalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp,
    color: Color = JasmineTheme.colors.divider
) {
    Box(
        modifier = modifier
            .height(thickness)
            .background(color)
    )
}
```

#### 1.5 CustomRadioButton.kt

```kotlin
package com.lhzkml.jasmine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.ui.theme.JasmineTheme

@Composable
fun CustomRadioButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selectedColor: Color = JasmineTheme.colors.accent,
    unselectedColor: Color = Color(0xFFE0E0E0)
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .border(
                width = 2.dp,
                color = if (selected) selectedColor else unselectedColor,
                shape = CircleShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(selectedColor)
            )
        }
    }
}
```

#### 1.6 CustomCheckbox.kt

```kotlin
package com.lhzkml.jasmine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.ui.theme.JasmineTheme

@Composable
fun CustomCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    checkedColor: Color = JasmineTheme.colors.accent,
    uncheckedColor: Color = Color(0xFFE0E0E0)
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(4.dp)
    
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(shape)
            .background(if (checked) checkedColor else Color.Transparent)
            .border(
                width = 2.dp,
                color = if (checked) checkedColor else uncheckedColor,
                shape = shape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = { onCheckedChange(!checked) }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            // 可以添加勾选图标
        }
    }
}
```

---

### 阶段 2: 替换所有文件中的 Material 3 组件 (优先级: 🔴 高)

#### 2.1 替换导入语句

**查找并替换**:

```kotlin
// 移除
import androidx.compose.material3.*

// 替换为
import com.lhzkml.jasmine.ui.components.*
import com.lhzkml.jasmine.ui.theme.JasmineTheme
```

#### 2.2 组件替换映射表

| 原 Material 3 代码 | 替换为自定义组件 |
|-------------------|----------------|
| `Button(onClick = {}) { Text("Click") }` | `CustomButton(onClick = {}) { BasicText("Click") }` |
| `TextButton(onClick = {}) { Text("Click") }` | `CustomTextButton(onClick = {}) { BasicText("Click") }` |
| `Switch(checked, onCheckedChange)` | `CustomSwitch(checked, onCheckedChange)` |
| `Surface { content }` | `Box(Modifier.background(Color.White)) { content }` |
| `AlertDialog(...)` | `CustomAlertDialog(...)` |
| `HorizontalDivider()` | `HorizontalDivider()` (自定义) |
| `RadioButton(selected, onClick)` | `CustomRadioButton(selected, onClick)` |
| `Checkbox(checked, onCheckedChange)` | `CustomCheckbox(checked, onCheckedChange)` |
| `MaterialTheme.colorScheme.primary` | `JasmineTheme.colors.accent` |
| `MaterialTheme.shapes.medium` | `RoundedCornerShape(8.dp)` |

#### 2.3 Text 组件处理

Material 3 的 `Text` 组件可以保留使用，因为它来自 `androidx.compose.material3`，但功能上与 `androidx.compose.foundation.text.BasicText` 类似。

**选项 1**: 保留使用 `Text`（需要保留 material3 依赖）
**选项 2**: 全部替换为 `BasicText`（完全移除 material3）

如果选择选项 2，需要注意 `BasicText` 不支持某些 `Text` 的便捷参数，需要通过 `TextStyle` 设置。

---

### 阶段 3: 修改 build.gradle 依赖 (优先级: 🔴 高)

#### 3.1 移除 Material 3 依赖

**文件**: `gradle/libs.versions.toml`

```toml
# 删除这一行
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
```

**文件**: `app/build.gradle.kts`

```kotlin
dependencies {
    // 删除这一行
    // implementation(libs.compose.material3)
    
    // 保留这些
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    // ... 其他依赖
}
```

---

### 阶段 4: 文件修改清单 (按优先级)

#### 🔴 高优先级（核心组件，17个 Activity）

1. ✅ `ui/theme/Theme.kt` - 已完成
2. `AgentStrategyActivity.kt`
3. `CompressionConfigActivity.kt`
4. `EventHandlerConfigActivity.kt`
5. `LauncherActivity.kt`
6. `McpServerActivity.kt`
7. `McpServerEditActivity.kt`
8. `PlannerConfigActivity.kt`
9. `ProviderListActivity.kt`
10. `SamplingParamsConfigActivity.kt`
11. `SettingsActivity.kt`
12. `ShellPolicyActivity.kt`
13. `SnapshotConfigActivity.kt`
14. `SystemPromptConfigActivity.kt`
15. `TimeoutConfigActivity.kt`
16. `TokenManagementActivity.kt`
17. `ToolConfigActivity.kt`
18. `TraceConfigActivity.kt`

#### 🟠 中优先级（UI 组件，4个）

19. `ui/ChatInputBar.kt`
20. `ui/ChatScreen.kt`
21. `ui/DrawerContent.kt`
22. `ui/MessageBubbles.kt`

---

## 📝 实施建议

### 方案 A: 渐进式迁移（推荐）

1. **第一步**: 创建所有自定义组件（阶段 1）
2. **第二步**: 选择 1-2 个简单的 Activity 进行试点迁移
3. **第三步**: 验证功能正常后，批量迁移其他 Activity
4. **第四步**: 最后移除 Material 3 依赖

**优点**: 风险可控，可以逐步验证
**缺点**: 迁移周期较长

### 方案 B: 一次性迁移

1. **第一步**: 创建所有自定义组件（阶段 1）
2. **第二步**: 批量替换所有文件中的 Material 3 组件
3. **第三步**: 移除 Material 3 依赖
4. **第四步**: 修复编译错误和运行时问题

**优点**: 迁移速度快
**缺点**: 风险较高，可能出现大量问题

---

## ⚠️ 注意事项

### 1. Text 组件的选择

如果完全移除 Material 3，`Text` 组件也需要替换。建议：

**选项 A**: 创建自定义 `CustomText` 组件包装 `BasicText`

```kotlin
@Composable
fun CustomText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = JasmineTheme.colors.textPrimary,
    fontSize: TextUnit = 14.sp,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign
        )
    )
}
```

**选项 B**: 保留 `androidx.compose.ui.text.BasicText` 直接使用

### 2. 图标处理

Material 3 的图标（`Icons.Default.*`）也需要替换为：
- 自定义 SVG 图标
- 使用 `androidx.compose.ui.graphics.vector.ImageVector`
- 或使用第三方图标库

### 3. 动画和交互

Material 3 提供的 Ripple 效果等需要自己实现或移除。

### 4. 可访问性

自定义组件需要手动添加可访问性支持（`semantics`）。

---

## 📊 工作量估算

| 任务 | 文件数 | 预计工时 | 难度 |
|-----|-------|---------|------|
| 创建自定义组件库 | 6-10 个组件 | 8-16 小时 | 中 |
| 修改 Activity 文件 | 17 个 | 17-34 小时 | 中 |
| 修改 UI 组件文件 | 4 个 | 4-8 小时 | 中 |
| 测试和修复 | - | 8-16 小时 | 高 |
| **总计** | **21+ 个文件** | **37-74 小时** | **中-高** |

---

## ✅ 验收标准

1. ✅ 所有文件不再导入 `androidx.compose.material3.*`
2. ✅ `build.gradle` 中移除 Material 3 依赖
3. ✅ 应用可以正常编译和运行
4. ✅ 所有功能正常工作
5. ✅ UI 外观与之前保持一致
6. ✅ 保留 `app/src/main/java/com/lhzkml/jasmine/ui/theme` 主题文件

---

## 📅 建议时间表

### 第 1 周
- 创建自定义组件库
- 试点迁移 2-3 个简单 Activity

### 第 2 周
- 迁移剩余 Activity 文件
- 迁移 UI 组件文件

### 第 3 周
- 移除 Material 3 依赖
- 全面测试和修复问题

---

## 🔗 相关文档

- [Jetpack Compose 基础组件文档](https://developer.android.com/jetpack/compose/layouts/basics)
- [自定义 Compose 组件指南](https://developer.android.com/jetpack/compose/custom-layouts)
- [Compose 主题系统](https://developer.android.com/jetpack/compose/themes)

---

## 📌 备注

- 这是一个大型重构项目，建议分阶段实施
- 建议在开始前创建 Git 分支进行开发
- 每完成一个阶段都应该进行充分测试
- 保持代码审查和团队沟通

---

最后更新：2024年
