package org.example.kalkulationsprogramm.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.example.kalkulationsprogramm.domain.EmailZuordnungTyp;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.EmailBlacklistRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpamFilterServiceTest {

    @Mock private LieferantenRepository lieferantenRepository;
    @Mock private KundeRepository kundeRepository;
    @Mock private AnfrageRepository anfrageRepository;
    @Mock private ProjektRepository projektRepository;
    @Mock private EmailBlacklistRepository emailBlacklistRepository;
    @Mock private SpamBayesService spamBayesService;

    private SpamFilterService service;

    @BeforeEach
    void setUp() {
        service = new SpamFilterService(lieferantenRepository, kundeRepository, anfrageRepository, projektRepository, emailBlacklistRepository, spamBayesService);
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
    // Image-Spam: Mail besteht fast ausschliesslich aus IMG-Tags
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class ImageSpam {

        @Test
        void bildOnlyMailMitButtonWirdAlsAutoSpamErkannt() {
            // Reproduziert die Beispiel-Mail: kryptischer Sender, Bild als
            // ganze Mail, ein Klick-Button als Link, kein sichtbarer Text.
            Email email = erstelleEmail(
                    "jAWULxW.irYpfHK@adventurecentral.com",
                    "Warum Urlauber dieses Gadget feiern Office32",
                    "");
            email.setHtmlBody(
                    "<html><body>"
                            + "<a href=\"https://promo.example.com/click\">"
                            + "<img src=\"https://cdn.example.com/banner.jpg\" width=\"600\" height=\"400\" />"
                            + "</a>"
                            + "</body></html>");

            int score = service.calculateSpamScore(email);

            // Bild-only +75, Cryptic-Sender (Mixed-Case) +10 -> >= 85
            // (AUTO_SPAM_THRESHOLD), wird automatisch in Spam verschoben.
            assertThat(score).isGreaterThanOrEqualTo(85);
        }

        @Test
        void echteGeschaeftsMailMitLogoAberTextIstKeinSpam() {
            // Gegenprobe: HTML-Mail mit Bild (z.B. Logo), aber substanziellem
            // Text muss NICHT als Spam markiert werden.
            Email email = erstelleEmail("kontakt@example-bauunternehmen.de",
                    "Anfrage Dachsanierung",
                    "");
            email.setHtmlBody(
                    "<html><body>"
                            + "<img src=\"logo.png\" />"
                            + "<p>Sehr geehrte Damen und Herren,</p>"
                            + "<p>wir würden gerne ein Angebot für die Sanierung "
                            + "unseres Daches anfordern. Das Objekt befindet sich "
                            + "in der Musterstraße 1, 12345 Musterstadt. Bitte "
                            + "melden Sie sich, wenn Sie einen Besichtigungstermin "
                            + "vereinbaren möchten.</p>"
                            + "<p>Mit freundlichen Grüßen, Max Mustermann</p>"
                            + "</body></html>");

            int score = service.calculateSpamScore(email);

            assertThat(score).isLessThan(50);
        }

        @Test
        void bildOnlyOhneLinkLoestKeinenBildSpamCheckAus() {
            // Bild ohne klickbaren Link ist nicht das typische Spam-Pattern
            // (eher ein Tracking-Pixel oder dekorativ); Bild-only-Heuristik
            // verlangt mind. einen Link.
            Email email = erstelleEmail("noreply@partner.example", "Update", "");
            email.setHtmlBody("<html><body><img src=\"x.jpg\" /></body></html>");

            int score = service.calculateSpamScore(email);

            assertThat(score).isLessThan(85);
        }

        @Test
        void mixedCaseRandomLocalPartTriggertSenderPattern() {
            // Beweis fuer den Lowercase-Bug-Fix: Mixed-Case-Random-Strings
            // werden gegen den Original-fromAddress getestet, nicht gegen
            // die lowercased Version.
            Email email = erstelleEmail(
                    "jAWULxW.irYpfHK@example.com",
                    "Hallo",
                    "Bitte klicken");

            int score = service.calculateSpamScore(email);

            // Mind. +10 vom Sender-Pattern. Kein Bild-Check, also bleibt
            // der Score klein, aber der Anteil ist nachweisbar > 0.
            assertThat(score).isGreaterThanOrEqualTo(10);
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

    // ═══════════════════════════════════════════════════════════════
    // Ensemble: Regel + Bayes (analyzeAndMarkSpam)
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class EnsembleIntegration {

        @Test
        void nurRegelScoreWennBayesNichtBereit() {
            // Bayes nicht bereit -> Cold-Start, nur Regeln
            when(spamBayesService.isModelReady()).thenReturn(false);

            Email email = erstelleEmail("spammer@test.com", "Buy Viagra now!", "Casino lottery");
            service.analyzeAndMarkSpam(email);

            // Score basiert nur auf Regeln
            assertThat(email.getSpamScore()).isGreaterThan(0);
            assertThat(email.getBayesScore()).isNull();
        }

        @Test
        void ensembleScorerMitBereitomBayesModell() {
            when(spamBayesService.isModelReady()).thenReturn(true);
            when(spamBayesService.tokenize(any(Email.class))).thenReturn(java.util.Set.of("viagra", "casino"));
            when(spamBayesService.predict(any())).thenReturn(0.95); // Bayes sagt 95% Spam

            Email email = erstelleEmail("spammer@test.com", "Buy Viagra now!", "Casino lottery");
            service.analyzeAndMarkSpam(email);

            // Ensemble: ruleScore * 0.4 + bayesScore(95) * 0.6
            assertThat(email.getSpamScore()).isGreaterThan(50);
            assertThat(email.getBayesScore()).isEqualTo(0.95);
        }

        @Test
        void autoSpamAb90Prozent() {
            when(spamBayesService.isModelReady()).thenReturn(true);
            when(spamBayesService.tokenize(any(Email.class))).thenReturn(java.util.Set.of("lottery", "winner", "prize"));
            when(spamBayesService.predict(any())).thenReturn(0.99); // Bayes sagt 99% Spam

            // Blacklisted Sender -> Regel-Score = 100, Ensemble: 100*0.4 + 99*0.6 = 99
            when(emailBlacklistRepository.existsByEmailAddress("blocked@evil.com")).thenReturn(true);
            Email email = erstelleEmail("blocked@evil.com", "You Won the Lottery!", "Congratulations winner prize");
            service.analyzeAndMarkSpam(email);

            // finalScore >= 90 -> automatisch Spam
            assertThat(email.isSpam()).isTrue();
            assertThat(email.getSpamScore()).isGreaterThanOrEqualTo(90);
        }

        @Test
        void keinAutoSpamUnter90Prozent() {
            when(spamBayesService.isModelReady()).thenReturn(true);
            when(spamBayesService.tokenize(any(Email.class))).thenReturn(java.util.Set.of("newsletter", "updates"));
            when(spamBayesService.predict(any())).thenReturn(0.6); // Bayes sagt 60% Spam

            Email email = erstelleEmail("news@shop.de", "Aktuelle Angebote", "Unsere neuesten Angebote");
            service.analyzeAndMarkSpam(email);

            // Score unter 90 -> kein Auto-Spam
            assertThat(email.isSpam()).isFalse();
        }

        @Test
        void zugeordneteEmailNichtAlsSpamMarkiert() {
            Email email = erstelleEmail("spam@evil.com", "Buy Viagra!", "Casino");
            email.setProjekt(new Projekt());

            service.analyzeAndMarkSpam(email);

            assertThat(email.isSpam()).isFalse();
            assertThat(email.getSpamScore()).isEqualTo(0);
        }

        @Test
        void lieferantenEmailNichtAlsSpamMarkiert() {
            Email email = erstelleEmail("noreply@wuerth.com", "DRINGEND Angebot!", "Act now!");
            when(lieferantenRepository.findByEmail("noreply@wuerth.com")).thenReturn(Optional.empty());
            when(lieferantenRepository.existsByEmailDomain("wuerth.com")).thenReturn(true);

            service.analyzeAndMarkSpam(email);

            assertThat(email.isSpam()).isFalse();
            assertThat(email.getSpamScore()).isEqualTo(0);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Bekannte Kunden-Emails werden nicht als Spam markiert
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class BekannteKundenEmails {

        @Test
        void emailVonKundeWirdNichtAlsSpamMarkiert() {
            Email email = erstelleEmail("kunde@example.com", "DRINGEND Angebot!", "Act now! Limited time offer!");
            when(kundeRepository.existsByKundenEmail("kunde@example.com")).thenReturn(true);

            service.analyzeAndMarkSpam(email);

            assertThat(email.isSpam()).isFalse();
            assertThat(email.isNewsletter()).isFalse();
            assertThat(email.getSpamScore()).isEqualTo(0);
        }

        @Test
        void emailVonProjektKundeWirdNichtAlsSpamMarkiert() {
            Email email = erstelleEmail("projekt-kunde@example.com", "Casino Jackpot!", "Lottery winner");
            when(kundeRepository.existsByKundenEmail("projekt-kunde@example.com")).thenReturn(false);
            when(projektRepository.findByKundenEmail("projekt-kunde@example.com")).thenReturn(List.of(new Projekt()));

            service.analyzeAndMarkSpam(email);

            assertThat(email.isSpam()).isFalse();
            assertThat(email.isNewsletter()).isFalse();
            assertThat(email.getSpamScore()).isEqualTo(0);
        }

        @Test
        void emailVonAnfrageKundeWirdNichtAlsSpamMarkiert() {
            Email email = erstelleEmail("anfrage-kunde@example.com", "Buy Viagra Casino!", "Free gift lottery");
            when(kundeRepository.existsByKundenEmail("anfrage-kunde@example.com")).thenReturn(false);
            when(projektRepository.findByKundenEmail("anfrage-kunde@example.com")).thenReturn(List.of());
            when(anfrageRepository.findByKundenEmail("anfrage-kunde@example.com")).thenReturn(List.of(new org.example.kalkulationsprogramm.domain.Anfrage()));

            service.analyzeAndMarkSpam(email);

            assertThat(email.isSpam()).isFalse();
            assertThat(email.isNewsletter()).isFalse();
            assertThat(email.getSpamScore()).isEqualTo(0);
        }

        @Test
        void emailVonUnbekanntemAbsenderBleibtSpam() {
            Email email = erstelleEmail("unknown@spam.com", "Buy Viagra Casino!", "Free gift lottery");
            when(kundeRepository.existsByKundenEmail("unknown@spam.com")).thenReturn(false);
            when(projektRepository.findByKundenEmail("unknown@spam.com")).thenReturn(List.of());
            when(anfrageRepository.findByKundenEmail("unknown@spam.com")).thenReturn(List.of());

            service.analyzeAndMarkSpam(email);

            assertThat(email.getSpamScore()).isGreaterThan(0);
        }

        @Test
        void emailMitNameKlammerFormatWirdErkannt() {
            Email email = erstelleEmail("Max Mustermann <kunde@example.com>", "DRINGEND!", "Act now!");
            email.setSenderDomain("example.com");
            when(kundeRepository.existsByKundenEmail("kunde@example.com")).thenReturn(true);

            service.analyzeAndMarkSpam(email);

            assertThat(email.isSpam()).isFalse();
            assertThat(email.getSpamScore()).isEqualTo(0);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Strukturelle / Header-Features (Issue #56, Phase 1)
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class StrukturelleFeatures {

        @Test
        void spfFailErhoehtSpamScore() {
            Email email = erstelleEmail("info@firma-mustermann.de", "Angebot", "Hallo, kurze Anfrage.");
            email.setAuthenticationResults("mx.example.com; spf=fail smtp.mailfrom=firma-mustermann.de");

            int score = service.calculateSpamScore(email);

            assertThat(score).isGreaterThanOrEqualTo(25);
        }

        @Test
        void dkimUndDmarcFailKombinierenSichZuHohemScore() {
            Email email = erstelleEmail("ceo@beispielfirma.de", "Re: Rechnung", "Bitte überweisen.");
            email.setAuthenticationResults("mx.example.com; dkim=fail (signature did not verify); dmarc=fail");

            int score = service.calculateSpamScore(email);

            assertThat(score).isGreaterThanOrEqualTo(55);
        }

        @Test
        void replyToAufAndererDomainIstVerdaechtig() {
            Email email = erstelleEmail("noreply@firma-original.de", "Konto", "Bitte einloggen.");
            email.setReplyToAddress("payments@phisher-xyz.tk");

            int score = service.calculateSpamScore(email);

            assertThat(score).isGreaterThanOrEqualTo(25);
        }

        @Test
        void replyToAufSubdomainDerselbenFirmaIstNichtVerdaechtig() {
            Email email = erstelleEmail("noreply@firma.de", "Newsletter", "Hallo.");
            email.setReplyToAddress("info@mail.firma.de");

            int scoreOhne = service.calculateSpamScore(erstelleEmail("noreply@firma.de", "Newsletter", "Hallo."));
            int scoreMit = service.calculateSpamScore(email);

            // Beide gleich — kein Mismatch-Aufschlag
            assertThat(scoreMit).isEqualTo(scoreOhne);
        }

        @Test
        void freeMailerMitVielenLinksIstVerdaechtig() {
            String body = "Hallo, schauen Sie hier: https://promo-a.tk/x https://promo-b.tk/y https://promo-c.tk/z";
            Email email = erstelleEmail("max.mueller@gmail.com", "Spannendes Angebot", body);

            int score = service.calculateSpamScore(email);

            // 3 Links, alle auf andere Domain als gmail.com → Domain-Mismatch (15)
            // + Free-Mailer + ≥3 Links → +20  (kumulativ +35)
            assertThat(score).isGreaterThanOrEqualTo(30);
        }

        @Test
        void firmenMailMitLinksAufEigeneDomainIstNichtVerdaechtig() {
            // 4 unique Subdomains der gleichen Firma → linkCount=4, alle Same-Org
            String body = "Hallo, siehe https://shop.firma.de/a https://blog.firma.de/b "
                    + "https://mail.firma.de/c https://www.firma.de/d";
            Email email = erstelleEmail("info@firma.de", "Newsletter", body);

            int score = service.calculateSpamScore(email);

            // Sender-Domain == Link-Domain (Same-Org-Check via registrableDomain) → kein Aufschlag
            assertThat(score).isLessThan(15);
        }

        @Test
        void authResultsNullErzeugtKeinenAufschlag() {
            Email email = erstelleEmail("info@firma-mustermann.de", "Anfrage", "Hallo");
            email.setAuthenticationResults(null);

            int score = service.calculateSpamScore(email);

            assertThat(score).isLessThan(15);
        }

        @Test
        void authResultsAllesPassErzeugtKeinenAufschlag() {
            Email email = erstelleEmail("info@firma-mustermann.de", "Anfrage", "Hallo");
            email.setAuthenticationResults("mx.example.com; spf=pass; dkim=pass; dmarc=pass");

            int score = service.calculateSpamScore(email);

            assertThat(score).isLessThan(15);
        }

        @Test
        void replyToOhneAtZeichenErzeugtKeinenAufschlag() {
            Email email = erstelleEmail("info@firma.de", "Hi", "Text");
            email.setReplyToAddress("not-an-email");

            int score = service.calculateSpamScore(email);

            assertThat(score).isLessThan(15);
        }

        @Test
        void replyToLeerErzeugtKeinenAufschlag() {
            Email email = erstelleEmail("info@firma.de", "Hi", "Text");
            email.setReplyToAddress("");

            int score = service.calculateSpamScore(email);

            assertThat(score).isLessThan(15);
        }

        @Test
        void vieleTrackingPixelIstVerdaechtig() {
            String html = "<html><body>Hi"
                    + "<img width=\"1\" height=\"1\" src=\"https://t1.example.com/p\">"
                    + "<img width=\"1\" height=\"1\" src=\"https://t2.example.com/p\">"
                    + "<img width=\"1\" height=\"1\" src=\"https://t3.example.com/p\">"
                    + "<img width=\"1\" height=\"1\" src=\"https://t4.example.com/p\">"
                    + "<img width=\"1\" height=\"1\" src=\"https://t5.example.com/p\">"
                    + "</body></html>";
            Email email = erstelleEmail("info@firma.de", "Hallo", "Hi");
            email.setHtmlBody(html);

            int score = service.calculateSpamScore(email);

            assertThat(score).isGreaterThanOrEqualTo(10);
        }

        @Test
        void wenigTextVieleLinksIstVerdaechtig() {
            String body = "Hi! https://a.tk/1 https://b.tk/2 https://c.tk/3 https://d.tk/4 https://e.tk/5";
            Email email = erstelleEmail("info@some-newsletter.com", "Promo", body);

            int score = service.calculateSpamScore(email);

            // ≥5 Links, kurzer Text → +15; alle Links auf andere Domain → +15
            assertThat(score).isGreaterThanOrEqualTo(15);
        }

        @Test
        void normaleFirmenAnfrageHatNiedrigenScore() {
            String body = "Sehr geehrte Damen und Herren, "
                    + "wir interessieren uns für Ihre Stahlbau-Leistungen für unsere neue Halle in München. "
                    + "Können Sie uns ein Angebot für ca. 80 Tonnen Konstruktionsstahl machen? "
                    + "Anbei finden Sie die Pläne. Mit freundlichen Grüßen, Max Mustermann.";
            Email email = erstelleEmail("m.mustermann@mustermann-bau.de", "Anfrage Stahlbau Halle", body);
            email.setAuthenticationResults("mx.example.com; spf=pass; dkim=pass; dmarc=pass");

            int score = service.calculateSpamScore(email);

            assertThat(score).isLessThan(20);
        }
    }
}
