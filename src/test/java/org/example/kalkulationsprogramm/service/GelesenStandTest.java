package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.junit.jupiter.api.Test;

class GelesenStandTest {

    private static Email mail(Long id, EmailDirection richtung, boolean gemeinsamGelesen) {
        Email e = new Email();
        e.setId(id);
        e.setDirection(richtung);
        e.setRead(gemeinsamGelesen);
        return e;
    }

    @Test
    void proBenutzerZaehltNurDieEigenenMerker() {
        GelesenStand erika = new GelesenStand(Set.of(1L), true);

        assertThat(erika.istGelesen(mail(1L, EmailDirection.IN, false))).isTrue();
        // Von jemand anderem geöffnet – für Erika trotzdem ungelesen.
        assertThat(erika.istUngelesen(mail(2L, EmailDirection.IN, true))).isTrue();
        // Mails ohne Richtung (Altbestand) behandeln wie eingehende.
        assertThat(erika.istUngelesen(mail(3L, null, false))).isTrue();
        assertThat(erika.istUngelesen(mail(null, EmailDirection.IN, false))).isTrue();
    }

    @Test
    void gesendeteUndFehlendeMailsGeltenAlsGelesen() {
        GelesenStand erika = new GelesenStand(Set.of(), true);

        assertThat(erika.istGelesen(mail(4L, EmailDirection.OUT, false))).isTrue();
        assertThat(erika.istGelesen(null)).isTrue();
    }

    @Test
    void ohneBenutzerZaehltDasGemeinsameFeld() {
        assertThat(GelesenStand.GEMEINSAM.istGelesen(mail(5L, EmailDirection.IN, true))).isTrue();
        assertThat(GelesenStand.GEMEINSAM.istUngelesen(mail(6L, EmailDirection.IN, false))).isTrue();
        assertThat(new GelesenStand(null, false).gelesen()).isEmpty();
    }
}
