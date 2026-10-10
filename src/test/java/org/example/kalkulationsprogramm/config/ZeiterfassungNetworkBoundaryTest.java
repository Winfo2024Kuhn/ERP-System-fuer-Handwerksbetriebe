package org.example.kalkulationsprogramm.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;

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
        enabledFilter().doFilter(request, response, (req, res) -> { throw new AssertionError("Funnel als lokal behandelt"); });
        assertThat(response.getStatus()).isEqualTo(403);
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
    /** So kommt eine Anfrage über Tailscale Funnel an: localhost, echter Absender in X-Forwarded-For. */
    private static MockHttpServletRequest ausDemInternet(String method, String path) {
        var request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.8");
        return request;
    }
    private static ZeiterfassungSecurityFilter enabledFilter() {
        var filter = new ZeiterfassungSecurityFilter();
        ReflectionTestUtils.setField(filter, "securityEnabled", true);
        return filter;
    }
}
