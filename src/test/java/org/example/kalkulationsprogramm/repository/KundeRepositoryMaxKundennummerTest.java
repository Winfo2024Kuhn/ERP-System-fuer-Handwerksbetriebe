package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Die hoechste Kundennummer wird in Java bestimmt (CAST AS UNSIGNED gibt es nur in MySQL). */
class KundeRepositoryMaxKundennummerTest {

    private static KundeRepository mitNummern(String... nummern) {
        KundeRepository repository = Mockito.mock(KundeRepository.class, Mockito.CALLS_REAL_METHODS);
        Mockito.doReturn(List.of(nummern)).when(repository).findAlleKundennummern();
        return repository;
    }

    @Test
    @DisplayName("Numerisch verglichen, nicht als Text (1009 > 998)")
    void numerischVerglichen() {
        assertThat(mitNummern("998", "1009", "42").findMaxKundennummer()).contains("1009");
    }

    @Test
    @DisplayName("Nummern mit Buchstaben oder Zeichen zaehlen nicht mit")
    void nurReineZahlen() {
        assertThat(mitNummern("K-5000", "100", "12a").findMaxKundennummer()).contains("100");
    }

    @Test
    @DisplayName("Leerzeichen werden ignoriert, leere Eintraege uebersprungen")
    void leerzeichen() {
        assertThat(mitNummern(" 205 ", "  ", "17").findMaxKundennummer()).contains("205");
    }

    @Test
    @DisplayName("Zu lange Zahlen (kein long) laufen nicht ueber")
    void zuLang() {
        assertThat(mitNummern("1234567890123456789012", "7").findMaxKundennummer()).contains("7");
    }

    @Test
    @DisplayName("Keine numerische Nummer: leer")
    void keine() {
        assertThat(mitNummern().findMaxKundennummer()).isEmpty();
        assertThat(mitNummern("K-1").findMaxKundennummer()).isEmpty();
    }

    @Test
    @DisplayName("Verhalten wie die alte MySQL-Abfrage: groesste Nummer als Text zurueck")
    void textZurueck() {
        assertThat(mitNummern("0042", "41").findMaxKundennummer()).contains("0042");
    }
}
