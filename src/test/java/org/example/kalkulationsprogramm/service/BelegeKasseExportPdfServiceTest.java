package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegKategorie;
import org.example.kalkulationsprogramm.domain.KassenbuchMonatsabschluss;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository;
import org.example.kalkulationsprogramm.repository.KassenbuchMonatsabschlussRepository;
import org.example.kalkulationsprogramm.repository.KassenzaehlungRepository;
import org.example.kalkulationsprogramm.service.pdf.PdfDesign;
import org.example.kalkulationsprogramm.service.pdf.PdfDesignPruefung;
import org.example.kalkulationsprogramm.service.pdf.PdfFarben;
import org.example.kalkulationsprogramm.service.pdf.PdfFirmenDesign;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.test.util.ReflectionTestUtils;

/** Erzeugt das Monats-Kassenbuch mit OpenPDF und prüft Datei-Kopf, Inhalt und Firmendesign. Nur Dummy-Daten. */
@ExtendWith(MockitoExtension.class)
class BelegeKasseExportPdfServiceTest {

    private static final Color BLAU = new Color(0x1d, 0x4e, 0xd8);

    @Mock
    private BelegRepository belegRepository;
    @Mock
    private FirmeninformationRepository firmeninformationRepository;
    @Mock
    private KassenbuchMonatsabschlussRepository abschlussRepository;
    @Mock
    private KassenzaehlungRepository zaehlungRepository;
    @Mock
    private BelegAuditChainVerifier verifier;
    @Mock
    private PdfFirmenDesign pdfFirmenDesign;

    @InjectMocks
    private BelegeKasseExportPdfService service;

    @TempDir
    private Path uploads;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "uploadPath", uploads.toString());
        when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.empty());
        when(zaehlungRepository.findByStichtagBetweenOrderByStichtagAscIdAsc(any(), any())).thenReturn(List.of());
    }

    private void pruefkette(boolean intakt) {
        BelegAuditChainVerifier.Bericht bericht = mock(BelegAuditChainVerifier.Bericht.class);
        when(bericht.isIntakt()).thenReturn(intakt);
        if (intakt) {
            when(bericht.getGesamtAnzahl()).thenReturn(42);
            when(bericht.getLetzterEntryHash()).thenReturn("aaaaaaaa1111111111111111bbbbbbbb");
        }
        when(verifier.verify()).thenReturn(bericht);
    }

    @Test
    void erzeugtLesbaresPdf17FuerLeerenMonat() throws Exception {
        pruefkette(true);
        when(pdfFirmenDesign.laden()).thenReturn(PdfDesign.aus(null, null, null));
        when(belegRepository.findGeprueftImZeitraumNachNummer(any(), any())).thenReturn(List.of());
        when(abschlussRepository.findByJahrAndMonat(anyInt(), anyInt())).thenReturn(Optional.empty());

        byte[] pdf = erzeuge();

        assertThat(new String(pdf, 0, 9, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-1.7\n");
        PdfReader reader = new PdfReader(pdf);
        String text = new PdfTextExtractor(reader).getTextFromPage(1);
        reader.close();
        assertThat(text).contains("Max Mustermann");
    }

    @Test
    void abgeschlossenerMonatErscheintInDerFirmenfarbeMitLogo() throws Exception {
        pruefkette(true);
        when(pdfFirmenDesign.laden()).thenReturn(
                PdfDesign.aus("#1d4ed8", PdfDesignPruefung.testLogo(), "Musterbetrieb GmbH"));
        zweiKassenbewegungen();
        when(abschlussRepository.findByJahrAndMonat(anyInt(), anyInt())).thenReturn(Optional.of(abschluss()));

        byte[] pdf = erzeuge();

        assertThat(PdfDesignPruefung.enthaeltFarbe(pdf, BLAU)).isTrue();
        assertThat(PdfDesignPruefung.enthaeltFarbe(pdf, PdfFarben.hell(BLAU))).as("Wechselzeile").isTrue();
        assertThat(PdfDesignPruefung.enthaeltFarbe(pdf, PdfFarben.STANDARD_AKZENT)).isFalse();
        assertThat(PdfDesignPruefung.enthaeltFarbe(pdf, PdfDesign.ERFOLG)).as("Status abgeschlossen").isTrue();
        assertThat(PdfDesignPruefung.anzahlBilder(pdf)).isEqualTo(1);
    }

    @Test
    void vorlaeufigerMonatBleibtAlsWarnungRotAuchMitFirmenfarbe() throws Exception {
        pruefkette(true);
        when(pdfFirmenDesign.laden()).thenReturn(PdfDesign.aus("#1d4ed8", null, "Musterbetrieb GmbH"));
        when(belegRepository.findGeprueftImZeitraumNachNummer(any(), any())).thenReturn(List.of());
        when(abschlussRepository.findByJahrAndMonat(anyInt(), anyInt())).thenReturn(Optional.empty());

        byte[] pdf = erzeuge();

        assertThat(PdfDesignPruefung.enthaeltFarbe(pdf, PdfDesign.WARNUNG)).isTrue();
        assertThat(PdfDesignPruefung.enthaeltFarbe(pdf, BLAU)).isTrue();
        assertThat(PdfDesignPruefung.anzahlBilder(pdf)).isZero();
    }

    @Test
    void ohneFirmenfarbeBleibtEsBeimBisherigenRot() throws Exception {
        pruefkette(true);
        when(pdfFirmenDesign.laden()).thenReturn(PdfDesign.aus(null, null, null));
        zweiKassenbewegungen();
        when(abschlussRepository.findByJahrAndMonat(anyInt(), anyInt())).thenReturn(Optional.of(abschluss()));

        byte[] pdf = erzeuge();

        assertThat(PdfDesignPruefung.enthaeltFarbe(pdf, PdfFarben.STANDARD_AKZENT)).isTrue();
        assertThat(PdfDesignPruefung.enthaeltFarbe(pdf, PdfFarben.hell(PdfFarben.STANDARD_AKZENT))).isTrue();
    }

    @Test
    void statuszeileUndFusszeileNennenNummernPruefsummeUndUnversehrtesProtokoll() throws Exception {
        pruefkette(true);
        when(pdfFirmenDesign.laden()).thenReturn(PdfDesign.aus(null, null, null));
        zweiKassenbewegungen();
        when(abschlussRepository.findByJahrAndMonat(anyInt(), anyInt())).thenReturn(Optional.of(abschluss()));

        String text = text(erzeuge());

        assertThat(text).contains("Monat abgeschlossen am 02.04.2026 09:00 · 2 Belege festgeschrieben"
                + " · Nummern 1 bis 2 · Prüfsumme 01234567…89abcdef");
        assertThat(text).contains("Das Änderungsprotokoll ist unversehrt (42 Einträge geprüft, "
                + "Prüfsumme aaaaaaaa…bbbbbbbb).");
        assertThat(text).doesNotContain("ACHTUNG");
    }

    @Test
    void fusszeileWarntBeiVeraenderterPruefkette() throws Exception {
        pruefkette(false);
        when(pdfFirmenDesign.laden()).thenReturn(PdfDesign.aus(null, null, null));
        when(belegRepository.findGeprueftImZeitraumNachNummer(any(), any())).thenReturn(List.of());
        when(abschlussRepository.findByJahrAndMonat(anyInt(), anyInt())).thenReturn(Optional.empty());

        String text = text(erzeuge());

        assertThat(text).contains("ACHTUNG: Das Änderungsprotokoll weist eine Lücke oder Veränderung auf.")
                .contains("VORLÄUFIG – dieser Monat ist noch nicht abgeschlossen.")
                .doesNotContain("unversehrt");
    }

    /** Text aller Seiten, Zeilenumbrüche zu Leerzeichen, damit umbrochene Sätze vergleichbar bleiben. */
    private static String text(byte[] pdf) throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            return new org.apache.pdfbox.text.PDFTextStripper().getText(doc).replaceAll("\\s++", " ");
        }
    }

    private byte[] erzeuge() throws Exception {
        Mitarbeiter ersteller = new Mitarbeiter();
        ersteller.setVorname("Max");
        ersteller.setNachname("Mustermann");
        Path datei = service.generatePdf(2026, 3, ersteller, false);
        return Files.readAllBytes(datei);
    }

    private void zweiKassenbewegungen() {
        when(belegRepository.findGeprueftImZeitraumNachNummer(any(), any()))
                .thenReturn(List.of(beleg(1L, "10.00"), beleg(2L, "20.00")))
                .thenReturn(List.of());
    }

    private static Beleg beleg(Long nummer, String betrag) {
        Beleg b = new Beleg();
        b.setId(nummer);
        b.setLaufendeNummer(nummer);
        b.setBelegKategorie(BelegKategorie.KASSE_EINNAHME);
        b.setBelegDatum(LocalDate.of(2026, 3, nummer.intValue()));
        b.setBetragBrutto(new BigDecimal(betrag));
        b.setBeschreibung("Barverkauf Max Mustermann");
        return b;
    }

    private static KassenbuchMonatsabschluss abschluss() {
        KassenbuchMonatsabschluss m = new KassenbuchMonatsabschluss();
        m.setAbgeschlossenAm(LocalDateTime.of(2026, 4, 2, 9, 0));
        m.setAnzahlBelege(2);
        m.setErsteLaufendeNummer(1L);
        m.setLetzteLaufendeNummer(2L);
        m.setEntryHash("0123456789abcdef0123456789abcdef");
        return m;
    }
}
