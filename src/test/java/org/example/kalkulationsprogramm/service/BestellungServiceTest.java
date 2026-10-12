package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.dto.Bestellung.BestellungResponseDto;
import org.example.kalkulationsprogramm.mapper.AnfrageMapper;
import org.example.kalkulationsprogramm.mapper.KundeMapper;
import org.example.kalkulationsprogramm.mapper.ProduktkategorieMapper;
import org.example.kalkulationsprogramm.mapper.ProjektMapper;
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BestellungServiceTest {

    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Mock
    private ArtikelInProjektRepository artikelInProjektRepository;

    private BestellungService service;

    @BeforeEach
    void setup() {
        service = new BestellungService(artikelInProjektRepository);
    }

    @Test
    void usesStoredKilogrammInDto() {
        ArtikelWerkstoffe artikel = new ArtikelWerkstoffe();

        ArtikelInProjekt aip = new ArtikelInProjekt();
        aip.setArtikel(artikel);
        aip.setKilogramm(new BigDecimal("10.0"));

        when(artikelInProjektRepository.findByBestelltFalseOrderByLieferant_LieferantennameAscProjekt_BauvorhabenAsc())
                .thenReturn(List.of(aip));

        List<BestellungResponseDto> result = service.findeOffeneBestellungen();

        assertEquals(0, result.getFirst().getKilogramm().compareTo(new BigDecimal("10.0")));
        assertEquals(0, result.getFirst().getGesamtKilogramm().compareTo(new BigDecimal("10.0")));
    }

    @Test
    void setBestelltHandlesNullPrice() {
        Artikel artikel = new Artikel();
        artikel.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        ArtikelInProjekt aip = new ArtikelInProjekt();
        aip.setId(5L);
        aip.setArtikel(artikel);
        LieferantenArtikelPreise lap = new LieferantenArtikelPreise();
        lap.setArtikel(new Artikel());
        lap.setLieferant(new Lieferanten());
        aip.setLieferantenArtikelPreis(lap);

        when(artikelInProjektRepository.findById(5L)).thenReturn(Optional.of(aip));
        when(artikelInProjektRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.setBestellt(5L, true);

        assertNull(aip.getPreisProStueck());
        assertNull(aip.getLieferantenArtikelPreis());
    }

    @Test
    void categoryOneArticlesGroupedAsWerkstoffe() {
        Kategorie root = new Kategorie();
        root.setId(1);
        Kategorie child = new Kategorie();
        child.setId(10);
        child.setParentKategorie(root);

        Artikel artikel = new Artikel();
        artikel.setKategorie(child);
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(3L);
        lieferant.setLieferantenname("L3");

        ArtikelInProjekt aip = new ArtikelInProjekt();
        aip.setArtikel(artikel);
        aip.setLieferant(lieferant);

        when(artikelInProjektRepository.findByBestelltFalseOrderByLieferant_LieferantennameAscProjekt_BauvorhabenAsc())
                .thenReturn(List.of(aip));

        List<BestellungResponseDto> result = service.findeOffeneBestellungen();

        assertEquals("Werkstoffe", result.getFirst().getLieferantName());
        assertNull(result.getFirst().getLieferantId());
    }

    @Test
    void categoryTwoKeepsSupplier() {
        Kategorie root = new Kategorie();
        root.setId(2);
        Kategorie child = new Kategorie();
        child.setId(20);
        child.setParentKategorie(root);

        Artikel artikel = new Artikel();
        artikel.setKategorie(child);
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(5L);
        lieferant.setLieferantenname("L5");

        ArtikelInProjekt aip = new ArtikelInProjekt();
        aip.setArtikel(artikel);
        aip.setLieferant(lieferant);

        when(artikelInProjektRepository.findByBestelltFalseOrderByLieferant_LieferantennameAscProjekt_BauvorhabenAsc())
                .thenReturn(List.of(aip));

        List<BestellungResponseDto> result = service.findeOffeneBestellungen();

        assertEquals("L5", result.getFirst().getLieferantName());
        assertEquals(5L, result.getFirst().getLieferantId());
    }

    @Test
    void nonWerkstoffArticlesUsePiecesEvenWhenMeterExists() {
        Kategorie nonWerkstoff = new Kategorie();
        nonWerkstoff.setId(2);

        Artikel artikel = new Artikel();
        artikel.setKategorie(nonWerkstoff);

        ArtikelInProjekt aip = new ArtikelInProjekt();
        aip.setArtikel(artikel);
        aip.setMeter(new BigDecimal("5"));
        aip.setStueckzahl(3);

        when(artikelInProjektRepository
                .findByBestelltFalseOrderByLieferant_LieferantennameAscProjekt_BauvorhabenAsc())
                .thenReturn(List.of(aip));

        BestellungResponseDto result = service.findeOffeneBestellungen().getFirst();

        assertEquals(0, result.getMenge().compareTo(new BigDecimal("3")));
        assertEquals("Stück", result.getEinheit());
    }

    @Test
    void setsRootKategorieFields() {
        Kategorie root = new Kategorie();
        root.setId(1);
        root.setBeschreibung("Werkstoffe");
        Kategorie child = new Kategorie();
        child.setId(10);
        child.setParentKategorie(root);

        Artikel artikel = new Artikel();
        artikel.setKategorie(child);

        ArtikelInProjekt aip = new ArtikelInProjekt();
        aip.setArtikel(artikel);

        when(artikelInProjektRepository.findByBestelltFalseOrderByLieferant_LieferantennameAscProjekt_BauvorhabenAsc())
                .thenReturn(List.of(aip));

        BestellungResponseDto dto = service.findeOffeneBestellungen().getFirst();
        assertEquals(1, dto.getRootKategorieId());
        assertEquals("Werkstoffe", dto.getRootKategorieName());
    }

    /**
     * Was im Projekt als Gesamtpreis der Position erscheint - genau so, wie
     * die Nachkalkulation es summiert.
     */
    private static BigDecimal sichtbarerGesamtpreis(ArtikelInProjekt aip) {
        ProjektMapper mapper = new ProjektMapper(new ProduktkategorieMapper(), new AnfrageMapper(),
                mock(KundeMapper.class));
        Projekt projekt = new Projekt();
        projekt.getArtikelInProjekt().add(aip);
        return mapper.toProjektResponseDto(projekt).getArtikel().getFirst().getGesamtpreis();
    }

    private ArtikelInProjekt offenePosition(Verrechnungseinheit ve, String preisJeEinheit) {
        Artikel artikel = new Artikel();
        artikel.setVerrechnungseinheit(ve);
        LieferantenArtikelPreise lap = new LieferantenArtikelPreise();
        lap.setArtikel(artikel);
        lap.setPreis(new BigDecimal(preisJeEinheit));

        ArtikelInProjekt aip = new ArtikelInProjekt();
        aip.setId(7L);
        aip.setArtikel(artikel);
        aip.setLieferantenArtikelPreis(lap);
        aip.setPreisProStueck(new BigDecimal(preisJeEinheit));

        lenient().when(artikelInProjektRepository.findById(7L)).thenReturn(Optional.of(aip));
        lenient().when(artikelInProjektRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        return aip;
    }

    @Test
    void zuruecknehmenVonBestelltMultipliziertDenEingefrorenenGesamtpreisNichtNochmal() {
        ArtikelInProjekt aip = offenePosition(Verrechnungseinheit.STUECK, "15.00");
        aip.setStueckzahl(4);
        assertEquals(0, sichtbarerGesamtpreis(aip).compareTo(new BigDecimal("60")));

        service.setBestellt(7L, true);
        assertEquals(0, sichtbarerGesamtpreis(aip).compareTo(new BigDecimal("60")));

        service.setBestellt(7L, false);

        assertFalse(aip.isBestellt());
        assertNull(aip.getBestelltAm());
        assertEquals(0, sichtbarerGesamtpreis(aip).compareTo(new BigDecimal("60")),
                "4 Stueck zu je 15 bleiben 60 - nicht 4 x 60 = 240");
    }

    @Test
    void erneutesAbhakenNachZuruecknehmenFriertWiederDieRichtigeSummeEin() {
        ArtikelInProjekt aip = offenePosition(Verrechnungseinheit.KILOGRAMM, "1.80");
        aip.setKilogramm(new BigDecimal("120.50"));

        service.setBestellt(7L, true);
        service.setBestellt(7L, false);
        assertEquals(0, sichtbarerGesamtpreis(aip).compareTo(new BigDecimal("216.90")));

        service.setBestellt(7L, true);

        assertTrue(aip.isBestellt());
        assertEquals(0, aip.getPreisProStueck().compareTo(new BigDecimal("216.90")));
        assertEquals(0, sichtbarerGesamtpreis(aip).compareTo(new BigDecimal("216.90")));
    }

    @Test
    void doppeltesZuruecknehmenRechntDenPreisNichtZweimalUm() {
        ArtikelInProjekt aip = offenePosition(Verrechnungseinheit.LAUFENDE_METER, "12.50");
        aip.setMeter(new BigDecimal("50"));

        service.setBestellt(7L, true);
        service.setBestellt(7L, false);
        service.setBestellt(7L, false);

        assertEquals(0, sichtbarerGesamtpreis(aip).compareTo(new BigDecimal("625.00")));
    }

    @Test
    void doppeltesAbhakenRechntDenPreisNichtZweimalUm() {
        ArtikelInProjekt aip = offenePosition(Verrechnungseinheit.STUECK, "0.35");
        aip.setStueckzahl(200);

        service.setBestellt(7L, true);
        service.setBestellt(7L, true);

        assertEquals(0, sichtbarerGesamtpreis(aip).compareTo(new BigDecimal("70.00")));
    }

    /**
     * Lagerware traegt in {@code preisProStueck} immer den Preis je Einheit
     * (siehe ProjektMapper). Abhaken und Zuruecknehmen duerfen daran nichts
     * umrechnen.
     */
    @Test
    void lagerwareBehaeltIhrenEinzelpreisBeimUmschalten() {
        ArtikelInProjekt aip = offenePosition(Verrechnungseinheit.STUECK, "12.50");
        aip.setStueckzahl(3);
        aip.setAusLager(true);
        aip.setBestellt(true);

        service.setBestellt(7L, false);
        assertEquals(0, aip.getPreisProStueck().compareTo(new BigDecimal("12.50")));

        service.setBestellt(7L, true);
        assertEquals(0, aip.getPreisProStueck().compareTo(new BigDecimal("12.50")));
        assertEquals(0, sichtbarerGesamtpreis(aip).compareTo(new BigDecimal("37.50")));
    }

    @Test
    void zuruecknehmenBeiMengeNullLaesstDenPreisStehen() {
        ArtikelInProjekt aip = offenePosition(Verrechnungseinheit.KILOGRAMM, "1.80");
        aip.setKilogramm(BigDecimal.ZERO);

        service.setBestellt(7L, true);
        service.setBestellt(7L, false);

        assertEquals(0, aip.getPreisProStueck().compareTo(BigDecimal.ZERO));
        assertEquals(0, sichtbarerGesamtpreis(aip).compareTo(BigDecimal.ZERO));
    }

    @Test
    void ohneVerrechnungseinheitWirdNichtMitDerMengeGerechnet() {
        ArtikelInProjekt aip = offenePosition(Verrechnungseinheit.STUECK, "9.50");
        aip.getArtikel().setVerrechnungseinheit(null);
        aip.setStueckzahl(5);

        service.setBestellt(7L, true);
        assertEquals(0, aip.getPreisProStueck().compareTo(new BigDecimal("9.50")));

        service.setBestellt(7L, false);
        assertEquals(0, aip.getPreisProStueck().compareTo(new BigDecimal("9.50")));
    }

}
