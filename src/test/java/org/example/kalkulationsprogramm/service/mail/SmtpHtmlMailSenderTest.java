package org.example.kalkulationsprogramm.service.mail;

import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.Mockito.*;

/**
 * Regressionstest fuer den Bug "SMTP-Passwort-Aenderung im System-Setup
 * wird nicht uebernommen". Frueher las die Klasse die Zugangsdaten via
 * {@code @Value} einmalig beim Spring-Boot-Start. Aenderungen im Frontend
 * landeten zwar in der DB (system_settings), wurden aber beim Versand
 * ignoriert.
 *
 * Diese Tests verifizieren, dass die Zugangsdaten bei JEDEM send()-Aufruf
 * frisch ueber {@link SystemSettingsService} gelesen werden.
 */
class SmtpHtmlMailSenderTest {

    private SystemSettingsService systemSettingsService;
    private SmtpHtmlMailSender sender;

    @BeforeEach
    void setup() {
        systemSettingsService = mock(SystemSettingsService.class);
        // Nicht aufloesbarer Host -> Transport.send() bricht ab, NACHDEM
        // die Credentials gelesen wurden. Das reicht fuer die Verifikation.
        when(systemSettingsService.getSmtpHost()).thenReturn("smtp.invalid.test");
        when(systemSettingsService.getSmtpPort()).thenReturn(465);
        when(systemSettingsService.getSmtpUsername()).thenReturn("info@example.com");
        when(systemSettingsService.getSmtpPassword()).thenReturn("altes-passwort");

        sender = new SmtpHtmlMailSender(systemSettingsService);
    }

    @Test
    void liestZugangsdatenZurLaufzeitAusSystemSettingsService() {
        // send() bricht beim echten SMTP-Connect ab - egal, wir verifizieren
        // nur, dass davor die Credentials aus dem Service gelesen wurden.
        try {
            sender.send("info@example.com", "kunde@example.com",
                    "Test", "<p>Hallo</p>", Map.of());
        } catch (Exception ignored) {
            // Verbindungsfehler erwartet
        }

        verify(systemSettingsService).getSmtpHost();
        verify(systemSettingsService).getSmtpPort();
        verify(systemSettingsService).getSmtpUsername();
        verify(systemSettingsService).getSmtpPassword();
    }

    @Test
    void liestPasswortBeiJedemSendAufrufNeu() {
        // Erster Aufruf mit altem Passwort
        try {
            sender.send("info@example.com", "kunde@example.com",
                    "Test 1", "<p>Erster Versand</p>", Map.of());
        } catch (Exception ignored) {
        }

        // Passwort wird im System-Setup geaendert -> System Settings liefert neuen Wert
        when(systemSettingsService.getSmtpPassword()).thenReturn("neues-passwort");

        try {
            sender.send("info@example.com", "kunde@example.com",
                    "Test 2", "<p>Zweiter Versand</p>", Map.of());
        } catch (Exception ignored) {
        }

        // Beweis: Passwort wurde zweimal gelesen (einmal pro send), nicht nur
        // einmal beim Spring-Boot-Start gecached.
        verify(systemSettingsService, times(2)).getSmtpPassword();
    }

    @Test
    void ueberspringtVersandWennEmpfaengerLeerIst() throws Exception {
        sender.send("info@example.com", "", "Test", "<p>Hi</p>", Map.of());
        sender.send("info@example.com", null, "Test", "<p>Hi</p>", Map.of());

        // Kein Server-Lookup wenn kein Empfaenger
        verifyNoInteractions(systemSettingsService);
    }
}
