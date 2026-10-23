package org.example.kalkulationsprogramm.config;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

import java.util.Locale;

import org.springframework.http.HttpHeaders;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Schutz vor Login-CSRF: Anmelden und Abmelden gehen ohne CSRF-Token nur von der eigenen
 * Oberfläche. Sonst könnte eine fremde Webseite per Formular den Browser eines Mitarbeiters
 * mit einem Konto des Angreifers anmelden. Alles, was hier nicht passt, prüft der normale
 * CSRF-Schutz und lehnt es ohne Token ab.
 *
 * <p>Eigene Oberfläche heißt: Der Browser schickt keinen {@code Origin} (Programme wie curl,
 * sehr alte Browser), oder Name und Port im {@code Origin} stimmen mit dem Host-Header überein.
 * Das Schema zählt bewusst nicht: Hinter {@code tailscale serve}, Funnel, nginx oder cloudflared
 * meldet der Browser {@code https://erp…}, beim ERP kommt die Anfrage aber als {@code http} an
 * ({@code server.forward-headers-strategy=none}, siehe {@link ClientIpResolver}). Aus demselben
 * Grund gelten 80 und 443 als „kein Port“. Der Proxy muss den Host-Header durchreichen; das tun
 * die genannten von sich aus, nginx mit {@code proxy_set_header Host $http_host}.</p>
 *
 * <p>localhost zu localhost passt auch bei anderem Port: Der Vite-Entwicklungsserver
 * ({@code :5173}) reicht die Anmeldung mit {@code changeOrigin} an {@code :8080} weiter. Eine
 * Seite auf demselben Rechner ist ohnehin keine fremde Webseite.</p>
 */
final class AnmeldungVonEigenerSeite implements RequestMatcher {

    private final RequestMatcher anmeldePfade = new OrRequestMatcher(
            pathPattern("/api/auth/login"), pathPattern("/api/auth/logout"));

    @Override
    public boolean matches(HttpServletRequest request) {
        return anmeldePfade.matches(request) && vonEigenerSeite(request);
    }

    static boolean vonEigenerSeite(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null) {
            return true;
        }
        Herkunft herkunft = Herkunft.aus(origin.trim());
        if (herkunft == null) {
            return false;
        }
        String serverName = ErlaubteAdressen.normalisiere(request.getServerName());
        if (istLoopback(herkunft.name()) && istLoopback(serverName)) {
            return true;
        }
        return herkunft.name().equals(serverName) && ohneStandardPort(herkunft.port()) == ohneStandardPort(request.getServerPort());
    }

    /** Fehlender Port, 80 und 443 sind gleichwertig – das Schema sieht das ERP hinter dem Proxy nicht. */
    private static int ohneStandardPort(int port) {
        return port == 80 || port == 443 ? -1 : port;
    }

    private static boolean istLoopback(String name) {
        return name.equals("localhost") || name.endsWith(".localhost") || name.equals("::1")
                || (name.startsWith("127.") && ErlaubteAdressen.istIpAdresse(name));
    }

    /**
     * {@code Origin: schema://name[:port]} – nie mit Pfad oder Zugangsdaten. {@code null}
     * (Sandbox, Datei, Umleitung) und alles andere gilt als fremd. Bewusst ohne
     * {@code java.net.URI}: Die kennt weder Umlaute noch Unterstriche im Namen.
     */
    record Herkunft(String name, int port) {

        static Herkunft aus(String origin) {
            int trenner = origin.indexOf("://");
            if (trenner < 0) {
                return null;
            }
            String schema = origin.substring(0, trenner).toLowerCase(Locale.ROOT);
            String rest = origin.substring(trenner + 3);
            if (!(schema.equals("http") || schema.equals("https")) || rest.isEmpty()
                    || rest.chars().anyMatch(c -> "/?#@\\ ".indexOf(c) >= 0)) {
                return null;
            }
            String host = ErlaubteAdressen.ohnePort(rest);
            String portTeil = rest.substring(host.length());
            int port = -1;
            if (!portTeil.isEmpty()) {
                String ziffern = portTeil.substring(1);
                if (portTeil.charAt(0) != ':' || ziffern.isEmpty() || ziffern.length() > 5
                        || !ziffern.chars().allMatch(c -> c >= '0' && c <= '9')) {
                    return null;
                }
                port = Integer.parseInt(ziffern);
            }
            String name = ErlaubteAdressen.normalisiere(host);
            return name.isEmpty() ? null : new Herkunft(name, port);
        }
    }
}
