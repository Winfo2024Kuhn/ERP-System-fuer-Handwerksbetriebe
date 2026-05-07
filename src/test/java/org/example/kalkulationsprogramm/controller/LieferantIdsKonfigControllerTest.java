package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.IdsProtokoll;
import org.example.kalkulationsprogramm.domain.LieferantIdsKonfig;
import org.example.kalkulationsprogramm.service.FrontendUserProfileService;
import org.example.kalkulationsprogramm.service.LieferantIdsKonfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests für die AuthZ-Schicht und Passwort-Maskierung des
 * LieferantIdsKonfigController. Kritisch: kein anonymer Zugriff auf
 * /api/admin/**, kein Klartext-Passwort im GET-Response.
 */
@WebMvcTest(controllers = LieferantIdsKonfigController.class)
@AutoConfigureMockMvc
class LieferantIdsKonfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LieferantIdsKonfigService konfigService;

    @MockBean
    private FrontendUserProfileService userProfileService;

    @Test
    void getOhneAuthentifizierung_gibt401Oder403() throws Exception {
        // Spring Security mit Default-Security-Setup: anonymer Zugriff geblockt.
        // Die feinkörnige hasRole(ADMIN)-Regel ist in SecurityConfig deklariert
        // und greift im echten Boot-Kontext für /api/admin/** – hier deckt der
        // Test ab, dass der Controller selbst KEIN permitAll() macht und ohne
        // Authentifizierung nicht erreichbar ist.
        mockMvc.perform(get("/api/admin/lieferanten/1/ids-konfig"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getMitAdminRolle_liefertKonfigUndMaskiertPasswort() throws Exception {
        LieferantIdsKonfig konfig = new LieferantIdsKonfig();
        konfig.setAktiviert(true);
        konfig.setProtokoll(IdsProtokoll.IDS_CONNECT_2_5);
        konfig.setPunchoutUrl("https://eshop.wuerth.de/...");
        konfig.setKundennummer("887051");
        konfig.setLoginName("14137019");
        // Hinterlegtes Passwort: VORHANDEN als Verschluesselt
        konfig.setPasswortVerschluesselt("VERSCHLUESSELT_AES_GCM_BASE64");

        when(konfigService.findOrEmpty(1L)).thenReturn(konfig);

        mockMvc.perform(get("/api/admin/lieferanten/1/ids-konfig"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aktiviert").value(true))
                .andExpect(jsonPath("$.kundennummer").value("887051"))
                .andExpect(jsonPath("$.loginName").value("14137019"))
                // Klartext-Passwort darf NIEMALS im Response landen
                .andExpect(jsonPath("$.passwort").value("********"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getOhneHinterlegtesPasswort_liefertNullStattPlatzhalter() throws Exception {
        LieferantIdsKonfig leer = new LieferantIdsKonfig();
        leer.setProtokoll(IdsProtokoll.IDS_CONNECT_2_5);
        // kein Passwort gesetzt
        when(konfigService.findOrEmpty(1L)).thenReturn(leer);

        mockMvc.perform(get("/api/admin/lieferanten/1/ids-konfig"))
                .andExpect(status().isOk())
                // Frontend kann an "passwort == null" erkennen, dass noch keines hinterlegt ist
                .andExpect(jsonPath("$.passwort").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void putMitAdminRolle_speichertKonfig() throws Exception {
        LieferantIdsKonfig nachSpeichern = new LieferantIdsKonfig();
        nachSpeichern.setAktiviert(true);
        nachSpeichern.setProtokoll(IdsProtokoll.IDS_CONNECT_2_5);
        nachSpeichern.setPunchoutUrl("https://eshop.wuerth.de/IDSInBound");
        nachSpeichern.setKundennummer("887051");
        nachSpeichern.setLoginName("14137019");
        nachSpeichern.setPasswortVerschluesselt("ENCRYPTED");

        when(konfigService.save(eq(1L), any(LieferantIdsKonfigService.IdsKonfigUpdate.class), any()))
                .thenReturn(nachSpeichern);

        String body = "{"
                + "\"aktiviert\":true,"
                + "\"protokoll\":\"IDS_CONNECT_2_5\","
                + "\"punchoutUrl\":\"https://eshop.wuerth.de/IDSInBound\","
                + "\"kundennummer\":\"887051\","
                + "\"loginName\":\"14137019\","
                + "\"passwort\":\"GeheimesPasswort2026\""
                + "}";

        mockMvc.perform(put("/api/admin/lieferanten/1/ids-konfig")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aktiviert").value(true))
                .andExpect(jsonPath("$.passwort").value("********"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void putMitUngueltigerUrl_gibt400() throws Exception {
        // javascript:- oder file:-URLs duerfen nicht gespeichert werden,
        // weil sie spaeter ins Punchout-Form gerendert werden.
        String body = "{"
                + "\"aktiviert\":true,"
                + "\"punchoutUrl\":\"javascript:alert(1)\""
                + "}";
        mockMvc.perform(put("/api/admin/lieferanten/1/ids-konfig")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
