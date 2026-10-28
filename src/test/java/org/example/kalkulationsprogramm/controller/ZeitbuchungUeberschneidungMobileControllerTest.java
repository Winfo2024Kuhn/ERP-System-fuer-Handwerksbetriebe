package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.example.kalkulationsprogramm.config.MobilePrincipal;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.UeberschneidungErgebnis;
import org.example.kalkulationsprogramm.domain.ZeitbuchungUeberschneidung;
import org.example.kalkulationsprogramm.service.ZeitbuchungUeberschneidungService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ZeitbuchungUeberschneidungMobileController.class)
@AutoConfigureMockMvc(addFilters = false)
class ZeitbuchungUeberschneidungMobileControllerTest {

    private static final Long MAX_ID = 7L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ZeitbuchungUeberschneidungService service;

    @AfterEach
    void aufraeumen() {
        SecurityContextHolder.clearContext();
    }

    private static void alsHandy(Long mitarbeiterId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new MobilePrincipal(mitarbeiterId), null, List.of()));
    }

    private static ZeitbuchungUeberschneidung meldung(String bezeichnung) {
        Mitarbeiter max = new Mitarbeiter();
        max.setId(MAX_ID);
        max.setVorname("Max");
        max.setNachname("Mustermann");
        ZeitbuchungUeberschneidung u = new ZeitbuchungUeberschneidung();
        u.setId(3L);
        u.setMitarbeiter(max);
        u.setHandyVon(LocalDateTime.of(2026, 10, 28, 10, 0));
        u.setHandyBis(LocalDateTime.of(2026, 10, 28, 12, 0));
        u.setBueroVon(LocalDateTime.of(2026, 10, 28, 9, 0));
        u.setBueroBis(LocalDateTime.of(2026, 10, 28, 11, 0));
        u.setBueroBezeichnung(bezeichnung);
        u.setErgebnis(UeberschneidungErgebnis.GEKUERZT);
        u.setErstelltAm(LocalDateTime.of(2026, 10, 28, 14, 3));
        u.setGeaendertAm(LocalDateTime.of(2026, 10, 28, 14, 3));
        return u;
    }

    @Test
    void listeLiefertEigeneUngeleseneMeldungen() throws Exception {
        alsHandy(MAX_ID);
        ZeitbuchungUeberschneidung u = meldung("<script>alert(1)</script>");
        when(service.ungeleseneFuerMitarbeiter(MAX_ID)).thenReturn(List.of(u));
        when(service.anzeigeFuerMitarbeiter(u)).thenReturn(
                new ZeitbuchungUeberschneidungService.Anzeige("Überschneidung am 28.10.2026", "Text"));

        mockMvc.perform(get("/api/zeiterfassung/ueberschneidungen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].von").value("2026-10-28T10:00"))
                .andExpect(jsonPath("$[0].bis").value("2026-10-28T12:00"))
                .andExpect(jsonPath("$[0].titel").value("Überschneidung am 28.10.2026"))
                .andExpect(jsonPath("$[0].text").value("Text"))
                .andExpect(jsonPath("$[0].erstelltAm").value("2026-10-28T14:03"));
    }

    @Test
    void laufendeHandyBuchungHatKeinEnde() throws Exception {
        alsHandy(MAX_ID);
        ZeitbuchungUeberschneidung u = meldung("Musterhaus · Montage");
        u.setHandyBis(null);
        when(service.ungeleseneFuerMitarbeiter(MAX_ID)).thenReturn(List.of(u));
        when(service.anzeigeFuerMitarbeiter(u)).thenReturn(new ZeitbuchungUeberschneidungService.Anzeige("t", "x"));

        mockMvc.perform(get("/api/zeiterfassung/ueberschneidungen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bis").isEmpty());
    }

    @Test
    void ohneHandyAnmeldungGibtEs401() throws Exception {
        mockMvc.perform(get("/api/zeiterfassung/ueberschneidungen"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/zeiterfassung/ueberschneidungen/3/gesehen"))
                .andExpect(status().isUnauthorized());
        verify(service, never()).ungeleseneFuerMitarbeiter(anyLong());
    }

    @Test
    void gesehenMarkiertNurEigeneMeldung() throws Exception {
        alsHandy(MAX_ID);
        when(service.alsGesehenMarkieren(3L, MAX_ID)).thenReturn(true);

        mockMvc.perform(put("/api/zeiterfassung/ueberschneidungen/3/gesehen"))
                .andExpect(status().isNoContent());
        verify(service).alsGesehenMarkieren(3L, MAX_ID);
    }

    @Test
    void fremdeOderUnbekannteMeldungIst404() throws Exception {
        alsHandy(MAX_ID);
        when(service.alsGesehenMarkieren(eq(99L), eq(MAX_ID))).thenReturn(false);

        mockMvc.perform(put("/api/zeiterfassung/ueberschneidungen/99/gesehen"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/zeiterfassung/ueberschneidungen/" + Long.MAX_VALUE + "/gesehen"))
                .andExpect(status().isNotFound());
    }

    @Test
    void ungueltigeIdsWerdenAbgelehnt() throws Exception {
        alsHandy(MAX_ID);
        mockMvc.perform(put("/api/zeiterfassung/ueberschneidungen/0/gesehen"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/zeiterfassung/ueberschneidungen/-1/gesehen"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/zeiterfassung/ueberschneidungen/1';DROP TABLE x;--/gesehen"))
                .andExpect(status().is4xxClientError());
        verify(service, never()).alsGesehenMarkieren(eq(0L), anyLong());
        verify(service, never()).alsGesehenMarkieren(eq(-1L), anyLong());
    }
}
