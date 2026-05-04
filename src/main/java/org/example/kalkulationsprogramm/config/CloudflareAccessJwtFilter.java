package org.example.kalkulationsprogramm.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URL;
import java.util.Date;

/**
 * Defense-in-Depth-Schicht für den öffentlichen Funnel-Endpoint.
 * <p>
 * Cloudflare-Access hängt jedem durchgelassenen Request einen signierten JWT in
 * den Header {@code Cf-Access-Jwt-Assertion}. Dieser Filter validiert Signatur
 * (gegen die JWKs des Team-Domains), Issuer und Audience. So kommt selbst dann
 * niemand durch, wenn der ERP-Port versehentlich direkt im LAN exponiert wird.
 * <p>
 * Property {@code cloudflare.access.enabled=false} (Default) schaltet den
 * Filter komplett ab – z.B. für lokale Entwicklung ohne Tunnel.
 */
@Slf4j
@Component
public class CloudflareAccessJwtFilter extends OncePerRequestFilter {

    private static final String HEADER = "Cf-Access-Jwt-Assertion";

    @Value("${cloudflare.access.enabled:false}")
    private boolean enabled;

    @Value("${cloudflare.access.team-domain:}")
    private String teamDomain;

    @Value("${cloudflare.access.application-aud:}")
    private String applicationAud;

    private volatile ConfigurableJWTProcessor<SecurityContext> processor;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }

        String token = request.getHeader(HEADER);
        if (token == null || token.isBlank()) {
            log.warn("CF-Access-JWT fehlt für {}", request.getRequestURI());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "CF-Access-JWT fehlt");
            return;
        }

        try {
            getProcessor().process(token, null);
            chain.doFilter(request, response);
        } catch (Exception e) {
            log.warn("CF-Access-JWT ungültig für {}: {}", request.getRequestURI(), e.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "CF-Access-JWT ungültig");
        }
    }

    private ConfigurableJWTProcessor<SecurityContext> getProcessor() throws Exception {
        ConfigurableJWTProcessor<SecurityContext> local = processor;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (processor != null) {
                return processor;
            }
            if (teamDomain == null || teamDomain.isBlank()) {
                throw new IllegalStateException("cloudflare.access.team-domain muss gesetzt sein");
            }
            if (applicationAud == null || applicationAud.isBlank()) {
                throw new IllegalStateException("cloudflare.access.application-aud muss gesetzt sein");
            }
            URL jwkUrl = new URL("https://" + teamDomain + "/cdn-cgi/access/certs");
            JWKSource<SecurityContext> jwkSource = JWKSourceBuilder.create(jwkUrl).retrying(true).build();
            DefaultJWTProcessor<SecurityContext> p = new DefaultJWTProcessor<>();
            p.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
            String expectedIssuer = "https://" + teamDomain;
            String expectedAud = applicationAud;
            p.setJWTClaimsSetVerifier((JWTClaimsSet claims, SecurityContext ctx) -> {
                if (!expectedIssuer.equals(claims.getIssuer())) {
                    throw new BadJWTException("Falscher Issuer: " + claims.getIssuer());
                }
                if (claims.getAudience() == null || !claims.getAudience().contains(expectedAud)) {
                    throw new BadJWTException("Falsche Audience");
                }
                Date exp = claims.getExpirationTime();
                if (exp == null || exp.before(new Date())) {
                    throw new BadJWTException("Token abgelaufen");
                }
            });
            processor = p;
            return p;
        }
    }

    /** Pakettransparenter Hook für Tests. */
    void overrideProcessorForTesting(ConfigurableJWTProcessor<SecurityContext> testProcessor) {
        this.processor = testProcessor;
    }
}
