package org.example.kalkulationsprogramm.service.kassenbuchpdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegKategorie;
import org.example.kalkulationsprogramm.domain.Kassenzaehlung;
import org.example.kalkulationsprogramm.service.pdf.PdfDesign;
import org.example.kalkulationsprogramm.util.PdfWriterFabrik;
import org.junit.jupiter.api.Test;
import org.openpdf.text.Document;
import org.openpdf.text.PageSize;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GoBD-Absicherung nach der Aufteilung: Das Kassenbuch-Journal druckt Übertrag, laufenden
 * Bestand, Summenblock, Nicht-Bar-Hinweis und Kassensturz mit genau den bisherigen Texten.
 * Nur Dummy-Daten.
 */
class KassenbuchJournalPdfTest {

    @Test
    void journalSummenblockHinweisUndKassensturzMitFestenBetraegen() throws Exception {
        Beleg ein1 = beleg(1L, BelegKategorie.KASSE_EINNAHME, "10.00", "Barverkauf Max Mustermann");
        Beleg ein2 = beleg(2L, BelegKategorie.KASSE_EINNAHME, "20.00", "Barverkauf Erika Musterfrau");
        Beleg aus = beleg(3L, BelegKategorie.KASSE_AUSGABE, "5.50", "Porto");
        Beleg bank = beleg(4L, BelegKategorie.BANK, "50.00", "Überweisung Musterbetrieb GmbH");

        Kassenzaehlung sturz = new Kassenzaehlung();
        sturz.setStichtag(LocalDate.of(2026, 3, 31));
        sturz.setGezaehlterBestand(new BigDecimal("120.00"));
        sturz.setRechnerischerBestand(new BigDecimal("124.50"));
        sturz.setDifferenz(new BigDecimal("-4.50"));
        sturz.setBemerkung("Fehlbetrag");
        sturz.setAusgleichBelegId(9L);

        String text = text(List.of(ein1, ein2, aus, bank), new BigDecimal("100.00"), List.of(sturz));

        assertThat(text).contains("Kasse · Bargeldbewegungen", "Übertrag aus dem Vormonat 100,00");
        // laufender Bestand nach jeder Buchung: 110,00 → 130,00 → 124,50
        assertThat(text).containsSubsequence("Barverkauf Max Mustermann", "110,00",
                "Barverkauf Erika Musterfrau", "130,00", "Porto", "5,50", "124,50");
        assertThat(text).contains(
                "Bestand am Monatsanfang 100,00 €",
                "+ Einnahmen und Einlagen 30,00 €",
                "– Ausgaben und Entnahmen 5,50 €",
                "= Bestand am Monatsende 124,50 €");
        assertThat(text).contains("Hinweis: 1 Belege dieses Monats über zusammen 50,00 € wurden nicht bar "
                + "bezahlt (Bank, Kreditkarte, Sonstiges).");
        assertThat(text).doesNotContain("Überweisung Musterbetrieb GmbH");
        assertThat(text).contains("Kassenstürze in diesem Monat", "31.03.2026 120,00 124,50 -4,50",
                "Fehlbetrag · Differenz wurde ausgebucht");
    }

    @Test
    void leererMonatOhneKassensturz() throws Exception {
        String text = text(List.of(), BigDecimal.ZERO, List.of());

        assertThat(text).contains("Keine Bargeldbewegungen in diesem Monat", "= Bestand am Monatsende 0,00 €")
                .doesNotContain("Kassenstürze", "nicht bar bezahlt");
    }

    private static String text(List<Beleg> belege, BigDecimal anfang, List<Kassenzaehlung> zaehlungen)
            throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
        PdfWriterFabrik.writer(doc, out);
        doc.open();
        KassenbuchJournalPdf journal = new KassenbuchJournalPdf(PdfDesign.aus(null, null, null));
        journal.journal(doc, belege, anfang);
        journal.kassenstuerze(doc, zaehlungen);
        doc.close();
        try (PDDocument pdf = Loader.loadPDF(out.toByteArray())) {
            return new PDFTextStripper().getText(pdf);
        }
    }

    private static Beleg beleg(Long nummer, BelegKategorie kategorie, String betrag, String zweck) {
        Beleg b = new Beleg();
        b.setId(nummer);
        b.setLaufendeNummer(nummer);
        b.setBelegKategorie(kategorie);
        b.setBelegDatum(LocalDate.of(2026, 3, nummer.intValue()));
        b.setBetragBrutto(new BigDecimal(betrag));
        b.setBeschreibung(zweck);
        return b;
    }
}
