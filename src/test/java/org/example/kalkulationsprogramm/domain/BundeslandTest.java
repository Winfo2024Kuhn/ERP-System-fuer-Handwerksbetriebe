package org.example.kalkulationsprogramm.domain;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BundeslandTest {

    @Test
    void ausKuerzel() {
        assertEquals(Optional.of(Bundesland.NW), Bundesland.ausKuerzel(" nw "));
        assertEquals(Optional.of(Bundesland.TH), Bundesland.ausKuerzel("TH"));
        assertTrue(Bundesland.ausKuerzel(null).isEmpty());
        assertTrue(Bundesland.ausKuerzel("").isEmpty());
        assertTrue(Bundesland.ausKuerzel("Bayern").isEmpty());
    }

    @Test
    void anzeigenameUndApiSchluessel() {
        assertEquals(16, Bundesland.values().length);
        assertEquals("Baden-Württemberg", Bundesland.BW.getAnzeigename());
        assertEquals("by", Bundesland.BY.apiSchluessel());
        assertEquals(Bundesland.BY, Bundesland.STANDARD);
    }
}
