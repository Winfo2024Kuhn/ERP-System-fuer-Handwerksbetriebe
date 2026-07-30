package org.example.kalkulationsprogramm.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Das Frontend führt Präfix (YYYY/MM/) und Zähler getrennt, damit der Benutzer nur den
 * Zähler tippt. Der Controller schneidet den Zähler deshalb aus der fertigen Auftragsnummer
 * heraus — auch dann, wenn die Nummer aus Altbestand ein unerwartetes Format hat.
 */
class ProjektControllerAuftragsnummerTest {

    @Test
    @DisplayName("Zähler wird aus der Kunden-Auftragsnummer geschnitten")
    void schneidetZaehlerAusKundenAuftragsnummer() {
        assertEquals(300, ProjektController.zaehlerAusAuftragsnummer("2026/05/00300"));
    }

    @Test
    @DisplayName("Führende Nullen gehen beim Schneiden nicht verloren")
    void behaeltWertBeiFuehrendenNullen() {
        // 00001 → 1; das Frontend füllt beim Anzeigen wieder auf fünf Stellen auf.
        assertEquals(1, ProjektController.zaehlerAusAuftragsnummer("2026/05/00001"));
        assertEquals(99, ProjektController.zaehlerAusAuftragsnummer("2026/12/00099"));
    }

    @Test
    @DisplayName("Unerwartete Formate liefern 0 statt einer Exception")
    void liefertNullBeiUnerwartetemFormat() {
        assertEquals(0, ProjektController.zaehlerAusAuftragsnummer(null));
        assertEquals(0, ProjektController.zaehlerAusAuftragsnummer(""));
        assertEquals(0, ProjektController.zaehlerAusAuftragsnummer("ohne-slash"));
        assertEquals(0, ProjektController.zaehlerAusAuftragsnummer("2026/05/"));
        assertEquals(0, ProjektController.zaehlerAusAuftragsnummer("2026/05/ABCDE"));
    }
}
