package com.lhzkml.jasmine.oss

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.ui.components.CustomHorizontalDivider
import com.lhzkml.jasmine.ui.components.CustomText
import com.lhzkml.jasmine.ui.components.CustomTextButton
import com.lhzkml.jasmine.R
import com.lhzkml.jasmine.ui.theme.BgPrimary
import com.lhzkml.jasmine.ui.theme.TextPrimary
import com.lhzkml.jasmine.ui.theme.TextSecondary

class OssLicensesListActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra("title") ?: getString(R.string.oss_licenses_title)
        setContent {
            OssLicensesListScreen(
                title = title,
                onBack = { finish() },
                onPluginLicenseClick = { entry ->
                    startActivity(
                        Intent(this, OssLicensesDetailActivity::class.java).apply {
                            putExtra("name", entry.name)
                            putExtra("offset", entry.offset)
                            putExtra("length", entry.length)
                        }
                    )
                },
                onManualLicenseClick = { entry ->
                    startActivity(
                        Intent(this, OssLicensesDetailActivity::class.java).apply {
                            putExtra("name", entry.name)
                            putExtra("licenseUrl", entry.licenseUrl)
                        }
                    )
                }
            )
        }
    }
}

@Composable
fun OssLicensesListScreen(
    title: String,
    onBack: () -> Unit,
    onPluginLicenseClick: (OssLicenseEntry) -> Unit,
    onManualLicenseClick: (ManualLicenseEntry) -> Unit
) {
    val context = LocalContext.current
    val pluginList = remember {
        OssLicenseLoader.loadLicenseList(context)
    }
    val manualList = remember {
        OssLicenseLoader.manualLicenses
    }
    val hasLicenses = remember {
        OssLicenseLoader.hasLicenses(context) || manualList.isNotEmpty()
    }

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
                text = title,
                fontSize = 17.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(56.dp))
        }

        CustomHorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!hasLicenses) {
                CustomText(
                    text = "许可信息仅在 release 构建中提供。请使用 release 版本查看开源许可。",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                manualList.forEach { entry ->
                    OssLicenseListItem(
                        name = entry.name,
                        onClick = { onManualLicenseClick(entry) }
                    )
                }
                pluginList.forEach { entry ->
                    OssLicenseListItem(
                        name = entry.name,
                        onClick = { onPluginLicenseClick(entry) }
                    )
                }
            }
        }
    }
}

@Composable
private fun OssLicenseListItem(
    name: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        CustomText(text = name, fontSize = 15.sp, color = TextPrimary)
        CustomText(text = "查看", fontSize = 14.sp, color = TextSecondary)
    }
}
