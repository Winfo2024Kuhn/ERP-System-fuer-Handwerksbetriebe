package org.example.kalkulationsprogramm.service.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.ServerSpamStatus;
import org.example.kalkulationsprogramm.repository.EmailAbsenderRepository;
import org.example.kalkulationsprogramm.repository.EmailPostfachZuordnungRepository;
import org.example.kalkulationsprogramm.service.PostfachService;
import org.example.kalkulationsprogramm.dto.Email.ServerSpamKandidat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

import com.sun.mail.imap.AppendUID;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPStore;

import jakarta.mail.Flags;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;

@ExtendWith(MockitoExtension.class)
class ServerSpamAbgleichServiceTest {

    @Mock private PostfachZugangService postfachZugangService;
    @Mock private PostfachService postfachService;
    @Mock private EmailAbsenderRepository postfachRepository;
    @Mock private EmailPostfachZuordnungRepository zuordnungRepository;

    @InjectMocks private ServerSpamAbgleichService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "emailFeaturesEnabled", true);
    }

    private static PostfachZugang zugang(Long postfachId, String imapHost, int imapPort) {
        return new PostfachZugang(postfachId, "info@example.com", "Musterbetrieb", "info@example.com", "geheim",
                null, PostfachZugang.STANDARD_SMTP_PORT, imapHost, imapPort, true);
    }

    private static ServerSpamKandidat kandidat(long zuordnungId, Long uid) {
        return new ServerSpamKandidat(zuordnungId, "INBOX", uid, "<werbung@example.org>",
                "werbung@example.org", "Sonderangebot");
    }

    private static EmailAbsender postfach(long id, boolean verschieben) {
        EmailAbsender p = new EmailAbsender();
        p.setId(id);
        p.setEmailAdresse("info@example.com");
        p.setSpamAufServerVerschieben(verschieben);
        return p;
    }

    @Test
    void kandidatVerraetBeimLoggenKeinePersonendaten() {
        assertThat(kandidat(9L, 1L).toString())
                .contains("zuordnungId=9", "INBOX")
                .doesNotContain("werbung@example.org", "Sonderangebot");
    }

    @Nested
    class Abgleich {

        @Test
        void ohneMailFunktionenPassiertNichts() {
            ReflectionTestUtils.setField(service, "emailFeaturesEnabled", false);

            service.gleicheAb();

            verifyNoInteractions(postfachZugangService, postfachRepository, zuordnungRepository, postfachService);
        }

        @Test
        void fehlerInEinemPostfachHaeltDieAnderenNichtAuf() {
            when(postfachZugangService.abrufbarePostfaecher())
                    .thenReturn(List.of(zugang(1L, "mail.example.com", 993), zugang(2L, "mail.example.com", 993)));
            when(postfachRepository.findById(1L)).thenThrow(new IllegalStateException("Datenbank kurz weg"));
            when(postfachRepository.findById(2L)).thenReturn(Optional.of(postfach(2L, false)));

            service.gleicheAb();

            verify(postfachRepository).findById(2L);
        }

        @Test
        void rueckfallKontoOhnePostfachWirdUebersprungen() throws Exception {
            assertThat(service.gleichePostfachAb(zugang(null, "mail.example.com", 993))).isZero();
            verifyNoInteractions(postfachRepository, zuordnungRepository);
        }

        @Test
        void unbekanntesPostfachFragtNichtsAb() throws Exception {
            when(postfachRepository.findById(Long.MAX_VALUE)).thenReturn(Optional.empty());

            assertThat(service.gleichePostfachAb(zugang(Long.MAX_VALUE, "mail.example.com", 993))).isZero();

            verifyNoInteractions(zuordnungRepository);
        }

        @Test
        void ausgeschaltetVerschiebtNichtsNeuesHoltAberWeiterZurueck() throws Exception {
            when(postfachRepository.findById(3L)).thenReturn(Optional.of(postfach(3L, false)));
            when(zuordnungRepository.findZurueckZuHolen(3L, ServerSpamStatus.VERSCHOBEN, Limit.of(100)))
                    .thenReturn(List.of());

            assertThat(service.gleichePostfachAb(zugang(3L, "mail.example.com", 993))).isZero();

            verify(zuordnungRepository, never()).findSpamZumVerschieben(any(), any(), any(), any());
            verify(zuordnungRepository).findZurueckZuHolen(3L, ServerSpamStatus.VERSCHOBEN, Limit.of(100));
        }

        @Test
        void ohneOffeneArbeitWirdKeineVerbindungAufgebaut() throws Exception {
            when(postfachRepository.findById(4L)).thenReturn(Optional.of(postfach(4L, true)));
            when(zuordnungRepository.findSpamZumVerschieben(4L, "INBOX", EmailDirection.IN, Limit.of(100)))
                    .thenReturn(List.of());
            when(zuordnungRepository.findZurueckZuHolen(4L, ServerSpamStatus.VERSCHOBEN, Limit.of(100)))
                    .thenReturn(List.of());

            // Ein nicht erreichbarer Server würde beim Verbinden scheitern – hier wird gar nicht verbunden.
            assertThat(service.gleichePostfachAb(zugang(4L, "127.0.0.1", 1))).isZero();
            verify(postfachService, never()).merkeSpamAbgleich(any(), any(), any());
        }

        @Test
        void verbindungsfehlerWirdAmPostfachVermerkt() {
            when(postfachRepository.findById(5L)).thenReturn(Optional.of(postfach(5L, true)));
            when(zuordnungRepository.findSpamZumVerschieben(5L, "INBOX", EmailDirection.IN, Limit.of(100)))
                    .thenReturn(List.of(kandidat(9L, 1L)));
            when(zuordnungRepository.findZurueckZuHolen(5L, ServerSpamStatus.VERSCHOBEN, Limit.of(100)))
                    .thenReturn(List.of());

            assertThatThrownBy(() -> service.gleichePostfachAb(zugang(5L, "127.0.0.1", 1)))
                    .isInstanceOf(jakarta.mail.MessagingException.class);
            verify(postfachService).merkeSpamAbgleich(5L, null, ServerSpamAbgleichService.VERBINDUNG_FEHLGESCHLAGEN);
        }
    }

    @Nested
    class Ordnerliste {

        @ParameterizedTest
        @ValueSource(longs = { 0L, -1L })
        void ungueltigeIdIstNichtGefunden(long id) {
            assertThatThrownBy(() -> service.serverOrdner(id)).isInstanceOf(NoSuchElementException.class);
            verifyNoInteractions(postfachRepository);
        }

        @Test
        void ohneIdIstNichtGefunden() {
            assertThatThrownBy(() -> service.serverOrdner(null)).isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void unbekanntesPostfachIstNichtGefunden() {
            when(postfachRepository.findById(Long.MAX_VALUE)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.serverOrdner(Long.MAX_VALUE))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessage("Postfach nicht gefunden.");
        }

        @Test
        void ohneZugangGibtEsEinenVerstaendlichenSatz() {
            EmailAbsender p = postfach(6L, true);
            when(postfachRepository.findById(6L)).thenReturn(Optional.of(p));
            when(postfachZugangService.zugangVon(p)).thenReturn(zugang(6L, null, 993));

            assertThatThrownBy(() -> service.serverOrdner(6L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(ServerSpamAbgleichService.KEIN_ZUGANG);
        }

        @Test
        void serverNichtErreichbarGibtEinenVerstaendlichenSatz() {
            EmailAbsender p = postfach(7L, true);
            when(postfachRepository.findById(7L)).thenReturn(Optional.of(p));
            when(postfachZugangService.zugangVon(p)).thenReturn(zugang(7L, "127.0.0.1", 1));

            assertThatThrownBy(() -> service.serverOrdner(7L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(ServerSpamAbgleichService.VERBINDUNG_FEHLGESCHLAGEN);
        }
    }

    @Nested
    class Verschieben {

        @Mock private IMAPStore store;
        @Mock private IMAPFolder posteingang;
        @Mock private IMAPFolder spam;
        @Mock private Message msg;

        @BeforeEach
        void ordner() throws Exception {
            lenient().when(store.getFolder("INBOX")).thenReturn(posteingang);
            lenient().when(store.getFolder("Junk")).thenReturn(spam);
            lenient().when(posteingang.exists()).thenReturn(true);
            lenient().when(spam.exists()).thenReturn(true);
            lenient().when(spam.getFullName()).thenReturn("Junk");
            lenient().when(posteingang.isOpen()).thenReturn(true);
            lenient().when(posteingang.getMessageByUID(1L)).thenReturn(msg);
            lenient().when(msg.getHeader("Message-ID")).thenReturn(new String[] { "<werbung@example.org>" });
        }

        @Test
        void mitMoveWirdVerschobenUndDieNeueUidGemerkt() throws Exception {
            when(store.hasCapability("MOVE")).thenReturn(true);
            when(posteingang.moveUIDMessages(any(), eq(spam))).thenReturn(new AppendUID[] { new AppendUID(1L, 77L) });

            var ergebnis = service.verschiebe(store, "INBOX", "Junk", List.of(kandidat(9L, 1L)),
                    ServerSpamStatus.VERSCHOBEN);

            assertThat(ergebnis.verschoben()).isEqualTo(1);
            verify(zuordnungRepository).merkeServerOrt(9L, "Junk", 77L, ServerSpamStatus.VERSCHOBEN);
            verify(posteingang).close(false);
            verify(posteingang, never()).expunge();
        }

        @Test
        void ohneMoveAberMitUidplusWirdNurDieseEineMailEntfernt() throws Exception {
            when(store.hasCapability("MOVE")).thenReturn(false);
            when(store.hasCapability("UIDPLUS")).thenReturn(true);
            when(posteingang.copyUIDMessages(any(), eq(spam))).thenReturn(new AppendUID[] { new AppendUID(1L, 78L) });

            service.verschiebe(store, "INBOX", "Junk", List.of(kandidat(9L, 1L)), ServerSpamStatus.VERSCHOBEN);

            verify(msg).setFlag(Flags.Flag.DELETED, true);
            org.mockito.ArgumentCaptor<Message[]> nur = org.mockito.ArgumentCaptor.forClass(Message[].class);
            verify(posteingang).expunge(nur.capture());
            assertThat(nur.getValue()).containsExactly(msg);
            verify(posteingang, never()).expunge();
            verify(posteingang, never()).close(true);
            verify(zuordnungRepository).merkeServerOrt(9L, "Junk", 78L, ServerSpamStatus.VERSCHOBEN);
        }

        @Test
        void ohneMoveUndUidplusBleibtDasOriginalNurMarkiert() throws Exception {
            when(store.hasCapability("MOVE")).thenReturn(false);
            when(store.hasCapability("UIDPLUS")).thenReturn(false);

            service.verschiebe(store, "INBOX", "Junk", List.of(kandidat(9L, 1L)), ServerSpamStatus.VERSCHOBEN);

            verify(posteingang).copyMessages(any(), eq(spam));
            verify(msg).setFlag(Flags.Flag.DELETED, true);
            verify(posteingang, never()).expunge();
            verify(posteingang, never()).expunge(any());
            verify(zuordnungRepository).merkeServerOrt(9L, "Junk", null, ServerSpamStatus.VERSCHOBEN);
        }

        @Test
        void zumLoeschenMarkierteMailZaehltNichtAlsGefunden() throws Exception {
            when(msg.isSet(Flags.Flag.DELETED)).thenReturn(true);
            when(posteingang.search(any())).thenReturn(new Message[] { msg });

            var ergebnis = service.verschiebe(store, "INBOX", "Junk", List.of(kandidat(9L, 1L)),
                    ServerSpamStatus.VERSCHOBEN);

            assertThat(ergebnis.verschoben()).isZero();
            verify(zuordnungRepository).merkeServerSpamStatus(9L, ServerSpamStatus.NICHT_GEFUNDEN);
        }

        @Test
        void perSucheGefundeneMailMussAuchAbsenderUndBetreffTreffen() throws Exception {
            Message fremde = org.mockito.Mockito.mock(Message.class);
            when(fremde.getHeader("Message-ID")).thenReturn(new String[] { "<werbung@example.org>" });
            when(fremde.getFrom()).thenReturn(new jakarta.mail.Address[] {
                    new jakarta.mail.internet.InternetAddress("betrueger@example.net") });
            lenient().when(fremde.getSubject()).thenReturn("Sonderangebot");
            when(posteingang.search(any())).thenReturn(new Message[] { fremde });

            service.verschiebe(store, "INBOX", "Junk", List.of(kandidat(9L, null)), ServerSpamStatus.VERSCHOBEN);

            verify(zuordnungRepository).merkeServerSpamStatus(9L, ServerSpamStatus.NICHT_GEFUNDEN);
            verify(posteingang, never()).moveUIDMessages(any(), any());
            verify(posteingang, never()).copyMessages(any(), any());
        }

        @Test
        void dauerhaftAbgelehnteMailWirdNachDreiVersuchenNichtMehrVersucht() throws Exception {
            when(store.hasCapability("MOVE")).thenReturn(true);
            when(posteingang.moveUIDMessages(any(), eq(spam)))
                    .thenThrow(new MessagingException("NO [OVERQUOTA] Postfach voll"));

            for (int lauf = 1; lauf <= 2; lauf++) {
                var ergebnis = service.verschiebe(store, "INBOX", "Junk", List.of(kandidat(9L, 1L)),
                        ServerSpamStatus.VERSCHOBEN);
                assertThat(ergebnis.fehlgeschlagen()).isEqualTo(1);
            }
            verify(zuordnungRepository, never()).merkeServerSpamStatus(9L, ServerSpamStatus.FEHLGESCHLAGEN);

            service.verschiebe(store, "INBOX", "Junk", List.of(kandidat(9L, 1L)), ServerSpamStatus.VERSCHOBEN);

            verify(zuordnungRepository).merkeServerSpamStatus(9L, ServerSpamStatus.FEHLGESCHLAGEN);
            verify(zuordnungRepository, never()).merkeServerOrt(any(), any(), any(), any());
        }

        @Test
        void fehlenderQuellordnerMarkiertAlleAlsNichtGefunden() throws Exception {
            IMAPFolder weg = org.mockito.Mockito.mock(IMAPFolder.class);
            when(store.getFolder("Alt")).thenReturn(weg);
            when(weg.exists()).thenReturn(false);

            var ergebnis = service.verschiebe(store, "Alt", "INBOX",
                    List.of(kandidat(9L, 1L), kandidat(10L, 2L)), null);

            assertThat(ergebnis.verschoben()).isZero();
            verify(zuordnungRepository).merkeServerSpamStatus(9L, ServerSpamStatus.NICHT_GEFUNDEN);
            verify(zuordnungRepository).merkeServerSpamStatus(10L, ServerSpamStatus.NICHT_GEFUNDEN);
        }
    }
}
