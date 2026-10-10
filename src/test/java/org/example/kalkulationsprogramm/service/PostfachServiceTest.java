package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.dto.Postfach.AbsenderPostfachDto;
import org.example.kalkulationsprogramm.dto.Postfach.PostfachDto;
import org.example.kalkulationsprogramm.dto.Postfach.PostfachSpeichernRequest;
import org.example.kalkulationsprogramm.dto.Postfach.PostfachTestErgebnis;
import org.example.kalkulationsprogramm.dto.Postfach.PostfachTestRequest;
import org.example.kalkulationsprogramm.repository.EmailAbsenderRepository;
import org.example.kalkulationsprogramm.repository.EmailPostfachZuordnungRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.service.mail.MailSecretService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostfachServiceTest {

    @Mock private EmailAbsenderRepository repository;
    @Mock private EmailPostfachZuordnungRepository zuordnungRepository;
    @Mock private FrontendUserProfileRepository frontendUserProfileRepository;
    @Mock private MailSecretService mailSecretService;
    @Mock private SystemSettingsService systemSettingsService;

    @InjectMocks private PostfachService service;

    private final List<EmailAbsender> gespeichert = new ArrayList<>();

    @BeforeEach
    void setUp() {
        lenient().when(repository.save(any(EmailAbsender.class))).thenAnswer(inv -> {
            EmailAbsender p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(100L + gespeichert.size());
            }
            gespeichert.add(p);
            return p;
        });
        lenient().when(mailSecretService.encrypt(anyString())).thenAnswer(inv -> "v1:" + inv.getArgument(0));
        lenient().when(repository.findFirstByHauptpostfachTrueOrderByIdAsc()).thenReturn(Optional.of(postfach(1L, "info@example.com", true)));
    }

    private static EmailAbsender postfach(Long id, String adresse, boolean haupt) {
        EmailAbsender p = new EmailAbsender();
        p.setId(id);
        p.setEmailAdresse(adresse);
        p.setHauptpostfach(haupt);
        return p;
    }

    private static PostfachSpeichernRequest request(String adresse) {
        return new PostfachSpeichernRequest(adresse, "Max Mustermann", true, 10, false, false,
                null, "geheim", "mail.example.com", 465, "mail.example.com", 993);
    }

    private static PostfachSpeichernRequest request(String adresse, Boolean aktiv, Boolean haupt, Boolean rechnung,
            String benutzer, String passwort) {
        return new PostfachSpeichernRequest(adresse, null, aktiv, null, haupt, rechnung, benutzer, passwort,
                "mail.example.com", 465, "mail.example.com", 993);
    }

    @Nested
    class Anlegen {

        @Test
        void speichertPasswortNurVerschluesselt() {
            PostfachDto dto = service.anlegen(request("max@example.com"));

            ArgumentCaptor<EmailAbsender> captor = ArgumentCaptor.forClass(EmailAbsender.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getPasswortVerschluesselt()).isEqualTo("v1:geheim");
            assertThat(dto.passwortGesetzt()).isTrue();
            assertThat(dto.abrufAktiv()).isTrue();
            assertThat(dto.emailAdresse()).isEqualTo("max@example.com");
            assertThat(dto.anzeigename()).isEqualTo("Max Mustermann");
            assertThat(dto.zugewieseneBenutzer()).isEmpty();
        }

        @Test
        void ohnePasswortBleibtEntwurfOhneAbruf() {
            PostfachDto dto = service.anlegen(request("max@example.com", null, null, null, null, "  "));

            assertThat(dto.passwortGesetzt()).isFalse();
            assertThat(dto.abrufAktiv()).isFalse();
            assertThat(dto.aktiv()).isTrue();
            verify(mailSecretService, never()).encrypt(anyString());
        }

        @Test
        void erstesPostfachWirdAutomatischHauptpostfach() {
            when(repository.findFirstByHauptpostfachTrueOrderByIdAsc()).thenReturn(Optional.empty());
            when(repository.findByHauptpostfachTrue()).thenReturn(List.of());

            PostfachDto dto = service.anlegen(request("info@example.com"));

            assertThat(dto.hauptpostfach()).isTrue();
        }

        @Test
        void neuesHauptpostfachNimmtDemAltenDenHaken() {
            EmailAbsender alt = postfach(1L, "info@example.com", true);
            when(repository.findByHauptpostfachTrue()).thenAnswer(inv -> List.of(alt, gespeichert.get(0)));

            PostfachDto dto = service.anlegen(request("buero@example.com", true, true, false, null, null));

            assertThat(dto.hauptpostfach()).isTrue();
            assertThat(alt.isHauptpostfach()).isFalse();
        }

        @Test
        void neuesRechnungsPostfachNimmtDemAltenDenHaken() {
            EmailAbsender alt = postfach(2L, "alt-rechnung@example.com", false);
            alt.setFuerGeschaeftsdokumente(true);
            when(repository.findByFuerGeschaeftsdokumenteTrue()).thenAnswer(inv -> List.of(alt, gespeichert.get(0)));

            PostfachDto dto = service.anlegen(request("rechnungen@example.com", true, false, true, null, null));

            assertThat(dto.fuerGeschaeftsdokumente()).isTrue();
            assertThat(alt.isFuerGeschaeftsdokumente()).isFalse();
        }

        @Test
        void doppelteAdresseWirdAbgelehnt() {
            when(repository.findByEmailAdresseIgnoreCase("INFO@example.com"))
                    .thenReturn(Optional.of(postfach(1L, "info@example.com", true)));

            assertThatThrownBy(() -> service.anlegen(request("INFO@example.com")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("bereits als Postfach angelegt");
        }

        @ParameterizedTest
        @ValueSource(strings = { "kein-at-zeichen", "<script>alert(1)</script>", "'; DROP TABLE email; --", "a@b" })
        void ungueltigeAdressenWerdenAbgelehnt(String adresse) {
            assertThatThrownBy(() -> service.anlegen(request(adresse)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("gültige E-Mail-Adresse");
            verify(repository, never()).save(any());
        }

        @Test
        void leereAdresseWirdAbgelehnt() {
            assertThatThrownBy(() -> service.anlegen(request("   ")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("E-Mail-Adresse des Postfachs eintragen");
        }

        @Test
        void fehlendeDatenWerdenAbgelehnt() {
            assertThatThrownBy(() -> service.anlegen(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Daten fehlen.");
        }

        @Test
        void ueberlangeEingabenWerdenAbgelehnt() {
            String lang = "x".repeat(10_001);
            PostfachSpeichernRequest r = new PostfachSpeichernRequest("max@example.com", lang, true, null, false,
                    false, null, null, null, null, null, null);

            assertThatThrownBy(() -> service.anlegen(r))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("zu lang");
        }

        @Test
        void anzeigenameMitSkriptWirdAlsTextGespeichert() {
            PostfachSpeichernRequest r = new PostfachSpeichernRequest("max@example.com", "<script>alert(1)</script>",
                    true, null, false, false, null, null, null, null, null, null);

            PostfachDto dto = service.anlegen(r);

            // Wird nie als HTML ausgegeben – das Frontend rendert ihn als Text.
            assertThat(dto.anzeigename()).isEqualTo("<script>alert(1)</script>");
        }

        @ParameterizedTest
        @ValueSource(ints = { 0, -1, 65536 })
        void ungueltigePortsWerdenAbgelehnt(int port) {
            PostfachSpeichernRequest r = new PostfachSpeichernRequest("max@example.com", null, true, null, false,
                    false, null, null, "mail.example.com", port, null, null);

            assertThatThrownBy(() -> service.anlegen(r))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Port");
        }

        @Test
        void ungueltigerImapPortWirdAbgelehnt() {
            PostfachSpeichernRequest r = new PostfachSpeichernRequest("max@example.com", null, true, null, false,
                    false, null, null, null, null, "mail.example.com", 70000);

            assertThatThrownBy(() -> service.anlegen(r)).hasMessageContaining("Port");
        }

        @Test
        void anmeldenameAufFremderDomainWirdAbgelehnt() {
            assertThatThrownBy(() -> service.anlegen(request("max@example.com", true, false, false,
                    "max@fremd.example.org", "geheim")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("selben Domain");
        }

        @Test
        void anmeldenameOhneAdresseWirdAbgelehnt() {
            assertThatThrownBy(() -> service.anlegen(request("max@example.com", true, false, false, "max", "geheim")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("vollständige Adresse");
        }

        @Test
        void anmeldenameAufGleicherDomainWirdGespeichert() {
            PostfachDto dto = service.anlegen(request("max@example.com", true, false, false,
                    "  m.mustermann@example.com ", "geheim"));

            assertThat(dto.benutzername()).isEqualTo("m.mustermann@example.com");
        }

        @Test
        void hauptpostfachDarfNichtAusgeschaltetWerden() {
            assertThatThrownBy(() -> service.anlegen(request("info2@example.com", false, true, false, null, null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nicht ausgeschaltet");
        }

        @Test
        void ohneSchluesselGibtEsEineVerstaendlicheMeldung() {
            when(mailSecretService.encrypt(anyString())).thenThrow(new IllegalStateException("kein Schlüssel"));

            assertThatThrownBy(() -> service.anlegen(request("max@example.com")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Schlüssel für Mailzugänge");
        }

        @Test
        void sortierungBleibtOhneAngabe() {
            PostfachDto dto = service.anlegen(request("max@example.com", true, false, false, null, null));

            assertThat(dto.sortierung()).isZero();
        }
    }

    @Nested
    class Aendern {

        @Test
        void serverwechselOhneNeuesPasswortWirdAbgelehnt() {
            EmailAbsender max = postfach(5L, "max@example.com", false);
            max.setPasswortVerschluesselt("v1:alt");
            max.setSmtpHost("mail.example.com");
            max.setImapHost("mail.example.com");
            when(repository.findById(5L)).thenReturn(Optional.of(max));

            PostfachSpeichernRequest fremderServer = new PostfachSpeichernRequest("max@example.com", null, true, null,
                    false, false, null, null, "mail.example.com", 465, "imap.angreifer.example.org", 993);
            assertThatThrownBy(() -> service.aendern(5L, fremderServer))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Passwort erneut eingeben");
            PostfachSpeichernRequest fremderLogin = new PostfachSpeichernRequest("max@example.com", null, true, null,
                    false, false, "anders@example.com", "", "mail.example.com", 465, "mail.example.com", 993);
            assertThatThrownBy(() -> service.aendern(5L, fremderLogin)).hasMessageContaining("Passwort erneut eingeben");
            assertThat(max.getImapHost()).isEqualTo("mail.example.com");
            verify(repository, never()).save(any());
        }

        @Test
        void serverwechselMitNeuemPasswortIstErlaubt() {
            EmailAbsender max = postfach(5L, "max@example.com", false);
            max.setPasswortVerschluesselt("v1:alt");
            max.setSmtpHost("alt.example.com");
            when(repository.findById(5L)).thenReturn(Optional.of(max));

            service.aendern(5L, request("max@example.com", true, false, false, null, "neu"));

            assertThat(max.getSmtpHost()).isEqualTo("mail.example.com");
            assertThat(max.getPasswortVerschluesselt()).isEqualTo("v1:neu");
        }

        @Test
        void leeresPasswortBehaeltGespeichertes() {
            EmailAbsender max = postfach(5L, "max@example.com", false);
            max.setPasswortVerschluesselt("v1:alt");
            max.setSmtpHost("mail.example.com");
            max.setImapHost("mail.example.com");
            when(repository.findById(5L)).thenReturn(Optional.of(max));
            when(repository.findByEmailAdresseIgnoreCase("max@example.com")).thenReturn(Optional.of(max));

            PostfachDto dto = service.aendern(5L, request("max@example.com", true, false, false, null, ""));

            assertThat(max.getPasswortVerschluesselt()).isEqualTo("v1:alt");
            assertThat(dto.passwortGesetzt()).isTrue();
        }

        @Test
        void eigeneAdresseKollidiertNichtMitSichSelbst() {
            EmailAbsender max = postfach(5L, "max@example.com", false);
            when(repository.findById(5L)).thenReturn(Optional.of(max));
            when(repository.findByEmailAdresseIgnoreCase("max@example.com")).thenReturn(Optional.of(max));
            FrontendUserProfile benutzer = new FrontendUserProfile();
            benutzer.setId(7L);
            benutzer.setDisplayName("Max Mustermann");
            when(frontendUserProfileRepository.findByEmailAbsenderId(5L)).thenReturn(List.of(benutzer));

            PostfachDto dto = service.aendern(5L, request("max@example.com"));

            assertThat(dto.zugewieseneBenutzer()).extracting(PostfachDto.BenutzerRefDto::displayName)
                    .containsExactly("Max Mustermann");
        }

        @Test
        void adresseEinesAnderenPostfachsWirdAbgelehnt() {
            EmailAbsender max = postfach(5L, "max@example.com", false);
            when(repository.findById(5L)).thenReturn(Optional.of(max));
            when(repository.findByEmailAdresseIgnoreCase("info@example.com"))
                    .thenReturn(Optional.of(postfach(1L, "info@example.com", true)));

            assertThatThrownBy(() -> service.aendern(5L, request("info@example.com")))
                    .hasMessageContaining("bereits als Postfach angelegt");
        }

        @Test
        void einzigesHauptpostfachKannNichtAbgewaehltWerden() {
            EmailAbsender info = postfach(1L, "info@example.com", true);
            when(repository.findById(1L)).thenReturn(Optional.of(info));

            assertThatThrownBy(() -> service.aendern(1L, request("info@example.com", true, false, false, null, null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Es muss ein Hauptpostfach geben");
        }

        @ParameterizedTest
        @ValueSource(longs = { 0L, -1L })
        void ungueltigeIdIstNichtGefunden(long id) {
            assertThatThrownBy(() -> service.aendern(id, request("max@example.com")))
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void unbekannteIdIstNichtGefunden() {
            when(repository.findById(Long.MAX_VALUE)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.aendern(Long.MAX_VALUE, request("max@example.com")))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessage("Postfach nicht gefunden.");
        }

        @Test
        void eintragOhneIdIstNichtGefunden() {
            assertThatThrownBy(() -> service.eins(null)).isInstanceOf(NoSuchElementException.class);
        }
    }

    @Nested
    class Loeschen {

        @Test
        void hauptpostfachKannNichtGeloeschtWerden() {
            when(repository.findById(1L)).thenReturn(Optional.of(postfach(1L, "info@example.com", true)));

            assertThatThrownBy(() -> service.loeschen(1L))
                    .hasMessage("Das Hauptpostfach kann nicht gelöscht werden.");
        }

        @Test
        void postfachMitMailsKannNichtGeloeschtWerden() {
            when(repository.findById(5L)).thenReturn(Optional.of(postfach(5L, "max@example.com", false)));
            when(zuordnungRepository.existsByPostfachId(5L)).thenReturn(true);

            assertThatThrownBy(() -> service.loeschen(5L))
                    .hasMessageContaining("bitte stattdessen ausschalten");
            verify(repository, never()).delete(any());
        }

        @Test
        void loeschenLoestBenutzerZuordnung() {
            EmailAbsender max = postfach(5L, "max@example.com", false);
            when(repository.findById(5L)).thenReturn(Optional.of(max));
            FrontendUserProfile benutzer = new FrontendUserProfile();
            benutzer.setEmailAbsender(max);
            when(frontendUserProfileRepository.findByEmailAbsenderId(5L)).thenReturn(List.of(benutzer));

            service.loeschen(5L);

            assertThat(benutzer.getEmailAbsender()).isNull();
            verify(frontendUserProfileRepository).save(benutzer);
            verify(repository).delete(max);
        }
    }

    @Nested
    class Lesen {

        @Test
        void alleOrdnetBenutzerZuUndLiefertKeinPasswort() {
            EmailAbsender info = postfach(1L, "info@example.com", true);
            info.setPasswortVerschluesselt("v1:geheim");
            info.setImapHost("mail.example.com");
            info.setLetzterAbrufAm(LocalDateTime.of(2026, 10, 10, 9, 41));
            EmailAbsender max = postfach(5L, "max@example.com", false);
            FrontendUserProfile benutzer = new FrontendUserProfile();
            benutzer.setId(7L);
            benutzer.setDisplayName("Max Mustermann");
            benutzer.setEmailAbsender(max);
            when(frontendUserProfileRepository.findByEmailAbsenderIsNotNull()).thenReturn(List.of(benutzer));
            when(repository.findAllByOrderBySortierungAscIdAsc()).thenReturn(List.of(info, max));

            List<PostfachDto> alle = service.alle();

            assertThat(alle).hasSize(2);
            assertThat(alle.get(0).abrufAktiv()).isTrue();
            assertThat(alle.get(0).zugewieseneBenutzer()).isEmpty();
            assertThat(alle.get(1).zugewieseneBenutzer()).extracting(PostfachDto.BenutzerRefDto::id).containsExactly(7L);
            assertThat(alle.toString()).doesNotContain("geheim");
        }

        @Test
        void ausgeschaltetesPostfachRuftNichtAb() {
            EmailAbsender max = postfach(5L, "max@example.com", false);
            max.setAktiv(false);
            max.setPasswortVerschluesselt("v1:x");
            max.setImapHost("mail.example.com");
            when(repository.findById(5L)).thenReturn(Optional.of(max));

            assertThat(service.eins(5L).abrufAktiv()).isFalse();
        }

        @Test
        void absenderAuswahlEigenesZuerstDannHauptpostfach() {
            EmailAbsender rechnungen = postfach(2L, "rechnungen@example.com", false);
            EmailAbsender info = postfach(1L, "info@example.com", true);
            EmailAbsender max = postfach(5L, "max@example.com", false);
            when(repository.findByAktivTrueOrderBySortierungAscIdAsc()).thenReturn(List.of(rechnungen, info, max));

            List<AbsenderPostfachDto> auswahl = service.absenderAuswahl(5L);

            assertThat(auswahl).extracting(AbsenderPostfachDto::emailAdresse)
                    .containsExactly("max@example.com", "info@example.com", "rechnungen@example.com");
            assertThat(auswahl.get(0).eigenes()).isTrue();
            assertThat(auswahl.get(1).hauptpostfach()).isTrue();
        }

        @Test
        void aktivesPostfach() {
            EmailAbsender aus = postfach(5L, "max@example.com", false);
            aus.setAktiv(false);
            when(repository.findById(5L)).thenReturn(Optional.of(aus));

            assertThat(service.aktivesPostfach(5L)).isEmpty();
            assertThat(service.aktivesPostfach(null)).isEmpty();
        }
    }

    @Nested
    class Abruf {

        @Test
        void merktFehlerUndZeitpunkt() {
            EmailAbsender max = postfach(5L, "max@example.com", false);
            when(repository.findById(5L)).thenReturn(Optional.of(max));

            service.merkeAbruf(5L, "x".repeat(600));

            assertThat(max.getLetzterAbrufAm()).isNotNull();
            assertThat(max.getLetzterAbrufFehler()).hasSize(500);
        }

        @Test
        void erfolgLoeschtAltenFehler() {
            EmailAbsender max = postfach(5L, "max@example.com", false);
            max.setLetzterAbrufFehler("Anmeldung fehlgeschlagen");
            when(repository.findById(5L)).thenReturn(Optional.of(max));

            service.merkeAbruf(5L, null);

            assertThat(max.getLetzterAbrufFehler()).isNull();
        }

        @Test
        void ohnePostfachIdPassiertNichts() {
            service.merkeAbruf(null, "egal");

            verify(repository, never()).findById(any());
        }
    }

    @Nested
    class Verbindungstest {

        @Test
        void nutztUebergebeneDaten() {
            when(systemSettingsService.testSmtp("mail.example.com", 465, "max@example.com", "neu", "test@example.com"))
                    .thenReturn(SystemSettingsService.TestResult.success("SMTP ok"));
            when(systemSettingsService.testImap("mail.example.com", 993, "max@example.com", "neu"))
                    .thenReturn(SystemSettingsService.TestResult.success("IMAP ok"));

            PostfachTestErgebnis ergebnis = service.teste(new PostfachTestRequest(null, "max@example.com", "neu",
                    "mail.example.com", null, "mail.example.com", null, " test@example.com "));

            assertThat(ergebnis.versandOk()).isTrue();
            assertThat(ergebnis.abrufOk()).isTrue();
            assertThat(ergebnis.message()).contains("SMTP ok").contains("IMAP ok");
        }

        @Test
        void nutztGespeichertesPasswortUndAnmeldenamen() {
            EmailAbsender max = postfach(5L, "max@example.com", false);
            max.setPasswortVerschluesselt("v1:alt");
            max.setSmtpHost("mail.example.com");
            when(repository.findById(5L)).thenReturn(Optional.of(max));
            when(mailSecretService.decrypt("v1:alt")).thenReturn("alt");
            when(systemSettingsService.testSmtp(eq("mail.example.com"), eq(465), eq("max@example.com"), eq("alt"), isNull()))
                    .thenReturn(SystemSettingsService.TestResult.failure("Anmeldung fehlgeschlagen"));

            PostfachTestErgebnis ergebnis = service.teste(new PostfachTestRequest(5L, " ", "", "mail.example.com", 465,
                    null, null, null));

            assertThat(ergebnis.versandOk()).isFalse();
            assertThat(ergebnis.abrufOk()).isFalse();
            assertThat(ergebnis.message()).contains("Kein Posteingangs-Server");
        }

        @Test
        void gespeichertesPasswortNichtAnFremdenServer() {
            EmailAbsender max = postfach(5L, "max@example.com", false);
            max.setPasswortVerschluesselt("v1:alt");
            max.setSmtpHost("mail.example.com");
            when(repository.findById(5L)).thenReturn(Optional.of(max));

            assertThatThrownBy(() -> service.teste(new PostfachTestRequest(5L, null, null,
                    "angreifer.example.org", 465, null, null, null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Passwort erneut eingeben");
            assertThatThrownBy(() -> service.teste(new PostfachTestRequest(5L, "fremd@example.com", null,
                    "mail.example.com", 465, null, null, null)))
                    .hasMessageContaining("Passwort erneut eingeben");
            assertThatThrownBy(() -> service.teste(new PostfachTestRequest(5L, null, null,
                    "mail.example.com", 465, "imap.angreifer.example.org", null, null)))
                    .hasMessageContaining("Passwort erneut eingeben");
            verify(mailSecretService, never()).decrypt(anyString());
        }

        @Test
        void gleich() {
            assertThat(PostfachService.gleich(" Mail.Example.com ", "mail.example.com")).isTrue();
            assertThat(PostfachService.gleich(null, "  ")).isTrue();
            assertThat(PostfachService.gleich("a", null)).isFalse();
        }

        @Test
        void ohneServerKeinTest() {
            PostfachTestErgebnis ergebnis = service.teste(new PostfachTestRequest(null, "max@example.com", "pw",
                    null, null, null, null, null));

            assertThat(ergebnis.message()).contains("Kein Server für den Versand");
            verify(systemSettingsService, never()).testSmtp(anyString(), anyInt(), anyString(), anyString(), any());
        }

        @Test
        void ohnePasswortGibtEsEinenHinweis() {
            EmailAbsender max = postfach(5L, "max@example.com", false);
            max.setPasswortVerschluesselt("v1:kaputt");
            max.setSmtpHost("mail.example.com");
            when(repository.findById(5L)).thenReturn(Optional.of(max));
            when(mailSecretService.decrypt("v1:kaputt")).thenThrow(new IllegalStateException("nicht lesbar"));

            PostfachTestErgebnis ergebnis = service.teste(new PostfachTestRequest(5L, null, null,
                    "mail.example.com", null, null, null, null));

            assertThat(ergebnis.message()).isEqualTo("Bitte Adresse und Passwort des Postfachs eintragen.");
        }

        @Test
        void ohneAlles() {
            assertThat(service.teste(new PostfachTestRequest(null, null, null, null, null, null, null, null)).message())
                    .contains("Adresse und Passwort");
        }

        @Test
        void fehlendeDaten() {
            assertThatThrownBy(() -> service.teste(null)).hasMessage("Daten fehlen.");
        }

        @Test
        void ueberlangerServerName() {
            assertThatThrownBy(() -> service.teste(new PostfachTestRequest(null, "max@example.com", "pw",
                    "h".repeat(300), null, null, null, null)))
                    .hasMessageContaining("zu lang");
        }
    }

    @Test
    void domainVon() {
        assertThat(PostfachService.domainVon("Max@Example.COM")).isEqualTo("example.com");
        assertThat(PostfachService.domainVon("ohne-at")).isEmpty();
        assertThat(PostfachService.domainVon(null)).isEmpty();
    }
}
