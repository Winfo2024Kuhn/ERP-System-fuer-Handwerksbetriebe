package org.example.kalkulationsprogramm.service;

import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.dto.SpracheingabeErgebnis;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterFehler;
import org.example.kalkulationsprogramm.service.ki.KiAnfrage;
import org.example.kalkulationsprogramm.service.ki.KiDienst;
import org.example.kalkulationsprogramm.service.ki.KiFunktion;
import org.example.kalkulationsprogramm.service.ki.KiInhaltNichtLesbar;
import org.example.kalkulationsprogramm.service.ki.KiNichtVerfuegbar;
import org.example.kalkulationsprogramm.service.ki.KiTeil;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

/**
 * Wandelt eine Sprachaufnahme aus der mobilen Zeiterfassung per KI in
 * nach Themen geordnete Stichpunkte um (Erledigt, Material verbraucht,
 * Probleme / Offen). Ein Handwerker diktiert damit unterwegs auf der
 * Baustelle einen Bautagebuch-Eintrag, oft in lauter Umgebung.
 *
 * <p><b>Datenschutz.</b> Es handelt sich um Sprachaufnahmen von Mitarbeitern
 * im Arbeitsverhaeltnis, nicht um oeffentliche oder betriebliche Dokumente.
 * Audio und Transkript leben ausschliesslich im Arbeitsspeicher: die
 * Aufnahme wird nie auf Platte geschrieben, nicht zwischengespeichert und in
 * keiner Datenbank-Spalte abgelegt. Nach dem KI-Aufruf werden beide
 * verworfen. Weder die Audio-Bytes noch der zurueckgegebene Text duerfen
 * jemals in ein Log gelangen, auch nicht gekuerzt und auch nicht im
 * Fehlerfall. Log-Zeilen dieser Klasse enthalten ausschliesslich technische
 * Metadaten (Bytes, Inhaltstyp, HTTP-Status). Welcher Anbieter die Aufnahme
 * bekommt, entscheidet {@link KiDienst} (Einstellungen → KI-Funktionen).
 */
@Slf4j
@Service
public class SpracheingabeService {

    static final String SYSTEM_ANWEISUNG = """
            Du bekommst eine Sprachaufnahme von einem Handwerker einer Bauschlosserei.
            Er diktiert einen Eintrag, oft auf einer lauten Baustelle.

            Er spricht frei und springt dabei zwischen Themen hin und her.

            Deine Aufgabe ist, das Gesagte als knappe Stichpunkte zu notieren
            und nach Themen zu ordnen, egal in welcher Reihenfolge es gesagt wurde.
            Verwende dafuer genau diese Abschnitte, in dieser Reihenfolge:

            Erledigt:
            Was gemacht, montiert, gefertigt, geprueft oder besprochen wurde.

            Material verbraucht:
            Verbrauchtes oder verbautes Material und Werkzeug-Verschleiss,
            mit Menge vorne, zum Beispiel "- 5 Bohrer" oder "- 3 Flexscheiben".
            Wurde keine Menge genannt, schreibst du keine Zahl hin.

            Probleme / Offen:
            Schwierigkeiten, Maengel, Fehlendes und was noch zu tun ist.

            Lass einen Abschnitt komplett weg, wenn nichts dazu gesagt wurde.
            Lass keine Angabe weg. Was zu keinem Abschnitt passt, etwa Wetter oder
            wer vor Ort war, schreibst du als Stichpunkte ohne Ueberschrift ans Ende.
            Passt das Gesagte zu keinem der Abschnitte, etwa eine kurze Bemerkung,
            schreibe nur die Stichpunkte, ohne Ueberschrift.

            Gib ausschliesslich die Stichpunkte zurueck, ohne Vorwort und ohne Kommentar.
            Schreibe nur, was gesagt wurde. Erfinde nichts dazu.
            Jede Angabe kommt genau einmal vor. Zahlen schreibst du als Ziffern.
            Werte, Masse, Mengen und Namen veraenderst du nicht.
            Lass Fuellwoerter weg, also aeh, aehm, halt, sozusagen.
            Korrigiert sich der Sprecher selbst, schreibe nur die korrigierte Fassung.
            Jeder Stichpunkt ist ein kurzer Satz oder eine kurze Wortgruppe.

            Das Ergebnis ist reiner Text.
            Keine Sternchen, keine Rauten, keine HTML-Tags, keine Markdown-Syntax.
            Jeder Stichpunkt steht in einer eigenen Zeile mit einem fuehrenden Bindestrich.
            Die Abschnitte trennst du durch eine Leerzeile.

            Der Sprecher arbeitet im Metallbau. Rechne mit Begriffen wie
            Feuerverzinkung, Pulverbeschichtung, VSG, ESG, Schwerlastanker,
            Klebeduebel, Konterlattung, Absturzsicherung.
            Profile schreibst du als HEB 200, IPE 160, Rohrprofil 40x40x3.
            Normen als DIN EN 1090, DIN 18008.

            Verstehst du eine Stelle akustisch nicht, schreibe dort [unverstaendlich].
            Rate nicht.
            """;

    static final int MAX_AUDIO_BYTES = 10 * 1024 * 1024;

    static final Set<String> ERLAUBTE_INHALTSTYPEN = Set.of(
            "audio/webm", "audio/ogg", "audio/mp4", "audio/m4a",
            "audio/aac", "audio/wav", "audio/mpeg");

    private static final Duration ZEITLIMIT = Duration.ofSeconds(90);
    private static final int LESE_PUFFER_BYTES = 8192;

    private final KiDienst kiDienst;

    public SpracheingabeService(KiDienst kiDienst) {
        this.kiDienst = kiDienst;
    }

    /**
     * Die Spracheingabe gehoert zu den freiwilligen KI-Funktionen: ohne
     * passenden KI-Anbieter ist sie aus, und die App blendet den Mikrofon-Knopf aus.
     */
    public boolean istVerfuegbar() {
        return kiDienst.verfuegbar(KiFunktion.SPRACHEINGABE);
    }

    /** Liest den Strom begrenzt in den Speicher, prueft, fragt die KI, liefert den Text. */
    public SpracheingabeErgebnis transkribiere(InputStream audioStrom, String inhaltstyp) {
        String normalisiert = normalisiereInhaltstyp(inhaltstyp);
        if (!ERLAUBTE_INHALTSTYPEN.contains(normalisiert)) {
            throw new IllegalArgumentException("Dieses Audioformat wird nicht unterstuetzt.");
        }

        // Vor dem Lesen pruefen: ohne passenden Anbieter muss die Aufnahme gar nicht erst in den Speicher.
        if (!istVerfuegbar()) {
            throw new KiNichtEingerichtet();
        }

        byte[] audio = leseHoechstens(audioStrom, MAX_AUDIO_BYTES);
        if (audio.length == 0) {
            throw new IllegalArgumentException("Die Aufnahme ist leer.");
        }

        try {
            String text = kiDienst.sende(KiFunktion.SPRACHEINGABE, baueAnfrage(audio, normalisiert)).text();
            return new SpracheingabeErgebnis(text.trim());
        } catch (KiNichtVerfuegbar e) {
            throw new KiNichtEingerichtet();
        } catch (KiInhaltNichtLesbar e) {
            // Kein Ausfall, sondern dauerhaft: der Nutzer bekommt den Grund (nur Anbieter und Format).
            log.warn("[Spracheingabe] {} ({} Bytes)", e.getMessage(), audio.length);
            throw new FormatNichtUnterstuetzt(e.getMessage());
        } catch (KiAnbieterFehler e) {
            // DSGVO: Die Meldung enthaelt nie Inhalte (KiAnbieterFehler), hier nur Metadaten.
            log.warn("[Spracheingabe] KI-Aufruf gescheitert, HTTP {}: {} ({} Bytes, {})", e.status(), e.getMessage(),
                    audio.length, normalisiert);
            throw new IllegalStateException("Die Spracherkennung hat mit einem Fehler geantwortet.", e);
        }
    }

    /**
     * Sichtbar fuer den Test: die Anfrage an die KI. Genau eine Datei, kein
     * begleitender Text - die Aufnahme ist der gesamte fachliche Inhalt.
     */
    KiAnfrage baueAnfrage(byte[] audio, String inhaltstyp) {
        return KiAnfrage.mitAnweisung(SYSTEM_ANWEISUNG)
                .nutzer(KiTeil.datei(audio, inhaltstyp))
                .temperatur(0.0)
                .zeitlimit(ZEITLIMIT)
                .bauen();
    }

    /**
     * Liest hoechstens {@code max} Bytes in einen {@link ByteArrayOutputStream}
     * und bricht ab, sobald max + 1 Bytes gelesen wurden. Puffert bewusst
     * NICHT erst alles und prueft danach die Groesse - sonst koennte ein
     * manipulierter Client beliebig viel Speicher belegen.
     */
    private byte[] leseHoechstens(InputStream eingabe, int max) {
        ByteArrayOutputStream puffer = new ByteArrayOutputStream();
        byte[] block = new byte[LESE_PUFFER_BYTES];
        try {
            int gelesen;
            while ((gelesen = eingabe.read(block)) != -1) {
                puffer.write(block, 0, gelesen);
                if (puffer.size() > max) {
                    throw new AufnahmeZuGross();
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Die Aufnahme konnte nicht gelesen werden.", e);
        }
        return puffer.toByteArray();
    }

    /** Sichtbar fuer den Test: "audio/webm;codecs=opus" -> "audio/webm". */
    static String normalisiereInhaltstyp(String rohwert) {
        if (rohwert == null) {
            return "";
        }
        int trennzeichen = rohwert.indexOf(';');
        String ohneParameter = trennzeichen < 0 ? rohwert : rohwert.substring(0, trennzeichen);
        return ohneParameter.trim().toLowerCase(Locale.ROOT);
    }

    /** 503 statt 502: kein Ausfall, sondern bewusst ausgeschaltet (kein KI-Schluessel). */
    public static class KiNichtEingerichtet extends IllegalStateException {
        public KiNichtEingerichtet() {
            super("Fuer die Spracheingabe ist keine KI eingerichtet.");
        }
    }

    /** 400 mit Klartext: Der eingestellte Anbieter kann dieses Aufnahmeformat nicht lesen. */
    public static class FormatNichtUnterstuetzt extends IllegalArgumentException {
        FormatNichtUnterstuetzt(String meldung) {
            super(meldung);
        }
    }

    /** 413 statt 400: eigene Unterklasse, damit der Controller sie trennen kann. */
    public static class AufnahmeZuGross extends IllegalArgumentException {
        AufnahmeZuGross() {
            super("Die Aufnahme ist zu lang. Bitte kuerzer diktieren.");
        }
    }
}
