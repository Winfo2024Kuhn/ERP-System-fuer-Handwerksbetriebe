package org.example.kalkulationsprogramm.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Security Filter für externe Zugriffe über Cloudflare Tunnel.
 * Erlaubt nur Zeiterfassungs-relevante Endpoints.
 * Alle anderen Endpoints sind nur aus dem lokalen Netzwerk erreichbar.
 */
@Component
@Order(1)
public class ZeiterfassungSecurityFilter implements Filter {

    @Value("${zeiterfassung.security.enabled:true}")
    private boolean securityEnabled;

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
            "/api/abwesenheit");

    // Lokale IP-Bereiche die immer Zugriff haben
    private static final List<String> LOCAL_IP_PREFIXES = List.of(
            "127.0.0.1",
            "192.168.",
            "10.",
            "100.", // Tailscale VPN
            "172.16.",
            "172.17.",
            "172.18.",
            "172.19.",
            "172.20.",
            "172.21.",
            "172.22.",
            "172.23.",
            "172.24.",
            "172.25.",
            "172.26.",
            "172.27.",
            "172.28.",
            "172.29.",
            "172.30.",
            "172.31.",
            "0:0:0:0:0:0:0:1" // IPv6 localhost
    );

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
        String clientIp = getClientIp(httpRequest);

        // Lokale IPs haben immer vollen Zugriff
        if (isLocalIp(clientIp)) {
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

    private String getClientIp(HttpServletRequest request) {
        // Cloudflare sendet die echte Client-IP im CF-Connecting-IP Header
        String cfIp = request.getHeader("CF-Connecting-IP");
        if (cfIp != null && !cfIp.isEmpty()) {
            return cfIp;
        }

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }

    private boolean isLocalIp(String ip) {
        if (ip == null)
            return false;
        for (String prefix : LOCAL_IP_PREFIXES) {
            if (ip.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAllowedPath(String path) {
        if (path == null)
            return false;
        for (String allowed : ALLOWED_PATHS) {
            if (path.startsWith(allowed)) {
                return true;
            }
        }
        return false;
    }
}
