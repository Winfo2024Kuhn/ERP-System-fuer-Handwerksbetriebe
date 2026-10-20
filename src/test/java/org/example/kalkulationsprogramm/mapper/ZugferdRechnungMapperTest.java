package org.example.kalkulationsprogramm.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdAbzug;
import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdArtikelPosition;
import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdBelegverweis;
import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdDaten;
import org.example.kalkulationsprogramm.service.RechnungPdfService.AbrechnungspositionPdfDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.AbrechnungsverlaufPdfDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.ContentBlockDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.KopfdatenDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.RechnungDto;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Nur Dummy-Daten (Max Mustermann). */
class ZugferdRechnungMapperTest {

    private static final ZugferdRechnungMapper MAPPER = new ZugferdRechnungMapper();
    private static final LocalDate RECHNUNGSDATUM = LocalDate.of(2026, 10, 12);
    private static final LocalDate LEISTUNGSDATUM = LocalDate.of(2026, 10, 5);

    private static KopfdatenDto kopf(String dokumentTyp, String anschrift, Integer zahlungszielTage) {
        return new KopfdatenDto("RE-2026-0042", RECHNUNGSDATUM, LEISTUNGSDATUM, "Max Mustermann", anschrift,
                "Bad", "K-1001", dokumentTyp, null, null, null, null, null, zahlungszielTage);
    }

    private static ContentBlockDto leistung(String pos, String titel, String html, String menge, String einheit,
            String preis, String gesamt, boolean optional, String rabatt) {
        return new ContentBlockDto("SERVICE", null, false, 10, pos, titel, html, new BigDecimal(menge), einheit,
                new BigDecimal(preis), new BigDecimal(gesamt), optional, null,
                rabatt != null ? new BigDecimal(rabatt) : null, null);
    }

    private static ContentBlockDto text(String html) {
        return new ContentBlockDto("TEXT", html, false, 10, null, null, null, null, null, null, null, false, null, null, null);
    }

    private static RechnungDto rechnung(KopfdatenDto kopf, List<ContentBlockDto> bloecke, String globalRabatt,
            AbrechnungsverlaufPdfDto verlauf, String betragNetto) {
        return new RechnungDto(null, kopf, bloecke, List.of(), null, null, null,
                globalRabatt != null ? new BigDecimal(globalRabatt) : null, verlauf,
                betragNetto != null ? new BigDecimal(betragNetto) : null, null);
    }

    private static AbrechnungsverlaufPdfDto verlauf(String basisNetto, AbrechnungspositionPdfDto... vorher) {
        return new AbrechnungsverlaufPdfDto("AB-2026-0007", "Auftragsbestätigung", LocalDate.of(2026, 9, 1),
                basisNetto != null ? new BigDecimal(basisNetto) : null, List.of(vorher));
    }

    private static AbrechnungspositionPdfDto vorher(String nummer, String netto) {
        return new AbrechnungspositionPdfDto(nummer, "ABSCHLAGSRECHNUNG", LocalDate.of(2026, 9, 15),
                new BigDecimal(netto), 1);
    }

    @Nested
    class Kopfdaten {

        @Test
        void uebernimmtNummerDatenKundeUndSatz() {
            ZugferdDaten daten = MAPPER.ausRechnung(
                    rechnung(kopf("Rechnung", null, 30), List.of(), null, null, null));

            assertThat(daten.getRechnungsnummer()).isEqualTo("RE-2026-0042");
            assertThat(daten.getRechnungsdatum()).isEqualTo(RECHNUNGSDATUM);
            assertThat(daten.getLeistungsdatum()).isEqualTo(LEISTUNGSDATUM);
            assertThat(daten.getKundenName()).isEqualTo("Max Mustermann");
            assertThat(daten.getKundennummer()).isEqualTo("K-1001");
            assertThat(daten.getGeschaeftsdokumentart()).isEqualTo("Rechnung");
            assertThat(daten.getMwstSatz()).isEqualByComparingTo("19");
        }

        private final List<ContentBlockDto> wartung = List.of(
                leistung("1", "Wartung", null, "1", "psch", "100", "100.00", false, null));

        @Test
        void faelligkeitFolgtDemZahlungszielDesDokuments() {
            ZugferdDaten daten = MAPPER.ausRechnung(
                    rechnung(kopf("Rechnung", null, 30), wartung, null, null, null));

            assertThat(daten.getFaelligkeitsdatum()).isEqualTo(RECHNUNGSDATUM.plusDays(30));
        }

        @Test
        void ohneZahlungszielGiltDerEditorStandardVonAchtTagen() {
            ZugferdDaten daten = MAPPER.ausRechnung(
                    rechnung(kopf("Schlussrechnung", null, null), wartung, null, null, null));

            assertThat(daten.getFaelligkeitsdatum()).isEqualTo(RECHNUNGSDATUM.plusDays(8));
        }

        @Test
        void angebotGutschriftUndStornoBekommenKeinZahlungsziel_sondernDenHinweisKeineZahlung() {
            for (String art : new String[] { "Angebot", "Auftragsbestätigung", "Gutschrift", "Stornorechnung", " ", null }) {
                ZugferdDaten daten = MAPPER.ausRechnung(rechnung(kopf(art, null, 14), wartung, null, null, null));
                assertThat(daten.getFaelligkeitsdatum()).as(art).isNull();
                // BR-CO-25: ein positiver Betrag braucht Fälligkeit oder Zahlungsbedingung
                assertThat(daten.getZahlungsbedingung()).as(art).isEqualTo("Keine Zahlung fällig.");
            }
            assertThat(MAPPER.ausRechnung(rechnung(kopf("Rechnung", null, 14), wartung, null, null, null))
                    .getZahlungsbedingung()).isNull();
        }

        @Test
        void ohneZahlbetragKeinZahlungsziel() {
            ZugferdDaten leer = MAPPER.ausRechnung(rechnung(kopf("Rechnung", null, 14), List.of(), null, null, null));
            // Abschläge decken die Schlussrechnung schon ganz
            ZugferdDaten ausgeglichen = MAPPER.ausRechnung(rechnung(kopf("Schlussrechnung", null, 14), wartung, null,
                    verlauf("100", vorher("AR-2026-0001", "100")), null));

            assertThat(leer.getFaelligkeitsdatum()).isNull();
            assertThat(ausgeglichen.getFaelligkeitsdatum()).isNull();
            assertThat(leer.getZahlungsbedingung()).isNull();
            assertThat(ausgeglichen.getZahlungsbedingung()).isNull();
        }
    }

    @Nested
    class Positionen {

        @Test
        void jedeBerechnetePositionWirdEineZeile_textUndBedarfspositionenNicht() {
            List<ContentBlockDto> bloecke = List.of(
                    text("<p>Sehr geehrter Herr Mustermann,</p>"),
                    leistung("1", "Fliesen verlegen", "<p>inkl. <strong>Kleber</strong> &amp; Fugen</p>",
                            "12.5", "m²", "47.55", "534.94", false, "10"),
                    leistung("2", "Anfahrt", null, "1", "psch", "35", "35.00", false, null),
                    leistung("3", "Bedarfsposition Abdichtung", null, "1", "psch", "100", "100.00", true, null));

            ZugferdDaten daten = MAPPER.ausRechnung(rechnung(kopf("Rechnung", null, 14), bloecke, null, null, null));

            assertThat(daten.getArtikelPositionen()).hasSize(2);
            ZugferdArtikelPosition fliesen = daten.getArtikelPositionen().get(0);
            assertThat(fliesen.getBezeichnung()).isEqualTo("Fliesen verlegen");
            assertThat(fliesen.getBeschreibung()).isEqualTo("inkl. Kleber & Fugen");
            assertThat(fliesen.getMenge()).isEqualByComparingTo("12.5");
            assertThat(fliesen.getMengeneinheit()).isEqualTo("m²");
            assertThat(fliesen.getEinzelpreis()).isEqualByComparingTo("47.55");
            assertThat(fliesen.getGesamtpreisNetto()).isEqualByComparingTo("534.94");
            assertThat(fliesen.getRabattProzent()).isEqualByComparingTo("10");
            assertThat(fliesen.getMwstSatz()).isEqualByComparingTo("19");
            assertThat(daten.getArtikelPositionen().get(1).getBeschreibung()).isNull();
            assertThat(daten.getAbzuege()).isEmpty();
        }

        @Test
        void positionOhneTitelHeisstNachIhrerNummer() {
            List<ContentBlockDto> bloecke = List.of(
                    leistung("2.1", " ", null, "1", "Stk", "10", "10.00", false, null),
                    leistung(null, null, "<p> </p>", "1", "Stk", "10", "10.00", false, null));

            ZugferdDaten daten = MAPPER.ausRechnung(rechnung(kopf("Rechnung", null, 14), bloecke, null, null, null));

            assertThat(daten.getArtikelPositionen()).extracting(ZugferdArtikelPosition::getBezeichnung)
                    .containsExactly("Position 2.1", "Position");
            assertThat(daten.getArtikelPositionen().get(1).getBeschreibung()).isNull();
        }

        @Test
        void nachDemAbschlussBlockZaehltNichtsMehr_wieImPdf() {
            ContentBlockDto abschluss = new ContentBlockDto("CLOSURE", null, false, 0, null, null, null, null, null,
                    null, null, false, null, null, null);
            List<ContentBlockDto> bloecke = List.of(
                    leistung("1", "Wartung", null, "1", "psch", "100", "100.00", false, null),
                    abschluss,
                    leistung("2", "Nachtrag hinter dem Abschluss", null, "1", "psch", "50", "50.00", false, null));

            ZugferdDaten daten = MAPPER.ausRechnung(rechnung(kopf("Rechnung", null, 14), bloecke, null, null, null));

            assertThat(daten.getArtikelPositionen()).extracting(ZugferdArtikelPosition::getBezeichnung)
                    .containsExactly("Wartung");
        }

        @Test
        void langtextBehaeltAbsaetzeUndListenpunkte() {
            assertThat(ZugferdRechnungMapper.ohneHtml(
                    "<p>Fliesen <strong>verlegen</strong></p><p>inkl. Kleber<br>und Fugen</p><ul><li>Wand</li><li>Boden</li></ul>"))
                    .isEqualTo("Fliesen verlegen\ninkl. Kleber\nund Fugen\nWand\nBoden");
            assertThat(ZugferdRechnungMapper.ohneHtml("nur Text &amp; mehr")).isEqualTo("nur Text & mehr");
            assertThat(ZugferdRechnungMapper.ohneHtml("<script>alert(1)</script>")).isNull();
        }

        @Test
        void ohneBloeckeEntstehenKeinePositionen() {
            ZugferdDaten daten = MAPPER.ausRechnung(rechnung(kopf("Rechnung", null, 14), null, null, null, null));

            assertThat(daten.getArtikelPositionen()).isEmpty();
        }
    }

    @Nested
    class Abzuege {

        @Test
        void pauschalrabattWirdAbzugMitDemBetragAusDemPdf() {
            // 100,10 € × 5 % = 5,005 → im PDF als Rabattzeile 5,01 € (RabattRechner)
            List<ContentBlockDto> bloecke = List.of(leistung("1", "Wartung", null, "1", "psch", "100.10", "100.10", false, null));

            ZugferdDaten daten = MAPPER.ausRechnung(rechnung(kopf("Rechnung", null, 14), bloecke, "5", null, null));

            assertThat(daten.getAbzuege()).singleElement().satisfies(abzug -> {
                assertThat(abzug.grund()).isEqualTo("Rabatt 5 %");
                assertThat(abzug.betragNetto()).isEqualByComparingTo("5.01");
                assertThat(abzug.mwstSatz()).isEqualByComparingTo("19");
            });
        }

        @Test
        void schlussrechnungZiehtDieBereitsGestelltenRechnungenAb() {
            List<ContentBlockDto> bloecke = List.of(leistung("1", "Fliesen verlegen", null, "35", "m²", "47.55", "1664.25", false, null));
            AbrechnungsverlaufPdfDto verlauf = verlauf("2000", vorher("AR-2026-0001", "600"),
                    vorher("AR-2026-0002", "0"), vorher(null, "400"));

            ZugferdDaten daten = MAPPER.ausRechnung(
                    rechnung(kopf("Schlussrechnung", null, 14), bloecke, null, verlauf, null));

            assertThat(daten.getArtikelPositionen()).hasSize(1);
            assertThat(daten.getAbzuege()).extracting(ZugferdAbzug::grund).containsExactly(
                    "Bereits berechnet: Abschlagsrechnung Nr. AR-2026-0001 vom 15.09.2026",
                    "Bereits berechnet: Abschlagsrechnung vom 15.09.2026");
            assertThat(daten.getAbzuege()).extracting(ZugferdAbzug::betragNetto)
                    .usingElementComparator(BigDecimal::compareTo)
                    .containsExactly(new BigDecimal("600"), new BigDecimal("400"));
            // Verweise auf die Vorrechnungen (BG-3) – nur die mit Nummer
            assertThat(daten.getVorrechnungen()).containsExactly(
                    new ZugferdBelegverweis("AR-2026-0001", LocalDate.of(2026, 9, 15)),
                    new ZugferdBelegverweis("AR-2026-0002", LocalDate.of(2026, 9, 15)));
        }

        @Test
        void vorrechnungOhneTypHeisstRechnung() {
            AbrechnungsverlaufPdfDto verlauf = verlauf("2000",
                    new AbrechnungspositionPdfDto("RE-2026-0003", null, null, new BigDecimal("100"), null));

            ZugferdDaten daten = MAPPER.ausRechnung(
                    rechnung(kopf("Schlussrechnung", null, 14), List.of(), null, verlauf, null));

            assertThat(daten.getAbzuege()).extracting(ZugferdAbzug::grund)
                    .containsExactly("Bereits berechnet: Rechnung Nr. RE-2026-0003");
        }

        @Test
        void ohneBasisdokumentZiehtAuchDieSchlussrechnungNichtsAb_wieImPdf() {
            ZugferdDaten daten = MAPPER.ausRechnung(
                    rechnung(kopf("Schlussrechnung", null, 14), List.of(), null, verlauf("0", vorher("AR-1", "600")), null));

            assertThat(daten.getAbzuege()).isEmpty();
        }

        @Test
        void andereRechnungenZiehenVorrechnungenNichtAb() {
            ZugferdDaten daten = MAPPER.ausRechnung(
                    rechnung(kopf("Teilrechnung", null, 14), List.of(), null, verlauf("2000", vorher("AR-1", "600")), null));

            assertThat(daten.getAbzuege()).isEmpty();
        }
    }

    @Nested
    class Abschlag {

        @Test
        void abschlagWirdEineZeileUeberDenFestenBetrag_mitBezugAufDenAuftrag() {
            List<ContentBlockDto> auftragspositionen = List.of(
                    leistung("1", "Fliesen verlegen", null, "35", "m²", "47.55", "1664.25", false, null));

            ZugferdDaten daten = MAPPER.ausRechnung(rechnung(kopf("Abschlagsrechnung", null, 14),
                    auftragspositionen, "2", verlauf("2000"), "1000"));

            assertThat(daten.getArtikelPositionen()).singleElement().satisfies(position -> {
                assertThat(position.getBezeichnung()).isEqualTo("Abschlagsrechnung");
                assertThat(position.getBeschreibung()).isEqualTo("zu Auftragsbestätigung Nr. AB-2026-0007 vom 01.09.2026");
                assertThat(position.getMenge()).isEqualByComparingTo("1");
                assertThat(position.getMengeneinheit()).isEqualTo("psch");
                assertThat(position.getEinzelpreis()).isEqualByComparingTo("1000.00");
                assertThat(position.getGesamtpreisNetto()).isEqualByComparingTo("1000.00");
            });
            // Der Pauschalrabatt wirkt im PDF auch auf den Abschlag
            assertThat(daten.getAbzuege()).singleElement()
                    .satisfies(abzug -> assertThat(abzug.betragNetto()).isEqualByComparingTo("20.00"));
        }

        @Test
        void abschlagOhneAuftragsbezugHatKeineBeschreibung() {
            AbrechnungsverlaufPdfDto ohneNummer = new AbrechnungsverlaufPdfDto(null, "Angebot", null,
                    new BigDecimal("2000"), List.of());

            ZugferdDaten mitTyp = MAPPER.ausRechnung(
                    rechnung(kopf(" ", null, 14), List.of(), null, ohneNummer, "500"));
            ZugferdDaten ohneVerlauf = MAPPER.ausRechnung(
                    rechnung(kopf("Abschlagsrechnung", null, 14), List.of(), null, null, "500"));

            assertThat(mitTyp.getArtikelPositionen().getFirst().getBezeichnung()).isEqualTo("Abschlag");
            assertThat(mitTyp.getArtikelPositionen().getFirst().getBeschreibung()).isEqualTo("zu Angebot");
            assertThat(ohneVerlauf.getArtikelPositionen().getFirst().getBeschreibung()).isNull();
        }
    }

    @Nested
    class Anschrift {

        private ZugferdDaten zerlege(String anschrift) {
            ZugferdDaten daten = new ZugferdDaten();
            ZugferdRechnungMapper.uebernimmAnschrift(daten, "Max Mustermann", anschrift);
            return daten;
        }

        @Test
        void strassePlzUndOrt() {
            ZugferdDaten daten = zerlege("Musterweg 7\n54321 Beispielstadt");

            assertThat(daten.getKundenStrasse()).isEqualTo("Musterweg 7");
            assertThat(daten.getKundenPlz()).isEqualTo("54321");
            assertThat(daten.getKundenOrt()).isEqualTo("Beispielstadt");
            assertThat(daten.getKundenLand()).isNull();
            assertThat(daten.getKundenAdresszusatz()).isNull();
        }

        @Test
        void derNameAlsErsteZeileIstKeineAdresszeile_weitereZeilenWerdenZusatz() {
            assertThat(zerlege("max mustermann\r\nMusterweg 7\r\n54321 Beispielstadt").getKundenAdresszusatz()).isNull();

            ZugferdDaten mitFirma = zerlege("Musterbau GmbH\n z. H. Max Mustermann \n\nMusterweg 7\n54321 Beispielstadt");
            assertThat(mitFirma.getKundenAdresszusatz()).isEqualTo("Musterbau GmbH, z. H. Max Mustermann");
            assertThat(mitFirma.getKundenStrasse()).isEqualTo("Musterweg 7");
        }

        @Test
        void landAusKennzeichenOderLaenderzeile() {
            assertThat(zerlege("Musterweg 7\nA-1010 Wien").getKundenLand()).isEqualTo("AT");
            assertThat(zerlege("Musterweg 7\nD-54321 Beispielstadt").getKundenLand()).isEqualTo("DE");
            assertThat(zerlege("Musterweg 7\nCH-8000 Zürich").getKundenLand()).isEqualTo("CH");
            assertThat(zerlege("Musterweg 7\n8000 Zürich\nSchweiz").getKundenLand()).isEqualTo("CH");
            assertThat(zerlege("Musterweg 7\n1010 Wien\nAustria").getKundenLand()).isEqualTo("AT");
            // Unbekanntes Kürzel oder unbekannte Zeile: kein Land behaupten (dann gilt DE)
            assertThat(zerlege("Musterweg 7\nE-28001 Madrid").getKundenLand()).isEqualTo("ES");
            assertThat(zerlege("Musterweg 7\nFL-9490 Vaduz").getKundenLand()).isEqualTo("LI");
            assertThat(zerlege("Musterweg 7\nZZ-54321 Beispielstadt").getKundenLand()).isNull();
        }

        @Test
        void zeilenUnterDemOrtWerdenZusatz_ausserDerLaenderzeile() {
            ZugferdDaten hinterhaus = zerlege("Musterweg 7\n54321 Beispielstadt\nHinterhaus");
            assertThat(hinterhaus.getKundenLand()).isNull();
            assertThat(hinterhaus.getKundenAdresszusatz()).isEqualTo("Hinterhaus");

            ZugferdDaten mitLand = zerlege("c/o Musterbau GmbH\nMusterweg 7\n8000 Zürich\nSchweiz\n2. OG");
            assertThat(mitLand.getKundenLand()).isEqualTo("CH");
            assertThat(mitLand.getKundenAdresszusatz()).isEqualTo("c/o Musterbau GmbH, 2. OG");
        }

        @Test
        void nurPlzUndOrt_ohneStrasse() {
            ZugferdDaten daten = zerlege("54321 Beispielstadt");

            assertThat(daten.getKundenStrasse()).isNull();
            assertThat(daten.getKundenOrt()).isEqualTo("Beispielstadt");
        }

        @Test
        void ohneErkennbarePlzLandetDerTextAlsStrasseUndZusatz() {
            ZugferdDaten daten = zerlege("Musterweg 7\nPostfach 12");

            assertThat(daten.getKundenStrasse()).isEqualTo("Musterweg 7");
            assertThat(daten.getKundenAdresszusatz()).isEqualTo("Postfach 12");
            assertThat(daten.getKundenPlz()).isNull();
            assertThat(zerlege("Musterweg 7").getKundenAdresszusatz()).isNull();
        }

        @Test
        void leereAnschriftOderNurDerName() {
            for (String leer : new String[] { null, "  ", "Max Mustermann" }) {
                ZugferdDaten daten = zerlege(leer);
                assertThat(daten.getKundenStrasse()).isNull();
                assertThat(daten.getKundenPlz()).isNull();
            }
            ZugferdDaten ohneName = new ZugferdDaten();
            ZugferdRechnungMapper.uebernimmAnschrift(ohneName, null, "Musterweg 7\n54321 Beispielstadt");
            assertThat(ohneName.getKundenStrasse()).isEqualTo("Musterweg 7");
        }

        @Test
        void schadhafteEingabenBleibenReinerText() {
            ZugferdDaten daten = zerlege("<script>alert(1)</script>\n'; DROP TABLE x; --\n54321 Beispielstadt");

            assertThat(daten.getKundenAdresszusatz()).isEqualTo("<script>alert(1)</script>");
            assertThat(daten.getKundenStrasse()).isEqualTo("'; DROP TABLE x; --");
        }
    }

    @Nested
    class GutschriftBezug {

        private KopfdatenDto kopfMitBezug(String typ, String bezug, String bezugDatum) {
            return new KopfdatenDto("GU-2026/10/00001", RECHNUNGSDATUM, LEISTUNGSDATUM, "Max Mustermann", null,
                    "Gutschrift", "K-1001", typ, bezug, null, null, "Rechnung", bezugDatum, null);
        }

        private final List<ContentBlockDto> nachlass = List.of(
                leistung("1", "Preisnachlass", null, "1", "psch", "100", "100", false, null));

        @Test
        void gutschriftVerweistAufDieRechnung_BT25() {
            ZugferdDaten deutsch = MAPPER.ausRechnung(rechnung(
                    kopfMitBezug("Gutschrift", " RE-2026/10/00001 ", "01.10.2026"), nachlass, null, null, null));
            assertThat(deutsch.getVorrechnungen())
                    .containsExactly(new ZugferdBelegverweis("RE-2026/10/00001", LocalDate.of(2026, 10, 1)));

            ZugferdDaten iso = MAPPER.ausRechnung(rechnung(
                    kopfMitBezug("Stornorechnung", "RE-2026/10/00002", "2026-10-02"), nachlass, null, null, null));
            assertThat(iso.getVorrechnungen())
                    .containsExactly(new ZugferdBelegverweis("RE-2026/10/00002", LocalDate.of(2026, 10, 2)));
        }

        @Test
        void unlesbaresDatum_verweisOhneDatum_rechnungOhneBezug() {
            ZugferdDaten kaputt = MAPPER.ausRechnung(rechnung(
                    kopfMitBezug("Gutschrift", "RE-1", "irgendwann"), nachlass, null, null, null));
            assertThat(kaputt.getVorrechnungen()).containsExactly(new ZugferdBelegverweis("RE-1", null));

            ZugferdDaten ohneDatum = MAPPER.ausRechnung(rechnung(
                    kopfMitBezug("Gutschrift", "RE-1", "  "), nachlass, null, null, null));
            assertThat(ohneDatum.getVorrechnungen()).containsExactly(new ZugferdBelegverweis("RE-1", null));

            ZugferdDaten rechnungMitBezug = MAPPER.ausRechnung(rechnung(
                    kopfMitBezug("Rechnung", "AB-2026-0007", "01.09.2026"), nachlass, null, null, null));
            assertThat(rechnungMitBezug.getVorrechnungen()).isEmpty();

            ZugferdDaten ohneBezug = MAPPER.ausRechnung(rechnung(
                    kopfMitBezug("Gutschrift", null, null), nachlass, null, null, null));
            assertThat(ohneBezug.getVorrechnungen()).isEmpty();
        }
    }
}
