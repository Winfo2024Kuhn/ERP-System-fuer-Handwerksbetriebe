package org.example.kalkulationsprogramm.service;

import org.example.email.ImapAppendService;
import org.example.kalkulationsprogramm.domain.EmailSignature;
import org.example.kalkulationsprogramm.domain.OooReplyLog;
import org.example.kalkulationsprogramm.domain.OutOfOfficeSchedule;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.OooReplyLogRepository;
import org.example.kalkulationsprogramm.repository.OutOfOfficeScheduleRepository;
import org.example.kalkulationsprogramm.service.mail.HtmlMailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class OutOfOfficeResponderTest {

    private OutOfOfficeScheduleRepository repository;
    private EmailSignatureService emailSignatureService;
    private HtmlMailSender mailSender;
    private SystemSettingsService systemSettingsService;
    private ImapAppendService imapAppendService;
    private KundeRepository kundeRepository;
    private OooReplyLogRepository replyLogRepository;
    private OutOfOfficeResponder responder;

    private static OutOfOfficeResponder.IncomingMail incoming(String from, String subject) {
        return new OutOfOfficeResponder.IncomingMail(
                from, subject, LocalDateTime.now(), false, false, null, null, null);
    }

    @BeforeEach
    void setup() {
        repository = mock(OutOfOfficeScheduleRepository.class);
        emailSignatureService = mock(EmailSignatureService.class);
        mailSender = mock(HtmlMailSender.class);
        systemSettingsService = mock(SystemSettingsService.class);
        imapAppendService = mock(ImapAppendService.class);
        kundeRepository = mock(KundeRepository.class);
        replyLogRepository = mock(OooReplyLogRepository.class);
        when(systemSettingsService.getSmtpUsername()).thenReturn("info@example.com");
        when(kundeRepository.existsByKundenEmail(anyString())).thenReturn(true);
        when(replyLogRepository.existsByScheduleIdAndSenderAddressIgnoreCase(anyLong(), anyString()))
                .thenReturn(false);

        responder = new OutOfOfficeResponder(repository, emailSignatureService, mailSender,
                systemSettingsService, imapAppendService, kundeRepository, replyLogRepository);
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

        when(repository.findFirstByActiveTrueAndStartAtLessThanEqualAndEndAtGreaterThanEqualOrderByStartAtDesc(any(), any()))
                .thenReturn(Optional.of(schedule));
        when(emailSignatureService.renderSignatureHtmlForEmail(signature, null)).thenReturn("<div>LG</div>");
        when(emailSignatureService.buildInlineCidFileMap(signature)).thenReturn(Map.of());

        responder.handleIncomingEmail(incoming("kunde@kundenfirma.de", "Ihre Anfrage"));

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailSender).send(eq("info@example.com"), eq("kunde@kundenfirma.de"),
                subjectCaptor.capture(), bodyCaptor.capture(), eq(Map.<String, File>of()));
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
        when(repository.findFirstByActiveTrueAndStartAtLessThanEqualAndEndAtGreaterThanEqualOrderByStartAtDesc(any(), any()))
                .thenReturn(Optional.empty());

        responder.handleIncomingEmail(incoming("kunde@kundenfirma.de", "Anfrage"));

        verifyNoInteractions(mailSender);
    }

    @Test
    void skipsWhenSpam() {
        responder.handleIncomingEmail(new OutOfOfficeResponder.IncomingMail(
                "kunde@kundenfirma.de", "Test", LocalDateTime.now(),
                true, false, null, null, null));

        verifyNoInteractions(mailSender);
        verifyNoInteractions(repository);
        verifyNoInteractions(kundeRepository);
    }

    @Test
    void skipsWhenNewsletter() {
        responder.handleIncomingEmail(new OutOfOfficeResponder.IncomingMail(
                "kunde@kundenfirma.de", "Newsletter", LocalDateTime.now(),
                false, true, null, null, null));

        verifyNoInteractions(mailSender);
        verifyNoInteractions(repository);
    }

    @Test
    void skipsWhenSenderIsNotKunde() {
        when(kundeRepository.existsByKundenEmail(anyString())).thenReturn(false);

        responder.handleIncomingEmail(incoming("fremder@unbekannt.de", "Hallo"));

        verifyNoInteractions(mailSender);
        verifyNoInteractions(repository);
    }

    @Test
    void skipsWhenAutoSubmittedHeaderPresent() {
        responder.handleIncomingEmail(new OutOfOfficeResponder.IncomingMail(
                "kunde@kundenfirma.de", "Re: Ihre Anfrage", LocalDateTime.now(),
                false, false, "auto-replied", null, null));

        verifyNoInteractions(mailSender);
        verifyNoInteractions(repository);
    }

    @Test
    void skipsWhenPrecedenceBulk() {
        responder.handleIncomingEmail(new OutOfOfficeResponder.IncomingMail(
                "kunde@kundenfirma.de", "Test", LocalDateTime.now(),
                false, false, null, "bulk", null));

        verifyNoInteractions(mailSender);
    }

    @Test
    void skipsWhenListIdHeaderPresent() {
        responder.handleIncomingEmail(new OutOfOfficeResponder.IncomingMail(
                "kunde@kundenfirma.de", "Test", LocalDateTime.now(),
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
        when(repository.findFirstByActiveTrueAndStartAtLessThanEqualAndEndAtGreaterThanEqualOrderByStartAtDesc(any(), any()))
                .thenReturn(Optional.of(schedule));
        when(replyLogRepository.existsByScheduleIdAndSenderAddressIgnoreCase(eq(1L), eq("kunde@kundenfirma.de")))
                .thenReturn(true);

        responder.handleIncomingEmail(incoming("kunde@kundenfirma.de", "Zweite Mail"));

        verifyNoInteractions(mailSender);
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
