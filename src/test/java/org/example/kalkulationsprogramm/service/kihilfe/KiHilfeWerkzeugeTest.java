package org.example.kalkulationsprogramm.service.kihilfe;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.example.kalkulationsprogramm.service.LocalRagService;
import org.example.kalkulationsprogramm.service.LocalRagService.Bereich;
import org.example.kalkulationsprogramm.service.LocalRagService.CodeChunkResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KiHilfeWerkzeugeTest {

    private static final String SEITE = "react-pc-frontend/src/pages/ProjektEditor.tsx";

    private final ObjectMapper objectMapper = JsonMapper.builderWithJackson2Defaults().build();
    private final LocalRagService rag = mock(LocalRagService.class);
    private final List<String> schritte = new ArrayList<>();
    private KiHilfeWerkzeuge werkzeuge;

    @BeforeEach
    void setUp() {
        werkzeuge = new KiHilfeWerkzeuge(rag, objectMapper, schritte::add);
    }

    private static CodeChunkResult abschnitt(String name, String inhalt) {
        return new CodeChunkResult(SEITE + "::" + name, inhalt, SEITE, "frontend-page", "component", name, false);
    }

    private JsonNode aufruf(String name, String args) throws Exception {
        return objectMapper.readTree("{\"id\":\"a1\",\"name\":\"" + name + "\",\"args\":" + args + "}");
    }

    @Test
    @DisplayName("Deklarationen: zwei Werkzeuge mit Pflichtfeldern und Bereichen")
    void deklarationen() {
        ArrayNode deklarationen = werkzeuge.deklarationen();

        assertThat(deklarationen).extracting(d -> d.path("name").asString(""))
                .containsExactly(KiHilfeWerkzeuge.SUCHEN, KiHilfeWerkzeuge.LESEN);
        JsonNode suchen = deklarationen.get(0).path("parameters");
        assertThat(suchen.path("required").toString()).contains("suchbegriffe", "hinweis");
        assertThat(suchen.path("properties").path("bereich").path("enum").toString())
                .contains("bedienung", "technik", "alles");
        assertThat(suchen.path("properties").path("bereich").path("format").asString("")).isEqualTo("enum");
        assertThat(deklarationen.get(1).path("parameters").path("required").toString()).contains("datei");
    }

    @Test
    @DisplayName("Suchen: meldet den Schritt, liefert Treffer und merkt sie sich")
    void suchen() throws Exception {
        werkzeuge.merkeGesehen(List.of(abschnitt("Vorher", "x")));
        when(rag.suchen(eq("Storno stornieren"), eq(Bereich.BEDIENUNG), eq(4), any()))
                .thenReturn(List.of(abschnitt("Teil 2", "<Button>Stornieren</Button>")));

        ObjectNode ergebnis = werkzeuge.ausfuehren(aufruf(KiHilfeWerkzeuge.SUCHEN,
                "{\"suchbegriffe\":\"Storno stornieren\",\"hinweis\":\"Wie man\\nstorniert\"}"));

        assertThat(schritte).containsExactly("Schaut nach: Wie man storniert");
        JsonNode treffer = ergebnis.path("treffer").get(0);
        assertThat(treffer.path("datei").asString("")).isEqualTo(SEITE);
        assertThat(treffer.path("abschnitt").asString("")).isEqualTo("Teil 2");
        assertThat(treffer.path("art").asString("")).startsWith("Bildschirm");
        assertThat(treffer.path("inhalt").asString("")).contains("Stornieren");

        // Zweite Suche bekommt beide als "schon gesehen"
        when(rag.suchen(anyString(), any(), anyInt(), any())).thenReturn(List.of());
        ObjectNode leer = werkzeuge.ausfuehren(aufruf(KiHilfeWerkzeuge.SUCHEN,
                "{\"suchbegriffe\":\"Gutschrift\",\"bereich\":\"technik\",\"hinweis\":\"\"}"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> gesehen = ArgumentCaptor.forClass(Set.class);
        verify(rag).suchen(eq("Gutschrift"), eq(Bereich.TECHNIK), eq(4), gesehen.capture());
        assertThat(gesehen.getValue()).containsExactlyInAnyOrder(SEITE + "::Vorher", SEITE + "::Teil 2");
        assertThat(leer.path("hinweis").asString("")).startsWith("Keine neuen Treffer");
        assertThat(schritte).last().isEqualTo("Schaut nach: Gutschrift");
    }

    @Test
    @DisplayName("Suchen: Bereich 'alles', ohne Suchbegriffe ein Hinweis statt Suche")
    void suchenBereicheUndLeer() throws Exception {
        when(rag.suchen(anyString(), any(), anyInt(), any())).thenReturn(List.of());

        werkzeuge.ausfuehren(aufruf(KiHilfeWerkzeuge.SUCHEN, "{\"suchbegriffe\":\"x\",\"bereich\":\" ALLES \"}"));
        verify(rag).suchen(eq("x"), eq(Bereich.ALLES), eq(4), any());

        ObjectNode ergebnis = werkzeuge.ausfuehren(aufruf(KiHilfeWerkzeuge.SUCHEN, "{\"suchbegriffe\":\"  \"}"));
        assertThat(ergebnis.path("hinweis").asString("")).isEqualTo("Bitte Suchbegriffe angeben.");
        assertThat(schritte).hasSize(1);
    }

    @Test
    @DisplayName("Suchen: Fehler der Suche geht als Hinweis an die KI, keine Exception")
    void suchenFehler() throws Exception {
        when(rag.suchen(anyString(), any(), anyInt(), any())).thenThrow(new IllegalStateException("kaputt"));

        ObjectNode ergebnis = werkzeuge.ausfuehren(aufruf(KiHilfeWerkzeuge.SUCHEN, "{\"suchbegriffe\":\"x\"}"));

        assertThat(ergebnis.path("hinweis").asString("")).startsWith("Die Suche ging gerade nicht");
    }

    @Test
    @DisplayName("Obergrenze: ist genug gelesen, wird nicht mehr gesucht")
    void budget() throws Exception {
        werkzeuge.merkeGesehen(List.of(abschnitt("Riesig", "x".repeat(KiHilfeWerkzeuge.MAX_ZEICHEN_GESAMT - 1_000))));

        ObjectNode suchen = werkzeuge.ausfuehren(aufruf(KiHilfeWerkzeuge.SUCHEN, "{\"suchbegriffe\":\"x\"}"));
        when(rag.dateiLesen(SEITE)).thenReturn(List.of(abschnitt("Teil 1", "a")));
        ObjectNode lesen = werkzeuge.ausfuehren(aufruf(KiHilfeWerkzeuge.LESEN, "{\"datei\":\"" + SEITE + "\"}"));

        assertThat(suchen.path("hinweis").asString("")).startsWith("Genug nachgeschlagen");
        assertThat(lesen.path("hinweis").asString("")).startsWith("Genug nachgeschlagen");
        verify(rag, never()).suchen(anyString(), any(), anyInt(), any());
        assertThat(schritte).isEmpty();
    }

    @Test
    @DisplayName("Obergrenze mitten in der Suche: zu grosse Treffer werden nicht mehr geliefert")
    void budgetBeimSuchen() throws Exception {
        werkzeuge.merkeGesehen(List.of(abschnitt("Viel", "x".repeat(KiHilfeWerkzeuge.MAX_ZEICHEN_GESAMT - 5_000))));
        when(rag.suchen(anyString(), any(), anyInt(), any()))
                .thenReturn(List.of(abschnitt("Gross", "y".repeat(6_000))));

        ObjectNode ergebnis = werkzeuge.ausfuehren(aufruf(KiHilfeWerkzeuge.SUCHEN, "{\"suchbegriffe\":\"y\"}"));

        assertThat(ergebnis.path("treffer")).isEmpty();
        assertThat(ergebnis.path("hinweis").asString("")).startsWith("Genug nachgeschlagen");
    }

    @Test
    @DisplayName("Lesen: ganze Datei in Reihenfolge, meldet den Schritt")
    void lesen() throws Exception {
        when(rag.dateiLesen(SEITE)).thenReturn(List.of(abschnitt("Teil 1", "Kopf"), abschnitt("Teil 2", "Knopf")));

        ObjectNode ergebnis = werkzeuge.ausfuehren(aufruf(KiHilfeWerkzeuge.LESEN,
                "{\"datei\":\" " + SEITE + " \",\"hinweis\":\"Die Seite Projekte\"}"));

        assertThat(ergebnis.path("inhalt").asString("")).isEqualTo("Kopf\n\nKnopf");
        assertThat(ergebnis.path("art").asString("")).startsWith("Bildschirm");
        assertThat(ergebnis.has("hinweis")).isFalse();
        assertThat(schritte).containsExactly("Liest genauer: Die Seite Projekte");
    }

    @Test
    @DisplayName("Lesen: zu lange Datei wird gekuerzt, mit Hinweis")
    void lesenGekuerzt() throws Exception {
        String teil = "z".repeat(6_000);
        when(rag.dateiLesen(SEITE)).thenReturn(List.of(abschnitt("1", teil), abschnitt("2", teil),
                abschnitt("3", teil), abschnitt("4", teil)));

        ObjectNode ergebnis = werkzeuge.ausfuehren(aufruf(KiHilfeWerkzeuge.LESEN, "{\"datei\":\"" + SEITE + "\"}"));

        assertThat(ergebnis.path("inhalt").asString("").length()).isLessThanOrEqualTo(KiHilfeWerkzeuge.MAX_ZEICHEN_DATEI);
        assertThat(ergebnis.path("hinweis").asString("")).contains(KiHilfeWerkzeuge.SUCHEN);
        assertThat(schritte).containsExactly("Liest genauer: eine Seite des Programms");
    }

    @Test
    @DisplayName("Lesen: unbekannte Datei oder Fehler -> Hinweis, kein Schritt")
    void lesenUnbekannt() throws Exception {
        when(rag.dateiLesen(anyString())).thenReturn(List.of());
        ObjectNode unbekannt = werkzeuge.ausfuehren(aufruf(KiHilfeWerkzeuge.LESEN, "{\"datei\":\"../../etc/passwd\"}"));
        assertThat(unbekannt.path("hinweis").asString("")).startsWith("Diese Datei gibt es");

        when(rag.dateiLesen(anyString())).thenThrow(new IllegalStateException("kaputt"));
        ObjectNode fehler = werkzeuge.ausfuehren(aufruf(KiHilfeWerkzeuge.LESEN, "{\"datei\":\"x\"}"));
        assertThat(fehler.path("hinweis").asString("")).startsWith("Das Lesen ging gerade nicht");
        assertThat(schritte).isEmpty();
    }

    @Test
    @DisplayName("Unbekanntes Werkzeug und zu viele Aufrufe: Hinweis an die KI")
    void unbekanntUndZuViele() throws Exception {
        assertThat(werkzeuge.ausfuehren(aufruf("rm_rf", "{}")).path("hinweis").asString(""))
                .contains(KiHilfeWerkzeuge.SUCHEN, KiHilfeWerkzeuge.LESEN);
        assertThat(werkzeuge.zuVieleAufrufe().path("hinweis").asString("")).startsWith("Zu viele Suchen");
    }

    @Test
    @DisplayName("Anzeigetext: eine Zeile, ohne Steuerzeichen, gekuerzt, sonst Ersatz")
    void anzeigeText() {
        assertThat(KiHilfeWerkzeuge.anzeigeText("  Wie\tman\u0000 eine\r\nRechnung\u200B storniert ", "x"))
                .isEqualTo("Wie man eine Rechnung storniert");
        assertThat(KiHilfeWerkzeuge.anzeigeText(" \n ", "Ersatz")).isEqualTo("Ersatz");
        assertThat(KiHilfeWerkzeuge.anzeigeText(null, "Ersatz")).isEqualTo("Ersatz");
        String lang = KiHilfeWerkzeuge.anzeigeText("a".repeat(10_000), "x");
        assertThat(lang).hasSize(KiHilfeWerkzeuge.MAX_HINWEIS).endsWith("…");
    }

    @Test
    @DisplayName("Lesen: schon bekannte Datei nicht noch einmal, Gelesenes zaehlt immer zum Budget")
    void lesenZaehltImmer() throws Exception {
        String teil = "q".repeat(15_000);
        when(rag.dateiLesen(SEITE)).thenReturn(List.of(abschnitt("1", teil), abschnitt("2", teil)));

        werkzeuge.ausfuehren(aufruf(KiHilfeWerkzeuge.LESEN, "{\"datei\":\"" + SEITE + "\"}"));
        werkzeuge.ausfuehren(aufruf(KiHilfeWerkzeuge.LESEN, "{\"datei\":\"" + SEITE + "\"}"));
        // Teil 2 kam nie (gekuerzt) - Lesen geht also weiter, aber nicht unbegrenzt
        for (int i = 0; i < 10; i++) werkzeuge.ausfuehren(aufruf(KiHilfeWerkzeuge.LESEN, "{\"datei\":\"" + SEITE + "\"}"));

        assertThat(werkzeuge.ausfuehren(aufruf(KiHilfeWerkzeuge.LESEN, "{\"datei\":\"" + SEITE + "\"}"))
                .path("hinweis").asString("")).startsWith("Genug nachgeschlagen");
        assertThat(schritte).hasSizeLessThanOrEqualTo(KiHilfeWerkzeuge.MAX_ZEICHEN_GESAMT / teil.length());

        when(rag.dateiLesen("docs/A.md")).thenReturn(List.of(new CodeChunkResult("docs/A.md::1", "kurz", "docs/A.md",
                "documentation", "document", "A", false)));
        KiHilfeWerkzeuge frisch = new KiHilfeWerkzeuge(rag, objectMapper, schritte::add);
        frisch.ausfuehren(aufruf(KiHilfeWerkzeuge.LESEN, "{\"datei\":\"docs/A.md\"}"));
        assertThat(frisch.ausfuehren(aufruf(KiHilfeWerkzeuge.LESEN, "{\"datei\":\"docs/A.md\"}"))
                .path("hinweis").asString("")).startsWith("Diese Datei kennst du schon");
    }

    @Test
    @DisplayName("Kuerzen halbiert kein Emoji")
    void kuerzenOhneHalbesEmoji() {
        String text = "a".repeat(KiHilfeWerkzeuge.MAX_HINWEIS - 2) + "😀😀😀";

        String gekuerzt = KiHilfeWerkzeuge.anzeigeText(text, "x");

        assertThat(gekuerzt).endsWith("…").hasSizeLessThanOrEqualTo(KiHilfeWerkzeuge.MAX_HINWEIS);
        assertThat(Character.isHighSurrogate(gekuerzt.charAt(gekuerzt.length() - 2))).isFalse();
    }
}
