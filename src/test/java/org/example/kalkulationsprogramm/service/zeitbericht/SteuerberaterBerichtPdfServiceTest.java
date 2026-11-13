package org.example.kalkulationsprogramm.service.zeitbericht;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.example.kalkulationsprogramm.dto.zeitbericht.SteuerberaterBerichtDto;
import org.example.kalkulationsprogramm.service.pdf.PdfDesign;
import org.example.kalkulationsprogramm.service.pdf.PdfFirmenDesign;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SteuerberaterBerichtPdfServiceTest {

    private final PdfFirmenDesign firmenDesign = mock(PdfFirmenDesign.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-05T10:00:00Z"), ZoneId.of("Europe/Berlin"));
    private final SteuerberaterBerichtPdfService service = new SteuerberaterBerichtPdfService(firmenDesign, clock);

    @Test
    void pdfEnthaeltTitelZeilenUndFirmennameInDerFusszeile() throws Exception {
        when(firmenDesign.laden()).thenReturn(PdfDesign.aus("#500010", null, "Musterbetrieb GmbH"));

        String text = text(service.erzeuge(bericht(false), true, true));

        assertThat(text).contains("Stundenaufstellung Oktober 2026", "2 Mitarbeiter", "Nr.", "Max Mustermann",
                "Erika Musterfrau", "Tagessoll", "Arbeitsstunden (Soll)", "Urlaub", "Feiertage", "Krankheit",
                "Fortbildung", "Sollstunden abzüglich", "148,0", "82,0", "Stundenkonto", "-3,5",
                "Resturlaub (Tage)", "7,5", "abgeschlossen", "offen", "noch nicht abgeschlossen",
                "Musterbetrieb GmbH", "Erstellt am 05.11.2026", "Seite 1");
    }

    /**
     * Der Steuerberater soll keine Gesamtstunden sehen: weder die Spalte „Soll“ (Sollstunden je
     * Mitarbeiter) noch die Summenzeile. „Tagessoll“ und „Arbeitsstunden (Soll)“ bleiben.
     */
    @Test
    void pdfZeigtKeineSollspalteUndKeineSummenzeile() throws Exception {
        when(firmenDesign.laden()).thenReturn(PdfDesign.aus(null, null, null));

        String text = text(service.erzeuge(bericht(false), true, true));

        assertThat(text).doesNotContain("Summe", "264,0", "230,0", "176,0", "88,0");
        // „Soll“ als eigene Kopfzelle – nicht als Teil von „Tagessoll“, „(Soll)“ oder „Sollstunden“
        assertThat(text).doesNotContainPattern("(?<![\\p{L}(])Soll(?![\\p{L})])");
        assertThat(text).contains("Tagessoll", "Arbeitsstunden (Soll)", "Sollstunden abzüglich");
    }

    @Test
    void spaltenStundenkontoUndResturlaubLassenSichAbwaehlen() throws Exception {
        when(firmenDesign.laden()).thenReturn(PdfDesign.aus(null, null, null));

        String text = text(service.erzeuge(bericht(true), false, false));

        assertThat(text).contains("Max Mustermann", "Arbeitsstunden (Ist)", "tatsächlich gebuchte Arbeitszeit")
                .doesNotContain("Stundenkonto", "Resturlaub", "noch nicht abgeschlossen", "Sollstunden abzüglich");
    }

    private static SteuerberaterBerichtDto bericht(boolean alleFest) {
        // Sollstunden je Mitarbeiter wären 176 bzw. 88, zusammen 264; Arbeitsstunden zusammen 230.
        // Keiner dieser Gesamtwerte darf im PDF auftauchen.
        var max = new SteuerberaterBerichtDto.Zeile(1L, "Max Mustermann", d("8"), d("148"), d("16"), d("8"),
                d("4"), d("0"), d("12.5"), d("7.5"), true);
        var erika = new SteuerberaterBerichtDto.Zeile(2L, "Erika Musterfrau", d("4"), d("82"), d("0"),
                d("4"), d("0"), d("2"), d("-3.5"), d("12"), alleFest);
        return new SteuerberaterBerichtDto(2026, 10, alleFest ? SteuerberaterBerichtDto.Stundenart.IST
                : SteuerberaterBerichtDto.Stundenart.SOLL, List.of(max, erika), alleFest);
    }

    private static BigDecimal d(String wert) {
        return new BigDecimal(wert);
    }

    private static String text(byte[] pdf) throws Exception {
        assertThat(new String(pdf, 0, 8)).startsWith("%PDF-1.7");
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }
}
