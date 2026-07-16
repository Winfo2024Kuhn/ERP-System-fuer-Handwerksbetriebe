package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.example.email.EmailService;
import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.EmailZuordnungTyp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class AnfrageBestaetigungVersandServiceTest {

    private EmailTextTemplateService emailTextTemplateService;
    private EmailSignatureService emailSignatureService;
    private SystemSettingsService systemSettingsService;
    private EmailOutboundPersistenceService outboundPersistenceService;
    private EmailService emailService;
    private AnfrageBestaetigungVersandService service;

    @BeforeEach
    void setUp() throws Exception {
        emailTextTemplateService = Mockito.mock(EmailTextTemplateService.class);
        emailSignatureService = Mockito.mock(EmailSignatureService.class);
        systemSettingsService = Mockito.mock(SystemSettingsService.class);
        outboundPersistenceService = Mockito.mock(EmailOutboundPersistenceService.class);
        emailService = Mockito.mock(EmailService.class);

        // Echte Spring-Bean-Verkabelung umgehen: wir ueberschreiben die
        // EmailService-Factory, damit der Test keinen echten SMTP-Connect macht.
        service = new AnfrageBestaetigungVersandService(
                emailTextTemplateService, emailSignatureService, systemSettingsService,
                outboundPersistenceService) {
            @Override
            EmailService baueEmailService() {
                return emailService;
            }
        };
        // Tests sollen bei Retry-Szenarien nicht echt schlafen.
        service.retryPauseMillis = 0L;

        given(systemSettingsService.isSmtpConfigured()).willReturn(true);
        given(systemSettingsService.getMailFromAddress()).willReturn("kontakt@example.de");
        given(emailSignatureService.appendSystemSignatureIfConfigured(anyString()))
                .willAnswer(inv -> inv.getArgument(0) + "<signatur/>");
        given(emailService.sendEmailAndReturnMessageId(
                anyString(), Mockito.any(), anyString(), anyString(), anyString(),
                Mockito.any(), Mockito.any()))
                .willReturn("<test-msgid@example.de>");
        given(outboundPersistenceService.existsByMessageId(anyString())).willReturn(false);
    }

    @Test
    void rendertVorlageMitFunnelDatenUndVersendetAnErstenEmpfaenger() throws Exception {
        Anfrage anfrage = baseAnfrage();
        given(emailTextTemplateService.render(eq("WEBSITE_ANFRAGE_BESTAETIGUNG"), any()))
                .willReturn(new EmailService.EmailContent(
                        "Anfrage erhalten – {{KUNDENNAME}}",
                        "<p>{{ANREDE}}</p><p>{{NACHRICHT}}</p>"));

        // Render erwartet die fertige Substitution — wir simulieren das hier,
        // indem wir Subject/Body bereits aufgeloest zurueckgeben (siehe oben)
        // und nur pruefen, dass der erwartete Kontext durchgereicht wurde.
        boolean ok = service.versendeBestaetigung(anfrage, "Max", "Mustermann", "Bitte um Angebot");

        assertThat(ok).isTrue();
        ArgumentCaptor<Map<String, String>> ctxCaptor = mapCaptor();
        verify(emailTextTemplateService).render(eq("WEBSITE_ANFRAGE_BESTAETIGUNG"), ctxCaptor.capture());
        Map<String, String> ctx = ctxCaptor.getValue();
        assertThat(ctx).containsEntry("ANREDE", "Hallo Max Mustermann");
        assertThat(ctx).containsEntry("KUNDENNAME", "Max Mustermann");
        assertThat(ctx).containsEntry("VORNAME", "Max");
        assertThat(ctx).containsEntry("NACHNAME", "Mustermann");
        assertThat(ctx).containsEntry("BAUVORHABEN", "Neubau - Wohnhaus");
        assertThat(ctx).containsEntry("NACHRICHT", "Bitte um Angebot");
        assertThat(ctx).containsEntry("ANFRAGE_DATUM", LocalDate.of(2026, 5, 11)
                .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")));
        assertThat(ctx).containsEntry("ANFRAGENUMMER", "42");

        verify(emailService).sendEmailAndReturnMessageId(
                eq("max@example.de"),
                eq(null),
                eq("kontakt@example.de"),
                eq("Anfrage erhalten – {{KUNDENNAME}}"),
                eq("<p>{{ANREDE}}</p><p>{{NACHRICHT}}</p><signatur/>"),
                eq(null),
                eq(null));
    }

    @Test
    void persistiertVersandteMailDirektMitAnfrageZuordnung() throws Exception {
        Anfrage anfrage = baseAnfrage();
        given(emailTextTemplateService.render(eq("WEBSITE_ANFRAGE_BESTAETIGUNG"), any()))
                .willReturn(new EmailService.EmailContent("Anfrage erhalten", "<p>Hallo</p>"));

        service.versendeBestaetigung(anfrage, "Max", "Mustermann", "Bitte um Angebot");

        ArgumentCaptor<Email> emailCaptor = ArgumentCaptor.forClass(Email.class);
        verify(outboundPersistenceService).speichereOutEmail(emailCaptor.capture());
        Email gespeichert = emailCaptor.getValue();
        assertThat(gespeichert.getAnfrage()).isSameAs(anfrage);
        assertThat(gespeichert.getZuordnungTyp()).isEqualTo(EmailZuordnungTyp.ANFRAGE);
        assertThat(gespeichert.getDirection()).isEqualTo(EmailDirection.OUT);
        assertThat(gespeichert.getMessageId()).isEqualTo("<test-msgid@example.de>");
        assertThat(gespeichert.getRecipient()).isEqualTo("max@example.de");
        assertThat(gespeichert.getFromAddress()).isEqualTo("kontakt@example.de");
        assertThat(gespeichert.isRead()).isTrue();
        assertThat(gespeichert.getSentAt()).isNotNull();
        assertThat(gespeichert.getImapFolder()).isNull();
    }

    @Test
    void persistenzFehlerWirdGeschlucktUndMailGiltTrotzdemAlsErfolgreich() throws Exception {
        // Wenn alle Speicherversuche scheitern (z.B. DB down), darf das nicht
        // propagieren — die SMTP-Mail ist beim Kunden bereits angekommen.
        Anfrage anfrage = baseAnfrage();
        given(emailTextTemplateService.render(anyString(), any()))
                .willReturn(new EmailService.EmailContent("Subject", "Body"));
        Mockito.doThrow(new RuntimeException("DB down"))
                .when(outboundPersistenceService).speichereOutEmail(any());

        boolean ok = service.versendeBestaetigung(anfrage, "Max", "Mustermann", "Hi");

        assertThat(ok).isTrue();
        // Alle drei Versuche wurden ausgeschoepft, bevor aufgegeben wurde.
        verify(outboundPersistenceService, Mockito.times(3)).speichereOutEmail(any());
    }

    @Test
    void persistenzWirdBeiLockTimeoutWiederholt() throws Exception {
        // Produktions-Szenario (anfrageId=146): der IMAP-Import-Job haelt Locks
        // auf der email-Tabelle, der erste Insert laeuft in "Lock wait timeout".
        // Der zweite Versuch nach kurzer Pause muss die Mail dann speichern —
        // der IMAP-Sent-Poll ist KEIN Sicherheitsnetz (T-Online legt SMTP-Mails
        // nicht zuverlaessig in INBOX.Sent ab).
        Anfrage anfrage = baseAnfrage();
        given(emailTextTemplateService.render(anyString(), any()))
                .willReturn(new EmailService.EmailContent("Subject", "Body"));
        Mockito.doThrow(new org.springframework.dao.PessimisticLockingFailureException("Lock wait timeout"))
                .doNothing()
                .when(outboundPersistenceService).speichereOutEmail(any());

        boolean ok = service.versendeBestaetigung(anfrage, "Max", "Mustermann", "Hi");

        assertThat(ok).isTrue();
        verify(outboundPersistenceService, Mockito.times(2)).speichereOutEmail(any());
    }

    @Test
    void duplikatWaehrendRetryBeendetDieVersuche() throws Exception {
        // DataIntegrityViolation = eine parallele Quelle hat die Message-ID
        // bereits angelegt — kein weiterer Versuch noetig.
        Anfrage anfrage = baseAnfrage();
        given(emailTextTemplateService.render(anyString(), any()))
                .willReturn(new EmailService.EmailContent("Subject", "Body"));
        Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate message_id"))
                .when(outboundPersistenceService).speichereOutEmail(any());

        boolean ok = service.versendeBestaetigung(anfrage, "Max", "Mustermann", "Hi");

        assertThat(ok).isTrue();
        verify(outboundPersistenceService, Mockito.times(1)).speichereOutEmail(any());
    }

    @Test
    void leereMessageIdLoestKeinenDbEintragAus() throws Exception {
        // Falls JavaMail keine Message-ID liefert (theoretischer Edge-Case),
        // ueberspringen wir den DB-Eintrag — ohne Message-ID keine Dedup-Basis.
        Anfrage anfrage = baseAnfrage();
        given(emailTextTemplateService.render(anyString(), any()))
                .willReturn(new EmailService.EmailContent("Subject", "Body"));
        given(emailService.sendEmailAndReturnMessageId(
                any(), any(), any(), any(), any(), any(), any()))
                .willReturn(null);

        boolean ok = service.versendeBestaetigung(anfrage, "Max", "Mustermann", "Hi");

        assertThat(ok).isTrue();
        verify(outboundPersistenceService, never()).speichereOutEmail(any());
    }

    @Test
    void duplikatPersistenzWirdUebersprungen() throws Exception {
        Anfrage anfrage = baseAnfrage();
        given(emailTextTemplateService.render(anyString(), any()))
                .willReturn(new EmailService.EmailContent("Subject", "Body"));
        given(outboundPersistenceService.existsByMessageId("<test-msgid@example.de>")).willReturn(true);

        boolean ok = service.versendeBestaetigung(anfrage, "Max", "Mustermann", "Hallo");

        assertThat(ok).isTrue();
        verify(outboundPersistenceService, never()).speichereOutEmail(any());
    }

    @Test
    void escapedNachrichtUmXssZuVerhindern() {
        Anfrage anfrage = baseAnfrage();
        given(emailTextTemplateService.render(anyString(), any()))
                .willReturn(new EmailService.EmailContent("Subject", "Body"));

        service.versendeBestaetigung(anfrage, "Max", "Mustermann",
                "<script>alert(1)</script>");

        ArgumentCaptor<Map<String, String>> ctxCaptor = mapCaptor();
        verify(emailTextTemplateService).render(anyString(), ctxCaptor.capture());
        assertThat(ctxCaptor.getValue().get("NACHRICHT"))
                .doesNotContain("<script>")
                .contains("&lt;script&gt;");
    }

    @Test
    void brichtAbWennKeineEmpfaengerMailHinterlegt() {
        Anfrage anfrage = baseAnfrage();
        anfrage.setKundenEmails(new ArrayList<>());

        boolean ok = service.versendeBestaetigung(anfrage, "Max", "Mustermann", "Hallo");

        assertThat(ok).isFalse();
        verify(emailTextTemplateService, never()).render(anyString(), any());
    }

    @Test
    void brichtAbWennKeineAktiveVorlageExistiert() throws Exception {
        Anfrage anfrage = baseAnfrage();
        given(emailTextTemplateService.render(anyString(), any())).willReturn(null);

        boolean ok = service.versendeBestaetigung(anfrage, "Max", "Mustermann", "Hallo");

        assertThat(ok).isFalse();
        verify(emailService, never()).sendEmailAndReturnMessageId(
                any(), any(), any(), any(), any(), any(), any());
        verify(outboundPersistenceService, never()).speichereOutEmail(any());
    }

    @Test
    void brichtAbWennSmtpNichtKonfiguriertIst() throws Exception {
        Anfrage anfrage = baseAnfrage();
        given(systemSettingsService.isSmtpConfigured()).willReturn(false);
        given(emailTextTemplateService.render(anyString(), any()))
                .willReturn(new EmailService.EmailContent("Subject", "Body"));

        boolean ok = service.versendeBestaetigung(anfrage, "Max", "Mustermann", "Hallo");

        assertThat(ok).isFalse();
        verify(emailService, never()).sendEmailAndReturnMessageId(
                any(), any(), any(), any(), any(), any(), any());
        verify(outboundPersistenceService, never()).speichereOutEmail(any());
    }

    @Test
    void schlucktExceptionsDamitFunnelPersistenzNichtScheitert() {
        Anfrage anfrage = baseAnfrage();
        given(emailTextTemplateService.render(anyString(), any()))
                .willThrow(new RuntimeException("DB down"));

        // Darf nicht propagieren — sonst rollbackt die Funnel-Transaktion.
        boolean ok = service.versendeBestaetigung(anfrage, "Max", "Mustermann", "Hallo");

        assertThat(ok).isFalse();
    }

    private static Anfrage baseAnfrage() {
        Anfrage a = new Anfrage();
        a.setId(42L);
        a.setBauvorhaben("Neubau - Wohnhaus");
        a.setAnlegedatum(LocalDate.of(2026, 5, 11));
        a.setKundenEmails(new ArrayList<>(List.of("max@example.de")));
        return a;
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Map<String, String>> mapCaptor() {
        return ArgumentCaptor.forClass(Map.class);
    }
}
