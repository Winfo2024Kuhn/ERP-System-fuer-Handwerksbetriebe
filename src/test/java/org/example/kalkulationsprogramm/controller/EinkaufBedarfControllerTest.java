package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBedarfService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufZeichnungsbedarfService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.mock.web.MockPart;
import org.springframework.http.MediaType;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EinkaufBedarfController.class)
@Import({SecurityConfig.class, EinkaufBedarfControllerTest.FilterBeans.class})
class EinkaufBedarfControllerTest {
    @TestConfiguration
    static class FilterBeans {
        @Bean CloudflareAccessJwtFilter cloudflareAccessJwtFilter() { return new CloudflareAccessJwtFilter(); }
    }

    @Autowired MockMvc mockMvc;
    @MockBean EinkaufBedarfService bedarfService;
    @MockBean EinkaufBerechtigungService berechtigungService;
    @MockBean EinkaufZeichnungsbedarfService zeichnungsbedarfe;
    @MockBean org.example.kalkulationsprogramm.service.einkauf.EinkaufWerkstattService werkstatt;
    @MockBean org.example.kalkulationsprogramm.service.einkauf.EinkaufPdfService pdf;
    @MockBean org.example.kalkulationsprogramm.service.einkauf.EinkaufBedarfLoeschService loeschService;
    @MockBean FrontendUserDetailsService userDetailsService;

    @Test
    void speichernUebertraegtBeschaffungsdetailsOhneVerlustAnDenService() throws Exception {
        when(berechtigungService.verlange(any(Authentication.class), eq(EinkaufBerechtigung.BEARBEITEN)))
                .thenReturn(7L);
        mockMvc.perform(post("/api/einkauf/bedarf")
                        .with(authentication(7L)).with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                            {"position":{"art":"FREITEXT","bezeichnung":"Testprofil","werkstoff":"S235",
                            "basis":{"menge":2,"einheit":"STUECK"},
                            "beschaffungsdetails":{"lieferantId":7,"kategorieId":8,"schnittbildId":9,
                            "schnittAchseId":10,"externeArtikelnummer":"00012-34"}},
                            "liefergruppe":{"lagerzweck":"Werkstatt"}}
                            """))
                .andExpect(status().isCreated());
        var captured = org.mockito.ArgumentCaptor.forClass(
                org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBedarfDto.Create.class);
        verify(bedarfService).anlegen(captured.capture(), eq(7L));
        org.junit.jupiter.api.Assertions.assertEquals(7L,
                captured.getValue().position().beschaffungsdetails().lieferantId());
        org.junit.jupiter.api.Assertions.assertEquals("00012-34",
                captured.getValue().position().beschaffungsdetails().externeArtikelnummer());
        org.junit.jupiter.api.Assertions.assertEquals("S235", captured.getValue().position().werkstoff());
    }

    @Test
    void anonymerZugriffAufBedarfeIstGesperrt() throws Exception {
        mockMvc.perform(get("/api/einkauf/bedarf")).andExpect(status().isUnauthorized());
        verifyNoInteractions(bedarfService, berechtigungService);
    }

    @Test
    void listeErfordertLeserechtUndGibtEineSeiteZurueck() throws Exception {
        when(berechtigungService.verlange(any(Authentication.class), eq(EinkaufBerechtigung.LESEN))).thenReturn(7L);
        when(bedarfService.suche(eq("Schraube"), eq(42L), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/einkauf/bedarf?q=Schraube&projektId=42")
                        .with(authentication(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(bedarfService).suche(eq("Schraube"), eq(42L), any());
    }

    @Test
    void schreibzugriffErfordertBearbeitungsrecht() throws Exception {
        when(berechtigungService.verlange(any(Authentication.class), eq(EinkaufBerechtigung.BEARBEITEN)))
                .thenThrow(new AccessDeniedException("keine Berechtigung"));

        mockMvc.perform(post("/api/einkauf/bedarf")
                        .with(authentication(7L))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json")
                        .content("{\"position\":null,\"liefergruppe\":null,\"artikelInProjektId\":null}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(bedarfService);
    }

    @Test
    void validierungsfehlerHabenFieldErrorsUndHttp400() throws Exception {
        when(berechtigungService.verlange(any(Authentication.class), eq(EinkaufBerechtigung.BEARBEITEN)))
                .thenReturn(7L);
        doThrow(new IllegalArgumentException("Bitte geben Sie die Liefergruppe an."))
                .when(bedarfService).anlegen(any(), eq(7L));

        mockMvc.perform(post("/api/einkauf/bedarf")
                        .with(authentication(7L))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json")
                        .content("{\"position\":null,\"liefergruppe\":null,\"artikelInProjektId\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Bitte geben Sie die Liefergruppe an."))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("request"));
    }

    @Test
    void zeichnungsteilErstanlageErfordertBearbeitungsrechtUndMultipartDatei() throws Exception {
        when(berechtigungService.verlange(any(Authentication.class), eq(EinkaufBerechtigung.BEARBEITEN))).thenReturn(7L);
        when(zeichnungsbedarfe.anlegen(any(), any(), eq("B"), eq(7L))).thenReturn(null);
        MockPart bedarf = new MockPart("bedarf", "{\"position\":{\"art\":\"ZEICHNUNGSTEIL\",\"interneReferenz\":\"ZT-4\",\"zeichnungsnummer\":\"Z-4\",\"zeichnungsrevision\":\"B\",\"bezeichnung\":\"Träger\",\"basis\":{\"menge\":2,\"einheit\":\"STUECK\",\"stueckzahl\":2},\"dokumente\":[],\"anlageVersionIds\":[]},\"liefergruppe\":{\"projektId\":9}}".getBytes());
        bedarf.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/einkauf/bedarf/zeichnungsteil")
                        .file("datei", "%PDF-1.7 Dummy".getBytes()).part(bedarf).param("revision", "B")
                        .with(authentication(7L)).with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isCreated());
        verify(zeichnungsbedarfe).anlegen(any(), any(), eq("B"), eq(7L));
    }

    @Test
    void werkstattpruefungErfordertCsrfUndBearbeitungsrecht() throws Exception {
        String payload = "{\"positionen\":[{\"bedarfId\":1,\"version\":0,\"vorhanden\":4}]}";
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/einkauf/bedarf/werkstattpruefung")
                .with(authentication(7L)).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden());
        verifyNoInteractions(werkstatt);
        when(berechtigungService.verlange(any(), eq(EinkaufBerechtigung.BEARBEITEN))).thenReturn(7L);
        when(werkstatt.pruefen(any(), eq(7L))).thenReturn(java.util.List.of());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/einkauf/bedarf/werkstattpruefung")
                .with(authentication(7L)).with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        verify(werkstatt).pruefen(any(), eq(7L));
    }

    @Test
    void ohneProjektFilterWirdExplizitWeitergegeben() throws Exception {
        when(bedarfService.suche(eq(null), eq(null), eq(true), any())).thenReturn(Page.empty());
        mockMvc.perform(get("/api/einkauf/bedarf?ohneProjekt=true").with(authentication(7L)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
        verify(bedarfService).suche(eq(null), eq(null), eq(true), any());
    }

    @Test
    void bedarfslisteIstGeschuetzterPdfDownload() throws Exception {
        when(pdf.bedarfsliste(java.util.List.of(1L, 2L)))
                .thenReturn(new org.springframework.core.io.ByteArrayResource("%PDF-Dummy".getBytes()));
        mockMvc.perform(get("/api/einkauf/bedarf/pdf?bedarfIds=1,2").with(authentication(7L)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_PDF));
        verify(berechtigungService).verlange(any(), eq(EinkaufBerechtigung.LESEN));
        mockMvc.perform(get("/api/einkauf/bedarf/pdf?bedarfIds=1,2")).andExpect(status().isUnauthorized());
    }

    @Test
    void bedarfslisteAkzeptiertIdsImPostBodyOhneLangeUrl() throws Exception {
        when(pdf.bedarfsliste(java.util.List.of(1L,2L)))
                .thenReturn(new org.springframework.core.io.ByteArrayResource("%PDF-Dummy".getBytes()));
        mockMvc.perform(post("/api/einkauf/bedarf/pdf").with(authentication(7L))
                .with(SecurityMockMvcRequestPostProcessors.csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bedarfIds\":[1,2]}"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_PDF));
        verify(berechtigungService).verlange(any(),eq(EinkaufBerechtigung.LESEN));
    }

    @Test
    void loeschenMitVersionErfordertCsrfUndBearbeitungsrecht() throws Exception {
        mockMvc.perform(delete("/api/einkauf/bedarf/5?version=3").with(authentication(7L)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(loeschService);
        when(berechtigungService.verlange(any(), eq(EinkaufBerechtigung.BEARBEITEN))).thenReturn(7L);
        mockMvc.perform(delete("/api/einkauf/bedarf/5?version=3").with(authentication(7L))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNoContent());
        verify(loeschService).loeschen(5L, 3L, 7L);
    }

    @Test
    void loeschenOhneBearbeitungsrechtIstVerboten() throws Exception {
        when(berechtigungService.verlange(any(), eq(EinkaufBerechtigung.BEARBEITEN)))
                .thenThrow(new AccessDeniedException("keine Berechtigung"));
        mockMvc.perform(delete("/api/einkauf/bedarf/5?version=3").with(authentication(7L))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(loeschService);
    }

    @Test
    void loeschenWeiterverarbeitetOderVeraltetLiefert409MitGrund() throws Exception {
        when(berechtigungService.verlange(any(), eq(EinkaufBerechtigung.BEARBEITEN))).thenReturn(7L);
        doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                "Der Bedarf ist bereits in Preisanfrage PA-2026-0001 enthalten und kann nicht gelöscht werden."))
                .when(loeschService).loeschen(5L, 3L, 7L);
        mockMvc.perform(delete("/api/einkauf/bedarf/5?version=3").with(authentication(7L))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Der Bedarf ist bereits in Preisanfrage PA-2026-0001 enthalten und kann nicht gelöscht werden."));
    }

    @Test
    void loeschenMitUngueltigenIdsOderOhneVersion() throws Exception {
        when(berechtigungService.verlange(any(), eq(EinkaufBerechtigung.BEARBEITEN))).thenReturn(7L);
        mockMvc.perform(delete("/api/einkauf/bedarf/5").with(authentication(7L))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isBadRequest());
        for (long id : new long[] {0L, -1L}) {
            doThrow(new IllegalArgumentException("Der Bedarf oder die Versionsangabe ist ungültig."))
                    .when(loeschService).loeschen(id, 0L, 7L);
            mockMvc.perform(delete("/api/einkauf/bedarf/" + id + "?version=0").with(authentication(7L))
                            .with(SecurityMockMvcRequestPostProcessors.csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Der Bedarf oder die Versionsangabe ist ungültig."));
        }
        doThrow(new org.example.kalkulationsprogramm.exception.NotFoundException("Der Einkaufsbedarf wurde nicht gefunden."))
                .when(loeschService).loeschen(Long.MAX_VALUE, 0L, 7L);
        mockMvc.perform(delete("/api/einkauf/bedarf/" + Long.MAX_VALUE + "?version=0").with(authentication(7L))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/einkauf/bedarf/{id}?version=0", "1 OR 1=1").with(authentication(7L))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isBadRequest());
    }

    private static RequestPostProcessor authentication(long id) {
        FrontendUserPrincipal principal = new FrontendUserPrincipal(id, "test@example.com", "Max Mustermann",
                "{noop}dummy", true, Set.of(FrontendUserRole.USER));
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, principal.getPassword(),
                principal.getAuthorities());
        return SecurityMockMvcRequestPostProcessors.authentication(auth);
    }
}
