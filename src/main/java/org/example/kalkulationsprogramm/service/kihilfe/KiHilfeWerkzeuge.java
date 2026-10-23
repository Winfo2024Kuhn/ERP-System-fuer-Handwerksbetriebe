package org.example.kalkulationsprogramm.service.kihilfe;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.service.LocalRagService;
import org.example.kalkulationsprogramm.service.LocalRagService.Bereich;
import org.example.kalkulationsprogramm.service.LocalRagService.CodeChunkResult;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Werkzeuge, mit denen die KI-Hilfe selbst im Programm nachschlaegt (Gemini Function Calling):
 * {@value #SUCHEN} und {@value #LESEN}.
 * <p>
 * Ein Objekt je Chat-Anfrage. Es merkt sich, welche Abschnitte die KI schon kennt (keine
 * Wiederholungen), und begrenzt, wie viel sie insgesamt liest (Kosten und Antwortzeit). Jeder
 * Werkzeug-Aufruf meldet einen Schritt, den der Nutzer live sieht ("Schaut nach: ...").
 * Fehler gehen als Hinweis an die KI zurueck, damit sie es anders versuchen kann.
 */
@Slf4j
public final class KiHilfeWerkzeuge {

    public static final String SUCHEN = "im_programm_suchen";
    public static final String LESEN = "datei_lesen";

    static final int TREFFER_JE_SUCHE = 4;
    static final int MAX_ZEICHEN_DATEI = 20_000;
    /** Mehr liest die KI je Frage nicht nach - danach soll sie antworten. */
    static final int MAX_ZEICHEN_GESAMT = 100_000;
    /** Darunter lohnt keine weitere Suche mehr. */
    private static final int MIN_RESTBUDGET = 2_000;
    static final int MAX_SUCHBEGRIFFE = 300;
    static final int MAX_HINWEIS = 100;

    private static final String BUDGET_ERSCHOEPFT =
            "Genug nachgeschlagen. Antworte jetzt mit dem, was du sicher weisst.";

    private final LocalRagService rag;
    private final ObjectMapper objectMapper;
    private final Consumer<String> schritt;
    private final Set<String> gesehen = new HashSet<>();
    private int gelieferteZeichen;

    public KiHilfeWerkzeuge(LocalRagService rag, ObjectMapper objectMapper, Consumer<String> schritt) {
        this.rag = rag;
        this.objectMapper = objectMapper;
        this.schritt = schritt;
    }

    /** Abschnitte, die die KI schon hat (z. B. aus der ersten Suche), kommen nicht noch einmal. */
    public void merkeGesehen(List<CodeChunkResult> treffer) {
        for (CodeChunkResult t : treffer) {
            gesehen.add(t.schluessel());
            gelieferteZeichen += t.content().length();
        }
    }

    /** Die Werkzeug-Beschreibungen fuer Gemini ({@code functionDeclarations}). */
    public ArrayNode deklarationen() {
        ArrayNode deklarationen = objectMapper.createArrayNode();

        ObjectNode suchen = parameter();
        ObjectNode suchenFelder = (ObjectNode) suchen.get("properties");
        suchenFelder.putObject("suchbegriffe").put("type", "STRING").put("description",
                "Woerter, nach denen gesucht wird. Mehrere Varianten helfen: Fachbegriffe, Beschriftungen "
                        + "von Knoepfen und Reitern, Seitennamen, Synonyme. Beispiel: "
                        + "'Rechnung stornieren Storno Stornorechnung Gutschrift'.");
        ObjectNode bereich = suchenFelder.putObject("bereich");
        bereich.put("type", "STRING").put("description",
                "'bedienung' (Standard): Anleitungen und Bildschirmseiten, also was der Nutzer sieht und anklickt. "
                        + "'technik': Regeln und Pruefungen im Hintergrund, z. B. warum etwas gesperrt ist "
                        + "oder welche Fehlermeldung kommt. 'alles': beides.");
        bereich.put("format", "enum");
        bereich.putArray("enum").add("bedienung").add("technik").add("alles");
        hinweisFeld(suchenFelder, "Wie man eine Rechnung storniert");
        suchen.putArray("required").add("suchbegriffe").add("hinweis");
        deklarationen.add(deklaration(SUCHEN,
                "Sucht in der Wissensbasis des Programms (Anleitungen, Bildschirmseiten, Regeln im Hintergrund) "
                        + "nach passenden Abschnitten. Liefert nur Abschnitte, die du in diesem Gespraech noch "
                        + "nicht bekommen hast. Nutze es, wenn das Bisherige fuer eine sichere Antwort nicht reicht.",
                suchen));

        ObjectNode lesen = parameter();
        ObjectNode lesenFelder = (ObjectNode) lesen.get("properties");
        lesenFelder.putObject("datei").put("type", "STRING").put("description",
                "Dateipfad genau so, wie er in einem Treffer unter 'datei' oder im Kontext steht.");
        hinweisFeld(lesenFelder, "Die Seite Projekte");
        lesen.putArray("required").add("datei").add("hinweis");
        deklarationen.add(deklaration(LESEN,
                "Liest eine Datei aus den Treffern komplett (bis etwa " + MAX_ZEICHEN_DATEI + " Zeichen), z. B. "
                        + "eine ganze Bildschirmseite, wenn ein Treffer nur einen Teil zeigt und du alle Knoepfe, "
                        + "Reiter oder Dialoge sehen musst.",
                lesen));
        return deklarationen;
    }

    /** Fuehrt einen Werkzeug-Aufruf ({@code functionCall}) aus und liefert die Antwort fuer Gemini. */
    public ObjectNode ausfuehren(JsonNode aufruf) {
        JsonNode args = aufruf.path("args");
        return switch (aufruf.path("name").asString("")) {
            case SUCHEN -> suchen(args);
            case LESEN -> lesen(args);
            default -> hinweis("Dieses Werkzeug gibt es nicht. Verfuegbar sind " + SUCHEN + " und " + LESEN + ".");
        };
    }

    /** Antwort auf Aufrufe ueber der Grenze je Runde - Gemini erwartet auf jeden Aufruf eine Antwort. */
    public ObjectNode zuVieleAufrufe() {
        return hinweis("Zu viele Suchen auf einmal. Nutze erst die Ergebnisse der anderen Aufrufe.");
    }

    /** Antwort, wenn die Zeit zum Nachschlagen um ist - die KI soll jetzt antworten. */
    public ObjectNode keineZeitMehr() {
        return hinweis("Keine Zeit mehr zum Nachschlagen. Antworte jetzt mit dem, was du sicher weisst.");
    }

    private ObjectNode suchen(JsonNode args) {
        String begriffe = kuerzen(args.path("suchbegriffe").asString("").strip(), MAX_SUCHBEGRIFFE);
        if (begriffe.isBlank()) return hinweis("Bitte Suchbegriffe angeben.");
        if (restbudget() < MIN_RESTBUDGET) return hinweis(BUDGET_ERSCHOEPFT);

        schritt.accept("Schaut nach: " + anzeigeText(args.path("hinweis").asString(""), begriffe));
        List<CodeChunkResult> treffer;
        try {
            treffer = rag.suchen(begriffe, bereich(args.path("bereich").asString("")), TREFFER_JE_SUCHE, gesehen);
        } catch (RuntimeException e) {
            log.warn("KI-Hilfe: Werkzeug-Suche fehlgeschlagen ({})", e.getClass().getSimpleName());
            return hinweis("Die Suche ging gerade nicht. Antworte mit dem, was du schon weisst.");
        }

        ObjectNode ergebnis = objectMapper.createObjectNode();
        ArrayNode liste = ergebnis.putArray("treffer");
        for (CodeChunkResult t : treffer) {
            if (t.content().length() > restbudget()) break;
            ObjectNode eintrag = liste.addObject();
            eintrag.put("datei", t.filePath());
            eintrag.put("abschnitt", t.name());
            eintrag.put("art", LocalRagService.art(t.category(), t.filePath()));
            eintrag.put("inhalt", t.content());
            merke(t);
        }
        if (liste.isEmpty()) {
            ergebnis.put("hinweis", treffer.isEmpty()
                    ? "Keine neuen Treffer. Versuche andere Begriffe (Synonyme, Beschriftungen) oder einen anderen Bereich."
                    : BUDGET_ERSCHOEPFT);
        }
        return ergebnis;
    }

    private ObjectNode lesen(JsonNode args) {
        String datei = args.path("datei").asString("").strip();
        List<CodeChunkResult> teile;
        try {
            teile = rag.dateiLesen(datei);
        } catch (RuntimeException e) {
            log.warn("KI-Hilfe: Datei lesen fehlgeschlagen ({})", e.getClass().getSimpleName());
            return hinweis("Das Lesen ging gerade nicht. Antworte mit dem, was du schon weisst.");
        }
        if (teile.isEmpty()) {
            return hinweis("Diese Datei gibt es in der Wissensbasis nicht. Nimm einen Pfad genau so, "
                    + "wie er in einem Treffer unter 'datei' steht.");
        }
        if (teile.stream().allMatch(t -> gesehen.contains(t.schluessel()))) {
            return hinweis("Diese Datei kennst du schon vollstaendig. Nutze, was du oben bekommen hast.");
        }
        int grenze = Math.min(MAX_ZEICHEN_DATEI, restbudget());
        if (grenze < MIN_RESTBUDGET) return hinweis(BUDGET_ERSCHOEPFT);

        StringBuilder inhalt = new StringBuilder();
        boolean gekuerzt = false;
        for (CodeChunkResult teil : teile) {
            if (inhalt.length() + teil.content().length() > grenze) {
                gekuerzt = true;
                break;
            }
            inhalt.append(teil.content()).append("\n\n");
            gesehen.add(teil.schluessel());
        }
        if (inhalt.isEmpty()) return hinweis(BUDGET_ERSCHOEPFT);
        // Alles Gelieferte zaehlt, auch schon bekannte Teile - sonst liesse sich das Budget umgehen
        gelieferteZeichen += inhalt.length();
        schritt.accept("Liest genauer: " + anzeigeText(args.path("hinweis").asString(""), "eine Seite des Programms"));
        ObjectNode ergebnis = objectMapper.createObjectNode();
        ergebnis.put("datei", datei);
        ergebnis.put("art", LocalRagService.art(teile.get(0).category(), teile.get(0).filePath()));
        ergebnis.put("inhalt", inhalt.toString().strip());
        if (gekuerzt) {
            ergebnis.put("hinweis", "Die Datei ist laenger. Den Rest findest du gezielt mit " + SUCHEN + ".");
        }
        return ergebnis;
    }

    private void merke(CodeChunkResult t) {
        if (gesehen.add(t.schluessel())) gelieferteZeichen += t.content().length();
    }

    private int restbudget() {
        return Math.max(0, MAX_ZEICHEN_GESAMT - gelieferteZeichen);
    }

    private static Bereich bereich(String wert) {
        return switch (wert.strip().toLowerCase(Locale.ROOT)) {
            case "technik" -> Bereich.TECHNIK;
            case "alles" -> Bereich.ALLES;
            default -> Bereich.BEDIENUNG;
        };
    }

    /**
     * Text fuer die Anzeige im Chat: eine Zeile, ohne Steuerzeichen, gekuerzt. Fehlt er, gilt
     * {@code ersatz}. Die Oberflaeche zeigt ihn als reinen Text (kein HTML, kein Markdown).
     */
    public static String anzeigeText(String text, String ersatz) {
        String bereinigt = einzeilig(text);
        return kuerzen(bereinigt.isEmpty() ? einzeilig(ersatz) : bereinigt, MAX_HINWEIS);
    }

    private static String einzeilig(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(Math.min(text.length(), 4 * MAX_HINWEIS));
        boolean leer = false;
        for (int i = 0; i < text.length() && sb.length() <= 4 * MAX_HINWEIS; i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c) || Character.getType(c) == Character.FORMAT) {
                leer = !sb.isEmpty();
            } else {
                if (leer) sb.append(' ');
                sb.append(c);
                leer = false;
            }
        }
        return sb.toString();
    }

    static String kuerzen(String text, int max) {
        if (text.length() <= max) return text;
        int ende = max - 1;
        // Kein halbes Emoji (Surrogatpaar) am Ende
        if (Character.isHighSurrogate(text.charAt(ende - 1))) ende--;
        return text.substring(0, ende).strip() + "…";
    }

    private ObjectNode hinweis(String text) {
        return objectMapper.createObjectNode().put("hinweis", text);
    }

    private ObjectNode parameter() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "OBJECT");
        schema.putObject("properties");
        return schema;
    }

    private static void hinweisFeld(ObjectNode felder, String beispiel) {
        felder.putObject("hinweis").put("type", "STRING").put("description",
                "Ein kurzer Satz in einfacher Sprache, was du nachschaust. Der Nutzer sieht ihn live, z. B. '"
                        + beispiel + "'. Keine Dateinamen, hoechstens " + MAX_HINWEIS + " Zeichen.");
    }

    private ObjectNode deklaration(String name, String beschreibung, ObjectNode parameter) {
        ObjectNode d = objectMapper.createObjectNode();
        d.put("name", name);
        d.put("description", beschreibung);
        d.set("parameters", parameter);
        return d;
    }
}
