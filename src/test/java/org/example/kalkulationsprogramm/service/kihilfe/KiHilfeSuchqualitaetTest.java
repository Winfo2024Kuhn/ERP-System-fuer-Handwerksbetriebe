package org.example.kalkulationsprogramm.service.kihilfe;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Qualitaet der Wortsuche auf dem echten Quellcode: Finden typische Handwerker-Fragen ihre Seite?
 * <p>
 * Die Wortsuche ist die Haelfte der Hybrid-Suche und der Rueckfall, wenn das Einbetten bei Google
 * ausfaellt. Gemessen wird die Trefferquote unter den ersten {@value #PLAETZE} (Recall@10) ueber
 * alle Fragen - nicht jede Frage einzeln, damit eine neue Doku-Datei den Build nicht zufaellig
 * bricht. Faellt die Quote, ist die Suche schlechter geworden: Die Fehlermeldung nennt den Platz
 * jeder Frage.
 */
class KiHilfeSuchqualitaetTest {

    private static final int PLAETZE = 10;
    private static final double MIN_TREFFERQUOTE = 0.75;

    /** Frage -> Teil des Dateipfads, der unter den ersten Treffern sein soll. */
    private static final Map<String, String> FRAGEN = Map.of(
            "Wie storniere ich eine Rechnung?", "pages/ProjektEditor.tsx",
            "Wie mache ich den Monatsabschluss?", "docs/benutzer/monatsabschluss.md",
            "Wo genehmige ich einen Urlaubsantrag?", "pages/Urlaubsantraege.tsx",
            "Mahnung verschicken offene Posten", "pages/OffenePostenEditor.tsx",
            "Arbeitszeit buchen im Zeiterfassung Kalender", "pages/ZeiterfassungKalender.tsx",
            "Stundensatz ändern Arbeitszeitart", "pages/ArbeitszeitartEditor.tsx",
            "Feiertage für die Zeiterfassung eintragen", "pages/ZeiterfassungFeiertage.tsx",
            "Lieferanten bearbeiten", "pages/LieferantenEditor.tsx");

    @Test
    @DisplayName("Typische Fragen finden ihre Seite unter den ersten 10 Treffern")
    void trefferquote() {
        List<RagIndexer.Rohabschnitt> abschnitte = new RagIndexer(new ObjectMapper())
                .zerlegen(Path.of(System.getProperty("user.dir")));
        assertThat(abschnitte).as("Quellcode im Projektordner").hasSizeGreaterThan(500);
        StichwortIndex index = StichwortIndex.aus(abschnitte.stream()
                .map(a -> new StichwortIndex.Text(a.filePath() + " " + a.name(), a.content()))
                .toList());

        List<String> plaetze = new ArrayList<>();
        int verfehlt = 0;
        for (Map.Entry<String, String> fall : FRAGEN.entrySet()) {
            List<String> pfade = index.suchen(fall.getKey(), PLAETZE).stream()
                    .map(t -> abschnitte.get(t.position()).filePath())
                    .toList();
            int platz = pfade.stream().filter(p -> p.contains(fall.getValue())).findFirst().map(pfade::indexOf).orElse(-1);
            plaetze.add(fall.getKey() + " -> " + (platz < 0 ? "verfehlt (" + fall.getValue() + ")" : "Platz " + (platz + 1)));
            if (platz < 0) verfehlt++;
        }

        double quote = 1.0 - (double) verfehlt / FRAGEN.size();
        assertThat(quote).as("Trefferquote; Plaetze: %s", plaetze).isGreaterThanOrEqualTo(MIN_TREFFERQUOTE);
    }
}
