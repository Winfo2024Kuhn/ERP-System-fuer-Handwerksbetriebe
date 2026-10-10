package org.example.kalkulationsprogramm.service.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.repository.EmailAbsenderRepository;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostfachZugangServiceTest {

    @Mock private EmailAbsenderRepository repository;
    @Mock private MailSecretService mailSecretService;

    @InjectMocks private PostfachZugangService service;

    private static EmailAbsender postfach(Long id, String adresse, boolean haupt, boolean mitZugang) {
        EmailAbsender p = new EmailAbsender();
        p.setId(id);
        p.setEmailAdresse(adresse);
        p.setHauptpostfach(haupt);
        if (mitZugang) {
            p.setPasswortVerschluesselt("v1:" + id);
            p.setSmtpHost(" mail.example.com ");
            p.setImapHost("mail.example.com");
        }
        return p;
    }

    @Test
    void hauptpostfachMitZugangWirdEntschluesselt() {
        EmailAbsender info = postfach(1L, "info@example.com", true, true);
        info.setAnzeigename("Musterbetrieb");
        when(repository.findFirstByHauptpostfachTrueOrderByIdAsc()).thenReturn(Optional.of(info));
        when(mailSecretService.decrypt("v1:1")).thenReturn("geheim");

        PostfachZugang zugang = service.hauptpostfachVersand().orElseThrow();

        assertThat(zugang.passwort()).isEqualTo("geheim");
        assertThat(zugang.smtpHost()).isEqualTo("mail.example.com");
        assertThat(zugang.smtpPort()).isEqualTo(465);
        assertThat(zugang.imapPort()).isEqualTo(993);
        assertThat(zugang.benutzername()).isEqualTo("info@example.com");
        SystemSettingsService.MailKonto konto = zugang.mailKonto();
        assertThat(konto.fromAddress()).isEqualTo("info@example.com");
        assertThat(konto.fromName()).isEqualTo("Musterbetrieb");
        assertThat(zugang.imapZugang().host()).isEqualTo("mail.example.com");
    }

    @Test
    void hauptpostfachAbrufBrauchtImapServer() {
        EmailAbsender info = postfach(1L, "info@example.com", true, true);
        info.setImapHost("  ");
        when(repository.findFirstByHauptpostfachTrueOrderByIdAsc()).thenReturn(Optional.of(info));
        when(mailSecretService.decrypt("v1:1")).thenReturn("geheim");

        assertThat(service.hauptpostfachAbruf()).isEmpty();
    }

    @Test
    void ausgeschaltetesHauptpostfachZaehltNicht() {
        EmailAbsender info = postfach(1L, "info@example.com", true, true);
        info.setAktiv(false);
        when(repository.findFirstByHauptpostfachTrueOrderByIdAsc()).thenReturn(Optional.of(info));

        assertThat(service.hauptpostfachVersand()).isEmpty();
    }

    @Test
    void unlesbaresPasswortGiltAlsNichtGesetzt() {
        EmailAbsender info = postfach(1L, "info@example.com", true, true);
        when(repository.findFirstByHauptpostfachTrueOrderByIdAsc()).thenReturn(Optional.of(info));
        when(mailSecretService.decrypt("v1:1")).thenThrow(new IllegalStateException("Schlüssel fehlt"));

        assertThat(service.hauptpostfachVersand()).isEmpty();
    }

    @Test
    void postfachOhnePasswortHatKeinenZugang() {
        PostfachZugang zugang = service.zugangVon(postfach(3L, "max@example.com", false, false));

        assertThat(zugang.hatVersandZugang()).isFalse();
        assertThat(zugang.hatAbrufZugang()).isFalse();
        assertThat(zugang.smtpHost()).isNull();
        assertThat(zugang.mailKonto().fromName()).isEmpty();
    }

    @Test
    void eigenerBenutzernameUndPortsWerdenUebernommen() {
        EmailAbsender max = postfach(3L, "max@example.com", false, true);
        max.setBenutzername("m.mustermann@example.com");
        max.setSmtpPort(587);
        max.setImapPort(0);
        when(mailSecretService.decrypt("v1:3")).thenReturn("pw");

        PostfachZugang zugang = service.zugangVon(max);

        assertThat(zugang.benutzername()).isEqualTo("m.mustermann@example.com");
        assertThat(zugang.smtpPort()).isEqualTo(587);
        assertThat(zugang.imapPort()).isEqualTo(993);
    }

    @Test
    void geschaeftsdokumentPostfachNurMitVersandZugang() {
        EmailAbsender rechnungen = postfach(2L, "rechnungen@example.com", false, true);
        when(repository.findFirstByFuerGeschaeftsdokumenteTrueAndAktivTrueOrderByIdAsc()).thenReturn(Optional.of(rechnungen));
        when(mailSecretService.decrypt("v1:2")).thenReturn("pw");

        assertThat(service.geschaeftsdokumentVersand()).map(PostfachZugang::emailAdresse)
                .contains("rechnungen@example.com");
    }

    @Test
    void abrufbarePostfaecherHauptpostfachZuerst() {
        EmailAbsender max = postfach(3L, "max@example.com", false, true);
        EmailAbsender info = postfach(1L, "info@example.com", true, true);
        EmailAbsender ohne = postfach(4L, "ohne@example.com", false, false);
        when(repository.findByAktivTrueOrderBySortierungAscIdAsc()).thenReturn(List.of(max, ohne, info));
        when(mailSecretService.decrypt("v1:3")).thenReturn("a");
        when(mailSecretService.decrypt("v1:1")).thenReturn("b");

        assertThat(service.abrufbarePostfaecher()).extracting(PostfachZugang::emailAdresse)
                .containsExactly("info@example.com", "max@example.com");
    }
}
