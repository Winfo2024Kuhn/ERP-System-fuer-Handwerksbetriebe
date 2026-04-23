package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.dto.Bestellung.BestellungResponseDto;
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.KategorieRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
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

    @Mock private ArtikelInProjektRepository artikelInProjektRepository;
    @Mock private ProjektRepository projektRepository;
    @Mock private KategorieRepository kategorieRepository;
    @Mock private ArtikelRepository artikelRepository;
    @Mock private org.example.kalkulationsprogramm.repository.SchnittbilderRepository schnittbilderRepository;

    private BestellungService service;

    @BeforeEach
    void setup() {
        ZeugnisService zeugnisService = new ZeugnisService(kategorieRepository);
        service = new BestellungService(artikelInProjektRepository, projektRepository, kategorieRepository, artikelRepository, schnittbilderRepository, zeugnisService);
    }

    @Test
    void usesStoredKilogrammInDto() {
        ArtikelWerkstoffe artikel = new ArtikelWerkstoffe();

        ArtikelInProjekt aip = new ArtikelInProjekt();
        aip.setArtikel(artikel);
        aip.setKilogramm(new BigDecimal("10.0"));

        when(artikelInProjektRepository.findByQuelleOrderByProjekt_BauvorhabenAsc(BestellQuelle.OFFEN))
                .thenReturn(List.of(aip));

        List<BestellungResponseDto> result = service.findeOffeneBestellungen();

        assertEquals(0, result.getFirst().getKilogramm().compareTo(new BigDecimal("10.0")));
        assertEquals(0, result.getFirst().getGesamtKilogramm().compareTo(new BigDecimal("10.0")));
    }

    @Test
    void setBestelltNullSafeOhneLieferantUndPreis() {
        // Nach Stufe A2: AiP hat weder Lieferant noch LieferantenArtikelPreis —
        // setBestellt darf nur die Quelle umschalten und keine Preis-Berechnung
        // mehr versuchen.
        Artikel artikel = new Artikel();
        artikel.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        ArtikelInProjekt aip = new ArtikelInProjekt();
        aip.setId(5L);
        aip.setArtikel(artikel);

        when(artikelInProjektRepository.findById(5L)).thenReturn(Optional.of(aip));
        when(artikelInProjektRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.setBestellt(5L, true);

        assertNull(aip.getPreisProStueck());
        assertEquals(BestellQuelle.BESTELLT, aip.getQuelle());
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

        ArtikelInProjekt aip = new ArtikelInProjekt();
        aip.setArtikel(artikel);

        when(artikelInProjektRepository.findByQuelleOrderByProjekt_BauvorhabenAsc(BestellQuelle.OFFEN))
                .thenReturn(List.of(aip));

        List<BestellungResponseDto> result = service.findeOffeneBestellungen();

        // Werkstoff-Positionen tragen den Platzhalter "Werkstoffe" im UI —
        // der Lieferant lebt ab A2 ausschliesslich auf der Bestellung.
        assertEquals("Werkstoffe", result.getFirst().getLieferantName());
        assertNull(result.getFirst().getLieferantId());
    }

    @Test
    void nonWerkstoffCategoryLaesstLieferantNameLeer() {
        // Ohne Lieferant-Feld auf AiP ist der LieferantName in der Bedarf-
        // Sicht immer leer — sobald der Bedarf in eine Bestellung umgebucht
        // wird, kommt der Name aus `bestellung.lieferant`.
        Kategorie root = new Kategorie();
        root.setId(2);
        Kategorie child = new Kategorie();
        child.setId(20);
        child.setParentKategorie(root);

        Artikel artikel = new Artikel();
        artikel.setKategorie(child);

        ArtikelInProjekt aip = new ArtikelInProjekt();
        aip.setArtikel(artikel);

        when(artikelInProjektRepository.findByQuelleOrderByProjekt_BauvorhabenAsc(BestellQuelle.OFFEN))
                .thenReturn(List.of(aip));

        List<BestellungResponseDto> result = service.findeOffeneBestellungen();

        assertNull(result.getFirst().getLieferantName());
        assertNull(result.getFirst().getLieferantId());
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
                .findByQuelleOrderByProjekt_BauvorhabenAsc(BestellQuelle.OFFEN))
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

        when(artikelInProjektRepository.findByQuelleOrderByProjekt_BauvorhabenAsc(BestellQuelle.OFFEN))
                .thenReturn(List.of(aip));

        BestellungResponseDto dto = service.findeOffeneBestellungen().getFirst();
        assertEquals(1, dto.getRootKategorieId());
        assertEquals("Werkstoffe", dto.getRootKategorieName());
    }

}
