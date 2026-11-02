package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.example.kalkulationsprogramm.domain.Firmeninformation;
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterFehler;
import org.example.kalkulationsprogramm.service.ki.KiAnfrage;
import org.example.kalkulationsprogramm.service.ki.KiDienst;
import org.example.kalkulationsprogramm.service.ki.KiErgebnis;
import org.example.kalkulationsprogramm.service.ki.KiFaehigkeit;
import org.example.kalkulationsprogramm.service.ki.KiFunktion;
import org.example.kalkulationsprogramm.service.ki.KiNachricht;
import org.example.kalkulationsprogramm.service.ki.KiNichtVerfuegbar;
import org.example.kalkulationsprogramm.service.ki.KiQuelle;
import org.example.kalkulationsprogramm.service.ki.KiTeil;
import org.example.kalkulationsprogramm.service.kihilfe.KiHilfeHinweistext;
import org.example.kalkulationsprogramm.service.kihilfe.KiHilfeWerkzeuge;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class KiHilfeService {

    /** Frontend context sent alongside chat messages for RAG enrichment. */
    public record PageContext(String route, String pageTitle, String visibleContent, String errorMessages, Double latitude, Double longitude) {}

    /** Antwort der KI-Hilfe mit Web-Quellen (Google-Suche) und den Schritten, die sie dafuer gegangen ist. */
    public record ChatResult(String reply, List<SourceLink> sources, List<String> schritte) {}
    public record SourceLink(String title, String url) {}

    /** Begrenzt den Firmennamen im System-Prompt. */
    private static final int MAX_BETRIEBSNAME_LAENGE = 120;

    /** Hoechstens so viele Nachschlage-Runden; danach muss die KI antworten. */
    static final int MAX_NACHSCHLAGE_RUNDEN = 4;
    /** Weitere gleichzeitige Werkzeug-Aufrufe einer Runde werden nicht ausgefuehrt. */
    static final int MAX_AUFRUFE_JE_RUNDE = 3;
    /** Hoechstens so viele Internet-Suchen werden dem Nutzer als Schritt gezeigt. */
    private static final int MAX_WEBSUCHEN_ANZEIGE = 3;
    /** Bis zu so vielen Woertern gilt eine Frage als Nachfrage ("Und bei Anfragen?"). */
    private static final int MAX_WOERTER_NACHFRAGE = 8;
    /** Danach wird nicht mehr nachgeschlagen, sondern geantwortet - der Nutzer wartet. */
    private static final Duration NACHSCHLAGEN_BIS = Duration.ofSeconds(75);
    /** Laengste Wartezeit auf eine einzelne Antwort der KI. */
    private static final Duration KI_ZEITLIMIT = Duration.ofSeconds(60);
    /** So viel Zeit muss fuer die Antwort selbst bleiben, sonst wird nicht mehr nachgeschlagen. */
    private static final Duration RESERVE_FUER_ANTWORT = KI_ZEITLIMIT;

    /** Schritt, den der Nutzer nach der ersten, automatischen Suche sieht. */
    static final String SCHRITT_ERSTE_SUCHE = "Passende Stellen im Programm gesucht";

    private final ObjectMapper objectMapper;
    private final CodebaseIndexService codebaseIndexService;
    private final LocalRagService localRagService;
    private final KiDienst kiDienst;
    private final FirmeninformationRepository firmeninformationRepository;

    /** Pause vor dem zweiten Versuch, wenn die KI kurz ueberlastet ist; in Tests ohne Warten. */
    private Duration wiederholungsPause = Duration.ofSeconds(1);

    /**
     * So lange darf eine Frage hoechstens dauern, ueber alle Runden und den Rueckfall hinweg. Kuerzer
     * als das Zeitlimit des Chats ({@code KiHilfeController}), damit der Nutzer eine Meldung bekommt.
     */
    private Duration gesamtZeitlimit = Duration.ofSeconds(150);

    @Value("${ai.ki-hilfe.temperature:0.4}")
    private double temperature;

    @Value("${ai.ki-hilfe.max-output-tokens:8192}")
    private int maxOutputTokens;

    @Value("${ai.ki-hilfe.web-search-enabled:true}")
    private boolean webSearchEnabled;

    public record ChatMessage(String role, String text) {}

    /** System-Prompt ohne Werkzeuge (z. B. wenn die Wissensbasis noch laedt). */
    String buildSystemPrompt(String ragContext) {
        return buildSystemPrompt(ragContext, false);
    }

    String buildSystemPrompt(String ragContext, boolean mitWerkzeugen) {
        return buildSystemPrompt(ragContext, mitWerkzeugen, true);
    }

    /**
     * System-Prompt mit den automatisch gefundenen Abschnitten (falls vorhanden). Ohne Internet-Suche
     * (der Anbieter kann sie nicht) sagt er der KI ehrlich, dass sie nichts nachsehen kann. Sonst haengt ohne
     * Werkzeuge der vollstaendige Codebase-Index als Rueckfall an; mit Werkzeugen nicht - er ginge
     * in jeder Runde neu mit, und die KI kann selbst suchen.
     */
    String buildSystemPrompt(String ragContext, boolean mitWerkzeugen, boolean mitInternet) {
        KiHilfeHinweistext.WebTexte web = KiHilfeHinweistext.web(mitInternet);
        String basisPrompt = KiHilfeHinweistext.basis().replace("{{BETRIEB}}", betriebsBezeichnung())
                .replace("{{WEB_STIL}}\n", web.stil())
                .replace("{{WEB_ABSCHNITT}}\n", web.abschnitt());
        if (mitWerkzeugen) {
            basisPrompt += KiHilfeHinweistext.nachschlagen().replace("{{RUNDEN}}", String.valueOf(MAX_NACHSCHLAGE_RUNDEN))
                    .replace("{{WEB_NACHSCHLAGEN}}\n", web.nachschlagen());
        }
        if (ragContext != null && !ragContext.isBlank()) {
            return basisPrompt +
                    "\n\n## Automatisch gefundene Abschnitte\n" +
                    "Die folgenden Abschnitte wurden automatisch als passend zur aktuellen Frage gefunden. " +
                    "Jeder ist markiert: 'Anleitung fuer Nutzer', 'Bildschirm (was der Nutzer sieht)', " +
                    "'Technische Beschreibung' oder 'Server-Regel (fuer den Nutzer nicht sichtbar)'. " +
                    "Was der Nutzer anklickt, steht nur in der Anleitung und im Bildschirm-Code. " +
                    "Abschnitte mit '>>> AKTUELLE SEITE DES BENUTZERS <<<' zeigen den EXAKTEN Quellcode der Seite, " +
                    "auf der sich der Benutzer GERADE befindet. Diese Abschnitte haben HOECHSTE Prioritaet: " +
                    "Nur Buttons, Tabs, Formulare und Funktionen die IN DIESEM CODE vorkommen, existieren auf dieser Seite. " +
                    "Beschreibe NUR das, was du im Code der aktuellen Seite tatsaechlich siehst.\n\n" +
                    ragContext;
        }

        if (mitWerkzeugen) {
            return basisPrompt;
        }

        // Fallback: full codebase index
        String index = codebaseIndexService.getIndex();
        if (index == null || index.isEmpty()) {
            return basisPrompt;
        }
        return basisPrompt +
                "\n\n## Frontend-Quellcode & Dokumentation (Read-Only Wissensbasis)\n" +
                "Im Folgenden findest du den KOMPLETTEN Frontend-Quellcode des Kalkulationsprogramms: " +
                "Alle React-Seiten (Pages), UI-Komponenten, Navigation (App.tsx Routing), " +
                "Hooks, Hilfsfunktionen und die Projekt-Dokumentation. " +
                "Nutze diesen Code, um Benutzern zu erklären, wie sie sich im Programm " +
                "zurechtfinden, welche Funktionen auf welcher Seite verfügbar sind, " +
                "und wie Workflows Schritt für Schritt ablaufen.\n\n" +
                index;
    }

    /** Betriebsname aus den Firmendaten; neutraler Fallback, solange keiner hinterlegt ist. */
    private String betriebsBezeichnung() {
        String name = firmeninformationRepository.findFirmeninformation()
                .map(Firmeninformation::getFirmenname)
                .map(n -> n.replaceAll("[\\p{Cntrl}\\p{Z}]+", " ").trim())
                .map(n -> n.length() > MAX_BETRIEBSNAME_LAENGE ? n.substring(0, MAX_BETRIEBSNAME_LAENGE) : n)
                .orElse("");
        return name.isEmpty()
                ? "das Kalkulationsprogramm deines Handwerksbetriebs"
                : "das Kalkulationsprogramm des Betriebs " + name;
    }

    /**
     * Chat ohne Live-Schritte.
     */
    public ChatResult chat(List<ChatMessage> messages, PageContext pageContext) throws IOException, InterruptedException {
        return chat(messages, pageContext, schritt -> { });
    }

    /**
     * Beantwortet die letzte Frage. Zuerst sucht das Programm selbst passende Abschnitte; reichen
     * sie der KI nicht, schlaegt sie ueber Werkzeuge nach (hoechstens {@value #MAX_NACHSCHLAGE_RUNDEN}
     * Runden), Fachfragen per Internet-Suche, wenn der Anbieter sie kann.
     *
     * @param fortschritt bekommt jeden Schritt als kurzen Text fuer den Nutzer ("Schaut nach: ...")
     */
    public ChatResult chat(List<ChatMessage> messages, PageContext pageContext, Consumer<String> fortschritt)
            throws IOException, InterruptedException {
        // Zur Laufzeit pruefen: Ohne passenden Anbieter (Einstellungen -> KI-Funktionen) geht nichts raus.
        if (!kiDienst.verfuegbar(KiFunktion.PROGRAMM_HILFE)) {
            throw new KeinPassenderAnbieter();
        }

        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("Keine Nachrichten angegeben");
        }

        // Limit conversation history to prevent abuse
        List<ChatMessage> limitedMessages = messages.size() > 20
                ? messages.subList(messages.size() - 20, messages.size())
                : messages;

        List<String> schritte = new ArrayList<>();
        Consumer<String> melden = text -> {
            String schritt = KiHilfeWerkzeuge.anzeigeText(text, "");
            schritte.add(schritt);
            fortschritt.accept(schritt);
        };

        // Die Frist laeuft ab hier - auch das Einbetten der ersten Suche zaehlt
        Frist frist = new Frist(System.nanoTime(), gesamtZeitlimit);
        KiHilfeWerkzeuge werkzeuge = null;
        String ragContext = null;
        if (localRagService.isAvailable()) {
            werkzeuge = new KiHilfeWerkzeuge(localRagService, objectMapper, melden);
            ragContext = ersteSuche(limitedMessages, pageContext, werkzeuge, melden);
        } else {
            log.info("  -> RAG nicht verfuegbar (enabled={}, ready={}), nutze Full-Codebase-Index",
                    localRagService.isEnabled(), localRagService.isReady());
        }

        // Build page context string for the user message
        String pageContextInfo = buildPageContextInfo(pageContext);
        if (pageContextInfo != null) {
            log.info("  -> Seitenkontext ({} Zeichen) wird an Nachricht angehaengt", pageContextInfo.length());
        }

        // Die Internet-Suche gibt es nur, wenn der Anbieter sie kann (Gemini); sonst ohne, und die KI weiss es.
        boolean mitInternet = kiDienst.kann(KiFunktion.PROGRAMM_HILFE, KiFaehigkeit.WEBSUCHE);
        Runde runde = new Runde(webSearchEnabled && mitInternet, melden, schritte, frist);
        // Der Systemtext verspricht die Internet-Suche nur, wenn sie wirklich mitgeht.
        String systemPrompt = buildSystemPrompt(ragContext, werkzeuge != null, runde.websuche());
        try {
            return beantworten(systemPrompt, verlauf(limitedMessages, pageContextInfo), werkzeuge, runde);
        } catch (AnfrageAbgelehnt e) {
            // Sicherheitsnetz: Kann das eingestellte Modell eigene Werkzeuge (zusammen mit der
            // Internet-Suche) nicht, antwortet die KI-Hilfe wie frueher - ohne selbst nachzuschlagen.
            if (werkzeuge == null || e.status != 400) throw e;
            log.warn("Die KI lehnt die Anfrage mit Werkzeugen ab (HTTP 400) - Antwort ohne Nachschlagen. "
                    + "Unterstuetzt das Modell '{}' Function Calling (zusammen mit der Internet-Suche)?",
                    kiDienst.modell(KiFunktion.PROGRAMM_HILFE, KiFunktion.PROGRAMM_HILFE.stufe()).orElse("?"));
            return beantworten(buildSystemPrompt(ragContext, false, runde.websuche()),
                    verlauf(limitedMessages, pageContextInfo), null, runde);
        }
    }

    /** Was fuer alle Runden einer Frage gleich bleibt, auch im Rueckfall ohne Werkzeuge. */
    private record Runde(boolean websuche, Consumer<String> melden, List<String> schritte, Frist frist) {}

    /** Gesamtzeit einer Frage; gemessen ab dem Start, gilt auch fuer den Rueckfall. */
    private record Frist(long startNanos, Duration gesamt) {

        Duration vergangen() {
            return Duration.ofNanos(System.nanoTime() - startNanos);
        }

        Duration rest() {
            return gesamt.minus(vergangen());
        }

        /** Nachschlagen nur in den ersten 75 s und solange genug Zeit fuer die Antwort bleibt. */
        boolean nachschlagenErlaubt() {
            return vergangen().compareTo(NACHSCHLAGEN_BIS) < 0 && rest().compareTo(RESERVE_FUER_ANTWORT) >= 0;
        }
    }

    /** Die Programm-Hilfe darf gerade über keinen Anbieter laufen (Einstellungen → KI-Funktionen). */
    public static final class KeinPassenderAnbieter extends IOException {
        public KeinPassenderAnbieter() {
            super("Für die Programm-Hilfe ist kein passender KI-Anbieter eingerichtet.");
        }
    }

    /** Die Frage hat die Gesamtzeit ueberschritten; der Nutzer soll es noch einmal versuchen. */
    public static final class ZeitlimitErreicht extends IOException {
        public ZeitlimitErreicht() {
            super("Zeitlimit der KI-Hilfe erreicht");
        }
    }

    /** Der Anbieter hat die Anfrage abgelehnt; ein zweiter Versuch mit derselben Anfrage hilft nicht. */
    static final class AnfrageAbgelehnt extends IOException {
        final int status;

        AnfrageAbgelehnt(int status, Throwable ursache) {
            super("KI-Hilfe nicht verfügbar (Fehler " + status + ")", ursache);
            this.status = status;
        }
    }

    /** Erste, automatische Suche; {@code null}, wenn nichts gefunden wurde. */
    private String ersteSuche(List<ChatMessage> verlauf, PageContext pageContext, KiHilfeWerkzeuge werkzeuge,
                              Consumer<String> melden) {
        String contextHint = pageContext != null
                ? "Seite: %s (%s)".formatted(
                        pageContext.pageTitle() != null ? pageContext.pageTitle() : "",
                        pageContext.route() != null ? pageContext.route() : "")
                : null;
        String currentRoute = pageContext != null ? pageContext.route() : null;
        List<LocalRagService.CodeChunkResult> results;
        try {
            results = localRagService.search(suchfrage(verlauf), contextHint, currentRoute);
        } catch (RuntimeException e) {
            log.warn("  [X] RAG-Suche fehlgeschlagen: {}", e.getClass().getSimpleName());
            return null;
        }
        if (results.isEmpty()) {
            log.info("  [X] RAG: Keine passenden Abschnitte gefunden");
            return null;
        }
        werkzeuge.merkeGesehen(results);
        String ragContext = localRagService.buildContextFromResults(results);
        log.info("  [OK] RAG: {} Abschnitte ({} Zeichen Kontext)", results.size(), ragContext.length());
        for (var r : results) {
            log.info("    - {} -- {} ({}{})", r.filePath(), r.name(), r.category(),
                    r.aktuelleSeite() ? ", aktuelle Seite" : "");
        }
        // Ausserhalb des try: Hat der Nutzer den Chat geschlossen, bricht die Meldung hier ab
        melden.accept(SCHRITT_ERSTE_SUCHE);
        return ragContext;
    }

    /**
     * Suchtext fuer die erste Suche: die letzte Frage. Ist sie kurz, ist sie meist eine Nachfrage
     * ("Und bei Anfragen?") - dann kommt die Frage davor dazu, sonst fehlt der Suche das Thema.
     */
    static String suchfrage(List<ChatMessage> verlauf) {
        List<String> fragen = verlauf.stream()
                .filter(m -> "user".equals(m.role()))
                .map(ChatMessage::text)
                .toList();
        if (fragen.isEmpty()) return "";
        String letzte = fragen.get(fragen.size() - 1);
        if (fragen.size() == 1 || letzte.strip().split("\\s++").length > MAX_WOERTER_NACHFRAGE) {
            return letzte;
        }
        return fragen.get(fragen.size() - 2) + "\n" + letzte;
    }

    /** Gespraechsverlauf fuer die KI; der Seitenkontext haengt an der letzten Frage. */
    private static List<KiNachricht> verlauf(List<ChatMessage> messages, String pageContextInfo) {
        List<KiNachricht> verlauf = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            boolean vomNutzer = "user".equals(msg.role());
            String text = msg.text();
            boolean isLastUserMessage = vomNutzer && i == messages.size() - 1;
            if (isLastUserMessage && pageContextInfo != null && !pageContextInfo.isBlank()) {
                text = text + "\n\n" + pageContextInfo;
            }
            verlauf.add(vomNutzer ? KiNachricht.nutzer(text) : KiNachricht.ki(text));
        }
        return verlauf;
    }

    /**
     * Die Nachschlage-Schleife: Die KI antwortet oder ruft Werkzeuge auf; deren Ergebnisse gehen in
     * den Verlauf, und es geht in die naechste Runde. In der letzten Runde sind Werkzeug-Aufrufe
     * abgeschaltet ({@link KiAnfrage.WerkzeugModus#KEINE}) - dann muss eine Antwort kommen.
     */
    private ChatResult beantworten(String systemPrompt, List<KiNachricht> verlauf, KiHilfeWerkzeuge werkzeuge,
                                   Runde gespraech) throws IOException, InterruptedException {
        Frist frist = gespraech.frist();
        Map<String, SourceLink> quellen = new LinkedHashMap<>();
        Set<String> websuchen = new LinkedHashSet<>();
        boolean antwortErzwingen = false;
        for (int runde = 0; ; runde++) {
            boolean nachschlagen = werkzeuge != null && runde < MAX_NACHSCHLAGE_RUNDEN && !antwortErzwingen
                    && frist.nachschlagenErlaubt();
            KiErgebnis ergebnis = senden(systemPrompt, verlauf, werkzeuge, nachschlagen, gespraech);

            websuchenMelden(ergebnis.websuchen(), websuchen, gespraech.melden());
            for (KiQuelle quelle : ergebnis.quellen()) {
                quellen.putIfAbsent(quelle.adresse(), new SourceLink(quelle.titel(), quelle.adresse()));
            }

            List<KiTeil.WerkzeugAufruf> aufrufe = ergebnis.werkzeugAufrufe();
            if (aufrufe.isEmpty() || !nachschlagen) {
                String replyText = ergebnis.text();
                if (replyText.isBlank() && nachschlagen && ergebnis.werkzeugAufrufFehlerhaft()) {
                    // Die KI hat einen Werkzeug-Aufruf verbockt: noch einmal, dann aber mit Antwortpflicht
                    log.warn("  -> KI: kaputter Werkzeug-Aufruf, Antwort ohne weiteres Nachschlagen");
                    antwortErzwingen = true;
                    continue;
                }
                if (replyText.isBlank()) {
                    throw new IOException("Keine Textantwort von der KI erhalten");
                }
                log.info("  -> Antwort nach {} Nachschlage-Runde(n), {} Web-Quellen", runde, quellen.size());
                return new ChatResult(replyText, List.copyOf(quellen.values()), List.copyOf(gespraech.schritte()));
            }

            verlauf.add(ergebnis.alsNachricht());
            List<KiTeil.WerkzeugErgebnis> antworten = new ArrayList<>();
            for (int i = 0; i < aufrufe.size(); i++) {
                KiTeil.WerkzeugAufruf aufruf = aufrufe.get(i);
                // Je Aufruf pruefen: Auch das Einbetten der Werkzeug-Suchen kostet Zeit
                boolean nochZeit = frist.nachschlagenErlaubt();
                antwortErzwingen |= !nochZeit;
                ObjectNode antwort = !nochZeit ? werkzeuge.keineZeitMehr()
                        : i < MAX_AUFRUFE_JE_RUNDE ? werkzeuge.ausfuehren(aufruf) : werkzeuge.zuVieleAufrufe();
                antworten.add(aufruf.ergebnis(antwort));
            }
            verlauf.add(KiNachricht.werkzeugErgebnisse(antworten));
            log.info("  -> Nachschlage-Runde {}: {} Werkzeug-Aufruf(e)", runde + 1, aufrufe.size());
        }
    }

    private KiAnfrage anfrage(String systemPrompt, List<KiNachricht> verlauf, KiHilfeWerkzeuge werkzeuge,
                              boolean nachschlagen, boolean websuche, Duration zeitlimit) {
        KiAnfrage.Bauer bauer = KiAnfrage.mitAnweisung(systemPrompt)
                .nachrichten(verlauf)
                .temperatur(temperature)
                .maxAusgabe(maxOutputTokens)
                .websuche(websuche)
                .zeitlimit(zeitlimit);
        if (werkzeuge != null) {
            bauer.werkzeuge(werkzeuge.deklarationen(),
                    nachschlagen ? KiAnfrage.WerkzeugModus.AUTO : KiAnfrage.WerkzeugModus.KEINE);
        }
        return bauer.bauen();
    }

    /**
     * Eine Runde an die KI. Jeder Aufruf wartet hoechstens {@link #KI_ZEITLIMIT} und nie laenger als
     * die Frist; bei Ueberlastung (429/5xx) gibt es einen zweiten Versuch.
     */
    private KiErgebnis senden(String systemPrompt, List<KiNachricht> verlauf, KiHilfeWerkzeuge werkzeuge,
                              boolean nachschlagen, Runde gespraech) throws IOException, InterruptedException {
        Frist frist = gespraech.frist();
        for (int versuch = 1; ; versuch++) {
            Duration rest = frist.rest();
            if (rest.isNegative() || rest.isZero()) {
                throw new ZeitlimitErreicht();
            }
            Duration zeitlimit = rest.compareTo(KI_ZEITLIMIT) < 0 ? rest : KI_ZEITLIMIT;
            try {
                return kiDienst.sende(KiFunktion.PROGRAMM_HILFE,
                        anfrage(systemPrompt, verlauf, werkzeuge, nachschlagen, gespraech.websuche(), zeitlimit));
            } catch (KiNichtVerfuegbar e) {
                // Die Einstellungen haben sich mitten in der Frage geaendert.
                throw new KeinPassenderAnbieter();
            } catch (KiAnbieterFehler e) {
                if (Thread.interrupted()) {
                    throw new InterruptedException("KI-Hilfe abgebrochen");
                }
                if (e.getCause() instanceof HttpTimeoutException zeit) {
                    if (!frist.rest().isPositive()) throw new ZeitlimitErreicht();
                    throw zeit;
                }
                if (e.status() == 0) {
                    throw new IOException(e.getMessage(), e);
                }
                if (versuch == 1 && e.voruebergehend()) {
                    log.warn("KI-Hilfe: HTTP {} - neuer Versuch", e.status());
                    Thread.sleep(wiederholungsPause.toMillis());
                    continue;
                }
                throw new AnfrageAbgelehnt(e.status(), e);
            }
        }
    }

    /** Neue Internet-Suchen der Runde als Schritt melden; ins Log nur die Anzahl (sie enthalten die Frage). */
    private void websuchenMelden(List<String> suchen, Set<String> bisher, Consumer<String> melden) {
        if (suchen.isEmpty()) return;
        log.info("  -> Internet-Suchen: {}", suchen.size());
        for (String suche : suchen) {
            String text = suche.strip();
            if (text.isEmpty() || bisher.size() >= MAX_WEBSUCHEN_ANZEIGE || !bisher.add(text)) continue;
            melden.accept("Sucht im Internet: " + text);
        }
    }

    /**
     * Formats page context information from the frontend into a string
     * appended to the user message so the LLM knows what screen the user is on.
     */
    private String buildPageContextInfo(PageContext ctx) {
        if (ctx == null) return null;

        StringBuilder sb = new StringBuilder("[Aktueller Bildschirmkontext]\n");
        if (ctx.route() != null && !ctx.route().isBlank()) {
            sb.append("Seite: ").append(ctx.route()).append("\n");
        }
        if (ctx.pageTitle() != null && !ctx.pageTitle().isBlank()) {
            sb.append("Titel: ").append(ctx.pageTitle()).append("\n");
        }
        if (ctx.errorMessages() != null && !ctx.errorMessages().isBlank()) {
            sb.append("Fehlermeldungen: ").append(ctx.errorMessages()).append("\n");
        }
        if (ctx.visibleContent() != null && !ctx.visibleContent().isBlank()) {
            // Limit visible content to prevent abuse
            String content = ctx.visibleContent();
            if (content.length() > 2000) {
                content = content.substring(0, 2000) + "…";
            }
            sb.append("Sichtbare Daten:\n").append(content).append("\n");
        }
        if (ctx.latitude() != null && ctx.longitude() != null) {
            sb.append("Standort (Geodaten): Breitengrad ").append(ctx.latitude())
              .append(", Laengengrad ").append(ctx.longitude()).append("\n");
        }
        return sb.toString();
    }
}
