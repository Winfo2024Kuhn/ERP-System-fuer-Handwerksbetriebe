package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.example.kalkulationsprogramm.service.DateiOrdnerService;
import org.example.kalkulationsprogramm.service.PostfachUmzugService;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Die Ersteinrichtung speichert das Mail-Konto weiter in den Einstellungen – und gleicht
 * dabei genau diesen Teil ins Hauptpostfach ab. Die alten Formulare für Absender und
 * Rechnungs-Konto gibt es nicht mehr (Einstellungen → E-Mail → Postfächer).
 */
@WebMvcTest(SystemSettingsController.class)
@AutoConfigureMockMvc(addFilters = false)
class SystemSettingsControllerMailKontoTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private SystemSettingsService settingsService;
    @MockBean private DateiOrdnerService dateiOrdnerService;
    @MockBean private PostfachUmzugService postfachUmzugService;

    @Test
    void smtpSpeichernGleichtVersandAb() throws Exception {
        mockMvc.perform(put("/api/settings/smtp").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"host\":\"mail.example.com\",\"port\":465,\"username\":\"info@example.com\",\"password\":\"pw\"}"))
                .andExpect(status().isOk());

        verify(settingsService).saveSmtpSettings("mail.example.com", 465, "info@example.com", "pw");
        verify(postfachUmzugService).gleicheHauptpostfachAb(PostfachUmzugService.Abgleich.VERSAND, true);
    }

    @Test
    void ungueltigesSmtpGleichtNichtsAb() throws Exception {
        mockMvc.perform(put("/api/settings/smtp").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"host\":\"\",\"port\":465,\"username\":\"info@example.com\"}"))
                .andExpect(status().isBadRequest());

        verify(postfachUmzugService, never()).gleicheHauptpostfachAb(any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void smtpOhneBenutzer400UndLeeresPasswortBehaeltDasAlte() throws Exception {
        mockMvc.perform(put("/api/settings/smtp").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"host\":\"mail.example.com\",\"port\":465,\"username\":\" \"}"))
                .andExpect(status().isBadRequest());
        // Leeres Passwort: das gespeicherte aus den Einstellungen bleiben, NICHT das entschlüsselte
        // Hauptpostfach-Passwort aus dem Getter (das landete sonst im Klartext in den Einstellungen).
        org.mockito.Mockito.when(settingsService.getSmtpPassword()).thenReturn("entschluesselt-aus-postfach");
        org.mockito.Mockito.when(settingsService.gespeichertesStandardKonto()).thenReturn(
                new SystemSettingsService.MailKonto("mail.example.com", 465, "info@example.com", "altes-pw", "", ""));

        mockMvc.perform(put("/api/settings/smtp").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"host\":\"mail.example.com\",\"port\":465,\"username\":\"info@example.com\",\"password\":\"\"}"))
                .andExpect(status().isOk());

        verify(settingsService).saveSmtpSettings("mail.example.com", 465, "info@example.com", "altes-pw");
        verify(postfachUmzugService).gleicheHauptpostfachAb(PostfachUmzugService.Abgleich.VERSAND, false);
    }

    @Test
    void imapUngueltig400UndLeeresPasswortBehaeltDasAlte() throws Exception {
        mockMvc.perform(put("/api/settings/imap").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"host\":\" \",\"username\":\"info@example.com\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/settings/imap").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"host\":\"mail.example.com\",\"username\":\"\"}"))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.when(settingsService.gespeicherterStandardImapZugang()).thenReturn(
                new SystemSettingsService.ImapZugang("mail.example.com", 993, "info@example.com", "altes-pw"));

        mockMvc.perform(put("/api/settings/imap").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"host\":\"mail.example.com\",\"port\":143,\"username\":\"info@example.com\"}"))
                .andExpect(status().isOk());

        verify(settingsService).saveImapSettings("mail.example.com", 143, "info@example.com", "altes-pw");
        verify(postfachUmzugService).gleicheHauptpostfachAb(PostfachUmzugService.Abgleich.ABRUF, false);
    }

    @Test
    void kontoOhneAdresse400() throws Exception {
        mockMvc.perform(put("/api/settings/email-account").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"pw\"}"))
                .andExpect(status().isBadRequest());
        verify(postfachUmzugService, never()).gleicheHauptpostfachAb(any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void imapSpeichernGleichtAbrufAb() throws Exception {
        mockMvc.perform(put("/api/settings/imap").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"host\":\"mail.example.com\",\"port\":0,\"username\":\"info@example.com\",\"password\":\"pw\"}"))
                .andExpect(status().isOk());

        verify(settingsService).saveImapSettings("mail.example.com", 993, "info@example.com", "pw");
        verify(postfachUmzugService).gleicheHauptpostfachAb(PostfachUmzugService.Abgleich.ABRUF, true);
    }

    @Test
    void kontoSpeichernGleichtZugangAb() throws Exception {
        mockMvc.perform(put("/api/settings/email-account").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"info@example.com\",\"password\":\"pw\"}"))
                .andExpect(status().isOk());

        verify(postfachUmzugService).gleicheHauptpostfachAb(PostfachUmzugService.Abgleich.ZUGANG, true);
    }

    @Test
    void verbindungstestSchicktGespeichertesPasswortNichtAnFremdenServer() throws Exception {
        org.mockito.Mockito.when(settingsService.getSmtpHost()).thenReturn("mail.example.com");
        org.mockito.Mockito.when(settingsService.getSmtpUsername()).thenReturn("info@example.com");
        org.mockito.Mockito.when(settingsService.getImapHost()).thenReturn("mail.example.com");
        org.mockito.Mockito.when(settingsService.getImapUsername()).thenReturn("info@example.com");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/settings/smtp/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"host\":\"angreifer.example.org\",\"port\":465,\"username\":\"info@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.success").value(false));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/settings/imap/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"host\":\"mail.example.com\",\"port\":993,\"username\":\"fremd@example.org\"}"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.success").value(false));

        verify(settingsService, never()).getSmtpPassword();
        verify(settingsService, never()).getImapPassword();
        verify(settingsService, never()).testSmtp(anyString(), anyInt(), anyString(), anyString(), any());
    }

    @Test
    void verbindungstestMitGespeichertemServerNutztGespeichertesPasswort() throws Exception {
        org.mockito.Mockito.when(settingsService.getSmtpHost()).thenReturn("mail.example.com");
        org.mockito.Mockito.when(settingsService.getSmtpPort()).thenReturn(465);
        org.mockito.Mockito.when(settingsService.getSmtpUsername()).thenReturn("info@example.com");
        org.mockito.Mockito.when(settingsService.getSmtpPassword()).thenReturn("pw");
        org.mockito.Mockito.when(settingsService.getImapHost()).thenReturn("mail.example.com");
        org.mockito.Mockito.when(settingsService.getImapPort()).thenReturn(993);
        org.mockito.Mockito.when(settingsService.getImapUsername()).thenReturn("info@example.com");
        org.mockito.Mockito.when(settingsService.getImapPassword()).thenReturn("pw");
        org.mockito.Mockito.when(settingsService.testSmtp("mail.example.com", 465, "info@example.com", "pw", null))
                .thenReturn(SystemSettingsService.TestResult.success("ok"));
        org.mockito.Mockito.when(settingsService.testImap("mail.example.com", 993, "info@example.com", "pw"))
                .thenReturn(SystemSettingsService.TestResult.success("ok"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/settings/smtp/test")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.success").value(true));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/settings/imap/test")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.success").value(true));
    }

    @Test
    void alteFormulareGibtEsNichtMehr() throws Exception {
        mockMvc.perform(get("/api/settings/dokument-mail")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/settings/mail-from")).andExpect(status().isNotFound());
        verify(settingsService, never()).saveDokumentMailSettings(org.mockito.ArgumentMatchers.anyBoolean(), anyString(),
                anyInt(), anyString(), anyString(), anyString());
    }
}
