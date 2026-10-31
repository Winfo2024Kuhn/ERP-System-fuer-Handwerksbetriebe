package org.example.kalkulationsprogramm.service;

import java.net.URI;
import java.net.http.HttpRequest;

/**
 * Baut Anfragen an die Gemini-API (Google KI) für alle Dienste gleich.
 *
 * <p>Der Schlüssel reist immer im Header {@value #SCHLUESSEL_HEADER}, nie in der URL: URLs stehen in
 * Fehlermeldungen (etwa von {@link URI#create(String)} oder Proxys) und damit im Log.
 */
public final class GeminiAnfrage {

    /** Header, in dem Google den API-Schlüssel erwartet. */
    public static final String SCHLUESSEL_HEADER = "x-goog-api-key";

    static final String MODELLE_URL = "https://generativelanguage.googleapis.com/v1beta/models";

    /** So viele Zeichen einer Fehlerantwort kommen höchstens ins Log. */
    static final int LOG_LAENGE = 200;

    private GeminiAnfrage() {
    }

    /** Anfrage zum Erzeugen von Inhalten mit dem Modell, z. B. {@code gemini-2.5-flash}. */
    public static HttpRequest.Builder generateContent(String modell, String schluessel) {
        return modellMethode(modell, ":generateContent", schluessel);
    }

    /** Anfrage an eine Methode des Modells, z. B. {@code :batchEmbedContents}. */
    public static HttpRequest.Builder modellMethode(String modell, String methode, String schluessel) {
        return mitSchluessel(URI.create(MODELLE_URL + "/" + modell + methode), schluessel);
    }

    /** Liste der Modelle; genügt, um einen Schlüssel zu prüfen. */
    public static HttpRequest.Builder modellListe(String schluessel) {
        return mitSchluessel(URI.create(MODELLE_URL), schluessel);
    }

    /** Liste der Modelle mit großer Seite, um eingestellte Modellnamen nachzuschlagen. */
    public static HttpRequest.Builder modellListe(String schluessel, int seitenGroesse) {
        return mitSchluessel(URI.create(MODELLE_URL + "?pageSize=" + seitenGroesse), schluessel);
    }

    /**
     * Lohnt ein zweiter Versuch? Bei Ueberlastung (429) und Stoerungen bei Google (5xx) ja; andere
     * Fehler (falscher Schluessel, ungueltige Anfrage) kommen beim naechsten Mal genauso.
     */
    public static boolean istVoruebergehend(int status) {
        return status == 429 || (status >= 500 && status < 600);
    }

    /** Fehlerantwort der API gekürzt fürs Log. */
    public static String fuerLog(String antwort) {
        if (antwort == null) {
            return "";
        }
        return antwort.length() <= LOG_LAENGE ? antwort : antwort.substring(0, LOG_LAENGE) + "…";
    }

    private static HttpRequest.Builder mitSchluessel(URI adresse, String schluessel) {
        return HttpRequest.newBuilder(adresse)
                .header("Content-Type", "application/json")
                .header(SCHLUESSEL_HEADER, schluessel);
    }
}
