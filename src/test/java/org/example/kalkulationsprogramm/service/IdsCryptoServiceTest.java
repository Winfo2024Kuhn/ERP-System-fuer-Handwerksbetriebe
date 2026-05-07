package org.example.kalkulationsprogramm.service;

import java.util.Base64;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests für die symmetrische AES-GCM-Verschlüsselung von IDS-Connect-
 * Passwörtern. Wichtig: Round-Trip muss exakt sein, IV muss pro Aufruf
 * neu sein (Ciphertexte unterscheiden sich), unkonfigurierter Service
 * muss klar fehlschlagen statt still im Klartext zu speichern.
 */
class IdsCryptoServiceTest {

    /** 256-Bit-Test-Key (NICHT in Produktion verwenden). */
    private static final String TEST_KEY = Base64.getEncoder()
            .encodeToString(new byte[]{
                    1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
                    17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32});

    @Test
    void roundTripLiefertExaktenKlartextZurueck() {
        IdsCryptoService service = new IdsCryptoService(TEST_KEY);
        String klartext = "Wuerth2026.";
        String cipher = service.encrypt(klartext);
        assertNotNull(cipher);
        assertNotEquals(klartext, cipher);
        assertEquals(klartext, service.decrypt(cipher));
    }

    @Test
    void zweiVerschluesselungenLiefernUnterschiedlicheCiphertexte() {
        IdsCryptoService service = new IdsCryptoService(TEST_KEY);
        String klartext = "geheim";
        String c1 = service.encrypt(klartext);
        String c2 = service.encrypt(klartext);
        assertNotEquals(c1, c2, "Pro encrypt-Aufruf muss ein neuer IV verwendet werden");
        // Beide entschlüsseln aber zum gleichen Klartext
        assertEquals(klartext, service.decrypt(c1));
        assertEquals(klartext, service.decrypt(c2));
    }

    @Test
    void nullBleibtNull() {
        IdsCryptoService service = new IdsCryptoService(TEST_KEY);
        assertNull(service.encrypt(null));
        assertNull(service.decrypt(null));
        assertNull(service.decrypt(""));
        assertNull(service.decrypt("   "));
    }

    @Test
    void ohneKeyIstServiceNichtVerfuegbar() {
        IdsCryptoService service = new IdsCryptoService("");
        assertEquals(false, service.isAvailable());
        assertThrows(IllegalStateException.class, () -> service.encrypt("test"));
        assertThrows(IllegalStateException.class, () -> service.decrypt("xyz"));
    }

    @Test
    void zuKurzerKeyWirftSofortBeimAnlegen() {
        // 16 Bytes statt 32 → AES-128, wir wollen aber AES-256
        String tooShort = Base64.getEncoder().encodeToString(new byte[16]);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new IdsCryptoService(tooShort));
        assertTrue(ex.getMessage().contains("256-Bit"));
    }

    @Test
    void manipulierterCiphertextWirftBeimEntschluesseln() {
        IdsCryptoService service = new IdsCryptoService(TEST_KEY);
        String cipher = service.encrypt("klartext");
        // Letztes Zeichen ändern → AES-GCM-Tag schlägt fehl
        String manipuliert = cipher.substring(0, cipher.length() - 2) + "AA";
        // IllegalArgumentException, weil Eingabe vom Aufrufer kommt (semantisch 400 statt 500)
        assertThrows(IllegalArgumentException.class, () -> service.decrypt(manipuliert));
    }

    @Test
    void zuKurzerCiphertextWirftBeimEntschluesseln() {
        IdsCryptoService service = new IdsCryptoService(TEST_KEY);
        // Nur 8 Bytes (kleiner als IV-Länge 12) → muss klar abgelehnt werden
        String zuKurz = Base64.getEncoder().encodeToString(new byte[8]);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.decrypt(zuKurz));
        assertTrue(ex.getMessage().contains("zu kurz"));
    }

    @Test
    void ungueltigesBase64WirftBeimEntschluesseln() {
        IdsCryptoService service = new IdsCryptoService(TEST_KEY);
        assertThrows(IllegalArgumentException.class, () -> service.decrypt("!!!nicht-base64!!!"));
    }

    @Test
    void roundTripMitUmlautenLiefertExaktenKlartextZurueck() {
        IdsCryptoService service = new IdsCryptoService(TEST_KEY);
        // 4-Byte-UTF-8-Zeichen + Umlaute, um UTF-8-Korrektheit zu prüfen
        String klartext = "Größe: M16, Drehmoment 450 Nm — gepasswort😀";
        assertEquals(klartext, service.decrypt(service.encrypt(klartext)));
    }
}
