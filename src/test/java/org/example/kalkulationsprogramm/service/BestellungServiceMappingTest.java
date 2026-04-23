package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.dto.Bestellung.BestellungResponseDto;
import org.example.kalkulationsprogramm.dto.Bestellung.ManuelleBestellpositionDto;
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.KategorieRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.SchnittbilderRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BestellungServiceMappingTest {

    private BestellungService newService(ArtikelInProjektRepository repo,
                                         ProjektRepository projektRepo,
                                         KategorieRepository kategorieRepo,
                                         ArtikelRepository artikelRepo,
                                         SchnittbilderRepository schnittbilderRepo) {
        ZeugnisService zeugnisService = new ZeugnisService(kategorieRepo);
        return new BestellungService(repo, projektRepo, kategorieRepo, artikelRepo,
                schnittbilderRepo, zeugnisService);
    }

    @Test
    void mapsSchnittbildUndAchsenBildInsDto() {
        ArtikelInProjektRepository repo = mock(ArtikelInProjektRepository.class);
        ProjektRepository projektRepo = mock(ProjektRepository.class);
        KategorieRepository kategorieRepo = mock(KategorieRepository.class);
        ArtikelRepository artikelRepo = mock(ArtikelRepository.class);
        SchnittbilderRepository schnittbilderRepo = mock(SchnittbilderRepository.class);
        BestellungService service = newService(repo, projektRepo, kategorieRepo, artikelRepo, schnittbilderRepo);

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

        SchnittAchse achse = new SchnittAchse();
        achse.setId(99L);
        achse.setBildUrl("/uploads/achsen/y-y.png");
        achse.setKategorie(kat);

        Schnittbilder sb = new Schnittbilder();
        sb.setId(7L);
        sb.setBildUrlSchnittbild("/uploads/schnittbilder/a.png");
        sb.setSchnittAchse(achse);
        aip.setSchnittbild(sb);
        aip.setAnschnittWinkelLinks(45.0);
        aip.setAnschnittWinkelRechts(90.0);

        when(repo.findByQuelleOrderByProjekt_BauvorhabenAsc(
                BestellQuelle.OFFEN)).thenReturn(List.of(aip));

        List<BestellungResponseDto> dtos = service.findeOffeneBestellungen();
        assertEquals(1, dtos.size());
        BestellungResponseDto dto = dtos.getFirst();
        assertEquals(7L, dto.getSchnittbildId());
        assertEquals("/uploads/schnittbilder/a.png", dto.getSchnittbildBildUrl());
        assertEquals("/uploads/achsen/y-y.png", dto.getSchnittAchseBildUrl());
        assertEquals(45.0, dto.getAnschnittWinkelLinks());
        assertEquals(90.0, dto.getAnschnittWinkelRechts());
        assertEquals("Test", dto.getKommentar());
    }

    @Test
    void applySchnittDaten_setztSchnittbildUndWinkelUndFuelltFehlendeMit90Grad() {
        ArtikelInProjektRepository repo = mock(ArtikelInProjektRepository.class);
        ProjektRepository projektRepo = mock(ProjektRepository.class);
        KategorieRepository kategorieRepo = mock(KategorieRepository.class);
        ArtikelRepository artikelRepo = mock(ArtikelRepository.class);
        SchnittbilderRepository schnittbilderRepo = mock(SchnittbilderRepository.class);
        BestellungService service = newService(repo, projektRepo, kategorieRepo, artikelRepo, schnittbilderRepo);

        Schnittbilder sb = new Schnittbilder();
        sb.setId(55L);
        sb.setBildUrlSchnittbild("/uploads/schnittbilder/x.png");
        when(schnittbilderRepo.findById(55L)).thenReturn(Optional.of(sb));
        when(repo.save(any(ArtikelInProjekt.class))).thenAnswer(inv -> inv.getArgument(0));

        ManuelleBestellpositionDto req = new ManuelleBestellpositionDto();
        req.setProduktname("Manuelle Pos");
        req.setMenge(new BigDecimal("2"));
        req.setSchnittbildId(55L);
        req.setAnschnittWinkelLinks(45.0);
        // Rechter Winkel absichtlich leer → muss auf 90° default fallen

        service.manuellePosition(req);

        ArgumentCaptor<ArtikelInProjekt> captor = ArgumentCaptor.forClass(ArtikelInProjekt.class);
        verify(repo).save(captor.capture());
        ArtikelInProjekt saved = captor.getValue();
        assertSame(sb, saved.getSchnittbild());
        assertEquals(45.0, saved.getAnschnittWinkelLinks());
        assertEquals(90.0, saved.getAnschnittWinkelRechts());
    }

    @Test
    void applySchnittDaten_ohneSchnittbildLaesstWinkelNull() {
        ArtikelInProjektRepository repo = mock(ArtikelInProjektRepository.class);
        ProjektRepository projektRepo = mock(ProjektRepository.class);
        KategorieRepository kategorieRepo = mock(KategorieRepository.class);
        ArtikelRepository artikelRepo = mock(ArtikelRepository.class);
        SchnittbilderRepository schnittbilderRepo = mock(SchnittbilderRepository.class);
        BestellungService service = newService(repo, projektRepo, kategorieRepo, artikelRepo, schnittbilderRepo);
        when(repo.save(any(ArtikelInProjekt.class))).thenAnswer(inv -> inv.getArgument(0));

        ManuelleBestellpositionDto req = new ManuelleBestellpositionDto();
        req.setProduktname("Normal-Zuschnitt");
        req.setMenge(new BigDecimal("1"));
        // kein Schnittbild → 90° Standard, Winkel NULL

        service.manuellePosition(req);

        ArgumentCaptor<ArtikelInProjekt> captor = ArgumentCaptor.forClass(ArtikelInProjekt.class);
        verify(repo).save(captor.capture());
        ArtikelInProjekt saved = captor.getValue();
        assertNull(saved.getSchnittbild());
        assertNull(saved.getAnschnittWinkelLinks());
        assertNull(saved.getAnschnittWinkelRechts());
    }
}
