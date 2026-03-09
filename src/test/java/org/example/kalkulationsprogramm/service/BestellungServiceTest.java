package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.dto.Bestellung.BestellungResponseDto;
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

}
