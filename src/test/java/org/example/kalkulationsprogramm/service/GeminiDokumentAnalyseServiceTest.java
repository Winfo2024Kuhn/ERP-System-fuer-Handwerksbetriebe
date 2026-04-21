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
import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdDaten;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantenArtikelPreiseRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.WerkstoffzeugnisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class GeminiDokumentAnalyseServiceTest {

    @Mock private ObjectMapper objectMapper;
    @Mock private LieferantenRepository lieferantenRepository;
    @Mock private LieferantDokumentRepository dokumentRepository;
    @Mock private ZugferdExtractorService zugferdExtractorService;
    @Mock private LieferantenArtikelPreiseRepository artikelPreiseRepository;
    @Mock private LieferantGeschaeftsdokumentRepository lieferantGeschaeftsdokumentRepository;
    @Mock private WerkstoffzeugnisRepository werkstoffzeugnisRepository;
    @Mock private ArtikelMatchingAgentService artikelMatchingAgentService;
    @Mock private ArtikelPreisHookService preisHookService;

    private GeminiDokumentAnalyseService service;

    @BeforeEach
    void setUp() {
        service = new GeminiDokumentAnalyseService(
                objectMapper,
                lieferantenRepository,
                dokumentRepository,
                zugferdExtractorService,
                artikelPreiseRepository,
                lieferantGeschaeftsdokumentRepository,
                werkstoffzeugnisRepository,
                artikelMatchingAgentService,
                preisHookService
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

    // --- Helper methods to invoke private methods via reflection ---

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
