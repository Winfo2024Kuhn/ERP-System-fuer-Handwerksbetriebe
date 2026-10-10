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

    public static boolean isPublic(HttpServletRequest request) {
        // Ein fremd gesetzter Marker kann Rechte ausschließlich einschränken.
        return request.getHeader(HEADER) != null;
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isPublic(request)) {
            response.setHeader(HEADER, "1");
            String path = request.getRequestURI().substring(request.getContextPath().length());
            boolean read = "GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod());
            boolean mobilePage = read && (path.equals("/zeiterfassung") || path.startsWith("/zeiterfassung/"));
            if (!"1".equals(request.getHeader(HEADER)) || (!mobilePage && !MobileApiPolicy.allows(request.getMethod(), path))) {
                response.setStatus(404);
                response.setHeader("Cache-Control", "no-store");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
