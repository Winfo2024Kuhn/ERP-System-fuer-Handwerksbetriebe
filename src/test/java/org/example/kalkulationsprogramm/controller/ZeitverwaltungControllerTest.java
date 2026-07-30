package org.example.kalkulationsprogramm.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Abwesenheit;
import org.example.kalkulationsprogramm.domain.AbwesenheitsTyp;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.domain.Zeitkonto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc-Tests für ZeitverwaltungController.
 * Schwerpunkt: Der Kalender liefert für Abwesenheiten die echte {@code abwesenheitId},
 * damit das Frontend Krankheit/Urlaub serverseitig löschen kann (Bug: ließ sich vorher
 * nur lokal aus dem UI entfernen).
 */
@WebMvcTest(ZeitverwaltungController.class)
@AutoConfigureMockMvc(addFilters = false)
class ZeitverwaltungControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private org.example.kalkulationsprogramm.repository.ZeitbuchungRepository zeitbuchungRepository;
    @MockBean
    private org.example.kalkulationsprogramm.repository.AbwesenheitRepository abwesenheitRepository;
    @MockBean
    private org.example.kalkulationsprogramm.repository.MitarbeiterRepository mitarbeiterRepository;
    @MockBean
    private org.example.kalkulationsprogramm.service.FeiertagService feiertagService;
    @MockBean
    private org.example.kalkulationsprogramm.service.ZeitkontoService zeitkontoService;
    @MockBean
    private org.example.kalkulationsprogramm.service.ProjektAuswertungPdfService projektAuswertungPdfService;
    @MockBean
    private org.example.kalkulationsprogramm.repository.ProjektRepository projektRepository;
    @MockBean
    private org.example.kalkulationsprogramm.repository.ArbeitsgangStundensatzRepository arbeitsgangStundensatzRepository;
    @MockBean
    private org.example.kalkulationsprogramm.repository.ArbeitsgangRepository arbeitsgangRepository;
    @MockBean
    private org.example.kalkulationsprogramm.service.ZeitbuchungAuditService auditService;
    @MockBean
    private org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository frontendUserProfileRepository;
    @MockBean
    private org.example.kalkulationsprogramm.service.MonatsSaldoService monatsSaldoService;
    @MockBean
    private org.example.kalkulationsprogramm.service.MonatsSaldoWarmupService monatsSaldoWarmupService;

    @Test
    void getKalender_LiefertEchteAbwesenheitIdFuerKrankheit() throws Exception {
        Mitarbeiter mitarbeiter = new Mitarbeiter();
        mitarbeiter.setId(1L);
        mitarbeiter.setVorname("Max");
        mitarbeiter.setNachname("Mustermann");

        Zeitkonto zeitkonto = new Zeitkonto(mitarbeiter);
        zeitkonto.setMontagStunden(new BigDecimal("8.00"));
        zeitkonto.setDienstagStunden(new BigDecimal("8.00"));
        zeitkonto.setMittwochStunden(new BigDecimal("8.00"));
        zeitkonto.setDonnerstagStunden(new BigDecimal("8.00"));
        zeitkonto.setFreitagStunden(new BigDecimal("8.00"));
        zeitkonto.setSamstagStunden(BigDecimal.ZERO);
        zeitkonto.setSonntagStunden(BigDecimal.ZERO);

        Abwesenheit krankheit = new Abwesenheit();
        krankheit.setId(42L);
        krankheit.setMitarbeiter(mitarbeiter);
        krankheit.setTyp(AbwesenheitsTyp.KRANKHEIT);
        krankheit.setDatum(LocalDate.of(2025, 6, 2)); // Montag
        krankheit.setStunden(new BigDecimal("5.00"));
        krankheit.setNotiz("Krankheit (abzgl. 3 h gearbeitet)");

        given(feiertagService.getFeiertageZwischen(any(), any())).willReturn(List.of());
        given(zeitkontoService.getOrCreateZeitkonto(1L)).willReturn(zeitkonto);
        given(zeitbuchungRepository.findByMitarbeiterIdAndStartZeitAfter(anyLong(), any()))
                .willReturn(List.of());
        given(abwesenheitRepository.findByMitarbeiterIdAndDatumBetween(anyLong(), any(), any()))
                .willReturn(List.of(krankheit));
        given(zeitkontoService.berechneSollstundenFuerMonat(anyLong(), any(Integer.class), any(Integer.class)))
                .willReturn(new BigDecimal("160.00"));

        mockMvc.perform(get("/api/zeitverwaltung/kalender")
                        .param("mitarbeiterId", "1")
                        .param("jahr", "2025")
                        .param("monat", "6"))
                .andExpect(status().isOk())
                // Bug-Fix: echte positive ID wird mitgeliefert (für DELETE /api/abwesenheit/{id})
                .andExpect(jsonPath("$..abwesenheitId", hasItem(42)))
                // Anzeige-ID bleibt negativ, um Abwesenheiten von echten Buchungen zu trennen
                .andExpect(jsonPath("$..buchungen[?(@.typ=='KRANKHEIT')].id", hasItem(-42)));
    }

    @Test
    void updateBuchung_RaeumtPruefKennzeichenDesAutoStopsAb() throws Exception {
        // Sobald ein Mensch die vom Auto-Stop geschätzte Endezeit im Kalender
        // korrigiert (oder bestätigt), ist der Prüffall erledigt - die Buchung
        // darf nicht weiter in der Benachrichtigungs-Glocke hängen.
        Mitarbeiter bearbeiter = new Mitarbeiter();
        bearbeiter.setId(1L);
        bearbeiter.setVorname("Max");
        bearbeiter.setNachname("Mustermann");

        Zeitbuchung buchung = new Zeitbuchung();
        buchung.setId(1903L);
        buchung.setMitarbeiter(bearbeiter);
        buchung.setStartZeit(LocalDateTime.of(2026, 7, 29, 16, 48));
        buchung.setEndeZeit(LocalDateTime.of(2026, 7, 29, 20, 0));
        buchung.setAutomatischBeendet(true);
        buchung.setVersion(2);

        given(mitarbeiterRepository.findById(1L)).willReturn(Optional.of(bearbeiter));
        given(zeitbuchungRepository.findById(1903L)).willReturn(Optional.of(buchung));
        given(zeitbuchungRepository.save(any(Zeitbuchung.class)))
                .willAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/zeitverwaltung/buchungen/1903")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bearbeiterId": 1,
                                  "aenderungsgrund": "Korrektur im Zeiterfassungskalender",
                                  "endeZeit": "2026-07-29T17:00:00"
                                }
                                """))
                .andExpect(status().isOk());

        assertThat(buchung.isAutomatischBeendet()).isFalse();
        assertThat(buchung.getEndeZeit()).isEqualTo(LocalDateTime.of(2026, 7, 29, 17, 0));
    }
}
