package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.EmailPostfachZuordnung;
import org.example.kalkulationsprogramm.repository.EmailAbsenderRepository;
import org.example.kalkulationsprogramm.repository.EmailPostfachZuordnungRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.service.mail.PostfachZugang;
import org.example.kalkulationsprogramm.service.mail.PostfachZugangService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sun.mail.imap.IMAPFolder;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;

/** Abruf über mehrere Postfächer: Schleife, Status, Dedupe, Löschen. Ohne echten Mail-Server. */
@ExtendWith(MockitoExtension.class)
class EmailImportServicePostfachTest {

    @Mock private EmailRepository emailRepository;
    @Mock private SystemSettingsService systemSettingsService;
    @Mock private PostfachZugangService postfachZugangService;
    @Mock private PostfachService postfachService;
    @Mock private EmailAbsenderRepository emailAbsenderRepository;
    @Mock private EmailPostfachZuordnungRepository emailPostfachZuordnungRepository;

    @InjectMocks private EmailImportService service;

    /** Port 1 auf localhost: Verbindung wird sofort abgewiesen, kein Netz nötig. */
    private static PostfachZugang unerreichbar(Long id, boolean haupt) {
        return new PostfachZugang(id, "p" + id + "@example.com", null, "p" + id + "@example.com", "pw",
                "127.0.0.1", 1, "127.0.0.1", 1, haupt);
    }

    @BeforeEach
    void selbst() {
        service.setSelf(service);
    }

    @Nested
    class Abruf {

        @Test
        void ohnePostfachUndOhneAltkontoPassiertNichts() {
            when(postfachZugangService.abrufbarePostfaecher()).thenReturn(List.of());
            when(systemSettingsService.isImapConfigured()).thenReturn(false);

            assertThat(service.doImport()).isZero();
            verify(postfachService, never()).merkeAbruf(any(), any());
        }

        @Test
        void ohnePostfachGiltDasAltkonto() {
            when(postfachZugangService.abrufbarePostfaecher()).thenReturn(List.of());
            when(systemSettingsService.isImapConfigured()).thenReturn(true);
            when(systemSettingsService.getStandardImapZugang()).thenReturn(
                    new SystemSettingsService.ImapZugang("imap.example.com", 993, "alt@example.com", "pw"));

            List<PostfachZugang> zugaenge = service.abrufZugaenge();

            assertThat(zugaenge).singleElement().satisfies(z -> {
                assertThat(z.postfachId()).isNull();
                assertThat(z.hauptpostfach()).isTrue();
                assertThat(z.imapHost()).isEqualTo("imap.example.com");
                assertThat(z.benutzername()).isEqualTo("alt@example.com");
            });
        }

        @Test
        void jedesPostfachBekommtSeinenStatusAuchWennEsScheitert() {
            when(postfachZugangService.abrufbarePostfaecher()).thenReturn(List.of(unerreichbar(1L, true), unerreichbar(2L, false)));

            assertThat(service.doImport()).isZero();

            verify(postfachService).merkeAbruf(eq(1L), eq("Server nicht erreichbar – bitte Server und Port prüfen."));
            verify(postfachService).merkeAbruf(eq(2L), eq("Server nicht erreichbar – bitte Server und Port prüfen."));
        }

        @Test
        void unerwarteterFehlerWirdAuchGemerkt() {
            PostfachZugang kaputt = new PostfachZugang(3L, "p3@example.com", null, "p3@example.com", "pw",
                    null, 465, null, 993, false);
            when(postfachZugangService.abrufbarePostfaecher()).thenReturn(List.of(kaputt));

            service.doImport();

            verify(postfachService).merkeAbruf(eq(3L), anyString());
        }

        @Test
        void fehlerTexte() {
            assertThat(EmailImportService.abrufFehlerText(new AuthenticationFailedException("535")))
                    .startsWith("Anmeldung fehlgeschlagen");
            assertThat(EmailImportService.abrufFehlerText(new MessagingException("x", new java.net.UnknownHostException("h"))))
                    .startsWith("Server nicht gefunden");
            assertThat(EmailImportService.abrufFehlerText(new MessagingException("x", new java.net.SocketTimeoutException())))
                    .startsWith("Server nicht erreichbar");
            assertThat(EmailImportService.abrufFehlerText(new IllegalStateException("?")))
                    .isEqualTo("Abruf fehlgeschlagen – bitte die Verbindung testen.");
        }
    }

    @Nested
    class Dedupe {

        private Message nachricht(String messageId) throws MessagingException {
            Message msg = mock(Message.class);
            when(msg.getHeader("Message-ID")).thenReturn(new String[] { messageId });
            return msg;
        }

        @Test
        void bekannteMailBekommtNurDasZweitePostfach() throws Exception {
            Message msg = nachricht("<doppelt@example.org>");
            IMAPFolder folder = mock(IMAPFolder.class);
            when(folder.getFullName()).thenReturn("INBOX");
            when(folder.getUID(msg)).thenReturn(42L);
            Email vorhanden = new Email();
            vorhanden.setDirection(EmailDirection.IN);
            EmailAbsender max = new EmailAbsender();
            max.setId(5L);
            when(emailRepository.existsByMessageId("<doppelt@example.org>")).thenReturn(true);
            when(emailRepository.findByMessageId("<doppelt@example.org>")).thenReturn(Optional.of(vorhanden));
            when(emailAbsenderRepository.getReferenceById(5L)).thenReturn(max);

            assertThat(service.importMessage(msg, folder, EmailDirection.IN, 5L)).isFalse();

            assertThat(vorhanden.getPostfachZuordnungen()).singleElement()
                    .satisfies(z -> assertThat(z.getImapUid()).isEqualTo(42L));
            verify(emailRepository).save(vorhanden);
        }

        @Test
        void schonZugeordnetWirdNichtNochmalGespeichert() throws Exception {
            Message msg = nachricht("<doppelt@example.org>");
            IMAPFolder folder = mock(IMAPFolder.class);
            EmailAbsender max = new EmailAbsender();
            max.setId(5L);
            Email vorhanden = new Email();
            vorhanden.ordnePostfachZu(max, "INBOX", 1L);
            when(emailRepository.existsByMessageId("<doppelt@example.org>")).thenReturn(true);
            when(emailRepository.findByMessageId("<doppelt@example.org>")).thenReturn(Optional.of(vorhanden));
            when(emailAbsenderRepository.getReferenceById(5L)).thenReturn(max);

            service.importMessage(msg, folder, EmailDirection.IN, 5L);

            verify(emailRepository, never()).save(any());
        }

        @Test
        void ohnePostfachKeineZuordnung() throws Exception {
            Message msg = nachricht("<doppelt@example.org>");
            when(emailRepository.existsByMessageId("<doppelt@example.org>")).thenReturn(true);

            assertThat(service.importMessage(msg, mock(IMAPFolder.class), EmailDirection.IN)).isFalse();

            verify(emailRepository, never()).findByMessageId(anyString());
        }

        @Test
        void verschwundeneMailWirdIgnoriert() throws Exception {
            Message msg = nachricht("<weg@example.org>");
            when(emailRepository.existsByMessageId("<weg@example.org>")).thenReturn(true);
            when(emailRepository.findByMessageId("<weg@example.org>")).thenReturn(Optional.empty());

            assertThat(service.importMessage(msg, mock(IMAPFolder.class), EmailDirection.IN, 5L)).isFalse();
            verify(emailRepository, never()).save(any());
        }
    }

    @Nested
    class Loeschen {

        private Email mail() {
            Email email = new Email();
            email.setId(9L);
            email.setMessageId("<loeschen@example.org>");
            email.setImapFolder("INBOX");
            email.setImapUid(3L);
            return email;
        }

        @Test
        void ohneMessageIdNichts() {
            Email email = mail();
            email.setMessageId(null);

            service.deleteEmailFromServer(email);

            verify(emailPostfachZuordnungRepository, never()).findByEmailId(any());
        }

        @Test
        void alteMailOhnePostfachWieBisherImHauptkonto() {
            when(emailPostfachZuordnungRepository.findByEmailId(9L)).thenReturn(List.of());
            when(systemSettingsService.getStandardImapZugang())
                    .thenReturn(new SystemSettingsService.ImapZugang("", 993, "", ""));

            service.deleteEmailFromServer(mail());

            verify(systemSettingsService).getStandardImapZugang();
        }

        @Test
        void postfachOhneZugangWirdUebersprungenAndereVersucht() {
            EmailAbsender ohne = new EmailAbsender();
            ohne.setId(1L);
            EmailAbsender mit = new EmailAbsender();
            mit.setId(2L);
            EmailPostfachZuordnung z1 = new EmailPostfachZuordnung();
            z1.setPostfach(ohne);
            EmailPostfachZuordnung z2 = new EmailPostfachZuordnung();
            z2.setPostfach(mit);
            z2.setImapOrdner("INBOX");
            z2.setImapUid(4L);
            when(emailPostfachZuordnungRepository.findByEmailId(9L)).thenReturn(List.of(z1, z2));
            when(postfachZugangService.zugangVon(ohne)).thenReturn(new PostfachZugang(1L, "a@example.com", null,
                    "a@example.com", null, null, 465, null, 993, true));
            when(postfachZugangService.zugangVon(mit)).thenReturn(unerreichbar(2L, false));

            service.deleteEmailFromServer(mail());

            verify(postfachZugangService).zugangVon(mit);
            verify(systemSettingsService, never()).getStandardImapZugang();
        }

        @Test
        void zuordnungOhneOrdnerNimmtDenDerMail() {
            EmailAbsender mit = new EmailAbsender();
            mit.setId(2L);
            EmailPostfachZuordnung z = new EmailPostfachZuordnung();
            z.setPostfach(mit);
            when(emailPostfachZuordnungRepository.findByEmailId(9L)).thenReturn(List.of(z));
            when(postfachZugangService.zugangVon(mit)).thenReturn(unerreichbar(2L, true));

            service.deleteEmailFromServer(mail());

            verify(postfachZugangService).zugangVon(mit);
        }
    }
}
