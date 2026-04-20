package org.example.kalkulationsprogramm.util;

import java.security.SecureRandom;
import java.util.function.Predicate;

/**
 * Erzeugt kurze, verwechslungsarme Tokens fuer Preisanfragen.
 * <p>
 * Alphabet: {@code A-Z} + {@code 2-9} (ohne {@code 0, O, 1, I}), damit Lieferanten
 * den Code auch handschriftlich aus dem PDF uebernehmen koennen, ohne Verwechslungen.
 * Bei 5 Zeichen ergeben sich ~60 Mio Kombinationen.
 */
public final class TokenGenerator {

    /** Alphabet ohne 0, O, 1, I. */
    public static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Maximale Anzahl Versuche, bis ein kollisionsfreier Token gefunden sein muss. */
    public static final int DEFAULT_MAX_RETRIES = 20;

    private TokenGenerator() {
    }

    /**
     * Erzeugt einen Token mit der gegebenen Laenge aus dem Alphabet {@link #ALPHABET}.
     * @param length Zeichenzahl, muss &gt;= 1 sein
     */
    public static String generate(int length) {
        if (length < 1) {
            throw new IllegalArgumentException("length muss >= 1 sein");
        }
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /**
     * Erzeugt einen Token und prueft gegen ein {@code existsCheck}-Praedikat,
     * das {@code true} zurueckgibt, wenn der Token bereits vergeben ist.
     * Wiederholt bis zu {@code maxRetries} Mal. Bei Erschoepfen der Versuche wird eine
     * {@link IllegalStateException} geworfen (sollte bei 5 Zeichen praktisch nie passieren).
     */
    public static String generateUnique(int length, Predicate<String> existsCheck, int maxRetries) {
        if (existsCheck == null) {
            throw new IllegalArgumentException("existsCheck darf nicht null sein");
        }
        if (maxRetries < 1) {
            throw new IllegalArgumentException("maxRetries muss >= 1 sein");
        }
        for (int i = 0; i < maxRetries; i++) {
            String candidate = generate(length);
            if (!existsCheck.test(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Konnte nach " + maxRetries + " Versuchen keinen kollisionsfreien Token erzeugen");
    }

    /** Kurzform mit {@link #DEFAULT_MAX_RETRIES}. */
    public static String generateUnique(int length, Predicate<String> existsCheck) {
        return generateUnique(length, existsCheck, DEFAULT_MAX_RETRIES);
    }
}
