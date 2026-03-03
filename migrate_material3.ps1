# Material 3 迁移脚本
# 批量替换所有 Activity 文件中的 Material 3 组件

$files = @(
    "app/src/main/java/com/lhzkml/jasmine/TraceConfigActivity.kt",
    "app/src/main/java/com/lhzkml/jasmine/PlannerConfigActivity.kt",
    "app/src/main/java/com/lhzkml/jasmine/SnapshotConfigActivity.kt",
    "app/src/main/java/com/lhzkml/jasmine/EventHandlerConfigActivity.kt",
    "app/src/main/java/com/lhzkml/jasmine/TimeoutConfigActivity.kt",
    "app/src/main/java/com/lhzkml/jasmine/AgentStrategyActivity.kt",
    "app/src/main/java/com/lhzkml/jasmine/McpServerActivity.kt",
    "app/src/main/java/com/lhzkml/jasmine/McpServerEditActivity.kt",
    "app/src/main/java/com/lhzkml/jasmine/ProviderListActivity.kt",
    "app/src/main/java/com/lhzkml/jasmine/ShellPolicyActivity.kt",
    "app/src/main/java/com/lhzkml/jasmine/ToolConfigActivity.kt"
)

foreach ($file in $files) {
    if (Test-Path $file) {
        $content = Get-Content $file -Raw
        
        # 替换导入
        $content = $content -replace 'import androidx\.compose\.material3\.\*', 'import com.lhzkml.jasmine.ui.components.*'
        
        # 添加必要的导入
        if ($content -notmatch 'import androidx\.compose\.ui\.draw\.clip') {
            $content = $content -replace '(import androidx\.compose\.ui\.Modifier)', "`$1`nimport androidx.compose.ui.draw.clip"
        }
        if ($content -notmatch 'import androidx\.compose\.ui\.text\.font\.FontWeight') {
            $content = $content -replace '(import androidx\.compose\.ui\.text\.TextStyle)', "`$1`nimport androidx.compose.ui.text.font.FontWeight"
        }
        if ($content -notmatch 'import androidx\.compose\.ui\.text\.style\.TextAlign') {
            $content = $content -replace '(import androidx\.compose\.ui\.text\.font\.FontWeight)', "`$1`nimport androidx.compose.ui.text.style.TextAlign"
        }
        
        # 替换组件
        $content = $content -replace 'TextButton\s*\(', 'CustomTextButton('
        $content = $content -replace 'Button\s*\(', 'CustomButton('
        $content = $content -replace '(?<!Custom)Text\s*\(', 'CustomText('
        $content = $content -replace 'Switch\s*\(', 'CustomSwitch('
        $content = $content -replace 'Surface\s*\(', 'Box('
        $content = $content -replace 'OutlinedButton\s*\(', 'CustomButton('
        
        # 替换 ButtonDefaults
        $content = $content -replace 'ButtonDefaults\.textButtonColors\([^)]*\)', ''
        $content = $content -replace 'colors\s*=\s*ButtonDefaults[^,)]*,?\s*', ''
        
        # 替换 SwitchDefaults
        $content = $content -replace 'colors\s*=\s*SwitchDefaults\.colors\s*\([^)]*\)', ''
        
        # 替换 MaterialTheme
        $content = $content -replace 'MaterialTheme\.shapes\.medium', 'RoundedCornerShape(8.dp)'
        
        Set-Content $file $content -NoNewline
        Write-Host "已处理: $file"
    }
}

Write-Host "迁移完成！"
