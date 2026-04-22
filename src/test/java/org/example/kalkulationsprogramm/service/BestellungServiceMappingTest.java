package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.dto.Bestellung.BestellungResponseDto;
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.KategorieRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BestellungServiceMappingTest {

    @Test
    void mapsAnglesAndFormIntoDto() {
        ArtikelInProjektRepository repo = mock(ArtikelInProjektRepository.class);
        ProjektRepository projektRepo = mock(ProjektRepository.class);
        KategorieRepository kategorieRepo = mock(KategorieRepository.class);
        ArtikelRepository artikelRepo = mock(ArtikelRepository.class);
        ZeugnisService zeugnisService = new ZeugnisService(kategorieRepo);
        BestellungService service = new BestellungService(repo, projektRepo, kategorieRepo, artikelRepo, zeugnisService);

        Kategorie kat = new Kategorie();
        kat.setId(2);
        kat.setBeschreibung("Formstahl");
        Artikel a = new Artikel();
        a.setId(10L);
        a.setExterneArtikelnummer("X-123");
        a.setProduktname("Profil");
        a.setProdukttext("Fixzuschnitt");
        a.setKategorie(kat);

        ArtikelInProjekt aip = new ArtikelInProjekt();
        aip.setId(5L);
        aip.setArtikel(a);
        aip.setStueckzahl(3);
        aip.setKilogramm(new BigDecimal("12.5"));
        aip.setHinzugefuegtAm(LocalDate.now());
        aip.setKommentar("Test");
        Schnittbilder sb = new Schnittbilder();
        sb.setId(7L);
        sb.setForm("A");
        sb.setBildUrlSchnittbild("/uploads/schnittbilder/a.png");
        aip.setSchnittbild(sb);
        aip.setAnschnittWinkelLinks(45.0);
        aip.setAnschnittWinkelRechts(90.0);

        when(repo.findByQuelleOrderByProjekt_BauvorhabenAsc(
                BestellQuelle.OFFEN)).thenReturn(List.of(aip));

        List<BestellungResponseDto> dtos = service.findeOffeneBestellungen();
        assertEquals(1, dtos.size());
        BestellungResponseDto dto = dtos.getFirst();
        assertEquals("A", dto.getSchnittbildForm());
        assertEquals(45.0, dto.getAnschnittWinkelLinks());
        assertEquals(90.0, dto.getAnschnittWinkelRechts());
        assertEquals("Test", dto.getKommentar());
    }
}
