package org.example.kalkulationsprogramm.service;

import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.EmailSignature;
import org.example.kalkulationsprogramm.domain.OooReplyLog;
import org.example.kalkulationsprogramm.domain.OutOfOfficeSchedule;
import org.example.email.ImapAppendService;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.OooReplyLogRepository;
import org.example.kalkulationsprogramm.repository.OutOfOfficeScheduleRepository;
import org.example.kalkulationsprogramm.util.EmailHtmlSanitizer;
import org.example.kalkulationsprogramm.service.mail.HtmlMailSender;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Berlin");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy",
            Locale.GERMAN);
    private static final Pattern START_TOKEN = Pattern.compile("(?i)\\{\\{start}}");
    private static final Pattern END_TOKEN = Pattern.compile("(?i)\\{\\{ende}}");
    private static final Pattern TITLE_TOKEN = Pattern.compile("(?i)\\{\\{title}}");
    private static final Pattern SUBJECT_TOKEN = Pattern.compile("(?i)\\{\\{subject}}");

    private final OutOfOfficeScheduleRepository scheduleRepository;
    private final EmailSignatureService emailSignatureService;
    private final HtmlMailSender mailSender;
    private final SystemSettingsService systemSettingsService;
    private final ImapAppendService imapAppendService;
    private final KundeRepository kundeRepository;
    private final OooReplyLogRepository replyLogRepository;

    /**
     * Kontext einer eingehenden E-Mail, der für die Auto-Reply-Entscheidung
     * benötigt wird. Wird vom Import-Flow befüllt.
     */
    public record IncomingMail(
            String fromAddress,
            String subject,
            LocalDateTime sentAt,
            boolean spam,
            boolean newsletter,
            String autoSubmittedHeader,
            String precedenceHeader,
            String listIdHeader) {}

    /**
     * Versendet (falls zutreffend) eine automatische Abwesenheitsantwort.
     * Trigger-Bedingungen:
     * <ul>
     *   <li>Absender ist ein Kunde (über {@link KundeRepository}).</li>
     *   <li>E-Mail ist NICHT als Spam oder Newsletter klassifiziert.</li>
     *   <li>E-Mail ist KEINE Auto-Reply (Auto-Submitted/Precedence/List-Id).</li>
     *   <li>Es existiert ein aktiver Abwesenheitsplan, dessen Datum die
     *       <em>Sendezeit</em> der Mail einschließt (TZ Europe/Berlin).</li>
     *   <li>Innerhalb dieses Plans wurde an diesen Absender noch nicht geantwortet.</li>
     * </ul>
     */
    public void handleIncomingEmail(IncomingMail incoming) {
        if (incoming == null || !StringUtils.hasText(incoming.fromAddress())) {
            return;
        }
        if (incoming.spam() || incoming.newsletter()) {
            return;
        }
        if (isAutoReplyHeader(incoming)) {
            return;
        }
        String sender = normalize(incoming.fromAddress());
        if (isSystemAddress(sender)) {
            return;
        }
        if (isReservedTestAddress(sender)) {
            log.debug("OOO-Antwort an Test-Adresse {} unterdrueckt.", maskAddress(sender));
            return;
        }
        String defaultFromAddress = systemSettingsService.getSmtpUsername();
        if (StringUtils.hasText(defaultFromAddress) && sender.equalsIgnoreCase(defaultFromAddress)) {
            return;
        }
        if (!kundeRepository.existsByKundenEmail(sender)) {
            return;
        }

        LocalDate referenceDate = incoming.sentAt() != null
                ? incoming.sentAt().atZone(ZoneId.systemDefault()).withZoneSameInstant(BUSINESS_ZONE).toLocalDate()
                : LocalDate.now(BUSINESS_ZONE);

        Optional<OutOfOfficeSchedule> scheduleOpt = scheduleRepository
                .findFirstByActiveTrueAndStartAtLessThanEqualAndEndAtGreaterThanEqualOrderByStartAtDesc(
                        referenceDate, referenceDate);
        if (scheduleOpt.isEmpty()) {
            return;
        }
        OutOfOfficeSchedule schedule = scheduleOpt.get();
        if (replyLogRepository.existsByScheduleIdAndSenderAddressIgnoreCase(schedule.getId(), sender)) {
            log.debug("OOO-Antwort an {} bereits versendet (Plan {}).", maskAddress(sender), schedule.getId());
            return;
        }

        sendReply(schedule, sender, incoming.subject(), defaultFromAddress);
    }

    private void sendReply(OutOfOfficeSchedule schedule, String sender, String originalSubject,
                           String defaultFromAddress) {
        EmailSignature signature = schedule.getSignature();
        String subject = buildSubject(schedule, originalSubject);
        String htmlBody = buildBodyHtml(schedule, signature, originalSubject);
        Map<String, File> inlineImages = signature != null
                ? emailSignatureService.buildInlineCidFileMap(signature)
                : Map.of();
        try {
            mailSender.send(defaultFromAddress, sender, subject, htmlBody, inlineImages);
            log.info("Automatische Abwesenheitsantwort an {} versendet.", maskAddress(sender));
            try {
                imapAppendService.appendToSent(
                        defaultFromAddress,
                        java.util.List.of(sender),
                        subject,
                        htmlBody,
                        null,
                        LocalDateTime.now());
                log.debug("OOO-Antwort im Gesendet-Ordner abgelegt.");
            } catch (Exception ex) {
                log.warn("Konnte OOO-Antwort nicht im Gesendet-Ordner ablegen: {}", ex.getMessage());
            }
            recordReply(schedule.getId(), sender);
        } catch (MessagingException ex) {
            log.warn("Abwesenheitsantwort konnte nicht gesendet werden: {}", ex.getMessage());
        }
    }

    private void recordReply(Long scheduleId, String senderAddress) {
        try {
            OooReplyLog entry = new OooReplyLog();
            entry.setScheduleId(scheduleId);
            entry.setSenderAddress(senderAddress);
            entry.setRepliedAt(LocalDateTime.now());
            replyLogRepository.save(entry);
        } catch (DataIntegrityViolationException race) {
            // Parallel-Import hat den Eintrag bereits geschrieben – ignorieren.
            log.debug("OOO-Reply-Log Race-Condition für {} (Plan {}): {}",
                    maskAddress(senderAddress), scheduleId,
                    race.getMostSpecificCause() != null ? race.getMostSpecificCause().getMessage() : race.getMessage());
        }
    }

    /**
     * Deaktiviert alle Abwesenheitspläne, deren Endzeitpunkt überschritten ist.
     * Wird vom EmailImportService täglich getriggert, damit ein abgelaufener
     * Plan nicht "active=true" in der UI stehen bleibt.
     */
    @Transactional
    public int deactivateExpiredSchedules() {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        var expired = scheduleRepository.findAll().stream()
                .filter(OutOfOfficeSchedule::isActive)
                .filter(s -> s.getEndAt() != null && s.getEndAt().isBefore(today))
                .toList();
        if (expired.isEmpty()) {
            return 0;
        }
        for (OutOfOfficeSchedule s : expired) {
            s.setActive(false);
        }
        scheduleRepository.saveAll(expired);
        log.info("{} abgelaufene Abwesenheitspläne automatisch deaktiviert.", expired.size());
        return expired.size();
    }

    private boolean isAutoReplyHeader(IncomingMail incoming) {
        String autoSubmitted = lower(incoming.autoSubmittedHeader());
        if (autoSubmitted != null && !autoSubmitted.equals("no")) {
            return true; // RFC 3834: "auto-replied", "auto-generated", ...
        }
        String precedence = lower(incoming.precedenceHeader());
        if (precedence != null
                && (precedence.contains("bulk") || precedence.contains("list") || precedence.contains("junk"))) {
            return true;
        }
        return StringUtils.hasText(incoming.listIdHeader());
    }

    private boolean isSystemAddress(String address) {
        String local = address.toLowerCase().trim();
        int at = local.indexOf('@');
        String localPart = at < 0 ? local : local.substring(0, at);
        return localPart.equals("mailer-daemon")
                || localPart.equals("postmaster")
                || localPart.equals("noreply")
                || localPart.equals("no-reply")
                || localPart.equals("donotreply")
                || localPart.equals("do-not-reply")
                || localPart.startsWith("bounce")
                || localPart.startsWith("bounces");
    }

    private String buildSubject(OutOfOfficeSchedule schedule, String originalSubject) {
        String template = Optional.ofNullable(schedule.getSubjectTemplate())
                .filter(StringUtils::hasText)
                .orElse("Automatische Antwort: {{subject}}");
        String subject = applyTokens(template, schedule, originalSubject);
        if (!StringUtils.hasText(subject.trim())) {
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

    /**
     * Normalisiert eine Absenderadresse:
     * Form "Name <user@example.com>" → "user@example.com" (lowercase, getrimmt).
     */
    private String normalize(String address) {
        String value = address.trim();
        int lt = value.lastIndexOf('<');
        int gt = value.lastIndexOf('>');
        if (lt >= 0 && gt > lt) {
            value = value.substring(lt + 1, gt);
        }
        return value.toLowerCase(Locale.ROOT).trim();
    }

    private String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT).trim();
    }

    /**
     * Prueft auf RFC 2606 reservierte Test-/Beispiel-Domains.
     */
    private boolean isReservedTestAddress(String address) {
        int at = address.lastIndexOf('@');
        if (at < 0 || at == address.length() - 1) return false;
        String domain = address.substring(at + 1);
        return domain.equals("example.com")
                || domain.equals("example.org")
                || domain.equals("example.net")
                || domain.endsWith(".example")
                || domain.endsWith(".test")
                || domain.endsWith(".invalid")
                || domain.endsWith(".localhost")
                || domain.equals("localhost");
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
