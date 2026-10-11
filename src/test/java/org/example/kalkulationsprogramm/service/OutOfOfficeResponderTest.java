package org.example.kalkulationsprogramm.service;

import org.example.email.EmailService;
import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.domain.EmailSignature;
import org.example.kalkulationsprogramm.domain.OooReplyLog;
import org.example.kalkulationsprogramm.domain.OutOfOfficeSchedule;
import org.example.kalkulationsprogramm.repository.EmailAbsenderRepository;
import org.example.kalkulationsprogramm.repository.OooReplyLogRepository;
import org.example.kalkulationsprogramm.repository.OutOfOfficeScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class OutOfOfficeResponderTest {

    private OutOfOfficeScheduleRepository repository;
    private EmailSignatureService emailSignatureService;
    private EmailService mailSender;
    private SystemSettingsService systemSettingsService;
    private OooReplyLogRepository replyLogRepository;
    private EmailAbsenderRepository postfachRepository;
    private PostfachVersandService versandService;
    private OutOfOfficeResponder responder;
    private EmailAbsender info;

    private static final Long INFO_ID = 5L;

    private static OutOfOfficeResponder.IncomingMail incoming(String from, String subject) {
        return mail(INFO_ID, from, subject, false, false, null, null, null);
    }

    private static OutOfOfficeResponder.IncomingMail mail(Long postfachId, String from, String subject,
            boolean spam, boolean newsletter, String autoSubmitted, String precedence, String listId) {
        return new OutOfOfficeResponder.IncomingMail(postfachId,
                from, subject, LocalDateTime.now(), spam, newsletter, autoSubmitted, precedence, listId);
    }

    private static EmailAbsender postfach(Long id, String adresse) {
        EmailAbsender p = new EmailAbsender();
        p.setId(id);
        p.setEmailAdresse(adresse);
        return p;
    }

    /** Versand über {@code postfach}: Absender-Adresse ist die des Postfachs. */
    private void versandUeber(EmailAbsender postfach) {
        when(versandService.versandUeber(postfach)).thenReturn(new PostfachVersandService.Versand(
                mailSender, postfach != null ? postfach.getEmailAdresse() : "standard@example.com", postfach));
    }

    private void verifyGesendet(String von, String an) throws Exception {
        verify(mailSender).sendEmailAndReturnMessageIdWithInline(eq(an), isNull(), eq(von),
                anyString(), anyString(), anyMap(), isNull(), isNull());
    }

    @BeforeEach
    void setup() {
        repository = mock(OutOfOfficeScheduleRepository.class);
        emailSignatureService = mock(EmailSignatureService.class);
        mailSender = mock(EmailService.class);
        systemSettingsService = mock(SystemSettingsService.class);
        replyLogRepository = mock(OooReplyLogRepository.class);
        postfachRepository = mock(EmailAbsenderRepository.class);
        versandService = mock(PostfachVersandService.class);
        when(systemSettingsService.getSmtpUsername()).thenReturn("standard@example.com");
        when(replyLogRepository.existsByScheduleIdAndSenderAddressIgnoreCase(anyLong(), anyString()))
                .thenReturn(false);
        info = postfach(INFO_ID, "info@example.com");
        when(postfachRepository.findById(INFO_ID)).thenReturn(Optional.of(info));
        when(postfachRepository.existsByEmailAdresseIgnoreCase(anyString())).thenReturn(false);
        versandUeber(info);

        responder = new OutOfOfficeResponder(repository, emailSignatureService,
                systemSettingsService, replyLogRepository, postfachRepository, versandService);
    }

    private OutOfOfficeSchedule activeSchedule() {
        OutOfOfficeSchedule s = new OutOfOfficeSchedule();
        s.setId(1L);
        s.setTitle("Betriebsurlaub");
        s.setStartAt(LocalDate.now().minusDays(1));
        s.setEndAt(LocalDate.now().plusDays(7));
        s.setActive(true);
        s.setSubjectTemplate("Automatische Antwort: {{title}}");
        s.setBodyTemplate("Ich bin von {{start}} bis {{ende}} nicht erreichbar.");
        return s;
    }

    @Test
    void sendsAutoReplyForKunde() throws Exception {
        OutOfOfficeSchedule schedule = activeSchedule();
        EmailSignature signature = new EmailSignature();
        signature.setId(99L);
        schedule.setSignature(signature);

        when(repository.findAktiveFuerPostfach(eq(INFO_ID), any())).thenReturn(List.of(schedule));
        when(emailSignatureService.renderSignatureHtmlForEmail(signature, null)).thenReturn("<div>LG</div>");
        when(emailSignatureService.buildInlineCidFileMap(signature)).thenReturn(Map.of());

        responder.handleIncomingEmail(incoming("kunde@kundenfirma.de", "Ihre Anfrage"));

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailSender).sendEmailAndReturnMessageIdWithInline(eq("kunde@kundenfirma.de"), isNull(),
                eq("info@example.com"), subjectCaptor.capture(), bodyCaptor.capture(),
                eq(Map.<String, File>of()), isNull(), isNull());
        assertThat(subjectCaptor.getValue()).contains("Betriebsurlaub");
        assertThat(bodyCaptor.getValue()).contains("Ich bin von");
        assertThat(bodyCaptor.getValue()).contains("<div>LG</div>");

        ArgumentCaptor<OooReplyLog> logCaptor = ArgumentCaptor.forClass(OooReplyLog.class);
        verify(replyLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getScheduleId()).isEqualTo(1L);
        assertThat(logCaptor.getValue().getSenderAddress()).isEqualTo("kunde@kundenfirma.de");
    }

    @Test
    void skipsWhenNoSchedule() {
        when(repository.findAktiveFuerPostfach(eq(INFO_ID), any())).thenReturn(List.of());

        responder.handleIncomingEmail(incoming("kunde@kundenfirma.de", "Anfrage"));

        verifyNoInteractions(mailSender);
    }

    @Test
    void skipsWhenSpam() {
        responder.handleIncomingEmail(mail(INFO_ID,
                "kunde@kundenfirma.de", "Test",
                true, false, null, null, null));

        verifyNoInteractions(mailSender);
        verifyNoInteractions(repository);
    }

    @Test
    void skipsWhenNewsletter() {
        responder.handleIncomingEmail(mail(INFO_ID,
                "kunde@kundenfirma.de", "Newsletter",
                false, true, null, null, null));

        verifyNoInteractions(mailSender);
        verifyNoInteractions(repository);
    }

    @Test
    void sendsAutoReplyEvenForUnknownExternalSender() throws Exception {
        // Auto-Reply geht an alle Absender, nicht nur an bekannte Kunden.
        OutOfOfficeSchedule schedule = activeSchedule();
        when(repository.findAktiveFuerPostfach(eq(INFO_ID), any())).thenReturn(List.of(schedule));

        responder.handleIncomingEmail(incoming("fremder@unbekannt.de", "Hallo"));

        verifyGesendet("info@example.com", "fremder@unbekannt.de");
    }

    @Test
    void skipsWhenAutoSubmittedHeaderPresent() {
        responder.handleIncomingEmail(mail(INFO_ID,
                "kunde@kundenfirma.de", "Re: Ihre Anfrage", 
                false, false, "auto-replied", null, null));

        verifyNoInteractions(mailSender);
        verifyNoInteractions(repository);
    }

    @Test
    void skipsWhenPrecedenceBulk() {
        responder.handleIncomingEmail(mail(INFO_ID,
                "kunde@kundenfirma.de", "Test",
                false, false, null, "bulk", null));

        verifyNoInteractions(mailSender);
    }

    @Test
    void skipsWhenListIdHeaderPresent() {
        responder.handleIncomingEmail(mail(INFO_ID,
                "kunde@kundenfirma.de", "Test",
                false, false, null, null, "<list.example.com>"));

        verifyNoInteractions(mailSender);
    }

    @Test
    void skipsWhenSenderIsSystemAddress() {
        responder.handleIncomingEmail(incoming("mailer-daemon@kundenfirma.de", "Bounce"));
        responder.handleIncomingEmail(incoming("noreply@kundenfirma.de", "Auto"));
        responder.handleIncomingEmail(incoming("postmaster@kundenfirma.de", "Sys"));

        verifyNoInteractions(mailSender);
    }

    @Test
    void skipsWhenSenderIsReservedTestDomain() {
        responder.handleIncomingEmail(incoming("kunde@example.com", "Test"));
        responder.handleIncomingEmail(incoming("user@subdomain.test", "Test"));
        responder.handleIncomingEmail(incoming("foo@localhost", "Test"));

        verifyNoInteractions(mailSender);
        verifyNoInteractions(repository);
    }

    @Test
    void skipsWhenAlreadyRepliedToSenderInThisSchedule() {
        OutOfOfficeSchedule schedule = activeSchedule();
        when(repository.findAktiveFuerPostfach(eq(INFO_ID), any())).thenReturn(List.of(schedule));
        when(replyLogRepository.existsByScheduleIdAndSenderAddressIgnoreCase(eq(1L), eq("kunde@kundenfirma.de")))
                .thenReturn(true);

        responder.handleIncomingEmail(incoming("kunde@kundenfirma.de", "Zweite Mail"));

        verifyNoInteractions(mailSender);
        verify(replyLogRepository, never()).save(any());
    }

    @Test
    void antwortetAusDemPostfachInDemDieMailAnkam() throws Exception {
        EmailAbsender max = postfach(7L, "max.mustermann@example.com");
        when(postfachRepository.findById(7L)).thenReturn(Optional.of(max));
        versandUeber(max);
        when(repository.findAktiveFuerPostfach(eq(7L), any())).thenReturn(List.of(activeSchedule()));

        responder.handleIncomingEmail(mail(7L, "kunde@kundenfirma.de", "Angebot", false, false, null, null, null));

        verifyGesendet("max.mustermann@example.com", "kunde@kundenfirma.de");
        verify(repository, never()).findAktiveOhnePostfach(any());
    }

    @Test
    void keineNotizFuerDiesesPostfach_keineAntwort() {
        EmailAbsender max = postfach(7L, "max.mustermann@example.com");
        when(postfachRepository.findById(7L)).thenReturn(Optional.of(max));
        when(repository.findAktiveFuerPostfach(eq(7L), any())).thenReturn(List.of());

        responder.handleIncomingEmail(mail(7L, "kunde@kundenfirma.de", "Angebot", false, false, null, null, null));

        verifyNoInteractions(mailSender, versandService);
    }

    @Test
    void mailOhnePostfach_zaehltAlsHauptpostfach() throws Exception {
        info.setHauptpostfach(true);
        when(postfachRepository.findFirstByHauptpostfachTrueOrderByIdAsc()).thenReturn(Optional.of(info));
        when(repository.findAktiveFuerPostfach(eq(INFO_ID), any())).thenReturn(List.of(activeSchedule()));

        responder.handleIncomingEmail(mail(null, "kunde@kundenfirma.de", "Frage", false, false, null, null, null));

        verifyGesendet("info@example.com", "kunde@kundenfirma.de");
    }

    @Test
    void ohneJedesPostfach_greifenAlteNotizenUeberDasStandardKonto() throws Exception {
        when(postfachRepository.findFirstByHauptpostfachTrueOrderByIdAsc()).thenReturn(Optional.empty());
        when(repository.findAktiveOhnePostfach(any())).thenReturn(List.of(activeSchedule()));
        versandUeber(null);

        responder.handleIncomingEmail(mail(null, "kunde@kundenfirma.de", "Frage", false, false, null, null, null));

        verifyGesendet("standard@example.com", "kunde@kundenfirma.de");
        verify(repository, never()).findAktiveFuerPostfach(any(), any());
    }

    @Test
    void auslaufendesPostfach_antwortetNicht() {
        info.setLaeuftAus(true);

        responder.handleIncomingEmail(incoming("kunde@kundenfirma.de", "Frage"));

        verifyNoInteractions(mailSender, versandService);
        verify(repository, never()).findAktiveFuerPostfach(any(), any());
    }

    @Test
    void mailAusEigenemPostfach_bekommtKeineAntwort() {
        when(postfachRepository.existsByEmailAdresseIgnoreCase("max.mustermann@firma.de")).thenReturn(true);

        responder.handleIncomingEmail(incoming("Max Mustermann <max.mustermann@firma.de>", "Intern"));

        verifyNoInteractions(mailSender, versandService, repository);
    }

    @Test
    void mailVonDerAbsenderAdresseDesStandardKontos_bekommtKeineAntwort() {
        when(systemSettingsService.getMailFromAddress()).thenReturn("buero@firma.de");

        responder.handleIncomingEmail(incoming("Buero@Firma.de", "Intern"));

        verifyNoInteractions(mailSender, versandService, repository);
    }

    @Test
    void dollarUndBackslashImBetreffSindText() throws Exception {
        OutOfOfficeSchedule schedule = activeSchedule();
        schedule.setSubjectTemplate("Re: {{subject}}");
        schedule.setBodyTemplate("Zu {{subject}}: bin weg.");
        when(repository.findAktiveFuerPostfach(eq(INFO_ID), any())).thenReturn(List.of(schedule));

        responder.handleIncomingEmail(incoming("kunde@kundenfirma.de", "Angebot 500$ \\ $1"));

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailSender).sendEmailAndReturnMessageIdWithInline(anyString(), isNull(), anyString(),
                subjectCaptor.capture(), anyString(), anyMap(), isNull(), isNull());
        assertThat(subjectCaptor.getValue()).isEqualTo("Re: Angebot 500$ \\ $1");
    }

    @Test
    void versandFehler_wirdNichtAlsBeantwortetVermerkt() throws Exception {
        when(repository.findAktiveFuerPostfach(eq(INFO_ID), any())).thenReturn(List.of(activeSchedule()));
        when(mailSender.sendEmailAndReturnMessageIdWithInline(anyString(), any(), anyString(), anyString(),
                anyString(), anyMap(), any(), any())).thenThrow(new jakarta.mail.MessagingException("SMTP weg"));

        responder.handleIncomingEmail(incoming("kunde@kundenfirma.de", "Frage"));

        verify(replyLogRepository, never()).save(any());
    }

    @Test
    void deactivatesExpiredSchedules() {
        OutOfOfficeSchedule expired = new OutOfOfficeSchedule();
        expired.setId(10L);
        expired.setActive(true);
        expired.setStartAt(LocalDate.now().minusDays(20));
        expired.setEndAt(LocalDate.now().minusDays(2));
        OutOfOfficeSchedule current = new OutOfOfficeSchedule();
        current.setId(11L);
        current.setActive(true);
        current.setStartAt(LocalDate.now().minusDays(1));
        current.setEndAt(LocalDate.now().plusDays(5));

        when(repository.findAll()).thenReturn(java.util.List.of(expired, current));

        int deactivated = responder.deactivateExpiredSchedules();

        assertThat(deactivated).isEqualTo(1);
        assertThat(expired.isActive()).isFalse();
        assertThat(current.isActive()).isTrue();
        verify(repository).saveAll(argThat(iter ->
                java.util.stream.StreamSupport.stream(iter.spliterator(), false).count() == 1));
    }
}
