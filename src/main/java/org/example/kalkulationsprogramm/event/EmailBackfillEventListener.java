package org.example.kalkulationsprogramm.event;

import java.util.List;

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Listener für EmailAddressChangedEvent.
 * Führt asynchron Backfill-Operationen durch, um historische E-Mails
 * rückwirkend zuzuordnen.
 * 
 * Nutzt die neue unified Email-Entity.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailBackfillEventListener {

    private final KundeRepository kundeRepository;
    private final LieferantenRepository lieferantenRepository;
    private final AnfrageRepository anfrageRepository;
    private final ProjektRepository projektRepository;
    private final EmailRepository emailRepository;
    private final org.example.kalkulationsprogramm.service.EmailAttachmentProcessingService emailAttachmentProcessingService;
    private final org.example.kalkulationsprogramm.service.EmailAutoAssignmentService emailAutoAssignmentService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    // KEIN @Transactional - jeder saveAndFlush committet sofort in eigener
    // Mini-Transaktion
    public void handleEmailAddressChanged(EmailAddressChangedEvent event) {
        if (event.getNewAddresses() == null || event.getNewAddresses().isEmpty()) {
            log.debug("[EmailBackfill] Keine neuen Adressen für {} ID={}",
                    event.getEntityType(), event.getEntityId());
            return;
        }

        log.info("[EmailBackfill] Starte Backfill für {} ID={} mit {} neuen Adressen",
                event.getEntityType(), event.getEntityId(), event.getNewAddresses().size());

        try {
            int count = switch (event.getEntityType()) {
                case KUNDE -> handleKundeBackfill(event);
                case LIEFERANT -> handleLieferantBackfill(event);
                case ANFRAGE -> handleAnfrageBackfill(event);
                case PROJEKT -> handleProjektBackfill(event);
                case ANGEBOT -> 0;
            };

            log.info("[EmailBackfill] {} ID={}: {} E-Mails rückwirkend zugeordnet",
                    event.getEntityType(), event.getEntityId(), count);
        } catch (Exception e) {
            log.error("[EmailBackfill] Fehler bei {} ID={}: {}",
                    event.getEntityType(), event.getEntityId(), e.getMessage(), e);
        }
    }

    private int handleKundeBackfill(EmailAddressChangedEvent event) {
        Kunde kunde = kundeRepository.findById(event.getEntityId()).orElse(null);
        if (kunde == null) {
            return 0;
        }

        int count = 0;
        // For each new email address, find unassigned emails from/to that address
        for (String address : event.getNewAddresses()) {
            // Find unassigned emails involving this address
            List<Email> candidates = emailRepository.findUnassignedByAddress(address);
            for (Email email : candidates) {
                // Try to assign this email to any of the customer's projects or offers
                if (tryAssignToKundeContext(email, kunde)) {
                    count++;
                }
            }
        }
        return count;
    }

    private boolean tryAssignToKundeContext(Email email, Kunde kunde) {
        // 1. Get all projects and offers of this customer
        List<Projekt> projekte = projektRepository.findByKundenId_Id(kunde.getId());
        List<Anfrage> anfragen = anfrageRepository.findByKundeId(kunde.getId());

        int total = projekte.size() + anfragen.size();

        // 2. If exactly one active project/offer, assign directly (strong heuristic)
        if (total == 1) {
            if (!projekte.isEmpty()) {
                email.assignToProjekt(projekte.getFirst());
                log.info("[EmailBackfill] Auto-assigned email {} to single project {} of customer {}",
                        email.getId(), projekte.getFirst().getId(), kunde.getId());
            } else {
                email.assignToAnfrage(anfragen.getFirst());
                log.info("[EmailBackfill] Auto-assigned email {} to single offer {} of customer {}",
                        email.getId(), anfragen.getFirst().getId(), kunde.getId());
            }
            emailRepository.save(email);
            return true;
        }

        // 3. Keyword matching
        return emailAutoAssignmentService.tryAssignByKeywords(email, projekte, anfragen);
    }

    private int handleLieferantBackfill(EmailAddressChangedEvent event) {
        Lieferanten lieferant = lieferantenRepository.findById(event.getEntityId()).orElse(null);
        if (lieferant == null) {
            log.warn("[EmailBackfill] Lieferant ID={} nicht gefunden", event.getEntityId());
            return 0;
        }

        int count = 0;
        int attachmentErrors = 0;
        for (String address : event.getNewAddresses()) {
            String domain = extractDomain(address);
            log.info("[EmailBackfill] Lieferant ID={}: Prüfe Domain '{}' (aus Adresse '{}')",
                    event.getEntityId(), domain, address);

            if (domain != null) {
                // 1. Finde unzugeordnete Emails mit passender Domain
                List<Email> emails = emailRepository.findUnassignedByDomain(domain);
                log.info("[EmailBackfill] Gefunden: {} unzugeordnete Emails für Domain '{}'", emails.size(), domain);

                for (Email email : emails) {
                    email.assignToLieferant(lieferant);

                    // Inquiry-Flag zurücksetzen - ist jetzt Lieferanten-Email
                    email.setPotentialInquiry(false);
                    email.setInquiryScore(0);

                    // WICHTIG: saveAndFlush für sofortigen DB-Commit
                    emailRepository.saveAndFlush(email);
                    log.info("[EmailBackfill] Email {} dem Lieferant {} zugeordnet", email.getId(), lieferant.getId());

                    // Trigger attachment processing (Invoices, ABs, etc.) - SEQUENTIELL
                    try {
                        log.info("[EmailBackfill] Verarbeite Anhänge für Email {} sequentiell...", email.getId());
                        int processed = emailAttachmentProcessingService.processLieferantAttachments(email);
                        log.info("[EmailBackfill] Anhänge für Email {} erfolgreich verarbeitet: {} Dokumente erstellt",
                                email.getId(), processed);
                    } catch (Exception e) {
                        log.error("[EmailBackfill] Error processing attachments for email {}: {}", email.getId(),
                                e.getMessage());
                        attachmentErrors++;
                    }

                    count++;
                }

                // 2. Auch bereits als Anfrage markierte Emails von dieser Domain zurücksetzen
                List<Email> inquiryEmails = emailRepository.findInquiriesByDomain(domain);
                for (Email email : inquiryEmails) {
                    email.setPotentialInquiry(false);
                    email.setInquiryScore(0);
                    emailRepository.saveAndFlush(email);
                    log.info("[EmailBackfill] Inquiry-Flag entfernt für Email {} (Lieferant-Domain {})", email.getId(),
                            domain);
                }
            }
        }

        // 3. WICHTIG: Verarbeite auch bereits zugeordnete Emails, deren Anhänge noch
        // nicht gescannt wurden!
        // Dies ist notwendig wenn Emails zugeordnet wurden bevor die Email-Adresse
        // registriert war.
        // Verwendet Eager-Fetch für Attachments um LazyInitializationException zu
        // vermeiden
        List<Email> alreadyAssignedEmails = emailRepository.findByLieferantIdWithAttachments(event.getEntityId());
        int alreadyProcessed = 0;
        log.info("[EmailBackfill] Prüfe {} bereits zugeordnete Emails auf unverarbeitete Anhänge",
                alreadyAssignedEmails.size());

        for (Email email : alreadyAssignedEmails) {
            if (email.getAttachments() == null || email.getAttachments().isEmpty()) {
                continue;
            }

            // Prüfe ob es unverarbeitete Anhänge gibt (nur PDFs - ZUGFeRD-XML ist
            // eingebettet)
            boolean hasUnprocessedAttachments = email.getAttachments().stream()
                    .anyMatch(att -> !Boolean.TRUE.equals(att.getAiProcessed())
                            && att.getOriginalFilename() != null
                            && att.getOriginalFilename().toLowerCase().endsWith(".pdf"));

            if (hasUnprocessedAttachments) {
                try {
                    log.info("[EmailBackfill] Bereits zugeordnete Email {} hat unverarbeitete Anhänge - verarbeite...",
                            email.getId());
                    int processed = emailAttachmentProcessingService.processLieferantAttachments(email);
                    if (processed > 0) {
                        alreadyProcessed += processed;
                        log.info("[EmailBackfill] Email {}: {} Dokumente aus Anhängen erstellt",
                                email.getId(), processed);
                    }
                } catch (Exception e) {
                    log.error("[EmailBackfill] Fehler bei Anhang-Verarbeitung für bereits zugeordnete Email {}: {}",
                            email.getId(), e.getMessage());
                    attachmentErrors++;
                }
            }
        }

        if (alreadyProcessed > 0) {
            log.info("[EmailBackfill] {} Dokumente aus bereits zugeordneten Emails erstellt", alreadyProcessed);
        }

        if (attachmentErrors > 0) {
            log.warn("[EmailBackfill] Lieferant {}: {} E-Mails hatten Fehler bei Anhang-Verarbeitung",
                    event.getEntityId(), attachmentErrors);
        }

        return count + alreadyProcessed;
    }

    private int handleAnfrageBackfill(EmailAddressChangedEvent event) {
        Anfrage anfrage = anfrageRepository.findById(event.getEntityId()).orElse(null);
        if (anfrage == null) {
            return 0;
        }

        int count = 0;
        for (String address : event.getNewAddresses()) {
            List<Email> emails = emailRepository.findUnassignedByAddress(address);
            for (Email email : emails) {
                // Double check if it really matches (query is LIKE)
                // Actually the query filters well enough for now
                email.assignToAnfrage(anfrage);
                emailRepository.save(email);
                count++;
            }
        }
        return count;
    }

    private int handleProjektBackfill(EmailAddressChangedEvent event) {
        Projekt projekt = projektRepository.findById(event.getEntityId()).orElse(null);
        if (projekt == null) {
            return 0;
        }

        int count = 0;
        for (String address : event.getNewAddresses()) {
            List<Email> emails = emailRepository.findUnassignedByAddress(address);
            for (Email email : emails) {
                email.assignToProjekt(projekt);
                emailRepository.save(email);
                count++;
            }
        }
        return count;
    }

    private String extractDomain(String email) {
        if (email == null || !email.contains("@"))
            return null;
        return email.substring(email.lastIndexOf("@") + 1).toLowerCase();
    }
}
