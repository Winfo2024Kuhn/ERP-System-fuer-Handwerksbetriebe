package org.example.kalkulationsprogramm.service;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    @Mock private org.example.kalkulationsprogramm.repository.LieferantDokumentVerknuepfungSperreRepository sperreRepository;
    @Mock private LieferantDokumentPositionService positionService;

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
                eventPublisher,
                new LieferantDokumentAbgleich(new ObjectMapper()),
                sperreRepository,
                positionService,
                new LieferantDokumentPositionLeser(new ObjectMapper()),
                new org.springframework.transaction.support.TransactionTemplate(
                        mock(org.springframework.transaction.PlatformTransactionManager.class))
        );
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(nullValues = "null", value = {
            "ANGEBOT, ANGEBOT",
            "ANGEBOT (Kopie), ANGEBOT",
            "auftragsbestaetigung (kopie), AUFTRAGSBESTAETIGUNG",
            "LIEFERSCHEIN (Kopie), LIEFERSCHEIN",
            "WERKSTOFFZEUGNIS, WERKSTOFFZEUGNIS",
            "Werkstoffzeugnis (Kopie), WERKSTOFFZEUGNIS",
            "RECHNUNG, RECHNUNG",
            "RECHNUNG (Kopie), null",
            "GUTSCHRIFT (Kopie), null",
            "SONSTIG (Kopie), null",
            "QUITTUNG, null",
            "null, null"
    })
    void typAusKiAntwortBeiKopien(String roh, LieferantDokumentTyp erwartet) {
        assertThat(GeminiDokumentAnalyseService.typAusKiAntwort(roh)).isEqualTo(erwartet);
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

    /**
     * Die Tests oben belegen nur, dass die private Mapper-Methode
     * {@code verarbeiteArtikelPositionen} bei passenden Eingaben ein Event
     * baut. Was noch fehlte: der Beweis, dass die oeffentliche Einstiegsmethode
     * {@code analysiereDokument()} diese Methode nach einer echten KI-Analyse
     * ueberhaupt aufruft - die Verdrahtung selbst.
     *
     * <p>Dafuer laeuft hier der komplette Weg: {@code analysiereDokument()} ->
     * ZUGFeRD-Versuch (schlaegt fuer die Test-Dummy-Datei erwartungsgemaess
     * fehl) -> KI-Fallback -> {@code rufGeminiApi} (mit gemocktem
     * {@code httpClient}) -> {@code mapJsonToData} ->
     * {@code verarbeiteArtikelPositionen} -> {@code eventPublisher.publishEvent}.
     *
     * <p>Der {@code httpClient} ist inline gebaut und kein Konstruktor-Parameter
     * - deshalb per Reflection ersetzt, genau wie in
     * {@code BelegKiAnalyseServiceTest} (dortiges {@code setField}-Muster).
     * Der {@code objectMapper} IST ein Konstruktor-Parameter; damit der volle
     * JSON-Roundtrip (Request bauen, Gemini-Antwort parsen, Positionen mappen)
     * tatsaechlich laeuft statt an einem unbestueckten Mock zu scheitern,
     * bekommt der Service hier eine eigene Instanz mit einem echten
     * {@link ObjectMapper} statt des klassenweiten Mock-Feldes.
     */
    @Nested
    class AnalysiereDokumentStoesstPreisuebernahmeAn {

        private static final String DATEINAME = "muster-dokument.pdf";

        private GeminiDokumentAnalyseService serviceMitEchtemMapper;
        private HttpClient httpClientMock;
        private Path tempUploadRoot;

        @BeforeEach
        void baueServiceMitGemocktemHttpClientAuf() throws Exception {
            serviceMitEchtemMapper = new GeminiDokumentAnalyseService(
                    new ObjectMapper(),
                    lieferantenRepository,
                    dokumentRepository,
                    zugferdExtractorService,
                    lieferantGeschaeftsdokumentRepository,
                    systemSettingsService,
                    eventPublisher,
                    new LieferantDokumentAbgleich(new ObjectMapper()),
                    sperreRepository,
                    positionService,
                    new LieferantDokumentPositionLeser(new ObjectMapper()),
                    new org.springframework.transaction.support.TransactionTemplate(
                            mock(org.springframework.transaction.PlatformTransactionManager.class)));

            httpClientMock = mock(HttpClient.class);
            setField(serviceMitEchtemMapper, "httpClient", httpClientMock);

            // Eigenes Upload-Verzeichnis pro Test, damit nichts im echten
            // uploads/-Ordner landet (Sperrzone fuer Commits, siehe CLAUDE.md).
            tempUploadRoot = Files.createTempDirectory("gemini-analyse-test-uploads");
            Files.createDirectories(tempUploadRoot.resolve("attachments"));
            Files.write(tempUploadRoot.resolve("attachments").resolve(DATEINAME),
                    "%PDF-1.4 Dummy-Inhalt fuer den Test".getBytes());
            setField(serviceMitEchtemMapper, "uploadPath", tempUploadRoot.toString());
        }

        @AfterEach
        void raeumeTempVerzeichnisAuf() throws Exception {
            if (tempUploadRoot == null) {
                return;
            }
            try (var dateien = Files.walk(tempUploadRoot)) {
                dateien.sorted(java.util.Comparator.reverseOrder()).forEach(pfad -> pfad.toFile().delete());
            }
        }

        @Test
        @org.junit.jupiter.api.DisplayName(
                "RECHNUNG mit Artikelpositionen -> PreisUebernahmeEvent wird nach der KI-Analyse veroeffentlicht")
        void rechnungMitArtikelpositionen_veroeffentlichtPreisUebernahmeEvent() throws Exception {
            Lieferanten lieferant = new Lieferanten();
            lieferant.setId(1L);
            lieferant.setLieferantenname("Musterlieferant GmbH");

            LieferantDokument dokument = new LieferantDokument();
            dokument.setId(501L);
            dokument.setLieferant(lieferant);
            dokument.setOriginalDateiname(DATEINAME);
            dokument.setGespeicherterDateiname(DATEINAME);

            String analyseJson = """
                    {
                      "dokumentTyp": "RECHNUNG",
                      "istGeschaeftsdokument": true,
                      "dokumentNummer": "RE-2026-0815",
                      "dokumentDatum": "2026-03-14",
                      "betragBrutto": 119.00,
                      "confidence": 0.95,
                      "artikelPositionen": [
                        {
                          "externeArtikelnummer": "MUSTER-001",
                          "einzelpreis": 62.00,
                          "preiseinheit": "Stück",
                          "mengeneinheit": "Stück"
                        }
                      ]
                    }
                    """;
            stelleKiAntwortBereit(dokument, analyseJson);

            serviceMitEchtemMapper.analysiereDokument(dokument);

            ArgumentCaptor<PreisUebernahmeEvent> captor = ArgumentCaptor.forClass(PreisUebernahmeEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            PreisUebernahmeEvent event = captor.getValue();

            assertThat(event.lieferant()).isSameAs(lieferant);
            assertThat(event.quelle()).isEqualTo(PreisQuelle.RECHNUNG);
            assertThat(event.belegnummer()).isEqualTo("RE-2026-0815");
            assertThat(event.positionen()).hasSize(1);
            PreisUebernahmeService.Position position = event.positionen().getFirst();
            assertThat(position.externeArtikelnummer()).isEqualTo("MUSTER-001");
            assertThat(position.einzelpreis()).isEqualByComparingTo("62.00");
        }

        @Test
        @org.junit.jupiter.api.DisplayName(
                "LIEFERSCHEIN -> analysiereDokument() veroeffentlicht KEIN PreisUebernahmeEvent")
        void lieferschein_veroeffentlichtKeinEvent() throws Exception {
            Lieferanten lieferant = new Lieferanten();
            lieferant.setId(2L);
            lieferant.setLieferantenname("Musterlieferant GmbH");

            LieferantDokument dokument = new LieferantDokument();
            dokument.setId(502L);
            dokument.setLieferant(lieferant);
            dokument.setOriginalDateiname(DATEINAME);
            dokument.setGespeicherterDateiname(DATEINAME);

            String analyseJson = """
                    {
                      "dokumentTyp": "LIEFERSCHEIN",
                      "istGeschaeftsdokument": true,
                      "dokumentNummer": "LS-2026-0815",
                      "dokumentDatum": "2026-03-14",
                      "confidence": 0.9,
                      "artikelPositionen": [
                        {
                          "externeArtikelnummer": "MUSTER-001",
                          "einzelpreis": 62.00,
                          "preiseinheit": "Stück",
                          "mengeneinheit": "Stück"
                        }
                      ]
                    }
                    """;
            stelleKiAntwortBereit(dokument, analyseJson);

            serviceMitEchtemMapper.analysiereDokument(dokument);

            verifyNoInteractions(eventPublisher);
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.CsvSource({
                "GUTSCHRIFT, GUTSCHRIFT, GU-2026-001",
                "GUTSCHRIFT, RECHNUNG, GU-2026-001",
                "SONSTIG, GUTSCHRIFT, RE-2026-001-KORR"
        })
        void speichertErneutAnalysierteKiGutschriftMitNegativenBetraegen(
                LieferantDokumentTyp gespeicherterTyp, String erkannterTyp, String nummer) throws Exception {
            Lieferanten lieferant = new Lieferanten();
            lieferant.setId(1L);
            LieferantDokument dokument = new LieferantDokument();
            dokument.setId(501L);
            dokument.setTyp(gespeicherterTyp);
            dokument.setLieferant(lieferant);
            dokument.setOriginalDateiname(DATEINAME);
            dokument.setGespeicherterDateiname(DATEINAME);
            stelleKiAntwortBereit(dokument, """
                    {"dokumentTyp":"%s", "dokumentNummer":"%s",
                     "betragNetto":100.00, "betragBrutto":119.00, "confidence":0.95}
                    """.formatted(erkannterTyp, nummer));

            LieferantGeschaeftsdokument result = serviceMitEchtemMapper.analysiereDokument(dokument);

            assertThat(result).isNotNull();
            assertThat(dokument.getTyp()).isEqualTo(LieferantDokumentTyp.GUTSCHRIFT);
            assertThat(result.getBetragNetto()).isEqualByComparingTo("-100.00");
            assertThat(result.getBetragBrutto()).isEqualByComparingTo("-119.00");
            ArgumentCaptor<LieferantGeschaeftsdokument> gespeichert =
                    ArgumentCaptor.forClass(LieferantGeschaeftsdokument.class);
            verify(lieferantGeschaeftsdokumentRepository).saveAndFlush(gespeichert.capture());
            assertThat(gespeichert.getValue().getBetragBrutto()).isEqualByComparingTo("-119.00");
        }

        private LieferantDokument musterDokument(long id) {
            Lieferanten lieferant = new Lieferanten();
            lieferant.setId(1L);
            lieferant.setLieferantenname("Musterlieferant GmbH");
            LieferantDokument dokument = new LieferantDokument();
            dokument.setId(id);
            dokument.setLieferant(lieferant);
            dokument.setOriginalDateiname(DATEINAME);
            dokument.setGespeicherterDateiname(DATEINAME);
            return dokument;
        }

        @SuppressWarnings("unchecked")
        @Test
        @org.junit.jupiter.api.DisplayName("Rechnung: alle Positionen werden nach dem Speichern abgelegt")
        void rechnungSpeichertPositionen() throws Exception {
            LieferantDokument dokument = musterDokument(601L);
            stelleKiAntwortBereit(dokument, """
                    {"dokumentTyp":"RECHNUNG","dokumentNummer":"RE-2026-0901","dokumentDatum":"2026-09-01",
                     "betragNetto":145.00,"betragBrutto":172.55,"confidence":0.95,
                     "artikelPositionen":[
                       {"positionNr":1,"positionsArt":"WARE","externeArtikelnummer":"MUSTER-001",
                        "bezeichnung":"Flachstahl 50x5","menge":10,"gesamtpreisNetto":100.00},
                       {"positionNr":2,"positionsArt":"NEBENKOSTEN","bezeichnung":"Fracht","gesamtpreisNetto":45.00}]}
                    """);

            serviceMitEchtemMapper.analysiereDokument(dokument);

            ArgumentCaptor<List<org.example.kalkulationsprogramm.domain.AusgelesenePosition>> captor =
                    ArgumentCaptor.forClass(List.class);
            verify(positionService).ersetzePositionen(any(LieferantGeschaeftsdokument.class),
                    org.mockito.ArgumentMatchers.eq(LieferantDokumentTyp.RECHNUNG), captor.capture());
            assertThat(captor.getValue()).hasSize(2);
            assertThat(captor.getValue().get(1).positionsArt())
                    .isEqualTo(org.example.kalkulationsprogramm.domain.PositionsArt.NEBENKOSTEN);
        }

        @Test
        @org.junit.jupiter.api.DisplayName("Formular (SONSTIG): keine Geschaeftsdaten, keine Positionen")
        void formularOhnePositionen() throws Exception {
            LieferantDokument dokument = musterDokument(602L);
            stelleKiAntwortBereit(dokument, """
                    {"dokumentTyp":"SONSTIG","istGeschaeftsdokument":false,"artikelPositionen":[]}""");

            serviceMitEchtemMapper.analysiereDokument(dokument);

            // null = "nicht ausgelesen": es wird nichts gespeichert
            verify(positionService).ersetzePositionen(any(), any(), org.mockito.ArgumentMatchers.isNull());
        }

        @SuppressWarnings("unchecked")
        @Test
        @org.junit.jupiter.api.DisplayName("Antwort am Ausgabelimit: Kopfdaten ohne Positionen, Positionen seitenweise")
        void abgeschnitteneAntwortLiestPositionenSeitenweise() throws Exception {
            LieferantDokument dokument = musterDokument(603L);
            // Echte zweiseitige PDF, damit sich das Dokument aufteilen laesst
            try (var pdf = new org.apache.pdfbox.pdmodel.PDDocument()) {
                pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage());
                pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage());
                pdf.save(tempUploadRoot.resolve("attachments").resolve(DATEINAME).toFile());
            }
            stelleRepositoriesBereit(dokument);
            String kopf = """
                    {"dokumentTyp":"RECHNUNG","dokumentNummer":"RE-2026-0902","dokumentDatum":"2026-09-02",
                     "betragNetto":250.00,"betragBrutto":297.50,"confidence":0.9,"artikelPositionen":[]}""";
            String positionen = """
                    {"artikelPositionen":[{"bezeichnung":"Flachstahl 50x5","gesamtpreisNetto":100},
                     {"bezeichnung":"Rundrohr 42,4x2","gesamtpreisNetto":150}]}""";
            HttpResponse<String> abgeschnitten = antwort("{\"dokumentTyp\":\"RECHNUNG\",\"artikelPos", "MAX_TOKENS");
            HttpResponse<String> kopfAntwort = antwort(kopf, "STOP");
            HttpResponse<String> positionsAntwort = antwort(positionen, "STOP");
            when(httpClientMock.<String>send(any(HttpRequest.class), any()))
                    .thenReturn(abgeschnitten, kopfAntwort, positionsAntwort);

            LieferantGeschaeftsdokument result = serviceMitEchtemMapper.analysiereDokument(dokument);

            ArgumentCaptor<List<org.example.kalkulationsprogramm.domain.AusgelesenePosition>> captor =
                    ArgumentCaptor.forClass(List.class);
            verify(positionService).ersetzePositionen(any(LieferantGeschaeftsdokument.class),
                    org.mockito.ArgumentMatchers.eq(LieferantDokumentTyp.RECHNUNG), captor.capture());
            assertThat(captor.getValue()).extracting(org.example.kalkulationsprogramm.domain.AusgelesenePosition::bezeichnung)
                    .containsExactly("Flachstahl 50x5", "Rundrohr 42,4x2");
            // Positionen stehen auch in der KI-Antwort – dort liest sie der Dokumentenabgleich
            assertThat(result.getAiRawJson()).contains("Rundrohr 42,4x2");
            assertThat(result.getDokumentNummer()).isEqualTo("RE-2026-0902");
        }

        @Test
        @org.junit.jupiter.api.DisplayName("Positionen nachlesen: speichert und ergaenzt die KI-Antwort")
        void positionenNachlesen() throws Exception {
            LieferantDokument dokument = musterDokument(604L);
            dokument.setTyp(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG);
            LieferantGeschaeftsdokument gd = new LieferantGeschaeftsdokument();
            gd.setId(604L);
            gd.setAiRawJson("{\"dokumentNummer\":\"AB-1\",\"artikelPositionen\":[]}");
            dokument.setGeschaeftsdaten(gd);
            when(dokumentRepository.findById(604L)).thenReturn(Optional.of(dokument));
            when(systemSettingsService.getGeminiApiKey()).thenReturn("dummy-test-key");
            HttpResponse<String> positionen = antwort(
                    "{\"artikelPositionen\":[{\"bezeichnung\":\"Glasklemme V2A\",\"menge\":16}]}", "STOP");
            when(httpClientMock.<String>send(any(HttpRequest.class), any())).thenReturn(positionen);
            when(positionService.ersetzePositionen(any(), any(), any())).thenReturn(1);

            int anzahl = serviceMitEchtemMapper.positionenNachlesen(604L);

            assertThat(anzahl).isEqualTo(1);
            verify(positionService).ersetzePositionen(org.mockito.ArgumentMatchers.same(gd),
                    org.mockito.ArgumentMatchers.eq(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG), any());
            assertThat(gd.getAiRawJson()).contains("Glasklemme V2A").contains("AB-1");
            verify(lieferantGeschaeftsdokumentRepository).save(gd);
        }

        @Test
        @org.junit.jupiter.api.DisplayName("Positionen nachlesen: nicht fuer Formulare, nicht ohne Dokument")
        void positionenNachlesenAbgelehnt() {
            LieferantDokument formular = musterDokument(605L);
            formular.setTyp(LieferantDokumentTyp.SONSTIG);
            formular.setGeschaeftsdaten(new LieferantGeschaeftsdokument());
            when(dokumentRepository.findById(605L)).thenReturn(Optional.of(formular));
            when(dokumentRepository.findById(999L)).thenReturn(Optional.empty());

            org.assertj.core.api.Assertions.assertThatThrownBy(() -> serviceMitEchtemMapper.positionenNachlesen(605L))
                    .isInstanceOf(IllegalArgumentException.class);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> serviceMitEchtemMapper.positionenNachlesen(999L))
                    .isInstanceOf(java.util.NoSuchElementException.class);
            verifyNoInteractions(positionService);
        }

        @Test
        @org.junit.jupiter.api.DisplayName("Positionen nachlesen: Datei-Fehler verraten keinen Serverpfad")
        void positionenNachlesenOhneServerpfadImFehler() throws Exception {
            LieferantDokument dokument = musterDokument(606L);
            dokument.setTyp(LieferantDokumentTyp.RECHNUNG);
            dokument.setGeschaeftsdaten(new LieferantGeschaeftsdokument());
            dokument.setGespeicherterDateiname("gibt-es-nicht.pdf");
            when(dokumentRepository.findById(606L)).thenReturn(Optional.of(dokument));

            org.assertj.core.api.Assertions.assertThatThrownBy(() -> serviceMitEchtemMapper.positionenNachlesen(606L))
                    .isInstanceOf(PositionenNichtLesbarException.class)
                    .hasMessageNotContaining(tempUploadRoot.toString());
            verifyNoInteractions(positionService, httpClientMock);
        }

        @SuppressWarnings("unchecked")
        @Test
        @org.junit.jupiter.api.DisplayName("Duplikat: KI-Analyse ersetzt keine ZUGFeRD-Positionen")
        void kiErsetztKeineStrukturiertenPositionen() throws Exception {
            LieferantDokument dokument = musterDokument(607L);
            LieferantGeschaeftsdokument ausXml = new LieferantGeschaeftsdokument();
            ausXml.setId(700L);
            ausXml.setDokumentNummer("RE-2026-0903");
            ausXml.setDatenquelle("XML");
            when(lieferantGeschaeftsdokumentRepository.findByLieferantIdAndDokumentNummer(1L, "RE-2026-0903"))
                    .thenReturn(List.of(ausXml));
            stelleKiAntwortBereit(dokument, """
                    {"dokumentTyp":"RECHNUNG","dokumentNummer":"RE-2026-0903","betragNetto":10,"betragBrutto":11.9,
                     "confidence":0.9,"artikelPositionen":[{"bezeichnung":"Flachstahl","gesamtpreisNetto":10}]}""");

            LieferantGeschaeftsdokument result = serviceMitEchtemMapper.analysiereDokument(dokument);

            assertThat(result).isNotSameAs(ausXml);
            assertThat(result.getDokument()).isSameAs(dokument);
            verify(positionService, never())
                    .ersetzePositionen(org.mockito.ArgumentMatchers.same(ausXml), any(), any());
        }

        @Test
        @org.junit.jupiter.api.DisplayName("Duplikat: Zeugnis mit Nummer eines Lieferscheins ueberschreibt dessen Daten nicht")
        void zeugnisMitNummerEinesLieferscheinsBleibtEigenstaendig() throws Exception {
            LieferantDokument zeugnis = musterDokument(608L);
            zeugnis.setTyp(LieferantDokumentTyp.WERKSTOFFZEUGNIS);
            LieferantDokument lieferschein = musterDokument(701L);
            lieferschein.setTyp(LieferantDokumentTyp.LIEFERSCHEIN);
            LieferantGeschaeftsdokument lieferscheinDaten = new LieferantGeschaeftsdokument();
            lieferscheinDaten.setId(701L);
            lieferscheinDaten.setDokument(lieferschein);
            lieferscheinDaten.setDokumentNummer("4107891");
            when(lieferantGeschaeftsdokumentRepository.findByLieferantIdAndDokumentNummer(1L, "4107891"))
                    .thenReturn(List.of(lieferscheinDaten));
            stelleKiAntwortBereit(zeugnis, """
                    {"dokumentTyp":"WERKSTOFFZEUGNIS","dokumentNummer":"4107891","confidence":0.9,
                     "artikelPositionen":[{"bezeichnung":"Flachstahl","werkstoff":"S235JR","charge":"123456"}]}""");

            LieferantGeschaeftsdokument result = serviceMitEchtemMapper.analysiereDokument(zeugnis);

            assertThat(result).isNotSameAs(lieferscheinDaten);
            assertThat(zeugnis.getTyp()).isEqualTo(LieferantDokumentTyp.WERKSTOFFZEUGNIS);
            verify(positionService, org.mockito.Mockito.never())
                    .ersetzePositionen(org.mockito.ArgumentMatchers.same(lieferscheinDaten), any(), any());
            verify(positionService).ersetzePositionen(org.mockito.ArgumentMatchers.same(result),
                    org.mockito.ArgumentMatchers.eq(LieferantDokumentTyp.WERKSTOFFZEUGNIS), any());
        }

        @Test
        @org.junit.jupiter.api.DisplayName("KI-Ergebnis wird vor dem Speichern an sein Dokument gehaengt (@MapsId)")
        void kiErgebnisKenntSeinDokument() throws Exception {
            LieferantDokument dokument = musterDokument(610L);
            stelleKiAntwortBereit(dokument, """
                    {"dokumentTyp":"RECHNUNG","dokumentNummer":"RE-2026-0610","confidence":0.9}""");

            serviceMitEchtemMapper.analysiereDokument(dokument);

            ArgumentCaptor<LieferantGeschaeftsdokument> gespeichert =
                    ArgumentCaptor.forClass(LieferantGeschaeftsdokument.class);
            verify(lieferantGeschaeftsdokumentRepository).saveAndFlush(gespeichert.capture());
            assertThat(gespeichert.getValue().getDokument()).isSameAs(dokument);
        }

        @Test
        @org.junit.jupiter.api.DisplayName("Erneute KI-Analyse aktualisiert vorhandene Geschaeftsdaten statt neue anzulegen")
        void erneuteKiAnalyseAktualisiertVorhandeneGeschaeftsdaten() throws Exception {
            LieferantDokument zeugnis = musterDokument(611L);
            zeugnis.setTyp(LieferantDokumentTyp.WERKSTOFFZEUGNIS);
            LieferantGeschaeftsdokument vorhanden = new LieferantGeschaeftsdokument();
            vorhanden.setId(611L);
            vorhanden.setDokument(zeugnis);
            vorhanden.setBezahlt(true);
            vorhanden.setManuellePruefungErforderlich(true);
            zeugnis.setGeschaeftsdaten(vorhanden);
            stelleKiAntwortBereit(zeugnis, """
                    {"dokumentTyp":"WERKSTOFFZEUGNIS","dokumentNummer":"ZND-0611","confidence":0.9,
                     "artikelPositionen":[{"bezeichnung":"Flachstahl","werkstoff":"S235JR","charge":"123456"}]}""");

            LieferantGeschaeftsdokument result = serviceMitEchtemMapper.analysiereDokument(zeugnis);

            assertThat(result).isSameAs(vorhanden);
            assertThat(vorhanden.getDokumentNummer()).isEqualTo("ZND-0611");
            assertThat(vorhanden.getManuellePruefungErforderlich()).isFalse();
            assertThat(vorhanden.getBezahlt()).isTrue();
            verify(lieferantGeschaeftsdokumentRepository).saveAndFlush(org.mockito.ArgumentMatchers.same(vorhanden));
            verify(positionService).ersetzePositionen(org.mockito.ArgumentMatchers.same(vorhanden),
                    org.mockito.ArgumentMatchers.eq(LieferantDokumentTyp.WERKSTOFFZEUGNIS),
                    org.mockito.ArgumentMatchers.argThat(positionen -> positionen != null && positionen.size() == 1));
        }

        @Test
        @org.junit.jupiter.api.DisplayName("bindeAnDokument: fehlende KI-Werte ueberschreiben vorhandene nicht, Pruefmarke folgt dem Ergebnis")
        void bindeAnDokumentBehaeltVorhandeneWerte() {
            LieferantDokument dokument = musterDokument(612L);
            LieferantGeschaeftsdokument vorhanden = vorhandeneDaten(dokument);
            vorhanden.setDokumentNummer("ALT-1");
            vorhanden.setBetragBrutto(new BigDecimal("50.00"));
            vorhanden.setManuellePruefungErforderlich(true);
            LieferantGeschaeftsdokument neu = new LieferantGeschaeftsdokument();
            neu.setDokumentNummer("NEU-1");
            neu.setAusgelesenePositionen(List.of());

            LieferantGeschaeftsdokument result = GeminiDokumentAnalyseService.bindeAnDokument(dokument, neu);

            assertThat(result).isSameAs(vorhanden);
            assertThat(result.getDokumentNummer()).isEqualTo("NEU-1");
            assertThat(result.getBetragBrutto()).isEqualByComparingTo("50.00");
            // Vollstaendig erkannt: die alte Pruefmarke entfaellt
            assertThat(result.getManuellePruefungErforderlich()).isFalse();
            assertThat(result.getAusgelesenePositionen()).isEmpty();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("bindeAnDokument: Zahlungs-, Freigabe- und Pruefstand bleiben bei Reanalyse erhalten")
        void bindeAnDokumentBehaeltZahlungsUndFreigabestand() {
            LieferantDokument dokument = musterDokument(613L);
            LieferantGeschaeftsdokument vorhanden = vorhandeneDaten(dokument);
            vorhanden.setBereitsGezahlt(true);
            vorhanden.setBezahlt(true);
            vorhanden.setBezahltAm(LocalDate.of(2026, 3, 20));
            vorhanden.setTatsaechlichGezahlt(new BigDecimal("116.62"));
            vorhanden.setMitSkonto(true);
            vorhanden.setGenehmigt(true);
            vorhanden.setLagerbestellung(true);
            vorhanden.setVerifiziert(true);
            LieferantGeschaeftsdokument neu = new LieferantGeschaeftsdokument();
            neu.setDokumentNummer("RE-2026-0613");

            GeminiDokumentAnalyseService.bindeAnDokument(dokument, neu);

            assertThat(vorhanden.getBereitsGezahlt()).isTrue();
            assertThat(vorhanden.getBezahlt()).isTrue();
            assertThat(vorhanden.getBezahltAm()).isEqualTo(LocalDate.of(2026, 3, 20));
            assertThat(vorhanden.getTatsaechlichGezahlt()).isEqualByComparingTo("116.62");
            assertThat(vorhanden.getMitSkonto()).isTrue();
            assertThat(vorhanden.getGenehmigt()).isTrue();
            assertThat(vorhanden.getLagerbestellung()).isTrue();
            assertThat(vorhanden.getVerifiziert()).isTrue();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("bindeAnDokument: KI erkennt Zahlung -> bereits gezahlt wird gesetzt")
        void bindeAnDokumentUebernimmtErkannteZahlung() {
            LieferantDokument dokument = musterDokument(614L);
            LieferantGeschaeftsdokument vorhanden = vorhandeneDaten(dokument);
            LieferantGeschaeftsdokument neu = new LieferantGeschaeftsdokument();
            neu.setBereitsGezahlt(true);

            GeminiDokumentAnalyseService.bindeAnDokument(dokument, neu);

            assertThat(vorhanden.getBereitsGezahlt()).isTrue();
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = {"AI_FAILED", "AI_PARSE_FAILED", "AI_ERROR"})
        @org.junit.jupiter.api.DisplayName("bindeAnDokument: fehlgeschlagene KI laesst den Bestand unveraendert")
        void bindeAnDokumentIgnoriertKiFehlschlag(String fehlerQuelle) {
            LieferantDokument dokument = musterDokument(615L);
            LieferantGeschaeftsdokument vorhanden = vorhandeneDaten(dokument);
            vorhanden.setDokumentNummer("RE-2026-0615");
            vorhanden.setDatenquelle("AI");
            vorhanden.setAiRawJson("{\"dokumentNummer\":\"RE-2026-0615\"}");
            LieferantGeschaeftsdokument neu = new LieferantGeschaeftsdokument();
            neu.setDatenquelle(fehlerQuelle);
            neu.setManuellePruefungErforderlich(true);
            neu.setAiRawJson("{kaputt");
            neu.setAusgelesenePositionen(List.of());

            LieferantGeschaeftsdokument result = GeminiDokumentAnalyseService.bindeAnDokument(dokument, neu);

            assertThat(result).isSameAs(vorhanden);
            assertThat(result.getDatenquelle()).isEqualTo("AI");
            assertThat(result.getManuellePruefungErforderlich()).isFalse();
            assertThat(result.getAiRawJson()).isEqualTo("{\"dokumentNummer\":\"RE-2026-0615\"}");
            assertThat(result.getAusgelesenePositionen()).isNull();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("Reanalyse: KI ersetzt vorhandene ZUGFeRD-Daten und -Positionen nicht")
        void kiErsetztStrukturierteDatenNicht() throws Exception {
            LieferantDokument dokument = musterDokument(616L);
            dokument.setTyp(LieferantDokumentTyp.RECHNUNG);
            LieferantGeschaeftsdokument vorhanden = vorhandeneDaten(dokument);
            vorhanden.setId(616L);
            vorhanden.setDokumentNummer("RE-2026-0616");
            vorhanden.setBetragBrutto(new BigDecimal("119.00"));
            vorhanden.setDatenquelle("ZUGFERD");
            stelleKiAntwortBereit(dokument, """
                    {"dokumentTyp":"RECHNUNG","dokumentNummer":"FALSCH-1","betragBrutto":1.00,"confidence":0.5,
                     "artikelPositionen":[{"bezeichnung":"Flachstahl","menge":1}]}""");

            LieferantGeschaeftsdokument result = serviceMitEchtemMapper.analysiereDokument(dokument);

            assertThat(result).isSameAs(vorhanden);
            assertThat(vorhanden.getDokumentNummer()).isEqualTo("RE-2026-0616");
            assertThat(vorhanden.getBetragBrutto()).isEqualByComparingTo("119.00");
            assertThat(vorhanden.getDatenquelle()).isEqualTo("ZUGFERD");
            verify(positionService).ersetzePositionen(org.mockito.ArgumentMatchers.same(vorhanden), any(),
                    org.mockito.ArgumentMatchers.isNull());
        }

        private LieferantGeschaeftsdokument vorhandeneDaten(LieferantDokument dokument) {
            LieferantGeschaeftsdokument vorhanden = new LieferantGeschaeftsdokument();
            vorhanden.setDokument(dokument);
            dokument.setGeschaeftsdaten(vorhanden);
            return vorhanden;
        }

        /** Geschaeftsdaten eines anderen Dokuments (z. B. XML zum PDF) mit derselben Belegnummer. */
        private LieferantGeschaeftsdokument partnerDaten(long id, String nummer, String datenquelle) {
            LieferantDokument partner = musterDokument(id);
            partner.setTyp(LieferantDokumentTyp.RECHNUNG);
            LieferantGeschaeftsdokument daten = vorhandeneDaten(partner);
            daten.setId(id);
            daten.setDokumentNummer(nummer);
            daten.setDatenquelle(datenquelle);
            when(lieferantGeschaeftsdokumentRepository.findByLieferantIdAndDokumentNummer(1L, nummer))
                    .thenReturn(List.of(daten));
            return daten;
        }

        @Test
        @org.junit.jupiter.api.DisplayName("PDF+XML-Paar: KI-Analyse behaelt eigene Geschaeftsdaten und uebernimmt die XML-Kopfdaten")
        void kiAnalyseMitXmlPartnerBehaeltEigeneGeschaeftsdaten() throws Exception {
            LieferantDokument pdf = musterDokument(617L);
            pdf.setTyp(LieferantDokumentTyp.RECHNUNG);
            LieferantGeschaeftsdokument eigene = vorhandeneDaten(pdf);
            eigene.setId(617L);
            eigene.setBezahlt(true);
            LieferantGeschaeftsdokument ausXml = partnerDaten(717L, "RE-2026-0617", "XML");
            ausXml.setBetragBrutto(new BigDecimal("238.00"));
            ausXml.setDokumentDatum(LocalDate.of(2026, 9, 1));
            stelleKiAntwortBereit(pdf, """
                    {"dokumentTyp":"RECHNUNG","dokumentNummer":"RE-2026-0617","betragBrutto":230.00,"confidence":0.9,
                     "artikelPositionen":[{"bezeichnung":"Flachstahl","gesamtpreisNetto":200}]}""");

            LieferantGeschaeftsdokument result = serviceMitEchtemMapper.analysiereDokument(pdf);

            assertThat(result).isSameAs(eigene);
            assertThat(pdf.getGeschaeftsdaten()).isSameAs(eigene);
            assertThat(eigene.getBetragBrutto()).isEqualByComparingTo("238.00");
            assertThat(eigene.getDokumentDatum()).isEqualTo(LocalDate.of(2026, 9, 1));
            assertThat(eigene.getBezahlt()).isTrue();
            assertThat(ausXml.getBetragBrutto()).isEqualByComparingTo("238.00");
            verify(lieferantGeschaeftsdokumentRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.same(ausXml));
            verify(positionService, never())
                    .ersetzePositionen(org.mockito.ArgumentMatchers.same(ausXml), any(), any());
            verify(positionService).ersetzePositionen(org.mockito.ArgumentMatchers.same(eigene),
                    org.mockito.ArgumentMatchers.eq(LieferantDokumentTyp.RECHNUNG),
                    org.mockito.ArgumentMatchers.argThat(positionen -> positionen != null && positionen.size() == 1));
        }

        @Test
        @org.junit.jupiter.api.DisplayName("PDF+XML-Paar: ZUGFeRD-Analyse behaelt eigene Geschaeftsdaten und verbessert die des KI-Partners")
        void zugferdMitKiPartnerBehaeltEigeneGeschaeftsdaten() throws Exception {
            LieferantDokument pdf = musterDokument(618L);
            pdf.setTyp(LieferantDokumentTyp.RECHNUNG);
            LieferantGeschaeftsdokument eigene = vorhandeneDaten(pdf);
            eigene.setId(618L);
            LieferantGeschaeftsdokument ausKi = partnerDaten(718L, "RE-2026-0618", "AI");
            ausKi.setBetragBrutto(new BigDecimal("100.00"));
            ausKi.setAiConfidence(0.6);
            ausKi.setBezahlt(true);
            stelleZugferdBereit(pdf, zugferdRechnung("RE-2026-0618", "Rechnung"));

            LieferantGeschaeftsdokument result = serviceMitEchtemMapper.analysiereDokument(pdf);

            assertThat(result).isSameAs(eigene);
            assertThat(pdf.getGeschaeftsdaten()).isSameAs(eigene);
            assertThat(eigene.getDatenquelle()).isEqualTo("ZUGFERD");
            assertThat(eigene.getBetragBrutto()).isEqualByComparingTo("119.00");
            assertThat(ausKi.getBetragBrutto()).isEqualByComparingTo("119.00");
            assertThat(ausKi.getAiConfidence()).isEqualTo(1.0);
            assertThat(ausKi.getBezahlt()).isTrue();
            var reihenfolge = org.mockito.Mockito.inOrder(lieferantGeschaeftsdokumentRepository);
            reihenfolge.verify(lieferantGeschaeftsdokumentRepository).saveAndFlush(org.mockito.ArgumentMatchers.same(eigene));
            reihenfolge.verify(lieferantGeschaeftsdokumentRepository).saveAndFlush(org.mockito.ArgumentMatchers.same(ausKi));
            verify(positionService).ersetzePositionen(org.mockito.ArgumentMatchers.same(eigene),
                    org.mockito.ArgumentMatchers.eq(LieferantDokumentTyp.RECHNUNG),
                    org.mockito.ArgumentMatchers.argThat(positionen -> positionen != null && positionen.size() == 1));
            verify(positionService).ersetzePositionen(org.mockito.ArgumentMatchers.same(ausKi),
                    org.mockito.ArgumentMatchers.eq(LieferantDokumentTyp.RECHNUNG),
                    org.mockito.ArgumentMatchers.argThat(positionen -> positionen != null && positionen.size() == 1));
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.NullSource
        @org.junit.jupiter.params.provider.EnumSource(value = LieferantDokumentTyp.class, names = "GUTSCHRIFT")
        @org.junit.jupiter.api.DisplayName("PDF+XML-Gutschrift: der KI-Partner bekommt die ZUGFeRD-Betraege als Minderung")
        void zugferdGutschriftGibtPartnerNegativeBetraege(LieferantDokumentTyp partnerTyp) throws Exception {
            LieferantDokument pdf = musterDokument(619L);
            LieferantGeschaeftsdokument ausKi = partnerDaten(719L, "GS-2026-0619", "AI");
            ausKi.getDokument().setTyp(partnerTyp);
            ausKi.setBetragBrutto(new BigDecimal("-100.00"));
            stelleZugferdBereit(pdf, zugferdRechnung("GS-2026-0619", "Gutschrift"));

            LieferantGeschaeftsdokument result = serviceMitEchtemMapper.analysiereDokument(pdf);

            assertThat(result.getBetragBrutto()).isEqualByComparingTo("-119.00");
            assertThat(ausKi.getBetragBrutto()).isEqualByComparingTo("-119.00");
        }

        @Test
        @org.junit.jupiter.api.DisplayName("PDF+XML-Paar: Positionen mit Projektaufteilung beim Partner bleiben erhalten")
        void zugferdErsetztAufgeteiltePartnerPositionenNicht() throws Exception {
            LieferantDokument pdf = musterDokument(620L);
            LieferantGeschaeftsdokument ausKi = partnerDaten(720L, "RE-2026-0620", "AI");
            var aufgeteilt = new org.example.kalkulationsprogramm.domain.LieferantDokumentPosition();
            aufgeteilt.setProjekt(new org.example.kalkulationsprogramm.domain.Projekt());
            when(positionService.findePositionen(720L)).thenReturn(List.of(aufgeteilt));
            stelleZugferdBereit(pdf, zugferdRechnung("RE-2026-0620", "Rechnung"));

            serviceMitEchtemMapper.analysiereDokument(pdf);

            assertThat(ausKi.getBetragBrutto()).isEqualByComparingTo("119.00");
            verify(positionService, never())
                    .ersetzePositionen(org.mockito.ArgumentMatchers.same(ausKi), any(), any());
        }

        @Test
        @org.junit.jupiter.api.DisplayName("PDF+XML-Paar: strukturierter Partner behaelt seine eigenen Positionen")
        void zugferdErsetztStrukturiertePartnerPositionenNicht() throws Exception {
            LieferantDokument pdf = musterDokument(621L);
            LieferantGeschaeftsdokument ausXml = partnerDaten(721L, "RE-2026-0621", "XML");
            stelleZugferdBereit(pdf, zugferdRechnung("RE-2026-0621", "Rechnung"));

            LieferantGeschaeftsdokument result = serviceMitEchtemMapper.analysiereDokument(pdf);

            assertThat(result).isNotSameAs(ausXml);
            verify(positionService, never())
                    .ersetzePositionen(org.mockito.ArgumentMatchers.same(ausXml), any(), any());
        }

        @Test
        @org.junit.jupiter.api.DisplayName("Duplikat: KI-Lieferschein ohne Typ uebernimmt keine Daten einer XML-Rechnung gleicher Nummer")
        void kiLieferscheinUebernimmtKeineRechnungsdaten() throws Exception {
            LieferantDokument lieferschein = musterDokument(622L);
            LieferantGeschaeftsdokument rechnung = partnerDaten(722L, "4711", "XML");
            rechnung.setBetragBrutto(new BigDecimal("238.00"));
            stelleKiAntwortBereit(lieferschein, """
                    {"dokumentTyp":"LIEFERSCHEIN","dokumentNummer":"4711","confidence":0.9}""");

            LieferantGeschaeftsdokument result = serviceMitEchtemMapper.analysiereDokument(lieferschein);

            assertThat(lieferschein.getTyp()).isEqualTo(LieferantDokumentTyp.LIEFERSCHEIN);
            assertThat(result.getBetragBrutto()).isNull();
        }

        private ZugferdDaten zugferdRechnung(String nummer, String art) {
            ZugferdDaten zugferd = new ZugferdDaten();
            zugferd.setRechnungsnummer(nummer);
            zugferd.setBetrag(new BigDecimal("119.00"));
            zugferd.setGeschaeftsdokumentart(art);
            zugferd.setArtikelPositionen(List.of(new org.example.kalkulationsprogramm.dto.Zugferd.ZugferdArtikelPosition(
                    "A-1", "Flachstahl", BigDecimal.ONE, "Stk", new BigDecimal("100.00"), null,
                    new BigDecimal("100.00"))));
            return zugferd;
        }

        /** Wie {@link #stelleKiAntwortBereit}, nur mit ZUGFeRD-Treffer statt KI-Aufruf. */
        private void stelleZugferdBereit(LieferantDokument dokument, ZugferdDaten zugferd) {
            when(zugferdExtractorService.extract(anyString(), anyString())).thenReturn(zugferd);
            when(dokumentRepository.findById(dokument.getId())).thenReturn(Optional.of(dokument));
            when(dokumentRepository.saveAndFlush(any(LieferantDokument.class))).thenAnswer(inv -> inv.getArgument(0));
            when(lieferantGeschaeftsdokumentRepository.saveAndFlush(any(LieferantGeschaeftsdokument.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        @org.junit.jupiter.api.DisplayName("Duplikat: gleicher Typ oder offener Typ darf zusammengefuehrt werden")
        void gleicherBelegTyp() {
            LieferantDokument rechnung = musterDokument(609L);
            rechnung.setTyp(LieferantDokumentTyp.RECHNUNG);
            LieferantDokument andereRechnung = musterDokument(702L);
            andereRechnung.setTyp(LieferantDokumentTyp.RECHNUNG);
            LieferantDokument lieferschein = musterDokument(703L);
            lieferschein.setTyp(LieferantDokumentTyp.LIEFERSCHEIN);
            LieferantGeschaeftsdokument gleich = new LieferantGeschaeftsdokument();
            gleich.setDokument(andereRechnung);
            LieferantGeschaeftsdokument anders = new LieferantGeschaeftsdokument();
            anders.setDokument(lieferschein);
            LieferantGeschaeftsdokument ohneDokument = new LieferantGeschaeftsdokument();

            assertThat(GeminiDokumentAnalyseService.gleicherBelegTyp(rechnung.getTyp(), gleich)).isTrue();
            assertThat(GeminiDokumentAnalyseService.gleicherBelegTyp(rechnung.getTyp(), anders)).isFalse();
            assertThat(GeminiDokumentAnalyseService.gleicherBelegTyp(rechnung.getTyp(), ohneDokument)).isTrue();
            rechnung.setTyp(LieferantDokumentTyp.SONSTIG);
            assertThat(GeminiDokumentAnalyseService.gleicherBelegTyp(rechnung.getTyp(), anders)).isTrue();
            rechnung.setTyp(null);
            assertThat(GeminiDokumentAnalyseService.gleicherBelegTyp(rechnung.getTyp(), anders)).isTrue();
        }

        private HttpResponse<String> antwort(String text, String finishReason) throws Exception {
            ObjectMapper baumapper = new ObjectMapper();
            var envelope = baumapper.createObjectNode();
            var kandidat = envelope.putArray("candidates").addObject();
            kandidat.putObject("content").putArray("parts").addObject().put("text", text);
            kandidat.put("finishReason", finishReason);
            @SuppressWarnings("unchecked")
            HttpResponse<String> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            when(response.body()).thenReturn(baumapper.writeValueAsString(envelope));
            return response;
        }

        /**
         * Stellt Repository- und API-Antworten so bereit, dass
         * {@code analysiereDokument()} den KI-Zweig durchlaeuft: kein
         * ZUGFeRD/XML-Treffer (Dummy-Datei), Gemini-API antwortet mit
         * {@code analyseJson}.
         */
        private void stelleKiAntwortBereit(LieferantDokument dokument, String analyseJson) throws Exception {
            stelleRepositoriesBereit(dokument);

            String antwortEnvelope = baueGeminiAntwortEnvelope(analyseJson);
            @SuppressWarnings("unchecked")
            HttpResponse<String> httpResponse = mock(HttpResponse.class);
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(antwortEnvelope);
            when(httpClientMock.<String>send(any(HttpRequest.class), any())).thenReturn(httpResponse);
        }

        private void stelleRepositoriesBereit(LieferantDokument dokument) {
            when(dokumentRepository.findById(dokument.getId())).thenReturn(Optional.of(dokument));
            when(systemSettingsService.getGeminiApiKey()).thenReturn("dummy-test-key");
            when(dokumentRepository.saveAndFlush(any(LieferantDokument.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(lieferantGeschaeftsdokumentRepository.saveAndFlush(any(LieferantGeschaeftsdokument.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
        }

        /**
         * Baut die Huelle, in der Gemini seine Antwort verpackt (candidates ->
         * content -> parts -> text), mit {@code analyseJsonText} als Inhalt des
         * text-Feldes - unabhaengig vom (gemockten) {@code objectMapper} der
         * Service-Instanz.
         */
        private String baueGeminiAntwortEnvelope(String analyseJsonText) throws Exception {
            ObjectMapper baumapper = new ObjectMapper();
            var envelope = baumapper.createObjectNode();
            var content = envelope.putArray("candidates").addObject().putObject("content");
            content.putArray("parts").addObject().put("text", analyseJsonText);
            return baumapper.writeValueAsString(envelope);
        }

        private void setField(Object target, String feldName, Object wert) throws Exception {
            var feld = GeminiDokumentAnalyseService.class.getDeclaredField(feldName);
            feld.setAccessible(true);
            feld.set(target, wert);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "GUTSCHRIFT, RECHNUNG, -119.00, true",
            "GUTSCHRIFT, RECHNUNG, 119.00, true",
            "GUTSCHRIFT, RECHNUNG, -120.00, false",
            "RECHNUNG, AUFTRAGSBESTAETIGUNG, -119.00, false"
    })
    void verknuepftGutschriftMitPositiverRechnungNachBetrag(
            LieferantDokumentTyp typ, LieferantDokumentTyp vorgaengerTyp,
            BigDecimal brutto, boolean erwartetVerknuepft) {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(1L);
        LieferantDokument dokument = new LieferantDokument();
        dokument.setId(2L);
        dokument.setLieferant(lieferant);
        dokument.setTyp(typ);
        LieferantGeschaeftsdokument daten = new LieferantGeschaeftsdokument();
        daten.setBetragBrutto(brutto);
        daten.setDokumentDatum(LocalDate.of(2026, 9, 14));
        dokument.setGeschaeftsdaten(daten);
        LieferantDokument rechnung = new LieferantDokument();
        rechnung.setId(3L);
        rechnung.setLieferant(lieferant);
        rechnung.setTyp(vorgaengerTyp);
        LieferantGeschaeftsdokument rechnungsdaten = new LieferantGeschaeftsdokument();
        rechnungsdaten.setBetragBrutto(new BigDecimal("119.00"));
        rechnungsdaten.setDokumentDatum(LocalDate.of(2026, 9, 10));
        rechnung.setGeschaeftsdaten(rechnungsdaten);

        service.performRelink(dokument, java.util.List.of(rechnung));

        if (erwartetVerknuepft) {
            assertThat(dokument.getVerknuepfteDokumente()).containsExactly(rechnung);
        } else {
            assertThat(dokument.getVerknuepfteDokumente()).isEmpty();
        }
    }

    @Test
    void spaeterEintreffendesAngebotWirdAnVorhandeneAbGehaengt() {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(1L);

        LieferantDokument ab = new LieferantDokument();
        ab.setId(10L);
        ab.setLieferant(lieferant);
        ab.setTyp(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG);
        LieferantGeschaeftsdokument abDaten = new LieferantGeschaeftsdokument();
        abDaten.setDokumentNummer("AB-77001");
        abDaten.setReferenzNummer("AN-2026-0815");
        abDaten.setDokumentDatum(LocalDate.of(2026, 4, 1));
        ab.setGeschaeftsdaten(abDaten);

        LieferantDokument angebot = new LieferantDokument();
        angebot.setId(11L);
        angebot.setLieferant(lieferant);
        angebot.setTyp(LieferantDokumentTyp.ANGEBOT);
        LieferantGeschaeftsdokument angebotDaten = new LieferantGeschaeftsdokument();
        angebotDaten.setDokumentNummer("AN 2026/0815");
        angebotDaten.setDokumentDatum(LocalDate.of(2026, 3, 1));
        angebot.setGeschaeftsdaten(angebotDaten);

        service.performRelink(angebot, java.util.List.of(ab, angebot));

        assertThat(ab.getVerknuepfteDokumente()).containsExactly(angebot);
        verify(dokumentRepository).save(ab);
    }

    private LieferantDokument relinkDokument(long id, Lieferanten lieferant, LieferantDokumentTyp typ,
            String nummer, LocalDate datum) {
        LieferantDokument dok = new LieferantDokument();
        dok.setId(id);
        dok.setLieferant(lieferant);
        dok.setTyp(typ);
        LieferantGeschaeftsdokument daten = new LieferantGeschaeftsdokument();
        daten.setDokumentNummer(nummer);
        daten.setDokumentDatum(datum);
        dok.setGeschaeftsdaten(daten);
        return dok;
    }

    @Test
    void sammelrechnungBekommtSpaeterenLieferscheinTrotzVorhandenerVerknuepfung() {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(1L);
        LieferantDokument ls1 = relinkDokument(9L, lieferant, LieferantDokumentTyp.LIEFERSCHEIN, "LS-1001", LocalDate.of(2026, 5, 2));
        LieferantDokument rechnung = relinkDokument(10L, lieferant, LieferantDokumentTyp.RECHNUNG, "RE-2001", LocalDate.of(2026, 5, 31));
        rechnung.getGeschaeftsdaten().setAiRawJson("{\"weitereReferenzen\":[\"LS-1001\",\"LS-1002\"]}");
        rechnung.getVerknuepfteDokumente().add(ls1);
        LieferantDokument ls2 = relinkDokument(11L, lieferant, LieferantDokumentTyp.LIEFERSCHEIN, "LS-1002", LocalDate.of(2026, 5, 9));

        service.performRelink(ls2, java.util.List.of(ls1, rechnung, ls2));

        assertThat(rechnung.getVerknuepfteDokumente()).containsExactlyInAnyOrder(ls1, ls2);
        verify(dokumentRepository).save(rechnung);
    }

    @Test
    void hinweisTrefferVerfaelschtVorhandeneZuordnungNicht() {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(1L);
        LieferantDokument vonHand = relinkDokument(9L, lieferant, LieferantDokumentTyp.ANGEBOT, "AN-1", LocalDate.of(2026, 2, 1));
        LieferantDokument ab = relinkDokument(10L, lieferant, LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 3, 1));
        ab.getGeschaeftsdaten().setBetragBrutto(new BigDecimal("595.00"));
        ab.getVerknuepfteDokumente().add(vonHand);
        LieferantDokument angebot = relinkDokument(11L, lieferant, LieferantDokumentTyp.ANGEBOT, "AN-2", LocalDate.of(2026, 2, 15));
        angebot.getGeschaeftsdaten().setBetragBrutto(new BigDecimal("595.00"));

        // Vorwärts (AB sucht Vorgänger) und rückwärts (neues Angebot sucht Nachfolger)
        service.performRelink(ab, java.util.List.of(vonHand, ab, angebot));
        service.performRelink(angebot, java.util.List.of(vonHand, ab, angebot));

        assertThat(ab.getVerknuepfteDokumente()).containsExactly(vonHand);
        verify(dokumentRepository, never()).save(ab);
    }

    @Test
    void verknuepfeDokumenteNeuZaehltNeueVerknuepfungen() {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(1L);
        LieferantDokument angebot = relinkDokument(1L, lieferant, LieferantDokumentTyp.ANGEBOT, "AN-4711", LocalDate.of(2026, 2, 1));
        LieferantDokument ab = relinkDokument(2L, lieferant, LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 3, 1));
        ab.getGeschaeftsdaten().setReferenzNummer("AN-4711");
        LieferantDokument rechnung = relinkDokument(3L, lieferant, LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2026, 3, 20));
        rechnung.getGeschaeftsdaten().setReferenzNummer("AB-1");
        LieferantDokument ohneDaten = new LieferantDokument();
        ohneDaten.setId(4L);
        ohneDaten.setLieferant(lieferant);
        ohneDaten.setTyp(LieferantDokumentTyp.RECHNUNG);

        int neu = service.verknuepfeDokumenteNeu(java.util.List.of(rechnung, ab, angebot, ohneDaten));

        assertThat(neu).isEqualTo(2);
        assertThat(ab.getVerknuepfteDokumente()).containsExactly(angebot);
        assertThat(rechnung.getVerknuepfteDokumente()).containsExactly(ab);
        assertThat(service.verknuepfeDokumenteNeu(java.util.List.of(rechnung, ab, angebot))).isZero();
    }

    @Test
    void backfillErgaenztNurUndBautAngebotsfassungenVorDerAbZusammen() {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(1L);
        LieferantDokument fassung1 = relinkDokument(1L, lieferant, LieferantDokumentTyp.ANGEBOT, "AN-4711", LocalDate.of(2026, 2, 1));
        fassung1.getGeschaeftsdaten().setAiRawJson("{\"kommission\":\"BV Mustermann\"}");
        LieferantDokument fassung2 = relinkDokument(2L, lieferant, LieferantDokumentTyp.ANGEBOT, "AN-4711-2", LocalDate.of(2026, 2, 20));
        fassung2.getGeschaeftsdaten().setAiRawJson("{\"kommission\":\"BV Mustermann\"}");
        LieferantDokument ab = relinkDokument(3L, lieferant, LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 3, 1));
        ab.getGeschaeftsdaten().setAiRawJson("{\"kommission\":\"BV Mustermann\"}");
        // Von Hand gesetzte Verknüpfung, die der Abgleich selbst nie finden würde
        LieferantDokument lieferschein = relinkDokument(4L, lieferant, LieferantDokumentTyp.LIEFERSCHEIN, "LS-1", LocalDate.of(2026, 3, 10));
        LieferantDokument rechnung = relinkDokument(5L, lieferant, LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2026, 3, 31));
        rechnung.getVerknuepfteDokumente().add(lieferschein);

        // Absichtlich neueste zuerst, wie das Repository liefert
        when(dokumentRepository.findByLieferantIdOrderByUploadDatumDesc(1L))
                .thenReturn(java.util.List.of(rechnung, lieferschein, ab, fassung2, fassung1));

        int neu = service.relinkDokumenteByLieferant(1L);

        assertThat(neu).isEqualTo(2);
        assertThat(fassung2.getVerknuepfteDokumente()).containsExactly(fassung1);
        assertThat(ab.getVerknuepfteDokumente()).containsExactly(fassung2);
        assertThat(rechnung.getVerknuepfteDokumente()).containsExactly(lieferschein);
    }

    @Test
    void backfillAllerLieferantenGruppiertJeLieferant() {
        Lieferanten a = new Lieferanten();
        a.setId(1L);
        Lieferanten b = new Lieferanten();
        b.setId(2L);
        LieferantDokument angebotA = relinkDokument(1L, a, LieferantDokumentTyp.ANGEBOT, "AN-4711", LocalDate.of(2026, 2, 1));
        LieferantDokument abB = relinkDokument(2L, b, LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 3, 1));
        abB.getGeschaeftsdaten().setReferenzNummer("AN-4711");
        LieferantDokument angebotB = relinkDokument(3L, b, LieferantDokumentTyp.ANGEBOT, "AN-4711", LocalDate.of(2026, 2, 1));
        when(dokumentRepository.findAll()).thenReturn(java.util.List.of(angebotA, abB, angebotB));

        assertThat(service.relinkAlleDokumente()).isEqualTo(1);
        // Nur das Angebot desselben Lieferanten, nie über Lieferanten hinweg
        assertThat(abB.getVerknuepfteDokumente()).containsExactly(angebotB);
    }

    @Test
    void vonHandGeloestesPaarWirdNichtWiederVerknuepft() {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(1L);
        LieferantDokument ls = relinkDokument(9L, lieferant, LieferantDokumentTyp.LIEFERSCHEIN, "LS-4711", LocalDate.of(2026, 5, 2));
        LieferantDokument rechnung = relinkDokument(10L, lieferant, LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2026, 5, 20));
        rechnung.getGeschaeftsdaten().setReferenzNummer("LS-4711");
        when(sperreRepository.findByLieferantId(1L)).thenReturn(java.util.List.of(
                new org.example.kalkulationsprogramm.domain.LieferantDokumentVerknuepfungSperre(10L, 9L,
                        java.time.LocalDateTime.of(2026, 6, 1, 8, 0))));

        // Beide Richtungen: neue Rechnung sucht Lieferschein, neuer Lieferschein sucht Rechnung
        service.performRelink(rechnung, java.util.List.of(ls, rechnung));
        service.performRelink(ls, java.util.List.of(ls, rechnung));
        assertThat(rechnung.getVerknuepfteDokumente()).isEmpty();

        // Backfill aller Lieferanten beachtet die Sperre ebenso
        when(dokumentRepository.findAll()).thenReturn(java.util.List.of(ls, rechnung));
        when(sperreRepository.findAll()).thenReturn(java.util.List.of(
                new org.example.kalkulationsprogramm.domain.LieferantDokumentVerknuepfungSperre(9L, 10L,
                        java.time.LocalDateTime.of(2026, 6, 1, 8, 0))));
        assertThat(service.relinkAlleDokumente()).isZero();
        assertThat(rechnung.getVerknuepfteDokumente()).isEmpty();
    }

    @Test
    void rechnungHaengtNichtMehrAnReinenHinweisen() {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(1L);
        LieferantDokument ab = relinkDokument(9L, lieferant, LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 5, 2));
        ab.getGeschaeftsdaten().setBetragBrutto(new BigDecimal("595.00"));
        LieferantDokument rechnung = relinkDokument(10L, lieferant, LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2026, 5, 20));
        rechnung.getGeschaeftsdaten().setBetragBrutto(new BigDecimal("595.00"));

        service.performRelink(rechnung, java.util.List.of(ab, rechnung));
        service.performRelink(ab, java.util.List.of(ab, rechnung));

        assertThat(rechnung.getVerknuepfteDokumente()).isEmpty();
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
