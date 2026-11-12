package org.example.kalkulationsprogramm.service.vorschau;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UmrechnungsplaetzeTest {

    @Test
    @DisplayName("Mit freiem Platz läuft die Umrechnung und liefert ihr Ergebnis")
    void freierPlatz() throws IOException {
        Umrechnungsplaetze plaetze = new Umrechnungsplaetze(1, 0);

        assertThat(plaetze.belege(() -> "fertig")).isEqualTo("fertig");
        assertThat(plaetze.belege(() -> "nochmal")).isEqualTo("nochmal");
    }

    @Test
    @DisplayName("Ohne freien Platz nach der Wartezeit: UmrechnungAusgelastetException, die Umrechnung läuft nicht")
    void ausgelastet() {
        Umrechnungsplaetze plaetze = new Umrechnungsplaetze(0, 0);
        AtomicReference<Boolean> gelaufen = new AtomicReference<>(false);

        assertThatThrownBy(() -> plaetze.belege(() -> {
            gelaufen.set(true);
            return "nie";
        })).isInstanceOf(UmrechnungAusgelastetException.class);
        assertThat(gelaufen.get()).isFalse();
    }

    @Test
    @DisplayName("Scheitert die Umrechnung, wird der Platz trotzdem wieder frei")
    void platzNachFehlerFrei() throws IOException {
        Umrechnungsplaetze plaetze = new Umrechnungsplaetze(1, 0);

        assertThatThrownBy(() -> plaetze.belege(() -> {
            throw new IOException("kaputt");
        })).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> plaetze.belege(() -> {
            throw new IllegalStateException("kaputt");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(plaetze.belege(() -> 1)).isEqualTo(1);
    }

    @Test
    @DisplayName("Belegter Platz: ein zweiter Aufruf gibt nach der Wartezeit auf")
    void zweiterAufrufWartetBegrenzt() throws Exception {
        Umrechnungsplaetze plaetze = new Umrechnungsplaetze(1, 50);
        CountDownLatch belegt = new CountDownLatch(1);
        CountDownLatch freigeben = new CountDownLatch(1);
        Thread erster = new Thread(() -> {
            try {
                plaetze.belege(() -> {
                    belegt.countDown();
                    freigeben.await();
                    return null;
                });
            } catch (Exception ignoriert) {
                // im Test egal
            }
        });
        erster.start();
        belegt.await();
        try {
            assertThatThrownBy(() -> plaetze.belege(() -> "zweiter"))
                    .isInstanceOf(UmrechnungAusgelastetException.class);
        } finally {
            freigeben.countDown();
            erster.join();
        }
        assertThat(plaetze.belege(() -> "danach")).isEqualTo("danach");
    }

    @Test
    @DisplayName("Unterbrochenes Warten gilt als ausgelastet und behält den Unterbrechungs-Vermerk")
    void unterbrochen() {
        Umrechnungsplaetze plaetze = new Umrechnungsplaetze(0, 10_000);
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> plaetze.belege(() -> "nie")).isInstanceOf(UmrechnungAusgelastetException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}
