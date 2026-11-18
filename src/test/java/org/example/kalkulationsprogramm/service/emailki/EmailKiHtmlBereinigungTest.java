package org.example.kalkulationsprogramm.service.emailki;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailKiHtmlBereinigungTest {

    @Nested
    @DisplayName("Text der Mail (KI-Antwort und Entwurf ohne feste Elemente)")
    class Text {

        @Test
        @DisplayName("Erlaubte Formatierung bleibt erhalten")
        void formatierungBleibt() {
            String html = "<p>Hallo <strong>Max</strong>, <b>fett</b> <em>kursiv</em> <i>schräg</i> "
                    + "<u>unterstrichen</u> <s>durch</s><br>neu</p><div>Block</div>"
                    + "<ul><li>eins</li></ul><ol><li>zwei</li></ol>";
            assertThat(EmailKiHtmlBereinigung.bereinigeText(html)).isEqualTo(html);
        }

        @Test
        @DisplayName("Links mit http, https und mailto bleiben, andere verlieren den Link und behalten den Text")
        void links() {
            assertThat(EmailKiHtmlBereinigung.bereinigeText(
                    "<p><a href=\"https://example.com/x\">Seite</a> <a href=\"mailto:max@example.com\">Mail</a> "
                            + "<a href=\"javascript:alert(1)\">böse</a> <a href=\"ftp://x\">ftp</a> <a>leer</a></p>"))
                    .isEqualTo("<p><a href=\"https://example.com/x\">Seite</a> <a href=\"mailto:max@example.com\">Mail</a> "
                            + "böse ftp leer</p>");
        }

        @Test
        @DisplayName("Skripte, Ereignis-Attribute, Bilder und Tabellen fliegen raus, Text bleibt")
        void xssFliegtRaus() {
            String bereinigt = EmailKiHtmlBereinigung.bereinigeText(
                    "<p onclick=\"alert(1)\">Hallo<script>alert('xss')</script> <img src=x onerror=alert(1)>Max</p>"
                            + "<table><tr><td>Zelle</td></tr></table><h5>Titel</h5>");
            assertThat(bereinigt)
                    .doesNotContain("script").doesNotContain("alert").doesNotContain("img")
                    .doesNotContain("onclick").doesNotContain("table").doesNotContain("h5")
                    .contains("Hallo").contains("Max").contains("Zelle").contains("Titel");
        }

        @Test
        @DisplayName("span und Absatz behalten nur erlaubte Stile")
        void stile() {
            assertThat(EmailKiHtmlBereinigung.bereinigeText(
                    "<p style=\"color: red\"><span style=\"color: red; position: fixed; FONT-SIZE: 14pt; "
                            + "background-color: #ffee00; background-image: url(x); font-weight: bold; "
                            + "font-style: italic; text-decoration: underline line-through\">Text</span></p>"))
                    .isEqualTo("<p style=\"color: red\"><span style=\"color: red; font-size: 14pt; background-color: #ffee00; "
                            + "font-weight: bold; font-style: italic; text-decoration: underline line-through\">Text</span></p>");
        }

        @Test
        @DisplayName("Gefährliche Stilwerte werden verworfen, span ohne Stil wird aufgelöst")
        void gefaehrlicheStile() {
            assertThat(EmailKiHtmlBereinigung.bereinigeText(
                    "<p><span style=\"color: expression(alert(1))\">A</span>"
                            + "<span style=\"color: rgb(1, 2, 3)\">B</span>"
                            + "<span style=\"color: red\\9\">C</span><span>D</span></p>"))
                    .isEqualTo("<p>A<span style=\"color: rgb(1, 2, 3)\">B</span>CD</p>");
        }

        @Test
        @DisplayName("Formate der Mail-Leiste bleiben: Überschrift, Ausrichtung, Einzug, Durchgestrichen")
        void leistenformate() {
            String html = "<h3>Angebot Geländer</h3>"
                    + "<p style=\"text-align: center\">Mitte</p>"
                    + "<div style=\"text-align: right\">Rechts</div>"
                    + "<div align=\"center\">Alt zentriert</div>"
                    + "<blockquote style=\"margin: 0 0 0 40px; padding: 0px\">Eingerückt</blockquote>"
                    + "<p><strike>alt</strike> <s>auch alt</s></p>"
                    + "<h1>Groß</h1><h2>Mittel</h2><h4>Klein</h4>"
                    + "<p style=\"margin-left: 40px; padding-left: 10px; color: red\">Einzug</p>";
            assertThat(EmailKiHtmlBereinigung.bereinigeText(html)).isEqualTo(html);
        }

        @Test
        @DisplayName("An Blöcken gelten dieselben Stilprüfungen; unbekannte Ausrichtung und gefährliche Werte fallen weg")
        void blockStile() {
            assertThat(EmailKiHtmlBereinigung.bereinigeText(
                    "<p style=\"text-align: center; position: absolute; background: url(x)\">A</p>"
                            + "<div align=\"javascript:alert(1)\">B</div>"
                            + "<blockquote style=\"margin-left: expression(alert(1))\">C</blockquote>"
                            + "<h5>D</h5>"))
                    .isEqualTo("<p style=\"text-align: center\">A</p><div>B</div><blockquote>C</blockquote>D");
        }

        @Test
        @DisplayName("null und leer ergeben leer")
        void leer() {
            assertThat(EmailKiHtmlBereinigung.bereinigeText(null)).isEmpty();
            assertThat(EmailKiHtmlBereinigung.bereinigeText("  ")).isEmpty();
        }
    }

    @Nested
    @DisplayName("Feste Elemente (Tabellen, Bilder)")
    class FesteElemente {

        @Test
        @DisplayName("Tabelle bleibt mit Inline-Stilen und Tabellen-Attributen unverändert")
        void tabelleBleibt() {
            String tabelle = "<table style=\"border-collapse: collapse; width: 100%\" border=\"1\" cellpadding=\"4\">"
                    + "<tbody><tr><th style=\"background-color: #eee\">Position</th><th>Menge</th><th>Preis</th></tr>"
                    + "<tr><td colspan=\"2\" align=\"right\">Geländer – 8–10 m</td><td><strong>1.234,56 €</strong></td></tr>"
                    + "</tbody></table>";
            assertThat(EmailKiHtmlBereinigung.bereinigeFestesElement(tabelle)).isEqualTo(tabelle);
        }

        @Test
        @DisplayName("Skripte und Ereignis-Attribute in Tabellen werden entfernt")
        void xssInTabelle() {
            String bereinigt = EmailKiHtmlBereinigung.bereinigeFestesElement(
                    "<table><tr><td onclick=\"alert(1)\">A<script>alert(2)</script>"
                            + "<a href=\"javascript:alert(3)\">B</a></td></tr></table>");
            assertThat(bereinigt).doesNotContain("alert").doesNotContain("onclick").doesNotContain("javascript")
                    .contains(">A").contains("B");
        }

        @Test
        @DisplayName("Stil mit url() oder expression wird ganz verworfen")
        void gefaehrlicherStilInTabelle() {
            assertThat(EmailKiHtmlBereinigung.bereinigeFestesElement(
                    "<table><tbody><tr><td style=\"background: url(javascript:alert(1))\">A</td>"
                            + "<td style=\"width: expression(alert(1))\">B</td></tr></tbody></table>"))
                    .isEqualTo("<table><tbody><tr><td>A</td><td>B</td></tr></tbody></table>");
        }

        @Test
        @DisplayName("Bilder: https, cid und data bleiben, javascript-Quellen und onerror fliegen raus")
        void bilder() {
            assertThat(EmailKiHtmlBereinigung.bereinigeFestesElement("<img src=\"https://example.com/a.png\" alt=\"Skizze\" width=\"200\">"))
                    .isEqualTo("<img src=\"https://example.com/a.png\" alt=\"Skizze\" width=\"200\">");
            assertThat(EmailKiHtmlBereinigung.bereinigeFestesElement("<img src=\"cid:bild1\">"))
                    .isEqualTo("<img src=\"cid:bild1\">");
            assertThat(EmailKiHtmlBereinigung.bereinigeFestesElement("<img src=\"data:image/png;base64,iVBOR\">"))
                    .isEqualTo("<img src=\"data:image/png;base64,iVBOR\">");
            assertThat(EmailKiHtmlBereinigung.bereinigeFestesElement("<img src=x onerror=alert(1)>"))
                    .isEqualTo("<img>");
            assertThat(EmailKiHtmlBereinigung.bereinigeFestesElement("<img src=\"javascript:alert(1)\">"))
                    .isEqualTo("<img>");
        }
    }

    @Nested
    @DisplayName("Klartext")
    class Klartext {

        @Test
        @DisplayName("Klartext ohne Tags wird erkannt, HTML nicht")
        void erkennung() {
            assertThat(EmailKiHtmlBereinigung.istKlartext("Hallo Max, 3 < 5 & gut")).isTrue();
            assertThat(EmailKiHtmlBereinigung.istKlartext("<p>Hallo</p>")).isFalse();
            assertThat(EmailKiHtmlBereinigung.istKlartext("Hallo <b>Max</b>")).isFalse();
        }

        @Test
        @DisplayName("Leerzeilen trennen Absätze, einfache Umbrüche werden br, Sonderzeichen werden maskiert")
        void textAlsHtml() {
            assertThat(EmailKiHtmlBereinigung.textAlsHtml("Hallo Max,\r\nwie besprochen.\n \n\n3 < 5 & <script>"))
                    .isEqualTo("<p>Hallo Max,<br>wie besprochen.</p><p>3 &lt; 5 &amp; &lt;script&gt;</p>");
            assertThat(EmailKiHtmlBereinigung.textAlsHtml("")).isEmpty();
        }
    }
}
