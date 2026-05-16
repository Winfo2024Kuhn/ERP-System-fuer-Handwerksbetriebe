package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageFunnelRequestDto;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * Prüft eingehende Funnel-Anfragen mit einem austauschbaren LLM-Backend
 * auf offensichtliche Spaß-/Spam-Eingaben (z. B. "Test 123", Beleidigungen,
 * unsinnige E-Mail-Adressen). Echte Anfragen werden durchgelassen, Spam
 * wird mit einer kurzen Begründung abgewiesen, damit das Webseiten-Frontend
 * dem Absender eine sinnvolle Fehlermeldung zeigen kann.
 *
 * <p>Welches Backend verwendet wird, entscheidet die System-Setting
 * {@code anfrage.funnel.spamfilter.provider} (Default: {@code lokal} –
 * spricht ein lokales LLM an, sodass personenbezogene Daten den Server
 * nicht verlassen). Implementierungen siehe {@link SpamFilterChatBackend}.
 *
 * <p>Verhalten bei Backend-Ausfall oder fehlender Konfiguration:
 * <b>durchlassen</b>. Wir wollen keine echten Kunden blockieren, nur weil
 * die KI nicht erreichbar ist – Spaß-Anfragen können später manuell
 * gelöscht werden.
 */
@Slf4j
@Service
public class AnfrageFunnelSpamFilterService {

    private static final String SYSTEM_PROMPT = """
            Du bist ein Spam-Filter für das Kontaktformular eines Handwerksbetriebs.
            Du bekommst eine Anfrage von der Webseite. Entscheide, ob es sich um
            eine ERNST GEMEINTE Anfrage handelt oder um Müll/Spaß/Beleidigung.

            Markiere als SPAM, wenn:
            - Name, E-Mail oder Nachricht offensichtlicher Unsinn sind
              ("test", "asdf", "qwertz", "111", "lol", "leck mich", "fick dich" o.ä.)
            - Die E-Mail-Adresse beleidigend oder unsinnig wirkt
              (z.B. "leckmichaa@test.de", "asdf@asdf.de", "test123@xyz")
            - Die Nachricht beleidigend, sexuell, drohend oder klar ironisch ist
            - Der gesamte Inhalt nach automatisiertem Spam aussieht
              (Werbung, SEO-Keywords, Links zu fremden Seiten)

            Behandle als ECHT, wenn auch nur grob plausibel:
            - Realistischer Name + plausible E-Mail + minimaler Bezug zum Handwerk
            - Auch kurze Anfragen sind OK ("Treppe gewünscht, bitte Rückruf")
            - Bei Zweifel: ECHT.

            Antworte AUSSCHLIESSLICH mit kompaktem JSON in genau diesem Format:
            {"spam": true|false, "grund": "kurzer deutscher Grund, max. 80 Zeichen"}
            """;

    private final List<SpamFilterChatBackend> backends;
    private final ObjectMapper objectMapper;
    private final SystemSettingsService systemSettingsService;

    public AnfrageFunnelSpamFilterService(List<SpamFilterChatBackend> backends,
                                          ObjectMapper objectMapper,
                                          SystemSettingsService systemSettingsService) {
        this.backends = backends;
        this.objectMapper = objectMapper;
        this.systemSettingsService = systemSettingsService;
    }

    /**
     * Liefert {@link Result#ok()}, wenn die Anfrage durchgelassen werden soll,
     * ansonsten {@link Result#spam(String)} mit Klartext-Begründung.
     */
    public Result pruefe(AnfrageFunnelRequestDto dto) {
        if (dto == null) {
            return Result.ok();
        }
        if (!systemSettingsService.isAnfrageFunnelSpamFilterAktiv()) {
            log.debug("Funnel-Spam-Filter übersprungen (im Firma-Editor deaktiviert)");
            return Result.ok();
        }

        Optional<SpamFilterChatBackend> backendOpt = waehleBackend();
        if (backendOpt.isEmpty()) {
            log.debug("Funnel-Spam-Filter übersprungen (kein Backend konfiguriert)");
            return Result.ok();
        }
        SpamFilterChatBackend backend = backendOpt.get();

        try {
            String raw = backend.chat(SYSTEM_PROMPT, baueUserPayload(dto));
            return parseAntwort(raw);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Funnel-Spam-Filter Backend '{}' unterbrochen, lasse Anfrage durch",
                    backend.identifier());
            return Result.ok();
        } catch (Exception e) {
            log.warn("Funnel-Spam-Filter Fehler über Backend '{}' ({}), lasse Anfrage durch",
                    backend.identifier(), e.getMessage());
            return Result.ok();
        }
    }

    private Optional<SpamFilterChatBackend> waehleBackend() {
        String gewuenscht = systemSettingsService.getAnfrageFunnelSpamFilterProvider();
        if ("aus".equalsIgnoreCase(gewuenscht)) {
            return Optional.empty();
        }
        // 1) Exakter Treffer auf den konfigurierten Provider, falls einsatzbereit.
        for (SpamFilterChatBackend b : backends) {
            if (b.identifier().equalsIgnoreCase(gewuenscht) && b.isEnabled()) {
                return Optional.of(b);
            }
        }
        // 2) Erstes einsatzbereites Backend (lokal hat Vorrang vor extern).
        for (SpamFilterChatBackend b : backends) {
            if (LocalSpamFilterChatBackend.ID.equals(b.identifier()) && b.isEnabled()) {
                return Optional.of(b);
            }
        }
        for (SpamFilterChatBackend b : backends) {
            if (b.isEnabled()) {
                return Optional.of(b);
            }
        }
        return Optional.empty();
    }

    private String baueUserPayload(AnfrageFunnelRequestDto dto) {
        StringBuilder sb = new StringBuilder();
        sb.append("Vorname: ").append(safe(dto.getVorname())).append('\n');
        sb.append("Nachname: ").append(safe(dto.getNachname())).append('\n');
        sb.append("E-Mail: ").append(safe(dto.getEmail())).append('\n');
        sb.append("Telefon: ").append(safe(dto.getTelefon())).append('\n');
        sb.append("Projekt-Anschrift: ").append(safe(dto.getProjektAnschrift())).append('\n');
        sb.append("Service-Typ: ").append(safe(dto.getServiceTyp())).append('\n');
        if (dto.getProjektarten() != null && !dto.getProjektarten().isEmpty()) {
            sb.append("Projektarten: ").append(String.join(", ", dto.getProjektarten())).append('\n');
        }
        sb.append("Nachricht: ").append(safe(dto.getNachricht())).append('\n');
        return sb.toString();
    }

    private Result parseAntwort(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Result.ok();
        }
        try {
            JsonNode node = objectMapper.readTree(raw.trim());
            boolean spam = node.path("spam").asBoolean(false);
            if (!spam) {
                return Result.ok();
            }
            String grund = node.path("grund").asText("Anfrage wirkt nicht ernst gemeint");
            return Result.spam(grund);
        } catch (Exception e) {
            log.warn("Funnel-Spam-Filter konnte Antwort nicht parsen: '{}'", raw);
            return Result.ok();
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    public record Result(boolean spam, String grund) {
        public static Result ok() {
            return new Result(false, null);
        }
        public static Result spam(String grund) {
            return new Result(true, grund);
        }
    }
}
