package org.example.kalkulationsprogramm.service;

import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service zum Parsen von GAEB DA XML (X83) Dateien.
 * Unterstützt GAEB DA XML 3.2 und 3.3 Formate.
 *
 * Erkennt hierarchische Bauabschnitte (BoQCtgy > BoQCtgy)
 * und erstellt SECTION_HEADER-Blöcke mit untergeordneten Leistungen.
 *
 * XML-Struktur: GAEB > Award > BoQ > BoQBody > [BoQCtgy hierarchy] > Itemlist > Item/Remark
 * Item-Textstruktur: Description > CompleteText > DetailTxt > Text > [HTML]
 * Item-Kurztext:     Description > CompleteText > OutlineText > OutlTxt > TextOutlTxt > [HTML]
 */
@Service
public class GaebImportService {

    /** Pattern to match &lt;p ...&gt;content&lt;/p&gt; or self-closing &lt;p .../&gt; */
    private static final Pattern P_TAG_PATTERN = Pattern.compile(
        "<p(?:\\s[^>]*)?>\\s*(.*?)\\s*</p>|<p(?:\\s[^>]*)?\\s*/>",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    public List<Map<String, Object>> parseGaebXml(InputStream inputStream) {
        List<Map<String, Object>> blocks = new ArrayList<>();

        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            // Secure processing to prevent XXE attacks
            dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            // Keep namespace-awareness OFF so getElementsByTagName works with local names
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(inputStream);
            doc.getDocumentElement().normalize();

            // Find the top-level BoQ element
            NodeList boqNodes = doc.getElementsByTagName("BoQ");
            if (boqNodes.getLength() == 0) return blocks;

            Element boq = (Element) boqNodes.item(0);
            List<Element> boqBodies = getDirectChildElementsByTag(boq, "BoQBody");
            if (boqBodies.isEmpty()) return blocks;

            int[] posCounter = {1};
            processBoQBody(boqBodies.get(0), blocks, posCounter);

        } catch (Exception e) {
            throw new RuntimeException("Fehler beim Parsen der GAEB-Datei: " + e.getMessage(), e);
        }

        return blocks;
    }

    // ========== Recursive Category Processing ==========

    /**
     * Processes a BoQBody element which can contain BoQCtgy (categories) or Itemlist elements.
     */
    private void processBoQBody(Element boqBody, List<Map<String, Object>> blocks, int[] posCounter) {
        for (Element child : getDirectChildElements(boqBody)) {
            String tag = child.getTagName();
            if ("BoQCtgy".equals(tag)) {
                processBoQCtgy(child, blocks, posCounter);
            } else if ("Itemlist".equals(tag)) {
                // Flat Itemlist without category wrapper
                processItemlist(child, blocks, posCounter, null);
            }
        }
    }

    /**
     * Processes a BoQCtgy element. If it contains sub-categories, recurses.
     * If it is a leaf category (contains Itemlist), creates a SECTION_HEADER.
     */
    private void processBoQCtgy(Element ctgy, List<Map<String, Object>> blocks, int[] posCounter) {
        String label = extractLblTx(ctgy);

        List<Element> bodyElements = getDirectChildElementsByTag(ctgy, "BoQBody");
        if (bodyElements.isEmpty()) return;

        Element body = bodyElements.get(0);
        List<Element> subCategories = getDirectChildElementsByTag(body, "BoQCtgy");

        if (!subCategories.isEmpty()) {
            // Parent category with sub-categories → recurse deeper
            processBoQBody(body, blocks, posCounter);
        } else {
            // Leaf category → create SECTION_HEADER with children
            List<Element> itemlists = getDirectChildElementsByTag(body, "Itemlist");
            if (itemlists.isEmpty()) return;

            Map<String, Object> section = new HashMap<>();
            section.put("type", "SECTION_HEADER");
            section.put("id", java.util.UUID.randomUUID().toString());
            section.put("sectionLabel", label != null ? label : "Bauabschnitt");

            List<Map<String, Object>> children = new ArrayList<>();
            for (Element itemlist : itemlists) {
                collectItemlistChildren(itemlist, children, posCounter, label);
            }
            section.put("children", children);
            blocks.add(section);
        }
    }

    /**
     * Processes an Itemlist adding blocks directly to a flat list.
     * Used when items are not within a named category.
     */
    private void processItemlist(Element itemlist, List<Map<String, Object>> blocks, int[] posCounter, String categoryLabel) {
        for (Element child : getDirectChildElements(itemlist)) {
            String tag = child.getTagName();
            if ("Item".equals(tag)) {
                Map<String, Object> item = parseItem(child, posCounter[0], categoryLabel);
                if (item != null) {
                    blocks.add(item);
                    posCounter[0]++;
                }
            } else if ("Remark".equals(tag)) {
                Map<String, Object> remark = parseRemark(child);
                if (remark != null) blocks.add(remark);
            }
        }
    }

    /**
     * Collects Item and Remark elements from an Itemlist as children of a SECTION_HEADER.
     */
    private void collectItemlistChildren(Element itemlist, List<Map<String, Object>> children, int[] posCounter, String categoryLabel) {
        for (Element child : getDirectChildElements(itemlist)) {
            String tag = child.getTagName();
            if ("Item".equals(tag)) {
                Map<String, Object> item = parseItem(child, posCounter[0], categoryLabel);
                if (item != null) {
                    children.add(item);
                    posCounter[0]++;
                }
            } else if ("Remark".equals(tag)) {
                Map<String, Object> remark = parseRemark(child);
                if (remark != null) children.add(remark);
            }
        }
    }

    /**
     * Parst ein Item-Element zu einem SERVICE-Block.
     */
    private Map<String, Object> parseItem(Element itemElement, int posCounter, String categoryLabel) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", "SERVICE");
        map.put("id", java.util.UUID.randomUUID().toString());

        // Position number from RNoPart attribute (e.g. "0001", "0002")
        String rnoPart = itemElement.getAttribute("RNoPart");
        if (rnoPart != null && !rnoPart.isBlank()) {
            // Strip leading zeros for display: "0001" -> "1"
            map.put("pos", rnoPart.replaceFirst("^0+", ""));
        } else {
            map.put("pos", String.valueOf(posCounter));
        }

        // Quantity (Menge) - Tag: Qty
        String qtyStr = getDirectTagValue("Qty", itemElement);
        if (qtyStr != null) {
            try {
                map.put("quantity", Double.parseDouble(qtyStr.trim()));
            } catch (NumberFormatException e) {
                map.put("quantity", 1.0);
            }
        } else {
            map.put("quantity", 1.0);
        }

        // Unit (Einheit) - Tag: QU
        String unit = getDirectTagValue("QU", itemElement);
        map.put("unit", unit != null ? unit.trim() : "Stk");

        // Price (EP) - Tag: UP (Unit Price)
        String priceStr = getTagValue("UP", itemElement);
        if (priceStr != null) {
            try {
                map.put("price", Double.parseDouble(priceStr.trim()));
            } catch (NumberFormatException e) {
                map.put("price", 0.0);
            }
        } else {
            map.put("price", 0.0);
        }

        // === Description (Langtext) ===
        // Path: Description > CompleteText > DetailTxt > Text > [HTML content]
        String description = extractDetailText(itemElement);
        if (description != null && !description.isBlank()) {
            map.put("description", compactParagraphs(description));
        } else {
            // Fallback: try legacy tag name "DetailText"
            String legacyDesc = extractLegacyDetailText(itemElement);
            map.put("description", legacyDesc != null ? compactParagraphs(legacyDesc) : "");
        }

        // === Title (Kurztext) ===
        // Path: Description > CompleteText > OutlineText > OutlTxt > TextOutlTxt > [HTML content]
        String title = extractOutlineText(itemElement);
        if (title != null && !title.isBlank()) {
            map.put("title", cleanTextForTitle(title));
        } else {
            // Fallback: use first line of description or category
            String desc = (String) map.get("description");
            if (desc != null && !desc.isBlank()) {
                map.put("title", extractFirstLine(desc));
            } else {
                map.put("title", categoryLabel != null ? categoryLabel : "Position");
            }
        }

        return map;
    }

    /**
     * Parst ein Remark-Element zu einem TEXT-Block (Hinweistext ohne Menge/Preis).
     */
    private Map<String, Object> parseRemark(Element remarkElement) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", "TEXT");
        map.put("id", java.util.UUID.randomUUID().toString());

        // Remark has the same Description structure as Item
        String detailText = extractDetailText(remarkElement);
        if (detailText != null && !detailText.isBlank()) {
            map.put("content", compactParagraphs(detailText));
        } else {
            String legacyText = extractLegacyDetailText(remarkElement);
            if (legacyText != null && !legacyText.isBlank()) {
                map.put("content", compactParagraphs(legacyText));
            } else {
                return null; // Skip empty remarks
            }
        }

        // Also extract outline text for a title hint
        String outline = extractOutlineText(remarkElement);
        if (outline != null && !outline.isBlank()) {
            map.put("title", cleanTextForTitle(outline));
        }

        return map;
    }

    // ========== Text Extraction Methods ==========

    /**
     * Extracts HTML content from: Description > CompleteText > DetailTxt > Text
     * This is the GAEB DA XML 3.x standard path.
     */
    private String extractDetailText(Element parentElement) {
        NodeList descNodes = parentElement.getElementsByTagName("Description");
        if (descNodes.getLength() == 0) return null;

        Element descEl = (Element) descNodes.item(0);
        NodeList completeNodes = descEl.getElementsByTagName("CompleteText");
        if (completeNodes.getLength() == 0) return null;

        Element completeEl = (Element) completeNodes.item(0);

        // Try "DetailTxt" first (GAEB 3.x standard)
        NodeList detailTxtNodes = completeEl.getElementsByTagName("DetailTxt");
        if (detailTxtNodes.getLength() > 0) {
            Element detailTxtEl = (Element) detailTxtNodes.item(0);

            // Inside DetailTxt, the actual HTML content is wrapped in <Text>
            NodeList textNodes = detailTxtEl.getElementsByTagName("Text");
            if (textNodes.getLength() > 0) {
                return extractHtmlContent(textNodes.item(0));
            }
            // Fallback: get HTML directly from DetailTxt if no <Text> wrapper
            return extractHtmlContent(detailTxtEl);
        }

        return null;
    }

    /**
     * Fallback: tries legacy tag "DetailText" (without the abbreviation).
     */
    private String extractLegacyDetailText(Element parentElement) {
        NodeList descNodes = parentElement.getElementsByTagName("Description");
        if (descNodes.getLength() == 0) return null;

        Element descEl = (Element) descNodes.item(0);
        NodeList completeNodes = descEl.getElementsByTagName("CompleteText");
        if (completeNodes.getLength() == 0) return null;

        Element completeEl = (Element) completeNodes.item(0);
        NodeList detailTextNodes = completeEl.getElementsByTagName("DetailText");
        if (detailTextNodes.getLength() > 0) {
            NodeList textNodes = ((Element) detailTextNodes.item(0)).getElementsByTagName("Text");
            if (textNodes.getLength() > 0) {
                return extractHtmlContent(textNodes.item(0));
            }
            return extractHtmlContent(detailTextNodes.item(0));
        }

        return null;
    }

    /**
     * Extracts short text from: Description > CompleteText > OutlineText > OutlTxt > TextOutlTxt
     */
    private String extractOutlineText(Element parentElement) {
        NodeList descNodes = parentElement.getElementsByTagName("Description");
        if (descNodes.getLength() == 0) return null;

        Element descEl = (Element) descNodes.item(0);
        NodeList completeNodes = descEl.getElementsByTagName("CompleteText");
        if (completeNodes.getLength() == 0) return null;

        Element completeEl = (Element) completeNodes.item(0);
        NodeList outlineNodes = completeEl.getElementsByTagName("OutlineText");
        if (outlineNodes.getLength() == 0) return null;

        Element outlineEl = (Element) outlineNodes.item(0);
        NodeList outlTxtNodes = outlineEl.getElementsByTagName("OutlTxt");
        if (outlTxtNodes.getLength() == 0) return null;

        Element outlTxtEl = (Element) outlTxtNodes.item(0);
        NodeList textOutlNodes = outlTxtEl.getElementsByTagName("TextOutlTxt");
        if (textOutlNodes.getLength() > 0) {
            return extractHtmlContent(textOutlNodes.item(0));
        }

        // Fallback: get content directly from OutlTxt
        return extractHtmlContent(outlTxtEl);
    }

    // ========== HTML Content Helpers ==========

    /**
     * Extracts the inner HTML of a node, preserving p/span structure
     * as clean HTML suitable for TiptapEditor.
     * Strips inline styles but keeps paragraph structure.
     */
    private String extractHtmlContent(Node node) {
        StringBuilder sb = new StringBuilder();
        NodeList children = node.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);

            if (child.getNodeType() == Node.TEXT_NODE) {
                String text = child.getNodeValue();
                if (text != null && !text.isBlank()) {
                    sb.append(escapeHtml(text.trim()));
                }
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                String tag = child.getNodeName().toLowerCase();

                if ("p".equals(tag)) {
                    String innerHtml = extractInlineContent(child);
                    if (innerHtml.isBlank()) {
                        // Empty paragraph acts as line break
                        sb.append("<p></p>");
                    } else {
                        sb.append("<p>").append(innerHtml).append("</p>");
                    }
                } else if ("br".equals(tag)) {
                    sb.append("<br>");
                } else if ("span".equals(tag)) {
                    sb.append(extractInlineContent(child));
                } else {
                    // For any other element, recurse
                    sb.append(extractHtmlContent(child));
                }
            } else if (child.getNodeType() == Node.COMMENT_NODE) {
                // Skip XML comments (e.g. "<!-- GAEB version used: 320 -->")
            }
        }

        return sb.toString().trim();
    }

    /**
     * Extract text content from inline elements (span, etc.) without wrapping tags.
     */
    private String extractInlineContent(Node node) {
        StringBuilder sb = new StringBuilder();
        NodeList children = node.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                String text = child.getNodeValue();
                if (text != null) {
                    sb.append(escapeHtml(text));
                }
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                sb.append(extractInlineContent(child));
            }
        }
        return sb.toString();
    }

    // ========== Utility Methods ==========

    /**
     * Gets the text content of the first matching direct-descendant tag.
     */
    private String getDirectTagValue(String tag, Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && tag.equals(child.getNodeName())) {
                return child.getTextContent();
            }
        }
        return null;
    }

    /**
     * Gets the text content of the first matching tag anywhere below the element.
     */
    private String getTagValue(String tag, Element element) {
        NodeList nodeList = element.getElementsByTagName(tag);
        if (nodeList != null && nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent();
        }
        return null;
    }

    /**
     * Extracts the LblTx (label text) from a BoQCtgy element.
     * Returns plain text stripped of HTML.
     */
    private String extractLblTx(Element ctgy) {
        NodeList children = ctgy.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && "LblTx".equals(n.getNodeName())) {
                String text = n.getTextContent().trim();
                // Strip any HTML tags from the label
                text = text.replaceAll("<[^>]+>", "").trim();
                return text.isBlank() ? null : text;
            }
        }
        return null;
    }

    /**
     * Gets all direct child Elements of a parent element.
     */
    private List<Element> getDirectChildElements(Element parent) {
        List<Element> elements = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                elements.add((Element) child);
            }
        }
        return elements;
    }

    /**
     * Gets direct child Elements with a specific tag name.
     */
    private List<Element> getDirectChildElementsByTag(Element parent, String tagName) {
        List<Element> elements = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && tagName.equals(child.getNodeName())) {
                elements.add((Element) child);
            }
        }
        return elements;
    }

    /**
     * Extracts the first meaningful line of text from HTML content.
     * Used as fallback title.
     */
    private String extractFirstLine(String html) {
        if (html == null) return "Position";
        // Strip HTML tags and decode entities
        String text = html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        text = decodeHtmlEntities(text);
        // Take first ~120 characters
        if (text.length() > 120) {
            text = text.substring(0, 120).trim() + "...";
        }
        return text.isBlank() ? "Position" : text;
    }

    /**
     * Converts HTML text content to a clean single-line title.
     */
    private String cleanTextForTitle(String html) {
        if (html == null) return "Position";
        String text = html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        text = decodeHtmlEntities(text);
        if (text.length() > 150) {
            text = text.substring(0, 150).trim() + "...";
        }
        return text.isBlank() ? "Position" : text;
    }

    /**
     * Basic HTML entity escaping for text content.
     * Note: Quotes (") do NOT need escaping in HTML body text, only in attribute values.
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;");
    }

    /**
     * Decodes basic HTML entities back to plain text characters.
     */
    private String decodeHtmlEntities(String text) {
        if (text == null) return "";
        return text.replace("&quot;", "\"")
                   .replace("&amp;", "&")
                   .replace("&lt;", "<")
                   .replace("&gt;", ">")
                   .replace("&#39;", "'");
    }

    // ========== Paragraph Compaction ==========

    /**
     * Compacts paragraph HTML for better visual appearance:
     * - Consecutive non-empty paragraphs are joined with &lt;br&gt; (visual line breaks).
     * - A single empty paragraph between text = just a &lt;br&gt; (minor visual break).
     * - Two consecutive empty paragraphs = actual paragraph break (&lt;/p&gt;&lt;p&gt;).
     *
     * This transforms verbose GAEB HTML (every line in its own &lt;p&gt;) into
     * compact HTML where only double-empty-lines create real paragraph spacing.
     */
    private String compactParagraphs(String html) {
        if (html == null || html.isBlank()) return html;
        if (!html.contains("<p")) return html; // No paragraphs, return as-is

        // Parse all paragraphs into content entries
        List<String> entries = new ArrayList<>();
        Matcher m = P_TAG_PATTERN.matcher(html);
        while (m.find()) {
            String content = m.group(1);
            if (content != null && !content.trim().isEmpty()) {
                entries.add(content.trim());
            } else {
                entries.add(""); // empty paragraph marker
            }
        }

        if (entries.isEmpty()) return html; // no <p> tags found, return as-is

        // Build compacted output
        StringBuilder result = new StringBuilder();
        boolean inParagraph = false;
        int consecutiveEmpty = 0;

        for (String entry : entries) {
            if (entry.isEmpty()) {
                consecutiveEmpty++;
                if (consecutiveEmpty >= 2 && inParagraph) {
                    // Double empty → actual paragraph break
                    result.append("</p>");
                    inParagraph = false;
                    consecutiveEmpty = 0;
                }
            } else {
                if (consecutiveEmpty == 1 && inParagraph) {
                    // Single empty between content → just a visual line break
                    result.append("<br>");
                }
                consecutiveEmpty = 0;

                if (!inParagraph) {
                    result.append("<p>");
                    inParagraph = true;
                } else {
                    result.append("<br>");
                }
                result.append(entry);
            }
        }

        if (inParagraph) {
            result.append("</p>");
        }

        String compacted = result.toString().trim();
        return compacted.isEmpty() ? html : compacted;
    }
}
