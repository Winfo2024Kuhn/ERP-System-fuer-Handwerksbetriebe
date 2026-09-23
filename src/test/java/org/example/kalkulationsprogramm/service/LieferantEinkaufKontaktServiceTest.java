package org.example.kalkulationsprogramm.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.einkauf.LieferantEinkaufKontakt;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKontaktDto;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKontaktDto.Kontakt;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKontaktDto.KontaktZweck;
import org.example.kalkulationsprogramm.repository.LieferantEinkaufKontaktRepository;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufAuditService;
import org.example.kalkulationsprogramm.service.einkauf.LieferantEinkaufKontaktService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LieferantEinkaufKontaktServiceTest {
    @Mock LieferantEinkaufKontaktRepository kontakte;
    @Mock EinkaufAuditService audit;
    LieferantEinkaufKontaktService service;

    @BeforeEach void setup() { service = new LieferantEinkaufKontaktService(kontakte, audit, new com.fasterxml.jackson.databind.ObjectMapper()); }

    @Test void standardAnfrageUndBestellungSindGetrenntUndSnapshotKopiertEmpfaengerwerte() {
        Lieferanten lieferant = lieferant(7L);
        when(kontakte.findLieferantByIdForUpdate(7L)).thenReturn(Optional.of(lieferant));
        LieferantEinkaufKontakt anfrage = kontakt(1L, lieferant, "Anfrage", "Frau", "anfrage@example.com", true, false);
        LieferantEinkaufKontakt bestellung = kontakt(2L, lieferant, "Bestellung", "Herr", "bestellung@example.com", false, true);
        when(kontakte.findByLieferantIdAndAktivTrueOrderById(7L)).thenReturn(List.of(anfrage, bestellung));

        var snap = service.snapshot(7L, null, null, KontaktZweck.ANFRAGE);

        assertEquals("Anfrage", snap.name());
        assertEquals("Frau", snap.anrede());
        assertEquals("00127", snap.eigeneKundennummer());
        assertEquals("anfrage@example.com", snap.email());
    }

    @Test void kontaktEinesAnderenLieferantenKannNichtAlsEmpfaengerGenutztWerden() {
        when(kontakte.findLieferantByIdForUpdate(7L)).thenReturn(Optional.of(lieferant(7L)));
        LieferantEinkaufKontakt fremd = kontakt(4L, lieferant(8L), "Fremd", null, "fremd@example.com", true, true);
        when(kontakte.findByIdAndLieferantId(4L, 7L)).thenReturn(Optional.of(fremd));
        assertThrows(IllegalArgumentException.class, () -> service.snapshot(7L, 4L, null, KontaktZweck.ANFRAGE));
    }

    @Test void neutralerKontaktOhneNamenKannMitEinmaligerAdresseGesnapshottetWerden() {
        Lieferanten lieferant = lieferant(7L);
        when(kontakte.findLieferantByIdForUpdate(7L)).thenReturn(Optional.of(lieferant));
        LieferantEinkaufKontakt kontakt = kontakt(3L, lieferant, null, null, "poststelle@example.com", false, false);
        when(kontakte.findByIdAndLieferantId(3L, 7L)).thenReturn(Optional.of(kontakt));

        var snap = service.snapshot(7L, 3L, "einkauf@example.com", KontaktZweck.BESTELLUNG);

        assertNull(snap.name());
        assertNull(snap.anrede());
        assertEquals("einkauf@example.com", snap.email());
        assertEquals("00127", snap.eigeneKundennummer());
    }

    @Test void gespeicherterKontaktWirdBeiDeaktivierungNichtPhysischGeloescht() {
        Lieferanten lieferant = lieferant(7L);
        when(kontakte.findLieferantByIdForUpdate(7L)).thenReturn(Optional.of(lieferant));
        when(kontakte.findByIdAndLieferantId(1L, 7L)).thenReturn(Optional.of(kontakt(1L, lieferant, "A", null, "a@example.com", true, true)));
        when(kontakte.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service.speichern(7L, new Kontakt(1L, 1L, "A", null, "a@example.com", true, true, false), 99L);
        verify(kontakte).saveAndFlush(argThat(saved -> !saved.isAktiv()));
        verify(kontakte, never()).delete(any());
    }

    private static Lieferanten lieferant(Long id) { Lieferanten l = new Lieferanten(); l.setId(id); l.setLieferantenname("Dummy Stahl"); l.setEigeneKundennummer("00127"); return l; }
    private static LieferantEinkaufKontakt kontakt(Long id, Lieferanten l, String name, String anrede, String email, boolean anfrage, boolean bestellung) {
        LieferantEinkaufKontakt k = new LieferantEinkaufKontakt(); k.setId(id); k.setLieferant(l); k.setName(name); k.setAnrede(anrede); k.setEmail(email); k.setStandardAnfrage(anfrage); k.setStandardBestellung(bestellung); k.setAktiv(true); k.setVersion(1L); return k;
    }
}
