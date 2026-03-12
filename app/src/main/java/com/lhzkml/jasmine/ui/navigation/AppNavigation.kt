package com.lhzkml.jasmine.ui.navigation

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lhzkml.jasmine.AddCustomProviderScreen
import com.lhzkml.jasmine.AgentStrategyScreen
import com.lhzkml.jasmine.config.AppConfig
import com.lhzkml.jasmine.CheckpointDetailScreen
import com.lhzkml.jasmine.CheckpointManagerScreen
import com.lhzkml.jasmine.CompressionConfigScreen
import com.lhzkml.jasmine.EventHandlerConfigScreen
import com.lhzkml.jasmine.McpServerEditScreen
import com.lhzkml.jasmine.McpServerScreen
import com.lhzkml.jasmine.PlannerConfigScreen
import com.lhzkml.jasmine.ProviderConfigScreen
import com.lhzkml.jasmine.ProviderListScreen
import com.lhzkml.jasmine.R
import com.lhzkml.jasmine.RulesScreen
import com.lhzkml.jasmine.SamplingParamsConfigScreen
import com.lhzkml.jasmine.ShellPolicyScreen
import com.lhzkml.jasmine.SnapshotConfigScreen
import com.lhzkml.jasmine.SystemPromptConfigScreen
import com.lhzkml.jasmine.TimeoutConfigScreen
import com.lhzkml.jasmine.TokenManagementScreen
import com.lhzkml.jasmine.ToolConfigScreen
import com.lhzkml.jasmine.TraceConfigScreen
import com.lhzkml.jasmine.AboutScreen
import com.lhzkml.jasmine.core.agent.runtime.CheckpointService
import com.lhzkml.jasmine.core.config.SnapshotStorageType
import com.lhzkml.jasmine.core.conversation.storage.ConversationRepository
import com.lhzkml.jasmine.ui.ChatViewModel
import com.lhzkml.jasmine.ui.MainScreen
import com.lhzkml.jasmine.SettingsScreen
import com.lhzkml.jasmine.mnn.ExportImportProgress
import com.lhzkml.jasmine.mnn.MnnManagementScreen
import com.lhzkml.jasmine.mnn.MnnModelManager
import com.lhzkml.jasmine.mnn.MnnModelMarketScreen
import com.lhzkml.jasmine.mnn.MnnModelSettingsScreen
import com.lhzkml.jasmine.oss.OssLicenseEntry
import com.lhzkml.jasmine.oss.OssLicensesDetailScreen
import com.lhzkml.jasmine.oss.OssLicensesListScreen
import com.lhzkml.jasmine.rag.EmbeddingConfigScreen
import com.lhzkml.jasmine.rag.RagConfigScreen
import com.lhzkml.jasmine.rag.RagLibraryContentScreen
import com.lhzkml.jasmine.ui.theme.JasmineTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * 主应用导航图
 *
 * Single-Activity + Navigation Compose，所有设置子页面均通过路由导航。
 */
@Composable
fun AppNavigation(
    viewModel: ChatViewModel,
    navController: androidx.navigation.NavHostController = rememberNavController()
) {
    val conversationRepo: ConversationRepository = koinInject()
    val checkpointService: CheckpointService? = remember { AppConfig.checkpointService() }

    NavHost(
        navController = navController,
        startDestination = Routes.MAIN
    ) {
        composable(Routes.MAIN) {
            MainScreen(
                viewModel = viewModel,
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                conversationRepo = conversationRepo,
                onRefreshCallbackSet = null,
                onNavigate = { navController.navigate(it) }
            )
        }

        composable(Routes.PROVIDER_LIST) {
            val providerRepository: com.lhzkml.jasmine.repository.ProviderRepository = koinInject()
            val ragRepository: com.lhzkml.jasmine.repository.RagConfigRepository = koinInject()
            ProviderListScreen(
                repository = providerRepository,
                ragRepository = ragRepository,
                onBack = { navController.popBackStack() },
                onProviderClick = { navController.navigate(Routes.providerConfig(it)) },
                onNavigateToEmbedding = { navController.navigate(Routes.EMBEDDING_CONFIG) },
                onNavigateToMnnManagement = { navController.navigate(Routes.MNN_MANAGEMENT) },
                onNavigateToAddCustomProvider = { navController.navigate(Routes.ADD_CUSTOM_PROVIDER) }
            )
        }

        composable(
            route = Routes.PROVIDER_CONFIG,
            arguments = listOf(navArgument("providerId") { type = NavType.StringType })
        ) { backStackEntry ->
            val providerId = backStackEntry.arguments?.getString("providerId") ?: return@composable
            val providerRepository: com.lhzkml.jasmine.repository.ProviderRepository = koinInject()
            val provider = remember(providerId) { providerRepository.getProvider(providerId) }
            if (provider != null) {
                ProviderConfigScreen(
                    provider = provider,
                    repository = providerRepository,
                    initialTab = 0,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable(Routes.ADD_CUSTOM_PROVIDER) {
            val providerRepository: com.lhzkml.jasmine.repository.ProviderRepository = koinInject()
            AddCustomProviderScreen(
                repository = providerRepository,
                onBack = {
                    navController.previousBackStackEntry?.savedStateHandle?.set("providerAdded", true)
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.MNN_MANAGEMENT) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val activity = context as? ComponentActivity
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            var pendingExport by remember { mutableStateOf<String?>(null) }
            var progress by remember { mutableStateOf<ExportImportProgress?>(null) }
            val mnnRepository: com.lhzkml.jasmine.repository.MnnModelRepository = koinInject()

            val exportLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/zip")
            ) { uri ->
                uri ?: return@rememberLauncherForActivityResult
                val modelId = pendingExport ?: return@rememberLauncherForActivityResult
                scope.launch(Dispatchers.IO) {
                    try {
                        progress = ExportImportProgress(0f, "正在打包…")
                        val zip = MnnModelManager.createModelZip(context, modelId) { p, msg ->
                            progress = ExportImportProgress(p * 0.9f, msg)
                        }
                        if (zip != null) {
                            val zipSize = zip.length()
                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                zip.inputStream().use { input ->
                                    val buf = ByteArray(64 * 1024)
                                    var read: Int
                                    var written = 0L
                                    while (input.read(buf).also { read = it } != -1) {
                                        out.write(buf, 0, read)
                                        written += read
                                        if (zipSize > 0) {
                                            progress = ExportImportProgress(
                                                0.9f + 0.1f * (written.toFloat() / zipSize),
                                                "正在写入… ${MnnModelManager.formatSize(written)} / ${MnnModelManager.formatSize(zipSize)}"
                                            )
                                        }
                                    }
                                }
                            }
                            zip.delete()
                            progress = null
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "模型已导出", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            progress = null
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "导出失败：模型不存在", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        progress = null
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            val importFolderLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocumentTree()
            ) { uri ->
                uri ?: return@rememberLauncherForActivityResult
                scope.launch {
                    progress = ExportImportProgress(0f, "正在导入…")
                    try {
                        val result = MnnModelManager.importModelFromTree(context, uri) { p, msg ->
                            progress = ExportImportProgress(p, msg)
                        }
                        progress = null
                        withContext(Dispatchers.Main) {
                            if (result != null) {
                                Toast.makeText(context, "已导入模型: $result", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "导入失败：未找到有效模型目录", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        progress = null
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            val importZipLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                uri ?: return@rememberLauncherForActivityResult
                scope.launch {
                    progress = ExportImportProgress(0f, "正在导入…")
                    try {
                        val result = MnnModelManager.importModelFromZip(context, uri) { p, msg ->
                            progress = ExportImportProgress(p, msg)
                        }
                        progress = null
                        withContext(Dispatchers.Main) {
                            if (result != null) {
                                Toast.makeText(context, "已导入模型: $result", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "导入失败：无效的模型压缩包", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        progress = null
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            MnnManagementScreen(
                repository = mnnRepository,
                onBack = { navController.popBackStack() },
                onRefreshCallbackSet = null,
                onExportModel = { modelId ->
                    pendingExport = modelId
                    val dirName = if (modelId.contains("/")) MnnModelManager.safeModelId(modelId) else modelId
                    exportLauncher.launch("${dirName}.zip")
                },
                onImportFolder = { importFolderLauncher.launch(null) },
                onImportZip = { importZipLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed")) },
                progressState = progress
            )
        }

        composable(Routes.MNN_MODEL_MARKET) {
            MnnModelMarketScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.MNN_MODEL_SETTINGS,
            arguments = listOf(navArgument("modelId") { type = NavType.StringType })
        ) { backStackEntry ->
            val modelId = backStackEntry.arguments?.getString("modelId") ?: return@composable
            MnnModelSettingsScreen(modelId = modelId, onBack = { navController.popBackStack() })
        }

        composable(Routes.TOKEN_MANAGEMENT) {
            TokenManagementScreen(
                conversationRepo = conversationRepo,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SAMPLING_PARAMS) {
            SamplingParamsConfigScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SYSTEM_PROMPT) {
            SystemPromptConfigScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.RAG_CONFIG) {
            val ragRepository: com.lhzkml.jasmine.repository.RagConfigRepository = koinInject()
            val sessionRepository: com.lhzkml.jasmine.repository.SessionRepository = koinInject()
            RagConfigScreen(
                ragRepository = ragRepository,
                sessionRepository = sessionRepository,
                onBack = { navController.popBackStack() },
                onNavigateToLibraryContent = { libId, libName ->
                    navController.navigate("rag_library_content/$libId?libraryName=${Uri.encode(libName)}")
                },
                onNavigateToEmbedding = { navController.navigate(Routes.EMBEDDING_CONFIG) }
            )
        }

        composable(
            route = "rag_library_content/{libraryId}?libraryName={libraryName}",
            arguments = listOf(
                navArgument("libraryId") { type = NavType.StringType },
                navArgument("libraryName") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val libraryId = backStackEntry.arguments?.getString("libraryId") ?: return@composable
            val libraryName = backStackEntry.arguments?.getString("libraryName") ?: libraryId
            val ragLibraryRepository: com.lhzkml.jasmine.repository.RagLibraryRepository = koinInject()
            val ragConfigRepository: com.lhzkml.jasmine.repository.RagConfigRepository = koinInject()
            RagLibraryContentScreen(
                libraryId = libraryId,
                libraryName = libraryName,
                ragLibraryRepository = ragLibraryRepository,
                ragConfigRepository = ragConfigRepository,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.EMBEDDING_CONFIG) {
            val ragRepository: com.lhzkml.jasmine.repository.RagConfigRepository = koinInject()
            val mnnRepository: com.lhzkml.jasmine.repository.MnnModelRepository = koinInject()
            EmbeddingConfigScreen(
                ragRepository = ragRepository,
                mnnRepository = mnnRepository,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.RULES) {
            val rulesRepository: com.lhzkml.jasmine.repository.RulesRepository = koinInject()
            val sessionRepository: com.lhzkml.jasmine.repository.SessionRepository = koinInject()
            RulesScreen(
                rulesRepository = rulesRepository,
                sessionRepository = sessionRepository,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.TOOL_CONFIG) {
            val toolRepository: com.lhzkml.jasmine.repository.ToolSettingsRepository = koinInject()
            ToolConfigScreen(
                repository = toolRepository,
                isAgentPreset = false,
                onBack = { navController.popBackStack() },
                onSave = { navController.popBackStack() }
            )
        }

        composable(Routes.TOOL_CONFIG_AGENT) {
            val toolRepository: com.lhzkml.jasmine.repository.ToolSettingsRepository = koinInject()
            ToolConfigScreen(
                repository = toolRepository,
                isAgentPreset = true,
                onBack = { navController.popBackStack() },
                onSave = { navController.popBackStack() }
            )
        }

        composable(Routes.AGENT_STRATEGY) {
            AgentStrategyScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.MCP_SERVER) {
            val mcpRepository: com.lhzkml.jasmine.repository.McpRepository = koinInject()
            McpServerScreen(
                repository = mcpRepository,
                onBack = { navController.popBackStack() },
                onAddServer = { navController.navigate(Routes.mcpServerEdit("new")) },
                onEditServer = { index ->
                    navController.navigate(Routes.mcpServerEdit(index.toString()))
                },
                onRefreshCallbackSet = null
            )
        }

        composable(
            route = Routes.MCP_SERVER_EDIT,
            arguments = listOf(navArgument("serverId") { type = NavType.StringType })
        ) { backStackEntry ->
            val serverId = backStackEntry.arguments?.getString("serverId") ?: "-1"
            val editIndex = when (serverId) {
                "new" -> -1
                else -> serverId.toIntOrNull() ?: -1
            }
            val mcpRepository: com.lhzkml.jasmine.repository.McpRepository = koinInject()
            McpServerEditScreen(
                repository = mcpRepository,
                editIndex = editIndex,
                onCancel = { navController.popBackStack() },
                onSave = { configChanged ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("mcpConfigChanged", configChanged)
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.SHELL_POLICY) {
            val shellPolicyRepository: com.lhzkml.jasmine.repository.ShellPolicyRepository = koinInject()
            ShellPolicyScreen(
                repository = shellPolicyRepository,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.COMPRESSION_CONFIG) {
            val compressionRepository: com.lhzkml.jasmine.repository.CompressionSettingsRepository = koinInject()
            CompressionConfigScreen(
                repository = compressionRepository,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.TIMEOUT_CONFIG) {
            val timeoutRepository: com.lhzkml.jasmine.repository.TimeoutSettingsRepository = koinInject()
            TimeoutConfigScreen(
                repository = timeoutRepository,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.TRACE_CONFIG) {
            val traceRepository: com.lhzkml.jasmine.repository.TraceSettingsRepository = koinInject()
            val activity = androidx.compose.ui.platform.LocalContext.current as? ComponentActivity
            TraceConfigScreen(
                repository = traceRepository,
                onBack = { navController.popBackStack() },
                getTraceDir = { activity?.getExternalFilesDir("traces")?.absolutePath ?: "未知路径" }
            )
        }

        composable(Routes.PLANNER_CONFIG) {
            val plannerRepository: com.lhzkml.jasmine.repository.PlannerSettingsRepository = koinInject()
            PlannerConfigScreen(
                repository = plannerRepository,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SNAPSHOT_CONFIG) {
            val snapshotRepository: com.lhzkml.jasmine.repository.SnapshotSettingsRepository = koinInject()
            val context = androidx.compose.ui.platform.LocalContext.current
            val activity = context as? ComponentActivity
            SnapshotConfigScreen(
                repository = snapshotRepository,
                onBack = { navController.popBackStack() },
                onViewCheckpoints = {
                    if (snapshotRepository.getSnapshotStorage() != SnapshotStorageType.FILE) {
                        Toast.makeText(context, "内存存储模式下无法查看检查点，请切换到文件存储", Toast.LENGTH_SHORT).show()
                    } else {
                        navController.navigate(Routes.CHECKPOINT_MANAGER)
                    }
                },
                onClearCheckpoints = { },
                onPerformClear = { afterClear ->
                    val snapshotDir = activity?.getExternalFilesDir("snapshots")
                    if (snapshotDir != null && snapshotDir.exists()) {
                        snapshotDir.deleteRecursively()
                        snapshotDir.mkdirs()
                    }
                    Toast.makeText(context, "已清除", Toast.LENGTH_SHORT).show()
                    afterClear()
                },
                getCheckpointCount = {
                    if (snapshotRepository.getSnapshotStorage() == SnapshotStorageType.FILE) {
                        val snapshotDir = activity?.getExternalFilesDir("snapshots")
                        if (snapshotDir != null && snapshotDir.exists()) {
                            val agentDirs = snapshotDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
                            var totalCheckpoints = 0
                            for (dir in agentDirs) {
                                totalCheckpoints += dir.listFiles()?.count { it.extension == "json" } ?: 0
                            }
                            "文件存储: ${agentDirs.size} 个会话, $totalCheckpoints 个检查点"
                        } else "文件存储: 暂无检查点"
                    } else "内存存储: 检查点仅在运行时有效"
                }
            )
        }

        composable(Routes.EVENT_HANDLER_CONFIG) {
            val eventHandlerRepository: com.lhzkml.jasmine.repository.EventHandlerSettingsRepository = koinInject()
            EventHandlerConfigScreen(
                repository = eventHandlerRepository,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.CHECKPOINT_MANAGER) { backStackEntry ->
            var refreshTrigger by remember { mutableIntStateOf(0) }
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            val checkpointContext = androidx.compose.ui.platform.LocalContext.current
            val checkpointRepository: com.lhzkml.jasmine.repository.CheckpointRepository = koinInject()

            androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        if (backStackEntry.savedStateHandle.get<Boolean>("checkpointDeleted") == true) {
                            backStackEntry.savedStateHandle.remove<Boolean>("checkpointDeleted")
                            refreshTrigger++
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            CheckpointManagerScreen(
                repository = checkpointRepository,
                refreshTrigger = refreshTrigger,
                onBack = { navController.popBackStack() },
                onOpenDetail = { agentId, cp ->
                    navController.navigate("checkpoint_detail/$agentId?checkpointId=${cp.checkpointId}")
                },
                onDeleteSession = {
                    scope.launch {
                        checkpointRepository.deleteSession(it)
                        Toast.makeText(checkpointContext, "会话已清除", Toast.LENGTH_SHORT).show()
                        refreshTrigger++
                    }
                },
                onClearAll = {
                    scope.launch {
                        checkpointRepository.clearAll()
                        Toast.makeText(checkpointContext, "已清除", Toast.LENGTH_SHORT).show()
                        refreshTrigger++
                    }
                }
            )
        }

        composable(
            route = "${Routes.CHECKPOINT_DETAIL}?checkpointId={checkpointId}",
            arguments = listOf(
                navArgument("agentId") { type = NavType.StringType },
                navArgument("checkpointId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val agentId = backStackEntry.arguments?.getString("agentId") ?: return@composable
            val checkpointId = backStackEntry.arguments?.getString("checkpointId") ?: return@composable
            val checkpointRepository: com.lhzkml.jasmine.repository.CheckpointRepository = koinInject()
            CheckpointDetailScreen(
                agentId = agentId,
                checkpointId = checkpointId,
                repository = checkpointRepository,
                onBack = { navController.popBackStack() },
                onRestored = { intent ->
                    navController.popBackStack(Routes.MAIN, false)
                    viewModel.handleNewIntent(intent)
                },
                onDeleted = {
                    navController.previousBackStackEntry?.savedStateHandle?.set("checkpointDeleted", true)
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.ABOUT) {
            val aboutRepository: com.lhzkml.jasmine.repository.AboutRepository = koinInject()
            AboutScreen(
                repository = aboutRepository,
                onBack = { navController.popBackStack() },
                onNavigateToOssLicenses = { navController.navigate(Routes.OSS_LICENSES_LIST) }
            )
        }

        composable(Routes.OSS_LICENSES_LIST) {
            val aboutRepository: com.lhzkml.jasmine.repository.AboutRepository = koinInject()
            val context = androidx.compose.ui.platform.LocalContext.current
            val title = remember { context.getString(R.string.oss_licenses_title) }
            OssLicensesListScreen(
                repository = aboutRepository,
                title = title,
                onBack = { navController.popBackStack() },
                onPluginLicenseClick = { entry ->
                    navController.navigate(
                        "oss_licenses_detail/${Uri.encode(entry.name)}?offset=${entry.offset}&length=${entry.length}"
                    )
                },
                onManualLicenseClick = { entry ->
                    navController.navigate(
                        "oss_licenses_detail/${Uri.encode(entry.name)}?licenseUrl=${Uri.encode(entry.licenseUrl)}"
                    )
                }
            )
        }

        composable(
            route = "oss_licenses_detail/{name}?offset={offset}&length={length}&licenseUrl={licenseUrl}",
            arguments = listOf(
                navArgument("name") { type = NavType.StringType },
                navArgument("offset") { type = NavType.StringType; defaultValue = "" },
                navArgument("length") { type = NavType.StringType; defaultValue = "" },
                navArgument("licenseUrl") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val name = backStackEntry.arguments?.getString("name")?.let { Uri.decode(it) } ?: return@composable
            val offsetStr = backStackEntry.arguments?.getString("offset") ?: ""
            val lengthStr = backStackEntry.arguments?.getString("length") ?: ""
            val licenseUrl = backStackEntry.arguments?.getString("licenseUrl")

            val entry = if (!licenseUrl.isNullOrBlank()) {
                null
            } else {
                val offset = offsetStr.toLongOrNull() ?: 0L
                val length = lengthStr.toIntOrNull() ?: 0
                OssLicenseEntry(name = name, offset = offset, length = length)
            }
            val directUrl = if (!licenseUrl.isNullOrBlank()) Uri.decode(licenseUrl) else null

            val aboutRepository: com.lhzkml.jasmine.repository.AboutRepository = koinInject()
            OssLicensesDetailScreen(
                repository = aboutRepository,
                entryName = name,
                entry = entry,
                directLicenseUrl = directUrl,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
