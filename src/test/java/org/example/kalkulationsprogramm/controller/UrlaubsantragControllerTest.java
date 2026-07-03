package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.Krankenkasse;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Urlaubsantrag;
import org.example.kalkulationsprogramm.service.UrlaubsantragService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UrlaubsantragController.class)
@AutoConfigureMockMvc(addFilters = false)
class UrlaubsantragControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UrlaubsantragService service;

    /**
     * Mitarbeiter mit befüllten sensiblen Feldern – im JSON der Urlaubs-API
     * dürfen davon nur id, vorname, nachname und jahresUrlaub ankommen.
     */
    private Urlaubsantrag beispielAntrag() {
        Mitarbeiter mitarbeiter = new Mitarbeiter();
        mitarbeiter.setId(1L);
        mitarbeiter.setVorname("Max");
        mitarbeiter.setNachname("Mustermann");
        mitarbeiter.setJahresUrlaub(30);
        mitarbeiter.setStundenlohn(new BigDecimal("25.50"));
        mitarbeiter.setGeburtstag(LocalDate.of(1990, 1, 1));
        mitarbeiter.setLoginToken("geheimer-token");
        mitarbeiter.setKrankenkasse(new Krankenkasse());

        Urlaubsantrag antrag = new Urlaubsantrag();
        antrag.setId(42L);
        antrag.setMitarbeiter(mitarbeiter);
        antrag.setVonDatum(LocalDate.of(2026, 8, 3));
        antrag.setBisDatum(LocalDate.of(2026, 8, 14));
        antrag.setBemerkung("Sommerurlaub");
        antrag.setTyp(Urlaubsantrag.Typ.URLAUB);
        antrag.setStatus(Urlaubsantrag.Status.OFFEN);
        return antrag;
    }

    @Test
    void createAntragLiefert200MitGefiltertemMitarbeiter() throws Exception {
        when(service.createAntrag(eq(1L), any(), any(), any(), eq(Urlaubsantrag.Typ.URLAUB)))
                .thenReturn(beispielAntrag());

        mockMvc.perform(post("/api/urlaub/antraege")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mitarbeiterId":1,"von":"2026-08-03","bis":"2026-08-14",
                                 "bemerkung":"Sommerurlaub","typ":"URLAUB"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.status").value("OFFEN"))
                .andExpect(jsonPath("$.mitarbeiter.id").value(1))
                .andExpect(jsonPath("$.mitarbeiter.vorname").value("Max"))
                .andExpect(jsonPath("$.mitarbeiter.nachname").value("Mustermann"))
                .andExpect(jsonPath("$.mitarbeiter.jahresUrlaub").value(30))
                // Der Bug: krankenkasse (Lazy-Proxy) crashte die Serialisierung → 500.
                // Sensible Felder dürfen die Urlaubs-API nie verlassen (DSGVO).
                .andExpect(jsonPath("$.mitarbeiter.krankenkasse").doesNotExist())
                .andExpect(jsonPath("$.mitarbeiter.stundenlohn").doesNotExist())
                .andExpect(jsonPath("$.mitarbeiter.geburtstag").doesNotExist())
                .andExpect(jsonPath("$.mitarbeiter.loginToken").doesNotExist());
    }

    @Test
    void createAntragLiefert400MitFehlermeldungBeiUeberlappung() throws Exception {
        when(service.createAntrag(eq(1L), any(), any(), any(), eq(Urlaubsantrag.Typ.URLAUB)))
                .thenThrow(new IllegalStateException(
                        "Es existiert bereits ein URLAUB-Antrag vom 2026-08-03 bis 2026-08-14 in diesem Zeitraum"));

        mockMvc.perform(post("/api/urlaub/antraege")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mitarbeiterId":1,"von":"2026-08-03","bis":"2026-08-14",
                                 "bemerkung":"Dublette","typ":"URLAUB"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "Es existiert bereits ein URLAUB-Antrag vom 2026-08-03 bis 2026-08-14 in diesem Zeitraum"));
    }

    @Test
    void getAntraegeLiefertOffeneAntraegeMitGefiltertemMitarbeiter() throws Exception {
        when(service.getOffeneAntraege()).thenReturn(List.of(beispielAntrag()));

        mockMvc.perform(get("/api/urlaub/antraege"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(42))
                .andExpect(jsonPath("$[0].vonDatum").value("2026-08-03"))
                .andExpect(jsonPath("$[0].bisDatum").value("2026-08-14"))
                .andExpect(jsonPath("$[0].mitarbeiter.nachname").value("Mustermann"))
                .andExpect(jsonPath("$[0].mitarbeiter.krankenkasse").doesNotExist())
                .andExpect(jsonPath("$[0].mitarbeiter.stundenlohn").doesNotExist());
    }
}
