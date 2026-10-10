package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.repository.EmailAbsenderRepository;
import org.example.kalkulationsprogramm.repository.EmailPostfachZuordnungRepository;
import org.example.kalkulationsprogramm.service.mail.MailSecretService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostfachUmzugServiceTest {

    @Mock private EmailAbsenderRepository repository;
    @Mock private EmailPostfachZuordnungRepository zuordnungRepository;
    @Mock private SystemSettingsService systemSettingsService;
    @Mock private MailSecretService mailSecretService;

    @InjectMocks private PostfachUmzugService service;

    @BeforeEach
    void setUp() {
        lenient().when(mailSecretService.isConfigured()).thenReturn(true);
        lenient().when(mailSecretService.encrypt(anyString())).thenAnswer(inv -> "v1:" + inv.getArgument(0));
        lenient().when(repository.save(any(EmailAbsender.class))).thenAnswer(inv -> {
            EmailAbsender p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(11L);
            }
            return p;
        });
        lenient().when(systemSettingsService.gespeichertesStandardKonto()).thenReturn(new SystemSettingsService.MailKonto(
                "mail.example.com", 465, "info@example.com", "geheim", "info@example.com", "Musterbetrieb"));
        lenient().when(systemSettingsService.gespeicherterStandardImapZugang()).thenReturn(new SystemSettingsService.ImapZugang(
                "imap.example.com", 993, "info@example.com", "geheim"));
    }

    private static EmailAbsender postfach(Long id, String adresse) {
        EmailAbsender p = new EmailAbsender();
        p.setId(id);
        p.setEmailAdresse(adresse);
        return p;
    }

    @Nested
    class ZiehUm {

        @Test
        void standardKontoWirdHauptpostfachUndAlteMailsWandernMit() {
            when(repository.findFirstByHauptpostfachTrueOrderByIdAsc()).thenReturn(Optional.empty());
            when(repository.findByEmailAdresseIgnoreCase("info@example.com")).thenReturn(Optional.empty());
            when(zuordnungRepository.ordneOhnePostfachZu(11L)).thenReturn(1234);

            service.ziehUm();

            ArgumentCaptor<EmailAbsender> captor = ArgumentCaptor.forClass(EmailAbsender.class);
            verify(repository).save(captor.capture());
            EmailAbsender haupt = captor.getValue();
            assertThat(haupt.isHauptpostfach()).isTrue();
            assertThat(haupt.getEmailAdresse()).isEqualTo("info@example.com");
            assertThat(haupt.getAnzeigename()).isEqualTo("Musterbetrieb");
            assertThat(haupt.getBenutzername()).isNull();
            assertThat(haupt.getPasswortVerschluesselt()).isEqualTo("v1:geheim");
            assertThat(haupt.getSmtpHost()).isEqualTo("mail.example.com");
            assertThat(haupt.getImapHost()).isEqualTo("imap.example.com");
            verify(zuordnungRepository).ordneOhnePostfachZu(11L);
            verify(zuordnungRepository).ordneAusgangsmailsNachAbsenderZu();
        }

        @Test
        void ausgangsmailsNachAbsenderVorDemHauptpostfach() {
            when(repository.findFirstByHauptpostfachTrueOrderByIdAsc()).thenReturn(Optional.of(postfach(1L, "info@example.com")));
            when(repository.findFirstByFuerGeschaeftsdokumenteTrueAndAktivTrueOrderByIdAsc())
                    .thenReturn(Optional.of(postfach(2L, "rechnungen@example.com")));
            when(zuordnungRepository.ordneAusgangsmailsNachAbsenderZu()).thenReturn(12);

            service.ziehUm();

            org.mockito.InOrder reihenfolge = org.mockito.Mockito.inOrder(zuordnungRepository);
            reihenfolge.verify(zuordnungRepository).ordneAusgangsmailsNachAbsenderZu();
            reihenfolge.verify(zuordnungRepository).ordneOhnePostfachZu(1L);
        }

        @Test
        void vorhandeneAbsenderAdresseWirdZumHauptpostfach() {
            EmailAbsender vorhanden = postfach(4L, "info@example.com");
            vorhanden.setAnzeigename("Schon da");
            when(repository.findFirstByHauptpostfachTrueOrderByIdAsc()).thenReturn(Optional.empty());
            when(repository.findByEmailAdresseIgnoreCase("info@example.com")).thenReturn(Optional.of(vorhanden));

            service.ziehUm();

            assertThat(vorhanden.isHauptpostfach()).isTrue();
            assertThat(vorhanden.getAnzeigename()).isEqualTo("Schon da");
            verify(zuordnungRepository).ordneOhnePostfachZu(4L);
        }

        @Test
        void abweichenderLoginWirdAlsBenutzernameGemerkt() {
            when(systemSettingsService.gespeichertesStandardKonto()).thenReturn(new SystemSettingsService.MailKonto(
                    "mail.example.com", 0, "login@example.com", "geheim", "kein-at", ""));
            when(repository.findFirstByHauptpostfachTrueOrderByIdAsc()).thenReturn(Optional.empty());
            when(repository.findByEmailAdresseIgnoreCase("login@example.com")).thenReturn(Optional.empty());

            service.ziehUm();

            ArgumentCaptor<EmailAbsender> captor = ArgumentCaptor.forClass(EmailAbsender.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getEmailAdresse()).isEqualTo("login@example.com");
            assertThat(captor.getValue().getSmtpPort()).isNull();
        }

        @Test
        void ohneAltkontoKeinHauptpostfach() {
            when(systemSettingsService.gespeichertesStandardKonto()).thenReturn(new SystemSettingsService.MailKonto(
                    "", 465, "", "", "", ""));
            when(repository.findFirstByHauptpostfachTrueOrderByIdAsc()).thenReturn(Optional.empty());

            service.ziehUm();

            verify(repository, never()).save(any());
            verify(zuordnungRepository, never()).ordneOhnePostfachZu(anyLong());
        }

        @Test
        void altkontoOhneGueltigeAdresseWirdNichtUmgezogen() {
            when(systemSettingsService.gespeichertesStandardKonto()).thenReturn(new SystemSettingsService.MailKonto(
                    "mail.example.com", 465, "nur-login", "geheim", "auch-kein-at", ""));
            when(repository.findFirstByHauptpostfachTrueOrderByIdAsc()).thenReturn(Optional.empty());

            service.ziehUm();

            verify(repository, never()).save(any());
        }

        @Test
        void ohneSchluesselNurWarnung() {
            when(mailSecretService.isConfigured()).thenReturn(false);
            when(repository.findFirstByHauptpostfachTrueOrderByIdAsc()).thenReturn(Optional.empty());

            service.ziehUm();

            verify(repository, never()).save(any());
            verify(mailSecretService, never()).encrypt(anyString());
        }

        @Test
        void ohneSchluesselAberMitHauptpostfachWandernAlteMailsTrotzdem() {
            when(mailSecretService.isConfigured()).thenReturn(false);
            when(repository.findFirstByHauptpostfachTrueOrderByIdAsc()).thenReturn(Optional.of(postfach(1L, "info@example.com")));

            service.ziehUm();

            verify(zuordnungRepository).ordneOhnePostfachZu(1L);
        }

        @Test
        void zweiterLaufAendertNichts() {
            when(repository.findFirstByHauptpostfachTrueOrderByIdAsc()).thenReturn(Optional.of(postfach(1L, "info@example.com")));
            when(repository.findFirstByFuerGeschaeftsdokumenteTrueAndAktivTrueOrderByIdAsc())
                    .thenReturn(Optional.of(postfach(2L, "rechnungen@example.com")));
            when(zuordnungRepository.ordneOhnePostfachZu(1L)).thenReturn(0);

            service.ziehUm();

            verify(repository, never()).save(any());
        }

        @Test
        void dokumentKontoWirdRechnungsPostfach() {
            when(repository.findFirstByHauptpostfachTrueOrderByIdAsc()).thenReturn(Optional.of(postfach(1L, "info@example.com")));
            when(repository.findFirstByFuerGeschaeftsdokumenteTrueAndAktivTrueOrderByIdAsc()).thenReturn(Optional.empty());
            when(systemSettingsService.isDokumentMailKontoAktiv()).thenReturn(true);
            when(systemSettingsService.isDokumentMailKontoConfigured()).thenReturn(true);
            when(systemSettingsService.getDokumentMailFromAddress()).thenReturn("rechnungen@example.com");
            when(systemSettingsService.getDokumentSmtpUsername()).thenReturn("rechnungen@example.com");
            when(systemSettingsService.getDokumentSmtpPassword()).thenReturn("rechnung-pw");
            when(systemSettingsService.getDokumentSmtpHost()).thenReturn("mail.example.com");
            when(systemSettingsService.getDokumentSmtpPort()).thenReturn(465);
            when(systemSettingsService.getDokumentImapHost()).thenReturn("mail.example.com");
            when(systemSettingsService.getDokumentImapPort()).thenReturn(993);
            when(systemSettingsService.getDokumentMailAbsenderName()).thenReturn("Musterbetrieb Rechnungen");
            when(repository.findByEmailAdresseIgnoreCase("rechnungen@example.com")).thenReturn(Optional.empty());

            service.ziehUm();

            ArgumentCaptor<EmailAbsender> captor = ArgumentCaptor.forClass(EmailAbsender.class);
            verify(repository).save(captor.capture());
            EmailAbsender rechnung = captor.getValue();
            assertThat(rechnung.isFuerGeschaeftsdokumente()).isTrue();
            assertThat(rechnung.isHauptpostfach()).isFalse();
            assertThat(rechnung.getPasswortVerschluesselt()).isEqualTo("v1:rechnung-pw");
            assertThat(rechnung.getAnzeigename()).isEqualTo("Musterbetrieb Rechnungen");
        }

        @Test
        void dokumentKontoAufVorhandenemPostfachBehaeltDessenPasswort() {
            EmailAbsender vorhanden = postfach(6L, "rechnungen@example.com");
            vorhanden.setPasswortVerschluesselt("v1:schon-gesetzt");
            when(repository.findFirstByHauptpostfachTrueOrderByIdAsc()).thenReturn(Optional.of(postfach(1L, "info@example.com")));
            when(repository.findFirstByFuerGeschaeftsdokumenteTrueAndAktivTrueOrderByIdAsc()).thenReturn(Optional.empty());
            when(systemSettingsService.isDokumentMailKontoAktiv()).thenReturn(true);
            when(systemSettingsService.isDokumentMailKontoConfigured()).thenReturn(true);
            when(systemSettingsService.getDokumentMailFromAddress()).thenReturn("rechnungen@example.com");
            when(systemSettingsService.getDokumentMailAbsenderName()).thenReturn("");
            when(repository.findByEmailAdresseIgnoreCase("rechnungen@example.com")).thenReturn(Optional.of(vorhanden));

            service.ziehUm();

            assertThat(vorhanden.getPasswortVerschluesselt()).isEqualTo("v1:schon-gesetzt");
            assertThat(vorhanden.isFuerGeschaeftsdokumente()).isTrue();
        }

        @Test
        void ausgeschaltetesDokumentKontoBleibtWoEsIst() {
            when(repository.findFirstByHauptpostfachTrueOrderByIdAsc()).thenReturn(Optional.of(postfach(1L, "info@example.com")));
            when(repository.findFirstByFuerGeschaeftsdokumenteTrueAndAktivTrueOrderByIdAsc()).thenReturn(Optional.empty());
            when(systemSettingsService.isDokumentMailKontoAktiv()).thenReturn(false);

            service.ziehUm();

            verify(repository, never()).save(any());
        }

        @Test
        void dokumentKontoOhneAdresseBleibtWoEsIst() {
            when(repository.findFirstByHauptpostfachTrueOrderByIdAsc()).thenReturn(Optional.of(postfach(1L, "info@example.com")));
            when(repository.findFirstByFuerGeschaeftsdokumenteTrueAndAktivTrueOrderByIdAsc()).thenReturn(Optional.empty());
            when(systemSettingsService.isDokumentMailKontoAktiv()).thenReturn(true);
            when(systemSettingsService.isDokumentMailKontoConfigured()).thenReturn(true);
            when(systemSettingsService.getDokumentMailFromAddress()).thenReturn("");
            when(systemSettingsService.getDokumentSmtpUsername()).thenReturn("");

            service.ziehUm();

            verify(repository, never()).save(any());
        }
    }

    @Nested
    class Ersteinrichtung {

        private EmailAbsender haupt;

        @BeforeEach
        void hauptpostfach() {
            haupt = postfach(1L, "info@example.com");
            haupt.setHauptpostfach(true);
            haupt.setPasswortVerschluesselt("v1:alt");
            haupt.setSmtpHost("alt.example.com");
            haupt.setImapHost("alt-imap.example.com");
            lenient().when(repository.findFirstByHauptpostfachTrueOrderByIdAsc()).thenReturn(Optional.of(haupt));
        }

        @Test
        void versandUebernimmtNurSmtp() {
            service.gleicheHauptpostfachAb(PostfachUmzugService.Abgleich.VERSAND, true);

            assertThat(haupt.getSmtpHost()).isEqualTo("mail.example.com");
            assertThat(haupt.getImapHost()).isEqualTo("alt-imap.example.com");
            assertThat(haupt.getPasswortVerschluesselt()).isEqualTo("v1:geheim");
        }

        @Test
        void abrufUebernimmtNurImap() {
            service.gleicheHauptpostfachAb(PostfachUmzugService.Abgleich.ABRUF, true);

            assertThat(haupt.getImapHost()).isEqualTo("imap.example.com");
            assertThat(haupt.getSmtpHost()).isEqualTo("alt.example.com");
        }

        @Test
        void zugangUebernimmtNurAnmeldung() {
            when(systemSettingsService.gespeichertesStandardKonto()).thenReturn(new SystemSettingsService.MailKonto(
                    "neu.example.com", 465, "anderer-login@example.com", "", "info@example.com", ""));

            service.gleicheHauptpostfachAb(PostfachUmzugService.Abgleich.ZUGANG, true);

            assertThat(haupt.getBenutzername()).isEqualTo("anderer-login@example.com");
            assertThat(haupt.getPasswortVerschluesselt()).isEqualTo("v1:alt");
            assertThat(haupt.getSmtpHost()).isEqualTo("alt.example.com");
        }

        @Test
        void ohneNeuesPasswortUndGleicherServerBleibtDasPasswort() {
            haupt.setSmtpHost("mail.example.com");
            haupt.setImapHost("imap.example.com");

            service.gleicheHauptpostfachAb(PostfachUmzugService.Abgleich.VERSAND, false);
            service.gleicheHauptpostfachAb(PostfachUmzugService.Abgleich.ABRUF, false);
            service.gleicheHauptpostfachAb(PostfachUmzugService.Abgleich.ZUGANG, false);

            assertThat(haupt.getPasswortVerschluesselt()).isEqualTo("v1:alt");
        }

        @Test
        void neuerServerOhneNeuesPasswortVerwirftDasAlte() {
            // haupt hat alt.example.com, die Ersteinrichtung meldet mail.example.com
            service.gleicheHauptpostfachAb(PostfachUmzugService.Abgleich.VERSAND, false);

            assertThat(haupt.getSmtpHost()).isEqualTo("mail.example.com");
            assertThat(haupt.getPasswortVerschluesselt()).isNull();
        }

        @Test
        void ohneHauptpostfachLaeuftDerUmzug() {
            when(repository.findFirstByHauptpostfachTrueOrderByIdAsc()).thenReturn(Optional.empty());
            when(repository.findByEmailAdresseIgnoreCase("info@example.com")).thenReturn(Optional.empty());

            service.gleicheHauptpostfachAb(PostfachUmzugService.Abgleich.VERSAND, true);

            verify(zuordnungRepository).ordneOhnePostfachZu(11L);
        }

        @Test
        void ohneSchluesselNichts() {
            when(mailSecretService.isConfigured()).thenReturn(false);

            service.gleicheHauptpostfachAb(PostfachUmzugService.Abgleich.VERSAND, true);

            verify(repository, never()).save(any());
        }
    }
}
