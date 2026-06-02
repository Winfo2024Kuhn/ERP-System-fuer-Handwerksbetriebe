package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdDaten;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ZugferdExtractorServiceTest {

    private ZugferdExtractorService service;

    @BeforeEach
    void setUp() {
        service = new ZugferdExtractorService();
    }

    @Nested
    class GeschaeftsdokumentartErkennung {

        @Test
        void erkenntAuftragsbestaetigungAusDateiname() {
            ZugferdDaten result = service.extract("nicht_existiert.pdf", "Auftragsbestätigung_2025.pdf");
            assertThat(result.getGeschaeftsdokumentart()).isEqualTo("Auftragsbestätigung");
        }

        @Test
        void erkenntAuftragsbestaetigungOhneUmlaute() {
            ZugferdDaten result = service.extract("nicht_existiert.pdf", "auftragsbestaetigung_123.pdf");
            assertThat(result.getGeschaeftsdokumentart()).isEqualTo("Auftragsbestätigung");
        }

        @Test
        void erkenntAngebotAusDateiname() {
            ZugferdDaten result = service.extract("nicht_existiert.pdf", "Angebot_kunde_2025.pdf");
            assertThat(result.getGeschaeftsdokumentart()).isEqualTo("Angebot");
        }

        @Test
        void erkenntGutschriftAusDateiname() {
            ZugferdDaten result = service.extract("nicht_existiert.pdf", "Gutschrift_2025.pdf");
            assertThat(result.getGeschaeftsdokumentart()).isEqualTo("Gutschrift");
        }

        @Test
        void erkenntLieferscheinAusDateiname() {
            ZugferdDaten result = service.extract("nicht_existiert.pdf", "Lieferschein_2025.pdf");
            assertThat(result.getGeschaeftsdokumentart()).isEqualTo("Lieferschein");
        }

        @Test
        void defaultIstRechnungBeiUnbekanntemDateinamen() {
            ZugferdDaten result = service.extract("nicht_existiert.pdf", "dokument_12345.pdf");
            assertThat(result.getGeschaeftsdokumentart()).isEqualTo("Rechnung");
        }

        @Test
        void defaultIstRechnungBeiNullDateiname() {
            ZugferdDaten result = service.extract("nicht_existiert.pdf", null);
            assertThat(result.getGeschaeftsdokumentart()).isEqualTo("Rechnung");
        }
    }

    @Nested
    class TypeCodeMapping {

        @Test
        void mapptTypeCode380ZuRechnung() {
            assertThat(service.mapTypeCodeToGeschaeftsdokumentart("380")).isEqualTo("Rechnung");
        }

        @Test
        void mapptTypeCode384ZuRechnung() {
            // 384 = Korrigierte Rechnung
            assertThat(service.mapTypeCodeToGeschaeftsdokumentart("384")).isEqualTo("Rechnung");
        }

        @Test
        void mapptTypeCode389ZuRechnung() {
            // 389 = Eigenrechnung (Self-billed invoice)
            assertThat(service.mapTypeCodeToGeschaeftsdokumentart("389")).isEqualTo("Rechnung");
        }

        @Test
        void mapptTypeCode381ZuGutschrift() {
            assertThat(service.mapTypeCodeToGeschaeftsdokumentart("381")).isEqualTo("Gutschrift");
        }

        @Test
        void mapptTypeCode351ZuAngebot() {
            assertThat(service.mapTypeCodeToGeschaeftsdokumentart("351")).isEqualTo("Angebot");
        }

        @Test
        void mapptTypeCode231ZuAuftragsbestaetigung() {
            assertThat(service.mapTypeCodeToGeschaeftsdokumentart("231")).isEqualTo("Auftragsbestätigung");
        }

        @Test
        void mapptTypeCode261ZuLieferschein() {
            assertThat(service.mapTypeCodeToGeschaeftsdokumentart("261")).isEqualTo("Lieferschein");
        }

        @Test
        void mapptTypeCode270ZuLieferschein() {
            assertThat(service.mapTypeCodeToGeschaeftsdokumentart("270")).isEqualTo("Lieferschein");
        }

        @Test
        void gibtNullBeiUnbekanntemTypeCode() {
            assertThat(service.mapTypeCodeToGeschaeftsdokumentart("999")).isNull();
        }

        @Test
        void gibtNullBeiNullTypeCode() {
            assertThat(service.mapTypeCodeToGeschaeftsdokumentart(null)).isNull();
        }

        @Test
        void trimtWhitespace() {
            assertThat(service.mapTypeCodeToGeschaeftsdokumentart(" 380 ")).isEqualTo("Rechnung");
        }
    }

    @Nested
    class BereitsBezahlteRechnungOhneFaelligkeitsdatum {

        /**
         * Erzeugt eine echte ZUGFeRD-PDF einer bereits bezahlten Rechnung
         * (= ohne Fälligkeitsdatum), wie sie z.B. Amazon ausstellt.
         * Nur Dummy-Daten (Max Mustermann).
         */
        private java.nio.file.Path erzeugeBezahlteRechnungOhneFaelligkeit() throws Exception {
            ZugferdDaten daten = new ZugferdDaten();
            daten.setRechnungsnummer("RE-2026-TEST");
            daten.setRechnungsdatum(java.time.LocalDate.of(2026, 6, 2));
            daten.setKundenName("Max Mustermann");
            daten.setBetrag(new java.math.BigDecimal("119.00"));
            daten.setGeschaeftsdokumentart("Rechnung");
            // KEIN Fälligkeitsdatum -> im XML fehlt DueDateDateTime (bereits bezahlt)

            return new ZugferdErstellService().erzeuge(erzeugeBasisPdf().toString(), daten);
        }

        /**
         * Erzeugt eine Basis-PDF mit Text-Inhalt (Seite besitzt /Resources,
         * sonst scheitert Mustangs A3-Exporter mit "res is null").
         */
        private java.nio.file.Path erzeugeBasisPdf() throws Exception {
            java.nio.file.Path basis = java.nio.file.Files.createTempFile("basis-", ".pdf");
            try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
                org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage(
                        org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
                doc.addPage(page);
                try (org.apache.pdfbox.pdmodel.PDPageContentStream cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(
                        doc, page)) {
                    cs.beginText();
                    cs.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(
                            org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(50, 750);
                    cs.showText("Rechnung Max Mustermann");
                    cs.endText();
                }
                doc.save(basis.toFile());
            }
            return basis;
        }

        /**
         * Erzeugt eine vorausbezahlte ZUGFeRD-PDF (TotalPrepaidAmount = Bruttobetrag),
         * wie sie Amazon ausstellt. Baut die Mustang-Rechnung direkt, um den
         * Prepaid-Betrag setzen zu können (der ErstellService kann das nicht).
         * Nur Dummy-Daten (Max Mustermann).
         */
        private java.nio.file.Path erzeugeVorausbezahlteRechnung() throws Exception {
            java.nio.file.Path ziel = java.nio.file.Files.createTempFile("zugferd-paid-", ".pdf");
            java.math.BigDecimal brutto = new java.math.BigDecimal("119.00");

            try (org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromA3 exporter =
                    new org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromA3()
                            .setCreator("Test").setProducer("Test")
                            .load(erzeugeBasisPdf().toString())) {

                org.mustangproject.TradeParty seller = new org.mustangproject.TradeParty();
                seller.setName("Bauschlosserei Kuhn");
                org.mustangproject.TradeParty buyer = new org.mustangproject.TradeParty();
                buyer.setName("Max Mustermann");

                org.mustangproject.Product product = new org.mustangproject.Product();
                product.setName("Rechnung");
                product.setVATPercent(new java.math.BigDecimal("19"));

                org.mustangproject.Invoice invoice = new org.mustangproject.Invoice();
                invoice.setNumber("RE-2026-PAID");
                invoice.setIssueDate(java.util.Date.from(java.time.LocalDate.of(2026, 6, 2)
                        .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()));
                invoice.setSender(seller);
                invoice.setRecipient(buyer);
                invoice.addItem(new org.mustangproject.Item(product, java.math.BigDecimal.ONE,
                        new java.math.BigDecimal("100.00")));
                // Komplett vorausbezahlt -> DuePayableAmount = 0
                invoice.setTotalPrepaidAmount(brutto);

                exporter.setTransaction(invoice);
                exporter.export(ziel.toString());
            }
            return ziel;
        }

        @Test
        void liestBetragTrotzFehlendemFaelligkeitsdatum() throws Exception {
            java.nio.file.Path pdf = erzeugeBezahlteRechnungOhneFaelligkeit();

            ZugferdDaten result = service.extract(pdf.toString(), "rechnung.pdf");

            // Regression: getDueDate() warf eine NPE und brach die gesamte
            // Extraktion ab -> Betrag/Netto blieben null (Symptom: Spalten "–").
            assertThat(result.getRechnungsnummer()).isEqualTo("RE-2026-TEST");
            assertThat(result.getFaelligkeitsdatum()).isNull();
            assertThat(result.getBetrag()).isNotNull();
            assertThat(result.getBetrag()).isPositive();
            assertThat(result.getBetragNetto()).isNotNull();
        }

        @Test
        void erkenntVorausbezahlteRechnungAlsBereitsGezahlt() throws Exception {
            java.nio.file.Path pdf = erzeugeVorausbezahlteRechnung();

            ZugferdDaten result = service.extract(pdf.toString(), "amazon.pdf");

            // Vorauszahlung deckt den Bruttobetrag -> bereits bezahlt,
            // soll nicht mehr in den Offenen Posten erscheinen.
            assertThat(result.getBetrag()).isNotNull();
            assertThat(result.getBereitsGezahlt()).isTrue();
        }
    }

    @Nested
    class FallbackBeiUngueltigemPdf {

        @Test
        void gibtDatenMitDokumentartZurueckWennPdfNichtExistiert() {
            ZugferdDaten result = service.extract("pfad/der/nicht/existiert.pdf", "rechnung_123.pdf");

            assertThat(result).isNotNull();
            assertThat(result.getGeschaeftsdokumentart()).isEqualTo("Rechnung");
            // Keine ZUGFeRD-Daten extrahiert
            assertThat(result.getRechnungsnummer()).isNull();
            assertThat(result.getBetrag()).isNull();
        }

        @Test
        void liefertLeereArtikelpositionenBeiUngueltigemPdf() {
            ZugferdDaten result = service.extract("nicht_existiert.pdf", "anfrage_xyz.pdf");

            assertThat(result.getArtikelPositionen()).isNotNull().isEmpty();
        }
    }
}
