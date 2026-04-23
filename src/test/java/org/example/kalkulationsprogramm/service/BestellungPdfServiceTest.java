package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Preisanfrage;
import org.example.kalkulationsprogramm.domain.PreisanfrageLieferant;
import org.example.kalkulationsprogramm.domain.PreisanfragePosition;
import org.example.kalkulationsprogramm.dto.Bestellung.BestellungResponseDto;
import org.example.kalkulationsprogramm.repository.PreisanfrageLieferantRepository;
import org.example.kalkulationsprogramm.repository.PreisanfragePositionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BestellungPdfServiceTest {

    private BestellungPdfService newService(BestellungService bestellungService) {
        DateiSpeicherService dateiSpeicherService = Mockito.mock(DateiSpeicherService.class);
        ZeugnisService zeugnisService = Mockito.mock(ZeugnisService.class);
        FirmeninformationService firmeninformationService = Mockito.mock(FirmeninformationService.class);
        PreisanfrageLieferantRepository palRepo = Mockito.mock(PreisanfrageLieferantRepository.class);
        PreisanfragePositionRepository posRepo = Mockito.mock(PreisanfragePositionRepository.class);
        return new BestellungPdfService(bestellungService,
                dateiSpeicherService, zeugnisService, firmeninformationService,
                palRepo, posRepo);
    }

    @Test
    void createsPdfFile() throws Exception {
        BestellungService bestellungService = Mockito.mock(BestellungService.class);
        BestellungResponseDto dto = new BestellungResponseDto();
        dto.setLieferantId(1L);
        dto.setProjektName("Projekt A");
        dto.setExterneArtikelnummer("123");
        dto.setProduktname("Produkt");
        dto.setProdukttext("Text");
        dto.setKommentar("Kommentar");
        dto.setMenge(java.math.BigDecimal.ONE);
        dto.setEinheit("Stk");
        Mockito.when(bestellungService.findeOffeneBestellungen()).thenReturn(List.of(dto));

        BestellungPdfService service = newService(bestellungService);
        Path pdf = service.generatePdfForLieferant(1L);
        assertTrue(Files.size(pdf) > 0);
        String content = Files.readString(pdf, StandardCharsets.ISO_8859_1);
        assertTrue(content.contains("Bauvorhaben:"));
        assertTrue(content.contains("Rechnungen separat pro Auftrag"));
        Files.deleteIfExists(pdf);
    }

    @Test
    void createsPdfFileForUnknownLieferant() throws Exception {
        BestellungService bestellungService = Mockito.mock(BestellungService.class);
        BestellungResponseDto dto = new BestellungResponseDto();
        dto.setLieferantId(null);
        dto.setProjektName("Projekt B");
        dto.setExterneArtikelnummer("456");
        dto.setProduktname("ProduktB");
        dto.setProdukttext("TextB");
        dto.setKommentar("KommentarB");
        dto.setMenge(java.math.BigDecimal.ONE);
        dto.setEinheit("Stk");
        Mockito.when(bestellungService.findeOffeneBestellungen()).thenReturn(List.of(dto));

        BestellungPdfService service = newService(bestellungService);
        Path pdf = service.generatePdfForLieferant(null);
        assertTrue(Files.size(pdf) > 0);
        Files.deleteIfExists(pdf);
    }

    @Test
    void createsPdfForProjekt() throws Exception {
        BestellungService bestellungService = Mockito.mock(BestellungService.class);
        BestellungResponseDto dto = new BestellungResponseDto();
        dto.setProjektId(7L);
        dto.setProjektName("Projekt C");
        dto.setRootKategorieId(1);
        dto.setExterneArtikelnummer("789");
        dto.setProduktname("ProdC");
        dto.setProdukttext("TextC");
        dto.setKommentar("KommentarC");
        dto.setMenge(java.math.BigDecimal.ONE);
        dto.setEinheit("Stk");
        Mockito.when(bestellungService.findeOffeneBestellungen()).thenReturn(List.of(dto));

        BestellungPdfService service = newService(bestellungService);
        Path pdf = service.generatePdfForProjekt(7L);
        assertTrue(Files.size(pdf) > 0);
        Files.deleteIfExists(pdf);
    }

    @Test
    void generatePdfForPreisanfrage_wirftBeiNullId() {
        BestellungPdfService service = newService(Mockito.mock(BestellungService.class));
        assertThrows(IllegalArgumentException.class,
                () -> service.generatePdfForPreisanfrage(null));
    }

    @Test
    void generatePdfForPreisanfrage_wirftWennLieferantNichtGefunden() {
        BestellungService bestellungService = Mockito.mock(BestellungService.class);
        DateiSpeicherService dateiSpeicherService = Mockito.mock(DateiSpeicherService.class);
        ZeugnisService zeugnisService = Mockito.mock(ZeugnisService.class);
        FirmeninformationService firmeninformationService = Mockito.mock(FirmeninformationService.class);
        PreisanfrageLieferantRepository palRepo = Mockito.mock(PreisanfrageLieferantRepository.class);
        PreisanfragePositionRepository posRepo = Mockito.mock(PreisanfragePositionRepository.class);
        Mockito.when(palRepo.findById(999L)).thenReturn(Optional.empty());

        BestellungPdfService service = new BestellungPdfService(bestellungService,
                dateiSpeicherService, zeugnisService,
                firmeninformationService, palRepo, posRepo);

        assertThrows(IllegalArgumentException.class,
                () -> service.generatePdfForPreisanfrage(999L));
    }

    @Test
    void generatePdfForPreisanfrage_enthaeltTokenUndNummer() throws Exception {
        BestellungService bestellungService = Mockito.mock(BestellungService.class);
        DateiSpeicherService dateiSpeicherService = Mockito.mock(DateiSpeicherService.class);
        ZeugnisService zeugnisService = Mockito.mock(ZeugnisService.class);
        FirmeninformationService firmeninformationService = Mockito.mock(FirmeninformationService.class);
        PreisanfrageLieferantRepository palRepo = Mockito.mock(PreisanfrageLieferantRepository.class);
        PreisanfragePositionRepository posRepo = Mockito.mock(PreisanfragePositionRepository.class);

        Preisanfrage pa = new Preisanfrage();
        pa.setId(42L);
        pa.setNummer("PA-2026-007");
        pa.setBauvorhaben("Musterstraße 1");
        pa.setAntwortFrist(LocalDate.of(2026, 5, 15));
        pa.setNotiz("Bitte netto kalkulieren");

        PreisanfrageLieferant pal = new PreisanfrageLieferant();
        pal.setId(99L);
        pal.setPreisanfrage(pa);
        pal.setToken("PA-2026-007-AB3CD");

        PreisanfragePosition pos1 = new PreisanfragePosition();
        pos1.setId(1L);
        pos1.setReihenfolge(0);
        pos1.setExterneArtikelnummer("IPE-200");
        pos1.setProduktname("Stahlprofil IPE 200");
        pos1.setProdukttext("Warmgewalzt, S235JR");
        pos1.setWerkstoffName("S235JR");
        pos1.setMenge(new BigDecimal("12.5"));
        pos1.setEinheit("m");

        PreisanfragePosition pos2 = new PreisanfragePosition();
        pos2.setId(2L);
        pos2.setReihenfolge(1);
        pos2.setProduktname("Winkelstahl 50x50x5");
        pos2.setMenge(new BigDecimal("8"));
        pos2.setEinheit("Stk");

        Mockito.when(palRepo.findById(99L)).thenReturn(Optional.of(pal));
        Mockito.when(posRepo.findByPreisanfrageIdOrderByReihenfolgeAsc(42L))
                .thenReturn(List.of(pos1, pos2));

        BestellungPdfService service = new BestellungPdfService(bestellungService,
                dateiSpeicherService, zeugnisService,
                firmeninformationService, palRepo, posRepo);

        Path pdf = service.generatePdfForPreisanfrage(99L);
        assertNotNull(pdf);
        assertTrue(Files.size(pdf) > 0);

        String content = Files.readString(pdf, StandardCharsets.ISO_8859_1);
        assertTrue(content.contains("PA-2026-007"),
                "Nummer muss im PDF vorkommen, war: " + content.substring(0, Math.min(400, content.length())));
        assertTrue(content.contains("PA-2026-007-AB3CD"),
                "Token muss im PDF vorkommen");

        Files.deleteIfExists(pdf);
    }
}
