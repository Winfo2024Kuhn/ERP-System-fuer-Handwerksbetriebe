package org.example.kalkulationsprogramm.service.telefon.telefonbuch;

import org.example.kalkulationsprogramm.dto.Telefon.TelefonbuchAbgleichErgebnisDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Entprellung, Start- und Nachtlauf des Telefonbuch-Abgleichs – der TaskScheduler wird von Hand „getickt“. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelefonbuchAbgleichPlanerTest {

    private static final Instant JETZT = Instant.parse("2026-10-21T08:00:00Z");

    @Mock TaskScheduler scheduler;
    @Mock TelefonbuchAbgleichService abgleich;

    private TelefonbuchAbgleichPlaner planer;

    private static final TelefonbuchAbgleichErgebnisDto OK = new TelefonbuchAbgleichErgebnisDto(true, false, "ok", 0, 0, 0, 0, 0);

    @BeforeEach
    void setUp() {
        planer = new TelefonbuchAbgleichPlaner(scheduler, abgleich, Clock.fixed(JETZT, ZoneId.of("Europe/Berlin")),
                Duration.ofSeconds(10), Duration.ofSeconds(45));
        when(abgleich.abgleichen(anyBoolean())).thenReturn(OK);
        when(abgleich.istBereit()).thenReturn(true);
    }

    private Runnable geplanteAufgabe(int anzahl) {
        ArgumentCaptor<Runnable> aufgabe = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, times(anzahl)).schedule(aufgabe.capture(), any(Instant.class));
        return aufgabe.getValue();
    }

    @Test
    @DisplayName("Viele Änderungen hintereinander → ein Lauf 10 s nach der ersten")
    void entprellt() {
        planer.aenderungGemeldet();
        planer.aenderungGemeldet();
        planer.aenderungGemeldet();

        verify(scheduler, times(1)).schedule(any(Runnable.class), eq(JETZT.plusSeconds(10)));
        geplanteAufgabe(1).run();
        verify(abgleich, times(1)).abgleichen(false);

        // Nach dem Lauf plant die nächste Änderung wieder neu
        planer.aenderungGemeldet();
        geplanteAufgabe(2);
    }

    @Test
    @DisplayName("Lief gerade ein anderer Lauf, wird danach noch einmal geplant")
    void laeuftSchon() {
        when(abgleich.abgleichen(anyBoolean())).thenReturn(TelefonbuchAbgleichErgebnisDto.beschaeftigt());
        planer.aenderungGemeldet();
        geplanteAufgabe(1).run();

        geplanteAufgabe(2);
        // Status bleibt „läuft“, bis der nachgeholte Lauf startet
        verify(abgleich).markiereAngefordert();
    }

    @Test
    @DisplayName("Vollabgleich-Wunsch (Einschalten) wird an den geplanten Lauf weitergegeben")
    void vollabgleichGewuenscht() {
        planer.aenderungGemeldet();
        planer.vollabgleichGewuenscht();
        geplanteAufgabe(1).run();

        verify(abgleich).abgleichen(true);
    }

    @Test
    @DisplayName("Fehler beim Planen oder im Lauf werden geschluckt – Speichern und Start bleiben ungestört")
    void fehlerWerdenGeschluckt() {
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenThrow(new TaskRejectedException("voll"));
        assertThatCode(() -> planer.aenderungGemeldet()).doesNotThrowAnyException();
        assertThatCode(() -> planer.serverGestartet()).doesNotThrowAnyException();

        when(abgleich.abgleichen(anyBoolean())).thenThrow(new IllegalStateException("kaputt"));
        assertThatCode(() -> planer.geplanterLauf()).doesNotThrowAnyException();
        assertThatCode(() -> planer.naechtlicherVollabgleich()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Nach dem Scheitern des Planens wird beim nächsten Mal wieder geplant")
    void planenNachFehlerErneut() {
        when(scheduler.schedule(any(Runnable.class), any(Instant.class)))
                .thenThrow(new TaskRejectedException("voll")).thenReturn(null);
        planer.aenderungGemeldet();
        planer.aenderungGemeldet();
        verify(scheduler, times(2)).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    @DisplayName("Server gestartet: Vollabgleich 45 s später, im Hintergrund; nur wenn bereit")
    void startabgleich() {
        planer.serverGestartet();

        verify(scheduler).schedule(any(Runnable.class), eq(JETZT.plusSeconds(45)));
        verify(abgleich, never()).abgleichen(anyBoolean());
        geplanteAufgabe(1).run();
        verify(abgleich).abgleichen(true);

        clearInvocations(abgleich, scheduler);
        when(abgleich.istBereit()).thenReturn(false);
        planer.serverGestartet();
        geplanteAufgabe(1).run();
        verify(abgleich, never()).abgleichen(anyBoolean());
    }

    @Test
    @DisplayName("Startabgleich trifft auf laufenden Abgleich → Vollabgleich wird nachgeholt")
    void startabgleichWaehrendLauf() {
        when(abgleich.abgleichen(true)).thenReturn(TelefonbuchAbgleichErgebnisDto.beschaeftigt(), OK);
        planer.serverGestartet();
        geplanteAufgabe(1).run();

        geplanteAufgabe(2).run();
        verify(abgleich, times(2)).abgleichen(true);
    }

    @Test
    @DisplayName("Nachtlauf: läuft auf dem eigenen Thread, Vollabgleich nur, wenn es etwas zu tun gibt")
    void nachtlauf() {
        planer.naechtlicherVollabgleich();
        verify(abgleich, never()).abgleichen(anyBoolean());
        geplanteAufgabe(1).run();
        verify(abgleich).abgleichen(true);

        clearInvocations(abgleich, scheduler);
        when(abgleich.istBereit()).thenReturn(false);
        planer.naechtlicherVollabgleich();
        geplanteAufgabe(1).run();
        verify(abgleich, never()).abgleichen(anyBoolean());
    }

    @Test
    @DisplayName("Sofort: Status zeigt gleich „läuft“, Lauf startet ohne Verzögerung im Hintergrund")
    void sofort() {
        assertThat(planer.sofort()).isTrue();

        verify(abgleich).markiereAngefordert();
        verify(scheduler).schedule(any(Runnable.class), eq(JETZT));
        verify(abgleich, never()).abgleichen(anyBoolean());
        geplanteAufgabe(1).run();
        verify(abgleich).abgleichen(true);
    }

    @Test
    @DisplayName("Sofort: kann nicht geplant werden → Anforderung zurückgenommen, false")
    void sofortScheitert() {
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenThrow(new TaskRejectedException("voll"));

        assertThat(planer.sofort()).isFalse();
        verify(abgleich).verwerfeAnforderung();
    }

    @Test
    @DisplayName("Eigener Thread für das Telefonbuch, wird beim Beenden heruntergefahren")
    void eigenerThread() {
        TelefonbuchAbgleichPlaner echt = new TelefonbuchAbgleichPlaner(abgleich, Clock.systemUTC());
        assertThatCode(echt::beenden).doesNotThrowAnyException();
    }
}
