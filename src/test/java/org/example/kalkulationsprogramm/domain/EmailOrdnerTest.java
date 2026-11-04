package org.example.kalkulationsprogramm.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class EmailOrdnerTest {

    private static Email eingang() {
        Email e = new Email();
        e.setDirection(EmailDirection.IN);
        e.setZuordnungTyp(EmailZuordnungTyp.KEINE);
        return e;
    }

    @Test
    @DisplayName("Ordner einer Mail: Papierkorb vor Spam vor Newsletter vor Gesendet vor Zuordnung")
    void reihenfolge() {
        Email e = eingang();
        assertThat(EmailOrdner.von(e)).isEqualTo(EmailOrdner.INBOX);
        e.setPotentialInquiry(true);
        assertThat(EmailOrdner.von(e)).isEqualTo(EmailOrdner.INQUIRIES);
        e.setZuordnungTyp(EmailZuordnungTyp.PROJEKT);
        assertThat(EmailOrdner.von(e)).isEqualTo(EmailOrdner.PROJECTS);
        e.setDirection(EmailDirection.OUT);
        assertThat(EmailOrdner.von(e)).isEqualTo(EmailOrdner.SENT);
        e.setNewsletter(true);
        assertThat(EmailOrdner.von(e)).isEqualTo(EmailOrdner.NEWSLETTER);
        e.setSpam(true);
        assertThat(EmailOrdner.von(e)).isEqualTo(EmailOrdner.SPAM);
        e.setDeletedAt(LocalDateTime.now());
        assertThat(EmailOrdner.von(e)).isEqualTo(EmailOrdner.TRASH);
    }

    @Test
    @DisplayName("Zuordnungen: Anfrage, Lieferant und Steuerberater haben eigene Ordner")
    void zuordnungen() {
        Email e = eingang();
        e.setZuordnungTyp(EmailZuordnungTyp.ANFRAGE);
        assertThat(EmailOrdner.von(e).schluessel()).isEqualTo("offers");
        e.setZuordnungTyp(EmailZuordnungTyp.LIEFERANT);
        assertThat(EmailOrdner.von(e).schluessel()).isEqualTo("suppliers");
        e.setZuordnungTyp(EmailZuordnungTyp.STEUERBERATER);
        assertThat(EmailOrdner.von(e).schluessel()).isEqualTo("tax-advisors");
    }

    @Test
    void ohneZuordnungstypIstPosteingang() {
        Email e = eingang();
        e.setZuordnungTyp(null);
        assertThat(EmailOrdner.von(e)).isEqualTo(EmailOrdner.INBOX);
    }

    @ParameterizedTest
    @EnumSource(EmailOrdner.class)
    void schluesselHinUndZurueck(EmailOrdner ordner) {
        assertThat(EmailOrdner.vonSchluessel(ordner.schluessel())).contains(ordner);
    }

    @Test
    void unbekannterSchluessel() {
        assertThat(EmailOrdner.vonSchluessel("drafts")).isEmpty();
        assertThat(EmailOrdner.vonSchluessel(null)).isEmpty();
        assertThat(EmailOrdner.vonSchluessel("INBOX")).isEmpty();
        assertThat(EmailOrdner.vonSchluessel("'; DROP TABLE email; --")).isEmpty();
    }
}
