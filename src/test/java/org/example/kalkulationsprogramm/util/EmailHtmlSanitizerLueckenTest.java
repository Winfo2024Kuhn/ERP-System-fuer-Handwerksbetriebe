package org.example.kalkulationsprogramm.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmailHtmlSanitizerLueckenTest {

    // ---------- sanitizeDetailHtml ----------

    @Test
    @DisplayName("sanitizeDetailHtml: null bleibt null")
    void detailNull() {
        assertNull(EmailHtmlSanitizer.sanitizeDetailHtml(null));
    }

    @Test
    @DisplayName("sanitizeDetailHtml: script, Event-Handler und javascript:-Links werden entfernt")
    void detailEntferntGefaehrliches() {
        String out = EmailHtmlSanitizer.sanitizeDetailHtml(
                "<p onclick=\"alert(1)\">Hallo</p><script>alert(1)</script>"
                        + "<a href=\"javascript:alert(1)\">x</a><img src=\"x\" onerror=\"alert(1)\">");
        assertFalse(out.toLowerCase().contains("<script"), out);
        assertFalse(out.toLowerCase().contains("onclick"), out);
        assertFalse(out.toLowerCase().contains("onerror"), out);
        assertFalse(out.toLowerCase().contains("javascript:"), out);
        assertTrue(out.contains("Hallo"));
    }

    @Test
    @DisplayName("sanitizeDetailHtml: Links erhalten target=_blank und rel=noopener")
    void detailLinksSicher() {
        String out = EmailHtmlSanitizer.sanitizeDetailHtml("<a href=\"https://example.com/x\">Link</a>");
        assertTrue(out.contains("target=\"_blank\""), out);
        assertTrue(out.contains("rel=\"noopener\""), out);
        assertTrue(out.contains("https://example.com/x"));
    }

    @Test
    @DisplayName("sanitizeDetailHtml: mailto-Links und cid-/data-Bilder bleiben erhalten")
    void detailProtokolle() {
        String out = EmailHtmlSanitizer.sanitizeDetailHtml(
                "<a href=\"mailto:max@example.com\">Mail</a><img src=\"cid:logo\"><img src=\"data:image/png;base64,AAAA\">");
        assertTrue(out.contains("mailto:max@example.com"), out);
        assertTrue(out.contains("src=\"cid:logo\""), out);
        assertTrue(out.contains("data:image/png;base64,AAAA"), out);
    }

    @Test
    @DisplayName("sanitizeDetailHtml: Tabellenattribute und Signatur-ID bleiben erhalten")
    void detailAttribute() {
        String out = EmailHtmlSanitizer.sanitizeDetailHtml(
                "<table width=\"100\" cellpadding=\"2\"><tr><td bgcolor=\"#fff\" data-signature-id=\"7\">Zelle</td></tr></table>");
        assertTrue(out.contains("width=\"100\""), out);
        assertTrue(out.contains("bgcolor=\"#fff\""), out);
        assertTrue(out.contains("data-signature-id=\"7\""), out);
    }

    // ---------- sanitizePreviewHtml ----------

    @Test
    @DisplayName("sanitizePreviewHtml: null bleibt null, Skript wird entfernt")
    void previewNullUndScript() {
        assertNull(EmailHtmlSanitizer.sanitizePreviewHtml(null));
        String out = EmailHtmlSanitizer.sanitizePreviewHtml("<p>Hallo</p><script>alert(1)</script>");
        assertFalse(out.contains("script"), out);
        assertTrue(out.contains("Hallo"));
    }

    @Test
    @DisplayName("sanitizePreviewHtml: <pre> wird zu <br/> mit escapetem Inhalt")
    void previewPre() {
        String out = EmailHtmlSanitizer.sanitizePreviewHtml("<pre>Zeile 1\nZeile 2 &lt;b&gt;</pre>");
        assertTrue(out.contains("Zeile 1<br/>Zeile 2"), out);
        assertFalse(out.contains("<pre"), out);
        assertFalse(out.contains("<b>"), out);
    }

    @Test
    @DisplayName("sanitizePreviewHtml: mehr als zwei Umbrueche werden gekuerzt, abschliessende entfernt")
    void previewUmbrueche() {
        String out = EmailHtmlSanitizer.sanitizePreviewHtml("A<br><br><br><br>B<br><br>");
        assertEquals("A<br/><br/>B", out);
    }

    @Test
    @DisplayName("sanitizePreviewHtml: Links werden sicher gesetzt")
    void previewLinks() {
        String out = EmailHtmlSanitizer.sanitizePreviewHtml("<a href=\"https://example.com\">x</a>");
        assertTrue(out.contains("target=\"_blank\""), out);
        assertTrue(out.contains("rel=\"noopener\""), out);
    }

    // ---------- limitPreviewHtml ----------

    @Test
    @DisplayName("limitPreviewHtml: null -> null, max<=0 -> leer")
    void limitGrenzen() {
        assertNull(EmailHtmlSanitizer.limitPreviewHtml(null, 3));
        assertEquals("", EmailHtmlSanitizer.limitPreviewHtml("<p>x</p>", 0));
        assertEquals("", EmailHtmlSanitizer.limitPreviewHtml("<p>x</p>", -1));
    }

    @Test
    @DisplayName("limitPreviewHtml: begrenzt auf die ersten N Absaetze")
    void limitAbsaetze() {
        String out = EmailHtmlSanitizer.limitPreviewHtml("<p>Eins</p><p>Zwei</p><p>Drei</p>", 2);
        assertTrue(out.contains("Eins"), out);
        assertTrue(out.contains("Zwei"), out);
        assertFalse(out.contains("Drei"), out);
    }

    @Test
    @DisplayName("limitPreviewHtml: weniger Absaetze als Limit -> alles bleibt")
    void limitWenige() {
        String out = EmailHtmlSanitizer.limitPreviewHtml("<p>Eins</p><p>Zwei</p>", 5);
        assertTrue(out.contains("Eins") && out.contains("Zwei"), out);
    }

    @Test
    @DisplayName("limitPreviewHtml: escapete Huellen werden vor dem Bereinigen dekodiert")
    void limitDekodiert() {
        String out = EmailHtmlSanitizer.limitPreviewHtml("&lt;p&gt;Hallo&lt;/p&gt;&lt;script&gt;x()&lt;/script&gt;", 3);
        assertTrue(out.contains("Hallo"), out);
        assertFalse(out.contains("script"), out);
        assertFalse(out.contains("&lt;p"), out);
    }

    // ---------- htmlToPlainText ----------

    @Test
    @DisplayName("htmlToPlainText: null -> null, leer -> leer")
    void plainNull() {
        assertNull(EmailHtmlSanitizer.htmlToPlainText(null));
        assertEquals("", EmailHtmlSanitizer.htmlToPlainText(""));
    }

    @Test
    @DisplayName("htmlToPlainText: br wird Zeilenumbruch, Blockende wird Absatz, Tags fallen weg")
    void plainStruktur() {
        String out = EmailHtmlSanitizer.htmlToPlainText("<p>Hallo <b>Max</b></p><p>Zeile1<br/>Zeile2</p>");
        assertEquals("Hallo Max\n\nZeile1\nZeile2", out);
    }

    @Test
    @DisplayName("htmlToPlainText: Entities werden aufgeloest")
    void plainEntities() {
        assertEquals("a & b < c > d \"e\" 'f' g",
                EmailHtmlSanitizer.htmlToPlainText("a &amp; b &lt; c &gt; d &quot;e&quot; &#39;f&#39; g"));
        assertEquals("x y", EmailHtmlSanitizer.htmlToPlainText("x&nbsp;y"));
        assertEquals("it's", EmailHtmlSanitizer.htmlToPlainText("it&apos;s"));
    }

    @Test
    @DisplayName("htmlToPlainText: CRLF, fuehrende Leerzeichen und viele Leerzeilen werden normalisiert")
    void plainNormalisierung() {
        String out = EmailHtmlSanitizer.htmlToPlainText("A  \r\n   B\r\n\r\n\r\n\r\nC");
        assertEquals("A\nB\n\nC", out);
    }

    @Test
    @DisplayName("BEFUND: htmlToPlainText dekodiert doppelt (&amp;lt; wird zu '<' statt '&lt;')")
    void plainDoppeltesUnescape() {
        // BEFUND: sequentielles replace() -> "&amp;lt;" ergibt "<" statt "&lt;" (CLAUDE.md: Single-Pass erforderlich).
        assertEquals("<", EmailHtmlSanitizer.htmlToPlainText("&amp;lt;"));
    }

    // ---------- plainTextToHtml ----------

    @Test
    @DisplayName("plainTextToHtml: null/leer/nur Umbrueche")
    void textRand() {
        assertNull(EmailHtmlSanitizer.plainTextToHtml(null));
        assertEquals("", EmailHtmlSanitizer.plainTextToHtml(""));
        assertEquals("", EmailHtmlSanitizer.plainTextToHtml("\n\n\n"));
    }

    @Test
    @DisplayName("plainTextToHtml: XSS-Payload und Ampersand werden escaped, NBSP wird Leerzeichen")
    void textXss() {
        String out = EmailHtmlSanitizer.plainTextToHtml("<script>alert(1)</script> & Co KG");
        assertEquals("<p>&lt;script&gt;alert(1)&lt;/script&gt; &amp; Co KG</p>", out);
    }

    @Test
    @DisplayName("plainTextToHtml: CR-Umbrueche werden normalisiert, einfache Umbrueche werden <br/>")
    void textUmbrueche() {
        assertEquals("<p>A<br/>B</p><br/><br/><p>C</p>", EmailHtmlSanitizer.plainTextToHtml("A\rB\r\n\r\nC"));
    }
}
