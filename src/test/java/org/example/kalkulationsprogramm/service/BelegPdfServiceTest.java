package org.example.kalkulationsprogramm.service;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.example.kalkulationsprogramm.domain.Firmeninformation;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository;
import org.example.kalkulationsprogramm.service.BelegPdfService.EigenbelegDaten;
import org.example.kalkulationsprogramm.service.BelegPdfService.ErzeugtesPdf;
import org.example.kalkulationsprogramm.service.BelegPdfService.QuittungDaten;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit-Tests fuer BelegPdfService: Quittung (Bareinnahme-Beleg an den Kunden)
 * und Eigenbeleg (Ersatzbeleg ohne Fremdbeleg, ohne Vorsteuer).
 *
 * Dummy-Daten laut DSGVO-Vorgabe: Ersteller "Max Mustermann",
 * Gegenpartei "Musterbetrieb GmbH".
 */
class BelegPdfServiceTest {

    @TempDir
    Path tempDir;

    private FirmeninformationRepository firmeninformationRepository;
    private BelegPdfService service;

    @BeforeEach
    void setUp() {
        firmeninformationRepository = mock(FirmeninformationRepository.class);
        service = new BelegPdfService(firmeninformationRepository);
        ReflectionTestUtils.setField(service, "uploadPath", tempDir.toString());
    }

    // ======================= Test-Daten =======================

    private Mitarbeiter ersteller() {
        Mitarbeiter m = new Mitarbeiter();
        m.setVorname("Max");
        m.setNachname("Mustermann");
        return m;
    }

    private QuittungDaten quittungDaten(BigDecimal brutto) {
        return new QuittungDaten(brutto, new BigDecimal("19"), LocalDate.of(2026, 3, 15),
                "Musterbetrieb GmbH", "Beratung vor Ort", "8400 Erlöse 19 %", null, ersteller());
    }

    private EigenbelegDaten eigenbelegDaten() {
        return new EigenbelegDaten(new BigDecimal("15.00"), LocalDate.of(2026, 3, 10),
                "Musterbetrieb GmbH", "Parkgebühr Baustelle", "Parkautomat, kein Bon ausgegeben",
                "Bar", "4670 Reisekosten", ersteller());
    }

    private Firmeninformation firmaMitDaten() {
        Firmeninformation f = new Firmeninformation();
        f.setFirmenname("Musterbetrieb GmbH");
        f.setStrasse("Musterstraße 1");
        f.setPlz("12345");
        f.setOrt("Musterstadt");
        return f;
    }

    // ======================= Hilfsmethoden =======================

    private String extractText(Path pdf) throws Exception {
        byte[] bytes = Files.readAllBytes(pdf);
        PdfReader reader = new PdfReader(bytes);
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= reader.getNumberOfPages(); i++) {
                sb.append(extractor.getTextFromPage(i)).append("\n");
            }
            return sb.toString();
        } finally {
            reader.close();
        }
    }

    private String selbstBerechneterHash(Path datei) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(Files.readAllBytes(datei));
        StringBuilder sb = new StringBuilder(64);
        for (byte b : digest) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private void assertGueltigesPdf(Path datei) throws Exception {
        assertTrue(Files.exists(datei), "PDF-Datei muss existieren: " + datei);
        byte[] bytes = Files.readAllBytes(datei);
        assertTrue(bytes.length > 1024, "PDF sollte mehr als 1 kB haben, war " + bytes.length);
        assertEquals("%PDF", new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
    }

    // ======================= Quittung =======================

    @Test
    void erzeugeQuittung_legtLesbarePdfDateiImBelegeOrdnerAn() throws Exception {
        when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.empty());

        ErzeugtesPdf ergebnis = service.erzeugeQuittung(quittungDaten(new BigDecimal("119.00")));

        assertEquals("application/pdf", ergebnis.mimeType());
        assertTrue(ergebnis.gespeicherterDateiname().endsWith("_Quittung-2026-03-15.pdf"),
                "Unerwarteter Dateiname: " + ergebnis.gespeicherterDateiname());
        assertEquals("Quittung-2026-03-15.pdf", ergebnis.originalDateiname());

        Path datei = tempDir.resolve("belege").resolve(ergebnis.gespeicherterDateiname());
        assertGueltigesPdf(datei);
    }

    @Test
    void erzeugeQuittung_shaImRecordStimmtMitDateiUeberein() throws Exception {
        when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.empty());

        ErzeugtesPdf ergebnis = service.erzeugeQuittung(quittungDaten(new BigDecimal("119.00")));

        assertEquals(64, ergebnis.sha256().length());
        Path datei = tempDir.resolve("belege").resolve(ergebnis.gespeicherterDateiname());
        assertEquals(selbstBerechneterHash(datei), ergebnis.sha256());
    }

    @Test
    void erzeugeQuittung_ohneFirmeninformation_erzeugtTrotzdemGueltigesPdf() throws Exception {
        when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.empty());

        ErzeugtesPdf ergebnis = service.erzeugeQuittung(quittungDaten(new BigDecimal("119.00")));

        Path datei = tempDir.resolve("belege").resolve(ergebnis.gespeicherterDateiname());
        assertGueltigesPdf(datei);
    }

    @Test
    void erzeugeQuittung_mitFirmeninformation_briefkopfWirdGedruckt() throws Exception {
        when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.of(firmaMitDaten()));

        ErzeugtesPdf ergebnis = service.erzeugeQuittung(quittungDaten(new BigDecimal("119.00")));

        Path datei = tempDir.resolve("belege").resolve(ergebnis.gespeicherterDateiname());
        String text = extractText(datei);
        assertTrue(text.contains("Musterstraße"), "Firmenanschrift sollte im Briefkopf stehen");
    }

    @Test
    void erzeugeQuittung_enthaeltErwarteteZeilenUndFusstext() throws Exception {
        when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.empty());

        ErzeugtesPdf ergebnis = service.erzeugeQuittung(quittungDaten(new BigDecimal("119.00")));

        Path datei = tempDir.resolve("belege").resolve(ergebnis.gespeicherterDateiname());
        String text = extractText(datei);

        assertTrue(text.contains("Quittung"), "Titel fehlt");
        assertTrue(text.contains("Von"), "Zeile 'Von wem' fehlt");
        assertTrue(text.contains("Wofür"), "Zeile 'Wofür' fehlt");
        assertTrue(text.contains("MwSt"), "MwSt-Zeile fehlt");
        assertTrue(text.contains("8400"), "Sachkonto-Zeile fehlt");
        assertTrue(text.contains("Kassenbuch"), "Fusstext fehlt");
        assertTrue(text.contains("Registrierkasse"), "Fusstext (Registrierkasse-Hinweis) fehlt");
        assertTrue(text.contains("Erstellt"), "Ersteller-Block fehlt");
        assertTrue(text.contains("Mustermann"), "Erstellername fehlt");
        assertFalse(text.contains("Zu Rechnung"), "Ohne Rechnungsnummer darf 'Zu Rechnung' nicht erscheinen");
    }

    @Test
    void erzeugeQuittung_mitRechnungsnummer_zeigtZuRechnungZeile() throws Exception {
        when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.empty());
        QuittungDaten daten = new QuittungDaten(new BigDecimal("50.00"), new BigDecimal("19"),
                LocalDate.of(2026, 3, 15), "Musterbetrieb GmbH", "Beratung", null,
                "RE-2026-0042", ersteller());

        ErzeugtesPdf ergebnis = service.erzeugeQuittung(daten);

        Path datei = tempDir.resolve("belege").resolve(ergebnis.gespeicherterDateiname());
        String text = extractText(datei);
        assertTrue(text.contains("Zu Rechnung"), "'Zu Rechnung' fehlt trotz gesetzter Rechnungsnummer");
        assertTrue(text.contains("RE-2026-0042"));
    }

    @Test
    void erzeugeQuittung_ueber250Euro_enthaeltUstdvHinweis() throws Exception {
        when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.empty());

        ErzeugtesPdf ergebnis = service.erzeugeQuittung(quittungDaten(new BigDecimal("300.00")));

        Path datei = tempDir.resolve("belege").resolve(ergebnis.gespeicherterDateiname());
        String text = extractText(datei);
        assertTrue(text.contains("250"), "Ab 250 € muss der UStDV-Hinweis erscheinen");
        assertTrue(text.contains("vollständigen"), "Ab 250 € muss der UStDV-Hinweis erscheinen");
    }

    @Test
    void erzeugeQuittung_bis250Euro_enthaeltUstdvHinweisNicht() throws Exception {
        when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.empty());

        ErzeugtesPdf ergebnis = service.erzeugeQuittung(quittungDaten(new BigDecimal("119.00")));

        Path datei = tempDir.resolve("belege").resolve(ergebnis.gespeicherterDateiname());
        String text = extractText(datei);
        assertFalse(text.contains("vollständigen"), "Bis 250 € darf der UStDV-Hinweis nicht erscheinen");
    }

    // ======================= Eigenbeleg =======================

    @Test
    void erzeugeEigenbeleg_legtLesbarePdfDateiImBelegeOrdnerAn() throws Exception {
        when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.empty());

        ErzeugtesPdf ergebnis = service.erzeugeEigenbeleg(eigenbelegDaten());

        assertEquals("application/pdf", ergebnis.mimeType());
        assertTrue(ergebnis.gespeicherterDateiname().endsWith("_Ersatzbeleg-2026-03-10.pdf"),
                "Unerwarteter Dateiname: " + ergebnis.gespeicherterDateiname());
        assertEquals("Ersatzbeleg-2026-03-10.pdf", ergebnis.originalDateiname());

        Path datei = tempDir.resolve("belege").resolve(ergebnis.gespeicherterDateiname());
        assertGueltigesPdf(datei);
    }

    @Test
    void erzeugeEigenbeleg_shaImRecordStimmtMitDateiUeberein() throws Exception {
        when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.empty());

        ErzeugtesPdf ergebnis = service.erzeugeEigenbeleg(eigenbelegDaten());

        assertEquals(64, ergebnis.sha256().length());
        Path datei = tempDir.resolve("belege").resolve(ergebnis.gespeicherterDateiname());
        assertEquals(selbstBerechneterHash(datei), ergebnis.sha256());
    }

    @Test
    void erzeugeEigenbeleg_ohneFirmeninformation_erzeugtTrotzdemGueltigesPdf() throws Exception {
        when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.empty());

        ErzeugtesPdf ergebnis = service.erzeugeEigenbeleg(eigenbelegDaten());

        Path datei = tempDir.resolve("belege").resolve(ergebnis.gespeicherterDateiname());
        assertGueltigesPdf(datei);
    }

    @Test
    void erzeugeEigenbeleg_enthaeltGrundsatzUndKeinenMwstBetrag() throws Exception {
        when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.empty());

        ErzeugtesPdf ergebnis = service.erzeugeEigenbeleg(eigenbelegDaten());

        Path datei = tempDir.resolve("belege").resolve(ergebnis.gespeicherterDateiname());
        String text = extractText(datei);

        assertTrue(text.contains("Ersatzbeleg"), "Titel fehlt");
        assertTrue(text.contains("Eigenbeleg"), "Titel fehlt");
        assertTrue(text.contains("Ohne Fremdbeleg gibt es keine Vorsteuer."),
                "Fester Satz fehlt");
        assertFalse(text.contains("MwSt"), "Eigenbeleg darf keinen MwSt-Ausweis enthalten");
        assertFalse(text.toLowerCase(java.util.Locale.GERMAN).contains("unterschrift"),
                "Eigenbeleg darf keine Unterschriftszeile enthalten");
        assertTrue(text.contains("Grund"), "Zeile 'Grund' fehlt");
        assertTrue(text.contains("Parkautomat"), "Grund-Wert fehlt");
        assertTrue(text.contains("Erstellt"), "Ersteller-Block fehlt");
        assertTrue(text.contains("Mustermann"), "Erstellername fehlt");
    }
}
