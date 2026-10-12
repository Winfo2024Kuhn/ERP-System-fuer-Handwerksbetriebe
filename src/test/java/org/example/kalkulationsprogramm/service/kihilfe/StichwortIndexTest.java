package org.example.kalkulationsprogramm.service.kihilfe;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class StichwortIndexTest {

    /** Index nur aus Inhalten, ohne Titel. */
    private static StichwortIndex index(String... inhalte) {
        return StichwortIndex.aus(java.util.Arrays.stream(inhalte).map(i -> new StichwortIndex.Text("", i)).toList());
    }

    @Test
    @DisplayName("Woerter: klein, Umlaute ausgeschrieben, CamelCase getrennt, ohne Fuellwoerter")
    void woerter() {
        assertThat(StichwortIndex.woerter("Wie öffne ich die Rechnungsübersicht?"))
                .containsExactly("oeffne", "rechnungsuebersicht");
        assertThat(StichwortIndex.woerter("ProjektEditor.tsx")).containsExactly("projekt", "editor", "tsx");
        assertThat(StichwortIndex.woerter("PDFExport für Größe")).containsExactly("pdf", "export", "groesse");
        assertThat(StichwortIndex.woerter("DIN EN 1090, Ausführungsklasse EXC2"))
                .containsExactly("din", "en", "1090", "ausfuehrungsklasse", "exc2");
        assertThat(StichwortIndex.woerter("ÄRGER_mit_ÖL")).containsExactly("aerger", "oel");
        assertThat(StichwortIndex.woerter("x " + "a".repeat(41) + " ok")).containsExactly("ok");
        assertThat(StichwortIndex.woerter(null)).isEmpty();
    }

    @Test
    @DisplayName("Stamm: Endungen weg, mindestens vier Buchstaben bleiben")
    void stamm() {
        assertThat(StichwortIndex.stamm("rechnungen")).isEqualTo("rechnung");
        assertThat(StichwortIndex.stamm("projekte")).isEqualTo("projekt");
        assertThat(StichwortIndex.stamm("storniere")).isEqualTo("stornier");
        assertThat(StichwortIndex.stamm("mitarbeiterinnen")).isEqualTo("mitarbeiter");
        assertThat(StichwortIndex.stamm("kunden")).isEqualTo("kund");
        assertThat(StichwortIndex.stamm("haus")).isEqualTo("haus");
        assertThat(StichwortIndex.stamm("pdf")).isEqualTo("pdf");
    }

    @Test
    @DisplayName("Suchstaemme: ohne Doppelte und hoechstens 24")
    void suchstaemme() {
        assertThat(StichwortIndex.suchstaemme("Rechnung Rechnungen rechnung")).containsExactly("rechnung");
        StringBuilder viele = new StringBuilder();
        for (int i = 0; i < 40; i++) viele.append("wort").append((char) ('a' + i % 26)).append(i).append(' ');
        assertThat(StichwortIndex.suchstaemme(viele.toString())).hasSize(StichwortIndex.MAX_SUCHWOERTER);
    }

    @Test
    @DisplayName("BM25: seltene, passende Woerter zaehlen mehr; Verlaengerungen werden gefunden")
    void suchen() {
        StichwortIndex index = index(
                "Projekt anlegen Projekt Projekt",
                "Rechnungsuebersicht mit allen Rechnungen",
                "Eine Rechnung stornieren: Knopf Stornieren",
                "Urlaub beantragen");

        assertThat(index.suchen("Wie storniere ich Rechnungen?", 10))
                .extracting(StichwortIndex.Treffer::position)
                .containsExactly(2, 1);
        assertThat(index.suchen("Urlaub", 10)).extracting(StichwortIndex.Treffer::position).containsExactly(3);
        assertThat(index.suchen("Rechnung", 1)).hasSize(1);
        assertThat(index.suchen("Rechnung", 10, position -> position != 2))
                .extracting(StichwortIndex.Treffer::position)
                .containsExactly(1);
    }

    @Test
    @DisplayName("Kurze Staemme muessen genau passen")
    void kurzeStaemmeGenau() {
        StichwortIndex index = index("PDF erzeugen", "pdfx Format", "Ust-Nummer pruefen", "Ustid fehlt");

        assertThat(index.suchen("pdf", 10)).extracting(StichwortIndex.Treffer::position).containsExactly(0);
        assertThat(index.suchen("ust", 10)).extracting(StichwortIndex.Treffer::position).containsExactly(2);
    }

    @Test
    @DisplayName("Ohne brauchbares Suchwort oder ohne Abschnitte: keine Treffer")
    void keineTreffer() {
        StichwortIndex index = index("Projekt anlegen");

        assertThat(index.suchen("Wie geht das?", 5)).isEmpty();
        assertThat(index.suchen("Lieferschein", 5)).isEmpty();
        assertThat(index.suchen(null, 5)).isEmpty();
        assertThat(index.suchen("Projekt", 0)).isEmpty();
        assertThat(index().suchen("Projekt", 5)).isEmpty();
    }

    @Test
    @DisplayName("Gleiche Punkte: fruehere Abschnitte zuerst (stabile Reihenfolge)")
    void stabileReihenfolge() {
        StichwortIndex index = index("Kunde", "Kunde", "Kunde");

        assertThat(index.suchen("Kunde", 5)).extracting(StichwortIndex.Treffer::position).containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("Riesige Anfrage aus dem Chat: schnell, kein ReDoS")
    void langeAnfrage() {
        StichwortIndex index = index("Projekt anlegen", "Rechnung stornieren");
        String angriff = "aA1-".repeat(50_000) + "(((((";

        assertTimeoutPreemptively(Duration.ofMillis(500), () -> index.suchen(angriff, 5));
    }

    @Test
    @DisplayName("Titel zaehlt mehr: die Seite selbst vor Texten, die das Wort nur oft erwaehnen")
    void titelZaehltMehr() {
        StichwortIndex index = StichwortIndex.aus(List.of(
                new StichwortIndex.Text("docs/API.md", "Lieferant Lieferant Lieferant Lieferant anlegen und mehr Text"),
                new StichwortIndex.Text("pages/LieferantenEditor.tsx", "Neuer Eintrag anlegen")));

        assertThat(index.suchen("Lieferant anlegen", 2)).extracting(StichwortIndex.Treffer::position)
                .containsExactly(1, 0);
    }
}
