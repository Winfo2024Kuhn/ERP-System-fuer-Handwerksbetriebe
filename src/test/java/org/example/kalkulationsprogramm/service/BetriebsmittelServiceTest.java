package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Betriebsmittel;
import org.example.kalkulationsprogramm.domain.BetriebsmittelPruefung;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.BetriebsmittelPruefungRepository;
import org.example.kalkulationsprogramm.repository.BetriebsmittelRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BetriebsmittelServiceTest {

    @Mock
    private BetriebsmittelRepository betriebsmittelRepository;

    @Mock
    private BetriebsmittelPruefungRepository pruefungRepository;

    @Mock
    private MitarbeiterRepository mitarbeiterRepository;

    @InjectMocks
    private BetriebsmittelService service;

    // --- CRUD ---

    @Test
    void findAll_gibtListeZurueck() {
        Betriebsmittel bm = createBetriebsmittel(1L, "Bohrmaschine");
        when(betriebsmittelRepository.findAll()).thenReturn(List.of(bm));

        List<Betriebsmittel> result = service.findAll();

        assertEquals(1, result.size());
        assertEquals("Bohrmaschine", result.get(0).getBezeichnung());
    }

    @Test
    void findById_gefunden() {
        Betriebsmittel bm = createBetriebsmittel(1L, "Bohrmaschine");
        when(betriebsmittelRepository.findById(1L)).thenReturn(Optional.of(bm));

        Optional<Betriebsmittel> result = service.findById(1L);

        assertTrue(result.isPresent());
        assertEquals("Bohrmaschine", result.get().getBezeichnung());
    }

    @Test
    void findById_nichtGefunden() {
        when(betriebsmittelRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<Betriebsmittel> result = service.findById(999L);

        assertTrue(result.isEmpty());
    }

    @Test
    void findByBarcode_gefunden() {
        Betriebsmittel bm = createBetriebsmittel(1L, "Bohrmaschine");
        bm.setBarcode("BC-001");
        when(betriebsmittelRepository.findByBarcode("BC-001")).thenReturn(Optional.of(bm));

        Optional<Betriebsmittel> result = service.findByBarcode("BC-001");

        assertTrue(result.isPresent());
        assertEquals("BC-001", result.get().getBarcode());
    }

    @Test
    void findByBarcode_nichtGefunden() {
        when(betriebsmittelRepository.findByBarcode("UNKNOWN")).thenReturn(Optional.empty());

        Optional<Betriebsmittel> result = service.findByBarcode("UNKNOWN");

        assertTrue(result.isEmpty());
    }

    @Test
    void save_delegiertAnRepository() {
        Betriebsmittel bm = createBetriebsmittel(null, "Neu");
        Betriebsmittel saved = createBetriebsmittel(1L, "Neu");
        when(betriebsmittelRepository.save(bm)).thenReturn(saved);

        Betriebsmittel result = service.save(bm);

        assertEquals(1L, result.getId());
        verify(betriebsmittelRepository).save(bm);
    }

    @Test
    void delete_delegiertAnRepository() {
        service.delete(1L);

        verify(betriebsmittelRepository).deleteById(1L);
    }

    // --- Prüfprotokoll ---

    @Test
    void findPruefungen_delegiertAnRepository() {
        BetriebsmittelPruefung p = new BetriebsmittelPruefung();
        when(pruefungRepository.findByBetriebsmittelIdOrderByPruefDatumDesc(1L))
                .thenReturn(List.of(p));

        List<BetriebsmittelPruefung> result = service.findPruefungen(1L);

        assertEquals(1, result.size());
    }

    @Test
    void findOffenePruefungen_delegiertAnRepository() {
        when(pruefungRepository.findByVonElektrikerVerifiziertFalse())
                .thenReturn(List.of(new BetriebsmittelPruefung()));

        List<BetriebsmittelPruefung> result = service.findOffenePruefungen();

        assertEquals(1, result.size());
    }

    @Test
    void pruefungErfassen_berechnetNaechstesPruefDatum() {
        Betriebsmittel bm = createBetriebsmittel(1L, "Bohrmaschine");
        bm.setPruefIntervallMonate(6);
        when(betriebsmittelRepository.findById(1L)).thenReturn(Optional.of(bm));

        BetriebsmittelPruefung pruefung = new BetriebsmittelPruefung();
        pruefung.setPruefDatum(LocalDate.of(2024, 6, 15));
        when(pruefungRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(betriebsmittelRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BetriebsmittelPruefung result = service.pruefungErfassen(1L, null, pruefung);

        assertEquals(LocalDate.of(2024, 12, 15), result.getNaechstesPruefDatum());
        assertEquals(bm, result.getBetriebsmittel());
        // Prüfe, dass Betriebsmittel auch aktualisiert wird
        assertEquals(LocalDate.of(2024, 12, 15), bm.getNaechstesPruefDatum());
    }

    @Test
    void pruefungErfassen_mitPruefer() {
        Betriebsmittel bm = createBetriebsmittel(1L, "Bohrmaschine");
        bm.setPruefIntervallMonate(12);
        when(betriebsmittelRepository.findById(1L)).thenReturn(Optional.of(bm));

        Mitarbeiter pruefer = new Mitarbeiter();
        pruefer.setId(5L);
        when(mitarbeiterRepository.findById(5L)).thenReturn(Optional.of(pruefer));

        BetriebsmittelPruefung pruefung = new BetriebsmittelPruefung();
        pruefung.setPruefDatum(LocalDate.of(2024, 3, 1));
        when(pruefungRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(betriebsmittelRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BetriebsmittelPruefung result = service.pruefungErfassen(1L, 5L, pruefung);

        assertEquals(pruefer, result.getPruefer());
        assertEquals(LocalDate.of(2025, 3, 1), result.getNaechstesPruefDatum());
    }

    @Test
    void pruefungErfassen_ohnePruefDatum_setztHeute() {
        Betriebsmittel bm = createBetriebsmittel(1L, "Bohrmaschine");
        bm.setPruefIntervallMonate(12);
        when(betriebsmittelRepository.findById(1L)).thenReturn(Optional.of(bm));

        BetriebsmittelPruefung pruefung = new BetriebsmittelPruefung();
        // pruefDatum ist null
        when(pruefungRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(betriebsmittelRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BetriebsmittelPruefung result = service.pruefungErfassen(1L, null, pruefung);

        assertEquals(LocalDate.now(), result.getPruefDatum());
    }

    @Test
    void pruefungErfassen_betriebsmittelNichtGefunden_wirftException() {
        when(betriebsmittelRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.pruefungErfassen(999L, null, new BetriebsmittelPruefung()));
    }

    @Test
    void pruefungErfassen_prueferNichtGefunden_keinPrueferGesetzt() {
        Betriebsmittel bm = createBetriebsmittel(1L, "Bohrmaschine");
        bm.setPruefIntervallMonate(12);
        when(betriebsmittelRepository.findById(1L)).thenReturn(Optional.of(bm));
        when(mitarbeiterRepository.findById(999L)).thenReturn(Optional.empty());

        BetriebsmittelPruefung pruefung = new BetriebsmittelPruefung();
        pruefung.setPruefDatum(LocalDate.of(2024, 1, 1));
        when(pruefungRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(betriebsmittelRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BetriebsmittelPruefung result = service.pruefungErfassen(1L, 999L, pruefung);

        assertNull(result.getPruefer());
    }

    @Test
    void elektrikerVerifizieren_setztFlagAufTrue() {
        BetriebsmittelPruefung pruefung = new BetriebsmittelPruefung();
        pruefung.setVonElektrikerVerifiziert(false);
        when(pruefungRepository.findById(1L)).thenReturn(Optional.of(pruefung));
        when(pruefungRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BetriebsmittelPruefung result = service.elektrikerVerifizieren(1L);

        assertTrue(result.isVonElektrikerVerifiziert());
    }

    @Test
    void elektrikerVerifizieren_nichtGefunden_wirftException() {
        when(pruefungRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.elektrikerVerifizieren(999L));
    }

    @Test
    void findFaellig_delegiertAnRepositoryMitHeutigemDatum() {
        Betriebsmittel bm = createBetriebsmittel(1L, "Fällig");
        when(betriebsmittelRepository.findFaelligBis(LocalDate.now())).thenReturn(List.of(bm));

        List<Betriebsmittel> result = service.findFaellig();

        assertEquals(1, result.size());
        verify(betriebsmittelRepository).findFaelligBis(LocalDate.now());
    }

    // --- Hilfs-Methoden ---

    private Betriebsmittel createBetriebsmittel(Long id, String bezeichnung) {
        Betriebsmittel bm = new Betriebsmittel();
        bm.setId(id);
        bm.setBezeichnung(bezeichnung);
        bm.setPruefIntervallMonate(12);
        return bm;
    }
}
