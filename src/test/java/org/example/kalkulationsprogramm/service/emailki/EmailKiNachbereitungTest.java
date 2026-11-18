package org.example.kalkulationsprogramm.service.emailki;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class EmailKiNachbereitungTest {

    private static final String K = "a1b2c3d4";
    private static final String TABELLE = "<table style=\"width: 100%\"><tbody><tr><td>Geländer – 8–10 m</td>"
            + "<td>1.234,56 €</td></tr></tbody></table>";
    private static final String TABELLE_2 = "<table><tbody><tr><td>Tor</td></tr></tbody></table>";
    private static final String BILD = "<img src=\"cid:skizze\">";

    private static VorbereiteterEntwurf entwurf(List<String> feste, Set<String> links) {
        return new VorbereiteterEntwurf("<p>x</p>", K, feste, links);
    }

    private static VorbereiteterEntwurf entwurf(String... feste) {
        return entwurf(List.of(feste), Set.of());
    }

    private static String p(int nr) {
        return "[[FEST_" + K + "_" + nr + "]]";
    }

    @Nested
    @DisplayName("Feste Elemente zurücksetzen")
    class Platzhalter {

        @Test
        @DisplayName("Tabelle kommt genau an die Stelle des Platzhalters zurück")
        void tabelleAnIhrePlatz() {
            String antwort = "<p>Guten Tag Herr Mustermann, anbei die Preise:</p><p>" + p(1) + "</p><p>Bis Montag.</p>";
            assertThat(EmailKiNachbereitung.fertigstellen(antwort, entwurf(TABELLE)))
                    .isEqualTo("<p>Guten Tag Herr Mustermann, anbei die Preise:</p>" + TABELLE + "<p>Bis Montag.</p>");
        }

        @Test
        @DisplayName("Fehlende Platzhalter: Elemente kommen in Originalreihenfolge ans Ende")
        void fehlendeAnsEnde() {
            String antwort = "<p>Guten Tag, anbei die Preise.</p>";
            assertThat(EmailKiNachbereitung.fertigstellen(antwort, entwurf(TABELLE, BILD, TABELLE_2)))
                    .isEqualTo("<p>Guten Tag, anbei die Preise.</p>" + TABELLE + "<p>" + BILD + "</p>" + TABELLE_2);
        }

        @Test
        @DisplayName("Doppelte Platzhalter werden nur einmal eingesetzt, erfundene entfernt")
        void doppeltUndErfunden() {
            String antwort = "<p>Preise:</p><p>" + p(1) + "</p><p>" + p(1) + "</p><p>Siehe " + p(9) + "oben.</p>";
            assertThat(EmailKiNachbereitung.fertigstellen(antwort, entwurf(TABELLE)))
                    .isEqualTo("<p>Preise:</p>" + TABELLE + "<p>Siehe oben.</p>");
        }

        @Test
        @DisplayName("Platzhalter-ähnlicher Text des Nutzers mit fremder Kennung bleibt Text")
        void fremderPlatzhalterBleibt() {
            String antwort = "<p>Wir nutzen [[FEST_1]] und [[FEST_ffffffff_1]] als Markierung.</p>";
            assertThat(EmailKiNachbereitung.fertigstellen(antwort, entwurf()))
                    .isEqualTo(antwort);
        }

        @Test
        @DisplayName("Bild im Satz bleibt im Satz")
        void bildImSatz() {
            String antwort = "<p>Siehe " + p(1) + " oben.</p>";
            assertThat(EmailKiNachbereitung.fertigstellen(antwort, entwurf(BILD)))
                    .isEqualTo("<p>Siehe " + BILD + " oben.</p>");
        }

        @Test
        @DisplayName("Tabelle mitten im Absatz wird hinter den Absatz gesetzt")
        void tabelleImAbsatz() {
            String antwort = "<p>Anbei die Preise " + p(1) + " wie besprochen.</p>";
            assertThat(EmailKiNachbereitung.fertigstellen(antwort, entwurf(TABELLE)))
                    .isEqualTo("<p>Anbei die Preise wie besprochen.</p>" + TABELLE);
        }

        @Test
        @DisplayName("Kleine Abweichungen der KI im Platzhalter (Leerzeichen) werden erkannt")
        void leerzeichenImPlatzhalter() {
            String antwort = "<p>Preise:</p><p>[[ FEST_" + K + "_1 ]]</p>";
            assertThat(EmailKiNachbereitung.fertigstellen(antwort, entwurf(TABELLE)))
                    .isEqualTo("<p>Preise:</p>" + TABELLE);
        }
    }

    @Nested
    @DisplayName("Text der KI-Antwort")
    class Text {

        @Test
        @DisplayName("Formatierung bleibt erhalten")
        void formatierungBleibt() {
            String antwort = "<p>Guten Tag, hier <strong>fett</strong>, <em>kursiv</em>, <u>unterstrichen</u> "
                    + "und <span style=\"color: #e11d48; font-size: 14pt\">farbig</span>.</p>"
                    + "<ul><li><b>Geländer</b> aus Stahl</li><li>Tor</li></ul>";
            assertThat(EmailKiNachbereitung.fertigstellen(antwort, entwurf())).isEqualTo(antwort);
        }

        @Test
        @DisplayName("Gedankenstriche im Text werden ersetzt, in der wieder eingesetzten Tabelle nicht")
        void gedankenstriche() {
            String antwort = "<p><strong>Preise</strong> – wie besprochen:</p><p>" + p(1) + "</p>"
                    + "<p>Wir kommen Mo–Fr von 8–10 Uhr — oder nach Absprache.</p>";
            assertThat(EmailKiNachbereitung.fertigstellen(antwort, entwurf(TABELLE)))
                    .isEqualTo("<p><strong>Preise</strong>, wie besprochen:</p>" + TABELLE
                            + "<p>Wir kommen Mo–Fr von 8–10 Uhr, oder nach Absprache.</p>");
        }

        @Test
        @DisplayName("Strich direkt vor einem Tag (Grenze zwischen Textknoten) wird erkannt")
        void strichVorTag() {
            assertThat(EmailKiNachbereitung.fertigstellen("<p>Preise – <em>wie besprochen</em></p>", entwurf()))
                    .isEqualTo("<p>Preise, <em>wie besprochen</em></p>");
        }

        @Test
        @DisplayName("Leerzeichen vor dem Strich im fett gesetzten Wort wird mitgenommen")
        void leerzeichenImVorgaenger() {
            assertThat(EmailKiNachbereitung.fertigstellen("<p><strong>Preise </strong>– wie besprochen</p>", entwurf()))
                    .isEqualTo("<p><strong>Preise</strong>, wie besprochen</p>");
            assertThat(EmailKiNachbereitung.fertigstellen("<p>Preise –<em> wie besprochen</em></p>", entwurf()))
                    .isEqualTo("<p>Preise, <em>wie besprochen</em></p>");
        }

        @Test
        @DisplayName("Eine Einleitungszeile, die schon im Entwurf des Nutzers stand, bleibt")
        void eigeneEinleitungBleibt() {
            VorbereiteterEntwurf eigener = new VorbereiteterEntwurf("<p>Hier ist das Angebot:</p><p>passt so</p>",
                    K, List.of(), Set.of());
            assertThat(EmailKiNachbereitung.fertigstellen("<p>Hier ist das Angebot:</p><p>Passt so.</p>", eigener))
                    .isEqualTo("<p>Hier ist das Angebot:</p><p>Passt so.</p>");
        }

        @Test
        @DisplayName("Aufzählungsstrich am Anfang eines Listenpunkts oder nach einem Umbruch bleibt")
        void aufzaehlungsstrich() {
            String antwort = "<p>Bitte mitbringen:<br>- Schlüssel<br>- Plan</p><ul><li>- Tor</li></ul>";
            assertThat(EmailKiNachbereitung.fertigstellen(antwort, entwurf())).isEqualTo(antwort);
        }

        @Test
        @DisplayName("Links bleiben nur, wenn sie genau so in der Eingabe vorkamen")
        void links() {
            String antwort = "<p>Hier das <a href=\"https://example.com/angebot\">Angebot</a> und "
                    + "<a href=\"https://boese.example/zahlung\">die neue Bankverbindung</a>.</p>";
            assertThat(EmailKiNachbereitung.fertigstellen(antwort,
                    entwurf(List.of(), Set.of("https://example.com/angebot"))))
                    .isEqualTo("<p>Hier das <a href=\"https://example.com/angebot\">Angebot</a> und "
                            + "die neue Bankverbindung.</p>");
        }

        @Test
        @DisplayName("Schadcode in der KI-Antwort wird entfernt")
        void xss() {
            String ergebnis = EmailKiNachbereitung.fertigstellen(
                    "<p>Hallo<script>alert(1)</script> <img src=x onerror=alert(2)>"
                            + "<a href=\"javascript:alert(3)\">Max</a></p><table><tr><td>neu</td></tr></table>",
                    entwurf(List.of(), Set.of("javascript:alert(3)")));
            assertThat(ergebnis).isEqualTo("<p>Hallo Max</p>neu");
        }

        @Test
        @DisplayName("Codeblock, Einleitung und Grußformel der KI fallen weg")
        void rahmenFaelltWeg() {
            String antwort = "```html\n<p>Hier ist der überarbeitete Text:</p><p>Guten Tag Herr Mustermann,</p>"
                    + "<p>wir kommen am Montag.</p><p>Viele Grüße<br>Max Mustermann</p>\n```";
            assertThat(EmailKiNachbereitung.fertigstellen(antwort, entwurf()))
                    .isEqualTo("<p>Guten Tag Herr Mustermann,</p><p>wir kommen am Montag.</p>");
        }

        @Test
        @DisplayName("Grußformel in eigenen Absätzen und mitten im letzten Absatz fällt weg")
        void grussformel() {
            assertThat(EmailKiNachbereitung.fertigstellen(
                    "<p>Wir kommen Montag.</p><p>Mit freundlichen Grüßen</p><p>Max Mustermann</p>", entwurf()))
                    .isEqualTo("<p>Wir kommen Montag.</p>");
            assertThat(EmailKiNachbereitung.fertigstellen(
                    "<p>Wir kommen Montag.<br>LG<br>Max</p>", entwurf()))
                    .isEqualTo("<p>Wir kommen Montag.</p>");
        }

        @Test
        @DisplayName("Klartext-Antwort wird zu Absätzen")
        void klartextAntwort() {
            assertThat(EmailKiNachbereitung.fertigstellen("Guten Tag,\n\nwir kommen Montag - versprochen.\n" + p(1), entwurf(TABELLE)))
                    .isEqualTo("<p>Guten Tag,</p><p>wir kommen Montag, versprochen.</p>" + TABELLE);
        }

        @Test
        @DisplayName("Leere Antwort oder nur Platzhalter ergibt leer")
        void leer() {
            assertThat(EmailKiNachbereitung.fertigstellen(null, entwurf())).isEmpty();
            assertThat(EmailKiNachbereitung.fertigstellen("  ", entwurf(TABELLE))).isEmpty();
            assertThat(EmailKiNachbereitung.fertigstellen("<p>" + p(1) + "</p>", entwurf(TABELLE))).isEmpty();
        }
    }

    @Test
    @DisplayName("Ohne KI: bereinigter Entwurf mit seinen festen Elementen")
    void ohneKi() {
        VorbereiteterEntwurf entwurf = new VorbereiteterEntwurf(
                "<p>Preise – siehe:</p><p>" + p(1) + "</p>", K, List.of(TABELLE), Set.of());
        assertThat(EmailKiNachbereitung.ohneKi(entwurf)).isEqualTo("<p>Preise – siehe:</p>" + TABELLE);
    }

    @Test
    @DisplayName("Formate der Mail-Leiste überstehen die Rundreise durch die KI")
    void leistenformateRundreise() {
        String antwort = "<h3>Angebot Geländer</h3><p style=\"text-align: center\">Guten Tag Herr Mustermann,</p>"
                + "<blockquote style=\"margin: 0 0 0 40px; padding: 0px\">wie besprochen.</blockquote>"
                + "<p><strike>alter Preis</strike> neuer Preis</p>";
        assertThat(EmailKiNachbereitung.fertigstellen(antwort, entwurf())).isEqualTo(antwort);
    }

    @Test
    @DisplayName("Viele Leerzeichen an Textknoten-Grenzen laufen in linearer Zeit (ReDoS)")
    void keinReDoSAnKnotengrenzen() {
        String leer = " ".repeat(100_000);
        String antwort1 = "<p><b>a" + leer + "b</b>– wie besprochen</p>";
        String antwort2 = "<p>Preise –<em>" + leer + "x</em></p>";
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> EmailKiNachbereitung.fertigstellen(antwort1, entwurf()));
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> EmailKiNachbereitung.fertigstellen(antwort2, entwurf()));
    }
}
