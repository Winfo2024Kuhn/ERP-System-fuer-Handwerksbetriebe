package org.example.kalkulationsprogramm.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(OffenePostenController.class)
@AutoConfigureMockMvc(addFilters = false)
class OffenePostenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository;

    @MockBean
    private MitarbeiterRepository mitarbeiterRepository;

    @MockBean
    private org.example.kalkulationsprogramm.service.FrontendUserProfileService frontendUserProfileService;

    @MockBean
    private org.example.kalkulationsprogramm.service.GeminiDokumentAnalyseService geminiDokumentAnalyseService;

    @MockBean
    private org.example.kalkulationsprogramm.repository.ProjektRepository projektRepository;

    @MockBean
    private org.example.kalkulationsprogramm.repository.ProjektDokumentRepository projektDokumentRepository;

    @MockBean
    private org.example.kalkulationsprogramm.service.DateiSpeicherService dateiSpeicherService;

    @MockBean
    private org.example.kalkulationsprogramm.service.AusgangsGeschaeftsDokumentService ausgangsGeschaeftsDokumentService;

    private Mitarbeiter buildMitarbeiter(boolean darfGenehmigen, boolean darfSehen) {
        Mitarbeiter m = new Mitarbeiter();
        m.setId(1L);
        Abteilung abt = new Abteilung();
        abt.setId(1L);
        abt.setDarfRechnungenGenehmigen(darfGenehmigen);
        abt.setDarfRechnungenSehen(darfSehen);
        Set<Abteilung> abteilungen = new HashSet<>();
        abteilungen.add(abt);
        m.setAbteilungen(abteilungen);
        return m;
    }

    private LieferantGeschaeftsdokument buildGeschaeftsdokument(Long id) {
        LieferantGeschaeftsdokument gd = new LieferantGeschaeftsdokument();
        gd.setId(id);
        gd.setDokumentNummer("RE-001");
        gd.setDokumentDatum(LocalDate.of(2024, 1, 15));
        gd.setBetragBrutto(new BigDecimal("1190.00"));
        gd.setBetragNetto(new BigDecimal("1000.00"));
        gd.setBezahlt(false);
        gd.setGenehmigt(false);
        return gd;
    }

    @Nested
    @DisplayName("GET /api/offene-posten/eingang")
    class OffeneEingangsrechnungen {

        @Test
        @DisplayName("Abteilung Büro (3) sieht alle offenen Rechnungen")
        void bueroSiehtAlleRechnungen() throws Exception {
            Mitarbeiter m = buildMitarbeiter(true, true);
            given(mitarbeiterRepository.findByLoginToken("buero-token")).willReturn(Optional.of(m));

            LieferantGeschaeftsdokument gd = buildGeschaeftsdokument(1L);
            given(geschaeftsdokumentRepository.findAllOffeneEingangsrechnungen()).willReturn(List.of(gd));

            mockMvc.perform(get("/api/offene-posten/eingang")
                            .header("X-Auth-Token", "buero-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].dokumentNummer").value("RE-001"))
                    .andExpect(jsonPath("$[0].darfGenehmigen").value(true));
        }

        @Test
        @DisplayName("Abteilung Buchhaltung (2) sieht nur genehmigte Rechnungen")
        void buchhaltungSiehtNurGenehmigte() throws Exception {
            Mitarbeiter m = buildMitarbeiter(false, true);
            given(mitarbeiterRepository.findByLoginToken("bh-token")).willReturn(Optional.of(m));
            given(geschaeftsdokumentRepository.findAllOffeneGenehmigte()).willReturn(List.of());

            mockMvc.perform(get("/api/offene-posten/eingang")
                            .header("X-Auth-Token", "bh-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }

        @Test
        @DisplayName("Ohne Token bekommt leere Liste")
        void ohneTokenLeereListe() throws Exception {
            mockMvc.perform(get("/api/offene-posten/eingang"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());
        }

        @Test
        @DisplayName("Ungültiger Token bekommt leere Liste")
        void ungueltigerTokenLeereListe() throws Exception {
            given(mitarbeiterRepository.findByLoginToken("invalid")).willReturn(Optional.empty());

            mockMvc.perform(get("/api/offene-posten/eingang")
                            .header("X-Auth-Token", "invalid"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());
        }
    }

    @Nested
    @DisplayName("GET /api/offene-posten/eingang/alle")
    class AlleEingangsrechnungen {

        @Test
        @DisplayName("Abteilung Büro sieht alle Rechnungen")
        void bueroSiehtAlleRechnungen() throws Exception {
            Mitarbeiter m = buildMitarbeiter(true, true);
            given(mitarbeiterRepository.findByLoginToken("buero-token")).willReturn(Optional.of(m));
            given(geschaeftsdokumentRepository.findAllEingangsrechnungen()).willReturn(List.of());

            mockMvc.perform(get("/api/offene-posten/eingang/alle")
                            .header("X-Auth-Token", "buero-token"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("PUT /api/offene-posten/eingang/{id}/bezahlt")
    class Bezahlt {

        @Test
        @DisplayName("Setzt Rechnung als bezahlt")
        void setztAlsBezahlt() throws Exception {
            Mitarbeiter m = buildMitarbeiter(true, true);
            given(mitarbeiterRepository.findByLoginToken("token")).willReturn(Optional.of(m));

            LieferantGeschaeftsdokument gd = buildGeschaeftsdokument(1L);
            given(geschaeftsdokumentRepository.findById(1L)).willReturn(Optional.of(gd));
            given(geschaeftsdokumentRepository.save(any())).willReturn(gd);

            mockMvc.perform(put("/api/offene-posten/eingang/1/bezahlt")
                            .header("X-Auth-Token", "token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"bezahlt\": true}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1));
        }

        @Test
        @DisplayName("Unbekannte Rechnung gibt 404")
        void unbekannteRechnungGibt404() throws Exception {
            given(geschaeftsdokumentRepository.findById(999L)).willReturn(Optional.empty());

            mockMvc.perform(put("/api/offene-posten/eingang/999/bezahlt")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"bezahlt\": true}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Skonto wird berechnet wenn innerhalb Frist")
        void skontoBerechnung() throws Exception {
            Mitarbeiter m = buildMitarbeiter(true, true);
            given(mitarbeiterRepository.findByLoginToken("token")).willReturn(Optional.of(m));

            LieferantGeschaeftsdokument gd = buildGeschaeftsdokument(1L);
            gd.setSkontoTage(30);
            gd.setSkontoProzent(new BigDecimal("2.00"));
            gd.setDokumentDatum(LocalDate.now().minusDays(5)); // innerhalb Skonto-Frist
            given(geschaeftsdokumentRepository.findById(1L)).willReturn(Optional.of(gd));
            given(geschaeftsdokumentRepository.save(any())).willReturn(gd);

            mockMvc.perform(put("/api/offene-posten/eingang/1/bezahlt")
                            .header("X-Auth-Token", "token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"bezahlt\": true}"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("PATCH /api/offene-posten/eingang/{id}/genehmigen")
    class Genehmigen {

        @Test
        @DisplayName("Büro-Mitarbeiter kann genehmigen")
        void bueroKannGenehmigen() throws Exception {
            Mitarbeiter m = buildMitarbeiter(true, true);
            given(mitarbeiterRepository.findByLoginToken("buero-token")).willReturn(Optional.of(m));

            LieferantGeschaeftsdokument gd = buildGeschaeftsdokument(1L);
            given(geschaeftsdokumentRepository.findById(1L)).willReturn(Optional.of(gd));
            given(geschaeftsdokumentRepository.save(any())).willReturn(gd);

            mockMvc.perform(patch("/api/offene-posten/eingang/1/genehmigen")
                            .header("X-Auth-Token", "buero-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"genehmigt\": true}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.genehmigt").value(true));
        }

        @Test
        @DisplayName("Buchhaltung (Abt. 2) darf nicht genehmigen → 403")
        void buchhaltungDarfNichtGenehmigen() throws Exception {
            Mitarbeiter m = buildMitarbeiter(false, true);
            given(mitarbeiterRepository.findByLoginToken("bh-token")).willReturn(Optional.of(m));

            mockMvc.perform(patch("/api/offene-posten/eingang/1/genehmigen")
                            .header("X-Auth-Token", "bh-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"genehmigt\": true}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Ohne Token darf nicht genehmigen → 403")
        void ohneTokenDarfNichtGenehmigen() throws Exception {
            mockMvc.perform(patch("/api/offene-posten/eingang/1/genehmigen")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"genehmigt\": true}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Unbekannte Rechnung gibt 404 (bei berechtigtem Benutzer)")
        void unbekannteRechnungGibt404() throws Exception {
            Mitarbeiter m = buildMitarbeiter(true, true);
            given(mitarbeiterRepository.findByLoginToken("buero-token")).willReturn(Optional.of(m));
            given(geschaeftsdokumentRepository.findById(999L)).willReturn(Optional.empty());

            mockMvc.perform(patch("/api/offene-posten/eingang/999/genehmigen")
                            .header("X-Auth-Token", "buero-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"genehmigt\": true}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("SQL Injection im Token wird nicht interpretiert")
        void sqlInjectionImToken() throws Exception {
            String sqlToken = "'; DROP TABLE mitarbeiter; --";
            given(mitarbeiterRepository.findByLoginToken(sqlToken)).willReturn(Optional.empty());

            mockMvc.perform(patch("/api/offene-posten/eingang/1/genehmigen")
                            .header("X-Auth-Token", sqlToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"genehmigt\": true}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Ausgangsrechnung manuell erfassen")
    class ImportAusgangsrechnung {

        private org.springframework.mock.web.MockMultipartFile pdf() {
            return new org.springframework.mock.web.MockMultipartFile(
                    "datei", "rechnung.pdf", MediaType.APPLICATION_PDF_VALUE, "inhalt".getBytes());
        }

        @Test
        @DisplayName("Übernimmt Betrag und stößt die Preisberechnung an")
        void importiertRechnungUndBerechnetProjektpreisNeu() throws Exception {
            org.example.kalkulationsprogramm.domain.Projekt projekt =
                    new org.example.kalkulationsprogramm.domain.Projekt();
            projekt.setId(5L);
            projekt.setBauvorhaben("Carport Musterstraße");

            org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument gespeichert =
                    new org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument();
            gespeichert.setId(77L);

            given(projektRepository.findById(5L)).willReturn(Optional.of(projekt));
            given(projektDokumentRepository.existsByDokumentid("RE-2026-100")).willReturn(false);
            given(dateiSpeicherService.speichereDatei(any(), any(), any())).willReturn(gespeichert);
            given(projektDokumentRepository.save(any())).willReturn(gespeichert);

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .multipart("/api/offene-posten/ausgang/import")
                            .file(pdf())
                            .param("projektId", "5")
                            .param("rechnungsnummer", "RE-2026-100")
                            .param("betragBrutto", "1190.00"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rechnungsnummer").value("RE-2026-100"));

            org.mockito.Mockito.verify(ausgangsGeschaeftsDokumentService)
                    .aktualisiereProjektPreisAusDokumenten(5L);
        }

        @Test
        @DisplayName("Ohne Betrag: 400, damit der Auftragspreis nicht verloren geht")
        void lehntRechnungOhneBetragAb() throws Exception {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .multipart("/api/offene-posten/ausgang/import")
                            .file(pdf())
                            .param("projektId", "5")
                            .param("rechnungsnummer", "RE-2026-101"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Rechnungsbetrag ist erforderlich"));

            org.mockito.Mockito.verifyNoInteractions(ausgangsGeschaeftsDokumentService);
        }

        @Test
        @DisplayName("Betrag 0 oder negativ wird abgelehnt")
        void lehntNichtPositivenBetragAb() throws Exception {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .multipart("/api/offene-posten/ausgang/import")
                            .file(pdf())
                            .param("projektId", "5")
                            .param("rechnungsnummer", "RE-2026-102")
                            .param("betragBrutto", "0"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Rechnungsbetrag muss größer als 0 sein"));
        }

        @Test
        @DisplayName("Betrag ohne Zahlenformat wird abgefangen statt zu crashen")
        void lehntUngueltigenBetragAb() throws Exception {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .multipart("/api/offene-posten/ausgang/import")
                            .file(pdf())
                            .param("projektId", "5")
                            .param("rechnungsnummer", "RE-2026-103")
                            .param("betragBrutto", "<script>alert(1)</script>"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Rechnungsbetrag ist keine gültige Zahl"));
        }

        @Test
        @DisplayName("Unbekanntes Projekt wird abgelehnt")
        void lehntUnbekanntesProjektAb() throws Exception {
            given(projektRepository.findById(Long.MAX_VALUE)).willReturn(Optional.empty());

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .multipart("/api/offene-posten/ausgang/import")
                            .file(pdf())
                            .param("projektId", String.valueOf(Long.MAX_VALUE))
                            .param("rechnungsnummer", "RE-2026-104")
                            .param("betragBrutto", "500.00"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Projekt nicht gefunden"));

            org.mockito.Mockito.verifyNoInteractions(ausgangsGeschaeftsDokumentService);
        }

        @Test
        @DisplayName("Doppelte Rechnungsnummer wird abgelehnt")
        void lehntDoppelteRechnungsnummerAb() throws Exception {
            org.example.kalkulationsprogramm.domain.Projekt projekt =
                    new org.example.kalkulationsprogramm.domain.Projekt();
            projekt.setId(5L);

            given(projektRepository.findById(5L)).willReturn(Optional.of(projekt));
            given(projektDokumentRepository.existsByDokumentid("RE-2026-100")).willReturn(true);

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .multipart("/api/offene-posten/ausgang/import")
                            .file(pdf())
                            .param("projektId", "5")
                            .param("rechnungsnummer", "RE-2026-100")
                            .param("betragBrutto", "500.00"))
                    .andExpect(status().isConflict());

            org.mockito.Mockito.verifyNoInteractions(ausgangsGeschaeftsDokumentService);
        }
    }
}
