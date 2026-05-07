package org.example.kalkulationsprogramm.service;

import java.util.Optional;

import org.example.kalkulationsprogramm.domain.IdsProtokoll;
import org.example.kalkulationsprogramm.domain.LieferantIdsKonfig;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.LieferantIdsKonfigRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.service.LieferantIdsKonfigService.IdsKonfigUpdate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests für die Passwort-Logik des LieferantIdsKonfigService:
 * <ul>
 *   <li>Klartext-Passwort → wird verschlüsselt persistiert</li>
 *   <li>Platzhalter "********" → bestehender Verschlüsselungs-Wert bleibt unverändert</li>
 *   <li>{@code null}/blank → bestehender Wert bleibt unverändert</li>
 * </ul>
 * Damit ist sicher gestellt, dass beim Bearbeiten der Konfig im Frontend ein
 * unverändertes Passwort-Feld nicht den hinterlegten Wert löscht.
 */
class LieferantIdsKonfigServiceTest {

    private static final String TEST_KEY = java.util.Base64.getEncoder().encodeToString(new byte[]{
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
            17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32});

    private LieferantIdsKonfigService createService(LieferantIdsKonfigRepository repo,
                                                    LieferantenRepository lieferantenRepo) {
        return new LieferantIdsKonfigService(repo, lieferantenRepo, new IdsCryptoService(TEST_KEY));
    }

    @Test
    void klartextWirdVerschluesseltGespeichert() {
        LieferantIdsKonfigRepository repo = mock(LieferantIdsKonfigRepository.class);
        LieferantenRepository lieferantenRepo = mock(LieferantenRepository.class);
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(1L);
        when(lieferantenRepo.findById(1L)).thenReturn(Optional.of(lieferant));
        when(repo.findByLieferantId(1L)).thenReturn(Optional.empty());
        when(repo.save(any(LieferantIdsKonfig.class))).thenAnswer(inv -> inv.getArgument(0));

        IdsKonfigUpdate update = new IdsKonfigUpdate();
        update.aktiviert = true;
        update.protokoll = IdsProtokoll.IDS_CONNECT_2_5;
        update.passwortKlartext = "Wuerth2026.";

        LieferantIdsKonfigService service = createService(repo, lieferantenRepo);
        LieferantIdsKonfig saved = service.save(1L, update, null);

        assertNotNull(saved.getPasswortVerschluesselt());
        // Klartext darf nicht direkt in der Spalte landen
        assertEquals(false, saved.getPasswortVerschluesselt().contains("Wuerth2026"));
        // Aber durch decrypt zurückgewinnbar
        assertEquals("Wuerth2026.", service.entschluesselePasswort(saved));
    }

    @Test
    void platzhalterLaesstBestehendesPasswortUnveraendert() {
        LieferantIdsKonfigRepository repo = mock(LieferantIdsKonfigRepository.class);
        LieferantenRepository lieferantenRepo = mock(LieferantenRepository.class);
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(1L);

        // Bestehende Konfig mit verschlüsseltem Passwort
        LieferantIdsKonfig bestand = new LieferantIdsKonfig();
        bestand.setLieferant(lieferant);
        bestand.setPasswortVerschluesselt("VORHANDENES_VERSCHLUESSELTES_PASSWORT");

        when(lieferantenRepo.findById(1L)).thenReturn(Optional.of(lieferant));
        when(repo.findByLieferantId(1L)).thenReturn(Optional.of(bestand));
        when(repo.save(any(LieferantIdsKonfig.class))).thenAnswer(inv -> inv.getArgument(0));

        IdsKonfigUpdate update = new IdsKonfigUpdate();
        update.aktiviert = true;
        update.protokoll = IdsProtokoll.IDS_CONNECT_2_5;
        update.passwortKlartext = "********"; // Frontend signalisiert "unverändert"

        LieferantIdsKonfigService service = createService(repo, lieferantenRepo);
        LieferantIdsKonfig saved = service.save(1L, update, null);

        assertEquals("VORHANDENES_VERSCHLUESSELTES_PASSWORT", saved.getPasswortVerschluesselt());
    }

    @Test
    void nullPasswortLaesstBestehendesPasswortUnveraendert() {
        LieferantIdsKonfigRepository repo = mock(LieferantIdsKonfigRepository.class);
        LieferantenRepository lieferantenRepo = mock(LieferantenRepository.class);
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(1L);

        LieferantIdsKonfig bestand = new LieferantIdsKonfig();
        bestand.setLieferant(lieferant);
        bestand.setPasswortVerschluesselt("BESTAND");

        when(lieferantenRepo.findById(1L)).thenReturn(Optional.of(lieferant));
        when(repo.findByLieferantId(1L)).thenReturn(Optional.of(bestand));
        when(repo.save(any(LieferantIdsKonfig.class))).thenAnswer(inv -> inv.getArgument(0));

        IdsKonfigUpdate update = new IdsKonfigUpdate();
        update.aktiviert = true;
        update.protokoll = IdsProtokoll.IDS_CONNECT_2_5;
        update.passwortKlartext = null;

        LieferantIdsKonfigService service = createService(repo, lieferantenRepo);
        LieferantIdsKonfig saved = service.save(1L, update, null);

        assertEquals("BESTAND", saved.getPasswortVerschluesselt());
    }

    @Test
    void unbekannterLieferantWirftIllegalArgumentException() {
        LieferantIdsKonfigRepository repo = mock(LieferantIdsKonfigRepository.class);
        LieferantenRepository lieferantenRepo = mock(LieferantenRepository.class);
        when(lieferantenRepo.findById(99L)).thenReturn(Optional.empty());

        IdsKonfigUpdate update = new IdsKonfigUpdate();
        update.aktiviert = false;

        LieferantIdsKonfigService service = createService(repo, lieferantenRepo);
        assertThrows(IllegalArgumentException.class,
                () -> service.save(99L, update, null));
    }

    @Test
    void findOrEmptyLiefertLeerenDefaultBeiUnbekanntemLieferanten() {
        LieferantIdsKonfigRepository repo = mock(LieferantIdsKonfigRepository.class);
        LieferantenRepository lieferantenRepo = mock(LieferantenRepository.class);
        when(repo.findByLieferantId(42L)).thenReturn(Optional.empty());

        LieferantIdsKonfigService service = createService(repo, lieferantenRepo);
        LieferantIdsKonfig leer = service.findOrEmpty(42L);

        assertNull(leer.getId());
        assertEquals(IdsProtokoll.IDS_CONNECT_2_5, leer.getProtokoll());
        assertEquals(false, leer.isAktiviert());
        assertNull(leer.getPasswortVerschluesselt());
    }

    @Test
    void platzhalterAlsKonstante() {
        // Wenn sich der Platzhalter mal ändert, müssen Frontend und Tests synchron sein
        assertEquals("********", LieferantIdsKonfigService.passwortPlatzhalter());
        assertEquals(true, LieferantIdsKonfigService.istPasswortPlatzhalter("********"));
        assertEquals(false, LieferantIdsKonfigService.istPasswortPlatzhalter("anders"));
        assertEquals(false, LieferantIdsKonfigService.istPasswortPlatzhalter(null));
    }

    @Test
    void mitarbeiterWirdAlsGeaendertVonUebernommen() {
        LieferantIdsKonfigRepository repo = mock(LieferantIdsKonfigRepository.class);
        LieferantenRepository lieferantenRepo = mock(LieferantenRepository.class);
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(1L);
        Mitarbeiter mitarbeiter = new Mitarbeiter();
        mitarbeiter.setId(7L);
        mitarbeiter.setVorname("Max");
        mitarbeiter.setNachname("Mustermann");

        when(lieferantenRepo.findById(1L)).thenReturn(Optional.of(lieferant));
        when(repo.findByLieferantId(1L)).thenReturn(Optional.empty());
        when(repo.save(any(LieferantIdsKonfig.class))).thenAnswer(inv -> inv.getArgument(0));

        IdsKonfigUpdate update = new IdsKonfigUpdate();
        update.aktiviert = false;

        LieferantIdsKonfigService service = createService(repo, lieferantenRepo);
        LieferantIdsKonfig saved = service.save(1L, update, mitarbeiter);

        assertEquals(7L, saved.getGeaendertVon().getId());
        assertNotNull(saved.getGeaendertAm());
    }
}
