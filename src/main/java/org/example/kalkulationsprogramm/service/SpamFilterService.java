package org.example.kalkulationsprogramm.service;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
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
    private final KundeRepository kundeRepository;
    private final AnfrageRepository anfrageRepository;
    private final ProjektRepository projektRepository;
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
            // ─────────────────────────────────────────────────────────
            // GEWINNSPIELE / BETRUG (DE + EN)
            // ─────────────────────────────────────────────────────────
            new SpamKeyword("gewinnspiel", 30),
            new SpamKeyword("sie haben gewonnen", 45),
            new SpamKeyword("gewonnen", 25),
            new SpamKeyword("herzlichen glückwunsch", 20),
            new SpamKeyword("winner", 25),
            new SpamKeyword("congratulations you won", 40),
            new SpamKeyword("lottery", 35),
            new SpamKeyword("lotterie", 35),
            new SpamKeyword("geldpreis", 40),
            new SpamKeyword("preisgeld", 35),
            new SpamKeyword("sofortgewinn", 40),
            new SpamKeyword("gratis iphone", 50),
            new SpamKeyword("kostenlos iphone", 45),
            new SpamKeyword("gutschein geschenkt", 30),
            new SpamKeyword("amazon gutschein", 25),

            // ─────────────────────────────────────────────────────────
            // PHARMA-SPAM (DE + EN)
            // ─────────────────────────────────────────────────────────
            new SpamKeyword("viagra", 50),
            new SpamKeyword("cialis", 50),
            new SpamKeyword("pharmacy", 30),
            new SpamKeyword("apotheke online", 30),
            new SpamKeyword("online apotheke", 30),
            new SpamKeyword("rezeptfrei", 35),
            new SpamKeyword("ohne rezept", 35),
            new SpamKeyword("medication", 20),
            new SpamKeyword("pills", 25),
            new SpamKeyword("tabletten bestellen", 30),
            new SpamKeyword("potenzmittel", 50),
            new SpamKeyword("abnehmpillen", 40),
            new SpamKeyword("diätpillen", 40),
            new SpamKeyword("weight loss pill", 35),

            // ─────────────────────────────────────────────────────────
            // CASINO / GLÜCKSSPIEL (DE + EN)
            // ─────────────────────────────────────────────────────────
            new SpamKeyword("casino", 35),
            new SpamKeyword("online casino", 45),
            new SpamKeyword("jackpot", 30),
            new SpamKeyword("bet now", 35),
            new SpamKeyword("jetzt wetten", 35),
            new SpamKeyword("freispiele", 35),
            new SpamKeyword("free spins", 35),
            new SpamKeyword("spielautomaten", 40),
            new SpamKeyword("slot machine", 35),
            new SpamKeyword("sportwetten bonus", 40),
            new SpamKeyword("einzahlungsbonus", 35),
            new SpamKeyword("poker bonus", 30),

            // ─────────────────────────────────────────────────────────
            // FINANZ-SPAM / KRYPTO-BETRUG (DE + EN)
            // ─────────────────────────────────────────────────────────
            new SpamKeyword("bitcoin opportunity", 35),
            new SpamKeyword("investment opportunity", 25),
            new SpamKeyword("make money fast", 40),
            new SpamKeyword("schnell geld verdienen", 40),
            new SpamKeyword("earn extra cash", 30),
            new SpamKeyword("millionaire", 25),
            new SpamKeyword("millionär werden", 35),
            new SpamKeyword("krypto geheimtipp", 45),
            new SpamKeyword("crypto profit", 40),
            new SpamKeyword("trading bot", 35),
            new SpamKeyword("trading signal", 30),
            new SpamKeyword("passives einkommen", 25),
            new SpamKeyword("finanzielle freiheit", 20),
            new SpamKeyword("rendite garantiert", 45),
            new SpamKeyword("guaranteed return", 40),
            new SpamKeyword("forex signal", 35),
            new SpamKeyword("binary option", 40),
            new SpamKeyword("pyramid scheme", 50),
            new SpamKeyword("schneeballsystem", 50),
            new SpamKeyword("network marketing einkommen", 30),
            new SpamKeyword("double your money", 45),
            new SpamKeyword("geld verdoppeln", 45),
            new SpamKeyword("sofort auszahlung", 30),
            new SpamKeyword("nft opportunity", 30),

            // ─────────────────────────────────────────────────────────
            // PHISHING (DE + EN)
            // ─────────────────────────────────────────────────────────
            new SpamKeyword("verify your account", 30),
            new SpamKeyword("konto verifizieren", 30),
            new SpamKeyword("konto bestätigen", 25),
            new SpamKeyword("account suspended", 30),
            new SpamKeyword("konto gesperrt", 30),
            new SpamKeyword("konto eingeschränkt", 25),
            new SpamKeyword("zugang gesperrt", 30),
            new SpamKeyword("urgent action required", 25),
            new SpamKeyword("sofortige maßnahme erforderlich", 30),
            new SpamKeyword("dringende sicherheitswarnung", 30),
            new SpamKeyword("password expired", 30),
            new SpamKeyword("passwort abgelaufen", 30),
            new SpamKeyword("passwort zurücksetzen", 20),
            new SpamKeyword("confirm your identity", 25),
            new SpamKeyword("identität bestätigen", 25),
            new SpamKeyword("ungewöhnliche aktivität", 25),
            new SpamKeyword("unusual activity", 25),
            new SpamKeyword("suspicious login", 30),
            new SpamKeyword("verdächtiger zugriff", 30),
            new SpamKeyword("sicherheitsüberprüfung", 20),
            new SpamKeyword("ihr paket konnte nicht zugestellt", 25),
            new SpamKeyword("zollgebühren bezahlen", 35),
            new SpamKeyword("dhl express sendung", 20),
            new SpamKeyword("zahlung fehlgeschlagen klicken", 35),
            new SpamKeyword("letzte warnung", 25),
            new SpamKeyword("final warning", 25),

            // ─────────────────────────────────────────────────────────
            // FAKE-RECHNUNGEN / CEO-FRAUD
            // ─────────────────────────────────────────────────────────
            new SpamKeyword("überfällige zahlung", 20),
            new SpamKeyword("inkasso androhung", 30),
            new SpamKeyword("gerichtliche schritte", 20),
            new SpamKeyword("mahnbescheid erhalten", 25),
            new SpamKeyword("anwaltliche mahnung", 20),
            new SpamKeyword("dringende überweisung", 35),
            new SpamKeyword("bitte sofort überweisen", 30),
            new SpamKeyword("wire transfer urgent", 35),
            new SpamKeyword("vertrauliche überweisung", 40),

            // ─────────────────────────────────────────────────────────
            // ALLGEMEIN VERDÄCHTIG / MARKETING-SPAM
            // ─────────────────────────────────────────────────────────
            new SpamKeyword("free gift", 25),
            new SpamKeyword("limited time offer", 20),
            new SpamKeyword("act now", 15),
            new SpamKeyword("don't miss out", 15),
            new SpamKeyword("exclusive deal", 15),
            new SpamKeyword("cloud-speicher", 40),
            new SpamKeyword("cloudspeicher", 40),
            new SpamKeyword("aktion erforderlich", 40),
            new SpamKeyword("klicken sie hier sofort", 25),
            new SpamKeyword("nur noch heute", 15),
            new SpamKeyword("angebot läuft ab", 15),
            new SpamKeyword("100% kostenlos", 25),
            new SpamKeyword("100% gratis", 25),
            new SpamKeyword("kein risiko", 15),
            new SpamKeyword("unglaubliches angebot", 20),
            new SpamKeyword("sonderaktion exklusiv", 20),

            // ─────────────────────────────────────────────────────────
            // DEUTSCHE VULGÄR- / BELEIDIGUNGSSPAM
            // ─────────────────────────────────────────────────────────
            new SpamKeyword("prostituierte", 50),
            new SpamKeyword("nutten", 50),
            new SpamKeyword("fotze", 50),
            new SpamKeyword("fotzen", 50),
            new SpamKeyword("arschloch", 40),
            new SpamKeyword("wichser", 45),
            new SpamKeyword("hurensohn", 50),
            new SpamKeyword("ficken", 50),
            new SpamKeyword("fick dich", 50),
            new SpamKeyword("hure", 50),
            new SpamKeyword("schlampe", 45),
            new SpamKeyword("missgeburt", 45),
            new SpamKeyword("behindert du", 40),
            new SpamKeyword("schwuchtel", 45),
            new SpamKeyword("dreckige sau", 45),

            // ─────────────────────────────────────────────────────────
            // ADULT / PORNO-SPAM (DE + EN)
            // ─────────────────────────────────────────────────────────
            new SpamKeyword("porno", 50),
            new SpamKeyword("porn", 50),
            new SpamKeyword("xxx", 50),
            new SpamKeyword("sex webcam", 40),
            new SpamKeyword("live cam girl", 45),
            new SpamKeyword("nacktbilder", 40),
            new SpamKeyword("nacktfotos", 40),
            new SpamKeyword("naked", 40),
            new SpamKeyword("sex tape", 40),
            new SpamKeyword("hookup", 35),
            new SpamKeyword("dating hot", 35),
            new SpamKeyword("heiße singles", 40),
            new SpamKeyword("hot singles", 40),
            new SpamKeyword("erotik kontakte", 40),
            new SpamKeyword("sex treffen", 45),
            new SpamKeyword("sextreffen", 45),
            new SpamKeyword("sexkontakte", 45),
            new SpamKeyword("escort service", 40),
            new SpamKeyword("erotische massage", 35),
            new SpamKeyword("onlyfans leak", 45),
            new SpamKeyword("adult content", 35),
            new SpamKeyword("adult dating", 40),
            new SpamKeyword("camgirl", 40),

            // ─────────────────────────────────────────────────────────
            // DROGEN / ILLEGALES (DE + EN)
            // ─────────────────────────────────────────────────────────
            new SpamKeyword("koks", 40),
            new SpamKeyword("kokain", 40),
            new SpamKeyword("heroin", 40),
            new SpamKeyword("crystal meth", 50),
            new SpamKeyword("ecstasy bestellen", 50),
            new SpamKeyword("drogen kaufen", 50),
            new SpamKeyword("drogen bestellen", 50),
            new SpamKeyword("darknet", 35),
            new SpamKeyword("dark web", 35),
            new SpamKeyword("waffen kaufen", 50),
            new SpamKeyword("buy guns", 50),
            new SpamKeyword("ausweis fälschen", 50),
            new SpamKeyword("fake passport", 50),
            new SpamKeyword("gefälschte dokumente", 50),
            new SpamKeyword("counterfeit", 35),

            // ─────────────────────────────────────────────────────────
            // DROHUNGEN / TERROR
            // ─────────────────────────────────────────────────────────
            new SpamKeyword("bombendrohung", 100),
            new SpamKeyword("bomben", 35),
            new SpamKeyword("terroranschlag", 50),
            new SpamKeyword("ich bringe dich um", 100),
            new SpamKeyword("ich werde dich töten", 100),
            new SpamKeyword("amoklauf", 50),
            new SpamKeyword("sprengstoff", 50),

            // ─────────────────────────────────────────────────────────
            // ERPRESSUNG / SEXTORTION (DE + EN)
            // ─────────────────────────────────────────────────────────
            new SpamKeyword("i hacked your", 50),
            new SpamKeyword("ich habe dich gehackt", 50),
            new SpamKeyword("ich habe ihren computer", 50),
            new SpamKeyword("i have your password", 50),
            new SpamKeyword("ich kenne ihr passwort", 50),
            new SpamKeyword("webcam aufnahme", 50),
            new SpamKeyword("webcam video von ihnen", 50),
            new SpamKeyword("kompromittierende aufnahmen", 50),
            new SpamKeyword("kompromittierendes material", 50),
            new SpamKeyword("bitcoin wallet", 35),
            new SpamKeyword("zahlen sie bitcoin", 50),
            new SpamKeyword("pay bitcoin", 45),
            new SpamKeyword("ransom", 45),
            new SpamKeyword("lösegeld", 45),
            new SpamKeyword("loesegeld", 45),
            new SpamKeyword("verschlüsselt ihre dateien", 50),
            new SpamKeyword("encrypted your files", 50),
            new SpamKeyword("48 stunden zeit", 35),
            new SpamKeyword("24 hours to pay", 35),
            new SpamKeyword("an ihre kontakte senden", 40),
            new SpamKeyword("send to your contacts", 40),

            // ─────────────────────────────────────────────────────────
            // HANDWERKSBETRIEB-SPEZIFISCH: Fake-Einträge / Branchenbuch
            // ─────────────────────────────────────────────────────────
            new SpamKeyword("branchenbuch eintrag", 30),
            new SpamKeyword("firmenverzeichnis aktualisieren", 30),
            new SpamKeyword("gewerbeauskunft", 30),
            new SpamKeyword("handelsregister aktualisierung", 30),
            new SpamKeyword("firmenregistrierung pflicht", 35),
            new SpamKeyword("eintrag kostenpflichtig", 35),
            new SpamKeyword("datenschutzgrundverordnung verstoß", 25),
            new SpamKeyword("dsgvo abmahnung", 30),
            new SpamKeyword("impressum abmahnung", 25),
            new SpamKeyword("domainregistrierung ablauf", 30),
            new SpamKeyword("ihre domain läuft ab", 30),
            new SpamKeyword("domain expiration", 30),
            new SpamKeyword("seo optimierung garantie", 30),
            new SpamKeyword("erste seite bei google garantiert", 40),
            new SpamKeyword("google platzierung kaufen", 35));

    /**
     * Verdächtige Absender-Domains.
     */
    private static final Set<String> BLOCKED_DOMAINS = Set.of(
            // URL-Shortener (verschleiern Ziel)
            "bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd",
            "buff.ly", "adf.ly", "shorte.st", "cutt.ly",
            // Wegwerf-Mail-Dienste
            "temp-mail.org", "guerrillamail.com", "mailinator.com",
            "10minutemail.com", "throwaway.email", "tempail.com",
            "yopmail.com", "trashmail.com", "sharklasers.com",
            "guerrillamailblock.com", "grr.la", "dispostable.com",
            "fakeinbox.com", "tempinbox.com", "maildrop.cc",
            "getairmail.com", "mohmal.com", "burnermail.io",
            // Bekannte Spam-Domains
            "spam4.me", "spamgourmet.com", "bugmenot.com");

    /**
     * Patterns für verdächtige Absender-Adressen.
     */
    private static final List<Pattern> SUSPICIOUS_SENDER_PATTERNS = List.of(
            Pattern.compile(".*noreply@.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*no-reply@.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*donotreply@.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*mailer-daemon@.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*[0-9]{5,}@.*"),      // Viele Zahlen vor dem @
            Pattern.compile(".*@.*[0-9]{4,}\\..*"),  // Viele Zahlen in der Domain
            Pattern.compile(".*[a-z]{15,}@.*")       // Extrem langer lokaler Teil (generiert)
    );

    /**
     * Gefährliche Dateiendungen (Ausführbare Dateien + Makro-Dokumente).
     */
    private static final List<String> DANGEROUS_EXTENSIONS = List.of(
            // Ausführbare Dateien
            "exe", "bat", "cmd", "com", "scr", "pif", "cpl",
            // Skripte
            "js", "vbs", "wsf", "wsh", "ps1", "sh",
            // Installer / Archive mit Code
            "jar", "msi", "msp", "mst",
            // Office-Makros (häufig für Malware)
            "docm", "xlsm", "pptm", "dotm", "xltm",
            // Andere gefährliche Formate
            "hta", "inf", "reg", "rgs", "sct", "lnk", "iso", "img");

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

        // WICHTIG: Emails von bekannten Kunden (Kunde, Projekt, Anfrage)
        // dürfen NIEMALS als Spam markiert werden.
        if (email.getFromAddress() != null && isFromKnownKunde(email.getFromAddress())) {
            email.setSpam(false);
            email.setNewsletter(false);
            email.setSpamScore(0);
            log.debug("[SpamFilter] Email von bekanntem Kunden übersprungen: from='{}', subject='{}'",
                    email.getFromAddress(), email.getSubject());
            return;
        }

        // 1. Spam Check (Regel-basiert)
        int ruleScore = calculateSpamScore(email);

        // 2. Bayes ML-Score (wenn Modell bereit)
        double bayesProb = -1.0;
        if (spamBayesService.isModelReady()) {
            java.util.Set<String> tokens = spamBayesService.tokenize(email);
            bayesProb = spamBayesService.predict(tokens);
            email.setBayesScore(bayesProb >= 0 ? bayesProb : null);
        }

        // 3. Ensemble: Regel + Bayes kombinieren
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
            // Echter Spam überschreibt Newsletter-Flag – Spam hat Vorrang
            email.setNewsletter(false);
            log.info("[SpamFilter] Email automatisch als Spam verschoben (Score ≥ {}%): finalScore={} (rule={}, bayes={}), subject='{}'",
                    AUTO_SPAM_THRESHOLD, finalScore, ruleScore, bayesProb >= 0 ? String.format("%.2f", bayesProb) : "n/a",
                    email.getSubject());
            return;
        }

        // 4. Newsletter Check – nur wenn Email KEIN Spam ist
        // (verhindert, dass echter Spam mit "abmelden"-Links im Newsletter-Ordner landet)
        if (!email.isNewsletter()) {
            if (checkForNewsletter(email)) {
                email.setNewsletter(true);
            }
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
     * Prüft ob die Absender-Adresse zu einem bekannten Kunden gehört.
     * Sucht in den Kunden-, Projekt- und Anfrage-Email-Tabellen.
     */
    private boolean isFromKnownKunde(String fromAddress) {
        if (fromAddress == null || fromAddress.isBlank()) {
            return false;
        }

        // Extrahiere reine Email-Adresse falls Format "Name <email@domain.com>"
        String emailAddr = fromAddress;
        if (fromAddress.contains("<") && fromAddress.contains(">")) {
            int start = fromAddress.indexOf('<') + 1;
            int end = fromAddress.indexOf('>');
            if (start < end) {
                emailAddr = fromAddress.substring(start, end);
            }
        }

        String normalized = emailAddr.toLowerCase().trim();

        // 1. Kunde-Emails prüfen
        if (kundeRepository.existsByKundenEmail(normalized)) {
            return true;
        }

        // 2. Projekt-Kunden-Emails prüfen
        if (!projektRepository.findByKundenEmail(normalized).isEmpty()) {
            return true;
        }

        // 3. Anfrage-Kunden-Emails prüfen
        return !anfrageRepository.findByKundenEmail(normalized).isEmpty();
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
