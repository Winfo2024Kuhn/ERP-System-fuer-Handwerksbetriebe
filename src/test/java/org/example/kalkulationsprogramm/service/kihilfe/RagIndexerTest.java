package org.example.kalkulationsprogramm.service.kihilfe;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.example.kalkulationsprogramm.service.kihilfe.RagIndexer.Abschnitt;
import org.example.kalkulationsprogramm.service.kihilfe.RagIndexer.Ergebnis;
import org.example.kalkulationsprogramm.service.kihilfe.RagIndexer.Rohabschnitt;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class RagIndexerTest {

    private final ObjectMapper objectMapper = JsonMapper.builderWithJackson2Defaults().build();
    private final RagIndexer indexer = new RagIndexer(objectMapper);

    @TempDir
    Path projekt;

    private void datei(String pfad, String inhalt) throws IOException {
        Path ziel = projekt.resolve(pfad);
        Files.createDirectories(ziel.getParent());
        Files.writeString(ziel, inhalt);
    }

    private static float[] vektor(float wert) {
        float[] v = new float[RagIndexer.DIMENSIONEN];
        v[0] = wert;
        v[1] = 1f;
        return v;
    }

    /** Zaehlt Aufrufe und liefert je Text einen Vektor. */
    private static RagIndexer.Einbetter zaehlenderEinbetter(AtomicInteger texte) {
        return liste -> {
            texte.addAndGet(liste.size());
            List<float[]> vektoren = new ArrayList<>();
            for (int i = 0; i < liste.size(); i++) vektoren.add(vektor(i));
            return vektoren;
        };
    }

    @Nested
    @DisplayName("zerlegen")
    class Zerlegen {

        @Test
        @DisplayName("nimmt Benutzer-Doku und Doku-Hauptordner, aber keine internen Plaene, Reviews oder Tests")
        void filtertQuellen() throws IOException {
            datei("docs/benutzer/monatsabschluss.md", "# Monatsabschluss\nKnopf: Monat jetzt abschliessen");
            datei("docs/RECHNUNGSWESEN.md", "# Rechnungen\nSo schreibst du eine Rechnung.");
            datei("docs/superpowers/plans/2026-09-01-plan.md", "# Plan\nNoch nicht gebaut");
            datei("docs/reviews/2026-09-14-review.md", "# Review");
            datei("react-pc-frontend/src/pages/ProjektEditor.tsx", "export default function ProjektEditor() { return null; }");
            datei("react-pc-frontend/src/pages/ProjektEditor.test.tsx", "test('x', () => {});");
            datei("src/main/java/org/example/kalkulationsprogramm/service/ProjektService.java", "class ProjektService {}");
            datei("src/main/java/org/example/kalkulationsprogramm/service/ProjektServiceTest.java", "class ProjektServiceTest {}");

            List<Rohabschnitt> abschnitte = indexer.zerlegen(projekt);

            assertThat(abschnitte).extracting(Rohabschnitt::filePath).containsExactly(
                    "docs/benutzer/monatsabschluss.md",
                    "docs/RECHNUNGSWESEN.md",
                    "react-pc-frontend/src/pages/ProjektEditor.tsx",
                    "src/main/java/org/example/kalkulationsprogramm/service/ProjektService.java");
            assertThat(abschnitte.get(0).category()).isEqualTo("documentation");
            assertThat(abschnitte.get(2).category()).isEqualTo("frontend-page");
            assertThat(abschnitte.get(3).category()).isEqualTo("backend-service");
        }

        @Test
        @DisplayName("teilt lange Markdown-Doku an ##-Ueberschriften")
        void markdownNachUeberschriften() throws IOException {
            String langerText = "Zeile mit Inhalt fuer Max Mustermann.\n".repeat(120);
            datei("docs/ANLEITUNG.md", "# Anleitung\nEinleitung\n## Rechnung schreiben\n" + langerText
                    + "## Rechnung stornieren\n" + langerText);

            List<Rohabschnitt> abschnitte = indexer.zerlegen(projekt);

            assertThat(abschnitte).extracting(Rohabschnitt::name)
                    .containsExactly("Anleitung", "Rechnung schreiben", "Rechnung stornieren");
            assertThat(abschnitte).allSatisfy(a -> assertThat(a.chunkType()).isEqualTo("section"));
        }

        @Test
        @DisplayName("schneidet nichts ab: zu lange Dateien werden in Teile zerlegt")
        void langeDateiInTeilen() throws IOException {
            StringBuilder jsx = new StringBuilder("export default function Seite() {\n  return (\n    <div>\n");
            for (int i = 0; i < 400; i++) jsx.append("      <Button>Knopf ").append(i).append("</Button>\n");
            jsx.append("    </div>\n  );\n}\n");
            datei("react-pc-frontend/src/pages/Seite.tsx", jsx.toString());

            List<Rohabschnitt> abschnitte = indexer.zerlegen(projekt);

            assertThat(abschnitte).hasSizeGreaterThan(1);
            assertThat(abschnitte).allSatisfy(a ->
                    assertThat(a.content().length()).isLessThanOrEqualTo(RagIndexer.MAX_ZEICHEN));
            assertThat(abschnitte.get(0).name()).isEqualTo("Seite.tsx (Teil 1/" + abschnitte.size() + ")");
            assertThat(String.join("\n", abschnitte.stream().map(Rohabschnitt::content).toList()))
                    .isEqualTo(jsx.toString())
                    .contains("Knopf 399");
        }

        @Test
        @DisplayName("packt kleine Java-Methoden zu Abschnitten bis zur Hoechstgroesse")
        void javaMethodenGepackt() throws IOException {
            StringBuilder java = new StringBuilder("""
                    package org.example.kalkulationsprogramm.service;

                    public class RechnungService {
                        private final String name = "Max Mustermann";

                    """);
            for (int i = 0; i < 80; i++) {
                java.append("    public int methode").append(i).append("() {\n        return ").append(i)
                        .append("; // etwas Text, damit die Datei gross genug wird\n    }\n\n");
            }
            java.append("}\n");
            datei("src/main/java/org/example/kalkulationsprogramm/service/RechnungService.java", java.toString());

            List<Rohabschnitt> abschnitte = indexer.zerlegen(projekt);

            assertThat(abschnitte).hasSizeBetween(2, 5);
            assertThat(abschnitte).allSatisfy(a -> {
                assertThat(a.content().length()).isLessThanOrEqualTo(RagIndexer.MAX_ZEICHEN);
                assertThat(a.chunkType()).isEqualTo("methods");
                assertThat(a.name()).startsWith("RechnungService: ");
            });
            assertThat(abschnitte.get(0).content()).startsWith("package org.example");
            assertThat(abschnitte.get(0).name()).startsWith("RechnungService: (header), (Felder), methode0");
            String alles = String.join("\n", abschnitte.stream().map(Rohabschnitt::content).toList());
            assertThat(alles).contains("methode0()", "methode79()");
        }

        @Test
        @DisplayName("schwaerzt Schluessel und Passwoerter im Quelltext")
        void schwaerztGeheimnisse() throws IOException {
            datei("docs/SETUP.md", "api.key=geheim123\npassword: hunter2");

            Rohabschnitt abschnitt = indexer.zerlegen(projekt).get(0);

            assertThat(abschnitt.content()).doesNotContain("geheim123", "hunter2").contains("***REDACTED***");
        }

        @Test
        @DisplayName("schwaerzt auch Werte in Anfuehrungszeichen (JSON, Java/TS)")
        void schwaerztGeheimnisseInAnfuehrungszeichen() throws IOException {
            datei("docs/KONFIG.md", "{\"password\": \"geheim123\"}\napiKey = \"abc456\";");

            Rohabschnitt abschnitt = indexer.zerlegen(projekt).get(0);

            assertThat(abschnitt.content()).doesNotContain("geheim123", "abc456");
        }

        @Test
        @DisplayName("folgt keinen Symlinks - ein Link auf eine fremde Datei wird nicht gelesen")
        void keineSymlinks(@TempDir Path draussen) throws IOException {
            datei("docs/ECHT.md", "# Echt");
            Path geheim = Files.writeString(draussen.resolve("geheim.txt"), "nicht ausliefern");
            Path link = projekt.resolve("docs/LINK.md");
            try {
                Files.createSymbolicLink(link, geheim);
            } catch (UnsupportedOperationException | IOException e) {
                Assumptions.abort("Symlinks werden hier nicht unterstuetzt");
            }

            assertThat(indexer.zerlegen(projekt)).extracting(Rohabschnitt::filePath).containsExactly("docs/ECHT.md");
        }

        @Test
        @DisplayName("kurzer Text vor dem ersten export geht nicht verloren")
        void kurzerKopfBleibt() throws IOException {
            String gross = "  const x = 1; // etwas Text, damit die Datei gross genug wird\n".repeat(60);
            datei("react-pc-frontend/src/components/Liste.tsx", "import x;\n"
                    + "export function A() {\n" + gross + "}\n"
                    + "export function B() {\n" + gross + "}\n");

            List<Rohabschnitt> abschnitte = indexer.zerlegen(projekt);

            assertThat(abschnitte.get(0).content()).startsWith("import x;");
            assertThat(abschnitte.get(0).name()).startsWith("Liste.tsx: (imports), A");
        }

        @Test
        @DisplayName("leerer Projektordner (Docker-Image) liefert keine Abschnitte")
        void leererOrdner() {
            assertThat(indexer.zerlegen(projekt)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Java-Member erkennen")
    class JavaTeilen {

        @Test
        @DisplayName("trennt Kopf, Felder und Methoden mit und ohne Annotationen")
        void trenntMember() {
            String java = """
                    package com.example;

                    public class SampleService {
                        private final String name;

                        public SampleService(String name) {
                            this.name = name;
                        }

                        @Override
                        @Deprecated
                        public String toString() {
                            return name;
                        }

                        List<String> buildKetten(int id) {
                            return List.of();
                        }
                    }
                    """;

            List<String> teile = RagIndexer.javaTeilen(java);

            assertThat(teile).hasSize(5);
            assertThat(teile.get(0)).startsWith("package com.example").endsWith("public class SampleService {");
            assertThat(teile.get(1)).isEqualTo("private final String name;");
            assertThat(teile.get(2)).startsWith("public SampleService");
            assertThat(teile.get(3)).startsWith("@Override");
            assertThat(teile.get(4)).startsWith("List<String> buildKetten");
        }

        @Test
        @DisplayName("ist immun gegen ReDoS (Catastrophic Backtracking)")
        void reDosResilienz() {
            StringBuilder pathologisch = new StringBuilder();
            for (int i = 0; i < 500; i++) {
                pathologisch.append("    @annotation").append(i).append(" trailing stuff\n");
            }
            pathologisch.append("    not_a_method_modifier_so_it_fails_match();\n");

            assertTimeoutPreemptively(Duration.ofSeconds(1),
                    () -> assertThat(RagIndexer.javaTeilen(pathologisch.toString())).isNotEmpty());
        }
    }

    @Nested
    @DisplayName("inTeileZerlegen")
    class InTeileZerlegen {

        @Test
        @DisplayName("kurzer Text bleibt ein Teil")
        void kurz() {
            assertThat(RagIndexer.inTeileZerlegen("kurz")).containsExactly("kurz");
        }

        @Test
        @DisplayName("ueberlange Zeile wird hart geteilt, keine leeren Teile")
        void ueberlangeZeile() {
            String zeile = "x".repeat(RagIndexer.MAX_ZEICHEN * 2 + 10);
            String genauGross = "y".repeat(RagIndexer.MAX_ZEICHEN);

            List<String> teile = RagIndexer.inTeileZerlegen(zeile + "\n" + genauGross + "\n\n");

            assertThat(teile).noneMatch(String::isBlank);
            assertThat(teile).allSatisfy(t -> assertThat(t.length()).isLessThanOrEqualTo(RagIndexer.MAX_ZEICHEN));
            assertThat(String.join("", teile).replace("\n", "")).isEqualTo(zeile + genauGross);
        }
    }

    @Nested
    @DisplayName("aktualisieren")
    class Aktualisieren {

        private final Rohabschnitt alt = new Rohabschnitt("Inhalt A", "docs/A.md", "documentation", "document", "A.md");
        private final Rohabschnitt neu = new Rohabschnitt("Inhalt B", "docs/B.md", "documentation", "document", "B.md");

        @Test
        @DisplayName("bettet nur ein, was nicht schon in der Vorlage steht")
        void nurNeues() {
            Abschnitt bekannt = new Abschnitt(alt.content(), alt.filePath(), "documentation", "document",
                    "alter Name", alt.hash(), vektor(7));
            AtomicInteger eingebettet = new AtomicInteger();

            Ergebnis ergebnis = indexer.aktualisieren(List.of(alt, neu), Map.of(bekannt.schluessel(), bekannt),
                    zaehlenderEinbetter(eingebettet), null);

            assertThat(eingebettet).hasValue(1);
            assertThat(ergebnis.ausVorlage()).isEqualTo(1);
            assertThat(ergebnis.neuEingebettet()).isEqualTo(1);
            assertThat(ergebnis.vollstaendig()).isTrue();
            assertThat(ergebnis.abschnitte()).extracting(Abschnitt::filePath).containsExactly("docs/A.md", "docs/B.md");
            // Vektor aus der Vorlage, Name aus dem aktuellen Stand
            assertThat(ergebnis.abschnitte().get(0).vector()[0]).isEqualTo(7f);
            assertThat(ergebnis.abschnitte().get(0).name()).isEqualTo("A.md");
        }

        @Test
        @DisplayName("gleicher Text in derselben Datei wird nur einmal eingebettet")
        void doppelteNurEinmal() {
            AtomicInteger eingebettet = new AtomicInteger();

            Ergebnis ergebnis = indexer.aktualisieren(List.of(neu, neu), Map.of(), zaehlenderEinbetter(eingebettet), null);

            assertThat(eingebettet).hasValue(1);
            assertThat(ergebnis.abschnitte()).hasSize(1);
        }

        @Test
        @DisplayName("bei abgelaufenem Zeitlimit bleibt der Rest fuer den naechsten Lauf")
        void zeitlimit() {
            AtomicInteger eingebettet = new AtomicInteger();

            Ergebnis ergebnis = indexer.aktualisieren(List.of(neu), Map.of(), zaehlenderEinbetter(eingebettet),
                    Duration.ofSeconds(-1));

            assertThat(eingebettet).hasValue(0);
            assertThat(ergebnis.zeitlimitErreicht()).isTrue();
            assertThat(ergebnis.fehlend()).isEqualTo(1);
            assertThat(ergebnis.vollstaendig()).isFalse();
            assertThat(ergebnis.abschnitte()).isEmpty();
        }

        @Test
        @DisplayName("ein Fehler beim Einbetten haelt den Lauf an, das Bisherige bleibt")
        void fehlerBehaeltBisheriges() {
            List<Rohabschnitt> viele = new ArrayList<>();
            for (int i = 0; i < RagIndexer.STAPEL_GROESSE + 5; i++) {
                viele.add(new Rohabschnitt("Text " + i, "docs/X.md", "documentation", "section", "S" + i));
            }
            AtomicInteger aufrufe = new AtomicInteger();
            RagIndexer.Einbetter ersterGutDannFehler = texte -> {
                if (aufrufe.incrementAndGet() > 1) throw new IOException("Gemini Embedding Fehler 400");
                return texte.stream().map(t -> vektor(1)).toList();
            };

            Ergebnis ergebnis = indexer.aktualisieren(viele, Map.of(), ersterGutDannFehler, null);

            assertThat(ergebnis.abschnitte()).hasSize(RagIndexer.STAPEL_GROESSE);
            assertThat(ergebnis.fehlend()).isEqualTo(5);
            assertThat(ergebnis.fehler()).contains("400");
            assertThat(ergebnis.zeitlimitErreicht()).isFalse();
        }

        @Test
        @DisplayName("falsche Anzahl Vektoren zaehlt als Fehler")
        void falscheAnzahl() {
            Ergebnis ergebnis = indexer.aktualisieren(List.of(alt, neu), Map.of(), texte -> List.of(vektor(1)), null);

            assertThat(ergebnis.abschnitte()).isEmpty();
            assertThat(ergebnis.fehler()).contains("1 statt 2");
        }

        @Test
        @DisplayName("Abbruch von aussen wird weitergereicht")
        void unterbrochen() {
            Ergebnis ergebnis = indexer.aktualisieren(List.of(neu), Map.of(), texte -> {
                throw new InterruptedException();
            }, null);

            assertThat(ergebnis.fehler()).isEqualTo("Abgebrochen");
            assertThat(Thread.interrupted()).isTrue();
        }

        @Test
        @DisplayName("eingebettet wird mit Dateipfad, gespeichert ohne")
        void einbettTextMitPfad() {
            List<String> gesehen = new ArrayList<>();

            Ergebnis ergebnis = indexer.aktualisieren(List.of(neu), Map.of(), texte -> {
                gesehen.addAll(texte);
                return List.of(vektor(1));
            }, null);

            assertThat(gesehen).containsExactly("Datei: docs/B.md | B.md\nInhalt B");
            assertThat(ergebnis.abschnitte().get(0).content()).isEqualTo("Inhalt B");
        }
    }

    @Nested
    @DisplayName("RAG-Datei")
    class RagDatei {

        @Test
        @DisplayName("speichern und laden ergibt dieselben Abschnitte")
        void rundreise() throws IOException {
            Abschnitt a = new Abschnitt("Inhalt mit Umlauten: Gebühr für Max Mustermann", "docs/A.md",
                    "documentation", "section", "Gebühren", "abc123", vektor(0.25f));
            Path datei = projekt.resolve("ki-hilfe/rag-cache.json");

            indexer.speichern(datei, List.of(a));
            Map<String, Abschnitt> geladen = indexer.laden(datei);

            assertThat(Files.exists(projekt.resolve("ki-hilfe/rag-cache.json.neu"))).isFalse();
            assertThat(geladen).hasSize(1);
            Abschnitt b = geladen.values().iterator().next();
            assertThat(b.content()).isEqualTo(a.content());
            assertThat(b.name()).isEqualTo("Gebühren");
            assertThat(b.contentHash()).isEqualTo("abc123");
            assertThat(b.vector()).containsExactly(a.vector());
        }

        @Test
        @DisplayName("fehlende Datei ergibt eine leere Vorlage")
        void fehlt() {
            assertThat(indexer.laden(projekt.resolve("gibt-es-nicht.json"))).isEmpty();
            assertThat(indexer.laden(null)).isEmpty();
        }

        @Test
        @DisplayName("altes Format (JSON-Liste) wird verworfen")
        void altesFormat() throws IOException {
            datei(".rag-cache.json", "[{\"content\":\"x\",\"vector\":[0.1,0.2]}]");

            assertThat(indexer.laden(projekt.resolve(".rag-cache.json"))).isEmpty();
        }

        @Test
        @DisplayName("anderes Embedding-Modell wird verworfen")
        void anderesModell() throws IOException {
            datei("rag.json", "{\"format\":2,\"modell\":\"text-embedding-004\",\"dimensionen\":768,"
                    + "\"abschnitte\":[{\"filePath\":\"a\",\"contentHash\":\"h\",\"vector\":\""
                    + RagIndexer.vektorSchreiben(vektor(1)) + "\"}]}");

            assertThat(indexer.laden(projekt.resolve("rag.json"))).isEmpty();
        }

        @Test
        @DisplayName("kaputte Datei ergibt eine leere Vorlage statt eines Fehlers")
        void kaputt() throws IOException {
            datei("rag.json", "{\"format\":2,\"modell\":\"gemini-embedding-001\",\"dimensionen\":768,\"abschnitte\":[{\"filePath\":");

            assertThat(indexer.laden(projekt.resolve("rag.json"))).isEmpty();
        }

        @Test
        @DisplayName("Abschnitte mit falsch langem Vektor werden uebersprungen, unbekannte Felder ignoriert")
        void falscherVektor() throws IOException {
            datei("rag.json", "{\"format\":2,\"modell\":\"gemini-embedding-001\",\"dimensionen\":768,"
                    + "\"erstellt\":{\"von\":\"CI\"},\"abschnitte\":["
                    + "{\"filePath\":\"a\",\"contentHash\":\"1\",\"vector\":\"AAAA\"},"
                    + "{\"filePath\":\"b\",\"contentHash\":\"2\",\"vector\":\"" + RagIndexer.vektorSchreiben(vektor(1)) + "\"}]}");

            assertThat(indexer.laden(projekt.resolve("rag.json"))).containsOnlyKeys("b::2");
        }

        @Test
        @DisplayName("ein kaputter Vektor kostet nur seinen Abschnitt, nicht die ganze Datei")
        void kaputterVektor() throws IOException {
            datei("rag.json", "{\"format\":2,\"modell\":\"gemini-embedding-001\",\"dimensionen\":768,\"abschnitte\":["
                    + "{\"filePath\":\"a\",\"contentHash\":\"1\",\"vector\":\"###kein base64###\"},"
                    + "{\"filePath\":\"b\",\"contentHash\":\"2\",\"vector\":\"" + RagIndexer.vektorSchreiben(vektor(1)) + "\"}]}");

            assertThat(indexer.laden(projekt.resolve("rag.json"))).containsOnlyKeys("b::2");
        }

        @Test
        @DisplayName("abschnitte ohne Liste: leer, ohne im Objekt weiterzulesen")
        void abschnitteKeineListe() throws IOException {
            datei("rag.json", "{\"format\":2,\"modell\":\"gemini-embedding-001\",\"dimensionen\":768,"
                    + "\"abschnitte\":{\"filePath\":\"x\",\"liste\":[1,2]},\"danach\":1}");

            assertThat(indexer.laden(projekt.resolve("rag.json"))).isEmpty();
        }

        @Test
        @DisplayName("scheitert das Schreiben, bleibt keine halbe Datei liegen")
        void schreibfehlerRaeumtAuf() {
            Path datei = projekt.resolve("ki-hilfe/rag-cache.json");
            Abschnitt ohneVektor = new Abschnitt("Inhalt", "docs/A.md", "documentation", "section", "A", "h", null);

            assertThatThrownBy(() -> indexer.speichern(datei, List.of(ohneVektor)))
                    .isInstanceOf(NullPointerException.class);
            assertThat(Files.exists(datei)).isFalse();
            assertThat(Files.exists(projekt.resolve("ki-hilfe/rag-cache.json.neu"))).isFalse();
        }

        @Test
        @DisplayName("Vektor als Base64 bleibt verlustfrei")
        void vektorBase64() {
            float[] v = vektor(-0.123456f);

            assertThat(RagIndexer.vektorLesen(RagIndexer.vektorSchreiben(v))).containsExactly(v);
            assertThat(RagIndexer.vektorLesen("")).isNull();
            assertThat(RagIndexer.vektorLesen(null)).isNull();
        }
    }
}
