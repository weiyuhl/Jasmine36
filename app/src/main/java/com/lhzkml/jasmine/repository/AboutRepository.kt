package com.lhzkml.jasmine.repository

import android.content.Context
import android.content.pm.PackageManager
import com.lhzkml.jasmine.BuildConfig
import com.lhzkml.jasmine.mnn.MnnBridge
import com.lhzkml.jasmine.oss.ManualLicenseEntry
import com.lhzkml.jasmine.oss.OssLicenseEntry
import com.lhzkml.jasmine.oss.OssLicenseLoader

/**
 * 关于页面 Repository
 *
 * 负责：
 * - App 版本号
 * - jasmine-core 版本
 * - MNN 版本
 * - OSS licenses 列表
 * - OSS license 正文
 *
 * 对应页面：
 * - AboutActivity
 * - OssLicensesListActivity
 * - OssLicensesDetailActivity
 */
interface AboutRepository {
    
    /**
     * 获取应用版本号
     */
    fun getAppVersion(): String
    
    /**
     * 获取 Jasmine-core 版本
     */
    fun getJasmineCoreVersion(): String
    
    /**
     * 获取 MNN 引擎版本
     * @return MNN 版本号，如果不可用返回 "不可用"
     */
    fun getMnnVersion(): String
    
    /**
     * 获取插件生成的 OSS 许可列表
     */
    fun getPluginLicenseList(): List<OssLicenseEntry>
    
    /**
     * 获取手动添加的许可列表（如 MNN）
     */
    fun getManualLicenseList(): List<ManualLicenseEntry>
    
    /**
     * 判断是否有许可信息
     */
    fun hasLicenses(): Boolean
    
    /**
     * 加载指定许可的正文
     */
    fun loadLicenseText(entry: OssLicenseEntry): String?
}

/**
 * 默认实现
 */
class DefaultAboutRepository(
    private val context: Context
) : AboutRepository {
    
    override fun getAppVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName 
                ?: BuildConfig.VERSION_NAME
        } catch (e: PackageManager.NameNotFoundException) {
            BuildConfig.VERSION_NAME
        }
    }
    
    override fun getJasmineCoreVersion(): String {
        return BuildConfig.JASMINE_CORE_VERSION
    }
    
    override fun getMnnVersion(): String {
        return try {
            if (MnnBridge.isAvailable()) {
                MnnBridge.getMnnVersion()
            } else {
                "不可用"
            }
        } catch (e: Exception) {
            "不可用"
        }
    }
    
    override fun getPluginLicenseList(): List<OssLicenseEntry> {
        return OssLicenseLoader.loadLicenseList(context).distinctBy { it.name }
    }
    
    override fun getManualLicenseList(): List<ManualLicenseEntry> {
        return OssLicenseLoader.manualLicenses
    }
    
    override fun hasLicenses(): Boolean {
        return OssLicenseLoader.hasLicenses(context) || getManualLicenseList().isNotEmpty()
    }
    
    override fun loadLicenseText(entry: OssLicenseEntry): String? {
        return OssLicenseLoader.loadLicenseText(context, entry)
    }
}
