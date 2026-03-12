package com.lhzkml.jasmine

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.clickable
import com.lhzkml.jasmine.oss.OssLicensesListActivity
import com.lhzkml.jasmine.repository.AboutRepository
import com.lhzkml.jasmine.ui.components.CustomHorizontalDivider
import com.lhzkml.jasmine.ui.components.CustomText
import com.lhzkml.jasmine.ui.components.CustomTextButton
import com.lhzkml.jasmine.ui.theme.BgPrimary
import com.lhzkml.jasmine.ui.theme.TextPrimary
import com.lhzkml.jasmine.ui.theme.TextSecondary
import org.koin.android.ext.android.inject

class AboutActivity : ComponentActivity() {

    private val aboutRepository: AboutRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AboutScreen(
                repository = aboutRepository,
                onBack = { finish() },
                onNavigateToOssLicenses = {
                    startActivity(Intent(this, OssLicensesListActivity::class.java).apply {
                        putExtra("title", getString(R.string.oss_licenses_title))
                    })
                }
            )
        }
    }
}

@Composable
fun AboutScreen(
    repository: AboutRepository,
    onBack: () -> Unit,
    onNavigateToOssLicenses: () -> Unit = {}
) {
    val context = LocalContext.current
    val appVersion = remember { repository.getAppVersion() }
    val jasmineCoreVersion = remember { repository.getJasmineCoreVersion() }
    val mnnVersion = remember { repository.getMnnVersion() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
    ) {
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
                text = "关于",
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
            AboutIntroCard(
                appName = context.getString(R.string.app_name),
                description = context.getString(R.string.about_app_description)
            )
            VersionItem(title = "应用版本", value = appVersion)
            VersionItem(title = "Jasmine-core 版本", value = jasmineCoreVersion)
            VersionItem(title = "MNN 引擎版本", value = mnnVersion)
            OssLicensesItem(onClick = onNavigateToOssLicenses)
            Spacer(modifier = Modifier.height(24.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CustomText(
                    text = context.getString(R.string.about_copyright_line1),
                    fontSize = 12.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                CustomText(
                    text = context.getString(R.string.about_copyright_line2),
                    fontSize = 12.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun AboutIntroCard(
    appName: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CustomText(
            text = appName,
            fontSize = 20.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        CustomText(text = description, fontSize = 14.sp, color = TextSecondary)
    }
}

@Composable
private fun OssLicensesItem(onClick: () -> Unit) {
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
        CustomText(text = "开源许可", fontSize = 15.sp, color = TextPrimary)
        CustomText(text = "查看", fontSize = 14.sp, color = TextSecondary)
    }
}

@Composable
private fun VersionItem(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        CustomText(text = title, fontSize = 15.sp, color = TextPrimary)
        CustomText(text = value, fontSize = 14.sp, color = TextSecondary)
    }
}
