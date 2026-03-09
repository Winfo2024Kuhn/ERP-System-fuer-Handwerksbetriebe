package org.example.kalkulationsprogramm.service;

import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.EmailSignature;
import org.example.kalkulationsprogramm.domain.OutOfOfficeSchedule;
import org.example.kalkulationsprogramm.repository.OutOfOfficeScheduleRepository;
import org.example.kalkulationsprogramm.util.EmailHtmlSanitizer;
import org.example.kalkulationsprogramm.service.mail.HtmlMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutOfOfficeResponder {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy",
            Locale.GERMAN);
    private static final Pattern START_TOKEN = Pattern.compile("(?i)\\{\\{start}}");
    private static final Pattern END_TOKEN = Pattern.compile("(?i)\\{\\{ende}}");
    private static final Pattern TITLE_TOKEN = Pattern.compile("(?i)\\{\\{title}}");
    private static final Pattern SUBJECT_TOKEN = Pattern.compile("(?i)\\{\\{subject}}");

    private final OutOfOfficeScheduleRepository scheduleRepository;
    private final EmailSignatureService emailSignatureService;
    private final HtmlMailSender mailSender;

    @Value("${smtp.username}")
    private String defaultFromAddress;

    @Transactional(readOnly = true)
    public void handleIncomingEmail(String fromAddress, String originalSubject) {
        if (!StringUtils.hasText(fromAddress)) {
            return;
        }
        if (defaultFromAddress != null && fromAddress.equalsIgnoreCase(defaultFromAddress)) {
            return;
        }
        Optional<OutOfOfficeSchedule> scheduleOpt = scheduleRepository
                .findFirstByActiveTrueAndStartAtLessThanEqualAndEndAtGreaterThanEqualOrderByStartAtDesc(
                        LocalDate.now(), LocalDate.now());
        if (scheduleOpt.isEmpty()) {
            return;
        }
        OutOfOfficeSchedule schedule = scheduleOpt.get();
        EmailSignature signature = schedule.getSignature();
        // Signature is optional - OOO works without it too

        String subject = buildSubject(schedule, originalSubject);
        String htmlBody = buildBodyHtml(schedule, signature, originalSubject);
        Map<String, File> inlineImages = signature != null 
                ? emailSignatureService.buildInlineCidFileMap(signature) 
                : Map.of();
        try {
            mailSender.send(defaultFromAddress, fromAddress, subject, htmlBody, inlineImages);
            log.info("Automatische Abwesenheitsantwort an {} versendet.", maskAddress(fromAddress));

            // Append to IMAP Sent folder so it appears in Outlook
            try {
                org.example.email.ImapAppendService.appendToSent(
                        defaultFromAddress,
                        java.util.List.of(fromAddress),
                        subject,
                        htmlBody,
                        null,
                        LocalDateTime.now());
                log.debug("OOO-Antwort im Gesendet-Ordner abgelegt.");
            } catch (Exception ex) {
                log.warn("Konnte OOO-Antwort nicht im Gesendet-Ordner ablegen: {}", ex.getMessage());
            }
        } catch (MessagingException ex) {
            log.warn("Abwesenheitsantwort konnte nicht gesendet werden: {}", ex.getMessage());
        }
    }

    private String buildSubject(OutOfOfficeSchedule schedule, String originalSubject) {
        String template = Optional.ofNullable(schedule.getSubjectTemplate())
                .filter(StringUtils::hasText)
                .orElse("Automatische Antwort: {{subject}}");
        String subject = applyTokens(template, schedule, originalSubject);
        if (!StringUtils.hasText(subject)) {
            subject = "Automatische Antwort: " + Optional.ofNullable(schedule.getTitle()).orElse("");
        }
        return subject;
    }

    private String buildBodyHtml(OutOfOfficeSchedule schedule, EmailSignature signature, String originalSubject) {
        String template = Optional.ofNullable(schedule.getBodyTemplate())
                .filter(StringUtils::hasText)
                .orElse("Ich bin vom {{start}} bis {{ende}} nicht erreichbar.");
        String message = applyTokens(template, schedule, originalSubject);
        String htmlBody = EmailHtmlSanitizer.plainTextToHtml(message);
        if (signature != null) {
            String signatureHtml = emailSignatureService.renderSignatureHtmlForEmail(signature, null);
            if (StringUtils.hasText(signatureHtml)) {
                htmlBody = htmlBody + signatureHtml;
            }
        }
        return htmlBody;
    }

    private String applyTokens(String template, OutOfOfficeSchedule schedule, String originalSubject) {
        if (template == null) {
            return "";
        }
        String value = template;
        String start = formatDate(schedule.getStartAt());
        String end = formatDate(schedule.getEndAt());
        String title = Optional.ofNullable(schedule.getTitle()).orElse("");
        String subject = Optional.ofNullable(originalSubject).orElse("");
        value = START_TOKEN.matcher(value).replaceAll(Objects.requireNonNullElse(start, ""));
        value = END_TOKEN.matcher(value).replaceAll(Objects.requireNonNullElse(end, ""));
        value = TITLE_TOKEN.matcher(value).replaceAll(title);
        value = SUBJECT_TOKEN.matcher(value).replaceAll(subject);
        return value;
    }

    private String formatDate(LocalDate value) {
        if (value == null) {
            return "";
        }
        return DATE_TIME_FORMATTER.format(value);
    }

    private String maskAddress(String address) {
        if (!StringUtils.hasText(address)) {
            return "";
        }
        int at = address.indexOf('@');
        if (at <= 1) {
            return address;
        }
        return address.charAt(0) + "***" + address.substring(at);
    }
}
