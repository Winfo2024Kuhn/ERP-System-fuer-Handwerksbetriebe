package org.example.kalkulationsprogramm.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailPostfachZuordnungTest {

    private static EmailAbsender postfach(Long id) {
        EmailAbsender p = new EmailAbsender();
        p.setId(id);
        p.setEmailAdresse("p" + id + "@example.com");
        return p;
    }

    @Test
    void ordnetJedesPostfachNurEinmalZu() {
        Email email = new Email();
        EmailAbsender info = postfach(1L);

        assertThat(email.ordnePostfachZu(info, "INBOX", 7L)).isTrue();
        assertThat(email.ordnePostfachZu(info, "INBOX.Sent", 8L)).isFalse();
        assertThat(email.ordnePostfachZu(postfach(1L), "INBOX", 7L)).isFalse();
        assertThat(email.ordnePostfachZu(postfach(2L), null, null)).isTrue();
        assertThat(email.ordnePostfachZu(null, "INBOX", 1L)).isFalse();

        assertThat(email.getPostfachZuordnungen()).hasSize(2);
        EmailPostfachZuordnung erste = email.getPostfachZuordnungen().get(0);
        assertThat(erste.getEmail()).isSameAs(email);
        assertThat(erste.getImapOrdner()).isEqualTo("INBOX");
        assertThat(erste.getImapUid()).isEqualTo(7L);
    }

    @Test
    void neuePostfaecherOhneIdSindVerschieden() {
        Email email = new Email();
        EmailAbsender a = new EmailAbsender();
        EmailAbsender b = new EmailAbsender();

        assertThat(email.ordnePostfachZu(a, null, null)).isTrue();
        assertThat(email.ordnePostfachZu(a, null, null)).isFalse();
        assertThat(email.ordnePostfachZu(b, null, null)).isTrue();
    }

    @Test
    void zuordnungOhnePostfachWirdUebersprungen() {
        Email email = new Email();
        EmailPostfachZuordnung leer = new EmailPostfachZuordnung();
        email.getPostfachZuordnungen().add(leer);

        assertThat(email.ordnePostfachZu(postfach(3L), null, null)).isTrue();
    }

    @Test
    void anmeldenameFaelltAufAdresseZurueck() {
        EmailAbsender p = postfach(4L);
        assertThat(p.effektiverBenutzername()).isEqualTo("p4@example.com");
        p.setBenutzername("  ");
        assertThat(p.effektiverBenutzername()).isEqualTo("p4@example.com");
        p.setBenutzername(" login@example.com ");
        assertThat(p.effektiverBenutzername()).isEqualTo("login@example.com");
    }
}
