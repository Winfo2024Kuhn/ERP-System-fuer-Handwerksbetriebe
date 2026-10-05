package org.example.kalkulationsprogramm.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmailAiPostProcessorLueckenTest {

    @Test
    @DisplayName("null -> null, leer/Leerraum -> leer")
    void leer() {
        assertNull(EmailAiPostProcessor.sanitizePlainText(null));
        assertEquals("", EmailAiPostProcessor.sanitizePlainText(""));
        assertEquals("", EmailAiPostProcessor.sanitizePlainText("  \r\n  "));
    }

    @Test
    @DisplayName("CRLF/CR werden zu LF, mehr als zwei Leerzeilen werden auf eine reduziert")
    void zeilenumbrueche() {
        assertEquals("A\n\nB\nC",
                EmailAiPostProcessor.sanitizePlainText("A\r\n\r\n\r\n\r\nB\rC"));
    }

    @Test
    @DisplayName("Codeblock mit Sprachangabe wird entfernt, Inhalt bleibt")
    void codeFenceMitSprache() {
        assertEquals("Hallo Max", EmailAiPostProcessor.sanitizePlainText("```text\nHallo Max\n```"));
        assertEquals("Hallo Max", EmailAiPostProcessor.sanitizePlainText("```\nHallo Max\n```\n\n"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Hier ist der überarbeitete Text:",
            "Hier ist die E-Mail:",
            "Verbesserte Version:",
            "Überarbeitete Version:",
            "Optimierte E-Mail:",
            "Antwort:",
            "Gerne, hier ist der Text:",
            "Natürlich! Hier die Version:",
            "Selbstverständlich: "
    })
    @DisplayName("Einleitungszeilen werden entfernt")
    void einleitung(String intro) {
        assertEquals("Sehr geehrter Herr Mustermann,\n\nvielen Dank.",
                EmailAiPostProcessor.sanitizePlainText(intro + "\n\nSehr geehrter Herr Mustermann,\n\nvielen Dank."));
    }

    @Test
    @DisplayName("Normale Zeile mit Doppelpunkt ohne Schluesselwort bleibt erhalten")
    void doppelpunktOhneSchluessel() {
        String text = "Termine:\n- Montag\n- Dienstag";
        assertEquals(text, EmailAiPostProcessor.sanitizePlainText(text));
    }

    @Test
    @DisplayName("Mehrere Markdown-Ueberschriften und Leerzeilen am Anfang werden entfernt, Aufzaehlungen bleiben")
    void ueberschriften() {
        assertEquals("- Punkt 1\n- Punkt 2",
                EmailAiPostProcessor.sanitizePlainText("# Titel\n\n## Untertitel\n- Punkt 1\n- Punkt 2"));
    }

    @Test
    @DisplayName("Umschliessende Anfuehrungszeichen werden entfernt, ungepaarte nicht")
    void anfuehrungszeichen() {
        assertEquals("Hallo", EmailAiPostProcessor.sanitizePlainText("\"Hallo\""));
        assertEquals("Hallo", EmailAiPostProcessor.sanitizePlainText("'Hallo'"));
        assertEquals("\"Hallo", EmailAiPostProcessor.sanitizePlainText("\"Hallo"));
        assertEquals("\"", EmailAiPostProcessor.unwrapSymmetricQuotes("\""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Mit freundlichen Grüßen", "Viele Grüße", "Freundliche Grüße", "Beste Grüße",
            "Herzliche Grüße", "Gruss", "Gruesse", "VG", "LG", "MfG", "Best regards", "Kind regards"})
    @DisplayName("Schlussformeln samt Signatur (max. 4 Zeilen) werden entfernt")
    void schlussformel(String closing) {
        assertEquals("Vielen Dank fuer Ihre Anfrage.",
                EmailAiPostProcessor.sanitizePlainText("Vielen Dank fuer Ihre Anfrage.\n\n" + closing + "\nMax Mustermann"));
    }

    @Test
    @DisplayName("Grussformel mitten im Text (mehr als 4 Zeilen vor Ende) bleibt erhalten")
    void schlussformelWeitVorEnde() {
        String text = "Viele Grüße an alle\nzeile2\nzeile3\nzeile4\nzeile5\nzeile6";
        assertEquals(text, EmailAiPostProcessor.sanitizePlainText(text));
    }

    @Test
    @DisplayName("Text, der nur aus Grussformel besteht, wird leer")
    void nurGruss() {
        assertEquals("", EmailAiPostProcessor.sanitizePlainText("Viele Grüße"));
        assertEquals("", EmailAiPostProcessor.stripTrailingClosings("\n\n"));
        assertNull(EmailAiPostProcessor.stripTrailingClosings(null));
    }

    @Test
    @DisplayName("Kombination aus Fence, Ueberschrift, Einleitung, Anfuehrungszeichen und Gruss")
    void kombination() {
        String in = "```\n# Entwurf\nHier ist die überarbeitete Version:\n\n\"Guten Tag,\n\nwir kommen Montag.\"\n```";
        assertEquals("Guten Tag,\n\nwir kommen Montag.", EmailAiPostProcessor.sanitizePlainText(in));
    }

    @Test
    @DisplayName("HTML/Script im KI-Text wird nicht interpretiert, sondern unveraendert durchgereicht")
    void htmlBleibtText() {
        assertEquals("<script>alert(1)</script>",
                EmailAiPostProcessor.sanitizePlainText("<script>alert(1)</script>"));
    }

    @Test
    @DisplayName("Sehr langer Text mit vielen Zeilen laeuft ohne Backtracking-Probleme durch")
    void langerText() {
        String text = ("x".repeat(100) + "\n").repeat(20_000) + "\n\n\n\n";
        long start = System.nanoTime();
        String out = EmailAiPostProcessor.sanitizePlainText(text);
        assertTrue((System.nanoTime() - start) / 1_000_000 < 5_000);
        assertTrue(out.startsWith("xxxx"));
    }
}
