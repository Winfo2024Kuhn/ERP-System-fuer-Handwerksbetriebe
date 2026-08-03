package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Die Projektliste kennt bewusst nur eine Regel: Haken "Beendet" nicht gesetzt.
 * Weder die Dokumentenlage noch der Bezahlt-Status dürfen ein Projekt herausfiltern.
 */
@ExtendWith(MockitoExtension.class)
class ProjektListenPdfServiceTest {

    @Mock
    private ProjektRepository projektRepository;

    @InjectMocks
    private ProjektListenPdfService service;

    /** Der Kundenname hängt an der Kunde-Relation ({@code Projekt#getKunde()} ist abgeleitet). */
    private Projekt projekt(Long id, String bauvorhaben, BigDecimal preis) {
        Projekt projekt = new Projekt();
        projekt.setId(id);
        projekt.setBauvorhaben(bauvorhaben);
        projekt.setAuftragsnummer("2026/07/0000" + id);
        projekt.setAnlegedatum(LocalDate.of(2026, 7, 28));
        projekt.setBruttoPreis(preis);
        return projekt;
    }

    private Kunde kunde(Long id, String name) {
        Kunde kunde = new Kunde();
        kunde.setId(id);
        kunde.setName(name);
        return kunde;
    }

    @Test
    void nimmtGenauDieProjekteOhneHakenBeendet() {
        Projekt offen = projekt(1L, "Dachsanierung Musterweg", new BigDecimal("7930.04"));
        offen.setKundenId(kunde(50L, "Max Mustermann"));
        when(projektRepository.findByAbgeschlossenFalseOrderByAnlegedatumDesc()).thenReturn(List.of(offen));
        when(projektRepository.findIdsMitVorherigemProjektFuerKunde(List.of(1L))).thenReturn(List.of());

        byte[] pdf = service.generatePdf();

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1)).startsWith("%PDF");
        verify(projektRepository).findByAbgeschlossenFalseOrderByAnlegedatumDesc();
    }

    @Test
    void klaertDieAuftragsartMitEinerEinzigenAbfrageFuerAlleZeilen() {
        Kunde stammkunde = kunde(51L, "Erika Musterfrau");
        Projekt erstauftrag = projekt(2L, "Carport Beispielstraße", new BigDecimal("1500.00"));
        erstauftrag.setKundenId(stammkunde);
        Projekt folgeauftrag = projekt(3L, "Treppe Musterhof", new BigDecimal("2400.00"));
        folgeauftrag.setKundenId(stammkunde);

        when(projektRepository.findByAbgeschlossenFalseOrderByAnlegedatumDesc())
                .thenReturn(List.of(erstauftrag, folgeauftrag));
        when(projektRepository.findIdsMitVorherigemProjektFuerKunde(List.of(2L, 3L))).thenReturn(List.of(3L));

        byte[] pdf = service.generatePdf();

        assertThat(pdf).isNotEmpty();
        verify(projektRepository).findIdsMitVorherigemProjektFuerKunde(List.of(2L, 3L));
        verify(projektRepository, never()).existsVorherigesProjektFuerKunde(anyLong(), any(), anyLong());
    }

    @Test
    void erzeugtAuchOhneProjekteEinGueltigesPdf() {
        when(projektRepository.findByAbgeschlossenFalseOrderByAnlegedatumDesc()).thenReturn(List.of());

        byte[] pdf = service.generatePdf();

        assertThat(new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1)).startsWith("%PDF");
        // Keine Projekte → auch keine Zusatzabfrage.
        verify(projektRepository, never()).findIdsMitVorherigemProjektFuerKunde(any());
    }

    @Test
    void drucktDieListeHochkantAufA4() throws Exception {
        Projekt offen = projekt(4L, "Fassade Beispielweg", new BigDecimal("3200.00"));
        offen.setKundenId(kunde(52L, "Max Mustermann"));
        when(projektRepository.findByAbgeschlossenFalseOrderByAnlegedatumDesc()).thenReturn(List.of(offen));
        when(projektRepository.findIdsMitVorherigemProjektFuerKunde(List.of(4L))).thenReturn(List.of());

        try (PDDocument pdf = Loader.loadPDF(service.generatePdf())) {
            PDRectangle seite = pdf.getPage(0).getMediaBox();

            assertThat(seite.getHeight()).isGreaterThan(seite.getWidth());
            assertThat(seite.getWidth()).isCloseTo(PDRectangle.A4.getWidth(), within(1f));
            assertThat(seite.getHeight()).isCloseTo(PDRectangle.A4.getHeight(), within(1f));
        }
    }

    @Test
    void zeigtDenKundenVorDemBauvorhaben() throws Exception {
        Projekt offen = projekt(5L, "Fassade Beispielweg", new BigDecimal("3200.00"));
        offen.setKundenId(kunde(53L, "Erika Musterfrau"));
        when(projektRepository.findByAbgeschlossenFalseOrderByAnlegedatumDesc()).thenReturn(List.of(offen));
        when(projektRepository.findIdsMitVorherigemProjektFuerKunde(List.of(5L))).thenReturn(List.of());

        String text;
        try (PDDocument pdf = Loader.loadPDF(service.generatePdf())) {
            text = new PDFTextStripper().getText(pdf);
        }

        // PDFTextStripper liest zeilenweise von links nach rechts – die Kundenspalte
        // muss daher sowohl im Kopf als auch in der Datenzeile zuerst auftauchen.
        // containsSubsequence prüft Vorhandensein UND Reihenfolge; ein reiner
        // indexOf-Vergleich wäre auch dann grün, wenn eine Spalte ganz fehlt.
        assertThat(text).containsSubsequence("Kunde", "Bauvorhaben");
        assertThat(text).containsSubsequence("Erika Musterfrau", "Fassade Beispielweg");
    }

    @Test
    void wiederholtDieKopfzeileAufJederSeite() throws Exception {
        List<Projekt> viele = java.util.stream.IntStream.rangeClosed(1, 80)
                .mapToObj(i -> {
                    // Der Wert darf das Wort "Bauvorhaben" nicht selbst enthalten, sonst
                    // würde die Kopfzeilen-Prüfung unten auch ohne echte Kopfzelle grün.
                    Projekt p = projekt((long) i, "Halle Nr. " + i, new BigDecimal("1000.00"));
                    p.setKundenId(kunde(100L + i, "Max Mustermann " + i));
                    return p;
                })
                .toList();
        when(projektRepository.findByAbgeschlossenFalseOrderByAnlegedatumDesc()).thenReturn(viele);
        when(projektRepository.findIdsMitVorherigemProjektFuerKunde(any())).thenReturn(List.of());

        try (PDDocument pdf = Loader.loadPDF(service.generatePdf())) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);

            // Auf jeder einzelnen Seite muss die Kopfzeile stehen, sonst weiß beim
            // Ausdruck niemand mehr, welche Spalte was bedeutet.
            for (int seite = 1; seite <= pdf.getNumberOfPages(); seite++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(seite);
                stripper.setEndPage(seite);
                assertThat(stripper.getText(pdf)).containsSubsequence("Kunde", "Bauvorhaben");
            }
        }
    }
}
