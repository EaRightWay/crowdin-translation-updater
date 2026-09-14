package io.github.earightway.crowdin

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PropertiesBundlesTest {
    private val bundles = listOf("site", "templates", "mail_welcome")

    // Kotlin resolves \uXXXX even inside raw strings, so the expected escapes are built from this.
    private val backslash = 92.toChar()

    @Test
    fun `reads the language from the directory when Crowdin exports one folder per language`() {
        assertThat(resolveTranslationEntry("de/site.properties", bundles)).isEqualTo(TranslationEntry("site", "de"))
        assertThat(resolveTranslationEntry("export/zh-CN/mail_welcome.properties", bundles))
            .isEqualTo(TranslationEntry("mail_welcome", "zh-CN"))
    }

    @Test
    fun `reads the language from the file name when Crowdin exports a flat layout`() {
        assertThat(resolveTranslationEntry("site_de.properties", bundles)).isEqualTo(TranslationEntry("site", "de"))
        assertThat(resolveTranslationEntry("resources/site_zh_CN.properties", bundles))
            .isEqualTo(TranslationEntry("site", "zh_CN"))
    }

    @Test
    fun `prefers the longest bundle name so an underscore in the bundle is not read as a language`() {
        assertThat(resolveTranslationEntry("mail_welcome_pt.properties", bundles + "mail"))
            .isEqualTo(TranslationEntry("mail_welcome", "pt"))
    }

    @Test
    fun `ignores entries that belong to no configured bundle`() {
        assertThat(resolveTranslationEntry("de/unknown.properties", bundles)).isNull()
        assertThat(resolveTranslationEntry("de/site.xml", bundles)).isNull()
        assertThat(resolveTranslationEntry("site.properties", bundles)).isNull()
    }

    @Test
    fun `builds the file name for a language and for the fallback bundle`() {
        assertThat(bundleFileName("site", "zh_CN")).isEqualTo("site_zh_CN.properties")
        assertThat(bundleFileName("site", null)).isEqualTo("site.properties")
    }

    @Test
    fun `escapes non-ascii characters the way java properties files store them`() {
        assertThat(escapeNonAscii("category.food=Cafés & Bars"))
            .isEqualTo("category.food=Caf" + backslash + "u00e9s & Bars")
        assertThat(escapeNonAscii("中文")).isEqualTo("" + backslash + "u4e2d" + backslash + "u6587")
    }

    @Test
    fun `leaves already escaped ascii content untouched so the task is idempotent`() {
        val escaped = "category.food=Caf" + backslash + "u00e9s\r\nkey=value\r\n"
        assertThat(escapeNonAscii(escaped)).isSameAs(escaped)
    }
}
