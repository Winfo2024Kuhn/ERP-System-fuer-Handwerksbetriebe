package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.SperrbarerTyp;
import org.example.kalkulationsprogramm.dto.LieferantDokumentDto;
import org.example.kalkulationsprogramm.repository.EmailAttachmentRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.service.BelegService;
import org.example.kalkulationsprogramm.service.DatensatzLockService;
import org.example.kalkulationsprogramm.service.EmailAttachmentProcessingService;
import org.example.kalkulationsprogramm.service.GeminiDokumentAnalyseService;
import org.example.kalkulationsprogramm.service.LieferantDokumentService;
import org.example.kalkulationsprogramm.service.LieferantDokumentZugriffService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Einzelabruf, Download, Bearbeiten und Neu-Analyse eines Lieferanten-Dokuments per ID
 * müssen die Dokumenttyp-Rechte der Abteilung einhalten — mit der echten
 * {@link SecurityConfig} und dem echten {@link LieferantDokumentZugriffService}.
 * Nicht sichtbare Dokumente antworten 404, damit ihre Existenz nicht verraten wird.
 */
@WebMvcTest(controllers = LieferantDokumentController.class)
@Import({ SecurityConfig.class, LieferantDokumentZugriffService.class,
        LieferantDokumentEinzelzugriffSecurityTest.EchteFilterBeans.class })
class LieferantDokumentEinzelzugriffSecurityTest {

    @TestConfiguration
    static class EchteFilterBeans {
        @Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private LieferantDokumentController controller;

    @TempDir
    Path uploadVerzeichnis;

    @MockBean
    private BelegService belegService;
    @MockBean
    private GeminiDokumentAnalyseService analyseService;
    @MockBean
    private LieferantDokumentRepository dokumentRepository;
    @MockBean
    private LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository;
    @MockBean
    private LieferantDokumentService dokumentService;
    @MockBean
    private EmailRepository emailRepository;
    @MockBean
    private EmailAttachmentProcessingService emailAttachmentProcessingService;
    @MockBean
    private EmailAttachmentRepository emailAttachmentRepository;
    @MockBean
    private DatensatzLockService dokumentLockService;
    @MockBean
    private FrontendUserDetailsService frontendUserDetailsService;

    // ---------------------------------------------------------------- Helfer

    private static RequestPostProcessor sessionAls(FrontendUserRole rolle) {
        var principal = new FrontendUserPrincipal(1L, "max.mustermann", "Max Mustermann", "hash", true,
                Set.of(rolle));
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(auth));
        return request -> {
            request.setSession(session);
            return request;
        };
    }

    private static Mitarbeiter mitarbeiter(long id) {
        Mitarbeiter m = new Mitarbeiter();
        m.setId(id);
        return m;
    }

    /** Der angemeldete Benutzer ist Mitarbeiter 5 und darf genau diese Typen sehen. */
    private void benutzerDarfSehen(LieferantDokumentTyp... typen) {
        given(belegService.findCaller(any(), any())).willReturn(mitarbeiter(5L));
        given(dokumentService.getBerechtigungen(5L)).willReturn(
                LieferantDokumentDto.BerechtigungenResponse.builder()
                        .sichtbareTypen(List.of(typen)).scanbarTypen(List.of()).build());
    }

    private LieferantDokument dokument(long id, LieferantDokumentTyp typ, String dateiname) throws Exception {
        Files.write(uploadVerzeichnis.resolve(dateiname), "%PDF-1.4 Mustermann".getBytes());
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(7L);
        lieferant.setLieferantenname("Muster Lieferant GmbH");
        LieferantDokument dokument = new LieferantDokument();
        dokument.setId(id);
        dokument.setLieferant(lieferant);
        dokument.setTyp(typ);
        dokument.setOriginalDateiname(dateiname);
        dokument.setGespeicherterDateiname(dateiname);
        given(dokumentRepository.findById(id)).willReturn(Optional.of(dokument));
        return dokument;
    }

    private static LieferantDokumentDto.Response dto(long id, LieferantDokumentTyp typ) {
        return LieferantDokumentDto.Response.builder().id(id).typ(typ)
                .verknuepfteDokumente(List.of(
                        LieferantDokumentDto.VerknuepftesDoc.builder().id(100L)
                                .typ(LieferantDokumentTyp.LIEFERSCHEIN).build(),
                        LieferantDokumentDto.VerknuepftesDoc.builder().id(101L)
                                .typ(LieferantDokumentTyp.RECHNUNG).build()))
                .build();
    }

    // ---------------------------------------------- GET /api/lieferant-dokumente/{id}

    @Test
    @DisplayName("Einzelabruf ohne Anmeldung: 401, es wird nichts geladen")
    void einzelabrufAnonymWird401() throws Exception {
        mockMvc.perform(get("/api/lieferant-dokumente/1"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(dokumentService);
    }

    @Test
    @DisplayName("Einzelabruf: Typ ohne Recht -> 404")
    void einzelabrufOhneRechtWird404() throws Exception {
        benutzerDarfSehen(LieferantDokumentTyp.LIEFERSCHEIN);
        given(dokumentService.getDokumentById(1L)).willReturn(dto(1L, LieferantDokumentTyp.RECHNUNG));

        mockMvc.perform(get("/api/lieferant-dokumente/1").with(sessionAls(FrontendUserRole.USER)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Einzelabruf: Benutzer ohne verknüpften Mitarbeiter sieht kein Dokument")
    void einzelabrufOhneMitarbeiterWird404() throws Exception {
        given(belegService.findCaller(any(), any())).willReturn(null);
        given(dokumentService.getDokumentById(1L)).willReturn(dto(1L, LieferantDokumentTyp.RECHNUNG));

        mockMvc.perform(get("/api/lieferant-dokumente/1").with(sessionAls(FrontendUserRole.USER)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Einzelabruf: sichtbarer Typ kommt zurück, Verknüpfungen nicht sichtbarer Typen entfallen")
    void einzelabrufMitRechtFiltertVerknuepfungen() throws Exception {
        benutzerDarfSehen(LieferantDokumentTyp.RECHNUNG, LieferantDokumentTyp.LIEFERSCHEIN);
        given(dokumentService.getDokumentById(1L)).willReturn(dto(1L, LieferantDokumentTyp.RECHNUNG));
        mockMvc.perform(get("/api/lieferant-dokumente/1").with(sessionAls(FrontendUserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verknuepfteDokumente.length()").value(2));

        benutzerDarfSehen(LieferantDokumentTyp.RECHNUNG);
        given(dokumentService.getDokumentById(1L)).willReturn(dto(1L, LieferantDokumentTyp.RECHNUNG));
        mockMvc.perform(get("/api/lieferant-dokumente/1").with(sessionAls(FrontendUserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verknuepfteDokumente.length()").value(1))
                .andExpect(jsonPath("$.verknuepfteDokumente[0].id").value(101));
    }

    @Test
    @DisplayName("Einzelabruf: Admin sieht jedes Dokument inklusive aller Verknüpfungen")
    void einzelabrufAdminSiehtAlles() throws Exception {
        given(dokumentService.getDokumentById(1L)).willReturn(dto(1L, LieferantDokumentTyp.RECHNUNG));

        mockMvc.perform(get("/api/lieferant-dokumente/1").with(sessionAls(FrontendUserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verknuepfteDokumente.length()").value(2));
    }

    @ParameterizedTest
    @ValueSource(longs = { -1L, 0L, Long.MAX_VALUE })
    @DisplayName("Einzelabruf mit ungültiger ID: 404")
    void einzelabrufUngueltigeId(long id) throws Exception {
        given(dokumentService.getDokumentById(id)).willReturn(null);

        mockMvc.perform(get("/api/lieferant-dokumente/" + id).with(sessionAls(FrontendUserRole.ADMIN)))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------- GET /api/lieferant-dokumente/{id}/download

    @Test
    @DisplayName("Download ohne Anmeldung: 401, die Datei wird nicht gelesen")
    void downloadAnonymWird401() throws Exception {
        mockMvc.perform(get("/api/lieferant-dokumente/1/download"))
                .andExpect(status().isUnauthorized());

        verify(dokumentRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("Download: Typ ohne Recht -> 404 statt Dateiinhalt")
    void downloadOhneRechtWird404() throws Exception {
        ReflectionTestUtils.setField(controller, "uploadDir", uploadVerzeichnis.toString());
        benutzerDarfSehen(LieferantDokumentTyp.LIEFERSCHEIN);
        dokument(1L, LieferantDokumentTyp.RECHNUNG, "rechnung.pdf");

        mockMvc.perform(get("/api/lieferant-dokumente/1/download").with(sessionAls(FrontendUserRole.USER)))
                .andExpect(status().isNotFound())
                .andExpect(content().bytes(new byte[0]));
    }

    @Test
    @DisplayName("Download: Typ mit Recht liefert die Datei")
    void downloadMitRechtLiefertDatei() throws Exception {
        ReflectionTestUtils.setField(controller, "uploadDir", uploadVerzeichnis.toString());
        benutzerDarfSehen(LieferantDokumentTyp.LIEFERSCHEIN);
        dokument(2L, LieferantDokumentTyp.LIEFERSCHEIN, "lieferschein.pdf");

        mockMvc.perform(get("/api/lieferant-dokumente/2/download").with(sessionAls(FrontendUserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    @DisplayName("Download: Admin darf jeden Typ laden")
    void downloadAdminDarfAlles() throws Exception {
        ReflectionTestUtils.setField(controller, "uploadDir", uploadVerzeichnis.toString());
        dokument(1L, LieferantDokumentTyp.RECHNUNG, "rechnung.pdf");

        mockMvc.perform(get("/api/lieferant-dokumente/1/download").with(sessionAls(FrontendUserRole.ADMIN)))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(longs = { -1L, 0L, Long.MAX_VALUE })
    @DisplayName("Download mit ungültiger ID: 404")
    void downloadUngueltigeId(long id) throws Exception {
        given(dokumentRepository.findById(id)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/lieferant-dokumente/" + id + "/download")
                .with(sessionAls(FrontendUserRole.ADMIN)))
                .andExpect(status().isNotFound());
    }

    // ----------------------------------- PUT /{id} und POST /{id}/reanalyze

    @Test
    @DisplayName("Bearbeiten: Typ ohne Recht -> 404, nichts wird gespeichert")
    void bearbeitenOhneRechtWird404() throws Exception {
        benutzerDarfSehen(LieferantDokumentTyp.LIEFERSCHEIN);
        given(dokumentLockService.isHeldBy(any(SperrbarerTyp.class), anyLong(), anyLong())).willReturn(true);
        dokument(1L, LieferantDokumentTyp.RECHNUNG, "rechnung.pdf");

        mockMvc.perform(put("/api/lieferant-dokumente/1").with(sessionAls(FrontendUserRole.USER)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"geschaeftsdaten\":{\"dokumentNummer\":\"'; DROP TABLE x; --\"}}"))
                .andExpect(status().isNotFound());

        verify(dokumentRepository, never()).save(any());
        verify(geschaeftsdokumentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Neu-Analyse: Typ ohne Recht -> 404, die KI wird nicht angefragt")
    void reanalyseOhneRechtWird404() throws Exception {
        benutzerDarfSehen(LieferantDokumentTyp.LIEFERSCHEIN);
        dokument(1L, LieferantDokumentTyp.RECHNUNG, "rechnung.pdf");

        mockMvc.perform(post("/api/lieferant-dokumente/1/reanalyze")
                .with(sessionAls(FrontendUserRole.USER)).with(csrf()))
                .andExpect(status().isNotFound());

        verifyNoInteractions(analyseService);
    }

    @Test
    @DisplayName("Bearbeiten: Typwechsel auf einen gesperrten Typ -> 403, nichts wird gespeichert")
    void bearbeitenTypwechselAufGesperrtenTypWird403() throws Exception {
        benutzerDarfSehen(LieferantDokumentTyp.LIEFERSCHEIN);
        given(dokumentLockService.isHeldBy(any(SperrbarerTyp.class), anyLong(), anyLong())).willReturn(true);
        LieferantDokument dokument = dokument(1L, LieferantDokumentTyp.LIEFERSCHEIN, "ls.pdf");

        mockMvc.perform(put("/api/lieferant-dokumente/1").with(sessionAls(FrontendUserRole.USER)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"typ\":\"RECHNUNG\"}"))
                .andExpect(status().isForbidden());

        org.assertj.core.api.Assertions.assertThat(dokument.getTyp()).isEqualTo(LieferantDokumentTyp.LIEFERSCHEIN);
        verify(dokumentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Bearbeiten: mit Recht gespeichert, Antwort ohne Verknüpfungen nicht sichtbarer Typen")
    void bearbeitenMitRechtFiltertVerknuepfungenInDerAntwort() throws Exception {
        benutzerDarfSehen(LieferantDokumentTyp.LIEFERSCHEIN);
        given(dokumentLockService.isHeldBy(any(SperrbarerTyp.class), anyLong(), anyLong())).willReturn(true);
        dokument(1L, LieferantDokumentTyp.LIEFERSCHEIN, "ls.pdf");
        given(dokumentService.getDokumentById(1L)).willReturn(dto(1L, LieferantDokumentTyp.LIEFERSCHEIN));

        mockMvc.perform(put("/api/lieferant-dokumente/1").with(sessionAls(FrontendUserRole.USER)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"typ\":\"LIEFERSCHEIN\",\"geschaeftsdaten\":{\"dokumentNummer\":\"LS-1\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verknuepfteDokumente.length()").value(1))
                .andExpect(jsonPath("$.verknuepfteDokumente[0].id").value(100));
        verify(dokumentRepository).save(any());
    }

    @Test
    @DisplayName("Bearbeiten: Admin darf jeden Typ setzen")
    void bearbeitenAdminDarfTypWechseln() throws Exception {
        given(dokumentLockService.isHeldBy(any(SperrbarerTyp.class), anyLong(), anyLong())).willReturn(true);
        LieferantDokument dokument = dokument(1L, LieferantDokumentTyp.LIEFERSCHEIN, "ls.pdf");
        given(dokumentService.getDokumentById(1L)).willReturn(dto(1L, LieferantDokumentTyp.RECHNUNG));

        mockMvc.perform(put("/api/lieferant-dokumente/1").with(sessionAls(FrontendUserRole.ADMIN)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"typ\":\"RECHNUNG\"}"))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(dokument.getTyp()).isEqualTo(LieferantDokumentTyp.RECHNUNG);
    }

    @Test
    @DisplayName("Neu-Analyse: mit Recht analysiert, Antwort ohne Verknüpfungen nicht sichtbarer Typen")
    void reanalyseMitRechtFiltertVerknuepfungen() throws Exception {
        benutzerDarfSehen(LieferantDokumentTyp.LIEFERSCHEIN);
        LieferantDokument dokument = dokument(1L, LieferantDokumentTyp.LIEFERSCHEIN, "ls.pdf");
        given(analyseService.analysiereDokument(dokument))
                .willReturn(new org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument());
        given(dokumentService.getDokumentById(1L)).willReturn(dto(1L, LieferantDokumentTyp.LIEFERSCHEIN));

        mockMvc.perform(post("/api/lieferant-dokumente/1/reanalyze")
                .with(sessionAls(FrontendUserRole.USER)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verknuepfteDokumente.length()").value(1));
    }

    @Test
    @DisplayName("Neu-Analyse: ändert die Analyse den Typ auf einen gesperrten, kommt kein Dokument zurück")
    void reanalyseMitTypwechselDurchAnalyseGibt404() throws Exception {
        benutzerDarfSehen(LieferantDokumentTyp.LIEFERSCHEIN);
        LieferantDokument dokument = dokument(1L, LieferantDokumentTyp.LIEFERSCHEIN, "ls.pdf");
        given(analyseService.analysiereDokument(dokument))
                .willReturn(new org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument());
        given(dokumentService.getDokumentById(1L)).willReturn(dto(1L, LieferantDokumentTyp.RECHNUNG));

        mockMvc.perform(post("/api/lieferant-dokumente/1/reanalyze")
                .with(sessionAls(FrontendUserRole.USER)).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Neu-Analyse: ohne Anmeldung 401, die KI wird nicht angefragt")
    void reanalyseAnonymWird401() throws Exception {
        mockMvc.perform(post("/api/lieferant-dokumente/1/reanalyze").with(csrf()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(analyseService);
    }
}
