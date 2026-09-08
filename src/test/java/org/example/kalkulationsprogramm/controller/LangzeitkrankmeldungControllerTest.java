package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.Langzeitkrankmeldung;
import org.example.kalkulationsprogramm.domain.LangzeitkrankmeldungPhaseTyp;
import org.example.kalkulationsprogramm.domain.LangzeitkrankmeldungStatus;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.dto.Langzeitkrankmeldung.LangzeitkrankmeldungDto;
import org.example.kalkulationsprogramm.service.LangzeitkrankmeldungService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import java.time.LocalDate;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc-Tests fuer {@link LangzeitkrankmeldungController}. Desktop-API fuer
 * Langzeitkrankmeldungen (Vorgabe des Projektinhabers vom 08.09.2026: nur am
 * PC gepflegt).
 */
@WebMvcTest(LangzeitkrankmeldungController.class)
@AutoConfigureMockMvc(addFilters = false)
class LangzeitkrankmeldungControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LangzeitkrankmeldungService service;

    private Langzeitkrankmeldung meldungMitId(Long id) {
        Mitarbeiter mitarbeiter = new Mitarbeiter();
        mitarbeiter.setId(1L);
        mitarbeiter.setVorname("Max");
        mitarbeiter.setNachname("Mustermann");

        Langzeitkrankmeldung meldung = new Langzeitkrankmeldung();
        meldung.setId(id);
        meldung.setMitarbeiter(mitarbeiter);
        meldung.setBeginn(LocalDate.of(2026, 1, 5));
        meldung.setLohnfortzahlungBis(LocalDate.of(2026, 2, 15));
        meldung.setStatus(LangzeitkrankmeldungStatus.LAUFEND);
        meldung.setVersion(3L);
        return meldung;
    }

    private LangzeitkrankmeldungDto dtoFuer(Langzeitkrankmeldung meldung) {
        LangzeitkrankmeldungDto dto = new LangzeitkrankmeldungDto();
        dto.setId(meldung.getId());
        dto.setMitarbeiterId(meldung.getMitarbeiter().getId());
        dto.setMitarbeiterName("Mustermann, Max");
        dto.setBeginn(meldung.getBeginn());
        dto.setLohnfortzahlungBis(meldung.getLohnfortzahlungBis());
        dto.setStatus(meldung.getStatus());
        dto.setVersion(meldung.getVersion());
        dto.setPhasen(java.util.List.of());
        return dto;
    }

    @Test
    void anlegen_HappyPath_Liefert201MitDto() throws Exception {
        Langzeitkrankmeldung gespeichert = meldungMitId(42L);
        LangzeitkrankmeldungDto dto = dtoFuer(gespeichert);

        given(service.anlegen(eq(1L), eq(LocalDate.of(2026, 1, 5)), any(), any()))
                .willReturn(gespeichert);
        given(service.toDto(gespeichert, false)).willReturn(dto);

        mockMvc.perform(post("/api/langzeitkrankmeldungen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mitarbeiterId": 1,
                                  "beginn": "2026-01-05",
                                  "notiz": "Interne Notiz"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.mitarbeiterId").value(1));
    }

    @Test
    void anlegen_UeberlappendeMeldung_Liefert400MitKlartext() throws Exception {
        given(service.anlegen(any(), any(), any(), any()))
                .willThrow(new IllegalStateException(
                        "Für diesen Mitarbeiter läuft bereits eine Krankmeldung seit 2026-01-01."));

        mockMvc.perform(post("/api/langzeitkrankmeldungen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mitarbeiterId": 1,
                                  "beginn": "2026-01-05"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "Für diesen Mitarbeiter läuft bereits eine Krankmeldung seit 2026-01-01."));
    }

    @Test
    void aendern_HappyPath_Liefert200MitDto() throws Exception {
        Langzeitkrankmeldung aktuell = meldungMitId(42L);
        Langzeitkrankmeldung geaendert = meldungMitId(42L);
        geaendert.setVersion(4L);
        LangzeitkrankmeldungDto dto = dtoFuer(geaendert);

        given(service.findeMitPhasen(42L)).willReturn(aktuell);
        given(service.aendern(eq(42L), any(), any(), any())).willReturn(geaendert);
        given(service.toDto(geaendert, false)).willReturn(dto);

        mockMvc.perform(put("/api/langzeitkrankmeldungen/42")
                        .param("version", "3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "beginn": "2026-01-05",
                                  "notiz": "Geaenderte Notiz"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(4));
    }

    @Test
    void aendern_VeralteteVersion_Liefert409MitKlartext() throws Exception {
        Langzeitkrankmeldung aktuell = meldungMitId(42L);
        aktuell.setVersion(5L);

        given(service.findeMitPhasen(42L)).willReturn(aktuell);

        mockMvc.perform(put("/api/langzeitkrankmeldungen/42")
                        .param("version", "3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "beginn": "2026-01-05"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Jemand anders hat diese Daten gerade gespeichert. Ihre Änderungen wurden nicht übernommen — bitte neu laden."));
    }

    /**
     * Nachbesserung (Abschnitt-4-Review): der 409-Fall war bisher nur fuer
     * PUT /{id} getestet, obwohl alle sieben aendernden Endpunkte dieselbe
     * private pruefeVersion()-Vorabpruefung nutzen. Deckt die uebrigen sechs
     * ab, damit ein spaeter versehentlich entferntes oder falsch verdrahtetes
     * pruefeVersion() an jedem einzelnen Endpunkt aussfaellt.
     */
    @ParameterizedTest(name = "{0} liefert 409 bei veralteter Version")
    @MethodSource("aendierendeEndpunkteOhneAendernPut")
    void versionskonflikt_LiefertAnJedemAendierendenEndpunkt409(String bezeichnung, RequestBuilder requestBuilder)
            throws Exception {
        Langzeitkrankmeldung aktuell = meldungMitId(42L);
        aktuell.setVersion(5L);
        given(service.findeMitPhasen(42L)).willReturn(aktuell);

        mockMvc.perform(requestBuilder)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Jemand anders hat diese Daten gerade gespeichert. Ihre Änderungen wurden nicht übernommen — bitte neu laden."));
    }

    static Stream<Arguments> aendierendeEndpunkteOhneAendernPut() {
        String phasenBody = """
                {
                  "typ": "KRANKENGELD",
                  "vonDatum": "2026-02-16",
                  "bisDatum": "2026-03-01"
                }
                """;
        return Stream.of(
                Arguments.of("PUT /{id}/beenden", put("/api/langzeitkrankmeldungen/42/beenden")
                        .param("ende", "2026-03-01")
                        .param("version", "3")),
                Arguments.of("PUT /{id}/oeffnen", put("/api/langzeitkrankmeldungen/42/oeffnen")
                        .param("version", "3")),
                Arguments.of("PUT /{id}/abbrechen", put("/api/langzeitkrankmeldungen/42/abbrechen")
                        .param("version", "3")),
                Arguments.of("POST /{id}/phasen", post("/api/langzeitkrankmeldungen/42/phasen")
                        .param("version", "3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(phasenBody)),
                Arguments.of("PUT /{id}/phasen/{phasenId}", put("/api/langzeitkrankmeldungen/42/phasen/7")
                        .param("version", "3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(phasenBody)),
                Arguments.of("DELETE /{id}/phasen/{phasenId}", delete("/api/langzeitkrankmeldungen/42/phasen/7")
                        .param("version", "3")));
    }

    @Test
    void detail_HappyPath_LiefertDtoMitStufenplanTagen() throws Exception {
        Langzeitkrankmeldung meldung = meldungMitId(42L);
        LangzeitkrankmeldungDto dto = dtoFuer(meldung);
        dto.setStufenplanTage(java.util.List.of());

        given(service.findeMitPhasen(42L)).willReturn(meldung);
        given(service.toDto(meldung, true)).willReturn(dto);

        mockMvc.perform(get("/api/langzeitkrankmeldungen/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.stufenplanTage").isArray());
    }

    @Test
    void detail_UnbekannteId_Liefert404() throws Exception {
        given(service.findeMitPhasen(999L))
                .willThrow(new IllegalArgumentException("Langzeitkrankmeldung nicht gefunden"));

        mockMvc.perform(get("/api/langzeitkrankmeldungen/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Langzeitkrankmeldung nicht gefunden"));
    }

    @Test
    void liste_OhneStatusParameter_FragtServiceMitDefaultLaufendAb() throws Exception {
        Langzeitkrankmeldung meldung = meldungMitId(42L);
        LangzeitkrankmeldungDto dto = dtoFuer(meldung);

        given(service.finde(LangzeitkrankmeldungStatus.LAUFEND)).willReturn(java.util.List.of(meldung));
        given(service.toDto(meldung, false)).willReturn(dto);

        mockMvc.perform(get("/api/langzeitkrankmeldungen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(42));
    }

    @Test
    void liste_MitStatusParameter_LeitetIhnAnDenServiceWeiter() throws Exception {
        given(service.finde(LangzeitkrankmeldungStatus.BEENDET)).willReturn(java.util.List.of());

        mockMvc.perform(get("/api/langzeitkrankmeldungen").param("status", "BEENDET"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void beenden_HappyPath_Liefert200MitBeendetemStatus() throws Exception {
        Langzeitkrankmeldung aktuell = meldungMitId(42L);
        Langzeitkrankmeldung beendet = meldungMitId(42L);
        beendet.setStatus(LangzeitkrankmeldungStatus.BEENDET);
        beendet.setEnde(LocalDate.of(2026, 3, 1));
        beendet.setVersion(4L);
        LangzeitkrankmeldungDto dto = dtoFuer(beendet);

        given(service.findeMitPhasen(42L)).willReturn(aktuell);
        given(service.beenden(42L, LocalDate.of(2026, 3, 1))).willReturn(beendet);
        given(service.toDto(beendet, false)).willReturn(dto);

        mockMvc.perform(put("/api/langzeitkrankmeldungen/42/beenden")
                        .param("ende", "2026-03-01")
                        .param("version", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BEENDET"));
    }

    @Test
    void beenden_NichtLaufendeMeldung_Liefert400() throws Exception {
        Langzeitkrankmeldung aktuell = meldungMitId(42L);
        given(service.findeMitPhasen(42L)).willReturn(aktuell);
        given(service.beenden(42L, LocalDate.of(2026, 3, 1)))
                .willThrow(new IllegalStateException("Nur eine laufende Krankmeldung kann beendet werden."));

        mockMvc.perform(put("/api/langzeitkrankmeldungen/42/beenden")
                        .param("ende", "2026-03-01")
                        .param("version", "3"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Nur eine laufende Krankmeldung kann beendet werden."));
    }

    @Test
    void oeffnen_HappyPath_Liefert200MitLaufendemStatus() throws Exception {
        Langzeitkrankmeldung aktuell = meldungMitId(42L);
        aktuell.setStatus(LangzeitkrankmeldungStatus.BEENDET);
        Langzeitkrankmeldung wiederEroeffnet = meldungMitId(42L);
        wiederEroeffnet.setVersion(4L);
        LangzeitkrankmeldungDto dto = dtoFuer(wiederEroeffnet);

        given(service.findeMitPhasen(42L)).willReturn(aktuell);
        given(service.wiederEroeffnen(42L)).willReturn(wiederEroeffnet);
        given(service.toDto(wiederEroeffnet, false)).willReturn(dto);

        mockMvc.perform(put("/api/langzeitkrankmeldungen/42/oeffnen").param("version", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LAUFEND"));
    }

    @Test
    void abbrechen_HappyPath_Liefert200MitAbgebrochenemStatus() throws Exception {
        Langzeitkrankmeldung aktuell = meldungMitId(42L);
        Langzeitkrankmeldung abgebrochen = meldungMitId(42L);
        abgebrochen.setStatus(LangzeitkrankmeldungStatus.ABGEBROCHEN);
        abgebrochen.setVersion(4L);
        LangzeitkrankmeldungDto dto = dtoFuer(abgebrochen);

        given(service.findeMitPhasen(42L)).willReturn(aktuell);
        given(service.abbrechen(42L)).willReturn(abgebrochen);
        given(service.toDto(abgebrochen, false)).willReturn(dto);

        mockMvc.perform(put("/api/langzeitkrankmeldungen/42/abbrechen").param("version", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABGEBROCHEN"));
    }

    @Test
    void phaseHinzufuegen_HappyPath_Liefert201MitDto() throws Exception {
        Langzeitkrankmeldung aktuell = meldungMitId(42L);
        Langzeitkrankmeldung mitPhase = meldungMitId(42L);
        mitPhase.setVersion(4L);
        LangzeitkrankmeldungDto dto = dtoFuer(mitPhase);

        given(service.findeMitPhasen(42L)).willReturn(aktuell);
        given(service.phaseHinzufuegen(eq(42L), eq(LangzeitkrankmeldungPhaseTyp.WIEDEREINGLIEDERUNG),
                any(), any(), any())).willReturn(mitPhase);
        given(service.toDto(mitPhase, false)).willReturn(dto);

        mockMvc.perform(post("/api/langzeitkrankmeldungen/42/phasen")
                        .param("version", "3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "typ": "WIEDEREINGLIEDERUNG",
                                  "vonDatum": "2026-02-16",
                                  "bisDatum": "2026-03-01",
                                  "stundenProTag": 4.00
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(4));
    }

    @Test
    void phaseHinzufuegen_WiedereingliederungOhneStunden_Liefert400MitKlartext() throws Exception {
        Langzeitkrankmeldung aktuell = meldungMitId(42L);
        given(service.findeMitPhasen(42L)).willReturn(aktuell);
        given(service.phaseHinzufuegen(eq(42L), any(), any(), any(), any()))
                .willThrow(new IllegalStateException("Bitte trage ein, wie viele Stunden pro Tag geplant sind."));

        mockMvc.perform(post("/api/langzeitkrankmeldungen/42/phasen")
                        .param("version", "3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "typ": "WIEDEREINGLIEDERUNG",
                                  "vonDatum": "2026-02-16",
                                  "bisDatum": "2026-03-01"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bitte trage ein, wie viele Stunden pro Tag geplant sind."));
    }

    @Test
    void phaseHinzufuegen_StundenUeberTagessoll_Liefert400MitKlartext() throws Exception {
        Langzeitkrankmeldung aktuell = meldungMitId(42L);
        given(service.findeMitPhasen(42L)).willReturn(aktuell);
        given(service.phaseHinzufuegen(eq(42L), any(), any(), any(), any()))
                .willThrow(new IllegalStateException(
                        "Die Stufenplan-Stunden (9.00) dürfen das normale Tagessoll (8.00) nicht übersteigen."));

        mockMvc.perform(post("/api/langzeitkrankmeldungen/42/phasen")
                        .param("version", "3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "typ": "WIEDEREINGLIEDERUNG",
                                  "vonDatum": "2026-02-16",
                                  "bisDatum": "2026-03-01",
                                  "stundenProTag": 9.00
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "Die Stufenplan-Stunden (9.00) dürfen das normale Tagessoll (8.00) nicht übersteigen."));
    }

    @Test
    void phaseHinzufuegen_LueckeZwischenPhasen_Liefert400MitKlartext() throws Exception {
        Langzeitkrankmeldung aktuell = meldungMitId(42L);
        given(service.findeMitPhasen(42L)).willReturn(aktuell);
        given(service.phaseHinzufuegen(eq(42L), any(), any(), any(), any()))
                .willThrow(new IllegalStateException("Zwischen zwei Phasen darf keine Lücke bestehen."));

        mockMvc.perform(post("/api/langzeitkrankmeldungen/42/phasen")
                        .param("version", "3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "typ": "KRANKENGELD",
                                  "vonDatum": "2026-02-20",
                                  "bisDatum": "2026-03-01"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Zwischen zwei Phasen darf keine Lücke bestehen."));
    }

    @Test
    void phaseAendern_HappyPath_Liefert200MitDto() throws Exception {
        Langzeitkrankmeldung aktuell = meldungMitId(42L);
        Langzeitkrankmeldung geaendert = meldungMitId(42L);
        geaendert.setVersion(4L);
        LangzeitkrankmeldungDto dto = dtoFuer(geaendert);

        given(service.findeMitPhasen(42L)).willReturn(aktuell);
        given(service.phaseAendern(eq(42L), eq(7L), any(), any(), any(), any())).willReturn(geaendert);
        given(service.toDto(geaendert, false)).willReturn(dto);

        mockMvc.perform(put("/api/langzeitkrankmeldungen/42/phasen/7")
                        .param("version", "3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "typ": "KRANKENGELD",
                                  "vonDatum": "2026-02-16",
                                  "bisDatum": "2026-03-01"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(4));
    }

    @Test
    void phaseLoeschen_HappyPath_Liefert204() throws Exception {
        Langzeitkrankmeldung aktuell = meldungMitId(42L);
        given(service.findeMitPhasen(42L)).willReturn(aktuell);

        mockMvc.perform(delete("/api/langzeitkrankmeldungen/42/phasen/7").param("version", "3"))
                .andExpect(status().isNoContent());
    }

    @Test
    void phaseLoeschen_UnbekanntePhase_Liefert404() throws Exception {
        Langzeitkrankmeldung aktuell = meldungMitId(42L);
        given(service.findeMitPhasen(42L)).willReturn(aktuell);
        org.mockito.BDDMockito.willThrow(new IllegalArgumentException("Phase nicht gefunden"))
                .given(service).phaseLoeschen(42L, 999L);

        mockMvc.perform(delete("/api/langzeitkrankmeldungen/42/phasen/999").param("version", "3"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Phase nicht gefunden"));
    }

    // ==================== Sicherheits-Pflichtcheckliste ====================
    // (TESTING_SECURITY.md: ungueltige IDs, XSS, Laengenlimits, SQL-Injection-String)

    @Test
    void detail_NegativeId_Liefert404StattAbsturz() throws Exception {
        given(service.findeMitPhasen(-1L))
                .willThrow(new IllegalArgumentException("Langzeitkrankmeldung nicht gefunden"));

        mockMvc.perform(get("/api/langzeitkrankmeldungen/-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Langzeitkrankmeldung nicht gefunden"));
    }

    @Test
    void detail_IdNull_Liefert404StattAbsturz() throws Exception {
        given(service.findeMitPhasen(0L))
                .willThrow(new IllegalArgumentException("Langzeitkrankmeldung nicht gefunden"));

        mockMvc.perform(get("/api/langzeitkrankmeldungen/0"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Langzeitkrankmeldung nicht gefunden"));
    }

    @Test
    void detail_MaxLongId_Liefert404StattAbsturz() throws Exception {
        given(service.findeMitPhasen(Long.MAX_VALUE))
                .willThrow(new IllegalArgumentException("Langzeitkrankmeldung nicht gefunden"));

        mockMvc.perform(get("/api/langzeitkrankmeldungen/" + Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Langzeitkrankmeldung nicht gefunden"));
    }

    @Test
    void anlegen_NotizMitScriptTag_WirdUnveraendertAlsJsonStringZurueckgegeben() throws Exception {
        // JSON-Serialisierung ist kein HTML-Kontext - der String darf 1:1
        // durchgereicht werden, das Frontend rendert ihn als reinen Text.
        String notizMitScript = "<script>alert(1)</script>";
        Langzeitkrankmeldung gespeichert = meldungMitId(42L);
        gespeichert.setNotiz(notizMitScript);
        LangzeitkrankmeldungDto dto = dtoFuer(gespeichert);
        dto.setNotiz(notizMitScript);

        given(service.anlegen(eq(1L), any(), any(), eq(notizMitScript))).willReturn(gespeichert);
        given(service.toDto(gespeichert, false)).willReturn(dto);

        mockMvc.perform(post("/api/langzeitkrankmeldungen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mitarbeiterId": 1,
                                  "beginn": "2026-01-05",
                                  "notiz": "<script>alert(1)</script>"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.notiz").value(notizMitScript));
    }

    @Test
    void anlegen_NotizUeber10000Zeichen_Liefert400StattAbsturz() throws Exception {
        String ueberlangeNotiz = "a".repeat(10_001);
        given(service.anlegen(any(), any(), any(), any()))
                .willThrow(new IllegalArgumentException("Die Notiz darf höchstens 500 Zeichen lang sein."));

        mockMvc.perform(post("/api/langzeitkrankmeldungen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mitarbeiterId": 1,
                                  "beginn": "2026-01-05",
                                  "notiz": "%s"
                                }
                                """.formatted(ueberlangeNotiz)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Die Notiz darf höchstens 500 Zeichen lang sein."));
    }

    @Test
    void anlegen_NotizMitSqlInjectionString_WirdAlsGewoehnlicherTextGespeichert() throws Exception {
        // Named Params im Service/Repository, kein String-Concat - der Text ist
        // fachlich einfach eine (kurze) Notiz, kein Angriff auf die Query.
        String injectionVersuch = "'; DROP TABLE x; --";
        Langzeitkrankmeldung gespeichert = meldungMitId(42L);
        gespeichert.setNotiz(injectionVersuch);
        LangzeitkrankmeldungDto dto = dtoFuer(gespeichert);
        dto.setNotiz(injectionVersuch);

        given(service.anlegen(eq(1L), any(), any(), eq(injectionVersuch))).willReturn(gespeichert);
        given(service.toDto(gespeichert, false)).willReturn(dto);

        mockMvc.perform(post("/api/langzeitkrankmeldungen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mitarbeiterId": 1,
                                  "beginn": "2026-01-05",
                                  "notiz": "'; DROP TABLE x; --"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.notiz").value(injectionVersuch));
    }
}
