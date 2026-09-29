package org.example.kalkulationsprogramm.service.telefon.fritzbox;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/**
 * HTTP-Digest-Anmeldung (RFC 7616, MD5 mit qop=auth), wie sie die FRITZ!Box
 * für TR-064 verlangt. MD5 ist hier vom Protokoll vorgegeben und dient nur
 * dazu, das Passwort nicht im Klartext über das LAN zu schicken.
 */
final class DigestAnmeldung {

    private static final SecureRandom ZUFALL = new SecureRandom();

    private DigestAnmeldung() {
    }

    /**
     * Baut den Authorization-Header für eine Anfrage.
     *
     * @param challenge Wert des {@code WWW-Authenticate}-Headers der 401-Antwort
     * @param methode   HTTP-Methode, z.B. "POST"
     * @param uri       Pfad + Query der Anfrage
     */
    static String header(String challenge, String methode, String uri, String benutzer, String passwort) {
        return header(challenge, methode, uri, benutzer, passwort, HexFormat.of().formatHex(zufall(8)));
    }

    static String header(String challenge, String methode, String uri, String benutzer, String passwort, String cnonce) {
        Map<String, String> p = parameter(challenge);
        String realm = p.getOrDefault("realm", "");
        String nonce = p.getOrDefault("nonce", "");
        String opaque = p.get("opaque");
        String algorithmus = p.getOrDefault("algorithm", "MD5");
        boolean mitQop = p.containsKey("qop") && p.get("qop").toLowerCase(Locale.ROOT).contains("auth");

        String nc = "00000001";
        String ha1 = md5(benutzer + ":" + realm + ":" + passwort);
        if ("MD5-sess".equalsIgnoreCase(algorithmus)) {
            ha1 = md5(ha1 + ":" + nonce + ":" + cnonce);
        }
        String ha2 = md5(methode + ":" + uri);
        String antwort = mitQop
                ? md5(ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":auth:" + ha2)
                : md5(ha1 + ":" + nonce + ":" + ha2);

        StringBuilder h = new StringBuilder("Digest ");
        h.append("username=\"").append(quote(benutzer)).append("\", ");
        h.append("realm=\"").append(quote(realm)).append("\", ");
        h.append("nonce=\"").append(quote(nonce)).append("\", ");
        h.append("uri=\"").append(quote(uri)).append("\", ");
        h.append("algorithm=").append(algorithmus).append(", ");
        h.append("response=\"").append(antwort).append('"');
        if (mitQop) {
            h.append(", qop=auth, nc=").append(nc).append(", cnonce=\"").append(cnonce).append('"');
        }
        if (opaque != null) {
            h.append(", opaque=\"").append(quote(opaque)).append('"');
        }
        return h.toString();
    }

    /** Zerlegt {@code Digest realm="x", nonce="y", qop="auth"} in Schlüssel/Werte. */
    static Map<String, String> parameter(String challenge) {
        Map<String, String> werte = new HashMap<>();
        if (challenge == null) {
            return werte;
        }
        String s = challenge.trim();
        if (s.regionMatches(true, 0, "Digest", 0, 6)) {
            s = s.substring(6);
        }
        int i = 0;
        while (i < s.length()) {
            while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == ',')) {
                i++;
            }
            int gleich = s.indexOf('=', i);
            if (gleich < 0) {
                break;
            }
            String schluessel = s.substring(i, gleich).trim().toLowerCase(Locale.ROOT);
            i = gleich + 1;
            StringBuilder wert = new StringBuilder();
            if (i < s.length() && s.charAt(i) == '"') {
                i++;
                while (i < s.length() && s.charAt(i) != '"') {
                    if (s.charAt(i) == '\\' && i + 1 < s.length()) {
                        i++;
                    }
                    wert.append(s.charAt(i));
                    i++;
                }
                i++;
            } else {
                while (i < s.length() && s.charAt(i) != ',') {
                    wert.append(s.charAt(i));
                    i++;
                }
            }
            werte.put(schluessel, wert.toString().trim());
        }
        return werte;
    }

    static String md5(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 nicht verfügbar", e);
        }
    }

    private static byte[] zufall(int laenge) {
        byte[] b = new byte[laenge];
        ZUFALL.nextBytes(b);
        return b;
    }

    private static String quote(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
