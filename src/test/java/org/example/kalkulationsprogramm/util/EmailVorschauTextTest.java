package org.example.kalkulationsprogramm.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailVorschauTextTest {

    @Test
    void entferntOutlookKopfMitAllenVierZeilen() {
        String text = "Hallo Herr Mustermann,\n\nPasst so.\n\nGruß Erika\n\nVon: Max Mustermann <max@example.com>\n"
                + "Gesendet: Dienstag, 23. Juni 2026 17:23\nAn: info@example.com\nBetreff: Angebot\n\nAlter Text";
        assertThat(EmailVorschauText.ohneZitiertenVerlauf(text))
                .isEqualTo("Hallo Herr Mustermann,\n\nPasst so.\n\nGruß Erika");
    }

    @Test
    void entferntVerlaufNachOutlookUnterstrichLinieAuchMitDreiKopfZeilen() {
        String text = "Danke!\n________________________________\nVon: max@example.com\nGesendet: Montag\nBetreff: Termin\nAlt";
        assertThat(EmailVorschauText.vorschau(text, 200)).isEqualTo("Danke!");
    }

    @Test
    void dreiKopfZeilenOhneTrennerBleibenStehen() {
        String text = "Neu\nVon: max@example.com\nDatum: heute\nBetreff: Frage\nWeiter";
        assertThat(EmailVorschauText.vorschau(text, 200)).isEqualTo("Neu Von: max@example.com Datum: heute Betreff: Frage Weiter");
    }

    @Test
    void entferntVerlaufAbUrspruenglicheNachrichtUndTelekomOriginalNachricht() {
        assertThat(EmailVorschauText.vorschau("Ok\n-------- Ursprüngliche Nachricht --------\nVon: a@b.de\nAlt", 200)).isEqualTo("Ok");
        assertThat(EmailVorschauText.vorschau("Ok\r\n-----Original-Nachricht-----\r\nBetreff: x\r\nAlt", 200)).isEqualTo("Ok");
        assertThat(EmailVorschauText.vorschau("Ok\r-----Original Message-----\rAlt", 200)).isEqualTo("Ok");
    }

    @Test
    void entferntZitierteZeilenSamtZuschreibungUndBehaeltAntwortenDazwischen() {
        String text = "Neu\nAm Do., 17. Sept. 2026 um 10:00 Uhr schrieb Max Mustermann <max@example.com>:\n\n> Alt\n> Alt 2\nAntwort";
        assertThat(EmailVorschauText.vorschau(text, 200)).isEqualTo("Neu Antwort");
        assertThat(EmailVorschauText.vorschau("Neu\nOn Sep 8, 2026, max@example.com wrote:\n> Old", 200)).isEqualTo("Neu");
    }

    @Test
    void zuschreibungAmEndeOhneZitatWirdEntfernt() {
        // Abgeschnittene Klartext-Bodys enden oft direkt nach der Zuschreibung.
        assertThat(EmailVorschauText.vorschau("MfG Max\nAm 17.09.2026 schrieb info@example.com:", 200)).isEqualTo("MfG Max");
    }

    @Test
    void zuschreibungOhneFolgendesZitatBleibtStehen() {
        String text = "Neu\nAm 8. September 2026 schrieb max@example.com:\nEine neue Erklärung ohne Zitatmarkierung";
        assertThat(EmailVorschauText.vorschau(text, 200)).contains("Eine neue Erklärung");
    }

    @Test
    void weiterleitungBleibtVollstaendig() {
        String text = "Zur Info\n---------- Weitergeleitete Nachricht ----------\nVon: a@b.de\nDatum: x\nAn: c@d.de\nBetreff: y\nInhalt";
        assertThat(EmailVorschauText.ohneZitiertenVerlauf(text)).isEqualTo(text);
    }

    @Test
    void kopfOhneNeuenTextDavorWirdNichtAbgeschnitten() {
        String formular = "Von: Max Mustermann\nDatum: 05.10.2026\nAn: info@example.com\nBetreff: Anfrage\nNachricht: Geländer";
        assertThat(EmailVorschauText.vorschau(formular, 500)).contains("Nachricht: Geländer");
    }

    @Test
    void umbrocheneEmpfaengerlisteGehoertZumKopf() {
        String text = "Ok\nVon: a@example.com\nGesendet: heute\nAn: b@example.com;\n c@example.com;\n d@example.com\nBetreff: x\nAlt";
        assertThat(EmailVorschauText.vorschau(text, 200)).isEqualTo("Ok");
    }

    @Test
    void kuerztUndFasstLeerraumZusammen() {
        assertThat(EmailVorschauText.vorschau("a   b\n\nc", 3)).isEqualTo("a b");
        assertThat(EmailVorschauText.vorschau(null, 10)).isEmpty();
        assertThat(EmailVorschauText.vorschau("   ", 10)).isEmpty();
    }

    @Test
    void kuerztOhneEmojiZuHalbieren() {
        assertThat(EmailVorschauText.vorschau("ab\uD83D\uDE00cd", 3)).isEqualTo("ab\uD83D\uDE00");
    }
}
