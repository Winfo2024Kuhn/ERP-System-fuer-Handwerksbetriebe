package org.example.kalkulationsprogramm.service.rechnungpdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.example.kalkulationsprogramm.service.RechnungPdfService.AbrechnungspositionPdfDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.AbrechnungsverlaufPdfDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.AbschlagInfoPdfDto;
import org.example.kalkulationsprogramm.util.PdfWriterFabrik;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openpdf.text.Document;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

/**
 * Abschnitt „Abrechnungsstand“ mit vom Handwerker angepassten Zeilen „diese Rechnung“ und
 * Differenzzeile. Ausblenden nimmt nur die Zeile weg, alle Betraege bleiben gleich.
 * Nur Dummy-Daten.
 */
class RechnungAbrechnungsstandPdfTest {

    /** Nutzerfall: Angebot 6.430,76 €, eine Rechnung über 7.710,90 € schließt den Auftrag ab. */
    private static AbrechnungsverlaufPdfDto nutzerfall(AbschlussZeileAnpassung diese, AbschlussZeileAnpassung differenz) {
        return verlauf("6430.76", List.of(), "Treppengeländer Edelstahl mit Glasfüllung", true, diese, differenz);
    }

    private static AbrechnungsverlaufPdfDto verlauf(String basis, List<AbrechnungspositionPdfDto> vorher, String hinweis,
                                                    boolean schliesst, AbschlussZeileAnpassung diese,
                                                    AbschlussZeileAnpassung differenz) {
        return new AbrechnungsverlaufPdfDto("AG-2026/03/00003", "Angebot", LocalDate.of(2026, 3, 2),
                new BigDecimal(basis), vorher, true, hinweis, schliesst, diese, differenz);
    }

    private static String rechnung(String netto, AbrechnungsverlaufPdfDto verlauf) {
        return rendere("Rechnung", false, netto, verlauf, null);
    }

    private static String rendere(String typ, boolean abschlag, String netto, AbrechnungsverlaufPdfDto verlauf,
                                  AbschlagInfoPdfDto info) {
        BigDecimal betrag = new BigDecimal(netto);
        PdfPTable tabelle = new PdfPTable(new float[] { 6f, 2f, 2f });
        tabelle.setWidthPercentage(100);
        RechnungAbrechnungsstandPdf.fuelle(tabelle, new RechnungAbrechnungsstandPdf.Eingabe(
                verlauf, typ, abschlag, betrag, null, BigDecimal.ZERO, betrag, info));
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document dokument = new Document();
            PdfWriterFabrik.writer(dokument, out);
            dokument.open();
            dokument.add(tabelle);
            dokument.close();
            PdfReader reader = new PdfReader(out.toByteArray());
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            reader.close();
            return text;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("Ohne Anpassung: automatische Texte und Beträge wie bisher")
    void ohneAnpassungWieBisher() {
        String text = rechnung("7710.90", nutzerfall(null, null));

        assertThat(text).contains("Auftragssumme", "7.652,60 €", "diese Rechnung", "- 9.175,97 €",
                "Zusätzliche Leistungen", "Treppengeländer Edelstahl mit Glasfüllung", "+ 1.523,37 €",
                "Zahlbetrag", "100 % des Auftrags");
        assertThat(text).doesNotContain("Noch offen");
    }

    @Nested
    @DisplayName("Zeile „diese Rechnung“")
    class DieseRechnung {

        @Test
        void eigeneBezeichnungUndUnterzeileErsetzenDenAutomatischenText() {
            String text = rechnung("7710.90", nutzerfall(
                    new AbschlussZeileAnpassung("Rechnung Treppengeländer", "Fertigstellung 11/2026", false), null));

            assertThat(text).contains("Rechnung Treppengeländer", "Fertigstellung 11/2026", "- 9.175,97 €");
            assertThat(text).doesNotContain("diese Rechnung");
        }

        @Test
        void leereBezeichnungFaelltAufDenAutomatischenTextZurueck() {
            String text = rechnung("7710.90", nutzerfall(new AbschlussZeileAnpassung("   ", null, null), null));

            assertThat(text).contains("diese Rechnung", "- 9.175,97 €");
        }

        @Test
        void ausblendenEntferntNurDieZeileDerZahlbetragBleibt() {
            String text = rechnung("7710.90", nutzerfall(new AbschlussZeileAnpassung("egal", "egal", true), null));

            assertThat(text).doesNotContain("diese Rechnung", "egal", "- 9.175,97 €");
            assertThat(text).contains("Auftragssumme", "Zusätzliche Leistungen", "+ 1.523,37 €",
                    "Nettobetrag 7.710,90 €", "1.465,07 €", "Zahlbetrag 9.175,97 €");
        }

        @Test
        void unterzeileErsetztDenAbschlagshinweis() {
            String text = rendere("Abschlagsrechnung", true, "3000",
                    verlauf("10000", List.of(), null, false,
                            new AbschlussZeileAnpassung(null, "Rohbau fertig", null), null),
                    new AbschlagInfoPdfDto("prozent", new BigDecimal("30")));

            assertThat(text).contains("diese Abschlagsrechnung", "Rohbau fertig", "Noch offen", "8.330,00 €");
            assertThat(text).doesNotContain("ca. 30 %");
        }

        @Test
        void ausgeblendeteAbschlagszeileAendertProzentUndRestNicht() {
            String text = rendere("Abschlagsrechnung", true, "3000",
                    verlauf("10000", List.of(), null, false, new AbschlussZeileAnpassung(null, null, true), null),
                    new AbschlagInfoPdfDto("prozent", new BigDecimal("30")));

            assertThat(text).doesNotContain("diese Abschlagsrechnung");
            assertThat(text).contains("30 % des Auftrags", "Noch offen", "8.330,00 €", "Zahlbetrag 3.570,00 €");
        }

        @Test
        void htmlWirdAlsKlartextGedruckt() {
            String text = rechnung("7710.90", nutzerfall(
                    new AbschlussZeileAnpassung("<script>alert(1)</script>", "<b>fett</b>", null), null));

            assertThat(text).contains("<script>alert(1)</script>", "<b>fett</b>");
        }
    }

    @Nested
    @DisplayName("Differenzzeile")
    class Differenzzeile {

        @Test
        void eigeneBezeichnungBehaeltVorzeichenUndBetrag() {
            String text = rechnung("7710.90", nutzerfall(null,
                    new AbschlussZeileAnpassung("Mehrleistung", null, null)));

            assertThat(text).contains("Mehrleistung", "+ 1.523,37 €", "netto 1.280,14 €",
                    "Treppengeländer Edelstahl mit Glasfüllung");
            assertThat(text).doesNotContain("Zusätzliche Leistungen");
        }

        @Test
        void eigeneUnterzeileGehtVorDemHinweis() {
            String text = rechnung("7710.90", nutzerfall(null,
                    new AbschlussZeileAnpassung(null, "Glasfüllung statt Lochblech", null)));

            assertThat(text).contains("Zusätzliche Leistungen", "Glasfüllung statt Lochblech");
            assertThat(text).doesNotContain("Treppengeländer Edelstahl mit Glasfüllung");
        }

        @Test
        void eigeneUnterzeileGehtVorDemErsatztext() {
            String text = rechnung("7710.90", verlauf("6430.76", List.of(), null, true, null,
                    new AbschlussZeileAnpassung(null, "Glasfüllung statt Lochblech", null)));

            assertThat(text).contains("Glasfüllung statt Lochblech");
            assertThat(text).doesNotContain("Unterschied zur Auftragsbestätigung");
        }

        @Test
        void ausblendenEntferntNurDieZeileRestBleibtMitDifferenzGerechnet() {
            String text = rechnung("7710.90", nutzerfall(null, new AbschlussZeileAnpassung("Mehrleistung", null, true)));

            assertThat(text).doesNotContain("Zusätzliche Leistungen", "Mehrleistung", "1.523,37",
                    "Treppengeländer Edelstahl mit Glasfüllung");
            // Rest bleibt 0,00 € und damit wie bisher ausgeblendet; Zahlbetrag und Balken unverändert.
            assertThat(text).doesNotContain("Noch offen");
            assertThat(text).contains("diese Rechnung", "Zahlbetrag 9.175,97 €", "100 % des Auftrags");
        }

        @Test
        void schlussrechnungMitAusgeblendeterDifferenzZeigtWeiterNochOffenNull() {
            List<AbrechnungspositionPdfDto> vorher = List.of(new AbrechnungspositionPdfDto(
                    "RE-2026/03/00011", "TEILRECHNUNG", LocalDate.of(2026, 3, 1), new BigDecimal("4000"), null));
            String text = rendere("Schlussrechnung", false, "8000",
                    verlauf("10000", vorher, "Gerüststellung", false, null,
                            new AbschlussZeileAnpassung(null, null, true)), null);

            assertThat(text).doesNotContain("Nicht angefallen", "Gerüststellung", "2.380,00");
            assertThat(text).contains("Noch offen", "0,00 €", "Zahlbetrag 4.760,00 €");
        }

        @Test
        void ohneDifferenzHatDieAnpassungKeineWirkung() {
            String text = rechnung("6430.76", nutzerfall(null, new AbschlussZeileAnpassung("Mehrleistung", "x", false)));

            assertThat(text).doesNotContain("Mehrleistung", "Zusätzliche Leistungen", "Nicht angefallen");
        }

        @Test
        void nichtAbschliessendeRechnungIgnoriertDieAnpassung() {
            String text = rechnung("6000", verlauf("6430.76", List.of(), null, false, null,
                    new AbschlussZeileAnpassung("Mehrleistung", null, false)));

            assertThat(text).doesNotContain("Mehrleistung");
            assertThat(text).contains("Noch offen");
        }
    }
}
