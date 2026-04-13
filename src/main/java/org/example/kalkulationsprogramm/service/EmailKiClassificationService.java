package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * KI-gestützte Email-Klassifizierung via lokalem Ollama-Modell.
 * <p>
 * Wird aktiviert, wenn bei der automatischen Zuordnung mehrere Projekte/Anfragen
 * als Kandidaten in Frage kommen und die regelbasierte Schlagwortsuche keine
 * eindeutige Zuordnung liefern konnte.
 * <p>
 * Ablauf:
 * 1. Vorfilterung über Kunden-Emails → Kandidaten-Liste
 * 2. Pro Kandidat: Key-Value-Steckbrief + bisheriger Email-Verlauf
 * 3. KI analysiert die eingehende Email im Kontext aller Kandidaten
 * 4. KI gibt den Schlüssel (Key) des passenden Kandidaten zurück
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailKiClassificationService {

    private final OllamaService ollamaService;
    private final EmailRepository emailRepository;
    private final ObjectMapper objectMapper;

    /**
     * System-Prompt für die Email-Zuordnung.
     * Maximale Präzision durch strikte Anweisungen, Formatvorgabe und Negativ-Beispiele.
     */
    private static final String CLASSIFICATION_SYSTEM_PROMPT = """
            Du bist ein hochspezialisierter Klassifizierungs-Agent für ein ERP-System eines deutschen Handwerksbetriebs.
            Deine EINZIGE Aufgabe: Ordne eine eingehende E-Mail EXAKT EINEM Projekt oder einer Anfrage zu.
            
            ═══════════════════════════════════════════
            KONTEXT
            ═══════════════════════════════════════════
            
            Du erhältst:
            1. DIE EINGEHENDE E-MAIL (Betreff, Absender, Text)
            2. EINE LISTE VON KANDIDATEN – jeder Kandidat ist ein Projekt oder eine Anfrage mit:
               - Key (eindeutige ID, z.B. "PROJEKT_42" oder "ANFRAGE_17")
               - Bauvorhaben (Name des Bauprojekts/Auftrags)
               - Kurzbeschreibung (was gemacht wird)
               - Auftragsnummer (falls vorhanden)
               - Kunden-Emails (welche Email-Adressen zu diesem Projekt gehören)
               - Bisheriger Email-Verlauf (die letzten Emails in diesem Thread, chronologisch)
            
            ═══════════════════════════════════════════
            ENTSCHEIDUNGSREGELN (absteigend nach Priorität)
            ═══════════════════════════════════════════
            
            1. DIREKTER BEZUG: Wird im Betreff oder Text eine Auftragsnummer, ein Bauvorhaben-Name,
               oder ein spezifisches Detail (Adresse, Stockwerk, Raum, Material) aus einem Kandidaten
               EXPLIZIT genannt? → Dieser Kandidat gewinnt.
            
            2. THEMATISCHE ÜBEREINSTIMMUNG: Passt der Inhalt der Email (beschriebene Arbeiten,
               Materialien, Probleme, Terminvereinbarungen) thematisch zu genau einem der Kandidaten?
               Vergleiche mit Bauvorhaben + Kurzbeschreibung + bisherigem Email-Verlauf.
            
            3. KONVERSATIONS-KONTEXT: Ist die E-Mail offensichtlich eine Antwort auf oder Fortsetzung
               einer Konversation, die im Email-Verlauf eines Kandidaten sichtbar ist?
               Gleiche Themen, gleiche Ansprechpartner, gleicher Tonfall = starkes Signal.
            
            4. ZEITLICHE NÄHE: Wenn der Email-Verlauf eines Kandidaten kürzlich aktiv war und
               die neue Email thematisch dazu passt, bevorzuge diesen Kandidaten.
            
            5. ABSENDER-KONTEXT: Wenn der Absender in nur einem Kandidaten-Verlauf vorkommt,
               ist das ein starkes Indiz (aber NICHT allein ausreichend, wenn die Email
               inhaltlich offensichtlich zu einem anderen Kandidaten gehört).
            
            ═══════════════════════════════════════════
            ANTWORTFORMAT – STRIKT EINHALTEN
            ═══════════════════════════════════════════
            
            Antworte AUSSCHLIESSLICH mit einem JSON-Objekt. Kein Fließtext. Keine Erklärung außerhalb des JSON.
            
            Format:
            {
              "key": "<EXAKTER Key des besten Kandidaten>",
              "confidence": <Zahl 0.0-1.0>,
              "reason": "<1 kurzer Satz auf Deutsch warum>"
            }
            
            - "key": MUSS exakt einem der übergebenen Keys entsprechen (z.B. "PROJEKT_42")
            - "confidence": Deine Zuversicht (0.5 = unsicher, 0.8+ = sehr sicher)
            - "reason": Maximal 1 Satz Begründung
            
            WENN du dir NICHT sicher bist (confidence < 0.5):
            {
              "key": "NONE",
              "confidence": 0.0,
              "reason": "Keine eindeutige Zuordnung möglich"
            }
            
            ═══════════════════════════════════════════
            VERBOTEN
            ═══════════════════════════════════════════
            
            - Erfinde NIEMALS einen Key, der nicht in der Kandidaten-Liste steht.
            - Gib NIEMALS mehr als einen Key zurück.
            - Schreibe NICHTS außer dem JSON-Objekt.
            - Lass dich NICHT durch Email-Signaturen, Werbung oder Disclaimer im Email-Text ablenken.
            - Bewerte NICHT den Inhalt der Email moralisch oder rechtlich.
            """;

    /**
     * Versucht eine Email per KI einem der Kandidaten-Projekte/-Anfragen zuzuordnen.
     *
     * @param email     Die zu klassifizierende Email
     * @param projekte  Kandidaten-Projekte (vorgefilert über Email-Adresse)
     * @param anfragen  Kandidaten-Anfragen (vorgefiltert über Email-Adresse)
     * @return Das Klassifizierungsergebnis (kann NONE sein)
     */
    @Transactional(readOnly = true)
    public ClassificationResult classify(Email email, List<Projekt> projekte, List<Anfrage> anfragen) {
        if (!ollamaService.isEnabled()) {
            log.debug("[KI-Classify] Ollama deaktiviert – überspringe KI-Zuordnung");
            return ClassificationResult.none("Ollama deaktiviert");
        }

        if (projekte.isEmpty() && anfragen.isEmpty()) {
            return ClassificationResult.none("Keine Kandidaten");
        }

        try {
            String userPrompt = buildUserPrompt(email, projekte, anfragen);
            String response = ollamaService.chat(CLASSIFICATION_SYSTEM_PROMPT, userPrompt);
            return parseResponse(response, projekte, anfragen);
        } catch (Exception e) {
            log.warn("[KI-Classify] Fehler bei KI-Klassifizierung: {}", e.getMessage());
            return ClassificationResult.none("KI-Fehler: " + e.getMessage());
        }
    }

    /**
     * Baut den User-Prompt mit allen Kandidaten-Daten und Email-Verläufen.
     */
    public String buildUserPrompt(Email email, List<Projekt> projekte, List<Anfrage> anfragen) {
        StringBuilder sb = new StringBuilder();

        // === Die eingehende Email ===
        sb.append("═══ EINGEHENDE E-MAIL ═══\n");
        sb.append("Betreff: ").append(nullSafe(email.getSubject())).append("\n");
        sb.append("Von: ").append(nullSafe(email.getFromAddress())).append("\n");
        sb.append("Datum: ").append(email.getSentAt() != null ? email.getSentAt().toString() : "unbekannt").append("\n");
        sb.append("Text:\n").append(truncate(nullSafe(email.getBody()), 2000)).append("\n\n");

        // === Kandidaten ===
        sb.append("═══ KANDIDATEN ═══\n\n");

        // Projekte
        for (Projekt p : projekte) {
            String key = "PROJEKT_" + p.getId();
            sb.append("--- Kandidat: ").append(key).append(" ---\n");
            sb.append("Typ: Projekt\n");
            sb.append("Bauvorhaben: ").append(nullSafe(p.getBauvorhaben())).append("\n");
            sb.append("Kurzbeschreibung: ").append(nullSafe(p.getKurzbeschreibung())).append("\n");
            if (p.getAuftragsnummer() != null) {
                sb.append("Auftragsnummer: ").append(p.getAuftragsnummer()).append("\n");
            }
            sb.append("Kunden-Emails: ").append(p.getAllEmails()).append("\n");

            // Email-Verlauf laden
            List<Email> verlauf = emailRepository.findByProjektOrderBySentAtDesc(p);
            appendEmailVerlauf(sb, verlauf);
            sb.append("\n");
        }

        // Anfragen
        for (Anfrage a : anfragen) {
            String key = "ANFRAGE_" + a.getId();
            sb.append("--- Kandidat: ").append(key).append(" ---\n");
            sb.append("Typ: Anfrage\n");
            sb.append("Bauvorhaben: ").append(nullSafe(a.getBauvorhaben())).append("\n");
            sb.append("Kurzbeschreibung: ").append(nullSafe(a.getKurzbeschreibung())).append("\n");
            sb.append("Kunden-Emails: ").append(a.getKundenEmails()).append("\n");

            List<Email> verlauf = emailRepository.findByAnfrageOrderBySentAtDesc(a);
            appendEmailVerlauf(sb, verlauf);
            sb.append("\n");
        }

        sb.append("═══ ENDE DER KANDIDATEN ═══\n\n");
        sb.append("Ordne die eingehende E-Mail dem passendsten Kandidaten zu. Antworte NUR mit JSON.");

        return sb.toString();
    }

    /**
     * Hängt die letzten Emails eines Verlaufs an (max. 10, truncated).
     */
    private void appendEmailVerlauf(StringBuilder sb, List<Email> verlauf) {
        if (verlauf.isEmpty()) {
            sb.append("Email-Verlauf: (keine bisherigen Emails)\n");
            return;
        }

        // Maximal 10 Emails, neueste zuerst
        List<Email> limited = verlauf.stream().limit(10).collect(Collectors.toList());
        // Chronologisch (älteste zuerst)
        Collections.reverse(limited);

        sb.append("Email-Verlauf (letzte ").append(limited.size()).append(" von ").append(verlauf.size()).append("):\n");
        for (Email e : limited) {
            sb.append("  [").append(e.getDirection() == EmailDirection.IN ? "EINGANG" : "AUSGANG").append("] ");
            sb.append(e.getSentAt() != null ? e.getSentAt().toLocalDate() : "?").append(" | ");
            sb.append("Von: ").append(nullSafe(e.getFromAddress())).append(" | ");
            sb.append("Betreff: ").append(nullSafe(e.getSubject())).append("\n");
            sb.append("  ").append(truncate(nullSafe(e.getBody()), 300)).append("\n");
        }
    }

    /**
     * Parsed die KI-Antwort und löst den Key zum Entitäts-Typ auf.
     */
    public ClassificationResult parseResponse(String response, List<Projekt> projekte, List<Anfrage> anfragen) {
        try {
            // JSON extrahieren: KI gibt manchmal Text drumherum
            String jsonStr = extractJson(response);
            JsonNode json = objectMapper.readTree(jsonStr);

            String key = json.path("key").asText("NONE");
            double confidence = json.path("confidence").asDouble(0.0);
            String reason = json.path("reason").asText("");

            log.info("[KI-Classify] Ergebnis: key={}, confidence={}, reason={}", key, confidence, reason);

            if ("NONE".equals(key) || confidence < 0.5) {
                return ClassificationResult.none(reason);
            }

            // Key auflösen
            if (key.startsWith("PROJEKT_")) {
                Long id = Long.parseLong(key.substring("PROJEKT_".length()));
                Optional<Projekt> match = projekte.stream().filter(p -> p.getId().equals(id)).findFirst();
                if (match.isPresent()) {
                    return new ClassificationResult(
                            EmailZuordnungTyp.PROJEKT, match.get().getId(), confidence, reason, key);
                }
            } else if (key.startsWith("ANFRAGE_")) {
                Long id = Long.parseLong(key.substring("ANFRAGE_".length()));
                Optional<Anfrage> match = anfragen.stream().filter(a -> a.getId().equals(id)).findFirst();
                if (match.isPresent()) {
                    return new ClassificationResult(
                            EmailZuordnungTyp.ANFRAGE, match.get().getId(), confidence, reason, key);
                }
            }

            log.warn("[KI-Classify] KI hat unbekannten Key zurückgegeben: {}", key);
            return ClassificationResult.none("Ungültiger Key: " + key);

        } catch (Exception e) {
            log.warn("[KI-Classify] Fehler beim Parsen der KI-Antwort: {} – Raw: {}", e.getMessage(), response);
            return ClassificationResult.none("Parse-Fehler: " + e.getMessage());
        }
    }

    /**
     * Extrahiert JSON-Objekt aus einer Antwort die evtl. Text drumherum hat.
     */
    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }

    private String truncate(String text, int maxLen) {
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "…";
    }

    // ═══════════════════════════════════════════════════════════════
    // Ergebnis-DTO
    // ═══════════════════════════════════════════════════════════════

    public record ClassificationResult(
            EmailZuordnungTyp zuordnungTyp,
            Long entityId,
            double confidence,
            String reason,
            String key
    ) {
        public static ClassificationResult none(String reason) {
            return new ClassificationResult(EmailZuordnungTyp.KEINE, null, 0.0, reason, "NONE");
        }

        public boolean isAssigned() {
            return zuordnungTyp != EmailZuordnungTyp.KEINE && entityId != null;
        }
    }
}
