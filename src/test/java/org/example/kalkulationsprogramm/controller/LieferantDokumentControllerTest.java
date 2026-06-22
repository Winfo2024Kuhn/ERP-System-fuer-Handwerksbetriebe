package org.example.kalkulationsprogramm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.dto.LieferantDokumentDto;
import org.example.kalkulationsprogramm.repository.EmailAttachmentRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.service.DokumentLockService;
import org.example.kalkulationsprogramm.service.EmailAttachmentProcessingService;
import org.example.kalkulationsprogramm.service.GeminiDokumentAnalyseService;
import org.example.kalkulationsprogramm.service.LieferantDokumentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LieferantDokumentController.class)
@AutoConfigureMockMvc(addFilters = false)
class LieferantDokumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LieferantDokumentController controller;

    @MockBean
    private LieferantDokumentRepository dokumentRepository;

    @MockBean
    private LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository;

    @MockBean
    private LieferantDokumentService dokumentService;

    @MockBean
    private GeminiDokumentAnalyseService analyseService;

    @MockBean
    private EmailRepository emailRepository;

    @MockBean
    private EmailAttachmentProcessingService emailAttachmentProcessingService;

    @MockBean
    private EmailAttachmentRepository emailAttachmentRepository;

    @MockBean
    private DokumentLockService dokumentLockService;

    /**
     * MockMvc-Tests laufen mit `addFilters = false`, daher wird der
     * `Authentication`-Parameter im Controller nicht aus dem SecurityContext
     * aufgeloest. Wir setzen den Auth-Token deshalb direkt als Request-
     * Principal — Springs ServletRequestMethodArgumentResolver liest den von
     * dort, weil UsernamePasswordAuthenticationToken auch java.security.Principal
     * implementiert.
     */
    private UsernamePasswordAuthenticationToken testAuth() {
        FrontendUserPrincipal principal = new FrontendUserPrincipal(
                42L, "max.mustermann", "Max Mustermann", "hash", true,
                Set.of(FrontendUserRole.ADMIN));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @BeforeEach
    void mockLockHeld() {
        given(dokumentLockService.isHeldBy(anyString(), anyLong(), anyLong())).willReturn(true);
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("PUT /api/lieferant-dokumente/{dokumentId}")
    class UpdateDokument {

        @Test
        @DisplayName("Aktualisiert Dokument erfolgreich")
        void aktualisiertDokumentErfolgreich() throws Exception {
            LieferantDokument dokument = new LieferantDokument();
            dokument.setId(1L);
            dokument.setTyp(LieferantDokumentTyp.RECHNUNG);
            LieferantGeschaeftsdokument gd = new LieferantGeschaeftsdokument();
            gd.setId(1L);
            dokument.setGeschaeftsdaten(gd);
            gd.setDokument(dokument);

            given(dokumentRepository.findById(1L)).willReturn(Optional.of(dokument));
            given(geschaeftsdokumentRepository.save(any())).willReturn(gd);
            given(dokumentRepository.save(any())).willReturn(dokument);

            LieferantDokumentDto.Response response = new LieferantDokumentDto.Response();
            response.setId(1L);
            given(dokumentService.getDokumentById(1L)).willReturn(response);

            String body = """
                    {
                        "typ": "RECHNUNG",
                        "geschaeftsdaten": {
                            "dokumentNummer": "RE-001",
                            "betragBrutto": 1190.00
                        }
                    }
                    """;

            mockMvc.perform(put("/api/lieferant-dokumente/1")
                            .principal(testAuth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1));
        }

        @Test
        @DisplayName("Unbekanntes Dokument gibt 404")
        void unbekanntesDokumentGibt404() throws Exception {
            given(dokumentRepository.findById(999L)).willReturn(Optional.empty());

            mockMvc.perform(put("/api/lieferant-dokumente/999")
                            .principal(testAuth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"typ\": \"RECHNUNG\"}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Ohne Lock-Halterung: 409 Conflict")
        void ohneLockGibt409() throws Exception {
            given(dokumentLockService.isHeldBy(anyString(), anyLong(), anyLong())).willReturn(false);

            mockMvc.perform(put("/api/lieferant-dokumente/1")
                            .principal(testAuth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"typ\": \"RECHNUNG\"}"))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Ohne Authentication: 401 Unauthorized")
        void ohneAuthGibt401() throws Exception {
            mockMvc.perform(put("/api/lieferant-dokumente/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"typ\": \"RECHNUNG\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("SQL Injection in Dokumentnummer wird als String gespeichert")
        void sqlInjectionInDokumentnummer() throws Exception {
            LieferantDokument dokument = new LieferantDokument();
            dokument.setId(1L);
            LieferantGeschaeftsdokument gd = new LieferantGeschaeftsdokument();
            gd.setId(1L);
            dokument.setGeschaeftsdaten(gd);
            gd.setDokument(dokument);

            given(dokumentRepository.findById(1L)).willReturn(Optional.of(dokument));
            given(geschaeftsdokumentRepository.save(any())).willReturn(gd);
            given(dokumentRepository.save(any())).willReturn(dokument);

            LieferantDokumentDto.Response response = new LieferantDokumentDto.Response();
            response.setId(1L);
            given(dokumentService.getDokumentById(1L)).willReturn(response);

            String body = """
                    {
                        "geschaeftsdaten": {
                            "dokumentNummer": "'; DROP TABLE lieferant_dokumente; --"
                        }
                    }
                    """;

            mockMvc.perform(put("/api/lieferant-dokumente/1")
                            .principal(testAuth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("XML Content-Type wird abgelehnt")
        void xmlContentTypeWirdAbgelehnt() throws Exception {
            mockMvc.perform(put("/api/lieferant-dokumente/1")
                            .contentType(MediaType.APPLICATION_XML)
                            .content("<dokument />"))
                    .andExpect(status().isUnsupportedMediaType());
        }
    }

    @Nested
    @DisplayName("GET /api/lieferant-dokumente/{dokumentId}")
    class GetDokument {

        @Test
        @DisplayName("Lädt Dokument erfolgreich")
        void laedtDokumentErfolgreich() throws Exception {
            LieferantDokumentDto.Response response = new LieferantDokumentDto.Response();
            response.setId(42L);
            given(dokumentService.getDokumentById(42L)).willReturn(response);

            mockMvc.perform(get("/api/lieferant-dokumente/42"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(42));
        }

        @Test
        @DisplayName("Unbekanntes Dokument gibt 404")
        void unbekanntesDokumentGibt404() throws Exception {
            given(dokumentService.getDokumentById(999L)).willReturn(null);

            mockMvc.perform(get("/api/lieferant-dokumente/999"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/lieferant-dokumente/{dokumentId}/reanalyze")
    class ReanalyzeDokument {

        @Test
        @DisplayName("Re-Analyse für unbekanntes Dokument gibt 404")
        void unbekanntesDokumentGibt404() throws Exception {
            given(dokumentRepository.findById(999L)).willReturn(Optional.empty());

            mockMvc.perform(post("/api/lieferant-dokumente/999/reanalyze"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Re-Analyse erfolgreich")
        void reanalyseErfolgreich() throws Exception {
            LieferantDokument dokument = new LieferantDokument();
            dokument.setId(1L);
            given(dokumentRepository.findById(1L)).willReturn(Optional.of(dokument));

            LieferantGeschaeftsdokument result = new LieferantGeschaeftsdokument();
            result.setDokumentNummer("RE-001");
            given(analyseService.analysiereDokument(any())).willReturn(result);

            LieferantDokumentDto.Response response = new LieferantDokumentDto.Response();
            response.setId(1L);
            given(dokumentService.getDokumentById(1L)).willReturn(response);

            mockMvc.perform(post("/api/lieferant-dokumente/1/reanalyze"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1));
        }
    }

    @Nested
    @DisplayName("POST /api/lieferant-dokumente/lieferant/{lieferantId}/reanalyze")
    class ReanalyzeByLieferant {

        @Test
        @DisplayName("Re-Analyse für Lieferant gibt Zusammenfassung")
        void reanalyseByLieferant() throws Exception {
            given(dokumentRepository.findByLieferantIdOrderByUploadDatumDesc(1L)).willReturn(List.of());

            mockMvc.perform(post("/api/lieferant-dokumente/lieferant/1/reanalyze"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.lieferantId").value(1))
                    .andExpect(jsonPath("$.gesamt").value(0));
        }
    }

    @Nested
    @DisplayName("POST /api/lieferant-dokumente/relink-all")
    class RelinkAll {

        @Test
        @DisplayName("Relink gibt Ergebnis zurück")
        void relinkAll() throws Exception {
            given(analyseService.relinkAlleDokumente()).willReturn(3);

            mockMvc.perform(post("/api/lieferant-dokumente/relink-all"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.neuVerknuepft").value(3));
        }
    }

    @Nested
    @DisplayName("GET /api/lieferant-dokumente/{dokumentId}/download")
    class Download {

        @Test
        @DisplayName("Unbekanntes Dokument gibt 404")
        void unbekanntesDokumentGibt404() throws Exception {
            given(dokumentRepository.findById(999L)).willReturn(Optional.empty());

            mockMvc.perform(get("/api/lieferant-dokumente/999/download"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Dokument ohne Datei gibt 404")
        void dokumentOhneDateiGibt404() throws Exception {
            LieferantDokument dokument = new LieferantDokument();
            dokument.setId(1L);
            // No gespeicherterDateiname and no attachment → resolveDokumentPath returns null
            given(dokumentRepository.findById(1L)).willReturn(Optional.of(dokument));

            mockMvc.perform(get("/api/lieferant-dokumente/1/download"))
                    .andExpect(status().isNotFound());
        }

        /**
         * Happy-Path fuer den Bugfix: Mobile-Belege (vom BelegScanner) werden
         * mit gespeicherterDateiname="belege/<file>" als LieferantDokument
         * promotet. Die Stufe-0-Aufloesung muss diese Datei aus
         * <uploadDir>/belege/ ausliefern.
         */
        @Test
        @DisplayName("Mobile-Beleg (gespeicherterDateiname='belege/...') wird aus uploadDir/belege ausgeliefert")
        void mobileBelegWirdAusgeliefert(@TempDir Path workDir) throws Exception {
            Path uploadDir = workDir.resolve("uploads");
            Path belegeDir = uploadDir.resolve("belege");
            Files.createDirectories(belegeDir);
            Files.write(belegeDir.resolve("scan.pdf"), "%PDF-1.4 stub".getBytes());

            ReflectionTestUtils.setField(controller, "uploadDir", uploadDir.toString());

            LieferantDokument dokument = new LieferantDokument();
            dokument.setId(5L);
            dokument.setOriginalDateiname("scan.pdf");
            dokument.setGespeicherterDateiname("belege/scan.pdf");
            given(dokumentRepository.findById(5L)).willReturn(Optional.of(dokument));

            mockMvc.perform(get("/api/lieferant-dokumente/5/download"))
                    .andExpect(status().isOk());
        }

        /**
         * Defense-in-Depth: ein boesartig gesetzter gespeicherterDateiname mit
         * ../-Traversal darf die Datei NICHT ausserhalb von uploadDir
         * ausliefern. Ohne die startsWith(uploadBase)-Pruefung wuerde Stufe 0
         * `<uploadDir>/../secret.txt` aufloesen und Files.exists() haette
         * Treffer → LFI. Mit Containment-Check muss Stufe 0 die Datei
         * verwerfen.
         */
        @Test
        @DisplayName("Path-Traversal in gespeicherterDateiname wird durch Containment-Check blockiert")
        void pathTraversalWirdBlockiert(@TempDir Path workDir) throws Exception {
            // uploadDir ist Subdir, secret.txt liegt als Geschwister im selben
            // workDir → mit "../secret.txt" wuerde Stufe 0 ohne Haertung den
            // aufgeloesten Pfad treffen.
            Path uploadDir = workDir.resolve("uploads");
            Files.createDirectories(uploadDir);
            Path secret = workDir.resolve("secret.txt");
            Files.writeString(secret, "geheim");

            ReflectionTestUtils.setField(controller, "uploadDir", uploadDir.toString());

            LieferantDokument dokument = new LieferantDokument();
            dokument.setId(6L);
            dokument.setGespeicherterDateiname("../secret.txt");
            given(dokumentRepository.findById(6L)).willReturn(Optional.of(dokument));

            mockMvc.perform(get("/api/lieferant-dokumente/6/download"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/lieferant-dokumente/duplicates")
    class Duplicates {

        @Test
        @DisplayName("Keine Duplikate gefunden")
        void keineDuplikate() throws Exception {
            given(geschaeftsdokumentRepository.findAllDuplicates()).willReturn(List.of());

            mockMvc.perform(get("/api/lieferant-dokumente/duplicates"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.duplikateGesamt").value(0));
        }
    }

    @Nested
    @DisplayName("POST /api/lieferant-dokumente/process-email/{emailId}")
    class ProcessEmail {

        @Test
        @DisplayName("Unbekannte Email gibt 404")
        void unbekannteEmailGibt404() throws Exception {
            given(emailRepository.findById(999L)).willReturn(Optional.empty());

            mockMvc.perform(post("/api/lieferant-dokumente/process-email/999"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /api/lieferant-dokumente/{id}")
    class LoescheDokument {

        @Test
        @DisplayName("Löscht Dokument erfolgreich → 204")
        void loeschtErfolgreich_gibt204() throws Exception {
            doNothing().when(dokumentService).loescheDokument(eq(1L), anyString());

            mockMvc.perform(delete("/api/lieferant-dokumente/1").principal(testAuth()))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("Unbekannte ID → 404")
        void unbekannteId_gibt404() throws Exception {
            doThrow(new EntityNotFoundException("Dokument nicht gefunden: 99"))
                    .when(dokumentService).loescheDokument(eq(99L), anyString());

            mockMvc.perform(delete("/api/lieferant-dokumente/99").principal(testAuth()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("GoBD-geschützter Typ (RECHNUNG) → 422 mit Fehlermeldung")
        void goBDGeschuetzterTyp_gibt422() throws Exception {
            doThrow(new IllegalArgumentException("GoBD-Aufbewahrungspflicht"))
                    .when(dokumentService).loescheDokument(eq(2L), anyString());

            mockMvc.perform(delete("/api/lieferant-dokumente/2").principal(testAuth()))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.message").value("GoBD-Aufbewahrungspflicht"));
        }
    }
}
