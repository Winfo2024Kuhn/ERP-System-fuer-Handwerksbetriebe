package org.example.kalkulationsprogramm.domain.firmendokumente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentZugriffsStufe.AENDERN;
import static org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentZugriffsStufe.ANSEHEN;
import static org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentZugriffsStufe.KEINE;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FirmendokumentZugriffsStufeTest {

    @Test
    @DisplayName("Ansehen ab ANSEHEN, Ändern nur mit AENDERN")
    void rechte() {
        assertThat(KEINE.darfAnsehen()).isFalse();
        assertThat(KEINE.darfAendern()).isFalse();
        assertThat(ANSEHEN.darfAnsehen()).isTrue();
        assertThat(ANSEHEN.darfAendern()).isFalse();
        assertThat(AENDERN.darfAnsehen()).isTrue();
        assertThat(AENDERN.darfAendern()).isTrue();
    }

    @Test
    @DisplayName("Mehrere Abteilungen: die höchste Stufe gilt; null zählt wie KEINE")
    void hoechste() {
        assertThat(FirmendokumentZugriffsStufe.hoechste(ANSEHEN, AENDERN)).isEqualTo(AENDERN);
        assertThat(FirmendokumentZugriffsStufe.hoechste(AENDERN, KEINE)).isEqualTo(AENDERN);
        assertThat(FirmendokumentZugriffsStufe.hoechste(KEINE, ANSEHEN)).isEqualTo(ANSEHEN);
        assertThat(FirmendokumentZugriffsStufe.hoechste(null, ANSEHEN)).isEqualTo(ANSEHEN);
        assertThat(FirmendokumentZugriffsStufe.hoechste(ANSEHEN, null)).isEqualTo(ANSEHEN);
    }
}
