package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.dto.Bestellung.BestellungResponseDto;
import org.junit.jupiter.api.Test;
import org.example.kalkulationsprogramm.repository.SchnittbilderRepository;
import org.example.kalkulationsprogramm.service.pdf.PdfDesign;
import org.example.kalkulationsprogramm.service.pdf.PdfDesignPruefung;
import org.example.kalkulationsprogramm.service.pdf.PdfFarben;
import org.example.kalkulationsprogramm.service.pdf.PdfFirmenDesign;
import org.mockito.Mockito;

import java.awt.Color;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BestellungPdfServiceTest {

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

        SchnittbilderRepository schnittbilderRepository = Mockito.mock(SchnittbilderRepository.class);
        DateiSpeicherService dateiSpeicherService = Mockito.mock(DateiSpeicherService.class);
        BestellungPdfService service = new BestellungPdfService(bestellungService, schnittbilderRepository, dateiSpeicherService, design(null, null));
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

        SchnittbilderRepository schnittbilderRepository = Mockito.mock(SchnittbilderRepository.class);
        DateiSpeicherService dateiSpeicherService = Mockito.mock(DateiSpeicherService.class);
        BestellungPdfService service = new BestellungPdfService(bestellungService, schnittbilderRepository, dateiSpeicherService, design(null, null));
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

        SchnittbilderRepository schnittbilderRepository = Mockito.mock(SchnittbilderRepository.class);
        DateiSpeicherService dateiSpeicherService = Mockito.mock(DateiSpeicherService.class);
        BestellungPdfService service = new BestellungPdfService(bestellungService, schnittbilderRepository, dateiSpeicherService, design(null, null));
        Path pdf = service.generatePdfForProjekt(7L);
        assertTrue(Files.size(pdf) > 0);
        Files.deleteIfExists(pdf);
    }

    private static final Color BLAU = new Color(0x1d, 0x4e, 0xd8);

    private static PdfFirmenDesign design(String farbe, org.openpdf.text.Image logo) {
        PdfFirmenDesign design = Mockito.mock(PdfFirmenDesign.class);
        Mockito.when(design.laden()).thenReturn(PdfDesign.aus(farbe, logo, "Musterbetrieb GmbH"));
        return design;
    }

    private static byte[] bestellungFuerLieferant(PdfFirmenDesign design) throws Exception {
        BestellungService bestellungService = Mockito.mock(BestellungService.class);
        BestellungResponseDto dto = new BestellungResponseDto();
        dto.setLieferantId(1L);
        dto.setProjektName("Projekt A");
        dto.setProduktname("Produkt");
        dto.setMenge(java.math.BigDecimal.ONE);
        dto.setEinheit("Stk");
        BestellungResponseDto zweite = new BestellungResponseDto();
        zweite.setLieferantId(1L);
        zweite.setProjektName("Projekt A");
        zweite.setProduktname("Produkt 2");
        Mockito.when(bestellungService.findeOffeneBestellungen()).thenReturn(List.of(dto, zweite));
        BestellungPdfService service = new BestellungPdfService(bestellungService,
                Mockito.mock(SchnittbilderRepository.class), Mockito.mock(DateiSpeicherService.class), design);
        Path pdf = service.generatePdfForLieferant(1L);
        try {
            return Files.readAllBytes(pdf);
        } finally {
            Files.deleteIfExists(pdf);
        }
    }

    @Test
    void mitFirmenfarbeErscheinenTitelUndTabellenkopfInDieserFarbeMitLogo() throws Exception {
        byte[] pdf = bestellungFuerLieferant(design("#1d4ed8", PdfDesignPruefung.testLogo()));

        assertTrue(PdfDesignPruefung.enthaeltFarbe(pdf, BLAU));
        assertFalse(PdfDesignPruefung.enthaeltFarbe(pdf, new Color(204, 0, 0)), "altes festes Rot");
        assertFalse(PdfDesignPruefung.enthaeltFarbe(pdf, PdfFarben.STANDARD_AKZENT));
        assertTrue(PdfDesignPruefung.enthaeltFarbe(pdf, new Color(245, 245, 245)), "graue Wechselzeile bleibt");
        assertEquals(1, PdfDesignPruefung.anzahlBilder(pdf));
    }

    @Test
    void ohneFirmenfarbeWirdDasBisherigeRotGenutzt() throws Exception {
        byte[] pdf = bestellungFuerLieferant(design(null, null));

        assertTrue(PdfDesignPruefung.enthaeltFarbe(pdf, PdfFarben.STANDARD_AKZENT));
        assertEquals(0, PdfDesignPruefung.anzahlBilder(pdf));
    }
}
