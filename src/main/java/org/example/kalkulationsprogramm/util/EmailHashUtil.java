package org.example.kalkulationsprogramm.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Hilfsmethoden zum deterministischen Hashen von E-Mail-Adressen, z. B. für
 * anonymisierte Vergleiche oder Cache-Schlüssel.
 */
public final class EmailHashUtil {
    private EmailHashUtil() {}

    /** Normalisiert eine Adresse und liefert ihren SHA-256-Hash in Hexdarstellung. */
    public static String hashAddress(String address) {
        if (address == null) return null;
        String normalized = address.trim().toLowerCase();
        return sha256Hex(normalized);
    }

    /** Bildet den SHA-256-Hash einer Zeichenkette und gibt ihn als Hex-String zurück. */
    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append("%02x".formatted(b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}

