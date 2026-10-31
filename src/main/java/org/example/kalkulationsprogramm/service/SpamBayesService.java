package org.example.kalkulationsprogramm.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.example.kalkulationsprogramm.domain.SpamModelStats;
import org.example.kalkulationsprogramm.repository.SpamModelStatsRepository;
import org.example.kalkulationsprogramm.repository.SpamTokenCountRepository;
import org.example.kalkulationsprogramm.service.spam.SpamSignal;
import org.example.kalkulationsprogramm.service.spam.SpamSignale;
import org.example.kalkulationsprogramm.util.FreemailDomains;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Lernender Spam-Filter nach Robinson/Fisher (wie Bogofilter und SpamBayes).
 *
 * Lernt supervised aus User-Feedback (mark-spam / mark-not-spam)
 * und kann mit CSV-Daten (z.B. Kaggle SMS Spam Collection) vortrainiert werden.
 *
 * <p>Jedes Wort zählt je Mail höchstens einmal ({@link #tokenize} liefert eine Menge). Die Zähler in
 * {@code spam_token_count} sind deshalb Dokument-Häufigkeiten: „in wie vielen Spam- bzw.
 * Ham-Mails kam das Wort vor“; {@code total_spam}/{@code total_ham} zählen die trainierten Mails.
 * Genau das braucht Robinson: {@code p(w) = (s/S) / (s/S + h/H)}.
 *
 * <p>Token-Frequenzen werden in der Datenbank persistiert und beim Start in einen
 * In-Memory-Cache geladen für schnelle Prediction.
 *
 * <p>Der Zerleger ist versioniert ({@link #AKTUELLE_ZERLEGER_VERSION}): {@link #train} merkt sich
 * die Version an der Mail, {@link #untrain} zerlegt wieder mit genau dieser Version, damit nur die
 * Zähler zurückgenommen werden, die damals hochgezählt wurden.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpamBayesService {

    private final SpamTokenCountRepository tokenRepo;
    private final SpamModelStatsRepository statsRepo;

    // ═══════════════════════════════════════════════════════════════
    // KONFIGURATION
    // ═══════════════════════════════════════════════════════════════

    /** Minimale Anzahl trainierter Dokumente bevor das Modell aktiv wird. */
    private static final int MIN_TRAINING_SAMPLES = 20;

    /**
     * Aktuelle Zerleger-Version. Version 1 (an der Mail {@code null}): Betreff, Text und
     * Absender-Domain. Version 2 zusätzlich Free-Mail, Reply-To, Anhangtypen und Spam-Signale.
     */
    static final int AKTUELLE_ZERLEGER_VERSION = 2;

    /** Robinson: angenommene Wahrscheinlichkeit für ein Wort, über das noch nichts bekannt ist. */
    private static final double ROBINSON_X = 0.5;

    /** Robinson: Gewicht dieser Annahme (in „Mails“); kleine Zähler werden zur Mitte gezogen. */
    private static final double ROBINSON_S = 0.45;

    /** Nur Wörter, die mindestens so weit von 0,5 entfernt sind, gehen in die Bewertung ein. */
    private static final double MIN_ABWEICHUNG = 0.1;

    /** Höchstens so viele Wörter (die mit der größten Abweichung) bewerten eine Mail. */
    static final int MAX_BEWERTETE_TOKENS = 150;

    /** Ergebnis, wenn kein Wort etwas aussagt. */
    private static final double NEUTRAL = 0.5;

    /** Höchstens so viele verschiedene Anhang-Endungen je Mail als Token. */
    private static final int MAX_ANHANG_ENDUNGEN = 5;

    /** Erlaubte Anhang-Endung (nach Kleinschreibung). */
    private static final Pattern ANHANG_ENDUNG = Pattern.compile("[a-z0-9]{1,10}");

    /** Max. Tokens pro Email (verhindert Dominanz langer Emails). */
    private static final int MAX_TOKENS_PER_EMAIL = 500;

    /** Min/Max Token-Länge. */
    private static final int MIN_TOKEN_LENGTH = 3;
    private static final int MAX_TOKEN_LENGTH = 30;

    /** Pattern für Tokenisierung: Wörter mit Umlauten. */
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^a-zA-ZäöüÄÖÜß0-9]+");

    /** Pattern zum Erkennen reiner Zahlen. */
    private static final Pattern PURE_NUMBER = Pattern.compile("\\d+");

    /** HTML-Tag-Pattern zum Strippen. */
    private static final Pattern HTML_TAGS = Pattern.compile("<[^>]++>");

    // ═══════════════════════════════════════════════════════════════
    // IN-MEMORY CACHE
    // ═══════════════════════════════════════════════════════════════

    /** token -> [spamCount, hamCount] */
    private volatile Map<String, int[]> tokenCache = new ConcurrentHashMap<>();
    private volatile long totalSpam = 0;
    private volatile long totalHam = 0;
    private volatile int vocabularySize = 0;

    // ═══════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    @PostConstruct
    public void loadModel() {
        refreshModel();
        log.info("[SpamBayes] Modell geladen: {} Tokens, {} Spam-Docs, {} Ham-Docs, ready={}",
                vocabularySize, totalSpam, totalHam, isModelReady());
    }

    @Scheduled(fixedDelay = 300_000) // Alle 5 Minuten
    public void refreshModel() {
        try {
            // Lade Stats
            totalSpam = statsRepo.findByStatKey("total_spam")
                    .map(SpamModelStats::getStatValue).orElse(0L);
            totalHam = statsRepo.findByStatKey("total_ham")
                    .map(SpamModelStats::getStatValue).orElse(0L);

            // Lade alle Tokens in neuen Cache
            Map<String, int[]> newCache = new ConcurrentHashMap<>();
            var allTokens = tokenRepo.findAll();
            for (var tc : allTokens) {
                newCache.put(tc.getToken(), new int[]{tc.getSpamCount(), tc.getHamCount()});
            }

            // Atomisch swappen
            this.tokenCache = newCache;
            this.vocabularySize = newCache.size();
        } catch (Exception e) {
            log.error("[SpamBayes] Fehler beim Laden des Modells", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // TOKENISIERUNG
    // ═══════════════════════════════════════════════════════════════

    /**
     * Tokenisiert Subject + Body einer Email in ein Set von Tokens.
     */
    public Set<String> tokenize(String subject, String body) {
        StringBuilder sb = new StringBuilder();
        if (subject != null) {
            sb.append(subject).append(" ");
        }
        if (body != null) {
            // HTML-Tags entfernen
            sb.append(HTML_TAGS.matcher(body).replaceAll(" "));
        }

        String text = sb.toString().toLowerCase();
        String[] parts = TOKEN_SPLIT.split(text);

        Set<String> tokens = new LinkedHashSet<>();
        for (String part : parts) {
            if (part.length() >= MIN_TOKEN_LENGTH
                    && part.length() <= MAX_TOKEN_LENGTH
                    && !PURE_NUMBER.matcher(part).matches()) {
                tokens.add(part);
                if (tokens.size() >= MAX_TOKENS_PER_EMAIL) {
                    break;
                }
            }
        }
        return tokens;
    }

    /**
     * Tokenisiert direkt aus einer Email-Entity.
     *
     * Zusaetzlich zum Subject/Body werden auch Sender-Merkmale als
     * praefixierte Tokens eingespeist (Domain, TLD, "from_random_local"
     * fuer Mixed-Case-Random-Local-Parts). So lernt Bayes z.B. dass Mails
     * von "adventurecentral.com" oder mit Random-Sender oft Spam sind —
     * das fehlt, wenn man nur Body-Tokens betrachtet.
     */
    public Set<String> tokenize(Email email) {
        return tokenize(email, AKTUELLE_ZERLEGER_VERSION);
    }

    /**
     * Zerlegt eine Mail mit einer bestimmten Zerleger-Version. {@code untrain} braucht das, um
     * genau die Tokens zurückzunehmen, die beim Training gezählt wurden.
     *
     * @param version 1 (oder kleiner) = nur Betreff, Text und Absender; ab 2 zusätzlich die
     *                Kopf-, Anhang- und Signal-Tokens. Höhere Versionen als die aktuelle werden
     *                wie die aktuelle behandelt.
     */
    public Set<String> tokenize(Email email, int version) {
        String body = combineBody(email);
        Set<String> tokens = tokenize(email.getSubject(), body);
        addSenderTokens(tokens, email.getFromAddress());
        if (version >= 2) {
            // Version-2-Tokens kommen nach den Version-1-Tokens und ohne die Obergrenze von
            // MAX_TOKENS_PER_EMAIL (es sind nur wenige), damit der Version-1-Teil gleich bleibt.
            addKopfTokens(tokens, email);
            addAnhangTokens(tokens, email.getAttachments());
            tokens.addAll(signalTokens(SpamSignale.erkenne(email)));
        }
        return tokens;
    }

    /**
     * Fuegt Sender-bezogene Marker-Tokens zum Token-Set hinzu.
     * Praefix verhindert Kollisionen mit normalen Body-Tokens.
     */
    private void addSenderTokens(Set<String> tokens, String fromAddress) {
        String email = reineAdresse(fromAddress);
        if (email == null) return;
        int at = email.indexOf('@');
        if (at <= 0 || at >= email.length() - 1) return;

        String localPart = email.substring(0, at);
        String domain = brauchbareDomain(email);
        if (domain == null) {
            return;
        }

        if (tokens.size() < MAX_TOKENS_PER_EMAIL) {
            tokens.add("from_domain_" + domain);
        }
        int dot = domain.lastIndexOf('.');
        if (dot > 0 && dot < domain.length() - 1 && tokens.size() < MAX_TOKENS_PER_EMAIL) {
            tokens.add("from_tld_" + domain.substring(dot + 1));
        }
        if (looksRandomLocalPart(localPart) && tokens.size() < MAX_TOKENS_PER_EMAIL) {
            tokens.add("from_random_local");
        }
    }

    /**
     * Zerleger Version 2: Free-Mail-Absender und Reply-To. Kontaktformulare (Absender beim
     * Hoster, Reply-To beim Kunden) lösen {@code replyto_abweichend} auch aus – der Lernfilter
     * lernt aus den Zählern, wie viel das bedeutet.
     */
    private void addKopfTokens(Set<String> tokens, Email email) {
        String fromAdresse = reineAdresse(email.getFromAddress());
        String fromDomain = fromAdresse != null ? brauchbareDomain(fromAdresse) : null;
        if (fromDomain != null && FreemailDomains.istFreemailDomain(fromDomain)) {
            tokens.add("from_freemail");
        }

        String replyToAdresse = reineAdresse(email.getReplyToAddress());
        String replyToDomain = replyToAdresse != null ? brauchbareDomain(replyToAdresse) : null;
        if (replyToDomain == null) {
            return;
        }
        tokens.add("replyto_domain_" + replyToDomain);
        if (fromDomain != null && !hauptDomain(fromDomain).equals(hauptDomain(replyToDomain))) {
            tokens.add("replyto_abweichend");
        }
    }

    /**
     * Zerleger Version 2: Endungen der Anhänge ({@code anhang_pdf}, höchstens fünf verschiedene,
     * alphabetisch – unabhängig von der Reihenfolge, in der die Anhänge geladen werden).
     * Eingebettete Bilder (Signatur-Logos) zählen nicht als Anhang. Gefährliche Anhänge kommen
     * über {@code signal_gefaehrlicher_anhang} (siehe {@link SpamSignale}), nicht doppelt.
     */
    private void addAnhangTokens(Set<String> tokens, Collection<EmailAttachment> anhaenge) {
        if (anhaenge == null || anhaenge.isEmpty()) {
            return;
        }
        Set<String> endungen = new TreeSet<>();
        for (EmailAttachment anhang : anhaenge) {
            String endung = anhang != null ? dateiEndung(anhang.getOriginalFilename()) : null;
            if (endung != null && !anhang.isInline()) {
                endungen.add(endung);
            }
        }
        endungen.stream().limit(MAX_ANHANG_ENDUNGEN).forEach(e -> tokens.add("anhang_" + e));
    }

    /**
     * Übersetzt erkannte Spam-Signale in Tokens ({@code signal_absender_gefaelscht} …), in fester
     * Reihenfolge der Aufzählung.
     */
    static List<String> signalTokens(Set<SpamSignal> signale) {
        if (signale == null || signale.isEmpty()) {
            return List.of();
        }
        return EnumSet.copyOf(signale).stream()
                .map(signal -> "signal_" + signal.name().toLowerCase(Locale.ROOT))
                .toList();
    }

    /** Endung eines Dateinamens, kleingeschrieben, nur {@code [a-z0-9]{1,10}}; sonst {@code null}. */
    private static String dateiEndung(String dateiname) {
        if (dateiname == null) {
            return null;
        }
        String name = dateiname.trim();
        int punkt = name.lastIndexOf('.');
        if (punkt < 0 || punkt == name.length() - 1) {
            return null;
        }
        String endung = name.substring(punkt + 1).toLowerCase(Locale.ROOT);
        return ANHANG_ENDUNG.matcher(endung).matches() ? endung : null;
    }

    /**
     * Pure E-Mail aus "Name <email@domain.com>" extrahieren.
     * lastIndexOf('>') / lastIndexOf('<') statt indexOf, damit Adressen
     * mit doppelten Bracket-Paaren (z.B. "Foo <bar> <real@x.de>")
     * korrekt das letzte (= echte Adresse) Paar treffen.
     */
    private static String reineAdresse(String adresse) {
        if (adresse == null || adresse.isBlank()) return null;
        int lt = adresse.lastIndexOf('<');
        int gt = adresse.lastIndexOf('>');
        if (lt >= 0 && gt > lt) {
            return adresse.substring(lt + 1, gt);
        }
        return adresse;
    }

    /**
     * Domain einer reinen Adresse, kleingeschrieben – oder {@code null}, wenn keine da ist.
     * Extrem lange Domains skippen — sind selten echt und blaehen
     * das Token-Set unnoetig auf. Schranke gilt fuer alle
     * Sender-Tokens, damit bei langen Domains kein TLD-Token ohne
     * Domain-Token entsteht.
     */
    private static String brauchbareDomain(String reineAdresse) {
        int at = reineAdresse.indexOf('@');
        if (at <= 0 || at >= reineAdresse.length() - 1) return null;
        String domain = reineAdresse.substring(at + 1).toLowerCase().trim();
        if (domain.isBlank() || domain.length() > MAX_TOKEN_LENGTH + 10) {
            return null;
        }
        return domain;
    }

    /** Grobe Haupt-Domain: die letzten zwei Teile ({@code mail.firma.de} → {@code firma.de}). */
    private static String hauptDomain(String domain) {
        int letzterPunkt = domain.lastIndexOf('.');
        int vorletzterPunkt = letzterPunkt > 0 ? domain.lastIndexOf('.', letzterPunkt - 1) : -1;
        return vorletzterPunkt >= 0 ? domain.substring(vorletzterPunkt + 1) : domain;
    }

    /**
     * Heuristik fuer "automatisch generierter Local-Part".
     *
     * Zwei Kriterien muessen erfuellt sein, damit echte CamelCase-Namen
     * wie "MaxMustermann" oder "JohnDoeSmith" NICHT faelschlich erkannt
     * werden:
     *   - Mind. 12 Zeichen (legt die Latte ueber typische 2-Wort-Namen).
     *   - Mind. 4 Case-Wechsel (Random-Strings wie "jAWULxW.irYpfHK"
     *     haben sehr viele; CamelCase-Namen typisch 1-3).
     */
    private boolean looksRandomLocalPart(String s) {
        if (s == null || s.length() < 12) return false;
        int caseSwitches = 0;
        Boolean prevWasUpper = null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c)) {
                if (prevWasUpper != null && !prevWasUpper) caseSwitches++;
                prevWasUpper = true;
            } else if (Character.isLowerCase(c)) {
                if (prevWasUpper != null && prevWasUpper) caseSwitches++;
                prevWasUpper = false;
            }
            if (caseSwitches >= 4) return true;
        }
        return false;
    }

    private String combineBody(Email email) {
        StringBuilder sb = new StringBuilder();
        if (email.getBody() != null) {
            sb.append(email.getBody()).append(" ");
        }
        if (email.getHtmlBody() != null) {
            sb.append(email.getHtmlBody());
        }
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════
    // TRAINING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Trainiert das Modell mit einer Email.
     * Wird aufgerufen wenn der User eine Email als Spam/Ham markiert.
     *
     * <p>Setzt {@code spamTokenVersion} an der Mail; speichern muss der Aufrufer.
     */
    @Transactional
    public void train(Email email, boolean isSpam) {
        Set<String> tokens = tokenize(email, AKTUELLE_ZERLEGER_VERSION);
        trainTokens(tokens, isSpam);
        email.setSpamTokenVersion((short) AKTUELLE_ZERLEGER_VERSION);
    }

    /**
     * Trainiert das Modell mit rohem Text (für CSV-Import).
     */
    @Transactional
    public void trainText(String text, boolean isSpam) {
        Set<String> tokens = tokenize(null, text);
        trainTokens(tokens, isSpam);
    }

    private void trainTokens(Set<String> tokens, boolean isSpam) {
        if (tokens.isEmpty()) return;

        int spamInc = isSpam ? 1 : 0;
        int hamInc = isSpam ? 0 : 1;

        for (String token : tokens) {
            tokenRepo.upsertToken(token, spamInc, hamInc);
        }

        // Globalen Zähler incrementieren
        statsRepo.incrementStat(isSpam ? "total_spam" : "total_ham");

        // In-Memory-Cache sofort aktualisieren für schnelles Feedback
        for (String token : tokens) {
            tokenCache.compute(token, (k, counts) -> {
                if (counts == null) counts = new int[]{0, 0};
                counts[isSpam ? 0 : 1]++;
                return counts;
            });
        }
        if (isSpam) {
            totalSpam++;
        } else {
            totalHam++;
        }
        vocabularySize = tokenCache.size();
    }

    // ═══════════════════════════════════════════════════════════════
    // UNTRAINING (Reklassifizierung rückgängig machen)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Macht ein vorheriges Training rückgängig.
     * Wird aufgerufen wenn der User eine Email umklassifiziert (Spam→Ham oder Ham→Spam),
     * damit die alten Token-Zähler korrigiert werden bevor die neue Klassifizierung
     * trainiert wird. Verhindert doppelte Zählung.
     *
     * <p>Zerlegt mit der Version, mit der die Mail trainiert wurde ({@code spamTokenVersion},
     * {@code null} = Version 1), damit genau die damals gezählten Tokens zurückgehen.
     *
     * @param email   die Email deren vorherige Klassifizierung rückgängig gemacht wird
     * @param wasSpam true wenn die Email bisher als Spam trainiert war
     */
    @Transactional
    public void untrain(Email email, boolean wasSpam) {
        Short gespeicherteVersion = email.getSpamTokenVersion();
        int version = gespeicherteVersion != null ? gespeicherteVersion : 1;
        Set<String> tokens = tokenize(email, version);
        untrainTokens(tokens, wasSpam);
    }

    private void untrainTokens(Set<String> tokens, boolean wasSpam) {
        if (tokens.isEmpty()) return;

        int spamDec = wasSpam ? 1 : 0;
        int hamDec  = wasSpam ? 0 : 1;

        for (String token : tokens) {
            // GREATEST(0,...) in der Query verhindert negative Werte
            tokenRepo.decrementToken(token, spamDec, hamDec);
        }

        // Globalen Zähler dekrementieren (mindestens 0)
        statsRepo.decrementStat(wasSpam ? "total_spam" : "total_ham");

        // In-Memory-Cache sofort aktualisieren
        for (String token : tokens) {
            tokenCache.computeIfPresent(token, (k, counts) -> {
                counts[wasSpam ? 0 : 1] = Math.max(0, counts[wasSpam ? 0 : 1] - 1);
                return counts;
            });
        }
        if (wasSpam) {
            totalSpam = Math.max(0, totalSpam - 1);
        } else {
            totalHam = Math.max(0, totalHam - 1);
        }
        vocabularySize = tokenCache.size();
    }

    // ═══════════════════════════════════════════════════════════════
    // PREDICTION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Berechnet die Spam-Wahrscheinlichkeit für gegebene Tokens (Robinson/Fisher).
     *
     * <ol>
     *   <li>Je Wort {@code p(w) = (s/S) / (s/S + h/H)} aus den Dokument-Häufigkeiten.</li>
     *   <li>Robinson-Glättung {@code f = (0,45·0,5 + n·p) / (0,45 + n)} mit {@code n = s + h}:
     *       selten gesehene Wörter bleiben nahe 0,5, unbekannte sind genau 0,5.</li>
     *   <li>Nur Wörter mit {@code |f − 0,5| ≥ 0,1}, höchstens die 150 deutlichsten.</li>
     *   <li>Fisher: {@code S' = 1 − chi2Q(−2·Σ ln(1−f), 2k)}, {@code H' = 1 − chi2Q(−2·Σ ln f, 2k)},
     *       Ergebnis {@code (1 + S' − H') / 2}. Werte um 0,5 heißen „unsicher“.</li>
     * </ol>
     *
     * @return Wahrscheinlichkeit 0.0-1.0 (0,5, wenn kein Wort etwas aussagt), oder -1.0 wenn
     *         Modell nicht bereit
     */
    public double predict(Set<String> tokens) {
        if (!isModelReady()) {
            return -1.0;
        }
        return fisherKombination(deutlichsteWahrscheinlichkeiten(tokens));
    }

    /**
     * Geglättete Wahrscheinlichkeiten der Wörter, die etwas aussagen: mindestens
     * {@link #MIN_ABWEICHUNG} von 0,5 entfernt, die {@link #MAX_BEWERTETE_TOKENS} deutlichsten
     * zuerst (bei Gleichstand alphabetisch, damit das Ergebnis reproduzierbar ist).
     */
    List<Double> deutlichsteWahrscheinlichkeiten(Set<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return List.of();
        }
        long spamDocs = totalSpam;
        long hamDocs = totalHam;
        Map<String, int[]> cache = tokenCache;

        record Bewertet(String token, double f) {
            double abweichung() {
                return Math.abs(f - NEUTRAL);
            }
        }
        List<Bewertet> aussagekraeftig = new ArrayList<>();
        for (String token : tokens) {
            int[] counts = cache.get(token);
            if (counts == null) {
                continue; // unbekannt = neutral
            }
            double f = geglaetteteWahrscheinlichkeit(counts[0], counts[1], spamDocs, hamDocs);
            if (Math.abs(f - NEUTRAL) >= MIN_ABWEICHUNG) {
                aussagekraeftig.add(new Bewertet(token, f));
            }
        }
        return aussagekraeftig.stream()
                .sorted(Comparator.comparingDouble(Bewertet::abweichung).reversed()
                        .thenComparing(Bewertet::token))
                .limit(MAX_BEWERTETE_TOKENS)
                .map(Bewertet::f)
                .toList();
    }

    /**
     * Robinson: {@code p = (s/S) / (s/S + h/H)}, geglättet zu {@code f = (s_x·x + n·p) / (s_x + n)}.
     * Fehlt eine Seite ganz ({@code S = 0} oder {@code H = 0}), zählt ihr Anteil als 0; sagt keine
     * Seite etwas, ist das Wort neutral.
     */
    static double geglaetteteWahrscheinlichkeit(int spamCount, int hamCount, long spamDocs, long hamDocs) {
        int s = Math.max(0, spamCount);
        int h = Math.max(0, hamCount);
        // Anteile auf 1 begrenzt: nach einem CSV-Import oder Untrain können Zähler kurz größer
        // sein als die Mail-Summe.
        double spamAnteil = spamDocs > 0 ? Math.min(1.0, (double) s / spamDocs) : 0.0;
        double hamAnteil = hamDocs > 0 ? Math.min(1.0, (double) h / hamDocs) : 0.0;
        if (spamAnteil + hamAnteil <= 0.0) {
            return ROBINSON_X;
        }
        double p = spamAnteil / (spamAnteil + hamAnteil);
        double n = (double) s + h;
        return (ROBINSON_S * ROBINSON_X + n * p) / (ROBINSON_S + n);
    }

    /**
     * Fisher-Kombination nach Robinson: {@code (1 + S' − H') / 2}; ohne Wörter 0,5.
     */
    static double fisherKombination(List<Double> wahrscheinlichkeiten) {
        int k = wahrscheinlichkeiten.size();
        if (k == 0) {
            return NEUTRAL;
        }
        double summeLnSpam = 0.0; // Σ ln(1 − f)
        double summeLnHam = 0.0;  // Σ ln f
        for (double f : wahrscheinlichkeiten) {
            summeLnSpam += Math.log1p(-f);
            summeLnHam += Math.log(f);
        }
        double spamBeleg = 1.0 - chi2Q(-2.0 * summeLnSpam, 2 * k);
        double hamBeleg = 1.0 - chi2Q(-2.0 * summeLnHam, 2 * k);
        return klemme((1.0 + spamBeleg - hamBeleg) / 2.0);
    }

    /**
     * Obere Wahrscheinlichkeit der Chi²-Verteilung {@code P(X ≥ x2)} für gerade Freiheitsgrade:
     * {@code e^{-m} · Σ_{i=0}^{v/2-1} m^i / i!} mit {@code m = x2/2}. Die Summanden werden im
     * Logarithmus gebildet, damit {@code e^{-m}} bei großem {@code m} nicht vorzeitig zu 0 wird.
     */
    static double chi2Q(double x2, int freiheitsgrade) {
        if (freiheitsgrade <= 0 || freiheitsgrade % 2 != 0) {
            throw new IllegalArgumentException("Freiheitsgrade müssen gerade und positiv sein: " + freiheitsgrade);
        }
        if (!(x2 > 0.0)) {
            return 1.0;
        }
        double m = x2 / 2.0;
        double logM = Math.log(m);
        double logSummand = -m;
        double summe = Math.exp(logSummand);
        for (int i = 1; i < freiheitsgrade / 2; i++) {
            logSummand += logM - Math.log(i);
            summe += Math.exp(logSummand);
        }
        return klemme(summe);
    }

    private static double klemme(double wert) {
        return Math.min(1.0, Math.max(0.0, wert));
    }

    /**
     * Ist das Modell ausreichend trainiert?
     */
    public boolean isModelReady() {
        return (totalSpam + totalHam) >= MIN_TRAINING_SAMPLES;
    }

    // ═══════════════════════════════════════════════════════════════
    // CSV-IMPORT (Pre-Training)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Importiert Trainingsdaten aus CSV/TSV (UCI SMS Spam Collection, Kaggle- oder HuggingFace-Format).
     * Unterstützt:
     * - Tab-separiert: "ham\tNachrichtentext" (UCI-Format, kein Header)
     * - Komma-separiert: "ham,Nachrichtentext" oder "v1,v2" Header (Kaggle-Format)
     * - HuggingFace-Format: "text,labels" Header mit Werten "spam"/"not_spam" (Label hinten)
     * 
     * Optimiert: Sammelt alle Token-Counts im RAM und schreibt dann batch-weise in die DB.
     *
     * @return Array [spamCount, hamCount] der importierten Datensätze
     */
    @Transactional
    public int[] bootstrapFromCsv(InputStream csvStream) throws IOException {
        int spamCount = 0;
        int hamCount = 0;

        // Alle Token-Counts erst im RAM sammeln statt pro-Zeile in die DB zu schreiben
        Map<String, int[]> batchCounts = new HashMap<>(); // token -> [spam, ham]

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(csvStream, StandardCharsets.UTF_8))) {

            String line;
            boolean firstLine = true;
            Boolean tabSeparated = null; // Auto-detect
            boolean labelLast = false;  // HuggingFace-Format: text,labels

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;

                // Beim ersten Durchlauf: Format erkennen + ggf. Header überspringen
                if (firstLine) {
                    firstLine = false;
                    tabSeparated = line.contains("\t");
                    String headerLower = line.toLowerCase();
                    // HuggingFace-Format: "text,labels" (Text zuerst, Label hinten)
                    if (headerLower.startsWith("text,") && headerLower.contains("label")) {
                        labelLast = true;
                        continue;
                    }
                    if (headerLower.contains("v1") || headerLower.contains("label")) {
                        continue;
                    }
                }

                // Parsing: Tab- oder Komma-separiert
                String[] parts;
                if (Boolean.TRUE.equals(tabSeparated)) {
                    int tabIndex = line.indexOf('\t');
                    if (tabIndex < 0) continue;
                    parts = new String[]{line.substring(0, tabIndex), line.substring(tabIndex + 1)};
                } else {
                    parts = parseCsvLine(line);
                }

                if (parts.length < 2) continue;

                String label;
                String text;

                if (labelLast) {
                    // HuggingFace-Format: text,...,label (letztes Feld = Label)
                    label = parts[parts.length - 1].trim().toLowerCase();
                    // Alle Felder vor dem Label als Text zusammenfügen (falls Kommas im Text)
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < parts.length - 1; i++) {
                        if (i > 0) sb.append(',');
                        sb.append(parts[i]);
                    }
                    text = sb.toString().trim();
                } else {
                    label = parts[0].trim().toLowerCase();
                    text = parts[1].trim();
                }

                if (text.isEmpty()) continue;

                boolean isSpam;
                if ("spam".equals(label)) {
                    isSpam = true;
                    spamCount++;
                } else if ("ham".equals(label) || "not_spam".equals(label)) {
                    isSpam = false;
                    hamCount++;
                } else {
                    continue;
                }

                // Tokens im RAM aggregieren (statt sofortigem DB-Upsert pro Token)
                Set<String> tokens = tokenize(null, text);
                for (String token : tokens) {
                    batchCounts.computeIfAbsent(token, k -> new int[]{0, 0});
                    batchCounts.get(token)[isSpam ? 0 : 1]++;
                }
            }
        }

        // Batch-Upsert: einmal pro unique Token statt einmal pro Token-Vorkommen
        log.info("[SpamBayes] Batch-Import: {} unique Tokens in DB schreiben...", batchCounts.size());
        int batchIdx = 0;
        for (Map.Entry<String, int[]> entry : batchCounts.entrySet()) {
            tokenRepo.upsertToken(entry.getKey(), entry.getValue()[0], entry.getValue()[1]);
            batchIdx++;
            if (batchIdx % 1000 == 0) {
                log.info("[SpamBayes] ... {}/{} Tokens geschrieben", batchIdx, batchCounts.size());
            }
        }

        // Globale Zähler setzen
        for (int i = 0; i < spamCount; i++) statsRepo.incrementStat("total_spam");
        for (int i = 0; i < hamCount; i++) statsRepo.incrementStat("total_ham");

        // In-Memory-Cache aktualisieren
        for (Map.Entry<String, int[]> entry : batchCounts.entrySet()) {
            tokenCache.put(entry.getKey(), new int[]{entry.getValue()[0], entry.getValue()[1]});
        }
        totalSpam = spamCount;
        totalHam = hamCount;
        vocabularySize = tokenCache.size();

        refreshModel();
        log.info("[SpamBayes] CSV-Bootstrap abgeschlossen: {} Spam, {} Ham, {} unique Tokens",
                spamCount, hamCount, batchCounts.size());
        return new int[]{spamCount, hamCount};
    }

    /**
     * Einfacher CSV-Parser der Komma-Trennung und Anführungszeichen unterstützt.
     */
    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++; // Escaped quote
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    // ═══════════════════════════════════════════════════════════════
    // STATUS
    // ═══════════════════════════════════════════════════════════════

    public long getTotalSpam() {
        return totalSpam;
    }

    public long getTotalHam() {
        return totalHam;
    }

    public int getVocabularySize() {
        return vocabularySize;
    }
}
