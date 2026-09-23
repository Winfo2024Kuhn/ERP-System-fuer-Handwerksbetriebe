package org.example.kalkulationsprogramm.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.core.env.MapPropertySource;
import java.util.Map;

class LocalTestDatabaseGuardTest {

    private final LocalTestDatabaseGuard guard = new LocalTestDatabaseGuard();

    @Test
    void acceptsOnlyTheDedicatedLoopbackDatabaseAndLoopbackBinding() {
        MockEnvironment environment = localTestEnvironment(
                "jdbc:mysql://127.0.0.1:3309/kalkulationsprogramm_db", "127.0.0.1");

        assertDoesNotThrow(() -> guard.validate(environment));
    }

    @Test
    void rejectsRemoteDatabaseEvenWhenConfiguredByHighPriorityProperty() {
        MockEnvironment environment = localTestEnvironment(
                "jdbc:mysql://127.0.0.1:3309/kalkulationsprogramm_db", "127.0.0.1");
        environment.getPropertySources().addFirst(new MapPropertySource("commandLineArgs", Map.of(
                "spring.datasource.url", "jdbc:mysql://erp-kalkulations-db:3306/kalkulationsprogramm_db")));

        assertThrows(IllegalStateException.class, () -> guard.validate(environment));
    }

    @Test
    void rejectsHostSwitchParametersAndNonLoopbackServerBinding() {
        assertThrows(IllegalStateException.class, () -> guard.validate(localTestEnvironment(
                "jdbc:mysql://127.0.0.1:3309/kalkulationsprogramm_db?failOverReadOnly=false", "127.0.0.1")));
        assertThrows(IllegalStateException.class, () -> guard.validate(localTestEnvironment(
                "jdbc:mysql://127.0.0.1:3309/kalkulationsprogramm_db", "0.0.0.0")));
        MockEnvironment alternatePoolUrl = localTestEnvironment(
                "jdbc:mysql://127.0.0.1:3309/kalkulationsprogramm_db", "127.0.0.1");
        alternatePoolUrl.setProperty("spring.datasource.hikari.jdbc-url", "jdbc:mysql://remote/db");
        assertThrows(IllegalStateException.class, () -> guard.validate(alternatePoolUrl));

        MockEnvironment nestedHikariOverride = localTestEnvironment(
                "jdbc:mysql://127.0.0.1:3309/kalkulationsprogramm_db", "127.0.0.1");
        nestedHikariOverride.setProperty("spring.datasource.hikari.data-source-properties.socketFactory", "example.RouteSocketFactory");
        assertThrows(IllegalStateException.class, () -> guard.validate(nestedHikariOverride));
    }

    @Test
    void doesNotChangeOtherProfiles() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:mysql://remote/db");

        assertDoesNotThrow(() -> guard.validate(environment));
    }

    @Test
    void guardRunsAfterConfigDataHasResolved() {
        assert org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor.ORDER < guard.getOrder();
    }

    @Test
    void realSpringStartupRejectsCliDatabaseOverrideBeforeContextRefresh() {
        org.springframework.boot.builder.SpringApplicationBuilder application =
                new org.springframework.boot.builder.SpringApplicationBuilder(
                        org.example.kalkulationsprogramm.KalkulationsprogrammApplication.class)
                        .web(org.springframework.boot.WebApplicationType.NONE)
                        .profiles("local-test");

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> application.run("--spring.datasource.url=jdbc:mysql://invalid.example:3306/not-a-real-db"));
    }

    @Test
    void rejectsTlsOverrideThatContradictsTheLocalHttpProfile() {
        MockEnvironment environment = localTestEnvironment(
                "jdbc:mysql://127.0.0.1:3309/kalkulationsprogramm_db", "127.0.0.1");
        environment.getPropertySources().addFirst(new MapPropertySource("systemEnvironment", Map.of(
                "server.ssl.enabled", "true")));

        assertThrows(IllegalStateException.class, () -> guard.validate(environment));
    }

    @Test
    void rejectsOverridesThatDisableFlywayValidationOrMigrations() {
        MockEnvironment validationOff = localTestEnvironment(
                "jdbc:mysql://127.0.0.1:3309/kalkulationsprogramm_db", "127.0.0.1");
        validationOff.setProperty("spring.flyway.validate-on-migrate", "false");
        assertThrows(IllegalStateException.class, () -> guard.validate(validationOff));

        MockEnvironment flywayOff = localTestEnvironment(
                "jdbc:mysql://127.0.0.1:3309/kalkulationsprogramm_db", "127.0.0.1");
        flywayOff.setProperty("spring.flyway.enabled", "false");
        assertThrows(IllegalStateException.class, () -> guard.validate(flywayOff));
    }

    private static MockEnvironment localTestEnvironment(String url, String address) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local-test");
        return environment.withProperty("spring.datasource.url", url)
                .withProperty("server.address", address)
                .withProperty("spring.flyway.validate-on-migrate", "true");
    }
}
