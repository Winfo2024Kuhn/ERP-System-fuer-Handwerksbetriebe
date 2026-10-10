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
    private static ZeiterfassungSecurityFilter enabledFilter() {
        var filter = new ZeiterfassungSecurityFilter();
        ReflectionTestUtils.setField(filter, "securityEnabled", true);
        return filter;
    }
}
