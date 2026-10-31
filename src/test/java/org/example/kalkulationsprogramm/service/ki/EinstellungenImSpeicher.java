package org.example.kalkulationsprogramm.service.ki;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

import java.util.HashMap;
import java.util.Map;

import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.example.kalkulationsprogramm.service.mail.MailSecretService;
import org.springframework.mock.env.MockEnvironment;

/**
 * {@link SystemSettingsService} als Map im Speicher, damit {@link KiEinstellungen}
 * und {@link KiDienst} zusammen getestet werden können. Nur Test-Schlüssel.
 */
final class EinstellungenImSpeicher {

    static final String VERSCHLUESSELUNG = "0123456789abcdef0123456789abcdef";

    final Map<String, String> werte = new HashMap<>();
    final SystemSettingsService settings = mock(SystemSettingsService.class);
    final MockEnvironment umgebung = new MockEnvironment();
    final MailSecretService secrets;
    final KiEinstellungen einstellungen;

    EinstellungenImSpeicher() {
        this(VERSCHLUESSELUNG);
    }

    EinstellungenImSpeicher(String verschluesselung) {
        secrets = new MailSecretService(verschluesselung);
        lenient().when(settings.get(anyString(), any())).thenAnswer(
                aufruf -> werte.getOrDefault(aufruf.<String>getArgument(0), aufruf.getArgument(1)));
        lenient().doAnswer(aufruf -> werte.put(aufruf.getArgument(0), aufruf.getArgument(1)))
                .when(settings).save(anyString(), any(), any());
        lenient().when(settings.getGeminiApiKey()).thenAnswer(aufruf -> werte.getOrDefault("ai.gemini.api-key", ""));
        lenient().doAnswer(aufruf -> werte.put("ai.gemini.api-key", aufruf.getArgument(0)))
                .when(settings).saveGeminiApiKey(anyString());
        lenient().doAnswer(aufruf -> werte.put("ai.gemini.api-key", ""))
                .when(settings).entferneGeminiApiKey();
        einstellungen = new KiEinstellungen(settings, secrets, umgebung);
    }

    /** Wie oft bisher aus {@code system_setting} gelesen wurde (jeder Aufruf ist im Betrieb ein DB-Read). */
    long lesezugriffe() {
        return mockingDetails(settings).getInvocations().stream()
                .map(aufruf -> aufruf.getMethod().getName())
                .filter(name -> name.equals("get") || name.equals("getGeminiApiKey"))
                .count();
    }
}
