package org.example.kalkulationsprogramm.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmailHashUtilLueckenTest {

    @Test
    @DisplayName("null liefert null")
    void nullLiefertNull() {
        assertNull(EmailHashUtil.hashAddress(null));
    }

    @Test
    @DisplayName("Hash ist 64 Zeichen lang und hexadezimal")
    void hashFormat() {
        String h = EmailHashUtil.hashAddress("max.mustermann@example.com");
        assertEquals(64, h.length());
        assertTrue(h.matches("[0-9a-f]{64}"));
    }

    @Test
    @DisplayName("Gross-/Kleinschreibung und Leerraum werden normalisiert")
    void normalisiert() {
        assertEquals(EmailHashUtil.hashAddress("max@example.com"),
                EmailHashUtil.hashAddress("  MAX@Example.COM \n"));
    }

    @Test
    @DisplayName("Unterschiedliche Adressen ergeben unterschiedliche Hashes")
    void unterschiedlich() {
        assertNotEquals(EmailHashUtil.hashAddress("max@example.com"),
                EmailHashUtil.hashAddress("erika@example.com"));
    }

    @Test
    @DisplayName("Leerstring ergibt bekannten SHA-256 des leeren Strings")
    void leerString() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                EmailHashUtil.hashAddress("   "));
    }
}
