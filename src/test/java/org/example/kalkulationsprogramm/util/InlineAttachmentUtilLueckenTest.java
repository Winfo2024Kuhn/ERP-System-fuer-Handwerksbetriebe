package org.example.kalkulationsprogramm.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InlineAttachmentUtilLueckenTest {

    /** cid, inline, url */
    record Anh(String cid, boolean inline, String url) {}

    private String rewrite(String html, List<Anh> list) {
        return InlineAttachmentUtil.rewriteCidSources(html, list, Anh::inline, Anh::cid, Anh::url);
    }

    @Test
    @DisplayName("null/blank HTML wird unveraendert zurueckgegeben")
    void leeresHtml() {
        assertNull(rewrite(null, List.of(new Anh("a", true, "/x"))));
        assertEquals("  ", rewrite("  ", List.of(new Anh("a", true, "/x"))));
    }

    @Test
    @DisplayName("null oder leere Anhangsliste gibt HTML unveraendert zurueck")
    void keineAnhaenge() {
        String html = "<img src=\"cid:a\">";
        assertSame(html, rewrite(html, null));
        assertSame(html, rewrite(html, List.of()));
    }

    @Test
    @DisplayName("Nicht-Inline-Anhaenge und leere Content-IDs fuehren zu keiner Aenderung")
    void keineInlineKandidaten() {
        String html = "<img src=\"cid:a\">";
        List<Anh> list = Arrays.asList(null, new Anh("a", false, "/x"), new Anh(" ", true, "/y"),
                new Anh(null, true, "/z"));
        assertSame(html, rewrite(html, list));
    }

    @Test
    @DisplayName("cid wird durch URL ersetzt und data-inline-cid gesetzt")
    void ersetzt() {
        String out = rewrite("<p>Hallo</p><img src=\"cid:logo123\">", List.of(new Anh("logo123", true, "/api/dl/1")));
        assertTrue(out.contains("src=\"/api/dl/1\""), out);
        assertTrue(out.contains("data-inline-cid=\"logo123\""), out);
        assertTrue(out.contains("<p>Hallo</p>"));
    }

    @Test
    @DisplayName("Spitze Klammern, cid:-Praefix und Gross-/Kleinschreibung werden normalisiert")
    void normalisiert() {
        String out = rewrite("<img src=\"cid:LOGO\">", List.of(new Anh("<logo>", true, "/u")));
        assertTrue(out.contains("src=\"/u\""), out);
        String out2 = rewrite("<img src=\"cid:<Bild1>\">", List.of(new Anh("cid:bild1", true, "/v")));
        assertTrue(out2.contains("src=\"/v\""), out2);
    }

    @Test
    @DisplayName("Unbekannte cid bleibt stehen; Resolver mit null/leer ersetzt nichts")
    void unbekanntOderLeereUrl() {
        String out = rewrite("<img src=\"cid:x\"><img src=\"cid:y\">",
                List.of(new Anh("y", true, null), new Anh("z", true, "/z")));
        assertTrue(out.contains("src=\"cid:x\""));
        assertTrue(out.contains("src=\"cid:y\""));
        assertFalse(out.contains("data-inline-cid"));
        String out2 = rewrite("<img src=\"cid:y\">", List.of(new Anh("y", true, " ")));
        assertTrue(out2.contains("src=\"cid:y\""));
    }

    @Test
    @DisplayName("Bei doppelter cid gewinnt der erste Anhang")
    void ersterGewinnt() {
        String out = rewrite("<img src=\"cid:a\">", List.of(new Anh("a", true, "/erste"), new Anh("a", true, "/zweite")));
        assertTrue(out.contains("/erste"));
        assertFalse(out.contains("/zweite"));
    }

    @Test
    @DisplayName("Nur 'cid:' ohne ID wird uebersprungen, Nicht-cid-Bilder bleiben")
    void randfaelle() {
        String out = rewrite("<img src=\"cid:\"><img src=\"https://example.com/a.png\">", List.of(new Anh("a", true, "/u")));
        assertTrue(out.contains("src=\"cid:\""), out);
        assertTrue(out.contains("https://example.com/a.png"));
    }
}
