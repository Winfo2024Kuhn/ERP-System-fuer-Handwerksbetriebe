package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit-Tests für AbwesenheitService.
 * Schwerpunkt: Krankheit füllt nur die Lücke bis zum Soll auf, wenn an dem Tag
 * bereits gearbeitet wurde (Bug: Krankheit erzeugte sonst Überstunden).
 */
@ExtendWith(MockitoExtension.class)
class AbwesenheitServiceTest {

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

    private Mitarbeiter testMitarbeiter;
    private Zeitkonto testZeitkonto;

    private static final Long MITARBEITER_ID = 1L;
    // 2025-06-02 ist ein Montag → Soll 8h
    private static final LocalDate MONTAG = LocalDate.of(2025, 6, 2);

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

    private void stubGrunddaten() {
        when(mitarbeiterRepository.findById(MITARBEITER_ID)).thenReturn(Optional.of(testMitarbeiter));
        when(abwesenheitRepository.existsByMitarbeiterIdAndDatumAndTyp(anyLong(), any(), any())).thenReturn(false);
        when(feiertagService.istFeiertag(any())).thenReturn(false);
        when(zeitkontoService.getOrCreateZeitkonto(MITARBEITER_ID)).thenReturn(testZeitkonto);
        when(abwesenheitRepository.save(any(Abwesenheit.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tagesSollService.arbeitsSoll(anyLong(), any(), any())).thenReturn(new BigDecimal("8.00"));
    }

    private Zeitbuchung erstelleArbeitsbuchung(BigDecimal stunden) {
        Zeitbuchung b = new Zeitbuchung();
        b.setMitarbeiter(testMitarbeiter);
        b.setStartZeit(MONTAG.atTime(8, 0));
        b.setEndeZeit(MONTAG.atTime(8, 0).plusMinutes(stunden.multiply(BigDecimal.valueOf(60)).longValue()));
        b.setAnzahlInStunden(stunden);
        b.setTyp(BuchungsTyp.ARBEIT);
        return b;
    }

    @Test
    void krankheit_OhneVorherigeBuchung_BuchtVolleSollStunden() {
        stubGrunddaten();
        when(zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(anyLong(), any(), any()))
                .thenReturn(List.of());

        Abwesenheit result = abwesenheitService.bucheAbwesenheit(
                MITARBEITER_ID, MONTAG, AbwesenheitsTyp.KRANKHEIT, false);

        assertEquals(0, new BigDecimal("8.00").compareTo(result.getStunden()),
                "Ohne gearbeitete Stunden muss die Krankheit die vollen Sollstunden abdecken");
    }

    @Test
    void krankheit_NachTeilweiseGearbeitet_ZiehtGearbeiteteStundenAbVomSoll() {
        // Bug-Szenario: Mitarbeiter arbeitet 3h, geht dann krank nach Hause.
        stubGrunddaten();
        when(zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(anyLong(), any(), any()))
                .thenReturn(List.of(erstelleArbeitsbuchung(new BigDecimal("3.00"))));

        Abwesenheit result = abwesenheitService.bucheAbwesenheit(
                MITARBEITER_ID, MONTAG, AbwesenheitsTyp.KRANKHEIT, false);

        // Soll 8h - 3h gearbeitet = 5h Krankheit (gearbeitet + Krankheit = Soll)
        assertEquals(0, new BigDecimal("5.00").compareTo(result.getStunden()),
                "Krankheit muss um die bereits gearbeiteten Stunden reduziert werden");
    }

    @Test
    void krankheit_PausenZaehlenNichtAlsGearbeitet() {
        stubGrunddaten();
        Zeitbuchung pause = new Zeitbuchung();
        pause.setMitarbeiter(testMitarbeiter);
        pause.setStartZeit(MONTAG.atTime(12, 0));
        pause.setEndeZeit(MONTAG.atTime(12, 30));
        pause.setAnzahlInStunden(new BigDecimal("0.50"));
        pause.setTyp(BuchungsTyp.PAUSE);

        when(zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(anyLong(), any(), any()))
                .thenReturn(List.of(erstelleArbeitsbuchung(new BigDecimal("3.00")), pause));

        Abwesenheit result = abwesenheitService.bucheAbwesenheit(
                MITARBEITER_ID, MONTAG, AbwesenheitsTyp.KRANKHEIT, false);

        // Pause (0,5h) darf NICHT abgezogen werden → 8h - 3h = 5h
        assertEquals(0, new BigDecimal("5.00").compareTo(result.getStunden()),
                "Pausen dürfen nicht als gearbeitete Stunden gezählt werden");
    }

    @Test
    void krankheit_GanzerTagBereitsGearbeitet_BuchtNullStunden() {
        stubGrunddaten();
        when(zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(anyLong(), any(), any()))
                .thenReturn(List.of(erstelleArbeitsbuchung(new BigDecimal("8.00"))));

        Abwesenheit result = abwesenheitService.bucheAbwesenheit(
                MITARBEITER_ID, MONTAG, AbwesenheitsTyp.KRANKHEIT, false);

        // Soll bereits voll gearbeitet → keine zusätzlichen Krankheitsstunden (nie negativ)
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getStunden()),
                "Wenn das Soll schon gearbeitet wurde, darf die Krankheit nicht negativ werden");
    }

    @Test
    void krankheit_NotizZeigtEchteGearbeiteteStunden_AuchBeiUebersoll() {
        // Regression zum Reviewer-Hinweis: Notiz darf NICHT aus dem geclampten Saldo
        // zurückgerechnet werden. Bei 9h gearbeitet (> 8h Soll) muss die Notiz 9h zeigen.
        stubGrunddaten();
        when(zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(anyLong(), any(), any()))
                .thenReturn(List.of(erstelleArbeitsbuchung(new BigDecimal("9.00"))));

        Abwesenheit result = abwesenheitService.bucheAbwesenheit(
                MITARBEITER_ID, MONTAG, AbwesenheitsTyp.KRANKHEIT, false);

        assertEquals(0, BigDecimal.ZERO.compareTo(result.getStunden()));
        assertTrue(result.getNotiz().contains("9"),
                "Notiz muss die echten gearbeiteten Stunden (9h) zeigen, nicht den geclampten Wert");
    }

    @Test
    void krankheit_HalberTag_ZiehtGearbeiteteStundenVonHalbemSollAb() {
        stubGrunddaten();
        when(zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(anyLong(), any(), any()))
                .thenReturn(List.of(erstelleArbeitsbuchung(new BigDecimal("1.00"))));

        Abwesenheit result = abwesenheitService.bucheAbwesenheit(
                MITARBEITER_ID, MONTAG, AbwesenheitsTyp.KRANKHEIT, true);

        // Halber Tag: Basis 4h - 1h gearbeitet = 3h
        assertEquals(0, new BigDecimal("3.00").compareTo(result.getStunden()),
                "Halbtags-Krankheit muss gearbeitete Stunden vom halben Soll abziehen");
    }

    @Test
    void krankheit_WaehrendWiedereingliederung_BuchtStufenplanStundenStattVollemSoll() {
        // Stufenplan: Mitarbeiter läuft laut TagesSollService mit 2h/Tag statt der
        // vollen 8h Sollstunden aus dem Zeitkonto. Eigene Stubs statt stubGrunddaten(),
        // weil hier eine engere tagesSollService-Antwort die generische überschreiben
        // muss (sonst UnnecessaryStubbingException bei doppelter Stubbierung).
        when(mitarbeiterRepository.findById(MITARBEITER_ID)).thenReturn(Optional.of(testMitarbeiter));
        when(abwesenheitRepository.existsByMitarbeiterIdAndDatumAndTyp(anyLong(), any(), any())).thenReturn(false);
        when(feiertagService.istFeiertag(any())).thenReturn(false);
        when(zeitkontoService.getOrCreateZeitkonto(MITARBEITER_ID)).thenReturn(testZeitkonto);
        when(abwesenheitRepository.save(any(Abwesenheit.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tagesSollService.arbeitsSoll(MITARBEITER_ID, testZeitkonto, MONTAG)).thenReturn(new BigDecimal("2.00"));
        when(zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(anyLong(), any(), any()))
                .thenReturn(List.of());

        Abwesenheit result = abwesenheitService.bucheAbwesenheit(
                MITARBEITER_ID, MONTAG, AbwesenheitsTyp.KRANKHEIT, false);

        assertEquals(0, new BigDecimal("2.00").compareTo(result.getStunden()),
                "Während einer Wiedereingliederung muss die Krankmeldung die reduzierten " +
                        "Stufenplan-Stunden (2h) buchen, nicht die vollen Sollstunden (8h)");
    }

    @Test
    void urlaub_IgnoriertGearbeiteteStunden_BleibtVollesSoll() {
        // Regression: Nur KRANKHEIT füllt die Lücke; URLAUB bleibt unverändert volle Sollstunden
        stubGrunddaten();

        Abwesenheit result = abwesenheitService.bucheAbwesenheit(
                MITARBEITER_ID, MONTAG, AbwesenheitsTyp.URLAUB, false);

        assertEquals(0, new BigDecimal("8.00").compareTo(result.getStunden()),
                "Urlaub darf nicht um gearbeitete Stunden reduziert werden");
        verify(zeitbuchungRepository, never()).findByMitarbeiterIdAndStartZeitBetween(anyLong(), any(), any());
    }
}
