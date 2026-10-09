package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.fasterxml.jackson.databind.ObjectMapper;

class LieferantDokumentAbgleichTest {

    private final LieferantDokumentAbgleich abgleich = new LieferantDokumentAbgleich(new ObjectMapper());
    private long naechsteId = 1;

    private LieferantDokument dokument(LieferantDokumentTyp typ, String nummer, LocalDate datum) {
        LieferantDokument dok = new LieferantDokument();
        dok.setId(naechsteId++);
        dok.setTyp(typ);
        LieferantGeschaeftsdokument gd = new LieferantGeschaeftsdokument();
        gd.setDokumentNummer(nummer);
        gd.setDokumentDatum(datum);
        dok.setGeschaeftsdaten(gd);
        return dok;
    }

    private LieferantDokumentAbgleich.Ergebnis abgleich(LieferantDokument dok, LieferantDokument... kandidaten) {
        return abgleich.findeVorgaenger(dok, dok.getGeschaeftsdaten(), List.of(kandidaten));
    }

    /** Alle gelieferten Vorgänger, egal ob sicher oder Hinweis. */
    private List<LieferantDokument> vorgaenger(LieferantDokument dok, LieferantDokument... kandidaten) {
        LieferantDokumentAbgleich.Ergebnis ergebnis = abgleich(dok, kandidaten);
        List<LieferantDokument> alle = new java.util.ArrayList<>(ergebnis.sicher());
        if (ergebnis.hinweis() != null) {
            alle.add(ergebnis.hinweis());
        }
        return alle;
    }

    @Nested
    class SichereTreffer {

        @Test
        void abNenntAngebotsnummer() {
            LieferantDokument angebot = dokument(LieferantDokumentTyp.ANGEBOT, "AN-2026-0815", LocalDate.of(2026, 3, 1));
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-77001", LocalDate.of(2026, 4, 1));
            ab.getGeschaeftsdaten().setReferenzNummer("AN 2026/0815");

            assertThat(vorgaenger(ab, angebot)).containsExactly(angebot);
        }

        @Test
        void angebotNenntAbRueckwaerts() {
            // Manche Lieferanten schreiben die spätere Vorgangsnummer schon aufs Angebot.
            LieferantDokument angebot = dokument(LieferantDokumentTyp.ANGEBOT, "AN-100", LocalDate.of(2026, 3, 1));
            angebot.getGeschaeftsdaten().setReferenzNummer("AB-77001");
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-77001", LocalDate.of(2026, 4, 1));

            assertThat(vorgaenger(ab, angebot)).containsExactly(angebot);
        }

        @Test
        void ziffernkernGleichTrotzAndererVorsilbe() {
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "4711234", LocalDate.of(2026, 4, 1));
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-9", LocalDate.of(2026, 5, 1));
            rechnung.getGeschaeftsdaten().setReferenzNummer("AB 0004711234");

            assertThat(vorgaenger(rechnung, ab)).containsExactly(ab);
        }

        @Test
        void weitereReferenzenAusKiAntwort() {
            LieferantDokument angebot = dokument(LieferantDokumentTyp.ANGEBOT, "AN-5512", LocalDate.of(2026, 1, 10));
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 2, 1));
            ab.getGeschaeftsdaten().setAiRawJson("{\"weitereReferenzen\":[\"AN-5512\",\"Kd-Nr fehlt\"]}");

            assertThat(vorgaenger(ab, angebot)).containsExactly(angebot);
        }

        @Test
        void gleicheBestellnummerVerknuepftAlleLieferscheine() {
            LieferantDokument ls1 = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "LS-1", LocalDate.of(2026, 5, 2));
            LieferantDokument ls2 = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "LS-2", LocalDate.of(2026, 5, 9));
            ls1.getGeschaeftsdaten().setBestellnummer("B-2026-44");
            ls2.getGeschaeftsdaten().setBestellnummer("B 2026 44");
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2026, 5, 31));
            rechnung.getGeschaeftsdaten().setBestellnummer("B-2026-44");

            assertThat(abgleich(rechnung, ls1, ls2).sicher()).containsExactlyInAnyOrder(ls1, ls2);
        }

        @Test
        void ziffernkernZaehltNurImZeitfenster() {
            LieferantDokument alteAb = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-2024-00123", LocalDate.of(2024, 5, 1));
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2026, 5, 1));
            rechnung.getGeschaeftsdaten().setReferenzNummer("2024 00123");

            assertThat(vorgaenger(rechnung, alteAb)).isEmpty();
        }

        @Test
        void bestellnummerWirdNichtUeberZiffernkernVerglichen() {
            // Gleich aufgebaute Nummernkreise: unsere Bestellung BE-2026-00123,
            // die AB des Lieferanten AB-2026-00123 – kein Bezug.
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-2026-00123", LocalDate.of(2026, 4, 1));
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2026, 4, 20));
            rechnung.getGeschaeftsdaten().setBestellnummer("BE-2026-00123");

            assertThat(vorgaenger(rechnung, ab)).isEmpty();
        }

        @Test
        void bestellnummerGleichDerVorgaengernummerIstEinBezug() {
            LieferantDokument angebot = dokument(LieferantDokumentTyp.ANGEBOT, "AN-4711", LocalDate.of(2026, 2, 1));
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 3, 1));
            ab.getGeschaeftsdaten().setBestellnummer("AN 4711");

            assertThat(abgleich(ab, angebot).sicher()).containsExactly(angebot);
        }

        @Test
        void bestellnummerUeberMonateVerstreutIstNichtTrennscharf() {
            // Früher entschied die Anzahl (mehr als 3 Belege = Kundennummer). Das brach
            // Teillieferungen mit 4 Lieferscheinen. Jetzt entscheidet die Streuung: Eine
            // Kundennummer steht über Monate verteilt auf den Belegen.
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2026, 5, 31));
            rechnung.getGeschaeftsdaten().setBestellnummer("KD-12345");
            LieferantDokument[] lieferscheine = new LieferantDokument[4];
            for (int i = 0; i < lieferscheine.length; i++) {
                lieferscheine[i] = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "LS-" + i, LocalDate.of(2026, 1 + i, 1));
                lieferscheine[i].getGeschaeftsdaten().setBestellnummer("KD-12345");
            }
            LieferantDokument alteRechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-0", LocalDate.of(2025, 11, 3));
            alteRechnung.getGeschaeftsdaten().setBestellnummer("KD-12345");
            List<LieferantDokument> kandidaten = new java.util.ArrayList<>(List.of(lieferscheine));
            kandidaten.add(alteRechnung);

            assertThat(abgleich.findeVorgaenger(rechnung, rechnung.getGeschaeftsdaten(), kandidaten).sicher()).isEmpty();
        }

        @Test
        void textOhneZiffernIstKeineNummer() {
            LieferantDokument angebot = dokument(LieferantDokumentTyp.ANGEBOT, null, LocalDate.of(2026, 3, 1));
            angebot.getGeschaeftsdaten().setBestellnummer("telefonisch");
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 3, 5));
            ab.getGeschaeftsdaten().setBestellnummer("telefonisch");

            assertThat(vorgaenger(ab, angebot)).isEmpty();
        }
    }

    /**
     * Typische Nummernmuster von Lieferanten – alle Nummern erfunden.
     */
    @Nested
    class GemeinsameNummern {

        private List<LieferantDokument> sicher(LieferantDokument rechnung, List<LieferantDokument> kandidaten) {
            return abgleich.findeVorgaenger(rechnung, rechnung.getGeschaeftsdaten(), kandidaten).sicher();
        }

        @Test
        void muster1_teillieferungenMitBestellUndAuftragsnummerAlleVerknuepft() {
            List<LieferantDokument> lieferscheine = new java.util.ArrayList<>();
            int[] tage = { 2, 4, 6, 9 };
            for (int i = 0; i < tage.length; i++) {
                LieferantDokument ls = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "LS-90000" + i, LocalDate.of(2026, 3, tage[i]));
                ls.getGeschaeftsdaten().setBestellnummer("47711111");
                ls.getGeschaeftsdaten().setReferenzNummer("2299000001");
                lieferscheine.add(ls);
            }
            LieferantDokument fremd = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "LS-900099", LocalDate.of(2026, 3, 5));
            fremd.getGeschaeftsdaten().setBestellnummer("Lager");
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-5550001", LocalDate.of(2026, 3, 31));
            rechnung.getGeschaeftsdaten().setBestellnummer("47711111");
            rechnung.getGeschaeftsdaten().setReferenzNummer("2299000001");
            List<LieferantDokument> kandidaten = new java.util.ArrayList<>(lieferscheine);
            kandidaten.add(fremd);

            assertThat(sicher(rechnung, kandidaten)).containsExactlyInAnyOrderElementsOf(lieferscheine);
        }

        @Test
        void muster2_abUndLieferscheinMitZweiTeilrechnungen() {
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "RL 12 - 98700001/1", LocalDate.of(2026, 8, 1));
            ab.getGeschaeftsdaten().setBestellnummer("telef. vom 30.07.2026");
            LieferantDokument ls = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "RL 12 - 98700001/01", LocalDate.of(2026, 8, 10));
            LieferantDokument andereAb = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "RL 12 - 98700777/1", LocalDate.of(2026, 8, 2));
            LieferantDokument teil1 = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2026, 8, 15));
            teil1.getGeschaeftsdaten().setReferenzNummer("98700001");
            LieferantDokument teil2 = dokument(LieferantDokumentTyp.RECHNUNG, "RE-2", LocalDate.of(2026, 9, 20));
            teil2.getGeschaeftsdaten().setReferenzNummer("98700001");
            List<LieferantDokument> alle = List.of(ab, ls, andereAb, teil1, teil2);

            assertThat(sicher(teil1, alle)).containsExactlyInAnyOrder(ab, ls);
            assertThat(sicher(teil2, alle)).containsExactlyInAnyOrder(ab, ls);
        }

        @Test
        void muster3_rechnungNenntLieferscheinKundennummerZaehltNicht() {
            List<LieferantDokument> alle = new java.util.ArrayList<>();
            for (int monat = 1; monat <= 7; monat += 2) {
                LieferantDokument ls = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "AU-99000" + monat, LocalDate.of(2026, monat, 12));
                ls.getGeschaeftsdaten().setReferenzNummer("9900123");
                alle.add(ls);
            }
            LieferantDokument gemeint = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "AU-990017", LocalDate.of(2026, 7, 20));
            gemeint.getGeschaeftsdaten().setReferenzNummer("9900123");
            alle.add(gemeint);
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-80001", LocalDate.of(2026, 7, 28));
            rechnung.getGeschaeftsdaten().setReferenzNummer("AU-990017");
            rechnung.getGeschaeftsdaten().setAiRawJson("{\"weitereReferenzen\":[\"Kd-Nr. 9900123\"]}");

            assertThat(sicher(rechnung, alle)).containsExactly(gemeint);
        }

        @Test
        void kundennummerUeberMonateAllein_keinTreffer() {
            LieferantDokument ls1 = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "LS-1", LocalDate.of(2026, 1, 12));
            LieferantDokument ls2 = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "LS-2", LocalDate.of(2026, 4, 2));
            LieferantDokument ls3 = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "LS-3", LocalDate.of(2026, 7, 2));
            for (LieferantDokument ls : List.of(ls1, ls2, ls3)) {
                ls.getGeschaeftsdaten().setReferenzNummer("9900123");
            }
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2026, 7, 10));
            rechnung.getGeschaeftsdaten().setReferenzNummer("Kunde 9900123");

            LieferantDokumentAbgleich.Ergebnis ergebnis = abgleich(rechnung, ls1, ls2, ls3);
            assertThat(ergebnis.sicher()).isEmpty();
            assertThat(ergebnis.hinweis()).isNull();
        }

        @ParameterizedTest
        @CsvSource({
                "99-2-01234, 99201234",
                "99-01234, 9901234",
                "0099-01234, LS 9901234"
        })
        void muster4_ziffernkernDesGanzenFeldes(String lieferscheinReferenz, String rechnungsBestellnummer) {
            LieferantDokument ls = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "L-1", LocalDate.of(2026, 5, 5));
            ls.getGeschaeftsdaten().setReferenzNummer(lieferscheinReferenz);
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "R-1", LocalDate.of(2026, 5, 20));
            rechnung.getGeschaeftsdaten().setBestellnummer(rechnungsBestellnummer);

            assertThat(sicher(rechnung, List.of(ls))).containsExactly(ls);
        }

        @Test
        void datumsFreitextIstKeineNummer() {
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 8, 6));
            ab.getGeschaeftsdaten().setBestellnummer("telef. vom 05.08.2026");
            LieferantDokument ls = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "LS-1", LocalDate.of(2026, 8, 8));
            ls.getGeschaeftsdaten().setReferenzNummer("2026-08-05");
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2026, 8, 20));
            rechnung.getGeschaeftsdaten().setBestellnummer("telef. vom 05.08.2026");
            rechnung.getGeschaeftsdaten().setReferenzNummer("Lieferung 5.8.26");

            LieferantDokumentAbgleich.Ergebnis ergebnis = abgleich(rechnung, ab, ls);
            assertThat(ergebnis.sicher()).isEmpty();
            assertThat(ergebnis.hinweis()).isNull();
            assertThat(LieferantDokumentAbgleich.ohneDatum("vom 05.08.2026")).doesNotContain("2026");
            assertThat(LieferantDokumentAbgleich.ohneDatum("99-2-01234")).isEqualTo("99-2-01234");
        }

        @Test
        void zweiBestellungenMitGleicherKundennummer_keineKreuzverknuepfung() {
            // Junger Lieferant: Die Kundennummer steht erst auf zwei Lieferscheinen in
            // 30 Tagen – noch nicht verstreut. Die genauere Bestellnummer entscheidet.
            LieferantDokument lsA = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "LS-A", LocalDate.of(2026, 3, 1));
            lsA.getGeschaeftsdaten().setReferenzNummer("Kd 9900123");
            lsA.getGeschaeftsdaten().setBestellnummer("BE 55500011");
            LieferantDokument lsB = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "LS-B", LocalDate.of(2026, 3, 31));
            lsB.getGeschaeftsdaten().setReferenzNummer("Kd 9900123");
            lsB.getGeschaeftsdaten().setBestellnummer("BE 55500022");
            LieferantDokument rechnungA = dokument(LieferantDokumentTyp.RECHNUNG, "RE-A", LocalDate.of(2026, 4, 10));
            rechnungA.getGeschaeftsdaten().setReferenzNummer("Kd 9900123");
            rechnungA.getGeschaeftsdaten().setBestellnummer("55500011");
            LieferantDokument rechnungB = dokument(LieferantDokumentTyp.RECHNUNG, "RE-B", LocalDate.of(2026, 4, 30));
            rechnungB.getGeschaeftsdaten().setReferenzNummer("Kd 9900123");
            rechnungB.getGeschaeftsdaten().setBestellnummer("55500022");
            List<LieferantDokument> alle = List.of(lsA, lsB, rechnungA, rechnungB);

            assertThat(sicher(rechnungA, alle)).containsExactly(lsA);
            assertThat(sicher(rechnungB, alle)).containsExactly(lsB);
        }

        @Test
        void rechnungNenntLieferschein_abDerselbenBestellungBleibtDran_andererLieferscheinFaelltWeg() {
            // Teilrechnung: nennt Lieferschein 1 ausdrücklich. Die AB teilt nur die Auftragsnummer,
            // ist aber keine Konkurrenz zum Lieferschein. Lieferschein 2 gehört zur zweiten Teilrechnung.
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "RL 12 - 98700555/1", LocalDate.of(2026, 4, 8));
            LieferantDokument ls1 = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "98700555/01", LocalDate.of(2026, 4, 9));
            LieferantDokument ls2 = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "98700555/03", LocalDate.of(2026, 4, 14));
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-80555", LocalDate.of(2026, 4, 16));
            rechnung.getGeschaeftsdaten().setReferenzNummer("98700555/01");

            assertThat(sicher(rechnung, List.of(ab, ls1, ls2))).containsExactlyInAnyOrder(ab, ls1);
        }

        @Test
        void nurDieKundennummerGemeinsam_bleibtBeiEinemLieferscheinVerknuepft() {
            // Ohne genauere Nummer gibt es keinen Maßstab – ein einzelner Treffer bleibt.
            LieferantDokument ls = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "LS-A", LocalDate.of(2026, 3, 1));
            ls.getGeschaeftsdaten().setReferenzNummer("Kd 9900123");
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-A", LocalDate.of(2026, 3, 10));
            rechnung.getGeschaeftsdaten().setReferenzNummer("Kd 9900123");

            assertThat(sicher(rechnung, List.of(ls))).containsExactly(ls);
        }

        @Test
        void kurzerFreitextInDerBestellnummerVerbindetKeineFremdenAuftraege() {
            // Zwei ABs und zwei Rechnungen tragen "Mail vom 7.5" – das ist keine Bestellnummer.
            // Jede Rechnung nennt nur ihre eigene AB.
            LieferantDokument ab1 = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "24-01111", LocalDate.of(2024, 5, 7));
            ab1.getGeschaeftsdaten().setBestellnummer("Mail vom 7.5");
            LieferantDokument ab2 = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "24-02222", LocalDate.of(2024, 5, 8));
            ab2.getGeschaeftsdaten().setBestellnummer("Mail vom 7.5");
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "2409999", LocalDate.of(2024, 5, 24));
            rechnung.getGeschaeftsdaten().setBestellnummer("Mail vom 7.5");
            rechnung.getGeschaeftsdaten().setReferenzNummer("24-02222");

            assertThat(sicher(rechnung, List.of(ab1, ab2))).containsExactly(ab2);
            assertThat(LieferantDokumentAbgleich.ohneFreitext("MAILVOM75")).isNull();
            assertThat(LieferantDokumentAbgleich.ohneFreitext("B44")).isEqualTo("B44");
            assertThat(LieferantDokumentAbgleich.ohneFreitext("DE990321")).isEqualTo("DE990321");
        }

        @Test
        void gleichAufgebauteKreiseVerschiedenerBelegartenTreffenSichNicht() {
            // Lieferschein nennt seine AB, die Rechnung unsere Bestellung: Gleicher Ziffernkern,
            // aber verschiedene Vorsilben – kein Bezug.
            LieferantDokument ls = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "LS-1", LocalDate.of(2026, 4, 1));
            ls.getGeschaeftsdaten().setReferenzNummer("AB-2026-00123");
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2026, 4, 20));
            rechnung.getGeschaeftsdaten().setBestellnummer("BE-2026-00123");

            assertThat(sicher(rechnung, List.of(ls))).isEmpty();
            // Ohne Vorsilbe auf einer Seite zählt der Kern
            rechnung.getGeschaeftsdaten().setBestellnummer("2026-00123");
            assertThat(sicher(rechnung, List.of(ls))).containsExactly(ls);
        }

        @ParameterizedTest
        @CsvSource({
                "2026-04-20, true",   // 10 Tage vor dem Lieferschein
                "2026-04-19, false",  // 11 Tage davor
                "2026-09-27, true",   // 150 Tage danach
                "2026-09-28, false"   // 151 Tage danach
        })
        void rechnungsdatumMussZurLieferungPassen(LocalDate rechnungsdatum, boolean erwartet) {
            LieferantDokument ls = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "LS-1", LocalDate.of(2026, 4, 30));
            ls.getGeschaeftsdaten().setBestellnummer("BE 77700001");
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", rechnungsdatum);
            rechnung.getGeschaeftsdaten().setBestellnummer("77700001");

            assertThat(sicher(rechnung, List.of(ls))).hasSize(erwartet ? 1 : 0);
        }

        @Test
        void ohneDatumKeinNummernTreffer() {
            LieferantDokument ls = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "LS-1", null);
            ls.getGeschaeftsdaten().setBestellnummer("77700001");
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2026, 5, 1));
            rechnung.getGeschaeftsdaten().setBestellnummer("77700001");

            assertThat(sicher(rechnung, List.of(ls))).isEmpty();
        }

        @Test
        void kurzeBestellnummerBehaeltDieAlteRegel() {
            // "B-44" hat keine 5-stellige Nummer – hier gilt weiter: höchstens 3 Belege.
            LieferantDokument ls = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "LS-1", LocalDate.of(2026, 5, 2));
            ls.getGeschaeftsdaten().setBestellnummer("B-44");
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2026, 5, 31));
            rechnung.getGeschaeftsdaten().setBestellnummer("B 44");

            assertThat(sicher(rechnung, List.of(ls))).containsExactly(ls);
        }

        @Test
        void sehrLangeFelderOhneBacktracking() {
            LieferantDokument ls = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "LS-1", LocalDate.of(2026, 5, 2));
            ls.getGeschaeftsdaten().setReferenzNummer("1-".repeat(20_000) + "x");
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2026, 5, 31));
            rechnung.getGeschaeftsdaten().setReferenzNummer("12.12.".repeat(5_000));

            long start = System.nanoTime();
            assertThat(sicher(rechnung, List.of(ls))).isEmpty();
            assertThat(System.nanoTime() - start).isLessThan(2_000_000_000L);
        }
    }

    @Nested
    class RechnungNurSicher {

        @Test
        void hinweisAllein_keineAutomatischeVerknuepfung() {
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 9, 10));
            ab.getGeschaeftsdaten().setBetragBrutto(new BigDecimal("1190.00"));
            ab.getGeschaeftsdaten().setAiRawJson("{\"kommission\":\"BV Mustermann Treppe\"}");
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2026, 9, 14));
            rechnung.getGeschaeftsdaten().setBetragBrutto(new BigDecimal("1190.00"));
            rechnung.getGeschaeftsdaten().setAiRawJson("{\"kommission\":\"BV Mustermann Treppe\"}");

            LieferantDokumentAbgleich.Ergebnis ergebnis = abgleich(rechnung, ab);
            assertThat(ergebnis.sicher()).isEmpty();
            assertThat(ergebnis.hinweis()).isNull();

            // Als Vorschlag bleibt das Paar sichtbar
            var einschaetzung = abgleich.schaetzeEin(rechnung, ab, false, null, abgleich.neuerSpeicher());
            assertThat(einschaetzung.trefferquote()).isGreaterThanOrEqualTo(RechnungsVorschlagService.MIN_QUOTE_KARTE);
            assertThat(einschaetzung.sicher()).isFalse();
        }

        @Test
        void gesperrtesPaarWirdNieVerknuepft() {
            LieferantDokument ls = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "LS-4711", LocalDate.of(2026, 5, 2));
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2026, 5, 31));
            rechnung.getGeschaeftsdaten().setReferenzNummer("LS-4711");

            var speicher = abgleich.neuerSpeicher();
            speicher.sperre(rechnung.getId(), ls.getId());
            assertThat(abgleich.findeVorgaenger(rechnung, rechnung.getGeschaeftsdaten(), List.of(ls), speicher).sicher())
                    .isEmpty();

            // Auch in Gegenrichtung gespeichert gilt die Sperre
            var umgekehrt = abgleich.neuerSpeicher();
            umgekehrt.sperre(ls.getId(), rechnung.getId());
            assertThat(abgleich.findeVorgaenger(rechnung, rechnung.getGeschaeftsdaten(), List.of(ls), umgekehrt).sicher())
                    .isEmpty();
            // Ohne Sperre: sicher
            assertThat(abgleich(rechnung, ls).sicher()).containsExactly(ls);
        }

        @Test
        void andereTypenBehaltenHinweisTreffer() {
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2026, 9, 10));
            rechnung.getGeschaeftsdaten().setBetragBrutto(new BigDecimal("119.00"));
            LieferantDokument gutschrift = dokument(LieferantDokumentTyp.GUTSCHRIFT, "GS-1", LocalDate.of(2026, 9, 14));
            gutschrift.getGeschaeftsdaten().setBetragBrutto(new BigDecimal("-119.00"));

            assertThat(abgleich(gutschrift, rechnung).hinweis()).isSameAs(rechnung);
        }
    }

    @Nested
    class Hinweise {

        @Test
        void angebotOhneBestellnummerUeberKommission() {
            LieferantDokument angebot = dokument(LieferantDokumentTyp.ANGEBOT, "AN-1", LocalDate.of(2026, 1, 15));
            angebot.getGeschaeftsdaten().setAiRawJson("{\"kommission\":\"BV Mustermann, Hauptstr. 5\"}");
            LieferantDokument anderesAngebot = dokument(LieferantDokumentTyp.ANGEBOT, "AN-2", LocalDate.of(2026, 1, 20));
            anderesAngebot.getGeschaeftsdaten().setAiRawJson("{\"kommission\":\"Lager\"}");
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 4, 1));
            ab.getGeschaeftsdaten().setBestellnummer("B-2026-1");
            ab.getGeschaeftsdaten().setAiRawJson("{\"kommission\":\"bv mustermann hauptstr 5\"}");

            LieferantDokumentAbgleich.Ergebnis ergebnis = abgleich(ab, angebot, anderesAngebot);
            assertThat(ergebnis.sicher()).isEmpty();
            assertThat(ergebnis.hinweis()).isSameAs(angebot);
        }

        @Test
        void kurzeKommissionTrifftNurGleichlautend() {
            LieferantDokument angebot = dokument(LieferantDokumentTyp.ANGEBOT, "AN-1", LocalDate.of(2026, 2, 1));
            angebot.getGeschaeftsdaten().setAiRawJson("{\"kommission\":\"Lagerhalle\"}");
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 3, 1));
            ab.getGeschaeftsdaten().setAiRawJson("{\"kommission\":\"Lager\"}");

            assertThat(vorgaenger(ab, angebot)).isEmpty();
        }

        @Test
        void langeKommissionTrifftAuchAlsTeil() {
            LieferantDokument angebot = dokument(LieferantDokumentTyp.ANGEBOT, "AN-1", LocalDate.of(2026, 2, 1));
            angebot.getGeschaeftsdaten().setAiRawJson("{\"kommission\":\"BV Mustermann\"}");
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 3, 1));
            ab.getGeschaeftsdaten().setAiRawJson("{\"kommission\":\"BV Mustermann, Treppe EG\"}");

            assertThat(vorgaenger(ab, angebot)).containsExactly(angebot);
        }

        @Test
        void ohneDatumReichtEinMerkmalNicht() {
            LieferantDokument angebot = dokument(LieferantDokumentTyp.ANGEBOT, "AN-1", null);
            angebot.getGeschaeftsdaten().setBetragBrutto(new BigDecimal("595.00"));
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 3, 1));
            ab.getGeschaeftsdaten().setBetragBrutto(new BigDecimal("595.00"));

            assertThat(vorgaenger(ab, angebot)).isEmpty();

            angebot.getGeschaeftsdaten().setAiRawJson("{\"kommission\":\"Treppe Musterweg\"}");
            ab.getGeschaeftsdaten().setAiRawJson("{\"kommission\":\"Treppe Musterweg\"}");
            assertThat(vorgaenger(ab, angebot)).containsExactly(angebot);
        }

        @ParameterizedTest
        @CsvSource({
                "-119.00, true",
                "119.00, true",
                "-120.00, false"
        })
        void gutschriftVergleichtNurDieBetragsgroesse(BigDecimal gutschriftBetrag, boolean erwartet) {
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2026, 9, 10));
            rechnung.getGeschaeftsdaten().setBetragBrutto(new BigDecimal("119.00"));
            LieferantDokument gutschrift = dokument(LieferantDokumentTyp.GUTSCHRIFT, "GS-1", LocalDate.of(2026, 9, 14));
            gutschrift.getGeschaeftsdaten().setBetragBrutto(gutschriftBetrag);

            assertThat(vorgaenger(gutschrift, rechnung)).hasSize(erwartet ? 1 : 0);
        }

        @Test
        void negativerBetragPasstNichtZuPositiverAb() {
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 9, 10));
            ab.getGeschaeftsdaten().setBetragBrutto(new BigDecimal("119.00"));
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2026, 9, 14));
            rechnung.getGeschaeftsdaten().setBetragBrutto(new BigDecimal("-119.00"));

            assertThat(vorgaenger(rechnung, ab)).isEmpty();
        }

        @Test
        void speicherLiefertGleicheErgebnisseUeberMehrereAufrufe() {
            LieferantDokument angebot = dokument(LieferantDokumentTyp.ANGEBOT, "AN-1", LocalDate.of(2026, 2, 1));
            angebot.getGeschaeftsdaten().setAiRawJson("{\"kommission\":\"Treppe Musterweg\"}");
            LieferantDokument ab1 = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 3, 1));
            ab1.getGeschaeftsdaten().setAiRawJson("{\"kommission\":\"Treppe Musterweg\"}");
            LieferantDokument ab2 = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-2", LocalDate.of(2026, 3, 2));

            var speicher = abgleich.neuerSpeicher();
            assertThat(abgleich.findeVorgaenger(ab1, ab1.getGeschaeftsdaten(), List.of(angebot), speicher).hinweis())
                    .isSameAs(angebot);
            assertThat(abgleich.findeVorgaenger(ab2, ab2.getGeschaeftsdaten(), List.of(angebot), speicher).hinweis())
                    .isNull();
        }

        @Test
        void angebotUeberGemeinsameArtikelnummern() {
            LieferantDokument angebot = dokument(LieferantDokumentTyp.ANGEBOT, "AN-1", LocalDate.of(2026, 2, 1));
            angebot.getGeschaeftsdaten().setAiRawJson("""
                    {"artikelPositionen":[{"externeArtikelnummer":"100-200"},{"externeArtikelnummer":"100-300"},
                    {"externeArtikelnummer":"100-400"}]}""");
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 2, 20));
            ab.getGeschaeftsdaten().setAiRawJson("""
                    {"artikelPositionen":[{"externeArtikelnummer":"100200"},{"externeArtikelnummer":"100300"}]}""");

            assertThat(vorgaenger(ab, angebot)).containsExactly(angebot);
        }

        private static final String POSITIONEN_GELAENDER = """
                {"artikelPositionen":[
                  {"bezeichnung":"Flachstahl 50x5 S235JR","menge":10},
                  {"bezeichnung":"Rundrohr 42,4x2 verzinkt","menge":4},
                  {"bezeichnung":"Handlaufhalter Edelstahl V2A","menge":12},
                  {"positionsArt":"NEBENKOSTEN","bezeichnung":"Fracht","gesamtpreisNetto":45}]}""";

        @Test
        void angebotUeberGleicheBezeichnungenOhneNummern() {
            LieferantDokument angebot = dokument(LieferantDokumentTyp.ANGEBOT, "AN-1", LocalDate.of(2026, 2, 1));
            angebot.getGeschaeftsdaten().setAiRawJson(POSITIONEN_GELAENDER);
            LieferantDokument anderesAngebot = dokument(LieferantDokumentTyp.ANGEBOT, "AN-2", LocalDate.of(2026, 2, 3));
            anderesAngebot.getGeschaeftsdaten().setAiRawJson("""
                    {"artikelPositionen":[{"bezeichnung":"Trapezblech 35/207 RAL 7016","menge":30},
                    {"bezeichnung":"Dichtband Butyl selbstklebend","menge":5}]}""");
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 2, 20));
            // In der AB ist eine Position dazugekommen
            ab.getGeschaeftsdaten().setAiRawJson("""
                    {"artikelPositionen":[
                      {"bezeichnung":"FLACHSTAHL 50X5 S235JR","menge":10},
                      {"bezeichnung":"Rundrohr 42,4x2 verzinkt","menge":4},
                      {"bezeichnung":"Handlaufhalter Edelstahl V2A","menge":12},
                      {"bezeichnung":"Endkappe Rundrohr 42,4","menge":2}]}""");

            assertThat(vorgaenger(ab, angebot, anderesAngebot)).containsExactly(angebot);
        }

        @Test
        void gleicheFrachtAlleinVerknuepftNicht() {
            LieferantDokument angebot = dokument(LieferantDokumentTyp.ANGEBOT, "AN-1", LocalDate.of(2026, 2, 1));
            angebot.getGeschaeftsdaten().setAiRawJson("""
                    {"artikelPositionen":[{"positionsArt":"NEBENKOSTEN","bezeichnung":"Frachtkosten Spedition"}]}""");
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 2, 5));
            ab.getGeschaeftsdaten().setAiRawJson("""
                    {"artikelPositionen":[{"positionsArt":"NEBENKOSTEN","bezeichnung":"Frachtkosten Spedition"}]}""");

            assertThat(vorgaenger(ab, angebot)).isEmpty();
        }

        @Test
        void einschaetzungNenntGleichePositionen() {
            LieferantDokument angebot = dokument(LieferantDokumentTyp.ANGEBOT, "AN-1", LocalDate.of(2026, 2, 1));
            angebot.getGeschaeftsdaten().setAiRawJson(POSITIONEN_GELAENDER);
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 2, 10));
            ab.getGeschaeftsdaten().setAiRawJson(POSITIONEN_GELAENDER);
            var lieferant = new org.example.kalkulationsprogramm.domain.Lieferanten();
            lieferant.setId(7L);
            angebot.setLieferant(lieferant);
            ab.setLieferant(lieferant);

            var einschaetzung = abgleich.schaetzeEin(ab, angebot, false, null, abgleich.neuerSpeicher());

            assertThat(einschaetzung.gruende()).contains("3 von 3 Positionen gleich", "Gleiche Mengen");
            assertThat(einschaetzung.sicher()).isFalse();
            assertThat(einschaetzung.trefferquote()).isBetween(80, LieferantDokumentAbgleich.QUOTE_HINWEIS_MAX);
        }

        @Test
        void zeitnaheBelegeSchlagenSpaete() {
            // Gleiche Positionen in zwei Angeboten – das zeitlich nähere gewinnt.
            LieferantDokument altesAngebot = dokument(LieferantDokumentTyp.ANGEBOT, "AN-1", LocalDate.of(2025, 11, 1));
            altesAngebot.getGeschaeftsdaten().setAiRawJson(POSITIONEN_GELAENDER);
            LieferantDokument neuesAngebot = dokument(LieferantDokumentTyp.ANGEBOT, "AN-7", LocalDate.of(2026, 2, 12));
            neuesAngebot.getGeschaeftsdaten().setAiRawJson(POSITIONEN_GELAENDER);
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 2, 20));
            ab.getGeschaeftsdaten().setAiRawJson(POSITIONEN_GELAENDER);

            assertThat(vorgaenger(ab, altesAngebot, neuesAngebot)).containsExactly(neuesAngebot);
        }

        @Test
        void gleichePositionslisteMachtAusZweiAngebotenKeineRevision() {
            // Dieselbe Stahlliste für zwei Kunden – ohne gemeinsamen Nummernstamm keine Revision
            LieferantDokument erstes = dokument(LieferantDokumentTyp.ANGEBOT, "AN-4711", LocalDate.of(2026, 2, 1));
            erstes.getGeschaeftsdaten().setAiRawJson(POSITIONEN_GELAENDER);
            LieferantDokument zweites = dokument(LieferantDokumentTyp.ANGEBOT, "AN-5822", LocalDate.of(2026, 2, 10));
            zweites.getGeschaeftsdaten().setAiRawJson(POSITIONEN_GELAENDER);

            assertThat(vorgaenger(zweites, erstes)).isEmpty();
        }

        @Test
        void ohneDatumReichenPositionenAlleinNicht() {
            LieferantDokument angebot = dokument(LieferantDokumentTyp.ANGEBOT, "AN-1", null);
            angebot.getGeschaeftsdaten().setAiRawJson(POSITIONEN_GELAENDER);
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 2, 20));
            ab.getGeschaeftsdaten().setAiRawJson(POSITIONEN_GELAENDER);

            assertThat(vorgaenger(ab, angebot)).isEmpty();
        }

        @Test
        void kommissionUndBetragSchlagenNurBetrag() {
            LieferantDokument passend = dokument(LieferantDokumentTyp.ANGEBOT, "AN-1", LocalDate.of(2026, 2, 1));
            passend.getGeschaeftsdaten().setBetragNetto(new BigDecimal("1000.00"));
            passend.getGeschaeftsdaten().setAiRawJson("{\"kommission\":\"Treppe Musterweg\"}");
            LieferantDokument nurBetrag = dokument(LieferantDokumentTyp.ANGEBOT, "AN-2", LocalDate.of(2026, 2, 3));
            nurBetrag.getGeschaeftsdaten().setBetragNetto(new BigDecimal("1000.00"));
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 3, 1));
            ab.getGeschaeftsdaten().setBetragNetto(new BigDecimal("1000"));
            ab.getGeschaeftsdaten().setAiRawJson("{\"kommission\":\"Treppe Musterweg\"}");

            assertThat(vorgaenger(ab, passend, nurBetrag)).containsExactly(passend);
        }

        @Test
        void gleichstandVerknuepftNichts() {
            LieferantDokument a = dokument(LieferantDokumentTyp.ANGEBOT, "AN-1", LocalDate.of(2026, 2, 1));
            a.getGeschaeftsdaten().setBetragBrutto(new BigDecimal("119.00"));
            LieferantDokument b = dokument(LieferantDokumentTyp.ANGEBOT, "AN-2", LocalDate.of(2026, 2, 2));
            b.getGeschaeftsdaten().setBetragBrutto(new BigDecimal("119.00"));
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 3, 1));
            ab.getGeschaeftsdaten().setBetragBrutto(new BigDecimal("119.00"));

            assertThat(vorgaenger(ab, a, b)).isEmpty();
        }

        @ParameterizedTest
        @CsvSource({
                "ANGEBOT, AUFTRAGSBESTAETIGUNG, 2025-07-01, true",   // Angebot ~9 Monate alt
                "ANGEBOT, AUFTRAGSBESTAETIGUNG, 2025-03-01, false",  // über ein Jahr alt
                "ANGEBOT, AUFTRAGSBESTAETIGUNG, 2026-04-10, true",   // leicht nachdatiert
                "ANGEBOT, AUFTRAGSBESTAETIGUNG, 2026-05-01, false",  // deutlich nach der AB
                // Früher mit RECHNUNG; Rechnungen hängen aber nur noch an sicheren Treffern
                // (siehe RechnungNurSicher). Das Zeitfenster gilt gleich für Lieferscheine.
                "AUFTRAGSBESTAETIGUNG, LIEFERSCHEIN, 2026-01-15, true",
                "AUFTRAGSBESTAETIGUNG, LIEFERSCHEIN, 2025-10-01, false"
        })
        void betragZaehltNurImPassendenZeitfenster(LieferantDokumentTyp vorgaengerTyp,
                LieferantDokumentTyp typ, LocalDate vorgaengerDatum, boolean erwartet) {
            LieferantDokument vorher = dokument(vorgaengerTyp, "V-1", vorgaengerDatum);
            vorher.getGeschaeftsdaten().setBetragBrutto(new BigDecimal("595.00"));
            LieferantDokument dok = dokument(typ, "D-1", LocalDate.of(2026, 4, 1));
            dok.getGeschaeftsdaten().setBetragBrutto(new BigDecimal("595.00"));

            assertThat(vorgaenger(dok, vorher)).hasSize(erwartet ? 1 : 0);
        }

        @Test
        void kaputteKiAntwortWirdIgnoriert() {
            LieferantDokument angebot = dokument(LieferantDokumentTyp.ANGEBOT, "AN-1", LocalDate.of(2026, 2, 1));
            angebot.getGeschaeftsdaten().setAiRawJson("{kein json");
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 3, 1));
            ab.getGeschaeftsdaten().setAiRawJson("[]");

            assertThat(vorgaenger(ab, angebot)).isEmpty();
        }
    }

    @Nested
    class AngebotsRevisionen {

        @Test
        void geaendertesAngebotHaengtAmUrsprungUeberNummernstammUndKommission() {
            LieferantDokument fassung1 = dokument(LieferantDokumentTyp.ANGEBOT, "AN-4711", LocalDate.of(2026, 2, 1));
            fassung1.getGeschaeftsdaten().setAiRawJson("{\"kommission\":\"BV Mustermann\"}");
            LieferantDokument fassung2 = dokument(LieferantDokumentTyp.ANGEBOT, "AN-4711-2", LocalDate.of(2026, 2, 20));
            fassung2.getGeschaeftsdaten().setAiRawJson("{\"kommission\":\"BV Mustermann\"}");

            assertThat(abgleich(fassung2, fassung1).hinweis()).isSameAs(fassung1);
            // Nie in Gegenrichtung: das ältere Angebot hat keinen Vorgänger.
            assertThat(vorgaenger(fassung1, fassung2)).isEmpty();
        }

        @Test
        void einMerkmalReichtFuerEineRevisionNicht() {
            LieferantDokument treppe = dokument(LieferantDokumentTyp.ANGEBOT, "AN-100", LocalDate.of(2026, 2, 1));
            treppe.getGeschaeftsdaten().setAiRawJson("{\"kommission\":\"BV Mustermann\"}");
            LieferantDokument gelaender = dokument(LieferantDokumentTyp.ANGEBOT, "AN-200", LocalDate.of(2026, 2, 5));
            gelaender.getGeschaeftsdaten().setAiRawJson("{\"kommission\":\"BV Mustermann\"}");

            assertThat(vorgaenger(gelaender, treppe)).isEmpty();
        }

        @Test
        void revisionMitAusdruecklichemBezugIstSicher() {
            LieferantDokument fassung1 = dokument(LieferantDokumentTyp.ANGEBOT, "AN-100", LocalDate.of(2026, 2, 1));
            LieferantDokument fassung2 = dokument(LieferantDokumentTyp.ANGEBOT, "AN-180", LocalDate.of(2026, 2, 20));
            fassung2.getGeschaeftsdaten().setReferenzNummer("AN-100");

            assertThat(abgleich(fassung2, fassung1).sicher()).containsExactly(fassung1);
        }

        @Test
        void gleichesDatumEntscheidetDieEingangsreihenfolge() {
            LieferantDokument frueher = dokument(LieferantDokumentTyp.ANGEBOT, "AN-100", LocalDate.of(2026, 2, 1));
            LieferantDokument spaeter = dokument(LieferantDokumentTyp.ANGEBOT, "AN-180", LocalDate.of(2026, 2, 1));
            spaeter.getGeschaeftsdaten().setReferenzNummer("AN-100");
            frueher.getGeschaeftsdaten().setReferenzNummer("AN-180");

            assertThat(vorgaenger(spaeter, frueher)).containsExactly(frueher);
            assertThat(vorgaenger(frueher, spaeter)).isEmpty();
        }

        @Test
        void abWaehltBeiGleichstandDieNeuesteFassung() {
            LieferantDokument fassung1 = dokument(LieferantDokumentTyp.ANGEBOT, "AN-4711", LocalDate.of(2026, 2, 1));
            fassung1.getGeschaeftsdaten().setAiRawJson("{\"kommission\":\"BV Mustermann\"}");
            LieferantDokument fassung2 = dokument(LieferantDokumentTyp.ANGEBOT, "AN-4711-2", LocalDate.of(2026, 2, 20));
            fassung2.getGeschaeftsdaten().setAiRawJson("{\"kommission\":\"BV Mustermann\"}");
            fassung2.getVerknuepfteDokumente().add(fassung1);
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 3, 1));
            ab.getGeschaeftsdaten().setAiRawJson("{\"kommission\":\"BV Mustermann\"}");

            assertThat(abgleich(ab, fassung1, fassung2).hinweis()).isSameAs(fassung2);
        }

        @Test
        void fortlaufendeNummernSindKeineRevisionen() {
            // Gleicher Kreis "2026-<Zähler>" + gleiche Kommission: Treppe und Geländer
            // desselben Bauvorhabens, aber keine Fassungen voneinander.
            LieferantDokument treppe = dokument(LieferantDokumentTyp.ANGEBOT, "2026-1", LocalDate.of(2026, 2, 1));
            treppe.getGeschaeftsdaten().setAiRawJson("{\"kommission\":\"BV Mustermann\"}");
            LieferantDokument gelaender = dokument(LieferantDokumentTyp.ANGEBOT, "2026-2", LocalDate.of(2026, 2, 5));
            gelaender.getGeschaeftsdaten().setAiRawJson("{\"kommission\":\"BV Mustermann\"}");

            assertThat(vorgaenger(gelaender, treppe)).isEmpty();
        }

        @Test
        void ausdruecklicheRevisionsEndungZaehltAuchZwischenFassungen() {
            LieferantDokument fassung1 = dokument(LieferantDokumentTyp.ANGEBOT, "AN-4711 Rev. 1", LocalDate.of(2026, 2, 1));
            fassung1.getGeschaeftsdaten().setAiRawJson("{\"kommission\":\"BV Mustermann\"}");
            LieferantDokument fassung2 = dokument(LieferantDokumentTyp.ANGEBOT, "AN-4711 Rev. 2", LocalDate.of(2026, 2, 5));
            fassung2.getGeschaeftsdaten().setAiRawJson("{\"kommission\":\"BV Mustermann\"}");

            assertThat(vorgaenger(fassung2, fassung1)).containsExactly(fassung1);
        }

        @Test
        void abFindetNeuesteFassungAuchImStern() {
            // Beide Änderungen verweisen auf den Ursprung: v2 -> v1, v3 -> v1.
            LieferantDokument v1 = dokument(LieferantDokumentTyp.ANGEBOT, "AN-4711", LocalDate.of(2026, 2, 1));
            LieferantDokument v2 = dokument(LieferantDokumentTyp.ANGEBOT, "AN-4711-2", LocalDate.of(2026, 2, 10));
            LieferantDokument v3 = dokument(LieferantDokumentTyp.ANGEBOT, "AN-4711-3", LocalDate.of(2026, 2, 20));
            for (LieferantDokument v : List.of(v1, v2, v3)) {
                v.getGeschaeftsdaten().setAiRawJson("{\"kommission\":\"BV Mustermann\"}");
            }
            v2.getVerknuepfteDokumente().add(v1);
            v3.getVerknuepfteDokumente().add(v1);
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 3, 1));
            ab.getGeschaeftsdaten().setAiRawJson("{\"kommission\":\"BV Mustermann\"}");

            assertThat(abgleich(ab, v1, v2, v3).hinweis()).isSameAs(v3);
        }

        @Test
        void sehrLangeNummerOhneEndungsErkennung() {
            String lang = "AN-" + "1".repeat(80) + "-2";
            assertThat(LieferantDokumentAbgleich.nummernstamm(lang)).endsWith("2");
        }

        @Test
        void gleichstandOhneRevisionsverknuepfungBleibtOffen() {
            LieferantDokument a = dokument(LieferantDokumentTyp.ANGEBOT, "AN-100", LocalDate.of(2026, 2, 1));
            a.getGeschaeftsdaten().setAiRawJson("{\"kommission\":\"BV Mustermann\"}");
            LieferantDokument b = dokument(LieferantDokumentTyp.ANGEBOT, "AN-200", LocalDate.of(2026, 2, 20));
            b.getGeschaeftsdaten().setAiRawJson("{\"kommission\":\"BV Mustermann\"}");
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 3, 1));
            ab.getGeschaeftsdaten().setAiRawJson("{\"kommission\":\"BV Mustermann\"}");

            assertThat(vorgaenger(ab, a, b)).isEmpty();
        }
    }

    @Nested
    class Rahmen {

        @Test
        void falscheTypenUndSichSelbstIgnorieren() {
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LocalDate.of(2026, 3, 1));
            ab.getGeschaeftsdaten().setReferenzNummer("AB-1");
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "AB-1", LocalDate.of(2026, 3, 1));

            assertThat(vorgaenger(ab, ab, rechnung)).isEmpty();
        }

        @Test
        void typOhneVorgaengerLiefertNichts() {
            LieferantDokument sonstig = dokument(LieferantDokumentTyp.SONSTIG, "KAT-2026", LocalDate.of(2026, 3, 1));
            assertThat(vorgaenger(sonstig, dokument(LieferantDokumentTyp.SONSTIG, "KAT-2025", null))).isEmpty();
            LieferantDokument angebot = dokument(LieferantDokumentTyp.ANGEBOT, "AN-1", LocalDate.of(2026, 3, 1));
            assertThat(LieferantDokumentAbgleich.vorgaengerTypen(null)).isEmpty();
            assertThat(abgleich.findeVorgaenger(angebot, null, List.of()).sicher()).isEmpty();
            assertThat(abgleich.findeVorgaenger(angebot, null, List.of()).hinweis()).isNull();
        }

        @Test
        void nummernstamm() {
            assertThat(LieferantDokumentAbgleich.nummernstamm("AN-4711-2")).isEqualTo("AN4711");
            assertThat(LieferantDokumentAbgleich.nummernstamm("AN 4711 Rev. 3")).isEqualTo("AN4711");
            assertThat(LieferantDokumentAbgleich.nummernstamm("AN-4711/b")).isEqualTo("AN4711");
            assertThat(LieferantDokumentAbgleich.nummernstamm("AN-4711")).isEqualTo("AN4711");
            assertThat(LieferantDokumentAbgleich.nummernstamm(null)).isNull();
            // Viele Trenner ohne Endung: kein Backtracking-Problem (ReDoS)
            assertThat(LieferantDokumentAbgleich.nummernstamm("AN" + "-".repeat(5000) + "!")).isNull();
            assertThat(LieferantDokumentAbgleich.kettenRang(LieferantDokumentTyp.ANGEBOT))
                    .isLessThan(LieferantDokumentAbgleich.kettenRang(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG));
            assertThat(LieferantDokumentAbgleich.kettenRang(null)).isEqualTo(Integer.MAX_VALUE);
        }

        @Test
        void normalisierung() {
            assertThat(LieferantDokumentAbgleich.normalisiereNummer(" ab-2024/001 ")).isEqualTo("AB2024001");
            assertThat(LieferantDokumentAbgleich.normalisiereNummer("12")).isNull();
            assertThat(LieferantDokumentAbgleich.normalisiereNummer("mündlich")).isNull();
            assertThat(LieferantDokumentAbgleich.normalisiereKommission("BV Mustermann-Mühlstraße")).isEqualTo("bvmustermannmühlstraße");
            assertThat(LieferantDokumentAbgleich.normalisiereKommission("BV")).isNull();
        }
    }

    @Nested
    class Werkstoffzeugnis {

        private static final LocalDate LIEFERUNG = LocalDate.of(2026, 9, 1);

        private LieferantDokument lieferschein(String nummer, LocalDate datum, String positionen) {
            LieferantDokument ls = dokument(LieferantDokumentTyp.LIEFERSCHEIN, nummer, datum);
            ls.getGeschaeftsdaten().setAiRawJson(positionen);
            return ls;
        }

        private LieferantDokument zeugnis(LocalDate datum, String json) {
            LieferantDokument z = dokument(LieferantDokumentTyp.WERKSTOFFZEUGNIS, "3.1-77001", datum);
            z.getGeschaeftsdaten().setAiRawJson(json);
            return z;
        }

        private static String position(String bezeichnung, String werkstoff, String abmessung, String charge) {
            return "{\"bezeichnung\":\"" + bezeichnung + "\",\"werkstoff\":" + text(werkstoff)
                    + ",\"abmessung\":" + text(abmessung) + ",\"charge\":" + text(charge) + "}";
        }

        private static String text(String wert) {
            return wert == null ? "null" : "\"" + wert + "\"";
        }

        private static String json(String kommission, String... positionen) {
            return "{\"kommission\":" + text(kommission) + ",\"artikelPositionen\":["
                    + String.join(",", positionen) + "]}";
        }

        @Test
        void typen() {
            assertThat(LieferantDokumentAbgleich.vorgaengerTypen(LieferantDokumentTyp.WERKSTOFFZEUGNIS))
                    .containsExactly(LieferantDokumentTyp.LIEFERSCHEIN, LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG);
            assertThat(LieferantDokumentAbgleich.kettenRang(LieferantDokumentTyp.WERKSTOFFZEUGNIS))
                    .isGreaterThan(LieferantDokumentAbgleich.kettenRang(LieferantDokumentTyp.LIEFERSCHEIN))
                    .isLessThan(LieferantDokumentAbgleich.kettenRang(LieferantDokumentTyp.RECHNUNG));
            // Ein Zeugnis ist nie Vorgänger einer Rechnung – es zählt nicht als „geliefert/erledigt“.
            assertThat(LieferantDokumentAbgleich.vorgaengerTypen(LieferantDokumentTyp.RECHNUNG))
                    .doesNotContain(LieferantDokumentTyp.WERKSTOFFZEUGNIS);
        }

        @Test
        void zeugnisNenntLieferscheinnummer() {
            LieferantDokument ls = lieferschein("LS-880011", LIEFERUNG, null);
            LieferantDokument z = zeugnis(LIEFERUNG.minusDays(150), null);
            z.getGeschaeftsdaten().setReferenzNummer("LS 880011");

            assertThat(abgleich(z, ls).sicher()).containsExactly(ls);
        }

        @Test
        void ohneLieferscheinHaengtZeugnisAnDerAb() {
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-445566", LIEFERUNG);
            LieferantDokument z = zeugnis(LIEFERUNG.plusDays(3), null);
            z.getGeschaeftsdaten().setReferenzNummer("AB-445566");

            assertThat(abgleich(z, ab).sicher()).containsExactly(ab);
        }

        @Test
        void gleicheChargeIstSicher() {
            LieferantDokument ls = lieferschein("LS-1", LIEFERUNG,
                    json(null, position("FL 50x5", "S235JR", "50x5", "123456")));
            LieferantDokument anderer = lieferschein("LS-2", LIEFERUNG,
                    json(null, position("FL 50x5", "S235JR", "50x5", "999999")));
            LieferantDokument z = zeugnis(LIEFERUNG.minusDays(200),
                    json(null, position("Flachstahl", "S235JR+AR", "50x5", "12 34 56")));

            LieferantDokumentAbgleich.Ergebnis ergebnis = abgleich(z, ls, anderer);
            assertThat(ergebnis.sicher()).containsExactly(ls);
            assertThat(abgleich.schaetzeEin(z, ls, false, null, abgleich.neuerSpeicher()).gruende())
                    .contains("Gleiche Charge 123456");
        }

        @Test
        void kurzeChargeVerknuepftNicht() {
            LieferantDokument ls = lieferschein("LS-1", LIEFERUNG, json(null, position("Rohr", null, null, "A1")));
            LieferantDokument z = zeugnis(LIEFERUNG, json(null, position("Rohr", null, null, "A1")));

            assertThat(abgleich(z, ls).sicher()).isEmpty();
        }

        @Test
        void sichererLieferscheinVerdraengtDieAb() {
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LIEFERUNG.minusDays(5));
            ab.getGeschaeftsdaten().setReferenzNummer("Auftrag 7765432");
            LieferantDokument ls = lieferschein("LS-1", LIEFERUNG, null);
            ls.getGeschaeftsdaten().setReferenzNummer("Auftrag 7765432");
            LieferantDokument z = zeugnis(LIEFERUNG.plusDays(1), "{\"weitereReferenzen\":[\"7765432\"]}");

            assertThat(abgleich(z, ab, ls).sicher()).containsExactly(ls);
        }

        @Test
        void gleicheChargeAufMehrerenLieferungenHaengtAnAllen() {
            // Dieselbe Schmelze in zwei Lieferungen: Das Zeugnis gilt für beide – gewollt.
            String charge = json(null, position("FL 50x5", "S235JR", "50x5", "123456"));
            LieferantDokument ls1 = lieferschein("LS-1", LIEFERUNG, charge);
            LieferantDokument ls2 = lieferschein("LS-2", LIEFERUNG.plusDays(90), charge);
            LieferantDokument z = zeugnis(LIEFERUNG, json(null, position("Flachstahl", "S235JR", "50x5", "123456")));

            assertThat(abgleich(z, ls1, ls2).sicher()).containsExactlyInAnyOrder(ls1, ls2);
        }

        @Test
        void anDerAbNurSicherNichtPerHinweis() {
            LieferantDokument ab = dokument(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1", LIEFERUNG);
            ab.getGeschaeftsdaten().setAiRawJson(json("BV Mustermann Hauptstr. 5",
                    position("FL 50x5", "S235JR", "50x5", null)));
            LieferantDokument z = zeugnis(LIEFERUNG.plusDays(1),
                    json("BV Mustermann Hauptstr. 5", position("Flachstahl", "S235JR", "50x5", null)));

            assertThat(vorgaenger(z, ab)).isEmpty();
        }

        @Test
        void gemeinsameAuftragsnummerIstSicher() {
            LieferantDokument ls = lieferschein("LS-1", LIEFERUNG, null);
            ls.getGeschaeftsdaten().setReferenzNummer("Auftrag 7765432");
            LieferantDokument z = zeugnis(LIEFERUNG.plusDays(1), "{\"weitereReferenzen\":[\"7765432\"]}");

            assertThat(abgleich(z, ls).sicher()).containsExactly(ls);
        }

        @Test
        void gleichesMaterialUndZeitnahIstHinweis() {
            LieferantDokument ls = lieferschein("LS-1", LIEFERUNG,
                    json(null, position("FL 50x5 S235JR", "S235JR", "50 x 5", null)));
            LieferantDokument z = zeugnis(LIEFERUNG.plusDays(2),
                    json(null, position("Flachstahl", "S235JR+AR", "50x5", null)));

            LieferantDokumentAbgleich.Ergebnis ergebnis = abgleich(z, ls);
            assertThat(ergebnis.sicher()).isEmpty();
            assertThat(ergebnis.hinweis()).isEqualTo(ls);
            assertThat(abgleich.schaetzeEin(z, ls, false, null, abgleich.neuerSpeicher()).gruende())
                    .contains("Gleicher Werkstoff und gleiche Abmessung", "2 Tage Abstand");
        }

        @Test
        void kommissionUndZehnTageIstHinweis() {
            LieferantDokument ls = lieferschein("LS-1", LIEFERUNG, json("BV Mustermann Hauptstr. 5"));
            LieferantDokument z = zeugnis(LIEFERUNG.plusDays(10), json("BV Mustermann Hauptstr. 5"));

            assertThat(abgleich(z, ls).hinweis()).isEqualTo(ls);
        }

        @Test
        void kommissionAllein45TageReichtNicht() {
            LieferantDokument ls = lieferschein("LS-1", LIEFERUNG, json("BV Mustermann Hauptstr. 5"));
            LieferantDokument z = zeugnis(LIEFERUNG.plusDays(45), json("BV Mustermann Hauptstr. 5"));

            assertThat(vorgaenger(z, ls)).isEmpty();
        }

        @Test
        void naeheAlleinReichtNie() {
            LieferantDokument ls = lieferschein("LS-1", LIEFERUNG,
                    json(null, position("Rundrohr", "S355J2", "60,3x2,9", null)));
            LieferantDokument z = zeugnis(LIEFERUNG,
                    json(null, position("Flachstahl", "S235JR", "50x5", null)));

            assertThat(vorgaenger(z, ls)).isEmpty();
        }

        @Test
        void mehrAls60TageKeinHinweis() {
            LieferantDokument ls = lieferschein("LS-1", LIEFERUNG,
                    json("BV Mustermann Hauptstr. 5", position("FL 50x5", "S235JR", "50x5", null)));
            LieferantDokument z = zeugnis(LIEFERUNG.plusDays(61),
                    json("BV Mustermann Hauptstr. 5", position("Flachstahl", "S235JR", "50x5", null)));

            assertThat(vorgaenger(z, ls)).isEmpty();
        }

        @Test
        void eingangsdatumZaehltWennZeugnisdatumAltIst() {
            // Werkszeugnis vom Walzwerk, Monate alt – aber zwei Tage nach der Lieferung eingegangen.
            LieferantDokument ls = lieferschein("LS-1", LIEFERUNG,
                    json(null, position("FL 50x5", "S235JR", "50x5", null)));
            LieferantDokument z = zeugnis(LIEFERUNG.minusDays(180),
                    json(null, position("Flachstahl", "S235JR", "50x5", null)));
            z.setUploadDatum(LIEFERUNG.plusDays(2).atTime(9, 30));

            assertThat(abgleich(z, ls).hinweis()).isEqualTo(ls);
        }

        @Test
        void gleichstandVerknuepftNichts() {
            String material = json(null, position("FL 50x5", "S235JR", "50x5", null));
            LieferantDokument ls1 = lieferschein("LS-1", LIEFERUNG, material);
            LieferantDokument ls2 = lieferschein("LS-2", LIEFERUNG, material);
            LieferantDokument z = zeugnis(LIEFERUNG.plusDays(1),
                    json(null, position("Flachstahl", "S235JR", "50x5", null)));

            assertThat(vorgaenger(z, ls1, ls2)).isEmpty();
        }

        @Test
        void naeherLiegenderLieferscheinGewinnt() {
            String material = json(null, position("FL 50x5", "S235JR", "50x5", null));
            LieferantDokument nah = lieferschein("LS-1", LIEFERUNG, material);
            LieferantDokument fern = lieferschein("LS-2", LIEFERUNG.minusDays(20), material);
            LieferantDokument z = zeugnis(LIEFERUNG.plusDays(1),
                    json(null, position("Flachstahl", "S235JR", "50x5", null)));

            assertThat(abgleich(z, nah, fern).hinweis()).isEqualTo(nah);
        }

        @Test
        void ohneDatumGiltStrengereSchwelle() {
            LieferantDokument ls = lieferschein("LS-1", null,
                    json(null, position("FL 50x5", "S235JR", "50x5", null)));
            LieferantDokument z = zeugnis(null, json(null, position("Flachstahl", "S235JR", "50x5", null)));

            assertThat(vorgaenger(z, ls)).isEmpty();
        }

        @ParameterizedTest
        @CsvSource({ "0,30", "3,30", "4,20", "14,20", "15,10", "30,10", "31,0", "60,0" })
        void zeitpunkte(long tage, int punkte) {
            assertThat(LieferantDokumentAbgleich.zeugnisZeitPunkte(tage)).isEqualTo(punkte);
        }

        @Test
        void zeugnisVerzerrtDieStreuungNicht() {
            // Ein uraltes Zeugnis mit derselben Auftragsnummer darf die Nummer nicht
            // „gestreut“ wirken lassen – sonst verlöre die Rechnung ihren Lieferschein.
            LieferantDokument ls = lieferschein("LS-1", LIEFERUNG, null);
            ls.getGeschaeftsdaten().setReferenzNummer("Auftrag 7765432");
            LieferantDokument altesZeugnis = zeugnis(LIEFERUNG.minusDays(400),
                    "{\"weitereReferenzen\":[\"7765432\"]}");
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LIEFERUNG.plusDays(5));
            rechnung.getGeschaeftsdaten().setReferenzNummer("Auftrag 7765432");

            assertThat(abgleich(rechnung, ls, altesZeugnis).sicher()).containsExactly(ls);
        }
    }
}
