package org.example.kalkulationsprogramm.service;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.SpamStufe;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.service.spam.MailDomain;
import org.example.kalkulationsprogramm.service.spam.SpamSignal;
import org.example.kalkulationsprogramm.service.spam.SpamSignale;
import org.example.kalkulationsprogramm.service.spam.SpamStufenRegel;
import org.example.kalkulationsprogramm.service.spam.Textsuche;
import org.example.kalkulationsprogramm.util.HtmlKlartext;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Spam-Filter aus Regeln und Lernfilter (Naive Bayes, lernt aus „Spam“/„Kein Spam“).
 *
 * <p>Regelpunkte R (0-100):
 * <ul>
 *   <li>Gesperrter Absender (Blacklist): 100.</li>
 *   <li>Gewichtete Spam-Wörter im Betreff/Text, Wegwerf-Adressen (+40).</li>
 *   <li>Zusammentreffende {@link SpamSignal Signale}: eines allein 0, zwei +40, ab drei +70,
 *       gefährlicher Anhang mit einem weiteren +85 ({@link SpamStufenRegel#gruppenPunkte}).</li>
 *   <li>Kleine Auffälligkeiten (noreply, Großbuchstaben, viele Links …) zusammen höchstens
 *       {@value #MAX_AUFFAELLIGKEITEN} – allein reichen sie nie für einen Verdacht.</li>
 *   <li>Ein Free-Mail-Absender (gmail.com, web.de …) gibt nie Punkte.</li>
 * </ul>
 *
 * <p>Aus R und dem Lernfilter-Wert entscheidet {@link SpamStufenRegel} die Stufe. Nur
 * {@link SpamStufe#SPAM} setzt {@code isSpam} (und verschiebt die Mail damit auch auf dem
 * Mail-Server); {@link SpamStufe#UNSICHER} bleibt im Posteingang mit Hinweis.
 *
 * <p>Nie Spam: Handentscheidungen, ausgehende und zugeordnete Mails, bekannte Lieferanten und
 * Kunden. Rechnungen mit PDF/XML-Anhang bekommen 0 Regelpunkte.
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
    private final org.example.kalkulationsprogramm.repository.SeenSenderDomainRepository seenSenderDomainRepository;
    private final SpamBayesService spamBayesService;

    // ═══════════════════════════════════════════════════════════════
    // KONFIGURATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Ab diesem Regel-Score meldet {@link #isSpam(Email)} Spam (nur Prüfung, setzt nichts).
     */
    private static final int SPAM_THRESHOLD = 50;

    /**
     * Ab so vielen Wort-Punkten zählen die Spam-Wörter als eigenes {@link SpamSignal#SPAM_WOERTER}.
     */
    static final int SPAM_WOERTER_MINDESTPUNKTE = 30;

    /** Die Wortsuche prüft höchstens so viele Zeichen (lineare Suche, trotzdem begrenzt). */
    private static final int MAX_WORTSUCHE_LAENGE = 50_000;

    /**
     * Kleine Auffälligkeiten zusammen geben höchstens so viele Punkte – weniger als
     * {@link SpamStufenRegel#REGEL_MIT_LERNFILTER}, damit sie allein nie zu Spam führen.
     */
    static final int MAX_AUFFAELLIGKEITEN = 20;

    /** Absender von Wegwerf-Adressen und Link-Kürzern ({@link #BLOCKED_DOMAINS}). */
    private static final int PUNKTE_WEGWERF_DOMAIN = 40;

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
            Pattern.compile(".*[a-z]{15,}@.*"),      // Extrem langer lokaler Teil (generiert)
            // Mixed-Case Random-String wie "jAWULxW.irYpfHK@..." — typisch
            // fuer automatisch generierte Wegwerfadressen. Mindestens 12
            // Buchstaben und Mix aus Gross-/Kleinschreibung.
            Pattern.compile("(?=[^@]*[a-z])(?=[^@]*[A-Z])[a-zA-Z.]{12,}@.*")
    );

    /**
     * Kostenlose Mailprovider. Ein Free-Mail-Absender gibt nie Punkte: Viele Privatkunden
     * schreiben von dort. Auffälligkeiten, die an der Absender-Adresse hängen (Zahlen im Namen,
     * Erstkontakt, keine Links auf die eigene Domain), werden für sie deshalb gar nicht gewertet.
     */
    private static final Set<String> FREE_MAIL_DOMAINS = Set.of(
            // Deutsche Provider
            "gmail.com", "googlemail.com",
            "web.de", "gmx.de", "gmx.net", "gmx.com", "gmx.at", "gmx.ch",
            "t-online.de", "freenet.de", "freenet.com",
            "hotmail.com", "hotmail.de", "outlook.com", "outlook.de",
            "live.com", "live.de", "msn.com",
            "yahoo.com", "yahoo.de", "ymail.com",
            "aol.com", "aol.de",
            "icloud.com", "me.com", "mac.com",
            // Weitere häufige
            "mail.com", "mail.de", "mail.ru",
            "protonmail.com", "proton.me", "tutanota.com", "tutanota.de",
            "zoho.com", "zoho.eu",
            "rocketmail.com",
            "yandex.com", "yandex.ru");

    /**
     * Pattern für Links im Body (zur Domain-Extraktion).
     */
    private static final Pattern LINK_PATTERN = Pattern.compile(
            "https?://([a-zA-Z0-9.-]+)", Pattern.CASE_INSENSITIVE);

    /**
     * Pattern für Tracking-Pixel (1x1 Bilder, häufig in Newsletter-/Spam-Mails).
     */
    private static final Pattern TRACKING_PIXEL = Pattern.compile(
            "<img[^>]*(?:width=[\"']?1[\"']?|height=[\"']?1[\"']?)[^>]*>",
            Pattern.CASE_INSENSITIVE);

    // ═══════════════════════════════════════════════════════════════
    // SPAM-ANALYSE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Bewertet eine Mail: setzt Spam-Stufe, Gründe, Anzeige-Score, {@code isSpam} (nur bei
     * {@link SpamStufe#SPAM}) und {@code isNewsletter}.
     */
    public void analyzeAndMarkSpam(Email email) {
        // Die Handentscheidung (SPAM/HAM/HAM_IMPLICIT) hat immer Vorrang. Eine erneute
        // Bewertung würde sie überschreiben, und Spam wandert auch auf dem Mailserver
        // in den Spam-Ordner.
        if (email.getUserSpamVerdict() != null) {
            return;
        }

        // Ausgehende Emails sind niemals Spam
        if (email.getDirection() == EmailDirection.OUT) {
            markiereAlsKeinSpam(email);
            return;
        }

        // WICHTIG: Zugeordnete Emails (auch Steuerberater-Post wie Lohnabrechnungen)
        // dürfen niemals als Spam oder Newsletter markiert werden
        if (email.getLieferant() != null || email.getProjekt() != null || email.getAnfrage() != null
                || email.getSteuerberater() != null) {
            markiereAlsKeinSpam(email);
            log.debug("[SpamFilter] Email übersprungen (zugeordnet): id={}", email.getId());
            return;
        }

        // WICHTIG: Emails von bekannten Lieferanten-Domains dürfen NIEMALS
        // als Spam oder Newsletter markiert werden - unabhängig vom Inhalt.
        // Lieferanten senden oft über Newsletter-Tools (Mailchimp etc.),
        // verwenden noreply@-Adressen oder haben "news"/"update" Keywords.
        if (email.getFromAddress() != null && isFromLieferant(email.getFromAddress())) {
            markiereAlsKeinSpam(email);
            log.debug("[SpamFilter] Email von bekanntem Lieferant übersprungen: id={}", email.getId());
            return;
        }

        // WICHTIG: Emails von bekannten Kunden (Kunde, Projekt, Anfrage)
        // dürfen NIEMALS als Spam markiert werden.
        if (email.getFromAddress() != null && isFromKnownKunde(email.getFromAddress())) {
            markiereAlsKeinSpam(email);
            log.debug("[SpamFilter] Email von bekanntem Kunden übersprungen: id={}", email.getId());
            return;
        }

        // 1. Regeln
        RegelBewertung regeln = bewerteRegeln(email);

        // 2. Lernfilter (wenn Modell bereit)
        Double lernfilter = null;
        if (spamBayesService.isModelReady()) {
            double p = spamBayesService.predict(spamBayesService.tokenize(email));
            lernfilter = p >= 0 ? p : null;
            email.setBayesScore(lernfilter);
        }

        // 3. Stufe: nur SPAM setzt isSpam – UNSICHER bleibt im Posteingang mit Hinweis
        SpamStufe stufe = SpamStufenRegel.bestimme(lernfilter, regeln.punkte(), regeln.signale());
        email.setSpamStufe(stufe);
        email.setSpamGruende(alsKuerzel(regeln.signale()));
        email.setSpamScore(SpamStufenRegel.anzeigeWert(regeln.punkte(), lernfilter));
        email.setSpam(stufe == SpamStufe.SPAM);

        if (email.isSpam()) {
            // Echter Spam überschreibt Newsletter-Flag – Spam hat Vorrang
            email.setNewsletter(false);
            log.info("[SpamFilter] Email {} als Spam erkannt: regeln={}, lernfilter={}, gruende={}",
                    email.getId(), regeln.punkte(),
                    lernfilter != null ? String.format("%.2f", lernfilter) : "n/a", email.getSpamGruende());
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

    /** Ausgehend, zugeordnet oder bekannt: nie Spam, nie Newsletter. */
    private static void markiereAlsKeinSpam(Email email) {
        email.setSpam(false);
        email.setNewsletter(false);
        email.setSpamScore(0);
        email.setSpamStufe(SpamStufe.KEIN_SPAM);
        email.setSpamGruende(null);
    }

    /** Kommagetrennte Kürzel in fester Reihenfolge, {@code null} ohne Signale. */
    private static String alsKuerzel(Set<SpamSignal> signale) {
        if (signale.isEmpty()) {
            return null;
        }
        return signale.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
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
     * Regelpunkte R einer Email (0-100), ohne Lernfilter. Setzt nichts an der Mail.
     */
    public int calculateSpamScore(Email email) {
        return bewerteRegeln(email).punkte();
    }

    /**
     * Regelpunkte und erkannte Signale. Die Signale bleiben auch bei Blacklist und Whitelist
     * erhalten, damit ein gefährlicher Anhang in einer „Rechnung“ zumindest „Vielleicht Spam“ wird.
     */
    RegelBewertung bewerteRegeln(Email email) {
        String subject = email.getSubject() != null ? email.getSubject().toLowerCase() : "";
        String body = getCombinedBody(email);
        String fromAddress = email.getFromAddress() != null ? email.getFromAddress().toLowerCase() : "";
        String senderDomain = email.getSenderDomain() != null ? email.getSenderDomain().toLowerCase() : "";
        String combinedText = subject + " " + body;
        Set<SpamSignal> signale = SpamSignale.erkenne(email);

        // 0. Blacklist: bewusste Nutzerentscheidung → sofort sicher
        if (emailBlacklistRepository.existsByEmailAddress(fromAddress)) {
            log.debug("[SpamFilter] Absender ist auf Blacklist: id={}", email.getId());
            return new RegelBewertung(100, signale);
        }

        // ═══════════════════════════════════════════════════════════════
        // WHITELIST - 0 Punkte wenn zutreffend
        // ═══════════════════════════════════════════════════════════════

        // 1. Emails von bekannten Lieferanten → NIEMALS Spam
        if (isFromLieferant(fromAddress)) {
            log.debug("[SpamFilter] Whitelist: Email von Lieferant erkannt: id={}", email.getId());
            return new RegelBewertung(0, signale);
        }

        // 2. Rechnungs-Emails mit PDF/XML Attachment → kein Spam
        if (combinedText.contains("rechnung") && hasRelevantAttachment(email)) {
            log.debug("[SpamFilter] Whitelist: Rechnungs-Email mit PDF/XML erkannt: id={}", email.getId());
            return new RegelBewertung(0, signale);
        }

        // ═══════════════════════════════════════════════════════════════
        // SPAM-SCORING
        // ═══════════════════════════════════════════════════════════════

        // 1. Gewichtete Spam-Wörter – nur ganze Wörter im sichtbaren Text, kein rohes HTML:
        //    „porn“ darf nicht in „Ansporn“ treffen, „xxx“ nicht in einer Klassen-ID.
        String sichtbarerText = Textsuche.normalisiere(java.util.Objects.toString(email.getSubject(), "") + " " + sichtbarerInhalt(email),
                MAX_WORTSUCHE_LAENGE);
        int woerter = 0;
        for (SpamKeyword keyword : SPAM_KEYWORDS) {
            if (Textsuche.enthaeltWort(sichtbarerText, Textsuche.normalisiere(keyword.keyword))) {
                woerter += keyword.weight;
            }
        }
        if (woerter >= SPAM_WOERTER_MINDESTPUNKTE) {
            signale.add(SpamSignal.SPAM_WOERTER);
        }

        int score = woerter;

        // 2. Wegwerf-Adressen und Link-Kürzer als Absender
        if (BLOCKED_DOMAINS.contains(senderDomain)) {
            score += PUNKTE_WEGWERF_DOMAIN;
        }

        // 3. Zusammentreffende Signale (eines allein gibt nichts)
        score += SpamStufenRegel.gruppenPunkte(signale);

        // 4. Kleine Auffälligkeiten, gedeckelt
        score += Math.min(MAX_AUFFAELLIGKEITEN, kleineAuffaelligkeiten(email, subject, body, senderDomain));

        return new RegelBewertung(Math.min(100, Math.max(0, score)), signale);
    }

    /**
     * Kleine Auffälligkeiten. Jede für sich trifft auch echte Mails; deshalb gedeckelt auf
     * {@value #MAX_AUFFAELLIGKEITEN} Punkte. Für Free-Mail-Absender zählt nichts, was an der
     * Absender-Adresse hängt.
     */
    private int kleineAuffaelligkeiten(Email email, String subject, String body, String senderDomain) {
        int delta = 0;
        boolean isFreeMail = !senderDomain.isBlank() && FREE_MAIL_DOMAINS.contains(senderDomain);

        // Verdächtige Absender-Muster. Patterns werden gegen den ORIGINAL-fromAddress
        // getestet, weil Mixed-Case-Random-Strings ("jAWULxW.irYpfHK@...") nach Lowercase
        // wie ein normales Wort aussehen würden.
        String fromAddressOriginal = email.getFromAddress() != null ? email.getFromAddress() : "";
        if (!isFreeMail) {
            for (Pattern pattern : SUSPICIOUS_SENDER_PATTERNS) {
                if (pattern.matcher(fromAddressOriginal).matches()) {
                    delta += 10;
                    break; // Nur einmal zählen
                }
            }
        }

        // Leerer Betreff
        if (subject.isBlank()) {
            delta += 15;
        }

        // Zu viele GROSSBUCHSTABEN im Betreff
        if (email.getSubject() != null) {
            long upperCount = email.getSubject().chars().filter(Character::isUpperCase).count();
            long totalLetters = email.getSubject().chars().filter(Character::isLetter).count();
            if (totalLetters > 5 && (double) upperCount / totalLetters > 0.7) {
                delta += 20;
            }
        }

        // Zu viele Links im Body
        int linkVorkommen = countOccurrences(body, "http://") + countOccurrences(body, "https://");
        if (linkVorkommen > 10) {
            delta += 15;
        }

        Set<String> linkDomains = extractLinkDomains(body);
        int linkCount = linkDomains.size();

        // Mehr als 3 Links und KEINE führt auf die Sender-Domain. Free-Mailer verlinken
        // nie auf gmail.com & Co., deshalb gilt das nur für Firmen-Domains.
        if (!isFreeMail && linkCount >= 3 && !senderDomain.isBlank()
                && linkDomains.stream().noneMatch(d -> MailDomain.gleicheOrganisation(d, senderDomain))) {
            delta += 15;
        }

        // Erstkontakt: neue Firmen-Domain mit mehreren Links. Erstkontakt allein zählt nie –
        // jede Neuanfrage ist ein Erstkontakt.
        if (!isFreeMail && linkCount >= 3 && !senderDomain.isBlank()
                && !seenSenderDomainRepository.existsByDomain(senderDomain)) {
            delta += 10;
        }

        // Sehr wenig Text + viele Links
        int textLength = body.replaceAll("\\s++", " ").length();
        if (linkCount >= 5 && textLength > 0 && textLength < 200) {
            delta += 15;
        }

        // Tracking-Pixel: Newsletter haben oft 1-2; Spam oft 5+.
        if (email.getHtmlBody() != null) {
            int pixelCount = 0;
            var pixelMatcher = TRACKING_PIXEL.matcher(email.getHtmlBody());
            while (pixelMatcher.find() && pixelCount < 10) {
                pixelCount++;
            }
            if (pixelCount >= 5) {
                delta += 10;
            }
        }

        return delta;
    }

    /**
     * Extrahiert alle Link-Domains aus dem Body (lowercase, ohne www-Prefix).
     */
    private Set<String> extractLinkDomains(String body) {
        if (body == null || body.isEmpty()) {
            return Set.of();
        }
        var matcher = LINK_PATTERN.matcher(body);
        Set<String> domains = new java.util.HashSet<>();
        while (matcher.find()) {
            String domain = matcher.group(1).toLowerCase();
            if (domain.startsWith("www.")) {
                domain = domain.substring(4);
            }
            domains.add(domain);
            if (domains.size() >= 50) break; // Schutz gegen Riesen-Bodies
        }
        return domains;
    }

    /** Text, den der Empfänger sieht: Klartext, sonst das HTML ohne Tags. */
    private static String sichtbarerInhalt(Email email) {
        if (email.getBody() != null && !email.getBody().isBlank()) {
            return email.getBody();
        }
        String html = email.getHtmlBody();
        if (html == null || html.isBlank()) {
            return "";
        }
        return HtmlKlartext.zuText(html.length() > MAX_WORTSUCHE_LAENGE * 4 ? html.substring(0, MAX_WORTSUCHE_LAENGE * 4) : html);
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

    /**
     * Regelpunkte R (0-100) und die dabei erkannten Signale.
     */
    record RegelBewertung(int punkte, Set<SpamSignal> signale) {
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
