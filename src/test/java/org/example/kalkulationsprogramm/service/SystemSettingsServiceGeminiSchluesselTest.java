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

/** Gemini-Schlüssel in den Einstellungen: Entfernen schaltet die KI aus. Prüfen und Speichern: {@code KiEinstellungen}. */
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
}
