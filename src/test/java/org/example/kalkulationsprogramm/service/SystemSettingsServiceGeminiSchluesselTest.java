package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.example.kalkulationsprogramm.domain.SystemSetting;
import org.mockito.ArgumentCaptor;

import org.example.kalkulationsprogramm.repository.SystemSettingRepository;
import org.junit.jupiter.api.Test;

/** Schlüssel-Test der KI-Einstellungen: ungültige Zeichen ohne Netzaufruf und ohne Schlüssel in der Meldung. */
class SystemSettingsServiceGeminiSchluesselTest {

    private final SystemSettingRepository repository = mock(SystemSettingRepository.class);
    private final SystemSettingsService service = new SystemSettingsService(repository);

    @Test
    void entfernenSpeichertLeerenSchluesselUndSchaltetKiAus() {
        when(repository.findById("ai.gemini.api-key"))
                .thenReturn(Optional.of(new SystemSetting("ai.gemini.api-key", "dummy-test-key", "Google Gemini API Key")));

        service.entferneGeminiApiKey();

        ArgumentCaptor<SystemSetting> gespeichert = ArgumentCaptor.forClass(SystemSetting.class);
        verify(repository).save(gespeichert.capture());
        assertThat(gespeichert.getValue().getValue()).isEmpty();
        when(repository.findById("ai.gemini.api-key")).thenReturn(Optional.of(gespeichert.getValue()));
        assertThat(service.isGeminiConfigured()).isFalse();
    }

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
