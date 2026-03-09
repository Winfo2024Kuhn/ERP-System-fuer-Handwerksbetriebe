package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.SchnittbilderRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class StuecklistePdfServiceTest {

    @Test
    void createsPdfWithWerkstoffMm() throws Exception {
        ProjektRepository projektRepository = Mockito.mock(ProjektRepository.class);
        ArtikelInProjektRepository aipRepo = Mockito.mock(ArtikelInProjektRepository.class);
        SchnittbilderRepository schnittbilderRepository = Mockito.mock(SchnittbilderRepository.class);
        DateiSpeicherService dateiSpeicherService = Mockito.mock(DateiSpeicherService.class);

        Projekt p = new Projekt();
        p.setId(10L);
        p.setBauvorhaben("BV X");
        p.setAuftragsnummer("A-123");
        Kunde kunde = new Kunde();
        kunde.setName("Muster GmbH");
        kunde.setKundennummer("K-TEST");
        p.setKundenId(kunde);
        Mockito.when(projektRepository.findById(10L)).thenReturn(Optional.of(p));

        Kategorie rootWerk = new Kategorie(); rootWerk.setId(1); rootWerk.setBeschreibung("Werkstoffe");
        Kategorie subWerk = new Kategorie(); subWerk.setId(11); subWerk.setBeschreibung("Profile"); subWerk.setParentKategorie(rootWerk);

        Werkstoff w = new Werkstoff(); w.setName("S235");

        ArtikelWerkstoffe art = new ArtikelWerkstoffe();
        art.setId(5L);
        art.setProduktname("U-Profil");
        art.setKategorie(subWerk);
        art.setWerkstoff(w);
        art.setVerrechnungseinheit(Verrechnungseinheit.LAUFENDE_METER);

        ArtikelInProjekt aip = new ArtikelInProjekt();
        aip.setId(99L);
        aip.setProjekt(p);
        aip.setArtikel(art);
        aip.setStueckzahl(5);
        aip.setMeter(new BigDecimal("12.5"));
        aip.setKommentar("Zuschnitt");

        Mockito.when(aipRepo.findByProjekt_Id(10L)).thenReturn(List.of(aip));

        StuecklistePdfService service = new StuecklistePdfService(projektRepository, aipRepo, schnittbilderRepository, dateiSpeicherService);
        byte[] pdf = service.generateForProjekt(10L);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
        String content = new String(pdf, StandardCharsets.ISO_8859_1);
        // Simple markers likely present in raw text
        assertTrue(content.contains("Bauvorhaben:"));
        assertTrue(content.toLowerCase().contains("stueckliste") || content.contains("Stückliste"));
    }
}
