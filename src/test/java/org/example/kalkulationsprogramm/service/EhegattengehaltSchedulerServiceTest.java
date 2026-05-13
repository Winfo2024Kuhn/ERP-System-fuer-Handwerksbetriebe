package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.KasseEinstellung;
import org.example.kalkulationsprogramm.domain.Sachkonto;
import org.example.kalkulationsprogramm.repository.KasseEinstellungRepository;
import org.example.kalkulationsprogramm.repository.SachkontoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit-Tests fuer den EhegattengehaltSchedulerService — Stichtag-Pruefung,
 * Idempotenz pro YYYY-MM, Konfigurations-Validierung. Buchungskonto wird
 * hart als "4120" vom SachkontoRepository nachgeschlagen.
 *
 * DSGVO-Dummy: Empfaenger ist immer "Diana Mustermann".
 */
@ExtendWith(MockitoExtension.class)
class EhegattengehaltSchedulerServiceTest {

    @Mock private KasseEinstellungRepository kasseEinstellungRepository;
    @Mock private KasseShortcutService kasseShortcutService;
    @Mock private SachkontoRepository sachkontoRepository;

    private EhegattengehaltSchedulerService service;

    @BeforeEach
    void setUp() {
        service = new EhegattengehaltSchedulerService(
                kasseEinstellungRepository, kasseShortcutService, sachkontoRepository);
        // Default: Sachkonto 4120 ist da. Einzelne Tests koennen das ueberschreiben.
        Sachkonto lohn = new Sachkonto();
        lohn.setId(42L);
        lohn.setNummer("4120");
        lohn.setBezeichnung("Loehne & Gehaelter");
        lenient().when(sachkontoRepository.findByNummer(eq("4120"))).thenReturn(Optional.of(lohn));
    }

    @Test
    @DisplayName("inaktiv -> keine Buchung")
    void inaktiv_buchtNicht() {
        KasseEinstellung k = einstellung();
        k.setEhegattengehaltAktiv(false);
        given(kasseEinstellungRepository.findSingleton()).willReturn(Optional.of(k));

        assertThat(service.tryLohnBuchung(LocalDate.of(2026, 5, 13))).isFalse();
        verify(kasseShortcutService, never()).lohnZahlung(any(), any(), any(), any(), any(), any());
        verify(kasseEinstellungRepository, never()).save(any());
    }

    @Test
    @DisplayName("Stichtag noch nicht erreicht -> keine Buchung")
    void vorStichtag_buchtNicht() {
        KasseEinstellung k = einstellung();
        k.setEhegattengehaltTag(20);
        given(kasseEinstellungRepository.findSingleton()).willReturn(Optional.of(k));

        assertThat(service.tryLohnBuchung(LocalDate.of(2026, 5, 13))).isFalse();
        verify(kasseShortcutService, never()).lohnZahlung(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Stichtag erreicht und Monat noch nicht gebucht -> bucht und setzt Sperre")
    void stichtagErreicht_buchtUndMarkiert() {
        KasseEinstellung k = einstellung();
        k.setId(1L);
        k.setEhegattengehaltTag(1);
        given(kasseEinstellungRepository.findSingleton()).willReturn(Optional.of(k));
        given(kasseEinstellungRepository.findById(anyLong())).willReturn(Optional.of(k));

        boolean gebucht = service.tryLohnBuchung(LocalDate.of(2026, 5, 13));

        assertThat(gebucht).isTrue();
        ArgumentCaptor<KasseEinstellung> savedCaptor = ArgumentCaptor.forClass(KasseEinstellung.class);
        verify(kasseEinstellungRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getLetzteBuchungJahrmonat()).isEqualTo("2026-05");
        ArgumentCaptor<String> empfCaptor = ArgumentCaptor.forClass(String.class);
        verify(kasseShortcutService).lohnZahlung(any(), any(), empfCaptor.capture(), any(), any(), any());
        assertThat(empfCaptor.getValue()).startsWith("Auto:");
        assertThat(empfCaptor.getValue()).contains("Diana Mustermann");
    }

    @Test
    @DisplayName("Idempotenz: bereits in diesem Monat gebucht -> keine zweite Buchung")
    void bereitsGebucht_buchtNichtMehr() {
        KasseEinstellung k = einstellung();
        k.setEhegattengehaltTag(1);
        k.setLetzteBuchungJahrmonat("2026-05");
        given(kasseEinstellungRepository.findSingleton()).willReturn(Optional.of(k));

        assertThat(service.tryLohnBuchung(LocalDate.of(2026, 5, 13))).isFalse();
        verify(kasseShortcutService, never()).lohnZahlung(any(), any(), any(), any(), any(), any());
        verify(kasseEinstellungRepository, never()).save(any());
    }

    @Test
    @DisplayName("Aktiv ohne Betrag -> nicht gebucht (Konfigurationsfehler)")
    void aktivOhneBetrag_buchtNicht() {
        KasseEinstellung k = einstellung();
        k.setEhegattengehaltBetrag(null);
        given(kasseEinstellungRepository.findSingleton()).willReturn(Optional.of(k));

        assertThat(service.tryLohnBuchung(LocalDate.of(2026, 5, 13))).isFalse();
        verify(kasseShortcutService, never()).lohnZahlung(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Sachkonto 4120 fehlt im Repository -> nicht gebucht (Konfigurationsfehler)")
    void aktivOhneSachkonto_buchtNicht() {
        KasseEinstellung k = einstellung();
        given(kasseEinstellungRepository.findSingleton()).willReturn(Optional.of(k));
        given(sachkontoRepository.findByNummer(eq("4120"))).willReturn(Optional.empty());

        assertThat(service.tryLohnBuchung(LocalDate.of(2026, 5, 13))).isFalse();
        verify(kasseShortcutService, never()).lohnZahlung(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Fehler bei Lohn-Buchung -> Sperre bleibt erhalten (kein Rollback)")
    void lohnBuchungFailedNachSperre_keinRollback() {
        KasseEinstellung k = einstellung();
        k.setId(1L);
        k.setEhegattengehaltTag(1);
        given(kasseEinstellungRepository.findSingleton()).willReturn(Optional.of(k));
        given(kasseEinstellungRepository.findById(anyLong())).willReturn(Optional.of(k));
        org.mockito.Mockito.when(kasseShortcutService.lohnZahlung(any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Buchung kaputt"));

        assertThatThrownBy(() -> service.tryLohnBuchung(LocalDate.of(2026, 5, 13)))
                .isInstanceOf(RuntimeException.class);

        // Sperre ist trotz Buchungsfehler gesetzt — sonst Doppelbuchungs-Risiko.
        verify(kasseEinstellungRepository).save(any(KasseEinstellung.class));
    }

    private static KasseEinstellung einstellung() {
        KasseEinstellung k = new KasseEinstellung();
        k.setEhegattengehaltAktiv(true);
        k.setEhegattengehaltBetrag(new BigDecimal("500.00"));
        k.setEhegattengehaltTag(1);
        k.setEhegattengehaltEmpfaengerName("Diana Mustermann");
        return k;
    }
}
