package org.example.kalkulationsprogramm.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.PlainHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

/**
 * Prüft die Bausteine, aus denen {@link CloudflareAccessJwtFilter} seine Signaturprüfung baut
 * ({@link DefaultJWTProcessor} + {@link JWSVerificationKeySelector} mit RS256), gegen echte, lokal
 * signierte Tokens. Der Filter-Test ersetzt den Prozessor durch ein Mock – hier fällt auf, wenn
 * eine neue nimbus-jose-jwt-Version Standards ändert (z. B. beim Typ-Header oder der Algorithmuswahl).
 * Der Aufbau spiegelt {@code CloudflareAccessJwtFilter#getProcessor}; nur die Schlüsselquelle ist
 * lokal statt der Cloudflare-URL.
 */
class CloudflareAccessJwtSignaturTest {

    private static final String ISSUER = "https://musterfirma.cloudflareaccess.com";
    private static final String AUD = "aud-mustermann";

    private RSAKey schluessel;
    private DefaultJWTProcessor<SecurityContext> prozessor;

    @BeforeEach
    void aufbauWieImFilter() throws Exception {
        schluessel = new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
        prozessor = new DefaultJWTProcessor<>();
        prozessor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256,
                new ImmutableJWKSet<>(new JWKSet(schluessel.toPublicJWK()))));
        prozessor.setJWTClaimsSetVerifier((JWTClaimsSet claims, SecurityContext ctx) -> {
            if (!ISSUER.equals(claims.getIssuer())) {
                throw new BadJWTException("Falscher Issuer: " + claims.getIssuer());
            }
            if (claims.getAudience() == null || !claims.getAudience().contains(AUD)) {
                throw new BadJWTException("Falsche Audience");
            }
            Date exp = claims.getExpirationTime();
            if (exp == null || exp.before(new Date())) {
                throw new BadJWTException("Token abgelaufen");
            }
        });
    }

    private JWTClaimsSet claims(String issuer, String aud, long gueltigMs) {
        return new JWTClaimsSet.Builder().issuer(issuer).audience(List.of(aud)).subject("max.mustermann@example.com")
                .expirationTime(new Date(System.currentTimeMillis() + gueltigMs)).build();
    }

    private String signiert(RSAKey mit, JWTClaimsSet claims) throws Exception {
        // Cloudflare setzt typ=JWT im Kopf
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(schluessel.getKeyID())
                .type(JOSEObjectType.JWT).build(), claims);
        jwt.sign(new RSASSASigner(mit));
        return jwt.serialize();
    }

    @Test
    void gueltigesTokenWirdAngenommen() throws Exception {
        String token = signiert(schluessel, claims(ISSUER, AUD, 60_000));
        assertThatCode(() -> prozessor.process(token, null)).doesNotThrowAnyException();
    }

    @Test
    void fremdeSignaturWirdAbgewiesen() throws Exception {
        RSAKey fremd = new RSAKeyGenerator(2048).keyID(schluessel.getKeyID()).generate();
        String token = signiert(fremd, claims(ISSUER, AUD, 60_000));
        assertThatThrownBy(() -> prozessor.process(token, null)).isInstanceOf(Exception.class);
    }

    @Test
    void falscheAudienceOderIssuerWirdAbgewiesen() throws Exception {
        String fremdeAud = signiert(schluessel, claims(ISSUER, "andere-app", 60_000));
        String fremderIssuer = signiert(schluessel, claims("https://boese.example.com", AUD, 60_000));
        assertThatThrownBy(() -> prozessor.process(fremdeAud, null)).hasMessageContaining("Audience");
        assertThatThrownBy(() -> prozessor.process(fremderIssuer, null)).hasMessageContaining("Issuer");
    }

    @Test
    void abgelaufenesTokenWirdAbgewiesen() throws Exception {
        String token = signiert(schluessel, claims(ISSUER, AUD, -60_000));
        assertThatThrownBy(() -> prozessor.process(token, null)).hasMessageContaining("abgelaufen");
    }

    @Test
    void unsigniertesOderHmacTokenWirdAbgewiesen() throws Exception {
        String ohneSignatur = new PlainJWT(new PlainHeader(), claims(ISSUER, AUD, 60_000)).serialize();
        SignedJWT hmac = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims(ISSUER, AUD, 60_000));
        hmac.sign(new MACSigner(new byte[32]));
        String hmacToken = hmac.serialize();

        assertThatThrownBy(() -> prozessor.process(ohneSignatur, null)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> prozessor.process(hmacToken, null)).isInstanceOf(Exception.class);
    }
}
