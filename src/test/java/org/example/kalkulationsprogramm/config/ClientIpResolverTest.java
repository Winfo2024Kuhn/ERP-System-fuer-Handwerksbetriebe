package org.example.kalkulationsprogramm.config;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.assertThat;
class ClientIpResolverTest {
    @Test void direkteClientsKoennenKeineWeiterleitungsHeaderFaelschen() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.8");
        request.addHeader("CF-Connecting-IP", "127.0.0.1");
        request.addHeader("X-Forwarded-For", "192.168.1.1");
        assertThat(new ClientIpResolver("").resolve(request)).isEqualTo("203.0.113.8");
    }
    @Test void vertrauteProxyKetteWirdVonRechtsGeprueft() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "192.168.1.1, 203.0.113.8, 10.0.0.2");
        assertThat(new ClientIpResolver("127.0.0.1/32,10.0.0.2/32").resolve(request)).isEqualTo("203.0.113.8");
    }
    @Test void ungueltigeAdressenWerdenNichtAlsHostnamenAufgeloest() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "attacker.example");
        assertThat(new ClientIpResolver("127.0.0.1/32").resolve(request)).isEqualTo("127.0.0.1");
    }
    @Test void hostnamenAusHexZeichenWerdenNichtAufgeloest() {
        // „dead.beef“ besteht nur aus Hex-Zeichen und Punkten – ohne Literal-Prüfung gäbe es eine DNS-Abfrage.
        for (String value : new String[]{"dead.beef", "999.1.1.1", "1.2.3", "abc", "1.2.3.4.5"}) {
            assertThat(ClientIpResolver.numericAddress(value)).as(value).isNull();
        }
        assertThat(ClientIpResolver.numericAddress("203.0.113.8")).isEqualTo("203.0.113.8");
        assertThat(ClientIpResolver.numericAddress("2001:db8::1")).isEqualTo("2001:db8:0:0:0:0:0:1");
    }
    @Test void weiterleitungNurVonFreigegebenemProxyGilt() {
        var tunnel = new MockHttpServletRequest();
        tunnel.setRemoteAddr("127.0.0.1");
        tunnel.addHeader("CF-Connecting-IP", "203.0.113.8");
        assertThat(new ClientIpResolver("").weitergeleitetVonUnbekanntemProxy(tunnel)).isTrue();
        assertThat(new ClientIpResolver("127.0.0.1/32").weitergeleitetVonUnbekanntemProxy(tunnel)).isFalse();

        var direkt = new MockHttpServletRequest();
        direkt.setRemoteAddr("127.0.0.1");
        assertThat(new ClientIpResolver("").weitergeleitetVonUnbekanntemProxy(direkt)).isFalse();
    }
}
