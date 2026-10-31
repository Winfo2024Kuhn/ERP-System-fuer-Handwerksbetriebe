package org.example.kalkulationsprogramm.service.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.EmailPostfachZuordnung;
import org.example.kalkulationsprogramm.domain.ServerSpamStatus;
import org.example.kalkulationsprogramm.dto.Postfach.PostfachServerOrdnerDto;
import org.example.kalkulationsprogramm.repository.EmailAbsenderRepository;
import org.example.kalkulationsprogramm.repository.EmailPostfachZuordnungRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.service.PostfachService;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.DummySSLServerSocketFactory;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.sun.mail.imap.IMAPFolder;

import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

/**
 * Spam-Abgleich gegen einen echten IMAP-Server (GreenMail, im Speicher, mit TLS):
 * Spam wandert in den Spam-Ordner, „Kein Spam“ holt ihn zurück, eine fremde Mail mit
 * gleicher UID wird nie angefasst. Nur Dummy-Adressen.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ ServerSpamAbgleichService.class, PostfachService.class, PostfachZugangService.class,
        SystemSettingsService.class, MailSecretService.class })
@TestPropertySource(properties = {
        "mail.credentials.encryption-key=0123456789abcdef",
        "email.features.enabled=true"
})
class ServerSpamAbgleichIntegrationTest {

    @RegisterExtension
    static final GreenMailExtension MAILSERVER = new GreenMailExtension(ServerSetupTest.IMAPS);

    private static SSLContext vorherigerKontext;

    @Autowired private ServerSpamAbgleichService service;
    @Autowired private PostfachZugangService postfachZugangService;
    @Autowired private MailSecretService mailSecretService;
    @Autowired private EmailAbsenderRepository postfachRepository;
    @Autowired private EmailRepository emailRepository;
    @Autowired private EmailPostfachZuordnungRepository zuordnungRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private EmailAbsender info;

    /** GreenMail nutzt ein selbstsigniertes Zertifikat – nur diesem wird im Test vertraut. */
    @BeforeAll
    static void vertraueTestserver() throws Exception {
        vorherigerKontext = SSLContext.getDefault();
        TrustManagerFactory nurGreenMail = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        nurGreenMail.init(new DummySSLServerSocketFactory().getKeyStore());
        SSLContext kontext = SSLContext.getInstance("TLS");
        kontext.init(null, nurGreenMail.getTrustManagers(), null);
        SSLContext.setDefault(kontext);
    }

    @AfterAll
    static void vertrauenZuruecksetzen() {
        SSLContext.setDefault(vorherigerKontext);
    }

    @BeforeEach
    void postfachAnlegen() {
        MAILSERVER.setUser("info@example.com", "info@example.com", "pw-info");
        EmailAbsender p = new EmailAbsender();
        p.setEmailAdresse("info@example.com");
        p.setAnzeigename("Musterbetrieb");
        p.setHauptpostfach(true);
        p.setPasswortVerschluesselt(mailSecretService.encrypt("pw-info"));
        p.setImapHost("127.0.0.1");
        p.setImapPort(ServerSetupTest.IMAPS.getPort());
        info = postfachRepository.save(p);
    }

    @AfterEach
    void aufraeumen() {
        new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
            zuordnungRepository.deleteAll();
            emailRepository.deleteAll();
            postfachRepository.deleteAll();
        });
    }

    @Test
    void spamWandertInDenSpamOrdnerUndKeinSpamHoltIhnZurueck() throws Exception {
        legeOrdnerAn("Junk");
        liefere("<werbung-1@example.org>", "Sonderangebot Werkzeug");
        Email mail = speichereMail("<werbung-1@example.org>", true, uidImOrdner("INBOX", "<werbung-1@example.org>"));

        int verschoben = service.gleichePostfachAb(zugang());

        assertThat(verschoben).isEqualTo(1);
        assertThat(messageIds("INBOX")).isEmpty();
        assertThat(messageIds("Junk")).containsExactly("<werbung-1@example.org>");
        EmailPostfachZuordnung z = zuordnung(mail);
        assertThat(z.getImapOrdner()).isEqualTo("Junk");
        assertThat(z.getServerSpamStatus()).isEqualTo(ServerSpamStatus.VERSCHOBEN);
        if (z.getImapUid() != null) {
            assertThat(z.getImapUid()).isEqualTo(uidImOrdner("Junk", "<werbung-1@example.org>"));
        }
        assertThat(postfachRepository.findById(info.getId()).orElseThrow().getSpamOrdnerErkannt()).isEqualTo("Junk");

        // Zweiter Lauf ohne Änderung: nichts zu tun.
        assertThat(service.gleichePostfachAb(zugang())).isZero();

        // „Kein Spam“ im ERP → zurück in den Posteingang.
        new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
            Email frisch = emailRepository.findById(mail.getId()).orElseThrow();
            frisch.setSpam(false);
            emailRepository.save(frisch);
        });
        assertThat(service.gleichePostfachAb(zugang())).isEqualTo(1);

        assertThat(messageIds("INBOX")).containsExactly("<werbung-1@example.org>");
        assertThat(messageIds("Junk")).isEmpty();
        EmailPostfachZuordnung zurueck = zuordnung(mail);
        assertThat(zurueck.getImapOrdner()).isEqualTo("INBOX");
        assertThat(zurueck.getServerSpamStatus()).isNull();
    }

    @Test
    void fremdeMailMitPassenderUidWirdNieVerschoben() throws Exception {
        legeOrdnerAn("INBOX.Spam");
        liefere("<auftrag-7@example.org>", "Auftrag Geländer");
        long uidDerEchtenMail = uidImOrdner("INBOX", "<auftrag-7@example.org>");
        // Gespeicherte Mail mit derselben UID, aber anderer Message-ID (z. B. UID neu vergeben).
        Email andere = speichereMail("<ganz-andere@example.org>", true, uidDerEchtenMail);

        assertThat(service.gleichePostfachAb(zugang())).isZero();

        assertThat(messageIds("INBOX")).containsExactly("<auftrag-7@example.org>");
        assertThat(messageIds("INBOX.Spam")).isEmpty();
        assertThat(zuordnung(andere).getServerSpamStatus()).isEqualTo(ServerSpamStatus.NICHT_GEFUNDEN);

        // Einmal nicht gefunden → wird nicht bei jedem Lauf erneut gesucht.
        assertThat(zuordnungRepository.findSpamZumVerschieben(info.getId(), "INBOX", EmailDirection.IN,
                org.springframework.data.domain.Limit.of(10))).isEmpty();
    }

    @Test
    void ohneSpamOrdnerBleibtAllesLiegenUndDasPostfachZeigtDenGrund() throws Exception {
        liefere("<werbung-2@example.org>", "Gewinnspiel");
        speichereMail("<werbung-2@example.org>", true, uidImOrdner("INBOX", "<werbung-2@example.org>"));

        assertThat(service.gleichePostfachAb(zugang())).isZero();

        assertThat(messageIds("INBOX")).containsExactly("<werbung-2@example.org>");
        EmailAbsender nachher = postfachRepository.findById(info.getId()).orElseThrow();
        assertThat(nachher.getSpamAbgleichFehler()).isEqualTo(ServerSpamAbgleichService.KEIN_SPAM_ORDNER);
        assertThat(nachher.getSpamOrdnerErkannt()).isNull();
    }

    @Test
    void vonHandGewaehlterOrdnerGehtVorUndFehlenderWirdGemeldet() throws Exception {
        legeOrdnerAn("Junk");
        legeOrdnerAn("Werbung");
        liefere("<werbung-3@example.org>", "Newsletter");
        speichereMail("<werbung-3@example.org>", true, null);
        info.setSpamOrdner("Werbung");
        info = postfachRepository.save(info);

        assertThat(service.gleichePostfachAb(zugang())).isEqualTo(1);
        assertThat(messageIds("Werbung")).containsExactly("<werbung-3@example.org>");
        assertThat(messageIds("Junk")).isEmpty();

        info.setSpamOrdner("Gibt-es-nicht");
        info = postfachRepository.save(info);
        liefere("<werbung-4@example.org>", "Newsletter 2");
        speichereMail("<werbung-4@example.org>", true, null);

        assertThat(service.gleichePostfachAb(zugang())).isZero();
        assertThat(messageIds("INBOX")).containsExactly("<werbung-4@example.org>");
        assertThat(postfachRepository.findById(info.getId()).orElseThrow().getSpamAbgleichFehler())
                .contains("Gibt-es-nicht");
    }

    @Test
    void ausgeschaltetPassiertNichts() throws Exception {
        legeOrdnerAn("Junk");
        liefere("<werbung-5@example.org>", "Rabatt");
        speichereMail("<werbung-5@example.org>", true, null);
        info.setSpamAufServerVerschieben(false);
        info = postfachRepository.save(info);

        assertThat(service.gleichePostfachAb(zugang())).isZero();
        assertThat(messageIds("INBOX")).containsExactly("<werbung-5@example.org>");
    }

    @Test
    void ausgeschaltetHoltSchonVerschobeneMailsTrotzdemZurueck() throws Exception {
        legeOrdnerAn("Junk");
        liefere("<werbung-6@example.org>", "Rabatt 2");
        Email mail = speichereMail("<werbung-6@example.org>", true, null);
        assertThat(service.gleichePostfachAb(zugang())).isEqualTo(1);

        info.setSpamAufServerVerschieben(false);
        info = postfachRepository.save(info);
        new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
            Email frisch = emailRepository.findById(mail.getId()).orElseThrow();
            frisch.setSpam(false);
            emailRepository.save(frisch);
        });

        assertThat(service.gleichePostfachAb(zugang())).isEqualTo(1);
        assertThat(messageIds("INBOX")).containsExactly("<werbung-6@example.org>");
        assertThat(messageIds("Junk")).isEmpty();
    }

    @Test
    void papierkorbAlsGewaehlterOrdnerWirdNieBenutzt() throws Exception {
        legeOrdnerAn("Trash");
        liefere("<werbung-7@example.org>", "Gutschein");
        speichereMail("<werbung-7@example.org>", true, null);
        info.setSpamOrdner("Trash");
        info = postfachRepository.save(info);

        assertThat(service.gleichePostfachAb(zugang())).isZero();

        assertThat(messageIds("INBOX")).containsExactly("<werbung-7@example.org>");
        assertThat(messageIds("Trash")).isEmpty();
        assertThat(postfachRepository.findById(info.getId()).orElseThrow().getSpamAbgleichFehler())
                .contains("eignet sich nicht als Spam-Ordner");
    }

    @Test
    void ordnerlisteErkenntDenSpamOrdnerUndLaesstUngeeigneteWeg() throws Exception {
        legeOrdnerAn("Junk");
        legeOrdnerAn("Trash");
        legeOrdnerAn("INBOX.Eingangsrechnungen");

        PostfachServerOrdnerDto ordner = service.serverOrdner(info.getId());

        assertThat(ordner.erkannterSpamOrdner()).isEqualTo("Junk");
        assertThat(ordner.ordner()).extracting(PostfachServerOrdnerDto.Ordner::name)
                .contains("Junk", "INBOX.Eingangsrechnungen")
                .doesNotContain("INBOX", "Trash");
        assertThat(ordner.ordner()).filteredOn(PostfachServerOrdnerDto.Ordner::spamOrdner)
                .extracting(PostfachServerOrdnerDto.Ordner::name).containsExactly("Junk");
    }

    // ==================== Hilfen ====================

    private PostfachZugang zugang() {
        return postfachZugangService.zugangVon(postfachRepository.findById(info.getId()).orElseThrow());
    }

    private Email speichereMail(String messageId, boolean spam, Long uid) {
        return speichereMail(messageId, BETREFF.getOrDefault(messageId, "Testmail"), spam, uid);
    }

    private Email speichereMail(String messageId, String betreff, boolean spam, Long uid) {
        return new TransactionTemplate(transactionManager).execute(s -> {
            Email e = new Email();
            e.setMessageId(messageId);
            e.setDirection(EmailDirection.IN);
            e.setFromAddress("kunde@example.org");
            e.setSubject(betreff);
            e.setSpam(spam);
            e.ordnePostfachZu(postfachRepository.getReferenceById(info.getId()), "INBOX", uid);
            return emailRepository.save(e);
        });
    }

    private EmailPostfachZuordnung zuordnung(Email email) {
        return zuordnungRepository.findByEmailId(email.getId()).getFirst();
    }

    private static Store imap() throws Exception {
        Properties props = new Properties();
        props.put("mail.imaps.ssl.enable", "true");
        Store store = Session.getInstance(props).getStore("imaps");
        store.connect("127.0.0.1", ServerSetupTest.IMAPS.getPort(), "info@example.com", "pw-info");
        return store;
    }

    private static void legeOrdnerAn(String name) throws Exception {
        try (Store store = imap()) {
            Folder f = store.getFolder(name);
            if (!f.exists()) {
                f.create(Folder.HOLDS_MESSAGES);
            }
        }
    }

    /** Betreff je zugestellter Mail – die gespeicherte Mail muss dazu passen (Absender + Betreff). */
    private static final java.util.Map<String, String> BETREFF = new java.util.concurrent.ConcurrentHashMap<>();

    private static void liefere(String messageId, String betreff) throws Exception {
        BETREFF.put(messageId, betreff);
        MimeMessage msg = new MimeMessage(Session.getInstance(new Properties())) {
            @Override
            protected void updateMessageID() throws jakarta.mail.MessagingException {
                setHeader("Message-ID", messageId);
            }
        };
        msg.setFrom(new InternetAddress("kunde@example.org"));
        msg.setRecipients(Message.RecipientType.TO, "info@example.com");
        msg.setSubject(betreff);
        msg.setText("Guten Tag, Max Mustermann.");
        msg.saveChanges();
        MAILSERVER.getUserManager().getUser("info@example.com").deliver(msg);
    }

    private static List<String> messageIds(String ordner) throws Exception {
        try (Store store = imap()) {
            Folder f = store.getFolder(ordner);
            f.open(Folder.READ_ONLY);
            try {
                List<String> ids = new ArrayList<>();
                for (Message m : f.getMessages()) {
                    if (!m.isSet(jakarta.mail.Flags.Flag.DELETED)) {
                        ids.add(m.getHeader("Message-ID")[0]);
                    }
                }
                return ids;
            } finally {
                f.close(false);
            }
        }
    }

    private static long uidImOrdner(String ordner, String messageId) throws Exception {
        try (Store store = imap()) {
            IMAPFolder f = (IMAPFolder) store.getFolder(ordner);
            f.open(Folder.READ_ONLY);
            try {
                for (Message m : f.getMessages()) {
                    if (messageId.equals(m.getHeader("Message-ID")[0])) {
                        return f.getUID(m);
                    }
                }
                throw new IllegalStateException("Mail nicht im Ordner " + ordner);
            } finally {
                f.close(false);
            }
        }
    }
}
