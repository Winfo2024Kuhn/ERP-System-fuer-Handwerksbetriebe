package org.example.kalkulationsprogramm.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.dto.*;
import org.example.kalkulationsprogramm.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class ZeitkontoWechselServiceTest {
    @Mock MitarbeiterRepository mitarbeiterRepository;
    @Mock ZeitkontoVersionRepository versionRepository;
    @Mock ZeitbuchungRepository buchungRepository;
    @Mock AbwesenheitRepository abwesenheitRepository;
    @Mock ZeitkontoService zeitkontoService;
    @Mock MonatsSaldoService saldoService;
    @Mock EntityManager em;
    @Mock TypedQuery<MonatsSaldo> query;
    ZeitkontoWechselService service;
    Mitarbeiter mensch;
    ZeitkontoWechselDto request;

    @BeforeEach void setup() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        service = new ZeitkontoWechselService(mitarbeiterRepository, versionRepository, buchungRepository,
                abwesenheitRepository, zeitkontoService, saldoService, em, validator);
        mensch = new Mitarbeiter(); mensch.setId(1L); mensch.setVersion(2L);
        mensch.setVorname("Max"); mensch.setNachname("Mustermann");
        mensch.setEintrittsdatum(LocalDate.now().withDayOfMonth(1));
        request = new ZeitkontoWechselDto(LocalDate.now(), 2L, null, null, null, null,
                new ZeitkontenmodellDto.Arbeitszeit(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                        BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, null, null));
    }
    private void mitarbeiter() {
        when(mitarbeiterRepository.findById(1L)).thenReturn(Optional.of(mensch));
        when(versionRepository.findImZeitraum(eq(1L), any(), any())).thenReturn(List.of());
    }
    private void monate() {
        when(em.createQuery(anyString(), eq(MonatsSaldo.class))).thenReturn(query);
        when(query.setParameter("id", 1L)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
    }
    private MonatsSaldo saldo(String soll, boolean geschlossen) {
        MonatsSaldo s = new MonatsSaldo(); s.setSollStunden(new BigDecimal(soll));
        s.setIstStunden(new BigDecimal("10")); s.setFestgeschrieben(geschlossen); return s;
    }
    @Test void vorschauSchreibtNichtsUndWarntVorSnapshots() {
        mitarbeiter(); monate();
        when(saldoService.berechneOhneSpeichern(eq(1L), anyInt(), anyInt())).thenReturn(saldo("8", false));
        when(abwesenheitRepository.findByMitarbeiterIdAndDatumBetween(eq(1L), any(), any())).thenReturn(List.of(new Abwesenheit()));
        var result = service.vorschau(1L, request);
        assertFalse(result.gespeichert()); assertEquals(1, result.bestehendeAbwesenheiten());
        assertTrue(result.hinweis().contains("Abwesenheitsstunden bleiben unverändert"));
        assertNull(result.monate().get(0).saldoNachher());
        verifyNoInteractions(zeitkontoService);
        verify(em, never()).flush();
        verify(saldoService, never()).saveMonatsSaldoCache(anyLong(), anyInt(), anyInt(), any());
    }
    @Test void uebernahmeZeigtTatsaechlicheAenderungNachDemSchreiben() {
        mitarbeiter(); monate();
        when(saldoService.berechneOhneSpeichern(eq(1L), anyInt(), anyInt()))
                .thenReturn(saldo("8", false), saldo("5", false));
        var result = service.uebernehmen(1L, request);
        assertTrue(result.gespeichert());
        assertEquals(new BigDecimal("2"), result.monate().get(0).saldoVorher());
        assertEquals(new BigDecimal("5"), result.monate().get(0).saldoNachher());
        assertTrue(result.monate().get(0).geaendert());
        var reihenfolge = inOrder(zeitkontoService, em, saldoService);
        reihenfolge.verify(saldoService).berechneOhneSpeichern(eq(1L), anyInt(), anyInt());
        reihenfolge.verify(zeitkontoService).zuweisen(1L, request);
        reihenfolge.verify(em).flush();
        reihenfolge.verify(saldoService).berechneOhneSpeichern(eq(1L), anyInt(), anyInt());
        verify(saldoService, never()).getOrBerechne(anyLong(), anyInt(), anyInt());
    }
    @Test void abgeschlossenerMonatBleibtImErgebnisUnveraendert() {
        mitarbeiter(); monate();
        when(saldoService.berechneOhneSpeichern(eq(1L), anyInt(), anyInt())).thenReturn(saldo("8", true));
        var result = service.uebernehmen(1L, request);
        assertTrue(result.monate().get(0).abgeschlossen());
        assertFalse(result.monate().get(0).geaendert());
        assertEquals(result.monate().get(0).saldoVorher(), result.monate().get(0).saldoNachher());
        verify(saldoService, never()).saveMonatsSaldoCache(anyLong(), anyInt(), anyInt(), any());
    }
    @Test void veralteteMitarbeiterdatenWerdenVorMutationAbgelehnt() {
        mitarbeiter(); mensch.setVersion(3L);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.uebernehmen(1L, request)).getStatusCode().value());
        verifyNoInteractions(zeitkontoService, saldoService);
    }
    @Test void ausgeschaltetesKontoZeigtHistorieOhneNeueAnlage() {
        mitarbeiter(); mensch.setFuehrtZeitkonto(false);
        var result = service.status(1L);
        assertFalse(result.fuehrtZeitkonto()); assertFalse(result.eingerichtet());
        assertTrue(result.hinweis().contains("bisherigen Stunden bleiben erhalten"));
        verifyNoInteractions(zeitkontoService);
    }
    @Test void doppelteMitarbeiterauswahlWirdAtomarAbgelehnt() {
        var a = new ZeitkontoWechselErgebnisDto.Auswahl(1L, request);
        assertThrows(ResponseStatusException.class, () -> service.mehrere(new ZeitkontoWechselErgebnisDto.Mehrere(List.of(a, a))));
        verifyNoInteractions(zeitkontoService, saldoService);
    }
}
