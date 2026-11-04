package org.example.kalkulationsprogramm.util.suche;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmailSuchtextTest {

    private static EmailSuchtext.Quellen quellen(String body, String html, List<String> dateinamen) {
        return new EmailSuchtext.Quellen("Max Mustermann <max@example.com>", "antwort@example.com",
                "Erika Musterfrau <erika@example.com>", "Büro <buero@example.com>", "Angebot Geländer", body, html,
                dateinamen);
    }

    @Test
    @DisplayName("Enthält Absender samt Anzeigename, Antwortadresse, Empfänger, CC, Betreff, Text und Dateinamen")
    void alleFelder() {
        String suchtext = EmailSuchtext.aus(quellen("Hallo Herr Müller,\n> zitierter Verlauf", null,
                List.of("Aufmaß_Treppe.pdf")));

        assertThat(suchtext)
                .contains("max mustermann <max@example.com>")
                .contains("antwort@example.com")
                .contains("erika musterfrau <erika@example.com>")
                .contains("buro <buro@example.com>")
                .contains("angebot gelander")
                .contains("hallo herr muller, > zitierter verlauf")
                .contains("aufmass_treppe.pdf");
    }

    @Test
    @DisplayName("Mail nur mit HTML: der Text kommt ohne Tags und mit entpackten Entitäten hinein")
    void nurHtml() {
        String suchtext = EmailSuchtext.aus(quellen(null,
                "<p>Gru&szlig;</p><div>Termin <b>Montag</b> &amp; Dienstag</div><blockquote>Alter Verlauf</blockquote>",
                List.of()));

        assertThat(suchtext).contains("montag").contains("& dienstag").contains("alter verlauf")
                .doesNotContain("<b>").doesNotContain("<p>");
    }

    @Test
    @DisplayName("HTML: Umlaut-Entitäten werden entpackt, Stil- und Skriptblöcke zählen nicht, <header> schon")
    void htmlEntitaetenUndBloecke() {
        String suchtext = EmailSuchtext.aus(quellen(null,
                "<html><HEAD><title>Kopf</title></HEAD><style type=\"text/css\">.font-family{color:red}</style>"
                        + "<header>Firma Würth</header><p>M&uuml;ller Stra&szlig;e</p><script>alert(1)</script></html>",
                List.of()));

        assertThat(suchtext).contains("muller strasse").contains("firma wurth")
                .doesNotContain("font-family").doesNotContain("alert").doesNotContain("kopf");
    }

    @Test
    @DisplayName("Kaputtes HTML ohne schließendes Tag bleibt lesbar")
    void kaputtesHtml() {
        assertThat(EmailSuchtext.aus(quellen(null, "<style>nie geschlossen <p>Text", List.of())))
                .contains("text");
    }

    @Test
    @DisplayName("Gleicher Text als Klartext und HTML steht nur einmal drin")
    void htmlGleichKlartext() {
        String suchtext = EmailSuchtext.aus(quellen("Termin Montag", "<p>Termin Montag</p>", List.of()));

        assertThat(suchtext.indexOf("termin montag")).isEqualTo(suchtext.lastIndexOf("termin montag"));
    }

    @Test
    @DisplayName("Leere Felder sind kein Problem")
    void leer() {
        assertThat(EmailSuchtext.aus(new EmailSuchtext.Quellen(null, null, null, null, null, null, null, null)))
                .isEmpty();
    }

    @Test
    @DisplayName("Überlange Inhalte werden auf die Höchstlänge gekürzt, Dateinamen bleiben erhalten")
    void hoechstlaenge() {
        String riesig = "geländer ".repeat(40_000);
        String suchtext = EmailSuchtext.aus(quellen(riesig, null, List.of("Rechnung_4711.pdf")));

        assertThat(suchtext).hasSizeLessThanOrEqualTo(EmailSuchtext.MAX_LAENGE);
        assertThat(suchtext).contains("rechnung_4711.pdf");
    }

    @Test
    @DisplayName("Dateiname nachtragen: nur, wenn er noch fehlt und Platz ist")
    void dateinameNachtragen() {
        assertThat(EmailSuchtext.mitDateiname("angebot", "Skizze.PDF")).isEqualTo("angebot skizze.pdf");
        assertThat(EmailSuchtext.mitDateiname("angebot skizze.pdf", "Skizze.pdf")).isEqualTo("angebot skizze.pdf");
        assertThat(EmailSuchtext.mitDateiname("angebot", null)).isEqualTo("angebot");
        assertThat(EmailSuchtext.mitDateiname("", "Plan.pdf")).isEqualTo("plan.pdf");
        String voll = "x".repeat(EmailSuchtext.MAX_LAENGE);
        assertThat(EmailSuchtext.mitDateiname(voll, "Plan.pdf")).isSameAs(voll);
    }
}
