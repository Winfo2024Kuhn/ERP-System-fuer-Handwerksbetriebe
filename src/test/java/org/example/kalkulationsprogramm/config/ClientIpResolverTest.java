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
    @Test void absenderOhneGepruefteKetteGiltAlsUnbekannt() {
        // Nicht freigegebener Proxy im LAN meldet einen Absender.
        var fremderProxy = new MockHttpServletRequest();
        fremderProxy.setRemoteAddr("192.168.1.50");
        fremderProxy.addHeader("X-Forwarded-For", "203.0.113.8");
        assertThat(new ClientIpResolver("").ermittle(fremderProxy).unbekannt()).isTrue();

        // Tunnel auf demselben Rechner, der den Absender nur in CF-Connecting-IP meldet.
        var nurCloudflareHeader = new MockHttpServletRequest();
        nurCloudflareHeader.setRemoteAddr("127.0.0.1");
        nurCloudflareHeader.addHeader("CF-Connecting-IP", "203.0.113.8");
        assertThat(new ClientIpResolver("").ermittle(nurCloudflareHeader).unbekannt()).isTrue();

        var unbrauchbareKette = new MockHttpServletRequest();
        unbrauchbareKette.setRemoteAddr("127.0.0.1");
        unbrauchbareKette.addHeader("X-Forwarded-For", "attacker.example");
        assertThat(new ClientIpResolver("").ermittle(unbrauchbareKette).unbekannt()).isTrue();

        var direkt = new MockHttpServletRequest();
        direkt.setRemoteAddr("127.0.0.1");
        assertThat(new ClientIpResolver("").ermittle(direkt).unbekannt()).isFalse();
    }
    @Test void tailscaleAufDemselbenRechnerMeldetDenEchtenAbsenderOhneKonfiguration() {
        // tailscale serve / Funnel verbinden sich über localhost und setzen X-Forwarded-For selbst.
        var ausDemTailnet = new MockHttpServletRequest();
        ausDemTailnet.setRemoteAddr("127.0.0.1");
        ausDemTailnet.addHeader("X-Forwarded-For", "100.101.102.103");
        assertThat(new ClientIpResolver("").resolve(ausDemTailnet)).isEqualTo("100.101.102.103");
        assertThat(new ClientIpResolver("").ermittle(ausDemTailnet).unbekannt()).isFalse();

        var ausDemInternet = new MockHttpServletRequest();
        ausDemInternet.setRemoteAddr("0:0:0:0:0:0:0:1");
        ausDemInternet.addHeader("X-Forwarded-For", "203.0.113.8");
        assertThat(new ClientIpResolver("").resolve(ausDemInternet)).isEqualTo("203.0.113.8");

        var tailnetIpv6 = new MockHttpServletRequest();
        tailnetIpv6.setRemoteAddr("127.0.0.1");
        tailnetIpv6.addHeader("X-Forwarded-For", "fd7a:115c:a1e0::5");
        assertThat(new ClientIpResolver("").resolve(tailnetIpv6)).isEqualTo("fd7a:115c:a1e0:0:0:0:0:5");
    }
    @Test void durchgereichteFalscheKetteVerraetSichDurchAbweichendeEinzeladresse() {
        // Lokaler Proxy reicht das X-Forwarded-For des Angreifers durch und meldet den echten Absender nur in X-Real-IP.
        for (String header : new String[]{"X-Real-IP", "CF-Connecting-IP", "True-Client-IP"}) {
            var request = new MockHttpServletRequest();
            request.setRemoteAddr("127.0.0.1");
            request.addHeader("X-Forwarded-For", "192.168.1.5");
            request.addHeader(header, "203.0.113.8");
            var absender = new ClientIpResolver("").ermittle(request);
            assertThat(absender.unbekannt()).as(header).isTrue();
            // Auch die Code-Sperre zählt dann den direkten Partner, nicht die gefälschte Adresse.
            assertThat(absender.adresse()).as(header).isEqualTo("127.0.0.1");
        }
        var forwarded = new MockHttpServletRequest();
        forwarded.setRemoteAddr("127.0.0.1");
        forwarded.addHeader("X-Forwarded-For", "192.168.1.5");
        forwarded.addHeader("Forwarded", "for=203.0.113.8");
        assertThat(new ClientIpResolver("").ermittle(forwarded).unbekannt()).isTrue();
    }
    @Test void passendeEinzeladresseBestaetigtDieKette() {
        // cloudflared: Die Cloudflare-Edge setzt CF-Connecting-IP und hängt denselben Absender an X-Forwarded-For.
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "10.0.0.1, 203.0.113.8");
        request.addHeader("CF-Connecting-IP", "203.0.113.8");
        var absender = new ClientIpResolver("").ermittle(request);
        assertThat(absender.unbekannt()).isFalse();
        assertThat(absender.adresse()).isEqualTo("203.0.113.8");
    }
    @Test void mehrereXForwardedForZeilenWerdenGemeinsamGelesen() {
        // HAProxy u. a. hängen eine eigene Zeile an, statt die vorhandene zu ergänzen.
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.8");
        assertThat(new ClientIpResolver("").resolve(request)).isEqualTo("203.0.113.8");
    }
    @Test void ipv6LoopbackAlsVerbindungspartnerAusDemTailnet() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("0:0:0:0:0:0:0:1");
        request.addHeader("X-Forwarded-For", "100.101.102.103");
        var absender = new ClientIpResolver("").ermittle(request);
        assertThat(absender.adresse()).isEqualTo("100.101.102.103");
        assertThat(absender.unbekannt()).isFalse();
    }
    @Test void leereGliederMachenDieKetteUngueltig() {
        // Glieder links der ersten nicht vertrauten Adresse stammen vom Client und werden nie gelesen.
        for (String kette : new String[]{"10.0.0.1,,", "10.0.0.1, "}) {
            var request = new MockHttpServletRequest();
            request.setRemoteAddr("127.0.0.1");
            request.addHeader("X-Forwarded-For", kette);
            var absender = new ClientIpResolver("").ermittle(request);
            assertThat(absender.unbekannt()).as(kette).isTrue();
            assertThat(absender.adresse()).as(kette).isEqualTo("127.0.0.1");
        }
    }
}
