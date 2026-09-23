package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto;
import org.example.kalkulationsprogramm.exception.EinkaufExceptionHandler;
import org.example.kalkulationsprogramm.service.mail.MailkontoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = EinkaufMailkontoController.class)
@Import({SecurityConfig.class, EinkaufExceptionHandler.class, EinkaufMailkontoHttpTest.FilterBeans.class})
class EinkaufMailkontoHttpTest {
    @TestConfiguration
    static class FilterBeans {
        @Bean CloudflareAccessJwtFilter cloudflareAccessJwtFilter() { return new CloudflareAccessJwtFilter(); }
    }

    @Autowired private MockMvc mvc;
    @MockBean private MailkontoService mailkontoService;
    @MockBean private FrontendUserDetailsService userDetailsService;

    @Test
    void ungueltigeMailkontoEingabeWirdAls400MitSichererMeldungAbgebildet() throws Exception {
        doThrow(new IllegalArgumentException("Bitte eine gültige Absender-Adresse eintragen."))
                .when(mailkontoService).speichern(any(), any());

        mvc.perform(put("/api/settings/einkauf-mail")
                        .with(authentication(70L, FrontendUserRole.ADMIN))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(validRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Bitte prüfe deine Eingaben."));
    }

    @Test
    void statuskonfliktWirdAls409OhneExceptiondetailsAbgebildet() throws Exception {
        doThrow(new IllegalStateException("provider password=geheim"))
                .when(mailkontoService).speichern(any(), any());

        mvc.perform(put("/api/settings/einkauf-mail")
                        .with(authentication(70L, FrontendUserRole.ADMIN))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(validRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Die Mailkonto-Einstellungen konnten nicht gespeichert werden. Bitte neu laden."));
    }

    @Test
    void verbindungstestBenötigtAdminUndCsrfUndGibtDummyTransportstatusZurueck() throws Exception {
        when(mailkontoService.verbindungTesten(any())).thenReturn(new MailTransportDto.Testverbindung(true, false, "IMAP_VERBINDUNG"));

        mvc.perform(put("/api/settings/einkauf-mail/verbindung-testen")
                        .with(authentication(70L, FrontendUserRole.ADMIN)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/settings/einkauf-mail/verbindung-testen")
                        .with(authentication(70L, FrontendUserRole.USER))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(mailkontoService);

        mvc.perform(put("/api/settings/einkauf-mail/verbindung-testen")
                        .with(authentication(70L, FrontendUserRole.ADMIN))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.smtpErfolgreich").value(true))
                .andExpect(jsonPath("$.imapErfolgreich").value(false))
                .andExpect(jsonPath("$.fehlerCode").value("IMAP_VERBINDUNG"));
        verify(mailkontoService).verbindungTesten(any());
    }

    @Test
    void testmailAntwortEnthaeltKeinMimeUndSicherenFehlercode() throws Exception {
        when(mailkontoService.testmail(any(), any())).thenReturn(new MailTransportDto.TestmailErgebnis(
                MailTransportDto.Status.SICHER_FEHLGESCHLAGEN, "<dummy@example.test>", "AUTHENTIFIZIERUNG_FEHLGESCHLAGEN"));
        mvc.perform(put("/api/settings/einkauf-mail/testmail")
                        .with(authentication(70L, FrontendUserRole.ADMIN))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"empfaenger\":\"test@example.com\",\"empfaengerBestaetigt\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SICHER_FEHLGESCHLAGEN"))
                .andExpect(jsonPath("$.fehlerCode").value("AUTHENTIFIZIERUNG_FEHLGESCHLAGEN"))
                .andExpect(jsonPath("$.mime").doesNotExist());
        verify(mailkontoService).testmail(any(), any());
    }

    private String validRequest() {
        return """
                {"version":0,"aktiv":false,"fromAddress":"einkauf@example.com","fromName":"Einkauf",
                "smtpHost":"smtp.test.invalid","smtpPort":465,"smtpUsername":"smtp-user","smtpTls":"TLS",
                "imapHost":"imap.test.invalid","imapPort":993,"imapUsername":"imap-user","imapTls":"STARTTLS",
                "inbox":"INBOX","sent":"Sent"}
                """;
    }

    private static RequestPostProcessor authentication(long id, FrontendUserRole role) {
        FrontendUserPrincipal principal = new FrontendUserPrincipal(id, "test@example.com", "Max Mustermann",
                "{noop}secret", true, Set.of(role));
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), principal.getAuthorities());
        return SecurityMockMvcRequestPostProcessors.authentication(auth);
    }
}
