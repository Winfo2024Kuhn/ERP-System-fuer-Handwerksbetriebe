package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.example.kalkulationsprogramm.domain.ErfassungsQuelle;
import org.example.kalkulationsprogramm.domain.KorrekturTyp;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.ZeitkontoKorrektur;
import org.example.kalkulationsprogramm.service.AngemeldeterMitarbeiterService;
import org.example.kalkulationsprogramm.service.ZeitkontoKorrekturService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

/**
 * GoBD: Der Bearbeiter einer Zeitkonto-Korrektur kommt aus der Anmeldung. Ein vom
 * Client mitgeschicktes {@code erstelltVonId}/{@code bearbeiterId} darf nie im
 * Protokoll landen.
 */
@WebMvcTest(ZeitkontoKorrekturController.class)
@AutoConfigureMockMvc(addFilters = false)
class ZeitkontoKorrekturControllerTest {

    private static final String KEIN_MITARBEITER = AngemeldeterMitarbeiterService.KEIN_MITARBEITER;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ZeitkontoKorrekturService korrekturService;

    @MockitoBean
    private AngemeldeterMitarbeiterService angemeldeterMitarbeiter;

    private Mitarbeiter angemeldet;
    private Mitarbeiter betroffen;

    @BeforeEach
    void setUp() {
        angemeldet = mitarbeiter(7L, "Max", "Mustermann");
        betroffen = mitarbeiter(3L, "Erika", "Musterfrau");
        given(angemeldeterMitarbeiter.bearbeiter(any())).willReturn(angemeldet);
    }

    private static Mitarbeiter mitarbeiter(long id, String vorname, String nachname) {
        Mitarbeiter m = new Mitarbeiter();
        m.setId(id);
        m.setVorname(vorname);
        m.setNachname(nachname);
        return m;
    }

    private ZeitkontoKorrektur korrektur(long id) {
        ZeitkontoKorrektur k = new ZeitkontoKorrektur();
        k.setId(id);
        k.setMitarbeiter(betroffen);
        k.setDatum(LocalDate.of(2026, 9, 30));
        k.setStunden(new BigDecimal("8.50"));
        k.setGrund("Überstundenausgleich");
        k.setTyp(KorrekturTyp.STUNDEN);
        k.setErstelltAm(LocalDateTime.of(2026, 10, 1, 9, 0));
        k.setErstelltVon(angemeldet);
        return k;
    }

    private void keinMitarbeiterZugeordnet() {
        given(angemeldeterMitarbeiter.bearbeiter(any()))
                .willThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, KEIN_MITARBEITER));
    }

    // ==================== Erstellen ====================

    @Test
    void erstellen_ProtokolliertAngemeldetenMitarbeiterUndIgnoriertErstelltVonIdImBody() throws Exception {
        given(korrekturService.erstelleKorrektur(eq(3L), any(), any(), any(), eq(7L),
                eq(ErfassungsQuelle.DESKTOP), eq(KorrekturTyp.STUNDEN))).willReturn(korrektur(11L));

        mockMvc.perform(post("/api/zeitkonto/korrekturen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mitarbeiterId": 3, "stunden": 8.5, "datum": "2026-09-30",
                                 "grund": "Überstundenausgleich", "erstelltVonId": 3, "typ": "STUNDEN"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.erstelltVon").value("Max Mustermann"));

        verify(korrekturService).erstelleKorrektur(eq(3L), eq(new BigDecimal("8.5")),
                eq(LocalDate.of(2026, 9, 30)), eq("Überstundenausgleich"), eq(7L),
                eq(ErfassungsQuelle.DESKTOP), eq(KorrekturTyp.STUNDEN));
    }

    @Test
    void erstellen_OhneZugeordnetenMitarbeiterGibt403() throws Exception {
        keinMitarbeiterZugeordnet();

        mockMvc.perform(post("/api/zeitkonto/korrekturen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mitarbeiterId": 3, "stunden": 8.5, "datum": "2026-09-30", "grund": "Ausgleich"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(KEIN_MITARBEITER));

        verifyNoInteractions(korrekturService);
    }

    @Test
    void erstellen_FachlicherFehlerGibt400() throws Exception {
        given(korrekturService.erstelleKorrektur(anyLong(), any(), any(), any(), anyLong(), any(), any()))
                .willThrow(new IllegalArgumentException("Begründung ist ein Pflichtfeld für GoBD-Konformität"));

        mockMvc.perform(post("/api/zeitkonto/korrekturen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mitarbeiterId": 3, "stunden": 8.5, "datum": "2026-09-30", "grund": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Begründung ist ein Pflichtfeld für GoBD-Konformität"));
    }

    @Test
    void erstellen_ReichtSqlUndXssNurAlsTextDurch() throws Exception {
        String sql = "'; DROP TABLE zeitkonto_korrektur; --";
        given(korrekturService.erstelleKorrektur(anyLong(), any(), any(), any(), anyLong(), any(), any()))
                .willReturn(korrektur(12L));

        mockMvc.perform(post("/api/zeitkonto/korrekturen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mitarbeiterId\": 3, \"stunden\": 1, \"datum\": \"2026-09-30\", \"grund\": \""
                                + sql + "\"}"))
                .andExpect(status().isCreated());

        verify(korrekturService).erstelleKorrektur(eq(3L), any(), any(), eq(sql), eq(7L), any(), any());
    }

    // ==================== Ändern ====================

    @Test
    void aendern_ProtokolliertAngemeldetenMitarbeiterUndIgnoriertBearbeiterIdImBody() throws Exception {
        given(korrekturService.aendereKorrektur(eq(11L), any(), any(), eq(7L), any(), eq(ErfassungsQuelle.DESKTOP)))
                .willReturn(korrektur(11L));

        mockMvc.perform(put("/api/zeitkonto/korrekturen/11")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stunden": 10, "bearbeiterId": 3, "aenderungsgrund": "<script>alert(1)</script>"}
                                """))
                .andExpect(status().isOk());

        verify(korrekturService).aendereKorrektur(eq(11L), eq(new BigDecimal("10")), eq(null), eq(7L),
                eq("<script>alert(1)</script>"), eq(ErfassungsQuelle.DESKTOP));
    }

    @Test
    void aendern_OhneZugeordnetenMitarbeiterGibt403() throws Exception {
        keinMitarbeiterZugeordnet();

        mockMvc.perform(put("/api/zeitkonto/korrekturen/11")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stunden\": 10, \"aenderungsgrund\": \"Tippfehler\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(korrekturService);
    }

    // ==================== Stornieren ====================

    @Test
    void stornieren_ProtokolliertAngemeldetenMitarbeiterUndIgnoriertBearbeiterIdImBody() throws Exception {
        mockMvc.perform(delete("/api/zeitkonto/korrekturen/11")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bearbeiterId\": 3, \"stornierungsgrund\": \"Fehlerhafte Buchung\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(korrekturService).storniereKorrektur(11L, 7L, "Fehlerhafte Buchung", ErfassungsQuelle.DESKTOP);
    }

    @Test
    void stornieren_OhneZugeordnetenMitarbeiterGibt403() throws Exception {
        keinMitarbeiterZugeordnet();

        mockMvc.perform(delete("/api/zeitkonto/korrekturen/11")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stornierungsgrund\": \"Fehlerhafte Buchung\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(KEIN_MITARBEITER));

        verifyNoInteractions(korrekturService);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, Long.MAX_VALUE})
    void stornieren_UngueltigeIdGibt400(long id) throws Exception {
        doThrow(new IllegalArgumentException("Korrektur nicht gefunden: " + id))
                .when(korrekturService).storniereKorrektur(eq(id), anyLong(), any(), any());

        mockMvc.perform(delete("/api/zeitkonto/korrekturen/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stornierungsgrund\": \"Fehlerhafte Buchung\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Korrektur nicht gefunden: " + id));
    }
}
