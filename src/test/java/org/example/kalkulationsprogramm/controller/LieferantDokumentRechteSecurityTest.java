package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantDetailDto;
import org.example.kalkulationsprogramm.dto.LieferantDokumentDto;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.service.BelegService;
import org.example.kalkulationsprogramm.service.LieferantDokumentService;
import org.example.kalkulationsprogramm.service.LieferantDokumentZugriffService;
import org.example.kalkulationsprogramm.service.LieferantenDetailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Abteilungsrechte für Lieferanten-Dokumenttypen müssen serverseitig durchgesetzt werden —
 * mit der echten {@link SecurityConfig} und dem echten {@link LieferantDokumentZugriffService}.
 * Aufrufer ist die Session (PC) oder das Mitarbeiter-Token (Mobile), nie ein Client-Parameter.
 */
@WebMvcTest(controllers = { LieferantenController.class, DokumentUebersichtController.class })
@Import({ SecurityConfig.class, LieferantDokumentZugriffService.class,
        LieferantDokumentRechteSecurityTest.EchteFilterBeans.class })
class LieferantDokumentRechteSecurityTest {

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
    private LieferantenController lieferantenController;

    @TempDir
    Path uploadVerzeichnis;

    @MockBean
    private BelegService belegService;
    @MockBean
    private LieferantDokumentService dokumentService;
    @MockBean
    private org.example.kalkulationsprogramm.service.LieferantDokumentSucheService dokumentSucheService;
    @MockBean
    private LieferantenRepository lieferantenRepository;
    @MockBean
    private LieferantenDetailService lieferantenDetailService;
    @MockBean
    private LieferantGeschaeftsdokumentRepository lieferantGdRepo;
    @MockBean
    private AusgangsGeschaeftsDokumentRepository ausgangsRepo;
    @MockBean
    private FrontendUserDetailsService frontendUserDetailsService;

    // Weitere Abhängigkeiten des LieferantenController (für diese Tests ohne Bedeutung)
    @MockBean
    private org.example.kalkulationsprogramm.service.mail.SentMailArchiver sentMailArchiver;
    @MockBean
    private org.example.kalkulationsprogramm.service.BildVorschauService bildVorschauService;
    @MockBean
    private org.example.kalkulationsprogramm.repository.MitarbeiterRepository mitarbeiterRepository;
    @MockBean
    private org.example.kalkulationsprogramm.repository.KostenstelleRepository kostenstelleRepository;
    @MockBean
    private org.example.kalkulationsprogramm.service.LieferantStandardKostenstelleAutoAssigner standardKostenstelleAutoAssigner;
    @MockBean
    private org.example.kalkulationsprogramm.mapper.LieferantMapper lieferantMapper;
    @MockBean
    private org.example.kalkulationsprogramm.service.LieferantEmailResolver lieferantEmailResolver;
    @MockBean
    private org.example.kalkulationsprogramm.service.LieferantArtikelpreisService artikelpreisService;
    @MockBean
    private org.example.kalkulationsprogramm.service.EmailAttachmentProcessingService emailAttachmentProcessingService;
    @MockBean
    private org.example.kalkulationsprogramm.repository.LieferantDokumentRepository lieferantDokumentRepository;
    @MockBean
    private org.example.kalkulationsprogramm.service.GeminiDokumentAnalyseService geminiService;
    @MockBean
    private org.example.kalkulationsprogramm.service.BelegZuordnungService belegZuordnungService;
    @MockBean
    private org.example.kalkulationsprogramm.repository.LieferantNotizRepository notizRepository;
    @MockBean
    private org.example.kalkulationsprogramm.repository.LieferantBildRepository lieferantBildRepository;
    @MockBean
    private org.example.kalkulationsprogramm.repository.EmailRepository emailRepository;
    @MockBean
    private org.example.kalkulationsprogramm.service.FrontendUserProfileService frontendUserProfileService;
    @MockBean
    private org.example.kalkulationsprogramm.service.EmailSignatureService emailSignatureService;
    @MockBean
    private org.example.kalkulationsprogramm.service.SystemSettingsService systemSettingsService;

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

    private void mitarbeiterDarfSehen(long id, LieferantDokumentTyp... typen) {
        given(dokumentService.getBerechtigungen(id)).willReturn(
                LieferantDokumentDto.BerechtigungenResponse.builder()
                        .sichtbareTypen(List.of(typen)).scanbarTypen(List.of()).build());
    }

    private static LieferantGeschaeftsdokument eingang(long id, LieferantDokumentTyp typ) {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(7L);
        lieferant.setLieferantenname("Muster Lieferant GmbH");
        LieferantDokument dokument = new LieferantDokument();
        dokument.setId(id);
        dokument.setLieferant(lieferant);
        dokument.setTyp(typ);
        LieferantGeschaeftsdokument gd = new LieferantGeschaeftsdokument();
        gd.setId(id);
        gd.setDokument(dokument);
        gd.setDokumentNummer("NR-" + id);
        return gd;
    }

    @SuppressWarnings("unchecked")
    private Set<LieferantDokumentTyp> weitergegebeneTypen(Long lieferantId, LieferantDokumentTyp typ) {
        ArgumentCaptor<Set<LieferantDokumentTyp>> captor = ArgumentCaptor.forClass(Set.class);
        verify(dokumentService).getDokumenteFiltered(eq(lieferantId), captor.capture(), eq(typ));
        return captor.getValue();
    }

    // ------------------------------------------- GET /api/lieferanten/{id}/dokumente

    private static LieferantDokumentDto.Response dokumentMitVerknuepfungen() {
        return LieferantDokumentDto.Response.builder().id(1L).typ(LieferantDokumentTyp.LIEFERSCHEIN)
                .verknuepfteDokumente(List.of(
                        LieferantDokumentDto.VerknuepftesDoc.builder().id(2L)
                                .typ(LieferantDokumentTyp.LIEFERSCHEIN).originalDateiname("lieferschein.pdf").build(),
                        LieferantDokumentDto.VerknuepftesDoc.builder().id(3L)
                                .typ(LieferantDokumentTyp.RECHNUNG).originalDateiname("rechnung.pdf").build()))
                .build();
    }

    @ParameterizedTest
    @EnumSource(value = FrontendUserRole.class, names = { "USER", "ADMIN" })
    @DisplayName("Dokumentliste filtert auch Verknüpfungen nach Rechten; Admin behält alle")
    void listeFiltertVerknuepfteDokumente(FrontendUserRole rolle) throws Exception {
        given(lieferantenRepository.existsById(7L)).willReturn(true);
        given(belegService.findCaller(any(), any())).willReturn(mitarbeiter(5L));
        mitarbeiterDarfSehen(5L, LieferantDokumentTyp.LIEFERSCHEIN);
        given(dokumentService.getDokumenteFiltered(eq(7L), any(), eq(LieferantDokumentTyp.LIEFERSCHEIN)))
                .willReturn(List.of(dokumentMitVerknuepfungen()));

        mockMvc.perform(get("/api/lieferanten/7/dokumente").param("typ", "LIEFERSCHEIN").with(sessionAls(rolle)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].verknuepfteDokumente.length()").value(rolle == FrontendUserRole.ADMIN ? 2 : 1))
                .andExpect(jsonPath("$[0].verknuepfteDokumente[0].id").value(2));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    @DisplayName("Lieferanten-Detail beim Laden und Speichern enthält keine verbotenen Verknüpfungen")
    void detailFiltertVerknuepfteDokumente(boolean speichern) throws Exception {
        given(belegService.findCaller(any(), any())).willReturn(mitarbeiter(5L));
        mitarbeiterDarfSehen(5L, LieferantDokumentTyp.LIEFERSCHEIN);
        var detail = new LieferantDetailDto();
        detail.setId(7L);
        detail.setDokumente(List.of(dokumentMitVerknuepfungen()));
        given(lieferantenDetailService.loadDetails(7L)).willReturn(detail);
        var lieferant = new Lieferanten();
        lieferant.setId(7L);
        lieferant.setLieferantenname("Muster Lieferant GmbH");
        given(lieferantenRepository.findById(7L)).willReturn(java.util.Optional.of(lieferant));
        var request = speichern
                ? put("/api/lieferanten/7").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"lieferantenname\":\"Muster Lieferant GmbH\"}")
                : get("/api/lieferanten/7");

        mockMvc.perform(request.with(sessionAls(FrontendUserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dokumente[0].verknuepfteDokumente.length()").value(1))
                .andExpect(jsonPath("$.dokumente[0].verknuepfteDokumente[0].id").value(2));
    }

    @Test
    @DisplayName("Admin-Session: alle Dokumenttypen")
    void listeAdminSiehtAlleTypen() throws Exception {
        given(lieferantenRepository.existsById(7L)).willReturn(true);

        mockMvc.perform(get("/api/lieferanten/7/dokumente").with(sessionAls(FrontendUserRole.ADMIN)))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(weitergegebeneTypen(7L, null))
                .isEqualTo(EnumSet.allOf(LieferantDokumentTyp.class));
    }

    @Test
    @DisplayName("Benutzer-Session ohne Token: nur Typen der Abteilung, Liste kommt gefiltert zurück")
    void listeBenutzerBekommtNurErlaubteTypen() throws Exception {
        given(lieferantenRepository.existsById(7L)).willReturn(true);
        given(belegService.findCaller(any(), any())).willReturn(mitarbeiter(5L));
        mitarbeiterDarfSehen(5L, LieferantDokumentTyp.RECHNUNG);
        given(dokumentService.getDokumenteFiltered(eq(7L), any(), any())).willReturn(List.of(
                LieferantDokumentDto.Response.builder().id(1L).typ(LieferantDokumentTyp.RECHNUNG).build()));

        mockMvc.perform(get("/api/lieferanten/7/dokumente").with(sessionAls(FrontendUserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].typ").value("RECHNUNG"));

        org.assertj.core.api.Assertions.assertThat(weitergegebeneTypen(7L, null))
                .containsExactly(LieferantDokumentTyp.RECHNUNG);
    }

    @Test
    @DisplayName("Benutzer fragt verbotenen Typ an: Typ-Filter wird mit den erlaubten Typen weitergereicht")
    void listeVerbotenerTypWirdMitErlaubtenTypenGeprueft() throws Exception {
        given(lieferantenRepository.existsById(7L)).willReturn(true);
        given(belegService.findCaller(any(), any())).willReturn(mitarbeiter(5L));
        mitarbeiterDarfSehen(5L, LieferantDokumentTyp.RECHNUNG);
        given(dokumentService.getDokumenteFiltered(eq(7L), any(), eq(LieferantDokumentTyp.ANGEBOT)))
                .willReturn(List.of());

        mockMvc.perform(get("/api/lieferanten/7/dokumente").param("typ", "ANGEBOT")
                .with(sessionAls(FrontendUserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        org.assertj.core.api.Assertions.assertThat(weitergegebeneTypen(7L, LieferantDokumentTyp.ANGEBOT))
                .doesNotContain(LieferantDokumentTyp.ANGEBOT);
    }

    @Test
    @DisplayName("Benutzer ohne verknüpften Mitarbeiter: leere Liste, kein Fehler")
    void listeBenutzerOhneMitarbeiterBekommtNichts() throws Exception {
        given(lieferantenRepository.existsById(7L)).willReturn(true);
        given(belegService.findCaller(any(), any())).willReturn(null);

        mockMvc.perform(get("/api/lieferanten/7/dokumente").with(sessionAls(FrontendUserRole.USER)))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(weitergegebeneTypen(7L, null)).isEmpty();
    }

    @Test
    @DisplayName("Mobile: gültiges Token ohne Session nutzt die Rechte des Mitarbeiters")
    void listeMobileTokenNutztMitarbeiterRechte() throws Exception {
        given(lieferantenRepository.existsById(7L)).willReturn(true);
        given(belegService.findByToken("token-max")).willReturn(mitarbeiter(8L));
        mitarbeiterDarfSehen(8L, LieferantDokumentTyp.LIEFERSCHEIN);

        mockMvc.perform(get("/api/lieferanten/7/dokumente")
                .param("typ", "LIEFERSCHEIN").param("token", "token-max"))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(weitergegebeneTypen(7L, LieferantDokumentTyp.LIEFERSCHEIN))
                .containsExactly(LieferantDokumentTyp.LIEFERSCHEIN);
    }

    @ParameterizedTest
    @ValueSource(strings = { "unbekannt", "'; DROP TABLE x; --", "<script>alert(1)</script>" })
    @DisplayName("Ohne Session und mit ungültigem Token: 401, keine Dokumente")
    void listeUngueltigesTokenWird401(String token) throws Exception {
        mockMvc.perform(get("/api/lieferanten/7/dokumente").param("token", token))
                .andExpect(status().isUnauthorized());

        verify(dokumentService, never()).getDokumenteFiltered(anyLong(), any(), any());
        verifyNoInteractions(lieferantenRepository);
    }

    @Test
    @DisplayName("Ohne Session und ohne Token: 401 statt aller Dokumente")
    void listeAnonymWird401() throws Exception {
        mockMvc.perform(get("/api/lieferanten/7/dokumente"))
                .andExpect(status().isUnauthorized());

        verify(dokumentService, never()).getDokumenteFiltered(anyLong(), any(), any());
        verify(dokumentService, never()).getDokumenteByLieferant(anyLong(), any());
    }

    @ParameterizedTest
    @ValueSource(longs = { -1L, 0L, Long.MAX_VALUE })
    @DisplayName("Ungültige Lieferanten-IDs: 404")
    void listeUngueltigeIds(long id) throws Exception {
        mockMvc.perform(get("/api/lieferanten/" + id + "/dokumente").with(sessionAls(FrontendUserRole.ADMIN)))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @EnumSource(value = FrontendUserRole.class, names = { "USER", "ADMIN" })
    @DisplayName("Positionssuche nutzt auch ohne Token die Rechte der PC-Sitzung")
    void positionssucheNutztSessionRechte(FrontendUserRole rolle) throws Exception {
        given(lieferantenRepository.existsById(7L)).willReturn(true);
        given(belegService.findCaller(any(), any())).willReturn(mitarbeiter(5L));
        mitarbeiterDarfSehen(5L, LieferantDokumentTyp.WERKSTOFFZEUGNIS);
        Set<LieferantDokumentTyp> erlaubt = rolle == FrontendUserRole.ADMIN
                ? EnumSet.allOf(LieferantDokumentTyp.class) : EnumSet.of(LieferantDokumentTyp.WERKSTOFFZEUGNIS);
        given(dokumentSucheService.suchePositionen("S235JR", 7L, erlaubt, null, null))
                .willReturn(java.util.Map.of(9L, new org.example.kalkulationsprogramm.dto.PositionsTrefferDto(
                        9L, "Flachstahl · S235JR", 0)));

        mockMvc.perform(get("/api/lieferanten/7/dokumente/positionssuche").param("q", "S235JR")
                        .with(sessionAls(rolle)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].dokumentId").value(9));
        verify(dokumentSucheService).suchePositionen("S235JR", 7L, erlaubt, null, null);
    }

    @Test
    @DisplayName("Positionssuche mit Mobile-Token verwendet dessen Dokumentrechte")
    void positionssucheNutztMobileRechte() throws Exception {
        given(lieferantenRepository.existsById(7L)).willReturn(true);
        given(belegService.findByToken("token-max")).willReturn(mitarbeiter(8L));
        mitarbeiterDarfSehen(8L, LieferantDokumentTyp.LIEFERSCHEIN);

        mockMvc.perform(get("/api/lieferanten/7/dokumente/positionssuche").param("q", "S235JR")
                        .param("token", "token-max"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        verify(dokumentSucheService).suchePositionen("S235JR", 7L,
                EnumSet.of(LieferantDokumentTyp.LIEFERSCHEIN), null, null);
    }

    @Test
    @DisplayName("Positionssuche ohne Mitarbeiter-Zuordnung bekommt keine Dokumenttypen")
    void positionssucheOhneMitarbeiter() throws Exception {
        given(lieferantenRepository.existsById(7L)).willReturn(true);

        mockMvc.perform(get("/api/lieferanten/7/dokumente/positionssuche").param("q", "S235JR")
                        .with(sessionAls(FrontendUserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        verify(dokumentSucheService).suchePositionen("S235JR", 7L,
                EnumSet.noneOf(LieferantDokumentTyp.class), null, null);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "ungueltig" })
    @DisplayName("Anonyme Positionssuche wird vor dem Datenzugriff abgewiesen")
    void positionssucheAnonym(String token) throws Exception {
        mockMvc.perform(get("/api/lieferanten/7/dokumente/positionssuche").param("q", "S235JR")
                        .param("token", token))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(dokumentSucheService, lieferantenRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "WERKSTOFFZEUGNIS", "RECHNUNG" })
    @DisplayName("Eingangssuche schneidet Typfilter mit den Dokumentrechten vor der Positionsabfrage")
    void eingangssucheBegrenztPositionenAufRechte(String typ) throws Exception {
        given(belegService.findCaller(any(), any())).willReturn(mitarbeiter(5L));
        mitarbeiterDarfSehen(5L, LieferantDokumentTyp.WERKSTOFFZEUGNIS);
        given(lieferantGdRepo.findAllSortedByDatum()).willReturn(new java.util.ArrayList<>(List.of(
                eingang(1L, LieferantDokumentTyp.WERKSTOFFZEUGNIS), eingang(2L, LieferantDokumentTyp.RECHNUNG))));
        Set<LieferantDokumentTyp> erlaubt = "RECHNUNG".equals(typ)
                ? EnumSet.noneOf(LieferantDokumentTyp.class) : EnumSet.of(LieferantDokumentTyp.WERKSTOFFZEUGNIS);
        given(dokumentSucheService.suchePositionen("S235JR", null, erlaubt, null, null))
                .willReturn(erlaubt.isEmpty() ? java.util.Map.of() : java.util.Map.of(1L,
                        new org.example.kalkulationsprogramm.dto.PositionsTrefferDto(1L, "S235JR", 0)));
        var request = get("/api/dokumentuebersicht/eingang").param("search", "S235JR")
                .with(sessionAls(FrontendUserRole.USER));
        if (!typ.isEmpty()) request.param("typ", typ);

        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(erlaubt.isEmpty() ? 0 : 1));
        verify(dokumentSucheService).suchePositionen("S235JR", null, erlaubt, null, null);
    }

    // ------------------------------------------------- GET /api/lieferanten/{id} (Detail)

    @Test
    @DisplayName("Lieferanten-Detail: Dokumente und Zähler werden auf sichtbare Typen beschränkt")
    void detailFiltertDokumente() throws Exception {
        given(belegService.findCaller(any(), any())).willReturn(mitarbeiter(5L));
        mitarbeiterDarfSehen(5L, LieferantDokumentTyp.RECHNUNG);
        var detail = new LieferantDetailDto();
        detail.setId(7L);
        detail.setLieferantenname("Muster Lieferant GmbH");
        detail.setDokumente(List.of(
                LieferantDokumentDto.Response.builder().id(1L).typ(LieferantDokumentTyp.RECHNUNG).build(),
                LieferantDokumentDto.Response.builder().id(2L).typ(LieferantDokumentTyp.ANGEBOT).build()));
        detail.setDokumenteAnzahl(2L);
        given(lieferantenDetailService.loadDetails(7L)).willReturn(detail);

        mockMvc.perform(get("/api/lieferanten/7").with(sessionAls(FrontendUserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lieferantenname").value("Muster Lieferant GmbH"))
                .andExpect(jsonPath("$.dokumente.length()").value(1))
                .andExpect(jsonPath("$.dokumente[0].typ").value("RECHNUNG"))
                .andExpect(jsonPath("$.dokumenteAnzahl").value(1));
    }

    @Test
    @DisplayName("Lieferanten-Detail ohne Anmeldung: Stammdaten ja, Dokumente nein")
    void detailAnonymOhneDokumente() throws Exception {
        var detail = new LieferantDetailDto();
        detail.setId(7L);
        detail.setLieferantenname("Muster Lieferant GmbH");
        detail.setDokumente(List.of(
                LieferantDokumentDto.Response.builder().id(1L).typ(LieferantDokumentTyp.RECHNUNG).build()));
        given(lieferantenDetailService.loadDetails(7L)).willReturn(detail);

        mockMvc.perform(get("/api/lieferanten/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lieferantenname").value("Muster Lieferant GmbH"))
                .andExpect(jsonPath("$.dokumente").isEmpty());
    }

    @Test
    @DisplayName("Lieferanten-Detail nurStammdaten: Zähler zählt nur sichtbare Typen")
    void detailNurStammdatenZaehlerGefiltert() throws Exception {
        given(belegService.findCaller(any(), any())).willReturn(mitarbeiter(5L));
        mitarbeiterDarfSehen(5L, LieferantDokumentTyp.RECHNUNG);
        var detail = new LieferantDetailDto();
        detail.setId(7L);
        detail.setDokumenteAnzahl(25L);
        given(lieferantenDetailService.loadStammdaten(7L)).willReturn(detail);
        given(dokumentService.zaehleDokumente(7L, EnumSet.of(LieferantDokumentTyp.RECHNUNG))).willReturn(3L);

        mockMvc.perform(get("/api/lieferanten/7").param("nurStammdaten", "true")
                .with(sessionAls(FrontendUserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dokumenteAnzahl").value(3));
    }

    // ------------------------------------- GET /api/lieferanten/{id}/dokumente/{did}/download

    private static LieferantDokument dokumentVon(long id, long lieferantId, LieferantDokumentTyp typ) {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(lieferantId);
        LieferantDokument d = new LieferantDokument();
        d.setId(id);
        d.setLieferant(lieferant);
        d.setTyp(typ);
        return d;
    }

    @Test
    @DisplayName("Download ohne Anmeldung: 401, Dokument wird nicht einmal geladen")
    void downloadAnonymWird401() throws Exception {
        mockMvc.perform(get("/api/lieferanten/7/dokumente/9/download"))
                .andExpect(status().isUnauthorized());

        verify(dokumentService, never()).findById(anyLong());
    }

    @Test
    @DisplayName("Download eines nicht erlaubten Dokumenttyps: 404 statt Datei")
    void downloadVerbotenerTyp() throws Exception {
        given(belegService.findCaller(any(), any())).willReturn(mitarbeiter(5L));
        mitarbeiterDarfSehen(5L, LieferantDokumentTyp.RECHNUNG);
        given(dokumentService.findById(9L)).willReturn(dokumentVon(9L, 7L, LieferantDokumentTyp.ANGEBOT));

        mockMvc.perform(get("/api/lieferanten/7/dokumente/9/download").with(sessionAls(FrontendUserRole.USER)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Download mit Mobile-Token liefert die Datei eines erlaubten Typs")
    void downloadErlaubterTypPassiertPruefung() throws Exception {
        given(belegService.findByToken("token-max")).willReturn(mitarbeiter(8L));
        mitarbeiterDarfSehen(8L, LieferantDokumentTyp.LIEFERSCHEIN);
        byte[] datei = downloadDatei(LieferantDokumentTyp.LIEFERSCHEIN);

        mockMvc.perform(get("/api/lieferanten/7/dokumente/9/download").param("token", "token-max"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(datei));
    }

    @Test
    @DisplayName("Download mit Admin-Session ohne Mitarbeiter-Zuordnung liefert die Datei")
    void downloadAdminOhneMitarbeiter() throws Exception {
        byte[] datei = downloadDatei(LieferantDokumentTyp.RECHNUNG);

        mockMvc.perform(get("/api/lieferanten/7/dokumente/9/download").with(sessionAls(FrontendUserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().bytes(datei));
    }

    private byte[] downloadDatei(LieferantDokumentTyp typ) throws Exception {
        byte[] datei = "%PDF-1.4 Dummy-Dokument Max Mustermann".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(uploadVerzeichnis.resolve("muster.pdf"), datei);
        ReflectionTestUtils.setField(lieferantenController, "uploadDir", uploadVerzeichnis.toString());
        LieferantDokument dokument = dokumentVon(9L, 7L, typ);
        dokument.setOriginalDateiname("muster.pdf");
        dokument.setGespeicherterDateiname("muster.pdf");
        given(dokumentService.findById(9L)).willReturn(dokument);
        return datei;
    }

    // ------------------------------------------- GET /api/dokumentuebersicht/eingang

    @Test
    @DisplayName("Dokumentübersicht Eingang: Admin sieht alle Typen")
    void eingangAdminSiehtAlles() throws Exception {
        given(lieferantGdRepo.findAllSortedByDatum()).willReturn(new java.util.ArrayList<>(List.of(
                eingang(1L, LieferantDokumentTyp.RECHNUNG), eingang(2L, LieferantDokumentTyp.ANGEBOT))));

        mockMvc.perform(get("/api/dokumentuebersicht/eingang").with(sessionAls(FrontendUserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("Dokumentübersicht Eingang: Benutzer sieht nur erlaubte Typen")
    void eingangBenutzerSiehtNurErlaubte() throws Exception {
        given(belegService.findCaller(any(), any())).willReturn(mitarbeiter(5L));
        mitarbeiterDarfSehen(5L, LieferantDokumentTyp.RECHNUNG);
        given(lieferantGdRepo.findAllSortedByDatum()).willReturn(new java.util.ArrayList<>(List.of(
                eingang(1L, LieferantDokumentTyp.RECHNUNG), eingang(2L, LieferantDokumentTyp.ANGEBOT))));

        mockMvc.perform(get("/api/dokumentuebersicht/eingang").with(sessionAls(FrontendUserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].typ").value("RECHNUNG"));
    }

    @Test
    @DisplayName("Dokumentübersicht Eingang: verbotener Typ-Filter liefert nichts")
    void eingangVerbotenerTypFilter() throws Exception {
        given(belegService.findCaller(any(), any())).willReturn(mitarbeiter(5L));
        mitarbeiterDarfSehen(5L, LieferantDokumentTyp.RECHNUNG);
        given(lieferantGdRepo.findAllSortedByDatum()).willReturn(new java.util.ArrayList<>(List.of(
                eingang(1L, LieferantDokumentTyp.RECHNUNG), eingang(2L, LieferantDokumentTyp.ANGEBOT))));

        mockMvc.perform(get("/api/dokumentuebersicht/eingang").param("typ", "ANGEBOT")
                .with(sessionAls(FrontendUserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("Dokumentübersicht Eingang: Benutzer ohne Mitarbeiter bekommt nichts")
    void eingangBenutzerOhneMitarbeiter() throws Exception {
        given(belegService.findCaller(any(), any())).willReturn(null);
        given(lieferantGdRepo.findAllSortedByDatum()).willReturn(new java.util.ArrayList<>(List.of(
                eingang(1L, LieferantDokumentTyp.RECHNUNG))));

        mockMvc.perform(get("/api/dokumentuebersicht/eingang").with(sessionAls(FrontendUserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("Dokumentübersicht Eingang: ohne Anmeldung 401")
    void eingangAnonymWird401() throws Exception {
        mockMvc.perform(get("/api/dokumentuebersicht/eingang"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(lieferantGdRepo);
    }
}
