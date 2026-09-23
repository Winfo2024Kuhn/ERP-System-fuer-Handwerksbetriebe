package org.example.kalkulationsprogramm.service.mail;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MailSecretServiceTest {

    @Test
    void verschluesseltMitZufaelligemNonceUndEntschluesseltWieder() {
        MailSecretService service = new MailSecretService("0123456789abcdef0123456789abcdef");

        String first = service.encrypt("geheimes-passwort");
        String second = service.encrypt("geheimes-passwort");

        assertNotEquals(first, second);
        assertFalse(first.contains("geheimes-passwort"));
        assertEquals("geheimes-passwort", service.decrypt(first));
    }

    @Test
    void verweigertSpeichernOhneSchluesselUndVerraetFehlerursacheNicht() {
        MailSecretService service = new MailSecretService("");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.encrypt("geheimes-passwort"));

        assertFalse(error.getMessage().contains("geheimes-passwort"));
    }

    @Test
    void authentifiziertCiphertextUndMeldetManipulationOhneSecret() {
        MailSecretService service = new MailSecretService("0123456789abcdef0123456789abcdef");
        String cipherText = service.encrypt("geheimes-passwort");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.decrypt(cipherText.substring(0, cipherText.length() - 2) + "AA"));

        assertFalse(error.getMessage().contains("geheimes-passwort"));
    }
}
