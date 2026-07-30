package org.example.kalkulationsprogramm.controller;

import java.util.List;

import org.example.kalkulationsprogramm.dto.Projekt.ProjektResponseDto;
import org.example.kalkulationsprogramm.mapper.ProduktkategorieMapper;
import org.example.kalkulationsprogramm.repository.LieferantDokumentProjektAnteilRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.ProjektNotizBildRepository;
import org.example.kalkulationsprogramm.repository.ProjektNotizRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.example.kalkulationsprogramm.service.DateiSpeicherService;
import org.example.kalkulationsprogramm.service.DokumentFreigabeService;
import org.example.kalkulationsprogramm.service.FrontendUserProfileService;
import org.example.kalkulationsprogramm.service.PdfAiExtractorService;
import org.example.kalkulationsprogramm.service.ProjektManagementService;
import org.example.kalkulationsprogramm.service.ProjektListenPdfService;
import org.example.kalkulationsprogramm.service.StuecklistePdfService;
import org.example.kalkulationsprogramm.service.ZugferdErstellService;
import org.example.kalkulationsprogramm.service.ZugferdExtractorService;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProjektController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProjektControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private DateiSpeicherService dateiSpeicherService;

        @MockBean
        private ProjektManagementService projektManagementService;

        @MockBean
        private ProjektListenPdfService projektListenPdfService;

        @MockBean
        private ZugferdExtractorService zugferdExtractorService;

        @MockBean
        private ZugferdErstellService zugferdErstellService;

        @MockBean
        private ProduktkategorieMapper produktkategorieMapper;

        @MockBean
        private StuecklistePdfService stuecklistePdfService;

        @MockBean
        private PdfAiExtractorService pdfAiExtractorService;

        @MockBean
        private FrontendUserProfileService frontendUserProfileService;

        @MockBean
        private MitarbeiterRepository mitarbeiterRepository;

        @MockBean
        private LieferantenRepository lieferantenRepository;

        @MockBean
        private LieferantDokumentProjektAnteilRepository lieferantDokumentProjektAnteilRepository;

        @MockBean
        private LieferantGeschaeftsdokumentRepository lieferantGeschaeftsdokumentRepository;

        @MockBean
        private ProjektNotizRepository projektNotizRepository;

        @MockBean
        private ProjektNotizBildRepository projektNotizBildRepository;

        @MockBean
        private ProjektRepository projektRepository;

        @MockBean
        private ZeitbuchungRepository zeitbuchungRepository;

        @MockBean
        private DokumentFreigabeService dokumentFreigabeService;

        @Test
        void getAlleProjekte_returnsPagedResponse() throws Exception {
                ProjektResponseDto dto = new ProjektResponseDto();
                dto.setId(123L);
                Page<ProjektResponseDto> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 50), 1);
                when(projektManagementService.findeProjekteMitFilter(
                                isNull(),
                                isNull(),
                                isNull(),
                                isNull(),
                                isNull(),
                                isNull(),
                                isNull(),
                                isNull(),
                                eq(0),
                                eq(50))).thenReturn(page);

                mockMvc.perform(get("/api/projekte")
                                .param("size", "999")
                                .param("page", "-3"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.projekte[0].id").value(123))
                                .andExpect(jsonPath("$.gesamt").value(1))
                                .andExpect(jsonPath("$.seite").value(0))
                                .andExpect(jsonPath("$.seitenGroesse").value(50));

                verify(projektManagementService).findeProjekteMitFilter(
                                isNull(),
                                isNull(),
                                isNull(),
                                isNull(),
                                isNull(),
                                isNull(),
                                isNull(),
                                isNull(),
                                eq(0),
                                eq(50));
        }

        @Test
        void getAlleProjekte_filtersInArbeitByAbgeschlossenStatus() throws Exception {
                Page<ProjektResponseDto> page = new PageImpl<>(List.of(), PageRequest.of(0, 50), 0);
                when(projektManagementService.findeProjekteMitFilter(
                                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(false), eq(0), eq(50)))
                                .thenReturn(page);

                mockMvc.perform(get("/api/projekte").param("abgeschlossen", "false"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.gesamt").value(0));

                verify(projektManagementService).findeProjekteMitFilter(
                                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(false), eq(0), eq(50));
        }

        @Test
        void setzeAbgeschlossen_entferntDenHakenUndLiefertDasProjekt() throws Exception {
                ProjektResponseDto dto = new ProjektResponseDto();
                dto.setId(42L);
                dto.setAbgeschlossen(false);
                when(projektManagementService.setzeAbgeschlossen(42L, false)).thenReturn(dto);

                mockMvc.perform(patch("/api/projekte/42/abgeschlossen").param("abgeschlossen", "false"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(42))
                                .andExpect(jsonPath("$.abgeschlossen").value(false));

                verify(projektManagementService).setzeAbgeschlossen(42L, false);
        }

        @Test
        void setzeAbgeschlossen_liefert404FuerUnbekanntesProjekt() throws Exception {
                when(projektManagementService.setzeAbgeschlossen(999L, true))
                                .thenThrow(new RuntimeException("Projekt konnte nicht gefunden werden."));

                mockMvc.perform(patch("/api/projekte/999/abgeschlossen").param("abgeschlossen", "true"))
                                .andExpect(status().isNotFound());
        }

        @Test
        void exportiereProjektliste_liefertPdfZurVorschau() throws Exception {
                byte[] pdf = "%PDF-Test".getBytes();
                when(projektListenPdfService.generatePdf()).thenReturn(pdf);

                mockMvc.perform(get("/api/projekte/export-pdf"))
                                .andExpect(status().isOk())
                                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("application/pdf"))
                                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                                .header().string("Content-Disposition",
                                                                "inline; filename=\"projekte-in-arbeit.pdf\""))
                                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(pdf));

                verify(projektListenPdfService).generatePdf();
        }

        @Test
        void fuehreAnfrageZusammenGibtProjektZurueck() throws Exception {
                ProjektResponseDto dto = new ProjektResponseDto();
                dto.setId(100L);
                when(projektManagementService.fuehreAnfrageZusammen(100L, 10L)).thenReturn(dto);

                mockMvc.perform(post("/api/projekte/100/anfragen/10/zusammenfuehren"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(100));

                verify(projektManagementService).fuehreAnfrageZusammen(100L, 10L);
        }

        @Test
        void fuehreAnfrageZusammenGibtKonfliktMitHinweisZurueck() throws Exception {
                when(projektManagementService.fuehreAnfrageZusammen(100L, 10L))
                                .thenThrow(new IllegalStateException(
                                                "Zusammenführen nicht möglich, da im Projekt bereits Ausgangsgeschäftsdokumente vorhanden sind."));

                mockMvc.perform(post("/api/projekte/100/anfragen/10/zusammenfuehren"))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.message").value(
                                                "Zusammenführen nicht möglich, da im Projekt bereits Ausgangsgeschäftsdokumente vorhanden sind."));
        }

        // --- Tests: PATCH /api/projekte/dokumente/{id}/bezahlt ---

        @Test
        void setzeDokumentBezahltGibt204BeiErfolg() throws Exception {
                doNothing().when(dateiSpeicherService).setzeGeschaeftsdokumentBezahlt(42L, true);

                mockMvc.perform(patch("/api/projekte/dokumente/42/bezahlt")
                                .param("bezahlt", "true"))
                                .andExpect(status().isNoContent());

                verify(dateiSpeicherService).setzeGeschaeftsdokumentBezahlt(42L, true);
        }

        @Test
        void setzeDokumentBezahltFalseGibt204() throws Exception {
                doNothing().when(dateiSpeicherService).setzeGeschaeftsdokumentBezahlt(42L, false);

                mockMvc.perform(patch("/api/projekte/dokumente/42/bezahlt")
                                .param("bezahlt", "false"))
                                .andExpect(status().isNoContent());

                verify(dateiSpeicherService).setzeGeschaeftsdokumentBezahlt(42L, false);
        }

        @Test
        void setzeDokumentBezahltGibt404WennDokumentNichtGefunden() throws Exception {
                doThrow(new RuntimeException("Nicht gefunden"))
                                .when(dateiSpeicherService).setzeGeschaeftsdokumentBezahlt(999L, true);

                mockMvc.perform(patch("/api/projekte/dokumente/999/bezahlt")
                                .param("bezahlt", "true"))
                                .andExpect(status().isNotFound());
        }

        @Test
        void setzeDokumentBezahltGibt404WennKeinGeschaeftsdokument() throws Exception {
                doThrow(new RuntimeException("Kein Geschäftsdokument"))
                                .when(dateiSpeicherService).setzeGeschaeftsdokumentBezahlt(50L, true);

                mockMvc.perform(patch("/api/projekte/dokumente/50/bezahlt")
                                .param("bezahlt", "true"))
                                .andExpect(status().isNotFound());
        }

        // --- Regressionstests: Eingangsrechnungen nur mit Netto-Beträgen ---

        @Test
        void eingangsrechnungen_gibtNettoBasiertenBerechneterBetragZurueck_nichtBruttoAltdaten() throws Exception {
                // Arrangieren: Anteil mit brutto-basiertem Altdaten-Betrag (75% von 119 Brutto = 89,25)
                org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument gd =
                        new org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument();
                gd.setId(1L);
                gd.setBetragNetto(new java.math.BigDecimal("100.00"));
                gd.setBetragBrutto(new java.math.BigDecimal("119.00"));
                gd.setDokumentNummer("TEST-001");

                org.example.kalkulationsprogramm.domain.LieferantDokument dok =
                        new org.example.kalkulationsprogramm.domain.LieferantDokument();
                dok.setId(1L);
                dok.setUploadDatum(java.time.LocalDateTime.of(2026, 4, 2, 0, 0));
                dok.setGeschaeftsdaten(gd);

                org.example.kalkulationsprogramm.domain.LieferantDokumentProjektAnteil anteil =
                        new org.example.kalkulationsprogramm.domain.LieferantDokumentProjektAnteil();
                anteil.setId(1L);
                anteil.setProzent(75);
                anteil.setBerechneterBetrag(new java.math.BigDecimal("89.25")); // Altdaten: 75% von Brutto
                anteil.setDokument(dok);

                when(lieferantDokumentProjektAnteilRepository.findByProjektIdEager(99L))
                        .thenReturn(List.of(anteil));
                when(lieferantDokumentProjektAnteilRepository.findByDokumentIdEager(1L))
                        .thenReturn(List.of(anteil));

                // berechneterBetrag muss 75,00 sein (75% von 100 Netto), NICHT 89,25 (75% von 119 Brutto)
                // gesamtbetrag muss 100,00 sein (betragNetto), NICHT 119,00 (betragBrutto)
                mockMvc.perform(get("/api/projekte/99/eingangsrechnungen"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$[0].berechneterBetrag").value(75.00))
                        .andExpect(jsonPath("$[0].gesamtbetrag").value(100.00));
        }

        @Test
        void eingangsrechnungen_dokumentenkette_gibtBetragNettoZurueck() throws Exception {
                // Arrangieren: Dokument mit Dokumentenkette (verknüpfte Dokumente)
                org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument gd =
                        new org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument();
                gd.setId(2L);
                gd.setBetragNetto(new java.math.BigDecimal("200.00"));
                gd.setBetragBrutto(new java.math.BigDecimal("238.00"));
                gd.setDokumentNummer("TEST-002");

                org.example.kalkulationsprogramm.domain.LieferantDokument dok =
                        new org.example.kalkulationsprogramm.domain.LieferantDokument();
                dok.setId(2L);
                dok.setUploadDatum(java.time.LocalDateTime.of(2026, 4, 2, 0, 0));
                dok.setGeschaeftsdaten(gd);
                // Verknüpftes Dokument (Lieferschein) – keine Geschäftsdaten nötig
                dok.setVerknuepfteDokumente(new java.util.HashSet<>());
                dok.setVerknuepftVon(new java.util.HashSet<>());

                org.example.kalkulationsprogramm.domain.LieferantDokumentProjektAnteil anteil =
                        new org.example.kalkulationsprogramm.domain.LieferantDokumentProjektAnteil();
                anteil.setId(2L);
                anteil.setProzent(100);
                anteil.setBerechneterBetrag(new java.math.BigDecimal("238.00")); // Altdaten: Brutto
                anteil.setDokument(dok);

                when(lieferantDokumentProjektAnteilRepository.findByProjektIdEager(99L))
                        .thenReturn(List.of(anteil));
                when(lieferantDokumentProjektAnteilRepository.findByDokumentIdEager(2L))
                        .thenReturn(List.of(anteil));

                // berechneterBetrag muss 200,00 sein (100% von 200 Netto), NICHT 238,00 (Brutto)
                mockMvc.perform(get("/api/projekte/99/eingangsrechnungen"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$[0].berechneterBetrag").value(200.00))
                        .andExpect(jsonPath("$[0].gesamtbetrag").value(200.00));
        }
}
