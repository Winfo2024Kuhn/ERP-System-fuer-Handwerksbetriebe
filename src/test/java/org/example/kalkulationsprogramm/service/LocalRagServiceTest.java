package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.service.kihilfe.GeminiEinbetter;
import org.example.kalkulationsprogramm.service.kihilfe.RagIndexer;
import org.example.kalkulationsprogramm.service.kihilfe.RagIndexer.Abschnitt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LocalRagServiceTest {

    private final RagIndexer indexer = new RagIndexer(new ObjectMapper());
    private final GeminiEinbetter einbetter = mock(GeminiEinbetter.class);
    private final SystemSettingsService einstellungen = mock(SystemSettingsService.class);
    private LocalRagService service;

    @TempDir
    Path projekt;

    @TempDir
    Path image;

    @BeforeEach
    void setUp() {
        service = new LocalRagService(indexer, einbetter, einstellungen);
        ReflectionTestUtils.setField(service, "ragEnabled", true);
        ReflectionTestUtils.setField(service, "topK", 4);
        ReflectionTestUtils.setField(service, "scoreThreshold", 0.3);
        ReflectionTestUtils.setField(service, "projektOrdner", projekt.toString());
        ReflectionTestUtils.setField(service, "ragDatei", "");
    }

    /** Einheitsvektor in Richtung {@code achse} (plus etwas Richtung 0). */
    private static float[] richtung(int achse, float anteil) {
        float[] v = new float[RagIndexer.DIMENSIONEN];
        v[achse] = 1f;
        v[0] += anteil;
        return v;
    }

    private static Abschnitt abschnitt(String pfad, String kategorie, String name, float[] vektor) {
        return new Abschnitt("Inhalt von " + name, pfad, kategorie, "component", name, name + "-hash", vektor);
    }

    /** Server-Betrieb: kein Quellcode, nur die RAG-Datei aus dem Image. */
    private Path ragDateiImImage(List<Abschnitt> abschnitte) throws Exception {
        Path datei = image.resolve("ki-hilfe/rag-cache.json");
        indexer.speichern(datei, abschnitte);
        ReflectionTestUtils.setField(service, "ragDatei", datei.toString());
        return datei;
    }

    @Test
    @DisplayName("Server: laedt die RAG-Datei aus dem Image ohne Schluessel und ohne einzubetten")
    void serverLaedtRagDatei() throws Exception {
        ragDateiImImage(List.of(abschnitt("docs/RECHNUNGSWESEN.md", "documentation", "Rechnung", richtung(1, 0))));

        service.indexAufbauen();

        assertThat(service.isReady()).isTrue();
        assertThat(service.isAvailable()).isTrue();
        verifyNoInteractions(einstellungen);
        verify(einbetter, never()).einbetten(anyList());
    }

    @Test
    @DisplayName("Server ohne RAG-Datei: KI-Hilfe bleibt ohne Programmwissen, kein Fehler")
    void serverOhneRagDatei() throws Exception {
        ReflectionTestUtils.setField(service, "ragDatei", image.resolve("fehlt.json").toString());

        service.indexAufbauen();

        assertThat(service.isReady()).isFalse();
        assertThat(service.search("Wie schreibe ich eine Rechnung?", null, "/projekte")).isEmpty();
        verify(einbetter, never()).anfrageEinbetten(anyString());
    }

    @Test
    @DisplayName("Suche: aktuelle Seite zuerst, Backend-Klassen mit gleichem Namen zaehlen nicht als Seite")
    void sucheMitAktuellerSeite() throws Exception {
        ragDateiImImage(List.of(
                abschnitt("react-pc-frontend/src/pages/ProjektEditor.tsx", "frontend-page", "ProjektEditor", richtung(5, 0)),
                abschnitt("src/main/java/org/example/kalkulationsprogramm/service/ProjektEditorHilfe.java",
                        "backend-service", "ProjektEditorHilfe", richtung(6, 0)),
                abschnitt("docs/RECHNUNGSWESEN.md", "documentation", "Rechnung schreiben", richtung(1, 0.1f)),
                abschnitt("react-pc-frontend/src/components/RechnungDialog.tsx", "frontend-component", "RechnungDialog", richtung(1, 0.2f))));
        service.indexAufbauen();
        when(einbetter.anfrageEinbetten(anyString())).thenReturn(richtung(1, 0));

        List<LocalRagService.CodeChunkResult> treffer =
                service.search("Wie schreibe ich eine Rechnung?", "Seite: Projekte (/projekte)", "/projekte/42");

        assertThat(treffer).extracting(LocalRagService.CodeChunkResult::name)
                .containsExactly("ProjektEditor", "RechnungDialog", "Rechnung schreiben");
        assertThat(treffer.get(0).score()).isEqualTo(1.0);
        assertThat(treffer.get(1).score()).isLessThan(1.0);
        verify(einbetter).anfrageEinbetten("Wie schreibe ich eine Rechnung?\n\nSeitenkontext: Seite: Projekte (/projekte)");

        String kontext = service.buildContextFromResults(treffer);
        assertThat(kontext).contains(">>> AKTUELLE SEITE DES BENUTZERS <<<", "Score: ", "Inhalt von RechnungDialog");
        assertThat(service.buildContextFromResults(List.of())).isEmpty();
    }

    @Test
    @DisplayName("Grosse Seite: genommen wird der Teil, der zur Frage passt, nicht der Dateianfang")
    void seitenTeileNachAehnlichkeit() throws Exception {
        List<Abschnitt> teile = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            teile.add(abschnitt("react-pc-frontend/src/pages/ProjektEditor.tsx", "frontend-page",
                    "ProjektEditor.tsx (Teil " + i + "/8)", richtung(10 + i, 0)));
        }
        ragDateiImImage(teile);
        service.indexAufbauen();
        // Die Frage passt zu Teil 6 (Achse 16)
        when(einbetter.anfrageEinbetten(anyString())).thenReturn(richtung(16, 0));

        List<LocalRagService.CodeChunkResult> treffer = service.search("Wo ist der Knopf Rechnung erstellen?", null, "/projekte");

        assertThat(treffer).hasSize(1);
        assertThat(treffer.get(0).name()).isEqualTo("ProjektEditor.tsx (Teil 6/8)");
        assertThat(treffer.get(0).score()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("unsinnige Einstellung top-k=1: kein Fehler, genau ein Treffer")
    void topKEins() throws Exception {
        ReflectionTestUtils.setField(service, "topK", 1);
        ragDateiImImage(List.of(
                abschnitt("react-pc-frontend/src/pages/ProjektEditor.tsx", "frontend-page", "ProjektEditor", richtung(5, 0)),
                abschnitt("docs/A.md", "documentation", "A", richtung(1, 0))));
        service.indexAufbauen();
        when(einbetter.anfrageEinbetten(anyString())).thenReturn(richtung(1, 0));

        assertThat(service.search("Frage", null, "/projekte")).extracting(LocalRagService.CodeChunkResult::name)
                .containsExactly("ProjektEditor");
        assertThat(service.search("Frage", null, null)).extracting(LocalRagService.CodeChunkResult::name)
                .containsExactly("A");
    }

    @Test
    @DisplayName("Suche ohne Route nutzt nur die Aehnlichkeit")
    void sucheOhneRoute() throws Exception {
        ragDateiImImage(List.of(abschnitt("docs/A.md", "documentation", "A", richtung(1, 0))));
        service.indexAufbauen();
        when(einbetter.anfrageEinbetten(anyString())).thenReturn(richtung(1, 0));

        assertThat(service.search("Frage", null)).extracting(LocalRagService.CodeChunkResult::name).containsExactly("A");
        verify(einbetter).anfrageEinbetten("Frage");
    }

    @Test
    @DisplayName("Entwicklung: bettet den Quellcode ein und speichert die RAG-Datei")
    void entwicklungBettetEin() throws Exception {
        Files.createDirectories(projekt.resolve("docs"));
        Files.writeString(projekt.resolve("docs/ANLEITUNG.md"), "# Anleitung\nSo geht es.", StandardCharsets.UTF_8);
        when(einstellungen.getGeminiApiKey()).thenReturn("test-schluessel");
        when(einbetter.einbetten(anyList())).thenReturn(List.of(richtung(1, 0)));

        service.indexAufbauen();

        assertThat(service.isReady()).isTrue();
        Path datei = projekt.resolve(".rag-cache.json");
        assertThat(indexer.laden(datei)).hasSize(1);

        // Zweiter Start: nichts geaendert -> nichts neu einbetten
        service.indexAufbauen();
        verify(einbetter, times(1)).einbetten(anyList());
    }

    @Test
    @DisplayName("Entwicklung ohne Schluessel: RAG bleibt aus")
    void entwicklungOhneSchluessel() throws Exception {
        Files.createDirectories(projekt.resolve("docs"));
        Files.writeString(projekt.resolve("docs/ANLEITUNG.md"), "# Anleitung", StandardCharsets.UTF_8);
        when(einstellungen.getGeminiApiKey()).thenReturn(null);

        service.indexAufbauen();

        assertThat(service.isReady()).isFalse();
        verify(einbetter, never()).einbetten(anyList());
    }

    @Test
    @DisplayName("Entwicklung: Einbetten scheitert -> Teilstand, kein Absturz")
    void entwicklungEinbettenScheitert() throws Exception {
        Files.createDirectories(projekt.resolve("docs"));
        Files.writeString(projekt.resolve("docs/ANLEITUNG.md"), "# Anleitung", StandardCharsets.UTF_8);
        when(einstellungen.getGeminiApiKey()).thenReturn("test-schluessel");
        when(einbetter.einbetten(anyList())).thenThrow(new java.io.IOException("Gemini Embedding Fehler 403"));

        service.indexAufbauen();

        assertThat(service.isReady()).isFalse();
    }

    @Test
    @DisplayName("abgeschaltet: init laedt nichts")
    void abgeschaltet() {
        ReflectionTestUtils.setField(service, "ragEnabled", false);

        service.init();

        assertThat(service.isEnabled()).isFalse();
        assertThat(service.isAvailable()).isFalse();
        verifyNoInteractions(einstellungen);
    }

    @Test
    @DisplayName("init laedt im Hintergrund")
    void initImHintergrund() throws Exception {
        ragDateiImImage(List.of(abschnitt("docs/A.md", "documentation", "A", richtung(1, 0))));

        service.init();

        for (int i = 0; i < 100 && !service.isReady(); i++) Thread.sleep(50);
        assertThat(service.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("Route -> Seite: genaue Treffer, IDs und Unterseiten")
    void dateienZurRoute() {
        assertThat(LocalRagService.dateienZurRoute("/projekte")).containsExactly("ProjektEditor");
        assertThat(LocalRagService.dateienZurRoute("/projekte/42")).containsExactly("ProjektEditor");
        assertThat(LocalRagService.dateienZurRoute("/projekte/")).containsExactly("ProjektEditor");
        assertThat(LocalRagService.dateienZurRoute("/emails/inbox/17")).containsExactly("EmailCenter");
        assertThat(LocalRagService.dateienZurRoute("/telefon/anrufe")).containsExactly("TelefonPage");
        assertThat(LocalRagService.dateienZurRoute("/artikel")).containsExactly("ArtikelEditor");
        assertThat(LocalRagService.dateienZurRoute("/artikel/5")).containsExactly("ArtikelDetail");
        assertThat(LocalRagService.dateienZurRoute("/bestellungen/bedarf")).containsExactly("BestellungEditor");
        assertThat(LocalRagService.dateienZurRoute("/unbekannt")).isEmpty();
        assertThat(LocalRagService.dateienZurRoute("/")).isEmpty();
        assertThat(LocalRagService.dateienZurRoute(" ")).isEmpty();
        assertThat(LocalRagService.dateienZurRoute(null)).isEmpty();
        assertThat(LocalRagService.dateienZurRoute("/projekte" + "/".repeat(150))).containsExactly("ProjektEditor");
    }

    @Test
    @DisplayName("Route aus dem Chat-Request: ueberlange Eingaben kosten keine Rechenzeit (ReDoS)")
    void langeRoute() {
        String angriff = "/".repeat(100_000) + "x";

        assertTimeoutPreemptively(Duration.ofMillis(500),
                () -> assertThat(LocalRagService.dateienZurRoute(angriff)).isEmpty());
        assertThat(LocalRagService.dateienZurRoute("/projekte/" + "1".repeat(300))).isEmpty();
    }
}
