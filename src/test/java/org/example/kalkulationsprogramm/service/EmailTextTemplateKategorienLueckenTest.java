package org.example.kalkulationsprogramm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.example.kalkulationsprogramm.domain.EmailTextTemplateKategorie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmailTextTemplateKategorienLueckenTest {

    @ParameterizedTest
    @ValueSource(strings = {"ANGEBOT", "NACHTRAGSANGEBOT", "AUFTRAGSBESTAETIGUNG", "ZEICHNUNG", "RECHNUNG",
            "TEILRECHNUNG", "ABSCHLAGSRECHNUNG", "SCHLUSSRECHNUNG", "GUTSCHRIFT", "STORNORECHNUNG"})
    @DisplayName("Dokumenttypen landen in DOKUMENT")
    void dokumente(String typ) {
        assertEquals(EmailTextTemplateKategorie.DOKUMENT, EmailTextTemplateKategorien.kategorieFuer(typ));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ZAHLUNGSERINNERUNG", "ERSTE_MAHNUNG", "ZWEITE_MAHNUNG", "MAHNUNG"})
    @DisplayName("Mahnstufen landen in MAHNWESEN")
    void mahnwesen(String typ) {
        assertEquals(EmailTextTemplateKategorie.MAHNWESEN, EmailTextTemplateKategorien.kategorieFuer(typ));
    }

    @Test
    @DisplayName("Webseiten-Bestaetigung landet in WEBSITE")
    void website() {
        assertEquals(EmailTextTemplateKategorie.WEBSITE,
                EmailTextTemplateKategorien.kategorieFuer("WEBSITE_ANFRAGE_BESTAETIGUNG"));
    }

    @Test
    @DisplayName("Eingabe wird getrimmt und gross geschrieben")
    void normalisierung() {
        assertEquals(EmailTextTemplateKategorie.DOKUMENT, EmailTextTemplateKategorien.kategorieFuer("  rechnung "));
    }

    @Test
    @DisplayName("null, leer, unbekannt und Injection-Strings fallen auf SYSTEM")
    void fallback() {
        assertEquals(EmailTextTemplateKategorie.SYSTEM, EmailTextTemplateKategorien.kategorieFuer(null));
        assertEquals(EmailTextTemplateKategorie.SYSTEM, EmailTextTemplateKategorien.kategorieFuer(""));
        assertEquals(EmailTextTemplateKategorie.SYSTEM, EmailTextTemplateKategorien.kategorieFuer("UNBEKANNT"));
        assertEquals(EmailTextTemplateKategorie.SYSTEM,
                EmailTextTemplateKategorien.kategorieFuer("'; DROP TABLE x; --"));
        assertEquals(EmailTextTemplateKategorie.SYSTEM,
                EmailTextTemplateKategorien.kategorieFuer("<script>alert(1)</script>"));
    }
}
