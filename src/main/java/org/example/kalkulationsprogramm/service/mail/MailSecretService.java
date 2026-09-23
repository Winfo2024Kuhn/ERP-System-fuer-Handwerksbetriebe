package org.example.kalkulationsprogramm.service.mail;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MailSecretService {
    private static final String PREFIX = "v1:";
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final byte[] key;

    public MailSecretService(@Value("${mail.credentials.encryption-key:}") String key) {
        this.key = key == null ? new byte[0] : key.getBytes(StandardCharsets.UTF_8);
    }

    public boolean isConfigured() {
        return key.length == 16 || key.length == 24 || key.length == 32;
    }

    public void ensureConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("Der Schlüssel für geschützte Mailzugänge ist nicht eingerichtet.");
        }
    }

    public String encrypt(String plaintext) {
        ensureConfigured();
        if (plaintext == null || plaintext.isEmpty()) return null;
        byte[] nonce = new byte[NONCE_LENGTH];
        RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            cipher.updateAAD(PREFIX.getBytes(StandardCharsets.US_ASCII));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, combined, 0, nonce.length);
            System.arraycopy(encrypted, 0, combined, nonce.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Mailzugang konnte nicht geschützt gespeichert werden.");
        }
    }

    public String decrypt(String cipherText) {
        ensureConfigured();
        if (cipherText == null || cipherText.isBlank()) return null;
        if (!cipherText.startsWith(PREFIX)) throw new IllegalStateException("Mailzugang ist nicht lesbar.");
        try {
            byte[] combined = Base64.getDecoder().decode(cipherText.substring(PREFIX.length()));
            if (combined.length <= NONCE_LENGTH + 16) throw new IllegalArgumentException();
            byte[] nonce = java.util.Arrays.copyOfRange(combined, 0, NONCE_LENGTH);
            byte[] encrypted = java.util.Arrays.copyOfRange(combined, NONCE_LENGTH, combined.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            cipher.updateAAD(PREFIX.getBytes(StandardCharsets.US_ASCII));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            throw new IllegalStateException("Mailzugang konnte nicht entschlüsselt werden.");
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Mailzugang konnte nicht entschlüsselt werden.");
        }
    }
}
