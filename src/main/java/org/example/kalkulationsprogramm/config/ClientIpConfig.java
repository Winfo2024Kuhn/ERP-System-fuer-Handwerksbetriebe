package org.example.kalkulationsprogramm.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Stellt den {@link ClientIpResolver} für Controller bereit, die die Absender-IP ins
 * GoBD-Audit schreiben. Dieselben freigegebenen Proxys wie in {@link SecurityConfig}:
 * {@code X-Forwarded-For} zählt nur, wenn ein freigegebener Proxy ihn gesetzt hat.
 */
@Configuration
public class ClientIpConfig {

    @Bean
    public ClientIpResolver clientIpResolver(
            @Value("${zeiterfassung.security.trusted-proxies:}") String trustedProxies) {
        return new ClientIpResolver(trustedProxies);
    }
}
