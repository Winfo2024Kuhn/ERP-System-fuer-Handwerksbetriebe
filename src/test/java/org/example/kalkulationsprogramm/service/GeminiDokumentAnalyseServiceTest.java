package org.example.kalkulationsprogramm.service;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.PreisQuelle;
import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdDaten;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class GeminiDokumentAnalyseServiceTest {

    @Mock private ObjectMapper objectMapper;
    @Mock private LieferantenRepository lieferantenRepository;
    @Mock private LieferantDokumentRepository dokumentRepository;
    @Mock private ZugferdExtractorService zugferdExtractorService;
    @Mock private LieferantGeschaeftsdokumentRepository lieferantGeschaeftsdokumentRepository;
    @Mock private SystemSettingsService systemSettingsService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private GeminiDokumentAnalyseService service;

    @BeforeEach
    void setUp() {
        service = new GeminiDokumentAnalyseService(
                objectMapper,
                lieferantenRepository,
                dokumentRepository,
                zugferdExtractorService,
                lieferantGeschaeftsdokumentRepository,
                systemSettingsService,
                eventPublisher
        );
    }

    @Nested
    class ZugferdDokumentTypErkennung {

        private ZugferdDaten createZugferdDaten(String geschaeftsdokumentart) {
            ZugferdDaten daten = new ZugferdDaten();
            daten.setRechnungsnummer("RE-2025-001");
            daten.setBetrag(new BigDecimal("119.00"));
            daten.setRechnungsdatum(LocalDate.of(2025, 3, 1));
            daten.setGeschaeftsdokumentart(geschaeftsdokumentart);
            return daten;
        }

        @Test
        void setzt_RECHNUNG_typ_bei_zugferd_rechnung() throws Exception {
            ZugferdDaten daten = createZugferdDaten("Rechnung");
            when(zugferdExtractorService.extract(anyString(), anyString())).thenReturn(daten);

            LieferantDokument dokument = new LieferantDokument();
            dokument.setTyp(LieferantDokumentTyp.SONSTIG);

            LieferantGeschaeftsdokument result = invokeZugferdExtraktion(
                    Path.of("test.pdf"), "rechnung.pdf", dokument);

            assertThat(result).isNotNull();
            assertThat(result.getDetectedTyp()).isEqualTo(LieferantDokumentTyp.RECHNUNG);
            assertThat(dokument.getTyp()).isEqualTo(LieferantDokumentTyp.RECHNUNG);
            assertThat(result.getDatenquelle()).isEqualTo("ZUGFERD");
            assertThat(result.getVerifiziert()).isTrue();
        }

        @Test
        void setzt_GUTSCHRIFT_typ_bei_zugferd_gutschrift() throws Exception {
            ZugferdDaten daten = createZugferdDaten("Gutschrift");
            when(zugferdExtractorService.extract(anyString(), anyString())).thenReturn(daten);

            LieferantDokument dokument = new LieferantDokument();
            dokument.setTyp(LieferantDokumentTyp.SONSTIG);

            LieferantGeschaeftsdokument result = invokeZugferdExtraktion(
                    Path.of("test.pdf"), "gutschrift.pdf", dokument);

            assertThat(result).isNotNull();
            assertThat(result.getDetectedTyp()).isEqualTo(LieferantDokumentTyp.GUTSCHRIFT);
            assertThat(dokument.getTyp()).isEqualTo(LieferantDokumentTyp.GUTSCHRIFT);
        }

        @Test
        void setzt_AUFTRAGSBESTAETIGUNG_typ_bei_zugferd() throws Exception {
            ZugferdDaten daten = createZugferdDaten("Auftragsbestätigung");
            when(zugferdExtractorService.extract(anyString(), anyString())).thenReturn(daten);

            LieferantDokument dokument = new LieferantDokument();
            dokument.setTyp(null);

            LieferantGeschaeftsdokument result = invokeZugferdExtraktion(
                    Path.of("test.pdf"), "ab_2025.pdf", dokument);

            assertThat(result).isNotNull();
            assertThat(result.getDetectedTyp()).isEqualTo(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG);
            assertThat(dokument.getTyp()).isEqualTo(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG);
        }

        @Test
        void setzt_ANGEBOT_typ_bei_zugferd() throws Exception {
            ZugferdDaten daten = createZugferdDaten("Angebot");
            when(zugferdExtractorService.extract(anyString(), anyString())).thenReturn(daten);

            LieferantDokument dokument = new LieferantDokument();
            dokument.setTyp(LieferantDokumentTyp.SONSTIG);

            LieferantGeschaeftsdokument result = invokeZugferdExtraktion(
                    Path.of("test.pdf"), "angebot.pdf", dokument);

            assertThat(result).isNotNull();
            assertThat(result.getDetectedTyp()).isEqualTo(LieferantDokumentTyp.ANGEBOT);
            assertThat(dokument.getTyp()).isEqualTo(LieferantDokumentTyp.ANGEBOT);
        }

        @Test
        void setzt_LIEFERSCHEIN_typ_bei_zugferd() throws Exception {
            ZugferdDaten daten = createZugferdDaten("Lieferschein");
            when(zugferdExtractorService.extract(anyString(), anyString())).thenReturn(daten);

            LieferantDokument dokument = new LieferantDokument();
            dokument.setTyp(null);

            LieferantGeschaeftsdokument result = invokeZugferdExtraktion(
                    Path.of("test.pdf"), "lieferschein.pdf", dokument);

            assertThat(result).isNotNull();
            assertThat(result.getDetectedTyp()).isEqualTo(LieferantDokumentTyp.LIEFERSCHEIN);
            assertThat(dokument.getTyp()).isEqualTo(LieferantDokumentTyp.LIEFERSCHEIN);
        }

        @Test
        void ueberschreibt_SONSTIG_mit_erkanntem_typ() throws Exception {
            ZugferdDaten daten = createZugferdDaten("Rechnung");
            when(zugferdExtractorService.extract(anyString(), anyString())).thenReturn(daten);

            LieferantDokument dokument = new LieferantDokument();
            dokument.setTyp(LieferantDokumentTyp.SONSTIG);

            LieferantGeschaeftsdokument result = invokeZugferdExtraktion(
                    Path.of("test.pdf"), "dokument.pdf", dokument);

            assertThat(dokument.getTyp()).isEqualTo(LieferantDokumentTyp.RECHNUNG);
        }

        @Test
        void ueberschreibt_nicht_wenn_typ_bereits_gesetzt() throws Exception {
            ZugferdDaten daten = createZugferdDaten("Rechnung");
            when(zugferdExtractorService.extract(anyString(), anyString())).thenReturn(daten);

            LieferantDokument dokument = new LieferantDokument();
            dokument.setTyp(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG);

            LieferantGeschaeftsdokument result = invokeZugferdExtraktion(
                    Path.of("test.pdf"), "re.pdf", dokument);

            // Typ bleibt AUFTRAGSBESTAETIGUNG, wird nicht überschrieben
            assertThat(dokument.getTyp()).isEqualTo(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG);
            // Aber detectedTyp wird gesetzt
            assertThat(result.getDetectedTyp()).isEqualTo(LieferantDokumentTyp.RECHNUNG);
        }

        @Test
        void setzt_datenquelle_ZUGFERD() throws Exception {
            ZugferdDaten daten = createZugferdDaten("Rechnung");
            when(zugferdExtractorService.extract(anyString(), anyString())).thenReturn(daten);

            LieferantGeschaeftsdokument result = invokeZugferdExtraktion(
                    Path.of("test.pdf"), "re.pdf", null);

            assertThat(result.getDatenquelle()).isEqualTo("ZUGFERD");
        }

        @Test
        void setzt_verifiziert_true() throws Exception {
            ZugferdDaten daten = createZugferdDaten("Rechnung");
            when(zugferdExtractorService.extract(anyString(), anyString())).thenReturn(daten);

            LieferantGeschaeftsdokument result = invokeZugferdExtraktion(
                    Path.of("test.pdf"), "re.pdf", null);

            assertThat(result.getVerifiziert()).isTrue();
        }

        @Test
        void setzt_confidence_1_0() throws Exception {
            ZugferdDaten daten = createZugferdDaten("Rechnung");
            when(zugferdExtractorService.extract(anyString(), anyString())).thenReturn(daten);

            LieferantGeschaeftsdokument result = invokeZugferdExtraktion(
                    Path.of("test.pdf"), "re.pdf", null);

            assertThat(result.getAiConfidence()).isEqualTo(1.0);
        }

        @Test
        void gibt_null_zurueck_wenn_keine_daten() throws Exception {
            ZugferdDaten daten = new ZugferdDaten(); // Alles null
            when(zugferdExtractorService.extract(anyString(), anyString())).thenReturn(daten);

            LieferantGeschaeftsdokument result = invokeZugferdExtraktion(
                    Path.of("test.pdf"), "test.pdf", null);

            assertThat(result).isNull();
        }

        @Test
        void uebertraegt_skonto_daten() throws Exception {
            ZugferdDaten daten = createZugferdDaten("Rechnung");
            daten.setSkontoTage(14);
            daten.setSkontoProzent(new BigDecimal("2.0"));
            daten.setNettoTage(30);
            when(zugferdExtractorService.extract(anyString(), anyString())).thenReturn(daten);

            LieferantGeschaeftsdokument result = invokeZugferdExtraktion(
                    Path.of("test.pdf"), "re.pdf", null);

            assertThat(result.getSkontoTage()).isEqualTo(14);
            assertThat(result.getSkontoProzent()).isEqualByComparingTo(new BigDecimal("2.0"));
            assertThat(result.getNettoTage()).isEqualTo(30);
        }

        @Test
        void uebertraegt_bestellnummer_und_referenz() throws Exception {
            ZugferdDaten daten = createZugferdDaten("Rechnung");
            daten.setBestellnummer("BEST-001");
            daten.setReferenzNummer("AB-2025-042");
            when(zugferdExtractorService.extract(anyString(), anyString())).thenReturn(daten);

            LieferantGeschaeftsdokument result = invokeZugferdExtraktion(
                    Path.of("test.pdf"), "re.pdf", null);

            assertThat(result.getBestellnummer()).isEqualTo("BEST-001");
            assertThat(result.getReferenzNummer()).isEqualTo("AB-2025-042");
        }
    }

    @Nested
    class XmlDokumentTypErkennung {

        @Test
        void setzt_RECHNUNG_typ_bei_CrossIndustryInvoice_xml() throws Exception {
            String xmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rsm:CrossIndustryInvoice>
                    <rsm:ExchangedDocument>
                        <ram:ID>RE-2025-001</ram:ID>
                        <ram:TypeCode>380</ram:TypeCode>
                    </rsm:ExchangedDocument>
                    <rsm:SupplyChainTradeTransaction>
                        <ram:ApplicableHeaderTradeSettlement>
                            <ram:SpecifiedTradeSettlementHeaderMonetarySummation>
                                <ram:GrandTotalAmount>119.00</ram:GrandTotalAmount>
                            </ram:SpecifiedTradeSettlementHeaderMonetarySummation>
                        </ram:ApplicableHeaderTradeSettlement>
                    </rsm:SupplyChainTradeTransaction>
                </rsm:CrossIndustryInvoice>
                """;

            Path tempFile = Files.createTempFile("test_rechnung", ".xml");
            try {
                Files.writeString(tempFile, xmlContent);

                LieferantDokument dokument = new LieferantDokument();
                dokument.setTyp(LieferantDokumentTyp.SONSTIG);

                LieferantGeschaeftsdokument result = invokeXmlExtraktion(tempFile, dokument);

                assertThat(result).isNotNull();
                assertThat(result.getDokumentNummer()).isEqualTo("RE-2025-001");
                assertThat(result.getBetragBrutto()).isEqualByComparingTo(new BigDecimal("119.00"));
                assertThat(result.getDatenquelle()).isEqualTo("XML");
                assertThat(result.getDetectedTyp()).isEqualTo(LieferantDokumentTyp.RECHNUNG);
                assertThat(dokument.getTyp()).isEqualTo(LieferantDokumentTyp.RECHNUNG);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }

        @Test
        void setzt_GUTSCHRIFT_bei_TypeCode_381() throws Exception {
            String xmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rsm:CrossIndustryInvoice>
                    <rsm:ExchangedDocument>
                        <ram:ID>GS-2025-001</ram:ID>
                        <ram:TypeCode>381</ram:TypeCode>
                    </rsm:ExchangedDocument>
                    <rsm:SupplyChainTradeTransaction>
                        <ram:ApplicableHeaderTradeSettlement>
                            <ram:SpecifiedTradeSettlementHeaderMonetarySummation>
                                <ram:GrandTotalAmount>50.00</ram:GrandTotalAmount>
                            </ram:SpecifiedTradeSettlementHeaderMonetarySummation>
                        </ram:ApplicableHeaderTradeSettlement>
                    </rsm:SupplyChainTradeTransaction>
                </rsm:CrossIndustryInvoice>
                """;

            Path tempFile = Files.createTempFile("test_gutschrift", ".xml");
            try {
                Files.writeString(tempFile, xmlContent);

                LieferantDokument dokument = new LieferantDokument();
                dokument.setTyp(null);

                LieferantGeschaeftsdokument result = invokeXmlExtraktion(tempFile, dokument);

                assertThat(result).isNotNull();
                assertThat(result.getDetectedTyp()).isEqualTo(LieferantDokumentTyp.GUTSCHRIFT);
                assertThat(dokument.getTyp()).isEqualTo(LieferantDokumentTyp.GUTSCHRIFT);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }

        @Test
        void setzt_GUTSCHRIFT_bei_CreditNote_tag() throws Exception {
            String xmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Invoice>
                    <CreditNote>true</CreditNote>
                    <ID>GS-2025-002</ID>
                    <GrandTotalAmount>75.00</GrandTotalAmount>
                </Invoice>
                """;

            Path tempFile = Files.createTempFile("test_credit", ".xml");
            try {
                Files.writeString(tempFile, xmlContent);

                LieferantDokument dokument = new LieferantDokument();
                dokument.setTyp(LieferantDokumentTyp.SONSTIG);

                LieferantGeschaeftsdokument result = invokeXmlExtraktion(tempFile, dokument);

                assertThat(result).isNotNull();
                assertThat(result.getDetectedTyp()).isEqualTo(LieferantDokumentTyp.GUTSCHRIFT);
                assertThat(dokument.getTyp()).isEqualTo(LieferantDokumentTyp.GUTSCHRIFT);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }

        @Test
        void ueberschreibt_SONSTIG_mit_erkanntem_xml_typ() throws Exception {
            String xmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <CrossIndustryInvoice>
                    <ID>RE-2025-099</ID>
                    <PayableAmount>200.00</PayableAmount>
                </CrossIndustryInvoice>
                """;

            Path tempFile = Files.createTempFile("test", ".xml");
            try {
                Files.writeString(tempFile, xmlContent);

                LieferantDokument dokument = new LieferantDokument();
                dokument.setTyp(LieferantDokumentTyp.SONSTIG);

                LieferantGeschaeftsdokument result = invokeXmlExtraktion(tempFile, dokument);

                assertThat(result).isNotNull();
                // SONSTIG wird überschrieben
                assertThat(dokument.getTyp()).isEqualTo(LieferantDokumentTyp.RECHNUNG);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }

        @Test
        void gibt_null_bei_nicht_invoice_xml() throws Exception {
            String xmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <ProductCatalog>
                    <Product>
                        <Name>Schraube M8</Name>
                    </Product>
                </ProductCatalog>
                """;

            Path tempFile = Files.createTempFile("test_catalog", ".xml");
            try {
                Files.writeString(tempFile, xmlContent);

                LieferantDokument dokument = new LieferantDokument();

                LieferantGeschaeftsdokument result = invokeXmlExtraktion(tempFile, dokument);

                assertThat(result).isNull();
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }

        @Test
        void setzt_datenquelle_XML() throws Exception {
            String xmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Invoice>
                    <ID>RE-001</ID>
                    <GrandTotalAmount>100.00</GrandTotalAmount>
                </Invoice>
                """;

            Path tempFile = Files.createTempFile("test", ".xml");
            try {
                Files.writeString(tempFile, xmlContent);

                LieferantGeschaeftsdokument result = invokeXmlExtraktion(tempFile, null);

                assertThat(result).isNotNull();
                assertThat(result.getDatenquelle()).isEqualTo("XML");
                assertThat(result.getAiConfidence()).isEqualTo(1.0);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }

        @Test
        void parst_datum_im_basic_iso_format() throws Exception {
            String xmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <CrossIndustryInvoice>
                    <ID>RE-001</ID>
                    <ram:DateTimeString>20250315</ram:DateTimeString>
                    <GrandTotalAmount>100.00</GrandTotalAmount>
                </CrossIndustryInvoice>
                """;

            Path tempFile = Files.createTempFile("test", ".xml");
            try {
                Files.writeString(tempFile, xmlContent);

                LieferantGeschaeftsdokument result = invokeXmlExtraktion(tempFile, null);

                assertThat(result).isNotNull();
                assertThat(result.getDokumentDatum()).isEqualTo(LocalDate.of(2025, 3, 15));
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }

        /**
         * Regression: UBL-XRechnung führt Beträge mit currencyID-Attribut
         * (z.B. {@code <cbc:PayableAmount currencyID="EUR">}). Vorher matchte das
         * Regex nur attributlose Tags -> Netto/Brutto blieben leer (0,00 € in den
         * Offenen Posten). Netto, Brutto, MwSt und Fälligkeit müssen extrahiert werden.
         */
        @Test
        void liest_betraege_aus_ubl_xrechnung_mit_currencyId_attribut() throws Exception {
            String xmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <ubl:Invoice xmlns:cbc="urn:cbc" xmlns:cac="urn:cac" xmlns:ubl="urn:ubl">
                    <cbc:ID>2026-0814</cbc:ID>
                    <cbc:IssueDate>2026-05-29</cbc:IssueDate>
                    <cbc:DueDate>2026-06-12</cbc:DueDate>
                    <cac:TaxTotal>
                        <cbc:TaxAmount currencyID="EUR">138.47</cbc:TaxAmount>
                        <cac:TaxSubtotal>
                            <cbc:TaxableAmount currencyID="EUR">728.80</cbc:TaxableAmount>
                            <cac:TaxCategory>
                                <cbc:Percent>19.00</cbc:Percent>
                            </cac:TaxCategory>
                        </cac:TaxSubtotal>
                    </cac:TaxTotal>
                    <cac:LegalMonetaryTotal>
                        <cbc:TaxExclusiveAmount currencyID="EUR">728.80</cbc:TaxExclusiveAmount>
                        <cbc:TaxInclusiveAmount currencyID="EUR">867.27</cbc:TaxInclusiveAmount>
                        <cbc:PayableAmount currencyID="EUR">867.27</cbc:PayableAmount>
                    </cac:LegalMonetaryTotal>
                </ubl:Invoice>
                """;

            Path tempFile = Files.createTempFile("test_ubl", ".xml");
            try {
                Files.writeString(tempFile, xmlContent);

                LieferantGeschaeftsdokument result = invokeXmlExtraktion(tempFile, null);

                assertThat(result).isNotNull();
                assertThat(result.getDokumentNummer()).isEqualTo("2026-0814");
                assertThat(result.getBetragBrutto()).isEqualByComparingTo(new BigDecimal("867.27"));
                assertThat(result.getBetragNetto()).isEqualByComparingTo(new BigDecimal("728.80"));
                assertThat(result.getMwstSatz()).isEqualByComparingTo(new BigDecimal("0.19"));
                assertThat(result.getDokumentDatum()).isEqualTo(LocalDate.of(2026, 5, 29));
                assertThat(result.getZahlungsziel()).isEqualTo(LocalDate.of(2026, 6, 12));
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }
    }

    @Nested
    class ZahlungsartParsing {

        @Test
        void parseJsonToAnalyzeResponse_normalisiert_sepa_lastschrift() throws Exception {
            String json = """
                    {
                      "dokumentTyp": "RECHNUNG",
                      "dokumentNummer": "RE-2025-001",
                      "dokumentDatum": "2025-03-15",
                      "betragBrutto": 119.00,
                      "bereitsGezahlt": true,
                      "zahlungsart": "Lastschrift"
                    }
                    """;

            com.fasterxml.jackson.databind.ObjectMapper realMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            when(objectMapper.readTree(json)).thenReturn(realMapper.readTree(json));

            var result = invokeParseJsonToAnalyzeResponse(json);

            assertThat(result).isNotNull();
            assertThat(result.getBereitsGezahlt()).isTrue();
            assertThat(result.getZahlungsart()).isEqualTo("SEPA_LASTSCHRIFT");
        }

        @Test
        void mapJsonToData_setzt_sepa_lastschrift_und_bezahlt_flag() throws Exception {
            String json = """
                    {
                      "istGeschaeftsdokument": true,
                      "dokumentTyp": "RECHNUNG",
                      "dokumentNummer": "RE-2025-002",
                      "dokumentDatum": "2025-03-16",
                      "betragBrutto": 200.00,
                      "bereitsGezahlt": true,
                      "zahlungsart": "SEPA Direct Debit"
                    }
                    """;

            com.fasterxml.jackson.databind.ObjectMapper realMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            when(objectMapper.readTree(json)).thenReturn(realMapper.readTree(json));

            LieferantGeschaeftsdokument result = invokeMapJsonToData(json);

            assertThat(result).isNotNull();
            assertThat(result.getBereitsGezahlt()).isTrue();
            assertThat(result.getBezahlt()).isTrue();
            assertThat(result.getZahlungsart()).isEqualTo("SEPA_LASTSCHRIFT");
        }
    }

    @Nested
    class ZusammenstellungKlassifizierung {

        @Test
        @org.junit.jupiter.api.DisplayName("E-ZUSAMMENSTELLUNG: mapJsonToData gibt null zurück wenn KI SONSTIG zurückgibt")
        void dkv_zusammenstellung_sonstig_ergibt_null() throws Exception {
            // Simuliert, was das KI-Modell nach dem Prompt-Fix für eine
            // DKV-E-ZUSAMMENSTELLUNG zurückgeben soll: SONSTIG + istGeschaeftsdokument=false.
            String json = """
                    {
                      "dokumentTyp": "SONSTIG",
                      "istGeschaeftsdokument": false,
                      "dokumentNummer": null,
                      "betragBrutto": 147.45,
                      "zahlungsziel": "2026-06-25",
                      "confidence": 0.0
                    }
                    """;

            com.fasterxml.jackson.databind.ObjectMapper realMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            when(objectMapper.readTree(json)).thenReturn(realMapper.readTree(json));

            LieferantGeschaeftsdokument result = invokeMapJsonToData(json);

            assertThat(result).isNull();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("E-ZUSAMMENSTELLUNG: mapJsonToData gibt null zurück wenn istGeschaeftsdokument=false")
        void istGeschaeftsdokument_false_ergibt_null() throws Exception {
            String json = """
                    {
                      "dokumentTyp": "RECHNUNG",
                      "istGeschaeftsdokument": false,
                      "dokumentNummer": "26/652639533/001",
                      "betragBrutto": 147.45,
                      "confidence": 0.9
                    }
                    """;

            com.fasterxml.jackson.databind.ObjectMapper realMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            when(objectMapper.readTree(json)).thenReturn(realMapper.readTree(json));

            LieferantGeschaeftsdokument result = invokeMapJsonToData(json);

            assertThat(result).isNull();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("SONSTIG-Typ (bei istGeschaeftsdokument=true) ergibt ebenfalls null")
        void sonstig_typ_ohne_istGeschaeftsdokument_flag_ergibt_null() throws Exception {
            // Testet den SONSTIG-Branch in mapJsonToData (Z. 2222–2226) separat:
            // auch wenn istGeschaeftsdokument fehlt/true, muss SONSTIG → null liefern.
            String json = """
                    {
                      "dokumentTyp": "SONSTIG",
                      "istGeschaeftsdokument": true,
                      "dokumentNummer": "SONSTIG-001",
                      "betragBrutto": 50.00,
                      "confidence": 0.3
                    }
                    """;

            com.fasterxml.jackson.databind.ObjectMapper realMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            when(objectMapper.readTree(json)).thenReturn(realMapper.readTree(json));

            LieferantGeschaeftsdokument result = invokeMapJsonToData(json);

            assertThat(result).isNull();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("Echte DKV-E-RECHNUNG mit RECHNUNG-Typ wird korrekt gemappt")
        void dkv_erechnung_rechnung_wird_akzeptiert() throws Exception {
            String json = """
                    {
                      "dokumentTyp": "RECHNUNG",
                      "istGeschaeftsdokument": true,
                      "dokumentNummer": "26/652639533/001",
                      "dokumentDatum": "2026-06-15",
                      "betragBrutto": 147.45,
                      "betragNetto": 123.91,
                      "mwstSatz": 0.19,
                      "zahlungsziel": "2026-06-25",
                      "bereitsGezahlt": true,
                      "zahlungsart": "SEPA_LASTSCHRIFT",
                      "confidence": 0.95
                    }
                    """;

            com.fasterxml.jackson.databind.ObjectMapper realMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            when(objectMapper.readTree(json)).thenReturn(realMapper.readTree(json));

            LieferantGeschaeftsdokument result = invokeMapJsonToData(json);

            assertThat(result).isNotNull();
            assertThat(result.getDetectedTyp()).isEqualTo(LieferantDokumentTyp.RECHNUNG);
            assertThat(result.getDokumentNummer()).isEqualTo("26/652639533/001");
            assertThat(result.getBetragBrutto()).isEqualByComparingTo(new java.math.BigDecimal("147.45"));
        }
    }

    @Nested
    class JsonTruncationHandling {

        // Regression: Gemini kann bei maxOutputTokens-Erreichung eine schlie\u00dfende }
        // anh\u00e4ngen, so dass isJsonTruncated() false-negative liefert, aber Jackson
        // intern noch \"Unexpected end-of-input\" wirft. mapJsonToData soll in diesem Fall
        // null zur\u00fcckgeben (kein unkontrollierter Exception-Throw).
        @Test
        void mapJsonToData_gibt_null_zurueck_bei_jackson_parse_fehler() throws Exception {
            String truncatedJson = "{\"dokumentTyp\": \"RECHNUNG\", \"confidence\": 0.}";
            com.fasterxml.jackson.databind.ObjectMapper realMapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            when(objectMapper.readTree(truncatedJson))
                    .thenThrow(new com.fasterxml.jackson.core.JsonParseException(null,
                            "Unexpected end-of-input within/between Object entries"));

            LieferantGeschaeftsdokument result = invokeMapJsonToData(truncatedJson);

            assertThat(result).isNull();
        }

        @Test
        void isJsonTruncated_erkennt_fehlendes_ende_klammer() throws Exception {
            String truncated = "{\"dokumentTyp\": \"RECHNUNG\", \"betrag\":";
            boolean result = invokeIsJsonTruncated(truncated);
            assertThat(result).isTrue();
        }

        @Test
        void isJsonTruncated_erkennt_gueltiges_json_als_nicht_abgeschnitten() throws Exception {
            String valid = "{\"dokumentTyp\": \"RECHNUNG\", \"betrag\": 119.0}";
            boolean result = invokeIsJsonTruncated(valid);
            assertThat(result).isFalse();
        }
    }

    /**
     * Der KI-Pfad hat lange gar keine Preise aktualisiert - der Aufruf war
     * auskommentiert. Diese Tests halten fest, dass die Positionen jetzt
     * tatsaechlich bei der Preisuebernahme angemeldet werden, und zwar
     * unveraendert.
     *
     * <p>Angemeldet, nicht geschrieben: Die Analyse veroeffentlicht nur noch ein
     * {@link PreisUebernahmeEvent}. Ausgefuehrt wird es erst nach dem Commit -
     * geprueft wird hier also der Inhalt der Meldung.
     */
    @Nested
    class PreisuebernahmeAusKiAnalyse {

        private final ObjectMapper echterMapper = new ObjectMapper();

        @Test
        void reichtStueckpreisPositionenUnveraendertWeiter() throws Exception {
            Lieferanten lieferant = new Lieferanten();
            lieferant.setLieferantenname("Musterlieferant");

            String json = """
                    {"artikelPositionen": [
                      {"externeArtikelnummer": "HLH-42", "einzelpreis": "62,00",
                       "preiseinheit": "Stück", "mengeneinheit": "Stück"}
                    ]}
                    """;

            invokeVerarbeiteArtikelPositionen(json, lieferant, LieferantDokumentTyp.RECHNUNG, null, null);

            PreisUebernahmeEvent event = gemeldetesEvent();
            assertThat(event.lieferant()).isSameAs(lieferant);
            assertThat(event.quelle()).isEqualTo(PreisQuelle.RECHNUNG);
            assertThat(event.positionen()).hasSize(1);
            PreisUebernahmeService.Position position = event.positionen().getFirst();
            assertThat(position.externeArtikelnummer()).isEqualTo("HLH-42");
            // Komma-Dezimaltrennzeichen aus dem deutschen PDF muss ankommen.
            assertThat(position.einzelpreis()).isEqualByComparingTo("62.00");
            assertThat(position.preiseinheit()).isEqualTo("Stück");
        }

        @Test
        void reichtDasBelegdatumWeiter() throws Exception {
            Lieferanten lieferant = new Lieferanten();
            lieferant.setLieferantenname("Musterlieferant");

            String json = """
                    {"artikelPositionen": [{"externeArtikelnummer": "A-1", "einzelpreis": 10.00}]}
                    """;

            invokeVerarbeiteArtikelPositionen(json, lieferant, LieferantDokumentTyp.RECHNUNG,
                    java.time.LocalDate.of(2026, 3, 14), null);

            java.util.Date datum = gemeldetesEvent().dokumentDatum();

            assertThat(datum).isNotNull();
            assertThat(datum.toInstant()
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate())
                    .isEqualTo(java.time.LocalDate.of(2026, 3, 14));
        }

        /**
         * Ohne Belegnummer laesst sich spaeter nicht mehr sagen, aus welcher
         * Rechnung ein Preis stammt.
         */
        @Test
        void reichtDieBelegnummerWeiter() throws Exception {
            Lieferanten lieferant = new Lieferanten();
            lieferant.setLieferantenname("Musterlieferant");

            String json = """
                    {"artikelPositionen": [{"externeArtikelnummer": "A-1", "einzelpreis": 10.00}]}
                    """;

            invokeVerarbeiteArtikelPositionen(json, lieferant, LieferantDokumentTyp.RECHNUNG, null,
                    "RE-2026-0815");

            assertThat(gemeldetesEvent().belegnummer()).isEqualTo("RE-2026-0815");
        }

        @Test
        void meldetFehlendePreiseinheitAlsNullStattSieZuRaten() throws Exception {
            Lieferanten lieferant = new Lieferanten();
            lieferant.setLieferantenname("Musterlieferant");

            String json = """
                    {"artikelPositionen": [{"externeArtikelnummer": "X-1", "einzelpreis": 18.50}]}
                    """;

            invokeVerarbeiteArtikelPositionen(json, lieferant, LieferantDokumentTyp.RECHNUNG, null, null);

            PreisUebernahmeEvent event = gemeldetesEvent();
            assertThat(event.positionen().getFirst().preiseinheit()).isNull();
            assertThat(event.positionen().getFirst().mengeneinheit()).isNull();
        }

        @Test
        void buchtAngebotspositionenAlsAngebotsquelle() throws Exception {
            Lieferanten lieferant = new Lieferanten();
            lieferant.setLieferantenname("Musterlieferant");

            String json = """
                    {"artikelPositionen": [{"externeArtikelnummer": "A-1", "einzelpreis": 10.00}]}
                    """;

            invokeVerarbeiteArtikelPositionen(json, lieferant, LieferantDokumentTyp.ANGEBOT, null, null);

            assertThat(gemeldetesEvent().quelle()).isEqualTo(PreisQuelle.ANGEBOT_EMAIL);
        }

        /**
         * Kataloge, Preislisten und Rechnungszusammenstellungen stuft die KI als
         * SONSTIG ein. Dort stehen zwar Zahlen neben Artikelnummern - in die
         * Kalkulation gehoeren sie aber nicht.
         */
        @Test
        void schreibtKeinePreiseAusNichtGeschaeftsdokumenten() throws Exception {
            Lieferanten lieferant = new Lieferanten();
            lieferant.setLieferantenname("Musterlieferant");

            String json = """
                    {"artikelPositionen": [{"externeArtikelnummer": "KAT-1", "einzelpreis": 99.00}]}
                    """;

            invokeVerarbeiteArtikelPositionen(json, lieferant, LieferantDokumentTyp.SONSTIG, null, null);

            verifyNoInteractions(eventPublisher);
        }

        /**
         * Bei nicht erkanntem Typ - etwa wenn die KI-Antwort nicht parsebar war und
         * nur ein Ersatzobjekt entstand - darf ebenfalls nichts geschrieben werden.
         */
        @Test
        void schreibtKeinePreiseBeiUnerkanntemDokumenttyp() throws Exception {
            Lieferanten lieferant = new Lieferanten();
            lieferant.setLieferantenname("Musterlieferant");

            String json = """
                    {"artikelPositionen": [{"externeArtikelnummer": "A-1", "einzelpreis": 10.00}]}
                    """;

            invokeVerarbeiteArtikelPositionen(json, lieferant, null, null, null);

            verifyNoInteractions(eventPublisher);
        }

        /** Eine Gutschrift nennt den erstatteten Betrag, nicht den Einkaufspreis. */
        @Test
        void schreibtKeinePreiseAusGutschriften() throws Exception {
            Lieferanten lieferant = new Lieferanten();
            lieferant.setLieferantenname("Musterlieferant");

            String json = """
                    {"artikelPositionen": [{"externeArtikelnummer": "A-1", "einzelpreis": 4.00}]}
                    """;

            invokeVerarbeiteArtikelPositionen(json, lieferant, LieferantDokumentTyp.GUTSCHRIFT, null, null);

            verifyNoInteractions(eventPublisher);
        }

        /** Ein Lieferschein fuehrt Listen- statt Nettopreise. */
        @Test
        void schreibtKeinePreiseAusLieferscheinen() throws Exception {
            Lieferanten lieferant = new Lieferanten();
            lieferant.setLieferantenname("Musterlieferant");

            String json = """
                    {"artikelPositionen": [{"externeArtikelnummer": "A-1", "einzelpreis": 10.00}]}
                    """;

            invokeVerarbeiteArtikelPositionen(json, lieferant, LieferantDokumentTyp.LIEFERSCHEIN, null, null);

            verifyNoInteractions(eventPublisher);
        }

        @Test
        void ruehrtOhneLieferantNichtsAn() throws Exception {
            String json = """
                    {"artikelPositionen": [{"externeArtikelnummer": "A-1", "einzelpreis": 10.00}]}
                    """;

            invokeVerarbeiteArtikelPositionen(json, null, LieferantDokumentTyp.RECHNUNG, null, null);

            verifyNoInteractions(eventPublisher);
        }

        @Test
        void kommtMitFehlendenUndUnlesbarenFeldernZurecht() throws Exception {
            Lieferanten lieferant = new Lieferanten();
            lieferant.setLieferantenname("Musterlieferant");

            String json = """
                    {"artikelPositionen": [
                      {"externeArtikelnummer": null, "einzelpreis": "keine Zahl"},
                      {"externeArtikelnummer": "  ", "einzelpreis": 5.00}
                    ]}
                    """;

            invokeVerarbeiteArtikelPositionen(json, lieferant, LieferantDokumentTyp.RECHNUNG, null, null);

            // Aussortiert wird erst im PreisUebernahmeService - hier darf nichts knallen.
            var positionen = gemeldetesEvent().positionen();
            assertThat(positionen).hasSize(2);
            assertThat(positionen.getFirst().externeArtikelnummer()).isNull();
            assertThat(positionen.getFirst().einzelpreis()).isNull();
        }

        @Test
        void ignoriertAntwortOhneArtikelPositionen() throws Exception {
            Lieferanten lieferant = new Lieferanten();
            lieferant.setLieferantenname("Musterlieferant");

            invokeVerarbeiteArtikelPositionen("{\"dokumentTyp\": \"RECHNUNG\"}", lieferant,
                    LieferantDokumentTyp.RECHNUNG, null, null);

            verifyNoInteractions(eventPublisher);
        }

        private void invokeVerarbeiteArtikelPositionen(String json, Lieferanten lieferant,
                LieferantDokumentTyp typ, java.time.LocalDate dokumentDatum, String belegnummer)
                throws Exception {
            Method method = GeminiDokumentAnalyseService.class.getDeclaredMethod(
                    "verarbeiteArtikelPositionen", com.fasterxml.jackson.databind.JsonNode.class,
                    Lieferanten.class, LieferantDokumentTyp.class, java.time.LocalDate.class,
                    String.class);
            method.setAccessible(true);
            method.invoke(service, echterMapper.readTree(json), lieferant, typ, dokumentDatum, belegnummer);
        }

        /** Das einzige veroeffentlichte Event - alles andere waere ein Fehler. */
        private PreisUebernahmeEvent gemeldetesEvent() {
            ArgumentCaptor<PreisUebernahmeEvent> captor = ArgumentCaptor.forClass(PreisUebernahmeEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            return captor.getValue();
        }
    }

    // --- Helper methods to invoke private methods via reflection ---

    private boolean invokeIsJsonTruncated(String json) throws Exception {
        Method method = GeminiDokumentAnalyseService.class.getDeclaredMethod(
                "isJsonTruncated", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, json);
    }

    private LieferantGeschaeftsdokument invokeZugferdExtraktion(Path dateiPfad, String dateiname,
            LieferantDokument dokument) throws Exception {
        Method method = GeminiDokumentAnalyseService.class.getDeclaredMethod(
                "versucheZugferdExtraktion", Path.class, String.class, LieferantDokument.class);
        method.setAccessible(true);
        return (LieferantGeschaeftsdokument) method.invoke(service, dateiPfad, dateiname, dokument);
    }

    private LieferantGeschaeftsdokument invokeXmlExtraktion(Path dateiPfad,
            LieferantDokument dokument) throws Exception {
        Method method = GeminiDokumentAnalyseService.class.getDeclaredMethod(
            "versucheXmlExtraktion", String.class, LieferantDokument.class, String.class);
        method.setAccessible(true);
        return (LieferantGeschaeftsdokument) method.invoke(service, Files.readString(dateiPfad), dokument,
            dateiPfad.getFileName().toString());
        }

        private org.example.kalkulationsprogramm.dto.LieferantDokumentDto.AnalyzeResponse invokeParseJsonToAnalyzeResponse(
            String json) throws Exception {
        Method method = GeminiDokumentAnalyseService.class.getDeclaredMethod(
            "parseJsonToAnalyzeResponse", String.class);
        method.setAccessible(true);
        return (org.example.kalkulationsprogramm.dto.LieferantDokumentDto.AnalyzeResponse) method.invoke(service, json);
        }

        private LieferantGeschaeftsdokument invokeMapJsonToData(String json) throws Exception {
        Method method = GeminiDokumentAnalyseService.class.getDeclaredMethod(
            "mapJsonToData", String.class);
        method.setAccessible(true);
        return (LieferantGeschaeftsdokument) method.invoke(service, json);
    }
}
