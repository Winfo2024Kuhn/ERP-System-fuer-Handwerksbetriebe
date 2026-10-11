package org.example.kalkulationsprogramm.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignaturHtmlPruefungTest {

    private static final String PNG_1PX = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";

    @Test
    @DisplayName("Name: getrimmt, Pflicht, höchstens 200 Zeichen")
    void name() {
        assertThat(SignaturHtmlPruefung.pruefeName("  Standard  ")).isEqualTo("Standard");
        assertThatThrownBy(() -> SignaturHtmlPruefung.pruefeName(null)).hasMessageContaining("Namen");
        assertThatThrownBy(() -> SignaturHtmlPruefung.pruefeName("   ")).hasMessageContaining("Namen");
        assertThat(SignaturHtmlPruefung.pruefeName("x".repeat(200))).hasSize(200);
        assertThatThrownBy(() -> SignaturHtmlPruefung.pruefeName("x".repeat(201))).hasMessageContaining("zu lang");
    }

    @Test
    @DisplayName("Formulardaten: leer wird null, zu groß wird abgelehnt")
    void vorlageDaten() {
        assertThat(SignaturHtmlPruefung.pruefeVorlageDaten(null)).isNull();
        assertThat(SignaturHtmlPruefung.pruefeVorlageDaten("  ")).isNull();
        assertThat(SignaturHtmlPruefung.pruefeVorlageDaten("{\"a\":1}")).isEqualTo("{\"a\":1}");
        String zuGross = "x".repeat(SignaturHtmlPruefung.MAX_ZEICHEN + 1);
        assertThatThrownBy(() -> SignaturHtmlPruefung.pruefeVorlageDaten(zuGross)).hasMessageContaining("zu groß");
    }

    @Test
    @DisplayName("Leeres oder übergroßes HTML wird abgelehnt")
    void leerOderZuGross() {
        assertThatThrownBy(() -> SignaturHtmlPruefung.bereinige(null)).hasMessageContaining("leer");
        assertThatThrownBy(() -> SignaturHtmlPruefung.bereinige(" ")).hasMessageContaining("leer");
        assertThatThrownBy(() -> SignaturHtmlPruefung.bereinige("<script>alert(1)</script>")).hasMessageContaining("leer");
        String zuGross = "<p>" + "x".repeat(SignaturHtmlPruefung.MAX_ZEICHEN) + "</p>";
        assertThatThrownBy(() -> SignaturHtmlPruefung.bereinige(zuGross)).hasMessageContaining("zu groß");
    }

    @Test
    @DisplayName("Skripte, Event-Handler und javascript:-Links fliegen raus")
    void gefaehrlichesWirdEntfernt() {
        String html = SignaturHtmlPruefung.bereinige(
                "<p onclick=\"alert(1)\">Max Mustermann</p><script>alert(1)</script>"
                        + "<a href=\"javascript:alert(1)\">Klick</a><img src=\"javascript:alert(1)\" onerror=\"x()\">");
        assertThat(html).doesNotContain("script", "onclick", "onerror", "javascript");
        assertThat(html).contains("Max Mustermann", "Klick");
    }

    @Test
    @DisplayName("Signatur-Layout bleibt erhalten: Tabellen, Styles, Links, Telefon, Bilder")
    void layoutBleibt() {
        String eingabe = "<table cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"font-family: Arial;\"><tbody><tr>"
                + "<td width=\"46\" aria-label=\"Vertical Spacer\" style=\"vertical-align: top;\">"
                + "<img src=\"https://example.com/logo.png\" width=\"130\" alt=\"Logo\" style=\"border-radius: 12px;\">"
                + "<img src=\"cid:sig-1\">"
                + "<a href=\"tel:+491234567\">0123 4567</a> <a href=\"mailto:max@example.com\">Mail</a>"
                + " <a href=\"https://example.com\" target=\"_blank\">Web</a>"
                + "<font color=\"#500010\">Max</font></td></tr></tbody></table>";

        String html = SignaturHtmlPruefung.bereinige(eingabe);

        assertThat(html).contains("cellpadding=\"0\"", "style=\"font-family: Arial;\"", "aria-label=\"Vertical Spacer\"",
                "src=\"https://example.com/logo.png\"", "src=\"cid:sig-1\"", "href=\"tel:+491234567\"",
                "href=\"mailto:max@example.com\"", "href=\"https://example.com\"", "<font color=\"#500010\">");
    }

    @Test
    @DisplayName("Platzhalter-Marker der System-Signatur bleibt erhalten")
    void platzhalterMarker() {
        assertThat(SignaturHtmlPruefung.bereinige("<div data-system-placeholder=\"1\">Bitte eintragen</div>"))
                .contains("data-system-placeholder=\"1\"");
    }

    @Test
    @DisplayName("Eingebettete Bilder: PNG/JPG/GIF/WebP erlaubt")
    void erlaubteBilder() {
        for (String typ : new String[] {"png", "jpeg", "jpg", "gif", "webp", "PNG"}) {
            String html = SignaturHtmlPruefung.bereinige("<img src=\"data:image/" + typ + ";base64," + PNG_1PX + "\">");
            assertThat(html).contains("data:image/" + typ + ";base64,");
        }
    }

    @Test
    @DisplayName("Eingebettete Bilder: SVG, HTML und kaputtes Base64 werden abgelehnt")
    void unerlaubteBilder() {
        assertThatThrownBy(() -> SignaturHtmlPruefung.bereinige("<img src=\"data:image/svg+xml;base64,PHN2Zz48L3N2Zz4=\">"))
                .hasMessageContaining("Format");
        assertThatThrownBy(() -> SignaturHtmlPruefung.bereinige("<img src=\"data:text/html;base64,PHNjcmlwdD4=\">"))
                .hasMessageContaining("Format");
        assertThatThrownBy(() -> SignaturHtmlPruefung.bereinige("<img src=\"data:image/png;base64,A\">"))
                .hasMessageContaining("beschädigt");
    }

    @Test
    @DisplayName("Eingebettetes Bild über 1 MB wird abgelehnt")
    void bildZuGross() {
        String gross = Base64.getEncoder().encodeToString(new byte[SignaturHtmlPruefung.MAX_BILD_BYTES + 1]);
        assertThatThrownBy(() -> SignaturHtmlPruefung.bereinige("<img src=\"data:image/png;base64," + gross + "\">"))
                .hasMessageContaining("zu groß");
        String passt = Base64.getEncoder().encodeToString(new byte[SignaturHtmlPruefung.MAX_BILD_BYTES]);
        assertThat(SignaturHtmlPruefung.bereinige("<img src=\"data:image/png;base64," + passt + "\">")).contains("data:image/png");
    }

    @Test
    @DisplayName("SQL-Strings sind nur Text")
    void sqlIstText() {
        assertThat(SignaturHtmlPruefung.bereinige("<p>'; DROP TABLE x; --</p>")).contains("DROP TABLE");
    }
}
