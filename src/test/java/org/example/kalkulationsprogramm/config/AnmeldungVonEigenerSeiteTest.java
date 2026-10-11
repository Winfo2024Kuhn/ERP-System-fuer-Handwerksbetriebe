package org.example.kalkulationsprogramm.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** Wann Anmelden und Abmelden ohne CSRF-Token durchgehen. Alle Adressen sind Dummy-Werte. */
class AnmeldungVonEigenerSeiteTest {

    private final AnmeldungVonEigenerSeite matcher = new AnmeldungVonEigenerSeite();

    @Test
    void eigeneOberflaecheHinterTlsProxy() {
        // tailscale serve: Browser meldet https ohne Port, beim ERP kommt http auf Port 80 an.
        assertThat(AnmeldungVonEigenerSeite.vonEigenerSeite(anfrage("erp.example.test", 80, "https://erp.example.test"))).isTrue();
        assertThat(AnmeldungVonEigenerSeite.vonEigenerSeite(anfrage("ERP.example.test", 80, "https://erp.EXAMPLE.test"))).isTrue();
        assertThat(AnmeldungVonEigenerSeite.vonEigenerSeite(anfrage("erp.example.test", 443, "http://erp.example.test"))).isTrue();
    }

    @Test
    void eigeneOberflaecheDirektMitPort() {
        assertThat(AnmeldungVonEigenerSeite.vonEigenerSeite(anfrage("192.168.178.20", 8080, "http://192.168.178.20:8080"))).isTrue();
        assertThat(AnmeldungVonEigenerSeite.vonEigenerSeite(anfrage("[fd7a:115c:a1e0::1]", 8080, "http://[fd7a:115c:a1e0::1]:8080"))).isTrue();
    }

    @Test
    void namenMitUnterstrichUndUmlaut() {
        // java.net.URI kennt beides nicht; der Browser schickt Umlaute als xn--…
        assertThat(AnmeldungVonEigenerSeite.vonEigenerSeite(anfrage("erp_pc", 8080, "http://erp_pc:8080"))).isTrue();
        String punycode = java.net.IDN.toASCII("erp.schlosserei-müller.example");
        assertThat(AnmeldungVonEigenerSeite.vonEigenerSeite(anfrage(punycode, 80, "https://" + punycode))).isTrue();
    }

    @Test
    void ohneOriginWieBisher() {
        // curl, Skripte und sehr alte Browser schicken keinen Origin.
        assertThat(AnmeldungVonEigenerSeite.vonEigenerSeite(anfrage("erp.example.test", 80, null))).isTrue();
    }

    @Test
    void entwicklungsserverAufDemselbenRechner() {
        // Vite (changeOrigin): Browser auf :5173, beim ERP kommt localhost:8080 an.
        assertThat(AnmeldungVonEigenerSeite.vonEigenerSeite(anfrage("localhost", 8080, "http://localhost:5173"))).isTrue();
        assertThat(AnmeldungVonEigenerSeite.vonEigenerSeite(anfrage("localhost", 8080, "http://127.0.0.1:5173"))).isTrue();
        assertThat(AnmeldungVonEigenerSeite.vonEigenerSeite(anfrage("[::1]", 8080, "http://[::1]:5173"))).isTrue();
        assertThat(AnmeldungVonEigenerSeite.vonEigenerSeite(anfrage("localhost", 8080, "http://erp.localhost:5173"))).isTrue();
    }

    @Test
    void fremdeSeitenSindNichtDieEigeneOberflaeche() {
        String[] fremd = {"https://angreifer.example", "null", "https://erp.example.test:8443", "http://erp.example.test:8080",
                "https://erp.example.test.angreifer.example", "https://sub.erp.example.test", "ftp://erp.example.test",
                "file://", "erp.example.test", "%%%", "http://localhost:5173", "https://localhost.angreifer.example",
                "https://max@erp.example.test", "https://erp.example.test/", "https://erp.example.test:", "https://erp.example.test:x80",
                "https://erp.example.test:123456", "https://", "http://[::1", "https://erp.example.test 1", "http://[::1]x", "http://:8080"};
        for (String origin : fremd) {
            assertThat(AnmeldungVonEigenerSeite.vonEigenerSeite(anfrage("erp.example.test", 80, origin))).as(origin).isFalse();
        }
        // Eine Seite im LAN ist nicht das ERP auf localhost.
        assertThat(AnmeldungVonEigenerSeite.vonEigenerSeite(anfrage("localhost", 8080, "http://192.168.178.50:8080"))).isFalse();
    }

    @Test
    void ausnahmeGiltNurFuerAnmeldenUndAbmelden() {
        assertThat(matcher.matches(anfrage("/api/auth/login", "erp.example.test", 80, "https://erp.example.test"))).isTrue();
        assertThat(matcher.matches(anfrage("/api/auth/logout", "erp.example.test", 80, null))).isTrue();
        assertThat(matcher.matches(anfrage("/api/auth/login", "erp.example.test", 80, "https://angreifer.example"))).isFalse();
        assertThat(matcher.matches(anfrage("/api/auth/register", "erp.example.test", 80, "https://erp.example.test"))).isFalse();
        assertThat(matcher.matches(anfrage("/api/kunden", "erp.example.test", 80, null))).isFalse();
    }

    private static MockHttpServletRequest anfrage(String serverName, int serverPort, String origin) {
        return anfrage("/api/auth/login", serverName, serverPort, origin);
    }

    private static MockHttpServletRequest anfrage(String pfad, String serverName, int serverPort, String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", pfad);
        request.setServletPath(pfad);
        request.setScheme("http");
        request.setServerName(serverName);
        request.setServerPort(serverPort);
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        return request;
    }
}
