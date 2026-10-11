package org.example.kalkulationsprogramm.config;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.kalkulationsprogramm.service.TailscaleFunnelFreigabe;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

/** Zusätzliche Netzgrenze. Identität und Rechte prüft immer die Spring-Security-Kette.
 * Weiterleitungs-Header gelten nur von freigegebenen Proxys (siehe {@link ClientIpResolver}).
 *
 * <p>Von außen – direkt, über Tailscale Funnel, einen Tunnel oder das Gateway – ist nur die
 * Handy-App erreichbar, nach denselben Regeln wie am öffentlichen Gateway. Desktop-Sitzungen
 * zählen dort nicht. So bleibt das ERP auch ohne vorgeschaltetes Gateway geschützt.
 * Tailscale Funnel zusätzlich nur, wenn er in den Einstellungen freigegeben ist
 * ({@link TailscaleFunnelFreigabe}).</p>
 *
 * <p>Aus dem eigenen Netz nur unter einer Adresse, die zum ERP gehören kann
 * ({@link ErlaubteAdressen}). Sonst könnte eine fremde Webseite per DNS-Rebinding den Browser
 * eines Mitarbeiters als Brücke ins Büronetz nutzen, etwa für die Erst-Registrierung.</p>
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

    /** Eigene Domains zusätzlich zu den immer erlaubten Adressen, siehe {@link ErlaubteAdressen}. */
    @Value("${app.security.allowed-hosts:}")
    private String erlaubteHosts = "";

    @Value("${zeiterfassung.base-url:}")
    private String zeiterfassungBaseUrl = "";

    /** Name dieses Rechners; in Tests ersetzbar, damit das Ergebnis nicht vom Testrechner abhängt. */
    private Supplier<Set<String>> rechnernamen = ErlaubteAdressen::eigeneRechnernamen;

    /** Schalter in den Einstellungen, erst bei Bedarf geholt. Fehlt er (Tests), bleibt der Funnel zu. */
    @Autowired
    private ObjectProvider<TailscaleFunnelFreigabe> funnelFreigabe;

    private volatile ClientIpResolver resolver;
    private volatile String resolverFuer;
    private volatile ErlaubteAdressen adressen;
    private volatile String adressenFuer;

    /** Unlesbare Einträge in app.security.allowed-hosts stehen so schon beim Start im Log. */
    @PostConstruct
    void pruefeErlaubteAdressen() {
        adressen();
    }

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
            // DNS-Rebinding: Der Absender ist ein Büro-PC, der Host-Header aber der Name einer fremden Webseite.
            // Von außen braucht es das nicht – dort ist ohnehin nur die Handy-App erreichbar.
            ErlaubteAdressen erlaubt = adressen();
            if (!erlaubt.erlaubt(httpRequest.getServerName())) {
                erlaubt.meldeAbgewiesen(httpRequest.getServerName());
                abweisenMitHinweis(httpRequest, httpResponse, "Adresse nicht freigegeben", FREMDE_ADRESSE);
                return;
            }
            chain.doFilter(request, response);
            return;
        }

        // Funnel nur, wenn in den Einstellungen freigegeben. Sonst nutzen die Mitarbeiter die
        // Tailscale-App, und eine Funnel-Anfrage heißt: Am Server läuft ein Funnel, den keiner wollte.
        if (httpRequest.getHeader(FUNNEL_HEADER) != null) {
            TailscaleFunnelFreigabe schalter = funnelFreigabe == null ? null : funnelFreigabe.getIfAvailable();
            boolean freigegeben = schalter != null && schalter.istFreigegeben();
            if (schalter != null) {
                schalter.anfrageGesehen(!freigegeben);
            }
            if (!freigegeben) {
                abweisenMitHinweis(httpRequest, httpResponse, "Zeiterfassung", FUNNEL_GESPERRT);
                return;
            }
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

    static final String FUNNEL_GESPERRT =
            "Die Zeiterfassung ist nicht über das Internet freigegeben. Bitte die Tailscale-App einschalten.";

    /** Ohne Adresse aus der Anfrage: Der Host-Header stammt beim DNS-Rebinding vom Angreifer. */
    static final String FREMDE_ADRESSE =
            "Unter dieser Adresse ist das ERP nicht freigegeben. Bitte die gewohnte Adresse benutzen. "
                    + "Gehört die Adresse zum ERP, gibt der Administrator sie frei (Einstellung app.security.allowed-hosts).";

    /** Wer die Adresse im Browser öffnet, sieht einen Satz statt JSON. Nur feste Texte, nichts aus der Anfrage. */
    private static void abweisenMitHinweis(HttpServletRequest request, HttpServletResponse response,
            String titel, String meldung) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        String accept = request.getHeader("Accept");
        if (accept == null || !accept.contains("text/html")) {
            verweigern(response, HttpServletResponse.SC_FORBIDDEN, meldung);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("<!doctype html><html lang=\"de\"><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>" + titel + "</title><p style=\"font-family:sans-serif;margin:2rem\">"
                + meldung + "</p></html>");
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

    /** Wie {@link #resolver()}: neu gebaut, wenn sich die Einstellung ändert. */
    private ErlaubteAdressen adressen() {
        String konfiguration = erlaubteHosts + "|" + zeiterfassungBaseUrl;
        ErlaubteAdressen aktuell = adressen;
        if (aktuell == null || !konfiguration.equals(adressenFuer)) {
            aktuell = new ErlaubteAdressen(erlaubteHosts, zeiterfassungBaseUrl, rechnernamen);
            adressen = aktuell;
            adressenFuer = konfiguration;
        }
        return aktuell;
    }

    private boolean isLocalIp(String ip) {
        if (ip == null) return false;
        return LOKALE_NETZE.stream().anyMatch(m -> m.matches(ip));
    }
}
