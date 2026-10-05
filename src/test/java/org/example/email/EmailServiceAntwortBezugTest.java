package org.example.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

class EmailServiceAntwortBezugTest {

    private final MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));

    @Test
    void schreibtInReplyToUndReferencesOhneDoppelteIds() throws Exception {
        new EmailService.AntwortBezug("<c@example.com>", List.of("<a@example.com>", " <b@example.com> ", "", "<c@example.com>"))
                .schreibeIn(message);

        assertThat(message.getHeader("In-Reply-To", null)).isEqualTo("<c@example.com>");
        assertThat(message.getHeader("References", null)).isEqualTo("<a@example.com> <b@example.com> <c@example.com>");
    }

    @Test
    void leererBezugSchreibtKeineKopfzeilen() throws Exception {
        new EmailService.AntwortBezug(" ", null).schreibeIn(message);
        new EmailService.AntwortBezug(null, List.of("<a@example.com>")).schreibeIn(message);

        assertThat(message.getHeader("In-Reply-To")).isNull();
        assertThat(message.getHeader("References")).isNull();
    }

    @Test
    void referencesOhneVorgaengerEnthaeltNurDieBeantworteteMail() throws Exception {
        new EmailService.AntwortBezug("<c@example.com>", null).schreibeIn(message);
        assertThat(message.getHeader("References", null)).isEqualTo("<c@example.com>");
    }

    @Test
    void adresslisteAkzeptiertSemikolonUndKomma() throws Exception {
        InternetAddress[] adressen = EmailService.parseAdressliste("a@example.com; \"Mustermann, Max\" <max@example.com>,b@example.com");
        assertThat(adressen).extracting(InternetAddress::getAddress)
                .containsExactly("a@example.com", "max@example.com", "b@example.com");
    }

    @Test
    void verwirftManipulierteUndErfundeneMessageIds() throws Exception {
        new EmailService.AntwortBezug("<c@example.com>\r\nBcc: angreifer@example.com",
                List.of("<a@example.com>")).schreibeIn(message);
        assertThat(message.getHeader("In-Reply-To")).isNull();

        new EmailService.AntwortBezug("<c@example.com>",
                List.of("<a@example.com>\r\nBcc: angreifer@example.com", "<no-msgid-uid-5@INBOX>", "<b c@example.com>"))
                .schreibeIn(message);
        assertThat(message.getHeader("References", null)).isEqualTo("<c@example.com>");
        assertThat(new EmailService.AntwortBezug("<no-msgid-uid-5@INBOX>", null).istLeer()).isTrue();
    }

    @Test
    void semikolonInAnfuehrungszeichenTrenntNicht() throws Exception {
        InternetAddress[] adressen = EmailService.parseAdressliste("\"Muster; GmbH\" <info@example.com>; max@example.com");
        assertThat(adressen).extracting(InternetAddress::getAddress).containsExactly("info@example.com", "max@example.com");
        assertThat(adressen[0].getPersonal()).isEqualTo("Muster; GmbH");
    }

    @Test
    void langeReferencesWerdenUmbrochen() throws Exception {
        List<String> kette = java.util.stream.IntStream.range(0, 19)
                .mapToObj(i -> "<" + "x".repeat(60) + i + "@example.com>").toList();
        new EmailService.AntwortBezug("<c@example.com>", kette).schreibeIn(message);
        String references = message.getHeader("References", null);
        assertThat(references.lines()).allMatch(zeile -> zeile.length() <= 998);
        assertThat(jakarta.mail.internet.MimeUtility.unfold(references).split(" ")).hasSize(20);
    }
}
