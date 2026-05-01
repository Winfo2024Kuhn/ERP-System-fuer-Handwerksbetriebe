package org.example.kalkulationsprogramm.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.example.email.EmailService;
import org.example.kalkulationsprogramm.domain.EmailTextTemplate;
import org.example.kalkulationsprogramm.dto.Email.EmailTextTemplateDto;
import org.example.kalkulationsprogramm.repository.EmailTextTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmailTextTemplateService {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([A-Z0-9_]+)\\s*\\}\\}");

    private final EmailTextTemplateRepository repository;

    @Transactional(readOnly = true)
    public List<EmailTextTemplate> list() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(EmailTextTemplate::getDokumentTyp, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    @Transactional(readOnly = true)
    public EmailTextTemplate get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("E-Mail-Textvorlage nicht gefunden"));
    }

    @Transactional(readOnly = true)
    public Optional<EmailTextTemplate> findByDokumentTyp(String dokumentTyp) {
        if (dokumentTyp == null || dokumentTyp.isBlank()) {
            return Optional.empty();
        }
        return repository.findByDokumentTyp(dokumentTyp.trim().toUpperCase());
    }

    public EmailTextTemplate create(EmailTextTemplateDto dto) {
        EmailTextTemplate entity = new EmailTextTemplate();
        dto.applyToEntity(entity);
        return repository.save(entity);
    }

    public EmailTextTemplate update(Long id, EmailTextTemplateDto dto) {
        EmailTextTemplate entity = get(id);
        dto.applyToEntity(entity);
        return repository.save(entity);
    }

    public void delete(Long id) {
        if (id == null) {
            return;
        }
        repository.deleteById(id);
    }

    /**
     * Renders the active DB template for the given dokumentTyp by replacing
     * {{TOKEN}} placeholders with values from the context map. Returns null when
     * no active template is stored, so callers can fall back to the hardcoded
     * EmailService builders.
     */
    @Transactional(readOnly = true)
    public EmailService.EmailContent render(String dokumentTyp, Map<String, String> context) {
        return findByDokumentTyp(dokumentTyp)
                .filter(EmailTextTemplate::isAktiv)
                .map(template -> new EmailService.EmailContent(
                        replacePlaceholders(template.getSubjectTemplate(), context),
                        replacePlaceholders(template.getHtmlBody(), context)))
                .orElse(null);
    }

    private String replacePlaceholders(String input, Map<String, String> context) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(input);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String token = matcher.group(1);
            String value = context != null ? context.getOrDefault(token, "") : "";
            matcher.appendReplacement(out, Matcher.quoteReplacement(value == null ? "" : value));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
