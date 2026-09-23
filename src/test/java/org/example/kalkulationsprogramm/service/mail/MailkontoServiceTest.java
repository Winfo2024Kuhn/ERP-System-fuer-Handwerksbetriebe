package org.example.kalkulationsprogramm.service.mail;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufMailkonto;
import org.example.kalkulationsprogramm.dto.Einkauf.MailkontoDto;
import org.example.kalkulationsprogramm.dto.Einkauf.MailkontoDto.Update;
import org.example.kalkulationsprogramm.dto.Einkauf.MailkontoDto.Verschluesselung;
import org.example.kalkulationsprogramm.repository.EinkaufMailkontoRepository;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class MailkontoServiceTest {
    @Mock private EinkaufMailkontoRepository repository;
    @Mock private EinkaufBerechtigungService berechtigungen;
    @Mock private SystemSettingsService settings;
    @Mock private MailSecretService secrets;
    private MailkontoService service;

    @BeforeEach
    void setUp() {
        service = new MailkontoService(repository, settings, secrets, berechtigungen);
    }

    @Test
    void nutztHauptUndDokumenteAlsKompatibleSystemSettingsAdapter() {
        when(settings.getStandardMailKonto()).thenReturn(
                new SystemSettingsService.MailKonto("smtp.test.invalid", 465, "test@example.com", "pw",
                        "test@example.com", "Test"));
        when(settings.getStandardImapZugang()).thenReturn(
                new SystemSettingsService.ImapZugang("imap.test.invalid", 993, "imap-user", "imap-pw"));

        MailkontoService.KontoZugang konto = service.resolve("HAUPT");

        assertEquals("HAUPT", konto.id());
        assertEquals("test@example.com", konto.smtp().username());
        assertEquals("imap-user", konto.imap().username());
    }

    @Test
    void passwortAbsentBewahrtBisherigesSecretUndResponseGibtNurFlagsZurueck() {
        FrontendUserPrincipal principal = aktiverAdmin();
        when(berechtigungen.verlangeAktivenAdmin(any())).thenReturn(70L);
        when(repository.findById("EINKAUF")).thenReturn(Optional.of(kontoMitSecrets()));
        when(repository.save(any(EinkaufMailkonto.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Update update = new Update(0L, true, "einkauf@example.com", "Einkauf",
                "smtp.test.invalid", 465, "smtp-user", Verschluesselung.TLS,
                "imap.test.invalid", 993, "imap-user", Verschluesselung.TLS,
                "INBOX", "Sent", null, null);
        MailkontoDto.Response response = service.speichern(auth(principal), update);

        assertTrue(response.smtpPasswordSet());
        assertTrue(response.imapPasswordSet());
        verify(secrets, never()).encrypt(null);
        assertFalse(response.toString().contains("smtp-secret"));
        assertFalse(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(response).toString().contains("smtp-secret"));
    }

    @Test
    void aktiviertNurVollstaendigKonfiguriertesKontoUndKeinFallback() {
        assertThrows(IllegalArgumentException.class, () -> service.resolve("einkauf"));
        when(repository.findById("EINKAUF")).thenReturn(Optional.empty());

        MailkontoService.KontoZugang konto = service.resolve("EINKAUF");

        assertFalse(konto.aktiv());
        assertNull(konto.smtp().password());
        verifyNoInteractions(settings);
    }

    @Test
    void verhindertAktivierungOhneVollstaendigeKonfigurationUndSecrets() {
        when(berechtigungen.verlangeAktivenAdmin(any())).thenReturn(70L);
        Update update = new Update(0L, true, "einkauf@example.com", "Einkauf",
                "smtp.test.invalid", 465, "smtp-user", Verschluesselung.TLS,
                "imap.test.invalid", 993, "imap-user", Verschluesselung.TLS,
                "INBOX", "Sent", null, null);

        assertThrows(IllegalArgumentException.class, () -> service.speichern(auth(aktiverAdmin()), update));
        verify(repository, never()).save(any());
    }

    @Test
    void lehntAktivierungOhneGetrennteServerBenutzernamenAb() {
        when(berechtigungen.verlangeAktivenAdmin(any())).thenReturn(70L);
        Update update = new Update(0L, true, "einkauf@example.com", "Einkauf",
                "smtp.test.invalid", 465, "", Verschluesselung.TLS,
                "imap.test.invalid", 993, "", Verschluesselung.STARTTLS,
                "INBOX", "Sent", "neues-smtp-passwort", "neues-imap-passwort");

        assertThrows(IllegalArgumentException.class, () -> service.speichern(auth(aktiverAdmin()), update));
        verify(repository, never()).save(any());
    }

    @Test
    void blockiertAktivesKontoWennDerVerschluesselungsschluesselFehlt() {
        when(berechtigungen.verlangeAktivenAdmin(any())).thenReturn(70L);
        when(repository.findById("EINKAUF")).thenReturn(Optional.of(kontoMitSecrets()));
        when(secrets.isConfigured()).thenReturn(false);
        doThrow(new IllegalStateException("Schlüssel fehlt")).when(secrets).ensureConfigured();
        Update update = new Update(0L, true, "einkauf@example.com", "Einkauf",
                "smtp.test.invalid", 465, "smtp-user", Verschluesselung.TLS,
                "imap.test.invalid", 993, "imap-user", Verschluesselung.STARTTLS,
                "INBOX", "Sent", null, null);

        assertThrows(IllegalStateException.class, () -> service.speichern(auth(aktiverAdmin()), update));
        verify(repository, never()).save(any());
    }

    @Test
    void passtDokumenteResolverAnBestehendesSystemSettingsKontoAn() {
        when(settings.getDokumentMailKonto()).thenReturn(
                new SystemSettingsService.MailKonto("docs-smtp.test.invalid", 587, "docs@example.com", "pw",
                        "docs@example.com", "Belege"));
        when(settings.getDokumentImapZugang()).thenReturn(
                new SystemSettingsService.ImapZugang("docs-imap.test.invalid", 993, "docs-user", "imap-pw"));

        MailkontoService.KontoZugang konto = service.resolve("DOKUMENTE");

        assertEquals("DOKUMENTE", konto.id());
        assertEquals("docs@example.com", konto.smtp().username());
        assertEquals("docs-user", konto.imap().username());
    }

    @Test
    void nurAktuellesAktivesAdminProfilDarfKontoSpeichern() {
        var staleAdmin = auth(aktiverAdmin());
        when(berechtigungen.verlangeAktivenAdmin(any())).thenThrow(new AccessDeniedException("inactive"));

        assertThrows(AccessDeniedException.class, () -> service.lesen(staleAdmin));
        verify(repository, never()).findById("EINKAUF");
    }

    private FrontendUserPrincipal aktiverAdmin() {
        return new FrontendUserPrincipal(70L, "test@example.com", "Max Mustermann", "", true,
                java.util.Set.of(FrontendUserRole.ADMIN));
    }

    private UsernamePasswordAuthenticationToken auth(FrontendUserPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities());
    }

    private EinkaufMailkonto kontoMitSecrets() {
        EinkaufMailkonto konto = new EinkaufMailkonto();
        konto.setId("EINKAUF");
        konto.setSmtpPasswordCiphertext("enc-smtp");
        konto.setImapPasswordCiphertext("enc-imap");
        konto.setVersion(0L);
        return konto;
    }
}
