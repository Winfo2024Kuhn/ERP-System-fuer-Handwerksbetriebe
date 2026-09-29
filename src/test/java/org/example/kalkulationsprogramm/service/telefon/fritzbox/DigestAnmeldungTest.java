package org.example.kalkulationsprogramm.service.telefon.fritzbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DigestAnmeldungTest {

    @Test
    @DisplayName("Antwort entspricht dem Beispiel aus RFC 2617")
    void rfc2617Beispiel() {
        String challenge = "Digest realm=\"testrealm@host.com\", qop=\"auth,auth-int\", "
                + "nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\", opaque=\"5ccc069c403ebaf9f0171e9517f40e41\"";
        String header = DigestAnmeldung.header(challenge, "GET", "/dir/index.html", "Mufasa", "Circle Of Life", "0a4f113b");

        assertThat(header).startsWith("Digest ")
                .contains("response=\"6629fae49393a05397450978507c4ef1\"")
                .contains("qop=auth, nc=00000001, cnonce=\"0a4f113b\"")
                .contains("opaque=\"5ccc069c403ebaf9f0171e9517f40e41\"")
                .contains("uri=\"/dir/index.html\"");
    }

    @Test
    @DisplayName("Ohne qop wird die alte RFC-2069-Antwort gebildet")
    void ohneQop() {
        String header = DigestAnmeldung.header("Digest realm=\"r\", nonce=\"n\"", "POST", "/x", "u", "p", "c");
        String erwartet = DigestAnmeldung.md5(DigestAnmeldung.md5("u:r:p") + ":n:" + DigestAnmeldung.md5("POST:/x"));
        assertThat(header).contains("response=\"" + erwartet + "\"").doesNotContain("qop=").doesNotContain("opaque");
    }

    @Test
    @DisplayName("MD5-sess bezieht nonce und cnonce in HA1 ein")
    void md5Sess() {
        String header = DigestAnmeldung.header("Digest realm=\"r\", nonce=\"n\", algorithm=MD5-sess, qop=\"auth\"",
                "GET", "/", "u", "p", "c");
        String ha1 = DigestAnmeldung.md5(DigestAnmeldung.md5("u:r:p") + ":n:c");
        String erwartet = DigestAnmeldung.md5(ha1 + ":n:00000001:c:auth:" + DigestAnmeldung.md5("GET:/"));
        assertThat(header).contains("response=\"" + erwartet + "\"").contains("algorithm=MD5-sess");
    }

    @Test
    @DisplayName("Parameter mit Anführungszeichen, Escapes und ohne Anführungszeichen")
    void parameter() {
        Map<String, String> p = DigestAnmeldung.parameter(
                "Digest realm=\"F!Box \\\"SOAP\\\"\", nonce=ABC, qop=\"auth\",algorithm=MD5");
        assertThat(p).containsEntry("realm", "F!Box \"SOAP\"")
                .containsEntry("nonce", "ABC")
                .containsEntry("qop", "auth")
                .containsEntry("algorithm", "MD5");
        assertThat(DigestAnmeldung.parameter(null)).isEmpty();
        assertThat(DigestAnmeldung.parameter("Digest kaputt")).isEmpty();
    }

    @Test
    @DisplayName("Anführungszeichen im Benutzernamen werden maskiert")
    void maskiertBenutzer() {
        String header = DigestAnmeldung.header("Digest realm=\"r\", nonce=\"n\"", "GET", "/", "a\"b", "p", "c");
        assertThat(header).contains("username=\"a\\\"b\"");
    }
}
