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
import org.example.kalkulationsprogramm.service.StuecklistePdfService;
import org.example.kalkulationsprogramm.service.ZugferdErstellService;
import org.example.kalkulationsprogramm.service.ZugferdExtractorService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
                                eq(0),
                                eq(50));
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

        // --- Tests: POST /api/projekte/{id}/notizen mit kategorie (EN-1090-Bautagebuch) ---

        @Test
        void erstelleNotiz_speichertKategorieAusDto() throws Exception {
                org.example.kalkulationsprogramm.domain.Projekt projekt =
                        new org.example.kalkulationsprogramm.domain.Projekt();
                projekt.setId(7L);
                org.example.kalkulationsprogramm.domain.Mitarbeiter mitarbeiter =
                        new org.example.kalkulationsprogramm.domain.Mitarbeiter();
                mitarbeiter.setId(11L);
                mitarbeiter.setVorname("Max");
                mitarbeiter.setNachname("Mustermann");

                when(projektRepository.findById(7L))
                        .thenReturn(java.util.Optional.of(projekt));
                when(mitarbeiterRepository.findByLoginToken("tok"))
                        .thenReturn(java.util.Optional.of(mitarbeiter));
                when(projektNotizRepository.save(any(org.example.kalkulationsprogramm.domain.ProjektNotiz.class)))
                        .thenAnswer(inv -> {
                                org.example.kalkulationsprogramm.domain.ProjektNotiz arg = inv.getArgument(0);
                                arg.setId(99L);
                                return arg;
                        });

                String body = "{\"notiz\":\"24x M16 HV 450 Nm\",\"kategorie\":\"VERBINDUNGSMITTEL\"}";
                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/projekte/7/notizen")
                                .param("token", "tok")
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content(body))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.kategorie").value("VERBINDUNGSMITTEL"));

                ArgumentCaptor<org.example.kalkulationsprogramm.domain.ProjektNotiz> captor =
                        ArgumentCaptor.forClass(org.example.kalkulationsprogramm.domain.ProjektNotiz.class);
                verify(projektNotizRepository).save(captor.capture());
                org.junit.jupiter.api.Assertions.assertEquals(
                        org.example.kalkulationsprogramm.domain.ProjektNotizKategorie.VERBINDUNGSMITTEL,
                        captor.getValue().getKategorie());
        }

        @Test
        void erstelleNotiz_ohneKategorie_landetAufAllgemein() throws Exception {
                org.example.kalkulationsprogramm.domain.Projekt projekt =
                        new org.example.kalkulationsprogramm.domain.Projekt();
                projekt.setId(7L);
                org.example.kalkulationsprogramm.domain.Mitarbeiter mitarbeiter =
                        new org.example.kalkulationsprogramm.domain.Mitarbeiter();
                mitarbeiter.setId(11L);

                when(projektRepository.findById(7L))
                        .thenReturn(java.util.Optional.of(projekt));
                when(mitarbeiterRepository.findByLoginToken("tok"))
                        .thenReturn(java.util.Optional.of(mitarbeiter));
                when(projektNotizRepository.save(any(org.example.kalkulationsprogramm.domain.ProjektNotiz.class)))
                        .thenAnswer(inv -> {
                                org.example.kalkulationsprogramm.domain.ProjektNotiz arg = inv.getArgument(0);
                                arg.setId(100L);
                                return arg;
                        });

                // Älterer Mobile-Client schickt kategorie nicht mit
                String body = "{\"notiz\":\"Material angeliefert\"}";
                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/projekte/7/notizen")
                                .param("token", "tok")
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content(body))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.kategorie").value("ALLGEMEIN"));
        }

        @Test
        void erstelleNotiz_ungueltigeKategorie_faelltStillAufAllgemein() throws Exception {
                org.example.kalkulationsprogramm.domain.Projekt projekt =
                        new org.example.kalkulationsprogramm.domain.Projekt();
                projekt.setId(7L);
                org.example.kalkulationsprogramm.domain.Mitarbeiter mitarbeiter =
                        new org.example.kalkulationsprogramm.domain.Mitarbeiter();
                mitarbeiter.setId(11L);

                when(projektRepository.findById(7L))
                        .thenReturn(java.util.Optional.of(projekt));
                when(mitarbeiterRepository.findByLoginToken("tok"))
                        .thenReturn(java.util.Optional.of(mitarbeiter));
                when(projektNotizRepository.save(any(org.example.kalkulationsprogramm.domain.ProjektNotiz.class)))
                        .thenAnswer(inv -> {
                                org.example.kalkulationsprogramm.domain.ProjektNotiz arg = inv.getArgument(0);
                                arg.setId(101L);
                                return arg;
                        });

                // Ein Mobile-Tippfehler darf das Bautagebuch nicht blockieren
                String body = "{\"notiz\":\"Test\",\"kategorie\":\"FOO_BAR\"}";
                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/projekte/7/notizen")
                                .param("token", "tok")
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content(body))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.kategorie").value("ALLGEMEIN"));
        }
}
