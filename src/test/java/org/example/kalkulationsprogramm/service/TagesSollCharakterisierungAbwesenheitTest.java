package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Abwesenheit;
import org.example.kalkulationsprogramm.domain.AbwesenheitsTyp;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Zeitkonto;
import org.example.kalkulationsprogramm.repository.AbwesenheitRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Charakterisierungstest (Langzeitkrankmeldung, Abschnitt 1 / Task 2).
 *
 * Friert {@link AbwesenheitService#bucheAbwesenheit} zahlengenau ein - Task 4
 * stellt die Sollstunden-Ermittlung auf {@code TagesSollService.arbeitsSoll}
 * um (siehe E1 im Plan). Wird eine dieser Zusicherungen rot, ist die
 * Zusicherung falsch, nicht der Bestand.
 *
 * Dummy-Daten (DSGVO): Max Mustermann, ID 1.
 */
@ExtendWith(MockitoExtension.class)
class TagesSollCharakterisierungAbwesenheitTest {

    @Mock
    private AbwesenheitRepository abwesenheitRepository;
    @Mock
    private MitarbeiterRepository mitarbeiterRepository;
    @Mock
    private ZeitkontoService zeitkontoService;
    @Mock
    private FeiertagService feiertagService;
    @Mock
    private MonatsSaldoService monatsSaldoService;
    @Mock
    private ZeitbuchungRepository zeitbuchungRepository;
    @Mock
    private TagesSollService tagesSollService;

    @InjectMocks
    private AbwesenheitService abwesenheitService;

    private static final Long MITARBEITER_ID = 1L;

    // Gemeinsame Fixture aus dem Plan (Task 2).
    private static final LocalDate MONTAG_NORMAL = LocalDate.of(2026, 6, 1);
    private static final LocalDate SAMSTAG_WOCHENENDE = LocalDate.of(2026, 6, 6);
    private static final LocalDate VOLLER_FEIERTAG = LocalDate.of(2026, 1, 1); // Neujahr

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
        testZeitkonto.setSamstagStunden(new BigDecimal("0.00"));
        testZeitkonto.setSonntagStunden(new BigDecimal("0.00"));
    }

    private void stubGrunddaten() {
        when(mitarbeiterRepository.findById(MITARBEITER_ID)).thenReturn(java.util.Optional.of(testMitarbeiter));
        when(abwesenheitRepository.existsByMitarbeiterIdAndDatumAndTyp(anyLong(), any(), any())).thenReturn(false);
        when(feiertagService.istFeiertag(any())).thenReturn(false);
        when(zeitkontoService.getOrCreateZeitkonto(MITARBEITER_ID)).thenReturn(testZeitkonto);
        when(abwesenheitRepository.save(any(Abwesenheit.class))).thenAnswer(inv -> inv.getArgument(0));
        // Verkabelung fuer TagesSollService: pro Fixture-Tag exakt der Wert, den vorher
        // der rohe Zeitkonto-Wert lieferte (Montag = 8.00h) - kein pauschales any()->Wert,
        // damit ein falscher Stub-Wert die Zusicherungen unten tatsaechlich zum Kippen bringt.
        when(tagesSollService.arbeitsSoll(MITARBEITER_ID, testZeitkonto, MONTAG_NORMAL))
                .thenReturn(new BigDecimal("8.00"));
    }

    @Test
    void krankheit_normalerArbeitstag_gibtVolleSollstunden() {
        stubGrunddaten();
        when(zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(anyLong(), any(), any()))
                .thenReturn(List.of());

        Abwesenheit result = abwesenheitService.bucheAbwesenheit(
                MITARBEITER_ID, MONTAG_NORMAL, AbwesenheitsTyp.KRANKHEIT, false);

        assertEquals(0, new BigDecimal("8.00").compareTo(result.getStunden()));
    }

    @Test
    void krankheit_halberTag_gibtVierStunden() {
        stubGrunddaten();
        when(zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(anyLong(), any(), any()))
                .thenReturn(List.of());

        Abwesenheit result = abwesenheitService.bucheAbwesenheit(
                MITARBEITER_ID, MONTAG_NORMAL, AbwesenheitsTyp.KRANKHEIT, true);

        assertEquals(0, new BigDecimal("4.00").compareTo(result.getStunden()));
    }

    @Test
    void amSamstag_wirftKeinArbeitstag() {
        when(mitarbeiterRepository.findById(MITARBEITER_ID)).thenReturn(java.util.Optional.of(testMitarbeiter));
        when(abwesenheitRepository.existsByMitarbeiterIdAndDatumAndTyp(anyLong(), any(), any())).thenReturn(false);
        when(feiertagService.istFeiertag(SAMSTAG_WOCHENENDE)).thenReturn(false);
        when(zeitkontoService.getOrCreateZeitkonto(MITARBEITER_ID)).thenReturn(testZeitkonto);
        // Samstag: Zeitkonto liefert roh 0.00h - derselbe Wert wie vorher direkt aus dem Zeitkonto.
        when(tagesSollService.arbeitsSoll(MITARBEITER_ID, testZeitkonto, SAMSTAG_WOCHENENDE))
                .thenReturn(new BigDecimal("0.00"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> abwesenheitService.bucheAbwesenheit(
                        MITARBEITER_ID, SAMSTAG_WOCHENENDE, AbwesenheitsTyp.KRANKHEIT, false));

        assertEquals(true, ex.getMessage().contains("Kein Arbeitstag"),
                "Meldung war: " + ex.getMessage());
    }

    @Test
    void anFeiertag_wirftAbwesenheitNichtErlaubt() {
        when(mitarbeiterRepository.findById(MITARBEITER_ID)).thenReturn(java.util.Optional.of(testMitarbeiter));
        when(abwesenheitRepository.existsByMitarbeiterIdAndDatumAndTyp(anyLong(), any(), any())).thenReturn(false);
        when(feiertagService.istFeiertag(VOLLER_FEIERTAG)).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> abwesenheitService.bucheAbwesenheit(
                        MITARBEITER_ID, VOLLER_FEIERTAG, AbwesenheitsTyp.KRANKHEIT, false));

        assertEquals(true, ex.getMessage().contains("An Feiertagen kann keine Abwesenheit gebucht werden"),
                "Meldung war: " + ex.getMessage());
    }
}
