package org.example.kalkulationsprogramm.service.spam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;

import org.example.kalkulationsprogramm.domain.Email;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Gefälschte Absender am Anzeigenamen. Ein Fehlalarm ist schlimmer als ein durchgelassener
 * Spam – deshalb viele Gegenproben mit echten Absendern. Nur Dummy-Daten.
 */
class AbsenderFaelschungErkennungTest {

    @Nested
    class Marken {

        @ParameterizedTest
        @CsvSource({
                "PayPal, service@paypal.de",
                "PayPal, service@mail.paypal.de",
                "PayPal Deutschland, noreply@paypal.com",
                "Sparkasse Mainfranken, info@sparkasse-mainfranken.de",
                "Stadtsparkasse München Sparkasse, info@sskm.de",
                "Volksbank Musterstadt, info@vr.de",
                "Raiffeisenbank Musterdorf, service@raiffeisenbank-musterdorf.de",
                "Deutsche Bank, kundenservice@db.com",
                "Amazon.de, versand-bestaetigung@amazon.de",
                "Max Mustermann über WeTransfer, noreply@wetransfer.com"
        })
        void echteMarkenAbsenderSindNichtGefaelscht(String name, String absender) {
            assertThat(AbsenderFaelschungErkennung.istGefaelscht(name, absender, null)).isFalse();
        }

        @ParameterizedTest
        @CsvSource({
                "PayPal, service@paypal-sicherheit.example",
                "PayPal Kundenservice, max.mustermann@gmail.com",
                "Sparkasse, info@kundenportal.example",
                "Deutsche Bank, info@deutsche-bank.example",
                "DHL Paket, zustellung@paket-info.example",
                "Microsoft 365, admin@konto-pruefung.example"
        })
        void markeVonFremderDomainIstGefaelscht(String name, String absender) {
            assertThat(AbsenderFaelschungErkennung.istGefaelscht(name, absender, null)).isTrue();
        }

        @Test
        void markeNurAlsTeilEinesWortesZaehltNicht() {
            assertThat(AbsenderFaelschungErkennung.istGefaelscht("Paypalmer Bau", "info@paypalmer-bau.example", null))
                    .isFalse();
        }

        @Test
        void diplomIngenieurIstKeineBank() {
            assertThat(AbsenderFaelschungErkennung.istGefaelscht("Dipl.-Ing. Max Mustermann",
                    "max@mustermann-statik.example", null)).isFalse();
        }

        @Test
        void grossKleinschreibungUndMehrfacheLeerzeichenSindEgal() {
            assertThat(AbsenderFaelschungErkennung.istGefaelscht("DEUTSCHE   BANK", "x@phish.example", null)).isTrue();
        }
    }

    @Nested
    class AdresseImNamen {

        @Test
        void fremdeAdresseImNamenIstGefaelscht() {
            assertThat(AbsenderFaelschungErkennung.istGefaelscht("info@musterbank.example",
                    "versand@billig-mailer.example", null)).isTrue();
        }

        @Test
        void adresseDerselbenFirmaImNamenIstEcht() {
            assertThat(AbsenderFaelschungErkennung.istGefaelscht("Max Mustermann (max@mail.musterfirma.de)",
                    "info@musterfirma.de", null)).isFalse();
        }

        @Test
        void kontaktformularMitKundenadresseInNameUndReplyToIstEcht() {
            assertThat(AbsenderFaelschungErkennung.istGefaelscht("max.mustermann@web.de",
                    "formular@musterfirma.de", "Max.Mustermann@web.de")).isFalse();
        }

        @Test
        void noreplyAbsenderMitReplyToGleichAdresseImNamenIstEcht() {
            assertThat(AbsenderFaelschungErkennung.istGefaelscht("Anfrage von max.mustermann@gmx.de",
                    "noreply@website-hoster.example", "max.mustermann@gmx.de")).isFalse();
        }

        @Test
        void replyToAufAndereAdresseAlsImNamenIstGefaelscht() {
            assertThat(AbsenderFaelschungErkennung.istGefaelscht("info@musterbank.example",
                    "noreply@website-hoster.example", "zahlung@phish.example")).isTrue();
        }

        @Test
        void unvollstaendigeAdressenImNamenZaehlenNicht() {
            assertThat(AbsenderFaelschungErkennung.adressenImNamen("max@localhost, @web.de, a@b@c.de, info@firma.de."))
                    .containsExactly("info@firma.de");
        }

        @Test
        void nameOhneAtLiefertKeineAdressen() {
            assertThat(AbsenderFaelschungErkennung.adressenImNamen("Max Mustermann")).isEmpty();
            assertThat(AbsenderFaelschungErkennung.adressenImNamen(null)).isEmpty();
        }
    }

    @Nested
    class Grenzfaelle {

        @Test
        void nullWerteSindNieGefaelscht() {
            assertThat(AbsenderFaelschungErkennung.istGefaelscht(null, "info@firma.de", null)).isFalse();
            assertThat(AbsenderFaelschungErkennung.istGefaelscht("PayPal", null, null)).isFalse();
            assertThat(AbsenderFaelschungErkennung.istGefaelscht("   ", "info@firma.de", null)).isFalse();
            assertThat(AbsenderFaelschungErkennung.istGefaelscht("PayPal", "ohne-at-zeichen", null)).isFalse();
            assertThat(AbsenderFaelschungErkennung.istGefaelscht((Email) null)).isFalse();
        }

        @Test
        void liestFelderAusDerMail() {
            Email email = new Email();
            email.setFromName("PayPal");
            email.setFromAddress("service@paypal-sicherheit.example");
            assertThat(AbsenderFaelschungErkennung.istGefaelscht(email)).isTrue();
        }

        @Test
        void zehntausendZeichenLangerNameIstSchnellGeprueft() {
            String langerName = "a@b.".repeat(2_500);
            String langeMarke = "PayPal " + "x".repeat(10_000);

            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
                assertThat(AbsenderFaelschungErkennung.istGefaelscht(langerName, "info@firma.de", null)).isFalse();
                assertThat(AbsenderFaelschungErkennung.istGefaelscht(langeMarke, "info@firma.de", null)).isTrue();
            });
        }
    }
}
