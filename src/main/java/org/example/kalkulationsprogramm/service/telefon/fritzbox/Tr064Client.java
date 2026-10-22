package org.example.kalkulationsprogramm.service.telefon.fritzbox;

import org.example.kalkulationsprogramm.service.telefon.TelefonAnlageException;
import org.example.kalkulationsprogramm.service.telefon.TelefonAnlageException.Grund;
import org.example.kalkulationsprogramm.service.telefon.TelefonZugang;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimaler TR-064-Client (SOAP über HTTP mit Digest-Anmeldung).
 * <p>
 * Aufgerufen werden ausschließlich Adressen auf dem eingestellten Host und
 * Port – URLs, die die FRITZ!Box zurückgibt, werden auf Pfad + Query
 * reduziert und an die eigene Basisadresse gehängt (kein SSRF über Antworten).
 */
class Tr064Client {

    static final int STANDARD_PORT = 49000;
    private static final int MAX_ANTWORT_BYTES = 50 * 1024 * 1024;
    private static final Pattern SOAP_FEHLERCODE = Pattern.compile("<errorCode>(\\d{1,5})</errorCode>");
    private static final Pattern SOAP_FEHLERTEXT = Pattern.compile("<errorDescription>([^<]{1,200})</errorDescription>");
    /** Höchstlänge für Texte der Box im Fehler-Detail (Dateiname, Fehlerbeschreibung). */
    private static final int MAX_DETAIL_TEXT = 60;

    private final int port;
    private final Duration antwortTimeout;
    private final HttpClient http;

    Tr064Client() {
        this(STANDARD_PORT, Duration.ofSeconds(5), Duration.ofSeconds(15));
    }

    Tr064Client(int port, Duration verbindungsTimeout, Duration antwortTimeout) {
        this.port = port;
        this.antwortTimeout = antwortTimeout;
        this.http = HttpClient.newBuilder()
                .connectTimeout(verbindungsTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** Führt eine SOAP-Aktion aus und liefert die Ausgabe-Parameter (z.B. "NewCallListURL"). */
    Map<String, String> aktion(TelefonZugang zugang, String controlUrl, String serviceTyp,
                               String aktion, Map<String, String> argumente) {
        StringBuilder args = new StringBuilder();
        argumente.forEach((k, v) -> args.append('<').append(k).append('>')
                .append(xmlEscape(v)).append("</").append(k).append('>'));
        String body = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\""
                + " s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>"
                + "<u:" + aktion + " xmlns:u=\"" + serviceTyp + "\">" + args + "</u:" + aktion + ">"
                + "</s:Body></s:Envelope>";
        byte[] antwort = ausfuehren(zugang, "POST", controlUrl, body, serviceTyp + "#" + aktion, aktion);
        Document doc;
        try {
            doc = SicheresXml.parse(antwort);
        } catch (TelefonAnlageException e) {
            throw new TelefonAnlageException(Grund.UNERWARTETE_ANTWORT, aktion + ": Antwort nicht lesbar", e);
        }
        List<Element> treffer = SicheresXml.elemente(doc, aktion + "Response");
        if (treffer.isEmpty()) {
            throw new TelefonAnlageException(Grund.UNERWARTETE_ANTWORT, aktion + ": Antwort ohne Ergebnis", null);
        }
        return SicheresXml.kinder(treffer.getFirst());
    }

    /**
     * Lädt eine Datei, deren URL (oder Pfad) die FRITZ!Box geliefert hat.
     * Nur Pfad und Query werden übernommen, Host und Port sind immer die eingestellten.
     */
    byte[] laden(TelefonZugang zugang, String urlOderPfad) {
        String pfad;
        try {
            pfad = pfadUndQuery(urlOderPfad);
        } catch (TelefonAnlageException e) {
            throw new TelefonAnlageException(Grund.UNERWARTETE_ANTWORT, "Datei laden: Adresse ungültig", e);
        }
        return ausfuehren(zugang, "GET", pfad, null, null, dateiname(pfad) + " laden");
    }

    /**
     * "/phonebook.lua?sid=1&pbid=0" → "phonebook.lua". Ohne Query, weil dort die
     * Sitzungs-ID der Box steht – die gehört weder ins Log noch auf die Einstellungsseite.
     */
    static String dateiname(String pfadUndQuery) {
        int frage = pfadUndQuery.indexOf('?');
        String pfad = frage < 0 ? pfadUndQuery : pfadUndQuery.substring(0, frage);
        String name = pfad.substring(pfad.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._-]", "");
        if (name.length() > MAX_DETAIL_TEXT) {
            name = name.substring(0, MAX_DETAIL_TEXT);
        }
        return name.isEmpty() ? "Datei" : name;
    }

    /** "https://fritz.box:49443/calllist.lua?sid=1" → "/calllist.lua?sid=1". */
    static String pfadUndQuery(String urlOderPfad) {
        if (urlOderPfad == null || urlOderPfad.isBlank()) {
            throw new TelefonAnlageException(Grund.UNERWARTETE_ANTWORT);
        }
        String s = urlOderPfad.trim();
        if (s.startsWith("/")) {
            if (s.startsWith("//")) {
                throw new TelefonAnlageException(Grund.UNERWARTETE_ANTWORT);
            }
            return s;
        }
        try {
            URI uri = URI.create(s);
            if (!uri.isAbsolute()) {
                // Relativ ohne führenden "/" (z.B. "x@evil.test/a") würde, an die
                // Basisadresse gehängt, Host und Zugangsdaten der URL verändern.
                throw new TelefonAnlageException(Grund.UNERWARTETE_ANTWORT);
            }
            String pfad = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
            if (!pfad.startsWith("/") || pfad.startsWith("//")) {
                throw new TelefonAnlageException(Grund.UNERWARTETE_ANTWORT);
            }
            return uri.getRawQuery() == null ? pfad : pfad + "?" + uri.getRawQuery();
        } catch (IllegalArgumentException e) {
            throw new TelefonAnlageException(Grund.UNERWARTETE_ANTWORT, e);
        }
    }

    /**
     * Baut die Zieladresse und stellt sicher, dass sie wirklich auf die
     * eingestellte FRITZ!Box zeigt – nie auf einen anderen Host oder Port.
     */
    URI zielAdresse(String host, String pfad) {
        String h = FritzBoxHost.pruefe(host);
        if (pfad == null || !pfad.startsWith("/") || pfad.startsWith("//")) {
            throw new TelefonAnlageException(Grund.UNERWARTETE_ANTWORT);
        }
        URI uri;
        try {
            uri = URI.create("http://" + h + ":" + port + pfad);
        } catch (IllegalArgumentException e) {
            throw new TelefonAnlageException(Grund.UNERWARTETE_ANTWORT, e);
        }
        if (uri.getRawUserInfo() != null || uri.getPort() != port || !h.equalsIgnoreCase(uri.getHost())) {
            throw new TelefonAnlageException(Grund.UNERWARTETE_ANTWORT);
        }
        return uri;
    }

    private byte[] ausfuehren(TelefonZugang zugang, String methode, String pfad, String body, String soapAction,
                              String schritt) {
        URI uri = zielAdresse(zugang.host(), pfad);
        try {
            HttpResponse<InputStream> erste = http.send(request(uri, methode, body, soapAction, null),
                    HttpResponse.BodyHandlers.ofInputStream());
            HttpResponse<InputStream> antwort = erste;
            if (erste.statusCode() == 401) {
                erste.body().close();
                String challenge = erste.headers().firstValue("WWW-Authenticate").orElse(null);
                if (challenge == null || !challenge.regionMatches(true, 0, "Digest", 0, 6)) {
                    throw new TelefonAnlageException(Grund.ANMELDUNG_FEHLGESCHLAGEN,
                            schritt + ": HTTP 401 ohne Digest-Anmeldung", null);
                }
                String auth = DigestAnmeldung.header(challenge, methode, pfad,
                        zugang.benutzer() == null ? "" : zugang.benutzer(),
                        zugang.passwort() == null ? "" : zugang.passwort());
                antwort = http.send(request(uri, methode, body, soapAction, auth),
                        HttpResponse.BodyHandlers.ofInputStream());
            }
            return auswerten(antwort, schritt);
        } catch (TelefonAnlageException e) {
            throw e;
        } catch (IOException e) {
            throw new TelefonAnlageException(Grund.NICHT_ERREICHBAR, schritt + ": keine Verbindung", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TelefonAnlageException(Grund.NICHT_ERREICHBAR, schritt + ": abgebrochen", e);
        }
    }

    private HttpRequest request(URI uri, String methode, String body, String soapAction, String auth) {
        HttpRequest.Builder b = HttpRequest.newBuilder(uri).timeout(antwortTimeout);
        if (body != null) {
            b.header("Content-Type", "text/xml; charset=\"utf-8\"");
            b.method(methode, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        } else {
            b.method(methode, HttpRequest.BodyPublishers.noBody());
        }
        if (soapAction != null) {
            b.header("SOAPACTION", "\"" + soapAction + "\"");
        }
        if (auth != null) {
            b.header("Authorization", auth);
        }
        return b.build();
    }

    /** @param schritt Aktion bzw. Datei („SetPhonebookEntryUID“, „phonebook.lua laden“) für das Fehler-Detail */
    private static byte[] auswerten(HttpResponse<InputStream> antwort, String schritt) throws IOException {
        try (InputStream in = antwort.body()) {
            int status = antwort.statusCode();
            if (status == 401) {
                throw new TelefonAnlageException(Grund.ANMELDUNG_FEHLGESCHLAGEN, schritt + ": HTTP 401", null);
            }
            if (status == 403) {
                throw new TelefonAnlageException(Grund.KEINE_RECHTE, schritt + ": HTTP 403", null);
            }
            byte[] inhalt = in.readNBytes(MAX_ANTWORT_BYTES + 1);
            if (inhalt.length > MAX_ANTWORT_BYTES) {
                throw new TelefonAnlageException(Grund.UNERWARTETE_ANTWORT, schritt + ": Antwort zu groß", null);
            }
            if (status == 500) {
                // SOAP-Fehler: 606/607 = Aktion für diesen Benutzer nicht erlaubt
                String text = new String(inhalt, StandardCharsets.UTF_8);
                Matcher m = SOAP_FEHLERCODE.matcher(text);
                int code = m.find() ? Integer.parseInt(m.group(1)) : 0;
                String detail = soapFehlerDetail(schritt, code, text);
                if (code == 606 || code == 607) {
                    throw new TelefonAnlageException(Grund.KEINE_RECHTE, detail, null);
                }
                throw new SoapFehler(code, detail);
            }
            if (status < 200 || status >= 300) {
                throw new TelefonAnlageException(Grund.UNERWARTETE_ANTWORT, schritt + ": HTTP " + status, null);
            }
            return inhalt;
        }
    }

    /**
     * „SetPhonebookEntryUID: Fehlercode 402 „Invalid Args““. Die Beschreibung der Box wird auf
     * harmlose Zeichen und {@value #MAX_DETAIL_TEXT} Zeichen gekürzt, bevor sie in Log und Oberfläche geht.
     */
    static String soapFehlerDetail(String schritt, int code, String antwortText) {
        if (code == 0) {
            return schritt + ": HTTP 500 ohne Fehlercode";
        }
        Matcher m = SOAP_FEHLERTEXT.matcher(antwortText);
        String beschreibung = m.find() ? m.group(1).replaceAll("[^A-Za-z0-9 .,:_()-]", "").trim() : "";
        if (beschreibung.length() > MAX_DETAIL_TEXT) {
            beschreibung = beschreibung.substring(0, MAX_DETAIL_TEXT).trim();
        }
        return schritt + ": Fehlercode " + code + (beschreibung.isEmpty() ? "" : " „" + beschreibung + "“");
    }

    private static String xmlEscape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '&' -> sb.append("&amp;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
