package org.example.kalkulationsprogramm.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.example.email.EmailService;
import org.example.kalkulationsprogramm.domain.EmailTextTemplate;
import org.example.kalkulationsprogramm.dto.Email.EmailTextTemplateDto;
import org.example.kalkulationsprogramm.dto.FirmeninformationDto;
import org.example.kalkulationsprogramm.repository.EmailTextTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmailTextTemplateService {

    private static final Logger log = LoggerFactory.getLogger(EmailTextTemplateService.class);
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([A-Z0-9_]+)\\s*\\}\\}");

    private final EmailTextTemplateRepository repository;
    private final FirmeninformationService firmeninformationService;

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

    @Transactional
    public EmailTextTemplate create(EmailTextTemplateDto dto) {
        EmailTextTemplate entity = new EmailTextTemplate();
        dto.applyToEntity(entity);
        EmailTextTemplate saved = repository.save(entity);
        if (Boolean.TRUE.equals(dto.getStandard())) setStandard(saved);
        return saved;
    }

    @Transactional
    public EmailTextTemplate update(Long id, EmailTextTemplateDto dto) {
        EmailTextTemplate entity = get(id);
        String previousDokumentTyp = entity.getDokumentTyp();
        boolean previousStandard = entity.isStandard();
        dto.applyToEntity(entity);
        boolean dokumentTypGeaendert = !previousDokumentTyp.equals(entity.getDokumentTyp());
        if (dokumentTypGeaendert) {
            if (previousStandard) repository.deleteStandardAssignment(id);
            entity.setStandard(false);
        }
        EmailTextTemplate saved = repository.save(entity);
        if (dokumentTypGeaendert && previousStandard) {
            repository.findAllByDokumentTypOrderByIdAsc(previousDokumentTyp).stream()
                    .filter(EmailTextTemplate::isAktiv)
                    .findFirst()
                    .ifPresent(this::setStandard);
        }
        if (Boolean.TRUE.equals(dto.getStandard())) setStandard(saved);
        return saved;
    }

    private void setStandard(EmailTextTemplate template) {
        repository.clearStandardFlags(template.getDokumentTyp());
        repository.setStandardTemplate(template.getId());
        repository.markStandardTemplate(template.getId());
        template.setStandard(true);
    }

    @Transactional
    public void delete(Long id) {
        if (id == null) {
            return;
        }
        repository.deleteStandardAssignment(id);
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
        Map<String, String> mergedContext = withFirmenPlatzhalter(context);
        return findByDokumentTyp(dokumentTyp)
                .filter(EmailTextTemplate::isAktiv)
                .map(template -> new EmailService.EmailContent(
                        replacePlaceholders(template.getSubjectTemplate(), mergedContext),
                        replacePlaceholders(template.getHtmlBody(), mergedContext)))
                .orElse(null);
    }

    /**
     * Reichert den Caller-Kontext um Firmen-weite Platzhalter (BANK, IBAN, BIC)
     * aus den Firmeneinstellungen an. Caller-Werte gewinnen, damit explizite
     * Overrides in Tests oder Sonderfaellen weiter funktionieren.
     * <p>
     * Performance-Anmerkung: Wir laden die Firmeninformation pro render-Aufruf
     * neu. Bewusste Entscheidung, weil (a) die Mengen klein sind (typisch
     * &lt; 50 Mahnungen pro Versand-Lauf eines Handwerksbetriebs), (b) das
     * darunterliegende Repository ein simpler PK-Lookup ist und (c) ein Cache
     * Invalidierung beim Speichern der Firmeneinstellungen erforderlich machen
     * wuerde – das verschieben wir, bis ein Lastproblem messbar ist.
     * <p>
     * Wenn die Firmeninformation nicht geladen werden kann (z. B. in eng
     * gemockten Tests oder bei einer DB-Stoerung), loggen wir eine Warnung
     * und liefern die Vorlage trotzdem – mit leeren Bank-Tokens. Den Versand
     * deswegen abzubrechen waere schlimmer, weil der Handwerker dann gar
     * keine Mahnung mehr verschicken kann.
     */
    private Map<String, String> withFirmenPlatzhalter(Map<String, String> context) {
        Map<String, String> merged = new HashMap<>();
        try {
            FirmeninformationDto firma = firmeninformationService.getFirmeninformation();
            if (firma != null) {
                merged.put("BANK", nullToEmpty(firma.getBankName()));
                merged.put("IBAN", nullToEmpty(firma.getIban()));
                merged.put("BIC", nullToEmpty(firma.getBic()));
            }
        } catch (RuntimeException ex) {
            log.warn("Bank-Platzhalter (BANK/IBAN/BIC) konnten nicht aus den Firmenstammdaten geladen werden – sie bleiben in der E-Mail leer: {}",
                    ex.getMessage());
        }
        if (context != null) {
            merged.putAll(context);
        }
        return merged;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
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
