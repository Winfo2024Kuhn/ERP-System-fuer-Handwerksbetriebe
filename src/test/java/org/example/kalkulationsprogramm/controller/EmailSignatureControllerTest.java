package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.EmailSignature;
import org.example.kalkulationsprogramm.service.EmailSignatureService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc-Tests fuer die System-Signatur-Endpoints (V256-Feature).
 *
 * <p>Spring-Security-Filter sind via {@code addFilters=false} deaktiviert —
 * der Authorization-Test fuer "nur ADMIN darf system-default setzen" wird
 * NICHT hier abgedeckt, sondern in einem dedizierten Spring-Security-Test
 * mit echter FilterChain. Diese Test-Klasse prueft nur das Controller-
 * Routing + die HTTP-Status-Codes der einzelnen Endpoint-Pfade.</p>
 */
@WebMvcTest(EmailSignatureController.class)
@AutoConfigureMockMvc(addFilters = false)
class EmailSignatureControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EmailSignatureService service;

    private EmailSignature signaturMit(Long id, String name, boolean systemDefault) {
        EmailSignature sig = new EmailSignature();
        sig.setId(id);
        sig.setName(name);
        sig.setHtml("<p>Inhalt</p>");
        sig.setSystemDefault(systemDefault);
        return sig;
    }

    @Test
    @DisplayName("GET /system-default liefert 204, wenn keine System-Sig konfiguriert")
    void getSystemDefault_leerLiefert204() throws Exception {
        given(service.list()).willReturn(List.of(signaturMit(1L, "Max Mustermann", false)));

        mockMvc.perform(get("/api/email/signatures/system-default"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("GET /system-default liefert Signatur + Platzhalter-Flag")
    void getSystemDefault_liefertSignaturUndFlag() throws Exception {
        EmailSignature sig = signaturMit(7L, "System-Sig", true);
        sig.setHtml("<p>Mit freundlichen Gruessen</p>");
        given(service.list()).willReturn(List.of(sig));

        mockMvc.perform(get("/api/email/signatures/system-default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signature.id").value(7))
                .andExpect(jsonPath("$.signature.name").value("System-Sig"))
                .andExpect(jsonPath("$.isPlatzhalter").value(false));
    }

    @Test
    @DisplayName("GET /system-default markiert unveraenderten Platzhalter")
    void getSystemDefault_platzhalterFlagTrue() throws Exception {
        EmailSignature platzhalter = signaturMit(99L, "System (automatische E-Mails)", true);
        platzhalter.setHtml("<div data-system-placeholder=\"1\">Bitte eintragen</div>");
        given(service.list()).willReturn(List.of(platzhalter));

        mockMvc.perform(get("/api/email/signatures/system-default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPlatzhalter").value(true));
    }

    @Test
    @DisplayName("PUT /{id}/system-default ruft Service auf und liefert 200")
    void setSystemDefault_ruftServiceAuf() throws Exception {
        EmailSignature gespeichert = signaturMit(42L, "Neue Sys-Sig", true);
        given(service.setSystemDefault(42L)).willReturn(gespeichert);

        mockMvc.perform(put("/api/email/signatures/42/system-default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.systemDefault").value(true));

        verify(service).setSystemDefault(42L);
    }

    @Test
    @DisplayName("PUT /{id}/system-default liefert 404 bei unbekannter ID")
    void setSystemDefault_unbekannteIdLiefert404() throws Exception {
        given(service.setSystemDefault(99L))
                .willThrow(new IllegalArgumentException("Signatur nicht gefunden: 99"));

        mockMvc.perform(put("/api/email/signatures/99/system-default"))
                .andExpect(status().isNotFound());
    }

}
