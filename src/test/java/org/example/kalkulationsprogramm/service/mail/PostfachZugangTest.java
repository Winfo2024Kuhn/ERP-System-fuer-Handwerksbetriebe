package org.example.kalkulationsprogramm.service.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PostfachZugangTest {

    private static PostfachZugang zugang(String benutzer, String passwort, String smtp, String imap) {
        return new PostfachZugang(1L, "info@example.com", null, benutzer, passwort, smtp, 465, imap, 993, true);
    }

    @Test
    void versandBrauchtAnmeldungPasswortUndServer() {
        assertThat(zugang("info@example.com", "pw", "mail.example.com", null).hatVersandZugang()).isTrue();
        assertThat(zugang(null, "pw", "mail.example.com", null).hatVersandZugang()).isFalse();
        assertThat(zugang("info@example.com", " ", "mail.example.com", null).hatVersandZugang()).isFalse();
        assertThat(zugang("info@example.com", "pw", "", null).hatVersandZugang()).isFalse();
    }

    @Test
    void abrufBrauchtAnmeldungPasswortUndServer() {
        assertThat(zugang("info@example.com", "pw", null, "mail.example.com").hatAbrufZugang()).isTrue();
        assertThat(zugang(" ", "pw", null, "mail.example.com").hatAbrufZugang()).isFalse();
        assertThat(zugang("info@example.com", null, null, "mail.example.com").hatAbrufZugang()).isFalse();
        assertThat(zugang("info@example.com", "pw", null, null).hatAbrufZugang()).isFalse();
    }

    @Test
    void toStringVerraetDasPasswortNicht() {
        assertThat(zugang("info@example.com", "geheim123", "mail.example.com", null).toString())
                .doesNotContain("geheim123").contains("passwort=***");
        assertThat(zugang("info@example.com", null, null, null).toString()).contains("passwort=]");
    }
}
