package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Scoring-basierte Anfrage-Erkennung für E-Mails.
 * 
 * Bewertet E-Mails mit einem Score (0-100) basierend auf:
 * - Anfrage-Keywords im Betreff/Body (positiv)
 * - Ausschlusskriterien wie Lieferanten, Rechnungen, Newsletter (negativ)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InquiryDetectionService {

    private final LieferantenRepository lieferantenRepository;

    // ═══════════════════════════════════════════════════════════════
    // KONFIGURATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Ab diesem Score wird eine Email als potenzielle Anfrage markiert.
     */
    private static final int INQUIRY_THRESHOLD = 40;

    /**
     * Starke Anfrage-Indikatoren (25-40 Punkte).
     */
    private static final List<InquiryKeyword> STRONG_INQUIRY_KEYWORDS = List.of(
        new InquiryKeyword("anfrage für", 35),
        new InquiryKeyword("anfrage bezüglich", 35),
        new InquiryKeyword("bitte um angebot", 40),
        new InquiryKeyword("angebot erstellen", 35),
        new InquiryKeyword("preisanfrage", 40),
        new InquiryKeyword("kostenanfrage", 40),
        new InquiryKeyword("was würde", 30),
        new InquiryKeyword("kosten für", 25),
        new InquiryKeyword("hätten sie interesse", 30),
        new InquiryKeyword("könnten sie mir", 25),
        new InquiryKeyword("bitte um ein angebot", 40),
        new InquiryKeyword("unverbindliches angebot", 35),
        new InquiryKeyword("preisvorstellung", 30),
        new InquiryKeyword("kostenvoranschlag", 40),
        new InquiryKeyword("bauvorhaben", 25),
        new InquiryKeyword("projekt geplant", 30),
        new InquiryKeyword("auftrag erteilen", 30)
    );

    /**
     * Mittlere Anfrage-Indikatoren (10-20 Punkte).
     */
    private static final List<InquiryKeyword> MEDIUM_INQUIRY_KEYWORDS = List.of(
        new InquiryKeyword("anfrage", 15),
        new InquiryKeyword("angebot", 10),
        new InquiryKeyword("interesse", 15),
        new InquiryKeyword("benötigen", 10),
        new InquiryKeyword("suchen", 10),
        new InquiryKeyword("möchten beauftragen", 20),
        new InquiryKeyword("metallbau", 15),
        new InquiryKeyword("geländer", 20),
        new InquiryKeyword("treppe", 20),
        new InquiryKeyword("balkon", 20),
        new InquiryKeyword("carport", 20),
        new InquiryKeyword("zaun", 15),
        new InquiryKeyword("tor", 15),
        new InquiryKeyword("schweißarbeiten", 20)
    );

    /**
     * Negative Indikatoren (Abzug) - Zeichen dass es KEINE Anfrage ist.
     */
    private static final List<InquiryKeyword> NEGATIVE_KEYWORDS = List.of(
        new InquiryKeyword("newsletter", -80),
        new InquiryKeyword("abmelden", -50),
        new InquiryKeyword("unsubscribe", -60),
        new InquiryKeyword("rechnung", -40),
        new InquiryKeyword("lieferschein", -30),
        new InquiryKeyword("mahnung", -50),
        new InquiryKeyword("auftragsbestätigung", -40),
        new InquiryKeyword("tracking", -30),
        new InquiryKeyword("sendungsverfolgung", -30),
        new InquiryKeyword("bestellung eingegangen", -40),
        new InquiryKeyword("ihre bestellung", -30),
        new InquiryKeyword("automatisch generiert", -50),
        new InquiryKeyword("nicht antworten", -40),
        new InquiryKeyword("noreply", -40),
        new InquiryKeyword("no-reply", -40)
    );

    // ═══════════════════════════════════════════════════════════════
    // ANFRAGE-ANALYSE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Analysiert eine Email und berechnet den Anfrage-Score.
     * Setzt auch isPotentialInquiry basierend auf dem Threshold.
     */
    public void analyzeAndMarkInquiry(Email email) {
        int score = calculateInquiryScore(email);
        email.setInquiryScore(score);
        email.setPotentialInquiry(score >= INQUIRY_THRESHOLD);
        
        if (email.isPotentialInquiry()) {
            log.debug("[InquiryDetection] Email als Anfrage markiert: score={}, subject='{}'", 
                score, email.getSubject());
        }
    }

    /**
     * Berechnet den Anfrage-Score für eine Email (0-100).
     */
    public int calculateInquiryScore(Email email) {
        String subject = email.getSubject() != null ? email.getSubject().toLowerCase() : "";
        String body = email.getBody() != null ? email.getBody().toLowerCase() : "";
        String fromAddress = email.getFromAddress() != null ? email.getFromAddress().toLowerCase() : "";
        String combinedText = subject + " " + body;
        
        int score = 0;

        // ═══════════════════════════════════════════════════════════════
        // AUSSCHLUSS-CHECKS - sofort 0 zurückgeben
        // ═══════════════════════════════════════════════════════════════
        
        // Von Lieferanten → keine Kundenanfrage
        if (isFromLieferant(fromAddress)) {
            log.debug("[InquiryDetection] Ausschluss: Email von Lieferant: {}", fromAddress);
            return 0;
        }
        
        // Bereits als Spam markiert → keine Anfrage
        if (email.isSpam()) {
            return 0;
        }

        // ═══════════════════════════════════════════════════════════════
        // POSITIVES SCORING
        // ═══════════════════════════════════════════════════════════════

        // Starke Keywords (im Betreff 1.5x gewichtet)
        for (InquiryKeyword kw : STRONG_INQUIRY_KEYWORDS) {
            if (subject.contains(kw.keyword)) {
                score += (int)(kw.weight * 1.5); // Betreff-Bonus
            } else if (body.contains(kw.keyword)) {
                score += kw.weight;
            }
        }

        // Mittlere Keywords
        for (InquiryKeyword kw : MEDIUM_INQUIRY_KEYWORDS) {
            if (subject.contains(kw.keyword)) {
                score += (int)(kw.weight * 1.3);
            } else if (body.contains(kw.keyword)) {
                score += kw.weight;
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // NEGATIVES SCORING
        // ═══════════════════════════════════════════════════════════════

        for (InquiryKeyword kw : NEGATIVE_KEYWORDS) {
            if (combinedText.contains(kw.keyword)) {
                score += kw.weight; // weight ist bereits negativ
            }
        }

        // Score auf 0-100 begrenzen
        return Math.max(0, Math.min(100, score));
    }

    // ═══════════════════════════════════════════════════════════════
    // HILFSMETHODEN
    // ═══════════════════════════════════════════════════════════════

    /**
     * Prüft ob Absender-Adresse zu einem bekannten Lieferanten gehört.
     * Prüft sowohl exakte Email als auch Domain-Übereinstimmung.
     */
    private boolean isFromLieferant(String fromAddress) {
        if (fromAddress == null || fromAddress.isBlank()) {
            return false;
        }
        
        // Extrahiere reine Email-Adresse falls Format "Name <email@domain.com>"
        String email = fromAddress;
        if (fromAddress.contains("<") && fromAddress.contains(">")) {
            int start = fromAddress.indexOf('<') + 1;
            int end = fromAddress.indexOf('>');
            if (start < end) {
                email = fromAddress.substring(start, end);
            }
        }
        email = email.toLowerCase().trim();
        
        // 1. Exakte Email-Übereinstimmung
        if (lieferantenRepository.findByEmail(email).isPresent()) {
            return true;
        }
        
        // 2. Domain-basierte Prüfung
        if (email.contains("@")) {
            String domain = email.substring(email.lastIndexOf("@") + 1);
            return lieferantenRepository.existsByEmailDomain(domain);
        }
        
        return false;
    }

    /**
     * Internes Record für gewichtete Keywords.
     */
    private record InquiryKeyword(String keyword, int weight) {}

    // ═══════════════════════════════════════════════════════════════
    // RESULT DTO für Batch-Scan
    // ═══════════════════════════════════════════════════════════════

    /**
     * Ergebnis eines Batch-Anfrage-Scans.
     */
    public static class ScanResult {
        public int totalScanned;
        public int inquiriesFound;
        public int notInquiries;

        public ScanResult(int totalScanned, int inquiriesFound, int notInquiries) {
            this.totalScanned = totalScanned;
            this.inquiriesFound = inquiriesFound;
            this.notInquiries = notInquiries;
        }
    }
}
