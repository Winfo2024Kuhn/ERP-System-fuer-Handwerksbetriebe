package org.example.kalkulationsprogramm.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.example.kalkulationsprogramm.dto.Bestellung.BestellungResponseDto;
import org.junit.jupiter.api.Test;
import org.example.kalkulationsprogramm.repository.SchnittbilderRepository;
import org.mockito.Mockito;

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
        FirmeninformationService firmeninformationService = Mockito.mock(FirmeninformationService.class);
        Mockito.when(firmeninformationService.loadLogoImage()).thenReturn(null);
        BestellungPdfService service = new BestellungPdfService(bestellungService, schnittbilderRepository, dateiSpeicherService, firmeninformationService);
        Path pdf = service.generatePdfForLieferant(1L);
        assertTrue(Files.size(pdf) > 0);
        String content = pdfText(pdf);
        assertTrue(content.contains("Bauvorhaben:"));
        assertTrue(content.contains("Rechnungen separat pro Auftrag"));
        assertTrue(endsAtPdfEof(pdf), "PDF enthält keine nachträglich angehängten Textbytes");
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
        FirmeninformationService firmeninformationService = Mockito.mock(FirmeninformationService.class);
        Mockito.when(firmeninformationService.loadLogoImage()).thenReturn(null);
        BestellungPdfService service = new BestellungPdfService(bestellungService, schnittbilderRepository, dateiSpeicherService, firmeninformationService);
        Path pdf = service.generatePdfForLieferant(null);
        assertTrue(Files.size(pdf) > 0);
        assertTrue(endsAtPdfEof(pdf), "PDF enthält keine nachträglich angehängten Textbytes");
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
        FirmeninformationService firmeninformationService = Mockito.mock(FirmeninformationService.class);
        Mockito.when(firmeninformationService.loadLogoImage()).thenReturn(null);
        BestellungPdfService service = new BestellungPdfService(bestellungService, schnittbilderRepository, dateiSpeicherService, firmeninformationService);
        Path pdf = service.generatePdfForProjekt(7L);
        assertTrue(Files.size(pdf) > 0);
        assertTrue(endsAtPdfEof(pdf), "PDF enthält keine nachträglich angehängten Textbytes");
        Files.deleteIfExists(pdf);
    }

    private String pdfText(Path pdf) throws Exception {
        try (var document = Loader.loadPDF(Files.readAllBytes(pdf))) {
            return new PDFTextStripper().getText(document);
        }
    }

    private boolean endsAtPdfEof(Path pdf) throws Exception {
        String bytes = Files.readString(pdf, StandardCharsets.ISO_8859_1).stripTrailing();
        return bytes.endsWith("%%EOF");
    }
}
