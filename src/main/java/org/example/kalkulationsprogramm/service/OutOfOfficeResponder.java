package org.example.kalkulationsprogramm.service;

import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.domain.EmailSignature;
import org.example.kalkulationsprogramm.domain.OooReplyLog;
import org.example.kalkulationsprogramm.domain.OutOfOfficeSchedule;
import org.example.kalkulationsprogramm.repository.EmailAbsenderRepository;
import org.example.kalkulationsprogramm.repository.OooReplyLogRepository;
import org.example.kalkulationsprogramm.repository.OutOfOfficeScheduleRepository;
import org.example.kalkulationsprogramm.util.EmailHtmlSanitizer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
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
    private final SystemSettingsService systemSettingsService;
    private final OooReplyLogRepository replyLogRepository;
    private final EmailAbsenderRepository postfachRepository;
    private final PostfachVersandService postfachVersandService;

    /**
     * Kontext einer eingehenden E-Mail, der für die Auto-Reply-Entscheidung
     * benötigt wird. Wird vom Import-Flow befüllt.
     *
     * @param postfachId Postfach, in dem die Mail ankam; {@code null} = Standard-Konto aus den
     *                   Einstellungen (zählt wie überall als Hauptpostfach)
     */
    public record IncomingMail(
            Long postfachId,
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
     *   <li>E-Mail ist NICHT als Spam oder Newsletter klassifiziert.</li>
     *   <li>E-Mail ist KEINE Auto-Reply (Auto-Submitted/Precedence/List-Id) und
     *       der Absender ist keine System-/Bounce-/Test-Adresse (Loop-Schutz).</li>
     *   <li>Es existiert ein aktiver Abwesenheitsplan für das Postfach, in dem die Mail
     *       ankam, dessen Datum die <em>Sendezeit</em> der Mail einschließt (TZ Europe/Berlin).
     *       Geantwortet wird aus genau diesem Postfach.</li>
     *   <li>Innerhalb dieses Plans wurde an diesen Absender noch nicht geantwortet.</li>
     * </ul>
     * Es gibt KEINE Beschränkung auf bekannte Kunden — die Auto-Reply geht an
     * jeden externen Absender, sofern er die Loop-Schutz-Filter passiert.
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
        if (istEigeneAdresse(sender)) {
            return; // Mail aus einem eigenen Postfach (z. B. max@ an info@) – keine Auto-Antwort im Haus.
        }

        Optional<EmailAbsender> postfach = postfachDerMail(incoming.postfachId());
        if (postfach.isPresent() && !AbwesenheitsnotizService.antwortetAutomatisch(postfach.get())) {
            return;
        }

        LocalDate referenceDate = incoming.sentAt() != null
                ? incoming.sentAt().atZone(ZoneId.systemDefault()).withZoneSameInstant(BUSINESS_ZONE).toLocalDate()
                : LocalDate.now(BUSINESS_ZONE);

        List<OutOfOfficeSchedule> passend = postfach
                .map(p -> scheduleRepository.findAktiveFuerPostfach(p.getId(), referenceDate))
                .orElseGet(() -> scheduleRepository.findAktiveOhnePostfach(referenceDate));
        if (passend.isEmpty()) {
            return;
        }
        OutOfOfficeSchedule schedule = passend.get(0);
        if (replyLogRepository.existsByScheduleIdAndSenderAddressIgnoreCase(schedule.getId(), sender)) {
            log.debug("OOO-Antwort an {} bereits versendet (Plan {}).", maskAddress(sender), schedule.getId());
            return;
        }

        sendReply(schedule, postfach.orElse(null), sender, incoming.subject());
    }

    /** Postfach der Mail; ohne Angabe das Hauptpostfach, gibt es keins: leer (Standard-Konto). */
    private Optional<EmailAbsender> postfachDerMail(Long postfachId) {
        if (postfachId != null) {
            return postfachRepository.findById(postfachId);
        }
        return postfachRepository.findFirstByHauptpostfachTrueOrderByIdAsc();
    }

    /** Absender ist eines unserer Postfächer oder das Standard-Konto? */
    private boolean istEigeneAdresse(String sender) {
        for (String standard : new String[] { systemSettingsService.getSmtpUsername(),
                systemSettingsService.getMailFromAddress() }) {
            if (StringUtils.hasText(standard) && sender.equalsIgnoreCase(standard.trim())) {
                return true;
            }
        }
        return postfachRepository.existsByEmailAdresseIgnoreCase(sender);
    }

    /**
     * Verschickt die Antwort über das Postfach, in dem die Mail ankam (eigener Zugang oder – bei
     * reiner Absender-Adresse – über das Hauptpostfach mit dieser Adresse). Die Gesendet-Kopie
     * legt der Versand selbst ab.
     */
    private void sendReply(OutOfOfficeSchedule schedule, EmailAbsender postfach, String sender,
                           String originalSubject) {
        EmailSignature signature = schedule.getSignature();
        String subject = buildSubject(schedule, originalSubject);
        String htmlBody = buildBodyHtml(schedule, signature, originalSubject);
        Map<String, File> inlineImages = signature != null
                ? emailSignatureService.buildInlineCidFileMap(signature)
                : Map.of();
        try {
            PostfachVersandService.Versand versand = postfachVersandService.versandUeber(postfach);
            versand.dienst().sendEmailAndReturnMessageIdWithInline(
                    sender, null, versand.absenderAdresse(), subject, htmlBody, inlineImages, null, null);
            log.info("Automatische Abwesenheitsantwort an {} versendet (Postfach {}).", maskAddress(sender),
                    postfach != null ? postfach.getId() : "Standard");
            recordReply(schedule.getId(), sender);
        } catch (MessagingException | IOException | RuntimeException ex) {
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
        // Body-Template kann seit dem Rich-Text-Editor in EmailSettings echtes HTML
        // sein (Absaetze, Listen, eingebettete Signatur-Tabellen). Aeltere Eintraege
        // sind reiner Plaintext. Heuristisch unterscheiden, damit Plaintext nicht
        // als <pre>-escaped landet und HTML nicht doppelt-escaped beim Empfaenger.
        String htmlBody = AbwesenheitsnotizService.siehtNachHtmlAus(message)
                ? EmailHtmlSanitizer.sanitizeDetailHtml(message)
                : EmailHtmlSanitizer.plainTextToHtml(message);
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
        // quoteReplacement: "$" und "\" im Betreff des Absenders sind Text, keine Gruppen-Verweise.
        value = START_TOKEN.matcher(value).replaceAll(Matcher.quoteReplacement(Objects.requireNonNullElse(start, "")));
        value = END_TOKEN.matcher(value).replaceAll(Matcher.quoteReplacement(Objects.requireNonNullElse(end, "")));
        value = TITLE_TOKEN.matcher(value).replaceAll(Matcher.quoteReplacement(title));
        value = SUBJECT_TOKEN.matcher(value).replaceAll(Matcher.quoteReplacement(subject));
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
