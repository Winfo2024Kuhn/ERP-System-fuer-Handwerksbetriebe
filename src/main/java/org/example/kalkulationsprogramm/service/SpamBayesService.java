package org.example.kalkulationsprogramm.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.SpamModelStats;
import org.example.kalkulationsprogramm.repository.SpamModelStatsRepository;
import org.example.kalkulationsprogramm.repository.SpamTokenCountRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Multinomial Naive Bayes Spam-Klassifikator.
 *
 * Lernt supervised aus User-Feedback (mark-spam / mark-not-spam)
 * und kann mit CSV-Daten (z.B. Kaggle SMS Spam Collection) vortrainiert werden.
 *
 * Token-Frequenzen werden in MySQL persistiert und beim Start in einen
 * In-Memory-Cache geladen für schnelle Prediction.
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

    /** Laplace-Smoothing Parameter (verhindert Zero-Probability). */
    private static final double LAPLACE_ALPHA = 1.0;

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
    private volatile long totalSpamTokens = 0;
    private volatile long totalHamTokens = 0;
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
            long spamTokenSum = 0;
            long hamTokenSum = 0;

            var allTokens = tokenRepo.findAll();
            for (var tc : allTokens) {
                newCache.put(tc.getToken(), new int[]{tc.getSpamCount(), tc.getHamCount()});
                spamTokenSum += tc.getSpamCount();
                hamTokenSum += tc.getHamCount();
            }

            // Atomisch swappen
            this.tokenCache = newCache;
            this.totalSpamTokens = spamTokenSum;
            this.totalHamTokens = hamTokenSum;
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
     */
    public Set<String> tokenize(Email email) {
        String body = combineBody(email);
        return tokenize(email.getSubject(), body);
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
     */
    @Transactional
    public void train(Email email, boolean isSpam) {
        Set<String> tokens = tokenize(email);
        trainTokens(tokens, isSpam);
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
            totalSpamTokens += tokens.size();
        } else {
            totalHam++;
            totalHamTokens += tokens.size();
        }
        vocabularySize = tokenCache.size();
    }

    // ═══════════════════════════════════════════════════════════════
    // PREDICTION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Berechnet die Spam-Wahrscheinlichkeit für gegebene Tokens.
     *
     * @return Wahrscheinlichkeit 0.0-1.0, oder -1.0 wenn Modell nicht bereit
     */
    public double predict(Set<String> tokens) {
        if (!isModelReady() || tokens.isEmpty()) {
            return -1.0;
        }

        long totalDocs = totalSpam + totalHam;
        double logPriorSpam = Math.log((double) totalSpam / totalDocs);
        double logPriorHam = Math.log((double) totalHam / totalDocs);

        double logLikelihoodSpam = 0.0;
        double logLikelihoodHam = 0.0;

        int vocabSize = Math.max(vocabularySize, 1);

        for (String token : tokens) {
            int[] counts = tokenCache.get(token);
            int sc = (counts != null) ? counts[0] : 0;
            int hc = (counts != null) ? counts[1] : 0;

            // Multinomial Naive Bayes mit Laplace-Smoothing
            logLikelihoodSpam += Math.log((sc + LAPLACE_ALPHA) / (totalSpamTokens + LAPLACE_ALPHA * vocabSize));
            logLikelihoodHam += Math.log((hc + LAPLACE_ALPHA) / (totalHamTokens + LAPLACE_ALPHA * vocabSize));
        }

        double logSpam = logPriorSpam + logLikelihoodSpam;
        double logHam = logPriorHam + logLikelihoodHam;

        // Log-Sum-Exp-Trick für numerische Stabilität
        double maxLog = Math.max(logSpam, logHam);
        double expSpam = Math.exp(logSpam - maxLog);
        double expHam = Math.exp(logHam - maxLog);

        return expSpam / (expSpam + expHam);
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
