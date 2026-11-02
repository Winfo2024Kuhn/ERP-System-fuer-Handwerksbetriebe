package org.example.kalkulationsprogramm.service.kihilfe;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Fester Hinweistext der KI-Hilfe (Systemtext ohne die gefundenen Abschnitte). Der große Teil mit Menü,
 * Navigationsstruktur und Seitenlinks steht in der Ressource {@value #RESSOURCE}; die Platzhalter
 * {@code {{BETRIEB}}}, {@code {{WEB_STIL}}} und {@code {{WEB_ABSCHNITT}}} füllt {@code KiHilfeService}.
 * Die Bausteine zu Werkzeugen und Internet-Suche stehen hier, weil sie je Anbieter wechseln.
 *
 * <p>Die Seite „Bedarf“ ({@code /bestellungen/bedarf}) fehlt im Hinweistext mit Absicht: Sie ist noch nicht
 * freigegeben ({@code react-pc-frontend/src/lib/freigaben.ts}). Bei der Freigabe unter „Einkauf“ und in der
 * Linkliste wieder eintragen. {@code KiHilfeMenueAbgleichTest} vergleicht Menü und Seitenlinks mit der
 * Menüleiste.</p>
 */
public final class KiHilfeHinweistext {

    /** Pfad im Klassenpfad ({@code src/main/resources}). */
    public static final String RESSOURCE = "kihilfe/hinweistext.md";

    /** Einmal beim Laden der Klasse gelesen; fehlt die Datei, scheitert schon der erste Zugriff verständlich. */
    private static final String BASIS = lade(RESSOURCE);

    /** Selbst nachschlagen: wird an den System-Prompt gehaengt, wenn die KI Werkzeuge hat. */
    private static final String NACHSCHLAGEN = """

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
            {{WEB_NACHSCHLAGEN}}
            """;

    /** Teile des Prompts zur Internet-Suche: mit Suche (Gemini) wie seit jeher, ohne Suche ehrlich. */
    public record WebTexte(String stil, String abschnitt, String nachschlagen) {}

    private static final WebTexte MIT_INTERNET = new WebTexte("""
            - Wenn der user eine Frage außerhalb des Programms stellt, nutze die Web-Suche um eine aktuelle und fundierte Antwort zu geben.
            """, """
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
            
            """, """
            - Fragen außerhalb des Programms (Normen, Wetter, Preise) beantwortest du mit der Google-Suche,
              nicht mit diesen Werkzeugen.
            """);

    private static final WebTexte OHNE_INTERNET = new WebTexte("""
            - Bei Fragen außerhalb des Programms antwortest du kurz aus deinem Allgemeinwissen.
            """, """
            ## Fragen außerhalb des Programms
            Du hast KEINEN Zugriff auf das Internet und kannst nichts nachsehen. Bei Fachfragen (Normen,
            Vorschriften, Materialien) antwortest du kurz aus deinem Allgemeinwissen und sagst dazu, dass du
            den aktuellen Stand nicht prüfen kannst. Aktuelle Dinge (Wetter, Preise, Nachrichten) kannst du
            nicht beantworten - sag das ehrlich, statt etwas zu erfinden.

            WICHTIG: Bei Fragen zum Kalkulationsprogramm selbst, nutze IMMER den bereitgestellten Quellcode
            und die interne Wissensbasis.

            """, """
            - Fragen außerhalb des Programms beantwortest du ohne diese Werkzeuge.
            """);

    private KiHilfeHinweistext() {
    }

    /** Hinweistext mit Menü und Seitenlinks, Platzhalter noch ungefüllt. */
    public static String basis() {
        return BASIS;
    }

    /** Abschnitt „Selbst nachschlagen“, Platzhalter {@code {{RUNDEN}}} und {@code {{WEB_NACHSCHLAGEN}}} ungefüllt. */
    public static String nachschlagen() {
        return NACHSCHLAGEN;
    }

    /** Bausteine zur Internet-Suche: mit Suche (Gemini) oder ehrlich ohne. */
    public static WebTexte web(boolean mitInternet) {
        return mitInternet ? MIT_INTERNET : OHNE_INTERNET;
    }

    static String lade(String ressource) {
        try (InputStream in = KiHilfeHinweistext.class.getClassLoader().getResourceAsStream(ressource)) {
            if (in == null) {
                throw new IllegalStateException("Hinweistext der KI-Hilfe fehlt im Programm: " + ressource);
            }
            return zuText(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Hinweistext der KI-Hilfe ist nicht lesbar: " + ressource, e);
        }
    }

    /** UTF-8; Zeilenenden aus einem Windows-Checkout (CRLF) werden zu LF, damit die Platzhalter passen. */
    static String zuText(InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
