package org.example.kalkulationsprogramm.service.emailki;

import org.example.kalkulationsprogramm.dto.Email.EmailSchreibstil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KiSchreibregelnTest {

    @Test
    @DisplayName("Alle Stile bekommen die Kernregeln nach humanizer-de")
    void kernregelnFuerAlleStile() {
        for (EmailSchreibstil stil : EmailSchreibstil.values()) {
            assertThat(KiSchreibregeln.fuer(stil))
                    .contains("Gedankenstrich")
                    .contains("8–10 Uhr")
                    .contains("nahtlos").contains("umfassend").contains("zudem").contains("darüber hinaus")
                    .contains("entscheidend").contains("Ich hoffe").contains("Gerne").contains("Zögern Sie nicht")
                    .contains("Dreierlisten")
                    .contains("nicht nur").contains("Nicht X, sondern Y")
                    .contains("Fazit")
                    .contains("Zahlen").contains("Preise").contains("Namen").contains("Adressen")
                    .contains("Erfinde nichts")
                    .contains("Satzlängen");
        }
    }

    @Test
    @DisplayName("Locker nutzt den Modus Locker, alle anderen den Modus Sachlich")
    void modus() {
        assertThat(KiSchreibregeln.fuer(EmailSchreibstil.LOCKER)).contains("Modus: Locker").doesNotContain("Modus: Sachlich");
        assertThat(KiSchreibregeln.fuer(EmailSchreibstil.FOERMLICH)).contains("Modus: Sachlich");
        assertThat(KiSchreibregeln.fuer(EmailSchreibstil.VERLAUF)).contains("Modus: Sachlich");
        assertThat(KiSchreibregeln.fuer(EmailSchreibstil.RECHTSCHREIBUNG)).contains("Modus: Sachlich");
    }

    @Test
    @DisplayName("Bei reiner Rechtschreibprüfung werden Gedankenstriche und Floskeln trotzdem aufgelöst")
    void rechtschreibungLoestTrotzdemAuf() {
        assertThat(KiSchreibregeln.fuer(EmailSchreibstil.RECHTSCHREIBUNG))
                .contains("Auch bei der reinen Korrektur");
        assertThat(KiSchreibregeln.fuer(EmailSchreibstil.FOERMLICH))
                .doesNotContain("Auch bei der reinen Korrektur");
    }

    @Test
    @DisplayName("Die Regeln selbst enthalten keine Gedankenstriche als Satzzeichen")
    void regelnOhneGedankenstriche() {
        for (EmailSchreibstil stil : EmailSchreibstil.values()) {
            assertThat(KiSchreibregeln.fuer(stil)).doesNotContain(" – ").doesNotContain(" — ");
        }
    }
}
