package org.example.kalkulationsprogramm.util

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.safety.Safelist
import java.util.regex.Pattern

class EmailHtmlSanitizer private constructor() {
    companion object {
        private val SAFE_LIST: Safelist = Safelist.relaxed()
            .addTags("table", "thead", "tbody", "tfoot", "tr", "td", "th", "colgroup", "col", "blockquote")
            .addAttributes(":all", "class", "style", "data-signature-id")
            .addProtocols("img", "src", "cid", "data")

        private val BLOCK_CLOSING_TAG_PATTERN: Pattern = Pattern.compile(
            "(?i)</(?:p|div|section|article|header|footer|blockquote|ul|ol|li|table|thead|tbody|tfoot|tr|h[1-6])>",
        )

        private val BLOCK_TAGS: Set<String> = setOf(
            "p", "div", "section", "article", "header", "footer", "blockquote",
            "ul", "ol", "table", "thead", "tbody", "tfoot", "tr",
            "h1", "h2", "h3", "h4", "h5", "h6",
        )

        @JvmStatic
        fun sanitizeDetailHtml(html: String?): String? {
            if (html == null) {
                return null
            }

            val permissive = Safelist.relaxed()
                .addTags(
                    "table", "thead", "tbody", "tfoot", "tr", "td", "th",
                    "colgroup", "col", "blockquote", "span", "font", "center",
                    "hr", "caption", "section", "article", "header", "footer", "nav", "aside",
                )
                .addAttributes(
                    ":all", "class", "style", "id", "data-signature-id",
                    "width", "height", "border", "cellpadding", "cellspacing",
                    "align", "valign", "bgcolor", "color", "face", "size",
                )
                .addProtocols("img", "src", "cid", "data", "http", "https")
                .addProtocols("a", "href", "http", "https", "mailto")

            val outputSettings = Document.OutputSettings().prettyPrint(false)
            val clean = Jsoup.clean(html, "", permissive, outputSettings)
            val doc = Jsoup.parse(clean, "", org.jsoup.parser.Parser.htmlParser())
            doc.outputSettings().prettyPrint(false)

            for (anchor in doc.select("a[href]")) {
                anchor.attr("target", "_blank")
                anchor.attr("rel", "noopener")
            }

            return doc.body().html().trim()
        }

        @JvmStatic
        fun sanitizePreviewHtml(html: String?): String? {
            val doc = prepareDocument(html) ?: return null
            for (tag in BLOCK_TAGS) {
                convertBlocksToBreaks(doc, tag)
            }
            return finalizePreviewHtml(doc)
        }

        @JvmStatic
        fun limitPreviewHtml(html: String?, maxParagraphs: Int): String? {
            if (html == null) {
                return null
            }
            if (maxParagraphs <= 0) {
                return ""
            }
            val decoded = org.jsoup.parser.Parser.unescapeEntities(html, false)
            val sanitized = sanitizePreviewHtml(decoded) ?: return null
            var normalized = sanitized.replace(Regex("(?i)<br\\s*/?>"), "<br/>")
            normalized = normalized.replace(Regex("(?i)^(<br/?>\\s*)+"), "")
            val parts = normalized.split(Regex("(?i)<br/><br/>")).toTypedArray()
            if (parts.size <= maxParagraphs) {
                return normalized
            }
            val sb = StringBuilder()
            var emitted = 0
            for (part in parts) {
                if (part.isEmpty()) {
                    continue
                }
                if (emitted > 0) {
                    sb.append("<br/><br/>")
                }
                sb.append(part)
                emitted++
                if (emitted >= maxParagraphs) {
                    break
                }
            }
            return if (sb.isEmpty()) "" else sb.toString().trim()
        }

        @JvmStatic
        fun htmlToPlainText(html: String?): String? {
            if (html == null) {
                return null
            }
            var s = html.replace("\r\n", "\n").replace("\r", "\n")
            s = s.replace(Regex("(?i)<br\\s*/?>"), "\n")
            s = BLOCK_CLOSING_TAG_PATTERN.matcher(s).replaceAll("\n\n")
            s = s.replace(Regex("(?is)<[^>]+>"), "")
            s = s.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
            s = s.replace(Regex("[ \\t]+\\n"), "\n")
            s = s.replace(Regex("(?m)^[ \\t]+"), "")
            s = s.replace(Regex("\\n{3,}"), "\n\n")
            return s.trim()
        }

        @JvmStatic
        fun plainTextToHtml(text: String?): String? {
            if (text == null) {
                return null
            }
            if (text.isEmpty()) {
                return ""
            }
            val normalized = text
                .replace("\u00A0", " ")
                .replace("\r\n", "\n")
                .replace("\r", "\n")
            val paragraphs = normalized.split(Regex("\\n{2,}")).toTypedArray()
            val sb = StringBuilder()
            var emitted = 0
            for (paragraph in paragraphs) {
                if (paragraph.isEmpty()) {
                    continue
                }
                val trimmedParagraph = paragraph.replace(Regex("\\n+$"), "")
                if (trimmedParagraph.isEmpty()) {
                    continue
                }
                if (emitted > 0) {
                    sb.append("<br/><br/>")
                }
                sb.append("<p>")
                    .append(escapeHtml(trimmedParagraph).replace("\n", "<br/>"))
                    .append("</p>")
                emitted++
            }
            return if (emitted == 0) "" else sb.toString()
        }

        private fun escapeHtml(value: String): String =
            value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")

        private fun prepareDocument(html: String?): Document? {
            if (html == null) {
                return null
            }
            val outputSettings = Document.OutputSettings().prettyPrint(false)
            val clean = Jsoup.clean(html, "", SAFE_LIST, outputSettings)
            val doc = Jsoup.parse(clean, "", org.jsoup.parser.Parser.htmlParser())
            doc.outputSettings().prettyPrint(false)

            for (pre in doc.select("pre")) {
                val text = pre.text()
                val withBreaks = textToBrHtml(text)
                pre.after(withBreaks)
                pre.unwrap()
            }

            for (anchor in doc.select("a[href]")) {
                anchor.attr("target", "_blank")
                anchor.attr("rel", "noopener")
            }
            return doc
        }

        private fun finalizePreviewHtml(doc: Document): String {
            var body = doc.body().html()
            body = body.replace(Regex("(?i)<br\\s*/?>"), "<br/>")
            body = body.replace(Regex("(?i)(?:<br/?>\\s*){3,}"), "<br/><br/>")
            body = body.replace(Regex("(?i)(?:<br/?>\\s*)+$"), "")
            return body.trim()
        }

        private fun convertBlocksToBreaks(doc: Document, tag: String) {
            val elements = ArrayList<Element>(doc.select(tag))
            for (i in elements.size - 1 downTo 0) {
                val el = elements[i]
                if (el.parent() == null) {
                    continue
                }
                el.before(el.html() + "<br/><br/>")
                el.remove()
            }
        }

        private fun textToBrHtml(text: String?): String {
            if (text.isNullOrEmpty()) {
                return ""
            }
            val esc = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            return esc.replace("\r\n", "\n").replace("\r", "\n").replace("\n", "<br/>")
        }
    }
}
