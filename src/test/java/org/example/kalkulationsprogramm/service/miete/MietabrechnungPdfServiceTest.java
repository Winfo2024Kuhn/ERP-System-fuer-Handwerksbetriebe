package org.example.kalkulationsprogramm.service.miete;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.example.kalkulationsprogramm.service.miete.model.AnnualAccountingResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

/** Erzeugt die Jahresabrechnung mit OpenPDF und prüft Datei-Kopf und Inhalt. Nur Dummy-Daten. */
@ExtendWith(MockitoExtension.class)
class MietabrechnungPdfServiceTest {

    @Mock
    private MietabrechnungService mietabrechnungService;

    @InjectMocks
    private MietabrechnungPdfService service;

    @Test
    void erzeugtLesbaresPdf17() throws Exception {
        when(mietabrechnungService.berechneJahresabrechnung(1L, 2026)).thenReturn(AnnualAccountingResult.builder()
                .mietobjektId(1L)
                .mietobjektName("Musterhaus")
                .mietobjektStrasse("Musterweg 7")
                .mietobjektPlz("54321")
                .mietobjektOrt("Beispielstadt")
                .abrechnungsJahr(2026)
                .gesamtkosten(new BigDecimal("1200.00"))
                .gesamtkostenVorjahr(new BigDecimal("1100.00"))
                .gesamtkostenDifferenz(new BigDecimal("100.00"))
                .kostenstellen(List.of())
                .parteien(List.of())
                .verbrauchsvergleiche(List.of())
                .build());

        byte[] pdf = service.generatePdf(1L, 2026);

        assertThat(new String(pdf, 0, 9, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-1.7\n");
        PdfReader reader = new PdfReader(pdf);
        String text = new PdfTextExtractor(reader).getTextFromPage(1);
        reader.close();
        assertThat(text).contains("Jahresabrechnung 2026").contains("Musterhaus");
    }
}
