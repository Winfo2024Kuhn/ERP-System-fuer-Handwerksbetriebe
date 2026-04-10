package org.example.kalkulationsprogramm.service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.example.kalkulationsprogramm.dto.EmailThreadDto;
import org.example.kalkulationsprogramm.dto.EmailThreadEntryDto;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.util.InlineAttachmentUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service zum Laden und Aufbauen von E-Mail-Threads (Konversationsverläufen).
 *
 * <p>Nutzt die {@code parentEmail}/{@code replies}-Beziehungen aus der {@link Email}-Entity,
 * die durch {@link EmailImportService} via Message-ID / In-Reply-To / References-Header befüllt werden.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailThreadService {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    /** Maximale Snippet-Länge (Zeichen) für kollabierte Bubble-Vorschau. */
    private static final int SNIPPET_MAX_LENGTH = 120;

    private final EmailRepository emailRepository;

    // ═══════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Lädt den vollständigen Thread für eine gegebene E-Mail-ID.
     *
     * @param emailId ID der angeklickten (fokussierten) E-Mail
     * @return {@link EmailThreadDto} mit chronologisch sortierten Einträgen
     * @throws ResponseStatusException 404 wenn Email nicht gefunden
     */
    @Transactional(readOnly = true)
    public EmailThreadDto loadThreadFor(Long emailId) {
        Email focusedEmail = emailRepository.findById(emailId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Email not found: " + emailId));

        Email root = findRoot(focusedEmail);
        List<Email> allInThread = collectThread(root);

        List<EmailThreadEntryDto> entries = allInThread.stream()
                .map(this::toEntryDto)
                .collect(Collectors.toList());

        EmailThreadDto dto = new EmailThreadDto();
        dto.setRootEmailId(root.getId());
        dto.setFocusedEmailId(emailId);
        dto.setEmails(entries);

        log.debug("Thread for emailId={}: root={}, size={}", emailId, root.getId(), entries.size());
        return dto;
    }

    // ═══════════════════════════════════════════════════════════════
    // PACKAGE-PRIVATE (für Tests zugänglich)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Wandert rekursiv via {@code parentEmail} bis zur Wurzel-E-Mail.
     * Cycle-Schutz über besuchte IDs – bricht bei Cycle ab und gibt letztes valides Element zurück.
     *
     * @param email Ausgangspunkt
     * @return Wurzel-E-Mail (parentEmail == null oder Cycle erkannt)
     */
    Email findRoot(Email email) {
        Set<Long> visited = new HashSet<>();
        Email current = email;
        while (current.getParentEmail() != null) {
            if (visited.contains(current.getId())) {
                log.warn("Cycle detected in email thread at id={}", current.getId());
                break;
            }
            visited.add(current.getId());
            current = current.getParentEmail();
        }
        return current;
    }

    /**
     * Sammelt alle E-Mails im Thread via BFS über die {@code replies}-Liste,
     * beginnend bei der Wurzel. Sortiert das Ergebnis nach {@code sentAt ASC}.
     * Cycle-Schutz analog zu {@link #findRoot}.
     *
     * @param root Wurzel-E-Mail des Threads
     * @return Chronologisch sortierte Liste aller Thread-Einträge (inkl. root)
     */
    List<Email> collectThread(Email root) {
        List<Email> result = new ArrayList<>();
        Set<Long> visited = new HashSet<>();

        // BFS
        java.util.Queue<Email> queue = new java.util.ArrayDeque<>();
        queue.add(root);

        while (!queue.isEmpty()) {
            Email current = queue.poll();
            if (current == null || visited.contains(current.getId())) {
                continue;
            }
            visited.add(current.getId());
            result.add(current);

            List<Email> replies = current.getReplies();
            if (replies != null) {
                queue.addAll(replies);
            }
        }

        // Chronologisch sortieren (älteste zuerst)
        result.sort((a, b) -> {
            if (a.getSentAt() == null && b.getSentAt() == null) return 0;
            if (a.getSentAt() == null) return -1;
            if (b.getSentAt() == null) return 1;
            return a.getSentAt().compareTo(b.getSentAt());
        });

        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    // MAPPING
    // ═══════════════════════════════════════════════════════════════

    private EmailThreadEntryDto toEntryDto(Email email) {
        EmailThreadEntryDto dto = new EmailThreadEntryDto();
        dto.setId(email.getId());
        dto.setSubject(email.getSubject());
        dto.setFromAddress(email.getFromAddress());
        dto.setRecipient(email.getRecipient());
        dto.setSentAt(email.getSentAt() != null ? email.getSentAt().format(ISO_FORMATTER) : null);
        dto.setDirection(email.getDirection() != null ? email.getDirection().name() : null);
        boolean fwd = isForwardedEmail(email);
        dto.setForwarded(fwd);
        dto.setSnippet(buildSnippet(email, fwd));

        List<EmailThreadEntryDto.AttachmentDto> attachmentDtos = new ArrayList<>();
        if (email.getAttachments() != null) {
            for (EmailAttachment att : email.getAttachments()) {
                EmailThreadEntryDto.AttachmentDto attDto = new EmailThreadEntryDto.AttachmentDto();
                attDto.setId(att.getId());
                attDto.setOriginalFilename(att.getOriginalFilename());
                attDto.setMimeType(att.getMimeType());
                attDto.setSizeBytes(att.getSizeBytes());
                attDto.setContentId(att.getContentId());
                attDto.setInline(Boolean.TRUE.equals(att.getInlineAttachment()));
                attachmentDtos.add(attDto);
            }
        }
        dto.setAttachments(attachmentDtos);

        // Vollständiger HTML-Body mit rewritten CID-URLs für die expandierte Ansicht
        dto.setHtmlBody(buildHtmlBody(email));

        return dto;
    }

    /**
     * Liefert den vollständigen HTML-Body der E-Mail mit ersetzten cid:-Referenzen,
     * sodass Inline-Bilder direkt über die REST-API geladen werden können.
     */
    private String buildHtmlBody(Email email) {
        String html = email.getHtmlBody();
        if (html != null && !html.isBlank() && email.getAttachments() != null && !email.getAttachments().isEmpty()) {
            final Long emailId = email.getId();
            html = InlineAttachmentUtil.rewriteCidSources(
                    html,
                    email.getAttachments(),
                    att -> Boolean.TRUE.equals(att.getInlineAttachment())
                            || (att.getContentId() != null && !att.getContentId().isBlank()),
                    EmailAttachment::getContentId,
                    att -> "/api/emails/" + emailId + "/attachments/" + att.getId());
        }
        // Fallback auf Plain-Text wenn kein HTML vorhanden
        if (html == null || html.isBlank()) {
            String plain = email.getBody();
            if (plain != null && !plain.isBlank()) {
                html = "<pre style=\"white-space:pre-wrap;font-family:inherit;margin:0\">"
                        + plain
                                .replace("&", "&amp;")
                                .replace("<", "&lt;")
                                .replace(">", "&gt;")
                        + "</pre>";
            }
        }
        return html;
    }

    /**
     * Erkennt weitergeleitete E-Mails anhand des Betreff-Präfixes oder des Textinhalts.
     */
    private boolean isForwardedEmail(Email email) {
        String subject = email.getSubject();
        if (subject != null) {
            String s = subject.stripLeading().toLowerCase();
            if (s.startsWith("fwd:") || s.startsWith("fw:") || s.startsWith("wg:") || s.startsWith("weitergeleitet:")) {
                return true;
            }
        }
        String body = email.getBody();
        if (body != null && body.stripLeading().startsWith("---------- Forwarded message")) {
            return true;
        }
        return false;
    }

    /**
     * Erstellt einen sauberen Vorschau-Snippet ohne CSS/Script-Inhalt.
     * Bei weitergeleiteten E-Mails wird der Forwarded-Header übersprungen.
     */
    private String buildSnippet(Email email, boolean forwarded) {
        // 1. Bevorzuge Plain-Text-Body (bereits ohne HTML)
        String text = email.getBody();
        if (text != null && !text.isBlank()) {
            text = text.strip();
            if (forwarded) {
                text = stripForwardedHeaders(text);
            }
            return text.length() > SNIPPET_MAX_LENGTH
                    ? text.substring(0, SNIPPET_MAX_LENGTH) + "…"
                    : text;
        }

        // 2. Fallback: HTML bereinigen
        String html = email.getHtmlBody();
        if (html == null || html.isBlank()) return "";

        // style- und script-Blöcke vollständig entfernen (inkl. Inhalt)
        text = html
                .replaceAll("(?si)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?si)<script[^>]*>.*?</script>", " ")
                // Zeilenumbrüche aus Block-Tags erzeugen
                .replaceAll("(?i)<br\\s*/?>", " ")
                .replaceAll("(?i)</(p|div|tr|li|h[1-6])>", " ")
                // alle restlichen Tags entfernen
                .replaceAll("<[^>]+>", "")
                // HTML-Entities dekodieren
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                // Whitespace normalisieren
                .replaceAll("\\s+", " ")
                .strip();

        return text.length() > SNIPPET_MAX_LENGTH
                ? text.substring(0, SNIPPET_MAX_LENGTH) + "…"
                : text;
    }

    /**
     * Überspringt alle "---------- Forwarded message ---------"-Blöcke und
     * liefert den ersten echten Textinhalt danach.
     * Findet keine echten Inhalte → gibt "[Weitergeleitet]" zurück.
     */
    private String stripForwardedHeaders(String text) {
        String[] lines = text.split("\\r?\\n");
        boolean inHeader = false;
        StringBuilder real = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("---------- Forwarded message") || trimmed.startsWith("-------- Weitergeleitete")) {
                inHeader = true;
                continue;
            }
            if (inHeader) {
                // Header-Zeilen: Von/From, Date, Subject, To – überspringen
                if (trimmed.isEmpty()) {
                    inHeader = false; // Leerzeile = Ende des Headers
                } else if (trimmed.matches("(?i)(von|from|date|datum|subject|betreff|to|an):.*")) {
                    continue;
                } else {
                    inHeader = false;
                    real.append(trimmed).append(" ");
                }
            } else if (!trimmed.isEmpty()) {
                real.append(trimmed).append(" ");
            }
        }
        String result = real.toString().strip();
        return result.isEmpty() ? "[Weitergeleitet]" : result;
    }
}
