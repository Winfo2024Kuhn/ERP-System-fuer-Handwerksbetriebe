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
        void bestellnummerAufVielenDokumentenIstNichtTrennscharf() {
            LieferantDokument rechnung = dokument(LieferantDokumentTyp.RECHNUNG, "RE-1", LocalDate.of(2026, 5, 31));
            rechnung.getGeschaeftsdaten().setBestellnummer("KD-12345");
            LieferantDokument[] lieferscheine = new LieferantDokument[4];
            for (int i = 0; i < lieferscheine.length; i++) {
                lieferscheine[i] = dokument(LieferantDokumentTyp.LIEFERSCHEIN, "LS-" + i, LocalDate.of(2026, 5, 1 + i));
                lieferscheine[i].getGeschaeftsdaten().setBestellnummer("KD-12345");
            }

            assertThat(vorgaenger(rechnung, lieferscheine)).isEmpty();
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
                "AUFTRAGSBESTAETIGUNG, RECHNUNG, 2026-01-15, true",
                "AUFTRAGSBESTAETIGUNG, RECHNUNG, 2025-10-01, false"
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
}
