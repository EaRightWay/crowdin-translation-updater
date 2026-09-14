package io.github.earightway.crowdin

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

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
    fun `passes the export through byte for byte, whatever Crowdin escaped`() {
        val escaped = "category.food=Caf" + backslash + "u00e9s\n"
        val raw = "category.food=Cafés\n"
        assertThat(applyLineEndings(escaped, escaped, LineEndings.PRESERVE)).isEqualTo(escaped)
        assertThat(applyLineEndings(raw, raw, LineEndings.PRESERVE)).isEqualTo(raw)
    }

    @Test
    fun `finds bundles by the translations sitting next to them`(
        @TempDir directory: File,
    ) {
        listOf("de", "fr", "es", "pt", "zh_CN").forEach { language ->
            directory.resolve("site_$language.properties").writeText("a=1")
            directory.resolve("mail_welcome_$language.properties").writeText("a=1")
        }
        directory.resolve("site.properties").writeText("a=1")
        directory.resolve("mixpanel_ru.properties").writeText("a=1")
        directory.resolve("logback.properties").writeText("a=1")

        assertThat(discoverBundles(directory)).containsExactly("mail_welcome", "site")
    }

    @Test
    fun `ignores a name that has too few languages to be a bundle`(
        @TempDir directory: File,
    ) {
        listOf("de", "fr", "es").forEach { directory.resolve("site_$it.properties").writeText("a=1") }

        assertThat(discoverBundles(directory)).isEmpty()
        assertThat(discoverBundles(directory, minimumLanguages = 3)).containsExactly("site")
    }

    @Test
    fun `preserving keeps the endings the committed file already uses`() {
        assertThat(applyLineEndings("a=1\nb=2\n", "a=0\r\nb=0\r\n", LineEndings.PRESERVE)).isEqualTo("a=1\r\nb=2\r\n")
        assertThat(applyLineEndings("a=1\r\nb=2\r\n", "a=0\nb=0\n", LineEndings.PRESERVE)).isEqualTo("a=1\nb=2\n")
    }

    @Test
    fun `preserving keeps the exported endings when there is no committed file`() {
        assertThat(applyLineEndings("a=1\r\nb=2\r\n", null, LineEndings.PRESERVE)).isEqualTo("a=1\r\nb=2\r\n")
        assertThat(applyLineEndings("a=1\nb=2\n", null, LineEndings.PRESERVE)).isEqualTo("a=1\nb=2\n")
    }

    @Test
    fun `lf and crlf force the endings whatever the committed file uses`() {
        assertThat(applyLineEndings("a=1\r\nb=2\r\n", "a=0\r\n", LineEndings.LF)).isEqualTo("a=1\nb=2\n")
        assertThat(applyLineEndings("a=1\nb=2\n", "a=0\n", LineEndings.CRLF)).isEqualTo("a=1\r\nb=2\r\n")
    }

    @Test
    fun `line ending modes are read case-insensitively and bad ones name the alternatives`() {
        assertThat(LineEndings.of("preserve")).isEqualTo(LineEndings.PRESERVE)
        assertThat(LineEndings.of("CRLF")).isEqualTo(LineEndings.CRLF)
        assertThatThrownBy { LineEndings.of("windows") }
            .hasMessageContaining("preserve, lf, crlf")
    }
}
