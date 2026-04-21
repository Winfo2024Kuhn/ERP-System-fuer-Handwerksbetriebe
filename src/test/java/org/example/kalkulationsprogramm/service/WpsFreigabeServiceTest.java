package org.example.kalkulationsprogramm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.En1090Rolle;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Wps;
import org.example.kalkulationsprogramm.domain.WpsFreigabe;
import org.example.kalkulationsprogramm.repository.WpsFreigabeRepository;
import org.example.kalkulationsprogramm.repository.WpsRepository;
import org.example.kalkulationsprogramm.service.WpsFreigabeService.FreigabeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WpsFreigabeServiceTest {

    @Mock
    private WpsRepository wpsRepository;

    @Mock
    private WpsFreigabeRepository freigabeRepository;

    @InjectMocks
    private WpsFreigabeService service;

    // --- Rollencheck ---

    @Test
    void kannAlsSapSignieren_mitSapRolle_true() {
        Mitarbeiter m = mitarbeiter(1L, "Max", "Mustermann", "Schweißaufsicht (SAP)");
        assertTrue(service.kannAlsSapSignieren(m));
    }

    @Test
    void kannAlsSapSignieren_mitStellvertreterSap_true() {
        Mitarbeiter m = mitarbeiter(1L, "Max", "Mustermann", "Stellvertreter SAP");
        assertTrue(service.kannAlsSapSignieren(m));
    }

    @Test
    void kannAlsSapSignieren_ohneRolle_false() {
        Mitarbeiter m = mitarbeiter(1L, "Max", "Mustermann");
        assertFalse(service.kannAlsSapSignieren(m));
    }

    @Test
    void kannAlsSapSignieren_nurSchweisserRolle_false() {
        Mitarbeiter m = mitarbeiter(1L, "Max", "Mustermann", "Schweißer");
        assertFalse(service.kannAlsSapSignieren(m));
    }

    @Test
    void kannAlsSapSignieren_null_false() {
        assertFalse(service.kannAlsSapSignieren(null));
    }

    // --- freigeben ---

    @Test
    void freigeben_alsSap_legtFreigabeAnUndSpeichertSnapshotName() {
        Mitarbeiter sap = mitarbeiter(10L, "Anna", "Aufsicht", "Schweißaufsicht (SAP)");
        Wps wps = new Wps();
        wps.setId(7L);
        when(wpsRepository.findById(7L)).thenReturn(Optional.of(wps));
        when(freigabeRepository.save(any(WpsFreigabe.class))).thenAnswer(inv -> inv.getArgument(0));

        WpsFreigabe result = service.freigeben(7L, sap);

        assertNotNull(result);
        assertSame(wps, result.getWps());
        assertSame(sap, result.getMitarbeiter());
        assertEquals("Anna Aufsicht", result.getMitarbeiterName());
        assertNotNull(result.getZeitpunkt());
        verify(freigabeRepository, times(1)).save(any(WpsFreigabe.class));
    }

    @Test
    void freigeben_ohneSapRolle_wirftFreigabeException() {
        Mitarbeiter normal = mitarbeiter(11L, "Bob", "Bauer", "Schweißer");
        assertThrows(FreigabeException.class, () -> service.freigeben(7L, normal));
        verify(freigabeRepository, never()).save(any());
    }

    @Test
    void freigeben_ohneMitarbeiter_wirftFreigabeException() {
        assertThrows(FreigabeException.class, () -> service.freigeben(7L, null));
    }

    @Test
    void freigeben_wpsNichtGefunden_wirftFreigabeException() {
        Mitarbeiter sap = mitarbeiter(10L, "Anna", "Aufsicht", "Schweißaufsicht (SAP)");
        when(wpsRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(FreigabeException.class, () -> service.freigeben(99L, sap));
        verify(freigabeRepository, never()).save(any());
    }

    @Test
    void freigeben_doppelteFreigabeDesselbenMitarbeiters_verhindert() {
        Mitarbeiter sap = mitarbeiter(10L, "Anna", "Aufsicht", "Schweißaufsicht (SAP)");
        Wps wps = new Wps();
        wps.setId(7L);
        WpsFreigabe bestehende = new WpsFreigabe();
        bestehende.setId(1L);
        bestehende.setMitarbeiter(sap);
        wps.getFreigaben().add(bestehende);
        when(wpsRepository.findById(7L)).thenReturn(Optional.of(wps));

        assertThrows(FreigabeException.class, () -> service.freigeben(7L, sap));
        verify(freigabeRepository, never()).save(any());
    }

    // --- zuruecknehmen ---

    @Test
    void zuruecknehmen_eigeneFreigabe_wirdGeloescht() {
        Mitarbeiter sap = mitarbeiter(10L, "Anna", "Aufsicht", "Schweißaufsicht (SAP)");
        WpsFreigabe f = new WpsFreigabe();
        f.setId(42L);
        f.setMitarbeiter(sap);
        when(freigabeRepository.findById(42L)).thenReturn(Optional.of(f));

        service.zuruecknehmen(42L, sap);

        verify(freigabeRepository, times(1)).delete(f);
    }

    @Test
    void zuruecknehmen_fremdeFreigabe_wirftFreigabeException() {
        Mitarbeiter sap1 = mitarbeiter(10L, "Anna", "Aufsicht", "Schweißaufsicht (SAP)");
        Mitarbeiter sap2 = mitarbeiter(20L, "Otto", "Ober", "Schweißaufsicht (SAP)");
        WpsFreigabe f = new WpsFreigabe();
        f.setId(42L);
        f.setMitarbeiter(sap1);
        when(freigabeRepository.findById(42L)).thenReturn(Optional.of(f));

        assertThrows(FreigabeException.class, () -> service.zuruecknehmen(42L, sap2));
        verify(freigabeRepository, never()).delete(any());
    }

    // --- resetAlleFreigaben ---

    @Test
    void resetAlleFreigaben_leertListe() {
        Wps wps = new Wps();
        WpsFreigabe f = new WpsFreigabe();
        wps.getFreigaben().add(f);
        assertEquals(1, wps.getFreigaben().size());

        service.resetAlleFreigaben(wps);

        assertTrue(wps.getFreigaben().isEmpty());
    }

    @Test
    void resetAlleFreigaben_nullSicher() {
        service.resetAlleFreigaben(null);
        Wps wps = new Wps();
        service.resetAlleFreigaben(wps);
        assertTrue(wps.getFreigaben().isEmpty());
    }

    // --- Helpers ---

    private Mitarbeiter mitarbeiter(Long id, String vorname, String nachname, String... rollenKurztexte) {
        Mitarbeiter m = new Mitarbeiter();
        m.setId(id);
        m.setVorname(vorname);
        m.setNachname(nachname);
        Set<En1090Rolle> rollen = new HashSet<>();
        for (String kt : rollenKurztexte) {
            En1090Rolle r = new En1090Rolle();
            r.setKurztext(kt);
            rollen.add(r);
        }
        m.setEn1090Rollen(rollen);
        return m;
    }
}
