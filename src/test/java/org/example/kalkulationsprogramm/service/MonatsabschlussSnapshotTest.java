package org.example.kalkulationsprogramm.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MonatsabschlussSnapshotTest {
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
    final YearMonth monat = YearMonth.now().minusMonths(1);
    final AtomicReference<MonatsSaldo> gespeichert = new AtomicReference<>();
    Mitarbeiter mitarbeiter;

    @BeforeEach void setup() {
        mitarbeiter = new Mitarbeiter();
        mitarbeiter.setId(1L);
        mitarbeiter.setArt(MitarbeiterArt.MENSCH);
        mitarbeiter.setVorname("Max");
        mitarbeiter.setNachname("Mustermann");
        when(entityManager.find(Mitarbeiter.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(mitarbeiter);
        when(monatsSaldoRepository.findGesperrt(1L, monat.getYear(), monat.getMonthValue()))
                .thenAnswer(i -> Optional.ofNullable(gespeichert.get()));
    }

    void berechnung(String summe, List<AbwesenheitRepository.StundenNachTypUndPhase> details) {
        when(berechtigungService.verlangeAkteur(null)).thenReturn(mitarbeiter);
        when(zeitkontoService.berechneSollstundenFuerMonat(1L, monat.getYear(), monat.getMonthValue()))
                .thenReturn(new BigDecimal("160"));
        when(tagesSollService.feiertagsGutschriftSumme(eq(1L), any(), any())).thenReturn(BigDecimal.ZERO);
        doAnswer(i -> {
            gespeichert.set(i.getArgument(0));
            return gespeichert.get();
        }).when(monatsSaldoRepository).save(any());
        when(abwesenheitRepository.sumStundenByMitarbeiterIdAndDatumBetween(1L, monat.atDay(1), monat.atEndOfMonth()))
                .thenReturn(new BigDecimal(summe));
        when(abwesenheitRepository.sumStundenNachTypUndPhase(1L, monat.atDay(1), monat.atEndOfMonth()))
                .thenReturn(details);
    }

    AbwesenheitRepository.StundenNachTypUndPhase detail(AbwesenheitsTyp typ,
            LangzeitkrankmeldungPhaseTyp phase, String stunden) {
        return new AbwesenheitRepository.StundenNachTypUndPhase() {
            public AbwesenheitsTyp getTyp() { return typ; }
            public LangzeitkrankmeldungPhaseTyp getPhaseTyp() { return phase; }
            public BigDecimal getStunden() { return new BigDecimal(stunden); }
        };
    }

    @Test void speichertAlleTypenUndKrankheitsphasenMitExakterGesamtsumme() {
        berechnung("35.75", List.of(
                detail(AbwesenheitsTyp.URLAUB, null, "8.25"),
                detail(AbwesenheitsTyp.KRANKHEIT, null, "2.50"),
                detail(AbwesenheitsTyp.KRANKHEIT, LangzeitkrankmeldungPhaseTyp.LOHNFORTZAHLUNG, "5.00"),
                detail(AbwesenheitsTyp.KRANKHEIT, LangzeitkrankmeldungPhaseTyp.KRANKENGELD, "4.00"),
                detail(AbwesenheitsTyp.KRANKHEIT, LangzeitkrankmeldungPhaseTyp.WIEDEREINGLIEDERUNG, "3.00"),
                detail(AbwesenheitsTyp.FORTBILDUNG, null, "7.00"),
                detail(AbwesenheitsTyp.ZEITAUSGLEICH, null, "6.00")));
        var dto = service.abschliessenOhneVerlauf(1L, monat.getYear(), monat.getMonthValue(), null);
        var saldo = gespeichert.get();
        assertThat(saldo.getUrlaubStunden()).isEqualByComparingTo("8.25");
        assertThat(saldo.getKrankheitStunden()).isEqualByComparingTo("7.50");
        assertThat(saldo.getKrankengeldStunden()).isEqualByComparingTo("4");
        assertThat(saldo.getWiedereingliederungStunden()).isEqualByComparingTo("3");
        assertThat(saldo.getFortbildungStunden()).isEqualByComparingTo("7");
        assertThat(saldo.getZeitausgleichStunden()).isEqualByComparingTo("6");
        assertThat(saldo.getAbwesenheitsStunden()).isEqualByComparingTo("35.75");
        assertThat(dto.festgeschrieben()).isTrue();
        assertThat(dto.audit()).isEmpty();
        verify(auditRepository).save(any());
        verify(auditRepository, never()).findByMitarbeiterIdAndJahrAndMonatOrderByZeitpunktAscIdAsc(anyLong(), anyInt(), anyInt());
        verify(abwesenheitRepository, never()).findByMitarbeiterIdAndDatumBetween(anyLong(), any(), any());
    }

    @Test void neuerAbschlussOhneAbwesenheitSpeichertSechsNullstunden() {
        berechnung("0", List.of());
        service.abschliessen(1L, monat.getYear(), monat.getMonthValue(), null);
        var s = gespeichert.get();
        assertThat(List.of(s.getUrlaubStunden(), s.getKrankheitStunden(), s.getFortbildungStunden(),
                s.getZeitausgleichStunden(), s.getKrankengeldStunden(), s.getWiedereingliederungStunden()))
                .allSatisfy(wert -> assertThat(wert).isEqualByComparingTo("0"));
        verify(auditRepository).findByMitarbeiterIdAndJahrAndMonatOrderByZeitpunktAscIdAsc(1L, monat.getYear(), monat.getMonthValue());
    }

    @Test void abweichendeSummeVerhindertFestschreibenUndAudit() {
        berechnung("8", List.of(detail(AbwesenheitsTyp.URLAUB, null, "7.99")));
        assertThatThrownBy(() -> service.abschliessenOhneVerlauf(1L, monat.getYear(), monat.getMonthValue(), null))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
        assertThat(gespeichert.get().getFestgeschrieben()).isFalse();
        verify(monatsSaldoRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditRepository);
    }

    @Test void alteGeschlosseneNullDetailsWerdenNichtMitLivewertenErgaenzt() {
        var alt = new MonatsSaldo();
        alt.setFestgeschrieben(true);
        alt.setAbwesenheitsStunden(new BigDecimal("8"));
        gespeichert.set(alt);
        assertThat(service.getOrBerechne(1L, monat.getYear(), monat.getMonthValue())).isSameAs(alt);
        assertThat(service.saveMonatsSaldoCache(1L, monat.getYear(), monat.getMonthValue(), new MonatsSaldo())).isSameAs(alt);
        assertThat(alt.getUrlaubStunden()).isNull();
        assertThat(alt.getKrankheitStunden()).isNull();
        assertThat(alt.getFortbildungStunden()).isNull();
        assertThat(alt.getZeitausgleichStunden()).isNull();
        assertThat(alt.getKrankengeldStunden()).isNull();
        assertThat(alt.getWiedereingliederungStunden()).isNull();
        verifyNoInteractions(abwesenheitRepository, zeitkontoService, auditRepository);
    }

    @Test void wiederabschlussErsetztAlleDetailsUndGeschlossenerCacheSchuetztSie() {
        berechnung("8", List.of(detail(AbwesenheitsTyp.URLAUB, null, "8")));
        service.abschliessenOhneVerlauf(1L, monat.getYear(), monat.getMonthValue(), null);
        var saldo = gespeichert.get();
        service.saveMonatsSaldoCache(1L, monat.getYear(), monat.getMonthValue(), new MonatsSaldo());
        assertThat(saldo.getUrlaubStunden()).isEqualByComparingTo("8");
        assertThat(service.oeffnen(1L, monat.getYear(), monat.getMonthValue(), null).festgeschrieben()).isFalse();
        berechnung("3.25", List.of(detail(AbwesenheitsTyp.KRANKHEIT, LangzeitkrankmeldungPhaseTyp.KRANKENGELD, "3.25")));
        service.abschliessenOhneVerlauf(1L, monat.getYear(), monat.getMonthValue(), null);
        assertThat(saldo.getUrlaubStunden()).isEqualByComparingTo("0");
        assertThat(saldo.getKrankengeldStunden()).isEqualByComparingTo("3.25");
        assertThat(saldo.getKrankheitStunden()).isEqualByComparingTo("0");
        assertThat(saldo.getAbwesenheitsStunden()).isEqualByComparingTo("3.25");
    }
}
