package org.example.kalkulationsprogramm.config;

import org.example.kalkulationsprogramm.controller.BelegController;
import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegAufteilungsModus;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.dto.BelegDto;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.service.BelegService;
import org.example.kalkulationsprogramm.service.BelegSplitService;
import org.example.kalkulationsprogramm.service.KassenbuchungService;
import org.example.kalkulationsprogramm.service.MwstRechnerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Produktive Filter, Controller und Service-Objektprüfung; Persistenz und DTO-Anreicherung sind isoliert. */
@WebMvcTest(controllers = BelegController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ZeiterfassungSecurityFilter.class))
@Import({SecurityConfig.class, MobileBelegSecurityTest.FilterBeans.class})
class MobileBelegSecurityTest {
    private static final String TOKEN = "12345678-1234-4234-8234-123456789abc";
    @Autowired private MockMvc mvc;
    @MockBean private FrontendUserDetailsService users;
    @MockBean private MitarbeiterRepository mitarbeiter;
    @MockBean private BelegRepository belege;
    @MockBean private BelegSplitService split;
    @MockBean private MwstRechnerService mwst;
    @MockBean private KassenbuchungService kasse;
    @MockBean(answer = Answers.CALLS_REAL_METHODS) private BelegService service;
    private Mitarbeiter caller;

    @TestConfiguration
    static class FilterBeans {
        @Bean CloudflareAccessJwtFilter cloudflareAccessJwtFilter() { return new CloudflareAccessJwtFilter(); }
    }

    @BeforeEach
    void vorbereiten() {
        caller = new Mitarbeiter();
        caller.setId(7L);
        caller.setAktiv(true);
        when(mitarbeiter.findByLoginTokenAndAktivTrue(TOKEN)).thenReturn(Optional.of(caller));
        ReflectionTestUtils.setField(service, "belegRepository", belege);
        ReflectionTestUtils.setField(service, "belegSplitService", split);
        doReturn(caller).when(service).findCaller(eq(TOKEN), any());
        doReturn(caller).when(service).findCaller(isNull(), any());
        doReturn(true).when(service).darfSehen(caller);
        doReturn(true).when(service).darfScannen(caller);
        doReturn(BelegDto.PermissionResponse.builder().darfSehen(true).darfScannen(true).build())
                .when(service).getPermissions(caller);
        doAnswer(invocation -> {
            Beleg beleg = invocation.getArgument(0);
            return BelegDto.Response.builder().id(beleg.getId())
                    .uploadedById(beleg.getUploadedBy() == null ? null : beleg.getUploadedBy().getId()).build();
        }).when(service).toDto(any(Beleg.class), anyBoolean());
        when(belege.findById(40L)).thenReturn(Optional.of(beleg(40L, 7L)));
        when(belege.findById(41L)).thenReturn(Optional.of(beleg(41L, 8L)));
        when(belege.findById(42L)).thenReturn(Optional.of(beleg(42L, null)));
        when(split.aktualisiereAuswahl(anyLong(), anySet())).thenAnswer(invocation ->
                belege.findById(invocation.getArgument(0)).orElseThrow());
    }

    private Beleg beleg(long id, Long ownerId) {
        Beleg beleg = new Beleg();
        beleg.setId(id);
        if (ownerId != null) {
            Mitarbeiter owner = new Mitarbeiter();
            owner.setId(ownerId);
            beleg.setUploadedBy(owner);
        }
        return beleg;
    }

    @Test
    void eigenerBelegIstMitHeaderTokenLesbarUndBearbeitbar() throws Exception {
        mvc.perform(get("/api/buchhaltung/mobile/belege/40").header("X-Auth-Token", TOKEN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.uploadedById").value(7));
        mvc.perform(put("/api/buchhaltung/mobile/belege/40/positionen").header("X-Auth-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"firmaPositionIds\":[12]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(40));
    }

    @ParameterizedTest
    @ValueSource(longs = {41, 42})
    void fremdeUndNichtZugeordneteBelegeKoennenNichtGelesenOderGeaendertWerden(long id) throws Exception {
        mvc.perform(get("/api/buchhaltung/mobile/belege/" + id).header("X-Auth-Token", TOKEN))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/buchhaltung/mobile/belege/" + id + "/positionen").header("X-Auth-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"firmaPositionIds\":[12]}"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(split);
    }

    @Test
    void fehlendeBelegrechteSperrenAuchEigeneObjekte() throws Exception {
        doReturn(false).when(service).darfSehen(caller);
        doReturn(false).when(service).darfScannen(caller);
        mvc.perform(get("/api/buchhaltung/mobile/belege/40").header("X-Auth-Token", TOKEN))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/buchhaltung/mobile/belege/40/positionen").header("X-Auth-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"firmaPositionIds\":[12]}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(belege, split);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 43, Long.MAX_VALUE})
    void nichtVorhandeneIdsErlaubenWederLesenNochPositionsaenderung(long id) throws Exception {
        mvc.perform(get("/api/buchhaltung/mobile/belege/" + id).header("X-Auth-Token", TOKEN))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/buchhaltung/mobile/belege/" + id + "/positionen").header("X-Auth-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"firmaPositionIds\":[12]}"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(split);
    }

    @Test
    void rechteEndpointBrauchtGueltigeAnmeldung() throws Exception {
        mvc.perform(get("/api/buchhaltung/mobile/me/permissions"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/buchhaltung/mobile/me/permissions").header("X-Auth-Token", TOKEN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.darfSehen").value(true))
                .andExpect(jsonPath("$.darfScannen").value(true));
    }

    @Test
    void uploadUndListeNutzenDenTokenInhaber() throws Exception {
        when(belege.findTop20ByUploadedByOrderByUploadDatumDesc(caller)).thenReturn(List.of(beleg(40L, 7L)));
        doReturn(beleg(40L, 7L)).when(service).uploadBeleg(any(), isNull(), eq(BelegAufteilungsModus.VOLLSTAENDIG), eq(caller));
        mvc.perform(multipart("/api/buchhaltung/mobile/belege")
                        .file(new MockMultipartFile("datei", "test.pdf", "application/pdf", new byte[]{1, 2}))
                        .param("uploadedById", "8").header("X-Auth-Token", TOKEN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.uploadedById").value(7));
        mvc.perform(get("/api/buchhaltung/mobile/belege").header("X-Auth-Token", TOKEN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].uploadedById").value(7));
        verify(service).uploadBeleg(any(), isNull(), eq(BelegAufteilungsModus.VOLLSTAENDIG), eq(caller));
        verify(belege).findTop20ByUploadedByOrderByUploadDatumDesc(caller);
    }

    @Test
    @WithMockUser
    void desktopDarfFremdeBelegeWeiterBearbeitenAberMobileErbtDieseRechteNicht() throws Exception {
        mvc.perform(get("/api/buchhaltung/belege/41"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.uploadedById").value(8));
        mvc.perform(put("/api/buchhaltung/belege/41/positionen").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"firmaPositionIds\":[12]}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/buchhaltung/mobile/belege/41").header("X-Auth-Token", TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void tokenAlleinErschliesstKeinenDesktopDateipfad() throws Exception {
        mvc.perform(get("/api/buchhaltung/belege/41/datei").header("X-Auth-Token", TOKEN))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/buchhaltung/mobile/belege/41/datei").header("X-Auth-Token", TOKEN))
                .andExpect(status().isForbidden());
        verifyNoInteractions(belege);
    }
}
