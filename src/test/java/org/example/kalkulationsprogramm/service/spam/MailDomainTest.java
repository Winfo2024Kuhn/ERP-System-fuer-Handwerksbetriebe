package org.example.kalkulationsprogramm.service.spam;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MailDomainTest {

    @ParameterizedTest
    @CsvSource({
            "max@Musterfirma.DE, musterfirma.de",
            "'Max Mustermann <max@mail.musterfirma.de>', mail.musterfirma.de",
            "'max@musterfirma.de. ', musterfirma.de"
    })
    void domainAusAdresse(String adresse, String domain) {
        assertThat(MailDomain.vonAdresse(adresse)).isEqualTo(domain);
    }

    @Test
    void ohneDomainNull() {
        assertThat(MailDomain.vonAdresse(null)).isNull();
        assertThat(MailDomain.vonAdresse("keine-adresse")).isNull();
        assertThat(MailDomain.vonAdresse("max@")).isNull();
        assertThat(MailDomain.vonAdresse("max@.")).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "mail.musterfirma.de, musterfirma.de",
            "shop.musterfirma.co.uk, musterfirma.co.uk",
            "amt.gv.at, amt.gv.at",
            "musterfirma.de, musterfirma.de",
            "localhost, localhost"
    })
    void hauptDomain(String domain, String haupt) {
        assertThat(MailDomain.hauptDomain(domain)).isEqualTo(haupt);
    }

    @Test
    void hauptDomainVonNullIstNull() {
        assertThat(MailDomain.hauptDomain(null)).isNull();
    }

    @Test
    void gleicheOrganisation() {
        assertThat(MailDomain.gleicheOrganisation("mail.musterfirma.de", "musterfirma.de")).isTrue();
        assertThat(MailDomain.gleicheOrganisation("musterfirma.de", "phish.example")).isFalse();
        assertThat(MailDomain.gleicheOrganisation(null, "musterfirma.de")).isFalse();
        assertThat(MailDomain.gleicheOrganisation("musterfirma.de", " ")).isFalse();
    }

    @Test
    void gehoertZu() {
        assertThat(MailDomain.gehoertZu("Mail.PayPal.de", "paypal.de")).isTrue();
        assertThat(MailDomain.gehoertZu("paypal.de", "paypal.de")).isTrue();
        assertThat(MailDomain.gehoertZu("paypal.de.phish.example", "paypal.de")).isFalse();
        assertThat(MailDomain.gehoertZu("fakepaypal.de", "paypal.de")).isFalse();
        assertThat(MailDomain.gehoertZu(null, "paypal.de")).isFalse();
        assertThat(MailDomain.gehoertZu("paypal.de", null)).isFalse();
    }
}
