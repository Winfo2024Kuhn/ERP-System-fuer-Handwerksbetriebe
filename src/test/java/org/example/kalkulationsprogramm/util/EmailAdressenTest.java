package org.example.kalkulationsprogramm.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Reine Unit-Tests für {@link EmailAdressen#ersteAdresse(String)}.
 * Kein Spring-Context, damit die ReDoS-Stress-Assertion zuverlässig in Millisekunden misst.
 *
 * <p>Hintergrund: Das zugrunde liegende Pattern wurde nach CodeQL-Alert
 * {@code java/polynomial-redos} auf possessive Quantoren umgestellt. Diese Tests
 * sichern beide Seiten ab — Korrektheit für gültige Header und Backtracking-Freiheit
 * für adversariale Eingaben.
 */
@DisplayName("EmailAdressen")
class EmailAdressenTest {

    @Test
    @DisplayName("null-Eingabe → null")
    void nullInput_returnsNull() {
        assertThat(EmailAdressen.ersteAdresse(null)).isNull();
    }

    @Test
    @DisplayName("Leere/whitespace-Eingabe → null")
    void blankInput_returnsNull() {
        assertThat(EmailAdressen.ersteAdresse("")).isNull();
        assertThat(EmailAdressen.ersteAdresse("   \t \n")).isNull();
    }

    @Test
    @DisplayName("Reine E-Mail-Adresse → unverändert zurück")
    void plainAddress_returnsItself() {
        assertThat(EmailAdressen.ersteAdresse("max@example.com"))
                .isEqualTo("max@example.com");
    }

    @Test
    @DisplayName("Display-Name mit Adresse in spitzen Klammern → nur Adresse")
    void displayNameAndAddress_returnsAddressOnly() {
        assertThat(EmailAdressen.ersteAdresse(
                "\"Max Mustermann\" <max@example.com>"))
                .isEqualTo("max@example.com");
    }

    @Test
    @DisplayName("Mehrere Adressen → erste gewinnt")
    void multipleAddresses_returnsFirst() {
        assertThat(EmailAdressen.ersteAdresse(
                "first@example.com, second@example.com, third@example.com"))
                .isEqualTo("first@example.com");
    }

    @Test
    @DisplayName("Subdomains und mehrteilige TLDs werden korrekt erkannt")
    void subdomainAndMultiPartTld() {
        assertThat(EmailAdressen.ersteAdresse(
                "foo.bar@sub.example.co.uk"))
                .isEqualTo("foo.bar@sub.example.co.uk");
    }

    @Test
    @DisplayName("Punkt, Plus und Bindestrich im Local-Part werden gematcht")
    void specialCharsInLocalPart() {
        assertThat(EmailAdressen.ersteAdresse(
                "user.name+tag-1@my-host.example.org"))
                .isEqualTo("user.name+tag-1@my-host.example.org");
    }

    @Test
    @DisplayName("Eingehende-Mail-Header mit Sammeladresse → Adresse extrahiert")
    void groupAddressHeader_returnsAddress() {
        assertThat(EmailAdressen.ersteAdresse(
                "info@gemeinde.example.org"))
                .isEqualTo("info@gemeinde.example.org");
    }

    @Test
    @DisplayName("Header ohne gültige Adresse → null")
    void noEmailFound_returnsNull() {
        assertThat(EmailAdressen.ersteAdresse("kein-treffer-hier"))
                .isNull();
        assertThat(EmailAdressen.ersteAdresse("only @ sign no domain"))
                .isNull();
    }

    @Test
    @DisplayName("Adversariale Eingabe (3.000 % gefolgt von Adresse) läuft in <500ms (kein ReDoS)")
    void adversarialInput_isLinear() {
        // 3000 < EMAIL_HEADER_MAX_LEN (4096): nicht abgeschnitten, das `@example.com` ist Teil des Inputs.
        String adversarial = "%".repeat(3_000) + "@example.com";
        assertTimeoutPreemptively(Duration.ofMillis(500), () -> {
            String result = EmailAdressen.ersteAdresse(adversarial);
            assertThat(result).isNotNull().endsWith("@example.com");
        });
    }

    @Test
    @DisplayName("Adversariale Eingabe ohne @ (10k Zeichen) läuft in <500ms (kein polynomiales Backtracking)")
    void adversarialInputWithoutAt_isLinear() {
        // Klassischer ReDoS-Auslöser für das alte Pattern: viele % ohne `@` → ohne possessive
        // Quantoren würde der Engine an jeder Startposition den ++ greedy probieren und scheitern.
        String adversarial = "%".repeat(10_000);
        assertTimeoutPreemptively(Duration.ofMillis(500), () ->
                assertThat(EmailAdressen.ersteAdresse(adversarial)).isNull());
    }

    @Test
    @DisplayName("Über-langer Header (> 4096 Zeichen) wird abgeschnitten und liefert keine Exception")
    void overlongHeader_truncatedSafely() {
        String longPrefix = "x".repeat(5_000);
        String header = longPrefix + " <admin@example.com>";
        assertTimeoutPreemptively(Duration.ofMillis(500), () ->
                EmailAdressen.ersteAdresse(header));
    }

    @Test
    @DisplayName("domainVon: Domain der ersten Adresse, klein geschrieben")
    void domainVon() {
        assertThat(EmailAdressen.domainVon("Erika Musterfrau <Erika@Kanzlei.Example.ORG>")).isEqualTo("kanzlei.example.org");
        assertThat(EmailAdressen.domainVon("keine adresse")).isNull();
        assertThat(EmailAdressen.domainVon(null)).isNull();
    }

    @Test
    @DisplayName("hatAdresseAusDomain: genau die Domain, keine Subdomain und keine längere Domain")
    void hatAdresseAusDomain() {
        assertThat(EmailAdressen.hatAdresseAusDomain("a@example.org, Max <max@KANZLEI.Example.org>", "kanzlei.example.org")).isTrue();
        assertThat(EmailAdressen.hatAdresseAusDomain("max@kanzlei.example.org.example.net", "kanzlei.example.org")).isFalse();
        assertThat(EmailAdressen.hatAdresseAusDomain("max@sub.kanzlei.example.org", "kanzlei.example.org")).isFalse();
        assertThat(EmailAdressen.hatAdresseAusDomain("", "kanzlei.example.org")).isFalse();
        assertThat(EmailAdressen.hatAdresseAusDomain(null, "kanzlei.example.org")).isFalse();
    }
}
