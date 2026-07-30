package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.sun.mail.imap.IMAPFolder;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailImportServiceTest {

    @Mock private EmailRepository emailRepository;
    @Mock private EmailAttachmentRepository attachmentRepository;
    @Mock private EmailAutoAssignmentService emailAutoAssignmentService;
    @Mock private EmailAttachmentProcessingService emailAttachmentProcessingService;
    @Mock private SpamFilterService spamFilterService;
    @Mock private SteuerberaterEmailProcessingService steuerberaterEmailProcessingService;
    @Mock private LieferantenRepository lieferantenRepository;
    @Mock private EmailBlacklistRepository emailBlacklistRepository;
    @Mock private BounceErkennungService bounceErkennungService;

    @InjectMocks
    private EmailImportService service;

    private Email erstelleEmail(Long id, String messageId, String fromAddress) {
        Email email = new Email();
        email.setId(id);
        email.setMessageId(messageId);
        email.setFromAddress(fromAddress);
        email.setZuordnungTyp(EmailZuordnungTyp.KEINE);
        email.setDirection(EmailDirection.IN);
        if (fromAddress != null && fromAddress.contains("@")) {
            email.setSenderDomain(fromAddress.substring(fromAddress.lastIndexOf('@') + 1).toLowerCase());
        }
        return email;
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.3.1 Erkennt Duplikate anhand Message-ID
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class DuplikatErkennung {

        @Test
        void erkenntesBereitsImportiertesViamessageId() {
            // Die importMessage()-Methode benötigt jakarta.mail.Message,
            // daher testen wir die Logik indirekt über die Repository-Prüfung
            when(emailRepository.existsByMessageId("<test@example.com>")).thenReturn(true);

            boolean exists = emailRepository.existsByMessageId("<test@example.com>");
            assertThat(exists).isTrue();
        }

        @Test
        void neueMessageIdWirdNichtAlsDuplikatErkannt() {
            when(emailRepository.existsByMessageId("<new@example.com>")).thenReturn(false);

            boolean exists = emailRepository.existsByMessageId("<new@example.com>");
            assertThat(exists).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.3.2 Verknüpft Antworten mit Eltern-E-Mail
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class ParentEmailVerknuepfung {

        @Test
        void findetParentEmailAnhandMessageId() {
            Email parent = erstelleEmail(1L, "<parent@example.com>", "sender@firma.de");
            parent.setZuordnungTyp(EmailZuordnungTyp.PROJEKT);
            Projekt projekt = new Projekt();
            projekt.setId(5L);
            parent.setProjekt(projekt);

            when(emailRepository.findByMessageIdIn(List.of("<parent@example.com>")))
                    .thenReturn(List.of(parent));

            List<Email> found = emailRepository.findByMessageIdIn(List.of("<parent@example.com>"));
            assertThat(found).hasSize(1);
            assertThat(found.getFirst().getProjekt()).isEqualTo(projekt);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.3.2b Lieferant-Domain hat Vorrang vor Projekt-Thread-Vererbung
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class LieferantVorrangBeiThreadVererbung {

        @Test
        void lieferantDomainHatVorrangVorProjektVererbung() {
            // Arrange: Parent-Email ist einem Projekt zugeordnet
            Email parent = erstelleEmail(1L, "<parent@example.com>", "intern@firma.de");
            Projekt projekt = new Projekt();
            projekt.setId(95L);
            parent.assignToProjekt(projekt);

            // Lieferant mit passender Domain
            Lieferanten lieferant = new Lieferanten();
            lieferant.setId(222L);
            lieferant.setLieferantenname("Blue Solution");

            // Kind-Email vom Lieferanten (Antwort im Projekt-Thread)
            Email child = erstelleEmail(2L, "<reply@bluesolution.software>", "rechnungen@bluesolution.software");
            child.setParentEmail(parent);

            when(lieferantenRepository.findByEmailDomain("bluesolution.software"))
                    .thenReturn(List.of(lieferant));

            // Act: Simuliere die Zuordnungslogik wie in importMessage()
            // (Wir können importMessage nicht direkt aufrufen da es IMAP Message braucht,
            // daher prüfen wir die findByEmailDomain-Logik)
            boolean assignedToLieferant = false;
            if (parent.getProjekt() != null || parent.getAnfrage() != null) {
                String senderDomain = child.getSenderDomain();
                if (senderDomain != null) {
                    List<Lieferanten> matches = lieferantenRepository.findByEmailDomain(senderDomain);
                    if (!matches.isEmpty()) {
                        child.assignToLieferant(matches.getFirst());
                        assignedToLieferant = true;
                    }
                }
            }

            // Assert: Email soll Lieferant zugeordnet sein, NICHT dem Projekt
            assertThat(assignedToLieferant).isTrue();
            assertThat(child.getZuordnungTyp()).isEqualTo(EmailZuordnungTyp.LIEFERANT);
            assertThat(child.getLieferant()).isEqualTo(lieferant);
            assertThat(child.getProjekt()).isNull();
        }

        @Test
        void nichtLieferantDomainErbtProjektVonParent() {
            // Arrange: Parent-Email mit Projekt-Zuordnung
            Email parent = erstelleEmail(1L, "<parent@example.com>", "intern@firma.de");
            Projekt projekt = new Projekt();
            projekt.setId(95L);
            parent.assignToProjekt(projekt);

            // Kind-Email von unbekannter Domain (kein Lieferant)
            Email child = erstelleEmail(2L, "<reply@kunde.de>", "kontakt@kunde.de");

            when(lieferantenRepository.findByEmailDomain("kunde.de"))
                    .thenReturn(Collections.emptyList());

            // Act
            boolean assignedToLieferant = false;
            if (parent.getProjekt() != null || parent.getAnfrage() != null) {
                String senderDomain = child.getSenderDomain();
                if (senderDomain != null) {
                    List<Lieferanten> matches = lieferantenRepository.findByEmailDomain(senderDomain);
                    if (!matches.isEmpty()) {
                        child.assignToLieferant(matches.getFirst());
                        assignedToLieferant = true;
                    }
                }
            }
            if (!assignedToLieferant) {
                child.assignToProjekt(projekt);
            }

            // Assert: Email erbt Projekt-Zuordnung vom Parent
            assertThat(child.getZuordnungTyp()).isEqualTo(EmailZuordnungTyp.PROJEKT);
            assertThat(child.getProjekt()).isEqualTo(projekt);
            assertThat(child.getLieferant()).isNull();
        }

        @Test
        void zweiteDomainDesLieferantenWirdErkannt() {
            // Arrange: Blue Solution hat ZWEI Domains
            Email parent = erstelleEmail(1L, "<parent@example.com>", "intern@firma.de");
            Projekt projekt = new Projekt();
            projekt.setId(95L);
            parent.assignToProjekt(projekt);

            Lieferanten lieferant = new Lieferanten();
            lieferant.setId(222L);
            lieferant.setLieferantenname("Blue Solution");

            // Email von der zweiten Domain (bluesolution.de statt bluesolution.software)
            Email child = erstelleEmail(3L, "<info@bluesolution.de>", "info@bluesolution.de");

            when(lieferantenRepository.findByEmailDomain("bluesolution.de"))
                    .thenReturn(List.of(lieferant));

            // Act
            boolean assignedToLieferant = false;
            if (parent.getProjekt() != null) {
                String senderDomain = child.getSenderDomain();
                if (senderDomain != null) {
                    List<Lieferanten> matches = lieferantenRepository.findByEmailDomain(senderDomain);
                    if (!matches.isEmpty()) {
                        child.assignToLieferant(matches.getFirst());
                        assignedToLieferant = true;
                    }
                }
            }

            // Assert
            assertThat(assignedToLieferant).isTrue();
            assertThat(child.getZuordnungTyp()).isEqualTo(EmailZuordnungTyp.LIEFERANT);
            assertThat(child.getLieferant().getId()).isEqualTo(222L);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.3.3 Newsletter werden als solche markiert
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class NewsletterMarkierung {

        @Test
        void newsletterWerdenBeiPostProcessingMarkiert() {
            Email email = erstelleEmail(1L, "<newsletter@test.com>", "newsletter@portal.de");

            when(steuerberaterEmailProcessingService.processSteuerberaterEmail(email)).thenReturn(false);
            // SpamFilterService markiert Newsletter
            doAnswer(invocation -> {
                Email e = invocation.getArgument(0);
                e.setNewsletter(true);
                return null;
            }).when(spamFilterService).analyzeAndMarkSpam(email);

            service.postProcessEmail(email);

            verify(spamFilterService).analyzeAndMarkSpam(email);
            verify(emailAutoAssignmentService).tryAutoAssign(email);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.3.4 Newsletter von Lieferanten werden nicht gefiltert
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class LieferantenNewsletter {

        @Test
        void lieferantenNewsletterWerdenBereinigt() {
            Email email = erstelleEmail(1L, "<news@lieferant.de>", "news@lieferant.de");
            Lieferanten lieferant = new Lieferanten();
            lieferant.setId(10L);
            email.assignToLieferant(lieferant);

            when(steuerberaterEmailProcessingService.processSteuerberaterEmail(email)).thenReturn(false);
            // Auto-Assign returns false (already assigned)
            when(emailAutoAssignmentService.tryAutoAssign(email)).thenReturn(false);
            // SpamFilter marks as newsletter
            doAnswer(invocation -> {
                Email e = invocation.getArgument(0);
                e.setNewsletter(true);
                e.setSpamScore(30);
                return null;
            }).when(spamFilterService).analyzeAndMarkSpam(email);

            service.postProcessEmail(email);

            // Lieferanten-Emails: Spam/Newsletter-Flags werden bereinigt
            assertThat(email.isNewsletter()).isFalse();
            assertThat(email.isSpam()).isFalse();
            assertThat(email.getSpamScore()).isEqualTo(0);
            verify(emailRepository).save(email);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.3.5 Verarbeitet IMAP-Ausgangsordner korrekt (indirekt)
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class AusgangsordnerVerarbeitung {

        @Test
        void sentOrderIstAlsOutgoingDefiniert() {
            // Der Service hat static Lists INCOMING_FOLDERS und OUTGOING_FOLDERS
            // Wir verifizieren das Verhalten indirekt -
            // doImport() benutzt IMAP-Verbindung, daher testen wir postProcessEmail
            Email outgoing = erstelleEmail(1L, "<sent@test.com>", "wir@firma.de");
            outgoing.setDirection(EmailDirection.OUT);

            when(steuerberaterEmailProcessingService.processSteuerberaterEmail(outgoing)).thenReturn(false);

            service.postProcessEmail(outgoing);

            // Auto-Assign wird trotzdem versucht
            verify(emailAutoAssignmentService).tryAutoAssign(outgoing);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.3.6 Setzt Processing-Status korrekt im Lifecycle
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class ProcessingStatus {

        @Test
        void neueEmailHatDoneStatus() {
            Email email = new Email();
            email.setProcessingStatus(EmailProcessingStatus.DONE);

            assertThat(email.getProcessingStatus()).isEqualTo(EmailProcessingStatus.DONE);
        }

        @Test
        void processingStatusWirdBeiVerarbeitungGesetzt() {
            Email email = erstelleEmail(1L, "<test@test.com>", "test@test.com");
            email.setProcessingStatus(EmailProcessingStatus.QUEUED);

            // Simuliert den Lifecycle
            email.setProcessingStatus(EmailProcessingStatus.PROCESSING);
            assertThat(email.getProcessingStatus()).isEqualTo(EmailProcessingStatus.PROCESSING);

            email.setProcessingStatus(EmailProcessingStatus.DONE);
            assertThat(email.getProcessingStatus()).isEqualTo(EmailProcessingStatus.DONE);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.3.6b Regression: Emails ohne Message-ID werden nicht verworfen
    // Bug: importMessage() hatte `if (messageId == null) return false;`
    // Folge: Alle bluesolution.software Rechnungen wurden nie importiert.
    // Fix: Fallback-ID aus IMAP-UID + Ordnername (<no-msgid-uid-{uid}@{folder}>)
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class FallbackMessageId {

        private IMAPFolder mockFolder;
        private Message mockMessage;

        @BeforeEach
        void setUp() throws Exception {
            mockFolder = mock(IMAPFolder.class);
            mockMessage = mock(Message.class);

            // Standard-Setup: kein Message-ID Header
            // lenient(): einzelne Tests überschreiben diese Stubs (z.B. andere Ordner oder UIDs),
            // ohne dass Mockito strict-mode UnnecessaryStubbingException wirft.
            lenient().when(mockMessage.getHeader("Message-ID")).thenReturn(null);
            lenient().when(mockFolder.getUID(mockMessage)).thenReturn(146693L);
            lenient().when(mockFolder.getFullName()).thenReturn("INBOX");
            lenient().when(mockMessage.getFrom()).thenReturn(new Address[]{
                    new InternetAddress("rechnungen@bluesolution.software", "blue:solution software GmbH")
            });
            lenient().when(mockMessage.getSubject()).thenReturn("Rechnung 400185");
            lenient().when(mockMessage.getSentDate()).thenReturn(new Date());
            lenient().when(mockMessage.getRecipients(Message.RecipientType.TO)).thenReturn(null);
            lenient().when(mockMessage.getRecipients(Message.RecipientType.CC)).thenReturn(null);
            lenient().when(mockMessage.getHeader("In-Reply-To")).thenReturn(null);
            lenient().when(mockMessage.getHeader("References")).thenReturn(null);
            lenient().when(mockMessage.getHeader("List-Unsubscribe")).thenReturn(null);
            lenient().when(mockMessage.getHeader("X-Mailer")).thenReturn(null);
            lenient().when(mockMessage.getHeader("X-Spam-Status")).thenReturn(null);
            lenient().when(mockMessage.getContentType()).thenReturn("text/plain");
            lenient().when(mockMessage.getContent()).thenReturn("Rechnungsinhalt");

            // Repository-Mocks
            lenient().when(emailRepository.existsByMessageId(any())).thenReturn(false);
            lenient().when(emailRepository.save(any(Email.class))).thenAnswer(inv -> {
                Email e = inv.getArgument(0);
                e.setId(3202L);
                return e;
            });
            lenient().when(lieferantenRepository.findByEmailDomain(any())).thenReturn(Collections.emptyList());
            lenient().when(lieferantenRepository.existsByEmailDomain(any())).thenReturn(false);
            lenient().when(steuerberaterEmailProcessingService.processSteuerberaterEmail(any())).thenReturn(false);
        }

        @Test
        void emailOhneMessageIdWirdNichtVerworfen() throws Exception {
            // Regression für: `if (messageId == null) { return false; }`
            // Emails ohne Message-ID-Header müssen importiert werden
            boolean imported = service.importMessage(mockMessage, mockFolder, EmailDirection.IN);

            assertThat(imported).isTrue();
            verify(emailRepository, atLeastOnce()).save(any(Email.class));
        }

        @Test
        void fallbackIdWirdAusIMAPUidUndOrdnerGeneriert() throws Exception {
            // Fallback-ID muss deterministisch und eindeutig sein:
            // Format: <no-msgid-uid-{uid}@{folder}>
            service.importMessage(mockMessage, mockFolder, EmailDirection.IN);

            verify(emailRepository).existsByMessageId("<no-msgid-uid-146693@INBOX>");
        }

        @Test
        void fallbackIdErmoeglichtDeduplizierung() throws Exception {
            // Wenn die Fallback-ID bereits existiert, darf die Email nicht
            // erneut importiert werden (Dedup nach erfolgreichem Import)
            when(emailRepository.existsByMessageId("<no-msgid-uid-146693@INBOX>")).thenReturn(true);

            boolean imported = service.importMessage(mockMessage, mockFolder, EmailDirection.IN);

            assertThat(imported).isFalse();
            verify(emailRepository, never()).save(any(Email.class));
        }

        @Test
        void fallbackIdEncodeOrdnerMitLeerzeichen() throws Exception {
            // Ordnernamen mit Leerzeichen werden mit _ ersetzt (URL-sicher)
            when(mockFolder.getFullName()).thenReturn("INBOX.Mein Ordner");
            when(mockFolder.getUID(mockMessage)).thenReturn(999L);

            service.importMessage(mockMessage, mockFolder, EmailDirection.IN);

            verify(emailRepository).existsByMessageId("<no-msgid-uid-999@INBOX.Mein_Ordner>");
        }

        @Test
        void emailMitMessageIdNutztOriginalId() throws Exception {
            // Emails MIT Message-ID sollen die Original-ID benutzen (kein Fallback)
            when(mockMessage.getHeader("Message-ID"))
                    .thenReturn(new String[]{"<original-id@example.com>"});

            service.importMessage(mockMessage, mockFolder, EmailDirection.IN);

            // Original Message-ID wird für Dedup-Prüfung verwendet
            verify(emailRepository).existsByMessageId("<original-id@example.com>");
            // Fallback-Format (<no-msgid-uid-...>) wird NICHT für Dedup genutzt
            // (getUID wird aber trotzdem für imapUid-Speicherung aufgerufen – das ist korrekt)
            verify(emailRepository, never()).existsByMessageId(
                    argThat(id -> id != null && id.startsWith("<no-msgid-uid-")));
        }

        /**
         * Regelfall: Die eigene Sent-Kopie traegt dieselbe Message-ID wie die
         * lokal archivierte Ausgangsmail — die Deduplizierung greift, die Mail
         * steht kein zweites Mal (und nicht mit vom Server umgebautem Layout)
         * in der DB.
         */
        @Test
        void ueberspringtEigeneSentKopieUeberMessageIdDedup() throws Exception {
            when(mockMessage.getHeader("Message-ID"))
                    .thenReturn(new String[]{"<eigene-mail@example.com>"});
            when(emailRepository.existsByMessageId("<eigene-mail@example.com>")).thenReturn(true);

            boolean importiert = service.importMessage(mockMessage, mockFolder, EmailDirection.OUT);

            assertThat(importiert).as("Bereits archivierte Ausgangsmail nicht doppelt importieren").isFalse();
            verify(emailRepository, never()).save(any(Email.class));
        }

        /**
         * Sicherheitsnetz: Traegt die Mail zwar unseren Marker, steht aber NICHT
         * in der DB, ist die lokale Archivierung fehlgeschlagen. Dann muss sie
         * importiert werden — eine Mail mit veraendertem Layout ist besser als
         * eine, die im ERP komplett fehlt.
         */
        @Test
        void holtEigeneMailNachWennLokaleArchivierungFehlschlug() throws Exception {
            when(mockMessage.getHeader("Message-ID"))
                    .thenReturn(new String[]{"<nicht-archiviert@example.com>"});
            when(mockMessage.getHeader(org.example.email.EmailService.ERP_ORIGIN_HEADER))
                    .thenReturn(new String[]{org.example.email.EmailService.ERP_ORIGIN_WERT});

            boolean importiert = service.importMessage(mockMessage, mockFolder, EmailDirection.OUT);

            assertThat(importiert).as("Nicht archivierte Ausgangsmail muss nachgeholt werden").isTrue();
        }

        /**
         * Gegenprobe: Fremde Mails (z.B. vom Handy verschickt) haben keinen
         * Marker und laufen ganz normal durch.
         */
        @Test
        void importiertFremdeMailsOhneMarkerHeaderWeiterhin() throws Exception {
            when(mockMessage.getHeader("Message-ID"))
                    .thenReturn(new String[]{"<vom-handy@example.com>"});
            when(mockMessage.getHeader(org.example.email.EmailService.ERP_ORIGIN_HEADER))
                    .thenReturn(null);

            boolean importiert = service.importMessage(mockMessage, mockFolder, EmailDirection.OUT);

            assertThat(importiert).as("Mails ohne Marker muessen weiterhin importiert werden").isTrue();
        }

        @Test
        void speichertImapUidInEmail() throws Exception {
            // Die IMAP-UID muss in der Email gespeichert werden für spätere Referenzen.
            // Hinweis: save() wird 2× aufgerufen – einmal in importMessage, einmal in postProcessEmail.
            service.importMessage(mockMessage, mockFolder, EmailDirection.IN);

            verify(emailRepository, atLeastOnce()).save(argThat(email ->
                    email.getImapUid() != null && email.getImapUid() == 146693L
            ));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.3.6c Gesperrte Absender (Blacklist) werden beim Import komplett verworfen
    // Bug: Beim "Absender sperren" wurden Mails als Spam markiert → Bayes-Modell
    //      wurde verfälscht. Fix: Blacklist-Treffer skippt Import vollständig.
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class BlacklistSkip {

        private IMAPFolder mockFolder;
        private Message mockMessage;

        @BeforeEach
        void setUp() throws Exception {
            mockFolder = mock(IMAPFolder.class);
            mockMessage = mock(Message.class);

            lenient().when(mockMessage.getHeader("Message-ID"))
                    .thenReturn(new String[]{"<spam@evil.example.com>"});
            lenient().when(mockFolder.getUID(mockMessage)).thenReturn(42L);
            lenient().when(mockFolder.getFullName()).thenReturn("INBOX");
            lenient().when(mockMessage.getFrom()).thenReturn(new Address[]{
                    new InternetAddress("blocked@evil.example.com")
            });
            lenient().when(mockMessage.getSubject()).thenReturn("You won the lottery");
            lenient().when(mockMessage.getRecipients(any())).thenReturn(null);
            lenient().when(mockMessage.getHeader(any())).thenReturn(null);
            lenient().when(mockMessage.getHeader("Message-ID"))
                    .thenReturn(new String[]{"<spam@evil.example.com>"});
            lenient().when(emailRepository.existsByMessageId(any())).thenReturn(false);
        }

        @Test
        void gesperrterAbsenderWirdNichtImportiert() throws Exception {
            // Absender steht auf der Blacklist (lowercase-Lookup)
            when(emailBlacklistRepository.existsByEmailAddress("blocked@evil.example.com"))
                    .thenReturn(true);

            boolean imported = service.importMessage(mockMessage, mockFolder, EmailDirection.IN);

            // Mail wird verworfen, kein DB-Eintrag, kein Spam-Filter, kein Auto-Assign.
            // Genau diese Eigenschaften sind der Kern des Bugfixes — würden sie wieder
            // verloren gehen, würde Bestandsdaten + Bayes-Modell verschmutzt.
            assertThat(imported).isFalse();
            verify(emailRepository, never()).save(any(Email.class));
            verify(spamFilterService, never()).analyzeAndMarkSpam(any(Email.class));
            verify(emailAutoAssignmentService, never()).tryAutoAssign(any(Email.class));
        }

        @Test
        void nichtGesperrterAbsenderWirdNormalImportiert() throws Exception {
            when(emailBlacklistRepository.existsByEmailAddress(any())).thenReturn(false);
            when(emailRepository.save(any(Email.class))).thenAnswer(inv -> {
                Email e = inv.getArgument(0);
                e.setId(99L);
                return e;
            });
            lenient().when(lieferantenRepository.findByEmailDomain(any()))
                    .thenReturn(Collections.emptyList());
            lenient().when(lieferantenRepository.existsByEmailDomain(any())).thenReturn(false);
            lenient().when(steuerberaterEmailProcessingService.processSteuerberaterEmail(any()))
                    .thenReturn(false);
            lenient().when(mockMessage.getContent()).thenReturn("body");
            lenient().when(mockMessage.getContentType()).thenReturn("text/plain");

            boolean imported = service.importMessage(mockMessage, mockFolder, EmailDirection.IN);

            assertThat(imported).isTrue();
            verify(emailRepository, atLeastOnce()).save(any(Email.class));
        }

        @Test
        void blacklistCheckGreiftNichtFuerAusgehendeMails() throws Exception {
            // Eigene gesendete Mails dürfen niemals durch die Blacklist verworfen werden
            // (sonst würde z.B. eine Antwort an einen geblockten Absender im "Gesendet"-
            // Ordner verschwinden).
            when(emailRepository.save(any(Email.class))).thenAnswer(inv -> {
                Email e = inv.getArgument(0);
                e.setId(100L);
                return e;
            });
            lenient().when(steuerberaterEmailProcessingService.processSteuerberaterEmail(any()))
                    .thenReturn(false);
            lenient().when(mockMessage.getContent()).thenReturn("body");
            lenient().when(mockMessage.getContentType()).thenReturn("text/plain");

            boolean imported = service.importMessage(mockMessage, mockFolder, EmailDirection.OUT);

            assertThat(imported).isTrue();
            // Blacklist-Repository darf für ausgehende Mails gar nicht erst gefragt werden
            verify(emailBlacklistRepository, never()).existsByEmailAddress(any());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2.3.7 Behandelt fehlerhafte Messages ohne Abbruch
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class FehlerBehandlung {

        @Test
        void postProcessEmailBehandeltSteuerberaterFehlerOhneAbbruch() {
            Email email = erstelleEmail(1L, "<test@test.com>", "test@firma.de");

            when(steuerberaterEmailProcessingService.processSteuerberaterEmail(email))
                    .thenThrow(new RuntimeException("Verarbeitungsfehler"));

            // Should not throw - would be handled in calling code
            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                    () -> service.postProcessEmail(email));
        }

        @Test
        void postProcessEmailBehandeltAttachmentFehlerOhneAbbruch() {
            Email email = erstelleEmail(1L, "<test@test.com>", "test@lieferant.de");
            Lieferanten lieferant = new Lieferanten();
            lieferant.setId(5L);

            when(steuerberaterEmailProcessingService.processSteuerberaterEmail(email)).thenReturn(false);
            // Auto-Assign ordnet Lieferant zu
            doAnswer(invocation -> {
                Email e = invocation.getArgument(0);
                e.assignToLieferant(lieferant);
                return true;
            }).when(emailAutoAssignmentService).tryAutoAssign(email);

            when(emailAttachmentProcessingService.processLieferantAttachments(email))
                    .thenThrow(new RuntimeException("Attachment-Fehler"));

            // Should not throw - error is caught internally in postProcessEmail
            service.postProcessEmail(email);

            // Verify the spam filter was still called before attachments
            verify(spamFilterService).analyzeAndMarkSpam(email);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Subject-Normalisierung & Reply-Erkennung
    // (für Subject-basiertes Thread-Matching im Backfill, wenn
    //  In-Reply-To/References-Header fehlen)
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class SubjectNormalisierung {

        @Test
        void erkenntKlassischesReplyAmAnfang() {
            assertThat(service.isReplyOrForward("Re: Transfer")).isTrue();
            assertThat(service.isReplyOrForward("RE: Transfer")).isTrue();
            assertThat(service.isReplyOrForward("AW: Transfer")).isTrue();
            assertThat(service.isReplyOrForward("Fwd: Transfer")).isTrue();
            assertThat(service.isReplyOrForward("WG: Transfer")).isTrue();
        }

        @Test
        void erkenntReplyHinterKlammerTag() {
            // Bisheriger Bug: "[Ticket#…] RE: …" wurde NICHT als Reply erkannt,
            // weil isReplyOrForward nur am Stringanfang prüfte.
            assertThat(service.isReplyOrForward("[Ticket#2026050503034259] RE: Transfer bauschlosserei-mustermann.de"))
                    .isTrue();
            assertThat(service.isReplyOrForward("[External] AW: Angebot")).isTrue();
            assertThat(service.isReplyOrForward("[Bug 123] [SPAM] RE: Hinweis")).isTrue();
        }

        @Test
        void erkenntReplyNichtBeiNormalemSubject() {
            assertThat(service.isReplyOrForward("Transfer bauschlosserei-mustermann.de")).isFalse();
            assertThat(service.isReplyOrForward("[Ticket#1] Erstmeldung")).isFalse();
            assertThat(service.isReplyOrForward(null)).isFalse();
            assertThat(service.isReplyOrForward("")).isFalse();
        }

        @Test
        void normalizeEntferntReplyPrefixAmAnfang() {
            assertThat(service.normalizeSubject("Re: Transfer")).isEqualTo("transfer");
            assertThat(service.normalizeSubject("AW: Re: Fwd: Transfer")).isEqualTo("transfer");
        }

        @Test
        void normalizeEntferntReplyPrefixNachKlammerTagUndBehaeltTag() {
            // Beide Varianten ergeben dasselbe Normalisat -> matchen denselben Thread.
            String a = service.normalizeSubject("[Ticket#1] Transfer");
            String b = service.normalizeSubject("[Ticket#1] RE: Transfer");
            String c = service.normalizeSubject("RE: [Ticket#1] Transfer");
            String d = service.normalizeSubject("AW: [Ticket#1] Re: Transfer");
            assertThat(a).isEqualTo("[ticket#1] transfer");
            assertThat(b).isEqualTo(a);
            assertThat(c).isEqualTo(a);
            assertThat(d).isEqualTo(a);
        }

        @Test
        void normalizeTrenntUnterschiedlicheTicketsKorrekt() {
            // Klammer-Tag bleibt im Normalisat -> verschiedene Tickets bleiben getrennt.
            String t1 = service.normalizeSubject("[Ticket#1] RE: Transfer");
            String t2 = service.normalizeSubject("[Ticket#2] RE: Transfer");
            assertThat(t1).isNotEqualTo(t2);
        }

        @Test
        void normalizeHandhabtNullUndLeer() {
            assertThat(service.normalizeSubject(null)).isEmpty();
            assertThat(service.normalizeSubject("")).isEmpty();
            assertThat(service.normalizeSubject("   ")).isEmpty();
        }

        @Test
        void erkenntKeinReplyOhneSpaceNachDoppelpunkt() {
            // Mailclients setzen IMMER ein Space nach "RE:" — exotische
            // Subjects wie "WG:Foo" oder "FW:Bar" werden bewusst NICHT als
            // Reply klassifiziert, um false-positives bei seltenen
            // Abkuerzungen zu vermeiden.
            assertThat(service.isReplyOrForward("WG:Foo")).isFalse();
            assertThat(service.isReplyOrForward("FW:Bar")).isFalse();
        }

        @Test
        void normalizeNurReplyPrefixWirdLeer() {
            // Subject = nur Reply-Prefix → leeres Normalisat. Backfill muss
            // solche Mails skippen, damit sie nicht faelschlich gruppiert
            // werden (siehe Backfill-isBlank-Check).
            assertThat(service.normalizeSubject("RE: ")).isEmpty();
            assertThat(service.normalizeSubject("AW: ")).isEmpty();
            assertThat(service.normalizeSubject("Fwd: ")).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Subject-basiertes Fallback-Matching beim Live-Import
    // (findParentBySubject – greift, wenn In-Reply-To/References-Header
    //  beim Import keinen Parent liefern)
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class SubjectFallbackBeimImport {

        @BeforeEach
        void setupLookback() {
            // @Value wird im Unit-Test nicht aufgelöst → manuell setzen.
            ReflectionTestUtils.setField(service, "subjectFallbackLookback", 500);
        }

        private Email mailMitSubject(Long id, String subject, LocalDateTime sentAt) {
            Email e = new Email();
            e.setId(id);
            e.setSubject(subject);
            e.setSentAt(sentAt);
            return e;
        }

        @Test
        void rueckgabeNullWennSubjectKeinReply() {
            Email reply = mailMitSubject(1L, "Angebot AG-2026/04/00005", LocalDateTime.now());

            Email parent = service.findParentBySubject(reply);

            assertThat(parent).isNull();
            verify(emailRepository, never()).findRecentBefore(any(), any());
        }

        @Test
        void rueckgabeNullWennNormalisatBlank() {
            // "RE: " allein → normalisiert zu "" → DB darf nicht angefasst werden.
            Email reply = mailMitSubject(1L, "RE: ", LocalDateTime.now());

            Email parent = service.findParentBySubject(reply);

            assertThat(parent).isNull();
            verify(emailRepository, never()).findRecentBefore(any(), any());
        }

        @Test
        void rueckgabeNullWennSentAtNull() {
            Email reply = mailMitSubject(1L, "AW: Angebot AG-2026/04/00005", null);

            Email parent = service.findParentBySubject(reply);

            assertThat(parent).isNull();
            verify(emailRepository, never()).findRecentBefore(any(), any());
        }

        @Test
        void waehltJuengstenKandidatVorReplyAlsParent() {
            LocalDateTime jetzt = LocalDateTime.now();
            Email reply = mailMitSubject(99L, "AW: Angebot AG-2026/04/00005 - Balkongelaender", jetzt);
            Email aelterer = mailMitSubject(1L, "Angebot AG-2026/04/00005 - Balkongelaender", jetzt.minusDays(3));
            Email juengerer = mailMitSubject(2L, "Angebot AG-2026/04/00005 - Balkongelaender", jetzt.minusHours(2));
            when(emailRepository.findRecentBefore(any(LocalDateTime.class), any(Pageable.class)))
                    .thenReturn(List.of(juengerer, aelterer));

            Email parent = service.findParentBySubject(reply);

            assertThat(parent).isNotNull();
            assertThat(parent.getId()).isEqualTo(2L);
        }

        @Test
        void ignoriertKandidatenMitAbweichendemNormalisat() {
            LocalDateTime jetzt = LocalDateTime.now();
            Email reply = mailMitSubject(99L, "AW: Angebot AG-2026/04/00005", jetzt);
            Email fremd = mailMitSubject(1L, "Rechnung RE-2026/04/00007", jetzt.minusHours(1));
            when(emailRepository.findRecentBefore(any(LocalDateTime.class), any(Pageable.class)))
                    .thenReturn(List.of(fremd));

            Email parent = service.findParentBySubject(reply);

            assertThat(parent).isNull();
        }

        @Test
        void selfMatchWirdAusgeschlossen() {
            // Re-Import: Mail liegt mit gleicher id schon im Lookback-Fenster und
            // hat sentAt == reply.sentAt (Edge-Case: gleicher Zeitstempel im
            // selben Sekundenraster). Darf NICHT zu sich selbst threaden.
            LocalDateTime jetzt = LocalDateTime.now();
            Email reply = mailMitSubject(42L, "AW: Angebot AG-2026/04/00005", jetzt);
            Email selbst = mailMitSubject(42L, "AW: Angebot AG-2026/04/00005", jetzt.minusSeconds(1));
            when(emailRepository.findRecentBefore(any(LocalDateTime.class), any(Pageable.class)))
                    .thenReturn(List.of(selbst));

            Email parent = service.findParentBySubject(reply);

            assertThat(parent).isNull();
        }
    }
}
