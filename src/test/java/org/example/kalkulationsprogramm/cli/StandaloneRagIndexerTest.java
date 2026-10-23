package org.example.kalkulationsprogramm.cli;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.example.kalkulationsprogramm.service.kihilfe.RagIndexer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class StandaloneRagIndexerTest {

    private static final Map<String, String> MIT_KEY = Map.of("GEMINI_API_KEY", "test-schluessel");

    private final ObjectMapper objectMapper = JsonMapper.builderWithJackson2Defaults().build();
    private final ByteArrayOutputStream ausgabe = new ByteArrayOutputStream();
    private final AtomicInteger eingebettet = new AtomicInteger();
    private final List<String> genutzteSchluessel = new ArrayList<>();

    @TempDir
    Path projekt;

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(projekt.resolve("docs/benutzer"));
        Files.writeString(projekt.resolve("docs/benutzer/monatsabschluss.md"),
                "# Monatsabschluss\nKnopf: Monat jetzt abschliessen");
    }

    private int ausfuehren(Map<String, String> umgebung, String... args) {
        return StandaloneRagIndexer.ausfuehren(args, umgebung,
                new PrintStream(ausgabe, true, StandardCharsets.UTF_8), objectMapper, key -> {
                    genutzteSchluessel.add(key);
                    return texte -> {
                        eingebettet.addAndGet(texte.size());
                        List<float[]> vektoren = new ArrayList<>();
                        for (int i = 0; i < texte.size(); i++) vektoren.add(new float[RagIndexer.DIMENSIONEN]);
                        return vektoren;
                    };
                });
    }

    private String text() {
        return ausgabe.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("erzeugt die RAG-Datei und bettet beim zweiten Lauf nichts erneut ein")
    void erzeugtUndAktualisiert() {
        Path datei = projekt.resolve("ausgabe/rag-cache.json");

        assertThat(ausfuehren(MIT_KEY, projekt.toString(), datei.toString(), "--max-minuten", "15")).isZero();
        assertThat(Files.exists(datei)).isTrue();
        assertThat(eingebettet).hasValue(1);
        assertThat(genutzteSchluessel).containsExactly("test-schluessel");
        assertThat(text()).contains("Zeitlimit 15 min", "1 neu, 0 fehlen noch");

        assertThat(ausfuehren(MIT_KEY, projekt.toString(), datei.toString())).isZero();
        assertThat(eingebettet).hasValue(1);
        assertThat(text()).contains("1 unveraendert, 0 neu");
    }

    @Test
    @DisplayName("ohne Angabe der Datei landet sie als .rag-cache.json im Projektordner")
    void standardDatei() {
        assertThat(ausfuehren(MIT_KEY, projekt.toString())).isZero();

        assertThat(Files.exists(projekt.resolve(".rag-cache.json"))).isTrue();
    }

    @Test
    @DisplayName("ohne GEMINI_API_KEY bricht er ab, ohne etwas zu schreiben")
    void ohneSchluessel() {
        assertThat(ausfuehren(Map.of(), projekt.toString())).isEqualTo(StandaloneRagIndexer.AUFRUF);

        assertThat(text()).contains("GEMINI_API_KEY ist nicht gesetzt");
        assertThat(Files.exists(projekt.resolve(".rag-cache.json"))).isFalse();
    }

    @Test
    @DisplayName("--nur-zaehlen braucht keinen Schluessel und schreibt nichts")
    void nurZaehlen() {
        assertThat(ausfuehren(Map.of(), projekt.toString(), "--nur-zaehlen")).isZero();

        assertThat(text()).contains("documentation", "Gesamt: 1 Abschnitte", "1 davon neu einzubetten");
        assertThat(eingebettet).hasValue(0);
        assertThat(Files.exists(projekt.resolve(".rag-cache.json"))).isFalse();
    }

    @Test
    @DisplayName("falsche Aufrufe werden abgewiesen")
    void falscheAufrufe() {
        assertThat(ausfuehren(MIT_KEY, "--unbekannt")).isEqualTo(StandaloneRagIndexer.AUFRUF);
        assertThat(ausfuehren(MIT_KEY, "--max-minuten", "viele")).isEqualTo(StandaloneRagIndexer.AUFRUF);
        assertThat(ausfuehren(MIT_KEY, "--max-minuten", "0")).isEqualTo(StandaloneRagIndexer.AUFRUF);
        assertThat(text()).contains("Unbekannte Option", "erwartet eine Zahl ab 1");
    }

    @Test
    @DisplayName("ohne Doku und Quellcode gibt es nichts zu tun")
    void leererOrdner(@TempDir Path leer) {
        assertThat(ausfuehren(MIT_KEY, leer.toString())).isEqualTo(StandaloneRagIndexer.AUFRUF);

        assertThat(text()).contains("Keine Doku und kein Quellcode");
    }

    @Test
    @DisplayName("scheitert das Einbetten, meldet er Fehler und schreibt den Teilstand")
    void einbettenScheitert() {
        int code = StandaloneRagIndexer.ausfuehren(new String[]{projekt.toString()}, MIT_KEY,
                new PrintStream(ausgabe, true, StandardCharsets.UTF_8), objectMapper,
                key -> texte -> {
                    throw new IOException("Gemini Embedding Fehler 403");
                });

        assertThat(code).isEqualTo(StandaloneRagIndexer.FEHLER);
        assertThat(text()).contains("Einbetten fehlgeschlagen: Gemini Embedding Fehler 403");
        assertThat(Files.exists(projekt.resolve(".rag-cache.json"))).isTrue();
    }

    @Test
    @DisplayName("kann die RAG-Datei nicht geschrieben werden, ist das ein Fehler")
    void nichtSchreibbar() throws IOException {
        Path blockiert = projekt.resolve("blockiert");
        Files.writeString(blockiert, "eine Datei, kein Ordner");

        int code = ausfuehren(MIT_KEY, projekt.toString(), blockiert.resolve("rag-cache.json").toString());

        assertThat(code).isEqualTo(StandaloneRagIndexer.FEHLER);
        assertThat(text()).contains("RAG-Datei konnte nicht geschrieben werden");
    }
}
