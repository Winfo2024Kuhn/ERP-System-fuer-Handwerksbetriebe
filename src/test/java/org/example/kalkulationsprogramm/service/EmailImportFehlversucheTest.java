package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailImportFehlversucheTest {

    private final EmailImportFehlversuche fehlversuche = new EmailImportFehlversuche();
    private final String schluessel = EmailImportFehlversuche.schluessel(5L, "INBOX", 7L, 42L);

    @Test
    void neueNachrichtWirdVersucht() {
        assertThat(fehlversuche.ueberspringen(schluessel)).isFalse();
    }

    @Test
    void nachMaxVersuchenWirdUebersprungen() {
        for (int i = 1; i < EmailImportFehlversuche.MAX_VERSUCHE; i++) {
            assertThat(fehlversuche.fehlgeschlagen(schluessel)).isEqualTo(i);
            assertThat(fehlversuche.ueberspringen(schluessel)).isFalse();
        }
        assertThat(fehlversuche.fehlgeschlagen(schluessel)).isEqualTo(EmailImportFehlversuche.MAX_VERSUCHE);
        assertThat(fehlversuche.ueberspringen(schluessel)).isTrue();
    }

    @Test
    void erfolgSetztZaehlerZurueck() {
        fehlversuche.fehlgeschlagen(schluessel);
        fehlversuche.fehlgeschlagen(schluessel);
        fehlversuche.erfolgreich(schluessel);

        assertThat(fehlversuche.fehlgeschlagen(schluessel)).isEqualTo(1);
    }

    @Test
    void schluesselTrenntPostfachOrdnerUndUidValidity() {
        assertThat(schluessel)
                .isNotEqualTo(EmailImportFehlversuche.schluessel(6L, "INBOX", 7L, 42L))
                .isNotEqualTo(EmailImportFehlversuche.schluessel(5L, "INBOX.Sent", 7L, 42L))
                .isNotEqualTo(EmailImportFehlversuche.schluessel(5L, "INBOX", 8L, 42L))
                .isEqualTo(EmailImportFehlversuche.schluessel(5L, "INBOX", 7L, 42L));
    }
}
