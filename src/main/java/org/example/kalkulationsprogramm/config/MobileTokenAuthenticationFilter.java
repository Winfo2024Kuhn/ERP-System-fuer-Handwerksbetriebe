package org.example.kalkulationsprogramm.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authentifiziert mobile Aufrufe vor CSRF und vor jedem Fach-Controller. */
public final class MobileTokenAuthenticationFilter extends OncePerRequestFilter {
    static final String EXPLICIT_TOKEN = MobileTokenAuthenticationFilter.class.getName() + ".explicit";
    private static final Pattern TOKEN = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern TOKEN_PATH = Pattern.compile("/api/(?:mitarbeiter/by-token|zeiterfassung/(?:arbeitsgaenge|aktiv|heute|saldo|buchungen|urlaubsverfall|buchungszeitfenster|langzeitkrankmeldung))/([^/]+)");
    private static final int MAX_JSON_BYTES = 1_048_576;
    private final Function<String, Optional<Mitarbeiter>> lookup;
    private final TokenAttemptLimiter limiter;
    private final ClientIpResolver addresses;
    private final ObjectMapper mapper;

    public MobileTokenAuthenticationFilter(Function<String, Optional<Mitarbeiter>> lookup,
            TokenAttemptLimiter limiter, ClientIpResolver addresses, ObjectMapper mapper) {
        this.lookup = lookup;
        this.limiter = limiter;
        this.addresses = addresses;
        this.mapper = mapper;
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().substring(request.getContextPath().length()).startsWith("/api/");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String header = request.getHeader("X-Auth-Token");
        String query = request.getParameter("token");
        var pathMatch = TOKEN_PATH.matcher(path);
        String pathToken = pathMatch.matches() ? pathMatch.group(1) : null;
        var session = SecurityContextHolder.getContext().getAuthentication();
        boolean desktopSitzung = !PublicMobileIngressFilter.isPublic(request) && session != null && session.isAuthenticated()
                && !(session instanceof AnonymousAuthenticationToken) && !(session.getPrincipal() instanceof MobilePrincipal);
        // Ein expliziter Mobile-Token bleibt auch im selben Browser auf Mobile-Rechte begrenzt.
        if (desktopSitzung && header == null && query == null && pathToken == null) {
            chain.doFilter(request, response);
            return;
        }
        boolean safe = "GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod());
        String token = header != null ? header : pathToken != null ? pathToken : query;
        boolean explicit = token != null;
        if (token == null && safe && request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("ze_token".equals(cookie.getName())) {
                    try { token = URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8); }
                    catch (IllegalArgumentException exception) { token = ""; }
                }
            }
        }
        if (token == null) { reject(response, 401, 0); return; }
        String credential = token;
        boolean consistent = (header == null || header.equals(credential)) && (query == null || query.equals(credential))
                && (pathToken == null || pathToken.equals(credential))
                && (request.getParameterValues("token") == null || request.getParameterValues("token").length == 1);
        java.util.function.Supplier<Optional<Mitarbeiter>> pruefung = () ->
                consistent && TOKEN.matcher(credential).matches() ? lookup.apply(credential) : Optional.empty();
        // Angemeldete Büro-Nutzer raten keine Tokens: Fragt das Büro z. B. den Saldo eines
        // ausgeschiedenen Mitarbeiters ab, darf das nicht die Wartezeit für das ganze Firmennetz auslösen.
        var attempt = desktopSitzung
                ? new TokenAttemptLimiter.Attempt<>(pruefung.get(), 0)
                : limiter.attempt(addresses.resolve(request), pruefung);
        if (attempt.retryAfterSeconds() > 0) { reject(response, 429, attempt.retryAfterSeconds()); return; }
        if (attempt.value().isEmpty()) { reject(response, 401, 0); return; }
        Mitarbeiter employee = attempt.value().get();
        if (!MobileApiPolicy.allows(request.getMethod(), path)) { reject(response, 403, 0); return; }
        String employeeHeader = request.getHeader("X-Mitarbeiter-Id");
        if (request.getHeader("X-User-Profile-Id") != null || (employeeHeader != null && !employee.getId().toString().equals(employeeHeader))) {
            reject(response, 403, 0); return;
        }
        String[] employeeQueries = request.getParameterValues("mitarbeiterId");
        if (employeeQueries != null && (employeeQueries.length != 1 || !employee.getId().toString().equals(employeeQueries[0]))) {
            reject(response, 403, 0); return;
        }
        byte[] body = null;
        if (!safe && istJson(request.getContentType())) {
            body = request.getInputStream().readNBytes(MAX_JSON_BYTES + 1);
            if (body.length > MAX_JSON_BYTES) { reject(response, 413, 0); return; }
            if (body.length > 0) {
                JsonNode json;
                try { json = mapper.readTree(body); }
                catch (com.fasterxml.jackson.core.JsonProcessingException e) { reject(response, 400, 0); return; }
                if (json == null || !json.isObject()) { reject(response, 400, 0); return; }
                if ((json.has("token") && !credential.equals(json.path("token").asText()))
                        || (json.has("mitarbeiterId") && !employee.getId().toString().equals(json.path("mitarbeiterId").asText()))) {
                    reject(response, 403, 0); return;
                }
            }
        }
        request.setAttribute(EXPLICIT_TOKEN, explicit);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(new MobilePrincipal(employee.getId()), null,
                List.of(new SimpleGrantedAuthority("ROLE_MOBILE"))));
        SecurityContextHolder.setContext(context);
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        // Hochgeladene SVG/HTML-Dateien dürfen im PWA-Ursprung keine Skripte ausführen.
        response.setHeader("Content-Security-Policy", "sandbox; default-src 'none'");
        chain.doFilter(new VerifiedRequest(request, credential, employee.getId(), body), response);
    }

    /**
     * Liest Jackson den Body als JSON? Dann prüfen wir ihn auch – unabhängig von Groß-/Kleinschreibung
     * und auch für {@code application/*+json}. Ein unlesbarer Typ gilt vorsichtshalber als JSON.
     */
    static boolean istJson(String contentType) {
        if (contentType == null || contentType.isBlank()) return false;
        try {
            var typ = org.springframework.http.MediaType.parseMediaType(contentType);
            String unter = typ.getSubtype().toLowerCase(java.util.Locale.ROOT);
            return "application".equalsIgnoreCase(typ.getType()) && (unter.equals("json") || unter.endsWith("+json"));
        } catch (org.springframework.http.InvalidMediaTypeException e) {
            return true;
        }
    }

    private void reject(HttpServletResponse response, int status, long retryAfter) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        if (retryAfter > 0) response.setHeader("Retry-After", Long.toString(retryAfter));
        String message = status == 429 ? "Zu viele Anmeldeversuche. Bitte warte kurz."
                : status == 401 ? "Anmeldung erforderlich oder Token ungültig." : "Zugriff verweigert.";
        mapper.writeValue(response.getWriter(), Map.of("error", message, "message", message, "retryAfterSeconds", retryAfter));
    }

    /** Alte Controller bekommen ausschließlich die bereits geprüfte Identität. */
    private static final class VerifiedRequest extends HttpServletRequestWrapper {
        private final Map<String, String[]> parameters;
        private final String employeeId;
        private final byte[] body;
        VerifiedRequest(HttpServletRequest request, String token, Long employeeId, byte[] body) {
            super(request);
            parameters = new LinkedHashMap<>(request.getParameterMap());
            parameters.put("token", new String[]{token});
            this.employeeId = employeeId.toString();
            this.body = body;
        }
        @Override public String getHeader(String name) {
            if ("X-Mitarbeiter-Id".equalsIgnoreCase(name)) return employeeId;
            if ("X-User-Profile-Id".equalsIgnoreCase(name)) return null;
            return super.getHeader(name);
        }
        @Override public Enumeration<String> getHeaders(String name) {
            if ("X-Mitarbeiter-Id".equalsIgnoreCase(name)) return Collections.enumeration(List.of(employeeId));
            if ("X-User-Profile-Id".equalsIgnoreCase(name)) return Collections.emptyEnumeration();
            return super.getHeaders(name);
        }
        @Override public String getParameter(String name) { var values = parameters.get(name); return values == null ? null : values[0]; }
        @Override public String[] getParameterValues(String name) { return parameters.get(name); }
        @Override public Map<String, String[]> getParameterMap() { return Collections.unmodifiableMap(parameters); }
        @Override public Enumeration<String> getParameterNames() { return Collections.enumeration(parameters.keySet()); }
        @Override public ServletInputStream getInputStream() throws IOException {
            if (body == null) return super.getInputStream();
            var input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("Nur synchrone JSON-Anfragen"); }
            };
        }
        @Override public BufferedReader getReader() throws IOException { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
    }
}
