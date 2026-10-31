package org.example.kalkulationsprogramm.service.spam;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Signalgruppen der Spam-Erkennung. Nur Dummy-Daten.
 */
class SpamSignaleTest {

    private static Email mail(String von) {
        Email email = new Email();
        email.setFromAddress(von);
        email.setSubject("Anfrage Treppengeländer");
        email.setBody("Guten Tag, können Sie mir ein Angebot machen? Gruß Max Mustermann");
        email.setSentAt(LocalDateTime.of(2026, 10, 31, 9, 0));
        email.setAttachments(new ArrayList<>());
        return email;
    }

    private static EmailAttachment anhang(String name) {
        EmailAttachment a = new EmailAttachment();
        a.setOriginalFilename(name);
        return a;
    }

    @Nested
    class Gruppen {

        @Test
        void freeMailAbsenderAlleinIstKeinSignal() {
            Email email = mail("max.mustermann@gmail.com");
            email.setBody("Hallo, hier Ideen: https://a.example/1 https://b.example/2 https://c.example/3");

            assertThat(SpamSignale.erkenne(email)).isEmpty();
        }

        @Test
        void nullMailHatKeineSignale() {
            assertThat(SpamSignale.erkenne(null)).isEmpty();
        }

        @Test
        void eineGruppe() {
            Email email = mail("info@musterfirma.example");
            email.setSentAt(null);

            assertThat(SpamSignale.erkenne(email)).containsExactly(SpamSignal.DATUM_FEHLT);
        }

        @Test
        void zweiGruppen() {
            Email email = mail("info@musterfirma.example");
            email.setAnbieterSpamFlag(true);
            email.setAuthenticationResults("mx.example.net; spf=pass; dkim=pass; DMARC=fail header.from=musterfirma.example");

            assertThat(SpamSignale.erkenne(email))
                    .containsExactly(SpamSignal.ECHTHEIT_FEHLGESCHLAGEN, SpamSignal.ANBIETER_SPAM);
        }

        @Test
        void dreiGruppenZaehlenJeEinmal() {
            Email email = mail("service@paypal-sicherheit.example");
            email.setFromName("PayPal");
            email.setReplyToAddress("zahlung@phish.example");
            email.getAttachments().add(anhang("rechnung.exe"));
            email.getAttachments().add(anhang("lieferschein.js"));

            assertThat(SpamSignale.erkenne(email)).containsExactly(
                    SpamSignal.ABSENDER_GEFAELSCHT, SpamSignal.ANTWORT_UMGELEITET, SpamSignal.GEFAEHRLICHER_ANHANG);
        }

        @Test
        void anbieterFlagNeinOderUnbekanntIstKeinSignal() {
            Email nein = mail("info@musterfirma.example");
            nein.setAnbieterSpamFlag(false);
            Email unbekannt = mail("info@musterfirma.example");

            assertThat(SpamSignale.erkenne(nein)).isEmpty();
            assertThat(SpamSignale.erkenne(unbekannt)).isEmpty();
        }

        @Test
        void nurSpfOderDkimFehlerIstKeinSignal() {
            Email email = mail("info@musterfirma.example");
            email.setAuthenticationResults("mx.example.net; spf=softfail; dkim=fail");

            assertThat(SpamSignale.erkenne(email)).isEmpty();
        }
    }

    @Nested
    class AntwortUmgeleitet {

        @Test
        void andereHauptDomainIstUmgeleitet() {
            Email email = mail("noreply@musterfirma.example");
            email.setReplyToAddress("zahlung@phish.example");

            assertThat(SpamSignale.antwortUmgeleitet(email)).isTrue();
        }

        @Test
        void subdomainDerselbenFirmaIstNichtUmgeleitet() {
            Email email = mail("noreply@musterfirma.de");
            email.setReplyToAddress("info@mail.musterfirma.de");

            assertThat(SpamSignale.antwortUmgeleitet(email)).isFalse();
        }

        @Test
        void kontaktformularIstNichtUmgeleitet() {
            Email email = mail("noreply@website-hoster.example");
            email.setFromName("Max Mustermann <max.mustermann@web.de>");
            email.setReplyToAddress("max.mustermann@web.de");

            assertThat(SpamSignale.antwortUmgeleitet(email)).isFalse();
        }

        @Test
        void fehlendeOderKaputteAdressenSindNichtUmgeleitet() {
            Email ohneReplyTo = mail("info@musterfirma.example");
            Email kaputt = mail("info@musterfirma.example");
            kaputt.setReplyToAddress("keine-adresse");
            Email ohneAbsender = mail(null);
            ohneAbsender.setReplyToAddress("zahlung@phish.example");

            assertThat(SpamSignale.antwortUmgeleitet(ohneReplyTo)).isFalse();
            assertThat(SpamSignale.antwortUmgeleitet(kaputt)).isFalse();
            assertThat(SpamSignale.antwortUmgeleitet(ohneAbsender)).isFalse();
        }
    }

    @Nested
    class GefaehrlicherAnhang {

        @ParameterizedTest
        @ValueSource(strings = {"Rechnung.PDF.EXE", "makro.docm", "verknuepfung.lnk", "abbild.iso"})
        void erkenntGefaehrlicheEndungen(String name) {
            Email email = mail("info@musterfirma.example");
            email.getAttachments().add(anhang(name));

            assertThat(SpamSignale.hatGefaehrlichenAnhang(email)).isTrue();
        }

        @Test
        void harmloseOderNamenloseAnhaengeZaehlenNicht() {
            Email email = mail("info@musterfirma.example");
            email.getAttachments().addAll(List.of(anhang("foto.jpg"), anhang(".exe"), anhang(null), anhang("ohneendung")));

            assertThat(SpamSignale.hatGefaehrlichenAnhang(email)).isFalse();
        }

        @Test
        void leererListeneintragWirdUebersprungen() {
            Email email = mail("info@musterfirma.example");
            email.getAttachments().add(null);
            email.getAttachments().add(anhang("start.exe"));

            assertThat(SpamSignale.hatGefaehrlichenAnhang(email)).isTrue();
        }

        @Test
        void ohneAnhangListeKeinSignal() {
            Email email = mail("info@musterfirma.example");
            email.setAttachments(null);

            assertThat(SpamSignale.hatGefaehrlichenAnhang(email)).isFalse();
        }
    }

    @Nested
    class NurBildMitLink {

        @Test
        void bildMitLinkOhneTextIstSignal() {
            assertThat(SpamSignale.nurBildMitLink(
                    "<a href=\"https://promo.example/klick\"><IMG src=\"https://cdn.example/b.jpg\"></a>")).isTrue();
        }

        @Test
        void bildMitLinkUndEchtemTextIstKeinSignal() {
            String html = "<img src=\"cid:logo\"><p>Sehr geehrter Herr Mustermann, anbei unser Angebot für das "
                    + "Treppengeländer aus Edelstahl. Bei Fragen rufen Sie gerne an. Viele Grüße</p>"
                    + "<a href='https://www.musterfirma.example'>www.musterfirma.example</a>";

            assertThat(SpamSignale.nurBildMitLink(html)).isFalse();
        }

        @Test
        void handwerkerFotoOhneLinkIstKeinSignal() {
            assertThat(SpamSignale.nurBildMitLink("<p>Foto</p><img src=\"cid:bild1\">")).isFalse();
        }

        @Test
        void linkOhneBildIstKeinSignal() {
            assertThat(SpamSignale.nurBildMitLink("<a href=\"https://promo.example\">Klick</a>")).isFalse();
        }

        @Test
        void leeresHtmlIstKeinSignal() {
            assertThat(SpamSignale.nurBildMitLink(null)).isFalse();
            assertThat(SpamSignale.nurBildMitLink("  ")).isFalse();
        }

        @Test
        void erkenneNutztHtmlTeil() {
            Email email = mail("info@musterfirma.example");
            email.setHtmlBody("<a href=https://promo.example><img src=x></a>");

            assertThat(SpamSignale.erkenne(email)).containsExactly(SpamSignal.NUR_BILD_MIT_LINK);
        }
    }
}
