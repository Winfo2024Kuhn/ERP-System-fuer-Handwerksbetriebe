package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.DokumentFreigabe;
import org.example.kalkulationsprogramm.dto.FirmeninformationDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WiderrufsbelehrungPdfServiceTest {

    @Mock FirmeninformationService firmeninformationService;

    private static FirmeninformationDto firma() {
        FirmeninformationDto f = new FirmeninformationDto();
        f.setFirmenname("Musterbetrieb Max Mustermann");
        f.setStrasse("Musterstraße 1");
        f.setPlz("12345");
        f.setOrt("Musterstadt");
        f.setTelefon("01234 / 5678");
        f.setFax(" ");
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

    @Test
    void abschnitte_enthaltenVorbehaltParagrafenUndMusterformular() {
        String text = volltext(WiderrufsbelehrungPdfService.abschnitte(firma(), null));

        assertThat(text)
                .contains("vorsorglich")
                .contains("vertragliches Widerrufsrecht räumen wir nicht ein")
                .contains("§ 356 Abs. 5 Nr. 2 BGB")
                .contains("§ 357a Abs. 2 BGB")
                .contains("§ 312g Abs. 2 Nr. 1 BGB")
                .contains("einschließlich der Lieferkosten")
                .contains("Ohne dieses Verlangen warten wir die Frist ab")
                .contains("Kauf der folgenden Waren (*) / die Erbringung der folgenden Dienstleistung (*)")
                .contains("Muster-Widerrufsformular")
                .contains("Musterbetrieb Max Mustermann, Musterstraße 1, 12345 Musterstadt, Telefon 01234 / 5678, E-Mail info@example.de")
                // Muster-Formular ohne Telefon, wie im gesetzlichen Muster
                .contains("An: Musterbetrieb Max Mustermann, Musterstraße 1, 12345 Musterstadt, E-Mail info@example.de")
                // ohne gepflegte Faxnummer wird Telefax nicht als Weg genannt
                .contains("Brief oder E-Mail")
                .doesNotContain("Telefax")
                .doesNotContain("Ihre Erklärung zum Beginn");
    }

    @Test
    void abschnitte_haltenZustimmungZumVorzeitigenBeginnFest() {
        String mit = volltext(WiderrufsbelehrungPdfService.abschnitte(firma(), freigabe(true)));
        String ohne = volltext(WiderrufsbelehrungPdfService.abschnitte(firma(), freigabe(false)));
        String unbekannt = volltext(WiderrufsbelehrungPdfService.abschnitte(firma(), freigabe(null)));

        assertThat(mit).contains("Angebot AN-2026-0001 am 13.10.2026 um 09:30 Uhr ausdrücklich verlangt");
        assertThat(ohne).contains("keinen Beginn vor Ablauf der Widerrufsfrist verlangt");
        assertThat(unbekannt).doesNotContain("Ihre Erklärung zum Beginn");
    }

    @Test
    void abschnitte_freigabeOhneDatumUndNummer() {
        DokumentFreigabe f = new DokumentFreigabe();
        f.setVorzeitigerBeginn(true);

        String text = volltext(WiderrufsbelehrungPdfService.abschnitte(null, f));

        assertThat(text).contains("Online-Freigabe von Dokument ausdrücklich verlangt")
                .contains("siehe Briefkopf der Auftragsbestätigung");
    }

    @Test
    void anschrift_leereFirmaFaelltAufBriefkopfZurueck() {
        assertThat(WiderrufsbelehrungPdfService.anschrift(new FirmeninformationDto()))
                .isEqualTo("siehe Briefkopf der Auftragsbestätigung");
        FirmeninformationDto nurOrt = new FirmeninformationDto();
        nurOrt.setOrt("Musterstadt");
        nurOrt.setFax("01234 / 9");
        assertThat(WiderrufsbelehrungPdfService.anschrift(nurOrt)).isEqualTo("Musterstadt, Telefax 01234 / 9");
        String mitFax = WiderrufsbelehrungPdfService.abschnitte(nurOrt, null).get(1).absaetze().get(1);
        assertThat(mitFax).contains("Brief, Telefax oder E-Mail");
    }

    @Test
    void erzeugePdf_liefertPdfAuchOhneFirmendaten() {
        when(firmeninformationService.getFirmeninformation()).thenThrow(new IllegalStateException("DB weg"));
        WiderrufsbelehrungPdfService service = new WiderrufsbelehrungPdfService(firmeninformationService);

        byte[] pdf = service.erzeugePdf(freigabe(true));

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(1000);
    }

    @Test
    void erzeugePdf_mitFirmendaten() {
        when(firmeninformationService.getFirmeninformation()).thenReturn(firma());
        WiderrufsbelehrungPdfService service = new WiderrufsbelehrungPdfService(firmeninformationService);

        assertThat(new String(service.erzeugePdf(null), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void dateinameUndStand() {
        assertThat(WiderrufsbelehrungPdfService.dateiname()).isEqualTo("Widerrufsbelehrung_Stand_2026-10-13.pdf");
        assertThat(WiderrufsbelehrungPdfService.formatiereStand("2026-10-13")).isEqualTo("13.10.2026");
        assertThat(WiderrufsbelehrungPdfService.formatiereStand("kaputt")).isEqualTo("kaputt");
        assertThat(WiderrufsbelehrungPdfService.formatiereStand(null)).isEqualTo("—");
    }
}
