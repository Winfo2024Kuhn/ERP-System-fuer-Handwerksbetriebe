package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.SystemSetting;
import org.example.kalkulationsprogramm.repository.SystemSettingRepository;
import org.example.kalkulationsprogramm.service.mail.PostfachZugangService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Pflicht der Ersteinrichtung ist nur der Mailserver. Der Gemini-Schlüssel ist
 * freiwillig: ohne ihn bleiben die KI-Funktionen aus, der Rest läuft.
 */
class SystemSettingsServiceErsteinrichtungTest {

    private final Map<String, String> einstellungen = new HashMap<>();
    private SystemSettingsService service;

    @BeforeEach
    void setUp() {
        SystemSettingRepository repository = mock(SystemSettingRepository.class);
        lenient().when(repository.findById(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            if (!einstellungen.containsKey(key)) {
                return Optional.empty();
            }
            SystemSetting setting = new SystemSetting();
            setting.setKey(key);
            setting.setValue(einstellungen.get(key));
            return Optional.of(setting);
        });
        // Ohne Postfach gelten die Konto-Einstellungen aus der Map.
        service = new SystemSettingsService(repository, mock(PostfachZugangService.class));
    }

    private void mailserverEingerichtet() {
        einstellungen.put("smtp.host", "mail.example.com");
        einstellungen.put("smtp.port", "587");
        einstellungen.put("smtp.username", "max.mustermann@example.com");
        einstellungen.put("smtp.password", "dummy-passwort");
    }

    @Test
    @DisplayName("Mailserver da, kein Gemini-Schlüssel: Ersteinrichtung ist erledigt, KI ist aus")
    void ohneGeminiAberMitMailserverKeinePflicht() {
        mailserverEingerichtet();

        assertThat(service.isInitialConfigurationRequired()).isFalse();
        assertThat(service.isGeminiConfigured()).isFalse();
    }

    @Test
    @DisplayName("Mailserver und Gemini-Schlüssel da: Ersteinrichtung erledigt, KI ist an")
    void mitMailserverUndGeminiKeinePflichtUndKiAn() {
        mailserverEingerichtet();
        einstellungen.put("ai.gemini.api-key", "dummy-gemini-schluessel");

        assertThat(service.isInitialConfigurationRequired()).isFalse();
        assertThat(service.isGeminiConfigured()).isTrue();
    }

    @Test
    @DisplayName("Ohne Mailserver bleibt die Ersteinrichtung Pflicht – auch mit Gemini-Schlüssel")
    void ohneMailserverPflichtTrotzGemini() {
        einstellungen.put("ai.gemini.api-key", "dummy-gemini-schluessel");

        assertThat(service.isInitialConfigurationRequired()).isTrue();
    }

    @Test
    @DisplayName("Mailserver ohne Passwort zählt als unvollständig")
    void mailserverOhnePasswortIstPflicht() {
        mailserverEingerichtet();
        einstellungen.remove("smtp.password");

        assertThat(service.isInitialConfigurationRequired()).isTrue();
    }

    @Test
    @DisplayName("Ganz frische Installation: Mailserver fehlt, KI ist aus")
    void frischeInstallation() {
        assertThat(service.isInitialConfigurationRequired()).isTrue();
        assertThat(service.isGeminiConfigured()).isFalse();
    }

    @ParameterizedTest(name = "\"{0}\" zählt nicht als Schlüssel")
    @ValueSource(strings = {"", "   ", "OVERRIDE_IN_LOCAL", "override_in_local"})
    void platzhalterUndLeereWerteSchaltenKiNichtEin(String wert) {
        einstellungen.put("ai.gemini.api-key", wert);

        assertThat(service.isGeminiConfigured()).isFalse();
    }
}
