package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.SystemSetting;
import org.example.kalkulationsprogramm.repository.SystemSettingRepository;
import org.example.kalkulationsprogramm.service.mail.PostfachZugang;
import org.example.kalkulationsprogramm.service.mail.PostfachZugangService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Postfächer schlagen die alten Konto-Einstellungen – solange sie einen vollständigen
 * Zugang haben. Sonst gilt der Stand aus den Einstellungen.
 */
class SystemSettingsServicePostfachTest {

    private SystemSettingRepository repository;
    private PostfachZugangService postfachZugangService;
    private SystemSettingsService service;

    private static final PostfachZugang INFO = new PostfachZugang(1L, "info@example.com", "Musterbetrieb",
            "info@example.com", "neu", "mail.example.com", 465, "mail.example.com", 993, true);
    private static final PostfachZugang RECHNUNGEN = new PostfachZugang(2L, "rechnungen@example.com", "",
            "rechnungen@example.com", "r-pw", "mail.example.com", 465, null, 993, false);

    private final Map<String, String> alt = Map.of(
            "smtp.host", "alt-smtp.example.com",
            "smtp.port", "587",
            "smtp.username", "alt@example.com",
            "smtp.password", "alt-pw",
            "imap.host", "alt-imap.example.com",
            "imap.port", "143",
            "mail.from-address", "kein-at",
            "mail.absender-name", "Alter Name");

    @BeforeEach
    void setUp() {
        repository = mock(SystemSettingRepository.class);
        postfachZugangService = mock(PostfachZugangService.class);
        lenient().when(repository.findById(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            if (!alt.containsKey(key)) {
                return Optional.empty();
            }
            SystemSetting setting = new SystemSetting();
            setting.setKey(key);
            setting.setValue(alt.get(key));
            return Optional.of(setting);
        });
        service = new SystemSettingsService(repository, mock(org.springframework.core.env.Environment.class),
                postfachZugangService);
    }

    @Nested
    class MitHauptpostfach {

        @BeforeEach
        void hauptpostfach() {
            when(postfachZugangService.hauptpostfachVersand()).thenReturn(Optional.of(INFO));
            lenient().when(postfachZugangService.hauptpostfachAbruf()).thenReturn(Optional.of(INFO));
        }

        @Test
        void versandGetterLiefernHauptpostfach() {
            assertThat(service.getSmtpHost()).isEqualTo("mail.example.com");
            assertThat(service.getSmtpPort()).isEqualTo(465);
            assertThat(service.getSmtpUsername()).isEqualTo("info@example.com");
            assertThat(service.getSmtpPassword()).isEqualTo("neu");
            assertThat(service.getMailFromAddress()).isEqualTo("info@example.com");
            assertThat(service.getMailAbsenderName()).isEqualTo("Musterbetrieb");
            assertThat(service.isSmtpConfigured()).isTrue();
        }

        @Test
        void abrufGetterLiefernHauptpostfach() {
            assertThat(service.getImapHost()).isEqualTo("mail.example.com");
            assertThat(service.getImapPort()).isEqualTo(993);
            assertThat(service.getImapUsername()).isEqualTo("info@example.com");
            assertThat(service.getImapPassword()).isEqualTo("neu");
            assertThat(service.isImapConfigured()).isTrue();
        }

        @Test
        void standardKontoKommtAusDemHauptpostfach() {
            SystemSettingsService.MailKonto konto = service.getStandardMailKonto();

            assertThat(konto.host()).isEqualTo("mail.example.com");
            assertThat(konto.fromAddress()).isEqualTo("info@example.com");
            assertThat(konto.fromName()).isEqualTo("Musterbetrieb");
        }

        @Test
        void gespeichertesKontoIgnoriertDasHauptpostfach() {
            SystemSettingsService.MailKonto konto = service.gespeichertesStandardKonto();

            assertThat(konto.host()).isEqualTo("alt-smtp.example.com");
            assertThat(konto.port()).isEqualTo(587);
            assertThat(konto.fromAddress()).isEqualTo("alt@example.com");
            SystemSettingsService.ImapZugang imap = service.gespeicherterStandardImapZugang();
            assertThat(imap.host()).isEqualTo("alt-imap.example.com");
            assertThat(imap.port()).isEqualTo(143);
            assertThat(imap.username()).isEqualTo("alt@example.com");
            assertThat(imap.password()).isEqualTo("alt-pw");
        }
    }

    @Nested
    class OhnePostfach {

        @Test
        void alteEinstellungenGeltenWeiter() {
            when(postfachZugangService.hauptpostfachVersand()).thenReturn(Optional.empty());
            when(postfachZugangService.hauptpostfachAbruf()).thenReturn(Optional.empty());

            assertThat(service.getSmtpHost()).isEqualTo("alt-smtp.example.com");
            assertThat(service.getSmtpPort()).isEqualTo(587);
            assertThat(service.getMailFromAddress()).isEqualTo("alt@example.com");
            assertThat(service.getMailAbsenderName()).isEqualTo("Alter Name");
            assertThat(service.getImapHost()).isEqualTo("alt-imap.example.com");
            assertThat(service.getImapPort()).isEqualTo(143);
            assertThat(service.getStandardMailKonto().username()).isEqualTo("alt@example.com");
        }

        @Test
        void ohneZugangsServiceAuchDieAltenEinstellungen() {
            SystemSettingsService ohne = new SystemSettingsService(repository,
                    mock(org.springframework.core.env.Environment.class));

            assertThat(ohne.getSmtpHost()).isEqualTo("alt-smtp.example.com");
            assertThat(ohne.nutztDokumentMailKonto()).isFalse();
        }

        @Test
        void hauptpostfachOhneAnzeigenameErbtDenAlten() {
            PostfachZugang ohneName = new PostfachZugang(1L, "info@example.com", " ", "info@example.com", "neu",
                    "mail.example.com", 465, "mail.example.com", 993, true);
            when(postfachZugangService.hauptpostfachVersand()).thenReturn(Optional.of(ohneName));

            assertThat(service.getMailAbsenderName()).isEqualTo("Alter Name");
            assertThat(service.getStandardMailKonto().fromName()).isEqualTo("Alter Name");
        }
    }

    @Nested
    class Rechnungen {

        @Test
        void rechnungsPostfachSchlaegtDokumentKonto() {
            when(postfachZugangService.geschaeftsdokumentVersand()).thenReturn(Optional.of(RECHNUNGEN));

            assertThat(service.nutztDokumentMailKonto()).isTrue();
            SystemSettingsService.MailKonto konto = service.getDokumentMailKonto();
            assertThat(konto.fromAddress()).isEqualTo("rechnungen@example.com");
            assertThat(konto.fromName()).isEqualTo("Alter Name");
        }

        @Test
        void ohnePosteingangKeineKopieImFalschenPostfach() {
            when(postfachZugangService.geschaeftsdokumentVersand()).thenReturn(Optional.of(RECHNUNGEN));

            SystemSettingsService.ImapZugang zugang = service.getDokumentImapZugang();

            assertThat(zugang.host()).isNull();
            assertThat(zugang.username()).isEqualTo("rechnungen@example.com");
        }

        @Test
        void ohneRechnungsPostfachWieBisher() {
            when(postfachZugangService.geschaeftsdokumentVersand()).thenReturn(Optional.empty());
            when(postfachZugangService.hauptpostfachVersand()).thenReturn(Optional.of(INFO));
            lenient().when(postfachZugangService.hauptpostfachAbruf()).thenReturn(Optional.of(INFO));

            assertThat(service.nutztDokumentMailKonto()).isFalse();
            assertThat(service.getDokumentMailKonto().fromAddress()).isEqualTo("info@example.com");
            assertThat(service.getDokumentImapZugang().host()).isEqualTo("mail.example.com");
        }
    }

    @Nested
    class AlteEinstellungenRoh {

        private SystemSettingsService mit(Map<String, String> werte) {
            SystemSettingRepository repo = mock(SystemSettingRepository.class);
            when(repo.findById(anyString())).thenAnswer(inv -> {
                String key = inv.getArgument(0);
                if (!werte.containsKey(key)) {
                    return Optional.empty();
                }
                SystemSetting setting = new SystemSetting();
                setting.setKey(key);
                setting.setValue(werte.get(key));
                return Optional.of(setting);
            });
            return new SystemSettingsService(repo, mock(org.springframework.core.env.Environment.class));
        }

        @Test
        void gueltigeAbsenderAdresseUndKaputteZahlen() {
            SystemSettingsService s = mit(Map.of(
                    "smtp.username", "login@example.com",
                    "smtp.port", "abc",
                    "mail.from-address", "info@example.com",
                    "imap.port", "-5",
                    "imap.username", "imap@example.com",
                    "imap.password", "imap-pw"));

            SystemSettingsService.MailKonto konto = s.gespeichertesStandardKonto();
            assertThat(konto.fromAddress()).isEqualTo("info@example.com");
            assertThat(konto.port()).isZero();
            SystemSettingsService.ImapZugang imap = s.gespeicherterStandardImapZugang();
            assertThat(imap.port()).isEqualTo(993);
            assertThat(imap.username()).isEqualTo("imap@example.com");
            assertThat(imap.password()).isEqualTo("imap-pw");
        }

        @Test
        void leererAbsenderNimmtDenLogin() {
            SystemSettingsService s = mit(Map.of("smtp.username", "login@example.com", "smtp.port", "465"));

            assertThat(s.gespeichertesStandardKonto().fromAddress()).isEqualTo("login@example.com");
            assertThat(s.gespeichertesStandardKonto().port()).isEqualTo(465);
        }
    }

    @Nested
    class Uebersicht {

        @Test
        void passwoerterNurAlsGesetzt() {
            when(postfachZugangService.hauptpostfachVersand()).thenReturn(Optional.of(INFO));
            lenient().when(postfachZugangService.hauptpostfachAbruf()).thenReturn(Optional.empty());
            lenient().when(postfachZugangService.geschaeftsdokumentVersand()).thenReturn(Optional.empty());

            Map<String, String> alle = service.getAllSettings();

            assertThat(alle.get("smtp.password")).isEqualTo("gesetzt");
            // Ohne IMAP-Postfach: altes IMAP-Passwort fehlt, Rückfall auf das SMTP-Passwort des Hauptpostfachs.
            assertThat(alle.get("imap.password")).isEqualTo("gesetzt");
            assertThat(alle.get("smtp.dokumente.password")).isEmpty();
            assertThat(alle.values()).noneMatch(v -> v != null && v.contains("neu"));
        }
    }
}
