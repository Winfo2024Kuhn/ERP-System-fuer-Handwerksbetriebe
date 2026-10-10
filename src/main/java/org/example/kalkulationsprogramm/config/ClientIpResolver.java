package org.example.kalkulationsprogramm.config;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

/** Forwarded-Header sind nur hinter ausdrücklich freigegebenen Proxys vertrauenswürdig. */
public final class ClientIpResolver {
    /** Header, mit denen ein Proxy eine fremde Client-Adresse meldet. */
    private static final List<String> WEITERLEITUNGS_HEADER = List.of(
            "X-Forwarded-For", "Forwarded", "CF-Connecting-IP", "X-Real-IP", "True-Client-IP");
    private static final Pattern IPV4 = Pattern.compile("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}");
    private static final Pattern IPV6 = Pattern.compile("[0-9a-fA-F:.]{2,45}");

    private final List<IpAddressMatcher> trusted;
    public ClientIpResolver(String proxies) {
        trusted = Arrays.stream(proxies.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(IpAddressMatcher::new).toList();
    }
    public String resolve(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        if (!isTrusted(peer)) return peer;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.length() > 2048) return peer;
        String[] hops = forwarded.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            if (!isTrusted(peer)) break;
            String next = numericAddress(hops[i].trim());
            if (next == null) return request.getRemoteAddr();
            peer = next;
        }
        return peer;
    }

    /**
     * Meldet ein nicht freigegebener Verbindungspartner eine weitergeleitete Anfrage? Dann steht ein
     * Proxy oder Tunnel davor (z. B. cloudflared auf localhost), und die direkte Adresse sagt nichts
     * über den echten Absender. Solche Anfragen dürfen nicht als lokal gelten.
     */
    public boolean weitergeleitetVonUnbekanntemProxy(HttpServletRequest request) {
        if (isTrusted(request.getRemoteAddr())) return false;
        return WEITERLEITUNGS_HEADER.stream().anyMatch(name -> request.getHeader(name) != null);
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
