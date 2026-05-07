package org.example.kalkulationsprogramm.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests für die robuste Kategorie-Parsing-Logik. Ein Mobile-Bug oder
 * ein älterer Client darf das EN-1090-Bautagebuch nicht mit HTTP 400
 * blockieren – stattdessen fällt der Wert still auf ALLGEMEIN zurück.
 */
class ProjektNotizKategorieTest {

    @Test
    void nullWirdZuAllgemein() {
        assertEquals(ProjektNotizKategorie.ALLGEMEIN,
                ProjektNotizKategorie.fromStringOrDefault(null));
    }

    @Test
    void leerstringWirdZuAllgemein() {
        assertEquals(ProjektNotizKategorie.ALLGEMEIN,
                ProjektNotizKategorie.fromStringOrDefault(""));
    }

    @Test
    void nurWhitespaceWirdZuAllgemein() {
        assertEquals(ProjektNotizKategorie.ALLGEMEIN,
                ProjektNotizKategorie.fromStringOrDefault("   "));
    }

    @Test
    void gueltigerWertWirdGeparst() {
        assertEquals(ProjektNotizKategorie.VERBINDUNGSMITTEL,
                ProjektNotizKategorie.fromStringOrDefault("VERBINDUNGSMITTEL"));
    }

    @Test
    void kleinschreibungWirdNormalisiert() {
        assertEquals(ProjektNotizKategorie.SCHWEISSUNG,
                ProjektNotizKategorie.fromStringOrDefault("schweissung"));
    }

    @Test
    void mitWhitespaceWirdGetrimmt() {
        assertEquals(ProjektNotizKategorie.KORROSIONSSCHUTZ,
                ProjektNotizKategorie.fromStringOrDefault("  Korrosionsschutz  "));
    }

    @Test
    void unbekannterWertFaelltAufAllgemeinZurueck() {
        // Ein Mobile-Tippfehler darf das Bautagebuch nicht blockieren
        assertEquals(ProjektNotizKategorie.ALLGEMEIN,
                ProjektNotizKategorie.fromStringOrDefault("FOO_BAR"));
    }

    @Test
    void szInSchweissungWirdViaToUpperCaseToleriert() {
        // Java-Spezialfall: "Schweißung".toUpperCase() liefert "SCHWEISSUNG",
        // weil ß zu SS expandiert wird. Praktisch: Mobile-Tippfehler ist tolerant.
        assertEquals(ProjektNotizKategorie.SCHWEISSUNG,
                ProjektNotizKategorie.fromStringOrDefault("Schweißung"));
    }

    @Test
    void deutscherFreitextFaelltAufAllgemeinZurueck() {
        // Echter Freitext mit Umlauten passt auf keinen Enum-Wert
        assertEquals(ProjektNotizKategorie.ALLGEMEIN,
                ProjektNotizKategorie.fromStringOrDefault("Schraubenanzug überprüft"));
    }
}
