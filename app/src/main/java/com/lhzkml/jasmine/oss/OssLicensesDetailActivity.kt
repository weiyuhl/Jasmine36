package com.lhzkml.jasmine.oss

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.ui.components.CustomHorizontalDivider
import com.lhzkml.jasmine.ui.components.CustomText
import com.lhzkml.jasmine.ui.components.CustomTextButton
import com.lhzkml.jasmine.ui.theme.Accent
import org.koin.android.ext.android.inject
import com.lhzkml.jasmine.ui.theme.BgPrimary
import com.lhzkml.jasmine.ui.theme.TextPrimary
import com.lhzkml.jasmine.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

private val URL_PATTERN = Regex("https?://[^\\s<>\"']+")

/** 判断字符串是否为可获取的许可 URL */
private fun String.isLicenseUrl(): Boolean =
    trim().let { s -> s.startsWith("http://", true) || s.startsWith("https://", true) }

/** 许可 URL 获取结果缓存，避免重复请求 */
private val licenseFetchCache = mutableMapOf<String, Result<String>>()

/** 在后台线程从 URL 获取许可全文（带缓存）。http 会优先尝试 https 以绕过 Android 明文流量限制 */
private suspend fun fetchLicenseFromUrl(url: String): Result<String> = withContext(Dispatchers.IO) {
    val key = url.trim()
    licenseFetchCache.getOrPut(key) {
        val httpsUrl = if (key.startsWith("http://", true)) "https://" + key.substring(7) else key
        runCatching {
            URL(httpsUrl).openConnection().apply {
                connectTimeout = 15_000
                readTimeout = 20_000
            }.getInputStream().bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.recoverCatching {
            // https 失败时回退到原始 http（需 usesCleartextTraffic）
            if (httpsUrl != key) {
                URL(key).openConnection().apply {
                    connectTimeout = 15_000
                    readTimeout = 20_000
                }.getInputStream().bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else throw it
        }
    }
}

private fun buildAnnotatedStringWithLinks(
    text: String,
    uriHandler: (String) -> Unit
): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        var lastEnd = 0
        URL_PATTERN.findAll(text).forEach { match ->
            append(text.substring(lastEnd, match.range.first))
            withLink(LinkAnnotation.Url(
                    url = match.value,
                    styles = TextLinkStyles(style = SpanStyle(color = Accent)),
                    linkInteractionListener = { uriHandler(match.value) }
                )) {
                append(match.value)
            }
            lastEnd = match.range.last + 1
        }
        append(text.substring(lastEnd))
    }
}

class OssLicensesDetailActivity : ComponentActivity() {
    
    private val repository: com.lhzkml.jasmine.repository.AboutRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val name = intent.getStringExtra("name") ?: ""
        val licenseUrl = intent.getStringExtra("licenseUrl")
        val entry = if (licenseUrl != null) {
            // 手动添加的许可（如 MNN），直接使用 URL
            null
        } else {
            OssLicenseEntry(
                name = name,
                offset = intent.getLongExtra("offset", 0L),
                length = intent.getIntExtra("length", 0)
            )
        }

        setContent {
            OssLicensesDetailScreen(
                repository = repository,
                entryName = name,
                entry = entry,
                directLicenseUrl = licenseUrl,
                onBack = { finish() }
            )
        }
    }
}

@Composable
fun OssLicensesDetailScreen(
    repository: com.lhzkml.jasmine.repository.AboutRepository,
    entryName: String,
    entry: OssLicenseEntry?,
    directLicenseUrl: String?,
    onBack: () -> Unit
) {
    val rawLicense = remember(entry, directLicenseUrl) {
        when {
            directLicenseUrl != null -> directLicenseUrl
            entry != null -> repository.loadLicenseText(entry)
            else -> null
        }
    }

    // 当原始内容为 URL 时，运行时获取许可全文
    var fetchedText by remember(rawLicense) { mutableStateOf<String?>(null) }
    var fetchError by remember(rawLicense) { mutableStateOf<String?>(null) }
    var isLoading by remember(rawLicense) { mutableStateOf(false) }

    LaunchedEffect(rawLicense) {
        fetchedText = null
        fetchError = null
        if (rawLicense != null && rawLicense.isLicenseUrl()) {
            isLoading = true
            fetchLicenseFromUrl(rawLicense)
                .onSuccess { fetchedText = it }
                .onFailure { fetchError = it.message ?: "获取失败" }
            isLoading = false
        }
    }

    // 最终展示的许可正文：优先使用获取到的全文，否则用原始内容
    val displayText = fetchedText ?: rawLicense
    val licenseSourceUrl = if (rawLicense != null && rawLicense.isLicenseUrl()) rawLicense.trim() else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
    ) {
        // 标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(Color.White)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CustomTextButton(onClick = onBack, contentColor = TextPrimary) {
                CustomText("← 返回", fontSize = 14.sp, color = TextPrimary)
            }
            CustomText(
                text = entryName,
                fontSize = 17.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(56.dp))
        }

        CustomHorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)

        // 正文区域：库名、许可来源链接、License 全文
        val uriHandler = LocalUriHandler.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // 库名称（醒目展示）
            CustomText(
                text = entryName,
                fontSize = 16.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 许可来源（当内容来自 URL 时显示可点击链接）
            if (licenseSourceUrl != null) {
                CustomText(
                    text = "许可来源：",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    modifier = Modifier.fillMaxWidth()
                )
                BasicText(
                    text = buildAnnotatedString {
                        withLink(LinkAnnotation.Url(
                            url = licenseSourceUrl,
                            styles = TextLinkStyles(style = SpanStyle(color = Accent)),
                            linkInteractionListener = { uriHandler.openUri(licenseSourceUrl) }
                        )) {
                            append(licenseSourceUrl)
                        }
                    },
                    style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Accent),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // License 全文 或 加载/错误状态
            when {
                isLoading -> CustomText(
                    text = "正在加载许可全文…",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    modifier = Modifier.fillMaxWidth()
                )
                fetchError != null && displayText == rawLicense && licenseSourceUrl != null -> {
                    val url = licenseSourceUrl
                    CustomText(
                        text = "无法在线获取许可全文。请点击下方链接在浏览器中查看：",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    BasicText(
                        text = buildAnnotatedString {
                            withLink(LinkAnnotation.Url(
                                url = url,
                                styles = TextLinkStyles(style = SpanStyle(color = Accent)),
                                linkInteractionListener = { uriHandler.openUri(url) }
                            )) {
                                append(url)
                            }
                        },
                        style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                displayText != null && displayText.isNotBlank() -> {
                    CustomText(
                        text = "License 全文",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    BasicText(
                        text = buildAnnotatedStringWithLinks(displayText) { uriHandler.openUri(it) },
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 13.sp,
                            color = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                else -> CustomText(
                    text = "无法加载许可内容",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
