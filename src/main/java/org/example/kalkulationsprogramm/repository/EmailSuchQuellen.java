package org.example.kalkulationsprogramm.repository;

/** Die durchsuchbaren Felder einer Mail ohne Anhänge – Projektion für die Suchtext-Nachpflege. */
public record EmailSuchQuellen(Long id, String fromAddress, String replyToAddress, String recipient, String cc,
        String subject, String body, String htmlBody) {
}
