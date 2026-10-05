package org.example.email;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImapAppendServiceLueckenTest {

    @Mock SystemSettingsService settings;
    ImapAppendService service;

    @BeforeEach
    void setUp() {
        service = new ImapAppendService(settings);
    }

    @Test
    @DisplayName("Ohne IMAP-Konfiguration passiert nichts (keine Zugangsdaten werden gelesen)")
    void nichtKonfiguriert() {
        when(settings.isImapConfigured()).thenReturn(false);
        assertDoesNotThrow(() -> service.appendToSent("a@example.com", List.of("b@example.com"), "x", "<p>x</p>", null,
                LocalDateTime.now()));
        verify(settings, never()).getImapHost();
        verify(settings, never()).getImapPassword();
    }

    private void konfiguriert() {
        when(settings.isImapConfigured()).thenReturn(true);
        when(settings.getImapUsername()).thenReturn("user");
        when(settings.getImapPassword()).thenReturn("pw");
        when(settings.getImapHost()).thenReturn("127.0.0.1");
        when(settings.getImapPort()).thenReturn(1);
    }

    @Test
    @DisplayName("Nicht erreichbarer IMAP-Server: Fehler wird verschluckt, Hauptablauf laeuft weiter")
    void serverNichtErreichbar(@TempDir Path dir) throws Exception {
        konfiguriert();
        Path datei = dir.resolve("plan.pdf");
        Files.writeString(datei, "x");
        assertDoesNotThrow(() -> service.appendToSent("firma@example.com",
                Arrays.asList("max@example.com", " ", null, " erika@example.org "), "Betreff äöü",
                "<p>Hallo <img src=\"cid:Firmenlogo\"></p>",
                Arrays.asList(datei.toFile(), null, new File(dir.toFile(), "fehlt.txt")), LocalDateTime.now()));
        verify(settings).getImapHost();
    }

    @Test
    @DisplayName("Alle Parameter null: kein Fehler")
    void alleNull() {
        konfiguriert();
        assertDoesNotThrow(() -> service.appendToSent(null, null, null, null, null, null));
    }

    @Test
    @DisplayName("Ungueltige Adressen (Header-Injection) werden abgefangen")
    void ungueltigeAdresse() {
        konfiguriert();
        assertDoesNotThrow(() -> service.appendToSent("<<>>\r\nBcc: x@example.org", List.of("kein@@example.com\r\n"),
                "Betreff\r\nBcc: x@example.org", "<script>alert(1)</script>", List.of(), LocalDateTime.now()));
    }
}
