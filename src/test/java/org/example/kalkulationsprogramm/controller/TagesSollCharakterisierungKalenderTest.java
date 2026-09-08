package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.Feiertag;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Zeitkonto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Charakterisierungstest (Langzeitkrankmeldung, Abschnitt 1 / Task 2).
 *
 * Friert {@code GET /api/zeitverwaltung/kalender} zahlengenau ein - Task 11
 * stellt die Sollstunden-/Ist-Stunden-Ermittlung auf {@code TagesSollService}
 * um (siehe "Bewusste Verhaltensaenderungen" im Plan, Punkt 1).
 *
 * <p><b>Bewusster Bestandsbug, der HIER stehen bleibt:</b> der Controller
 * liefert an jedem Feiertag - auch einem halben wie Heiligabend - die vollen
 * Sollstunden als Ist-Stunden (Zeile 511-514 rechnet nicht halbiert), waehrend
 * die Monatsuebersicht ({@link org.example.kalkulationsprogramm.service.MonatsSaldoService})
 * korrekt halbiert. Ergebnis heute: +4h Phantom-Ueberstunden am halben
 * Feiertag. Die Zusicherung {@code tage[23].istStunden == 8} haelt genau
 * diesen Bug fest und ist die EINZIGE Zusicherung in diesem Task, die Task 11
 * bewusst aendern darf - alle anderen sind Regressionsschutz.
 *
 * Dummy-Daten (DSGVO): Max Mustermann, ID 1.
 */
@WebMvcTest(ZeitverwaltungController.class)
@AutoConfigureMockMvc(addFilters = false)
class TagesSollCharakterisierungKalenderTest {

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
    void dezember2026_liefertHeutigenStandFuerSollUndIstStunden() throws Exception {
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
        zeitkonto.setSamstagStunden(new BigDecimal("0.00"));
        zeitkonto.setSonntagStunden(new BigDecimal("0.00"));

        // 24.12.2026 = halber Feiertag (Heiligabend), 25.12.2026 = voller Feiertag.
        Feiertag heiligabend = Feiertag.halberFeiertag(LocalDate.of(2026, 12, 24), "Heiligabend");
        Feiertag ersterWeihnachtstag = new Feiertag(LocalDate.of(2026, 12, 25), "1. Weihnachtstag");

        given(feiertagService.getFeiertageZwischen(any(), any()))
                .willReturn(List.of(heiligabend, ersterWeihnachtstag));
        given(zeitkontoService.getOrCreateZeitkonto(1L)).willReturn(zeitkonto);
        given(zeitbuchungRepository.findByMitarbeiterIdAndStartZeitAfter(anyLong(), any()))
                .willReturn(List.of());
        given(abwesenheitRepository.findByMitarbeiterIdAndDatumBetween(anyLong(), any(), any()))
                .willReturn(List.of());
        given(zeitkontoService.berechneSollstundenFuerMonat(anyLong(), any(Integer.class), any(Integer.class)))
                .willReturn(new BigDecimal("168.00"));

        // 1.12.2026 ist ein Dienstag -> tage[0].
        mockMvc.perform(get("/api/zeitverwaltung/kalender")
                        .param("mitarbeiterId", "1")
                        .param("jahr", "2026")
                        .param("monat", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tage[0].datum").value("2026-12-01"))
                .andExpect(jsonPath("$.tage[0].sollStunden").value(8.00))
                .andExpect(jsonPath("$.tage[23].datum").value("2026-12-24"))
                .andExpect(jsonPath("$.tage[23].sollStunden").value(0))
                .andExpect(jsonPath("$.tage[24].datum").value("2026-12-25"))
                .andExpect(jsonPath("$.tage[24].sollStunden").value(0))
                // Heutiger Stand, aendert sich in Task 11: der Controller rechnet die
                // Ist-Stunden am Feiertag NICHT halbiert, obwohl der 24.12. laut
                // feiertagService.halbTag=true nur ein halber Feiertag ist. Die
                // Monatsuebersicht (MonatsSaldoService) halbiert korrekt - hier liegt
                // der +4h-Phantom-Ueberstunden-Bug aus den "Bewussten
                // Verhaltensaenderungen" des Plans.
                .andExpect(jsonPath("$.tage[23].istStunden").value(8.00));
    }
}
