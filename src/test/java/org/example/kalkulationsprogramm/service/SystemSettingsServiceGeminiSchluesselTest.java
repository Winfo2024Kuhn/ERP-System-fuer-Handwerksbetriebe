package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.example.kalkulationsprogramm.repository.SystemSettingRepository;
import org.junit.jupiter.api.Test;

/** Schlüssel-Test der KI-Einstellungen: ungültige Zeichen ohne Netzaufruf und ohne Schlüssel in der Meldung. */
class SystemSettingsServiceGeminiSchluesselTest {

    private final SystemSettingsService service = new SystemSettingsService(mock(SystemSettingRepository.class));

    @Test
    void leererSchluesselWirdAbgewiesen() {
        assertThat(service.testGeminiApiKey("  ").message()).isEqualTo("Kein API Key angegeben.");
    }

    @Test
    void schluesselMitZeilenumbruchNenntSichNichtSelbst() {
        var ergebnis = service.testGeminiApiKey("dummy-test\n-key");

        assertThat(ergebnis.success()).isFalse();
        assertThat(ergebnis.message())
                .isEqualTo("Der API Key enthält ungültige Zeichen. Bitte neu kopieren.")
                .doesNotContain("dummy-test");
    }
}
