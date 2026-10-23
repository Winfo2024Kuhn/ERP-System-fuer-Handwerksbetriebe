package org.example.kalkulationsprogramm.service;

import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.service.kihilfe.GeminiEinbetter;
import org.example.kalkulationsprogramm.service.kihilfe.RagIndexer;
import org.example.kalkulationsprogramm.service.kihilfe.RagIndexer.Abschnitt;
import org.example.kalkulationsprogramm.service.kihilfe.StichwortIndex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wissen der KI-Hilfe (RAG): Suche im Prozess, ohne Qdrant oder andere Dienste.
 * <p>
 * Zwei Betriebsarten, beim Start automatisch gewaehlt:
 * <ul>
 *   <li><b>Server (Docker-Image):</b> Kein Quellcode vorhanden. Geladen wird die RAG-Datei, die
 *       GitHub beim Image-Bau erzeugt ({@code ai.rag.datei}, siehe RAG_INDEXING.md). Sie kommt mit
 *       jedem Nachtupdate neu; der Server bettet selbst nichts ein.</li>
 *   <li><b>Entwicklung:</b> Quellcode liegt im Projektordner. Geaenderte Abschnitte werden mit dem
 *       Gemini-Key aus den System-Einstellungen eingebettet und in der RAG-Datei gespeichert.</li>
 * </ul>
 * Gesucht wird zweifach (Hybrid-Suche): nach Bedeutung (Vektor; die Frage wird mit dem Gemini-Key
 * des Betriebs eingebettet) und nach Woertern ({@link StichwortIndex}). Beide Ranglisten werden per
 * Reciprocal Rank Fusion verschmolzen. Faellt das Einbetten aus, sucht die Wortsuche allein weiter.
 */
@Slf4j
@Service
public class LocalRagService {

    /**
     * Seite -> Dateiname(n) der Seite. Den Code der Seite, auf der der Nutzer gerade ist, bekommt
     * die KI immer mit. ":x" steht fuer einen beliebigen Pfadteil (z. B. eine ID).
     */
    private static final Map<String, List<String>> ROUTE_TO_FILE_KEYWORDS = Map.ofEntries(
            Map.entry("/projekte", List.of("ProjektEditor")),
            Map.entry("/anfragen", List.of("AnfrageEditor")),
            Map.entry("/kunden", List.of("Kundeneditor")),
            Map.entry("/lieferanten", List.of("LieferantenEditor")),
            Map.entry("/artikel", List.of("ArtikelEditor")),
            Map.entry("/artikel/:id", List.of("ArtikelDetail")),
            Map.entry("/bestellungen", List.of("BestellungenUebersicht")),
            Map.entry("/bestellungen/bedarf", List.of("BestellungEditor")),
            Map.entry("/textbausteine", List.of("TextbausteinEditor")),
            Map.entry("/leistungen", List.of("LeistungenUndKategorien", "OrdnerBaum", "AuswertungReiter")),
            Map.entry("/arbeitsgaenge", List.of("ArbeitsgangEditor")),
            Map.entry("/mitarbeiter", List.of("MitarbeiterEditor")),
            Map.entry("/arbeitszeitarten", List.of("ArbeitszeitartEditor")),
            Map.entry("/kalender", List.of("TerminKalender")),
            Map.entry("/emails", List.of("EmailCenter")),
            Map.entry("/email-textvorlagen", List.of("EmailTextvorlagenEditor")),
            Map.entry("/email-einstellungen", List.of("EmailEinstellungenPage")),
            Map.entry("/telefon", List.of("TelefonPage")),
            Map.entry("/benachrichtigungen", List.of("BenachrichtigungenPage")),
            Map.entry("/formulare", List.of("FormularwesenEditor")),
            Map.entry("/website", List.of("WebsiteEditor")),
            Map.entry("/offeneposten", List.of("OffenePostenEditor")),
            Map.entry("/rechnungsuebersicht", List.of("RechnungsuebersichtEditor")),
            Map.entry("/belege-kasse", List.of("BelegeKasseEditor")),
            Map.entry("/dokumentuebersicht", List.of("DokumentUebersichtEditor")),
            Map.entry("/analyse", List.of("ErfolgsanalyseEditor")),
            Map.entry("/kostenstellen", List.of("KostenstellenControllingEditor")),
            Map.entry("/miete", List.of("MietabrechnungEditor")),
            Map.entry("/benutzer", List.of("BenutzerEditor")),
            Map.entry("/firma", List.of("FirmaEditor")),
            Map.entry("/einstellungen", List.of("EinstellungenEditor", "SystemSetupConfigurator", "BerechtigungenSection")),
            Map.entry("/monatsabschluss", List.of("Monatsabschluss")),
            Map.entry("/zeitbuchungen", List.of("ZeiterfassungKalender")),
            Map.entry("/auswertung", List.of("ZeiterfassungAuswertung")),
            Map.entry("/steuerberater", List.of("ZeiterfassungSteuerberater")),
            Map.entry("/zeitkonten", List.of("ZeiterfassungZeitkonten")),
            Map.entry("/feiertage", List.of("ZeiterfassungFeiertage")),
            Map.entry("/urlaubsantraege", List.of("Urlaubsantraege")),
            Map.entry("/langzeitkrankmeldungen", List.of("Langzeitkrankmeldungen")),
            Map.entry("/dokument-editor", List.of("DocumentEditorPage"))
    );

    /** Laengere Routen gibt es im ERP nicht - die Route kommt ungeprueft aus dem Chat-Request. */
    private static final int MAX_ROUTE = 200;

    /** Einmal zerlegt; feste Routen vor solchen mit Platzhalter, sonst alphabetisch. */
    private static final List<RoutenMuster> ROUTEN = ROUTE_TO_FILE_KEYWORDS.entrySet().stream()
            .map(e -> new RoutenMuster(e.getKey(), e.getKey().split("/"), e.getValue()))
            .sorted(Comparator.comparing(RoutenMuster::hatPlatzhalter).thenComparing(RoutenMuster::route))
            .toList();

    private static final String STANDARD_DATEINAME = ".rag-cache.json";

    /** Reciprocal Rank Fusion: Platz r zaehlt 1/(60 + r) - der uebliche Wert, daempft die Spitze. */
    private static final int RRF_K = 60;
    /** So viele Kandidaten je Suchart gehen in die Verschmelzung. */
    private static final int KANDIDATEN = 50;
    /** Hoechstens so viele Teile der aktuellen Seite in der ersten Suche. */
    private static final int MAX_SEITEN_TEILE = 3;

    /** Welche Abschnitte eine Suche beruecksichtigt. */
    public enum Bereich {
        /** Anleitungen und Bildschirmseiten: was der Nutzer sieht und wie er es bedient. */
        BEDIENUNG,
        /** Server-Code: Regeln, Pruefungen und Sperren im Hintergrund. */
        TECHNIK,
        ALLES;

        boolean umfasst(String category) {
            return switch (this) {
                case BEDIENUNG -> !istTechnik(category);
                case TECHNIK -> istTechnik(category);
                case ALLES -> true;
            };
        }
    }

    /** Ein gefundener Abschnitt; {@code schluessel} ist je Abschnitt eindeutig. */
    public record CodeChunkResult(String schluessel, String content, String filePath, String category,
                                  String chunkType, String name, boolean aktuelleSeite) {}

    /** Abschnitte und Wortindex - zusammen ersetzt, damit Suchen ohne Sperre laufen. */
    private record Wissen(List<Abschnitt> abschnitte, StichwortIndex stichworte) {
        static final Wissen LEER = new Wissen(List.of(), StichwortIndex.aus(List.of()));
    }

    private final RagIndexer indexer;
    private final GeminiEinbetter einbetter;
    private final SystemSettingsService systemSettingsService;

    private volatile Wissen wissen = Wissen.LEER;
    private volatile boolean ready = false;

    @Value("${ai.rag.enabled:false}")
    private boolean ragEnabled;

    @Value("${ai.rag.top-k:8}")
    private int topK;

    @Value("${ai.rag.score-threshold:0.3}")
    private double scoreThreshold;

    /** Wo Quellcode und Doku liegen (Entwicklung). Im Docker-Image gibt es dort keinen Quellcode. */
    @Value("${ai.rag.projekt-ordner:${user.dir}}")
    private String projektOrdner;

    /** Die RAG-Datei. Leer = {@value #STANDARD_DATEINAME} im Projektordner. */
    @Value("${ai.rag.datei:}")
    private String ragDatei;

    @Autowired
    public LocalRagService(ObjectMapper objectMapper, SystemSettingsService systemSettingsService) {
        this(new RagIndexer(objectMapper),
                new GeminiEinbetter(objectMapper, systemSettingsService::getGeminiApiKey),
                systemSettingsService);
    }

    LocalRagService(RagIndexer indexer, GeminiEinbetter einbetter, SystemSettingsService systemSettingsService) {
        this.indexer = indexer;
        this.einbetter = einbetter;
        this.systemSettingsService = systemSettingsService;
    }

    // ── Public API ──

    public boolean isAvailable() {
        return ragEnabled && ready;
    }

    public boolean isEnabled() {
        return ragEnabled;
    }

    public boolean isReady() {
        return ready;
    }

    /**
     * Erste, automatische Suche zur Frage. Die Seite, auf der der Nutzer gerade ist, kommt immer mit
     * (die Teile, die am besten zur Frage passen); der Rest nach Hybrid-Rangliste, mit wenig
     * Server-Code - der zeigt nicht, was auf dem Bildschirm steht.
     *
     * @param query        Die Frage (bei Nachfragen mit der vorigen Frage davor)
     * @param pageContext  Hinweis wie "Seite: Projekte (/projekte)" - schaerft nur die Bedeutungssuche
     * @param currentRoute Die Route im Frontend, z. B. "/projekte/42"
     */
    public List<CodeChunkResult> search(String query, String pageContext, String currentRoute) {
        Wissen stand = wissen;
        if (!ready || stand.abschnitte().isEmpty() || query == null || query.isBlank()) return List.of();

        String bedeutungsFrage = pageContext != null && !pageContext.isBlank()
                ? query + "\n\nSeitenkontext: " + pageContext
                : query;
        float[] frageVektor = frageEinbetten(bedeutungsFrage);
        long t0 = System.currentTimeMillis();

        // 1) Die aktuelle Seite. Grosse Seiten bestehen aus vielen Teilen - genommen werden die,
        //    die am besten zur Frage passen, nicht der Dateianfang.
        int seitenPlaetze = Math.min(MAX_SEITEN_TEILE, Math.max(1, topK / 3));
        List<Integer> seitenTeile = nachRelevanz(stand, seitenPositionen(stand, currentRoute), query, frageVektor);
        List<CodeChunkResult> ergebnis = new ArrayList<>();
        Set<Integer> genommen = new HashSet<>();
        for (int position : seitenTeile.subList(0, Math.min(seitenPlaetze, seitenTeile.size()))) {
            ergebnis.add(treffer(stand.abschnitte().get(position), true));
            genommen.add(position);
        }

        // 2) Auffuellen nach Rangliste; Server-Code hoechstens zu einem Viertel
        int maxTechnik = Math.max(1, topK / 4);
        int technik = 0;
        for (int position : rangliste(stand, query, frageVektor, Bereich.ALLES)) {
            if (ergebnis.size() >= topK) break;
            if (genommen.contains(position)) continue;
            Abschnitt a = stand.abschnitte().get(position);
            if (istTechnik(a.category())) {
                if (technik >= maxTechnik) continue;
                technik++;
            }
            ergebnis.add(treffer(a, false));
        }

        log.info("    Hybrid-Suche: {} Treffer in {} ms (Seitenteile={}, Bedeutungssuche={}, top-k={}, Abschnitte={})",
                ergebnis.size(), System.currentTimeMillis() - t0, Math.min(seitenPlaetze, seitenTeile.size()),
                frageVektor != null ? "ja" : "ausgefallen", topK, stand.abschnitte().size());
        return ergebnis;
    }

    /**
     * Suche der KI ueber ihr Werkzeug: die besten noch nicht gezeigten Abschnitte im Bereich.
     *
     * @param bereitsGesehen Schluessel der Abschnitte, die die KI in diesem Gespraech schon bekam
     */
    public List<CodeChunkResult> suchen(String anfrage, Bereich bereich, int anzahl, Set<String> bereitsGesehen) {
        Wissen stand = wissen;
        if (!ready || stand.abschnitte().isEmpty() || anfrage == null || anfrage.isBlank() || anzahl <= 0) {
            return List.of();
        }
        float[] frageVektor = frageEinbetten(anfrage);
        List<CodeChunkResult> ergebnis = new ArrayList<>();
        for (int position : rangliste(stand, anfrage, frageVektor, bereich)) {
            Abschnitt a = stand.abschnitte().get(position);
            if (bereitsGesehen.contains(schluessel(a))) continue;
            ergebnis.add(treffer(a, false));
            if (ergebnis.size() >= anzahl) break;
        }
        return ergebnis;
    }

    /** Alle Abschnitte einer Datei der Wissensbasis in Dateireihenfolge; leer, wenn es sie nicht gibt. */
    public List<CodeChunkResult> dateiLesen(String filePath) {
        if (filePath == null || filePath.isBlank()) return List.of();
        List<CodeChunkResult> teile = new ArrayList<>();
        for (Abschnitt a : wissen.abschnitte()) {
            if (a.filePath().equals(filePath)) teile.add(treffer(a, false));
        }
        return teile;
    }

    /** Ordner der Anleitung fuer Nutzer; die uebrige Doku beschreibt Technik fuer Entwickler. */
    private static final String ANLEITUNG_ORDNER = "docs/benutzer/";

    /**
     * Lesbare Art eines Abschnitts fuer die KI. Die Unterscheidung ist wichtig: Nur die Anleitung
     * und der Bildschirm-Code zeigen, was der Nutzer sieht und anklickt.
     */
    public static String art(String category, String filePath) {
        if (category.startsWith("documentation")) {
            return filePath.startsWith(ANLEITUNG_ORDNER)
                    ? "Anleitung fuer Nutzer"
                    : "Technische Beschreibung (Ablaeufe und Regeln, nicht die Oberflaeche)";
        }
        if (istTechnik(category)) return "Server-Regel (fuer den Nutzer nicht sichtbar)";
        return "Bildschirm (was der Nutzer sieht)";
    }

    /** Kontext fuer den System-Prompt; Abschnitte der aktuellen Seite sind besonders markiert. */
    public String buildContextFromResults(List<CodeChunkResult> results) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            CodeChunkResult r = results.get(i);
            String label = r.aktuelleSeite() ? ">>> AKTUELLE SEITE DES BENUTZERS <<<" : art(r.category(), r.filePath());
            sb.append("### [%d] %s -- %s | %s\n".formatted(i + 1, r.filePath(), r.name(), label));
            sb.append("```\n").append(r.content()).append("\n```\n\n");
        }
        return sb.toString();
    }

    // ── Suche ──

    /** Frage als Vektor; {@code null}, wenn das Einbetten scheitert - dann sucht die Wortsuche allein. */
    private float[] frageEinbetten(String text) {
        long t0 = System.currentTimeMillis();
        try {
            float[] vektor = einbetter.anfrageEinbetten(text);
            log.info("    Embedding erstellt in {} ms", System.currentTimeMillis() - t0);
            return vektor;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException | RuntimeException e) {
            log.warn("    Bedeutungssuche ausgefallen ({}), Wortsuche allein", e.getClass().getSimpleName());
            return null;
        }
    }

    /** Positionen nach Hybrid-Rangliste (Reciprocal Rank Fusion), beste zuerst. */
    private List<Integer> rangliste(Wissen stand, String anfrage, float[] frageVektor, Bereich bereich) {
        List<Abschnitt> abschnitte = stand.abschnitte();
        Map<Integer, Double> punkte = new HashMap<>();

        if (frageVektor != null) {
            List<double[]> aehnlich = new ArrayList<>();
            for (int position = 0; position < abschnitte.size(); position++) {
                Abschnitt a = abschnitte.get(position);
                if (!bereich.umfasst(a.category())) continue;
                double score = cosineSimilarity(frageVektor, a.vector());
                if (score >= scoreThreshold) aehnlich.add(new double[]{score, position});
            }
            aehnlich.sort(Comparator.comparingDouble((double[] t) -> t[0]).reversed());
            for (int rang = 0; rang < Math.min(KANDIDATEN, aehnlich.size()); rang++) {
                punkte.merge((int) aehnlich.get(rang)[1], 1.0 / (RRF_K + rang + 1), Double::sum);
            }
        }

        List<StichwortIndex.Treffer> woerter = stand.stichworte().suchen(anfrage, KANDIDATEN,
                position -> bereich.umfasst(abschnitte.get(position).category()));
        for (int rang = 0; rang < woerter.size(); rang++) {
            punkte.merge(woerter.get(rang).position(), 1.0 / (RRF_K + rang + 1), Double::sum);
        }

        return punkte.entrySet().stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .toList();
    }

    /** Seitenteile nach Naehe zur Frage: Bedeutung, ohne Vektor nach Woertern, sonst Dateireihenfolge. */
    private List<Integer> nachRelevanz(Wissen stand, List<Integer> positionen, String anfrage, float[] frageVektor) {
        if (positionen.size() <= 1) return positionen;
        Map<Integer, Double> relevanz = new HashMap<>();
        if (frageVektor != null) {
            for (int position : positionen) {
                relevanz.put(position, cosineSimilarity(frageVektor, stand.abschnitte().get(position).vector()));
            }
        } else {
            Set<Integer> seite = new HashSet<>(positionen);
            for (StichwortIndex.Treffer t : stand.stichworte().suchen(anfrage, positionen.size(), seite::contains)) {
                relevanz.put(t.position(), t.punkte());
            }
        }
        return positionen.stream()
                .sorted(Comparator.comparingDouble((Integer p) -> relevanz.getOrDefault(p, 0.0)).reversed())
                .toList();
    }

    /**
     * Positionen aller Abschnitte der Seite(n) zur Route. Nur Frontend-Dateien: "Monatsabschluss"
     * steckt auch in MonatsabschlussService.java - das ist aber nicht, was der Nutzer sieht.
     */
    private List<Integer> seitenPositionen(Wissen stand, String route) {
        List<String> keywords = dateienZurRoute(route);
        if (keywords.isEmpty()) return List.of();

        List<Integer> positionen = new ArrayList<>();
        for (int position = 0; position < stand.abschnitte().size(); position++) {
            Abschnitt entry = stand.abschnitte().get(position);
            if (istFrontend(entry.category()) && keywords.stream().anyMatch(entry.filePath()::contains)) {
                positionen.add(position);
            }
        }
        return positionen;
    }

    /**
     * Passende Seite zur Route. Ohne genauen Treffer wird hinten gekuerzt:
     * "/emails/inbox/42" -> "/emails/inbox" -> "/emails". Ohne regulaeren Ausdruck und mit
     * Laengengrenze: Die Route kommt ungeprueft aus dem Chat-Request (ReDoS).
     */
    static List<String> dateienZurRoute(String route) {
        if (route == null || route.isBlank() || route.length() > MAX_ROUTE) return List.of();
        int ende = route.length();
        while (ende > 0 && route.charAt(ende - 1) == '/') ende--;
        String[] teile = route.substring(0, ende).split("/");
        for (int laenge = teile.length; laenge > 1; laenge--) {
            for (RoutenMuster muster : ROUTEN) {
                if (muster.passt(teile, laenge)) return muster.dateien();
            }
        }
        return List.of();
    }

    /** Eine Route aus {@link #ROUTE_TO_FILE_KEYWORDS}, vorab zerlegt. */
    private record RoutenMuster(String route, String[] teile, List<String> dateien) {

        boolean hatPlatzhalter() {
            return route.contains("/:");
        }

        boolean passt(String[] pfad, int laenge) {
            if (teile.length != laenge) return false;
            for (int i = 0; i < laenge; i++) {
                if (!teile[i].startsWith(":") && !teile[i].equals(pfad[i])) return false;
            }
            // Ein Platzhalter passt nur, wenn es auch einen Pfadteil gibt ("/artikel/" ist keine ID)
            return !teile[laenge - 1].startsWith(":") || !pfad[laenge - 1].isEmpty();
        }
    }

    private static boolean istFrontend(String category) {
        return category.startsWith("frontend") || category.startsWith("zeiterfassung");
    }

    private static boolean istTechnik(String category) {
        return category.startsWith("backend");
    }

    private static String schluessel(Abschnitt a) {
        return a.filePath() + "::" + a.contentHash();
    }

    private static CodeChunkResult treffer(Abschnitt a, boolean aktuelleSeite) {
        return new CodeChunkResult(schluessel(a), a.content(), a.filePath(), a.category(), a.chunkType(),
                a.name(), aktuelleSeite);
    }

    // ── Laden / Indexieren (beim Start, im Hintergrund) ──

    @PostConstruct
    void init() {
        if (!ragEnabled) {
            log.info("Lokales RAG ist deaktiviert (ai.rag.enabled=false)");
            return;
        }
        // Nicht den Start blockieren: Laden bzw. Einbetten dauert Sekunden bis Minuten
        Thread.ofVirtual().name("rag-indexer").start(this::indexAufbauen);
    }

    void indexAufbauen() {
        try {
            Path ordner = Path.of(projektOrdner).toAbsolutePath().normalize();
            Path datei = ragDatei == null || ragDatei.isBlank()
                    ? ordner.resolve(STANDARD_DATEINAME)
                    : Path.of(ragDatei).toAbsolutePath().normalize();

            Map<String, Abschnitt> vorhanden = indexer.laden(datei);
            List<RagIndexer.Rohabschnitt> quellen = indexer.zerlegen(ordner);

            if (quellen.isEmpty()) {
                // Server: nur die mitgelieferte RAG-Datei, kein Quellcode zum Einbetten
                if (vorhanden.isEmpty()) {
                    log.warn("KI-Hilfe: keine RAG-Datei unter {} und kein Quellcode in {} - "
                            + "die KI-Hilfe antwortet ohne Programmwissen", datei, ordner);
                    return;
                }
                bereitstellen(List.copyOf(vorhanden.values()));
                log.info("RAG-Index FERTIG (RAG-Datei {}): {} Abschnitte, bereit fuer Anfragen",
                        datei, wissen.abschnitte().size());
                return;
            }

            String key = systemSettingsService.getGeminiApiKey();
            if (key == null || key.isBlank()) {
                log.warn("Lokales RAG: Gemini API Key fehlt - Quellcode kann nicht eingebettet werden, RAG bleibt aus");
                return;
            }
            log.info("RAG-Index: {} Abschnitte im Quellcode, {} in der RAG-Datei", quellen.size(), vorhanden.size());
            RagIndexer.Ergebnis ergebnis = indexer.aktualisieren(quellen, vorhanden, einbetter, null);
            if (ergebnis.neuEingebettet() > 0 || ergebnis.abschnitte().size() != vorhanden.size()) {
                try {
                    indexer.speichern(datei, ergebnis.abschnitte());
                    log.info("RAG-Datei gespeichert: {} Abschnitte -> {}", ergebnis.abschnitte().size(), datei);
                } catch (IOException e) {
                    log.warn("RAG-Datei konnte nicht gespeichert werden: {}", e.getMessage());
                }
            }
            if (!ergebnis.vollstaendig()) {
                log.warn("RAG-Index unvollstaendig: {} Abschnitte fehlen ({})", ergebnis.fehlend(),
                        ergebnis.zeitlimitErreicht() ? "Zeitlimit" : ergebnis.fehler());
            }
            bereitstellen(ergebnis.abschnitte());
            log.info("RAG-Index FERTIG: {} Abschnitte ({} aus der RAG-Datei, {} neu), bereit fuer Anfragen",
                    wissen.abschnitte().size(), ergebnis.ausVorlage(), ergebnis.neuEingebettet());
        } catch (Exception e) {
            log.error("RAG-Index Aufbau fehlgeschlagen: {}", e.getMessage(), e);
        }
    }

    private void bereitstellen(List<Abschnitt> abschnitte) {
        List<Abschnitt> kopie = List.copyOf(abschnitte);
        // Wie beim Einbetten: Dateipfad und Name helfen der Suche ("ProjektEditor" ~ Projekte)
        StichwortIndex stichworte = StichwortIndex.aus(kopie.stream()
                .map(a -> new StichwortIndex.Text(a.filePath() + " " + a.name(), a.content()))
                .toList());
        wissen = new Wissen(kopie, stichworte);
        ready = !kopie.isEmpty();
    }

    // ── Math ──

    private static double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0 : dot / denom;
    }
}
