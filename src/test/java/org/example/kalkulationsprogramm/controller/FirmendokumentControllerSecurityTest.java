package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentAendernDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentOrdnerAnlegenDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentOrdnerDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentSeiteDto;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.service.firmendokumente.FirmendokumentOrdnerService;
import org.example.kalkulationsprogramm.service.firmendokumente.FirmendokumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

/**
 * Firmendokumente sind Stammdaten für alle: Jeder angemeldete PC-Benutzer (auch ohne Admin-Rolle) darf
 * lesen und ändern – mit CSRF. Ohne Anmeldung 401, ohne CSRF 403, unbekannte IDs 404, kaputte IDs 400.
 */
@WebMvcTest(controllers = { FirmendokumentController.class, FirmendokumentOrdnerController.class })
@Import({ SecurityConfig.class, FirmendokumentControllerSecurityTest.EchteFilterBeans.class })
class FirmendokumentControllerSecurityTest {

    @TestConfiguration
    static class EchteFilterBeans {
        @Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    private static final String SQL = "'; DROP TABLE firmendokument; --";
    private static final String XSS = "<script>alert(1)</script>";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private FirmendokumentService service;
    @MockitoBean private FirmendokumentOrdnerService ordnerService;
    @MockitoBean private FrontendUserDetailsService frontendUserDetailsService;
    @MockitoBean private FrontendUserProfileRepository frontendUserProfileRepository;

    @TempDir Path tmp;

    private final FrontendUserPrincipal max = new FrontendUserPrincipal(70L, "max@example.com", "Max Mustermann", "", true,
            Set.of(FrontendUserRole.USER));

    private static FirmendokumentDto dto(String name) {
        return new FirmendokumentDto(7L, 1L, name, "scan.pdf", 1234L, LocalDateTime.of(2026, 11, 2, 9, 0),
                "Max Mustermann", LocalDate.of(2027, 10, 31), "/api/firmendokumente/7/datei");
    }

    @BeforeEach
    void setUp() {
        given(frontendUserDetailsService.aktuellerStand(any())).willAnswer(i -> Optional.of(i.getArgument(0)));
    }

    @Test
    @DisplayName("Ohne Anmeldung: 401 auf Ordner, Liste, Datei und Hochladen")
    void ohneAnmeldung() throws Exception {
        mockMvc.perform(get("/api/firmendokumente/ordner")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/firmendokumente").param("ordnerId", "1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/firmendokumente/7/datei")).andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/firmendokumente").file(new MockMultipartFile("datei", "a.pdf", "application/pdf",
                "%PDF-".getBytes(StandardCharsets.US_ASCII))).with(csrf())).andExpect(status().isUnauthorized());
        verifyNoInteractions(service, ordnerService);
    }

    @Test
    @DisplayName("Normaler PC-Benutzer (kein Admin) liest Ordner, Liste, Suche und Fälliges")
    void lesenOhneAdmin() throws Exception {
        given(ordnerService.liste()).willReturn(List.of(new FirmendokumentOrdnerDto(1L, "Prüfungen", null, 2, 1, 0)));
        given(service.imOrdner(1L, 0, 50)).willReturn(new FirmendokumentSeiteDto(List.of(dto(XSS)), 1, 0, 50));
        given(service.suche(SQL, 0, 50)).willReturn(new FirmendokumentSeiteDto(List.of(), 0, 0, 50));
        given(service.faellig(-1, 100_000)).willReturn(new FirmendokumentSeiteDto(List.of(), 0, 0, 50));

        mockMvc.perform(get("/api/firmendokumente/ordner").with(user(max))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Prüfungen"))
                .andExpect(jsonPath("$[0].anzahlUeberfaellig").value(1));
        mockMvc.perform(get("/api/firmendokumente").param("ordnerId", "1").with(user(max))).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.eintraege[0].name").value(XSS))
                .andExpect(jsonPath("$.eintraege[0].faelligAm").value("2027-10-31"))
                .andExpect(jsonPath("$.gesamt").value(1));
        mockMvc.perform(get("/api/firmendokumente/suche").param("q", SQL).with(user(max))).andExpect(status().isOk());
        mockMvc.perform(get("/api/firmendokumente/faellig").param("page", "-1").param("size", "100000").with(user(max)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Ändern ohne CSRF: 403, Dienst wird nicht aufgerufen")
    void ohneCsrf() throws Exception {
        mockMvc.perform(post("/api/firmendokumente/ordner").with(user(max)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Tore\"}")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/firmendokumente/7").with(user(max)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\",\"ordnerId\":1}")).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/firmendokumente/7").with(user(max))).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/firmendokumente/ordner/1").with(user(max))).andExpect(status().isForbidden());
        verifyNoInteractions(service, ordnerService);
    }

    @Test
    @DisplayName("Normaler PC-Benutzer legt Ordner an, benennt um, verschiebt und löscht")
    void ordnerBearbeiten() throws Exception {
        given(ordnerService.anlegen(new FirmendokumentOrdnerAnlegenDto(XSS, 1L)))
                .willReturn(new FirmendokumentOrdnerDto(9L, XSS, 1L, 0, 0, 0));
        given(ordnerService.umbenennen(9L, SQL)).willReturn(new FirmendokumentOrdnerDto(9L, SQL, 1L, 0, 0, 0));
        given(ordnerService.verschieben(9L, null)).willReturn(new FirmendokumentOrdnerDto(9L, SQL, null, 0, 0, 0));

        mockMvc.perform(post("/api/firmendokumente/ordner").with(user(max)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"<script>alert(1)</script>\",\"parentId\":1}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(9));
        mockMvc.perform(put("/api/firmendokumente/ordner/9").with(user(max)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"'; DROP TABLE firmendokument; --\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value(SQL));
        mockMvc.perform(put("/api/firmendokumente/ordner/9/verschieben").with(user(max)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"parentId\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.parentId").doesNotExist());
        mockMvc.perform(delete("/api/firmendokumente/ordner/9").with(user(max)).with(csrf())).andExpect(status().isNoContent());
        verify(ordnerService).loeschen(9L);
    }

    @Test
    @DisplayName("Hochladen: Name des Hochladers kommt aus der Anmeldung, Datum als ISO-Datum")
    void hochladen() throws Exception {
        MockMultipartFile datei = new MockMultipartFile("datei", "scan.pdf", "application/pdf",
                "%PDF-1.7".getBytes(StandardCharsets.US_ASCII));
        given(service.hochladen(any(), eq(1L), eq("Torprüfung"), eq(LocalDate.of(2027, 10, 31)), eq("Max Mustermann")))
                .willReturn(dto("Torprüfung"));

        mockMvc.perform(multipart("/api/firmendokumente").file(datei).param("ordnerId", "1").param("name", "Torprüfung")
                .param("faelligAm", "2027-10-31").with(user(max)).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value("/api/firmendokumente/7/datei"));
    }

    @Test
    @DisplayName("Hochladen: Fehler des Dienstes kommen als 400 mit deutscher Meldung zurück")
    void hochladenFehler() throws Exception {
        given(service.hochladen(any(), any(), any(), isNull(), any()))
                .willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Es können nur PDF-Dateien abgelegt werden."));

        mockMvc.perform(multipart("/api/firmendokumente").file(new MockMultipartFile("datei", "virus.exe",
                "application/octet-stream", "MZ".getBytes(StandardCharsets.US_ASCII))).param("ordnerId", "1")
                .param("name", "x").with(user(max)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Es können nur PDF-Dateien abgelegt werden."));
    }

    @Test
    @DisplayName("Ändern und Löschen eines Dokuments; unbekannte ID → 404, kaputte ID → 400")
    void dokumentAendernUndLoeschen() throws Exception {
        given(service.aendern(7L, new FirmendokumentAendernDto("Neu", 2L, null))).willReturn(dto("Neu"));
        given(service.aendern(eq(Long.MAX_VALUE), any()))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Das Dokument gibt es nicht mehr."));

        mockMvc.perform(put("/api/firmendokumente/7").with(user(max)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Neu\",\"ordnerId\":2,\"faelligAm\":null}")).andExpect(status().isOk());
        mockMvc.perform(put("/api/firmendokumente/" + Long.MAX_VALUE).with(user(max)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Neu\",\"ordnerId\":2}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Das Dokument gibt es nicht mehr."));
        mockMvc.perform(put("/api/firmendokumente/abc").with(user(max)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Neu\",\"ordnerId\":2}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/firmendokumente").param("ordnerId", SQL).with(user(max))).andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/firmendokumente/7").with(user(max)).with(csrf())).andExpect(status().isNoContent());
        verify(service).loeschen(7L);
    }

    @Test
    @DisplayName("Datei: inline als PDF mit nosniff, auf Wunsch als Anhang mit UTF-8-Dateinamen")
    void datei() throws Exception {
        Path pdf = Files.write(tmp.resolve("x.pdf"), "%PDF-1.7".getBytes(StandardCharsets.US_ASCII));
        given(service.datei(anyLong())).willAnswer(i -> {
            if (i.<Long>getArgument(0) == 7L) {
                return new FirmendokumentService.Datei(pdf, "Torprüfung.pdf");
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Das Dokument gibt es nicht mehr.");
        });

        mockMvc.perform(get("/api/firmendokumente/7/datei").with(user(max))).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.startsWith("inline; filename=")));
        mockMvc.perform(get("/api/firmendokumente/7/datei").param("download", "true").with(user(max)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("filename*=UTF-8''Torpr%C3%BCfung.pdf")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")));
        mockMvc.perform(get("/api/firmendokumente/-1/datei").with(user(max))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/firmendokumente/0/datei").with(user(max))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Seitenangaben werden unverändert an den Dienst gereicht, der sie begrenzt")
    void seitenangaben() throws Exception {
        given(service.imOrdner(eq(1L), anyInt(), anyInt())).willReturn(new FirmendokumentSeiteDto(List.of(), 0, 0, 50));
        mockMvc.perform(get("/api/firmendokumente").param("ordnerId", "1").param("page", "-1").param("size", "100000")
                .with(user(max))).andExpect(status().isOk());
        verify(service).imOrdner(1L, -1, 100_000);
    }
}
