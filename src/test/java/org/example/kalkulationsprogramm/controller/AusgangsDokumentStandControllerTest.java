package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.example.kalkulationsprogramm.config.ClientIpConfig;
import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.config.ZeiterfassungSecurityFilter;
import org.example.kalkulationsprogramm.domain.AusgangsDokumentStandArt;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.dto.AusgangsDokumentStand.StandDetailDto;
import org.example.kalkulationsprogramm.dto.AusgangsDokumentStand.StandEintragDto;
import org.example.kalkulationsprogramm.dto.AusgangsDokumentStand.StandSeiteDto;
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentResponseDto;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.dto.AusgangsDokumentStand.StandFesthaltenDto;
import org.example.kalkulationsprogramm.service.dokumentstand.DokumentStandAbfrageService;
import org.example.kalkulationsprogramm.service.dokumentstand.DokumentStandFesthaltenService;
import org.example.kalkulationsprogramm.testsupport.SitzungsRechteImTest;
import org.junit.jupiter.api.BeforeEach;
import org.example.kalkulationsprogramm.service.dokumentstand.DokumentStandWiederherstellenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

/** Verlauf der Dokument-Stände durch die echten Security-Ketten (Anmeldung, CSRF). */
@WebMvcTest(controllers = AusgangsDokumentStandController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ZeiterfassungSecurityFilter.class))
@Import({ SecurityConfig.class, ClientIpConfig.class, AusgangsDokumentStandControllerTest.FilterBeans.class })
class AusgangsDokumentStandControllerTest {

    @TestConfiguration
    static class FilterBeans {
        @Bean CloudflareAccessJwtFilter cloudflareAccessJwtFilter() { return new CloudflareAccessJwtFilter(); }
    }

    private static final String BASIS = "/api/ausgangs-dokumente/7/staende";

    @Autowired MockMvc mvc;
    @MockitoBean FrontendUserDetailsService users;
    @MockitoBean MitarbeiterRepository mitarbeiter;
    @MockitoBean DokumentStandAbfrageService abfrage;
    @MockitoBean DokumentStandWiederherstellenService wiederherstellen;
    @MockitoBean DokumentStandFesthaltenService festhalten;

    @BeforeEach
    void anmeldungBleibtGueltig() {
        SitzungsRechteImTest.rechteUnveraendert(users);
    }

    private static UsernamePasswordAuthenticationToken max() {
        FrontendUserPrincipal p = new FrontendUserPrincipal(42L, "max.mustermann", "Max Mustermann", "hash", true,
                Set.of(FrontendUserRole.USER));
        return new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
    }

    private static StandEintragDto eintrag() {
        return new StandEintragDto(11L, 1, AusgangsDokumentStandArt.EXPORT, LocalDateTime.of(2026, 11, 17, 10, 0),
                "Max Mustermann", List.of("Erika Mustermann"), new BigDecimal("100.00"), List.of("Erste Fassung"),
                null, true, false);
    }

    @Test
    @DisplayName("Liste: Seite mit Ständen und offenen Änderungen")
    void liste() throws Exception {
        given(abfrage.seite(7L, 0, 25)).willReturn(new StandSeiteDto(List.of(eintrag()), 1, 0, 25, false, List.of(), true));
        mvc.perform(get(BASIS).with(authentication(max())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staende[0].nummer").value(1))
                .andExpect(jsonPath("$.staende[0].art").value("EXPORT"))
                .andExpect(jsonPath("$.staende[0].festgehaltenAm").value("2026-11-17T10:00:00"))
                .andExpect(jsonPath("$.staende[0].bearbeitetVon[0]").value("Erika Mustermann"))
                .andExpect(jsonPath("$.staende[0].aktuell").value(true))
                .andExpect(jsonPath("$.staende[0].pdfVorhanden").value(false))
                .andExpect(jsonPath("$.gesamt").value(1))
                .andExpect(jsonPath("$.hatMehr").value(false))
                .andExpect(jsonPath("$.offeneAenderungen").isArray())
                .andExpect(jsonPath("$.dokumentBearbeitbar").value(true));
    }

    @Test
    @DisplayName("Liste: page/size gehen an den Service, der sie begrenzt")
    void listeGrenzenWerdenDurchgereicht() throws Exception {
        given(abfrage.seite(7L, -1, 100000)).willReturn(new StandSeiteDto(List.of(), 0, 0, 50, false, null, false));
        mvc.perform(get(BASIS).param("page", "-1").param("size", "100000").with(authentication(max())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seitenGroesse").value(50))
                .andExpect(jsonPath("$.offeneAenderungen").doesNotExist());
    }

    @Test
    @DisplayName("Ohne Anmeldung: 401")
    void ohneAnmeldung() throws Exception {
        mvc.perform(get(BASIS)).andExpect(status().isUnauthorized());
        mvc.perform(post(BASIS + "/11/wiederherstellen").with(csrf())).andExpect(status().isUnauthorized());
        verify(wiederherstellen, never()).wiederherstellen(anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("Wiederherstellen ohne CSRF-Token: 403")
    void ohneCsrf() throws Exception {
        mvc.perform(post(BASIS + "/11/wiederherstellen").with(authentication(max())))
                .andExpect(status().isForbidden());
        verify(wiederherstellen, never()).wiederherstellen(anyLong(), anyLong(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "abc", "'; DROP TABLE ausgangs_dokument_stand; --", "<script>alert(1)</script>" })
    @DisplayName("Kaputte IDs (SQL-Injection, XSS): 400 bzw. 404 (Schrägstrich trifft keinen Pfad), Service wird nicht gefragt")
    void kaputteIds(String id) throws Exception {
        mvc.perform(get("/api/ausgangs-dokumente/{id}/staende", id).with(authentication(max())))
                .andExpect(status().is4xxClientError());
        mvc.perform(get(BASIS + "/{standId}", id).with(authentication(max())))
                .andExpect(status().is4xxClientError());
        verify(abfrage, never()).seite(anyLong(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
        verify(abfrage, never()).detail(anyLong(), anyLong());
    }

    @Test
    @DisplayName("Detail: Inhalt des Stands; fremder Stand 404 mit Meldung")
    void detail() throws Exception {
        StandDetailDto detail = StandDetailDto.aus(eintrag(), "Geländer", LocalDate.of(2026, 11, 3),
                new BigDecimal("0.19"), 14, null, "{\"blocks\":[]}", "<p>x</p>", true);
        given(abfrage.detail(7L, 11L)).willReturn(detail);
        given(abfrage.detail(7L, 99L)).willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Diesen Stand gibt es nicht."));

        mvc.perform(get(BASIS + "/11").with(authentication(max())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.betreff").value("Geländer"))
                .andExpect(jsonPath("$.datum").value("2026-11-03"))
                .andExpect(jsonPath("$.positionenJson").value("{\"blocks\":[]}"))
                .andExpect(jsonPath("$.nummer").value(1))
                .andExpect(jsonPath("$.dokumentBearbeitbar").value(true));
        mvc.perform(get(BASIS + "/99").with(authentication(max())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Diesen Stand gibt es nicht."));
    }

    @Test
    @DisplayName("PDF: inline als application/pdf; fehlt es, 404")
    void pdf() throws Exception {
        given(abfrage.pdf(7L, 11L)).willReturn(new byte[] { 37, 80, 68, 70 });
        given(abfrage.pdf(7L, 12L)).willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Das PDF dieses Stands ist nicht aufbewahrt."));
        mvc.perform(get(BASIS + "/11/pdf").with(authentication(max())))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        mvc.perform(get(BASIS + "/12/pdf").with(authentication(max())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Wiederherstellen: Benutzer aus der Anmeldung, Antwort ist das Dokument")
    void wiederherstellenOk() throws Exception {
        AusgangsGeschaeftsDokumentResponseDto dto = new AusgangsGeschaeftsDokumentResponseDto();
        dto.setId(7L);
        dto.setBetreff("Früher");
        given(wiederherstellen.wiederherstellen(eq(7L), eq(11L), eq(42L), any())).willReturn(dto);
        mvc.perform(post(BASIS + "/11/wiederherstellen").with(authentication(max())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.betreff").value("Früher"));
    }

    @Test
    @DisplayName("Wiederherstellen: Fehler aus dem Service kommen als {message} mit Status")
    void wiederherstellenFehler() throws Exception {
        given(wiederherstellen.wiederherstellen(eq(7L), eq(12L), eq(42L), any())).willThrow(new ResponseStatusException(
                HttpStatus.CONFLICT, "Dieses Dokument wird gerade von einer anderen Person bearbeitet. "
                        + "Bitte warten, bis sie es schließt."));
        given(wiederherstellen.wiederherstellen(eq(7L), eq(13L), eq(42L), any())).willThrow(new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Dieser Stand ist bereits der aktuelle Inhalt."));
        mvc.perform(post(BASIS + "/12/wiederherstellen").with(authentication(max())).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Dieses Dokument wird gerade")));
        mvc.perform(post(BASIS + "/13/wiederherstellen").with(authentication(max())).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dieser Stand ist bereits der aktuelle Inhalt."));
    }

    @Test
    @DisplayName("Festhalten ohne PDF: Benutzer aus der Anmeldung, Antwort {festgehalten, stand}")
    void festhaltenOhnePdf() throws Exception {
        given(festhalten.festhalten(7L, 42L)).willReturn(new StandFesthaltenDto(true, eintrag()));
        given(festhalten.festhalten(8L, 42L)).willReturn(new StandFesthaltenDto(false, null));
        given(festhalten.festhalten(9L, 42L)).willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Dieses Dokument gibt es nicht."));
        mvc.perform(post(BASIS + "/festhalten").with(authentication(max())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.festgehalten").value(true))
                .andExpect(jsonPath("$.stand.nummer").value(1));
        mvc.perform(post("/api/ausgangs-dokumente/8/staende/festhalten").with(authentication(max())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.festgehalten").value(false))
                .andExpect(jsonPath("$.stand").doesNotExist());
        mvc.perform(post("/api/ausgangs-dokumente/9/staende/festhalten").with(authentication(max())).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post(BASIS + "/festhalten").with(authentication(max())))
                .andExpect(status().isForbidden());
        mvc.perform(post(BASIS + "/festhalten").with(csrf())).andExpect(status().isUnauthorized());
    }
}
