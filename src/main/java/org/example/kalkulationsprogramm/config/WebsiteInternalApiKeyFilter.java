package org.example.kalkulationsprogramm.config;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

/**
 * Schlüsselprüfung für die Server-zu-Server-Schnittstelle {@code /api/internal/**}
 * (Anfrage-Funnel, Freigabe-Seite, Analytics-Snapshot der Firmen-Website).
 *
 * <p>Die Website sendet ihr gemeinsames Geheimnis im Header {@value #HEADER}. Ohne
 * diese Prüfung wäre jedes Gerät im Firmennetz oder Tailscale-Netz zugriffsberechtigt.
 * Fail-closed: Ist kein Schlüssel konfiguriert, wird jede Anfrage mit 401 abgewiesen.
 *
 * <p>Ist Cloudflare Access aktiv und der Request wurde vom {@link CloudflareAccessJwtFilter}
 * über einen gültigen JWT authentifiziert, ist kein zusätzlicher Schlüssel nötig.
 *
 * <p>Bewusst keine {@code @Component}-Bean, damit der Servlet-Container den Filter nicht
 * zusätzlich für alle Pfade registriert; er hängt nur in der {@code /api/internal/**}-Kette.
 */
@Slf4j
public class WebsiteInternalApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-ERP-Website-Key";

    private final byte[] erwarteterDigest;
    private final ObjectMapper objectMapper;

    public WebsiteInternalApiKeyFilter(String schluessel, ObjectMapper objectMapper) {
        String bereinigt = schluessel == null ? "" : schluessel.strip();
        this.erwarteterDigest = bereinigt.isEmpty() ? null : sha256(bereinigt);
        this.objectMapper = objectMapper;
        if (erwarteterDigest == null) {
            log.warn("website.internal-api.key ist nicht gesetzt: /api/internal/** antwortet mit 401, "
                    + "bis ein gemeinsamer Schlüssel (WEBSITE_INTERNAL_API_KEY) konfiguriert ist. "
                    + "Nur relevant, wenn die Firmen-Website angebunden ist.");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (Boolean.TRUE.equals(request.getAttribute(CloudflareAccessJwtFilter.VERIFIED_ATTRIBUTE))
                || schluesselStimmt(request.getHeader(HEADER))) {
            chain.doFilter(request, response);
            return;
        }
        // Bewusst ohne URI: In /api/internal/freigabe/{uuid} steckt die Freigabe-UUID.
        log.warn("{}-Zugriff auf /api/internal/** ohne gültigen Website-Schlüssel abgewiesen", request.getMethod());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        objectMapper.writeValue(response.getWriter(),
                Map.of("success", false, "message", "Nicht autorisiert."));
    }

    private boolean schluesselStimmt(String gesendet) {
        if (erwarteterDigest == null || gesendet == null || gesendet.isBlank()) {
            return false;
        }
        // Digests haben feste Länge, so verrät auch die Länge des Schlüssels nichts.
        return MessageDigest.isEqual(erwarteterDigest, sha256(gesendet.strip()));
    }

    private static byte[] sha256(String wert) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(wert.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 nicht verfügbar", e);
        }
    }
}
