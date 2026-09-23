package org.example.kalkulationsprogramm.config;

import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** Prevents local-test from connecting to production or remotely routed databases. */
@Component
public final class LocalTestDatabaseGuard implements EnvironmentPostProcessor, Ordered {
    private static final Pattern ALLOWED_URL = Pattern.compile(
            "jdbc:mysql://127\\.0\\.0\\.1:3309/(?:kalkulationsprogramm_db|erp_local_test)");

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        validate(environment);
    }

    void validate(ConfigurableEnvironment environment) {
        if (!environment.acceptsProfiles(Profiles.of("local-test"))) {
            return;
        }
        String url = environment.getProperty("spring.datasource.url", "");
        String hikariUrl = environment.getProperty("spring.datasource.hikari.jdbc-url", "");
        String serverAddress = environment.getProperty("server.address", "");
        if (!ALLOWED_URL.matcher(url).matches()
                || (!hikariUrl.isBlank() && !ALLOWED_URL.matcher(hikariUrl).matches())) {
            throw new IllegalStateException("local-test erlaubt nur die isolierte Loopback-Datenbank auf Port 3309 ohne JDBC-Parameter.");
        }
        if (!"127.0.0.1".equals(serverAddress)) {
            throw new IllegalStateException("local-test muss den Server an 127.0.0.1 binden.");
        }
        if (environment.getProperty("server.ssl.enabled", Boolean.class, false)) {
            throw new IllegalStateException("local-test verlangt den expliziten lokalen HTTP-Modus.");
        }
        if (!environment.getProperty("spring.flyway.enabled", Boolean.class, true)
                || !environment.getProperty("spring.flyway.validate-on-migrate", Boolean.class, false)) {
            throw new IllegalStateException("local-test benötigt aktive Flyway-Migrationen mit Prüfsummenvalidierung.");
        }
        for (String property : Arrays.asList(
                "spring.datasource.jndi-name",
                "spring.datasource.hikari.data-source-class-name")) {
            if (!environment.getProperty(property, "").isBlank()) {
                throw new IllegalStateException("local-test erlaubt keine alternativen oder gerouteten Datenquellen.");
            }
        }
        Map<String, String> dataSourceProperties = Binder.get(environment)
                .bind("spring.datasource.hikari.data-source-properties", Bindable.mapOf(String.class, String.class))
                .orElse(Map.of());
        if (!dataSourceProperties.isEmpty()) {
            throw new IllegalStateException("local-test erlaubt keine zusätzlichen Hikari-Datenquelleneigenschaften.");
        }
    }
}
