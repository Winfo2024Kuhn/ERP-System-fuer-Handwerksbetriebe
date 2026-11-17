package org.example.kalkulationsprogramm.service.dokumentstand;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DokumentStandVergleichTest {

    private static DokumentStandInhalt inhalt(String positionenJson) {
        return new DokumentStandInhalt("Geländer Musterstraße", null, new BigDecimal("0.19"), 14, null, positionenJson);
    }

    private static String service(String id, String pos, String titel, double menge, String einheit, double preis) {
        return "{\"id\":\"" + id + "\",\"type\":\"SERVICE\",\"pos\":\"" + pos + "\",\"title\":\"" + titel
                + "\",\"quantity\":" + menge + ",\"unit\":\"" + einheit + "\",\"price\":" + preis + "}";
    }

    private static String json(String... bloecke) {
        return "{\"blocks\":[" + String.join(",", bloecke) + "]}";
    }

    @Test
    @DisplayName("Ohne vorigen Stand: „Erste Fassung“")
    void ersteFassung() {
        assertThat(DokumentStandVergleich.aenderungen(null, inhalt(json())))
                .containsExactly("Erste Fassung");
    }

    @Test
    @DisplayName("Gleicher Inhalt: keine Änderungen")
    void gleicherInhalt() {
        String j = json(service("a", "1", "Geländer", 2, "Stk", 120));
        assertThat(DokumentStandVergleich.aenderungen(inhalt(j), inhalt(j))).isEmpty();
    }

    @Test
    @DisplayName("Position gelöscht und hinzugefügt")
    void geloeschtUndHinzugefuegt() {
        String alt = json(service("a", "1", "Tor", 1, "Stk", 100), service("b", "3", "Geländer Edelstahl", 1, "Stk", 50));
        String neu = json(service("a", "1", "Tor", 1, "Stk", 100), service("c", "2", "Handlauf", 1, "Stk", 50));
        assertThat(DokumentStandVergleich.aenderungen(inhalt(alt), inhalt(neu)))
                .contains("Pos. 3 „Geländer Edelstahl“ gelöscht", "Pos. 2 „Handlauf“ hinzugefügt");
    }

    @Test
    @DisplayName("Preis, Menge, Rabatt, Bezeichnung und Beschreibung einer Position")
    void felderEinerPosition() {
        String alt = json("{\"id\":\"a\",\"type\":\"SERVICE\",\"pos\":\"2\",\"title\":\"<b>alt</b>\",\"quantity\":2,"
                + "\"unit\":\"Stk\",\"price\":120,\"description\":\"<p>eins</p>\"}");
        String neu = json("{\"id\":\"a\",\"type\":\"SERVICE\",\"pos\":\"2\",\"title\":\"neu\",\"quantity\":3,"
                + "\"unit\":\"Stk\",\"price\":95,\"discount\":10,\"description\":\"<p>zwei</p>\"}");
        assertThat(DokumentStandVergleich.aenderungen(inhalt(alt), inhalt(neu))).containsExactly(
                "Bezeichnung Pos. 2: „alt“ → „neu“",
                "Menge Pos. 2: 2 → 3 Stk",
                "Preis Pos. 2: 120,00 € → 95,00 €",
                "Rabatt Pos. 2: 0 % → 10 %",
                "Beschreibung Pos. 2 geändert");
    }

    @Test
    @DisplayName("Kommazahlen, Tausenderpunkt und Einheitswechsel")
    void zahlenformate() {
        String alt = json(service("a", "1", "Rohr", 2.5, "m", 1234.5));
        String neu = json(service("a", "1", "Rohr", 3.75, "lfm", 1000));
        assertThat(DokumentStandVergleich.aenderungen(inhalt(alt), inhalt(neu))).containsExactly(
                "Menge Pos. 1: 2,5 → 3,75 lfm",
                "Einheit Pos. 1: „m“ → „lfm“",
                "Preis Pos. 1: 1.234,50 € → 1.000,00 €");
    }

    @Test
    @DisplayName("Wahlposition an und aus")
    void wahlposition() {
        String normal = json(service("a", "4", "Vordach", 1, "Stk", 10));
        String wahl = json("{\"id\":\"a\",\"type\":\"SERVICE\",\"pos\":\"4\",\"title\":\"Vordach\",\"quantity\":1,"
                + "\"unit\":\"Stk\",\"price\":10,\"optional\":true}");
        assertThat(DokumentStandVergleich.aenderungen(inhalt(normal), inhalt(wahl)))
                .containsExactly("Pos. 4 ist jetzt Wahlposition");
        assertThat(DokumentStandVergleich.aenderungen(inhalt(wahl), inhalt(normal)))
                .containsExactly("Pos. 4 ist keine Wahlposition mehr");
    }

    @Test
    @DisplayName("Gesamtrabatt gegeben, geändert, entfernt")
    void gesamtrabatt() {
        String ohne = "{\"blocks\":[]}";
        String fuenf = "{\"blocks\":[],\"globalRabatt\":5}";
        String drei = "{\"blocks\":[],\"globalRabatt\":3}";
        assertThat(DokumentStandVergleich.aenderungen(inhalt(ohne), inhalt(fuenf))).containsExactly("Rabatt 5 % gegeben");
        assertThat(DokumentStandVergleich.aenderungen(inhalt(fuenf), inhalt(drei))).containsExactly("Rabatt 5 % → 3 %");
        assertThat(DokumentStandVergleich.aenderungen(inhalt(fuenf), inhalt(ohne))).containsExactly("Rabatt 5 % entfernt");
    }

    @Test
    @DisplayName("Bauabschnitte: hinzugefügt, gelöscht, umbenannt, Kinder werden verglichen")
    void bauabschnitte() {
        String alt = json("{\"id\":\"s1\",\"type\":\"SECTION_HEADER\",\"sectionLabel\":\"A\",\"children\":["
                + service("k1", "1.1", "Stütze", 1, "Stk", 80) + "]}",
                "{\"id\":\"s2\",\"type\":\"SECTION_HEADER\",\"sectionLabel\":\"Rohbau\",\"children\":[]}");
        String neu = json("{\"id\":\"s1\",\"type\":\"SECTION_HEADER\",\"sectionLabel\":\"B\",\"children\":["
                + service("k1", "1.1", "Stütze", 1, "Stk", 90) + "]}",
                "{\"id\":\"s3\",\"type\":\"SECTION_HEADER\",\"sectionLabel\":\"Ausbau\",\"children\":[]}");
        assertThat(DokumentStandVergleich.aenderungen(inhalt(alt), inhalt(neu))).containsExactly(
                "Bauabschnitt umbenannt: „A“ → „B“",
                "Bauabschnitt „Rohbau“ gelöscht",
                "Bauabschnitt „Ausbau“ hinzugefügt",
                "Preis Pos. 1.1: 80,00 € → 90,00 €");
    }

    @Test
    @DisplayName("Textblöcke werden zusammengefasst")
    void texte() {
        String alt = json("{\"id\":\"t1\",\"type\":\"TEXT\",\"content\":\"<p>Hallo</p>\"}",
                "{\"id\":\"t2\",\"type\":\"TEXT\",\"content\":\"<p>Gruß</p>\"}",
                "{\"id\":\"t3\",\"type\":\"TEXT\",\"content\":\"weg\"}");
        String neu = json("{\"id\":\"t1\",\"type\":\"TEXT\",\"content\":\"<p>Hallo Max</p>\"}",
                "{\"id\":\"t2\",\"type\":\"TEXT\",\"content\":\"<p>Viele Grüße</p>\"}",
                "{\"id\":\"t4\",\"type\":\"TEXT\",\"content\":\"neu\"}",
                "{\"id\":\"t5\",\"type\":\"TEXT\",\"content\":\"neu2\"}");
        assertThat(DokumentStandVergleich.aenderungen(inhalt(alt), inhalt(neu)))
                .containsExactly("Text hinzugefügt", "Text geändert", "Text gelöscht");
    }

    @Test
    @DisplayName("Reihenfolge geändert nur bei gleichen Positionen in anderer Ordnung")
    void reihenfolge() {
        String alt = json(service("a", "1", "Tor", 1, "Stk", 1), service("b", "2", "Zaun", 1, "Stk", 1));
        String neu = json(service("b", "2", "Zaun", 1, "Stk", 1), service("a", "1", "Tor", 1, "Stk", 1));
        assertThat(DokumentStandVergleich.aenderungen(inhalt(alt), inhalt(neu)))
                .containsExactly("Reihenfolge der Positionen geändert");
    }

    @Test
    @DisplayName("Kopfdaten: Betreff, Zahlungsziel, Rechnungsadresse, MwSt")
    void kopfdaten() {
        DokumentStandInhalt alt = new DokumentStandInhalt("alt", null, new BigDecimal("0.19"), 14, "Max Mustermann", "{}");
        DokumentStandInhalt neu = new DokumentStandInhalt("neu", null, new BigDecimal("0.07"), 30, "Erika Mustermann", "{}");
        assertThat(DokumentStandVergleich.aenderungen(alt, neu)).containsExactly(
                "Betreff: „alt“ → „neu“",
                "Zahlungsziel: 14 → 30 Tage",
                "MwSt.: 19 % → 7 %",
                "Rechnungsadresse geändert");
    }

    @Test
    @DisplayName("Summe netto steht als letzte Zeile")
    void summeNettoZuletzt() {
        DokumentStandInhalt alt = new DokumentStandInhalt("x", new BigDecimal("1200.00"), null, null, null,
                json(service("a", "1", "Tor", 1, "Stk", 1200)));
        DokumentStandInhalt neu = new DokumentStandInhalt("x", new BigDecimal("1140"), null, null, null,
                "{\"blocks\":[" + service("a", "1", "Tor", 1, "Stk", 1200) + "],\"globalRabatt\":5}");
        assertThat(DokumentStandVergleich.aenderungen(alt, neu))
                .containsExactly("Rabatt 5 % gegeben", "Summe netto: 1.200,00 € → 1.140,00 €");
    }

    @Test
    @DisplayName("Mehr als 30 Sätze: Rest zusammengefasst, Summe bleibt letzte Zeile")
    void hoechstensDreissig() {
        String neuBloecke = IntStream.rangeClosed(1, 37)
                .mapToObj(i -> service("p" + i, String.valueOf(i), "Teil " + i, 1, "Stk", 1))
                .collect(Collectors.joining(","));
        DokumentStandInhalt alt = new DokumentStandInhalt("x", BigDecimal.ZERO, null, null, null, json());
        DokumentStandInhalt neu = new DokumentStandInhalt("x", new BigDecimal("37"), null, null, null,
                "{\"blocks\":[" + neuBloecke + "]}");
        List<String> saetze = DokumentStandVergleich.aenderungen(alt, neu);
        assertThat(saetze).hasSize(32);
        assertThat(saetze.get(29)).isEqualTo("Pos. 30 „Teil 30“ hinzugefügt");
        assertThat(saetze.get(30)).isEqualTo("… und 7 weitere Änderungen");
        assertThat(saetze.get(31)).isEqualTo("Summe netto: 0,00 € → 37,00 €");
    }

    @Test
    @DisplayName("Lange Titel werden auf 60 Zeichen gekürzt")
    void langeTitel() {
        String lang = "A".repeat(80);
        List<String> saetze = DokumentStandVergleich.aenderungen(inhalt(json()),
                inhalt(json(service("a", "1", lang, 1, "Stk", 1))));
        assertThat(saetze).containsExactly("Pos. 1 „" + "A".repeat(59) + "…“ hinzugefügt");
    }

    @Test
    @DisplayName("Ohne pos: laufende Nummer der Leistung")
    void laufendeNummer() {
        String alt = json("{\"id\":\"t\",\"type\":\"TEXT\",\"content\":\"x\"}",
                "{\"id\":\"a\",\"type\":\"SERVICE\",\"title\":\"Tor\",\"price\":1}",
                "{\"id\":\"b\",\"type\":\"SERVICE\",\"title\":\"Zaun\",\"price\":1}");
        String neu = json("{\"id\":\"t\",\"type\":\"TEXT\",\"content\":\"x\"}",
                "{\"id\":\"a\",\"type\":\"SERVICE\",\"title\":\"Tor\",\"price\":1}");
        assertThat(DokumentStandVergleich.aenderungen(inhalt(alt), inhalt(neu)))
                .containsExactly("Pos. 2 „Zaun“ gelöscht");
    }

    @Test
    @DisplayName("Altes Array-Format wird gelesen")
    void legacyArray() {
        String alt = "[" + service("a", "1", "Tor", 1, "Stk", 10) + "]";
        String neu = "[" + service("a", "1", "Tor", 1, "Stk", 12) + "]";
        assertThat(DokumentStandVergleich.aenderungen(inhalt(alt), inhalt(neu)))
                .containsExactly("Preis Pos. 1: 10,00 € → 12,00 €");
    }

    @Test
    @DisplayName("Kaputtes JSON wirft keine Ausnahme")
    void kaputtesJson() {
        assertThat(DokumentStandVergleich.aenderungen(inhalt("{kaputt"), inhalt(json(service("a", "1", "Tor", 1, "Stk", 1)))))
                .containsExactly("Pos. 1 „Tor“ hinzugefügt");
        assertThat(DokumentStandVergleich.aenderungen(inhalt("{kaputt"), inhalt("[nochmal kaputt")))
                .containsExactly("Positionen geändert");
        assertThat(DokumentStandVergleich.aenderungen(inhalt(null), inhalt(null))).isEmpty();
    }

    @Test
    @DisplayName("Nur Gestaltung geändert (z. B. Schriftgröße): ein allgemeiner Satz statt leerer Liste")
    void nurGestaltung() {
        String alt = json("{\"id\":\"a\",\"type\":\"SERVICE\",\"pos\":\"1\",\"title\":\"Tor\",\"price\":1}");
        String neu = json("{\"id\":\"a\",\"type\":\"SERVICE\",\"pos\":\"1\",\"title\":\"Tor\",\"price\":1,\"fett\":true}");
        assertThat(DokumentStandVergleich.aenderungen(inhalt(alt), inhalt(neu)))
                .containsExactly("Gestaltung geändert");
    }

    @Test
    @DisplayName("XSS in Titeln landet als Klartext ohne Tags")
    void xssImTitel() {
        List<String> saetze = DokumentStandVergleich.aenderungen(inhalt(json()),
                inhalt(json(service("a", "1", "<script>alert(1)</script>Tor", 1, "Stk", 1))));
        assertThat(saetze).singleElement().satisfies(s -> assertThat(s).doesNotContain("<script>"));
    }
}
