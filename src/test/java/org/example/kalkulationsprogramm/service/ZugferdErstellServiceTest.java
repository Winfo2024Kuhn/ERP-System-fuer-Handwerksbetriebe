package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import org.example.kalkulationsprogramm.dto.FirmeninformationDto;
import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdAbzug;
import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdArtikelPosition;
import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdBelegverweis;
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
    class Steuersatz {

        @Test
        void prozentBleibtUnveraendert() {
            assertThat(ZugferdErstellService.alsProzent(new BigDecimal("7"))).isEqualByComparingTo("7");
        }

        @Test
        void anteilWirdZuProzent() {
            assertThat(ZugferdErstellService.alsProzent(new BigDecimal("0.07"))).isEqualByComparingTo("7");
        }

        @Test
        void nullUndEinsBleibenProzent() {
            assertThat(ZugferdErstellService.alsProzent(BigDecimal.ZERO)).isEqualByComparingTo("0");
            assertThat(ZugferdErstellService.alsProzent(BigDecimal.ONE)).isEqualByComparingTo("1");
        }

        @Test
        void ohneAngabeGiltRegelsatz() {
            assertThat(ZugferdErstellService.alsProzent(null)).isEqualByComparingTo("19");
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
                    // Nur der XML-Teil zählt: die Schriften des Test-PDFs sind kein PDF/A.
                    assertThat(xmlFehler(bericht))
                            .as("Validator-Fehler (nurSteuernummer=%s)", nurSteuernummer)
                            .isEmpty();
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

        @Test
        void steuersatzAlsAnteilWirdAlsProzentGelesen(@TempDir Path dir) throws Exception {
            ZugferdDaten daten = new ZugferdDaten();
            daten.setRechnungsnummer("RE-2025-003");
            daten.setKundenName("Max Mustermann");
            daten.setMwstSatz(new BigDecimal("0.19"));
            daten.setBetrag(new BigDecimal("119.00"));

            String xml = erzeugeUndLiesXml(dir, daten);

            assertThat(xml).containsPattern("<ram:RateApplicablePercent>19(\\.0+)?<")
                    .containsPattern("<ram:TaxBasisTotalAmount>100\\.00<")
                    .containsPattern("<ram:GrandTotalAmount>119\\.00<");
        }

        /** Erzeugt die E-Rechnung und liefert XML und Validator-Bericht. */
        private String[] erzeugeUndPruefe(Path dir, ZugferdDaten daten) throws Exception {
            when(firmeninformationService.getFirmeninformation()).thenReturn(musterbetrieb());
            Path quelle = dir.resolve("pruef-quelle.pdf");
            schreibeEinfachesPdf(quelle);
            Path ergebnis = service.erzeuge(quelle.toString(), daten);
            try {
                return new String[] {
                        new org.mustangproject.ZUGFeRD.ZUGFeRDImporter(ergebnis.toString()).getUTF8(),
                        new org.mustangproject.validator.ZUGFeRDValidator().validate(ergebnis.toString()) };
            } finally {
                Files.deleteIfExists(ergebnis);
            }
        }

        private static ZugferdArtikelPosition position(String bezeichnung, String menge, String einheit,
                String preis, String gesamt, String satz) {
            ZugferdArtikelPosition position = new ZugferdArtikelPosition(null, bezeichnung, new BigDecimal(menge),
                    einheit, new BigDecimal(preis), null, gesamt != null ? new BigDecimal(gesamt) : null);
            position.setMwstSatz(satz != null ? new BigDecimal(satz) : null);
            return position;
        }

        private static ZugferdDaten rechnungAnMaxMustermann() {
            ZugferdDaten daten = new ZugferdDaten();
            daten.setRechnungsnummer("RE-2026-0042");
            daten.setRechnungsdatum(LocalDate.of(2026, 10, 12));
            daten.setLeistungsdatum(LocalDate.of(2026, 10, 5));
            daten.setFaelligkeitsdatum(LocalDate.of(2026, 11, 11));
            daten.setGeschaeftsdokumentart("Rechnung");
            daten.setKundenName("Max Mustermann");
            daten.setKundennummer("K-1001");
            daten.setKundenStrasse("Musterweg 7");
            daten.setKundenPlz("54321");
            daten.setKundenOrt("Beispielstadt");
            daten.setMwstSatz(new BigDecimal("19"));
            return daten;
        }

        @Test
        void jedePositionWirdEineZeileMitEinheitUndEigenemSteuersatz(@TempDir Path dir) throws Exception {
            ZugferdDaten daten = rechnungAnMaxMustermann();
            ZugferdArtikelPosition fliesen = position("Fliesen verlegen", "12.5", "m²", "47.55", "534.94", "19");
            fliesen.setRabattProzent(new BigDecimal("10"));
            fliesen.setBeschreibung("inkl. Kleber");
            daten.getArtikelPositionen().add(fliesen);
            daten.getArtikelPositionen().add(position("Fachbuch Fliesenpflege", "1", "Stk", "20.00", "20.00", "0.07"));

            String[] ergebnis = erzeugeUndPruefe(dir, daten);
            String xml = ergebnis[0];

            assertThat(xmlFehler(ergebnis[1])).isEmpty();
            assertThat(xml).contains("<ram:Name>Fliesen verlegen</ram:Name>", "<ram:Description>inkl. Kleber</ram:Description>",
                            "<ram:Name>Fachbuch Fliesenpflege</ram:Name>",
                            "unitCode=\"MTK\">12.5000</ram:BilledQuantity>", "unitCode=\"H87\">1.0000</ram:BilledQuantity>")
                    // Positionsrabatt: 12,5 × 47,55 = 594,38 − 59,44 = 534,94 – genau wie im PDF
                    .contains("<ram:ActualAmount>59.44</ram:ActualAmount>", "<ram:Reason>Rabatt 10 %</ram:Reason>",
                            "<ram:LineTotalAmount>534.94</ram:LineTotalAmount>")
                    // Steueraufschlüsselung je Satz: 534,94 × 19 % und 20,00 × 7 %
                    .containsPattern("<ram:CalculatedAmount>101\\.64</ram:CalculatedAmount>\\s*<ram:TypeCode>VAT</ram:TypeCode>\\s*<ram:BasisAmount>534\\.94<")
                    .containsPattern("<ram:CalculatedAmount>1\\.40</ram:CalculatedAmount>\\s*<ram:TypeCode>VAT</ram:TypeCode>\\s*<ram:BasisAmount>20\\.00<")
                    .contains("<ram:TaxBasisTotalAmount>554.94</ram:TaxBasisTotalAmount>",
                            "<ram:GrandTotalAmount>657.98</ram:GrandTotalAmount>");
        }

        @Test
        void kaeuferanschriftZahlungszielUndLeistungsdatum(@TempDir Path dir) throws Exception {
            ZugferdDaten daten = rechnungAnMaxMustermann();
            daten.setKundenAdresszusatz("z. H. Max Mustermann");
            daten.getArtikelPositionen().add(position("Anfahrt", "1", "psch", "35", "35.00", null));

            String[] ergebnis = erzeugeUndPruefe(dir, daten);
            String xml = ergebnis[0];

            assertThat(xmlFehler(ergebnis[1])).isEmpty();
            String kaeufer = xml.substring(xml.indexOf("<ram:BuyerTradeParty>"), xml.indexOf("</ram:BuyerTradeParty>"));
            assertThat(kaeufer).contains("<ram:ID>K-1001</ram:ID>", "<ram:PostcodeCode>54321</ram:PostcodeCode>",
                    "<ram:LineOne>Musterweg 7</ram:LineOne>", "<ram:LineTwo>z. H. Max Mustermann</ram:LineTwo>",
                    "<ram:CityName>Beispielstadt</ram:CityName>", "<ram:CountryID>DE</ram:CountryID>");
            assertThat(xml).contains("<ram:Description>Zahlbar bis 11.11.2026.</ram:Description>",
                            "<udt:DateTimeString format=\"102\">20261111</udt:DateTimeString>",
                            "<udt:DateTimeString format=\"102\">20261005</udt:DateTimeString>")
                    .doesNotContain("Please remit");
        }

        @Test
        void kaeuferOhneAnschriftSitztInDeutschland_leereKundennummerFaelltWeg(@TempDir Path dir) throws Exception {
            ZugferdDaten daten = new ZugferdDaten();
            daten.setRechnungsnummer("RE-2026-0043");
            daten.setKundenName("<script>alert(1)</script>");
            daten.setKundennummer(" ");
            daten.setKundenLand("at");
            daten.setBetrag(new BigDecimal("119.00"));

            String xml = erzeugeUndPruefe(dir, daten)[0];

            String kaeufer = xml.substring(xml.indexOf("<ram:BuyerTradeParty>"), xml.indexOf("</ram:BuyerTradeParty>"));
            assertThat(kaeufer).doesNotContain("<ram:ID>").contains("<ram:CountryID>AT</ram:CountryID>")
                    .contains("&lt;script&gt;").doesNotContain("<script>");
        }

        @Test
        void negativerEinzelpreisWirdZurNegativenMenge(@TempDir Path dir) throws Exception {
            ZugferdDaten daten = rechnungAnMaxMustermann();
            daten.getArtikelPositionen().add(position("Fliesen verlegen", "10", "m²", "47.55", "475.50", null));
            daten.getArtikelPositionen().add(position("Material vom Kunden gestellt", "1", "psch", "-50", "-50.00", null));

            String[] ergebnis = erzeugeUndPruefe(dir, daten);
            String xml = ergebnis[0];

            assertThat(xmlFehler(ergebnis[1])).isEmpty();
            assertThat(xml).doesNotContain("<ram:ChargeAmount>-")
                    .contains("<ram:BilledQuantity unitCode=\"LS\">-1.0000</ram:BilledQuantity>",
                            "<ram:LineTotalAmount>-50.00</ram:LineTotalAmount>",
                            "<ram:TaxBasisTotalAmount>425.50</ram:TaxBasisTotalAmount>");
        }

        @Test
        void rabattAufNegativerMengeWirdZuschlag(@TempDir Path dir) throws Exception {
            ZugferdDaten daten = rechnungAnMaxMustermann();
            daten.getArtikelPositionen().add(position("Fliesen verlegen", "10", "m²", "47.55", "475.50", null));
            // −1 × 100 € mit 10 % Rabatt = −90 €: Mustang braucht dafür +10 € Zuschlag
            daten.getArtikelPositionen().add(position("Rücknahme Restmaterial", "-1", "Stk", "100", "-90.00", null));

            String[] ergebnis = erzeugeUndPruefe(dir, daten);

            assertThat(xmlFehler(ergebnis[1])).isEmpty();
            assertThat(ergebnis[0]).contains("<ram:LineTotalAmount>-90.00</ram:LineTotalAmount>",
                    "<ram:TaxBasisTotalAmount>385.50</ram:TaxBasisTotalAmount>");
        }

        @Test
        void stornoWirdGutschriftMitZahlungsbedingungStattFaelligkeit(@TempDir Path dir) throws Exception {
            ZugferdDaten daten = rechnungAnMaxMustermann();
            daten.setGeschaeftsdokumentart("Stornorechnung");
            daten.setFaelligkeitsdatum(null);
            daten.setZahlungsbedingung("Keine Zahlung fällig.");
            daten.getArtikelPositionen().add(position("Fliesen verlegen", "10", "m²", "47.55", "475.50", null));

            String[] ergebnis = erzeugeUndPruefe(dir, daten);

            assertThat(xmlFehler(ergebnis[1])).isEmpty();
            assertThat(ergebnis[0]).contains("<ram:TypeCode>381</ram:TypeCode>",
                            "<ram:Description>Keine Zahlung fällig.</ram:Description>")
                    .doesNotContain("DueDateDateTime");
        }

        @Test
        void rechnungBleibtTypeCode380(@TempDir Path dir) throws Exception {
            ZugferdDaten daten = rechnungAnMaxMustermann();
            daten.setZahlungsbedingung("wird ignoriert, weil fällig");
            daten.getArtikelPositionen().add(position("Anfahrt", "1", "psch", "35", "35.00", null));

            String xml = erzeugeUndPruefe(dir, daten)[0];

            assertThat(xml).contains("<ram:TypeCode>380</ram:TypeCode>", "Zahlbar bis 11.11.2026.")
                    .doesNotContain("wird ignoriert");
        }

        @Test
        void verweistAufVorausgegangeneRechnungen(@TempDir Path dir) throws Exception {
            ZugferdDaten daten = rechnungAnMaxMustermann();
            daten.getArtikelPositionen().add(position("Fliesen verlegen", "35", "m²", "47.55", "1664.25", null));
            daten.getVorrechnungen().add(new ZugferdBelegverweis("AR-2026-0001", LocalDate.of(2026, 9, 15)));
            daten.getVorrechnungen().add(new ZugferdBelegverweis("AR-2026-0002", null));
            daten.getVorrechnungen().add(new ZugferdBelegverweis(" ", null));
            daten.getVorrechnungen().add(null);

            String[] ergebnis = erzeugeUndPruefe(dir, daten);

            assertThat(xmlFehler(ergebnis[1])).isEmpty();
            assertThat(ergebnis[0]).contains("<ram:IssuerAssignedID>AR-2026-0001</ram:IssuerAssignedID>",
                            "<ram:IssuerAssignedID>AR-2026-0002</ram:IssuerAssignedID>",
                            "<qdt:DateTimeString format=\"102\">20260915</qdt:DateTimeString>")
                    .containsOnlyOnce("<ram:IssuerAssignedID>AR-2026-0001</ram:IssuerAssignedID>");
        }

        @Test
        void abzuegeUndZuschlaegeAufDieGanzeRechnung(@TempDir Path dir) throws Exception {
            ZugferdDaten daten = rechnungAnMaxMustermann();
            daten.getArtikelPositionen().add(position("Fliesen verlegen", "35", "m²", "47.55", "1664.25", null));
            daten.getAbzuege().add(new ZugferdAbzug("Rabatt 2 %", new BigDecimal("33.29"), null));
            daten.getAbzuege().add(new ZugferdAbzug("Bereits berechnet: Abschlagsrechnung Nr. AR-2026-0001",
                    new BigDecimal("600.00"), new BigDecimal("19")));
            daten.getAbzuege().add(new ZugferdAbzug(" ", new BigDecimal("-10.00"), null));
            daten.getAbzuege().add(new ZugferdAbzug("ohne Betrag", null, null));
            daten.getAbzuege().add(null);

            String[] ergebnis = erzeugeUndPruefe(dir, daten);
            String xml = ergebnis[0];

            assertThat(xmlFehler(ergebnis[1])).isEmpty();
            // 1664,25 − 33,29 − 600,00 + 10,00 = 1040,96; USt 197,78. Mustang fasst Abzüge mit
            // gleichem Steuersatz zu einem zusammen – beide Gründe stehen dann darin.
            assertThat(xml).containsPattern("<ram:Reason>[^<]*Rabatt 2 %[^<]*</ram:Reason>")
                    .containsPattern("<ram:Reason>[^<]*Bereits berechnet: Abschlagsrechnung Nr\\. AR-2026-0001[^<]*</ram:Reason>")
                    .contains("<ram:Reason>Zuschlag</ram:Reason>",
                            "<ram:AllowanceTotalAmount>633.29</ram:AllowanceTotalAmount>",
                            "<ram:ChargeTotalAmount>10.00</ram:ChargeTotalAmount>",
                            "<ram:TaxBasisTotalAmount>1040.96</ram:TaxBasisTotalAmount>",
                            "<ram:GrandTotalAmount>1238.74</ram:GrandTotalAmount>")
                    .doesNotContain("ohne Betrag");
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

    /**
     * Fehler aus dem XML-Teil des Validator-Berichts (EN 16931 und ZUGFeRD-Schematron). Hinweise
     * zu XRechnung (Leitweg-ID, BT-10) sind keine Fehler; der PDF-Teil bleibt außen vor, weil das
     * Test-PDF keine eingebetteten Schriften hat.
     */
    static java.util.List<String> xmlFehler(String bericht) {
        String xmlTeil = bericht.substring(bericht.indexOf("<xml>"), bericht.indexOf("</xml>"));
        return java.util.regex.Pattern.compile("<error [^>]*>([^<]*)</error>")
                .matcher(xmlTeil).results().map(r -> r.group(1)).toList();
    }
}
