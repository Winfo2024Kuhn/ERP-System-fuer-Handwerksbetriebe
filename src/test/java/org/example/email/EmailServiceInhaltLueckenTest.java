package org.example.email;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Testet die reinen Inhalts-Builder und Hilfstypen von {@link EmailService} (ohne SMTP). */
class EmailServiceInhaltLueckenTest {

    private static EmailService.EmailContent rechnung(String pfad, String kunde) {
        return EmailService.buildInvoiceEmail(pfad, "Sehr geehrter Herr", kunde, "Gartentor", "P-100", "R-2024-1",
                LocalDate.of(2024, 3, 5), LocalDate.of(2024, 4, 4), "1.234,56 EUR", "Erika Musterfrau");
    }

    @ParameterizedTest
    @CsvSource({
            "R-1.pdf,Rechnung:",
            "Schlussrechnung_R-1.pdf,Schlussrechnung:",
            "TEILRECHNUNG.pdf,Teilrechnung:",
            "abschlagsrechnung.pdf,Abschlagsrechnung:",
            "Mahnung_1.pdf,Mahnung:",
            "/pfad/zur/schlussrechnung/datei.pdf,Rechnung:"
    })
    @DisplayName("buildInvoiceEmail: Typ wird nur aus dem Dateinamen (nicht dem Verzeichnis) erkannt")
    void rechnungTyp(String pfad, String betreffStart) {
        EmailService.EmailContent c = rechnung(pfad, "Mustermann");
        assertTrue(c.subject().startsWith(betreffStart), c.subject());
        assertTrue(c.subject().contains("(BV: Gartentor)"));
        assertTrue(c.subject().endsWith("Rechnungsnummer: R-2024-1"));
    }

    @Test
    @DisplayName("buildInvoiceEmail: Datumsformat dd.MM.yyyy, Betrag, Kundenname optional")
    void rechnungInhalt() {
        String html = rechnung("r.pdf", "Mustermann").htmlBody();
        assertTrue(html.startsWith("Sehr geehrter Herr Mustermann,<br><br>"), html);
        assertTrue(html.contains("05.03.2024"));
        assertTrue(html.contains("04.04.2024"));
        assertTrue(html.contains("1.234,56 EUR"));
        assertTrue(html.contains("P-100"));

        assertTrue(rechnung("r.pdf", null).htmlBody().startsWith("Sehr geehrter Herr,<br><br>"));
        assertTrue(rechnung("r.pdf", "   ").htmlBody().startsWith("Sehr geehrter Herr,<br><br>"));
    }

    @Test
    @DisplayName("buildInvoiceEmail: Mahnung nennt Faelligkeit, Schlussrechnung enthaelt Bewertungs-Link, Rechnung nicht")
    void rechnungTextbausteine() {
        String mahnung = rechnung("mahnung.pdf", "M").htmlBody();
        assertTrue(mahnung.contains("noch nicht beglichen"));
        assertTrue(mahnung.contains("war am 04.04.2024 fällig"));
        String schluss = rechnung("schlussrechnung.pdf", "M").htmlBody();
        assertTrue(schluss.contains("Jetzt Bewertung abgeben"));
        String normal = rechnung("rechnung.pdf", "M").htmlBody();
        assertFalse(normal.contains("Bewertung"));
        assertTrue(rechnung("teilrechnung.pdf", "M").htmlBody().contains("Teilrechnung"));
        assertTrue(rechnung("abschlagsrechnung.pdf", "M").htmlBody().contains("Abschlagsrechnung"));
    }

    @Test
    @DisplayName("buildInvoiceEmail: fehlende Datumswerte fuehren zu NullPointerException (dokumentiert aktuelles Verhalten)")
    void rechnungOhneDatum() {
        // BEFUND: kein Null-Schutz fuer rechnungsdatum/faelligkeitsdatum -> NPE statt sprechender Fehlermeldung.
        assertThrows(NullPointerException.class, () -> EmailService.buildInvoiceEmail("r.pdf", "Hallo", "X", "BV", "P",
                "R", null, LocalDate.now(), "1", "u"));
        assertThrows(NullPointerException.class, () -> EmailService.buildInvoiceEmail("r.pdf", "Hallo", "X", "BV", "P",
                "R", LocalDate.now(), null, "1", "u"));
    }

    @Test
    @DisplayName("BEFUND: Felder werden ungeprueft in das HTML eingesetzt (XSS-Payload bleibt als Tag stehen)")
    void rechnungHtmlNichtEscaped() {
        // BEFUND: bauvorhaben/kundenName werden nicht HTML-escaped; das Ergebnis wird spaeter ueber
        // EmailHtmlSanitizer bereinigt, der Builder selbst schuetzt nicht.
        EmailService.EmailContent c = EmailService.buildInvoiceEmail("r.pdf", "Hallo", "<script>alert(1)</script>",
                "BV", "P", "R", LocalDate.now(), LocalDate.now(), "1", "u");
        assertTrue(c.htmlBody().contains("<script>alert(1)</script>"));
    }

    @ParameterizedTest
    @CsvSource({
            "Mahnung,Mahnung:",
            "Zahlungserinnerung,Mahnung:",
            "Abschlagsrechnung,Abschlagsrechnung:",
            "Teilrechnung,Teilrechnung:",
            "Schlussrechnung,Schlussrechnung:",
            "RECHNUNG,Rechnung:"
    })
    @DisplayName("buildInvoiceEmailWithTypeHints: Hinweis ueberschreibt den Dateinamen")
    void typeHintsUeberschreiben(String hint, String start) {
        EmailService.EmailContent c = EmailService.buildInvoiceEmailWithTypeHints("schlussrechnung.pdf", "Hallo", "X",
                "BV", "P", "R", LocalDate.now(), LocalDate.now(), "1", "u", hint);
        assertTrue(c.subject().startsWith(start), c.subject());
    }

    @Test
    @DisplayName("buildInvoiceEmailWithTypeHints: erster erkannter Hinweis gewinnt, null/leer/unbekannt werden uebersprungen")
    void typeHintsReihenfolge() {
        EmailService.EmailContent c = EmailService.buildInvoiceEmailWithTypeHints("x.pdf", "Hallo", "X", "BV", "P", "R",
                LocalDate.now(), LocalDate.now(), "1", "u", null, "  ", "irgendwas", "Teil", "Mahnung");
        assertTrue(c.subject().startsWith("Teilrechnung:"), c.subject());
    }

    @Test
    @DisplayName("buildInvoiceEmailWithTypeHints: ohne Treffer gilt der Dateiname, ohne beides 'Rechnung'")
    void typeHintsFallback() {
        assertTrue(EmailService.buildInvoiceEmailWithTypeHints("mahnung.pdf", "Hallo", "X", "BV", "P", "R",
                LocalDate.now(), LocalDate.now(), "1", "u", "nix").subject().startsWith("Mahnung:"));
        assertTrue(EmailService.buildInvoiceEmailWithTypeHints("mahnung.pdf", "Hallo", "X", "BV", "P", "R",
                LocalDate.now(), LocalDate.now(), "1", "u", (String[]) null).subject().startsWith("Mahnung:"));
        assertTrue(EmailService.buildInvoiceEmailWithTypeHints(null, "Hallo", "X", "BV", "P", "R",
                LocalDate.now(), LocalDate.now(), "1", "u").subject().startsWith("Rechnung:"));
        assertTrue(EmailService.buildInvoiceEmailWithTypeHints("  ", "Hallo", "X", "BV", "P", "R",
                LocalDate.now(), LocalDate.now(), "1", "u").subject().startsWith("Rechnung:"));
    }

    @Test
    @DisplayName("buildOrderConfirmationEmail: Betreff, optionale Auftragssumme, optionaler Kundenname")
    void auftragsbestaetigung() {
        EmailService.EmailContent mit = EmailService.buildOrderConfirmationEmail("a.pdf", "Guten Tag", "Mustermann",
                "Carport", "P-1", "AB-7", "500 EUR", "u");
        assertEquals("Auftragsbestätigung: (BV: Carport) Auftragsnummer: AB-7", mit.subject());
        assertTrue(mit.htmlBody().startsWith("Guten Tag Mustermann,<br><br>"));
        assertTrue(mit.htmlBody().contains("Auftragssumme"));
        assertTrue(mit.htmlBody().contains("500 EUR"));

        EmailService.EmailContent ohne = EmailService.buildOrderConfirmationEmail("a.pdf", "Guten Tag", null,
                "Carport", "P-1", "AB-7", " ", "u");
        assertTrue(ohne.htmlBody().startsWith("Guten Tag,<br><br>"));
        assertFalse(ohne.htmlBody().contains("Auftragssumme"));
        assertFalse(EmailService.buildOrderConfirmationEmail("a.pdf", "Hi", "X", "BV", "P", "A", null, "u")
                .htmlBody().contains("Auftragssumme"));
    }

    @Test
    @DisplayName("buildOfferEmail und buildDrawingEmail liefern Betreff und Anrede")
    void angebotUndZeichnung() {
        EmailService.EmailContent offer = EmailService.buildOfferEmail("Hallo", "Erika Musterfrau", "Zaun", "A-5", "u", "Chef");
        assertEquals("Anfrage: (BV: Zaun) Anfragesnummer: A-5", offer.subject());
        assertTrue(offer.htmlBody().startsWith("Hallo Erika Musterfrau,<br><br>"));
        assertTrue(EmailService.buildOfferEmail("Hallo", "", "Zaun", "A-5", "u", "p").htmlBody().startsWith("Hallo,<br><br>"));

        EmailService.EmailContent zeichnung = EmailService.buildDrawingEmail("Guten Tag", "u", "Zaun");
        assertEquals("Kundenzeichnung BV:(Zaun )", zeichnung.subject());
        assertTrue(zeichnung.htmlBody().startsWith("Guten Tag,<br><br>"));
    }

    @Test
    @DisplayName("getEmailBody: setzt den Benutzernamen in die Signatur ein")
    void signatur() {
        String s = EmailService.getEmailBody("Max Mustermann");
        assertTrue(s.contains("Max Mustermann<br>"));
        assertTrue(s.startsWith("<br><br>"));
    }

    @Test
    @DisplayName("Attachment: Konstruktor mit Datei liest Bytes, fehlende Datei ergibt leeres Array")
    void attachmentRecord(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("a.txt");
        Files.writeString(f, "Inhalt");
        EmailService.Attachment a = new EmailService.Attachment(f.toFile(), "Anzeige.txt", "text/plain");
        assertArrayEquals("Inhalt".getBytes(), a.data());
        assertEquals("Anzeige.txt", a.filename());
        assertEquals(f.toFile(), a.file());

        assertEquals(0, new EmailService.Attachment(new File(dir.toFile(), "fehlt.txt"), "x", "text/plain").data().length);
        assertEquals(0, new EmailService.Attachment((File) null, "x", "text/plain").data().length);
        assertEquals(0, new EmailService.Attachment(dir.toFile(), "x", "text/plain").data().length);

        EmailService.Attachment b = new EmailService.Attachment(new byte[] {1}, "b.bin", "application/octet-stream");
        assertNull(b.file());
    }

    @Test
    @DisplayName("Konstanten fuer den ERP-Herkunfts-Header")
    void konstanten() {
        assertEquals("X-ERP-Origin", EmailService.ERP_ORIGIN_HEADER);
        assertEquals("kalkulationsprogramm", EmailService.ERP_ORIGIN_WERT);
    }
}
