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

    /**
     * Rechnungen kommen sowohl als ZUGFeRD-PDF (CII) als auch als reine
     * XRechnung-Datei herein - letztere in beiden Syntaxen. Aus allen dreien muss
     * die Preisuebernahme die Positionen bekommen.
     */
    @Nested
    class ArtikelpositionenAusXml {

        @Test
        void liestCiiPositionenMitTonnenbasis() {
            String xml = """
                    <rsm:CrossIndustryInvoice>
                      <ram:IncludedSupplyChainTradeLineItem>
                        <ram:SpecifiedTradeProduct>
                          <ram:SellerAssignedID>S235-100</ram:SellerAssignedID>
                          <ram:Name>Flachstahl 100x10</ram:Name>
                        </ram:SpecifiedTradeProduct>
                        <ram:SpecifiedLineTradeAgreement>
                          <ram:NetPriceProductTradePrice>
                            <ram:ChargeAmount>1250.00</ram:ChargeAmount>
                            <ram:BasisQuantity unitCode="KGM">1000</ram:BasisQuantity>
                          </ram:NetPriceProductTradePrice>
                        </ram:SpecifiedLineTradeAgreement>
                        <ram:SpecifiedLineTradeDelivery>
                          <ram:BilledQuantity unitCode="KGM">340.00</ram:BilledQuantity>
                        </ram:SpecifiedLineTradeDelivery>
                      </ram:IncludedSupplyChainTradeLineItem>
                    </rsm:CrossIndustryInvoice>
                    """;

            var positionen = service.extractLineItems(xml);

            assertThat(positionen).hasSize(1);
            assertThat(positionen.getFirst().getExterneArtikelnummer()).isEqualTo("S235-100");
            assertThat(positionen.getFirst().getEinzelpreis()).isEqualByComparingTo("1250.00");
            assertThat(positionen.getFirst().getPreiseinheit()).isEqualTo("1000 KGM");
            assertThat(positionen.getFirst().getMengeneinheit()).isEqualTo("KGM");
        }

        @Test
        void liestUblPositionenMitStueckpreis() {
            String xml = """
                    <Invoice>
                      <cac:InvoiceLine>
                        <cbc:ID>1</cbc:ID>
                        <cbc:InvoicedQuantity unitCode="C62">4</cbc:InvoicedQuantity>
                        <cbc:LineExtensionAmount currencyID="EUR">248.00</cbc:LineExtensionAmount>
                        <cac:Item>
                          <cbc:Name>Handlaufhalter</cbc:Name>
                          <cac:SellersItemIdentification>
                            <cbc:ID>HLH-42</cbc:ID>
                          </cac:SellersItemIdentification>
                        </cac:Item>
                        <cac:Price>
                          <cbc:PriceAmount currencyID="EUR">62.00</cbc:PriceAmount>
                          <cbc:BaseQuantity unitCode="C62">1</cbc:BaseQuantity>
                        </cac:Price>
                      </cac:InvoiceLine>
                    </Invoice>
                    """;

            var positionen = service.extractLineItems(xml);

            assertThat(positionen).hasSize(1);
            assertThat(positionen.getFirst().getExterneArtikelnummer()).isEqualTo("HLH-42");
            // Der Einzelpreis, nicht die Positionssumme von 248,00.
            assertThat(positionen.getFirst().getEinzelpreis()).isEqualByComparingTo("62.00");
            assertThat(positionen.getFirst().getPreiseinheit()).isEqualTo("1 C62");
            assertThat(positionen.getFirst().getMengeneinheit()).isEqualTo("C62");
        }

        @Test
        void liestMehrereUblPositionen() {
            String xml = """
                    <Invoice>
                      <cac:InvoiceLine>
                        <cac:Item>
                          <cac:SellersItemIdentification><cbc:ID>A-1</cbc:ID></cac:SellersItemIdentification>
                        </cac:Item>
                        <cac:Price><cbc:PriceAmount>10.00</cbc:PriceAmount></cac:Price>
                      </cac:InvoiceLine>
                      <cac:InvoiceLine>
                        <cac:Item>
                          <cac:SellersItemIdentification><cbc:ID>A-2</cbc:ID></cac:SellersItemIdentification>
                        </cac:Item>
                        <cac:Price><cbc:PriceAmount>20.00</cbc:PriceAmount></cac:Price>
                      </cac:InvoiceLine>
                    </Invoice>
                    """;

            var positionen = service.extractLineItems(xml);

            assertThat(positionen).extracting("externeArtikelnummer").containsExactly("A-1", "A-2");
        }

        @Test
        void ueberspringtPositionenOhneArtikelnummer() {
            String xml = """
                    <Invoice>
                      <cac:InvoiceLine>
                        <cac:Item><cbc:Name>Frachtpauschale</cbc:Name></cac:Item>
                        <cac:Price><cbc:PriceAmount>19.90</cbc:PriceAmount></cac:Price>
                      </cac:InvoiceLine>
                    </Invoice>
                    """;

            assertThat(service.extractLineItems(xml)).isEmpty();
        }

        @Test
        void liestUblGutschriftspositionen() {
            String xml = """
                    <CreditNote>
                      <cac:CreditNoteLine>
                        <cbc:CreditedQuantity unitCode="C62">2</cbc:CreditedQuantity>
                        <cac:Item>
                          <cac:SellersItemIdentification><cbc:ID>RET-7</cbc:ID></cac:SellersItemIdentification>
                        </cac:Item>
                        <cac:Price><cbc:PriceAmount>15.00</cbc:PriceAmount></cac:Price>
                      </cac:CreditNoteLine>
                    </CreditNote>
                    """;

            var positionen = service.extractLineItems(xml);

            assertThat(positionen).hasSize(1);
            assertThat(positionen.getFirst().getExterneArtikelnummer()).isEqualTo("RET-7");
            assertThat(positionen.getFirst().getMengeneinheit()).isEqualTo("C62");
        }

        @Test
        void faelltOhneBasisQuantityAufDieMengeneinheitZurueck() {
            String xml = """
                    <rsm:CrossIndustryInvoice>
                      <ram:IncludedSupplyChainTradeLineItem>
                        <ram:SpecifiedTradeProduct>
                          <ram:SellerAssignedID>S235-400</ram:SellerAssignedID>
                        </ram:SpecifiedTradeProduct>
                        <ram:SpecifiedLineTradeAgreement>
                          <ram:NetPriceProductTradePrice>
                            <ram:ChargeAmount>1250.00</ram:ChargeAmount>
                          </ram:NetPriceProductTradePrice>
                        </ram:SpecifiedLineTradeAgreement>
                        <ram:SpecifiedLineTradeDelivery>
                          <ram:BilledQuantity unitCode="TNE">2.5</ram:BilledQuantity>
                        </ram:SpecifiedLineTradeDelivery>
                      </ram:IncludedSupplyChainTradeLineItem>
                    </rsm:CrossIndustryInvoice>
                    """;

            var positionen = service.extractLineItems(xml);

            assertThat(positionen).hasSize(1);
            assertThat(positionen.getFirst().getPreiseinheit()).isNull();
            // Ohne Preisbasis entscheidet die Mengeneinheit - hier Tonnen.
            assertThat(positionen.getFirst().getMengeneinheit()).isEqualTo("TNE");
        }

        @Test
        void liefertLeereListeOhneXml() {
            assertThat(service.extractLineItems(null)).isEmpty();
            assertThat(service.extractLineItems("<Invoice></Invoice>")).isEmpty();
        }
    }
}
