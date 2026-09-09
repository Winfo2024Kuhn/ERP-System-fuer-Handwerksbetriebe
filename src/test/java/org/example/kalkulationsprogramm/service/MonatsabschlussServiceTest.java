package org.example.kalkulationsprogramm.service;

import jakarta.persistence.*;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class MonatsabschlussServiceTest {
    @Mock MonatsSaldoRepository monatsSaldoRepository;
    @Mock ZeitbuchungRepository zeitbuchungRepository;
    @Mock AbwesenheitRepository abwesenheitRepository;
    @Mock ZeitkontoKorrekturRepository korrekturRepository;
    @Mock MitarbeiterRepository mitarbeiterRepository;
    @Mock ZeitkontoService zeitkontoService;
    @Mock TagesSollService tagesSollService;
    @Mock EntityManager entityManager;
    @Mock MonatsabschlussAuditRepository auditRepository;
    @Mock MonatsabschlussBerechtigungService berechtigungService;
    @InjectMocks MonatsSaldoService service;
    Mitarbeiter m;
    YearMonth ym = YearMonth.now().minusMonths(1);
    @BeforeEach void setup() {
        m = new Mitarbeiter(); m.setId(1L); m.setVorname("Max"); m.setNachname("Mustermann");
        lenient().when(entityManager.find(Mitarbeiter.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(m);
    }
    MonatsSaldo fixed(int year, int month) {
        var s = new MonatsSaldo(); s.setMitarbeiter(m); s.setJahr(year); s.setMonat(month);
        s.setIstStunden(new BigDecimal("123.45")); s.setFestgeschrieben(true); s.setGueltig(false);
        when(monatsSaldoRepository.findGesperrt(1L, year, month)).thenReturn(Optional.of(s));
        return s;
    }
    void calculation() {
        when(zeitkontoService.berechneSollstundenFuerMonat(1L, ym.getYear(), ym.getMonthValue())).thenReturn(BigDecimal.TEN);
        when(tagesSollService.feiertagsGutschriftSumme(eq(1L), any(), any())).thenReturn(BigDecimal.ZERO);
        when(monatsSaldoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }
    @Test void festgeschriebenGewinntVorLiveWeicheUndGueltig() {
        for (var month : List.of(ym, YearMonth.now(), YearMonth.now().plusMonths(1))) {
            var s = fixed(month.getYear(), month.getMonthValue());
            assertThat(service.getOrBerechne(1L, month.getYear(), month.getMonthValue())).isSameAs(s);
        }
        verifyNoInteractions(zeitkontoService, zeitbuchungRepository, auditRepository);
    }
    @Test void direkterCacheWriteKannFestgeschriebenNichtUeberschreiben() {
        var s = fixed(ym.getYear(), ym.getMonthValue());
        assertThat(service.saveMonatsSaldoCache(1L, ym.getYear(), ym.getMonthValue(), new MonatsSaldo())).isSameAs(s);
        assertThat(s.getIstStunden()).isEqualByComparingTo("123.45");
        verify(monatsSaldoRepository, never()).save(any());
        var order = inOrder(entityManager, monatsSaldoRepository);
        order.verify(entityManager).find(Mitarbeiter.class, 1L, LockModeType.PESSIMISTIC_WRITE);
        order.verify(monatsSaldoRepository).findGesperrt(1L, ym.getYear(), ym.getMonthValue());
        order.verify(entityManager).refresh(s, LockModeType.PESSIMISTIC_WRITE);
    }
    @Test void abschlussOhneRechtSchreibtNichts() {
        when(berechtigungService.verlangeAkteur(null)).thenThrow(new AccessDeniedException("Kein Recht"));
        assertThatThrownBy(() -> service.abschliessen(1L, ym.getYear(), ym.getMonthValue(), null)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(entityManager, monatsSaldoRepository, auditRepository);
    }
    @Test void aktuellerUndZukuenftigerMonatSindKonflikt() {
        when(berechtigungService.verlangeAkteur(null)).thenReturn(m);
        for (var month : List.of(YearMonth.now(), YearMonth.now().plusMonths(1))) {
            assertThatThrownBy(() -> service.abschliessen(1L, month.getYear(), month.getMonthValue(), null))
                .isInstanceOf(ResponseStatusException.class).satisfies(e -> assertThat(((ResponseStatusException)e).getStatusCode().value()).isEqualTo(409));
        }
        verifyNoInteractions(entityManager, auditRepository);
    }
    @Test void normalerCacheErzeugtKeinenAbschluss() {
        calculation();
        var s = service.getOrBerechne(1L, ym.getYear(), ym.getMonthValue());
        assertThat(s.getFestgeschrieben()).isFalse();
        verifyNoInteractions(auditRepository);
    }
    @Test void abschliessenUndOeffnenProtokolliertBeideAktionenUndRechnetNeu() {
        calculation();
        when(berechtigungService.verlangeAkteur(null)).thenReturn(m);
        var current = new java.util.concurrent.atomic.AtomicReference<MonatsSaldo>();
        when(monatsSaldoRepository.findGesperrt(1L, ym.getYear(), ym.getMonthValue())).thenAnswer(i -> Optional.ofNullable(current.get()));
        when(monatsSaldoRepository.save(any())).thenAnswer(i -> { current.set(i.getArgument(0)); return current.get(); });
        var audits = new ArrayList<MonatsabschlussAudit>();
        when(auditRepository.save(any())).thenAnswer(i -> { audits.add(i.getArgument(0)); return i.getArgument(0); });
        when(auditRepository.findByMitarbeiterIdAndJahrAndMonatOrderByZeitpunktAscIdAsc(1L, ym.getYear(), ym.getMonthValue())).thenReturn(audits);
        var closed = service.abschliessen(1L, ym.getYear(), ym.getMonthValue(), null);
        assertThat(closed.festgeschrieben()).isTrue();
        assertThat(closed.audit()).hasSize(1);
        when(zeitkontoService.berechneSollstundenFuerMonat(1L, ym.getYear(), ym.getMonthValue())).thenReturn(new BigDecimal("20"));
        var opened = service.oeffnen(1L, ym.getYear(), ym.getMonthValue(), null);
        assertThat(opened.festgeschrieben()).isFalse();
        assertThat(opened.festgeschriebenAm()).isNull();
        assertThat(opened.sollStunden()).isEqualByComparingTo("20");
        assertThat(opened.audit()).extracting(a -> a.aktion()).containsExactly("ABSCHLIESSEN", "OEFFNEN");
        assertThat(audits).allSatisfy(a -> assertThat(a.getAkteur()).isSameAs(m));
    }
    @Test void ungueltigeIdsUndMonateWerdenAbgewiesen() {
        for (long id : new long[]{-1, 0}) assertThatThrownBy(() -> service.status(id, 2025, 1)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.status(Long.MAX_VALUE, 2025, 1)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.status(1L, 2025, 13)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(zeitkontoService);
    }
}
