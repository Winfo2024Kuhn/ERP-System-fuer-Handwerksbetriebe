package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Bundesland;
import org.example.kalkulationsprogramm.domain.Feiertag;
import org.example.kalkulationsprogramm.dto.Feiertag.FeiertageVorschauDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Vorschlag Bundesland + Feiertage in den Firmendaten (Issue #222). */
@ExtendWith(MockitoExtension.class)
class FeiertageVorschauServiceTest {

    @Mock
    private FeiertagService feiertagService;

    @InjectMocks
    private FeiertageVorschauService service;

    @BeforeEach
    void setUp() {
        lenient().when(feiertagService.vorschau(anyInt(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> GesetzlicheFeiertage.fuer(inv.getArgument(0), inv.getArgument(1)));
    }

    @Test
    void plz_ohneBundesland_schlaegtVorUndZeigtDessenFeiertage() {
        FeiertageVorschauDto dto = service.vorschau("65183", null, 2026);

        assertEquals("HE", dto.vorgeschlagenesBundesland());
        assertEquals("HE", dto.bundesland());
        assertEquals("Hessen", dto.bundeslandName());
        assertEquals(2026, dto.jahr());
        assertEquals(10, dto.feiertage().size());
        assertEquals(new FeiertageVorschauDto.Eintrag(LocalDate.of(2026, 1, 1), "Neujahr"), dto.feiertage().getFirst());
        assertEquals(List.of(FeiertageVorschauService.HINWEIS_PLZ), dto.hinweise());
    }

    @Test
    void gewaehltesBundesland_gehtVorDerPlz() {
        FeiertageVorschauDto dto = service.vorschau("65183", "by", 2026);

        assertEquals("HE", dto.vorgeschlagenesBundesland());
        assertEquals("BY", dto.bundesland());
        assertTrue(dto.hinweise().contains(FeiertageVorschauService.HINWEIS_MARIAE_HIMMELFAHRT));
        verify(feiertagService).vorschau(2026, Bundesland.BY);
    }

    @Test
    void augsburg_bekommtHinweisAufsFriedensfest() {
        FeiertageVorschauDto dto = service.vorschau("86152", null, 2026);

        assertEquals("BY", dto.bundesland());
        assertEquals(List.of(FeiertageVorschauService.HINWEIS_PLZ, FeiertageVorschauService.HINWEIS_MARIAE_HIMMELFAHRT,
                FeiertageVorschauService.HINWEIS_AUGSBURG), dto.hinweise());
    }

    @Test
    void sachsenUndThueringen_bekommenHinweisZuFronleichnam() {
        assertTrue(service.vorschau(null, "SN", 2026).hinweise().contains(FeiertageVorschauService.HINWEIS_FRONLEICHNAM_SN));
        assertTrue(service.vorschau(null, "TH", 2026).hinweise().contains(FeiertageVorschauService.HINWEIS_FRONLEICHNAM_TH));
        assertTrue(service.vorschau(null, "NW", 2026).hinweise().isEmpty());
    }

    @Test
    void ohneGueltigePlz_nimmtDasEingestellteLand() {
        when(feiertagService.aktuellesBundesland()).thenReturn("SH");

        FeiertageVorschauDto dto = service.vorschau("<script>alert(1)</script>", " ", null);

        assertNull(dto.vorgeschlagenesBundesland());
        assertEquals("SH", dto.bundesland());
        assertEquals(LocalDate.now().getYear(), dto.jahr());
        assertTrue(dto.hinweise().isEmpty());
    }

    @Test
    void ungueltigeEingaben_geben400() {
        assertEquals("Bitte ein gültiges Bundesland wählen.", assertThrows(ResponseStatusException.class,
                () -> service.vorschau(null, "'; DROP TABLE feiertag; --", 2026)).getReason());
        assertEquals("Bitte ein gültiges Jahr angeben.", assertThrows(ResponseStatusException.class,
                () -> service.vorschau(null, null, 1999)).getReason());
        assertThrows(ResponseStatusException.class, () -> service.vorschau(null, null, 2101));
        verify(feiertagService, never()).vorschau(anyInt(), eq(Bundesland.BY));
    }

    @Test
    void feiertageAusDemService() {
        when(feiertagService.vorschau(2027, Bundesland.HB))
                .thenReturn(List.of(new Feiertag(LocalDate.of(2027, 10, 31), "Reformationstag", "HB")));

        assertEquals(List.of(new FeiertageVorschauDto.Eintrag(LocalDate.of(2027, 10, 31), "Reformationstag")),
                service.vorschau(null, "HB", 2027).feiertage());
    }
}
