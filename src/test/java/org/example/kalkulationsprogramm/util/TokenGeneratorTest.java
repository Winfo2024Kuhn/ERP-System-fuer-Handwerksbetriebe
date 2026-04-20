package org.example.kalkulationsprogramm.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class TokenGeneratorTest {

    @Test
    void generate_hatRichtigeLaengeUndNutztNurErlaubtesAlphabet() {
        for (int i = 0; i < 200; i++) {
            String t = TokenGenerator.generate(5);
            assertEquals(5, t.length());
            for (char c : t.toCharArray()) {
                assertTrue(TokenGenerator.ALPHABET.indexOf(c) >= 0,
                        "Unerlaubtes Zeichen: " + c);
            }
            // Keine verwechselbaren Zeichen
            assertEquals(-1, t.indexOf('0'));
            assertEquals(-1, t.indexOf('O'));
            assertEquals(-1, t.indexOf('1'));
            assertEquals(-1, t.indexOf('I'));
        }
    }

    @Test
    void generate_zweiAufrufeSindMeistUnterschiedlich() {
        String a = TokenGenerator.generate(8);
        String b = TokenGenerator.generate(8);
        assertNotEquals(a, b);
    }

    @Test
    void generate_wirftBeiNegativerLaenge() {
        assertThrows(IllegalArgumentException.class, () -> TokenGenerator.generate(0));
    }

    @Test
    void generateUnique_wiederholtBisFrei() {
        Set<String> belegt = new HashSet<>();
        AtomicInteger aufrufe = new AtomicInteger();
        // blockiere die ersten 3 Kandidaten, danach muss Erfolg kommen
        String result = TokenGenerator.generateUnique(5, candidate -> {
            aufrufe.incrementAndGet();
            if (belegt.size() < 3) {
                belegt.add(candidate);
                return true; // blockiert
            }
            return false;
        }, 20);
        assertNotNull(result);
        assertEquals(5, result.length());
        assertTrue(aufrufe.get() >= 4, "Erwartete mindestens 4 Versuche, waren " + aufrufe.get());
    }

    @Test
    void generateUnique_wirftWennKollisionenErschoepft() {
        assertThrows(IllegalStateException.class,
                () -> TokenGenerator.generateUnique(5, candidate -> true, 3));
    }

    @Test
    void generateUnique_wirftBeiNullPredicate() {
        assertThrows(IllegalArgumentException.class,
                () -> TokenGenerator.generateUnique(5, null, 10));
    }
}
