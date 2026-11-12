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
import org.example.kalkulationsprogramm.domain.firmendokumente.FirmendokumentZugriffsStufe;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentAbteilungsRechtDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentAendernDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentOrdnerAnlegenDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentOrdnerDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentOrdnerRechteDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentOrdnerRechteSpeichernDto;
import org.example.kalkulationsprogramm.dto.Firmendokumente.FirmendokumentSeiteDto;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.service.firmendokumente.FirmendokumentDateiart;
import org.example.kalkulationsprogramm.service.firmendokumente.FirmendokumentOrdnerRechteService;
import org.example.kalkulationsprogramm.service.firmendokumente.FirmendokumentOrdnerService;
import org.example.kalkulationsprogramm.service.firmendokumente.FirmendokumentService;
import org.example.kalkulationsprogramm.service.firmendokumente.FirmendokumentVorschaubildService;
import org.example.kalkulationsprogramm.service.vorschau.UmrechnungAusgelastetException;
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
 * Firmendokumente sind Stammdaten für alle Angemeldeten (mit CSRF); welche Ordner jemand sieht oder ändern darf,
 * entscheidet der Dienst (403/404 kommen von dort). Die Ordnerrechte selbst stellen nur Admins ein
 * (SecurityConfig). Ohne Anmeldung 401, ohne CSRF 403, unbekannte IDs 404, kaputte IDs 400.
 */
@WebMvcTest(controllers = { FirmendokumentController.class, FirmendokumentOrdnerController.class,
        FirmendokumentOrdnerRechteController.class })
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
    private static final String VERBOTEN = "Du darfst in diesem Ordner nichts ändern.";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private FirmendokumentService service;
    @MockitoBean private FirmendokumentVorschaubildService vorschaubildService;
    @MockitoBean private FirmendokumentOrdnerService ordnerService;
    @MockitoBean private FirmendokumentOrdnerRechteService rechteService;
    @MockitoBean private FrontendUserDetailsService frontendUserDetailsService;
    @MockitoBean private FrontendUserProfileRepository frontendUserProfileRepository;

    @TempDir Path tmp;

    private final FrontendUserPrincipal max = new FrontendUserPrincipal(70L, "max@example.com", "Max Mustermann", "", true,
            Set.of(FrontendUserRole.USER));
    private final FrontendUserPrincipal admin = new FrontendUserPrincipal(1L, "admin@example.com", "Erika Mustermann", "",
            true, Set.of(FrontendUserRole.ADMIN));

    private static FirmendokumentDto dto(String name) {
        return new FirmendokumentDto(7L, 1L, name, "scan.pdf", 1234L, LocalDateTime.of(2026, 11, 2, 9, 0),
                "Max Mustermann", LocalDate.of(2027, 10, 31), "/api/firmendokumente/7/datei", "PDF", true);
    }

    private static FirmendokumentOrdnerRechteDto rechte() {
        return new FirmendokumentOrdnerRechteDto(3L, "Versicherungen", true, List.of(
                new FirmendokumentAbteilungsRechtDto(10L, "Büro", FirmendokumentZugriffsStufe.AENDERN),
                new FirmendokumentAbteilungsRechtDto(11L, "Werkstatt", FirmendokumentZugriffsStufe.ANSEHEN)));
    }

    @BeforeEach
    void setUp() {
        given(frontendUserDetailsService.aktuellerStand(any())).willAnswer(i -> Optional.of(i.getArgument(0)));
    }

    @Test
    @DisplayName("Ohne Anmeldung: 401 auf Ordner, Liste, Datei, Vorschaubild, Hochladen und Ordnerrechte")
    void ohneAnmeldung() throws Exception {
        mockMvc.perform(get("/api/firmendokumente/ordner")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/firmendokumente").param("ordnerId", "1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/firmendokumente/7/datei")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/firmendokumente/7/vorschaubild")).andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/firmendokumente").file(new MockMultipartFile("datei", "a.pdf", "application/pdf",
                "%PDF-".getBytes(StandardCharsets.US_ASCII))).with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/firmendokumente/ordner/3/rechte")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/firmendokumente/ordner/3/rechte").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"eingeschraenkt\":false,\"abteilungen\":[]}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service, ordnerService, rechteService, vorschaubildService);
    }

    @Test
    @DisplayName("Normaler PC-Benutzer (kein Admin) liest Ordner (mit darfAendern/eingeschraenkt), Liste, Suche und Fälliges")
    void lesenOhneAdmin() throws Exception {
        given(ordnerService.liste(any())).willReturn(List.of(
                new FirmendokumentOrdnerDto(1L, "Prüfungen", null, 2, 1, 0, true, false),
                new FirmendokumentOrdnerDto(3L, "Versicherungen", null, 0, 0, 0, false, true)));
        given(service.imOrdner(eq(1L), eq(0), eq(50), any()))
                .willReturn(new FirmendokumentSeiteDto(List.of(dto(XSS)), 1, 0, 50));
        given(service.suche(eq(SQL), eq(0), eq(50), any())).willReturn(new FirmendokumentSeiteDto(List.of(), 0, 0, 50));
        given(service.faellig(eq(-1), eq(100_000), any())).willReturn(new FirmendokumentSeiteDto(List.of(), 0, 0, 50));

        mockMvc.perform(get("/api/firmendokumente/ordner").with(user(max))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Prüfungen"))
                .andExpect(jsonPath("$[0].anzahlUeberfaellig").value(1))
                .andExpect(jsonPath("$[0].darfAendern").value(true))
                .andExpect(jsonPath("$[0].eingeschraenkt").value(false))
                .andExpect(jsonPath("$[1].darfAendern").value(false))
                .andExpect(jsonPath("$[1].eingeschraenkt").value(true));
        mockMvc.perform(get("/api/firmendokumente").param("ordnerId", "1").with(user(max))).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.eintraege[0].name").value(XSS))
                .andExpect(jsonPath("$.eintraege[0].faelligAm").value("2027-10-31"))
                .andExpect(jsonPath("$.eintraege[0].dateiart").value("PDF"))
                .andExpect(jsonPath("$.eintraege[0].darfAendern").value(true))
                .andExpect(jsonPath("$.gesamt").value(1));
        mockMvc.perform(get("/api/firmendokumente/suche").param("q", SQL).with(user(max))).andExpect(status().isOk());
        mockMvc.perform(get("/api/firmendokumente/faellig").param("page", "-1").param("size", "100000").with(user(max)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Unsichtbarer Ordner bzw. unsichtbares Dokument: 404 mit Meldung, als gäbe es sie nicht")
    void unsichtbar() throws Exception {
        given(service.imOrdner(eq(3L), anyInt(), anyInt(), any()))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Den Ordner gibt es nicht mehr."));
        given(service.datei(eq(8L), any()))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Das Dokument gibt es nicht mehr."));

        mockMvc.perform(get("/api/firmendokumente").param("ordnerId", "3").with(user(max)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Den Ordner gibt es nicht mehr."));
        mockMvc.perform(get("/api/firmendokumente/8/datei").with(user(max)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Das Dokument gibt es nicht mehr."));
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
        mockMvc.perform(put("/api/firmendokumente/ordner/3/rechte").with(user(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"eingeschraenkt\":true,\"abteilungen\":[]}")).andExpect(status().isForbidden());
        verifyNoInteractions(service, ordnerService, rechteService);
    }

    @Test
    @DisplayName("Normaler PC-Benutzer legt Ordner an, benennt um, verschiebt und löscht")
    void ordnerBearbeiten() throws Exception {
        given(ordnerService.anlegen(eq(new FirmendokumentOrdnerAnlegenDto(XSS, 1L)), any()))
                .willReturn(new FirmendokumentOrdnerDto(9L, XSS, 1L, 0, 0, 0, true, false));
        given(ordnerService.umbenennen(eq(9L), eq(SQL), any()))
                .willReturn(new FirmendokumentOrdnerDto(9L, SQL, 1L, 0, 0, 0, true, false));
        given(ordnerService.verschieben(eq(9L), isNull(), any()))
                .willReturn(new FirmendokumentOrdnerDto(9L, SQL, null, 0, 0, 0, true, false));

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
        verify(ordnerService).loeschen(eq(9L), any());
    }

    @Test
    @DisplayName("Ordner ohne Ändern-Recht: 403 mit Meldung aus dem Dienst")
    void ordnerOhneRecht() throws Exception {
        given(ordnerService.umbenennen(eq(3L), any(), any()))
                .willThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, VERBOTEN));

        mockMvc.perform(put("/api/firmendokumente/ordner/3").with(user(max)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Neu\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value(VERBOTEN));

        String nurAdmin = "Aus einem eingeschränkten Ordner kann nur ein Admin in einen anderen Hauptordner verschieben.";
        given(ordnerService.verschieben(eq(3L), eq(1L), any()))
                .willThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, nurAdmin));
        mockMvc.perform(put("/api/firmendokumente/ordner/3/verschieben").with(user(max)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"parentId\":1}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value(nurAdmin));
    }

    @Test
    @DisplayName("Hochladen: Name des Hochladers kommt aus der Anmeldung, Datum als ISO-Datum")
    void hochladen() throws Exception {
        MockMultipartFile datei = new MockMultipartFile("datei", "scan.pdf", "application/pdf",
                "%PDF-1.7".getBytes(StandardCharsets.US_ASCII));
        given(service.hochladen(any(), eq(1L), eq("Torprüfung"), eq(LocalDate.of(2027, 10, 31)), eq("Max Mustermann"),
                any())).willReturn(dto("Torprüfung"));

        mockMvc.perform(multipart("/api/firmendokumente").file(datei).param("ordnerId", "1").param("name", "Torprüfung")
                .param("faelligAm", "2027-10-31").with(user(max)).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value("/api/firmendokumente/7/datei"))
                .andExpect(jsonPath("$.dateiart").value("PDF"));
    }

    @Test
    @DisplayName("Hochladen: Fehler des Dienstes kommen als 400 bzw. 403 mit deutscher Meldung zurück")
    void hochladenFehler() throws Exception {
        String programm = "Programmdateien (z. B. .exe oder .bat) können aus Sicherheitsgründen nicht abgelegt werden.";
        given(service.hochladen(any(), eq(1L), any(), isNull(), any(), any()))
                .willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, programm));
        given(service.hochladen(any(), eq(3L), any(), isNull(), any(), any()))
                .willThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, VERBOTEN));

        mockMvc.perform(multipart("/api/firmendokumente").file(new MockMultipartFile("datei", "virus.exe",
                "application/octet-stream", "MZ".getBytes(StandardCharsets.US_ASCII))).param("ordnerId", "1")
                .param("name", "x").with(user(max)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(programm));
        mockMvc.perform(multipart("/api/firmendokumente").file(new MockMultipartFile("datei", "police.pdf",
                "application/pdf", "%PDF-".getBytes(StandardCharsets.US_ASCII))).param("ordnerId", "3")
                .param("name", "Police").with(user(max)).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(VERBOTEN));
    }

    @Test
    @DisplayName("Ändern und Löschen eines Dokuments; unbekannte ID → 404, kaputte ID → 400")
    void dokumentAendernUndLoeschen() throws Exception {
        given(service.aendern(eq(7L), eq(new FirmendokumentAendernDto("Neu", 2L, null)), any())).willReturn(dto("Neu"));
        given(service.aendern(eq(Long.MAX_VALUE), any(), any()))
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
        verify(service).loeschen(eq(7L), any());
    }

    @Test
    @DisplayName("Datei: PDF inline mit nosniff und Sandbox, auf Wunsch als Anhang mit UTF-8-Dateinamen")
    void dateiPdf() throws Exception {
        Path pdf = Files.write(tmp.resolve("x.bin"), "%PDF-1.7".getBytes(StandardCharsets.US_ASCII));
        given(service.datei(anyLong(), any())).willAnswer(i -> {
            if (i.<Long>getArgument(0) == 7L) {
                return new FirmendokumentService.Datei(pdf, "Torprüfung.pdf", MediaType.APPLICATION_PDF,
                        FirmendokumentDateiart.PDF);
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Das Dokument gibt es nicht mehr.");
        });

        mockMvc.perform(get("/api/firmendokumente/7/datei").with(user(max))).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "sandbox; default-src 'none'"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline; filename=")));
        mockMvc.perform(get("/api/firmendokumente/7/datei").param("download", "true").with(user(max)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("filename*=UTF-8''Torpr%C3%BCfung.pdf")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")));
        mockMvc.perform(get("/api/firmendokumente/-1/datei").with(user(max))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/firmendokumente/0/datei").with(user(max))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/firmendokumente/" + Long.MAX_VALUE + "/datei").with(user(max)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Datei: Bild inline als image/png; HTML-Datei immer als octet-stream-Anhang, auch ohne download")
    void dateiAndereFormate() throws Exception {
        Path bild = Files.write(tmp.resolve("b.bin"), new byte[] { (byte) 0x89, 'P', 'N', 'G' });
        Path html = Files.write(tmp.resolve("h.bin"), XSS.getBytes(StandardCharsets.UTF_8));
        given(service.datei(eq(9L), any()))
                .willReturn(new FirmendokumentService.Datei(bild, "Foto Tor.png", MediaType.IMAGE_PNG,
                        FirmendokumentDateiart.BILD));
        given(service.datei(eq(10L), any())).willReturn(
                new FirmendokumentService.Datei(html, "Seite.html", MediaType.APPLICATION_OCTET_STREAM,
                        FirmendokumentDateiart.SONSTIGE));

        mockMvc.perform(get("/api/firmendokumente/9/datei").with(user(max))).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")));
        mockMvc.perform(get("/api/firmendokumente/10/datei").with(user(max))).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "sandbox; default-src 'none'"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("Seite.html")));
    }

    @Test
    @DisplayName("Vorschaubild: JPEG inline mit nosniff, ETag und no-cache (Browser fragt jedes Mal, Rechte gelten sofort)")
    void vorschaubild() throws Exception {
        byte[] jpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9 };
        String etag = "\"firmendokument-vorschau-v1-7\"";
        given(vorschaubildService.vorschaubild(eq(7L), isNull(), any()))
                .willReturn(new FirmendokumentVorschaubildService.Vorschaubild(etag, jpeg));

        mockMvc.perform(get("/api/firmendokumente/7/vorschaubild").with(user(max))).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(jpeg))
                .andExpect(header().string("ETag", etag))
                .andExpect(header().string("Cache-Control", "no-cache, private"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"7-vorschau.jpg\""));
    }

    @Test
    @DisplayName("Vorschaubild mit If-None-Match: der Dienst entscheidet (Rechte zuerst); unverändert → 304 ohne Inhalt")
    void vorschaubildUnveraendert() throws Exception {
        String etag = "\"firmendokument-vorschau-v1-7\"";
        given(vorschaubildService.vorschaubild(eq(7L), eq(etag), any()))
                .willReturn(new FirmendokumentVorschaubildService.Vorschaubild(etag, null));
        given(vorschaubildService.vorschaubild(eq(8L), eq(etag), any()))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Das Dokument gibt es nicht mehr."));

        mockMvc.perform(get("/api/firmendokumente/7/vorschaubild").header("If-None-Match", etag).with(user(max)))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", etag))
                .andExpect(header().string("Cache-Control", "no-cache, private"))
                .andExpect(content().bytes(new byte[0]));
        mockMvc.perform(get("/api/firmendokumente/8/vorschaubild").header("If-None-Match", etag).with(user(max)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Das Dokument gibt es nicht mehr."));
        mockMvc.perform(get("/api/firmendokumente/7/vorschaubild").header("If-None-Match", etag))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Vorschaubild: kein Vorschaubild bzw. unsichtbares Dokument → 404 mit Meldung; kaputte IDs 400")
    void vorschaubildFehler() throws Exception {
        String kein = "Für diese Datei gibt es kein Vorschaubild.";
        String fehlt = "Das Dokument gibt es nicht mehr.";
        given(vorschaubildService.vorschaubild(anyLong(), any(), any())).willAnswer(i -> {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, i.<Long>getArgument(0) == 11L ? kein : fehlt);
        });
        for (long id : new long[] { 0L, -1L, Long.MAX_VALUE, 12L }) {
            mockMvc.perform(get("/api/firmendokumente/" + id + "/vorschaubild").with(user(max)))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value(fehlt));
        }

        mockMvc.perform(get("/api/firmendokumente/11/vorschaubild").with(user(max)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value(kein));
        mockMvc.perform(get("/api/firmendokumente/abc/vorschaubild").with(user(max))).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/firmendokumente/{id}/vorschaubild", SQL).with(user(max)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/firmendokumente/{id}/vorschaubild", XSS).with(user(max)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Vorschaubild bei Andrang: 503 ohne Inhalt, no-store und Retry-After 5")
    void vorschaubildAusgelastet() throws Exception {
        given(vorschaubildService.vorschaubild(eq(7L), any(), any())).willThrow(new UmrechnungAusgelastetException());

        mockMvc.perform(get("/api/firmendokumente/7/vorschaubild").with(user(max)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(content().bytes(new byte[0]));
    }

    @Test
    @DisplayName("Seitenangaben werden unverändert an den Dienst gereicht, der sie begrenzt")
    void seitenangaben() throws Exception {
        given(service.imOrdner(eq(1L), anyInt(), anyInt(), any())).willReturn(new FirmendokumentSeiteDto(List.of(), 0, 0, 50));
        mockMvc.perform(get("/api/firmendokumente").param("ordnerId", "1").param("page", "-1").param("size", "100000")
                .with(user(max))).andExpect(status().isOk());
        verify(service).imOrdner(eq(1L), eq(-1), eq(100_000), any());
    }

    @Test
    @DisplayName("Ordnerrechte: Nicht-Admin bekommt 403 beim Lesen und Speichern, der Dienst wird nicht gefragt")
    void rechteNurAdmin() throws Exception {
        mockMvc.perform(get("/api/firmendokumente/ordner/3/rechte").with(user(max))).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/firmendokumente/ordner/3/rechte").with(user(max)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"eingeschraenkt\":false,\"abteilungen\":[]}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(rechteService);
    }

    @Test
    @DisplayName("Ordnerrechte: Admin liest und speichert; Antwort mit allen Abteilungen und Stufen")
    void rechteAdmin() throws Exception {
        given(rechteService.laden(3L)).willReturn(rechte());
        FirmendokumentOrdnerRechteSpeichernDto eingabe = new FirmendokumentOrdnerRechteSpeichernDto(true, List.of(
                new FirmendokumentOrdnerRechteSpeichernDto.Eintrag(10L, "AENDERN"),
                new FirmendokumentOrdnerRechteSpeichernDto.Eintrag(11L, "ANSEHEN")));
        given(rechteService.speichern(3L, eingabe)).willReturn(rechte());

        mockMvc.perform(get("/api/firmendokumente/ordner/3/rechte").with(user(admin))).andExpect(status().isOk())
                .andExpect(jsonPath("$.ordnerId").value(3))
                .andExpect(jsonPath("$.ordnerName").value("Versicherungen"))
                .andExpect(jsonPath("$.eingeschraenkt").value(true))
                .andExpect(jsonPath("$.abteilungen[0].abteilungId").value(10))
                .andExpect(jsonPath("$.abteilungen[0].name").value("Büro"))
                .andExpect(jsonPath("$.abteilungen[0].stufe").value("AENDERN"))
                .andExpect(jsonPath("$.abteilungen[1].stufe").value("ANSEHEN"));
        mockMvc.perform(put("/api/firmendokumente/ordner/3/rechte").with(user(admin)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eingeschraenkt\":true,\"abteilungen\":[{\"abteilungId\":10,\"stufe\":\"AENDERN\"},"
                        + "{\"abteilungId\":11,\"stufe\":\"ANSEHEN\"}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.abteilungen[1].name").value("Werkstatt"));
    }

    @Test
    @DisplayName("Ordnerrechte: unbekannte IDs 404, Unterordner/falsche Stufe 400 mit Meldung, kaputte ID 400")
    void rechteFehler() throws Exception {
        for (long id : new long[] { 0L, -1L, Long.MAX_VALUE }) {
            given(rechteService.laden(id))
                    .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Den Ordner gibt es nicht mehr."));
            mockMvc.perform(get("/api/firmendokumente/ordner/" + id + "/rechte").with(user(admin)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Den Ordner gibt es nicht mehr."));
        }
        String nurOben = "Rechte werden am obersten Ordner eingestellt. Sie gelten auch für alle Unterordner.";
        given(rechteService.laden(4L)).willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, nurOben));
        mockMvc.perform(get("/api/firmendokumente/ordner/4/rechte").with(user(admin)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(nurOben));

        String stufeFalsch = "Bitte für jede Abteilung „Kein Zugriff“, „Ansehen“ oder „Ändern“ wählen.";
        given(rechteService.speichern(eq(3L), any()))
                .willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, stufeFalsch));
        mockMvc.perform(put("/api/firmendokumente/ordner/3/rechte").with(user(admin)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eingeschraenkt\":true,\"abteilungen\":[{\"abteilungId\":10,\"stufe\":\"" + SQL + "\"}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(stufeFalsch));
        mockMvc.perform(get("/api/firmendokumente/ordner/abc/rechte").with(user(admin))).andExpect(status().isBadRequest());
    }
}
