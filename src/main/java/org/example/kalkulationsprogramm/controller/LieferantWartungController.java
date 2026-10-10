package org.example.kalkulationsprogramm.controller;

import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.service.EmailAttachmentProcessingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Wartungsläufe für einzelne Lieferanten.
 *
 * <p>Bewusst unter {@code /api/admin/...} statt unter {@code /api/lieferanten/...}:
 * {@code /api/lieferanten/**} ist für die Zeiterfassungs-PWA ohne Anmeldung
 * freigeschaltet. Unter {@code /api/admin} greift die normale API-Kette mit
 * Session-Login, Admin-Rolle und CSRF-Schutz.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/lieferanten")
@AllArgsConstructor
public class LieferantWartungController {

    private final LieferantenRepository lieferantenRepository;
    private final EmailRepository emailRepository;
    private final EmailAttachmentProcessingService emailAttachmentProcessingService;

    /**
     * Verarbeitet alle PDF-Anhänge der Mails eines Lieferanten erneut.
     * Setzt das aiProcessed-Flag zurück. Anhänge, zu denen es schon ein Dokument gibt,
     * werden nur wieder damit verknüpft (kein zweites Dokument, kein KI-Aufruf); nur
     * Anhänge ohne Dokument werden neu gelesen. Bestehende Dokumente neu von der KI
     * lesen lassen: {@code POST /api/lieferant-dokumente/lieferant/{id}/reanalyze}.
     */
    @PostMapping("/{id}/reprocess-attachments")
    // KEIN @Transactional - jeder innere saveAndFlush soll sofort committen
    public ResponseEntity<Map<String, Object>> reprocessAttachments(@PathVariable Long id) {
        Lieferanten lieferant = lieferantenRepository.findById(id).orElse(null);
        if (lieferant == null) {
            return ResponseEntity.notFound().build();
        }
        log.info("Neuverarbeitung der Anhänge für Lieferant {} gestartet", id);

        List<Email> emails = emailRepository.findByLieferantIdOrderBySentAtDesc(id);

        int totalAttachments = 0;
        int processed = 0;

        for (var email : emails) {
            for (var attachment : email.getAttachments()) {
                // Nur PDFs verarbeiten - ZUGFeRD-XML ist in PDFs eingebettet
                if (attachment.getOriginalFilename() != null &&
                        attachment.getOriginalFilename().toLowerCase().endsWith(".pdf")) {
                    totalAttachments++;

                    // Flag zurücksetzen
                    attachment.setAiProcessed(false);
                    attachment.setAiProcessedAt(null);
                }
            }
        }

        if (totalAttachments > 0) {
            for (var email : emails) {
                try {
                    processed += emailAttachmentProcessingService.processLieferantAttachments(email);
                } catch (Exception e) {
                    // Nur die IDs ins Log, keine Mail-Inhalte (DSGVO); der Lauf geht weiter
                    log.warn("Anhänge der E-Mail {} von Lieferant {} nicht verarbeitet: {}",
                            email.getId(), id, e.getClass().getSimpleName());
                }
            }
        }

        return ResponseEntity.ok(Map.of(
                "lieferantId", id,
                "totalAttachments", totalAttachments,
                "processed", processed));
    }
}
