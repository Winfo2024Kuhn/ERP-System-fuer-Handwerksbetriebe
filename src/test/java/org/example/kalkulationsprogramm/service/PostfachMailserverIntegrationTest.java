package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.domain.EmailPostfachZuordnung;
import org.example.kalkulationsprogramm.repository.EmailAbsenderRepository;
import org.example.kalkulationsprogramm.repository.EmailPostfachZuordnungRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.SeenSenderDomainRepository;
import org.example.kalkulationsprogramm.service.mail.MailSecretService;
import org.example.kalkulationsprogramm.service.mail.PostfachZugangService;
import org.example.kalkulationsprogramm.service.mail.SentMailArchiver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;

import jakarta.mail.Address;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

/**
 * Postfächer gegen einen echten IMAP-/SMTP-Server (GreenMail, im Speicher, mit TLS):
 * Abruf aus mehreren Postfächern, Dedupe, Abruf-Status, Versand mit Gesendet-Kopie,
 * fester Absender bei Antworten und Einzelversand. Nur Dummy-Adressen.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ EmailImportService.class, PostfachService.class, PostfachZugangService.class, PostfachVersandService.class,
        SystemSettingsService.class, MailSecretService.class, SentMailArchiver.class })
@TestPropertySource(properties = {
        "mail.credentials.encryption-key=0123456789abcdef",
        "email.features.enabled=true"
})
class PostfachMailserverIntegrationTest {

    @RegisterExtension
    static final GreenMailExtension MAILSERVER = new GreenMailExtension(ServerSetupTest.SMTPS_IMAPS);

    private static SSLContext vorherigerKontext;

    @MockBean private EmailAutoAssignmentService emailAutoAssignmentService;
    @MockBean private EmailAttachmentProcessingService emailAttachmentProcessingService;
    @MockBean private SpamFilterService spamFilterService;
    @MockBean private SteuerberaterEmailProcessingService steuerberaterEmailProcessingService;
    @MockBean private OutOfOfficeResponder outOfOfficeResponder;
    @MockBean private BounceErkennungService bounceErkennungService;
    @MockBean private SeenSenderDomainRepository seenSenderDomainRepository;

    @Autowired private EmailImportService emailImportService;
    @Autowired private PostfachVersandService postfachVersandService;
    @Autowired private MailSecretService mailSecretService;
    @Autowired private EmailAbsenderRepository postfachRepository;
    @Autowired private EmailRepository emailRepository;
    @Autowired private EmailPostfachZuordnungRepository zuordnungRepository;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private EmailAbsender info;
    private EmailAbsender max;

    /** GreenMail nutzt ein selbstsigniertes Zertifikat – nur in diesem Test wird ihm vertraut. */
    @BeforeAll
    static void vertraueTestserver() throws Exception {
        vorherigerKontext = SSLContext.getDefault();
        SSLContext alles = SSLContext.getInstance("TLS");
        alles.init(null, new TrustManager[] { new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] chain, String authType) { }
            @Override public void checkServerTrusted(X509Certificate[] chain, String authType) { }
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        } }, new SecureRandom());
        SSLContext.setDefault(alles);
    }

    @AfterAll
    static void vertrauenZuruecksetzen() {
        SSLContext.setDefault(vorherigerKontext);
    }

    @BeforeEach
    void postfaecherAnlegen() throws Exception {
        MAILSERVER.setUser("info@example.com", "info@example.com", "pw-info");
        MAILSERVER.setUser("max@example.com", "max@example.com", "pw-max");
        info = postfachRepository.save(postfach("info@example.com", "pw-info", true));
        max = postfachRepository.save(postfach("max@example.com", "pw-max", false));
        legeGesendetOrdnerAn("max@example.com", "pw-max");
        legeGesendetOrdnerAn("info@example.com", "pw-info");
    }

    @AfterEach
    void aufraeumen() {
        new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
            zuordnungRepository.deleteAll();
            emailRepository.deleteAll();
            postfachRepository.deleteAll();
        });
    }

    private EmailAbsender postfach(String adresse, String passwort, boolean haupt) {
        EmailAbsender p = new EmailAbsender();
        p.setEmailAdresse(adresse);
        p.setAnzeigename("Musterbetrieb");
        p.setHauptpostfach(haupt);
        p.setPasswortVerschluesselt(mailSecretService.encrypt(passwort));
        p.setSmtpHost("127.0.0.1");
        p.setSmtpPort(ServerSetupTest.SMTPS.getPort());
        p.setImapHost("127.0.0.1");
        p.setImapPort(ServerSetupTest.IMAPS.getPort());
        return p;
    }

    private static Store imap(String benutzer, String passwort) throws Exception {
        Properties props = new Properties();
        props.put("mail.imaps.ssl.enable", "true");
        Store store = Session.getInstance(props).getStore("imaps");
        store.connect("127.0.0.1", ServerSetupTest.IMAPS.getPort(), benutzer, passwort);
        return store;
    }

    private static void legeGesendetOrdnerAn(String benutzer, String passwort) throws Exception {
        try (Store store = imap(benutzer, passwort)) {
            Folder sent = store.getFolder("INBOX.Sent");
            if (!sent.exists()) {
                sent.create(Folder.HOLDS_MESSAGES);
            }
        }
    }

    private static MimeMessage nachricht(String messageId, String an, String cc, String betreff) throws Exception {
        MimeMessage msg = new MimeMessage(Session.getInstance(new Properties())) {
            @Override
            protected void updateMessageID() throws jakarta.mail.MessagingException {
                setHeader("Message-ID", messageId);
            }
        };
        msg.setFrom(new InternetAddress("kunde@example.org"));
        msg.setRecipients(Message.RecipientType.TO, an);
        if (cc != null) {
            msg.setRecipients(Message.RecipientType.CC, cc);
        }
        msg.setSubject(betreff);
        msg.setText("Guten Tag, bitte um ein Angebot.");
        msg.saveChanges();
        return msg;
    }

    /** Posteingang eines Empfängers (GreenMail legt unbekannte Empfänger mit Adresse als Passwort an). */
    private static List<MimeMessage> posteingang(String adresse) {
        return Arrays.stream(MAILSERVER.getReceivedMessagesForDomain(adresse.substring(adresse.indexOf('@') + 1)))
                .filter(m -> {
                    try {
                        return Arrays.stream(m.getAllRecipients())
                                .anyMatch(a -> ((InternetAddress) a).getAddress().equalsIgnoreCase(adresse))
                                && !istGesendetKopie(m);
                    } catch (jakarta.mail.MessagingException e) {
                        throw new IllegalStateException(e);
                    }
                })
                .toList();
    }

    /** Die Gesendet-Kopie ist als gelesen markiert, die zugestellte Mail nicht. */
    private static boolean istGesendetKopie(MimeMessage m) throws jakarta.mail.MessagingException {
        return m.isSet(jakarta.mail.Flags.Flag.SEEN);
    }

    private List<EmailPostfachZuordnung> zuordnungen(Email email) {
        return zuordnungRepository.findByEmailId(email.getId());
    }

    @Test
    void mailAnZweiPostfaecherWirdEinmalGespeichertUndLiegtInBeiden() throws Exception {
        MimeMessage msg = nachricht("<anfrage-1@example.org>", "info@example.com", "max@example.com", "Anfrage Treppe");
        MAILSERVER.getUserManager().getUser("info@example.com").deliver(msg);
        MAILSERVER.getUserManager().getUser("max@example.com").deliver(msg);

        emailImportService.doImport();

        List<Email> mails = emailRepository.findAll();
        assertThat(mails).hasSize(1);
        assertThat(zuordnungen(mails.get(0))).extracting(z -> z.getPostfach().getEmailAdresse())
                .containsExactlyInAnyOrder("info@example.com", "max@example.com");
        assertThat(zuordnungen(mails.get(0))).allMatch(z -> "INBOX".equals(z.getImapOrdner()) && z.getImapUid() != null);

        // Zweiter Lauf: nichts doppelt.
        emailImportService.doImport();
        assertThat(emailRepository.count()).isEqualTo(1);
        assertThat(zuordnungRepository.count()).isEqualTo(2);

        EmailAbsender nachAbruf = postfachRepository.findById(info.getId()).orElseThrow();
        assertThat(nachAbruf.getLetzterAbrufAm()).isNotNull();
        assertThat(nachAbruf.getLetzterAbrufFehler()).isNull();
    }

    @Test
    void falschesPasswortBremstDieAnderenPostfaecherNicht() throws Exception {
        max.setPasswortVerschluesselt(mailSecretService.encrypt("falsch"));
        postfachRepository.save(max);
        MAILSERVER.getUserManager().getUser("info@example.com")
                .deliver(nachricht("<anfrage-2@example.org>", "info@example.com", null, "Anfrage Geländer"));

        emailImportService.doImport();

        assertThat(emailRepository.count()).isEqualTo(1);
        EmailAbsender kaputt = postfachRepository.findById(max.getId()).orElseThrow();
        assertThat(kaputt.getLetzterAbrufFehler()).startsWith("Anmeldung fehlgeschlagen");
        assertThat(postfachRepository.findById(info.getId()).orElseThrow().getLetzterAbrufFehler()).isNull();
    }

    @Test
    void versandAusGewaehltemPostfachMitGesendetKopieDort() throws Exception {
        PostfachVersandService.Versand versand = postfachVersandService.versandUeber(max);

        versand.dienst().sendEmailWithMultipleAttachments("kunde@example.org", null, versand.absenderAdresse(),
                "Ihr Angebot", "<p>Guten Tag</p>", Map.of(), List.of());

        List<MimeMessage> beimKunden = posteingang("kunde@example.org");
        assertThat(beimKunden).hasSize(1);
        assertThat(((InternetAddress) beimKunden.get(0).getFrom()[0]).getAddress()).isEqualTo("max@example.com");
        assertThat(((InternetAddress) beimKunden.get(0).getFrom()[0]).getPersonal()).isEqualTo("Musterbetrieb");

        try (Store store = imap("max@example.com", "pw-max")) {
            Folder sent = store.getFolder("INBOX.Sent");
            sent.open(Folder.READ_ONLY);
            assertThat(sent.getMessageCount()).isEqualTo(1);
            assertThat(sent.getMessages()[0].getSubject()).isEqualTo("Ihr Angebot");
        }
        try (Store store = imap("info@example.com", "pw-info")) {
            Folder sent = store.getFolder("INBOX.Sent");
            sent.open(Folder.READ_ONLY);
            assertThat(sent.getMessageCount()).isZero();
        }
    }

    @Test
    void antwortAufInfoMailGehtVonInfoRaus() throws Exception {
        MAILSERVER.getUserManager().getUser("info@example.com")
                .deliver(nachricht("<anfrage-3@example.org>", "info@example.com", null, "Anfrage Carport"));
        emailImportService.doImport();
        Email eingang = new TransactionTemplate(transactionManager).execute(s -> {
            Email e = emailRepository.findAll().get(0);
            e.getPostfachZuordnungen().size();
            return e;
        });

        EmailAbsender antwortPostfach = postfachVersandService.antwortPostfach(eingang);
        PostfachVersandService.Versand versand = postfachVersandService.versandUeber(antwortPostfach);
        versand.dienst().sendEmailWithMultipleAttachments("kunde@example.org", null, versand.absenderAdresse(),
                "AW: Anfrage Carport", "<p>Gern</p>", Map.of(), List.of());

        assertThat(antwortPostfach.getEmailAdresse()).isEqualTo("info@example.com");
        MimeMessage antwort = posteingang("kunde@example.org").get(0);
        assertThat(((InternetAddress) antwort.getFrom()[0]).getAddress()).isEqualTo("info@example.com");
    }

    @Test
    void einzelversandJederSiehtNurSichSelbst() throws Exception {
        PostfachVersandService.Versand versand = postfachVersandService.versandUeber(info);
        List<String> empfaenger = postfachVersandService.pruefeEinzelversand(
                List.of("anna@example.org", "bernd@example.org", "carla@example.org"), List.of());

        PostfachVersandService.EinzelversandErgebnis<String> ergebnis = postfachVersandService.versendeEinzeln(
                empfaenger, adresse -> versand.dienst().sendEmailWithMultipleAttachments(adresse, null,
                        versand.absenderAdresse(), "Betriebsurlaub", "<p>Wir haben geschlossen.</p>", Map.of(), List.of()));

        assertThat(ergebnis.verschickt()).hasSize(3).doesNotHaveDuplicates();
        assertThat(ergebnis.fehlgeschlagen()).isEmpty();
        for (String adresse : empfaenger) {
            List<MimeMessage> angekommen = posteingang(adresse);
            assertThat(angekommen).hasSize(1);
            Address[] sichtbar = angekommen.get(0).getAllRecipients();
            assertThat(sichtbar).hasSize(1);
            assertThat(((InternetAddress) sichtbar[0]).getAddress()).isEqualTo(adresse);
            assertThat(angekommen.get(0).getRecipients(Message.RecipientType.CC)).isNull();
        }
        // Jede Mail liegt einzeln im Gesendet-Ordner von info@.
        try (Store store = imap("info@example.com", "pw-info")) {
            Folder sent = store.getFolder("INBOX.Sent");
            sent.open(Folder.READ_ONLY);
            assertThat(sent.getMessageCount()).isEqualTo(3);
        }
    }
}
