package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import org.example.kalkulationsprogramm.dto.FirmeninformationDto;
import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdDaten;
import org.example.kalkulationsprogramm.exception.FirmenstammdatenUnvollstaendigException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mustangproject.TradeParty;

@ExtendWith(MockitoExtension.class)
class ZugferdErstellServiceTest {

    @Mock
    private FirmeninformationService firmeninformationService;

    private ZugferdErstellService service;

    @BeforeEach
    void setUp() {
        service = new ZugferdErstellService(firmeninformationService);
    }

    /** Vollständiger Musterbetrieb – nur Dummy-Daten. */
    private static FirmeninformationDto musterbetrieb() {
        FirmeninformationDto firma = new FirmeninformationDto();
        firma.setFirmenname("Musterbetrieb GmbH");
        firma.setStrasse("Musterstraße 1");
        firma.setPlz("12345");
        firma.setOrt("Musterstadt");
        firma.setTelefon("01234 567890");
        firma.setEmail("info@musterbetrieb.example");
        firma.setUstIdNr("DE 123 456 789");
        firma.setSteuernummer("123/456/78901");
        firma.setBankName("Musterbank");
        firma.setIban("DE89 3704 0044 0532 0130 00");
        firma.setBic("COBADEFFXXX");
        firma.setGeschaeftsfuehrer("Max Mustermann");
        return firma;
    }

    @Nested
    class Verkaeufer {

        @Test
        void uebernimmtAlleFirmendatenInDenVerkaeufer() {
            TradeParty seller = service.baueVerkaeufer(musterbetrieb());

            assertThat(seller.getName()).isEqualTo("Musterbetrieb GmbH");
            assertThat(seller.getStreet()).isEqualTo("Musterstraße 1");
            assertThat(seller.getZIP()).isEqualTo("12345");
            assertThat(seller.getLocation()).isEqualTo("Musterstadt");
            assertThat(seller.getCountry()).isEqualTo("DE");
            assertThat(seller.getVATID()).isEqualTo("DE123456789");
            assertThat(seller.getTaxID()).isEqualTo("123/456/78901");
            assertThat(seller.getContact().getName()).isEqualTo("Max Mustermann");
            assertThat(seller.getContact().getPhone()).isEqualTo("01234 567890");
            assertThat(seller.getContact().getEMail()).isEqualTo("info@musterbetrieb.example");
            assertThat(seller.getBankDetails()).hasSize(1);
            assertThat(seller.getBankDetails().get(0).getIBAN()).isEqualTo("DE89370400440532013000");
            assertThat(seller.getBankDetails().get(0).getBIC()).isEqualTo("COBADEFFXXX");
        }

        @Test
        void steuernummerAlleinGenuegtAlsSteuerlicheKennung() {
            FirmeninformationDto firma = musterbetrieb();
            firma.setUstIdNr(null);

            TradeParty seller = service.baueVerkaeufer(firma);

            assertThat(seller.getVATID()).isNull();
            assertThat(seller.getTaxID()).isEqualTo("123/456/78901");
            // BR-CO-26: ohne USt-IdNr. braucht der Verkäufer eine eigene Kennung (BT-29)
            assertThat(seller.getID()).isEqualTo("123/456/78901");
        }

        @Test
        void pruefeVerkaeuferdatenWirftBeiLuecken_und_schweigtBeiVollstaendigenDaten() {
            when(firmeninformationService.getFirmeninformation()).thenReturn(musterbetrieb());
            service.pruefeVerkaeuferdaten();

            when(firmeninformationService.getFirmeninformation()).thenReturn(new FirmeninformationDto());
            assertThatThrownBy(() -> service.pruefeVerkaeuferdaten())
                    .isInstanceOf(FirmenstammdatenUnvollstaendigException.class);
        }

        @Test
        void ustIdWirdNormalisiertUndNeuesFormatGeprueft() {
            FirmeninformationDto klein = musterbetrieb();
            klein.setUstIdNr(" de 123456789 ");
            assertThat(service.baueVerkaeufer(klein).getVATID()).isEqualTo("DE123456789");

            FirmeninformationDto ohneLaendercode = musterbetrieb();
            ohneLaendercode.setUstIdNr("123456789");
            assertThatThrownBy(() -> service.baueVerkaeufer(ohneLaendercode))
                    .isInstanceOf(FirmenstammdatenUnvollstaendigException.class)
                    .hasMessageContaining("Umsatzsteuer-ID im Format DE123456789");
        }

        @Test
        void ustIdAlleinGenuegtAlsSteuerlicheKennung() {
            FirmeninformationDto firma = musterbetrieb();
            firma.setSteuernummer("  ");

            TradeParty seller = service.baueVerkaeufer(firma);

            assertThat(seller.getVATID()).isEqualTo("DE123456789");
            assertThat(seller.getTaxID()).isNull();
            assertThat(seller.getID()).isNull();
        }

        @Test
        void kontaktUndBankSindOptional() {
            FirmeninformationDto firma = musterbetrieb();
            firma.setTelefon(null);
            firma.setEmail(" ");
            firma.setIban(null);
            firma.setBic(null);

            TradeParty seller = service.baueVerkaeufer(firma);

            assertThat(seller.getContact()).isNull();
            assertThat(seller.getBankDetails()).isNullOrEmpty();
        }

        @Test
        void kontaktnameFaelltOhneGeschaeftsfuehrerAufFirmennameZurueck() {
            FirmeninformationDto firma = musterbetrieb();
            firma.setGeschaeftsfuehrer(null);

            TradeParty seller = service.baueVerkaeufer(firma);

            assertThat(seller.getContact().getName()).isEqualTo("Musterbetrieb GmbH");
        }

        @Test
        void ibanOhneBicWirdOhneBicUebernommen() {
            FirmeninformationDto firma = musterbetrieb();
            firma.setBic("");

            TradeParty seller = service.baueVerkaeufer(firma);

            assertThat(seller.getBankDetails().get(0).getIBAN()).isEqualTo("DE89370400440532013000");
            assertThat(seller.getBankDetails().get(0).getBIC()).isNull();
        }

        @Test
        void bricht_ab_und_nennt_alle_fehlenden_pflichtangaben() {
            FirmeninformationDto firma = new FirmeninformationDto();
            firma.setFirmenname("Musterbetrieb GmbH");

            assertThatThrownBy(() -> service.baueVerkaeufer(firma))
                    .isInstanceOf(FirmenstammdatenUnvollstaendigException.class)
                    .satisfies(e -> assertThat(((FirmenstammdatenUnvollstaendigException) e).getFehlendeAngaben())
                            .containsExactly("Straße", "Postleitzahl", "Ort", "Umsatzsteuer-ID oder Steuernummer"))
                    .hasMessageContaining("E-Rechnung")
                    .hasMessageContaining("Straße, Postleitzahl, Ort, Umsatzsteuer-ID oder Steuernummer");
        }

        @Test
        void leererFirmenname_zaehltAlsFehlend() {
            FirmeninformationDto firma = musterbetrieb();
            firma.setFirmenname("   ");

            assertThatThrownBy(() -> service.baueVerkaeufer(firma))
                    .isInstanceOf(FirmenstammdatenUnvollstaendigException.class)
                    .hasMessageContaining("Firmenname");
        }
    }

    @Nested
    class Erzeugen {

        @Test
        void bricht_vor_der_PDF_Verarbeitung_ab_wenn_Firmendaten_fehlen() {
            FirmeninformationDto leer = new FirmeninformationDto();
            when(firmeninformationService.getFirmeninformation()).thenReturn(leer);

            ZugferdDaten daten = new ZugferdDaten();
            daten.setRechnungsnummer("RE-2025-001");

            // Nicht-existentes PDF: ohne Vorab-Prüfung käme "ZUGFeRD Erstellung fehlgeschlagen"
            assertThatThrownBy(() -> service.erzeuge("nicht/existierend.pdf", daten))
                    .isInstanceOf(FirmenstammdatenUnvollstaendigException.class);
        }

        @Test
        void wirftRuntimeExceptionBeiNichtExistierendemPdf() {
            when(firmeninformationService.getFirmeninformation()).thenReturn(musterbetrieb());
            ZugferdDaten daten = new ZugferdDaten();
            daten.setRechnungsnummer("RE-2025-001");
            daten.setKundenName("Max Mustermann");
            daten.setBetrag(new BigDecimal("1190.00"));

            assertThatThrownBy(() -> service.erzeuge("nicht/existierend.pdf", daten))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("ZUGFeRD Erstellung fehlgeschlagen");
        }

        @Test
        void wirftExceptionBeiUngueltigemPfadUndLeerenDaten() {
            when(firmeninformationService.getFirmeninformation()).thenReturn(musterbetrieb());

            assertThatThrownBy(() -> service.erzeuge("/tmp/nonexistent.pdf", new ZugferdDaten()))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        void eingebettetesXmlEnthaeltDieFirmendatenStattEinesFestenBetriebs(@TempDir Path dir) throws Exception {
            when(firmeninformationService.getFirmeninformation()).thenReturn(musterbetrieb());
            Path quelle = dir.resolve("quelle.pdf");
            schreibeEinfachesPdf(quelle);
            ZugferdDaten daten = new ZugferdDaten();
            daten.setRechnungsnummer("RE-2025-001");
            daten.setKundenName("Max Mustermann");
            daten.setBetrag(new BigDecimal("1190.00"));

            Path ergebnis = service.erzeuge(quelle.toString(), daten);

            try {
                String xml = new org.mustangproject.ZUGFeRD.ZUGFeRDImporter(ergebnis.toString()).getUTF8();
                assertThat(xml)
                        .contains("Musterbetrieb GmbH", "Musterstraße 1", "12345", "Musterstadt",
                                "DE123456789", "123/456/78901", "DE89370400440532013000")
                        .doesNotContain("Kuhn");
            } finally {
                Files.deleteIfExists(ergebnis);
            }
        }

        @Test
        void verkaeuferErfuelltEN16931MitUstIdUndMitNurSteuernummer(@TempDir Path dir) throws Exception {
            for (boolean nurSteuernummer : new boolean[] { false, true }) {
                FirmeninformationDto firma = musterbetrieb();
                if (nurSteuernummer) {
                    firma.setUstIdNr(null);
                }
                when(firmeninformationService.getFirmeninformation()).thenReturn(firma);
                Path quelle = dir.resolve("quelle-" + nurSteuernummer + ".pdf");
                schreibeEinfachesPdf(quelle);
                ZugferdDaten daten = new ZugferdDaten();
                daten.setRechnungsnummer("RE-2025-001");
                daten.setRechnungsdatum(java.time.LocalDate.of(2025, 1, 15));
                daten.setFaelligkeitsdatum(java.time.LocalDate.of(2025, 1, 29));
                daten.setKundenName("Max Mustermann");
                daten.setGeschaeftsdokumentart("Rechnung");
                daten.setBetrag(new BigDecimal("119.00"));

                Path ergebnis = service.erzeuge(quelle.toString(), daten);
                try {
                    String bericht = new org.mustangproject.validator.ZUGFeRDValidator().validate(ergebnis.toString());
                    // Nur <error>-Meldungen zum Verkäufer zählen: Käuferanschrift (BR-11) und
                    // PDF/A-Schriften sind nicht Teil dieser Prüfung.
                    var fehler = java.util.regex.Pattern.compile("<error [^>]*>([^<]*)</error>")
                            .matcher(bericht).results().map(r -> r.group(1)).toList();
                    assertThat(fehler)
                            .as("Validator-Fehler (nurSteuernummer=%s)", nurSteuernummer)
                            .noneMatch(f -> f.contains("BR-CO-26") || f.contains("BR-S-02")
                                    || f.toLowerCase(java.util.Locale.ROOT).contains("seller"));
                } finally {
                    Files.deleteIfExists(ergebnis);
                }
            }
        }

        private String erzeugeUndLiesXml(Path dir, ZugferdDaten daten) throws Exception {
            when(firmeninformationService.getFirmeninformation()).thenReturn(musterbetrieb());
            Path quelle = dir.resolve("betrag-quelle.pdf");
            schreibeEinfachesPdf(quelle);
            Path ergebnis = service.erzeuge(quelle.toString(), daten);
            try {
                return new org.mustangproject.ZUGFeRD.ZUGFeRDImporter(ergebnis.toString()).getUTF8();
            } finally {
                Files.deleteIfExists(ergebnis);
            }
        }

        @Test
        void bruttoBetragWirdEinmalAlsNettoPlusNeunzehnProzentAusgewiesen(@TempDir Path dir) throws Exception {
            ZugferdDaten daten = new ZugferdDaten();
            daten.setRechnungsnummer("RE-2025-001");
            daten.setKundenName("Max Mustermann");
            daten.setBetrag(new BigDecimal("119.00"));

            String xml = erzeugeUndLiesXml(dir, daten);

            assertThat(xml).containsPattern("<ram:TaxBasisTotalAmount>100\\.00<")
                    .containsPattern("<ram:GrandTotalAmount>119\\.00<")
                    .containsPattern("<ram:DuePayableAmount>119\\.00<");
        }

        @Test
        void nettoUndSteuersatzAusDenDatenWerdenUebernommen(@TempDir Path dir) throws Exception {
            ZugferdDaten daten = new ZugferdDaten();
            daten.setRechnungsnummer("RE-2025-002");
            daten.setKundenName("Max Mustermann");
            daten.setBetragNetto(new BigDecimal("100.00"));
            daten.setMwstSatz(new BigDecimal("7"));
            daten.setBetrag(new BigDecimal("107.00"));

            String xml = erzeugeUndLiesXml(dir, daten);

            assertThat(xml).containsPattern("<ram:TaxBasisTotalAmount>100\\.00<")
                    .containsPattern("<ram:GrandTotalAmount>107\\.00<");
        }

        /** Basis-PDF mit Text (Seite braucht /Resources, sonst scheitert Mustangs A3-Exporter). */
        private void schreibeEinfachesPdf(Path ziel) throws Exception {
            try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
                org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage(
                        org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
                doc.addPage(page);
                try (org.apache.pdfbox.pdmodel.PDPageContentStream cs =
                        new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(
                            org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(50, 750);
                    cs.showText("Rechnung Max Mustermann");
                    cs.endText();
                }
                doc.save(ziel.toFile());
            }
        }
    }
}
