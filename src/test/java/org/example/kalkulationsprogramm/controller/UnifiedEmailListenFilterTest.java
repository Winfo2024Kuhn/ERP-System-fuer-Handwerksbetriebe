package org.example.kalkulationsprogramm.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Email;
import org.junit.jupiter.api.Test;

/** Listen-Filter des E-Mail-Centers: blendet verborgene Mails aus, ohne die Reihenfolge zu ändern. */
class UnifiedEmailListenFilterTest {

    private static Email mail(Long id) {
        Email e = new Email();
        e.setId(id);
        return e;
    }

    @Test
    void ohneVerborgeneMailsBleibtDieListeUnberuehrt() {
        UnifiedEmailController.ListenFilter filter = new UnifiedEmailController.ListenFilter(null);
        List<Email> liste = List.of(mail(1L), mail(2L));

        assertThat(filter.aktiv()).isFalse();
        assertThat(filter.anwenden(liste)).isSameAs(liste);
        assertThat(filter.test(mail(1L))).isTrue();
    }

    @Test
    void verborgeneMailsFallenHeraus() {
        UnifiedEmailController.ListenFilter filter = new UnifiedEmailController.ListenFilter(Set.of(2L));

        assertThat(filter.aktiv()).isTrue();
        assertThat(filter.anwenden(List.of(mail(1L), mail(2L), mail(3L)))).extracting(Email::getId)
                .containsExactly(1L, 3L);
        assertThat(filter.test(null)).isFalse();
    }
}
