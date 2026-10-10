package org.example.kalkulationsprogramm.config;

import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.example.kalkulationsprogramm.service.TailscaleFunnelFreigabe;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ZeiterfassungNetworkBoundaryTest {
    @Test void alleMobilenBereicheErreichenAuchVonAussenDieAuthentifizierung() throws Exception {
        for (String path : new String[]{"/api/buchhaltung/mobile/me/permissions", "/api/reklamationen/12", "/api/zeitverwaltung/feiertage/zwischen"}) {
            var filter = enabledFilter();
            var request = new MockHttpServletRequest("GET", path); request.setRemoteAddr("203.0.113.8");
            var reachedSecurity = new AtomicBoolean();
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> reachedSecurity.set(true));
            assertThat(reachedSecurity).as(path).isTrue();
        }
    }
    @Test void gefaelschteLokaleIpOeffnetKeinenVerwaltungsZugang() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/frontend-users");
        request.setRemoteAddr("203.0.113.8");
        request.addHeader("CF-Connecting-IP", "127.0.0.1");
        request.addHeader("X-Forwarded-For", "127.0.0.1");
        var response = new MockHttpServletResponse(); var passed = new AtomicBoolean();
        enabledFilter().doFilter(request, response, (req,res) -> passed.set(true));
        assertThat(response.getStatus()).isEqualTo(403); assertThat(passed).isFalse();
    }
    @Test void oeffentliche100erAdressenSindKeinTailscaleUndPfadPraefixIstKeinWildcard() throws Exception {
        for (String path : new String[]{"/api/frontend-users", "/api/zeiterfassung-admin"}) {
            var request = new MockHttpServletRequest("GET", path); request.setRemoteAddr("100.1.2.3");
            var response = new MockHttpServletResponse();
            enabledFilter().doFilter(request, response, (req,res) -> { throw new AssertionError("Netzgrenze umgangen"); });
            assertThat(response.getStatus()).isEqualTo(403);
        }
    }
    @Test void tunnelAufLocalhostGiltAlsExtern() throws Exception {
        // cloudflared verbindet sich auf localhost:8080 und meldet den echten Absender per Header.
        for (String header : new String[]{"CF-Connecting-IP", "X-Forwarded-For", "Forwarded", "X-Real-IP", "True-Client-IP"}) {
            var request = new MockHttpServletRequest("GET", "/api/frontend-users");
            request.setRemoteAddr("127.0.0.1");
            request.addHeader(header, "203.0.113.8");
            var response = new MockHttpServletResponse();
            enabledFilter().doFilter(request, response, (req, res) -> { throw new AssertionError("Tunnel als lokal behandelt: " + header); });
            assertThat(response.getStatus()).as(header).isEqualTo(403);
        }
    }
    @Test void tunnelErreichtMobilePfadeWeiterhin() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/zeiterfassung/projekte");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("CF-Connecting-IP", "203.0.113.8");
        var passed = new AtomicBoolean();
        enabledFilter().doFilter(request, new MockHttpServletResponse(), (req, res) -> passed.set(true));
        assertThat(passed).isTrue();
    }
    @Test void lokaleAnfrageOhneWeiterleitungBleibtLokal() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/frontend-users");
        request.setRemoteAddr("127.0.0.1");
        var passed = new AtomicBoolean();
        enabledFilter().doFilter(request, new MockHttpServletResponse(), (req, res) -> passed.set(true));
        assertThat(passed).isTrue();
    }
    @Test void freigegebenerProxyDarfWeiterleiten() throws Exception {
        var filter = enabledFilter();
        ReflectionTestUtils.setField(filter, "trustedProxies", "172.30.51.2/32");
        var lokal = new MockHttpServletRequest("GET", "/api/frontend-users");
        lokal.setRemoteAddr("172.30.51.2");
        lokal.addHeader("X-Forwarded-For", "192.168.1.20");
        var passed = new AtomicBoolean();
        filter.doFilter(lokal, new MockHttpServletResponse(), (req, res) -> passed.set(true));
        assertThat(passed).as("LAN-Client hinter freigegebenem Proxy").isTrue();

        var extern = new MockHttpServletRequest("GET", "/api/frontend-users");
        extern.setRemoteAddr("172.30.51.2");
        extern.addHeader("X-Forwarded-For", "203.0.113.8");
        var response = new MockHttpServletResponse();
        filter.doFilter(extern, response, (req, res) -> { throw new AssertionError("Externer Client durchgelassen"); });
        assertThat(response.getStatus()).isEqualTo(403);
    }
    @Test void tailscaleServeAusDemTailnetGiltAlsLokal() throws Exception {
        // Website-Server (Anfrage, Freigabe) und Büro-PCs kommen über tailscale serve auf localhost.
        for (String tailnet : new String[]{"100.101.102.103", "fd7a:115c:a1e0::5"}) {
            var request = new MockHttpServletRequest("POST", "/api/internal/anfrage");
            request.setRemoteAddr("127.0.0.1");
            request.addHeader("X-Forwarded-For", tailnet);
            var passed = new AtomicBoolean();
            enabledFilter().doFilter(request, new MockHttpServletResponse(), (req, res) -> passed.set(true));
            assertThat(passed).as(tailnet).isTrue();
            assertThat(PublicMobileIngressFilter.isPublic(request)).as(tailnet).isFalse();
        }
    }
    @Test void tailscaleFunnelAusDemInternetErreichtNurDieHandyApp() throws Exception {
        String[][] gesperrt = {{"POST", "/api/auth/login"}, {"POST", "/api/internal/anfrage"},
                {"GET", "/api/frontend-users"}, {"DELETE", "/api/projekte/1"}, {"GET", "/api/projekte"}};
        for (String[] aufruf : gesperrt) {
            var response = new MockHttpServletResponse();
            enabledFilter().doFilter(ausDemInternet(aufruf[0], aufruf[1]), response,
                    (req, res) -> { throw new AssertionError("Von außen durchgelassen: " + aufruf[0] + " " + aufruf[1]); });
            assertThat(response.getStatus()).as(aufruf[0] + " " + aufruf[1]).isEqualTo(403);
        }
        for (String[] aufruf : new String[][]{{"GET", "/zeiterfassung/"}, {"GET", "/api/zeiterfassung/projekte"},
                {"POST", "/api/zeiterfassung/start"}}) {
            var request = ausDemInternet(aufruf[0], aufruf[1]);
            var passed = new AtomicBoolean();
            enabledFilter().doFilter(request, new MockHttpServletResponse(), (req, res) -> passed.set(true));
            assertThat(passed).as(aufruf[0] + " " + aufruf[1]).isTrue();
            // Ohne Gateway davor: Desktop-Sitzungen zählen von außen trotzdem nicht.
            assertThat(PublicMobileIngressFilter.isPublic(request)).as(aufruf[1]).isTrue();
        }
    }
    @Test void adresseOhnePfadFuehrtVonAussenZurHandyApp() throws Exception {
        var response = new MockHttpServletResponse();
        enabledFilter().doFilter(ausDemInternet("GET", "/"), response,
                (req, res) -> { throw new AssertionError("Desktop-Startseite von außen ausgeliefert"); });
        assertThat(response.getStatus()).isEqualTo(302);
        // Relativ: Hinter tailscale serve ist die Verbindung zum ERP http, ein absoluter Link führte ins Leere.
        assertThat(response.getHeader("Location")).isEqualTo("/zeiterfassung/");
    }
    @Test void vonAussenGeltenAuchAndereMethodenNurLautRoutenliste() throws Exception {
        String[][] gesperrt = {{"POST", "/"}, {"PUT", "/api/zeiterfassung/projekte/1"}, {"PATCH", "/api/projekte/1"},
                {"HEAD", "/api/frontend-users"}, {"POST", "/zeiterfassung/index.html"}};
        for (String[] aufruf : gesperrt) {
            var response = new MockHttpServletResponse();
            enabledFilter().doFilter(ausDemInternet(aufruf[0], aufruf[1]), response,
                    (req, res) -> { throw new AssertionError("Von außen durchgelassen: " + aufruf[0] + " " + aufruf[1]); });
            assertThat(response.getStatus()).as(aufruf[0] + " " + aufruf[1]).isEqualTo(403);
        }
        for (String[] aufruf : new String[][]{{"HEAD", "/zeiterfassung/"}, {"PATCH", "/api/projekte/1/notizen/2"}}) {
            var passed = new AtomicBoolean();
            enabledFilter().doFilter(ausDemInternet(aufruf[0], aufruf[1]), new MockHttpServletResponse(), (req, res) -> passed.set(true));
            assertThat(passed).as(aufruf[0] + " " + aufruf[1]).isTrue();
        }
    }
    @Test void mehrdeutigePfadeWerdenVonAussenAbgewiesen() throws Exception {
        for (String path : new String[]{"/zeiterfassung/../api/frontend-users", "/zeiterfassung/..;/api/frontend-users",
                "/zeiterfassung/%2e%2e/api/frontend-users", "/zeiterfassung/x%2Fy", "/zeiterfassung/x%5cy",
                "/zeiterfassung/x%00", "/zeiterfassung;jsessionid=1"}) {
            var response = new MockHttpServletResponse();
            enabledFilter().doFilter(ausDemInternet("GET", path), response,
                    (req, res) -> { throw new AssertionError("Mehrdeutiger Pfad durchgelassen: " + path); });
            assertThat(response.getStatus()).as(path).isEqualTo(400);
        }
        assertThat(ZeiterfassungSecurityFilter.mehrdeutigerPfad("/zeiterfassung/assets/index-abc.js")).isFalse();
    }
    @Test void funnelKennzeichenGiltAuchBeiTailnetAdresseAlsAussen() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/frontend-users");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "100.101.102.103");
        request.addHeader("Tailscale-Funnel-Request", "?1");
        var response = new MockHttpServletResponse();
        // Freigegeben, damit hier die Routenliste greift und nicht schon der Schalter.
        filterMit(freigabe(true)).doFilter(request, response, (req, res) -> { throw new AssertionError("Funnel als lokal behandelt"); });
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).doesNotContain("Tailscale-App");
    }
    @Test void funnelOhneFreigabeErreichtAuchDieHandyAppNicht() throws Exception {
        var freigabe = freigabe(false);
        for (String[] aufruf : new String[][]{{"GET", "/zeiterfassung/"}, {"GET", "/api/zeiterfassung/projekte"},
                {"POST", "/api/zeiterfassung/start"}, {"GET", "/"}, {"GET", "/api/frontend-users"}}) {
            var response = new MockHttpServletResponse();
            filterMit(freigabe).doFilter(ueberFunnel(aufruf[0], aufruf[1]), response,
                    (req, res) -> { throw new AssertionError("Funnel ohne Freigabe durchgelassen: " + aufruf[1]); });
            assertThat(response.getStatus()).as(aufruf[1]).isEqualTo(403);
            assertThat(response.getContentAsString()).as(aufruf[1]).contains("Tailscale-App");
        }
        // Die Einstellungen zeigen so, dass am Server ein Funnel läuft, den keiner freigegeben hat.
        assertThat(freigabe.letzteAnfrage()).isNotNull();
        assertThat(freigabe.letzteAbgewiesen()).isTrue();
    }
    @Test void ohneSchalterBleibtDerFunnelZu() throws Exception {
        var response = new MockHttpServletResponse();
        enabledFilter().doFilter(ueberFunnel("GET", "/api/zeiterfassung/projekte"), response,
                (req, res) -> { throw new AssertionError("Funnel ohne Schalter durchgelassen"); });
        assertThat(response.getStatus()).isEqualTo(403);
    }
    @Test void freigegebenerFunnelErreichtNurDieHandyApp() throws Exception {
        var freigabe = freigabe(true);
        for (String[] aufruf : new String[][]{{"GET", "/zeiterfassung/"}, {"GET", "/api/zeiterfassung/projekte"},
                {"POST", "/api/zeiterfassung/start"}}) {
            var request = ueberFunnel(aufruf[0], aufruf[1]);
            var passed = new AtomicBoolean();
            filterMit(freigabe).doFilter(request, new MockHttpServletResponse(), (req, res) -> passed.set(true));
            assertThat(passed).as(aufruf[1]).isTrue();
            assertThat(PublicMobileIngressFilter.isPublic(request)).as(aufruf[1]).isTrue();
        }
        assertThat(freigabe.letzteAbgewiesen()).isFalse();
        for (String[] aufruf : new String[][]{{"POST", "/api/auth/login"}, {"GET", "/api/frontend-users"}, {"GET", "/api/settings"}}) {
            var response = new MockHttpServletResponse();
            filterMit(freigabe).doFilter(ueberFunnel(aufruf[0], aufruf[1]), response,
                    (req, res) -> { throw new AssertionError("Desktop über Funnel erreichbar: " + aufruf[1]); });
            assertThat(response.getStatus()).as(aufruf[1]).isEqualTo(403);
        }
    }
    @Test void browserSiehtBeiGesperrtemFunnelEinenSatzStattJson() throws Exception {
        var request = ueberFunnel("GET", "/zeiterfassung/");
        request.addHeader("Accept", "text/html,application/xhtml+xml");
        var response = new MockHttpServletResponse();
        filterMit(freigabe(false)).doFilter(request, response, (req, res) -> { throw new AssertionError("durchgelassen"); });
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("text/html");
        assertThat(response.getContentAsString()).contains("Tailscale-App").doesNotContain("{\"error\"");
    }
    @Test void tailnetUndTunnelBleibenVomFunnelSchalterUnberuehrt() throws Exception {
        var freigabe = freigabe(false);
        var tailnet = new MockHttpServletRequest("GET", "/api/frontend-users");
        tailnet.setRemoteAddr("127.0.0.1");
        tailnet.addHeader("X-Forwarded-For", "100.101.102.103");
        var passed = new AtomicBoolean();
        filterMit(freigabe).doFilter(tailnet, new MockHttpServletResponse(), (req, res) -> passed.set(true));
        assertThat(passed).as("Handy mit Tailscale-App").isTrue();

        var tunnel = new AtomicBoolean();
        filterMit(freigabe).doFilter(ausDemInternet("GET", "/api/zeiterfassung/projekte"), new MockHttpServletResponse(),
                (req, res) -> tunnel.set(true));
        assertThat(tunnel).as("Gateway/Tunnel ohne Funnel-Kennzeichen").isTrue();
        assertThat(freigabe.letzteAnfrage()).isNull();
    }
    @Test void uploadsVonAussenSindWieAmGatewayBegrenzt() throws Exception {
        String bildPfad = "/api/projekte/1/notizen/2/bilder";
        var zuGross = ausDemInternet("POST", bildPfad, ZeiterfassungSecurityFilter.MAX_UPLOAD_VON_AUSSEN + 1);
        var response = new MockHttpServletResponse();
        enabledFilter().doFilter(zuGross, response, (req, res) -> { throw new AssertionError("Riesen-Upload angenommen"); });
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("25 MB");

        var ohneLaenge = ausDemInternet("POST", bildPfad);
        ohneLaenge.addHeader("Transfer-Encoding", "chunked");
        response = new MockHttpServletResponse();
        enabledFilter().doFilter(ohneLaenge, response, (req, res) -> { throw new AssertionError("Upload ohne Länge angenommen"); });
        assertThat(response.getStatus()).isEqualTo(411);

        var passed = new AtomicBoolean();
        enabledFilter().doFilter(ausDemInternet("POST", bildPfad, ZeiterfassungSecurityFilter.MAX_UPLOAD_VON_AUSSEN),
                new MockHttpServletResponse(), (req, res) -> passed.set(true));
        assertThat(passed).as("Foto bis 25 MB").isTrue();

        // Im Büro gilt die Grenze nicht (große Zeichnungen, Sicherungen).
        var buero = new MockHttpServletRequest("POST", "/api/dokumente") {
            @Override public long getContentLengthLong() { return 500L * 1024 * 1024; }
        };
        buero.setRemoteAddr("192.168.1.20");
        var bueroPassed = new AtomicBoolean();
        enabledFilter().doFilter(buero, new MockHttpServletResponse(), (req, res) -> bueroPassed.set(true));
        assertThat(bueroPassed).isTrue();
    }
    private static MockHttpServletRequest ausDemInternet(String method, String path, long laenge) {
        var request = new MockHttpServletRequest(method, path) {
            @Override public long getContentLengthLong() { return laenge; }
        };
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.8");
        return request;
    }
    /** Anfrage von außen über einen Tunnel oder Proxy auf localhost, echter Absender in X-Forwarded-For. */
    private static MockHttpServletRequest ausDemInternet(String method, String path) {
        var request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.8");
        return request;
    }
    /** So kommt eine Anfrage über Tailscale Funnel an: wie oben, dazu das Kennzeichen von tailscaled. */
    private static MockHttpServletRequest ueberFunnel(String method, String path) {
        var request = ausDemInternet(method, path);
        request.addHeader("Tailscale-Funnel-Request", "?1");
        return request;
    }
    private static TailscaleFunnelFreigabe freigabe(boolean freigegeben) {
        var settings = mock(SystemSettingsService.class);
        when(settings.get(anyString(), anyString())).thenReturn(String.valueOf(freigegeben));
        return new TailscaleFunnelFreigabe(settings);
    }
    private static ZeiterfassungSecurityFilter filterMit(TailscaleFunnelFreigabe freigabe) {
        var beans = new StaticListableBeanFactory();
        beans.addBean("tailscaleFunnelFreigabe", freigabe);
        var filter = enabledFilter();
        ReflectionTestUtils.setField(filter, "funnelFreigabe", beans.getBeanProvider(TailscaleFunnelFreigabe.class));
        return filter;
    }
    private static ZeiterfassungSecurityFilter enabledFilter() {
        var filter = new ZeiterfassungSecurityFilter();
        ReflectionTestUtils.setField(filter, "securityEnabled", true);
        return filter;
    }
}
