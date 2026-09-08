package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.*;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit-Tests für UrlaubsantragService.
 *
 * Schwerpunkt: {@code approveAntrag} bucht Urlaubsstunden über
 * {@code TagesSollService.arbeitsSoll} statt direkt über das Zeitkonto-Soll
 * (E1 im Plan "Langzeitkrankmeldung") — damit folgen Urlaubsstunden während
 * einer laufenden Wiedereingliederung dem Stufenplan, statt Phantom-
 * Überstunden zu erzeugen. {@code pruefeHinweise} delegiert an
 * {@code LangzeitkrankmeldungService.pruefeUrlaubsHinweise}.
 *
 * Dummy-Daten (DSGVO): Max Mustermann, ID 1.
 */
@ExtendWith(MockitoExtension.class)
class UrlaubsantragServiceTest {

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

    private static final Long MITARBEITER_ID = 1L;
    private static final Long ANTRAG_ID = 100L;

    private Mitarbeiter testMitarbeiter;
    private Zeitkonto testZeitkonto;

    @BeforeEach
    void setUp() {
        testMitarbeiter = new Mitarbeiter();
        testMitarbeiter.setId(MITARBEITER_ID);
        testMitarbeiter.setVorname("Max");
        testMitarbeiter.setNachname("Mustermann");

        testZeitkonto = new Zeitkonto(testMitarbeiter);
        testZeitkonto.setMontagStunden(new BigDecimal("8.00"));
        testZeitkonto.setDienstagStunden(new BigDecimal("8.00"));
        testZeitkonto.setMittwochStunden(new BigDecimal("8.00"));
        testZeitkonto.setDonnerstagStunden(new BigDecimal("8.00"));
        testZeitkonto.setFreitagStunden(new BigDecimal("8.00"));
        testZeitkonto.setSamstagStunden(BigDecimal.ZERO);
        testZeitkonto.setSonntagStunden(BigDecimal.ZERO);
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

    /** Stubbt die für jeden approveAntrag-Test nötige Grundverkabelung. */
    private void stubApproveGrunddaten() {
        when(zeitkontoService.getOrCreateZeitkonto(MITARBEITER_ID)).thenReturn(testZeitkonto);
        when(abwesenheitRepository.existsByMitarbeiterIdAndDatumAndTyp(anyLong(), any(), any())).thenReturn(false);
        when(abwesenheitRepository.save(any(Abwesenheit.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repository.save(any(Urlaubsantrag.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void approveAntrag_montagBisFreitag_bucht8StundenJeTagUeberTagesSollService() {
        LocalDate von = LocalDate.of(2026, 6, 1);
        LocalDate bis = LocalDate.of(2026, 6, 5);
        stubApproveGrunddaten();
        when(repository.findById(ANTRAG_ID)).thenReturn(Optional.of(antrag(von, bis)));
        when(feiertagService.istFeiertag(any(LocalDate.class))).thenReturn(false);
        for (LocalDate d = von; !d.isAfter(bis); d = d.plusDays(1)) {
            when(tagesSollService.arbeitsSoll(MITARBEITER_ID, testZeitkonto, d)).thenReturn(new BigDecimal("8.00"));
        }

        urlaubsantragService.approveAntrag(ANTRAG_ID);

        ArgumentCaptor<Abwesenheit> captor = ArgumentCaptor.forClass(Abwesenheit.class);
        verify(abwesenheitRepository, times(5)).save(captor.capture());
        for (Abwesenheit a : captor.getAllValues()) {
            assertEquals(0, new BigDecimal("8.00").compareTo(a.getStunden()),
                    "Tag " + a.getDatum() + " hatte " + a.getStunden() + " Stunden");
        }
    }

    @Test
    void approveAntrag_waehrendWiedereingliederung_buchtStufenplanStundenStattVollemSoll() {
        LocalDate von = LocalDate.of(2026, 6, 1);
        LocalDate bis = LocalDate.of(2026, 6, 5);
        stubApproveGrunddaten();
        when(repository.findById(ANTRAG_ID)).thenReturn(Optional.of(antrag(von, bis)));
        when(feiertagService.istFeiertag(any(LocalDate.class))).thenReturn(false);
        for (LocalDate d = von; !d.isAfter(bis); d = d.plusDays(1)) {
            when(tagesSollService.arbeitsSoll(MITARBEITER_ID, testZeitkonto, d)).thenReturn(new BigDecimal("2.00"));
        }

        urlaubsantragService.approveAntrag(ANTRAG_ID);

        ArgumentCaptor<Abwesenheit> captor = ArgumentCaptor.forClass(Abwesenheit.class);
        verify(abwesenheitRepository, times(5)).save(captor.capture());
        for (Abwesenheit a : captor.getAllValues()) {
            assertEquals(0, new BigDecimal("2.00").compareTo(a.getStunden()),
                    "Tag " + a.getDatum() + " hatte " + a.getStunden() + " Stunden");
        }
    }

    @Test
    void approveAntrag_feiertagWirdUebersprungenUndNichtBeiTagesSollServiceAbgefragt() {
        // Mo 2026-06-01 bis Fr 2026-06-05, Mittwoch (2026-06-03) ist Feiertag.
        LocalDate von = LocalDate.of(2026, 6, 1);
        LocalDate bis = LocalDate.of(2026, 6, 5);
        LocalDate feiertag = LocalDate.of(2026, 6, 3);
        stubApproveGrunddaten();
        when(repository.findById(ANTRAG_ID)).thenReturn(Optional.of(antrag(von, bis)));
        when(feiertagService.istFeiertag(any(LocalDate.class))).thenAnswer(inv -> inv.getArgument(0).equals(feiertag));
        for (LocalDate d = von; !d.isAfter(bis); d = d.plusDays(1)) {
            if (!d.equals(feiertag)) {
                when(tagesSollService.arbeitsSoll(MITARBEITER_ID, testZeitkonto, d)).thenReturn(new BigDecimal("8.00"));
            }
        }

        urlaubsantragService.approveAntrag(ANTRAG_ID);

        verify(abwesenheitRepository, times(4)).save(any(Abwesenheit.class));
        verify(tagesSollService, never()).arbeitsSoll(eq(MITARBEITER_ID), eq(testZeitkonto), eq(feiertag));
    }

    @Test
    void approveAntrag_wochenendeWirdUebersprungenUndNichtBeiTagesSollServiceAbgefragt() {
        // Fr 2026-06-05 bis Mo 2026-06-08: dazwischen liegt ein ganzes Wochenende.
        LocalDate von = LocalDate.of(2026, 6, 5);
        LocalDate bis = LocalDate.of(2026, 6, 8);
        LocalDate samstag = LocalDate.of(2026, 6, 6);
        LocalDate sonntag = LocalDate.of(2026, 6, 7);
        stubApproveGrunddaten();
        when(repository.findById(ANTRAG_ID)).thenReturn(Optional.of(antrag(von, bis)));
        when(feiertagService.istFeiertag(any(LocalDate.class))).thenReturn(false);
        when(tagesSollService.arbeitsSoll(eq(MITARBEITER_ID), eq(testZeitkonto), any(LocalDate.class)))
                .thenReturn(new BigDecimal("8.00"));

        urlaubsantragService.approveAntrag(ANTRAG_ID);

        verify(abwesenheitRepository, times(2)).save(any(Abwesenheit.class));
        verify(tagesSollService, never()).arbeitsSoll(eq(MITARBEITER_ID), eq(testZeitkonto), eq(samstag));
        verify(tagesSollService, never()).arbeitsSoll(eq(MITARBEITER_ID), eq(testZeitkonto), eq(sonntag));
    }

    @Test
    void approveAntrag_ruftGetOrCreateZeitkontoGenauEinmalAuf_keinN1InDerSchleife() {
        LocalDate von = LocalDate.of(2026, 6, 1);
        LocalDate bis = LocalDate.of(2026, 6, 5);
        stubApproveGrunddaten();
        when(repository.findById(ANTRAG_ID)).thenReturn(Optional.of(antrag(von, bis)));
        when(feiertagService.istFeiertag(any(LocalDate.class))).thenReturn(false);
        when(tagesSollService.arbeitsSoll(eq(MITARBEITER_ID), eq(testZeitkonto), any(LocalDate.class)))
                .thenReturn(new BigDecimal("8.00"));

        urlaubsantragService.approveAntrag(ANTRAG_ID);

        verify(zeitkontoService, times(1)).getOrCreateZeitkonto(MITARBEITER_ID);
    }

    @Test
    void pruefeHinweise_delegiertAnLangzeitkrankmeldungService() {
        LocalDate von = LocalDate.of(2026, 3, 1);
        LocalDate bis = LocalDate.of(2026, 3, 5);
        List<String> erwartet = List.of(
                "In diesem Zeitraum läuft eine Krankmeldung (seit 2026-02-01). Bitte prüfen, ob der Urlaub wirklich passt.");
        when(langzeitkrankmeldungService.pruefeUrlaubsHinweise(MITARBEITER_ID, von, bis)).thenReturn(erwartet);

        List<String> ergebnis = urlaubsantragService.pruefeHinweise(MITARBEITER_ID, von, bis);

        assertEquals(erwartet, ergebnis);
        verify(langzeitkrankmeldungService).pruefeUrlaubsHinweise(MITARBEITER_ID, von, bis);
    }

    @Test
    void pruefeHinweise_ohneUeberlappendeKrankmeldung_liefertLeereListe() {
        LocalDate von = LocalDate.of(2026, 3, 1);
        LocalDate bis = LocalDate.of(2026, 3, 5);
        when(langzeitkrankmeldungService.pruefeUrlaubsHinweise(MITARBEITER_ID, von, bis)).thenReturn(List.of());

        List<String> ergebnis = urlaubsantragService.pruefeHinweise(MITARBEITER_ID, von, bis);

        assertTrue(ergebnis.isEmpty());
    }
}
