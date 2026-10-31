package org.example.kalkulationsprogramm.service.ki;

import java.util.ArrayList;
import java.util.List;

/**
 * Antwort einer KI.
 *
 * @param text                     die Antwort, nie {@code null}
 * @param abgeschnitten            true, wenn die Antwort am Ausgabelimit endete
 * @param anbieter                 wer geantwortet hat
 * @param modell                   mit welchem Modell
 * @param werkzeugAufrufe          Werkzeuge, die die KI aufrufen möchte (leer ohne Werkzeuge)
 * @param werkzeugAufrufFehlerhaft true, wenn die KI einen Werkzeug-Aufruf nicht sauber bilden konnte
 * @param websuchen                Suchbegriffe der Internet-Suche; enthalten die Frage, nie loggen
 * @param quellen                  Internet-Seiten, auf die sich die Antwort stützt
 * @param roh                      der Zug im Format des Anbieters für die nächste Runde, sonst {@code null}
 */
public record KiErgebnis(String text, boolean abgeschnitten, KiAnbieterTyp anbieter, String modell,
                         List<KiTeil.WerkzeugAufruf> werkzeugAufrufe, boolean werkzeugAufrufFehlerhaft,
                         List<String> websuchen, List<KiQuelle> quellen, KiRohZug roh) {

    public KiErgebnis {
        text = text == null ? "" : text;
        werkzeugAufrufe = werkzeugAufrufe == null ? List.of() : List.copyOf(werkzeugAufrufe);
        websuchen = websuchen == null ? List.of() : List.copyOf(websuchen);
        quellen = quellen == null ? List.of() : List.copyOf(quellen);
    }

    /** Nur Metadaten: Antwort, Websuchen und Aufrufe enthalten Geschäftsdaten (DSGVO). */
    @Override
    public String toString() {
        return "KiErgebnis[anbieter=" + anbieter + ", modell=" + modell + ", " + text.length() + " Zeichen, "
                + werkzeugAufrufe.size() + " Aufrufe, abgeschnitten=" + abgeschnitten + "]";
    }

    /** Eine reine Text-Antwort. */
    public KiErgebnis(String text, boolean abgeschnitten, KiAnbieterTyp anbieter, String modell) {
        this(text, abgeschnitten, anbieter, modell, List.of(), false, List.of(), List.of(), null);
    }

    /** Diese Antwort als Nachricht der KI für den Verlauf der nächsten Runde. */
    public KiNachricht alsNachricht() {
        List<KiTeil> teile = new ArrayList<>();
        if (!text.isEmpty()) {
            teile.add(KiTeil.text(text));
        }
        teile.addAll(werkzeugAufrufe);
        return new KiNachricht(KiNachricht.Rolle.KI, teile, roh);
    }
}
