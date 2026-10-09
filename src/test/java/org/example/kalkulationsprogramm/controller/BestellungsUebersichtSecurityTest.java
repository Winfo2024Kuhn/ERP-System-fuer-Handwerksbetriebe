package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.repository.BelegKostenstellenAnteilRepository;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.KostenstelleRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentProjektAnteilRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.service.BelegAuditService;
import org.example.kalkulationsprogramm.service.BelegService;
import org.example.kalkulationsprogramm.service.BestellungsUebersichtService;
import org.example.kalkulationsprogramm.service.KettenVorschlagService;
import org.example.kalkulationsprogramm.service.LieferantDokumentService;
import org.example.kalkulationsprogramm.service.RechnungsVorschlagService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Abhängen und Rechnung hochladen ändern Daten – nur angemeldet und nur mit
 * CSRF-Token.
 */
@WebMvcTest(controllers = BestellungsUebersichtController.class)
@Import({ SecurityConfig.class, BestellungsUebersichtSecurityTest.EchteFilterBeans.class })
class BestellungsUebersichtSecurityTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class EchteFilterBeans {
        @org.springframework.context.annotation.Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    @Autowired private MockMvc mockMvc;

    @MockBean private LieferantDokumentRepository dokumentRepository;
    @MockBean private LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository;
    @MockBean private ProjektRepository projektRepository;
    @MockBean private ProjektDokumentRepository projektDokumentRepository;
    @MockBean private LieferantDokumentProjektAnteilRepository projektAnteilRepository;
    @MockBean private KostenstelleRepository kostenstelleRepository;
    @MockBean private FrontendUserProfileRepository frontendUserProfileRepository;
    @MockBean private BelegRepository belegRepository;
    @MockBean private BelegKostenstellenAnteilRepository belegKostenstellenAnteilRepository;
    @MockBean private BelegService belegService;
    @MockBean private BelegAuditService belegAuditService;
    @MockBean private RechnungsVorschlagService rechnungsVorschlagService;
    @MockBean private KettenVorschlagService kettenVorschlagService;
    @MockBean private LieferantDokumentService lieferantDokumentService;
    @MockBean private BestellungsUebersichtService bestellungsUebersichtService;
    @MockBean private org.example.kalkulationsprogramm.service.LieferantDokumentZuordnungService zuordnungService;
    @MockBean private org.example.kalkulationsprogramm.service.LieferantDokumentZugriffService zugriffService;
    @MockBean private FrontendUserDetailsService frontendUserDetailsService;

    @org.junit.jupiter.api.BeforeEach
    void alleTypenSichtbar() {
        // Standard: angemeldet, alle Dokumenttypen sichtbar. Die Rechte-Fälle stehen weiter unten.
        given(zugriffService.sichtbareTypen(any(), any())).willReturn(java.util.Optional.of(
                java.util.EnumSet.allOf(LieferantDokumentTyp.class)));
        given(zugriffService.istSichtbar(any(), any())).willReturn(true);
    }

    private static final String ABHAENGEN = "/api/bestellungen-uebersicht/abhaengen";
    private static final String HOCHLADEN = "/api/bestellungen-uebersicht/rechnung-hochladen";

    private final MockMultipartFile pdf = new MockMultipartFile("datei", "rechnung.pdf", "application/pdf",
            "%PDF-1.7".getBytes());

    @Test
    @DisplayName("Abhängen ohne Anmeldung: 401")
    void abhaengenOhneLogin() throws Exception {
        mockMvc.perform(post(ABHAENGEN).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dokumentId\": 5}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(rechnungsVorschlagService);
    }

    @Test
    @WithMockUser
    @DisplayName("Abhängen ohne CSRF-Token: 403")
    void abhaengenOhneCsrf() throws Exception {
        mockMvc.perform(post(ABHAENGEN).contentType(MediaType.APPLICATION_JSON).content("{\"dokumentId\": 5}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(rechnungsVorschlagService);
    }

    @Test
    @WithMockUser
    @DisplayName("Abhängen angemeldet mit CSRF: 200")
    void abhaengenErlaubt() throws Exception {
        given(rechnungsVorschlagService.haengeAb(eq(5L), any())).willReturn(2);

        mockMvc.perform(post(ABHAENGEN).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dokumentId\": 5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.geloest").value(2));
    }

    @Test
    @WithMockUser
    @DisplayName("Abhängen mit ungültiger ID: 400")
    void abhaengenUngueltigeId() throws Exception {
        mockMvc.perform(post(ABHAENGEN).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dokumentId\": -1}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(rechnungsVorschlagService);
    }

    @Test
    @DisplayName("Hochladen ohne Anmeldung: 401")
    void hochladenOhneLogin() throws Exception {
        mockMvc.perform(multipart(HOCHLADEN).file(pdf).param("bestellDokumentId", "10").with(csrf()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(lieferantDokumentService);
    }

    @Test
    @WithMockUser
    @DisplayName("Hochladen ohne CSRF-Token: 403")
    void hochladenOhneCsrf() throws Exception {
        mockMvc.perform(multipart(HOCHLADEN).file(pdf).param("bestellDokumentId", "10"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(lieferantDokumentService);
    }

    @Test
    @WithMockUser
    @DisplayName("Hochladen angemeldet mit CSRF: 200 mit der neuen Rechnung")
    void hochladenErlaubt() throws Exception {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(4L);
        LieferantDokument rechnung = new LieferantDokument();
        rechnung.setId(77L);
        rechnung.setTyp(LieferantDokumentTyp.RECHNUNG);
        rechnung.setLieferant(lieferant);
        rechnung.setOriginalDateiname("rechnung.pdf");
        rechnung.setGespeicherterDateiname("x_rechnung.pdf");
        rechnung.setUploadDatum(LocalDateTime.of(2026, 10, 1, 9, 0));
        given(lieferantDokumentService.rechnungZuBestellungHochladen(eq(10L), any(), any())).willReturn(rechnung);

        mockMvc.perform(multipart(HOCHLADEN).file(pdf).param("bestellDokumentId", "10").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(77))
                .andExpect(jsonPath("$.typ").value("RECHNUNG"))
                .andExpect(jsonPath("$.ausgeblendet").value(false));
    }

    // ------------------------------------------------------------ Dokumentrechte

    private static final String BASIS = "/api/bestellungen-uebersicht";

    private void nurSichtbar(LieferantDokumentTyp... typen) {
        given(zugriffService.sichtbareTypen(any(), any())).willReturn(java.util.Optional.of(
                java.util.EnumSet.copyOf(java.util.List.of(typen))));
    }

    @Test
    @WithMockUser
    @DisplayName("Abhängen eines nicht sichtbaren Dokuments: 404, nichts wird gelöst")
    void abhaengenNichtSichtbar() throws Exception {
        given(zugriffService.istSichtbar(eq(5L), any())).willReturn(false);

        mockMvc.perform(post(ABHAENGEN).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dokumentId\": 5}"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(rechnungsVorschlagService);
    }

    // ------------------------------------------------- Dokument zur Kette hinzufügen

    private static final String KETTE_VERKNUEPFEN = BASIS + "/ketten-verknuepfen";
    private static final String KETTE_VORSCHLAEGE = BASIS + "/ketten-vorschlaege";

    @Test
    @DisplayName("Kette verknüpfen ohne Anmeldung: 401")
    void ketteVerknuepfenOhneLogin() throws Exception {
        mockMvc.perform(post(KETTE_VERKNUEPFEN).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"kettenDokumentId\": 10, \"dokumentId\": 20}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(kettenVorschlagService);
    }

    @Test
    @WithMockUser
    @DisplayName("Kette verknüpfen ohne CSRF-Token: 403")
    void ketteVerknuepfenOhneCsrf() throws Exception {
        mockMvc.perform(post(KETTE_VERKNUEPFEN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"kettenDokumentId\": 10, \"dokumentId\": 20}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(kettenVorschlagService);
    }

    @Test
    @WithMockUser
    @DisplayName("Kette verknüpfen: sichtbar -> 200, nicht sichtbar -> 404")
    void ketteVerknuepfenSichtbarkeit() throws Exception {
        given(zugriffService.istSichtbar(eq(10L), any())).willReturn(true);
        given(zugriffService.istSichtbar(eq(20L), any())).willReturn(true);
        mockMvc.perform(post(KETTE_VERKNUEPFEN).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"kettenDokumentId\": 10, \"dokumentId\": 20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        org.mockito.Mockito.verify(kettenVorschlagService).verknuepfe(eq(10L), eq(20L), any());

        given(zugriffService.istSichtbar(eq(20L), any())).willReturn(false);
        mockMvc.perform(post(KETTE_VERKNUEPFEN).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"kettenDokumentId\": 10, \"dokumentId\": 20}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    @DisplayName("Kette verknüpfen: unpassende Arten -> 400 mit verständlicher Meldung; ungültige IDs -> 400")
    void ketteVerknuepfenAbgelehnt() throws Exception {
        given(zugriffService.istSichtbar(any(), any())).willReturn(true);
        org.mockito.Mockito.doThrow(new org.example.kalkulationsprogramm.service.BelegAbgelehntException(
                "Rechnung und Angebot lassen sich nicht direkt verbinden."))
                .when(kettenVorschlagService).verknuepfe(eq(10L), eq(20L), any());

        mockMvc.perform(post(KETTE_VERKNUEPFEN).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"kettenDokumentId\": 10, \"dokumentId\": 20}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Rechnung und Angebot lassen sich nicht direkt verbinden."));

        for (String body : new String[] { "{\"kettenDokumentId\": 0, \"dokumentId\": 20}",
                "{\"kettenDokumentId\": 10, \"dokumentId\": -1}", "{\"kettenDokumentId\": 10}",
                "{\"kettenDokumentId\": \"'; DROP TABLE x; --\", \"dokumentId\": 20}" }) {
            mockMvc.perform(post(KETTE_VERKNUEPFEN).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("Ketten-Vorschläge ohne Anmeldung: 401")
    void ketteVorschlaegeOhneLogin() throws Exception {
        mockMvc.perform(get(KETTE_VORSCHLAEGE).param("dokumentIds", "1"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(bestellungsUebersichtService);
    }

    @Test
    @WithMockUser
    @DisplayName("Ketten-Vorschläge: gültig -> 200, ungültige IDs/Art -> 400, nichts sichtbar -> 404")
    void ketteVorschlaege() throws Exception {
        given(bestellungsUebersichtService.kettenVorschlaege(any(), eq(false), eq(LieferantDokumentTyp.WERKSTOFFZEUGNIS), any()))
                .willReturn(java.util.Optional.of(java.util.List.of()));
        mockMvc.perform(get(KETTE_VORSCHLAEGE).param("dokumentIds", "1").param("typ", "WERKSTOFFZEUGNIS"))
                .andExpect(status().isOk());

        mockMvc.perform(get(KETTE_VORSCHLAEGE).param("dokumentIds", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get(KETTE_VORSCHLAEGE).param("dokumentIds", String.valueOf(Long.MAX_VALUE)).param("typ", "SONSTIG"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(KETTE_VORSCHLAEGE).param("dokumentIds", "1").param("typ", "<script>alert(1)</script>"))
                .andExpect(status().isBadRequest());

        given(bestellungsUebersichtService.kettenVorschlaege(any(), eq(false), eq(null), any()))
                .willReturn(java.util.Optional.empty());
        mockMvc.perform(get(KETTE_VORSCHLAEGE).param("dokumentIds", "99")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    @DisplayName("Rechnung verknüpfen: Bestell- oder Rechnungsdokument nicht sichtbar -> 404")
    void verknuepfenNichtSichtbar() throws Exception {
        given(zugriffService.istSichtbar(eq(10L), any())).willReturn(true);
        given(zugriffService.istSichtbar(eq(20L), any())).willReturn(false);

        mockMvc.perform(post(BASIS + "/rechnung-verknuepfen").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bestellDokumentId\": 10, \"rechnungDokumentId\": 20}"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(rechnungsVorschlagService);
    }

    @Test
    @WithMockUser
    @DisplayName("Rechnung hochladen: Bestelldokument nicht sichtbar -> 404; ohne Rechnungs-Recht -> 403")
    void hochladenNichtSichtbar() throws Exception {
        given(zugriffService.istSichtbar(eq(10L), any())).willReturn(false);
        mockMvc.perform(multipart(HOCHLADEN).file(pdf).param("bestellDokumentId", "10").with(csrf()))
                .andExpect(status().isNotFound());

        given(zugriffService.istSichtbar(eq(10L), any())).willReturn(true);
        nurSichtbar(LieferantDokumentTyp.LIEFERSCHEIN);
        mockMvc.perform(multipart(HOCHLADEN).file(pdf).param("bestellDokumentId", "10").with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(lieferantDokumentService);
    }

    @Test
    @WithMockUser
    @DisplayName("Ausblenden/Einblenden berührt nur sichtbare Dokumente der Kette")
    void ausblendenNurSichtbare() throws Exception {
        nurSichtbar(LieferantDokumentTyp.LIEFERSCHEIN);
        LieferantDokument lieferschein = new LieferantDokument();
        lieferschein.setId(1L);
        lieferschein.setTyp(LieferantDokumentTyp.LIEFERSCHEIN);
        LieferantDokument rechnung = new LieferantDokument();
        rechnung.setId(2L);
        rechnung.setTyp(LieferantDokumentTyp.RECHNUNG);
        given(dokumentRepository.findAllById(any())).willReturn(java.util.List.of(lieferschein, rechnung));

        mockMvc.perform(post(BASIS + "/ausblenden").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dokumentIds\": [1, 2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.geaendert").value(1));

        org.assertj.core.api.Assertions.assertThat(lieferschein.isAusgeblendet()).isTrue();
        org.assertj.core.api.Assertions.assertThat(rechnung.isAusgeblendet()).isFalse();
    }

    @Test
    @WithMockUser
    @DisplayName("Zuordnen, Lagerbestellung und Zuordnung aufheben: nicht sichtbares Dokument wird abgewiesen")
    void zuordnungenNichtSichtbar() throws Exception {
        given(zugriffService.istSichtbar(any(), any())).willReturn(false);
        nurSichtbar(LieferantDokumentTyp.LIEFERSCHEIN);
        given(geschaeftsdokumentRepository.findById(5L)).willReturn(java.util.Optional.of(
                gdMitTyp(5L, LieferantDokumentTyp.RECHNUNG)));

        mockMvc.perform(post(BASIS + "/zuordnen").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"geschaeftsdokumentId\": 5, \"projektAnteile\": []}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(BASIS + "/lagerbestellung/5").with(csrf()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(delete(BASIS + "/zuordnung/5").with(csrf()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(zuordnungService);
        org.mockito.Mockito.verify(geschaeftsdokumentRepository, org.mockito.Mockito.never()).save(any());
    }

    private static org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument gdMitTyp(long id,
            LieferantDokumentTyp typ) {
        LieferantDokument dokument = new LieferantDokument();
        dokument.setId(id);
        dokument.setTyp(typ);
        var gd = new org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument();
        gd.setId(id);
        gd.setDokument(dokument);
        return gd;
    }

    private static RequestPostProcessor csrf() {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf();
    }
}
