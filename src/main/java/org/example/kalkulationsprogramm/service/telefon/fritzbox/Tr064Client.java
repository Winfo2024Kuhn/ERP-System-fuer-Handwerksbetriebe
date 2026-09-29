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
        byte[] antwort = ausfuehren(zugang, "POST", controlUrl, body, serviceTyp + "#" + aktion);
        Document doc = SicheresXml.parse(antwort);
        List<Element> treffer = SicheresXml.elemente(doc, aktion + "Response");
        if (treffer.isEmpty()) {
            throw new TelefonAnlageException(Grund.UNERWARTETE_ANTWORT);
        }
        return SicheresXml.kinder(treffer.getFirst());
    }

    /**
     * Lädt eine Datei, deren URL (oder Pfad) die FRITZ!Box geliefert hat.
     * Nur Pfad und Query werden übernommen, Host und Port sind immer die eingestellten.
     */
    byte[] laden(TelefonZugang zugang, String urlOderPfad) {
        return ausfuehren(zugang, "GET", pfadUndQuery(urlOderPfad), null, null);
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

    private byte[] ausfuehren(TelefonZugang zugang, String methode, String pfad, String body, String soapAction) {
        URI uri = zielAdresse(zugang.host(), pfad);
        try {
            HttpResponse<InputStream> erste = http.send(request(uri, methode, body, soapAction, null),
                    HttpResponse.BodyHandlers.ofInputStream());
            HttpResponse<InputStream> antwort = erste;
            if (erste.statusCode() == 401) {
                erste.body().close();
                String challenge = erste.headers().firstValue("WWW-Authenticate").orElse(null);
                if (challenge == null || !challenge.regionMatches(true, 0, "Digest", 0, 6)) {
                    throw new TelefonAnlageException(Grund.ANMELDUNG_FEHLGESCHLAGEN);
                }
                String auth = DigestAnmeldung.header(challenge, methode, pfad,
                        zugang.benutzer() == null ? "" : zugang.benutzer(),
                        zugang.passwort() == null ? "" : zugang.passwort());
                antwort = http.send(request(uri, methode, body, soapAction, auth),
                        HttpResponse.BodyHandlers.ofInputStream());
            }
            return auswerten(antwort);
        } catch (TelefonAnlageException e) {
            throw e;
        } catch (IOException e) {
            throw new TelefonAnlageException(Grund.NICHT_ERREICHBAR, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TelefonAnlageException(Grund.NICHT_ERREICHBAR, e);
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

    private static byte[] auswerten(HttpResponse<InputStream> antwort) throws IOException {
        try (InputStream in = antwort.body()) {
            int status = antwort.statusCode();
            if (status == 401) {
                throw new TelefonAnlageException(Grund.ANMELDUNG_FEHLGESCHLAGEN);
            }
            if (status == 403) {
                throw new TelefonAnlageException(Grund.KEINE_RECHTE);
            }
            byte[] inhalt = in.readNBytes(MAX_ANTWORT_BYTES + 1);
            if (inhalt.length > MAX_ANTWORT_BYTES) {
                throw new TelefonAnlageException(Grund.UNERWARTETE_ANTWORT);
            }
            if (status == 500) {
                // SOAP-Fehler: 606/607 = Aktion für diesen Benutzer nicht erlaubt
                String fehler = new String(inhalt, StandardCharsets.UTF_8);
                if (fehler.contains("<errorCode>606</errorCode>") || fehler.contains("<errorCode>607</errorCode>")) {
                    throw new TelefonAnlageException(Grund.KEINE_RECHTE);
                }
                throw new TelefonAnlageException(Grund.UNERWARTETE_ANTWORT);
            }
            if (status < 200 || status >= 300) {
                throw new TelefonAnlageException(Grund.UNERWARTETE_ANTWORT);
            }
            return inhalt;
        }
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
