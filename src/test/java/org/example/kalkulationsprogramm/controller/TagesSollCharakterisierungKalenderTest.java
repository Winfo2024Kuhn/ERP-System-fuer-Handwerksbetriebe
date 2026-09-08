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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * <p><b>Task 11 hat den einzigen bewusst geaenderten Wert in dieser Klasse
 * gefixt:</b> der Controller lieferte bis Task 11 an jedem Feiertag - auch
 * einem halben wie Heiligabend - die vollen Sollstunden als Ist-Stunden,
 * waehrend die Monatsuebersicht
 * ({@link org.example.kalkulationsprogramm.service.MonatsSaldoService})
 * korrekt halbiert. Ergebnis: +4h Phantom-Ueberstunden am halben Feiertag,
 * und der Kalender widersprach der Monatsuebersicht. Die Zusicherung
 * {@code tage[23].istStunden} ist deshalb (und AUSSCHLIESSLICH deshalb) von
 * {@code 8} auf {@code 4.00} geaendert - siehe Kommentar direkt an der
 * Zusicherung unten. Alle anderen Zusicherungen sind unveraendert und bleiben
 * Regressionsschutz.
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
    @MockBean
    private org.example.kalkulationsprogramm.service.TagesSollService tagesSollService;

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
        // arbeitsSollJeTag/feiertagsGutschriftJeTag bilden nach, was
        // TagesSollService (Task 3, seit Abschnitt 4 als Zeitraum-Variante -
        // siehe Kontext-Log) fuer einen Monat ohne laufende Wiedereingliederung
        // liefert: an Werktagen die volle Tagesbasis als arbeitsSoll, am vollen
        // Feiertag (25.12.) 0 arbeitsSoll/volle Gutschrift, am halben Feiertag
        // (24.12.) 0 arbeitsSoll/halbe Gutschrift.
        //
        // Nachbesserung Abschnitt 4, Befund 2: der Stub wertet den
        // tatsaechlich uebergebenen Zeitraum (von/bis-Argumente der
        // Invocation) aus, statt eine feste Dezember-2026-Map unabhaengig
        // davon zurueckzugeben. Mit `any(), any()` fuer von/bis UND einer
        // festen Rueckgabe wuerde der Test blind fuer einen falschen
        // Zeitraum, den der Controller an TagesSollService uebergibt - die
        // Tage im Kalender kaemen trotzdem aus der (zufaellig passenden)
        // festen Map. Mutationsprobe (siehe Report): Zeitraum in
        // ZeitverwaltungController.getKalender testweise um sechs Monate
        // verschoben - mit der alten festen Rueckgabe blieb der Test gruen,
        // mit dieser Auswertung wird er rot.
        given(tagesSollService.arbeitsSollJeTag(anyLong(), any(), any(), any()))
                .willAnswer(inv -> arbeitsSollJeTagFuer(inv.getArgument(2), inv.getArgument(3)));
        given(tagesSollService.feiertagsGutschriftJeTag(anyLong(), any(), any(), any()))
                .willAnswer(inv -> feiertagsGutschriftJeTagFuer(inv.getArgument(2), inv.getArgument(3)));

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
                // Bugfix (Task 11, siehe Plan "Bewusste Verhaltensaenderungen" Punkt 1):
                // der alte Wert 8.00 war der Bug - der Controller rechnete die
                // Ist-Stunden am Feiertag NICHT halbiert, obwohl der 24.12. laut
                // feiertagService.halbTag=true nur ein halber Feiertag ist, und
                // schrieb dadurch +4h Phantom-Ueberstunden gut. Der neue Wert 4.00
                // kommt aus TagesSollService.feiertagsGutschrift und stimmt jetzt mit
                // der Monatsuebersicht (MonatsSaldoService: Soll 4 / Gutschrift 4 ->
                // netto 0) ueberein.
                .andExpect(jsonPath("$.tage[23].istStunden").value(4.00));
    }

    /** Siehe Kommentar am Stub oben (Nachbesserung Abschnitt 4, Befund 2). */
    private static Map<LocalDate, BigDecimal> arbeitsSollJeTagFuer(LocalDate von, LocalDate bis) {
        Map<LocalDate, BigDecimal> ergebnis = new LinkedHashMap<>();
        for (LocalDate tag = von; !tag.isAfter(bis); tag = tag.plusDays(1)) {
            if (tag.equals(LocalDate.of(2026, 12, 24)) || tag.equals(LocalDate.of(2026, 12, 25))) {
                ergebnis.put(tag, BigDecimal.ZERO);
            } else {
                int wochentag = tag.getDayOfWeek().getValue();
                ergebnis.put(tag, wochentag <= 5 ? new BigDecimal("8.00") : BigDecimal.ZERO);
            }
        }
        return ergebnis;
    }

    /** Siehe Kommentar am Stub oben (Nachbesserung Abschnitt 4, Befund 2). */
    private static Map<LocalDate, BigDecimal> feiertagsGutschriftJeTagFuer(LocalDate von, LocalDate bis) {
        Map<LocalDate, BigDecimal> ergebnis = new LinkedHashMap<>();
        for (LocalDate tag = von; !tag.isAfter(bis); tag = tag.plusDays(1)) {
            if (tag.equals(LocalDate.of(2026, 12, 24))) {
                ergebnis.put(tag, new BigDecimal("4.00"));
            } else if (tag.equals(LocalDate.of(2026, 12, 25))) {
                ergebnis.put(tag, new BigDecimal("8.00"));
            } else {
                ergebnis.put(tag, BigDecimal.ZERO);
            }
        }
        return ergebnis;
    }
}
