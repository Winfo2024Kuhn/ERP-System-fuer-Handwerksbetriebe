package org.example.kalkulationsprogramm.service.emailki;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class EmailKiVorbereitungTest {

    private static final long SAAT = 42L;

    private EmailKiVorbereitung vorbereitung;
    private String ersteKennung;

    @BeforeEach
    void setUp() {
        vorbereitung = new EmailKiVorbereitung(new Random(SAAT));
        ersteKennung = String.format("%08x", new Random(SAAT).nextInt());
    }

    @Test
    @DisplayName("Tabelle wird durch einen eigenen Absatz mit Platzhalter ersetzt und unverändert aufbewahrt")
    void tabelleWirdPlatzhalter() {
        String tabelle = "<table style=\"width: 100%\" border=\"1\"><tbody><tr><td>Geländer</td><td>2</td>"
                + "<td>1.234,56 €</td></tr></tbody></table>";
        VorbereiteterEntwurf entwurf = vorbereitung.vorbereiten(
                "<p>Hallo Herr Mustermann, anbei die Preise:</p>" + tabelle + "<p>Bis bald</p>");

        assertThat(entwurf.kennung()).isEqualTo(ersteKennung);
        assertThat(entwurf.htmlFuerKi()).isEqualTo("<p>Hallo Herr Mustermann, anbei die Preise:</p>"
                + "<p>[[FEST_" + ersteKennung + "_1]]</p><p>Bis bald</p>");
        assertThat(entwurf.festeElemente()).containsExactly(tabelle);
        assertThat(entwurf.hatText()).isTrue();
    }

    @Test
    @DisplayName("Bild im Satz wird zum Platzhalter an derselben Stelle, Bild in Tabelle bleibt Teil der Tabelle")
    void bilder() {
        VorbereiteterEntwurf entwurf = vorbereitung.vorbereiten(
                "<p>Siehe <img src=\"cid:skizze\"> oben</p>"
                        + "<table><tbody><tr><td><img src=\"https://example.com/a.png\"></td></tr></tbody></table>");

        assertThat(entwurf.htmlFuerKi()).isEqualTo("<p>Siehe " + entwurf.platzhalter(1) + " oben</p>"
                + "<p>" + entwurf.platzhalter(2) + "</p>");
        assertThat(entwurf.festeElemente()).containsExactly(
                "<img src=\"cid:skizze\">",
                "<table><tbody><tr><td><img src=\"https://example.com/a.png\"></td></tr></tbody></table>");
    }

    @Test
    @DisplayName("Formatierung bleibt, Gefährliches fällt weg (auch in Tabellen)")
    void bereinigt() {
        VorbereiteterEntwurf entwurf = vorbereitung.vorbereiten(
                "<p>Hallo <strong>Max</strong><script>alert(1)</script></p>"
                        + "<ul><li><span style=\"color: red\">wichtig</span></li></ul>"
                        + "<table><tbody><tr><td onclick=\"alert(2)\">A</td></tr></tbody></table>"
                        + "<img src=x onerror=alert(3)>");

        assertThat(entwurf.htmlFuerKi())
                .startsWith("<p>Hallo <strong>Max</strong></p><ul><li><span style=\"color: red\">wichtig</span></li></ul>")
                .doesNotContain("alert").doesNotContain("script");
        assertThat(entwurf.festeElemente()).containsExactly("<table><tbody><tr><td>A</td></tr></tbody></table>");
        assertThat(String.join("", entwurf.festeElemente())).doesNotContain("alert");
    }

    @Test
    @DisplayName("Klartext ohne Tags wird zu Absätzen")
    void klartext() {
        VorbereiteterEntwurf entwurf = vorbereitung.vorbereiten("Hallo Max,\nwie besprochen.\n\nBis Montag & 3 < 5");
        assertThat(entwurf.htmlFuerKi()).isEqualTo("<p>Hallo Max,<br>wie besprochen.</p><p>Bis Montag &amp; 3 &lt; 5</p>");
        assertThat(entwurf.festeElemente()).isEmpty();
    }

    @Test
    @DisplayName("Schreibt der Nutzer selbst einen Platzhalter mit derselben Kennung, wird eine andere gewählt")
    void kennungKollidiertNicht() {
        VorbereiteterEntwurf entwurf = vorbereitung.vorbereiten("<p>[[FEST_" + ersteKennung + "_1]]</p>");
        assertThat(entwurf.kennung()).isNotEqualTo(ersteKennung).matches("[0-9a-f]{8}");
        assertThat(entwurf.htmlFuerKi()).isEqualTo("<p>[[FEST_" + ersteKennung + "_1]]</p>");
    }

    @Test
    @DisplayName("Erlaubte Links stammen aus Text und Tabellen der Eingabe")
    void erlaubteLinks() {
        VorbereiteterEntwurf entwurf = vorbereitung.vorbereiten(
                "<p><a href=\"https://example.com/angebot\">Angebot</a> <a href=\"javascript:alert(1)\">x</a></p>"
                        + "<table><tbody><tr><td><a href=\"mailto:max@example.com\">Mail</a></td></tr></tbody></table>");
        assertThat(entwurf.erlaubteLinks()).containsExactlyInAnyOrder("https://example.com/angebot", "mailto:max@example.com");
    }

    @Test
    @DisplayName("Nur eine Tabelle ohne Text: nichts für die KI zu tun")
    void nurTabelle() {
        VorbereiteterEntwurf entwurf = vorbereitung.vorbereiten("<table><tbody><tr><td>1</td></tr></tbody></table><p><br></p>");
        assertThat(entwurf.hatText()).isFalse();
    }
}
