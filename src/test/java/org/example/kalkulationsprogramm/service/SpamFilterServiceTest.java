package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.example.kalkulationsprogramm.domain.EmailZuordnungTyp;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.repository.EmailBlacklistRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpamFilterServiceTest {

    @Mock private LieferantenRepository lieferantenRepository;
    @Mock private EmailBlacklistRepository emailBlacklistRepository;

    private SpamFilterService service;

    @BeforeEach
    void setUp() {
        service = new SpamFilterService(lieferantenRepository, emailBlacklistRepository);
    }

    private Email erstelleEmail(String fromAddress, String subject, String body) {
        Email email = new Email();
        email.setFromAddress(fromAddress);
        email.setSubject(subject);
        email.setBody(body);
        email.setZuordnungTyp(EmailZuordnungTyp.KEINE);
        email.setAttachments(new ArrayList<>());
        if (fromAddress != null && fromAddress.contains("@")) {
            email.setSenderDomain(fromAddress.substring(fromAddress.lastIndexOf('@') + 1).toLowerCase());
        }
        return email;
    }

    private EmailAttachment erstelleAttachment(String filename, boolean inline) {
        EmailAttachment att = new EmailAttachment();
        att.setOriginalFilename(filename);
        att.setInlineAttachment(inline);
        return att;
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.1.1 Erkennt Spam anhand von Spam-Keywords (Score > Schwelle)
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class SpamKeywordErkennung {

        @Test
        void erkenntSpamAnhandVonKeywords() {
            Email email = erstelleEmail("spammer@unknown.com", "You Won the Lottery!", "Congratulations you won a free gift");

            int score = service.calculateSpamScore(email);

            // "lottery" (35) + "congratulations you won" (40) + "free gift" (25) = 100
            assertThat(score).isGreaterThanOrEqualTo(50);
        }

        @Test
        void erkenntPharmaSpam() {
            Email email = erstelleEmail("pills@spamsite.com", "Buy Viagra now!", "Best pharmacy deals on cialis");

            int score = service.calculateSpamScore(email);

            // "viagra" (50) + "pharmacy" (30) + "cialis" (50) -> weit über Schwelle
            assertThat(score).isGreaterThanOrEqualTo(50);
        }

        @Test
        void normaleGeschaeftsEmailIstKeinSpam() {
            Email email = erstelleEmail("info@handwerker.de", "Anfrage für Dachsanierung", "Anbei unser Anfrage für die geplante Dachsanierung.");

            int score = service.calculateSpamScore(email);

            assertThat(score).isLessThan(50);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.1.2 Lieferanten-Domains werden gewhitelistet
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class LieferantenWhitelist {

        @Test
        void lieferantenDomainWirdGewhitelistet() {
            Email email = erstelleEmail("noreply@wuerth.com", "DRINGENDES ANFRAGE", "Act now! Limited time offer!");

            when(lieferantenRepository.findByEmail("noreply@wuerth.com")).thenReturn(Optional.empty());
            when(lieferantenRepository.existsByEmailDomain("wuerth.com")).thenReturn(true);

            int score = service.calculateSpamScore(email);

            assertThat(score).isEqualTo(0);
        }

        @Test
        void lieferantenExacterEmailMatchWirdGewhitelistet() {
            Email email = erstelleEmail("rechnung@lieferant.de", "Rechnung RE-2025-001", "Siehe Anhang");

            when(lieferantenRepository.findByEmail("rechnung@lieferant.de")).thenReturn(Optional.of(new Lieferanten()));

            int score = service.calculateSpamScore(email);

            assertThat(score).isEqualTo(0);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.1.3 E-Mails mit Zuordnung sind nie Spam
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class ZugeordneteEmails {

        @Test
        void emailMitProjektZuordnungIstNieSpam() {
            Email email = erstelleEmail("spam@evil.com", "Buy Viagra Casino Lottery!", "Congratulations you won!");
            email.setZuordnungTyp(EmailZuordnungTyp.PROJEKT);
            email.setProjekt(new Projekt());

            service.analyzeAndMarkSpam(email);

            assertThat(email.isSpam()).isFalse();
            assertThat(email.isNewsletter()).isFalse();
            assertThat(email.getSpamScore()).isEqualTo(0);
        }

        @Test
        void emailMitLieferantZuordnungIstNieSpam() {
            Email email = erstelleEmail("news@supplier.com", "Newsletter: Neue Produkte", "Unsubscribe here");
            email.setZuordnungTyp(EmailZuordnungTyp.LIEFERANT);
            email.setLieferant(new Lieferanten());

            service.analyzeAndMarkSpam(email);

            assertThat(email.isSpam()).isFalse();
            assertThat(email.isNewsletter()).isFalse();
            assertThat(email.getSpamScore()).isEqualTo(0);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.1.4 Erkennt Newsletter anhand von Keywords
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class NewsletterErkennung {

        @Test
        void erkenntNewsletterAnhandListUnsubscribeKeyword() {
            Email email = erstelleEmail("info@newsletter-portal.com", "Neuigkeiten im März", "Hier abmelden");

            service.analyzeAndMarkSpam(email);

            assertThat(email.isNewsletter()).isTrue();
        }

        @Test
        void erkenntNewsletterAnhandSenderKeyword() {
            Email email = erstelleEmail("newsletter@firma.de", "Updates", "Normale Inhalte ohne Newsletter-Keywords");

            service.analyzeAndMarkSpam(email);

            assertThat(email.isNewsletter()).isTrue();
        }

        @Test
        void lieferantenNewsletterWirdNichtAlsNewsletterMarkiert() {
            Email email = erstelleEmail("newsletter@lieferant.de", "Newsletter: Neuigkeiten", "Abbestellen");

            when(lieferantenRepository.findByEmail("newsletter@lieferant.de")).thenReturn(Optional.empty());
            when(lieferantenRepository.existsByEmailDomain("lieferant.de")).thenReturn(true);

            service.analyzeAndMarkSpam(email);

            assertThat(email.isNewsletter()).isFalse();
            assertThat(email.isSpam()).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.1.5 ALL-CAPS-Betreff erhöht Spam-Score
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class AllCapsBetreff {

        @Test
        void allCapsBetreffErhoehtScore() {
            Email email = erstelleEmail("someone@unknown.com", "KAUFEN SIE JETZT DIESE PRODUKTE", "Normaler Body-Text");

            int score = service.calculateSpamScore(email);

            // ALL-CAPS Score (+20) sollte den Score erhöhen
            assertThat(score).isGreaterThanOrEqualTo(20);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.1.6 Hohe Link-Dichte erhöht Spam-Score (>10 Links)
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class LinkDichte {

        @Test
        void hoheLinkDichteErhoehtScore() {
            StringBuilder bodyMitLinks = new StringBuilder();
            for (int i = 0; i < 12; i++) {
                bodyMitLinks.append("Click here: https://example.com/link").append(i).append(" ");
            }
            Email email = erstelleEmail("promo@unknown.com", "Viele Links", bodyMitLinks.toString());

            int score = service.calculateSpamScore(email);

            // 12 Links > 10 → +15 Score
            assertThat(score).isGreaterThanOrEqualTo(15);
        }

        @Test
        void wenigeLinksErhoehenScoreNicht() {
            Email email = erstelleEmail("partner@firma.de", "Info", "Siehe https://firma.de und https://docs.firma.de");

            int score = service.calculateSpamScore(email);

            // 2 Links → kein Link-Bonus
            assertThat(score).isLessThan(15);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.1.7 noreply@-Absender werden als verdächtig erkannt
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class VerdaechtigeAbsender {

        @Test
        void noreplyAbsenderErhoehtScore() {
            Email email = erstelleEmail("noreply@unknown-service.com", "Info", "Normaler Inhalt");

            int score = service.calculateSpamScore(email);

            // noreply@ → +10 Score
            assertThat(score).isGreaterThanOrEqualTo(10);
        }

        @Test
        void doNotReplyAbsenderErhoehtScore() {
            Email email = erstelleEmail("donotreply@service.com", "Info", "Normaler Inhalt");

            int score = service.calculateSpamScore(email);

            assertThat(score).isGreaterThanOrEqualTo(10);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.1.8 Kombinationstest: Rechnung + PDF bei Lieferant = kein Spam
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class RechnungMitPdfKeinSpam {

        @Test
        void rechnungMitPdfAttachmentIstKeinSpam() {
            Email email = erstelleEmail("info@unknown-firma.de", "Rechnung Nr. 12345", "Anbei Ihre Rechnung");
            EmailAttachment pdfAtt = erstelleAttachment("Rechnung_12345.pdf", false);
            email.getAttachments().add(pdfAtt);

            int score = service.calculateSpamScore(email);

            // Whitelist: Rechnung + PDF Attachment → Score 0
            assertThat(score).isEqualTo(0);
        }

        @Test
        void rechnungMitXmlAttachmentIstKeinSpam() {
            Email email = erstelleEmail("billing@cloud.de", "Ihre Rechnung", "ZUGFeRD-Rechnung");
            EmailAttachment xmlAtt = erstelleAttachment("factur-x.xml", false);
            email.getAttachments().add(xmlAtt);

            int score = service.calculateSpamScore(email);

            assertThat(score).isEqualTo(0);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.1.9 Gefährliche Dateitypen (.exe, .bat) erhöhen Score
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class GefaehrlicheDateitypen {

        @Test
        void exeDateiErhoehtScoreAuf100() {
            Email email = erstelleEmail("unknown@suspicious.com", "Dokument", "Siehe Anhang");
            EmailAttachment exeAtt = erstelleAttachment("rechnung.exe", false);
            email.getAttachments().add(exeAtt);

            int score = service.calculateSpamScore(email);

            assertThat(score).isEqualTo(100);
        }

        @Test
        void batDateiErhoehtScoreAuf100() {
            Email email = erstelleEmail("unknown@suspicious.com", "Dokument", "Siehe Anhang");
            EmailAttachment batAtt = erstelleAttachment("setup.bat", false);
            email.getAttachments().add(batAtt);

            int score = service.calculateSpamScore(email);

            assertThat(score).isEqualTo(100);
        }

        @Test
        void jsDateiErhoehtScoreAuf100() {
            Email email = erstelleEmail("unknown@suspicious.com", "Dokument", "Siehe Anhang");
            EmailAttachment jsAtt = erstelleAttachment("payload.js", false);
            email.getAttachments().add(jsAtt);

            int score = service.calculateSpamScore(email);

            assertThat(score).isEqualTo(100);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.1.10 Domain-Blacklist blockiert bekannte Spam-Domains
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class DomainBlacklist {

        @Test
        void blockedDomainErhoehtScore() {
            Email email = erstelleEmail("user@mailinator.com", "Hello", "Normal content");
            email.setSenderDomain("mailinator.com");

            int score = service.calculateSpamScore(email);

            // Blocked domain → +40; noreply-ähnlich nein, aber domain allein +40
            assertThat(score).isGreaterThanOrEqualTo(40);
        }

        @Test
        void tempMailDomainErhoehtScore() {
            Email email = erstelleEmail("nobody@temp-mail.org", "Test", "Test message");
            email.setSenderDomain("temp-mail.org");

            int score = service.calculateSpamScore(email);

            assertThat(score).isGreaterThanOrEqualTo(40);
        }

        @Test
        void emailBlacklistGibt100() {
            Email email = erstelleEmail("blocked@evil.com", "Hello", "Normal content");

            when(emailBlacklistRepository.existsByEmailAddress("blocked@evil.com")).thenReturn(true);

            int score = service.calculateSpamScore(email);

            assertThat(score).isEqualTo(100);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // isSpam()-Methode
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class IsSpamMethode {

        @Test
        void isSpamGibtTrueWennScoreUeberSchwelle() {
            Email email = erstelleEmail("spam@test.com", "Buy Viagra now!", "Casino jackpot lottery");

            assertThat(service.isSpam(email)).isTrue();
        }

        @Test
        void isSpamGibtFalseBeiNormalerEmail() {
            Email email = erstelleEmail("partner@firma.de", "Projektstatus", "Hier der aktuelle Stand");

            assertThat(service.isSpam(email)).isFalse();
        }
    }
}
