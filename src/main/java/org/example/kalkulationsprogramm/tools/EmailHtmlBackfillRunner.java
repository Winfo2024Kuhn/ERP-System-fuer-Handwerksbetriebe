package org.example.kalkulationsprogramm.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.util.EmailHtmlSanitizer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailHtmlBackfillRunner {

    private final org.example.kalkulationsprogramm.repository.EmailRepository emailRepository;

    @Transactional
    public void run() {
        int updated = backfillEmails();
        log.info("HTML-Backfill abgeschlossen: {} E-Mails aktualisiert.", updated);
    }

    private int backfillEmails() {
        List<org.example.kalkulationsprogramm.domain.Email> changed = new ArrayList<>();
        for (org.example.kalkulationsprogramm.domain.Email email : emailRepository.findAll()) {
            if (refreshBodies(email.getRawBody(), email.getHtmlBody(), email.getBody(),
                    email::setHtmlBody, email::setBody)) {
                changed.add(email);
            }
        }
        if (!changed.isEmpty()) {
            emailRepository.saveAll(changed);
        }
        return changed.size();
    }

    private boolean refreshBodies(String rawBody,
                                  String currentHtml,
                                  String currentPlain,
                                  Consumer<String> htmlSetter,
                                  Consumer<String> plainSetter) {
        SanitizedBodies sanitized = sanitizeBodies(rawBody, currentHtml, currentPlain);
        if (sanitized == null) {
            return false;
        }
        boolean changed = false;
        if (sanitized.detailHtml() != null && !sanitized.detailHtml().equals(currentHtml)) {
            htmlSetter.accept(sanitized.detailHtml());
            changed = true;
        }
        if (sanitized.plainText() != null && !sanitized.plainText().equals(currentPlain)) {
            plainSetter.accept(sanitized.plainText());
            changed = true;
        }
        return changed;
    }

    private SanitizedBodies sanitizeBodies(String raw, String html, String plain) {
        String source = firstNonBlank(raw, html, plain);
        if (source == null || source.isBlank()) {
            return null;
        }
        String detailHtml = EmailHtmlSanitizer.sanitizeDetailHtml(source);
        String previewSource = detailHtml != null ? detailHtml : source;
        String previewHtml = EmailHtmlSanitizer.sanitizePreviewHtml(previewSource);
        String plainText = EmailHtmlSanitizer.htmlToPlainText(previewHtml != null ? previewHtml : previewSource);
        if ((detailHtml == null || detailHtml.isBlank()) && (plainText == null || plainText.isBlank())) {
            return null;
        }
        return new SanitizedBodies(
                detailHtml != null ? detailHtml : previewSource,
                plainText != null ? plainText.strip() : null
        );
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private record SanitizedBodies(String detailHtml, String plainText) {
    }
}
