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
import org.example.kalkulationsprogramm.util.EmailButtonHtml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmailTextTemplateService {

    private static final Logger log = LoggerFactory.getLogger(EmailTextTemplateService.class);
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([A-Z0-9_]+)\\s*\\}\\}");

    /**
     * Platzhalter, deren Wert bereits fertiges, abgesichertes HTML ist (z. B. der
     * Bewertungslink als {@code <a>}). Alle anderen Werte sind Klartext und werden im
     * HTML-Body maskiert.
     */
    private static final java.util.Set<String> HTML_PLATZHALTER = java.util.Set.of("REVIEW_LINK");

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
        Map<String, String> mergedContext = withFirmenPlatzhalter(context);
        return findByDokumentTyp(dokumentTyp)
                .filter(EmailTextTemplate::isAktiv)
                .map(template -> new EmailService.EmailContent(
                        replacePlaceholders(template.getSubjectTemplate(), mergedContext, false),
                        EmailButtonHtml.entferneButtonsOhneZiel(
                                replacePlaceholders(template.getHtmlBody(), mergedContext, true))))
                .orElse(null);
    }

    /**
     * Reichert den Caller-Kontext um Firmen-weite Platzhalter (BANK, IBAN, BIC,
     * REVIEW_URL) aus den Firmeneinstellungen an. REVIEW_URL ist die reine
     * Adresse des Google-Bewertungs-Links und steht im href von Buttons; ohne
     * hinterlegten Link bleibt sie leer und der Button faellt beim Rendern weg.
     * Caller-Werte gewinnen, damit explizite Overrides in Tests oder
     * Sonderfaellen weiter funktionieren.
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
                merged.put("REVIEW_URL", EmailButtonHtml.alsSichereAdresse(firma.getGoogleBewertungsLink()));
            }
        } catch (RuntimeException ex) {
            log.warn("Firmen-Platzhalter (BANK/IBAN/BIC/REVIEW_URL) konnten nicht aus den Firmenstammdaten geladen werden – sie bleiben in der E-Mail leer: {}",
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

    /**
     * Setzt die Platzhalter ein. Im HTML-Body werden Klartext-Werte maskiert, damit
     * Kundennamen, Bauvorhaben oder Freitext aus dem Anfrage-Formular kein Markup in die
     * Mail schmuggeln. Der Betreff ist Klartext und bleibt unverändert.
     */
    private String replacePlaceholders(String input, Map<String, String> context, boolean html) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(input);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String token = matcher.group(1);
            String value = context != null ? context.getOrDefault(token, "") : "";
            if (value == null) {
                value = "";
            } else if (html && !HTML_PLATZHALTER.contains(token)) {
                value = org.springframework.web.util.HtmlUtils.htmlEscape(value, "UTF-8");
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
