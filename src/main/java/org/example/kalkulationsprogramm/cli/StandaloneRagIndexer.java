package org.example.kalkulationsprogramm.cli;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.example.kalkulationsprogramm.service.kihilfe.GeminiEinbetter;
import org.example.kalkulationsprogramm.service.kihilfe.RagIndexer;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Erzeugt bzw. aktualisiert die RAG-Datei der KI-Hilfe ohne Spring-Kontext.
 * <p>
 * Laeuft beim Image-Bau in GitHub ({@code .github/workflows/docker-image.yml}) und lokal ueber
 * {@code rag-index.sh}/{@code rag-index.cmd}. Gleiche Logik wie der Server ({@link RagIndexer}):
 * Was sich seit der vorhandenen RAG-Datei nicht geaendert hat, wird nicht neu eingebettet.
 *
 * <pre>
 *   GEMINI_API_KEY=... java -cp app.jar -Dloader.main=org.example.kalkulationsprogramm.cli.StandaloneRagIndexer \
 *       org.springframework.boot.loader.launch.PropertiesLauncher [projekt-ordner] [rag-datei] [--max-minuten N]
 * </pre>
 * {@code --nur-zaehlen}: nur zeigen, wie viele Abschnitte es gibt und wie viele neu eingebettet
 * werden muessten - ohne Schluessel, ohne Kosten.
 * <p>
 * Exit-Code: 0 = fertig (auch wenn das Zeitlimit erreicht wurde - der Rest folgt beim naechsten
 * Lauf), 1 = Einbetten fehlgeschlagen (Teilstand gespeichert), 2 = falscher Aufruf/kein Key.
 */
public final class StandaloneRagIndexer {

    static final int OK = 0;
    static final int FEHLER = 1;
    static final int AUFRUF = 2;

    private StandaloneRagIndexer() {
    }

    public static void main(String[] args) {
        ObjectMapper objectMapper = JsonMapper.builderWithJackson2Defaults().build();
        int code = ausfuehren(args, System.getenv(), System.out, objectMapper,
                key -> new GeminiEinbetter(objectMapper, () -> key));
        System.exit(code);
    }

    static int ausfuehren(String[] args, Map<String, String> umgebung, PrintStream aus, ObjectMapper objectMapper,
                          Function<String, RagIndexer.Einbetter> einbetterFuer) {
        List<String> positionen = new ArrayList<>();
        Duration zeitLimit = null;
        boolean nurZaehlen = false;
        for (int i = 0; i < args.length; i++) {
            if ("--nur-zaehlen".equals(args[i])) {
                nurZaehlen = true;
            } else if ("--max-minuten".equals(args[i]) && i + 1 < args.length) {
                long minuten;
                try {
                    minuten = Long.parseLong(args[++i]);
                } catch (NumberFormatException e) {
                    minuten = 0;
                }
                if (minuten < 1) {
                    aus.println("--max-minuten erwartet eine Zahl ab 1, nicht '" + args[i] + "'");
                    return AUFRUF;
                }
                zeitLimit = Duration.ofMinutes(minuten);
            } else if (args[i].startsWith("--")) {
                aus.println("Unbekannte Option: " + args[i]);
                aus.println("Aufruf: StandaloneRagIndexer [projekt-ordner] [rag-datei] [--max-minuten N] [--nur-zaehlen]");
                return AUFRUF;
            } else {
                positionen.add(args[i]);
            }
        }
        Path ordner = Path.of(positionen.isEmpty() ? "." : positionen.get(0)).toAbsolutePath().normalize();
        Path datei = positionen.size() > 1 ? Path.of(positionen.get(1)) : ordner.resolve(".rag-cache.json");

        RagIndexer indexer = new RagIndexer(objectMapper);
        List<RagIndexer.Rohabschnitt> quellen = indexer.zerlegen(ordner);
        if (quellen.isEmpty()) {
            aus.println("Keine Doku und kein Quellcode unter " + ordner + " gefunden.");
            return AUFRUF;
        }
        Map<String, RagIndexer.Abschnitt> vorlage = indexer.laden(datei);
        if (nurZaehlen) {
            zaehlen(quellen, vorlage, aus);
            return OK;
        }

        String key = umgebung.get("GEMINI_API_KEY");
        if (key == null || key.isBlank()) {
            aus.println("GEMINI_API_KEY ist nicht gesetzt - ohne Schluessel kann nichts eingebettet werden.");
            return AUFRUF;
        }
        aus.println("RAG-Index: " + quellen.size() + " Abschnitte in " + ordner + ", "
                + vorlage.size() + " in der vorhandenen RAG-Datei" + (zeitLimit == null ? "" : ", Zeitlimit " + zeitLimit.toMinutes() + " min"));

        RagIndexer.Ergebnis ergebnis = indexer.aktualisieren(quellen, vorlage, einbetterFuer.apply(key), zeitLimit);
        try {
            indexer.speichern(datei, ergebnis.abschnitte());
        } catch (IOException e) {
            aus.println("RAG-Datei konnte nicht geschrieben werden: " + e.getMessage());
            return FEHLER;
        }
        aus.println("RAG-Datei " + datei + ": " + ergebnis.abschnitte().size() + " Abschnitte ("
                + ergebnis.ausVorlage() + " unveraendert, " + ergebnis.neuEingebettet() + " neu, "
                + ergebnis.fehlend() + " fehlen noch)");
        if (ergebnis.vollstaendig()) {
            return OK;
        }
        if (ergebnis.zeitlimitErreicht()) {
            aus.println("Zeitlimit erreicht - die fehlenden Abschnitte folgen beim naechsten Lauf.");
            return OK;
        }
        aus.println("Einbetten fehlgeschlagen: " + ergebnis.fehler());
        return FEHLER;
    }

    private static void zaehlen(List<RagIndexer.Rohabschnitt> quellen, Map<String, RagIndexer.Abschnitt> vorlage,
                                PrintStream aus) {
        Map<String, int[]> jeKategorie = new TreeMap<>();
        Set<String> neu = new HashSet<>();
        long zeichen = 0;
        for (RagIndexer.Rohabschnitt roh : quellen) {
            int[] summe = jeKategorie.computeIfAbsent(roh.category(), k -> new int[2]);
            summe[0]++;
            summe[1] += roh.content().length();
            zeichen += roh.content().length();
            String schluessel = roh.filePath() + "::" + roh.hash();
            if (!vorlage.containsKey(schluessel)) neu.add(schluessel);
        }
        jeKategorie.forEach((kategorie, summe) ->
                aus.printf("  %-24s %6d Abschnitte %,12d Zeichen%n", kategorie, summe[0], summe[1]));
        aus.printf("Gesamt: %d Abschnitte, %,d Zeichen; %d davon neu einzubetten%n", quellen.size(), zeichen, neu.size());
    }
}
