package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.Krankenkasse;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Urlaubsantrag;
import org.example.kalkulationsprogramm.service.UrlaubsantragService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.example.kalkulationsprogramm.config.MobilePrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyLong;
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

    @AfterEach
    void abmelden() {
        SecurityContextHolder.clearContext();
    }

    private static void alsHandy(long mitarbeiterId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new MobilePrincipal(mitarbeiterId), null, List.of()));
    }

    @Test
    void genehmigen_regelverstossLiefert400MitKlartext() throws Exception {
        when(service.approveAntrag(42L)).thenThrow(new IllegalStateException(
                "Für den 2026-08-03 ist noch keine Arbeitszeit hinterlegt. Bitte zuerst Arbeitszeit zuweisen."));

        mockMvc.perform(put("/api/urlaub/antraege/42/approve"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Für den 2026-08-03 ist noch keine Arbeitszeit hinterlegt. Bitte zuerst Arbeitszeit zuweisen."));
    }

    @Test
    void ablehnen_unbekannterAntragLiefert404_erfolgLiefertAntrag() throws Exception {
        when(service.rejectAntrag(Long.MAX_VALUE)).thenThrow(new IllegalArgumentException("Antrag nicht gefunden"));
        Urlaubsantrag abgelehnt = beispielAntrag();
        abgelehnt.setStatus(Urlaubsantrag.Status.ABGELEHNT);
        abgelehnt.setEntscheidungGesehen(false);
        when(service.rejectAntrag(42L)).thenReturn(abgelehnt);

        mockMvc.perform(put("/api/urlaub/antraege/" + Long.MAX_VALUE + "/reject"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Antrag nicht gefunden."));
        mockMvc.perform(put("/api/urlaub/antraege/42/reject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABGELEHNT"))
                .andExpect(jsonPath("$.entscheidungGesehen").value(false));
    }

    @Test
    void storno_amPcNutztBueroStorno_festgeschriebenerMonatLiefert409() throws Exception {
        Urlaubsantrag storniert = beispielAntrag();
        storniert.setStatus(Urlaubsantrag.Status.STORNIERT);
        when(service.stornoAntrag(42L)).thenReturn(storniert);
        when(service.stornoAntrag(43L)).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT,
                "Der Monat 08/2026 ist bereits festgeschrieben. Bitte zuerst den Monatsabschluss zurücksetzen."));

        mockMvc.perform(put("/api/urlaub/antraege/42/storno"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STORNIERT"));
        mockMvc.perform(put("/api/urlaub/antraege/43/storno"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Der Monat 08/2026 ist bereits festgeschrieben. Bitte zuerst den Monatsabschluss zurücksetzen."));
        verify(service, never()).ziehEigenenAntragZurueck(anyLong(), any());
    }

    @Test
    void storno_amHandyNurEigenerOffenerAntrag() throws Exception {
        alsHandy(1L);
        Urlaubsantrag storniert = beispielAntrag();
        storniert.setStatus(Urlaubsantrag.Status.STORNIERT);
        when(service.ziehEigenenAntragZurueck(42L, 1L)).thenReturn(storniert);
        when(service.ziehEigenenAntragZurueck(43L, 1L))
                .thenThrow(new IllegalStateException("Genehmigten Urlaub nimmt das Büro zurück. Bitte dort melden."));
        when(service.ziehEigenenAntragZurueck(44L, 1L))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Zugriff verweigert."));

        mockMvc.perform(put("/api/urlaub/antraege/42/storno"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STORNIERT"));
        mockMvc.perform(put("/api/urlaub/antraege/43/storno"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Genehmigten Urlaub nimmt das Büro zurück. Bitte dort melden."));
        mockMvc.perform(put("/api/urlaub/antraege/44/storno"))
                .andExpect(status().isForbidden());
        verify(service, never()).stornoAntrag(anyLong());
    }

    @Test
    void storno_ungueltigeIdLiefert400() throws Exception {
        mockMvc.perform(put("/api/urlaub/antraege/abc'; DROP TABLE urlaubsantrag; --/storno"))
                .andExpect(status().is4xxClientError());
        verify(service, never()).stornoAntrag(anyLong());
    }

    @Test
    void neueEntscheidungen_amHandyNurEigene() throws Exception {
        alsHandy(1L);
        when(service.neueEntscheidungen(1L)).thenReturn(List.of(new org.example.kalkulationsprogramm.dto.UrlaubsentscheidungDto(
                42L, Urlaubsantrag.Typ.URLAUB, Urlaubsantrag.Status.GENEHMIGT, LocalDate.of(2026, 8, 3),
                LocalDate.of(2026, 8, 14), java.time.LocalDateTime.of(2026, 7, 20, 9, 30))));

        mockMvc.perform(get("/api/urlaub/antraege/neue-entscheidungen").param("mitarbeiterId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(42))
                .andExpect(jsonPath("$[0].status").value("GENEHMIGT"))
                .andExpect(jsonPath("$[0].typ").value("URLAUB"))
                .andExpect(jsonPath("$[0].vonDatum").value("2026-08-03"))
                .andExpect(jsonPath("$[0].mitarbeiter").doesNotExist());
        mockMvc.perform(get("/api/urlaub/antraege/neue-entscheidungen").param("mitarbeiterId", "2"))
                .andExpect(status().isForbidden());
    }

    @Test
    void resturlaub_liefertHalbeTage() throws Exception {
        when(service.getUrlaubskonto(1L, 2026, null)).thenReturn(new org.example.kalkulationsprogramm.service.Urlaubskonto(
                2026, new java.math.BigDecimal("30"), new BigDecimal("3.5"), false, java.time.LocalDate.of(2026, 3, 31), BigDecimal.ZERO,
                new BigDecimal("2.5"), new BigDecimal("21"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("12.5")));

        mockMvc.perform(get("/api/urlaub/resturlaub").param("mitarbeiterId", "1").param("jahr", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verbleibend").value(12.5))
                .andExpect(jsonPath("$.jahr").value(2026))
                .andExpect(jsonPath("$.uebertragVorjahr").value(3.5))
                .andExpect(jsonPath("$.ausVorjahrOffen").value(2.5))
                .andExpect(jsonPath("$.verfallsdatum").value("2026-03-31"));
    }

    @Test
    void resturlaub_mitStichtagUndUngueltigemJahr() throws Exception {
        when(service.getUrlaubskonto(1L, 2027, LocalDate.of(2027, 4, 6))).thenReturn(new org.example.kalkulationsprogramm.service.Urlaubskonto(
                2027, new BigDecimal("30"), new BigDecimal("4"), false, LocalDate.of(2027, 3, 31), new BigDecimal("4"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("30")));

        mockMvc.perform(get("/api/urlaub/resturlaub").param("mitarbeiterId", "1").param("jahr", "2027")
                        .param("stichtag", "2027-04-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verbleibend").value(30))
                .andExpect(jsonPath("$.verfallen").value(4));
        mockMvc.perform(get("/api/urlaub/resturlaub").param("mitarbeiterId", "1").param("jahr", "1999"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/urlaub/resturlaub").param("mitarbeiterId", "1").param("jahr", "2101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/urlaub/resturlaub").param("mitarbeiterId", "1").param("stichtag", "<script>"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resturlaub_ohneVerfallLiefertLeeresVerfallsdatum() throws Exception {
        when(service.getUrlaubskonto(1L, 2026, null)).thenReturn(new org.example.kalkulationsprogramm.service.Urlaubskonto(
                2026, new java.math.BigDecimal("30"), new BigDecimal("4"), true, null, BigDecimal.ZERO, new BigDecimal("4"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("34")));

        mockMvc.perform(get("/api/urlaub/resturlaub").param("mitarbeiterId", "1").param("jahr", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verbleibend").value(34))
                .andExpect(jsonPath("$.verfallsdatum").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void einzelneEntscheidungGesehen_handyEigene204_fremd403_unbekannt404() throws Exception {
        alsHandy(1L);
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Zugriff verweigert."))
                .when(service).eigeneEntscheidungGesehen(43L, 1L);
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Antrag nicht gefunden"))
                .when(service).eigeneEntscheidungGesehen(0L, 1L);

        mockMvc.perform(put("/api/urlaub/antraege/42/entscheidung-gesehen")).andExpect(status().isNoContent());
        mockMvc.perform(put("/api/urlaub/antraege/43/entscheidung-gesehen")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/urlaub/antraege/0/entscheidung-gesehen"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Antrag nicht gefunden."));
        verify(service).eigeneEntscheidungGesehen(42L, 1L);
        verify(service, never()).entscheidungGesehenImBuero(anyLong());
    }

    @Test
    void einzelneEntscheidungGesehen_amPcOhneEinschraenkung() throws Exception {
        mockMvc.perform(put("/api/urlaub/antraege/42/entscheidung-gesehen")).andExpect(status().isNoContent());
        verify(service).entscheidungGesehenImBuero(42L);
        verify(service, never()).eigeneEntscheidungGesehen(anyLong(), any());
    }

    // ==================== Vorschau für die Handy-App (Issue #270) ====================

    @Test
    void vorschau_liefertArbeitstageResturlaubUndReicht() throws Exception {
        when(service.vorschau(1L, LocalDate.of(2027, 3, 3), LocalDate.of(2027, 3, 7)))
                .thenReturn(new org.example.kalkulationsprogramm.service.UrlaubsantragService.UrlaubsVorschau(
                        new BigDecimal("3"), new BigDecimal("12.5"), true));

        mockMvc.perform(get("/api/urlaub/vorschau").param("mitarbeiterId", "1")
                        .param("von", "2027-03-03").param("bis", "2027-03-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arbeitstage").value(3))
                .andExpect(jsonPath("$.verbleibend").value(12.5))
                .andExpect(jsonPath("$.reicht").value(true));
    }

    @Test
    void vorschau_geschaeftsfuehrerOhneKontingentUndFehler() throws Exception {
        when(service.vorschau(1L, LocalDate.of(2027, 3, 5), LocalDate.of(2027, 3, 8)))
                .thenReturn(new org.example.kalkulationsprogramm.service.UrlaubsantragService.UrlaubsVorschau(
                        new BigDecimal("2"), null, true));
        mockMvc.perform(get("/api/urlaub/vorschau").param("mitarbeiterId", "1")
                        .param("von", "2027-03-05").param("bis", "2027-03-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verbleibend").doesNotExist());

        when(service.vorschau(1L, LocalDate.of(2027, 3, 8), LocalDate.of(2027, 3, 5)))
                .thenThrow(new IllegalArgumentException("Das Ende darf nicht vor dem Beginn liegen."));
        mockMvc.perform(get("/api/urlaub/vorschau").param("mitarbeiterId", "1")
                        .param("von", "2027-03-08").param("bis", "2027-03-05"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/urlaub/vorschau").param("mitarbeiterId", "1")
                        .param("von", "'; DROP TABLE urlaubsantrag; --").param("bis", "2027-03-05"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/urlaub/vorschau").param("mitarbeiterId", "1").param("von", "2027-03-05"))
                .andExpect(status().isBadRequest());
    }
}
