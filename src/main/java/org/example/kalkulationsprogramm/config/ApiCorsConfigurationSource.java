package org.example.kalkulationsprogramm.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletRequest;

/**
 * CORS für {@code /api/**}: Eine fremde Webseite darf Antworten des ERP im Browser nur lesen
 * (GET/HEAD), wenn ihre Adresse (Origin) in {@code app.cors.allowed-origins} steht. Standard ist
 * leer – keine fremde Webseite. Schreiben und eigene Header bleiben auch eingetragenen Seiten
 * verwehrt. Die eigene Oberfläche (PC und Handy-App) läuft unter derselben
 * Adresse wie das ERP und braucht keinen Eintrag; die Website erreicht das ERP nur von Server
 * zu Server über {@code /api/internal/**}, dort ist CORS ganz aus.
 *
 * <p>Ein nicht eingetragener Origin bekommt keine CORS-Header, der Browser blockt das Lesen;
 * seine Vorab-Anfrage (Preflight) wird mit 403 abgelehnt. Gewöhnliche Anfragen werden bewusst
 * nicht hart abgelehnt: Hinter {@code tailscale serve}, Funnel oder einem anderen TLS-Proxy
 * kommt die Anfrage als {@code http} an, der Browser meldet als Origin aber {@code https} –
 * Spring hielte die eigene Oberfläche für fremd, und jedes Speichern scheiterte. Gegen
 * ungewollte Änderungen schützen dort das CSRF-Token (Desktop) bzw. der geheime Mitarbeiter-Code
 * der Handy-App, den eine fremde Seite nicht kennt.
 */
final class ApiCorsConfigurationSource implements CorsConfigurationSource {

    private static final Logger log = LoggerFactory.getLogger(ApiCorsConfigurationSource.class);

    private final UrlBasedCorsConfigurationSource apiPfade = new UrlBasedCorsConfigurationSource();

    /** @param erlaubteOrigins kommagetrennt, z. B. {@code https://intranet.firma.de}; leer = keine */
    ApiCorsConfigurationSource(String erlaubteOrigins) {
        List<String> origins = erlaubteOrigins(erlaubteOrigins);
        CorsConfiguration erlaubt = new CorsConfiguration();
        erlaubt.setAllowedOrigins(origins);
        // Nur lesen; ohne erlaubte Header scheitert jeder Preflight mit eigenen Headern.
        erlaubt.setAllowedMethods(List.of("GET", "HEAD"));
        erlaubt.setAllowCredentials(true);
        erlaubt.setMaxAge(3600L);
        apiPfade.registerCorsConfiguration("/api/**", erlaubt);
        if (!origins.isEmpty()) {
            log.info("CORS: fremde Webseiten mit Lesezugriff auf /api/**: {}", origins);
        }
    }

    @Override
    public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
        CorsConfiguration erlaubt = apiPfade.getCorsConfiguration(request);
        if (erlaubt == null) {
            return null;
        }
        // Preflight kommt nie von der eigenen Oberfläche: hier entscheidet die Liste (fremd oder schreibend = 403).
        if (CorsUtils.isPreFlightRequest(request)) {
            return erlaubt;
        }
        // Fremd oder schreibend: keine CORS-Header, aber auch kein 403 – sonst könnte die eigene
        // Oberfläche nicht mehr speichern, falls jemand ihre Adresse (oder Vite) einträgt.
        if (erlaubt.checkOrigin(request.getHeader(HttpHeaders.ORIGIN)) == null
                || erlaubt.checkHttpMethod(HttpMethod.valueOf(request.getMethod())) == null) {
            return null;
        }
        return erlaubt;
    }

    /**
     * Liest die kommagetrennte Liste. Erlaubt sind nur genaue Adressen {@code http(s)://host[:port]};
     * {@code *}, Platzhalter, Pfade und Kaputtes werden mit Warnung übergangen – lieber eine
     * Webseite zu wenig als alle.
     */
    static List<String> erlaubteOrigins(String liste) {
        if (liste == null || liste.isBlank()) {
            return List.of();
        }
        List<String> origins = new ArrayList<>();
        for (String eintrag : liste.split(",")) {
            String origin = eintrag.trim();
            if (origin.isEmpty()) {
                continue;
            }
            String normalisiert = normalisieren(origin);
            if (normalisiert == null) {
                log.warn("CORS: '{}' in app.cors.allowed-origins ist keine genaue Adresse "
                        + "(z. B. https://intranet.firma.de, Umlaut-Domains als xn--...) und wird ignoriert.", origin);
            } else if (!origins.contains(normalisiert)) {
                origins.add(normalisiert);
            }
        }
        return List.copyOf(origins);
    }

    /**
     * {@code scheme://host[:port]} in Kleinbuchstaben, Standardport weggelassen (so schickt ihn der
     * Browser), oder {@code null}, wenn es keine genaue Adresse ist.
     */
    static String normalisieren(String origin) {
        if (origin.contains("*")) {
            return null;
        }
        String ohneSchraegstrich = origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin;
        URI uri;
        try {
            uri = new URI(ohneSchraegstrich);
        } catch (URISyntaxException e) {
            return null;
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        boolean nurAdresse = uri.getHost() != null && uri.getRawUserInfo() == null
                && (uri.getRawPath() == null || uri.getRawPath().isEmpty())
                && uri.getRawQuery() == null && uri.getRawFragment() == null;
        if (!("http".equals(scheme) || "https".equals(scheme)) || !nurAdresse) {
            return null;
        }
        int port = uri.getPort();
        boolean standardPort = port == -1 || ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + (standardPort ? "" : ":" + port);
    }
}
