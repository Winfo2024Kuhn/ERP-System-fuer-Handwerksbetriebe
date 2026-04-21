package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.dto.Bestellung.BestellungResponseDto;
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.KategorieRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
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
        LieferantenRepository lieferantenRepo = mock(LieferantenRepository.class);
        KategorieRepository kategorieRepo = mock(KategorieRepository.class);
        ArtikelRepository artikelRepo = mock(ArtikelRepository.class);
        ZeugnisService zeugnisService = new ZeugnisService(kategorieRepo);
        BestellauftragService bestellauftragService = mock(BestellauftragService.class);
        BestellungService service = new BestellungService(repo, projektRepo, lieferantenRepo, kategorieRepo, artikelRepo, zeugnisService, bestellauftragService);

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
        aip.setSchnittForm("A");
        aip.setAnschnittWinkelLinks("45");
        aip.setAnschnittWinkelRechts("90");

        when(repo.findByBestelltFalseOrderByLieferant_LieferantennameAscProjekt_BauvorhabenAsc())
                .thenReturn(List.of(aip));

        List<BestellungResponseDto> dtos = service.findeOffeneBestellungen();
        assertEquals(1, dtos.size());
        BestellungResponseDto dto = dtos.getFirst();
        assertEquals("A", dto.getSchnittForm());
        assertEquals("45", dto.getAnschnittWinkelLinks());
        assertEquals("90", dto.getAnschnittWinkelRechts());
        assertEquals("Test", dto.getKommentar());
    }
}

