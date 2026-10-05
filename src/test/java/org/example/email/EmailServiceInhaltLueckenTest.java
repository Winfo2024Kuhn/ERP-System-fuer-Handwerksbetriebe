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

    /** Dummy-Firmendaten (keine echte Bankverbindung). */
    private static final EmailService.Firmenangaben FIRMA = new EmailService.Firmenangaben(
            "Musterbank", "DE00 1234 5678 9012 3456 78", "MUSTDEXXXXX", "https://example.com/bewertung");

    private static EmailService.EmailContent rechnung(String pfad, String kunde) {
        return EmailService.buildInvoiceEmail(pfad, "Sehr geehrter Herr", kunde, "Gartentor", "P-100", "R-2024-1",
                LocalDate.of(2024, 3, 5), LocalDate.of(2024, 4, 4), "1.234,56 EUR", "Erika Musterfrau", FIRMA);
    }

    private static EmailService.EmailContent mitHinweisen(String pfad, String... hinweise) {
        return EmailService.buildInvoiceEmailWithTypeHints(pfad, "Hallo", "X", "BV", "P", "R",
                LocalDate.now(), LocalDate.now(), "1", "u", FIRMA, hinweise);
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
    @DisplayName("buildInvoiceEmail: fehlende Datumswerte lassen die Zeilen weg statt abzustuerzen")
    void rechnungOhneDatum() {
        String ohneRechnungsdatum = EmailService.buildInvoiceEmail("r.pdf", "Hallo", "X", "BV", "P",
                "R", null, LocalDate.of(2024, 4, 4), "1", "u", FIRMA).htmlBody();
        assertFalse(ohneRechnungsdatum.contains("Rechnungsdatum"));
        assertTrue(ohneRechnungsdatum.contains("bis spätestens <span style=\"color:#C00000\">04.04.2024</span>"));

        String ohneFaelligkeit = EmailService.buildInvoiceEmail("r.pdf", "Hallo", "X", "BV", "P",
                "R", LocalDate.of(2024, 3, 5), null, "1", "u", FIRMA).htmlBody();
        assertFalse(ohneFaelligkeit.contains("Fälligkeitsdatum"));
        assertFalse(ohneFaelligkeit.contains("bis spätestens"));
        assertTrue(ohneFaelligkeit.contains("Bitte überweisen Sie den Gesamtbetrag auf das oben genannte Konto."));

        String mahnung = EmailService.buildInvoiceEmail("mahnung.pdf", "Hallo", "X", "BV", "P",
                "R", null, null, null, "u", null).htmlBody();
        assertTrue(mahnung.contains("Der Rechnungsbetrag ist bereits fällig."));

        String ohneNummer = EmailService.buildInvoiceEmail("mahnung.pdf", "Hallo", "X", null, "P",
                " ", null, null, "1", "u", null).htmlBody();
        assertTrue(ohneNummer.contains("dass die Rechnung noch nicht beglichen wurde."), ohneNummer);
    }

    @Test
    @DisplayName("Eingaben werden im HTML maskiert – auch Anrede, Betrag und Nummern")
    void rechnungHtmlEscaped() {
        EmailService.EmailContent c = EmailService.buildInvoiceEmail("mahnung.pdf", "Hallo <b>", "<script>alert(1)</script>",
                "BV \"<img src=x onerror=alert(1)>", "P&1", "R<1>", LocalDate.now(), LocalDate.now(), "1 < 2", "u", FIRMA);
        String html = c.htmlBody();
        assertFalse(html.contains("<script>"));
        assertFalse(html.contains("<img"));
        assertTrue(html.startsWith("Hallo &lt;b&gt; &lt;script&gt;alert(1)&lt;/script&gt;,<br><br>"), html);
        assertTrue(html.contains("BV &quot;&lt;img src=x onerror=alert(1)&gt;"));
        assertTrue(html.contains("P&amp;1"));
        assertTrue(html.contains("R&lt;1&gt;"));
        assertTrue(html.contains("1 &lt; 2"));
        // Der Betreff ist Klartext und bleibt unverändert.
        assertTrue(c.subject().contains("BV \"<img src=x onerror=alert(1)>"));
    }

    @Test
    @DisplayName("Bankverbindung und Bewertungslink kommen aus den Firmendaten, nie aus dem Code")
    void bankdatenAusFirmendaten() {
        String mitKonto = rechnung("rechnung.pdf", "M").htmlBody();
        assertTrue(mitKonto.contains("Bank: Musterbank<br>IBAN: DE00 1234 5678 9012 3456 78<br>BIC/SWIFT: MUSTDEXXXXX<br>"));
        assertTrue(mitKonto.contains("auf das oben genannte Konto."));

        String ohneKonto = EmailService.buildInvoiceEmail("schlussrechnung.pdf", "Hallo", "X", "BV", "P", "R",
                LocalDate.now(), LocalDate.now(), "1", "u", EmailService.Firmenangaben.keine()).htmlBody();
        assertFalse(ohneKonto.contains("Zahlungsinformationen"));
        assertFalse(ohneKonto.contains("IBAN"));
        assertFalse(ohneKonto.contains("oben genannte Konto"));
        assertFalse(ohneKonto.contains("Bewertung"));

        String nurIban = EmailService.buildInvoiceEmail("rechnung.pdf", "Hallo", "X", "BV", "P", "R",
                LocalDate.now(), LocalDate.now(), "1", "u",
                new EmailService.Firmenangaben(" ", "DE00 0000", null, null)).htmlBody();
        assertTrue(nurIban.contains("Zahlungsinformationen:<br>IBAN: DE00 0000<br><br>"));

        String markup = EmailService.buildInvoiceEmail("rechnung.pdf", "Hallo", "X", "BV", "P", "R",
                LocalDate.now(), LocalDate.now(), "1", "u",
                new EmailService.Firmenangaben("<b>Bank</b>", "<i>DE00</i>", "<u>BIC</u>", null)).htmlBody();
        assertTrue(markup.contains("Bank: &lt;b&gt;Bank&lt;/b&gt;<br>IBAN: &lt;i&gt;DE00&lt;/i&gt;<br>BIC/SWIFT: &lt;u&gt;BIC&lt;/u&gt;<br>"));
    }

    @Test
    @DisplayName("Bewertungslink nur fuer http(s) und maskiert")
    void bewertungsLinkAbgesichert() {
        java.util.function.Function<String, String> schluss = link -> EmailService.buildInvoiceEmail("schlussrechnung.pdf",
                "Hallo", "X", "BV", "P", "R", LocalDate.now(), LocalDate.now(), "1", "u",
                new EmailService.Firmenangaben(null, null, null, link)).htmlBody();
        assertTrue(schluss.apply("https://example.com/bewertung?a=1&b=2")
                .contains("<a href=\"https://example.com/bewertung?a=1&amp;b=2\" target=\"_blank\" rel=\"noopener noreferrer\">"));
        assertFalse(schluss.apply("javascript:alert(1)").contains("<a "));
        assertFalse(schluss.apply("https://example.com/\" onmouseover=\"x").contains("onmouseover=\"x"));
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
        EmailService.EmailContent c = mitHinweisen("schlussrechnung.pdf", hint);
        assertTrue(c.subject().startsWith(start), c.subject());
    }

    @Test
    @DisplayName("buildInvoiceEmailWithTypeHints: erster erkannter Hinweis gewinnt, null/leer/unbekannt werden uebersprungen")
    void typeHintsReihenfolge() {
        EmailService.EmailContent c = mitHinweisen("x.pdf", null, "  ", "irgendwas", "Teil", "Mahnung");
        assertTrue(c.subject().startsWith("Teilrechnung:"), c.subject());
    }

    @Test
    @DisplayName("buildInvoiceEmailWithTypeHints: ohne Treffer gilt der Dateiname, ohne beides 'Rechnung'")
    void typeHintsFallback() {
        assertTrue(mitHinweisen("mahnung.pdf", "nix").subject().startsWith("Mahnung:"));
        assertTrue(mitHinweisen("mahnung.pdf", (String[]) null).subject().startsWith("Mahnung:"));
        assertTrue(mitHinweisen(null).subject().startsWith("Rechnung:"));
        assertTrue(mitHinweisen("  ").subject().startsWith("Rechnung:"));
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
    @DisplayName("Angebot, Auftragsbestaetigung und Zeichnung maskieren Eingaben, leere Anrede faellt zurueck")
    void weitereBausteineMaskiert() {
        assertTrue(EmailService.buildOfferEmail("Hallo", "<i>X</i>", "<b>BV</b>", "A&1", "u", null).htmlBody()
                .contains("Hallo &lt;i&gt;X&lt;/i&gt;"));
        assertTrue(EmailService.buildOfferEmail("Hallo", null, "<b>BV</b>", "A&1", "u", null).htmlBody()
                .contains("&lt;b&gt;BV&lt;/b&gt;"));
        assertTrue(EmailService.buildOrderConfirmationEmail(null, null, null, "<b>", "P", "A", null, "u").htmlBody()
                .startsWith("Sehr geehrte Damen und Herren,<br><br>"));
        assertTrue(EmailService.buildDrawingEmail("<b>Hi</b>", "u", "BV").htmlBody().startsWith("&lt;b&gt;Hi&lt;/b&gt;,"));
        assertEquals("&amp;&lt;&gt;&quot;&#39;", EmailService.html("&<>\"'"));
        assertEquals("", EmailService.html(null));
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
