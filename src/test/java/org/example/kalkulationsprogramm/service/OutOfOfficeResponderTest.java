package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.EmailSignature;
import org.example.kalkulationsprogramm.domain.OutOfOfficeSchedule;
import org.example.kalkulationsprogramm.repository.OutOfOfficeScheduleRepository;
import org.example.kalkulationsprogramm.service.mail.HtmlMailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OutOfOfficeResponderTest {

    private OutOfOfficeScheduleRepository repository;
    private EmailSignatureService emailSignatureService;
    private HtmlMailSender mailSender;
    private SystemSettingsService systemSettingsService;
    private OutOfOfficeResponder responder;

    @BeforeEach
    void setup() {
        repository = mock(OutOfOfficeScheduleRepository.class);
        emailSignatureService = mock(EmailSignatureService.class);
        mailSender = mock(HtmlMailSender.class);
        systemSettingsService = mock(SystemSettingsService.class);
        when(systemSettingsService.getSmtpUsername()).thenReturn("info@example.com");

        responder = new OutOfOfficeResponder(repository, emailSignatureService, mailSender, systemSettingsService);
    }

    @Test
    void sendsAutoReplyWhenScheduleActive() throws Exception {
        OutOfOfficeSchedule schedule = new OutOfOfficeSchedule();
        schedule.setId(1L);
        schedule.setTitle("Betriebsurlaub");
        schedule.setStartAt(LocalDate.of(2025, 8, 1));
        schedule.setEndAt(LocalDate.of(2025, 8, 15));
        schedule.setSubjectTemplate("Automatische Antwort: {{title}}");
        schedule.setBodyTemplate("Ich bin von {{start}} bis {{ende}} nicht erreichbar.");
        EmailSignature signature = new EmailSignature();
        signature.setId(99L);
        signature.setHtml("<div class=\"email-signature\">Signatur</div>");
        schedule.setSignature(signature);

        when(repository.findFirstByActiveTrueAndStartAtLessThanEqualAndEndAtGreaterThanEqualOrderByStartAtDesc(any(), any()))
                .thenReturn(Optional.of(schedule));
        when(emailSignatureService.renderSignatureHtmlForEmail(signature, null)).thenReturn("<div>LG</div>");
        when(emailSignatureService.buildInlineCidFileMap(signature)).thenReturn(Map.of());

        responder.handleIncomingEmail("kunde@example.com", "Ihre Anfrage");

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailSender).send(eq("info@example.com"), eq("kunde@example.com"), subjectCaptor.capture(), bodyCaptor.capture(), eq(Map.<String, File>of()));
        assertThat(subjectCaptor.getValue()).contains("Betriebsurlaub");
        assertThat(bodyCaptor.getValue()).contains("Ich bin von");
        assertThat(bodyCaptor.getValue()).contains("<div>LG</div>");
    }

    @Test
    void skipsWhenNoSchedule() {
        when(repository.findFirstByActiveTrueAndStartAtLessThanEqualAndEndAtGreaterThanEqualOrderByStartAtDesc(any(), any()))
                .thenReturn(Optional.empty());

        responder.handleIncomingEmail("kunde@example.com", "Anfrage");

        verifyNoInteractions(mailSender);
    }
}
