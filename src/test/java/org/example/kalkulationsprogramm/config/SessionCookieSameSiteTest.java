package org.example.kalkulationsprogramm.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.web.server.Cookie;
import org.springframework.boot.web.server.Cookie.SameSite;

/**
 * Das Login-Cookie (JSESSIONID) trägt SameSite=Lax: Fremde Webseiten bekommen es bei Anfragen im
 * Hintergrund nicht, angeklickte Links ins ERP bleiben angemeldet. Gebunden wird wie in Spring
 * Boot selbst, damit ein Tippfehler im Schlüssel den Test scheitern lässt.
 */
class SessionCookieSameSiteTest {

    @Test
    void loginCookieGehtNichtAnFremdeWebseiten() throws Exception {
        Properties basis = new Properties();
        try (InputStream in = Files.newInputStream(Path.of("src", "main", "resources", "application.properties"))) {
            basis.load(in);
        }

        Cookie loginCookie = new Binder(new MapConfigurationPropertySource(basis))
                .bind("server.servlet.session.cookie", Cookie.class).get();

        assertThat(loginCookie.getSameSite()).isEqualTo(SameSite.LAX);
    }
}
