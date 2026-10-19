package org.example.kalkulationsprogramm.controller;

import java.util.List;

import jakarta.servlet.ServletException;

import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.dto.Projekt.ProjektResponseDto;
import org.example.kalkulationsprogramm.mapper.ProduktkategorieMapper;
import org.example.kalkulationsprogramm.repository.LieferantDokumentProjektAnteilRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.ProjektNotizBildRepository;
import org.example.kalkulationsprogramm.repository.ProjektNotizRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.example.kalkulationsprogramm.service.AngemeldeterMitarbeiterService;
import org.example.kalkulationsprogramm.service.DateiSpeicherService;
import org.example.kalkulationsprogramm.service.DokumentFreigabeService;
import org.example.kalkulationsprogramm.service.LieferantDokumentZugriffService;
import org.example.kalkulationsprogramm.service.PdfAiExtractorService;
import org.example.kalkulationsprogramm.service.ProjektManagementService;
import org.example.kalkulationsprogramm.service.ProjektListenPdfService;
import org.example.kalkulationsprogramm.service.StuecklistePdfService;
import org.example.kalkulationsprogramm.service.ZugferdErstellService;
import org.example.kalkulationsprogramm.service.ZugferdExtractorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProjektController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProjektControllerTest {

        /** Standardmeldung des RestExceptionHandler bei einem Versionskonflikt (@Version). */
        private static final String VERSIONSKONFLIKT =
                        "Jemand anders hat diese Daten gerade gespeichert. Ihre Änderungen wurden nicht übernommen — bitte neu laden.";

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
        private AngemeldeterMitarbeiterService angemeldeterMitarbeiter;


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

        @MockBean
        private LieferantDokumentZugriffService lieferantDokumentZugriffService;

        @BeforeEach
        void alleDokumenttypenSichtbar() {
                // Die Typ-Rechte prüfen die Tests "eingangsrechnungen_..." unten; sonst darf der Aufrufer alles sehen.
                when(lieferantDokumentZugriffService.sichtbareTypen(org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.any()))
                                .thenReturn(java.util.Optional.of(
                                                java.util.EnumSet.allOf(org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.class)));
        }

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
                                isNull(),
                                eq(0),
                                eq(50));
        }

        @Test
        void getAlleProjekte_filtersInArbeitByAbgeschlossenStatus() throws Exception {
                Page<ProjektResponseDto> page = new PageImpl<>(List.of(), PageRequest.of(0, 50), 0);
                when(projektManagementService.findeProjekteMitFilter(
                                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                                eq(false), eq(0), eq(50)))
                                .thenReturn(page);

                mockMvc.perform(get("/api/projekte").param("abgeschlossen", "false"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.gesamt").value(0));

                verify(projektManagementService).findeProjekteMitFilter(
                                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                                eq(false), eq(0), eq(50));
        }

        @Test
        void getAlleProjekte_reichtDenJahresfilterAnDenServiceDurch() throws Exception {
                Page<ProjektResponseDto> page = new PageImpl<>(List.of(), PageRequest.of(0, 50), 0);
                when(projektManagementService.findeProjekteMitFilter(
                                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(2025), isNull(),
                                isNull(), eq(0), eq(50)))
                                .thenReturn(page);

                mockMvc.perform(get("/api/projekte").param("jahr", "2025"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.gesamt").value(0));

                verify(projektManagementService).findeProjekteMitFilter(
                                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(2025), isNull(),
                                isNull(), eq(0), eq(50));
        }

        @Test
        void getAlleProjekte_lehntUnbrauchbaresJahrAb() throws Exception {
                // Kein gültiges Integer-Jahr (z.B. SQL-Injection-Versuch) → 400 statt 500.
                mockMvc.perform(get("/api/projekte").param("jahr", "'; DROP TABLE projekt; --"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void getAlleProjekte_lehntJahreAusserhalbDesGueltigenBereichsAb() throws Exception {
                // 2000000000 wäre ein gültiges Integer, würde aber ohne @Min/@Max erst in
                // LocalDate.of() als DateTimeException (HTTP 500) hochgehen. Negative Werte
                // und 0 sind ebenfalls kein sinnvolles Anlegejahr.
                for (String jahr : List.of("2000000000", "-1", "0", "1899", "2101")) {
                        mockMvc.perform(get("/api/projekte").param("jahr", jahr))
                                        .andExpect(status().isBadRequest());
                }
        }

        @Test
        void verfuegbareJahre_liefertDieJahreAbsteigend() throws Exception {
                when(projektManagementService.verfuegbareAnlegeJahre()).thenReturn(List.of(2026, 2025, 2024));

                mockMvc.perform(get("/api/projekte/jahre"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0]").value(2026))
                                .andExpect(jsonPath("$[1]").value(2025))
                                .andExpect(jsonPath("$[2]").value(2024));

                verify(projektManagementService).verfuegbareAnlegeJahre();
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
                                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                ProjektManagementService.PROJEKT_NICHT_GEFUNDEN));

                mockMvc.perform(patch("/api/projekte/999/abgeschlossen").param("abgeschlossen", "true"))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.message").value(ProjektManagementService.PROJEKT_NICHT_GEFUNDEN));
        }

        @Test
        void erstelleProjekt_meldetFehlgeschlageneAnfrageUebernahmeVerstaendlich() throws Exception {
                when(projektManagementService.erstelleProjekt(any(), any(), any(), any(), isNull(), isNull(), any()))
                                .thenThrow(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                                ProjektManagementService.ANFRAGE_UEBERNAHME_FEHLGESCHLAGEN));

                mockMvc.perform(post("/api/projekte").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"bauvorhaben\":\"Geländer Musterweg\",\"anfrageIds\":[1]}"))
                                .andExpect(status().isInternalServerError())
                                .andExpect(jsonPath("$.message")
                                                .value(ProjektManagementService.ANFRAGE_UEBERNAHME_FEHLGESCHLAGEN));
        }

        @Test
        void setzeAbgeschlossen_VersionskonfliktGibt409StattNichtGefunden() throws Exception {
                when(projektManagementService.setzeAbgeschlossen(42L, true))
                                .thenThrow(new ObjectOptimisticLockingFailureException(Projekt.class, 42L));

                mockMvc.perform(patch("/api/projekte/42/abgeschlossen").param("abgeschlossen", "true"))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.message").value(VERSIONSKONFLIKT));
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

        // --- Tests: GET /api/projekte/offene-posten/bezahlt (Issue #263) ---

        @Test
        void bezahlteRechnungenLiefertDieBezahltenAusDemService() throws Exception {
                org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument rechnung =
                                new org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument();
                rechnung.setId(77L);
                rechnung.setDokumentid("RE-2026-0042");
                rechnung.setBezahlt(true);
                rechnung.setGespeicherterDateiname("rechnung-77.pdf");
                when(dateiSpeicherService.holeBezahlteRechnungen()).thenReturn(java.util.List.of(rechnung));

                mockMvc.perform(get("/api/projekte/offene-posten/bezahlt"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(1))
                                .andExpect(jsonPath("$[0].id").value(77))
                                .andExpect(jsonPath("$[0].bezahlt").value(true));
        }

        @Test
        void stornierteRechnungZuruecknehmenGibt400MitMeldung() throws Exception {
                doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Diese Rechnung wurde storniert und bleibt deshalb erledigt."))
                                .when(dateiSpeicherService).setzeGeschaeftsdokumentBezahlt(43L, false);

                mockMvc.perform(patch("/api/projekte/dokumente/43/bezahlt").param("bezahlt", "false"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value("Diese Rechnung wurde storniert und bleibt deshalb erledigt."));
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
        void setzeDokumentBezahltGibt404MitMeldungWennDokumentNichtGefunden() throws Exception {
                doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Diese Rechnung gibt es nicht mehr."))
                                .when(dateiSpeicherService).setzeGeschaeftsdokumentBezahlt(999L, true);

                mockMvc.perform(patch("/api/projekte/dokumente/999/bezahlt")
                                .param("bezahlt", "true"))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.message").value("Diese Rechnung gibt es nicht mehr."));
        }

        @Test
        void setzeDokumentBezahltGibt400WennKeinGeschaeftsdokument() throws Exception {
                doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dieses Dokument ist keine Rechnung."))
                                .when(dateiSpeicherService).setzeGeschaeftsdokumentBezahlt(50L, true);

                mockMvc.perform(patch("/api/projekte/dokumente/50/bezahlt")
                                .param("bezahlt", "true"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value("Dieses Dokument ist keine Rechnung."));
        }

        @Test
        void setzeDokumentBezahltGibt409BeiVersionskonfliktAmProjekt() throws Exception {
                // Der Haken rechnet den Projektstatus neu – speichert jemand anders das Projekt
                // gleichzeitig, ist das ein Konflikt und kein "nicht gefunden".
                doThrow(new ObjectOptimisticLockingFailureException(Projekt.class, 10L))
                                .when(dateiSpeicherService).setzeGeschaeftsdokumentBezahlt(42L, true);

                mockMvc.perform(patch("/api/projekte/dokumente/42/bezahlt")
                                .param("bezahlt", "true"))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.message").value(VERSIONSKONFLIKT));
        }

        @Test
        void setzeDokumentBezahltMeldetUnerwarteteFehlerNichtAls404() throws Exception {
                doThrow(new IllegalStateException("Datenbank nicht erreichbar"))
                                .when(dateiSpeicherService).setzeGeschaeftsdokumentBezahlt(42L, true);

                // Kein Sammel-Catch mehr im Controller: der Fehler läuft als Serverfehler durch.
                ServletException fehler = assertThrows(ServletException.class,
                                () -> mockMvc.perform(patch("/api/projekte/dokumente/42/bezahlt")
                                                .param("bezahlt", "true")));
                assertInstanceOf(IllegalStateException.class, fehler.getCause());
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
                dok.setTyp(org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.RECHNUNG);
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
                dok.setTyp(org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.RECHNUNG);
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

        // --- Dokumentrechte: /eingangsrechnungen liegt in der offenen Zeiterfassungs-Chain ---

        private org.example.kalkulationsprogramm.domain.LieferantDokumentProjektAnteil anteilMitTyp(long id,
                        org.example.kalkulationsprogramm.domain.LieferantDokumentTyp typ) {
                var gd = new org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument();
                gd.setId(id);
                gd.setBetragNetto(new java.math.BigDecimal("100.00"));
                gd.setDokumentNummer("NR-" + id);
                var dok = new org.example.kalkulationsprogramm.domain.LieferantDokument();
                dok.setId(id);
                dok.setTyp(typ);
                dok.setGeschaeftsdaten(gd);
                dok.setVerknuepfteDokumente(new java.util.HashSet<>());
                dok.setVerknuepftVon(new java.util.HashSet<>());
                var anteil = new org.example.kalkulationsprogramm.domain.LieferantDokumentProjektAnteil();
                anteil.setId(id);
                anteil.setProzent(100);
                anteil.setDokument(dok);
                return anteil;
        }

        @Test
        void eingangsrechnungen_ohneAnmeldungGibt401UndLaedtNichts() throws Exception {
                when(lieferantDokumentZugriffService.sichtbareTypen(org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.any())).thenReturn(java.util.Optional.empty());

                mockMvc.perform(get("/api/projekte/99/eingangsrechnungen").param("token", "unbekannt"))
                                .andExpect(status().isUnauthorized());

                org.mockito.Mockito.verifyNoInteractions(lieferantDokumentProjektAnteilRepository);
        }

        @Test
        void eingangsrechnungen_zeigtNurSichtbareDokumenttypen() throws Exception {
                var rechnung = anteilMitTyp(1L, org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.RECHNUNG);
                var gutschrift = anteilMitTyp(2L, org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.GUTSCHRIFT);
                when(lieferantDokumentZugriffService.sichtbareTypen(org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.any())).thenReturn(java.util.Optional.of(java.util.EnumSet
                                                .of(org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.RECHNUNG)));
                when(lieferantDokumentProjektAnteilRepository.findByProjektIdEager(99L))
                                .thenReturn(List.of(rechnung, gutschrift));
                when(lieferantDokumentProjektAnteilRepository.findByDokumentIdEager(1L)).thenReturn(List.of(rechnung));

                mockMvc.perform(get("/api/projekte/99/eingangsrechnungen"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(1))
                                .andExpect(jsonPath("$[0].dokumentNummer").value("NR-1"));
        }

        @Test
        void eingangsrechnungen_ohneSichtbareTypenLeereListe() throws Exception {
                when(lieferantDokumentZugriffService.sichtbareTypen(org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.any())).thenReturn(java.util.Optional.of(java.util.EnumSet
                                                .noneOf(org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.class)));
                when(lieferantDokumentProjektAnteilRepository.findByProjektIdEager(99L)).thenReturn(List.of(
                                anteilMitTyp(1L, org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.RECHNUNG)));

                mockMvc.perform(get("/api/projekte/99/eingangsrechnungen"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isEmpty());
        }

        @Test
        void eingangsrechnungen_dokumentenketteVerschweigtNichtSichtbareTypen() throws Exception {
                var rechnung = anteilMitTyp(1L, org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.RECHNUNG);
                var lieferschein = anteilMitTyp(5L, org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.LIEFERSCHEIN)
                                .getDokument();
                var angebot = anteilMitTyp(6L, org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.ANGEBOT)
                                .getDokument();
                rechnung.getDokument().setVerknuepfteDokumente(new java.util.HashSet<>(List.of(lieferschein, angebot)));
                when(lieferantDokumentZugriffService.sichtbareTypen(org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.any())).thenReturn(java.util.Optional.of(java.util.EnumSet.of(
                                                org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.RECHNUNG,
                                                org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.LIEFERSCHEIN)));
                when(lieferantDokumentProjektAnteilRepository.findByProjektIdEager(99L)).thenReturn(List.of(rechnung));
                when(lieferantDokumentProjektAnteilRepository.findByDokumentIdEager(1L)).thenReturn(List.of(rechnung));

                mockMvc.perform(get("/api/projekte/99/eingangsrechnungen"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].dokumentenKette.length()").value(2))
                                .andExpect(jsonPath("$[0].dokumentenKette[?(@.typ=='ANGEBOT')]").isEmpty());
        }

        @Test
        void eingangsrechnungen_dokumentenketteEnthaeltAuchDokumenteUeberUmwege() throws Exception {
                // Rechnung -> AB <- Lieferschein <- Werkstoffzeugnis: Zeugnis und Lieferschein
                // hängen nicht direkt an der Rechnung und fehlten früher in der Projektkette.
                var rechnungAnteil = anteilMitTyp(1L, org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.RECHNUNG);
                var rechnung = rechnungAnteil.getDokument();
                var ab = anteilMitTyp(7L, org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG).getDokument();
                var lieferschein = anteilMitTyp(5L, org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.LIEFERSCHEIN).getDokument();
                var zeugnis = anteilMitTyp(8L, org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.WERKSTOFFZEUGNIS).getDokument();
                rechnung.getVerknuepfteDokumente().add(ab);
                ab.getVerknuepftVon().addAll(List.of(rechnung, lieferschein));
                lieferschein.getVerknuepfteDokumente().add(ab);
                lieferschein.getVerknuepftVon().add(zeugnis);
                zeugnis.getVerknuepfteDokumente().add(lieferschein);
                zeugnis.setUploadDatum(java.time.LocalDateTime.of(2026, 9, 22, 8, 30));
                zeugnis.setAusgeblendet(true);
                when(lieferantDokumentProjektAnteilRepository.findByProjektIdEager(99L)).thenReturn(List.of(rechnungAnteil));
                when(lieferantDokumentProjektAnteilRepository.findByDokumentIdEager(1L)).thenReturn(List.of(rechnungAnteil));

                mockMvc.perform(get("/api/projekte/99/eingangsrechnungen"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].dokumentenKette.length()").value(4))
                                .andExpect(jsonPath("$[0].dokumentenKette[0].typ").value("AUFTRAGSBESTAETIGUNG"))
                                .andExpect(jsonPath("$[0].dokumentenKette[1].typ").value("LIEFERSCHEIN"))
                                .andExpect(jsonPath("$[0].dokumentenKette[2].typ").value("WERKSTOFFZEUGNIS"))
                                .andExpect(jsonPath("$[0].dokumentenKette[2].eingangsDatum").value("2026-09-22"))
                                .andExpect(jsonPath("$[0].dokumentenKette[2].ausgeblendet").value(true))
                                .andExpect(jsonPath("$[0].dokumentenKette[3].typ").value("RECHNUNG"))
                                .andExpect(jsonPath("$[0].dokumentenKetteVerbindungen.length()").value(3))
                                .andExpect(jsonPath("$[0].dokumentenKetteVerbindungen[?(@.vonId==8 && @.zuId==5)]").isNotEmpty())
                                .andExpect(jsonPath("$[0].dokumentenKetteVerbindungen[?(@.vonId==5 && @.zuId==7)]").isNotEmpty())
                                .andExpect(jsonPath("$[0].dokumentenKetteVerbindungen[?(@.vonId==1 && @.zuId==7)]").isNotEmpty());
        }

        @Test
        void eingangsrechnungen_ohneKetteLeereListen() throws Exception {
                var rechnung = anteilMitTyp(1L, org.example.kalkulationsprogramm.domain.LieferantDokumentTyp.RECHNUNG);
                when(lieferantDokumentProjektAnteilRepository.findByProjektIdEager(99L)).thenReturn(List.of(rechnung));
                when(lieferantDokumentProjektAnteilRepository.findByDokumentIdEager(1L)).thenReturn(List.of(rechnung));

                mockMvc.perform(get("/api/projekte/99/eingangsrechnungen"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].dokumentenKette").isEmpty())
                                .andExpect(jsonPath("$[0].dokumentenKetteVerbindungen").isEmpty());
        }

        @Test
        void erzeugeZugferd_ohneVollstaendigeFirmendaten_liefert422MitLesbarerMeldung() throws Exception {
                when(zugferdErstellService.erzeuge(org.mockito.ArgumentMatchers.anyString(),
                                org.mockito.ArgumentMatchers.any()))
                                .thenThrow(new org.example.kalkulationsprogramm.exception.FirmenstammdatenUnvollstaendigException(
                                                "die E-Rechnung", List.of("Straße", "Ort")));

                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .multipart("/api/projekte/1/zugferd")
                                .file(new org.springframework.mock.web.MockMultipartFile(
                                                "datei", "rechnung.pdf", "application/pdf", new byte[] { 1, 2, 3 }))
                                .file(new org.springframework.mock.web.MockMultipartFile(
                                                "zugferdDaten", "", "application/json",
                                                "{\"kundenName\":\"Max Mustermann\"}".getBytes())))
                                .andExpect(status().isUnprocessableEntity())
                                .andExpect(jsonPath("$.message").value(
                                                org.hamcrest.Matchers.containsString("Straße, Ort")));
        }

        @Test
        void erzeugeZugferd_beiAnderemFehler_liefert400() throws Exception {
                when(zugferdErstellService.erzeuge(org.mockito.ArgumentMatchers.anyString(),
                                org.mockito.ArgumentMatchers.any()))
                                .thenThrow(new RuntimeException("ZUGFeRD Erstellung fehlgeschlagen"));

                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .multipart("/api/projekte/1/zugferd")
                                .file(new org.springframework.mock.web.MockMultipartFile(
                                                "datei", "rechnung.pdf", "application/pdf", new byte[] { 1, 2, 3 }))
                                .file(new org.springframework.mock.web.MockMultipartFile(
                                                "zugferdDaten", "", "application/json",
                                                "{\"kundenName\":\"Max Mustermann\"}".getBytes())))
                                .andExpect(status().isBadRequest());
        }

        // ==================== Identität kommt aus der Anmeldung, nie aus Headern ====================

        private static org.example.kalkulationsprogramm.domain.Mitarbeiter mitarbeiter(long id, String vorname) {
                var m = new org.example.kalkulationsprogramm.domain.Mitarbeiter();
                m.setId(id);
                m.setVorname(vorname);
                m.setNachname("Mustermann");
                return m;
        }

        private static org.springframework.security.authentication.UsernamePasswordAuthenticationToken pcAnmeldung() {
                var p = new org.example.kalkulationsprogramm.config.FrontendUserPrincipal(
                                42L, "max.mustermann", "Max Mustermann", "hash", true,
                                java.util.Set.of(org.example.kalkulationsprogramm.domain.FrontendUserRole.ADMIN));
                return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                p, null, p.getAuthorities());
        }

        private static org.example.kalkulationsprogramm.domain.ProjektNotiz notiz(long id,
                        org.example.kalkulationsprogramm.domain.Mitarbeiter verfasser, boolean privat) {
                var n = new org.example.kalkulationsprogramm.domain.ProjektNotiz();
                var projekt = new org.example.kalkulationsprogramm.domain.Projekt();
                projekt.setId(1L);
                n.setId(id);
                n.setProjekt(projekt);
                n.setMitarbeiter(verfasser);
                n.setNotiz("Notiz " + id);
                n.setMobileSichtbar(true);
                n.setNurFuerErsteller(privat);
                n.setErstelltAm(java.time.LocalDateTime.of(2026, 9, 1, 8, 0));
                return n;
        }

        @Test
        void notizen_PrivateNotizenFremderBleibenVerborgenAuchMitGefaelschtenHeadern() throws Exception {
                var max = mitarbeiter(7L, "Max");
                var erika = mitarbeiter(3L, "Erika");
                when(angemeldeterMitarbeiter.handelnderMitarbeiter(org.mockito.ArgumentMatchers.any()))
                                .thenReturn(java.util.Optional.of(max));
                when(projektNotizRepository.findByProjektIdOrderByErstelltAmDesc(1L)).thenReturn(List.of(
                                notiz(1L, max, true), notiz(2L, erika, true), notiz(3L, erika, false)));

                mockMvc.perform(get("/api/projekte/1/notizen")
                                .principal(pcAnmeldung())
                                .header("X-User-Profile-Id", "99")
                                .header("X-Mitarbeiter-Id", "3"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(2))
                                .andExpect(jsonPath("$[0].id").value(1))
                                .andExpect(jsonPath("$[1].id").value(3));
        }

        @Test
        void notizAnlegen_VerfasserIstAngemeldeterMitarbeiterNichtDerHeader() throws Exception {
                var max = mitarbeiter(7L, "Max");
                var projekt = new org.example.kalkulationsprogramm.domain.Projekt();
                projekt.setId(1L);
                when(angemeldeterMitarbeiter.handelnderMitarbeiter(org.mockito.ArgumentMatchers.any()))
                                .thenReturn(java.util.Optional.of(max));
                when(projektManagementService.findeProjektEntity(1L)).thenReturn(projekt);
                when(projektNotizRepository.save(org.mockito.ArgumentMatchers.any()))
                                .thenAnswer(inv -> inv.getArgument(0));

                mockMvc.perform(post("/api/projekte/1/notizen")
                                .principal(pcAnmeldung())
                                .header("X-User-Profile-Id", "99")
                                .header("X-Mitarbeiter-Id", "3")
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("{\"notiz\":\"<script>alert(1)</script>\",\"nurFuerErsteller\":true}"))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.mitarbeiterId").value(7))
                                .andExpect(jsonPath("$.notiz").value("<script>alert(1)</script>"));

                var gespeichert = org.mockito.ArgumentCaptor
                                .forClass(org.example.kalkulationsprogramm.domain.ProjektNotiz.class);
                verify(projektNotizRepository).save(gespeichert.capture());
                org.assertj.core.api.Assertions.assertThat(gespeichert.getValue().getMitarbeiter()).isSameAs(max);
        }

        @Test
        void notizAnlegen_OhneZugeordnetenMitarbeiterGibt403MitHinweisStatt401() throws Exception {
                var projekt = new org.example.kalkulationsprogramm.domain.Projekt();
                projekt.setId(1L);
                when(angemeldeterMitarbeiter.handelnderMitarbeiter(org.mockito.ArgumentMatchers.any()))
                                .thenReturn(java.util.Optional.empty());
                when(projektManagementService.findeProjektEntity(1L)).thenReturn(projekt);

                mockMvc.perform(post("/api/projekte/1/notizen")
                                .principal(pcAnmeldung())
                                .header("X-Mitarbeiter-Id", "3")
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("{\"notiz\":\"Test\"}"))
                                // 401 würde im PC-Frontend als abgelaufene Sitzung abmelden
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.message").value(AngemeldeterMitarbeiterService.KEIN_MITARBEITER));

                org.mockito.Mockito.verify(projektNotizRepository, org.mockito.Mockito.never())
                                .save(org.mockito.ArgumentMatchers.any());
        }

        @Test
        void upload_HochladerIstAngemeldeterMitarbeiterNichtDerHeader() throws Exception {
                var max = mitarbeiter(7L, "Max");
                when(angemeldeterMitarbeiter.handelnderMitarbeiter(org.mockito.ArgumentMatchers.any()))
                                .thenReturn(java.util.Optional.of(max));
                var dokument = new org.example.kalkulationsprogramm.domain.ProjektDokument();
                dokument.setId(55L);
                dokument.setOriginalDateiname("aufmass.jpg");
                dokument.setGespeicherterDateiname("abc.jpg");
                when(dateiSpeicherService.speichereDatei(org.mockito.ArgumentMatchers.any(), eq(1L),
                                eq(org.example.kalkulationsprogramm.domain.DokumentGruppe.BILDER), eq(max), isNull(),
                                isNull())).thenReturn(dokument);

                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .multipart("/api/projekte/1/dokumente")
                                .file(new org.springframework.mock.web.MockMultipartFile(
                                                "datei", "aufmass.jpg", "image/jpeg", new byte[] { 1 }))
                                .param("gruppe", "BILDER")
                                .principal(pcAnmeldung())
                                .header("X-User-Profile-Id", "99")
                                .header("X-Mitarbeiter-Id", "3"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].id").value(55));

                verify(dateiSpeicherService).speichereDatei(org.mockito.ArgumentMatchers.any(), eq(1L),
                                eq(org.example.kalkulationsprogramm.domain.DokumentGruppe.BILDER), eq(max), isNull(),
                                isNull());
        }

        // ==================== Private Notizen bleiben auch am PC beim Verfasser ====================

        private void angemeldetAls(org.example.kalkulationsprogramm.domain.Mitarbeiter m) {
                when(angemeldeterMitarbeiter.handelnderMitarbeiter(org.mockito.ArgumentMatchers.any()))
                                .thenReturn(java.util.Optional.ofNullable(m));
        }

        @Test
        void notizAendern_FremdePrivateNotizGibt403OhneTextUndBleibtPrivat() throws Exception {
                angemeldetAls(mitarbeiter(7L, "Max"));
                var fremd = notiz(20L, mitarbeiter(3L, "Erika"), true);
                when(projektNotizRepository.findById(20L)).thenReturn(java.util.Optional.of(fremd));

                mockMvc.perform(patch("/api/projekte/1/notizen/20")
                                .principal(pcAnmeldung())
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("{}"))
                                .andExpect(status().isForbidden())
                                .andExpect(content().string(org.hamcrest.Matchers.not(
                                                org.hamcrest.Matchers.containsString("Notiz 20"))));

                org.assertj.core.api.Assertions.assertThat(fremd.isNurFuerErsteller()).isTrue();
                org.mockito.Mockito.verify(projektNotizRepository, org.mockito.Mockito.never())
                                .save(org.mockito.ArgumentMatchers.any());
        }

        @Test
        void notizLoeschen_FremdePrivateNotizGibt403() throws Exception {
                angemeldetAls(mitarbeiter(7L, "Max"));
                var fremd = notiz(21L, mitarbeiter(3L, "Erika"), true);
                when(projektNotizRepository.findById(21L)).thenReturn(java.util.Optional.of(fremd));

                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .delete("/api/projekte/1/notizen/21").principal(pcAnmeldung()))
                                .andExpect(status().isForbidden());

                org.mockito.Mockito.verify(projektNotizRepository, org.mockito.Mockito.never())
                                .delete(org.mockito.ArgumentMatchers.any());
        }

        @Test
        void notizAendern_FremdeOeffentlicheNotizDarfAmPcGeaendertWerden() throws Exception {
                angemeldetAls(mitarbeiter(7L, "Max"));
                var fremd = notiz(22L, mitarbeiter(3L, "Erika"), false);
                when(projektNotizRepository.findById(22L)).thenReturn(java.util.Optional.of(fremd));
                when(projektNotizRepository.save(org.mockito.ArgumentMatchers.any()))
                                .thenAnswer(inv -> inv.getArgument(0));

                mockMvc.perform(patch("/api/projekte/1/notizen/22")
                                .principal(pcAnmeldung())
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("{\"notiz\":\"Korrigiert\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.notiz").value("Korrigiert"));
        }

        @Test
        void notizAendern_NotizAusAnderemProjektGibt404() throws Exception {
                angemeldetAls(mitarbeiter(7L, "Max"));
                when(projektNotizRepository.findById(23L))
                                .thenReturn(java.util.Optional.of(notiz(23L, mitarbeiter(7L, "Max"), false)));

                mockMvc.perform(patch("/api/projekte/2/notizen/23")
                                .principal(pcAnmeldung())
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("{\"notiz\":\"x\"}"))
                                .andExpect(status().isNotFound());
        }

        @Test
        void notizAendern_OhneZugeordnetenMitarbeiterGibt403MitHinweis() throws Exception {
                angemeldetAls(null);

                mockMvc.perform(patch("/api/projekte/1/notizen/22")
                                .principal(pcAnmeldung())
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("{\"notiz\":\"x\"}"))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.message").value(AngemeldeterMitarbeiterService.KEIN_MITARBEITER));

                org.mockito.Mockito.verifyNoInteractions(projektNotizRepository);
        }

        // ==================== Fehlercodes statt Sammel-404 ====================
        // Früher machte der Controller aus jeder RuntimeException ein 404 – auch aus einem
        // Versionskonflikt oder einem Serverfehler. Jetzt gilt: fehlt etwas → 404 mit Meldung,
        // Konflikt → 409, Unerwartetes → 500.

        @Test
        void getProjektById_UnbekanntesProjektGibt404MitMeldung() throws Exception {
                when(projektManagementService.findeProjektById(999L))
                                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                ProjektManagementService.PROJEKT_NICHT_GEFUNDEN));

                mockMvc.perform(get("/api/projekte/999"))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.message").value(ProjektManagementService.PROJEKT_NICHT_GEFUNDEN));
        }

        @Test
        void getProjektById_VersionskonfliktBeimNachrechnenGibt409() throws Exception {
                // Das Öffnen rechnet Preis und Status neu und speichert das Projekt.
                when(projektManagementService.findeProjektById(42L))
                                .thenThrow(new ObjectOptimisticLockingFailureException(Projekt.class, 42L));

                mockMvc.perform(get("/api/projekte/42"))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.message").value(VERSIONSKONFLIKT));
        }

        @Test
        void loescheProjekt_NochVerknuepftGibt409StattNichtGefunden() throws Exception {
                doThrow(new org.springframework.dao.DataIntegrityViolationException(
                                "update or delete on table \"projekt\" violates foreign key constraint "
                                                + "\"fk_rechnung_projekt\" on table \"rechnung\""))
                                .when(projektManagementService).loescheProjekt(42L);

                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .delete("/api/projekte/42"))
                                .andExpect(status().isConflict());
        }

        @Test
        void loescheProjekt_UnbekanntesProjektGibt404MitMeldung() throws Exception {
                doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, ProjektManagementService.PROJEKT_NICHT_GEFUNDEN))
                                .when(projektManagementService).loescheProjekt(999L);

                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .delete("/api/projekte/999"))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.message").value(ProjektManagementService.PROJEKT_NICHT_GEFUNDEN));
        }

        @Test
        void fuegeProduktkategorienHinzu_UnbekanntesProjektGibt404MitMeldungStattTechnikText() throws Exception {
                when(projektManagementService.fuegeProduktkategorienHinzu(eq(999L), org.mockito.ArgumentMatchers.anyList()))
                                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                ProjektManagementService.PROJEKT_NICHT_GEFUNDEN));

                mockMvc.perform(post("/api/projekte/999/produktkategorien")
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("[]"))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.message").value(ProjektManagementService.PROJEKT_NICHT_GEFUNDEN));
        }

        @Test
        void loescheProjektProduktkategorie_MitZeitbuchungenGibt409MitGrund() throws Exception {
                String grund = "Die Produktkategorie kann nicht gelöscht werden, da bereits Zeitbuchungen dafür existieren.";
                when(projektManagementService.loescheProjektProduktkategorie(1L, 5L))
                                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, grund));

                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .delete("/api/projekte/1/produktkategorien/5"))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.message").value(grund));
        }

        @Test
        void loescheDokument_UnbekanntesDokumentGibt404MitMeldung() throws Exception {
                doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Dieses Dokument gibt es nicht mehr."))
                                .when(dateiSpeicherService).loescheDatei(77L);

                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .delete("/api/projekte/1/dokumente/77"))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.message").value("Dieses Dokument gibt es nicht mehr."));
        }

        @Test
        void loescheDokument_DateifehlerWirdNichtAls404Gemeldet() throws Exception {
                doThrow(new RuntimeException("Fehler beim Löschen der physischen Datei"))
                                .when(dateiSpeicherService).loescheDatei(77L);

                ServletException fehler = assertThrows(ServletException.class,
                                () -> mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                                .delete("/api/projekte/1/dokumente/77")));
                assertInstanceOf(RuntimeException.class, fehler.getCause());
        }

        @Test
        void projektEmailHinzufuegen_UnbekanntesProjektGibt404StattServerfehler() throws Exception {
                when(projektManagementService.findeProjektEntity(999L)).thenThrow(new ResponseStatusException(
                                HttpStatus.NOT_FOUND, ProjektManagementService.PROJEKT_NICHT_GEFUNDEN));

                mockMvc.perform(post("/api/projekte/999/emails")
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"max.mustermann@example.com\"}"))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.message").value(ProjektManagementService.PROJEKT_NICHT_GEFUNDEN));
        }

        @Test
        void notizAnlegen_UnbekanntesProjektGibt404MitMeldung() throws Exception {
                angemeldetAls(mitarbeiter(7L, "Max"));
                when(projektManagementService.findeProjektEntity(999L)).thenThrow(new ResponseStatusException(
                                HttpStatus.NOT_FOUND, ProjektManagementService.PROJEKT_NICHT_GEFUNDEN));

                mockMvc.perform(post("/api/projekte/999/notizen")
                                .principal(pcAnmeldung())
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("{\"notiz\":\"Test\"}"))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.message").value(ProjektManagementService.PROJEKT_NICHT_GEFUNDEN));

                org.mockito.Mockito.verify(projektNotizRepository, org.mockito.Mockito.never())
                                .save(org.mockito.ArgumentMatchers.any());
        }

        @Test
        void notizLoeschen_UnbekannteNotizGibt404MitMeldung() throws Exception {
                angemeldetAls(mitarbeiter(7L, "Max"));
                when(projektNotizRepository.findById(404L)).thenReturn(java.util.Optional.empty());

                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .delete("/api/projekte/1/notizen/404").principal(pcAnmeldung()))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.message").value(
                                                "Diese Notiz gibt es nicht mehr. Vielleicht wurde sie inzwischen gelöscht."));
        }

        @Test
        void notizAendern_GleichzeitigGeloeschtGibt409StattNichtGefunden() throws Exception {
                // ProjektNotiz hat kein @Version: 409 kommt real, wenn jemand die Notiz zwischen
                // Laden und Speichern löscht ("Row was updated or deleted by another transaction").
                angemeldetAls(mitarbeiter(7L, "Max"));
                when(projektNotizRepository.findById(22L))
                                .thenReturn(java.util.Optional.of(notiz(22L, mitarbeiter(7L, "Max"), false)));
                when(projektNotizRepository.save(org.mockito.ArgumentMatchers.any()))
                                .thenThrow(new ObjectOptimisticLockingFailureException(
                                                org.example.kalkulationsprogramm.domain.ProjektNotiz.class, 22L));

                mockMvc.perform(patch("/api/projekte/1/notizen/22")
                                .principal(pcAnmeldung())
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("{\"notiz\":\"x\"}"))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.message").value(VERSIONSKONFLIKT));
        }

        @Test
        void notizBildHochladen_UnbekannteNotizGibt404StattServerfehler() throws Exception {
                angemeldetAls(mitarbeiter(7L, "Max"));
                when(projektNotizRepository.findById(404L)).thenReturn(java.util.Optional.empty());

                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .multipart("/api/projekte/1/notizen/404/bilder")
                                .file(new org.springframework.mock.web.MockMultipartFile(
                                                "datei", "foto.jpg", "image/jpeg", new byte[] { 1 }))
                                .principal(pcAnmeldung()))
                                .andExpect(status().isNotFound());

                org.mockito.Mockito.verify(dateiSpeicherService, org.mockito.Mockito.never())
                                .speichereProjektNotizBild(org.mockito.ArgumentMatchers.any());
        }

        @Test
        void notizBildLoeschen_BildEinerAnderenNotizGibt404UndBleibtStehen() throws Exception {
                angemeldetAls(mitarbeiter(7L, "Max"));
                when(projektNotizRepository.findById(22L))
                                .thenReturn(java.util.Optional.of(notiz(22L, mitarbeiter(7L, "Max"), false)));
                var bild = new org.example.kalkulationsprogramm.domain.ProjektNotizBild();
                bild.setId(5L);
                bild.setNotiz(notiz(30L, mitarbeiter(7L, "Max"), false));
                when(projektNotizBildRepository.findById(5L)).thenReturn(java.util.Optional.of(bild));

                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .delete("/api/projekte/1/notizen/22/bilder/5").principal(pcAnmeldung()))
                                .andExpect(status().isNotFound());

                org.mockito.Mockito.verify(projektNotizBildRepository, org.mockito.Mockito.never())
                                .delete(org.mockito.ArgumentMatchers.any());
        }

        @Test
        void notizBildLoeschen_EigenesBildWirdGeloescht() throws Exception {
                angemeldetAls(mitarbeiter(7L, "Max"));
                var notiz = notiz(22L, mitarbeiter(7L, "Max"), false);
                when(projektNotizRepository.findById(22L)).thenReturn(java.util.Optional.of(notiz));
                var bild = new org.example.kalkulationsprogramm.domain.ProjektNotizBild();
                bild.setId(5L);
                bild.setNotiz(notiz);
                bild.setGespeicherterDateiname("abc.jpg");
                when(projektNotizBildRepository.findById(5L)).thenReturn(java.util.Optional.of(bild));

                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .delete("/api/projekte/1/notizen/22/bilder/5").principal(pcAnmeldung()))
                                .andExpect(status().isNoContent());

                verify(projektNotizBildRepository).delete(bild);
        }

        // ==================== Manuell erfasste Materialkosten ====================

        @Test
        void fuegeMaterialkostenHinzu_SchicktGenauEinenEintragAnDenService() throws Exception {
                ProjektResponseDto antwort = new ProjektResponseDto();
                antwort.setId(42L);
                when(projektManagementService.fuegeMaterialkostenHinzu(eq(42L),
                                org.mockito.ArgumentMatchers.any())).thenReturn(antwort);

                mockMvc.perform(post("/api/projekte/42/materialkosten")
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("{\"beschreibung\":\"Kleinmaterial\",\"betrag\":12.5,\"monat\":10,"
                                                + "\"lieferantId\":7,\"rechnungsnummer\":\"RE-1\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(42));

                var captor = org.mockito.ArgumentCaptor.forClass(
                                org.example.kalkulationsprogramm.dto.Materialkosten.MaterialkostenErfassenDto.class);
                verify(projektManagementService).fuegeMaterialkostenHinzu(eq(42L), captor.capture());
                org.junit.jupiter.api.Assertions.assertEquals("Kleinmaterial", captor.getValue().getBeschreibung());
                org.junit.jupiter.api.Assertions.assertEquals(0,
                                captor.getValue().getBetrag().compareTo(new java.math.BigDecimal("12.5")));
                org.junit.jupiter.api.Assertions.assertEquals(7L, captor.getValue().getLieferantId());
                org.junit.jupiter.api.Assertions.assertEquals("RE-1", captor.getValue().getRechnungsnummer());
        }

        @Test
        void fuegeMaterialkostenHinzu_UngueltigeEingabeGibt400MitMeldung() throws Exception {
                when(projektManagementService.fuegeMaterialkostenHinzu(eq(42L), org.mockito.ArgumentMatchers.any()))
                                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte einen Betrag angeben."));

                mockMvc.perform(post("/api/projekte/42/materialkosten")
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("{\"beschreibung\":\"Kleinmaterial\"}"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value("Bitte einen Betrag angeben."));
        }

        @Test
        void fuegeMaterialkostenHinzu_UnbekanntesProjektGibt404MitMeldung() throws Exception {
                when(projektManagementService.fuegeMaterialkostenHinzu(eq(999L), org.mockito.ArgumentMatchers.any()))
                                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                ProjektManagementService.PROJEKT_NICHT_GEFUNDEN));

                mockMvc.perform(post("/api/projekte/999/materialkosten")
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("{\"beschreibung\":\"Kleinmaterial\",\"betrag\":1}"))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.message").value(ProjektManagementService.PROJEKT_NICHT_GEFUNDEN));
        }

        @Test
        void materialkostenListeErsetzenGibtEsNichtMehr() throws Exception {
                // Früher ersetzte PATCH die komplette Liste – und löschte so alle älteren Einträge.
                mockMvc.perform(patch("/api/projekte/42/materialkosten")
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("[]"))
                                .andExpect(status().isMethodNotAllowed());

                org.mockito.Mockito.verifyNoInteractions(projektManagementService);
        }

        @Test
        void entferneMaterialkosten_LiefertDasAktualisierteProjekt() throws Exception {
                ProjektResponseDto antwort = new ProjektResponseDto();
                antwort.setId(42L);
                when(projektManagementService.entferneMaterialkosten(42L, 10L)).thenReturn(antwort);

                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .delete("/api/projekte/42/materialkosten/10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(42));
        }

        @Test
        void entferneMaterialkosten_UnbekannterEintragGibt404MitMeldung() throws Exception {
                when(projektManagementService.entferneMaterialkosten(42L, Long.MAX_VALUE))
                                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                "Diese Materialkosten gibt es in diesem Projekt nicht mehr."));

                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .delete("/api/projekte/42/materialkosten/" + Long.MAX_VALUE))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.message").value("Diese Materialkosten gibt es in diesem Projekt nicht mehr."));
        }
}
