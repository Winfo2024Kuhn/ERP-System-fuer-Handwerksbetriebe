package org.example.kalkulationsprogramm.service.kihilfe;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonEncoding;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Wissensbasis der KI-Hilfe (RAG): zerlegt Doku und Quellcode in Abschnitte, bettet sie per
 * {@link Einbetter} ein und liest/schreibt die RAG-Datei.
 * <p>
 * Eine Implementierung fuer beide Wege, damit die Pruefsummen immer zusammenpassen:
 * {@code LocalRagService} (Server) und {@code StandaloneRagIndexer} (Image-Bau in GitHub, lokal
 * per {@code rag-index.sh}). Ein Abschnitt, dessen Inhalt sich nicht geaendert hat, wird nie neu
 * eingebettet - die Datei der Vorversion dient als Vorlage.
 */
@Slf4j
public final class RagIndexer {

    /** Bei anderem Modell oder anderer Dimension passen alte Vektoren nicht mehr. */
    public static final String MODELL = "gemini-embedding-001";
    public static final int DIMENSIONEN = 768;
    static final int FORMAT = 2;

    /** Obergrenze je Abschnitt; Groesseres wird an Zeilengrenzen in Teile zerlegt. */
    static final int MAX_ZEICHEN = 6000;
    static final int STAPEL_GROESSE = 20;

    private static final String JAVA_BASIS = "src/main/java/org/example/kalkulationsprogramm";

    /**
     * Welche Ordner in die Wissensbasis kommen - in dieser Reihenfolge. Was vorne steht, wird
     * zuerst eingebettet: Reicht die Zeit beim Image-Bau nicht, fehlen zuerst Backend-Details,
     * nie die Benutzer-Doku.
     */
    private static final List<Quelle> QUELLEN = List.of(
            new Quelle("docs/benutzer", ".md", "documentation", 2),
            new Quelle("docs", ".md", "documentation", 1),
            new Quelle("deployment/docker-server", ".md", "documentation", 1),
            new Quelle("react-pc-frontend/src/App.tsx", null, "frontend-routing", 0),
            new Quelle("react-pc-frontend/src/pages", ".tsx", "frontend-page", 3),
            new Quelle("react-zeiterfassung/src/pages", ".tsx", "zeiterfassung-page", 3),
            new Quelle("react-pc-frontend/src/components", ".tsx", "frontend-component", 4),
            new Quelle("react-zeiterfassung/src/components", ".tsx", "zeiterfassung-component", 3),
            new Quelle("react-pc-frontend/src/components", ".ts", "frontend-component", 4),
            new Quelle("react-pc-frontend/src/types.ts", null, "frontend-types", 0),
            new Quelle(JAVA_BASIS + "/controller", ".java", "backend-controller", 3),
            new Quelle(JAVA_BASIS + "/service", ".java", "backend-service", 3),
            new Quelle(JAVA_BASIS + "/domain", ".java", "backend-entity", 3),
            new Quelle(JAVA_BASIS + "/dto", ".java", "backend-dto", 4)
    );

    /** Tests gehoeren nicht ins Wissen der KI-Hilfe: sie beschreiben gestellte Situationen. */
    private static final Pattern TESTDATEI = Pattern.compile(
            "(?:Test|Tests|IT)\\.java$|\\.(?:test|spec)\\.tsx?$");

    /** Wert hinter password, secret, api-key, token ... - auch in Anfuehrungszeichen (JSON, Java/TS). */
    private static final Pattern GEHEIMNIS = Pattern.compile(
            "((?:password|passwd|secret|api[._-]?key|token|credentials)[\"']?\\s*+[=:]\\s*+[\"']?)([^\\s,;\"'}{]++)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MD_ABSCHNITT = Pattern.compile("(?m)(?=^## )");
    private static final Pattern TS_EXPORT = Pattern.compile(
            "^export\\s++(?:default\\s++)?(?:async\\s++)?(?:function|const|class)\\s++(\\w++)",
            Pattern.MULTILINE);
    private static final Pattern JAVA_TYP = Pattern.compile("(?:class|interface|enum|record)\\s++(\\w++)");
    /** Beginn eines Members (mit Annotationen) - possessiv/atomar gegen ReDoS (CodeQL). */
    private static final Pattern JAVA_METHODE = Pattern.compile(
            "^[ ]{4}(?>@[a-zA-Z0-9_]+[^\\r\\n]*+\\r?\\n[ ]{4})*+(?:(?:public|private|protected|static|final|synchronized|abstract|default|native|void)\\b|(?:<[a-zA-Z0-9_, ?&]*+>[ ]++)?[a-zA-Z0-9_]+(?:<[^>\\r\\n]*+>)?(?:\\[\\])*+[ ]++[a-zA-Z0-9_]+[ ]*+\\()",
            Pattern.MULTILINE);
    private static final Pattern JAVA_METHODENNAME = Pattern.compile(
            "(?:void|\\w++(?:<[^>\\n]++>)?)\\s++(\\w++)\\s*+\\(");

    private final ObjectMapper objectMapper;

    public RagIndexer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Bettet Texte als Dokumente ein; ein Vektor je Text, gleiche Reihenfolge. */
    @FunctionalInterface
    public interface Einbetter {
        List<float[]> einbetten(List<String> texte) throws IOException, InterruptedException;
    }

    /** Ein Stueck Doku oder Code, noch ohne Vektor. */
    public record Rohabschnitt(String content, String filePath, String category, String chunkType, String name) {

        /** Pruefsumme ueber Pfad + Inhalt: identischer Text in derselben Datei = gleicher Vektor. */
        public String hash() {
            return sha256(filePath + "\n" + content);
        }

        /** Was eingebettet wird: Der Dateipfad hilft der Suche ("ProjektEditor" ~ Projekte). */
        String einbettText() {
            return "Datei: " + filePath + " | " + name + "\n" + content;
        }
    }

    /** Ein eingebetteter Abschnitt der Wissensbasis. */
    public record Abschnitt(String content, String filePath, String category, String chunkType,
                            String name, String contentHash, float[] vector) {

        static Abschnitt aus(Rohabschnitt roh, float[] vector) {
            return new Abschnitt(roh.content(), roh.filePath(), roh.category(), roh.chunkType(),
                    roh.name(), roh.hash(), vector);
        }

        String schluessel() {
            return filePath + "::" + contentHash;
        }
    }

    /**
     * Ergebnis eines Laufs. Fehlen Abschnitte (Zeitlimit erreicht oder {@code fehler}), sind die
     * bis dahin eingebetteten trotzdem gueltig und werden gespeichert.
     */
    public record Ergebnis(List<Abschnitt> abschnitte, int ausVorlage, int neuEingebettet,
                           int fehlend, boolean zeitlimitErreicht, String fehler) {
        public boolean vollstaendig() {
            return fehlend == 0;
        }
    }

    private record Quelle(String pfad, String endung, String kategorie, int tiefe) {}

    /** Methode oder Export, bevor er mit Nachbarn zu einem Abschnitt gepackt wird. */
    private record Teil(String text, String name) {}

    // ── Zerlegen ──

    /** Zerlegt alle Quellen unter {@code projektOrdner}; fehlende Ordner werden uebersprungen. */
    public List<Rohabschnitt> zerlegen(Path projektOrdner) {
        Path wurzel = projektOrdner.toAbsolutePath().normalize();
        List<Rohabschnitt> abschnitte = new ArrayList<>();
        for (Quelle quelle : QUELLEN) {
            Path ziel = wurzel.resolve(quelle.pfad()).normalize();
            if (!ziel.startsWith(wurzel)) continue;
            if (quelle.endung() == null) {
                if (Files.isRegularFile(ziel, LinkOption.NOFOLLOW_LINKS)) {
                    zerlegeDatei(abschnitte, wurzel, ziel, quelle.kategorie());
                }
            } else if (Files.isDirectory(ziel)) {
                zerlegeOrdner(abschnitte, wurzel, ziel, quelle);
            }
        }
        return abschnitte;
    }

    private void zerlegeOrdner(List<Rohabschnitt> abschnitte, Path wurzel, Path ordner, Quelle quelle) {
        try (Stream<Path> dateien = Files.walk(ordner, quelle.tiefe())) {
            // Keine Symlinks: Ein Link auf ~/.ssh oder /proc wuerde sonst eingelesen und ausgeliefert
            dateien.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                    .filter(p -> p.getFileName().toString().endsWith(quelle.endung()))
                    .filter(p -> p.toAbsolutePath().normalize().startsWith(ordner))
                    .filter(p -> !TESTDATEI.matcher(p.getFileName().toString()).find())
                    .sorted()
                    .forEach(datei -> zerlegeDatei(abschnitte, wurzel, datei, quelle.kategorie()));
        } catch (IOException e) {
            log.warn("RAG: Ordner nicht lesbar: {}", ordner);
        }
    }

    private void zerlegeDatei(List<Rohabschnitt> abschnitte, Path wurzel, Path datei, String kategorie) {
        String inhalt;
        try {
            inhalt = Files.readString(datei);
        } catch (IOException e) {
            log.warn("RAG: Datei nicht lesbar: {}", datei);
            return;
        }
        inhalt = GEHEIMNIS.matcher(inhalt).replaceAll("$1***REDACTED***");
        String pfad = wurzel.relativize(datei).toString().replace('\\', '/');
        String name = datei.getFileName().toString();

        if (name.endsWith(".java")) {
            zerlegeJava(abschnitte, inhalt, pfad, kategorie);
        } else if (name.endsWith(".tsx") || name.endsWith(".ts")) {
            zerlegeTypeScript(abschnitte, inhalt, pfad, kategorie, name);
        } else if (name.endsWith(".md")) {
            zerlegeMarkdown(abschnitte, inhalt, pfad, kategorie, name);
        } else {
            hinzufuegen(abschnitte, inhalt, pfad, kategorie, "file", name);
        }
    }

    private void zerlegeJava(List<Rohabschnitt> abschnitte, String inhalt, String pfad, String kategorie) {
        Matcher typ = JAVA_TYP.matcher(inhalt);
        String klasse = typ.find() ? typ.group(1) : "Unknown";

        List<String> teile = javaTeilen(inhalt);
        if (teile.size() <= 2 || inhalt.length() <= MAX_ZEICHEN) {
            hinzufuegen(abschnitte, inhalt, pfad, kategorie, "class", klasse);
            return;
        }
        List<Teil> benannt = new ArrayList<>();
        benannt.add(new Teil(teile.get(0), "(header)"));
        for (String text : teile.subList(1, teile.size())) {
            Matcher methodenname = JAVA_METHODENNAME.matcher(text);
            benannt.add(new Teil(text, methodenname.find() ? methodenname.group(1) : "(Felder)"));
        }
        packen(abschnitte, benannt, pfad, kategorie, "methods", klasse);
    }

    /**
     * Zerlegt Java-Quelltext an Member-Grenzen: [Kopf (package, imports, Klassenkopf), Member 1, ...].
     * Ohne erkannten Member nur der Kopf. Nichts geht verloren, nichts ist doppelt.
     */
    static List<String> javaTeilen(String inhalt) {
        List<Integer> anfaenge = new ArrayList<>();
        Matcher member = JAVA_METHODE.matcher(inhalt);
        while (member.find()) anfaenge.add(member.start());

        List<String> teile = new ArrayList<>();
        teile.add(inhalt.substring(0, anfaenge.isEmpty() ? inhalt.length() : anfaenge.get(0)).trim());
        for (int i = 0; i < anfaenge.size(); i++) {
            int ende = i + 1 < anfaenge.size() ? anfaenge.get(i + 1) : inhalt.length();
            teile.add(inhalt.substring(anfaenge.get(i), ende).trim());
        }
        return teile;
    }

    private void zerlegeTypeScript(List<Rohabschnitt> abschnitte, String inhalt, String pfad,
                                   String kategorie, String dateiname) {
        List<Integer> anfaenge = new ArrayList<>();
        Matcher export = TS_EXPORT.matcher(inhalt);
        while (export.find()) anfaenge.add(export.start());

        if (anfaenge.size() <= 1 || inhalt.length() <= MAX_ZEICHEN) {
            hinzufuegen(abschnitte, inhalt, pfad, kategorie, "component", dateiname);
            return;
        }
        List<Teil> teile = new ArrayList<>();
        if (!inhalt.substring(0, anfaenge.get(0)).isBlank()) {
            teile.add(new Teil(inhalt.substring(0, anfaenge.get(0)).trim(), "(imports)"));
        }
        for (int i = 0; i < anfaenge.size(); i++) {
            int ende = i + 1 < anfaenge.size() ? anfaenge.get(i + 1) : inhalt.length();
            String text = inhalt.substring(anfaenge.get(i), ende).trim();
            Matcher name = TS_EXPORT.matcher(text);
            teile.add(new Teil(text, name.find() ? name.group(1) : "anonymous"));
        }
        packen(abschnitte, teile, pfad, kategorie, "component", dateiname);
    }

    /**
     * Fasst aufeinanderfolgende Methoden bzw. Exporte zu Abschnitten bis {@link #MAX_ZEICHEN}
     * zusammen. Einzelne Getter als eigener Abschnitt bringen der Suche nichts und blaehen die
     * RAG-Datei auf; zu Grosses zerlegt {@link #hinzufuegen} weiter.
     */
    private void packen(List<Rohabschnitt> abschnitte, List<Teil> teile, String pfad, String kategorie,
                        String art, String praefix) {
        StringBuilder text = new StringBuilder();
        List<String> namen = new ArrayList<>();
        for (Teil teil : teile) {
            if (!text.isEmpty() && text.length() + 2 + teil.text().length() > MAX_ZEICHEN) {
                hinzufuegen(abschnitte, text.toString(), pfad, kategorie, art, sammelname(praefix, namen));
                text.setLength(0);
                namen.clear();
            }
            if (!text.isEmpty()) text.append("\n\n");
            text.append(teil.text());
            namen.add(teil.name());
        }
        if (!text.isEmpty()) {
            hinzufuegen(abschnitte, text.toString(), pfad, kategorie, art, sammelname(praefix, namen));
        }
    }

    private static String sammelname(String praefix, List<String> namen) {
        String name = praefix + ": " + String.join(", ", namen);
        return name.length() <= 160 ? name : name.substring(0, 157) + "...";
    }

    private void zerlegeMarkdown(List<Rohabschnitt> abschnitte, String inhalt, String pfad,
                                 String kategorie, String dateiname) {
        String[] teile = MD_ABSCHNITT.split(inhalt);
        if (teile.length <= 1 || inhalt.length() <= MAX_ZEICHEN) {
            hinzufuegen(abschnitte, inhalt, pfad, kategorie, "document", dateiname);
            return;
        }
        for (String teil : teile) {
            String text = teil.trim();
            if (text.isEmpty()) continue;
            String ueberschrift = text.lines().findFirst().orElse("").replace("#", "").trim();
            hinzufuegen(abschnitte, text, pfad, kategorie, "section",
                    ueberschrift.isBlank() ? dateiname : ueberschrift);
        }
    }

    /** Zu Langes nicht abschneiden, sondern an Zeilengrenzen in Teile zerlegen. */
    private void hinzufuegen(List<Rohabschnitt> abschnitte, String inhalt, String pfad, String kategorie,
                             String art, String name) {
        if (inhalt.isBlank()) return;
        List<String> teile = inTeileZerlegen(inhalt);
        for (int i = 0; i < teile.size(); i++) {
            String teilName = teile.size() == 1 ? name : name + " (Teil " + (i + 1) + "/" + teile.size() + ")";
            abschnitte.add(new Rohabschnitt(teile.get(i), pfad, kategorie, art, teilName));
        }
    }

    static List<String> inTeileZerlegen(String inhalt) {
        if (inhalt.length() <= MAX_ZEICHEN) return List.of(inhalt);
        List<String> teile = new ArrayList<>();
        StringBuilder aktuell = new StringBuilder();
        for (String zeile : inhalt.split("\n", -1)) {
            while (zeile.length() > MAX_ZEICHEN) {
                abschliessen(teile, aktuell);
                teile.add(zeile.substring(0, MAX_ZEICHEN));
                zeile = zeile.substring(MAX_ZEICHEN);
            }
            if (!aktuell.isEmpty() && aktuell.length() + 1 + zeile.length() > MAX_ZEICHEN) {
                abschliessen(teile, aktuell);
            }
            if (!aktuell.isEmpty()) aktuell.append('\n');
            aktuell.append(zeile);
        }
        abschliessen(teile, aktuell);
        return teile;
    }

    private static void abschliessen(List<String> teile, StringBuilder aktuell) {
        if (!aktuell.toString().isBlank()) teile.add(aktuell.toString());
        aktuell.setLength(0);
    }

    // ── Einbetten ──

    /**
     * Bringt die Wissensbasis auf den Stand von {@code abschnitte}: Unveraenderte Abschnitte
     * kommen aus der Vorlage, nur neue/geaenderte werden eingebettet. Nach {@code zeitLimit}
     * oder bei einem Fehler wird aufgehoert - das Bisherige bleibt gueltig.
     */
    public Ergebnis aktualisieren(List<Rohabschnitt> abschnitte, Map<String, Abschnitt> vorlage,
                                  Einbetter einbetter, Duration zeitLimit) {
        Instant ende = zeitLimit == null ? null : Instant.now().plus(zeitLimit);
        // Gleicher Text in derselben Datei (z. B. zwei identische Abschnitte) nur einmal
        Map<String, Rohabschnitt> eindeutig = new LinkedHashMap<>();
        for (Rohabschnitt roh : abschnitte) {
            eindeutig.putIfAbsent(roh.filePath() + "::" + roh.hash(), roh);
        }

        Map<String, Abschnitt> fertig = new LinkedHashMap<>();
        List<Rohabschnitt> offen = new ArrayList<>();
        for (Map.Entry<String, Rohabschnitt> eintrag : eindeutig.entrySet()) {
            Abschnitt bekannt = vorlage.get(eintrag.getKey());
            if (bekannt != null) {
                // Name/Kategorie aus dem aktuellen Stand, Vektor aus der Vorlage
                fertig.put(eintrag.getKey(), Abschnitt.aus(eintrag.getValue(), bekannt.vector()));
            } else {
                offen.add(eintrag.getValue());
            }
        }
        int ausVorlage = fertig.size();

        int neu = 0;
        boolean zeitlimitErreicht = false;
        String fehler = null;
        for (int i = 0; i < offen.size(); i += STAPEL_GROESSE) {
            if (ende != null && Instant.now().isAfter(ende)) {
                zeitlimitErreicht = true;
                break;
            }
            List<Rohabschnitt> stapel = offen.subList(i, Math.min(i + STAPEL_GROESSE, offen.size()));
            try {
                List<float[]> vektoren = einbetter.einbetten(stapel.stream().map(Rohabschnitt::einbettText).toList());
                if (vektoren.size() != stapel.size()) {
                    throw new IOException("Gemini lieferte " + vektoren.size() + " statt " + stapel.size() + " Vektoren");
                }
                for (int j = 0; j < stapel.size(); j++) {
                    Abschnitt a = Abschnitt.aus(stapel.get(j), vektoren.get(j));
                    fertig.put(a.schluessel(), a);
                }
                neu += stapel.size();
                log.info("RAG: {}/{} neue Abschnitte eingebettet", neu, offen.size());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fehler = "Abgebrochen";
                break;
            } catch (IOException | RuntimeException e) {
                fehler = e.getMessage();
                log.warn("RAG: Einbetten abgebrochen nach {} neuen Abschnitten: {}", neu, fehler);
                break;
            }
        }

        // Reihenfolge wie in den Quellen, damit die Datei stabil bleibt
        List<Abschnitt> ergebnis = new ArrayList<>();
        for (String schluessel : eindeutig.keySet()) {
            Abschnitt a = fertig.get(schluessel);
            if (a != null) ergebnis.add(a);
        }
        return new Ergebnis(ergebnis, ausVorlage, neu, offen.size() - neu, zeitlimitErreicht, fehler);
    }

    // ── RAG-Datei ──

    /**
     * Liest die RAG-Datei, Schluessel = Pfad + Pruefsumme. Fehlt sie, ist sie unlesbar oder von
     * einem anderen Modell, gibt es eine leere Vorlage (dann wird alles neu eingebettet).
     * <p>
     * Abschnitt fuer Abschnitt gelesen statt als ganzer JSON-Baum: Die Datei ist zig MB gross,
     * und der Server laedt sie beim Start nach jedem Nachtupdate - auch auf kleinen Rechnern.
     */
    public Map<String, Abschnitt> laden(Path datei) {
        Map<String, Abschnitt> abschnitte = new LinkedHashMap<>();
        if (datei == null || !Files.isRegularFile(datei)) return abschnitte;
        try (JsonParser parser = objectMapper.createParser(datei.toFile())) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                log.info("RAG-Datei {} hat ein altes Format - wird nicht verwendet", datei);
                return abschnitte;
            }
            int format = 0;
            int dimensionen = 0;
            String modell = "";
            while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
                String feld = parser.currentName();
                JsonToken wert = parser.nextToken();
                switch (feld) {
                    case "format" -> format = parser.getValueAsInt();
                    case "modell" -> modell = parser.getValueAsString("");
                    case "dimensionen" -> dimensionen = parser.getValueAsInt();
                    case "abschnitte" -> {
                        // speichern() schreibt die Kopfdaten zuerst
                        if (format != FORMAT || !MODELL.equals(modell) || dimensionen != DIMENSIONEN) {
                            log.info("RAG-Datei {} hat ein altes Format oder Modell - wird nicht verwendet", datei);
                            return new LinkedHashMap<>();
                        }
                        if (wert != JsonToken.START_ARRAY) {
                            parser.skipChildren();
                            break;
                        }
                        while (parser.nextToken() == JsonToken.START_OBJECT) {
                            JsonNode a = parser.readValueAsTree();
                            float[] vektor = vektorLesen(a.path("vector").asString(""));
                            if (vektor == null) continue;
                            Abschnitt abschnitt = new Abschnitt(a.path("content").asString(""), a.path("filePath").asString(""),
                                    a.path("category").asString(""), a.path("chunkType").asString(""), a.path("name").asString(""),
                                    a.path("contentHash").asString(""), vektor);
                            abschnitte.put(abschnitt.schluessel(), abschnitt);
                        }
                    }
                    default -> parser.skipChildren();
                }
            }
        } catch (RuntimeException e) {
            log.warn("RAG-Datei {} nicht lesbar, wird nicht verwendet: {}", datei, e.getMessage());
            abschnitte.clear();
        }
        return abschnitte;
    }

    /** Schreibt erst in eine Nachbardatei und benennt dann um - nie eine halbe RAG-Datei. */
    public void speichern(Path datei, List<Abschnitt> abschnitte) throws IOException {
        Path ziel = datei.toAbsolutePath();
        if (ziel.getParent() != null) Files.createDirectories(ziel.getParent());
        Path temp = ziel.resolveSibling(ziel.getFileName() + ".neu");
        try {
            try (JsonGenerator json = objectMapper.createGenerator(temp.toFile(), JsonEncoding.UTF8)) {
                json.writeStartObject();
                json.writeNumberProperty("format", FORMAT);
                json.writeStringProperty("modell", MODELL);
                json.writeNumberProperty("dimensionen", DIMENSIONEN);
                json.writeArrayPropertyStart("abschnitte");
                for (Abschnitt a : abschnitte) {
                    json.writeStartObject();
                    json.writeStringProperty("filePath", a.filePath());
                    json.writeStringProperty("name", a.name());
                    json.writeStringProperty("category", a.category());
                    json.writeStringProperty("chunkType", a.chunkType());
                    json.writeStringProperty("contentHash", a.contentHash());
                    json.writeStringProperty("content", a.content());
                    json.writeStringProperty("vector", vektorSchreiben(a.vector()));
                    json.writeEndObject();
                }
                json.writeEndArray();
                json.writeEndObject();
            }
            Files.move(temp, ziel, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            // Keine halbe 25-MB-Datei liegen lassen
            Files.deleteIfExists(temp);
            // Jackson 3 meldet Schreibfehler (volle Platte) ungeprueft - die Aufrufer fangen IOException
            if (e instanceof JacksonException jackson) {
                throw new IOException("RAG-Datei konnte nicht geschrieben werden", jackson);
            }
            throw e;
        }
    }

    /** Vektor als Base64 von float32 (little endian): ein Viertel der Groesse von JSON-Zahlen. */
    static String vektorSchreiben(float[] vektor) {
        ByteBuffer puffer = ByteBuffer.allocate(vektor.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float wert : vektor) puffer.putFloat(wert);
        return Base64.getEncoder().encodeToString(puffer.array());
    }

    static float[] vektorLesen(String base64) {
        if (base64 == null || base64.isEmpty()) return null;
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            return null; // nur dieser Abschnitt faellt weg, nicht die ganze Datei
        }
        if (bytes.length != DIMENSIONEN * Float.BYTES) return null;
        ByteBuffer puffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] vektor = new float[DIMENSIONEN];
        for (int i = 0; i < DIMENSIONEN; i++) vektor[i] = puffer.getFloat();
        return vektor;
    }

    static String sha256(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 fehlt in dieser Java-Laufzeit", e);
        }
    }
}
