package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.PreisQuelle;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.LieferantenArtikelPreiseRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Deckt vor allem den Fall ab, an dem die Preisuebernahme aus Eingangsrechnungen
 * gescheitert ist: Stueckpreise von Zukaufteilen wurden vom alten
 * Kilopreis-Korridor verfaelscht oder kommentarlos verworfen.
 *
 * <p>Die Klasse laeuft bewusst <b>ohne</b> die sonst uebliche Rollback-Transaktion
 * von {@code @DataJpaTest}: {@code uebernehmePreise} oeffnet mit
 * {@code REQUIRES_NEW} eine eigene Transaktion und wuerde die noch nicht
 * committeten Testdaten sonst gar nicht sehen. Jeder Test legt dafuer eigene
 * Lieferanten und Artikelnummern an, damit sie sich nicht in die Quere kommen.
 */
@DataJpaTest
@Import(PreisUebernahmeService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PreisUebernahmeServiceTest {

    /** Aelter als jeder Beleg in diesen Tests. */
    private static final Date BESTANDSSTAND = new Date(0);

    @Autowired
    private PreisUebernahmeService preisUebernahmeService;

    @Autowired
    private ArtikelRepository artikelRepository;

    @Autowired
    private LieferantenRepository lieferantenRepository;

    @Autowired
    private LieferantenArtikelPreiseRepository artikelPreiseRepository;

    // ------------------------------------------------------------------
    // Der eigentliche Fehlerfall: Stueckpreise
    // ------------------------------------------------------------------

    @Test
    void stueckpreisWirdUnveraendertUebernommen() {
        Lieferanten lieferant = lieferant("Musterlieferant Zukauf");
        Artikel artikel = artikelMitPreis(lieferant, "HLH-42", Verrechnungseinheit.STUECK, "58.00");

        var ergebnis = uebernimm(lieferant, position("HLH-42", "62.00", "1 C62", "C62"));

        assertEquals(1, ergebnis.uebernommen());
        assertEquals(0, ergebnis.uebersprungen());
        assertPreis("62.00", artikel, lieferant);
    }

    @Test
    void stueckpreisUnterHalbemEuroWirdNichtHochgerechnet() {
        Lieferanten lieferant = lieferant("Musterlieferant Kleinteile");
        Artikel artikel = artikelMitPreis(lieferant, "ROS-1", Verrechnungseinheit.STUECK, "0.50");

        uebernimm(lieferant, position("ROS-1", "0.18", "1 C62", "C62"));

        assertPreis("0.18", artikel, lieferant);
    }

    // ------------------------------------------------------------------
    // Umrechnung nur dort, wo die Rechnung sie ausdruecklich nennt
    // ------------------------------------------------------------------

    @Test
    void tonnenpreisWirdAufKilogrammUmgerechnet() {
        Lieferanten lieferant = lieferant("Musterlieferant Stahl");
        Artikel artikel = artikelMitPreis(lieferant, "S235-100", Verrechnungseinheit.KILOGRAMM, "1.10");

        uebernimm(lieferant, position("S235-100", "1250.00", "1000 KGM", "KGM"));

        assertPreis("1.25", artikel, lieferant);
    }

    @Test
    void tonnenpreisMitDeutscherTausendertrennungWirdUmgerechnet() {
        Lieferanten lieferant = lieferant("Musterlieferant Tausender");
        Artikel artikel = artikelMitPreis(lieferant, "S235-300", Verrechnungseinheit.KILOGRAMM, "1.10");

        // "1.000 kg" ist deutsche Schreibweise. Als BigDecimal("1.000") gelesen
        // waere die Basis 1 - der Tonnenpreis stuende dann als Kilopreis in der DB.
        uebernimm(lieferant, position("S235-300", "1400.00", "1.000 kg", null));

        assertPreis("1.40", artikel, lieferant);
    }

    @Test
    void hundertKilogrammPreisWirdUmgerechnet() {
        Lieferanten lieferant = lieferant("Musterlieferant Blech");
        Artikel artikel = artikelMitPreis(lieferant, "BL-3", Verrechnungseinheit.KILOGRAMM, "1.10");

        uebernimm(lieferant, position("BL-3", "125.00", "100 kg", null));

        assertPreis("1.25", artikel, lieferant);
    }

    @Test
    void tonnenAngabeMitEuroPraefixWirdErkannt() {
        Lieferanten lieferant = lieferant("Musterlieferant Traeger");
        Artikel artikel = artikelMitPreis(lieferant, "IPE-200", Verrechnungseinheit.KILOGRAMM, "1.10");

        uebernimm(lieferant, position("IPE-200", "1400.00", "€/t", null));

        assertPreis("1.40", artikel, lieferant);
    }

    @Test
    void kilogrammpreisBleibtUnveraendert() {
        Lieferanten lieferant = lieferant("Musterlieferant Rohr");
        Artikel artikel = artikelMitPreis(lieferant, "RO-40", Verrechnungseinheit.KILOGRAMM, "1.10");

        uebernimm(lieferant, position("RO-40", "0.95", "1 KGM", "KGM"));

        assertPreis("0.95", artikel, lieferant);
    }

    @Test
    void laufendeMeterWerdenErkannt() {
        Lieferanten lieferant = lieferant("Musterlieferant Handlauf");
        Artikel artikel = artikelMitPreis(lieferant, "HL-RUND", Verrechnungseinheit.LAUFENDE_METER, "9.00");

        uebernimm(lieferant, position("HL-RUND", "11.40", "1 MTR", "MTR"));

        assertPreis("11.40", artikel, lieferant);
    }

    @Test
    void ohneEinheitsangabeWirdDerWertUnveraendertUebernommen() {
        Lieferanten lieferant = lieferant("Musterlieferant Ohne Einheit");
        Artikel artikel = artikelMitPreis(lieferant, "X-1", Verrechnungseinheit.STUECK, "5.00");

        uebernimm(lieferant, position("X-1", "18.50", null, null));

        assertPreis("18.50", artikel, lieferant);
    }

    @Test
    void zahlVorUnbekannterEinheitWirdNichtAlsTeilerVerwendet() {
        Lieferanten lieferant = lieferant("Musterlieferant Stangen");
        Artikel artikel = artikelMitPreis(lieferant, "ST-6", Verrechnungseinheit.STUECK, "20.00");

        // "6 m Stangen" ist eine Beschreibung, keine Mengenbasis. Wer hier durch 6
        // teilt, rechnet auf gut Glueck.
        uebernimm(lieferant, position("ST-6", "30.00", "6 m Stangen", null));

        assertPreis("30.00", artikel, lieferant);
    }

    // ------------------------------------------------------------------
    // Gespeichert wird immer je EINE Einheit - wie beim CSV-Import
    // ------------------------------------------------------------------

    @Test
    void hundertStueckPreisWirdAufEinStueckHeruntergerechnet() {
        Lieferanten lieferant = lieferant("Musterlieferant Schrauben");
        Artikel artikel = artikelMitPreis(lieferant, "SCHR-100", Verrechnungseinheit.STUECK, "0.05", "100");

        // Rechnung nennt 1,83 EUR je 100 Stueck. Gespeichert wird der Stueckpreis -
        // die Kalkulation multipliziert ihn spaeter mit der Stueckzahl.
        uebernimm(lieferant, position("SCHR-100", "1.83", "100 C62", "C62"));

        assertPreis("0.02", artikel, lieferant);
    }

    @Test
    void staffelAmArtikelAendertDieUmrechnungNicht() {
        Lieferanten mitStaffel = lieferant("Musterlieferant Mit Staffel");
        Lieferanten ohneStaffel = lieferant("Musterlieferant Ohne Staffel");
        Artikel a = artikelMitPreis(mitStaffel, "PAAR-1", Verrechnungseinheit.STUECK, "1.00", "100");
        Artikel b = artikelMitPreis(ohneStaffel, "PAAR-2", Verrechnungseinheit.STUECK, "1.00", null);

        uebernimm(mitStaffel, position("PAAR-1", "4.00", "1 C62", "C62"));
        uebernimm(ohneStaffel, position("PAAR-2", "4.00", "1 C62", "C62"));

        // Massgeblich ist allein die Angabe auf der Rechnung, nicht das Artikelfeld.
        assertPreis("4.00", a, mitStaffel);
        assertPreis("4.00", b, ohneStaffel);
    }

    // ------------------------------------------------------------------
    // Widerspruch: lieber nichts schreiben als raten
    // ------------------------------------------------------------------

    @Test
    void einheitenKonfliktLaesstDenBisherigenPreisStehen() {
        Lieferanten lieferant = lieferant("Musterlieferant Konflikt");
        Artikel artikel = artikelMitPreis(lieferant, "S235-200", Verrechnungseinheit.KILOGRAMM, "1.10");

        var ergebnis = uebernimm(lieferant, position("S235-200", "62.00", "1 C62", "C62"));

        assertEquals(0, ergebnis.uebernommen());
        assertEquals(1, ergebnis.uebersprungen());
        assertPreis("1.10", artikel, lieferant);
    }

    // ------------------------------------------------------------------
    // Belegdatum: eine alte Rechnung darf keinen neueren Stand ueberschreiben
    // ------------------------------------------------------------------

    @Test
    void aeltererBelegUeberschreibtDenNeuerenStandNicht() {
        Lieferanten lieferant = lieferant("Musterlieferant Reanalyse");
        Artikel artikel = artikelMitPreis(lieferant, "R-1", Verrechnungseinheit.STUECK, "10.00");

        Date heute = new Date();
        Date vorEinemJahr = new Date(heute.getTime() - 365L * 24 * 60 * 60 * 1000);

        preisUebernahmeService.uebernehmePreise(lieferant, PreisQuelle.RECHNUNG, heute,
                List.of(position("R-1", "12.00", "1 C62", "C62")));
        var ergebnis = preisUebernahmeService.uebernehmePreise(lieferant, PreisQuelle.RECHNUNG, vorEinemJahr,
                List.of(position("R-1", "8.00", "1 C62", "C62")));

        assertEquals(0, ergebnis.uebernommen());
        assertEquals(1, ergebnis.uebersprungen());
        assertPreis("12.00", artikel, lieferant);
    }

    @Test
    void belegOhneDatumWirdNichtUebernommen() {
        Lieferanten lieferant = lieferant("Musterlieferant Datumslos");
        Artikel artikel = artikelMitPreis(lieferant, "O-1", Verrechnungseinheit.STUECK, "10.00");

        // Ohne Belegdatum ist nicht entscheidbar, ob dieser Beleg neuer ist als der
        // gespeicherte Stand. Ersatzweise "jetzt" anzunehmen wuerde die
        // Stapel-Neuanalyse wieder kippen.
        var ergebnis = preisUebernahmeService.uebernehmePreise(lieferant, PreisQuelle.RECHNUNG, null,
                List.of(position("O-1", "12.00", "1 C62", "C62")));

        assertEquals(0, ergebnis.uebernommen());
        assertEquals(1, ergebnis.uebersprungen());
        assertPreis("10.00", artikel, lieferant);
    }

    @Test
    void belegdatumWirdAlsPreisstandUebernommen() {
        Lieferanten lieferant = lieferant("Musterlieferant Datum");
        Artikel artikel = artikelMitPreis(lieferant, "D-1", Verrechnungseinheit.STUECK, "10.00");
        Date belegdatum = new Date(new Date().getTime() - 7L * 24 * 60 * 60 * 1000);

        preisUebernahmeService.uebernehmePreise(lieferant, PreisQuelle.RECHNUNG, belegdatum,
                List.of(position("D-1", "11.00", "1 C62", "C62")));

        LieferantenArtikelPreise aktuell = artikelPreiseRepository
                .findByArtikel_IdAndLieferant_IdAndAktuellTrue(artikel.getId(), lieferant.getId()).orElseThrow();
        assertEquals(belegdatum, aktuell.getPreisAenderungsdatum());
    }

    // ------------------------------------------------------------------
    // Historie und Zuordnung
    // ------------------------------------------------------------------

    @Test
    void bisherigerStandBleibtAlsVerlaufErhalten() {
        Lieferanten lieferant = lieferant("Musterlieferant Verlauf");
        Artikel artikel = artikelMitPreis(lieferant, "V-1", Verrechnungseinheit.STUECK, "10.00");

        uebernimm(lieferant, position("V-1", "12.00", "1 C62", "C62"));

        List<LieferantenArtikelPreise> verlauf = artikelPreiseRepository.findeVerlauf(artikel.getId());
        assertEquals(2, verlauf.size());
        LieferantenArtikelPreise aktuell = verlauf.stream().filter(LieferantenArtikelPreise::isAktuell)
                .findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("12.00").compareTo(aktuell.getPreis()));
        assertEquals(PreisQuelle.RECHNUNG, aktuell.getQuelle());
        assertEquals("V-1", aktuell.getExterneArtikelnummer());
        assertTrue(verlauf.stream().anyMatch(p -> !p.isAktuell()
                && new BigDecimal("10.00").compareTo(p.getPreis()) == 0));
    }

    @Test
    void unveraenderterPreisLegtKeinenNeuenStandAn() {
        Lieferanten lieferant = lieferant("Musterlieferant Gleichpreis");
        Artikel artikel = artikelMitPreis(lieferant, "G-1", Verrechnungseinheit.STUECK, "12.00");

        var ergebnis = uebernimm(lieferant, position("G-1", "12.00", "1 C62", "C62"));

        assertEquals(0, ergebnis.uebernommen());
        assertEquals(1, artikelPreiseRepository.findeVerlauf(artikel.getId()).size());
    }

    @Test
    void preisEinesFremdenLieferantenBleibtUnberuehrt() {
        Lieferanten eigener = lieferant("Musterlieferant A");
        Lieferanten fremder = lieferant("Musterlieferant B");

        Artikel artikel = new Artikel();
        artikel.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        artikel.getArtikelpreis().add(preisstand(artikel, eigener, "DOPPELT", "10.00"));
        artikel.getArtikelpreis().add(preisstand(artikel, fremder, "DOPPELT", "20.00"));
        artikelRepository.save(artikel);

        uebernimm(eigener, position("DOPPELT", "11.00", "1 C62", "C62"));

        assertPreis("11.00", artikel, eigener);
        assertPreis("20.00", artikel, fremder);
    }

    @Test
    void artikelnummerWirdOhneRuecksichtAufSchreibweiseGefunden() {
        Lieferanten lieferant = lieferant("Musterlieferant Schreibweise");
        Artikel artikel = artikelMitPreis(lieferant, "abc-1", Verrechnungseinheit.STUECK, "10.00");

        uebernimm(lieferant, position("  ABC-1 ", "13.00", "1 C62", "C62"));

        assertPreis("13.00", artikel, lieferant);
    }

    // ------------------------------------------------------------------
    // Unbrauchbare Positionen
    // ------------------------------------------------------------------

    @Test
    void positionOhneArtikelnummerWirdUebersprungen() {
        Lieferanten lieferant = lieferant("Musterlieferant Leer");

        var ergebnis = uebernimm(lieferant, position("  ", "5.00", "1 C62", "C62"));

        assertEquals(0, ergebnis.uebernommen());
        assertEquals(1, ergebnis.uebersprungen());
    }

    @Test
    void unbekannteArtikelnummerWirdUebersprungen() {
        Lieferanten lieferant = lieferant("Musterlieferant Unbekannt");
        artikelMitPreis(lieferant, "BEKANNT", Verrechnungseinheit.STUECK, "10.00");

        var ergebnis = uebernimm(lieferant, position("UNBEKANNT", "5.00", "1 C62", "C62"));

        assertEquals(0, ergebnis.uebernommen());
        assertEquals(1, ergebnis.uebersprungen());
    }

    @Test
    void positionOhnePreisWirdUebersprungen() {
        Lieferanten lieferant = lieferant("Musterlieferant Preislos");
        Artikel artikel = artikelMitPreis(lieferant, "P-0", Verrechnungseinheit.STUECK, "10.00");

        var ergebnis = uebernimm(lieferant,
                new PreisUebernahmeService.Position("P-0", null, "1 C62", "C62"));

        assertEquals(1, ergebnis.uebersprungen());
        assertPreis("10.00", artikel, lieferant);
    }

    @Test
    void aufNullGerundeterPreisZerstoertDenBestandNicht() {
        Lieferanten lieferant = lieferant("Musterlieferant Centartikel");
        Artikel artikel = artikelMitPreis(lieferant, "CENT-1", Verrechnungseinheit.STUECK, "0.01");

        // 0,40 EUR je 100 Stueck sind 0,004 je Stueck - die Preisspalte fuehrt aber
        // nur zwei Nachkommastellen. Die 0,00 darf den gueltigen Preis nicht ersetzen.
        var ergebnis = uebernimm(lieferant, position("CENT-1", "0.40", "100 C62", "C62"));

        assertEquals(0, ergebnis.uebernommen());
        assertEquals(1, ergebnis.uebersprungen());
        assertPreis("0.01", artikel, lieferant);
    }

    @Test
    void mehrerePositionenWerdenEinzelnBewertet() {
        Lieferanten lieferant = lieferant("Musterlieferant Sammelrechnung");
        Artikel erster = artikelMitPreis(lieferant, "SAM-1", Verrechnungseinheit.STUECK, "10.00");
        Artikel zweiter = artikelMitPreis(lieferant, "SAM-2", Verrechnungseinheit.STUECK, "20.00");

        // Die mittlere Position ist unbrauchbar (Artikelnummer nicht hinterlegt) -
        // die beiden anderen muessen trotzdem durchkommen.
        var ergebnis = preisUebernahmeService.uebernehmePreise(lieferant, PreisQuelle.RECHNUNG, new Date(),
                List.of(position("SAM-1", "11.00", "1 C62", "C62"),
                        position("GIBTS-NICHT", "5.00", "1 C62", "C62"),
                        position("SAM-2", "21.00", "1 C62", "C62")));

        assertEquals(2, ergebnis.uebernommen());
        assertEquals(1, ergebnis.uebersprungen());
        assertPreis("11.00", erster, lieferant);
        assertPreis("21.00", zweiter, lieferant);
    }

    /**
     * Der Datenbankzugriff wird hier absichtlich zum Stolpern gebracht: eine
     * Position, die eine Ausnahme ausloest, darf die uebrigen Positionen derselben
     * Rechnung nicht mitreissen.
     */
    @Test
    void ausnahmeBeiEinerPositionStopptDieUebrigenNicht() {
        Lieferanten lieferant = lieferant("Musterlieferant Stolperstein");

        // Freies Fixture statt DB-Entity: die Klasse laeuft ohne Transaktion, ein
        // gelesener Preisstand wuerde beim Zugriff auf den Artikel lazy nachladen.
        Artikel artikel = new Artikel();
        artikel.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        LieferantenArtikelPreise vorhanden = preisstand(artikel, lieferant, "OK-1", "10.00");

        LieferantenArtikelPreiseRepository stolpernd = mock(LieferantenArtikelPreiseRepository.class);
        when(stolpernd.findByExterneArtikelnummerIgnoreCaseAndLieferant_IdAndAktuellTrue(eq("KRACH"), any()))
                .thenThrow(new IllegalStateException("Verbindung weggebrochen"));
        when(stolpernd.findByExterneArtikelnummerIgnoreCaseAndLieferant_IdAndAktuellTrue(eq("OK-1"), any()))
                .thenReturn(Optional.of(vorhanden));

        var ergebnis = new PreisUebernahmeService(stolpernd).uebernehmePreise(
                lieferant, PreisQuelle.RECHNUNG, new Date(),
                List.of(position("KRACH", "9.00", "1 C62", "C62"),
                        position("OK-1", "12.00", "1 C62", "C62")));

        assertEquals(1, ergebnis.uebernommen());
        assertEquals(1, ergebnis.uebersprungen());
    }

    @Test
    void negativerPreisWirdUebersprungen() {
        Lieferanten lieferant = lieferant("Musterlieferant Gutschrift");
        Artikel artikel = artikelMitPreis(lieferant, "N-1", Verrechnungseinheit.STUECK, "10.00");

        var ergebnis = uebernimm(lieferant, position("N-1", "-5.00", "1 C62", "C62"));

        assertEquals(1, ergebnis.uebersprungen());
        assertPreis("10.00", artikel, lieferant);
    }

    @Test
    void ohneLieferantPassiertNichts() {
        var ergebnis = preisUebernahmeService.uebernehmePreise(null, PreisQuelle.RECHNUNG, null,
                List.of(position("X", "1.00", "1 C62", "C62")));

        assertEquals(0, ergebnis.uebernommen());
        assertEquals(0, ergebnis.uebersprungen());
    }

    @Test
    void leereListeIstKeinFehler() {
        Lieferanten lieferant = lieferant("Musterlieferant Ohne Positionen");

        var ergebnis = preisUebernahmeService.uebernehmePreise(lieferant, PreisQuelle.RECHNUNG, null, List.of());

        assertEquals(0, ergebnis.uebernommen());
        assertEquals(0, ergebnis.uebersprungen());
    }

    @Test
    void genauEinPreisstandBleibtAktuell() {
        Lieferanten lieferant = lieferant("Musterlieferant Eindeutig");
        Artikel artikel = artikelMitPreis(lieferant, "E-1", Verrechnungseinheit.STUECK, "10.00");

        Date frueher = new Date(new Date().getTime() - 60_000);
        preisUebernahmeService.uebernehmePreise(lieferant, PreisQuelle.RECHNUNG, frueher,
                List.of(position("E-1", "11.00", "1 C62", "C62")));
        preisUebernahmeService.uebernehmePreise(lieferant, PreisQuelle.RECHNUNG, new Date(),
                List.of(position("E-1", "12.00", "1 C62", "C62")));

        List<LieferantenArtikelPreise> verlauf = artikelPreiseRepository.findeVerlauf(artikel.getId());
        assertEquals(3, verlauf.size());
        assertEquals(1, verlauf.stream().filter(LieferantenArtikelPreise::isAktuell).count());
        assertFalse(verlauf.stream().filter(LieferantenArtikelPreise::isAktuell)
                .anyMatch(p -> new BigDecimal("12.00").compareTo(p.getPreis()) != 0));
    }

    // ------------------------------------------------------------------
    // Einheiten-Deutung als Einzelteil
    // ------------------------------------------------------------------

    @Test
    void deutetGaengigeEinheitenAusZugferdUndKi() {
        assertEquals(Verrechnungseinheit.KILOGRAMM,
                PreisUebernahmeService.deutePreisbasis("1000 KGM", null).art());
        assertEquals(0, new BigDecimal("1000").compareTo(
                PreisUebernahmeService.deutePreisbasis("1000 KGM", null).menge()));

        assertEquals(Verrechnungseinheit.KILOGRAMM,
                PreisUebernahmeService.deutePreisbasis("t", null).art());
        assertEquals(0, new BigDecimal("1000").compareTo(
                PreisUebernahmeService.deutePreisbasis("t", null).menge()));

        assertEquals(Verrechnungseinheit.STUECK,
                PreisUebernahmeService.deutePreisbasis("Stück", null).art());
        assertEquals(Verrechnungseinheit.STUECK,
                PreisUebernahmeService.deutePreisbasis(null, "H87").art());
        assertEquals(Verrechnungseinheit.QUADRATMETER,
                PreisUebernahmeService.deutePreisbasis("1 MTK", null).art());
    }

    @Test
    void liestDeutscheTausendertrennungAlsGanzeZahl() {
        var basis = PreisUebernahmeService.deutePreisbasis("1.000 kg", null);

        assertEquals(Verrechnungseinheit.KILOGRAMM, basis.art());
        assertEquals(0, new BigDecimal("1000").compareTo(basis.menge()));
    }

    @Test
    void unbekannteEinheitLiefertKeineArtUndMengeEins() {
        var ohneZahl = PreisUebernahmeService.deutePreisbasis("1 XYZ", null);
        assertNull(ohneZahl.art());
        assertEquals(0, BigDecimal.ONE.compareTo(ohneZahl.menge()));

        // Auch eine grosse Zahl vor einem unbekannten Kuerzel darf nicht teilen.
        var mitZahl = PreisUebernahmeService.deutePreisbasis("6 m Stangen", null);
        assertNull(mitZahl.art());
        assertEquals(0, BigDecimal.ONE.compareTo(mitZahl.menge()));
    }

    @Test
    void mengeneinheitDientNurAlsRueckfallebene() {
        // Preiseinheit gewinnt: 100 kg, obwohl die Lieferung in Stueck gezaehlt wird.
        var basis = PreisUebernahmeService.deutePreisbasis("100 kg", "C62");

        assertEquals(Verrechnungseinheit.KILOGRAMM, basis.art());
        assertEquals(0, new BigDecimal("100").compareTo(basis.menge()));
    }

    @Test
    void fuellwoerterStoerenDieDeutungNicht() {
        assertEquals(Verrechnungseinheit.KILOGRAMM,
                PreisUebernahmeService.deutePreisbasis("pro 100 kg", null).art());
        assertEquals(0, new BigDecimal("100").compareTo(
                PreisUebernahmeService.deutePreisbasis("je 100 kg", null).menge()));
    }

    // ------------------------------------------------------------------
    // Hilfsmittel
    // ------------------------------------------------------------------

    private PreisUebernahmeService.Ergebnis uebernimm(Lieferanten lieferant,
                                                      PreisUebernahmeService.Position position) {
        return preisUebernahmeService.uebernehmePreise(lieferant, PreisQuelle.RECHNUNG, new Date(),
                List.of(position));
    }

    private PreisUebernahmeService.Position position(String nummer, String preis,
                                                     String preiseinheit, String mengeneinheit) {
        return new PreisUebernahmeService.Position(nummer, new BigDecimal(preis), preiseinheit, mengeneinheit);
    }

    private Lieferanten lieferant(String name) {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setLieferantenname(name);
        return lieferantenRepository.save(lieferant);
    }

    private Artikel artikelMitPreis(Lieferanten lieferant, String externeNummer,
                                    Verrechnungseinheit einheit, String preis) {
        return artikelMitPreis(lieferant, externeNummer, einheit, preis, null);
    }

    /** @param staffel Wert des Artikelfelds {@code preiseinheit}, z.B. "100" bei Schrauben */
    private Artikel artikelMitPreis(Lieferanten lieferant, String externeNummer,
                                    Verrechnungseinheit einheit, String preis, String staffel) {
        Artikel artikel = new Artikel();
        artikel.setVerrechnungseinheit(einheit);
        artikel.setPreiseinheit(staffel);
        artikel.getArtikelpreis().add(preisstand(artikel, lieferant, externeNummer, preis));
        return artikelRepository.save(artikel);
    }

    private LieferantenArtikelPreise preisstand(Artikel artikel, Lieferanten lieferant,
                                                String externeNummer, String preis) {
        LieferantenArtikelPreise stand = new LieferantenArtikelPreise();
        stand.setArtikel(artikel);
        stand.setLieferant(lieferant);
        stand.setExterneArtikelnummer(externeNummer);
        stand.setPreis(new BigDecimal(preis));
        stand.setPreisAenderungsdatum(BESTANDSSTAND);
        stand.setQuelle(PreisQuelle.MANUELL);
        return stand;
    }

    private void assertPreis(String erwartet, Artikel artikel, Lieferanten lieferant) {
        BigDecimal ist = artikelPreiseRepository
                .findByArtikel_IdAndLieferant_IdAndAktuellTrue(artikel.getId(), lieferant.getId())
                .orElseThrow().getPreis();
        assertEquals(0, new BigDecimal(erwartet).compareTo(ist),
                () -> "erwartet " + erwartet + " EUR, war " + ist + " EUR");
    }
}
