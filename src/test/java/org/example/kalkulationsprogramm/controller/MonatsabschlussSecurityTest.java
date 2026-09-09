package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.config.*;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.*;
import org.example.kalkulationsprogramm.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {MonatsabschlussController.class, AbteilungBerechtigungController.class})
@Import({SecurityConfig.class, MonatsabschlussBerechtigungService.class, MonatsabschlussSecurityTest.Filters.class})
class MonatsabschlussSecurityTest {
    @org.springframework.boot.test.context.TestConfiguration
    static class Filters {
        @org.springframework.context.annotation.Bean CloudflareAccessJwtFilter cloudflareAccessJwtFilter() { return new CloudflareAccessJwtFilter(); }
    }
    @Autowired MockMvc mvc;
    @MockBean FrontendUserDetailsService frontendUserDetailsService;
    @MockBean FrontendUserProfileRepository profiles;
    @MockBean MonatsSaldoService saldo;
    @MockBean AbteilungRepository abteilungen;
    @MockBean AbteilungDokumentBerechtigungRepository dokumentRechte;
    static final String BASE = "/api/zeitverwaltung/monatsabschluesse";

    @Test void anonymUndGefälschterHeaderErhaltenKeinenZugriff() throws Exception {
        mvc.perform(get(BASE + "/berechtigung").header("X-Mitarbeiter-Id", "1")).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/1/2025/1/abschliessen").with(csrf()).header("X-Frontend-User-Id", "1")).andExpect(status().isUnauthorized());
        verifyNoInteractions(saldo, profiles);
    }
    @Test @WithMockUser(roles="USER") void rechteOhneMitarbeiterIdUndStatusOhneAbschlussrecht() throws Exception {
        mvc.perform(get(BASE + "/berechtigung")).andExpect(status().isOk()).andExpect(jsonPath("$.darfMonatAbschliessen").value(false));
        mvc.perform(get(BASE + "/1/2025/1")).andExpect(status().isOk());
        verify(saldo).status(1L, 2025, 1);
    }
    @Test void rechteAusEchtemSessionPrincipal() throws Exception {
        var m = new Mitarbeiter(); m.setId(9L);
        var a = new Abteilung(); a.setDarfMonatAbschliessen(true); m.setAbteilungen(Set.of(a));
        var p = new FrontendUserProfile(); p.setMitarbeiter(m);
        when(profiles.findById(70L)).thenReturn(Optional.of(p));
        var principal = new FrontendUserPrincipal(70L, "test@example.com", "Max Mustermann", "", true, Set.of());
        mvc.perform(get(BASE + "/berechtigung").with(user(principal)).param("mitarbeiterId", "123"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.darfMonatAbschliessen").value(true));
        verify(profiles).findById(70L);
    }
    @Test @WithMockUser(roles="ADMIN") void csrfIstPflichtAuchFuerAdmin() throws Exception {
        mvc.perform(post(BASE + "/1/2025/1/abschliessen")).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/1/2025/1/oeffnen")).andExpect(status().isForbidden());
        mvc.perform(put("/api/abteilungen/1/berechtigungen").contentType("application/json").content("{\"darfMonatAbschliessen\":true}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(saldo, abteilungen);
    }
    @Test @WithMockUser(roles="USER") void berechtigungsPutIstAdministrativGeschuetzt() throws Exception {
        mvc.perform(put("/api/abteilungen/1/berechtigungen").with(csrf()).contentType("application/json").content("{\"darfMonatAbschliessen\":true}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(abteilungen);
    }
    @Test @WithMockUser(roles="ADMIN") void adminKannAbschlussflagExplizitSetzen() throws Exception {
        var a = new Abteilung(); a.setId(1L); a.setName("Testabteilung");
        when(abteilungen.findById(1L)).thenReturn(Optional.of(a));
        mvc.perform(put("/api/abteilungen/1/berechtigungen").with(csrf()).contentType("application/json").content("{\"darfMonatAbschliessen\":true}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.darfMonatAbschliessen").value(true));
        verify(abteilungen).save(a);
    }
}
