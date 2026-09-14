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
import org.springframework.transaction.PlatformTransactionManager;
import jakarta.persistence.EntityManager;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(MonatsabschlussUebersichtController.class)
@Import({SecurityConfig.class,MonatsabschlussBerechtigungService.class,MonatsabschlussUebersichtService.class,MonatsabschlussSammelService.class,MonatsabschlussUebersichtSecurityTest.Filters.class})
class MonatsabschlussUebersichtSecurityTest {
    @org.springframework.boot.test.context.TestConfiguration static class Filters {
        @org.springframework.context.annotation.Bean CloudflareAccessJwtFilter cloudflareAccessJwtFilter(){return new CloudflareAccessJwtFilter();}
    }
    @Autowired MockMvc mvc;
    @MockBean FrontendUserDetailsService details;
    @MockBean FrontendUserProfileRepository profiles;
    @MockBean MonatsabschlussUebersichtRepository repo;
    @MockBean MonatsSaldoService saldo;
    @MockBean MonatsSaldoRepository salden;
    @MockBean AbwesenheitRepository abwesenheiten;
    @MockBean EntityManager em;
    @MockBean PlatformTransactionManager tm;
    static final String B="/api/zeitverwaltung/monatsabschluesse";
    FrontendUserPrincipal principal() {
        var m=new Mitarbeiter();m.setId(1L);var a=new Abteilung();a.setDarfMonatAbschliessen(true);m.setAbteilungen(Set.of(a));var p=new FrontendUserProfile();p.setMitarbeiter(m);when(profiles.findById(70L)).thenReturn(Optional.of(p));return new FrontendUserPrincipal(70L,"test@example.com","Max Mustermann","",true,Set.of());
    }
    @Test void anonym401() throws Exception { for(String path:List.of("/uebersicht","/vergleich","/jahresvergleich")) mvc.perform(get(B+path).param("jahr","2025").param("monat","1").header("X-Mitarbeiter-Id","1")).andExpect(status().isUnauthorized());mvc.perform(post(B+"/sammelabschluss").with(csrf()).contentType("application/json").content("{\"auswahl\":[]}")).andExpect(status().isUnauthorized()); }
    @Test @WithMockUser void alleEndpunkteVerlangenRecht() throws Exception { for(String path:List.of("/uebersicht","/vergleich","/jahresvergleich"))mvc.perform(get(B+path).param("jahr","2025").param("monat","1")).andExpect(status().isForbidden());mvc.perform(post(B+"/sammelabschluss").with(csrf()).contentType("application/json").content("{\"auswahl\":[]}")).andExpect(status().isForbidden());verifyNoInteractions(repo,saldo,tm); }
    @Test void berechtigterZugriffUndUngueltigeFilter() throws Exception {
        var p=principal();mvc.perform(get(B+"/uebersicht").with(user(p)).param("jahr","2025").param("monat","1")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(B+"/vergleich").with(user(p)).param("jahr","2025").param("monat","1")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(6));
        mvc.perform(get(B+"/jahresvergleich").with(user(p)).param("jahr","2025")).andExpect(status().isOk()).andExpect(jsonPath("$.aktuellesJahr.length()").value(12));
        for(String value:List.of("<script>","'; DROP TABLE x; --","x".repeat(10001))) mvc.perform(get(B+"/uebersicht").with(user(p)).param("jahr","2025").param("monat","1").param("status",value)).andExpect(status().isBadRequest());
        for(String value:List.of("-1","0"))mvc.perform(get(B+"/uebersicht").with(user(p)).param("jahr","2025").param("monat","1").param("mitarbeiterId",value)).andExpect(status().isBadRequest());
        mvc.perform(post(B+"/sammelabschluss").with(user(p)).contentType("application/json").content("{\"auswahl\":[]}")).andExpect(status().isForbidden());
        for(String json:List.of("{\"auswahl\":[]}","{\"auswahl\":[{\"mitarbeiterId\":1,\"jahr\":2025,\"monat\":13}]}"))mvc.perform(post(B+"/sammelabschluss").with(user(p)).with(csrf()).contentType("application/json").content(json)).andExpect(status().isBadRequest());
    }
}
