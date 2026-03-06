package com.lhzkml.jasmine.core.config

import org.junit.Assert.*
import org.junit.Test

class FormatUtilsTest {

    // ========== formatTokenCount ==========

    @Test
    fun `formatTokenCount millions`() {
        assertEquals("1M", FormatUtils.formatTokenCount(1_000_000))
        assertEquals("2M", FormatUtils.formatTokenCount(2_500_000))
        assertEquals("128M", FormatUtils.formatTokenCount(128_000_000))
    }

    @Test
    fun `formatTokenCount thousands`() {
        assertEquals("1K", FormatUtils.formatTokenCount(1_000))
        assertEquals("4K", FormatUtils.formatTokenCount(4_096))
        assertEquals("128K", FormatUtils.formatTokenCount(128_000))
    }

    @Test
    fun `formatTokenCount small numbers`() {
        assertEquals("0", FormatUtils.formatTokenCount(0))
        assertEquals("500", FormatUtils.formatTokenCount(500))
        assertEquals("999", FormatUtils.formatTokenCount(999))
    }

    // ========== formatTokensWithUnit ==========

    @Test
    fun `formatTokensWithUnit millions`() {
        assertEquals("1.5M tokens", FormatUtils.formatTokensWithUnit(1_500_000))
        assertEquals("1.0M tokens", FormatUtils.formatTokensWithUnit(1_000_000))
    }

    @Test
    fun `formatTokensWithUnit thousands`() {
        assertEquals("1.5K tokens", FormatUtils.formatTokensWithUnit(1_500))
        assertEquals("4.0K tokens", FormatUtils.formatTokensWithUnit(4_000))
    }

    @Test
    fun `formatTokensWithUnit small numbers`() {
        assertEquals("0 tokens", FormatUtils.formatTokensWithUnit(0))
        assertEquals("500 tokens", FormatUtils.formatTokensWithUnit(500))
    }

    // ========== getFileIcon ==========

    @Test
    fun `getFileIcon kotlin files`() {
        assertEquals("K", FormatUtils.getFileIcon("Main.kt"))
        assertEquals("K", FormatUtils.getFileIcon("build.gradle.kts"))
    }

    @Test
    fun `getFileIcon java files`() {
        assertEquals("J", FormatUtils.getFileIcon("Activity.java"))
    }

    @Test
    fun `getFileIcon web files`() {
        assertEquals("JS", FormatUtils.getFileIcon("app.js"))
        assertEquals("JS", FormatUtils.getFileIcon("index.ts"))
        assertEquals("H", FormatUtils.getFileIcon("page.html"))
        assertEquals("C", FormatUtils.getFileIcon("style.css"))
    }

    @Test
    fun `getFileIcon config files`() {
        assertEquals("{}", FormatUtils.getFileIcon("config.json"))
        assertEquals("X", FormatUtils.getFileIcon("layout.xml"))
        assertEquals("Y", FormatUtils.getFileIcon("docker-compose.yaml"))
        assertEquals("Y", FormatUtils.getFileIcon("config.yml"))
        assertEquals("P", FormatUtils.getFileIcon("gradle.properties"))
        assertEquals("T", FormatUtils.getFileIcon("Cargo.toml"))
    }

    @Test
    fun `getFileIcon image files`() {
        assertEquals("Img", FormatUtils.getFileIcon("photo.png"))
        assertEquals("Img", FormatUtils.getFileIcon("icon.svg"))
        assertEquals("Img", FormatUtils.getFileIcon("bg.webp"))
    }

    @Test
    fun `getFileIcon shell files`() {
        assertEquals("$", FormatUtils.getFileIcon("run.sh"))
        assertEquals("$", FormatUtils.getFileIcon("build.bat"))
    }

    @Test
    fun `getFileIcon unknown extension`() {
        assertEquals(".", FormatUtils.getFileIcon("data.bin"))
        assertEquals(".", FormatUtils.getFileIcon("noext"))
    }

    @Test
    fun `getFileIcon case insensitive`() {
        assertEquals("K", FormatUtils.getFileIcon("Main.KT"))
        assertEquals("Img", FormatUtils.getFileIcon("photo.PNG"))
        assertEquals("JS", FormatUtils.getFileIcon("app.TS"))
    }
}
