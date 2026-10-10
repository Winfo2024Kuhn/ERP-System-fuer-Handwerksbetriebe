package org.example.kalkulationsprogramm.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Zusätzliche Netzgrenze. Identität und Rechte prüft immer die Spring-Security-Kette.
 * Weiterleitungs-Header gelten nur von freigegebenen Proxys (siehe {@link ClientIpResolver}).
 *
 * <p>Von außen – direkt, über Tailscale Funnel, einen Tunnel oder das Gateway – ist nur die
 * Handy-App erreichbar, nach denselben Regeln wie am öffentlichen Gateway. Desktop-Sitzungen
 * zählen dort nicht. So bleibt das ERP auch ohne vorgeschaltetes Gateway geschützt.</p>
 *
 * <p>Läuft <strong>vor</strong> Spring Security (Order -100): Sonst beantwortet der Login-Filter
 * {@code POST /api/auth/login} selbst, und die Netzgrenze käme für die Anmeldung nie zum Zug.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class ZeiterfassungSecurityFilter implements Filter {

    /** Wie {@code client_max_body_size 25m} im Docker-Gateway. */
    static final long MAX_UPLOAD_VON_AUSSEN = 25L * 1024 * 1024;

    /** Setzt tailscaled bei jeder Anfrage über Funnel (und entfernt vom Client gesetzte Werte). */
    private static final String FUNNEL_HEADER = "Tailscale-Funnel-Request";

    /** Private Netze inkl. Tailscale (100.64.0.0/10, IPv6 fd7a:115c:a1e0::/48 in fc00::/7). */
    private static final List<IpAddressMatcher> LOKALE_NETZE = Stream.of(
                    "127.0.0.0/8", "10.0.0.0/8", "192.168.0.0/16", "172.16.0.0/12", "100.64.0.0/10", "::1/128", "fc00::/7")
            .map(IpAddressMatcher::new).toList();

    @Value("${zeiterfassung.security.enabled:true}")
    private boolean securityEnabled;

    @Value("${zeiterfassung.security.trusted-proxies:}")
    private String trustedProxies = "";

    private volatile ClientIpResolver resolver;
    private volatile String resolverFuer;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!securityEnabled) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Lokale IPs passieren die Netzgrenze; Spring Security prüft weiterhin den Zugriff.
        // Fail-closed: Meldet ein Proxy einen Absender, den wir nicht prüfen können, gilt er als extern.
        ClientIpResolver.Absender absender = resolver().ermittle(httpRequest);
        if (isLocalIp(absender.adresse()) && !absender.unbekannt() && httpRequest.getHeader(FUNNEL_HEADER) == null) {
            chain.doFilter(request, response);
            return;
        }

        // Unsere Routenprüfung arbeitet auf der rohen Adresse. Mehrdeutige Schreibweisen
        // (/zeiterfassung/../api/…) weisen wir deshalb selbst ab – wie das Gateway.
        if (mehrdeutigerPfad(httpRequest.getRequestURI())) {
            verweigern(httpResponse, HttpServletResponse.SC_BAD_REQUEST, "Ungültige Adresse");
            return;
        }

        String path = httpRequest.getRequestURI().substring(httpRequest.getContextPath().length());
        String method = httpRequest.getMethod();
        if (PublicMobileIngressFilter.vonAussenErlaubt(method, path)) {
            // Ohne Gateway davor (z. B. Tailscale Funnel) nähme der Server sonst bis zu 15 GB an –
            // noch bevor der Mitarbeiter-Code geprüft ist. Grenze wie am Gateway.
            long laenge = httpRequest.getContentLengthLong();
            if (laenge > MAX_UPLOAD_VON_AUSSEN) {
                verweigern(httpResponse, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                        "Zu viele oder zu große Fotos auf einmal. Erlaubt sind 25 MB pro Upload.");
                return;
            }
            if (laenge < 0 && httpRequest.getHeader("Transfer-Encoding") != null) {
                verweigern(httpResponse, HttpServletResponse.SC_LENGTH_REQUIRED, "Die Größe der Anfrage fehlt.");
                return;
            }
            httpRequest.setAttribute(PublicMobileIngressFilter.VON_AUSSEN, Boolean.TRUE);
            chain.doFilter(request, response);
            return;
        }

        // Wer nur die Adresse eintippt, landet in der Handy-App statt vor einer Fehlermeldung.
        // Relativ, damit hinter tailscale serve (intern http) kein http://-Link entsteht.
        if ("/".equals(path) && ("GET".equals(method) || "HEAD".equals(method))) {
            httpResponse.setStatus(HttpServletResponse.SC_FOUND);
            httpResponse.setHeader("Location", httpRequest.getContextPath() + "/zeiterfassung/");
            return;
        }

        verweigern(httpResponse, HttpServletResponse.SC_FORBIDDEN, "Zugriff verweigert");
    }

    /** Wie {@code $ambiguous_path} im Docker-Gateway: kodierte Trenner und Punkte, Nullbyte, Pfadparameter, „..“. */
    static boolean mehrdeutigerPfad(String uri) {
        String klein = uri.toLowerCase(Locale.ROOT);
        return klein.contains("%2f") || klein.contains("%5c") || klein.contains("%2e") || klein.contains("%00")
                || klein.contains(";") || klein.contains("..");
    }

    private static void verweigern(HttpServletResponse response, int status, String meldung) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"" + meldung + "\"}");
    }

    /** Einmal je Konfiguration gebaut statt pro Anfrage. */
    private ClientIpResolver resolver() {
        String proxies = trustedProxies == null ? "" : trustedProxies;
        ClientIpResolver aktuell = resolver;
        if (aktuell == null || !proxies.equals(resolverFuer)) {
            aktuell = new ClientIpResolver(proxies);
            resolver = aktuell;
            resolverFuer = proxies;
        }
        return aktuell;
    }

    private boolean isLocalIp(String ip) {
        if (ip == null) return false;
        return LOKALE_NETZE.stream().anyMatch(m -> m.matches(ip));
    }
}
