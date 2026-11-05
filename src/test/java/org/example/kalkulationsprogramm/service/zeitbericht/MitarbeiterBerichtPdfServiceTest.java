package org.example.kalkulationsprogramm.service.zeitbericht;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.example.kalkulationsprogramm.service.Urlaubskonto;
import org.example.kalkulationsprogramm.service.pdf.PdfDesign;
import org.example.kalkulationsprogramm.service.pdf.PdfFirmenDesign;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MitarbeiterBerichtPdfServiceTest {

    private final PdfFirmenDesign firmenDesign = mock(PdfFirmenDesign.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-05T10:00:00Z"), ZoneId.of("Europe/Berlin"));
    private final MitarbeiterBerichtPdfService service = new MitarbeiterBerichtPdfService(firmenDesign, clock);

    @Test
    void jederMitarbeiterAufEigenerSeiteMitTageslisteUndUnterschrift() throws Exception {
        when(firmenDesign.laden()).thenReturn(PdfDesign.aus("#500010", null, "Musterbetrieb GmbH"));

        try (PDDocument doc = Loader.loadPDF(service.erzeuge(List.of(bericht("Max Mustermann"),
                bericht("Erika Musterfrau")), true, true))) {
            assertThat(doc.getNumberOfPages()).isEqualTo(2);
            String text = new PDFTextStripper().getText(doc);
            assertThat(text).contains("Zeitbericht Max Mustermann", "Zeitbericht Erika Musterfrau",
                    "05.10.2026 bis 06.10.2026", "Tag für Tag", "Mo 05.10.", "Abwesenheit", "Plus/Minus", "Urlaub 8,0", "Musterfeiertag",
                    "Stundenkonto", "4,3", "Urlaub 2026", "Übertrag Vorjahr", "19,0", "Unterschrift Mitarbeiter",
                    "Unterschrift Betrieb", "Musterbetrieb GmbH", "Seite 2");
        }
    }

    @Test
    void ohneTageslisteUndUnterschrift() throws Exception {
        when(firmenDesign.laden()).thenReturn(PdfDesign.aus(null, null, ""));

        try (PDDocument doc = Loader.loadPDF(service.erzeuge(List.of(bericht("Max Mustermann")), false, false))) {
            String text = new PDFTextStripper().getText(doc);
            assertThat(text).contains("Zeitbericht Max Mustermann", "Plus/Minus")
                    .doesNotContain("Tag für Tag", "Unterschrift");
        }
    }

    private static MitarbeiterBerichtDaten bericht(String name) {
        LocalDate mo = LocalDate.of(2026, 10, 5);
        var montag = new MitarbeiterBerichtDaten.Tag(mo, d("8"), d("9.75"), null, d("0"), null, d("0"), d("9.75"));
        var dienstag = new MitarbeiterBerichtDaten.Tag(mo.plusDays(1), d("8"), d("0"), "Urlaub", d("8"),
                "Musterfeiertag", d("0"), d("8"));
        var summen = new MitarbeiterBerichtDaten.Summen(d("16"), d("9.75"), d("8"), d("0"), d("0"), d("0"), d("17.75"));
        var urlaub = new Urlaubskonto(2026, d("30"), d("2"), false, null, d("0"), d("0"), d("10"), d("3"), d("0"),
                d("19"));
        return new MitarbeiterBerichtDaten(name, mo, mo.plusDays(1), List.of(montag, dienstag), summen, d("4.25"),
                urlaub);
    }

    private static BigDecimal d(String wert) {
        return new BigDecimal(wert);
    }
}
