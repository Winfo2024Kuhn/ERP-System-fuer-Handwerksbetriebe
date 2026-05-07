package org.example.kalkulationsprogramm.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Symmetrische Verschlüsselung sensibler Konfigwerte (z. B. IDS-Connect-
 * Passwörter) mit AES-GCM. Master-Key kommt aus
 * {@code ids.encryption.key} (Base64-codierter 256-Bit-Schlüssel) in
 * {@code application-local.properties}. Ist die Property nicht gesetzt,
 * werfen die Methoden eine klare {@link IllegalStateException} statt
 * still im Klartext zu speichern.
 *
 * <p>Format des Outputs: Base64(IV[12] || Ciphertext+Tag). Pro Aufruf
 * ein frischer IV.</p>
 */
@Service
public class IdsCryptoService {

    private static final Logger log = LoggerFactory.getLogger(IdsCryptoService.class);
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec keySpec;
    private final SecureRandom random = new SecureRandom();

    public IdsCryptoService(@Value("${ids.encryption.key:}") String keyBase64) {
        if (keyBase64 == null || keyBase64.isBlank()) {
            this.keySpec = null;
            log.warn("ids.encryption.key nicht gesetzt – IDS-Konfig kann ohne Property nicht verschluesselt werden. "
                    + "Bitte einen 256-Bit-AES-Key (Base64) in application-local.properties hinterlegen.");
            return;
        }
        byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "ids.encryption.key muss ein 256-Bit-Schluessel sein (Base64-codiert, 32 Bytes nach Decode), aktuell "
                            + keyBytes.length + " Bytes.");
        }
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
    }

    public boolean isAvailable() {
        return keySpec != null;
    }

    /** Verschlüsselt einen Klartext-String. {@code null} bleibt {@code null}. */
    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        if (keySpec == null) {
            throw new IllegalStateException(
                    "IDS-Verschluesselung ist nicht konfiguriert (ids.encryption.key fehlt).");
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Verschluesselung fehlgeschlagen", e);
        }
    }

    /**
     * Entschlüsselt einen Cipher-Text. {@code null}/leer bleibt {@code null}.
     * Bei manipuliertem oder zu kurzem Ciphertext wird eine
     * {@link IllegalArgumentException} geworfen, weil die Eingabe vom Aufrufer
     * (potenziell User-nah) kommt – semantisch korrektes 400 statt 500.
     */
    public String decrypt(String ciphertextBase64) {
        if (ciphertextBase64 == null || ciphertextBase64.isBlank()) return null;
        if (keySpec == null) {
            throw new IllegalStateException(
                    "IDS-Verschluesselung ist nicht konfiguriert (ids.encryption.key fehlt).");
        }
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertextBase64);
            if (combined.length <= IV_LENGTH) {
                throw new IllegalArgumentException("Ciphertext zu kurz fuer AES-GCM");
            }
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            byte[] ciphertext = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, IV_LENGTH, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plain = cipher.doFinal(ciphertext);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Entschluesselung fehlgeschlagen", e);
        }
    }
}
