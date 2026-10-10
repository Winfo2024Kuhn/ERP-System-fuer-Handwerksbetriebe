package org.example.kalkulationsprogramm.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Zusätzliche Einschränkung des öffentlichen Gateways, vor jeder Security-Filterkette. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public final class PublicMobileIngressFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-ERP-Public-Mobile";
    /** Request-Attribut: Die Netzgrenze hat die Anfrage als „von außen“ erkannt (z. B. über Tailscale Funnel). */
    static final String VON_AUSSEN = PublicMobileIngressFilter.class.getName() + ".vonAussen";

    /** Öffentlicher Handy-Zugang: über das Gateway (Marker) oder von der Netzgrenze als extern erkannt. */
    public static boolean isPublic(HttpServletRequest request) {
        // Ein fremd gesetzter Marker kann Rechte ausschließlich einschränken.
        return request.getHeader(HEADER) != null || Boolean.TRUE.equals(request.getAttribute(VON_AUSSEN));
    }

    /** Von außen erreichbar: die Seiten der Handy-App (nur lesen) und die freigegebenen mobilen API-Aufrufe. */
    static boolean vonAussenErlaubt(String method, String path) {
        boolean read = "GET".equals(method) || "HEAD".equals(method);
        boolean mobilePage = read && (path.equals("/zeiterfassung") || path.startsWith("/zeiterfassung/"));
        return mobilePage || MobileApiPolicy.allows(method, path);
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String marker = request.getHeader(HEADER);
        if (marker != null) {
            response.setHeader(HEADER, "1");
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if (!"1".equals(marker) || !vonAussenErlaubt(request.getMethod(), path)) {
                response.setStatus(404);
                response.setHeader("Cache-Control", "no-store");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
