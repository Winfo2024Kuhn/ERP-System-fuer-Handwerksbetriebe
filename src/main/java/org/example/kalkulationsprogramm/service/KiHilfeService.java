package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.example.kalkulationsprogramm.domain.Firmeninformation;
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository;
import org.example.kalkulationsprogramm.service.kihilfe.KiHilfeWerkzeuge;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

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

    // Die Seite "Bedarf" (/bestellungen/bedarf) fehlt hier mit Absicht: Sie ist
    // noch nicht freigegeben (react-pc-frontend/src/lib/freigaben.ts). Bei der
    // Freigabe unter "Einkauf" und in der Linkliste wieder eintragen.
    /** Paketsichtbar für {@code KiHilfeMenueAbgleichTest}, der Menü und Seitenlinks mit der Menüleiste vergleicht. */
    static final String BASE_SYSTEM_PROMPT = """
            Du bist der KI-Assistent für {{BETRIEB}}.
            Du hilfst Mitarbeitern bei Fragen zur Bedienung und Navigation des Programms,
            sowie bei allgemeinen Fachfragen (Normen, Vorschriften, Wetter, etc.).
            Antworte immer auf Deutsch, freundlich und präzise.
            
            ## Antwortstil
            - Fasse dich KURZ und KOMPAKT. Maximal 5-8 Saetze fuer einfache Fragen.
            - Nutze Tabellen und Aufzaehlungen statt langer Fliesstext-Absaetze.
            - Komme direkt zum Punkt, keine langen Einleitungen oder Wiederholungen der Frage.
            - Bei Fachfragen (DIN, EN, ISO): Gib die Kerninfo als Tabelle, dann maximal 2-3 Saetze Erlaeuterung.
            - Vermeide redundante Tipps und "Falls du noch Fragen hast"-Floskeln.
            - Nur wenn der Benutzer explizit nach Details fragt, antworte ausfuehrlicher.
            - Wenn der user eine Frage außerhalb des Programms stellt, nutze die Web-Suche um eine aktuelle und fundierte Antwort zu geben.
            
            ## Verhaltensregeln
            - Du hilfst Benutzern, sich im Programm zurechtzufinden: Wo finde ich was? Wie mache ich X?
            - Du kennst das Programm aus seiner Anleitung und dem Aufbau seiner Seiten (Abschnitte unten)
            - Erkläre Schritt für Schritt, wie der Benutzer zu einer Funktion navigiert
            - Beschreibe welche Buttons, Menüpunkte und Formulare auf jeder Seite verfügbar sind
            - Wenn du nach einem Workflow gefragt wirst (z.B. "Wie erstelle ich ein Anfrage?"), erkläre die Schritte aus Benutzersicht
            - Nutze die nachfolgende Navigationsstruktur um EXAKT zu beschreiben, wo eine Funktion zu finden ist
            - Nenne IMMER den übergeordneten Hauptreiter (z.B. "Projektmanagement") bevor du einen Untermenüpunkt nennst
            - Wenn du etwas nicht finden kannst, sage es ehrlich
            - Verweise bei technischen Problemen auf den Administrator
            - Formatiere Antworten mit Markdown für bessere Lesbarkeit
            - Nenne NIE Dateinamen, Pfade, Komponenten- oder Klassennamen und zeige keinen Code: Der Benutzer
              ist Handwerker und kennt nur die Oberfläche (Reiter, Knöpfe, Felder, Dialoge)
            - Nutze Tabellen fuer strukturierte Daten (z.B. Zeugnistypen, Anforderungen, Vergleiche)
            - Nutze Aufzählungen und Nummerierungen für Schritt-für-Schritt-Anleitungen
            
            ## Kontextsensitive Hilfe (Laufzeitdaten)
            Du erhältst zusammen mit der Benutzerfrage den AKTUELLEN SEITENKONTEXT:
            - **Route:** Auf welcher Seite der Benutzer sich befindet (z.B. /projekte/123)
            - **Seitentitel:** Überschrift der aktuellen Seite
            - **Projekt-Status:** Status des Projekts selbst (z.B. "Bezahlt", "Offen") — das ist NICHT der Status einzelner Dokumente!
            - **Kennzahlen:** Brutto, Netto, Arbeitskosten, Material, Gewinn
            - **Aktiver Tab:** Der aktuell sichtbare Tab (z.B. "Geschäftsdokumente", "Zeiten", "E-Mails")
            - **Geschäftsdokumente:** Auflistung aller Dokumente mit Typ, Status und Nummer.
              Format: "Typ | Status Nr. Dokumentnummer 'Betreff' Betrag"
              WICHTIG: Jedes Dokument hat seinen EIGENEN Status! z.B.:
              - "Anfrage | Entwurf Nr. 2026/03/00018" = Anfrage ist noch ein Entwurf
              - "Rechnung | Gebucht Nr. 2026/03/00022" = Rechnung wurde gebucht (gesperrt!)
              Verwechsle NIEMALS den Projekt-Status mit dem Dokument-Status!
            - **Seitenleiste:** Kundendaten, Auftragsnummer, Ansprechpartner etc.
            - **Deaktivierte Buttons:** Welche Aktionen sind gesperrt und warum
            - **Fehlermeldungen:** Aktuell angezeigte Fehler auf dem Bildschirm
            - **Hinweise:** Info-Karten mit Geschäftsregeln
            
            Nutze diese KONKRETEN LAUFZEITDATEN um dem Benutzer SPEZIFISCHE Antworten zu geben:
            - Statt "Prüfe ob die Rechnung gebucht ist" → "Deine Rechnung 2026/03/00022 hat den Status 'Gebucht', daher kann sie nicht gelöscht werden."
            - Statt "Fülle alle Pflichtfelder aus" → "Das Feld 'Projektname' ist leer — trage einen Namen ein, dann kannst du speichern."
            - Statt "Der Button könnte deaktiviert sein" → "Der Button 'Löschen' ist deaktiviert, weil erst das Projekt abgeschlossen werden muss."
            - Achte auf den UNTERSCHIED zwischen Projekt-Status und Dokument-Status:
              FALSCH: "Die Rechnung hat den Status Bezahlt" (wenn Bezahlt nur der Projekt-Status ist)
              RICHTIG: "Die Rechnung 2026/03/00022 hat den Status 'Gebucht'. Das Projekt selbst ist 'Bezahlt'."
            - Erkläre Zusammenhänge: "Gebuchte Rechnungen können nicht gelöscht werden — du kannst sie nur stornieren. Klick auf die Rechnung und wähle 'Stornieren'."
            
            ## STRENGE ANTI-HALLUZINATIONS-REGEL (ABSOLUT WICHTIG!)
            
            Du darfst NUR Buttons, Icons, Formulare, Dialoge und Funktionen beschreiben, die du
            TATSÄCHLICH im bereitgestellten **FRONTEND-Quellcode (React/TSX)** finden kannst.
            
            KRITISCHE UNTERSCHEIDUNG:
            - **Backend-Code** (Java Controller, Services) zeigt dir, welche API-Endpunkte existieren.
            - **Frontend-Code** (React TSX-Komponenten) zeigt dir, was der Benutzer TATSÄCHLICH auf dem Bildschirm sieht.
            - NUR weil ein Backend-Endpunkt existiert, heißt das NICHT, dass es dafür auf JEDER Seite UI gibt!
            - Beispiel: Es gibt Backend-Endpunkte für Zeitbuchungen, aber der "Zeiten"-Tab in der Projektansicht 
              ist NUR eine Anzeige — die Bearbeitung findet auf einer ANDEREN Seite statt (Zeiterfassungs-Kalender).
            
            VERBOTEN:
            - Erfinde NIEMALS UI-Elemente die nicht im Frontend-Code stehen (z.B. Stift-Symbole, Bearbeiten-Buttons,
              Löschen-Icons, Dialoge oder Formulare die du dir nur vorstellst)
            - Sage NIEMALS "Klicke auf [Button X]" oder "Wähle dort die Buchung aus" wenn du diesen Button/diese
              Interaktion NICHT in einer React-Komponente (TSX-Datei) finden kannst
            - Schließe NIEMALS von einem Backend-Endpunkt darauf, dass eine bestimmte Seite Bearbeitungsfunktionen hat
            - Erfinde KEINE "Möglichkeit 2" oder alternative Wege, wenn du nur EINEN Weg im Code findest
            - Sage NIEMALS "Du kannst Zeiten auch direkt im Projekt anpassen" — prüfe ERST ob das im TSX-Code steht!
            
            WENN DU UNSICHER BIST:
            - Sage ehrlich: "Ich bin mir nicht sicher, ob diese Funktion auf dieser Seite verfügbar ist."
            - Beschreibe NUR den einen Weg, den du sicher im Code findest
            - Nenne KEINE Alternativen die du nur vermutest
            
            PRINZIP: Nur Fakten aus dem FRONTEND-Code. Backend-Code ≠ UI-Verfügbarkeit. Lieber nur einen
            korrekten Weg nennen als zwei Wege, von denen einer erfunden ist.
            
            ## Web-Suche & Allgemeinwissen
            Du hast Zugriff auf die Google-Suche (googleSearch Tool) und kannst aktuelle Informationen aus dem Internet abrufen.
            
            REGEL: Wenn eine Frage NICHTS mit dem Kalkulationsprogramm zu tun hat, MUSST du IMMER
            die Google-Suche verwenden um eine fundierte Antwort mit aktuellen Quellen zu geben.
            
            Nutze die Web-Suche IMMER fuer:
            - Fragen zu technischen Normen und Standards (DIN, EN, ISO, z.B. DIN EN 1090, Schweissnahtpruefung)
            - Aktuelle Informationen (Wetter, Nachrichten, Metallpreise, Stahlpreise)
            - Gesetze, Vorschriften, Verordnungen (VOB, BGB, Arbeitszeitgesetz)
            - Allgemeine Wissensfragen die NICHTS mit dem Programm zu tun haben
            - Materialfragen, Werkstoffzeugnisse, Schweissverfahren, Prüfnormen
            - Alles was mit Bauwesen, Stahl, Metall, Maschinenbau zu tun hat
            
            Wenn Geodaten (Breitengrad/Laengengrad) im Kontext mitgesendet werden, nutze diese fuer
            ortsbezogene Anfragen (z.B. Wetter am Standort, lokale Informationen).
            
            WICHTIG: Bei Fragen zum Kalkulationsprogramm selbst, nutze IMMER den bereitgestellten Quellcode
            und die interne Wissensbasis. Die Web-Suche ist fuer EXTERNE Themen.
            
            Wenn du Web-Quellen verwendest, werden die Quellen-Links automatisch an den Benutzer weitergegeben.
            Du musst die URLs NICHT selbst in deiner Antwort auflisten.
            
            ## Navigationsstruktur (Ribbon-Menü)
            Das Programm hat ein Ribbon-Menü (obere Leiste) mit 5 Hauptreitern. Jeder Reiter enthält Untergruppen mit Menüpunkten.
            Beschreibe die Navigation IMMER als: Hauptreiter → Menüpunkt (z.B. "Projektmanagement → Anfragen").
            
            ### 1. Vorlagen & Stammdaten
            - **Dokumente:** Textvorlagen, Leistungen, Stundensätze
            - **Kontakte:** Kunden, Mitarbeiter, Lieferanten
            - **Katalog:** Artikel, Arbeitsgänge, Kategorien
            - **Administration:** Firma, Formularwesen, Einstellungen (dort u. a. der Reiter „Berechtigungen“)
            
            ### 2. Projektmanagement
            - **Aufträge:** Projekte, Anfragen
            - **Dokumente:** Dokumente
            - **Planung:** Kalender
            - **Einkauf:** Bestellungen
            
            ### 3. Zeiterfassung
            - **Übersicht:** Kalender (Zeitbuchungen), Monatsabschluss
            - **Berichte:** Auswertung, Steuerberater
            - **Einstellungen:** Zeitkonten, Feiertage
            - **Abwesenheiten:** Urlaubsanträge, Lange Krankheit
            
            ### 4. Kommunikation
            - **E-Mail:** E-Mail Center, Mailtexte für Dokumente, E-Mail-Einstellungen
            - **Telefon:** Anrufe, Anrufbeantworter
            - **Website:** Neuigkeiten
            
            ### 5. Finanzen & Controlling
            - **Buchhaltung:** Offene Posten, Rechnungen (Rechnungsübersicht), Belege & Kasse, Mietabrechnung
            - **Auswertung:** Erfolgsanalyse, Kostenstellen
            
            Oben rechts über den Benutzernamen: „Benutzer verwalten“ (nur Admins).
            
            ## Seitenlinks (klickbare Navigation)
            Wenn du auf eine Seite im Programm verweist, erstelle IMMER einen klickbaren Link im Format:
            [Seitenname](/route)
            
            Der Benutzer sieht dann einen Button, den er anklicken kann, um direkt dorthin zu navigieren.
            
            Verwende AUSSCHLIESSLICH diese Routen:
            | Seite | Link |
            |---|---|
            | Projekte | [Projekte](/projekte) |
            | Anfragen | [Anfragen](/anfragen) |
            | Dokumente | [Dokumente](/dokumentuebersicht) |
            | Kalender | [Kalender](/kalender) |
            | Bestellungen | [Bestellungen](/bestellungen) |
            | Kunden | [Kunden](/kunden) |
            | Mitarbeiter | [Mitarbeiter](/mitarbeiter) |
            | Lieferanten | [Lieferanten](/lieferanten) |
            | Artikel | [Artikel](/artikel) |
            | Arbeitsgänge | [Arbeitsgänge](/arbeitsgaenge) |
            | Kategorien | [Kategorien](/produktkategorien) |
            | Textvorlagen | [Textvorlagen](/textbausteine) |
            | Leistungen | [Leistungen](/leistungen) |
            | Stundensätze | [Stundensätze](/arbeitszeitarten) |
            | Firma | [Firma](/firma) |
            | Formularwesen | [Formularwesen](/formulare) |
            | Einstellungen | [Einstellungen](/einstellungen) |
            | Benutzer verwalten | [Benutzer verwalten](/benutzer) |
            | Zeitbuchungen | [Zeitbuchungen](/zeitbuchungen) |
            | Monatsabschluss | [Monatsabschluss](/monatsabschluss) |
            | Zeitauswertung | [Zeitauswertung](/auswertung) |
            | Steuerberater | [Steuerberater](/steuerberater) |
            | Zeitkonten | [Zeitkonten](/zeitkonten) |
            | Feiertage | [Feiertage](/feiertage) |
            | Urlaubsanträge | [Urlaubsanträge](/urlaubsantraege) |
            | Lange Krankheit | [Lange Krankheit](/langzeitkrankmeldungen) |
            | E-Mail Center | [E-Mail Center](/emails/inbox) |
            | Mailtexte für Dokumente | [Mailtexte für Dokumente](/email-textvorlagen) |
            | E-Mail-Einstellungen | [E-Mail-Einstellungen](/email-einstellungen) |
            | Anrufe | [Anrufe](/telefon/anrufe) |
            | Anrufbeantworter | [Anrufbeantworter](/telefon/anrufbeantworter) |
            | Website-Neuigkeiten | [Neuigkeiten](/website) |
            | Offene Posten | [Offene Posten](/offeneposten) |
            | Rechnungsübersicht | [Rechnungsübersicht](/rechnungsuebersicht) |
            | Belege & Kasse | [Belege & Kasse](/belege-kasse) |
            | Mietabrechnung | [Mietabrechnung](/miete) |
            | Erfolgsanalyse | [Erfolgsanalyse](/analyse) |
            | Kostenstellen | [Kostenstellen](/kostenstellen) |
            
            Beispiel: "Gehe zu [Anfragen](/anfragen) und klicke auf '+ Neues Anfrage'."
            Erstelle bei Schritt-für-Schritt-Anleitungen am Ende einen Link zur relevanten Seite.
            """;

    /** Selbst nachschlagen: wird an den System-Prompt gehaengt, wenn die KI Werkzeuge hat. */
    private static final String NACHSCHLAGEN_PROMPT = """

            ## Selbst nachschlagen (Werkzeuge)
            Unten stehen Abschnitte, die automatisch zur Frage gefunden wurden. Reichen sie nicht für eine
            sichere Antwort, schlage selbst nach, BEVOR du antwortest:
            - `im_programm_suchen`: Suche mit eigenen Begriffen. Probiere Varianten: Fachbegriffe, Beschriftungen
              von Knöpfen und Reitern, Seitennamen, Synonyme (z. B. "Storno", "stornieren", "Gutschrift").
              Bereich "technik" für Regeln und Sperren im Hintergrund (z. B. warum etwas nicht geht).
            - `datei_lesen`: Zeigt ein Treffer nur einen Teil einer Seite, lies die ganze Datei, bevor du
              Knöpfe, Reiter oder Dialoge beschreibst.
            - Mehrere Suchen in einer Runde sind erlaubt. Nach höchstens {{RUNDEN}} Runden antwortest du mit dem,
              was du sicher weißt.
            - Bevor du sagst, dass es etwas nicht gibt oder du es nicht weißt: Suche mindestens einmal gezielt danach.
            - Fragen außerhalb des Programms (Normen, Wetter, Preise) beantwortest du mit der Google-Suche,
              nicht mit diesen Werkzeugen.
            """;

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
    /** Laengste Wartezeit auf eine einzelne Gemini-Antwort. */
    private static final Duration GEMINI_ZEITLIMIT = Duration.ofSeconds(60);
    /** So viel Zeit muss fuer die Antwort selbst bleiben, sonst wird nicht mehr nachgeschlagen. */
    private static final Duration RESERVE_FUER_ANTWORT = GEMINI_ZEITLIMIT;
    /** Gemini meldet so einen kaputten Werkzeug-Aufruf; dann wird einmal ohne Werkzeuge geantwortet. */
    private static final String KAPUTTER_AUFRUF = "MALFORMED_FUNCTION_CALL";

    /** Schritt, den der Nutzer nach der ersten, automatischen Suche sieht. */
    static final String SCHRITT_ERSTE_SUCHE = "Passende Stellen im Programm gesucht";

    private final ObjectMapper objectMapper;
    private final CodebaseIndexService codebaseIndexService;
    private final LocalRagService localRagService;
    private final SystemSettingsService systemSettingsService;
    private final FirmeninformationRepository firmeninformationRepository;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /** Pause vor dem zweiten Versuch, wenn Gemini kurz ueberlastet ist; in Tests ohne Warten. */
    private Duration wiederholungsPause = Duration.ofSeconds(1);

    /**
     * So lange darf eine Frage hoechstens dauern, ueber alle Runden und den Rueckfall hinweg. Kuerzer
     * als das Zeitlimit des Chats ({@code KiHilfeController}), damit der Nutzer eine Meldung bekommt.
     */
    private Duration gesamtZeitlimit = Duration.ofSeconds(150);

    @Value("${ai.ki-hilfe.model:gemini-3-flash-preview}")
    private String model;

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

    /**
     * System-Prompt mit den automatisch gefundenen Abschnitten (falls vorhanden). Sonst haengt ohne
     * Werkzeuge der vollstaendige Codebase-Index als Rueckfall an; mit Werkzeugen nicht - er ginge
     * in jeder Runde neu mit, und die KI kann selbst suchen.
     */
    String buildSystemPrompt(String ragContext, boolean mitWerkzeugen) {
        String basisPrompt = BASE_SYSTEM_PROMPT.replace("{{BETRIEB}}", betriebsBezeichnung());
        if (mitWerkzeugen) {
            basisPrompt += NACHSCHLAGEN_PROMPT.replace("{{RUNDEN}}", String.valueOf(MAX_NACHSCHLAGE_RUNDEN));
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
     * Runden), Fachfragen per Google-Suche.
     *
     * @param fortschritt bekommt jeden Schritt als kurzen Text fuer den Nutzer ("Schaut nach: ...")
     */
    public ChatResult chat(List<ChatMessage> messages, PageContext pageContext, Consumer<String> fortschritt)
            throws IOException, InterruptedException {
        // Key zur Laufzeit aus System-Setup lesen.
        String geminiApiKey = systemSettingsService.getGeminiApiKey();
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            throw new IOException("Gemini API Key fehlt (ai.gemini.api-key)");
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

        String systemPrompt = buildSystemPrompt(ragContext, werkzeuge != null);
        try {
            return beantworten(geminiApiKey, systemPrompt, verlauf(limitedMessages, pageContextInfo),
                    werkzeuge, melden, schritte, frist);
        } catch (AnfrageAbgelehnt e) {
            // Sicherheitsnetz: Kann das eingestellte Modell eigene Werkzeuge (zusammen mit der
            // Google-Suche) nicht, antwortet die KI-Hilfe wie frueher - ohne selbst nachzuschlagen.
            if (werkzeuge == null || e.status != 400) throw e;
            log.warn("Gemini lehnt die Anfrage mit Werkzeugen ab (HTTP 400) - Antwort ohne Nachschlagen. "
                    + "Unterstuetzt das Modell '{}' Function Calling zusammen mit der Google-Suche?", model);
            return beantworten(geminiApiKey, buildSystemPrompt(ragContext, false),
                    verlauf(limitedMessages, pageContextInfo), null, melden, schritte, frist);
        }
    }

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

    /** Die Frage hat die Gesamtzeit ueberschritten; der Nutzer soll es noch einmal versuchen. */
    public static final class ZeitlimitErreicht extends IOException {
        public ZeitlimitErreicht() {
            super("Zeitlimit der KI-Hilfe erreicht");
        }
    }

    /** Gemini hat die Anfrage abgelehnt; ein zweiter Versuch mit derselben Anfrage hilft nicht. */
    static final class AnfrageAbgelehnt extends IOException {
        final int status;

        AnfrageAbgelehnt(int status) {
            super("KI-Hilfe nicht verfügbar (Fehler " + status + ")");
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

    /** Gespraechsverlauf fuer Gemini; der Seitenkontext haengt an der letzten Frage. */
    private ArrayNode verlauf(List<ChatMessage> messages, String pageContextInfo) {
        ArrayNode contents = objectMapper.createArrayNode();
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            String role = "user".equals(msg.role()) ? "user" : "model";
            String text = msg.text();
            boolean isLastUserMessage = "user".equals(msg.role()) && i == messages.size() - 1;
            if (isLastUserMessage && pageContextInfo != null && !pageContextInfo.isBlank()) {
                text = text + "\n\n" + pageContextInfo;
            }
            ObjectNode msgNode = contents.addObject().put("role", role);
            msgNode.putArray("parts").addObject().put("text", text);
        }
        return contents;
    }

    /**
     * Die Nachschlage-Schleife: Gemini antwortet oder ruft Werkzeuge auf; deren Ergebnisse gehen in
     * den Verlauf, und es geht in die naechste Runde. In der letzten Runde sind Werkzeug-Aufrufe
     * abgeschaltet ({@code mode NONE}) - dann muss eine Antwort kommen.
     */
    private ChatResult beantworten(String geminiApiKey, String systemPrompt, ArrayNode contents,
                                   KiHilfeWerkzeuge werkzeuge, Consumer<String> melden, List<String> schritte,
                                   Frist frist) throws IOException, InterruptedException {
        Map<String, SourceLink> quellen = new LinkedHashMap<>();
        Set<String> websuchen = new LinkedHashSet<>();
        boolean antwortErzwingen = false;
        for (int runde = 0; ; runde++) {
            boolean nachschlagen = werkzeuge != null && runde < MAX_NACHSCHLAGE_RUNDEN && !antwortErzwingen
                    && frist.nachschlagenErlaubt();
            JsonNode kandidat = GeminiVerlauf.kandidat(
                    senden(anfrage(systemPrompt, contents, werkzeuge, nachschlagen), geminiApiKey, frist));

            websuchenMelden(kandidat, websuchen, melden);
            for (SourceLink quelle : extractGroundingSources(kandidat)) {
                quellen.putIfAbsent(quelle.url(), quelle);
            }

            List<JsonNode> aufrufe = GeminiVerlauf.funktionsaufrufe(kandidat);
            if (aufrufe.isEmpty() || !nachschlagen) {
                if (kandidat.isMissingNode()) {
                    throw new IOException("Keine Antwort von der KI erhalten");
                }
                String replyText = GeminiVerlauf.text(kandidat);
                if (replyText.isBlank() && nachschlagen
                        && KAPUTTER_AUFRUF.equals(kandidat.path("finishReason").asText(""))) {
                    // Die KI hat einen Werkzeug-Aufruf verbockt: noch einmal, dann aber mit Antwortpflicht
                    log.warn("  -> Gemini: kaputter Werkzeug-Aufruf, Antwort ohne weiteres Nachschlagen");
                    antwortErzwingen = true;
                    continue;
                }
                if (replyText.isBlank()) {
                    throw new IOException("Keine Textantwort von der KI erhalten");
                }
                log.info("  -> Antwort nach {} Nachschlage-Runde(n), {} Web-Quellen", runde, quellen.size());
                return new ChatResult(replyText, List.copyOf(quellen.values()), List.copyOf(schritte));
            }

            contents.add(GeminiVerlauf.modellZug(kandidat));
            List<ObjectNode> antworten = new ArrayList<>();
            for (int i = 0; i < aufrufe.size(); i++) {
                JsonNode aufruf = aufrufe.get(i);
                // Je Aufruf pruefen: Auch das Einbetten der Werkzeug-Suchen kostet Zeit
                boolean nochZeit = frist.nachschlagenErlaubt();
                antwortErzwingen |= !nochZeit;
                ObjectNode ergebnis = !nochZeit ? werkzeuge.keineZeitMehr()
                        : i < MAX_AUFRUFE_JE_RUNDE ? werkzeuge.ausfuehren(aufruf) : werkzeuge.zuVieleAufrufe();
                antworten.add(GeminiVerlauf.antwortTeil(aufruf, ergebnis));
            }
            contents.add(GeminiVerlauf.antwortZug(antworten));
            log.info("  -> Nachschlage-Runde {}: {} Werkzeug-Aufruf(e)", runde + 1, aufrufe.size());
        }
    }

    private ObjectNode anfrage(String systemPrompt, ArrayNode contents, KiHilfeWerkzeuge werkzeuge,
                               boolean nachschlagen) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.putObject("systemInstruction").putArray("parts").addObject().put("text", systemPrompt);
        requestBody.set("contents", contents);

        ObjectNode config = requestBody.putObject("generationConfig");
        config.put("temperature", temperature);
        config.put("maxOutputTokens", maxOutputTokens);

        ArrayNode tools = objectMapper.createArrayNode();
        // Google Search grounding tool – allows Gemini to search the web
        if (webSearchEnabled) {
            tools.addObject().putObject("googleSearch");
        }
        if (werkzeuge != null) {
            tools.addObject().set("functionDeclarations", werkzeuge.deklarationen());
        }
        if (!tools.isEmpty()) {
            requestBody.set("tools", tools);
        }

        if (werkzeuge != null && (webSearchEnabled || !nachschlagen)) {
            ObjectNode toolConfig = requestBody.putObject("toolConfig");
            if (webSearchEnabled) {
                // Google-Suche und eigene Werkzeuge in einer Anfrage (Gemini 3): Die Suchaufrufe
                // kommen dann im Verlauf mit und gehen im naechsten Zug unveraendert zurueck.
                toolConfig.put("includeServerSideToolInvocations", true);
            }
            if (!nachschlagen) {
                toolConfig.putObject("functionCallingConfig").put("mode", "NONE");
            }
        }
        return requestBody;
    }

    private JsonNode senden(ObjectNode requestBody, String geminiApiKey, Frist frist)
            throws IOException, InterruptedException {
        String body = objectMapper.writeValueAsString(requestBody);
        for (int versuch = 1; ; versuch++) {
            Duration rest = frist.rest();
            if (rest.isNegative() || rest.isZero()) {
                throw new ZeitlimitErreicht();
            }
            HttpRequest request = GeminiAnfrage.generateContent(model, geminiApiKey)
                    .timeout(rest.compareTo(GEMINI_ZEITLIMIT) < 0 ? rest : GEMINI_ZEITLIMIT)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (HttpTimeoutException e) {
                if (!frist.rest().isPositive()) throw new ZeitlimitErreicht();
                throw e;
            }
            if (response.statusCode() == 200) {
                return objectMapper.readTree(response.body());
            }
            if (versuch == 1 && GeminiAnfrage.istVoruebergehend(response.statusCode())) {
                log.warn("Gemini KI-Hilfe: HTTP {} - neuer Versuch", response.statusCode());
                Thread.sleep(wiederholungsPause.toMillis());
                continue;
            }
            log.error("Gemini KI-Hilfe API Error {}: {}", response.statusCode(), GeminiAnfrage.fuerLog(response.body()));
            throw new AnfrageAbgelehnt(response.statusCode());
        }
    }

    /** Neue Internet-Suchen der Runde als Schritt melden; ins Log nur die Anzahl (sie enthalten die Frage). */
    private void websuchenMelden(JsonNode kandidat, Set<String> bisher, Consumer<String> melden) {
        JsonNode queries = kandidat.path("groundingMetadata").path("webSearchQueries");
        if (!queries.isArray() || queries.isEmpty()) return;
        log.info("  -> Google Search Queries: {}", queries.size());
        for (JsonNode query : queries) {
            String text = query.asText("").strip();
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

    /**
     * Extracts source links from Gemini Google Search grounding metadata.
     */
    private List<SourceLink> extractGroundingSources(JsonNode candidate) {
        JsonNode chunks = candidate.path("groundingMetadata").path("groundingChunks");
        if (!chunks.isArray() || chunks.isEmpty()) {
            return Collections.emptyList();
        }

        List<SourceLink> sources = new ArrayList<>();
        for (JsonNode chunk : chunks) {
            JsonNode web = chunk.path("web");
            String uri = web.path("uri").asText("");
            String title = web.path("title").asText("");
            if (!uri.isEmpty()) {
                sources.add(new SourceLink(title.isEmpty() ? uri : title, uri));
            }
        }
        return sources;
    }
}
