package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.time.Year;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelInProjekt;
import org.example.kalkulationsprogramm.domain.BestellStatus;
import org.example.kalkulationsprogramm.domain.Bestellposition;
import org.example.kalkulationsprogramm.domain.Bestellung;
import org.example.kalkulationsprogramm.domain.Kategorie;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.ZeugnisTyp;
import org.example.kalkulationsprogramm.repository.BestellungRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BestellauftragServiceTest {

    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Mock
    private BestellungRepository bestellungRepository;

    private BestellauftragService service;

    @BeforeEach
    void setup() {
        service = new BestellauftragService(bestellungRepository);
        lenient().when(bestellungRepository.save(any(Bestellung.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    private Lieferanten lieferant(long id, String name) {
        Lieferanten l = new Lieferanten();
        l.setId(id);
        l.setLieferantenname(name);
        return l;
    }

    private Projekt projekt(long id) {
        Projekt p = new Projekt();
        p.setId(id);
        return p;
    }

    private ArtikelInProjekt aip(Lieferanten l, Projekt p) {
        ArtikelInProjekt a = new ArtikelInProjekt();
        a.setLieferant(l);
        a.setProjekt(p);
        return a;
    }

    // ─── Tests ────────────────────────────────────────────────────────

    @Test
    void leereListeErzeugtKeineBestellungen() {
        List<Bestellung> result = service.erzeugeBestellungenAusExport(List.of(), null);
        assertTrue(result.isEmpty());
        verify(bestellungRepository, times(0)).save(any());
    }

    @Test
    void nullListeErzeugtKeineBestellungen() {
        List<Bestellung> result = service.erzeugeBestellungenAusExport(null, null);
        assertTrue(result.isEmpty());
    }

    @Test
    void gruppierungNachLieferantUndProjekt() {
        Lieferanten l1 = lieferant(1L, "Stahl AG");
        Lieferanten l2 = lieferant(2L, "Holz GmbH");
        Projekt p1 = projekt(10L);
        Projekt p2 = projekt(20L);

        // 4 AiPs: (l1,p1), (l1,p1), (l2,p1), (l1,p2) → 3 Bestellungen
        List<ArtikelInProjekt> positionen = List.of(
                aip(l1, p1),
                aip(l1, p1),
                aip(l2, p1),
                aip(l1, p2)
        );
        when(bestellungRepository.findMaxBestellnummerForPrefix(anyString())).thenReturn(Optional.empty());

        List<Bestellung> result = service.erzeugeBestellungenAusExport(positionen, null);

        assertEquals(3, result.size());
        Bestellung gruppeL1P1 = result.stream()
                .filter(b -> b.getLieferant().getId().equals(1L) && b.getProjekt().getId().equals(10L))
                .findFirst().orElseThrow();
        assertEquals(2, gruppeL1P1.getPositionen().size());
    }

    @Test
    void gruppierungMitNullLieferantUndNullProjekt() {
        ArtikelInProjekt a1 = new ArtikelInProjekt(); // Lieferant + Projekt null
        ArtikelInProjekt a2 = new ArtikelInProjekt(); // ebenfalls null/null
        when(bestellungRepository.findMaxBestellnummerForPrefix(anyString())).thenReturn(Optional.empty());

        List<Bestellung> result = service.erzeugeBestellungenAusExport(List.of(a1, a2), null);

        assertEquals(1, result.size());
        assertEquals(2, result.getFirst().getPositionen().size());
        assertNull(result.getFirst().getLieferant());
        assertNull(result.getFirst().getProjekt());
    }

    @Test
    void snapshotUebernimmtAlleRelevantenFelder() {
        Kategorie kategorie = new Kategorie();
        kategorie.setId(5);
        Artikel artikel = new Artikel();
        artikel.setKategorie(kategorie);
        artikel.setExterneArtikelnummer("EX-4711");

        Lieferanten l = lieferant(1L, "Stahl AG");
        Projekt p = projekt(10L);

        ArtikelInProjekt a = aip(l, p);
        a.setArtikel(artikel);
        a.setStueckzahl(3);
        a.setKilogramm(new BigDecimal("120.50"));
        a.setPreisProStueck(new BigDecimal("42.00"));
        a.setSchnittForm("gerade");
        a.setAnschnittWinkelLinks("45");
        a.setAnschnittWinkelRechts("30");
        a.setFixmassMm(6000);
        a.setZeugnisAnforderung(ZeugnisTyp.APZ_3_1);
        a.setKommentar("bitte sauber sägen");

        when(bestellungRepository.findMaxBestellnummerForPrefix(anyString())).thenReturn(Optional.empty());

        Bestellung bestellung = service.erzeugeBestellungenAusExport(List.of(a), null).getFirst();
        Bestellposition bp = bestellung.getPositionen().getFirst();

        assertAll(
                () -> assertEquals(artikel, bp.getArtikel()),
                () -> assertEquals("EX-4711", bp.getExterneArtikelnummer()),
                () -> assertEquals(kategorie, bp.getKategorie()),
                () -> assertEquals(3, bp.getStueckzahl()),
                () -> assertEquals(0, bp.getKilogramm().compareTo(new BigDecimal("120.50"))),
                () -> assertEquals(0, bp.getPreisProEinheit().compareTo(new BigDecimal("42.00"))),
                () -> assertEquals("gerade", bp.getSchnittForm()),
                () -> assertEquals("45", bp.getAnschnittWinkelLinks()),
                () -> assertEquals("30", bp.getAnschnittWinkelRechts()),
                () -> assertEquals(6000, bp.getFixmassMm()),
                () -> assertEquals(ZeugnisTyp.APZ_3_1, bp.getZeugnisAnforderung()),
                () -> assertEquals("bitte sauber sägen", bp.getKommentar()),
                () -> assertEquals(a, bp.getAusArtikelInProjekt()),
                () -> assertEquals(1, bp.getPositionsnummer())
        );
    }

    @Test
    void positionsnummernFortlaufendProBestellung() {
        Lieferanten l = lieferant(1L, "Stahl AG");
        Projekt p = projekt(10L);
        List<ArtikelInProjekt> positionen = List.of(aip(l, p), aip(l, p), aip(l, p));
        when(bestellungRepository.findMaxBestellnummerForPrefix(anyString())).thenReturn(Optional.empty());

        Bestellung b = service.erzeugeBestellungenAusExport(positionen, null).getFirst();

        assertEquals(List.of(1, 2, 3),
                b.getPositionen().stream().map(Bestellposition::getPositionsnummer).toList());
    }

    @Test
    void freitextPositionOhneArtikel() {
        Lieferanten l = lieferant(1L, "Stahl AG");
        Projekt p = projekt(10L);
        ArtikelInProjekt a = aip(l, p);
        a.setFreitextProduktname("Sonderprofil Ü-Schiene");
        a.setFreitextProdukttext("Spezialanfertigung nach Skizze");
        a.setFreitextMenge(new BigDecimal("7.5"));
        a.setFreitextEinheit("lfm");

        when(bestellungRepository.findMaxBestellnummerForPrefix(anyString())).thenReturn(Optional.empty());

        Bestellposition bp = service.erzeugeBestellungenAusExport(List.of(a), null).getFirst()
                .getPositionen().getFirst();

        assertAll(
                () -> assertNull(bp.getArtikel()),
                () -> assertEquals("Sonderprofil Ü-Schiene", bp.getFreitextProduktname()),
                () -> assertEquals("Spezialanfertigung nach Skizze", bp.getFreitextProdukttext()),
                () -> assertEquals(0, bp.getMenge().compareTo(new BigDecimal("7.5"))),
                () -> assertEquals("lfm", bp.getEinheit())
        );
    }

    @Test
    void mengeAusMeterWennKeinFreitext() {
        Lieferanten l = lieferant(1L, "Stahl AG");
        ArtikelInProjekt a = aip(l, projekt(10L));
        a.setMeter(new BigDecimal("12.30"));
        a.setStueckzahl(5); // wird ignoriert, meter hat Vorrang

        when(bestellungRepository.findMaxBestellnummerForPrefix(anyString())).thenReturn(Optional.empty());

        Bestellposition bp = service.erzeugeBestellungenAusExport(List.of(a), null).getFirst()
                .getPositionen().getFirst();

        assertEquals(0, bp.getMenge().compareTo(new BigDecimal("12.30")));
        assertEquals("m", bp.getEinheit());
    }

    @Test
    void mengeAusStueckzahlWennKeinMeterUndKeinFreitext() {
        Lieferanten l = lieferant(1L, "Stahl AG");
        ArtikelInProjekt a = aip(l, projekt(10L));
        a.setStueckzahl(4);

        when(bestellungRepository.findMaxBestellnummerForPrefix(anyString())).thenReturn(Optional.empty());

        Bestellposition bp = service.erzeugeBestellungenAusExport(List.of(a), null).getFirst()
                .getPositionen().getFirst();

        assertEquals(0, bp.getMenge().compareTo(new BigDecimal("4")));
        assertEquals("Stück", bp.getEinheit());
    }

    @Test
    void kategorieFallbackAusArtikel() {
        Kategorie k = new Kategorie();
        k.setId(7);
        Artikel artikel = new Artikel();
        artikel.setKategorie(k);

        ArtikelInProjekt a = aip(lieferant(1L, "L"), projekt(10L));
        a.setArtikel(artikel);
        // a.kategorie bewusst nicht gesetzt

        when(bestellungRepository.findMaxBestellnummerForPrefix(anyString())).thenReturn(Optional.empty());

        Bestellposition bp = service.erzeugeBestellungenAusExport(List.of(a), null).getFirst()
                .getPositionen().getFirst();

        assertEquals(k, bp.getKategorie());
    }

    @Test
    void erstelltVonNullLiefertSystemAlsSnapshotName() {
        ArtikelInProjekt a = aip(lieferant(1L, "L"), projekt(10L));
        when(bestellungRepository.findMaxBestellnummerForPrefix(anyString())).thenReturn(Optional.empty());

        Bestellung b = service.erzeugeBestellungenAusExport(List.of(a), null).getFirst();

        assertNull(b.getErstelltVon());
        assertEquals("System", b.getErstelltVonName());
    }

    @Test
    void erstelltVonMitarbeiterSetztSnapshotNameAusVornameUndNachname() {
        Mitarbeiter m = new Mitarbeiter();
        m.setId(42L);
        m.setVorname("Max");
        m.setNachname("Mustermann");

        ArtikelInProjekt a = aip(lieferant(1L, "L"), projekt(10L));
        when(bestellungRepository.findMaxBestellnummerForPrefix(anyString())).thenReturn(Optional.empty());

        Bestellung b = service.erzeugeBestellungenAusExport(List.of(a), m).getFirst();

        assertEquals(m, b.getErstelltVon());
        assertEquals("Max Mustermann", b.getErstelltVonName());
    }

    @Test
    void erstelltVonOhneNamenFaelltAufMitarbeiterIdZurueck() {
        Mitarbeiter m = new Mitarbeiter();
        m.setId(99L);
        // vorname und nachname leer

        ArtikelInProjekt a = aip(lieferant(1L, "L"), projekt(10L));
        when(bestellungRepository.findMaxBestellnummerForPrefix(anyString())).thenReturn(Optional.empty());

        Bestellung b = service.erzeugeBestellungenAusExport(List.of(a), m).getFirst();

        assertEquals("Mitarbeiter #99", b.getErstelltVonName());
    }

    @Test
    void bestellungStatusUndZeitstempelWerdenGesetzt() {
        ArtikelInProjekt a = aip(lieferant(1L, "L"), projekt(10L));
        when(bestellungRepository.findMaxBestellnummerForPrefix(anyString())).thenReturn(Optional.empty());

        Bestellung b = service.erzeugeBestellungenAusExport(List.of(a), null).getFirst();

        assertAll(
                () -> assertEquals(BestellStatus.VERSENDET, b.getStatus()),
                () -> assertNotNull(b.getBestelltAm()),
                () -> assertNotNull(b.getVersendetAm()),
                () -> assertNotNull(b.getExportiertAm()),
                () -> assertNotNull(b.getErstelltAm())
        );
    }

    @Test
    void bestellnummerSchemaBYyyyNnnnBeiErsterBestellung() {
        ArtikelInProjekt a = aip(lieferant(1L, "L"), projekt(10L));
        when(bestellungRepository.findMaxBestellnummerForPrefix(anyString())).thenReturn(Optional.empty());

        Bestellung b = service.erzeugeBestellungenAusExport(List.of(a), null).getFirst();

        assertEquals("B-" + Year.now().getValue() + "-0001", b.getBestellnummer());
    }

    @Test
    void bestellnummerInkrementiertBestehendeLaufendeNummer() {
        String prefix = "B-" + Year.now().getValue() + "-";
        ArtikelInProjekt a = aip(lieferant(1L, "L"), projekt(10L));
        when(bestellungRepository.findMaxBestellnummerForPrefix(anyString()))
                .thenReturn(Optional.of(prefix + "0041"));

        Bestellung b = service.erzeugeBestellungenAusExport(List.of(a), null).getFirst();

        assertEquals(prefix + "0042", b.getBestellnummer());
    }

    @Test
    void bestellnummerFaelltAuf0001ZurueckBeiKorruptemZaehlstand() {
        String prefix = "B-" + Year.now().getValue() + "-";
        ArtikelInProjekt a = aip(lieferant(1L, "L"), projekt(10L));
        when(bestellungRepository.findMaxBestellnummerForPrefix(anyString()))
                .thenReturn(Optional.of(prefix + "XXXX"));

        Bestellung b = service.erzeugeBestellungenAusExport(List.of(a), null).getFirst();

        assertEquals(prefix + "0001", b.getBestellnummer());
    }

    @Test
    void jedeGruppeWirdEinmalGespeichert() {
        Lieferanten l1 = lieferant(1L, "L1");
        Lieferanten l2 = lieferant(2L, "L2");
        Projekt p = projekt(10L);

        List<ArtikelInProjekt> positionen = List.of(aip(l1, p), aip(l2, p));
        when(bestellungRepository.findMaxBestellnummerForPrefix(anyString())).thenReturn(Optional.empty());

        service.erzeugeBestellungenAusExport(positionen, null);

        ArgumentCaptor<Bestellung> captor = ArgumentCaptor.forClass(Bestellung.class);
        verify(bestellungRepository, times(2)).save(captor.capture());
        assertEquals(2, captor.getAllValues().size());
    }
}
