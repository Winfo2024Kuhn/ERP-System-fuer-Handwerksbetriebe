package org.example.kalkulationsprogramm.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EmailHtmlSanitizerTest {

    @Test
    fun plainTextToHtmlPreservesParagraphsAndLineBreaks() {
        val text = """
            Hallo Herr Eichhoff,
            bitte um Lieferung wie anfragenn.

            Mit freundlichen Grüßen
            Max Mustermann
        """.trimIndent()

        val html = EmailHtmlSanitizer.plainTextToHtml(text)

        assertThat(html).isEqualTo("<p>Hallo Herr Eichhoff,<br/>bitte um Lieferung wie anfragenn.</p><br/><br/><p>Mit freundlichen Grüßen<br/>Max Mustermann</p>")
    }

    @Test
    fun plainTextToHtmlEscapesHtmlTagsAndCollapsesBlankParagraphs() {
        val text = """
            <script>alert('x')</script>


            Zeile 2
        """.trimIndent()

        val html = EmailHtmlSanitizer.plainTextToHtml(text)

        assertThat(html).isEqualTo("<p>&lt;script&gt;alert('x')&lt;/script&gt;</p><br/><br/><p>Zeile 2</p>")
    }

    @Test
    fun sanitizeDetailHtmlKeepsTablesAndInlineStyles() {
        val html = """
            <html><body><table style="border:1px solid #000"><tr><td style="color:#f00">Hallo</td></tr></table>
            <a href="https://example.com">Link</a><script>alert(1)</script></body></html>
        """.trimIndent()

        val sanitized = EmailHtmlSanitizer.sanitizeDetailHtml(html)

        assertThat(sanitized)
            .contains("<table", "<td style=\"color:#f00\">Hallo</td>")
            .contains("target=\"_blank\"", "rel=\"noopener\"")
            .doesNotContain("<script")
    }

    @Test
    fun sanitizePreviewHtmlFlattensBlockElements() {
        val html = "<div>Absatz 1</div><div>Absatz 2</div>"

        val preview = EmailHtmlSanitizer.sanitizePreviewHtml(html)

        assertThat(preview).isEqualTo("Absatz 1<br/><br/>Absatz 2")
    }
}
