package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.example.kalkulationsprogramm.exception.EinkaufExceptionHandler;
import org.example.kalkulationsprogramm.service.mail.MailkontoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EinkaufMailkontoHttpTest {
    private MailkontoService mailkontoService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mailkontoService = org.mockito.Mockito.mock(MailkontoService.class);
        mvc = MockMvcBuilders.standaloneSetup(new EinkaufMailkontoController(mailkontoService))
                .setControllerAdvice(new EinkaufExceptionHandler())
                .build();
    }

    @Test
    void ungültigeMailkontoEingabeWirdAls400MitSichererMeldungAbgebildet() throws Exception {
        doThrow(new IllegalArgumentException("Bitte eine gültige Absender-Adresse eintragen."))
                .when(mailkontoService).speichern(any(), any());

        mvc.perform(put("/api/settings/einkauf-mail")
                        .principal(new TestingAuthenticationToken("test@example.com", ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Bitte prüfe deine Eingaben."));
    }

    @Test
    void statuskonfliktWirdAls409OhneExceptiondetailsAbgebildet() throws Exception {
        doThrow(new IllegalStateException("provider password=geheim"))
                .when(mailkontoService).speichern(any(), any());

        mvc.perform(put("/api/settings/einkauf-mail")
                        .principal(new TestingAuthenticationToken("test@example.com", ""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Die Mailkonto-Einstellungen konnten nicht gespeichert werden. Bitte neu laden."));
    }

    private String validRequest() {
        return """
                {"version":0,"aktiv":false,"fromAddress":"einkauf@example.com","fromName":"Einkauf",
                "smtpHost":"smtp.test.invalid","smtpPort":465,"smtpUsername":"smtp-user","smtpTls":"TLS",
                "imapHost":"imap.test.invalid","imapPort":993,"imapUsername":"imap-user","imapTls":"STARTTLS",
                "inbox":"INBOX","sent":"Sent"}
                """;
    }
}
