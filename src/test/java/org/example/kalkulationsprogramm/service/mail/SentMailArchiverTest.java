package org.example.kalkulationsprogramm.service.mail;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Properties;

import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/**
 * Tests fuer die Server-Kopie im "Gesendet"-Ordner.
 *
 * <p>Geprueft werden die Abbruchbedingungen, die <em>vor</em> jedem
 * Netzwerkzugriff greifen. Das ist der sicherheitsrelevante Teil: der Archiver
 * wird aus dem Versand-Pfad heraus aufgerufen (auch aus dem naechtlichen
 * Mahnlauf), und ein Verbindungsversuch gegen einen nicht konfigurierten Server
 * wuerde dort pro Mail bis zum Timeout blockieren.</p>
 *
 * <p>Der erfolgreiche APPEND-Pfad ist bewusst nicht abgedeckt — er braucht
 * einen echten IMAP-Server und gehoert damit in einen Integrationstest.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SentMailArchiverTest
{
    @Mock SystemSettingsService systemSettingsService;
    @InjectMocks SentMailArchiver archiver;

    private static MimeMessage dummyNachricht() throws Exception
    {
        MimeMessage msg = new MimeMessage(Session.getInstance(new Properties()));
        msg.setSubject("Angebot AG-2026/07/00005");
        msg.setText("Dummy");
        msg.saveChanges();
        return msg;
    }

    @Test
    void versuchtKeineVerbindungWennImapNichtKonfiguriertIst() throws Exception
    {
        when(systemSettingsService.get(anyString(), anyString())).thenReturn("true");
        when(systemSettingsService.isImapConfigured()).thenReturn(false);

        archiver.archiviereKopie(dummyNachricht());

        // Kein Zugriff auf Host/Port/Zugangsdaten = kein Verbindungsversuch.
        verify(systemSettingsService, never()).getImapHost();
        verify(systemSettingsService, never()).getImapPassword();
    }

    @Test
    void versuchtKeineVerbindungWennFunktionAbgeschaltetIst() throws Exception
    {
        when(systemSettingsService.get(anyString(), anyString())).thenReturn("false");

        archiver.archiviereKopie(dummyNachricht());

        verify(systemSettingsService, never()).isImapConfigured();
        verify(systemSettingsService, never()).getImapHost();
    }

    @Test
    void ignoriertNullOhneFehler()
    {
        archiver.archiviereKopie(null);

        verify(systemSettingsService, never()).isImapConfigured();
    }
}
