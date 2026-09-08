package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Abwesenheit;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Urlaubsantrag;
import org.example.kalkulationsprogramm.domain.Zeitkonto;
import org.example.kalkulationsprogramm.repository.AbwesenheitRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.UrlaubsantragRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Charakterisierungstest (Langzeitkrankmeldung, Abschnitt 1 / Task 2).
 *
 * Friert {@link UrlaubsantragService#approveAntrag} zahlengenau ein - Task 4
 * stellt die Sollstunden-Ermittlung auf {@code TagesSollService.arbeitsSoll}
 * um (siehe E1 im Plan, "sechster Aufrufer").
 *
 * Dummy-Daten (DSGVO): Max Mustermann, ID 1.
 */
@ExtendWith(MockitoExtension.class)
class TagesSollCharakterisierungUrlaubsantragTest {

    @Mock
    private UrlaubsantragRepository repository;
    @Mock
    private MitarbeiterRepository mitarbeiterRepository;
    @Mock
    private AbwesenheitRepository abwesenheitRepository;
    @Mock
    private FeiertagService feiertagService;
    @Mock
    private ZeitkontoService zeitkontoService;
    @Mock
    private MonatsSaldoService monatsSaldoService;
    @Mock
    private ZeitkontoKorrekturService zeitkontoKorrekturService;
    @Mock
    private TagesSollService tagesSollService;
    @Mock
    private LangzeitkrankmeldungService langzeitkrankmeldungService;

    @InjectMocks
    private UrlaubsantragService urlaubsantragService;

    private static final Long ANTRAG_ID = 100L;

    private Mitarbeiter testMitarbeiter;
    private Zeitkonto testZeitkonto;

    @BeforeEach
    void setUp() {
        testMitarbeiter = new Mitarbeiter();
        testMitarbeiter.setId(1L);
        testMitarbeiter.setVorname("Max");
        testMitarbeiter.setNachname("Mustermann");

        testZeitkonto = new Zeitkonto(testMitarbeiter);
        testZeitkonto.setMontagStunden(new BigDecimal("8.00"));
        testZeitkonto.setDienstagStunden(new BigDecimal("8.00"));
        testZeitkonto.setMittwochStunden(new BigDecimal("8.00"));
        testZeitkonto.setDonnerstagStunden(new BigDecimal("8.00"));
        testZeitkonto.setFreitagStunden(new BigDecimal("8.00"));
        testZeitkonto.setSamstagStunden(new BigDecimal("0.00"));
        testZeitkonto.setSonntagStunden(new BigDecimal("0.00"));

        when(zeitkontoService.getOrCreateZeitkonto(1L)).thenReturn(testZeitkonto);
        when(abwesenheitRepository.existsByMitarbeiterIdAndDatumAndTyp(anyLong(), any(), any())).thenReturn(false);
        when(abwesenheitRepository.save(any(Abwesenheit.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repository.save(any(Urlaubsantrag.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Urlaubsantrag antrag(LocalDate von, LocalDate bis) {
        Urlaubsantrag antrag = new Urlaubsantrag();
        antrag.setId(ANTRAG_ID);
        antrag.setMitarbeiter(testMitarbeiter);
        antrag.setVonDatum(von);
        antrag.setBisDatum(bis);
        antrag.setTyp(Urlaubsantrag.Typ.URLAUB);
        antrag.setStatus(Urlaubsantrag.Status.OFFEN);
        return antrag;
    }

    @Test
    void montagBisFreitag_ohneFeiertag_erzeugtFuenfAbwesenheitenMitVollerSollstunde() {
        // Mo 2026-06-01 bis Fr 2026-06-05, keine Feiertage in dieser Woche.
        LocalDate von = LocalDate.of(2026, 6, 1);
        LocalDate bis = LocalDate.of(2026, 6, 5);
        when(repository.findById(ANTRAG_ID)).thenReturn(Optional.of(antrag(von, bis)));
        when(feiertagService.istFeiertag(any(LocalDate.class))).thenReturn(false);
        // Pro Fixture-Tag der konkrete Wert, nicht pauschal any() -> sonst blind.
        when(tagesSollService.arbeitsSoll(1L, testZeitkonto, LocalDate.of(2026, 6, 1)))
                .thenReturn(new BigDecimal("8.00"));
        when(tagesSollService.arbeitsSoll(1L, testZeitkonto, LocalDate.of(2026, 6, 2)))
                .thenReturn(new BigDecimal("8.00"));
        when(tagesSollService.arbeitsSoll(1L, testZeitkonto, LocalDate.of(2026, 6, 3)))
                .thenReturn(new BigDecimal("8.00"));
        when(tagesSollService.arbeitsSoll(1L, testZeitkonto, LocalDate.of(2026, 6, 4)))
                .thenReturn(new BigDecimal("8.00"));
        when(tagesSollService.arbeitsSoll(1L, testZeitkonto, LocalDate.of(2026, 6, 5)))
                .thenReturn(new BigDecimal("8.00"));

        urlaubsantragService.approveAntrag(ANTRAG_ID);

        ArgumentCaptor<Abwesenheit> captor = ArgumentCaptor.forClass(Abwesenheit.class);
        verify(abwesenheitRepository, times(5)).save(captor.capture());

        List<Abwesenheit> gebucht = captor.getAllValues();
        assertEquals(5, gebucht.size());
        for (Abwesenheit a : gebucht) {
            assertEquals(0, new BigDecimal("8.00").compareTo(a.getStunden()),
                    "Tag " + a.getDatum() + " hatte " + a.getStunden() + " Stunden");
        }
    }

    @Test
    void montagBisFreitag_mitFeiertagAmMittwoch_erzeugtNurVierAbwesenheiten() {
        // Mo 2026-06-01 bis Fr 2026-06-05, Mittwoch (2026-06-03) ist Feiertag
        // -> approveAntrag ueberspringt Feiertage komplett (Zeile 105-108).
        LocalDate von = LocalDate.of(2026, 6, 1);
        LocalDate bis = LocalDate.of(2026, 6, 5);
        LocalDate feiertag = LocalDate.of(2026, 6, 3);
        when(repository.findById(ANTRAG_ID)).thenReturn(Optional.of(antrag(von, bis)));
        when(feiertagService.istFeiertag(any(LocalDate.class)))
                .thenAnswer(inv -> inv.getArgument(0).equals(feiertag));
        // Pro Fixture-Tag der konkrete Wert, nicht pauschal any() -> sonst blind.
        // Der Feiertag (06-03) selbst bekommt bewusst KEINEN Stub: approveAntrag
        // ueberspringt ihn vor dem TagesSollService-Aufruf (siehe Gegenprobe im Report).
        when(tagesSollService.arbeitsSoll(1L, testZeitkonto, LocalDate.of(2026, 6, 1)))
                .thenReturn(new BigDecimal("8.00"));
        when(tagesSollService.arbeitsSoll(1L, testZeitkonto, LocalDate.of(2026, 6, 2)))
                .thenReturn(new BigDecimal("8.00"));
        when(tagesSollService.arbeitsSoll(1L, testZeitkonto, LocalDate.of(2026, 6, 4)))
                .thenReturn(new BigDecimal("8.00"));
        when(tagesSollService.arbeitsSoll(1L, testZeitkonto, LocalDate.of(2026, 6, 5)))
                .thenReturn(new BigDecimal("8.00"));

        urlaubsantragService.approveAntrag(ANTRAG_ID);

        ArgumentCaptor<Abwesenheit> captor = ArgumentCaptor.forClass(Abwesenheit.class);
        verify(abwesenheitRepository, times(4)).save(captor.capture());

        List<Abwesenheit> gebucht = captor.getAllValues();
        assertEquals(4, gebucht.size());
        assertEquals(false, gebucht.stream().anyMatch(a -> a.getDatum().equals(feiertag)),
                "Der Feiertag selbst darf keine Abwesenheit erzeugen");
    }
}
