package org.example.kalkulationsprogramm.service;

import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.example.kalkulationsprogramm.domain.DokumentFreigabe;
import org.example.kalkulationsprogramm.domain.RechtstextArt;
import org.example.kalkulationsprogramm.dto.FirmeninformationDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WiderrufsbelehrungPdfServiceTest {

    @Mock FirmeninformationService firmeninformationService;
    @Mock RechtstextService rechtstextService;

    private WiderrufsbelehrungPdfService service;
    /** Echter Service nur für Standardtext und Prüfung – ohne Datenbank. */
    private final RechtstextService echt = new RechtstextService(null, Clock.systemDefaultZone());

    @BeforeEach
    void setUp() {
        service = new WiderrufsbelehrungPdfService(firmeninformationService, rechtstextService);
    }

    private static FirmeninformationDto firma() {
        FirmeninformationDto f = new FirmeninformationDto();
        f.setFirmenname("Musterbetrieb Max Mustermann");
        f.setStrasse("Musterstraße 1");
        f.setPlz("12345");
        f.setOrt("Musterstadt");
        f.setTelefon("01234 / 5678");
        f.setFax("01234 / 9");
        f.setEmail("info@example.de");
        return f;
    }

    private static String volltext(List<WiderrufsbelehrungPdfService.Abschnitt> abschnitte) {
        return abschnitte.stream()
                .map(a -> a.titel() + "\n" + String.join("\n", a.absaetze()))
                .collect(Collectors.joining("\n"));
    }

    private static DokumentFreigabe freigabe(Boolean vorzeitig) {
        DokumentFreigabe f = new DokumentFreigabe();
        f.setDokumentArt("Angebot");
        f.setDokumentNummer("AN-2026-0001");
        f.setAkzeptiertAm(LocalDateTime.of(2026, 10, 13, 9, 30));
        f.setVorzeitigerBeginn(vorzeitig);
        return f;
    }

    private static String pdfText(byte[] pdf) throws Exception {
        try (PdfReader reader = new PdfReader(pdf)) {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder sb = new StringBuilder();
            for (int seite = 1; seite <= reader.getNumberOfPages(); seite++) {
                sb.append(extractor.getTextFromPage(seite)).append('\n');
            }
            return sb.toString().replaceAll("\\s++", " ");
        }
    }

    private RechtstextService.Rechtstext standard() {
        RechtstextArt w = RechtstextArt.WIDERRUFSBELEHRUNG;
        return new RechtstextService.Rechtstext(w, 0, w.getStandardStand(), null, echt.standardHtml(w), true);
    }

    @Test
    void festeAbschnitte_musterformularOhneTelefonUndFax() {
        String text = volltext(WiderrufsbelehrungPdfService.festeAbschnitte(firma(), null));

        assertThat(text)
                .contains("Muster-Widerrufsformular")
                .contains("Kauf der folgenden Waren (*) / die Erbringung der folgenden Dienstleistung (*)")
                .contains("An: Musterbetrieb Max Mustermann, Musterstraße 1, 12345 Musterstadt, E-Mail info@example.de")
                .doesNotContain("Telefon")
                .doesNotContain("Telefax")
                .doesNotContain("Ihre Erklärung zum Beginn");
    }

    @Test
    void festeAbschnitte_haltenZustimmungZumVorzeitigenBeginnFest() {
        String mit = volltext(WiderrufsbelehrungPdfService.festeAbschnitte(firma(), freigabe(true)));
        String ohne = volltext(WiderrufsbelehrungPdfService.festeAbschnitte(firma(), freigabe(false)));
        String unbekannt = volltext(WiderrufsbelehrungPdfService.festeAbschnitte(firma(), freigabe(null)));

        assertThat(mit).contains("Angebot AN-2026-0001 am 13.10.2026 um 09:30 Uhr ausdrücklich verlangt");
        assertThat(ohne).contains("keinen Beginn vor Ablauf der Widerrufsfrist verlangt");
        assertThat(unbekannt).doesNotContain("Ihre Erklärung zum Beginn");
    }

    @Test
    void festeAbschnitte_freigabeOhneDatumUndNummer() {
        DokumentFreigabe f = new DokumentFreigabe();
        f.setVorzeitigerBeginn(true);

        String text = volltext(WiderrufsbelehrungPdfService.festeAbschnitte(null, f));

        assertThat(text).contains("Online-Freigabe von Dokument ausdrücklich verlangt")
                .contains("An: siehe Briefkopf der Auftragsbestätigung");
    }

    @Test
    void anschrift_mitTelefonOhneFax_leereFirmaFaelltAufBriefkopfZurueck() {
        assertThat(WiderrufsbelehrungPdfService.anschrift(firma())).isEqualTo(
                "Musterbetrieb Max Mustermann, Musterstraße 1, 12345 Musterstadt, Telefon 01234 / 5678, E-Mail info@example.de");
        assertThat(WiderrufsbelehrungPdfService.anschrift(null)).isEqualTo("siehe Briefkopf der Auftragsbestätigung");
        assertThat(WiderrufsbelehrungPdfService.anschrift(new FirmeninformationDto()))
                .isEqualTo("siehe Briefkopf der Auftragsbestätigung");
        assertThat(WiderrufsbelehrungPdfService.formularAnschrift(new FirmeninformationDto()))
                .isEqualTo("siehe Briefkopf der Auftragsbestätigung");
        FirmeninformationDto nurOrt = new FirmeninformationDto();
        nurOrt.setOrt(" Musterstadt ");
        assertThat(WiderrufsbelehrungPdfService.anschrift(nurOrt)).isEqualTo("Musterstadt");
    }

    @Test
    void platzhalter_werdenAusgefuelltUndMaskiert() {
        FirmeninformationDto f = firma();
        f.setFirmenname("Max & <Söhne>");

        String html = WiderrufsbelehrungPdfService.fuelleAus(
                "<p>{{FIRMA_KONTAKT}} | {{firmenname}} | {{FIRMA_ANSCHRIFT}} | {{FIRMA_TELEFON}} | {{FIRMA_EMAIL}} | {{UNBEKANNT}}</p>", f);

        assertThat(html)
                .contains("Max &amp; &lt;S&ouml;hne&gt;, Musterstra&szlig;e 1")
                .contains("| Musterstra&szlig;e 1, 12345 Musterstadt |")
                .contains("| 01234 / 5678 | info@example.de | {{UNBEKANNT}}")
                .doesNotContain("<Söhne>");
        assertThat(WiderrufsbelehrungPdfService.platzhalterWerte(null))
                .containsEntry("FIRMA_KONTAKT", "siehe Briefkopf der Auftragsbestätigung")
                .containsEntry("FIRMENNAME", "")
                .containsEntry("FIRMA_EMAIL", "");
        assertThat(WiderrufsbelehrungPdfService.PLATZHALTER)
                .extracting(WiderrufsbelehrungPdfService.Platzhalter::token)
                .allSatisfy(t -> assertThat(WiderrufsbelehrungPdfService.platzhalterWerte(firma()))
                        .containsKey(t.substring(2, t.length() - 2)));
    }

    @Test
    void erzeuge_standardtextMitFirmendatenUndErklaerung() throws Exception {
        when(rechtstextService.aktuell(RechtstextArt.WIDERRUFSBELEHRUNG)).thenReturn(standard());
        when(firmeninformationService.getFirmeninformation()).thenReturn(firma());

        WiderrufsbelehrungPdfService.Belehrung b = service.erzeuge(freigabe(true));

        assertThat(b.dateiname()).isEqualTo("Widerrufsbelehrung_Stand_2026-10-13.pdf");
        assertThat(b.stand()).isEqualTo(LocalDate.of(2026, 10, 13));
        assertThat(b.fassung()).isZero();
        String text = pdfText(b.pdf());
        assertThat(text)
                .contains("Stand: 13.10.2026")
                .doesNotContain("Fassung")
                .contains("Widerrufsrecht")
                .contains("Vertragsabschlusses")
                .contains("Telefon 01234 / 5678")
                .contains("Ihre Erklärung zum Beginn der Arbeiten")
                .contains("Muster-Widerrufsformular")
                .doesNotContain("{{")
                .doesNotContain("Telefax");
    }

    @Test
    void erzeuge_gepflegteFassungOhneFirmendaten() throws Exception {
        when(rechtstextService.aktuell(RechtstextArt.WIDERRUFSBELEHRUNG)).thenReturn(new RechtstextService.Rechtstext(
                RechtstextArt.WIDERRUFSBELEHRUNG, 3, LocalDate.of(2026, 11, 2), LocalDateTime.of(2026, 11, 2, 9, 0),
                "<h3>Eigene Belehrung</h3><p>Schreiben Sie an {{FIRMA_KONTAKT}}.</p>", false));
        when(firmeninformationService.getFirmeninformation()).thenThrow(new IllegalStateException("DB weg"));

        WiderrufsbelehrungPdfService.Belehrung b = service.erzeuge(null);

        assertThat(b.fassung()).isEqualTo(3);
        assertThat(b.dateiname()).isEqualTo("Widerrufsbelehrung_Stand_2026-11-02_Fassung_3.pdf");
        assertThat(pdfText(b.pdf()))
                .contains("Stand: 02.11.2026 · Fassung 3")
                .contains("Eigene Belehrung")
                .contains("Schreiben Sie an siehe Briefkopf der Auftragsbestätigung.")
                .doesNotContain("Ihre Erklärung zum Beginn");
    }

    @Test
    void vorschau_ungespeicherterText() throws Exception {
        when(rechtstextService.pruefeUndBereinige("<p>Max</p><script>x</script>")).thenReturn("<p>Max</p>");
        when(firmeninformationService.getFirmeninformation()).thenReturn(firma());

        String text = pdfText(service.erzeugeVorschau("<p>Max</p><script>x</script>"));

        assertThat(text).contains("Vorschau – noch nicht gespeichert").contains("Max").contains("Muster-Widerrufsformular");
    }

    @Test
    void vorschau_ohneText_zeigtGespeichertenStand() throws Exception {
        when(rechtstextService.aktuell(RechtstextArt.WIDERRUFSBELEHRUNG)).thenReturn(standard());

        assertThat(pdfText(service.erzeugeVorschau(null))).contains("Stand: 13.10.2026");
    }

    @Test
    void vorschau_leererText_400() {
        when(rechtstextService.pruefeUndBereinige("")).thenThrow(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Der Text ist leer."));

        assertThatThrownBy(() -> service.erzeugeVorschau("")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void formatiereStand() {
        assertThat(WiderrufsbelehrungPdfService.formatiereStand("2026-10-13")).isEqualTo("13.10.2026");
        assertThat(WiderrufsbelehrungPdfService.formatiereStand("kaputt")).isEqualTo("kaputt");
        assertThat(WiderrufsbelehrungPdfService.formatiereStand(null)).isEqualTo("—");
    }
}
