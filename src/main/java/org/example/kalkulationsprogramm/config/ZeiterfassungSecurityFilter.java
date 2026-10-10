package org.example.kalkulationsprogramm.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/** Zusätzliche Netzgrenze. Identität und Rechte prüft immer die Spring-Security-Kette.
 * Weiterleitungs-Header gelten ausschließlich von konfigurierten vertrauenswürdigen Proxys.
 *
 * <p>Läuft <strong>vor</strong> Spring Security (Order -100): Sonst beantwortet der Login-Filter
 * {@code POST /api/auth/login} selbst, und die Netzgrenze käme für die Anmeldung nie zum Zug.</p>
 */
@Component
@Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 30)
public class ZeiterfassungSecurityFilter implements Filter {

    @Value("${zeiterfassung.security.enabled:true}")
    private boolean securityEnabled;

    @Value("${zeiterfassung.security.trusted-proxies:}")
    private String trustedProxies = "";

    // Erlaubte Pfade für externe Zugriffe
    private static final List<String> ALLOWED_PATHS = List.of(
            "/zeiterfassung",
            "/api/mitarbeiter/by-token",
            "/api/projekte",
            "/api/produktkategorien",
            "/api/arbeitsgaenge",
            "/api/kunden",
            "/api/lieferanten",
            "/api/zeiterfassung",
            "/api/urlaub",
            "/api/anfragen",
            "/api/dokumente",
            "/api/images",
            "/api/kalender/mobile",
            "/api/push",
            "/api/abwesenheit",
            "/api/spracheingabe",
            "/api/reklamationen",
            "/api/buchhaltung/mobile",
            "/api/zeitverwaltung/feiertage/zwischen");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!securityEnabled) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();
        ClientIpResolver resolver = resolver();
        String clientIp = resolver.resolve(httpRequest);

        // Lokale IPs passieren die Netzgrenze; Spring Security prüft weiterhin den Zugriff.
        // Fail-closed: Kommt eine weitergeleitete Anfrage von einem nicht freigegebenen Proxy
        // (z. B. cloudflared auf localhost), ist der Absender unbekannt und gilt als extern.
        if (isLocalIp(clientIp) && !resolver.weitergeleitetVonUnbekanntemProxy(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        // Externe IPs: nur erlaubte Pfade
        if (isAllowedPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Alles andere für externe IPs blockieren
        httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
        httpResponse.setContentType("application/json");
        httpResponse.getWriter().write("{\"error\":\"Zugriff verweigert\"}");
    }

    private static final List<org.springframework.security.web.util.matcher.IpAddressMatcher> LOKALE_NETZE =
            java.util.stream.Stream.of("127.0.0.0/8", "10.0.0.0/8", "192.168.0.0/16", "172.16.0.0/12", "100.64.0.0/10", "::1/128")
                    .map(org.springframework.security.web.util.matcher.IpAddressMatcher::new).toList();

    private volatile ClientIpResolver resolver;
    private volatile String resolverFuer;

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

    private boolean isAllowedPath(String path) {
        if (path == null)
            return false;
        for (String allowed : ALLOWED_PATHS) {
            if ((path.equals(allowed) || path.startsWith(allowed + "/"))) {
                return true;
            }
        }
        return false;
    }
}
