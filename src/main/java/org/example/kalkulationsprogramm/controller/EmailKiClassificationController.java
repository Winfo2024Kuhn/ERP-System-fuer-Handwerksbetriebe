package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.service.EmailKiClassificationService;
import org.example.kalkulationsprogramm.service.EmailKiClassificationService.ClassificationResult;
import org.example.kalkulationsprogramm.service.OllamaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * REST-Controller zum Testen der KI-basierten Email-Klassifizierung via Ollama.
 */
@Slf4j
@RestController
@RequestMapping("/api/email-ki")
@RequiredArgsConstructor
public class EmailKiClassificationController {

    private final OllamaService ollamaService;
    private final EmailKiClassificationService classificationService;
    private final EmailRepository emailRepository;
    private final ProjektRepository projektRepository;
    private final AnfrageRepository anfrageRepository;

    /**
     * Prüft ob der Ollama-Server erreichbar ist.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        boolean available = ollamaService.isAvailable();
        return ResponseEntity.ok(Map.of(
                "ollamaEnabled", ollamaService.isEnabled(),
                "ollamaAvailable", available
        ));
    }

    /**
     * Klassifiziert eine bestehende Email per KI.
     * Findet automatisch die Kandidaten-Projekte/-Anfragen über die Absender-Email.
     *
     * @param emailId Die ID der zu klassifizierenden Email
     */
    @PostMapping("/classify/{emailId}")
    public ResponseEntity<Map<String, Object>> classifyEmail(@PathVariable Long emailId) {
        Optional<Email> emailOpt = emailRepository.findById(emailId);
        if (emailOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Email nicht gefunden: " + emailId));
        }
        Email email = emailOpt.get();

        String fromAddress = email.getFromAddress();
        if (fromAddress == null || fromAddress.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email hat keine Absender-Adresse"));
        }

        String emailLower = fromAddress.toLowerCase().trim();

        // Kandidaten finden (gleiche Logik wie EmailAutoAssignmentService)
        List<Projekt> projekte = projektRepository.findByKundenEmail(emailLower);
        List<Anfrage> anfragen = anfrageRepository.findByKundenEmail(emailLower);

        if (projekte.isEmpty() && anfragen.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "result", "NO_CANDIDATES",
                    "message", "Keine Projekte/Anfragen mit Email-Adresse " + fromAddress + " gefunden"
            ));
        }

        ClassificationResult result = classificationService.classify(email, projekte, anfragen);

        return ResponseEntity.ok(Map.of(
                "emailId", emailId,
                "subject", nullSafe(email.getSubject()),
                "fromAddress", nullSafe(email.getFromAddress()),
                "candidateCount", projekte.size() + anfragen.size(),
                "candidates", buildCandidateList(projekte, anfragen),
                "result", Map.of(
                        "key", result.key(),
                        "zuordnungTyp", result.zuordnungTyp().name(),
                        "entityId", result.entityId() != null ? result.entityId() : "null",
                        "confidence", result.confidence(),
                        "reason", nullSafe(result.reason()),
                        "assigned", result.isAssigned()
                )
        ));
    }

    /**
     * Klassifiziert eine Email UND wendet die Zuordnung direkt an (wenn confidence >= 0.6).
     */
    @PostMapping("/classify-and-assign/{emailId}")
    public ResponseEntity<Map<String, Object>> classifyAndAssign(@PathVariable Long emailId) {
        Optional<Email> emailOpt = emailRepository.findById(emailId);
        if (emailOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Email nicht gefunden: " + emailId));
        }
        Email email = emailOpt.get();

        String fromAddress = email.getFromAddress();
        if (fromAddress == null || fromAddress.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email hat keine Absender-Adresse"));
        }

        String emailLower = fromAddress.toLowerCase().trim();
        List<Projekt> projekte = projektRepository.findByKundenEmail(emailLower);
        List<Anfrage> anfragen = anfrageRepository.findByKundenEmail(emailLower);

        ClassificationResult result = classificationService.classify(email, projekte, anfragen);

        boolean applied = false;
        if (result.isAssigned() && result.confidence() >= 0.6) {
            if (result.zuordnungTyp() == EmailZuordnungTyp.PROJEKT) {
                projekte.stream()
                        .filter(p -> p.getId().equals(result.entityId()))
                        .findFirst()
                        .ifPresent(p -> {
                            email.assignToProjekt(p);
                            emailRepository.save(email);
                        });
                applied = true;
            } else if (result.zuordnungTyp() == EmailZuordnungTyp.ANFRAGE) {
                anfragen.stream()
                        .filter(a -> a.getId().equals(result.entityId()))
                        .findFirst()
                        .ifPresent(a -> {
                            email.assignToAnfrage(a);
                            emailRepository.save(email);
                        });
                applied = true;
            }
        }

        return ResponseEntity.ok(Map.of(
                "emailId", emailId,
                "result", Map.of(
                        "key", result.key(),
                        "confidence", result.confidence(),
                        "reason", nullSafe(result.reason()),
                        "assigned", result.isAssigned()
                ),
                "applied", applied,
                "minConfidence", 0.6
        ));
    }

    /**
     * Zeigt den generierten Prompt für eine Email (Debug/Entwicklung).
     */
    @GetMapping("/debug-prompt/{emailId}")
    public ResponseEntity<Map<String, Object>> debugPrompt(@PathVariable Long emailId) {
        Optional<Email> emailOpt = emailRepository.findById(emailId);
        if (emailOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Email nicht gefunden: " + emailId));
        }
        Email email = emailOpt.get();

        String fromAddress = email.getFromAddress();
        if (fromAddress == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Kein Absender"));
        }

        String emailLower = fromAddress.toLowerCase().trim();
        List<Projekt> projekte = projektRepository.findByKundenEmail(emailLower);
        List<Anfrage> anfragen = anfrageRepository.findByKundenEmail(emailLower);

        String prompt = classificationService.buildUserPrompt(email, projekte, anfragen);

        return ResponseEntity.ok(Map.of(
                "emailId", emailId,
                "subject", nullSafe(email.getSubject()),
                "candidateCount", projekte.size() + anfragen.size(),
                "promptLength", prompt.length(),
                "prompt", prompt
        ));
    }

    private List<Map<String, String>> buildCandidateList(List<Projekt> projekte, List<Anfrage> anfragen) {
        List<Map<String, String>> list = new java.util.ArrayList<>();
        for (Projekt p : projekte) {
            list.add(Map.of(
                    "key", "PROJEKT_" + p.getId(),
                    "typ", "PROJEKT",
                    "bauvorhaben", nullSafe(p.getBauvorhaben())
            ));
        }
        for (Anfrage a : anfragen) {
            list.add(Map.of(
                    "key", "ANFRAGE_" + a.getId(),
                    "typ", "ANFRAGE",
                    "bauvorhaben", nullSafe(a.getBauvorhaben())
            ));
        }
        return list;
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }
}
