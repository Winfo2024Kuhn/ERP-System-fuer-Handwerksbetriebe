package org.example.kalkulationsprogramm.service;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Hybrid Spam-Filter: Regelbasiert + Naive Bayes ML.
 *
 * Berechnet einen Spam-Score (0-100) basierend auf:
 * - Keyword-Blacklist im Betreff/Body (Regeln)
 * - Absender-Domain-Blacklist (Regeln)
 * - Verdächtige Muster (Regeln)
 * - Naive Bayes Klassifikator (ML, lernt aus User-Feedback)
 *
 * Ensemble: 40% Regel-Score + 60% Bayes-Score (wenn Modell bereit).
 * Cold-Start: 100% Regel-Score bis genug Trainingsbeispiele vorhanden.
 *
 * Whitelist (kein Spam):
 * - Rechnungs-Emails mit PDF/XML Attachment
 * - Emails von bekannten Lieferanten
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpamFilterService {

    private final LieferantenRepository lieferantenRepository;
    private final org.example.kalkulationsprogramm.repository.EmailBlacklistRepository emailBlacklistRepository;
    private final SpamBayesService spamBayesService;

    // ... (rest of configuration)

    // ═══════════════════════════════════════════════════════════════
    // KONFIGURATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Ab diesem Score wird eine Email als Spam markiert.
     */
    private static final int SPAM_THRESHOLD = 50;

    /**
     * Ab diesem Score wird eine Email automatisch in Spam verschoben (ohne User-Eingriff).
     */
    private static final int AUTO_SPAM_THRESHOLD = 85;

    /**
     * Keywords die auf Newsletter hinweisen - KEIN Spam, nur Kategorie.
     */
    private static final List<String> NEWSLETTER_KEYWORDS = List.of(
            "newsletter",
            "unsubscribe",
            "abmelden",
            "abbestellen",
            "click here to unsubscribe",
            "nicht mehr erhalten", // Alternative zu abbestellen
            "online ansehen", // Oft oben in Newslettern
            "online version",
            "xing", // Social Media Notifications
            "dazn",
            "linkedin",
            "sales",
            "neuigkeiten", // "XING Neuigkeiten"
            "news", // "News"
            "benachrichtigung", // "Neue Benachrichtigung"
            "verpassen sie nicht" // Marketing-Sprech
    );

    /**
     * Keywords die auf Spam hinweisen (lowercase).
     * Gewichtung: 15-30 Punkte je nach Keyword.
     */
    private static final List<SpamKeyword> SPAM_KEYWORDS = List.of(
            // Gewinnspiele / Betrug
            new SpamKeyword("gewinnspiel", 30),
            new SpamKeyword("gewonnen", 25),
            new SpamKeyword("winner", 25),
            new SpamKeyword("congratulations you won", 40),
            new SpamKeyword("lottery", 35),

            // Pharma-Spam
            new SpamKeyword("viagra", 50),
            new SpamKeyword("cialis", 50),
            new SpamKeyword("pharmacy", 30),
            new SpamKeyword("medication", 20),

            // Casino / Glücksspiel
            new SpamKeyword("casino", 35),
            new SpamKeyword("jackpot", 30),
            new SpamKeyword("bet now", 35),

            // Finanz-Spam
            new SpamKeyword("bitcoin opportunity", 35),
            new SpamKeyword("investment opportunity", 25),
            new SpamKeyword("make money fast", 40),
            new SpamKeyword("earn extra cash", 30),
            new SpamKeyword("millionaire", 25),

            // Phishing
            new SpamKeyword("verify your account", 30),
            new SpamKeyword("account suspended", 30),
            new SpamKeyword("urgent action required", 25),
            new SpamKeyword("password expired", 30),
            new SpamKeyword("confirm your identity", 25),

            // Allgemein verdächtig
            new SpamKeyword("free gift", 25),
            new SpamKeyword("limited time offer", 20),
            new SpamKeyword("act now", 15),
            new SpamKeyword("don't miss out", 15),
            new SpamKeyword("exclusive deal", 15),
            new SpamKeyword("cloud-speicher", 40),
            new SpamKeyword("cloudspeicher", 40),
            new SpamKeyword("aktion erforderlich", 40));

    /**
     * Verdächtige Absender-Domains.
     */
    private static final Set<String> BLOCKED_DOMAINS = Set.of(
            "bit.ly",
            "tinyurl.com",
            "temp-mail.org",
            "guerrillamail.com",
            "mailinator.com",
            "10minutemail.com");

    /**
     * Patterns für verdächtige Absender-Adressen.
     */
    private static final List<Pattern> SUSPICIOUS_SENDER_PATTERNS = List.of(
            Pattern.compile(".*noreply@.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*no-reply@.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*donotreply@.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*mailer-daemon@.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*[0-9]{5,}@.*") // Viele Zahlen in der Adresse
    );

    /**
     * Gefährliche Dateiendungen (Ausführbare Dateien).
     */
    private static final List<String> DANGEROUS_EXTENSIONS = List.of(
            "exe", "bat", "cmd", "com", "scr", "js", "vbs", "jar", "msi", "sh", "ps1");

    // ═══════════════════════════════════════════════════════════════
    // SPAM-ANALYSE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Analysiert eine Email und berechnet den Spam-Score.
     * Setzt auch isSpam und isNewsletter.
     */
    public void analyzeAndMarkSpam(Email email) {
        // Ausgehende Emails sind niemals Spam
        if (email.getDirection() == EmailDirection.OUT) {
            email.setSpam(false);
            email.setNewsletter(false);
            email.setSpamScore(0);
            return;
        }

        // WICHTIG: Zugeordnete Emails dürfen niemals als Spam oder Newsletter markiert
        // werden
        if (email.getLieferant() != null || email.getProjekt() != null || email.getAnfrage() != null) {
            email.setSpam(false);
            email.setNewsletter(false);
            email.setSpamScore(0);
            log.debug("[SpamFilter] Email übersprungen (zugeordnet): subject='{}'", email.getSubject());
            return;
        }

        // WICHTIG: Emails von bekannten Lieferanten-Domains dürfen NIEMALS
        // als Spam oder Newsletter markiert werden - unabhängig vom Inhalt.
        // Lieferanten senden oft über Newsletter-Tools (Mailchimp etc.),
        // verwenden noreply@-Adressen oder haben "news"/"update" Keywords.
        if (email.getFromAddress() != null && isFromLieferant(email.getFromAddress())) {
            email.setSpam(false);
            email.setNewsletter(false);
            email.setSpamScore(0);
            log.debug("[SpamFilter] Email von bekanntem Lieferant übersprungen: from='{}', subject='{}'",
                    email.getFromAddress(), email.getSubject());
            return;
        }

        // 1. Newsletter Check (falls noch nicht via Header erkannt)
        if (!email.isNewsletter()) {
            if (checkForNewsletter(email)) {
                email.setNewsletter(true);
            }
        }

        // 2. Spam Check (Regel-basiert)
        int ruleScore = calculateSpamScore(email);

        // 3. Bayes ML-Score (wenn Modell bereit)
        double bayesProb = -1.0;
        if (spamBayesService.isModelReady()) {
            java.util.Set<String> tokens = spamBayesService.tokenize(email);
            bayesProb = spamBayesService.predict(tokens);
            email.setBayesScore(bayesProb >= 0 ? bayesProb : null);
        }

        // 4. Ensemble: Regel + Bayes kombinieren
        int finalScore;
        if (bayesProb < 0) {
            // Cold-Start: nur Regel-Score
            finalScore = ruleScore;
        } else {
            // Ensemble: 40% Regeln, 60% Bayes
            int bayesScore = (int) (bayesProb * 100);
            finalScore = (int) (ruleScore * 0.4 + bayesScore * 0.6);
        }

        email.setSpamScore(finalScore);
        email.setSpam(finalScore >= AUTO_SPAM_THRESHOLD);

        if (email.isSpam()) {
            log.info("[SpamFilter] Email automatisch als Spam verschoben (Score ≥ {}%): finalScore={} (rule={}, bayes={}), subject='{}'",
                    AUTO_SPAM_THRESHOLD, finalScore, ruleScore, bayesProb >= 0 ? String.format("%.2f", bayesProb) : "n/a",
                    email.getSubject());
        }
    }

    private static final List<String> NEWSLETTER_SENDER_KEYWORDS = List.of(
            "newsletter",
            "news",
            "mailrobot",
            "marketing",
            "update",
            "noreply",
            "no-reply");

    private boolean checkForNewsletter(Email email) {
        // 0. Wenn Sender-Domain zu einem bekannten Lieferanten gehört → KEIN Newsletter
        //    Lieferanten senden oft von noreply@, update@ etc. Adressen
        if (email.getFromAddress() != null && isFromLieferant(email.getFromAddress())) {
            return false;
        }

        // 1. Check Sender (sehr starkes Signal)
        if (email.getFromAddress() != null) {
            String from = email.getFromAddress().toLowerCase();
            for (String kw : NEWSLETTER_SENDER_KEYWORDS) {
                if (from.contains(kw))
                    return true;
            }
        }

        // 2. Check Subject
        if (email.getSubject() != null) {
            String subject = email.getSubject().toLowerCase();
            for (String kw : NEWSLETTER_KEYWORDS) {
                if (subject.contains(kw))
                    return true;
            }
        }

        // 3. Check Body
        String content = getCombinedBody(email);
        for (String keyword : NEWSLETTER_KEYWORDS) {
            if (content.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Berechnet den Spam-Score für eine Email (0-100).
     */
    public int calculateSpamScore(Email email) {
        String subject = email.getSubject() != null ? email.getSubject().toLowerCase() : "";
        String body = getCombinedBody(email);
        String fromAddress = email.getFromAddress() != null ? email.getFromAddress().toLowerCase() : "";
        String senderDomain = email.getSenderDomain() != null ? email.getSenderDomain().toLowerCase() : "";
        String combinedText = subject + " " + body;

        // 0. Blacklist Check (Sofort-Spam)
        if (emailBlacklistRepository.existsByEmailAddress(fromAddress)) {
            log.debug("[SpamFilter] Absender ist auf Blacklist: {}", fromAddress);
            return 100;
        }

        // ═══════════════════════════════════════════════════════════════
        // WHITELIST - sofort 0 zurückgeben wenn zutreffend
        // ═══════════════════════════════════════════════════════════════

        // 1. Emails von bekannten Lieferanten → NIEMALS Spam
        if (isFromLieferant(fromAddress)) {
            log.debug("[SpamFilter] Whitelist: Email von Lieferant erkannt: {}", fromAddress);
            return 0;
        }

        // 2. Rechnungs-Emails mit PDF/XML Attachment → kein Spam
        if (combinedText.contains("rechnung") && hasRelevantAttachment(email)) {
            log.debug("[SpamFilter] Whitelist: Rechnungs-Email mit PDF/XML erkannt: '{}'", email.getSubject());
            return 0;
        }

        // 3. Rechnungs-Emails von bekannten Lieferanten → kein Spam (auch ohne Attachment)
        if (combinedText.contains("rechnung") && isFromLieferant(fromAddress)) {
            log.debug("[SpamFilter] Whitelist: Rechnung von Lieferant erkannt: '{}'", email.getSubject());
            return 0;
        }

        // ═══════════════════════════════════════════════════════════════
        // SPAM-SCORING
        // ═══════════════════════════════════════════════════════════════

        int score = 0;

        // 1. Keyword-Check
        for (SpamKeyword keyword : SPAM_KEYWORDS) {
            if (combinedText.contains(keyword.keyword)) {
                score += keyword.weight;
            }
        }

        // 2. Domain-Blacklist
        if (BLOCKED_DOMAINS.contains(senderDomain)) {
            score += 40;
        }

        // 3. Verdächtige Absender-Muster
        for (Pattern pattern : SUSPICIOUS_SENDER_PATTERNS) {
            if (pattern.matcher(fromAddress).matches()) {
                score += 10;
                break; // Nur einmal zählen
            }
        }

        // 4. Leerer Betreff
        if (subject.isBlank()) {
            score += 15;
        }

        // 5. Zu viele GROSSBUCHSTABEN im Betreff
        if (email.getSubject() != null) {
            long upperCount = email.getSubject().chars().filter(Character::isUpperCase).count();
            long totalLetters = email.getSubject().chars().filter(Character::isLetter).count();
            if (totalLetters > 5 && (double) upperCount / totalLetters > 0.7) {
                score += 20;
            }
        }

        // 6. Zu viele Links im Body
        if (body.length() > 0) {
            int linkCount = countOccurrences(body, "http://") + countOccurrences(body, "https://");
            if (linkCount > 10) {
                score += 15;
            }
        }

        // 7. Security Check: Gefährliche Anhänge
        if (email.getAttachments() != null) {
            for (org.example.kalkulationsprogramm.domain.EmailAttachment attachment : email.getAttachments()) {
                String filename = attachment.getOriginalFilename();
                if (filename != null) {
                    String ext = getExtension(filename);
                    if (DANGEROUS_EXTENSIONS.contains(ext)) {
                        log.warn("[SpamFilter] SECURITY: Gefährlicher Anhang gefunden: {}", filename);
                        score += 100; // Sofort Spam
                        break;
                    }
                }
            }
        }

        // Score auf 0-100 begrenzen
        return Math.min(100, Math.max(0, score));
    }

    private String getCombinedBody(Email email) {
        StringBuilder sb = new StringBuilder();
        if (email.getBody() != null) {
            sb.append(email.getBody().toLowerCase()).append(" ");
        }
        if (email.getHtmlBody() != null) {
            sb.append(email.getHtmlBody().toLowerCase());
        }
        return sb.toString();
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot > 0) {
            return filename.substring(dot + 1).toLowerCase();
        }
        return "";
    }

    /**
     * Prüft ob eine Email Spam ist (ohne sie zu modifizieren).
     */
    public boolean isSpam(Email email) {
        return calculateSpamScore(email) >= SPAM_THRESHOLD;
    }

    // ═══════════════════════════════════════════════════════════════
    // HILFSMETHODEN
    // ═══════════════════════════════════════════════════════════════

    private int countOccurrences(String text, String pattern) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(pattern, idx)) != -1) {
            count++;
            idx += pattern.length();
        }
        return count;
    }

    /**
     * Prüft ob Email ein PDF oder XML Attachment hat (typisch für Rechnungen).
     */
    private boolean hasRelevantAttachment(Email email) {
        if (email.getAttachments() == null || email.getAttachments().isEmpty()) {
            return false;
        }

        return email.getAttachments().stream()
                .anyMatch(att -> att.isPdf() || att.isXml());
    }

    /**
     * Prüft ob Absender-Adresse oder -Domain zu einem bekannten Lieferanten gehört.
     * Nutzt Domain-basierte Prüfung damit Änderungen der Email-Adresse
     * beim selben Lieferanten die Erkennung nicht beeinträchtigen.
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

        String normalized = email.toLowerCase().trim();

        // 1. Exakter Email-Match (stärkstes Signal)
        if (lieferantenRepository.findByEmail(normalized).isPresent()) {
            return true;
        }

        // 2. Domain-basierter Match (robust gegen Email-Adress-Änderungen)
        if (normalized.contains("@")) {
            String domain = normalized.substring(normalized.lastIndexOf('@') + 1);
            if (!domain.isBlank()) {
                return lieferantenRepository.existsByEmailDomain(domain);
            }
        }

        return false;
    }

    /**
     * Internes Record für gewichtete Keywords.
     */
    private record SpamKeyword(String keyword, int weight) {
    }

    // ═══════════════════════════════════════════════════════════════
    // RESULT DTO
    // ═══════════════════════════════════════════════════════════════

    /**
     * Ergebnis eines Batch-Spam-Scans.
     */
    public static class ScanResult {
        public int totalScanned;
        public int spamFound;
        public int notSpam;

        public ScanResult(int totalScanned, int spamFound, int notSpam) {
            this.totalScanned = totalScanned;
            this.spamFound = spamFound;
            this.notSpam = notSpam;
        }
    }
}
