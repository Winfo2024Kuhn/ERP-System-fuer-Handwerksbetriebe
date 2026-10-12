package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.service.kihilfe.GeminiEinbetter;
import org.example.kalkulationsprogramm.service.kihilfe.RagIndexer;
import org.example.kalkulationsprogramm.service.kihilfe.RagIndexer.Abschnitt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wissen der KI-Hilfe (RAG): Vektor-Suche im Prozess, ohne Qdrant oder andere Dienste.
 * <p>
 * Zwei Betriebsarten, beim Start automatisch gewaehlt:
 * <ul>
 *   <li><b>Server (Docker-Image):</b> Kein Quellcode vorhanden. Geladen wird die RAG-Datei, die
 *       GitHub beim Image-Bau erzeugt ({@code ai.rag.datei}, siehe RAG_INDEXING.md). Sie kommt mit
 *       jedem Nachtupdate neu; der Server bettet selbst nichts ein.</li>
 *   <li><b>Entwicklung:</b> Quellcode liegt im Projektordner. Geaenderte Abschnitte werden mit dem
 *       Gemini-Key aus den System-Einstellungen eingebettet und in der RAG-Datei gespeichert.</li>
 * </ul>
 * Fragen werden immer mit dem Gemini-Key des Betriebs eingebettet.
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
            Map.entry("/leistungen", List.of("Leistungseditor")),
            Map.entry("/arbeitsgaenge", List.of("ArbeitsgangEditor")),
            Map.entry("/produktkategorien", List.of("ProduktkategorieEditor")),
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

    private final RagIndexer indexer;
    private final GeminiEinbetter einbetter;
    private final SystemSettingsService systemSettingsService;

    /** Wird beim Laden komplett ersetzt, nie veraendert - Suchen laufen ohne Sperre. */
    private volatile List<Abschnitt> wissen = List.of();
    private volatile boolean ready = false;

    @Value("${ai.rag.enabled:false}")
    private boolean ragEnabled;

    @Value("${ai.rag.top-k:10}")
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

    // ── Data structures ──

    public record CodeChunkResult(
            String content, String filePath, String category,
            String chunkType, String name, double score
    ) {}

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
     * Search for relevant code chunks by embedding the query and doing cosine similarity.
     * Always includes chunks from the current page's source file so the LLM knows
     * exactly what the user sees on screen.
     *
     * @param query       The user's question
     * @param pageContext A hint like "Seite: PROJEKTE (/projekte)" – used to enrich the query embedding
     * @param currentRoute The frontend route, e.g. "/projekte" – used to deterministically include that page's code
     */
    public List<CodeChunkResult> search(String query, String pageContext, String currentRoute)
            throws IOException, InterruptedException {
        List<Abschnitt> store = wissen;
        if (!ready || store.isEmpty()) return List.of();

        String enrichedQuery = pageContext != null && !pageContext.isBlank()
                ? query + "\n\nSeitenkontext: " + pageContext
                : query;

        long t0 = System.currentTimeMillis();
        float[] queryVec = einbetter.anfrageEinbetten(enrichedQuery);
        log.info("    Embedding erstellt in {} ms", System.currentTimeMillis() - t0);

        long t1 = System.currentTimeMillis();

        // 1) Die Seite, auf der der Nutzer gerade ist, kommt immer mit. Grosse Seiten bestehen aus
        //    vielen Teilen - genommen werden die, die am besten zur Frage passen, nicht der Dateianfang.
        int pageSlots = Math.max(1, topK / 3);
        List<CodeChunkResult> currentPageChunks = findChunksForRoute(store, currentRoute).stream()
                .sorted(Comparator.comparingDouble((Abschnitt a) -> cosineSimilarity(queryVec, a.vector())).reversed())
                .limit(pageSlots)
                .map(a -> new CodeChunkResult(a.content(), a.filePath(), a.category(), a.chunkType(), a.name(),
                        1.0)) // max score for current page
                .toList();
        pageSlots = currentPageChunks.size();
        Set<String> currentPagePaths = new HashSet<>();
        for (CodeChunkResult c : currentPageChunks) {
            currentPagePaths.add(c.filePath() + "::" + c.name());
        }
        if (!currentPageChunks.isEmpty()) {
            log.info("    Aktuelle Seite: {} Chunks deterministisch eingebunden ({})",
                    currentPageChunks.size(), currentPageChunks.stream().map(CodeChunkResult::name).toList());
        }

        // 2) Score all remaining chunks via cosine similarity
        List<CodeChunkResult> similarityResults = new ArrayList<>();
        for (Abschnitt entry : store) {
            String key = entry.filePath() + "::" + entry.name();
            if (currentPagePaths.contains(key)) continue; // already included deterministically

            double score = cosineSimilarity(queryVec, entry.vector());
            if (score >= scoreThreshold) {
                similarityResults.add(new CodeChunkResult(
                        entry.content(), entry.filePath(), entry.category(),
                        entry.chunkType(), entry.name(), score
                ));
            }
        }
        similarityResults.sort(Comparator.comparingDouble(CodeChunkResult::score).reversed());

        // 3) Reserve slots: current page gets priority (up to a third), rest filled by similarity with frontend/backend balance
        int remainingSlots = Math.max(0, topK - pageSlots);

        // Split similarity results into frontend vs backend
        List<CodeChunkResult> frontendSimilarity = new ArrayList<>();
        List<CodeChunkResult> otherSimilarity = new ArrayList<>();
        for (CodeChunkResult r : similarityResults) {
            if (istFrontend(r.category())) {
                frontendSimilarity.add(r);
            } else {
                otherSimilarity.add(r);
            }
        }

        // At least 30% of remaining slots for frontend (on top of current page chunks which are already frontend)
        int frontendSlots = Math.min(frontendSimilarity.size(), Math.min(remainingSlots, Math.max(remainingSlots * 3 / 10, 1)));
        int backendSlots = Math.max(0, remainingSlots - frontendSlots);

        List<CodeChunkResult> finalResults = new ArrayList<>();
        // Current page chunks first (highest priority)
        finalResults.addAll(currentPageChunks);
        // Then frontend similarity
        finalResults.addAll(frontendSimilarity.subList(0, Math.min(frontendSlots, frontendSimilarity.size())));
        // Then backend/other similarity
        finalResults.addAll(otherSimilarity.subList(0, Math.min(backendSlots, otherSimilarity.size())));

        // Fill remaining slots from whichever pool has leftovers
        if (finalResults.size() < topK) {
            int remaining = topK - finalResults.size();
            // Try more frontend first
            for (int i = frontendSlots; i < frontendSimilarity.size() && remaining > 0; i++) {
                finalResults.add(frontendSimilarity.get(i));
                remaining--;
            }
            // Then more backend
            for (int i = backendSlots; i < otherSimilarity.size() && remaining > 0; i++) {
                finalResults.add(otherSimilarity.get(i));
                remaining--;
            }
        }

        log.info("    Lokale Vektor-Suche: {} Treffer in {} ms (seiten-chunks={}, top-k={}, threshold={}, store={})",
                finalResults.size(), System.currentTimeMillis() - t1, pageSlots, topK, scoreThreshold, store.size());
        return finalResults;
    }

    /**
     * Backward-compatible search without route.
     */
    public List<CodeChunkResult> search(String query, String pageContext) throws IOException, InterruptedException {
        return search(query, pageContext, null);
    }

    /**
     * Alle Abschnitte der Seite(n) zur Route. Nur Frontend-Dateien: "Monatsabschluss" steckt auch
     * in MonatsabschlussService.java - das ist aber nicht, was der Nutzer sieht.
     */
    private List<Abschnitt> findChunksForRoute(List<Abschnitt> store, String route) {
        List<String> keywords = dateienZurRoute(route);
        if (keywords.isEmpty()) return List.of();

        List<Abschnitt> pageChunks = new ArrayList<>();
        for (Abschnitt entry : store) {
            if (istFrontend(entry.category()) && keywords.stream().anyMatch(entry.filePath()::contains)) {
                pageChunks.add(entry);
            }
        }
        return pageChunks;
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

    /**
     * Build formatted context string from search results for the LLM prompt.
     * Chunks with score 1.0 are from the current page (deterministically included).
     */
    public String buildContextFromResults(List<CodeChunkResult> results) {
        if (results.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            CodeChunkResult r = results.get(i);
            String label = r.score() >= 1.0
                    ? ">>> AKTUELLE SEITE DES BENUTZERS <<<"
                    : "Score: %.2f".formatted(r.score());
            sb.append("### [%d] %s -- %s (%s) | %s\n".formatted(
                    i + 1, r.filePath(), r.name(), r.chunkType(), label));
            sb.append("```\n").append(r.content()).append("\n```\n\n");
        }
        return sb.toString();
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
                log.info("RAG-Index FERTIG (RAG-Datei {}): {} Abschnitte, bereit fuer Anfragen", datei, wissen.size());
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
                    wissen.size(), ergebnis.ausVorlage(), ergebnis.neuEingebettet());
        } catch (Exception e) {
            log.error("RAG-Index Aufbau fehlgeschlagen: {}", e.getMessage(), e);
        }
    }

    private void bereitstellen(List<Abschnitt> abschnitte) {
        wissen = List.copyOf(abschnitte);
        ready = !wissen.isEmpty();
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
