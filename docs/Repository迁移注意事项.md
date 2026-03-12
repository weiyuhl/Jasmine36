# Repository 迁移注意事项

## Compose 状态管理关键点

### 1. 使用 `remember` 缓存 Repository 数据

在 Compose 中，所有从 Repository 读取的初始数据都应该使用 `remember` 包裹，以避免每次重组时重新读取。

**❌ 错误示例：**
```kotlin
@Composable
fun MyScreen(repository: MyRepository) {
    val currentFilter = repository.getFilter()  // 每次重组都会调用
    var enabled by remember { mutableStateOf(currentFilter.isEmpty()) }  // 但状态只初始化一次
}
```

**✅ 正确示例：**
```kotlin
@Composable
fun MyScreen(repository: MyRepository) {
    val currentFilter = remember { repository.getFilter() }  // 只在首次组合时调用
    var enabled by remember { mutableStateOf(currentFilter.isEmpty()) }
}
```

### 2. 状态初始化模式

对于可变状态，推荐使用以下模式：

```kotlin
var enabled by remember { mutableStateOf(repository.isEnabled()) }
```

对于不可变数据（如版本号），可以直接使用：

```kotlin
val appVersion = remember { repository.getAppVersion() }
```

### 3. DisposableEffect 保存数据

使用 `DisposableEffect` 在组件销毁时保存数据：

```kotlin
DisposableEffect(Unit) {
    onDispose {
        repository.setEnabled(enabled)
        repository.saveConfig(config)
    }
}
```

### 4. 实时更新状态

如果需要在用户操作时立即保存到 Repository：

```kotlin
CustomSwitch(
    checked = enabled,
    onCheckedChange = { 
        enabled = it
        repository.setEnabled(it)  // 立即保存
    }
)
```

## 已修复的问题

### TraceConfigActivity
- ✅ 修复：`currentFilter` 没有使用 `remember`
- 影响：每次重组都会重新调用 `repository.getTraceEventFilter()`

### EventHandlerConfigActivity  
- ✅ 修复：`currentFilter` 没有使用 `remember`
- 影响：每次重组都会重新调用 `repository.getEventHandlerFilter()`

## 迁移检查清单

在迁移每个 Activity 时，请检查：

- [ ] 所有从 Repository 读取的数据都使用了 `remember`
- [ ] 可变状态使用 `remember { mutableStateOf() }`
- [ ] 使用 `DisposableEffect` 在组件销毁时保存数据
- [ ] 或者在用户操作时立即调用 Repository 的 set 方法
- [ ] Activity 使用 `by inject()` 注入 Repository
- [ ] Compose Screen 通过参数接收 Repository
- [ ] AppNavigation 中使用 `koinInject()` 注入 Repository

## 测试建议

迁移完成后，应该测试：

1. 打开页面，检查是否正确显示当前配置
2. 修改配置，保存后退出
3. 重新打开页面，检查配置是否被正确保存和加载
4. 检查配置是否在其他使用该配置的地方生效
