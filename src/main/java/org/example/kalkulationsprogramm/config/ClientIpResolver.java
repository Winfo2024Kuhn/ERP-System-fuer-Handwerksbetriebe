package org.example.kalkulationsprogramm.config;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

/**
 * Forwarded-Header sind nur hinter freigegebenen Proxys vertrauenswürdig.
 *
 * <p>Proxys auf demselben Rechner gelten immer als freigegeben: {@code tailscale serve} und
 * Tailscale Funnel setzen {@code X-Forwarded-For} selbst auf den echten Absender; bei cloudflared
 * hängt ihn die Cloudflare-Edge an, cloudflared reicht ihn durch. Ein Programm auf dem Rechner
 * selbst, das den Header fälscht, kann sich damit nur zum externen Absender herabstufen – lokal
 * ist es ohnehin.</p>
 */
public final class ClientIpResolver {
    /** Header, mit denen ein Proxy eine fremde Client-Adresse meldet. */
    private static final List<String> WEITERLEITUNGS_HEADER = List.of(
            "X-Forwarded-For", "Forwarded", "CF-Connecting-IP", "X-Real-IP", "True-Client-IP");
    /** Einzeladressen, die ein Proxy zusätzlich meldet. Sie müssen zur geprüften Kette passen. */
    private static final List<String> EINZELADRESS_HEADER = List.of("CF-Connecting-IP", "X-Real-IP", "True-Client-IP");
    private static final List<String> EIGENER_RECHNER = List.of("127.0.0.0/8", "::1/128");
    private static final Pattern IPV4 = Pattern.compile("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}");
    private static final Pattern IPV6 = Pattern.compile("[0-9a-fA-F:.]{2,45}");

    /**
     * Ermittelter Absender. {@code unbekannt}: Ein Proxy meldet einen fremden Absender, der sich nicht
     * prüfen lässt. Die Anfrage darf dann nicht als lokal gelten; {@code adresse} ist in dem Fall der
     * direkte Verbindungspartner, damit gefälschte Angaben auch die Code-Sperre nicht umgehen.
     */
    public record Absender(String adresse, boolean unbekannt) {}

    private final List<IpAddressMatcher> trusted;
    public ClientIpResolver(String proxies) {
        trusted = Stream.concat(EIGENER_RECHNER.stream(), Arrays.stream(proxies.split(",")))
                .map(String::trim).filter(s -> !s.isEmpty()).map(IpAddressMatcher::new).toList();
    }

    public String resolve(HttpServletRequest request) {
        return ermittle(request).adresse();
    }

    public Absender ermittle(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        String kette = ausProxyKette(request);
        if (kette == null) {
            return new Absender(peer, WEITERLEITUNGS_HEADER.stream().anyMatch(name -> request.getHeader(name) != null));
        }
        return widersprichtDerKette(request, kette) ? new Absender(peer, true) : new Absender(kette, false);
    }

    /**
     * Liest {@code X-Forwarded-For} (alle Zeilen, RFC 9110 §5.3) von rechts nach links, solange die
     * jeweilige Station freigegeben ist. {@code null}, wenn der Verbindungspartner nicht freigegeben
     * ist oder die Kette fehlt bzw. unbrauchbar ist.
     */
    private String ausProxyKette(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        if (!isTrusted(peer)) return null;
        String forwarded = String.join(",", Collections.list(request.getHeaders("X-Forwarded-For")));
        if (forwarded.isEmpty() || forwarded.length() > 2048) return null;
        // -1: Leere Glieder („10.0.0.1,,“) machen die Kette ungültig, statt still verworfen zu werden.
        String[] hops = forwarded.split(",", -1);
        for (int i = hops.length - 1; i >= 0 && isTrusted(peer); i--) {
            peer = numericAddress(hops[i].trim());
            if (peer == null) return null;
        }
        return peer;
    }

    /**
     * Ein Proxy, der ein vom Client mitgeschicktes {@code X-Forwarded-For} unverändert durchreicht und
     * den echten Absender nur in {@code X-Real-IP} o. ä. meldet, verrät sich durch abweichende Angaben.
     * {@code Forwarded} werten wir nicht aus und können ihn deshalb nicht abgleichen.
     */
    private static boolean widersprichtDerKette(HttpServletRequest request, String kette) {
        if (request.getHeader("Forwarded") != null) return true;
        for (String name : EINZELADRESS_HEADER) {
            for (String wert : Collections.list(request.getHeaders(name))) {
                if (!kette.equals(numericAddress(wert.trim()))) return true;
            }
        }
        return false;
    }

    private boolean isTrusted(String ip) {
        return ip != null && trusted.stream().anyMatch(m -> m.matches(ip));
    }

    /** Nur IP-Literale; Hostnamen würden eine DNS-Abfrage auslösen. */
    static String numericAddress(String value) {
        if (value.length() > 45) return null;
        boolean v4 = IPV4.matcher(value).matches() && Arrays.stream(value.split("\\.")).allMatch(o -> Integer.parseInt(o) <= 255);
        boolean v6 = !v4 && value.contains(":") && IPV6.matcher(value).matches();
        if (!v4 && !v6) return null;
        try { return InetAddress.getByName(value).getHostAddress(); }
        catch (UnknownHostException e) { return null; }
    }
}
