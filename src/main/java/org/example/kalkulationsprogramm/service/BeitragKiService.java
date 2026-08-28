package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.dto.Beitraege.BeitragKiAnfrage;
import org.example.kalkulationsprogramm.dto.Beitraege.BeitragKiEntwurf;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektNotizRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Erzeugt aus einem Projekt, seinen Leistungspositionen, dem Bautagebuch und
 * ausgewaehlten Bildern einen Textvorschlag fuer einen Website-Beitrag.
 *
 * <p><b>Datenschutz.</b> Der erzeugte Text wird oeffentlich. Preise,
 * Kundennamen und private Bautagebuch-Notizen werden deshalb beim Aufbau des
 * Kontexts weggelassen, nicht bloss per Anweisung verboten. Die
 * Systemanweisung wiederholt das Verbot zusaetzlich, und der Nutzer sieht den
 * Text vor dem Veroeffentlichen.
 *
 * <p>Der Gemini-Aufruf ist hier bewusst eigenstaendig gebaut, nach dem Muster
 * von {@link KiHilfeService}. Ein gemeinsamer Client fuer alle drei
 * KI-Dienste ist eine eigene Aufgabe.
 */
@Slf4j
@Service
public class BeitragKiService {

    static final String SYSTEM_ANWEISUNG = """
            Du schreibst kurze Neuigkeiten-Beitraege fuer die Website einer Bauschlosserei.

            Schreibe alle Texte in einem einfachen, ehrlichen und bodenstaendigen Ton.
            Beachte dabei diese festen Regeln.
            Verwende niemals Doppelpunkte.
            Verwende niemals Gedankenstriche.
            Schreibe in kurzen und klaren Saetzen ohne Schachtelsaetze.
            Nutze eine einfache und verstaendliche Sprache.
            Verzichte komplett auf uebertriebene Werbesprache und leere Floskeln.
            Klinge wie ein erfahrener Handwerker mit technischem Sachverstand.
            Starte immer direkt mit dem Inhalt ohne Begruessung oder Einleitungssaetze.

            Zusaetzlich gilt fuer diesen oeffentlichen Text.
            Nenne keine Kundennamen und keine Firmennamen von Auftraggebern.
            Nenne keine Anschriften und keine Orte, die einen Auftraggeber erkennbar machen.
            Nenne keine Preise und keine Betraege.

            Formatiere den Beitragstext als reinen Text.
            Trenne Absaetze durch eine Leerzeile.
            Schreibe Aufzaehlungspunkte mit einem fuehrenden Bindestrich.
            Verwende keine HTML-Tags und keine Markdown-Ueberschriften.

            So sieht ein guter Beitrag aus. Halte dich an diesen Ton, diese
            Satzlaenge und diesen Aufbau. Ein Satz zur Ausgangslage, danach je
            ein Absatz pro Gewerk mit den verwendeten Materialien und ihrem
            Nutzen.

            Bei diesem Projekt haben wir eine Balkonanlage erweitert und modernisiert.

            Die tragende Konstruktion aus Rohrprofilen sitzt passgenau auf den bestehenden Pfeilern. Schwerlastanker und Klebeduebel verbinden das Tragwerk fest mit dem Gebaeude. Die Feuerverzinkung schuetzt den gesamten Stahl dauerhaft vor Rost und Witterung.

            Als Belag kommt ein robuster Aluminiumboden in Graualuminium zum Einsatz. Die Dielen sind witterungsbestaendig, pflegeleicht und bieten dank rutschhemmender Oberflaeche sicheren Halt.

            Das Gelaender sichert die neue Flaeche dreiseitig ab. Es besteht aus verzinkten Stahlpfosten und einem geschliffenen Handlauf. Die Fuellung aus mattem Verbundsicherheitsglas dient als gepruefter Absturzschutz. Gleichzeitig sorgt das Glas fuer Windschutz und Privatsphaere.
            """;

    private static final Duration READ_TIMEOUT = Duration.ofSeconds(90);
    private static final int MAX_BILDER = 8;
    private static final int MAX_NOTIZEN = 15;
    private static final int MAX_VERLAUF = 20;

    private final ProjektRepository projektRepository;
    private final ProjektNotizRepository notizRepository;
    private final AusgangsGeschaeftsDokumentRepository dokumentRepository;
    private final SystemSettingsService systemSettingsService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String modell;

    @Autowired
    public BeitragKiService(ProjektRepository projektRepository,
                            ProjektNotizRepository notizRepository,
                            AusgangsGeschaeftsDokumentRepository dokumentRepository,
                            SystemSettingsService systemSettingsService,
                            ObjectMapper objectMapper,
                            @Value("${ai.gemini.model.pro:gemini-pro-latest}") String modell) {
        this(projektRepository, notizRepository, dokumentRepository, systemSettingsService,
                objectMapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(),
                modell);
    }

    /** Test-Konstruktor mit injizierbarem HttpClient. */
    BeitragKiService(ProjektRepository projektRepository,
                     ProjektNotizRepository notizRepository,
                     AusgangsGeschaeftsDokumentRepository dokumentRepository,
                     SystemSettingsService systemSettingsService,
                     ObjectMapper objectMapper,
                     HttpClient httpClient,
                     String modell) {
        this.projektRepository = projektRepository;
        this.notizRepository = notizRepository;
        this.dokumentRepository = dokumentRepository;
        this.systemSettingsService = systemSettingsService;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.modell = modell;
    }

    /**
     * Baut den Sachkontext zum Projekt. Sichtbar fuer den Test, weil genau
     * hier entschieden wird, was oeffentlich werden darf.
     */
    String baueKontext(Long projektId) {
        Projekt projekt = projektRepository.findById(projektId)
                .orElseThrow(() -> new IllegalArgumentException("Projekt nicht gefunden: " + projektId));

        StringBuilder sb = new StringBuilder();
        // ACHTUNG: bauvorhaben ist ein Freitextfeld und kann Kundennamen oder
        // Anschriften enthalten (z.B. "Musterbau GmbH, Musterweg 5, 12345
        // Musterstadt - Hallentor"). Es wird hier NICHT gefiltert oder
        // geprueft. Die Absicherung liegt allein bei der Systemanweisung
        // (SYSTEM_ANWEISUNG, verbietet Kunden-/Firmennamen und Anschriften)
        // und der Sichtpruefung durch den Menschen vor dem Veroeffentlichen.
        // Bekanntes, im Plan akzeptiertes Restrisiko.
        sb.append("Bauvorhaben: ").append(sicher(projekt.getBauvorhaben())).append("\n\n");

        String leistungen = leseLeistungen(projektId);
        if (!leistungen.isBlank()) {
            sb.append("Ausgefuehrte Leistungen:\n").append(leistungen).append("\n");
        }

        String tagebuch = leseBautagebuch(projektId);
        if (!tagebuch.isBlank()) {
            sb.append("Notizen von der Baustelle:\n").append(tagebuch).append("\n");
        }
        return sb.toString();
    }

    /**
     * Liest die Leistungspositionen. Bevorzugt aus der Auftragsbestaetigung,
     * sonst aus dem juengsten Angebot oder Nachtragsangebot.
     *
     * <p>Preise, Rabatte und Betraege werden hier NICHT gelesen. Sie duerfen
     * nicht in einen oeffentlichen Text geraten.
     */
    private String leseLeistungen(Long projektId) {
        List<AusgangsGeschaeftsDokument> alle = dokumentRepository.findByProjektIdOrderByDatumDesc(projektId);

        Optional<AusgangsGeschaeftsDokument> gewaehlt = juengstes(alle, AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
        if (gewaehlt.isEmpty()) {
            gewaehlt = juengstes(alle, AusgangsGeschaeftsDokumentTyp.ANGEBOT, AusgangsGeschaeftsDokumentTyp.NACHTRAGSANGEBOT);
        }
        if (gewaehlt.isEmpty() || gewaehlt.get().getPositionenJson() == null) {
            return "";
        }

        JsonNode wurzel;
        try {
            wurzel = objectMapper.readTree(gewaehlt.get().getPositionenJson());
        } catch (IOException e) {
            log.warn("[BeitragKi] positionenJson unlesbar, Leistungen bleiben leer.", e);
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (JsonNode block : wurzel.path("blocks")) {
            schreibeBlock(sb, block);
        }
        return sb.toString();
    }

    /**
     * Schreibt einen einzelnen Block in den Leistungstext. SECTION_HEADER-
     * Bloecke (Bauabschnitte) tragen ihre Leistungen NICHT auf oberster Ebene
     * von "blocks", sondern im Feld "children" (siehe
     * react-pc-frontend/src/components/document-editor/types.ts,
     * {@code DocBlock.children}). Ohne diesen rekursiven Abstieg verschwinden
     * bei jedem Angebot mit Bauabschnitten alle Leistungen darunter spurlos
     * aus dem KI-Kontext, uebrig bleiben nur die Ueberschriften.
     *
     * <p>Preise, Rabatte und Betraege werden hier fuer Kinder GENAUSO wenig
     * gelesen wie auf oberster Ebene. Nur Titel, Beschreibung, Menge und
     * Einheit duerfen in den Text.
     */
    private void schreibeBlock(StringBuilder sb, JsonNode block) {
        String typ = block.path("type").asText();
        if ("SECTION_HEADER".equals(typ)) {
            sb.append("\n").append(sicher(block.path("sectionLabel").asText())).append("\n");
            for (JsonNode kind : block.path("children")) {
                schreibeBlock(sb, kind);
            }
        } else if ("SERVICE".equals(typ)) {
            sb.append("- ").append(sicher(block.path("title").asText()));
            String menge = block.path("quantity").isMissingNode() ? "" : block.path("quantity").asText();
            String einheit = sicher(block.path("unit").asText());
            if (!menge.isBlank() && !einheit.isBlank()) {
                sb.append(" (").append(menge).append(" ").append(einheit).append(")");
            }
            String beschreibung = sicher(block.path("description").asText());
            if (!beschreibung.isBlank()) {
                sb.append(". ").append(beschreibung);
            }
            sb.append("\n");
        }
        // TEXT-, CLOSURE-, SUBTOTAL- und SEPARATOR-Bloecke enthalten
        // Zahlungsbedingungen und Summen. Die gehen niemanden etwas an.
    }

    @SafeVarargs
    private Optional<AusgangsGeschaeftsDokument> juengstes(
            List<AusgangsGeschaeftsDokument> alle, AusgangsGeschaeftsDokumentTyp... typen) {
        List<AusgangsGeschaeftsDokumentTyp> gesucht = List.of(typen);
        return alle.stream()
                .filter(d -> gesucht.contains(d.getTyp()))
                .filter(d -> !d.isStorniert())
                .max(Comparator.comparing(AusgangsGeschaeftsDokument::getDatum,
                        Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    /** Nur oeffentliche Notizen. Private bleiben draussen. */
    private String leseBautagebuch(Long projektId) {
        return notizRepository.findByProjektIdOrderByErstelltAmDesc(projektId).stream()
                .filter(n -> !n.isNurFuerErsteller())
                .limit(MAX_NOTIZEN)
                .map(n -> "- " + sicher(n.getNotiz()))
                .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
    }

    private static String sicher(String wert) {
        return wert == null ? "" : wert.trim();
    }

    /**
     * Ruft Gemini und gibt den Vorschlag zurueck. Die Bilder gehen als
     * inline_data mit, hoechstens {@value #MAX_BILDER} Stueck.
     */
    public BeitragKiEntwurf erzeugeEntwurf(BeitragKiAnfrage anfrage, List<MultipartFile> bilder) {
        String apiKey = systemSettingsService.getGeminiApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Kein Gemini-Schluessel hinterlegt.");
        }

        return sendeUndLies(baueKoerper(anfrage, bilder), apiKey);
    }

    /**
     * Baut den Request-Koerper fuer Gemini. Sichtbar fuer den Test, weil hier
     * feststeht, in welcher Reihenfolge Chatverlauf und aktueller Auftrag
     * beim Modell ankommen.
     *
     * <p>Der bisherige Chat steht IMMER vor der aktuellen Runde. Stuende er
     * danach, laese das Modell die eigene Antwort vor der Frage. Beim Kuerzen
     * bleiben die LETZTEN {@value #MAX_VERLAUF} Eintraege erhalten, nicht die
     * ersten, sonst fliegen bei einem langen Chat genau die neuesten (und
     * damit wichtigsten) Nachrichten raus.
     */
    ObjectNode baueKoerper(BeitragKiAnfrage anfrage, List<MultipartFile> bilder) {
        ObjectNode koerper = objectMapper.createObjectNode();

        ObjectNode systemAnweisung = koerper.putObject("system_instruction");
        systemAnweisung.putArray("parts").addObject().put("text", SYSTEM_ANWEISUNG);

        ArrayNode contents = koerper.putArray("contents");

        // Bisheriger Chat zuerst und in chronologischer Reihenfolge, sonst
        // liest das Modell die Antwort vor der Frage.
        List<BeitragKiAnfrage.ChatNachricht> verlauf = anfrage.verlaufOderLeer();
        List<BeitragKiAnfrage.ChatNachricht> beschnitten = verlauf.size() > MAX_VERLAUF
                ? verlauf.subList(verlauf.size() - MAX_VERLAUF, verlauf.size())
                : verlauf;
        beschnitten.forEach(nachricht -> {
            ObjectNode eintrag = contents.addObject();
            eintrag.put("role", "model".equals(nachricht.rolle()) ? "model" : "user");
            eintrag.putArray("parts").addObject().put("text", sicher(nachricht.text()));
        });

        // Aktuelle Runde, immer als letztes: Sachkontext plus Bilder plus Auftrag.
        ObjectNode aktuelle = contents.addObject();
        aktuelle.put("role", "user");
        ArrayNode teile = aktuelle.putArray("parts");
        teile.addObject().put("text", baueKontext(anfrage.projektId()));

        List<MultipartFile> verwendbar = bilder == null ? List.of()
                : bilder.stream().filter(b -> b != null && !b.isEmpty()).limit(MAX_BILDER).toList();
        for (MultipartFile bild : verwendbar) {
            try {
                ObjectNode inline = teile.addObject().putObject("inline_data");
                inline.put("mime_type", bild.getContentType() == null ? "image/jpeg" : bild.getContentType());
                inline.put("data", Base64.getEncoder().encodeToString(bild.getBytes()));
            } catch (IOException e) {
                log.warn("[BeitragKi] Bild konnte nicht gelesen werden, wird uebersprungen.", e);
            }
        }

        String aktuellerText = sicher(anfrage.aktuellerText());
        if (aktuellerText.isBlank()) {
            teile.addObject().put("text",
                    "Schreibe daraus einen Beitrag fuer den Bereich Aktuelles. "
                    + "Gib Titel, Kurzbeschreibung und Text zurueck.");
        } else {
            teile.addObject().put("text",
                    "Das steht gerade im Editor und ist die Grundlage fuer Aenderungen.\n\n"
                    + "Titel: " + sicher(anfrage.aktuellerTitel()) + "\n\n" + aktuellerText);
        }

        // Feste Antwortform, damit kein Parsen von Freitext noetig ist.
        ObjectNode konfiguration = koerper.putObject("generationConfig");
        konfiguration.put("responseMimeType", "application/json");
        ObjectNode schema = konfiguration.putObject("responseSchema");
        schema.put("type", "OBJECT");
        ObjectNode eigenschaften = schema.putObject("properties");
        eigenschaften.putObject("titel").put("type", "STRING");
        eigenschaften.putObject("kurzbeschreibung").put("type", "STRING");
        eigenschaften.putObject("text").put("type", "STRING");
        eigenschaften.putObject("antwort").put("type", "STRING");
        ArrayNode pflicht = schema.putArray("required");
        pflicht.add("titel");
        pflicht.add("kurzbeschreibung");
        pflicht.add("text");
        pflicht.add("antwort");

        return koerper;
    }

    private BeitragKiEntwurf sendeUndLies(ObjectNode koerper, String apiKey) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + modell + ":generateContent?key=" + apiKey;

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(READ_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(koerper), StandardCharsets.UTF_8))
                    .build();
        } catch (IOException e) {
            throw new IllegalStateException("Anfrage an die KI konnte nicht gebaut werden.", e);
        }

        HttpResponse<String> antwort;
        try {
            antwort = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Die KI ist nicht erreichbar.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Der Aufruf der KI wurde unterbrochen.", e);
        }

        if (antwort.statusCode() >= 400) {
            log.warn("[BeitragKi] Gemini HTTP {} - {}", antwort.statusCode(), antwort.body());
            throw new IllegalStateException("Die KI hat mit einem Fehler geantwortet.");
        }

        try {
            JsonNode wurzel = objectMapper.readTree(antwort.body());
            String text = wurzel.path("candidates").path(0).path("content")
                    .path("parts").path(0).path("text").asText("");
            JsonNode entwurf = objectMapper.readTree(text);
            return new BeitragKiEntwurf(
                    entwurf.path("titel").asText(""),
                    entwurf.path("kurzbeschreibung").asText(""),
                    entwurf.path("text").asText(""),
                    entwurf.path("antwort").asText("Vorschlag erstellt."));
        } catch (IOException e) {
            throw new IllegalStateException("Die Antwort der KI war nicht lesbar.", e);
        }
    }
}
