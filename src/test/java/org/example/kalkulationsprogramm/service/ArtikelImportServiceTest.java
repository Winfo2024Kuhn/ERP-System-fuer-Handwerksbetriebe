package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.KategorieRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArtikelImportServiceTest {

    @Mock
    private ArtikelRepository artikelRepository;
    @Mock
    private LieferantenRepository lieferantenRepository;
    @Mock
    private KategorieRepository kategorieRepository;

    private ArtikelImportService artikelImportService;
    private ArgumentCaptor<Artikel> artikelCaptor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        artikelImportService = new ArtikelImportService(artikelRepository, lieferantenRepository, kategorieRepository);
        artikelCaptor = ArgumentCaptor.forClass(Artikel.class);
    }

    @Test
    void testImportiereCsv_NeuerArtikel() {
        String csvContent = "externeArtikelnummer;preis;produktname\n123;5,50;Testprodukt";
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", csvContent.getBytes());
        Map<String, String> mapping = new HashMap<>();
        mapping.put("externeArtikelnummer", "externeArtikelnummer");
        mapping.put("preis", "preis");
        mapping.put("produktname", "produktname");

        when(lieferantenRepository.findByLieferantenname("TestLieferant")).thenReturn(Optional.of(new Lieferanten()));
        when(artikelRepository.findByExterneArtikelnummer("123")).thenReturn(Optional.empty());

        artikelImportService.importiereCsv(file, "TestLieferant", mapping, null);

        verify(artikelRepository, times(1)).save(any(Artikel.class));
    }

    @Test
    void testImportiereCsv_ExistierenderArtikel() {
        String csvContent = "externeArtikelnummer;preis\n123;5,50";
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", csvContent.getBytes());
        Map<String, String> mapping = new HashMap<>();
        mapping.put("externeArtikelnummer", "externeArtikelnummer");
        mapping.put("preis", "preis");

        Artikel artikel = new Artikel();
        artikel.setArtikelpreis(new ArrayList<>());
        when(lieferantenRepository.findByLieferantenname("TestLieferant")).thenReturn(Optional.of(new Lieferanten()));
        when(artikelRepository.findByExterneArtikelnummer("123")).thenReturn(Optional.of(artikel));

        artikelImportService.importiereCsv(file, "TestLieferant", mapping, null);

        verify(artikelRepository, times(1)).save(artikel);
    }

    @Test
    void testImportiereCsv_PreisNormalisierung() {
        String csvContent = "externeArtikelnummer;preis\n123;550,00"; // Preis in Cent?
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", csvContent.getBytes());
        Map<String, String> mapping = new HashMap<>();
        mapping.put("externeArtikelnummer", "externeArtikelnummer");
        mapping.put("preis", "preis");

        Artikel artikel = new Artikel();
        artikel.setArtikelpreis(new ArrayList<>());
        when(lieferantenRepository.findByLieferantenname("TestLieferant")).thenReturn(Optional.of(new Lieferanten()));
        when(artikelRepository.findByExterneArtikelnummer("123")).thenReturn(Optional.of(artikel));

        artikelImportService.importiereCsv(file, "TestLieferant", mapping, null);

        verify(artikelRepository, times(1)).save(artikelCaptor.capture());
        Artikel savedArtikel = artikelCaptor.getValue();
        assertFalse(savedArtikel.getArtikelpreis().isEmpty(), "Artikelpreisliste sollte nicht leer sein");
        LieferantenArtikelPreise p = savedArtikel.getArtikelpreis().getFirst();
        assertEquals(0, new BigDecimal("5.50").compareTo(p.getPreis()));
    }
}
