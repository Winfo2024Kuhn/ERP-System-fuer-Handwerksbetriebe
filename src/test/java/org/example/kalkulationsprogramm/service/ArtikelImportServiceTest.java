package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.Werkstoff;
import org.example.kalkulationsprogramm.dto.ImportAnalysisResult;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.KategorieRepository;
import org.example.kalkulationsprogramm.repository.WerkstoffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    @Mock
    private WerkstoffRepository werkstoffRepository;

    private ArtikelImportService artikelImportService;
    private ArgumentCaptor<Artikel> artikelCaptor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        artikelImportService = new ArtikelImportService(artikelRepository, lieferantenRepository, kategorieRepository,
                werkstoffRepository);
        artikelCaptor = ArgumentCaptor.forClass(Artikel.class);
    }

    private Lieferanten lieferantMitId(long id) {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(id);
        return lieferant;
    }

    @Test
    void testImportiereCsv_NeuerArtikel() {
        String csvContent = "externeArtikelnummer;preis;produktname\n123;5,50;Testprodukt";
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", csvContent.getBytes());
        Map<String, String> mapping = new HashMap<>();
        mapping.put("externeArtikelnummer", "externeArtikelnummer");
        mapping.put("preis", "preis");
        mapping.put("produktname", "produktname");

        Lieferanten lieferant = lieferantMitId(1L);
        when(lieferantenRepository.findByLieferantenname("TestLieferant")).thenReturn(Optional.of(lieferant));
        when(artikelRepository.findByExterneArtikelnummerAndLieferantId("123", 1L)).thenReturn(Optional.empty());

        artikelImportService.importiereCsv(file, "TestLieferant", mapping, null, false);

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
        Lieferanten lieferant = lieferantMitId(1L);
        when(lieferantenRepository.findByLieferantenname("TestLieferant")).thenReturn(Optional.of(lieferant));
        when(artikelRepository.findByExterneArtikelnummerAndLieferantId("123", 1L)).thenReturn(Optional.of(artikel));

        artikelImportService.importiereCsv(file, "TestLieferant", mapping, null, false);

        verify(artikelRepository, times(1)).save(artikel);
    }

    @Test
    void testImportiereCsv_PreiskorrekturAusdruecklichAngefordert_NormalisiertWieBisher() {
        // Vorher (bis Task 5) lief diese Normalisierung fuer JEDEN Import automatisch.
        // Jetzt nur noch, wenn preiskorrekturAnwenden=true explizit angefordert wird.
        String csvContent = "externeArtikelnummer;preis\n123;550,00"; // Preis in Cent?
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", csvContent.getBytes());
        Map<String, String> mapping = new HashMap<>();
        mapping.put("externeArtikelnummer", "externeArtikelnummer");
        mapping.put("preis", "preis");

        Artikel artikel = new Artikel();
        artikel.setArtikelpreis(new ArrayList<>());
        Lieferanten lieferant = lieferantMitId(1L);
        when(lieferantenRepository.findByLieferantenname("TestLieferant")).thenReturn(Optional.of(lieferant));
        when(artikelRepository.findByExterneArtikelnummerAndLieferantId("123", 1L)).thenReturn(Optional.of(artikel));

        artikelImportService.importiereCsv(file, "TestLieferant", mapping, null, true);

        verify(artikelRepository, times(1)).save(artikelCaptor.capture());
        Artikel savedArtikel = artikelCaptor.getValue();
        assertFalse(savedArtikel.getArtikelpreis().isEmpty(), "Artikelpreisliste sollte nicht leer sein");
        LieferantenArtikelPreise p = savedArtikel.getArtikelpreis().getFirst();
        assertEquals(0, new BigDecimal("5.50").compareTo(p.getPreis()));
    }

    @Test
    void testImportiereCsv_StueckpreisOhneKorrekturBleibtUnveraendert_18_50() {
        String csvContent = "externeArtikelnummer;preis\n456;18,50";
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", csvContent.getBytes());
        Map<String, String> mapping = new HashMap<>();
        mapping.put("externeArtikelnummer", "externeArtikelnummer");
        mapping.put("preis", "preis");

        Lieferanten lieferant = lieferantMitId(1L);
        when(lieferantenRepository.findByLieferantenname("TestLieferant")).thenReturn(Optional.of(lieferant));
        when(artikelRepository.findByExterneArtikelnummerAndLieferantId("456", 1L)).thenReturn(Optional.empty());

        artikelImportService.importiereCsv(file, "TestLieferant", mapping, null, false);

        verify(artikelRepository).save(artikelCaptor.capture());
        LieferantenArtikelPreise p = artikelCaptor.getValue().getArtikelpreis().getFirst();
        assertEquals(0, new BigDecimal("18.50").compareTo(p.getPreis()),
                "Stueckpreis 18,50 EUR darf ohne angeforderte Korrektur nicht verworfen oder veraendert werden");
    }

    @Test
    void testImportiereCsv_StueckpreisOhneKorrekturBleibtUnveraendert_1250() {
        String csvContent = "externeArtikelnummer;preis\n789;1250,00";
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", csvContent.getBytes());
        Map<String, String> mapping = new HashMap<>();
        mapping.put("externeArtikelnummer", "externeArtikelnummer");
        mapping.put("preis", "preis");

        Lieferanten lieferant = lieferantMitId(1L);
        when(lieferantenRepository.findByLieferantenname("TestLieferant")).thenReturn(Optional.of(lieferant));
        when(artikelRepository.findByExterneArtikelnummerAndLieferantId("789", 1L)).thenReturn(Optional.empty());

        artikelImportService.importiereCsv(file, "TestLieferant", mapping, null, false);

        verify(artikelRepository).save(artikelCaptor.capture());
        LieferantenArtikelPreise p = artikelCaptor.getValue().getArtikelpreis().getFirst();
        assertEquals(0, new BigDecimal("1250.00").compareTo(p.getPreis()),
                "Stueckpreis 1250,00 EUR darf ohne angeforderte Korrektur nicht auf 1,25 EUR verfaelscht werden");
    }

    @Test
    void testImportiereCsv_GleicheArtikelnummerBeiZweiLieferantenErzeugtGetrennteArtikel() {
        String csvContentA = "externeArtikelnummer;preis\n999;12,00";
        String csvContentB = "externeArtikelnummer;preis\n999;15,00";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("externeArtikelnummer", "externeArtikelnummer");
        mapping.put("preis", "preis");

        Lieferanten lieferantA = lieferantMitId(1L);
        Lieferanten lieferantB = lieferantMitId(2L);
        when(lieferantenRepository.findByLieferantenname("LieferantA")).thenReturn(Optional.of(lieferantA));
        when(lieferantenRepository.findByLieferantenname("LieferantB")).thenReturn(Optional.of(lieferantB));
        // Beide Lieferanten fuehren dieselbe externe Artikelnummer 999, aber als eigene Artikel.
        when(artikelRepository.findByExterneArtikelnummerAndLieferantId("999", 1L)).thenReturn(Optional.empty());
        when(artikelRepository.findByExterneArtikelnummerAndLieferantId("999", 2L)).thenReturn(Optional.empty());

        MockMultipartFile fileA = new MockMultipartFile("file", "a.csv", "text/csv", csvContentA.getBytes());
        MockMultipartFile fileB = new MockMultipartFile("file", "b.csv", "text/csv", csvContentB.getBytes());

        ArgumentCaptor<Artikel> ersterAufrufCaptor = ArgumentCaptor.forClass(Artikel.class);
        artikelImportService.importiereCsv(fileA, "LieferantA", mapping, null, false);
        verify(artikelRepository, times(1)).save(ersterAufrufCaptor.capture());
        Artikel artikelVonLieferantA = ersterAufrufCaptor.getValue();

        // Regressionsschutz: Die alte, lieferantenuebergreifende Methode liefert absichtlich
        // den bereits angelegten Artikel von Lieferant A zurueck. Faellt importiereCsv
        // (versehentlich) wieder auf findByExterneArtikelnummer() zurueck, wuerde Lieferant B
        // exakt diesen Artikel wiederverwenden statt einen eigenen anzulegen - der
        // assertNotSame weiter unten schlaegt dann fehl. lenient(), weil der reparierte Code
        // diese alte Methode gar nicht mehr aufruft.
        lenient().when(artikelRepository.findByExterneArtikelnummer("999"))
                .thenReturn(Optional.of(artikelVonLieferantA));

        artikelImportService.importiereCsv(fileB, "LieferantB", mapping, null, false);

        verify(artikelRepository, times(2)).save(artikelCaptor.capture());
        List<Artikel> gespeicherte = artikelCaptor.getAllValues();
        assertNotSame(gespeicherte.get(0), gespeicherte.get(1),
                "Jeder Lieferant muss einen eigenen Artikel-Datensatz erhalten, keine Ueberschreibung");
        assertEquals(0, new BigDecimal("12.00").compareTo(gespeicherte.get(0).getArtikelpreis().getFirst().getPreis()));
        assertEquals(0, new BigDecimal("15.00").compareTo(gespeicherte.get(1).getArtikelpreis().getFirst().getPreis()));
    }

    @Test
    void testAnalyzeImport_ZaehltNurArtikelDesGewaehltenLieferanten() {
        // Vorschau und Import muessen dieselbe Aussage treffen: existiert der Artikel
        // fuer DIESEN Lieferanten nicht, zaehlt analyzeImport ihn als neu - genau wie
        // importiereCsv in diesem Fall einen neuen Artikel anlegen wuerde.
        String csvContent = "externeArtikelnummer;preis\n123;18,50";
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", csvContent.getBytes());
        Map<String, String> mapping = new HashMap<>();
        mapping.put("externeArtikelnummer", "externeArtikelnummer");
        mapping.put("preis", "preis");

        Lieferanten lieferant = lieferantMitId(1L);
        when(lieferantenRepository.findByLieferantenname("TestLieferant")).thenReturn(Optional.of(lieferant));
        when(artikelRepository.findByExterneArtikelnummerAndLieferantId("123", 1L)).thenReturn(Optional.empty());

        // Regressionsschutz: Der Artikel existiert bereits, aber nur bei einem ANDEREN
        // Lieferanten. Die alte, lieferantenuebergreifende Methode wuerde ihn faelschlich
        // als "existierend" zaehlen - genau das darf analyzeImport nicht mehr tun. lenient(),
        // weil der reparierte Code diese alte Methode gar nicht mehr aufruft.
        Artikel artikelEinesAnderenLieferanten = new Artikel();
        artikelEinesAnderenLieferanten.setArtikelpreis(new ArrayList<>());
        lenient().when(artikelRepository.findByExterneArtikelnummer("123"))
                .thenReturn(Optional.of(artikelEinesAnderenLieferanten));

        ImportAnalysisResult result = artikelImportService.analyzeImport(file, "TestLieferant", mapping);

        assertEquals(1, result.getNewCount());
        assertEquals(0, result.getExistingCount());
    }

    @Test
    void testAnalyzeImport_UnbekannterLieferantZaehltAllesAlsNeu() {
        // Lieferant wurde noch nie importiert -> es kann noch keine ihm zugeordneten
        // Artikel geben.
        String csvContent = "externeArtikelnummer;preis\n123;18,50";
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", csvContent.getBytes());
        Map<String, String> mapping = new HashMap<>();
        mapping.put("externeArtikelnummer", "externeArtikelnummer");
        mapping.put("preis", "preis");

        when(lieferantenRepository.findByLieferantenname("NeuerLieferant")).thenReturn(Optional.empty());

        ImportAnalysisResult result = artikelImportService.analyzeImport(file, "NeuerLieferant", mapping);

        assertEquals(1, result.getNewCount());
        assertEquals(0, result.getExistingCount());
        verify(artikelRepository, never()).findByExterneArtikelnummerAndLieferantId(anyString(), anyLong());
    }

    @Test
    void testImportiereCsv_WerkstoffWirdZugeordnetWennVorhanden() {
        String csvContent = "externeArtikelnummer;preis;werkstoff\n123;5,50;Stahl";
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8));

        Map<String, String> mapping = new HashMap<>();
        mapping.put("externeArtikelnummer", "externeArtikelnummer");
        mapping.put("preis", "preis");
        mapping.put("werkstoff", "werkstoff");

        Lieferanten lieferant = lieferantMitId(1L);
        when(lieferantenRepository.findByLieferantenname("TestLieferant")).thenReturn(Optional.of(lieferant));
        when(artikelRepository.findByExterneArtikelnummerAndLieferantId("123", 1L)).thenReturn(Optional.empty());

        Werkstoff werkstoff = new Werkstoff();
        werkstoff.setName("Stahl");
        when(werkstoffRepository.findByNameIgnoreCase("Stahl")).thenReturn(Optional.of(werkstoff));

        artikelImportService.importiereCsv(file, "TestLieferant", mapping, null, false);

        verify(artikelRepository).save(artikelCaptor.capture());
        assertSame(werkstoff, artikelCaptor.getValue().getWerkstoff());
        verify(werkstoffRepository, never()).save(any(Werkstoff.class));
    }

    @Test
    void testImportiereCsv_WerkstoffWirdNeuErstelltWennNichtVorhanden() {
        String csvContent = "externeArtikelnummer;preis;werkstoff\n123;5,50;Edelstahl";
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8));

        Map<String, String> mapping = new HashMap<>();
        mapping.put("externeArtikelnummer", "externeArtikelnummer");
        mapping.put("preis", "preis");
        mapping.put("werkstoff", "werkstoff");

        Lieferanten lieferant = lieferantMitId(1L);
        when(lieferantenRepository.findByLieferantenname("TestLieferant")).thenReturn(Optional.of(lieferant));
        when(artikelRepository.findByExterneArtikelnummerAndLieferantId("123", 1L)).thenReturn(Optional.empty());
        when(werkstoffRepository.findByNameIgnoreCase("Edelstahl")).thenReturn(Optional.empty());
        when(werkstoffRepository.save(any(Werkstoff.class))).thenAnswer(invocation -> invocation.getArgument(0));

        artikelImportService.importiereCsv(file, "TestLieferant", mapping, null, false);

        verify(werkstoffRepository).save(any(Werkstoff.class));
        verify(artikelRepository).save(artikelCaptor.capture());
        assertNotNull(artikelCaptor.getValue().getWerkstoff());
        assertEquals("Edelstahl", artikelCaptor.getValue().getWerkstoff().getName());
    }
}
