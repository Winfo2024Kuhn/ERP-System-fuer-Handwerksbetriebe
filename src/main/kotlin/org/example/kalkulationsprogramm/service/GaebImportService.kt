package org.example.kalkulationsprogramm.service

import org.springframework.stereotype.Service
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import java.util.UUID
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Service zum Parsen von GAEB DA XML (X83) Dateien.
 * Unterstuetzt GAEB DA XML 3.2 und 3.3 Formate.
 */
@Service
class GaebImportService {
    fun parseGaebXml(inputStream: InputStream): List<Map<String, Any>> {
        val blocks = mutableListOf<MutableMap<String, Any>>()
        try {
            val dbFactory = DocumentBuilderFactory.newInstance()
            dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            val dBuilder = dbFactory.newDocumentBuilder()
            val doc = dBuilder.parse(inputStream)
            doc.documentElement.normalize()

            val boqNodes = doc.getElementsByTagName("BoQ")
            if (boqNodes.length == 0) return blocks

            val boq = boqNodes.item(0) as Element
            val boqBodies = getDirectChildElementsByTag(boq, "BoQBody")
            if (boqBodies.isEmpty()) return blocks

            val posCounter = intArrayOf(1)
            processBoQBody(boqBodies[0], blocks, posCounter)
        } catch (e: Exception) {
            throw RuntimeException("Fehler beim Parsen der GAEB-Datei: ${e.message}", e)
        }
        return blocks
    }

    private fun processBoQBody(
        boqBody: Element,
        blocks: MutableList<MutableMap<String, Any>>,
        posCounter: IntArray,
    ) {
        for (child in getDirectChildElements(boqBody)) {
            when (child.tagName) {
                "BoQCtgy" -> processBoQCtgy(child, blocks, posCounter)
                "Itemlist" -> processItemlist(child, blocks, posCounter, null)
            }
        }
    }

    private fun processBoQCtgy(
        ctgy: Element,
        blocks: MutableList<MutableMap<String, Any>>,
        posCounter: IntArray,
    ) {
        val label = extractLblTx(ctgy)
        val bodyElements = getDirectChildElementsByTag(ctgy, "BoQBody")
        if (bodyElements.isEmpty()) return

        val body = bodyElements[0]
        val subCategories = getDirectChildElementsByTag(body, "BoQCtgy")
        if (subCategories.isNotEmpty()) {
            processBoQBody(body, blocks, posCounter)
            return
        }

        val itemlists = getDirectChildElementsByTag(body, "Itemlist")
        if (itemlists.isEmpty()) return

        val section = mutableMapOf<String, Any>(
            "type" to "SECTION_HEADER",
            "id" to UUID.randomUUID().toString(),
            "sectionLabel" to (label ?: "Bauabschnitt"),
        )
        val children = mutableListOf<MutableMap<String, Any>>()
        for (itemlist in itemlists) {
            collectItemlistChildren(itemlist, children, posCounter, label)
        }
        section["children"] = children
        blocks.add(section)
    }

    private fun processItemlist(
        itemlist: Element,
        blocks: MutableList<MutableMap<String, Any>>,
        posCounter: IntArray,
        categoryLabel: String?,
    ) {
        for (child in getDirectChildElements(itemlist)) {
            when (child.tagName) {
                "Item" -> parseItem(child, posCounter[0], categoryLabel).let {
                    blocks.add(it)
                    posCounter[0]++
                }
                "Remark" -> parseRemark(child)?.let(blocks::add)
            }
        }
    }

    private fun collectItemlistChildren(
        itemlist: Element,
        children: MutableList<MutableMap<String, Any>>,
        posCounter: IntArray,
        categoryLabel: String?,
    ) {
        for (child in getDirectChildElements(itemlist)) {
            when (child.tagName) {
                "Item" -> parseItem(child, posCounter[0], categoryLabel).let {
                    children.add(it)
                    posCounter[0]++
                }
                "Remark" -> parseRemark(child)?.let(children::add)
            }
        }
    }

    private fun parseItem(itemElement: Element, posCounter: Int, categoryLabel: String?): MutableMap<String, Any> {
        val map = mutableMapOf<String, Any>(
            "type" to "SERVICE",
            "id" to UUID.randomUUID().toString(),
        )

        val rnoPart = itemElement.getAttribute("RNoPart")
        map["pos"] = if (rnoPart.isNotBlank()) rnoPart.replaceFirst("^0+".toRegex(), "") else posCounter.toString()

        val qtyStr = getDirectTagValue("Qty", itemElement)
        map["quantity"] = qtyStr?.trim()?.toDoubleOrNull() ?: 1.0

        val unit = getDirectTagValue("QU", itemElement)
        map["unit"] = unit?.trim() ?: "Stk"

        val priceStr = getTagValue("UP", itemElement)
        map["price"] = priceStr?.trim()?.toDoubleOrNull() ?: 0.0

        val description = extractDetailText(itemElement)
        map["description"] = if (!description.isNullOrBlank()) {
            compactParagraphs(description)
        } else {
            val legacyDesc = extractLegacyDetailText(itemElement)
            if (legacyDesc != null) compactParagraphs(legacyDesc) else ""
        }

        val title = extractOutlineText(itemElement)
        map["title"] = if (!title.isNullOrBlank()) {
            cleanTextForTitle(title)
        } else {
            val desc = map["description"] as? String
            if (!desc.isNullOrBlank()) extractFirstLine(desc) else categoryLabel ?: "Position"
        }

        return map
    }

    private fun parseRemark(remarkElement: Element): MutableMap<String, Any>? {
        val map = mutableMapOf<String, Any>(
            "type" to "TEXT",
            "id" to UUID.randomUUID().toString(),
        )

        val detailText = extractDetailText(remarkElement)
        if (!detailText.isNullOrBlank()) {
            map["content"] = compactParagraphs(detailText)
        } else {
            val legacyText = extractLegacyDetailText(remarkElement)
            if (!legacyText.isNullOrBlank()) {
                map["content"] = compactParagraphs(legacyText)
            } else {
                return null
            }
        }

        val outline = extractOutlineText(remarkElement)
        if (!outline.isNullOrBlank()) {
            map["title"] = cleanTextForTitle(outline)
        }
        return map
    }

    private fun extractDetailText(parentElement: Element): String? {
        val descNodes = parentElement.getElementsByTagName("Description")
        if (descNodes.length == 0) return null
        val descEl = descNodes.item(0) as Element
        val completeNodes = descEl.getElementsByTagName("CompleteText")
        if (completeNodes.length == 0) return null
        val completeEl = completeNodes.item(0) as Element

        val detailTxtNodes = completeEl.getElementsByTagName("DetailTxt")
        if (detailTxtNodes.length > 0) {
            val detailTxtEl = detailTxtNodes.item(0) as Element
            val textNodes = detailTxtEl.getElementsByTagName("Text")
            return if (textNodes.length > 0) extractHtmlContent(textNodes.item(0)) else extractHtmlContent(detailTxtEl)
        }
        return null
    }

    private fun extractLegacyDetailText(parentElement: Element): String? {
        val descNodes = parentElement.getElementsByTagName("Description")
        if (descNodes.length == 0) return null
        val descEl = descNodes.item(0) as Element
        val completeNodes = descEl.getElementsByTagName("CompleteText")
        if (completeNodes.length == 0) return null
        val completeEl = completeNodes.item(0) as Element
        val detailTextNodes = completeEl.getElementsByTagName("DetailText")
        if (detailTextNodes.length > 0) {
            val textNodes = (detailTextNodes.item(0) as Element).getElementsByTagName("Text")
            return if (textNodes.length > 0) extractHtmlContent(textNodes.item(0)) else extractHtmlContent(detailTextNodes.item(0))
        }
        return null
    }

    private fun extractOutlineText(parentElement: Element): String? {
        val descNodes = parentElement.getElementsByTagName("Description")
        if (descNodes.length == 0) return null
        val descEl = descNodes.item(0) as Element
        val completeNodes = descEl.getElementsByTagName("CompleteText")
        if (completeNodes.length == 0) return null
        val completeEl = completeNodes.item(0) as Element
        val outlineNodes = completeEl.getElementsByTagName("OutlineText")
        if (outlineNodes.length == 0) return null
        val outlineEl = outlineNodes.item(0) as Element
        val outlTxtNodes = outlineEl.getElementsByTagName("OutlTxt")
        if (outlTxtNodes.length == 0) return null
        val outlTxtEl = outlTxtNodes.item(0) as Element
        val textOutlNodes = outlTxtEl.getElementsByTagName("TextOutlTxt")
        return if (textOutlNodes.length > 0) extractHtmlContent(textOutlNodes.item(0)) else extractHtmlContent(outlTxtEl)
    }

    private fun extractHtmlContent(node: Node): String {
        val sb = StringBuilder()
        val children = node.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            when (child.nodeType) {
                Node.TEXT_NODE -> {
                    val text = child.nodeValue
                    if (!text.isNullOrBlank()) {
                        sb.append(escapeHtml(text.trim()))
                    }
                }
                Node.ELEMENT_NODE -> {
                    when (child.nodeName.lowercase()) {
                        "p" -> {
                            val innerHtml = extractInlineContent(child)
                            if (innerHtml.isBlank()) sb.append("<p></p>") else sb.append("<p>").append(innerHtml).append("</p>")
                        }
                        "br" -> sb.append("<br>")
                        "span" -> sb.append(extractInlineContent(child))
                        else -> sb.append(extractHtmlContent(child))
                    }
                }
            }
        }
        return sb.toString().trim()
    }

    private fun extractInlineContent(node: Node): String {
        val sb = StringBuilder()
        val children = node.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            when (child.nodeType) {
                Node.TEXT_NODE -> child.nodeValue?.let { sb.append(escapeHtml(it)) }
                Node.ELEMENT_NODE -> sb.append(extractInlineContent(child))
            }
        }
        return sb.toString()
    }

    private fun getDirectTagValue(tag: String, element: Element): String? {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE && tag == child.nodeName) {
                return child.textContent
            }
        }
        return null
    }

    private fun getTagValue(tag: String, element: Element): String? {
        val nodeList = element.getElementsByTagName(tag)
        return if (nodeList.length > 0) nodeList.item(0).textContent else null
    }

    private fun extractLblTx(ctgy: Element): String? {
        val children = ctgy.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE && "LblTx" == node.nodeName) {
                val text = node.textContent.trim().replace("<[^>]+>".toRegex(), "").trim()
                return text.ifBlank { null }
            }
        }
        return null
    }

    private fun getDirectChildElements(parent: Element): List<Element> {
        val elements = mutableListOf<Element>()
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                elements.add(child as Element)
            }
        }
        return elements
    }

    private fun getDirectChildElementsByTag(parent: Element, tagName: String): List<Element> =
        getDirectChildElements(parent).filter { tagName == it.nodeName }

    private fun extractFirstLine(html: String?): String {
        if (html == null) return "Position"
        var text = html.replace("<[^>]+>".toRegex(), " ").replace("\\s+".toRegex(), " ").trim()
        text = decodeHtmlEntities(text)
        if (text.length > 120) {
            text = text.substring(0, 120).trim() + "..."
        }
        return text.ifBlank { "Position" }
    }

    private fun cleanTextForTitle(html: String?): String {
        if (html == null) return "Position"
        var text = html.replace("<[^>]+>".toRegex(), " ").replace("\\s+".toRegex(), " ").trim()
        text = decodeHtmlEntities(text)
        if (text.length > 150) {
            text = text.substring(0, 150).trim() + "..."
        }
        return text.ifBlank { "Position" }
    }

    private fun escapeHtml(text: String?): String =
        text.orEmpty()
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    private fun decodeHtmlEntities(text: String?): String =
        text.orEmpty()
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")

    private fun compactParagraphs(html: String): String {
        if (html.isBlank()) return html
        if (!html.contains("<p")) return html

        val entries = mutableListOf<String>()
        val matcher = P_TAG_PATTERN.matcher(html)
        while (matcher.find()) {
            val content = matcher.group(1)
            entries.add(if (!content.isNullOrBlank()) content.trim() else "")
        }
        if (entries.isEmpty()) return html

        val result = StringBuilder()
        var inParagraph = false
        var consecutiveEmpty = 0
        for (entry in entries) {
            if (entry.isEmpty()) {
                consecutiveEmpty++
                if (consecutiveEmpty >= 2 && inParagraph) {
                    result.append("</p>")
                    inParagraph = false
                    consecutiveEmpty = 0
                }
            } else {
                if (consecutiveEmpty == 1 && inParagraph) {
                    result.append("<br>")
                }
                consecutiveEmpty = 0
                if (!inParagraph) {
                    result.append("<p>")
                    inParagraph = true
                } else {
                    result.append("<br>")
                }
                result.append(entry)
            }
        }
        if (inParagraph) {
            result.append("</p>")
        }

        val compacted = result.toString().trim()
        return compacted.ifEmpty { html }
    }

    companion object {
        private val P_TAG_PATTERN = Pattern.compile(
            "<p(?:\\s[^>]*)?>\\s*(.*?)\\s*</p>|<p(?:\\s[^>]*)?\\s*/>",
            Pattern.DOTALL or Pattern.CASE_INSENSITIVE,
        )
    }
}
