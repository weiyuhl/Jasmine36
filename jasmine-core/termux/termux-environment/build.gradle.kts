plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lhzkml.jasmine.core.termux"
    compileSdk = 36
    
    defaultConfig {
        minSdk = 26
        
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
        
        externalNativeBuild {
            ndkBuild {
                arguments += listOf(
                    "NDK_PROJECT_PATH=${projectDir}/src/main/cpp",
                    "APP_BUILD_SCRIPT=${projectDir}/src/main/cpp/Android.mk"
                )
            }
        }
    }
    
    externalNativeBuild {
        ndkBuild {
            path = file("src/main/cpp/Android.mk")
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
}

// ========== Bootstrap 下载任务 ==========

/**
 * 下载单个架构的 Bootstrap 文件
 */
fun downloadBootstrap(arch: String, expectedChecksum: String, version: String) {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    
    val localUrl = "src/main/cpp/bootstrap-$arch.zip"
    val file = File(projectDir, localUrl)
    
    // 检查文件是否已存在且校验和正确
    if (file.exists()) {
        val buffer = ByteArray(8192)
        val input = java.io.FileInputStream(file)
        while (true) {
            val readBytes = input.read(buffer)
            if (readBytes < 0) break
            digest.update(buffer, 0, readBytes)
        }
        input.close()
        
        val checksum = java.math.BigInteger(1, digest.digest()).toString(16).padStart(64, '0')
        if (checksum == expectedChecksum) {
            logger.quiet("Bootstrap $arch already exists with correct checksum")
            return
        } else {
            logger.quiet("Deleting old bootstrap-$arch.zip with wrong hash")
            logger.quiet("Expected: $expectedChecksum")
            logger.quiet("Actual:   $checksum")
            file.delete()
        }
    }
    
    // 下载文件
    val remoteUrl = "https://github.com/termux/termux-packages/releases/download/bootstrap-$version/bootstrap-$arch.zip"
    logger.quiet("Downloading $remoteUrl ...")
    
    file.parentFile?.mkdirs()
    val out = java.io.BufferedOutputStream(java.io.FileOutputStream(file))
    
    val connection = java.net.URL(remoteUrl).openConnection()
    connection.setRequestProperty("User-Agent", "Jasmine-Termux-Integration")
    connection.connect()
    
    val digestStream = java.security.DigestInputStream(connection.getInputStream(), digest)
    digestStream.copyTo(out)
    out.close()
    digestStream.close()
    
    // 验证校验和
    val checksum = java.math.BigInteger(1, digest.digest()).toString(16).padStart(64, '0')
    if (checksum != expectedChecksum) {
        file.delete()
        throw GradleException("Wrong checksum for $remoteUrl\nExpected: $expectedChecksum\nActual:   $checksum")
    }
    
    logger.quiet("Successfully downloaded bootstrap-$arch.zip")
}

/**
 * 下载所有架构的 Bootstrap 文件
 */
tasks.register("downloadBootstraps") {
    doLast {
        val version = "2026.02.12-r1%2Bapt.android-7"
        
        logger.quiet("========================================")
        logger.quiet("Downloading Termux Bootstrap files...")
        logger.quiet("Version: $version")
        logger.quiet("========================================")
        
        downloadBootstrap("aarch64", "ea2aeba8819e517db711f8c32369e89e7c52cee73e07930ff91185e1ab93f4f3", version)
        downloadBootstrap("arm", "a38f4d3b2f735f83be2bf54eff463e86dc32a3e2f9f861c1557c4378d249c018", version)
        downloadBootstrap("i686", "f5bc0b025b9f3b420b5fcaeefc064f888f5f22a0d6fd7090f4aac0c33eb3555b", version)
        downloadBootstrap("x86_64", "b7fd0f2e3a4de534be3144f9f91acc768630fc463eaf134ab2e64c545e834f7a", version)
        
        logger.quiet("========================================")
        logger.quiet("All Bootstrap files downloaded successfully!")
        logger.quiet("========================================")
    }
}

/**
 * 清理 Bootstrap 文件
 */
tasks.named("clean") {
    doLast {
        val cppDir = File(projectDir, "src/main/cpp")
        cppDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("bootstrap-") && file.name.endsWith(".zip")) {
                logger.quiet("Deleting ${file.name}")
                file.delete()
            }
        }
    }
}

/**
 * 确保在编译前下载 Bootstrap
 */
afterEvaluate {
    tasks.matching { it.name.contains("compile", ignoreCase = true) }.configureEach {
        dependsOn("downloadBootstraps")
    }
}
